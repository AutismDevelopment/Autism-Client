package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;

public class WaitGamemodeChangeAction implements MacroAction, MacroCaptureOutput {
    public enum Match {
        ANY_CHANGE,
        TO_MODE
    }

    public enum TargetMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }

    public Match match = Match.ANY_CHANGE;
    public TargetMode gameMode = TargetMode.SURVIVAL;
    public boolean detectFake = false;
    public boolean listenDuringPreviousAction = false;
    public int timeoutMs = 0;
    public String saveAs = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("match", match.name());
        tag.putString("gameMode", gameMode.name());
        tag.putBoolean("detectFake", detectFake);
        tag.putInt("timeoutMs", Math.max(0, timeoutMs));
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        match = MacroStringList.enumValue(Match.class, tag.getStringOr("match", Match.ANY_CHANGE.name()), Match.ANY_CHANGE);
        gameMode = MacroStringList.enumValue(TargetMode.class, tag.getStringOr("gameMode", TargetMode.SURVIVAL.name()), TargetMode.SURVIVAL);
        detectFake = tag.getBooleanOr("detectFake", false);
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_GAMEMODE_CHANGE;
    }

    @Override
    public String getDisplayName() {
        String suffix = match == Match.TO_MODE ? " -> " + displayTarget() : " Change";
        return "Wait Gamemode" + suffix + (detectFake ? " (fake ok)" : "");
    }

    @Override
    public String getIcon() {
        return "GM?";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }

    public boolean accepts(GameType mode) {
        if (mode == null) return false;
        return match == Match.ANY_CHANGE || mode == targetGameType();
    }

    public GameType targetGameType() {
        return switch (gameMode) {
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
            case SURVIVAL -> GameType.SURVIVAL;
        };
    }

    private String displayTarget() {
        String lower = gameMode.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
