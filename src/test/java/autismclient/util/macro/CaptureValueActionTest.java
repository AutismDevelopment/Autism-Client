package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureValueActionTest {
    @Test
    void suggestsChangingAmountWithoutPlayerLevelDigits() {
        assertEquals("872M", CaptureValueAction.suggestDynamicPart("Payed 872M to MelonikLVL10"));
    }

    @Test
    void buildsReusablePatternFromClickedExample() {
        String pattern = CaptureValueAction.buildCapturePattern(
            "Payed 872M to Melonik",
            "872M",
            "amount"
        );
        assertEquals("Payed {amount} to Melonik", pattern);

        var result = MacroCapturePattern.match(
            MacroCapturePattern.Mode.CAPTURE,
            pattern,
            "Payed 171K to Melonik"
        );
        assertTrue(result.isPresent());
        assertEquals("171K", result.orElseThrow().values().get("amount").value());

        var decimal = MacroCapturePattern.match(
            MacroCapturePattern.Mode.CAPTURE,
            pattern,
            "Payed 2.7M to Melonik"
        );
        assertTrue(decimal.isPresent());
        assertEquals("2.7M", decimal.orElseThrow().values().get("amount").value());

        var precise = MacroCapturePattern.match(
            MacroCapturePattern.Mode.CAPTURE,
            pattern,
            "Payed 175.8K to Melonik"
        );
        assertTrue(precise.isPresent());
        assertEquals("175.8K", precise.orElseThrow().values().get("amount").value());
    }

    @Test
    void invalidSelectionCannotLeaveAFalsePattern() {
        assertEquals("", CaptureValueAction.buildCapturePattern(
            "Payed 872M to Melonik",
            "999B",
            "amount"
        ));
    }

    @Test
    void namedCaptureWinsOverFullMessageUsingSameVariableName() {
        Map<String, MacroValue> outputs = CaptureValueAction.combineCapturedOutputs(
            Map.of("value", MacroValue.text("171K")),
            "value",
            MacroValue.text("Payed 171K to Melonik")
        );
        assertEquals("171K", outputs.get("value").value());
    }

    @Test
    void onlyChatWaitsByDefaultBecauseOnlyChatIsAnEvent() {

        assertTrue(CaptureValueAction.defaultWaitForTrigger(CaptureValueAction.Source.RECENT_CHAT));
        for (CaptureValueAction.Source source : CaptureValueAction.Source.values()) {
            if (source == CaptureValueAction.Source.RECENT_CHAT) continue;
            assertFalse(CaptureValueAction.defaultWaitForTrigger(source), "should read now: " + source);
        }
        assertFalse(new CaptureValueAction().waitForTrigger);

        assertFalse(loadLegacyWait("SCOREBOARD"));
        assertFalse(loadLegacyWait("HELD_ITEM"));
        assertTrue(loadLegacyWait("RECENT_CHAT"));

        CaptureValueAction waiting = new CaptureValueAction();
        waiting.source = CaptureValueAction.Source.SCOREBOARD;
        waiting.waitForTrigger = true;
        CaptureValueAction reloaded = new CaptureValueAction();
        reloaded.fromTag(waiting.toTag());
        assertTrue(reloaded.waitForTrigger);
    }

    private static boolean loadLegacyWait(String source) {
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("type", "CAPTURE_VALUE");
        legacy.putString("source", source);
        CaptureValueAction restored = new CaptureValueAction();
        restored.fromTag(legacy);
        return restored.waitForTrigger;
    }

    @Test
    void normalizesCompactCapturedAmountsWithoutChangingText() {
        Map<String, MacroValue> normalized = CaptureValueAction.normalizeCapturedOutputs(Map.of(
            "thousands", MacroValue.text("162K"),
            "decimal", MacroValue.text("271.8K"),
            "millions", MacroValue.text("2.7M"),
            "player", MacroValue.text("Melonik")
        ));

        assertEquals("162000", normalized.get("thousands").value());
        assertEquals("271800", normalized.get("decimal").value());
        assertEquals("2700000", normalized.get("millions").value());
        assertEquals("Melonik", normalized.get("player").value());
    }

    @Test
    void editorExamplePreviewShowsTheCapturedPartInsteadOfLiveChatState() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.RECENT_CHAT;
        action.saveAs = "amount";
        action.exampleText = "Payed 172k to Melonik";
        action.selectedText = "172k";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(action.exampleText, action.selectedText, action.saveAs);

        assertEquals("172k", action.previewExample().value("amount"));
        action.numberMode = CaptureValueAction.NumberMode.SUFFIX_KMB;
        assertEquals("172000", action.previewExample().value("amount"));
    }

    @Test
    void appliesNumberMathToCompactCaptures() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "amount";
        action.exampleText = "Payed 271.8K to Melonik";
        action.selectedText = "271.8K";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 2;

        assertEquals("135900", action.previewExample().value("amount"));

        action.exampleText = "Payed 69k to Melonik";
        action.selectedText = "69k";
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.MULTIPLY;
        action.numberModifierAmount = 2;
        assertEquals("138000", action.previewExample().value("amount"));

        action.numberModifier = CaptureValueAction.NumberModifier.PLUS;
        action.numberModifierAmount = 1000;
        assertEquals("70000", action.previewExample().value("amount"));

        action.numberModifier = CaptureValueAction.NumberModifier.MINUS;
        action.numberModifierAmount = 9000;
        assertEquals("60000", action.previewExample().value("amount"));
    }

    @Test
    void rejectsDivisionByZero() {
        CaptureValueAction action = new CaptureValueAction();
        action.exampleText = "Money: 69k";
        action.selectedText = "69k";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 0;

        assertFalse(action.previewExample().success());
        assertEquals("Cannot divide by zero", action.previewExample().message());
    }

    @Test
    void listCaptureFieldsRoundTripThroughNbt() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.COMMAND_AUTOFILL;
        action.autofillCommand = "/msg ";
        action.autofillTimeoutMs = 1234;
        action.autofillCacheList = true;
        action.listSelection = CaptureListSelector.Selection.POSITION;
        action.listFilter = CaptureListSelector.Filter.SUFFIX;
        action.listFilterText = ".";
        action.listExcludeText = "Steve, Admin";
        action.listPickPosition = 7;
        action.listStripPrefix = true;
        action.excludeSelf = false;

        CaptureValueAction restored = new CaptureValueAction();
        restored.fromTag(action.toTag());

        assertEquals(CaptureValueAction.Source.COMMAND_AUTOFILL, restored.source);
        assertEquals("/msg ", restored.autofillCommand);
        assertEquals(1234, restored.autofillTimeoutMs);
        assertTrue(restored.autofillCacheList);
        assertEquals(CaptureListSelector.Selection.POSITION, restored.listSelection);
        assertEquals(CaptureListSelector.Filter.SUFFIX, restored.listFilter);
        assertEquals(".", restored.listFilterText);
        assertEquals("Steve, Admin", restored.listExcludeText);
        assertEquals(7, restored.listPickPosition);
        assertTrue(restored.listStripPrefix);
        assertFalse(restored.excludeSelf);
    }

    @Test
    void oldSavedTagsWithoutListKeysLoadWithDefaults() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("type", "CAPTURE_VALUE");
        tag.putString("source", "RECENT_CHAT");

        CaptureValueAction action = new CaptureValueAction();
        action.fromTag(tag);

        assertEquals("", action.autofillCommand);
        assertEquals(5000, action.autofillTimeoutMs);
        assertTrue(action.autofillCacheList);
        assertEquals(CaptureListSelector.Selection.RANDOM, action.listSelection);
        assertEquals(CaptureListSelector.Filter.NONE, action.listFilter);
        assertEquals("", action.listFilterText);
        assertEquals("", action.listExcludeText);
        assertEquals(1, action.listPickPosition);
        assertFalse(action.listStripPrefix);
        assertTrue(action.excludeSelf);
    }

    @Test
    void prefixFilterWorksOnLoadedMacroConfig() {

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("type", "CAPTURE_VALUE");
        tag.putString("source", "COMMAND_AUTOFILL");
        tag.putString("saveAs", "target");
        tag.putString("listFilter", "PREFIX");
        tag.putString("listFilterText", ".");
        tag.putString("listSelection", "RANDOM");
        CaptureValueAction action = new CaptureValueAction();
        action.fromTag(tag);

        CaptureValueAction.Preview preview = action.previewSuggestions(
            java.util.List.of("Steve", ".BedrockKid", "Alex", ".BedrockPro"), "/msg ");
        assertTrue(preview.success());
        assertTrue(preview.value("target").startsWith("."));
        assertEquals("2", preview.values().get("target").property("count").orElseThrow().value());
    }

    @Test
    void excludeListAndStripPrefixApplyToPicks() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.COMMAND_AUTOFILL;
        action.saveAs = "target";
        action.listFilter = CaptureListSelector.Filter.PREFIX;
        action.listFilterText = ".";
        action.listExcludeText = "kid";
        action.listStripPrefix = true;
        action.listSelection = CaptureListSelector.Selection.FIRST;

        CaptureValueAction.Preview preview = action.previewSuggestions(
            java.util.List.of("Steve", ".BedrockKid", ".BedrockPro"), "/msg ");

        assertTrue(preview.success());

        assertEquals("BedrockPro", preview.value("target"));
        MacroValue captured = preview.values().get("target");
        assertEquals("1", captured.property("count").orElseThrow().value());
        assertEquals(".BedrockPro", captured.property("list").orElseThrow().value());
    }

    @Test
    void listSourcePicksFilteredSuggestionAndExposesProperties() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.COMMAND_AUTOFILL;
        action.saveAs = "target";
        action.listFilter = CaptureListSelector.Filter.PREFIX;
        action.listFilterText = ".";

        CaptureValueAction.Preview preview = action.previewSuggestions(
            java.util.List.of("Steve", ".BedrockKid", ".BedrockPro"), "/msg ");

        assertTrue(preview.success());

        assertEquals(".BedrockKid", preview.value("target"));
        MacroValue captured = preview.values().get("target");
        assertEquals("1", captured.property("index").orElseThrow().value());
        assertEquals("2", captured.property("count").orElseThrow().value());
        assertEquals("3", captured.property("total").orElseThrow().value());

        assertEquals("/msg", captured.property("query").orElseThrow().value());
    }

    @Test
    void stalePatternAndModifierFromAnotherSourceDoNotAffectListCaptures() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.TABLIST;
        action.saveAs = "target";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = "Payed {amount} to Melonik";
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 0;

        assertEquals("", action.numberModifierError());
        action.source = CaptureValueAction.Source.COMMAND_AUTOFILL;
        CaptureValueAction.Preview preview = action.previewSuggestions(java.util.List.of("Steve"), "/msg ");
        assertTrue(preview.success());
        assertEquals("Steve", preview.value("target"));
    }

    @Test
    void scoreboardCaptureWithModifierKeepsPropertiesAndDivides() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 2;

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("objowner", 0, "obj", "Stats",
                "owner", "Money", "172k", "Money: 172k"));

        assertTrue(preview.success());
        assertEquals("86000", preview.value("money"));
        MacroValue captured = preview.values().get("money");
        assertEquals("172k", captured.property("score").orElseThrow().value());
        assertEquals("1", captured.property("row").orElseThrow().value());
    }

    @Test
    void scoreboardCaptureWithoutModifierIsUntouched() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("k", 2, "obj", "Stats", "o", "Money", "172k", "Money: 172k"));

        assertTrue(preview.success());
        assertEquals("Money: 172k", preview.value("money"));
    }

    @Test
    void numberModeReachesStructuredScoreboardCaptures() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberMode = CaptureValueAction.NumberMode.SUFFIX_KMB;

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("k", 0, "obj", "Stats", "o", "Money", "1,234", "Money: 1,234"));

        assertTrue(preview.success());
        assertEquals("1234", preview.value("money"));
    }

    @Test
    void chatCaptureWithModifierNoLongerFailsTheWholeCapture() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.RECENT_CHAT;
        action.saveAs = "amount";
        action.numberModifier = CaptureValueAction.NumberModifier.MULTIPLY;
        action.numberModifierAmount = 2;

        CaptureValueAction.Preview preview = action.previewChat(new MacroExecutor.RecentChatMessage(
            "Server", "Balance: 1,000", "Balance: 1,000", null, MacroExecutor.ChatSource.SERVER, 0L));

        assertTrue(preview.success());
        assertEquals("2000", preview.value("amount"));
        assertEquals("Server", preview.values().get("amount").property("sender").orElseThrow().value());
    }

    @Test
    void divideRoundsToUsableLength() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "amount";
        action.exampleText = "Payed 1000000 to Melonik";
        action.selectedText = "1000000";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 3;

        assertEquals("333333.33", action.previewExample().value("amount"));
    }

    @Test
    void divideKeepsCapturedDecimals() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "amount";
        action.exampleText = "Balance 12.75";
        action.selectedText = "12.75";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 2;

        assertEquals("6.375", action.previewExample().value("amount"));
    }

    @Test
    void nonNumericCaptureStillReportsTheModifierError() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "who";
        action.exampleText = "Welcome Melonik";
        action.selectedText = "Melonik";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.MULTIPLY;
        action.numberModifierAmount = 2;

        assertFalse(action.previewExample().success());
        assertEquals("Not a number", action.previewExample().message());
    }

    @Test
    void capturedScoreboardMoneySubstitutesIntoAMessage() {

        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 2;

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("k", 0, "obj", "Stats", "o", "Money", "$1,000", "Money: $1,000"));
        assertTrue(preview.success());

        MacroVariableContext context = new MacroVariableContext();
        context.setAll(preview.values());
        MacroTemplate.Resolution resolved = MacroTemplate.resolve("/pay Bob {money} of {money|score}", context, null);

        assertTrue(resolved.success());
        assertEquals("/pay Bob 500 of $1,000", resolved.value());
    }

    @Test
    void percentModifiersApplyATaxRate() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 0.2;

        CaptureValueAction.ScoreboardLine line = new CaptureValueAction.ScoreboardLine(
            "k", 0, "obj", "Stats", "o", "Money", "1,000,000", "Money: 1,000,000");

        assertEquals("998000", action.previewScoreboardLine(line).value("money"));

        action.numberModifier = CaptureValueAction.NumberModifier.PLUS_PERCENT;
        assertEquals("1002000", action.previewScoreboardLine(line).value("money"));

        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 7.5;
        assertEquals("925000", action.previewScoreboardLine(line).value("money"));
    }

    @Test
    void europeanFormattedScoreboardMoneySurvivesAModifier() {

        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        CaptureValueAction.ScoreboardLine line = new CaptureValueAction.ScoreboardLine(
            "k", 0, "obj", "Stats", "o", "Money", "22.600.162,00", "Money: 22.600.162,00");

        action.numberMode = CaptureValueAction.NumberMode.SUFFIX_KMB;
        assertEquals("22600162", action.previewScoreboardLine(line).value("money"));

        action.numberMode = CaptureValueAction.NumberMode.OFF;
        action.numberModifier = CaptureValueAction.NumberModifier.MULTIPLY;
        action.numberModifierAmount = 2;
        assertEquals("45200324", action.previewScoreboardLine(line).value("money"));

        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 0.2;
        assertEquals("22554961.68", action.previewScoreboardLine(line).value("money"));
    }

    @Test
    void numberModeOffIsTheDefaultAndLeavesTheTextAlone() {
        CaptureValueAction action = new CaptureValueAction();
        assertEquals(CaptureValueAction.NumberMode.OFF, action.numberMode);
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("k", 0, "obj", "Stats", "o", "Money", "1.286.189.590,03",
                "1.286.189.590,03"));

        assertTrue(preview.success());
        assertEquals("1.286.189.590,03", preview.value("money"));
    }

    @Test
    void dropCentsModeKeepsTheArithmeticWhole() {

        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberMode = CaptureValueAction.NumberMode.DROP_CENTS;
        CaptureValueAction.ScoreboardLine line = new CaptureValueAction.ScoreboardLine(
            "k", 0, "obj", "Stats", "o", "Money", "1.286.189.590,03", "Money: 1.286.189.590,03");

        assertEquals("1286189590", action.previewScoreboardLine(line).value("money"));

        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 10;
        assertEquals("1157570631", action.previewScoreboardLine(line).value("money"));
    }

    @Test
    void suffixModeReadsCompactAmountsInEitherMode() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        CaptureValueAction.ScoreboardLine line = new CaptureValueAction.ScoreboardLine(
            "k", 0, "obj", "Stats", "o", "Money", "1.2M", "Money: 1.2M");

        action.numberMode = CaptureValueAction.NumberMode.SUFFIX_KMB;
        assertEquals("1200000", action.previewScoreboardLine(line).value("money"));

        action.numberMode = CaptureValueAction.NumberMode.DROP_CENTS;
        assertEquals("1200000", action.previewScoreboardLine(line).value("money"));
    }

    @Test
    void numberModeRoundTripsAndMigratesTheOldToggle() {
        CaptureValueAction action = new CaptureValueAction();
        action.numberMode = CaptureValueAction.NumberMode.DROP_CENTS;
        CaptureValueAction restored = new CaptureValueAction();
        restored.fromTag(action.toTag());
        assertEquals(CaptureValueAction.NumberMode.DROP_CENTS, restored.numberMode);

        net.minecraft.nbt.CompoundTag legacyOn = new net.minecraft.nbt.CompoundTag();
        legacyOn.putString("type", "CAPTURE_VALUE");
        legacyOn.putBoolean("normalizeNumbers", true);
        CaptureValueAction migrated = new CaptureValueAction();
        migrated.fromTag(legacyOn);
        assertEquals(CaptureValueAction.NumberMode.SUFFIX_KMB, migrated.numberMode);

        net.minecraft.nbt.CompoundTag legacyOff = new net.minecraft.nbt.CompoundTag();
        legacyOff.putString("type", "CAPTURE_VALUE");
        CaptureValueAction untouched = new CaptureValueAction();
        untouched.fromTag(legacyOff);
        assertEquals(CaptureValueAction.NumberMode.OFF, untouched.numberMode);
    }

    @Test
    void percentOfZeroLeavesTheValueAlone() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "amount";
        action.exampleText = "Balance 1234.56";
        action.selectedText = "1234.56";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = CaptureValueAction.buildCapturePattern(
            action.exampleText, action.selectedText, action.saveAs);
        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 0;

        assertEquals("1234.56", action.previewExample().value("amount"));
    }

    @Test
    void percentModifierRoundTripsThroughNbt() {
        CaptureValueAction action = new CaptureValueAction();
        action.numberModifier = CaptureValueAction.NumberModifier.MINUS_PERCENT;
        action.numberModifierAmount = 0.2;

        CaptureValueAction restored = new CaptureValueAction();
        restored.fromTag(action.toTag());

        assertEquals(CaptureValueAction.NumberModifier.MINUS_PERCENT, restored.numberModifier);
        assertEquals(0.2, restored.numberModifierAmount);
    }

    @Test
    void dividingByALargeAmountKeepsSignificantDigits() {
        CaptureValueAction action = new CaptureValueAction();
        action.source = CaptureValueAction.Source.SCOREBOARD;
        action.saveAs = "money";
        action.numberModifier = CaptureValueAction.NumberModifier.DIVIDE;
        action.numberModifierAmount = 1000;

        CaptureValueAction.Preview preview = action.previewScoreboardLine(
            new CaptureValueAction.ScoreboardLine("k", 0, "obj", "Stats", "o", "Coins", "1", "Coins: 1"));

        assertTrue(preview.success());
        assertEquals("0.001", preview.value("money"));
    }

    @Test
    void siblingPatternGroupsAreNotTurnedIntoNumbers() {
        CaptureValueAction action = new CaptureValueAction();
        action.saveAs = "amount";
        action.matchMode = MacroCapturePattern.Mode.CAPTURE;
        action.pattern = "Payed {amount} to {who}";
        action.exampleText = "Payed 1,000 to Melonik7";
        action.numberModifier = CaptureValueAction.NumberModifier.MULTIPLY;
        action.numberModifierAmount = 2;

        CaptureValueAction.Preview preview = action.previewExample();

        assertTrue(preview.success());
        assertEquals("2000", preview.value("amount"));

        assertEquals("Melonik7", preview.value("who"));
    }

    @Test
    void normalizeLeavesUnnamedSiblingsAlone() {
        Map<String, MacroValue> normalized = CaptureValueAction.normalizeCapturedOutputs(Map.of(
            "amount", MacroValue.text("Balance: 1,234"),
            "who", MacroValue.text("Melonik7")
        ), "amount");

        assertEquals("1234", normalized.get("amount").value());
        assertEquals("Melonik7", normalized.get("who").value());
    }

    @Test
    void invalidVariableNameDoesNotSilentlyBecomeValue() {
        assertEquals("", CaptureValueAction.buildCapturePattern("Payed 872M to Melonik", "872M", "my money"));
    }
}
