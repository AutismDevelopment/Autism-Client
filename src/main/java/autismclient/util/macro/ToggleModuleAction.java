package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismCompatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public class ToggleModuleAction implements MacroAction {
    public enum ToggleMode { TOGGLE, ENABLE, DISABLE }
    public static class ModuleEntry {
        public String moduleName = "";
        public ToggleMode toggleMode = ToggleMode.TOGGLE;

        public ModuleEntry() {
        }

        public ModuleEntry(String moduleName, ToggleMode toggleMode) {
            this.moduleName = moduleName == null ? "" : moduleName.trim();
            this.toggleMode = toggleMode == null ? ToggleMode.TOGGLE : toggleMode;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("moduleName", moduleName);
            tag.putString("toggleMode", toggleMode.name());
            return tag;
        }

        public static ModuleEntry fromTag(CompoundTag tag) {
            ModuleEntry entry = new ModuleEntry();
            if (tag.contains("moduleName")) entry.moduleName = tag.getStringOr("moduleName", "");
            if (tag.contains("toggleMode")) {
                try { entry.toggleMode = ToggleMode.valueOf(tag.getStringOr("toggleMode", "TOGGLE")); }
                catch (IllegalArgumentException ignored) { entry.toggleMode = ToggleMode.TOGGLE; }
            }
            return entry;
        }
    }

    public String moduleName = "";
    public ToggleMode toggleMode = ToggleMode.TOGGLE;
    public List<ModuleEntry> entries = new ArrayList<>();
    private boolean enabled = true;

    public ToggleModuleAction() {}

    public ToggleModuleAction(String moduleName) {
        this.moduleName = moduleName == null ? "" : moduleName.trim();
        if (!this.moduleName.isBlank()) {
            this.entries.add(new ModuleEntry(this.moduleName, this.toggleMode));
        }
    }

    @Override
    public void execute(Minecraft mc) {
        if (!entries.isEmpty()) {
            for (ModuleEntry entry : entries) {
                if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
                    AutismCompatManager.toggleMeteorModule(entry.moduleName, entry.toggleMode);
                }
            }
            return;
        }
        AutismCompatManager.toggleMeteorModule(moduleName, toggleMode);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("moduleName", moduleName);
        tag.putString("toggleMode", toggleMode.name());
        ListTag list = new ListTag();
        for (ModuleEntry entry : entries) {
            if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) list.add(entry.toTag());
        }
        tag.put("entries", list);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("moduleName")) moduleName = tag.getStringOr("moduleName", "");
        if (tag.contains("toggleMode")) {
            try { toggleMode = ToggleMode.valueOf(tag.getStringOr("toggleMode", "TOGGLE")); }
            catch (IllegalArgumentException ignored) { toggleMode = ToggleMode.TOGGLE; }
        }
        entries.clear();
        if (tag.contains("entries")) {
            ListTag list = tag.getList("entries").orElse(new ListTag());
            for (Tag element : list) {
                if (element instanceof CompoundTag compound) {
                    ModuleEntry entry = ModuleEntry.fromTag(compound);
                    if (entry.moduleName != null && !entry.moduleName.isBlank()) entries.add(entry);
                }
            }
        }
        if (entries.isEmpty() && moduleName != null && !moduleName.isBlank()) {
            entries.add(new ModuleEntry(moduleName, toggleMode));
        }
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() { return MacroActionType.TOGGLE_MODULE; }

    @Override
    public String getDisplayName() {
        if (!entries.isEmpty()) {
            return entries.size() == 1
                    ? formatMode(entries.get(0).toggleMode) + " Module (" + entries.get(0).moduleName + ")"
                    : "Module Batch (" + entries.size() + ")";
        }
        String modeStr = switch (toggleMode) {
            case ENABLE  -> "Enable";
            case DISABLE -> "Disable";
            default      -> "Toggle";
        };
        return modeStr + " Module (" + (moduleName.isEmpty() ? "None" : moduleName) + ")";
    }

    @Override
    public String getIcon() { return "M"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public void sanitizeForSharing() {
        entries.removeIf(e -> e != null && PackHideState.isHideModuleName(e.moduleName));
        if (PackHideState.isHideModuleName(moduleName)) {
            moduleName = "";
            toggleMode = ToggleMode.TOGGLE;
        }
    }

    private static String formatMode(ToggleMode mode) {
        return switch (mode) {
            case ENABLE -> "Enable";
            case DISABLE -> "Disable";
            default -> "Toggle";
        };
    }
}
