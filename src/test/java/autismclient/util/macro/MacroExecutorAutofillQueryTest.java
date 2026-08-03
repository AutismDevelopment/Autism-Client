package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MacroExecutorAutofillQueryTest {
    @Test
    void bareCommandsGetTheArgumentSpace() {
        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("/msg", CaptureListSelector.Filter.NONE, ""));
        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("msg", CaptureListSelector.Filter.NONE, ""));
        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("/msg ", CaptureListSelector.Filter.NONE, ""));
    }

    @Test
    void prefixFilterIsPushedIntoTheQuery() {
        assertEquals("/msg a", MacroExecutor.buildAutofillQuery("msg", CaptureListSelector.Filter.PREFIX, "a"));
        assertEquals("/msg .", MacroExecutor.buildAutofillQuery("/msg ", CaptureListSelector.Filter.PREFIX, "."));
    }

    @Test
    void separatingSpaceIsAlwaysExactlyOne() {

        assertEquals("/msg a", MacroExecutor.buildAutofillQuery("msg", CaptureListSelector.Filter.PREFIX, "a"));

        assertEquals("/msg a", MacroExecutor.buildAutofillQuery("/msg ", CaptureListSelector.Filter.PREFIX, "a"));

        assertEquals("/msg a", MacroExecutor.buildAutofillQuery("/msg  ", CaptureListSelector.Filter.PREFIX, "a"));

        assertEquals("/msg a", MacroExecutor.buildAutofillQuery("/msg", CaptureListSelector.Filter.PREFIX, " a "));
    }

    @Test
    void prefixIsNotPushedWhenItCannotFormACleanArgument() {

        assertEquals("/warp na", MacroExecutor.buildAutofillQuery("/warp na", CaptureListSelector.Filter.PREFIX, "x"));

        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("/msg", CaptureListSelector.Filter.PREFIX, "a b"));

        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("/msg", CaptureListSelector.Filter.CONTAINS, "a"));
        assertEquals("/msg ", MacroExecutor.buildAutofillQuery("/msg", CaptureListSelector.Filter.PREFIX, ""));
    }
}
