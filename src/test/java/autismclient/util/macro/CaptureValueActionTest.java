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
    void waitingForNextTriggerIsTheDefault() {
        assertTrue(new CaptureValueAction().waitForTrigger);
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
        action.normalizeNumbers = true;
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
}
