package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MacroTemplateTest {
    @Test
    void expandsTypedPropertiesFormattersAndEscapedBraces() {
        MacroVariableContext context = new MacroVariableContext();
        context.set("item", MacroValue.structured(MacroValue.Kind.ITEM, "Obsidian", Map.of(
            "id", MacroValue.identifier("minecraft:obsidian"),
            "count", MacroValue.number(12),
            "slot", MacroValue.slot(7)
        )));
        context.set("amount", MacroValue.text("203M"));

        MacroTemplate.Resolution result = MacroTemplate.resolve(
            "{{item}} {item} {item.id} x{item.count} slot {item.slot} / {amount|number}", context, null);

        assertTrue(result.success());
        assertEquals("{item} Obsidian minecraft:obsidian x12 slot 7 / 203000000", result.value());
    }

    @Test
    void missingValuesFailUnlessDefaulted() {
        MacroVariableContext context = new MacroVariableContext();
        MacroTemplate.Resolution missing = MacroTemplate.resolve("/pay {playerName} {amount}", context, null);
        MacroTemplate.Resolution defaulted = MacroTemplate.resolve("{amount|default:0}", context, null);

        assertFalse(missing.success());
        assertEquals(2, missing.missing().size());
        assertTrue(defaulted.success());
        assertEquals("0", defaulted.value());
    }

    @Test
    void ordinaryJsonBracesRemainLiteral() {
        String json = "{\"item\":\"obsidian\",\"count\":4}";
        MacroTemplate.Resolution resolved = MacroTemplate.resolve(json, new MacroVariableContext(), null);
        assertTrue(resolved.success());
        assertEquals(json, resolved.value());
        assertFalse(MacroTemplate.hasVariables(json));
    }

    @Test
    void randomBuiltInGeneratesRangesAndPicks() {
        MacroVariableContext context = new MacroVariableContext();
        for (int i = 0; i < 25; i++) {
            int bare = Integer.parseInt(MacroTemplate.resolve("{random}", context, null).value());
            assertTrue(bare >= 0 && bare < 100);
            int ranged = Integer.parseInt(MacroTemplate.resolve("{random|1-100}", context, null).value());
            assertTrue(ranged >= 1 && ranged <= 100);
            int reversed = Integer.parseInt(MacroTemplate.resolve("{random|100-1}", context, null).value());
            assertTrue(reversed >= 1 && reversed <= 100);
            int negative = Integer.parseInt(MacroTemplate.resolve("{random|-5--1}", context, null).value());
            assertTrue(negative >= -5 && negative <= -1);
            double decimal = Double.parseDouble(MacroTemplate.resolve("{random|1.5-3.5}", context, null).value());
            assertTrue(decimal >= 1.5 && decimal <= 3.5);
            String picked = MacroTemplate.resolve("{random|pick:red, green ,blue}", context, null).value();
            assertTrue(java.util.List.of("red", "green", "blue").contains(picked));
        }
        assertEquals("5", MacroTemplate.resolve("{random|5-5}", context, null).value());
    }

    @Test
    void randomComposesWithFormattersAndFailsOnUnknownOnes() {
        MacroVariableContext context = new MacroVariableContext();
        assertTrue(MacroTemplate.resolve("{random|1-100|number}", context, null).success());
        assertFalse(MacroTemplate.resolve("{random|bogus}", context, null).success());
        assertFalse(MacroTemplate.resolve("{random|pick:}", context, null).success());
    }

    @Test
    void userVariableNamedRandomShadowsTheBuiltIn() {
        MacroVariableContext context = new MacroVariableContext();
        context.set("random", MacroValue.text("fixed"));
        assertEquals("fixed", MacroTemplate.resolve("{random}", context, null).value());
    }

    @Test
    void bareRangeShorthand() {
        MacroVariableContext context = new MacroVariableContext();
        for (int i = 0; i < 25; i++) {
            int ranged = Integer.parseInt(MacroTemplate.resolve("{1-100}", context, null).value());
            assertTrue(ranged >= 1 && ranged <= 100);
            int reversed = Integer.parseInt(MacroTemplate.resolve("{100-1}", context, null).value());
            assertTrue(reversed >= 1 && reversed <= 100);
            int negative = Integer.parseInt(MacroTemplate.resolve("{-5--1}", context, null).value());
            assertTrue(negative >= -5 && negative <= -1);
            double decimal = Double.parseDouble(MacroTemplate.resolve("{1.5-3.5}", context, null).value());
            assertTrue(decimal >= 1.5 && decimal <= 3.5);
        }
        assertEquals("5", MacroTemplate.resolve("{5-5}", context, null).value());
        assertEquals("0", MacroTemplate.resolve("{0-0}", context, null).value());
    }

    @Test
    void bareRangeZeroPadding() {
        MacroVariableContext context = new MacroVariableContext();
        for (int i = 0; i < 25; i++) {
            String two = MacroTemplate.resolve("{01-99}", context, null).value();
            assertEquals(2, two.length());
            int twoValue = Integer.parseInt(two);
            assertTrue(twoValue >= 1 && twoValue <= 99);
            String three = MacroTemplate.resolve("{001-100}", context, null).value();
            assertEquals(3, three.length());
            String signed = MacroTemplate.resolve("{-01-05}", context, null).value();
            assertTrue(signed.matches("-?\\d{2}"));
            int signedValue = Integer.parseInt(signed);
            assertTrue(signedValue >= -1 && signedValue <= 5);
            assertEquals(1, MacroTemplate.resolve("{0-9}", context, null).value().length());
            assertEquals(2, MacroTemplate.resolve("{random|01-99}", context, null).value().length());
        }
    }

    @Test
    void barePickShorthand() {
        MacroVariableContext context = new MacroVariableContext();
        java.util.List<String> greetings = java.util.List.of("hey", "yo", "sup");
        for (int i = 0; i < 25; i++) {
            assertTrue(greetings.contains(MacroTemplate.resolve("{hey,yo,sup}", context, null).value()));
            assertTrue(java.util.List.of("hey", "yo").contains(MacroTemplate.resolve("{hey , yo}", context, null).value()));
            assertTrue(java.util.List.of("a", "b").contains(MacroTemplate.resolve("{a,,b}", context, null).value()));
            assertTrue(java.util.List.of("héy", "ño").contains(MacroTemplate.resolve("{héy,ño}", context, null).value()));
        }
    }

    @Test
    void bareTokensStayLiteralWhenAmbiguous() {
        MacroVariableContext context = new MacroVariableContext();
        String[] literals = {
            "\\d{2,4}", "{0,1}", "{2,}", "{,4}", "{a,}", "{ , }",
            "{a=1,b=2}", "{\\d,\\w}", "{\"a\",\"b\"}", "{a:1,b:2}",
            "{12:30,14:00}", "{1-100,}", "{1-2-3}", "{|upper}"
        };
        for (String literal : literals) {
            MacroTemplate.Resolution resolved = MacroTemplate.resolve(literal, context, null);
            assertTrue(resolved.success(), literal);
            assertEquals(literal, resolved.value(), literal);
            assertFalse(MacroTemplate.hasVariables(literal), literal);
        }
    }

    @Test
    void quantifierLookalikeEscapes() {
        MacroVariableContext context = new MacroVariableContext();
        for (int i = 0; i < 25; i++) {
            assertTrue(java.util.List.of("2", "4").contains(MacroTemplate.resolve("{2, 4}", context, null).value()));
            assertTrue(java.util.List.of("1", "2", "3").contains(MacroTemplate.resolve("{1,2,3}", context, null).value()));
            assertTrue(java.util.List.of("-1", "1").contains(MacroTemplate.resolve("{-1,1}", context, null).value()));
            assertTrue(java.util.List.of("1.5", "2.5").contains(MacroTemplate.resolve("{1.5,2.5}", context, null).value()));
        }
    }

    @Test
    void barePickNestedRangesAndWeights() {
        MacroVariableContext context = new MacroVariableContext();
        for (int i = 0; i < 50; i++) {
            int nested = Integer.parseInt(MacroTemplate.resolve("{1-5,10,20-30}", context, null).value());
            assertTrue((nested >= 1 && nested <= 5) || nested == 10 || (nested >= 20 && nested <= 30));
            assertTrue(java.util.List.of("01", "02", "03", "x")
                .contains(MacroTemplate.resolve("{01-03,x}", context, null).value()));
        }

        assertEquals("a", MacroTemplate.resolve("{a*3,a}", context, null).value());
        assertEquals("a*0", MacroTemplate.resolve("{a*0,a*0}", context, null).value());
        assertEquals("a*999", MacroTemplate.resolve("{a*999,a*999}", context, null).value());
        assertEquals("*3", MacroTemplate.resolve("{*3,*3}", context, null).value());
        assertEquals("1", MacroTemplate.resolve("{1-1*3,1-1}", context, null).value());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(MacroTemplate.resolve("{hey*3,yo}", context, null).value());
        assertEquals(java.util.Set.of("hey", "yo"), seen);
    }

    @Test
    void bareSpecFormatters() {
        MacroVariableContext context = new MacroVariableContext();
        assertTrue(java.util.List.of("HEY", "YO").contains(MacroTemplate.resolve("{hey,yo|upper}", context, null).value()));
        assertTrue(MacroTemplate.resolve("{1-100|number}", context, null).success());
        assertFalse(MacroTemplate.resolve("{1-100|bogus}", context, null).success());
        assertFalse(MacroTemplate.resolve("{a,b|c,d}", context, null).success());

        MacroTemplate.Resolution variable = MacroTemplate.resolve("{a|b,c}", context, null);
        assertFalse(variable.success());
        assertTrue(variable.missing().contains("a"));
    }

    @Test
    void escapesUnchanged() {
        MacroVariableContext context = new MacroVariableContext();
        assertEquals("{1-100}", MacroTemplate.resolve("{{1-100}}", context, null).value());
        assertEquals("{hey,yo}", MacroTemplate.resolve("{{hey,yo}}", context, null).value());
        String wrapped = MacroTemplate.resolve("{{{1-100}}}", context, null).value();
        assertTrue(wrapped.matches("\\{\\d{1,3}}"));
        int inner = Integer.parseInt(wrapped.substring(1, wrapped.length() - 1));
        assertTrue(inner >= 1 && inner <= 100);
    }

    @Test
    void hasVariablesSeesBareSpecs() {
        assertTrue(MacroTemplate.hasVariables("send {1-100} coins"));
        assertTrue(MacroTemplate.hasVariables("{hey,yo}"));
        assertTrue(MacroTemplate.hasVariables("{1.5-3.5}"));
        assertFalse(MacroTemplate.hasVariables("\\d{2,4}"));
        assertFalse(MacroTemplate.hasVariables("{\"a\":1,\"b\":2}"));
        assertFalse(MacroTemplate.hasVariables("{a=1,b=2}"));
    }

    @Test
    void lastSentIsABuiltInAndTracksWhatWeSaid() {
        assertTrue(MacroTemplate.BUILT_IN_NAMES.contains("last_sent"));
        MacroVariableContext empty = new MacroVariableContext();
        autismclient.util.AutismSharedState.get().setLastSentMessage("/pay Bob 500");

        assertEquals("/pay Bob 500", MacroTemplate.resolve("{last_sent}", empty, null).value());

        assertEquals("/confirm pay Bob 500", MacroTemplate.resolve("/confirm {last_sent}", empty, null).value());
        assertEquals("said pay Bob 500", MacroTemplate.resolve("said {last_sent}", empty, null).value());

        assertEquals("/confirm /pay Bob 500", MacroTemplate.resolve("/confirm {last_sent|raw}", empty, null).value());

        autismclient.util.AutismSharedState.get().setLastSentMessage("hello there");
        assertEquals("hello there", MacroTemplate.resolve("{last_sent}", empty, null).value());
        assertEquals("say hello there", MacroTemplate.resolve("say {last_sent}", empty, null).value());

        autismclient.util.AutismSharedState.get().setLastSentMessage("/");
        assertEquals("x /", MacroTemplate.resolve("x {last_sent}", empty, null).value());

        MacroVariableContext context = new MacroVariableContext();
        context.set("last_sent", MacroValue.text("mine"));
        assertEquals("mine", MacroTemplate.resolve("{last_sent}", context, null).value());
    }

    @Test
    void anUnsetVariableIsReportedByName() {

        MacroTemplate.Resolution missing = MacroTemplate.resolve("{value}", new MacroVariableContext(), null);
        assertFalse(missing.success());
        assertEquals(java.util.List.of("value"), missing.missing());

        MacroVariableContext context = new MacroVariableContext();
        context.set("value", MacroValue.text("22600162"));
        MacroTemplate.Resolution ok = MacroTemplate.resolve("{value}", context, null);
        assertTrue(ok.success());
        assertEquals("22600162", ok.value());
    }

    @Test
    void captureNumberParserFindsEmbeddedNumbersAndKeepsDecimals() {
        assertEquals("1234", MacroTemplate.parseCaptureNumber("Money: 1,234"));
        assertEquals("1234.56", MacroTemplate.parseCaptureNumber("Balance: $1,234.56"));
        assertEquals("172000", MacroTemplate.parseCaptureNumber("Money: 172k"));
        assertEquals("12.75", MacroTemplate.parseCaptureNumber("bal 12.75 kills"));
        assertEquals("-50", MacroTemplate.parseCaptureNumber("Debt -50"));
        assertThrows(IllegalArgumentException.class, () -> MacroTemplate.parseCaptureNumber("Melonik"));

        assertEquals("100", MacroTemplate.parseCaptureNumber("Pos 100 64 200"));
        assertEquals("10", MacroTemplate.parseCaptureNumber("Time left: 10 m"));
        assertEquals("3", MacroTemplate.parseCaptureNumber("Lvl 3 t"));

        assertEquals("-50", MacroTemplate.parseCaptureNumber("-$50"));
        assertEquals("-1234.56", MacroTemplate.parseCaptureNumber("Balance: -$1,234.56"));
        assertEquals("500", MacroTemplate.parseCaptureNumber("Balance - 500"));
        assertEquals("1", MacroTemplate.parseCaptureNumber("Round 1 - 3"));

        assertEquals("0.99", MacroTemplate.parseCaptureNumber("Balance: $.99"));
        assertEquals("-0.5", MacroTemplate.parseCaptureNumber("Rate -.5"));
        assertEquals("750", MacroTemplate.parseCaptureNumber(".75k"));

        assertEquals("22600162", MacroTemplate.parseCaptureNumber("22.600.162,00"));
        assertEquals("22600162.5", MacroTemplate.parseCaptureNumber("Money: 22.600.162,50"));
        assertEquals("22600162", MacroTemplate.parseCaptureNumber("22.600.162"));
        assertEquals("1234.56", MacroTemplate.parseCaptureNumber("1.234,56"));
        assertEquals("12.5", MacroTemplate.parseCaptureNumber("12,5"));

        assertEquals("1234567.89", MacroTemplate.parseCaptureNumber("1,234,567.89"));
        assertEquals("1000000", MacroTemplate.parseCaptureNumber("1,000,000"));

        assertEquals("500", MacroTemplate.parseCaptureNumber("You have 500. Next"));

        MacroTemplate.NumberStyle whole = MacroTemplate.NumberStyle.WHOLE;
        assertEquals("1286189590", MacroTemplate.parseCaptureNumber("1.286.189.590,03", whole));
        assertEquals("22600162", MacroTemplate.parseCaptureNumber("Money: 22.600.162,00", whole));
        assertEquals("1234", MacroTemplate.parseCaptureNumber("1,234.56", whole));
        assertEquals("1286", MacroTemplate.parseCaptureNumber("1.286", whole));
        assertEquals("1286", MacroTemplate.parseCaptureNumber("1.286,5", whole));
        assertEquals("12", MacroTemplate.parseCaptureNumber("12.75", whole));
        assertEquals("0", MacroTemplate.parseCaptureNumber("$.99", whole));

        assertEquals("1200000", MacroTemplate.parseCaptureNumber("1.2M", whole));
        assertEquals("172000", MacroTemplate.parseCaptureNumber("172k", whole));

        assertEquals("1234", MacroTemplate.parseCaptureNumberStrict(" 1,234 "));
        assertEquals("2700000", MacroTemplate.parseCaptureNumberStrict("2.7M"));
        assertThrows(IllegalArgumentException.class, () -> MacroTemplate.parseCaptureNumberStrict("Melonik7"));
        assertThrows(IllegalArgumentException.class, () -> MacroTemplate.parseCaptureNumberStrict("Money: 1,234"));

        assertEquals("203000000", MacroTemplate.parseCompactNumber("203M"));
    }
}
