package autismclient.util;

import autismclient.util.macro.MacroConditionUtil;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.ToggleModuleAction;
import autismclient.util.macro.WaitDurabilityAction;
import autismclient.util.macro.WaitFreeSlotsAction;

public final class AutoFishStopMacroFactory {
    public enum Preset {
        FREE_SLOTS,
        DURABILITY,
        CUSTOM
    }

    public static final String FREE_SLOTS_NAME = "AutoFish - Free Slots Stop";
    public static final String DURABILITY_NAME = "AutoFish - Durability Stop";
    public static final String CUSTOM_NAME = "AutoFish - Custom Stop";

    private AutoFishStopMacroFactory() {
    }

    public static AutismMacro ensurePreset(Preset preset) {
        return switch (preset == null ? Preset.CUSTOM : preset) {
            case FREE_SLOTS -> ensureMacro(FREE_SLOTS_NAME, freeSlotsCondition());
            case DURABILITY -> ensureMacro(DURABILITY_NAME, durabilityCondition());
            case CUSTOM -> ensureMacro(CUSTOM_NAME, freeSlotsCondition());
        };
    }

    public static boolean isValidStopMacro(AutismMacro macro) {
        return MacroConditionUtil.startsWithWaitCondition(macro);
    }

    public static boolean isGeneratedStopMacroName(String name) {
        if (name == null) return false;
        return generatedNameMatches(name, FREE_SLOTS_NAME)
            || generatedNameMatches(name, DURABILITY_NAME)
            || generatedNameMatches(name, CUSTOM_NAME);
    }

    public static boolean isGeneratedStopMacro(AutismMacro macro) {
        return macro != null && isGeneratedStopMacroName(macro.name);
    }

    public static Preset presetForGeneratedName(String name) {
        if (name == null) return null;
        if (generatedNameMatches(name, FREE_SLOTS_NAME)) return Preset.FREE_SLOTS;
        if (generatedNameMatches(name, DURABILITY_NAME)) return Preset.DURABILITY;
        if (generatedNameMatches(name, CUSTOM_NAME)) return Preset.CUSTOM;
        return null;
    }

    public static boolean isAutoFishToggleAction(MacroAction action) {
        return action instanceof ToggleModuleAction toggle && togglesAutoFish(toggle);
    }

    public static boolean disablesAutoFish(MacroAction action) {
        return action instanceof ToggleModuleAction toggle && disablesAutoFish(toggle);
    }

    private static AutismMacro ensureMacro(String preferredName, MacroAction firstCondition) {
        AutismMacroManager manager = AutismMacroManager.get();
        AutismMacro existing = manager.get(preferredName);
        if (existing != null) {
            boolean changed = false;
            if (!isValidStopMacro(existing)) {
                existing.actions.add(0, firstCondition);
                changed = true;
            }
            changed |= removeImmediateGeneratedAutoFishToggle(existing);
            if (changed) manager.save();
            return existing;
        }
        AutismMacro macro = new AutismMacro(manager.createUniqueName(preferredName));
        macro.description = "AutoFish stop macro";
        macro.actions.add(firstCondition);
        manager.add(macro);
        return macro;
    }

    private static boolean togglesAutoFish(ToggleModuleAction action) {
        if (action == null) return false;
        if (action.entries != null && !action.entries.isEmpty()) {
            for (ToggleModuleAction.ModuleEntry entry : action.entries) {
                if (entry != null
                    && "AutoFish".equalsIgnoreCase(entry.moduleName)) {
                    return true;
                }
            }
            return false;
        }
        return "AutoFish".equalsIgnoreCase(action.moduleName);
    }

    private static boolean disablesAutoFish(ToggleModuleAction action) {
        if (action == null) return false;
        if (action.entries != null && !action.entries.isEmpty()) {
            for (ToggleModuleAction.ModuleEntry entry : action.entries) {
                if (entry != null
                    && "AutoFish".equalsIgnoreCase(entry.moduleName)
                    && entry.toggleMode == ToggleModuleAction.ToggleMode.DISABLE) {
                    return true;
                }
            }
            return false;
        }
        return "AutoFish".equalsIgnoreCase(action.moduleName)
            && action.toggleMode == ToggleModuleAction.ToggleMode.DISABLE;
    }

    private static boolean removeImmediateGeneratedAutoFishToggle(AutismMacro macro) {
        if (macro == null || macro.actions == null) return false;
        for (int i = 0; i < macro.actions.size(); i++) {
            MacroAction action = macro.actions.get(i);
            if (action == null || !action.isEnabled() || !MacroConditionUtil.isWaitConditionAction(action)) continue;
            int next = i + 1;
            if (next < macro.actions.size() && isAutoFishToggleAction(macro.actions.get(next))) {
                macro.actions.remove(next);
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean generatedNameMatches(String name, String base) {
        if (name == null || base == null) return false;
        String trimmed = name.trim();
        return trimmed.equalsIgnoreCase(base)
            || trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(base.toLowerCase(java.util.Locale.ROOT) + " (");
    }

    private static WaitFreeSlotsAction freeSlotsCondition() {
        WaitFreeSlotsAction action = new WaitFreeSlotsAction();
        action.countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
        action.comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
        action.slots = 0;
        action.timeoutMs = 0;
        return action;
    }

    private static WaitDurabilityAction durabilityCondition() {
        WaitDurabilityAction action = new WaitDurabilityAction();
        action.targetMode = WaitDurabilityAction.TargetMode.ITEM;
        action.itemName = "minecraft:fishing_rod";
        action.measurement = WaitDurabilityAction.Measurement.REMAINING;
        action.comparison = WaitDurabilityAction.Comparison.AT_MOST;
        action.value = 2;
        action.useNext = true;
        action.timeoutMs = 0;
        return action;
    }

}
