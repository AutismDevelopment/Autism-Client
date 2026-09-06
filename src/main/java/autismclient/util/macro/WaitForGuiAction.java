package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public class WaitForGuiAction implements MacroAction {
    public enum WaitMode { OPEN, CLOSE }

    public WaitMode waitMode = WaitMode.OPEN;
    public String guiType = "ANY";
    public String guiTitle = "";
    public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
    public String saveAs = "";
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;
    private boolean enabled = true;

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_GUI;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public String getDisplayName() {
        String prefix = waitMode == WaitMode.CLOSE ? "Wait GUI Close: " : "Wait GUI Open: ";
        String type = guiType == null || guiType.isBlank() || "ANY".equals(guiType) ? "Any" : guiType;
        return prefix + type + (guiTitle.isEmpty() ? "" : " \"" + guiTitle + "\"");
    }

    @Override
    public String getIcon() {
        return "GUI";
    }

    public boolean matchesText(String search, String target) {
        if (search.isEmpty() || target.isEmpty()) return false;
        if (search.equals(target)) return true;
        if (target.toLowerCase().contains(search.toLowerCase())) return true;

        String[] searchWords = search.toLowerCase().split("\\s+");
        String[] targetWords = target.toLowerCase().split("\\s+");

        boolean allFound = true;
        for (String word : searchWords) {
            boolean found = false;
            for (String tWord : targetWords) {
                if (tWord.contains(word) || word.contains(tWord)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                allFound = false;
                break;
            }
        }
        return allFound;
    }

    public boolean checkGui(Minecraft mc) {
        return mc != null && captureGui(mc.gui.screen()).isPresent();
    }

    public java.util.Optional<java.util.Map<String, MacroValue>> captureGui(Screen screen) {
        if (screen == null) return captureCustomMenu();
        if (!MacroGuiMatcher.matches(screen, guiType, "")) return java.util.Optional.empty();
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        if (matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(guiTitle, Minecraft.getInstance());
            if (!resolved.success()) return java.util.Optional.empty();
            if (!resolved.value().isBlank() && !MacroGuiMatcher.matches(screen, guiType, resolved.value())) return java.util.Optional.empty();
            return java.util.Optional.of(guiValues(screen, title, java.util.Map.of()));
        }
        var matched = MacroCapturePattern.match(matchMode, guiTitle, title);
        if (matched.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(guiValues(screen, title, matched.get().values()));
    }

    private java.util.Optional<java.util.Map<String, MacroValue>> captureCustomMenu() {
        autismclient.api.custommenu.CustomMenuSnapshot snapshot =
            autismclient.util.custommenu.CustomMenuTracker.current();
        boolean anyType = guiType == null || guiType.isBlank() || "ANY".equalsIgnoreCase(guiType);
        if (snapshot == null || !(anyType || MacroGuiMatcher.isCustomMenuType(guiType))) {
            return java.util.Optional.empty();
        }
        String title = snapshot.title();
        java.util.Map<String, MacroValue> captures = java.util.Map.of();
        if (matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(guiTitle, Minecraft.getInstance());
            if (!resolved.success() || !resolved.value().isBlank()
                && !title.toLowerCase(java.util.Locale.ROOT).contains(resolved.value().toLowerCase(java.util.Locale.ROOT))) {
                return java.util.Optional.empty();
            }
        } else {
            var matched = MacroCapturePattern.match(matchMode, guiTitle, title);
            if (matched.isEmpty()) return java.util.Optional.empty();
            captures = matched.get().values();
        }
        java.util.Map<String, MacroValue> values = new java.util.LinkedHashMap<>(captures);
        if (saveAs != null && !saveAs.isBlank()) {
            values.put(saveAs, MacroValue.structured(MacroValue.Kind.GUI, title, java.util.Map.of(
                "title", MacroValue.text(title), "type", MacroValue.text("CUSTOM_MENU"))));
        }
        return java.util.Optional.of(values);
    }

    private java.util.Map<String, MacroValue> guiValues(Screen screen, String title, java.util.Map<String, MacroValue> captures) {
        java.util.Map<String, MacroValue> values = new java.util.LinkedHashMap<>(captures);
        if (saveAs != null && !saveAs.isBlank()) {
            java.util.Map<String, MacroValue> properties = new java.util.LinkedHashMap<>();
            properties.put("title", MacroValue.text(title));
            properties.put("type", MacroValue.text(MacroGuiMatcher.semanticName(screen)));
            values.put(saveAs, MacroValue.structured(MacroValue.Kind.GUI, title, properties));
        }
        return values;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("waitMode", waitMode.name());
        tag.putString("guiType", guiType == null ? "ANY" : guiType);
        tag.putString("guiTitle", guiTitle);
        tag.putString("matchMode", (matchMode == null ? MacroCapturePattern.Mode.MATCH : matchMode).name());
        tag.putString("saveAs", saveAs == null ? "" : saveAs);
        tag.putInt("timeoutMs", timeoutMs);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("waitMode")) {
            try { waitMode = WaitMode.valueOf(tag.getStringOr("waitMode", "OPEN")); }
            catch (IllegalArgumentException ignored) { waitMode = WaitMode.OPEN; }
        }
        if (tag.contains("guiTitle")) {
            guiTitle = tag.getStringOr("guiTitle", "");
        } else if (tag.contains("title")) {
            guiTitle = tag.getStringOr("title", "");
        }
        guiType = tag.getStringOr("guiType", "ANY");
        matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
        saveAs = tag.getStringOr("saveAs", "");
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }

    public void fromGuiTypeTag(CompoundTag tag) {
        fromTag(tag);
        guiType = tag.getStringOr("guiType", "ANY");
        guiTitle = tag.getStringOr("title", tag.getStringOr("guiTitle", ""));
        timeoutMs = tag.getIntOr("timeoutMs", 0);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
