package autismclient.util.macro;

import autismclient.util.AutismMacro;

public final class MacroConditionUtil {
    private MacroConditionUtil() {
    }

    public static boolean isWaitConditionAction(MacroAction action) {
        return action != null
            && !(action instanceof DelayAction)
            && RaceAction.isConditionAction(action);
    }

    public static boolean startsWithWaitCondition(AutismMacro macro) {
        if (macro == null || macro.actions == null) return false;
        for (MacroAction action : macro.actions) {
            if (action == null || !action.isEnabled()) continue;
            return isWaitConditionAction(action);
        }
        return false;
    }
}
