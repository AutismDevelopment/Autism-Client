package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.List;

public class WaitForSoundAction implements MacroAction, WaitsForGui, MacroCaptureOutput {
    public List<String> soundIds = new ArrayList<>();

    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String waitGuiName = "";

    public boolean checkDistance = false;
    public double maxDistance = 16.0;
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    public transient volatile String matchedSoundId = "";
    public transient volatile double matchedX;
    public transient volatile double matchedY;
    public transient volatile double matchedZ;
    private boolean enabled = true;

    @Override public MacroActionType getType() { return MacroActionType.WAIT_SOUND; }
    @Override public void execute(Minecraft mc) {  }

    @Override
    public String getDisplayName() {
        if (soundIds.isEmpty()) return "Wait Sound: Any" + WaitsForGui.timingLabel(this);
        String first = AutismRegistryLabels.sound(soundIds.get(0));
        String s = soundIds.size() == 1 ? first : first + " (+" + (soundIds.size() - 1) + ")";
        return "Wait Sound: " + s + WaitsForGui.timingLabel(this);
    }

    @Override public String getIcon() { return "SND"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return waitGuiName; }
    @Override public void setWaitGuiName(String name) { waitGuiName = name; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        ListTag list = new ListTag();
        for (String id : soundIds) list.add(StringTag.valueOf(id));
        tag.put("soundIds", list);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("waitGuiName", waitGuiName);
        tag.putBoolean("checkDistance", checkDistance);
        tag.putDouble("maxDistance", maxDistance);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        soundIds.clear();
        if (tag.contains("soundIds")) {
            ListTag list = tag.getList("soundIds").orElse(new ListTag());
            for (Tag el : list) {
                String s = el.asString().orElse("");
                if (!s.isEmpty()) soundIds.add(s);
            }
        }
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        waitGuiName = tag.getStringOr("waitGuiName", "");
        checkDistance = tag.getBooleanOr("checkDistance", false);
        maxDistance = tag.getDoubleOr("maxDistance", 16.0);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    public void recordMatch(String soundId, double x, double y, double z) {
        matchedSoundId = soundId == null ? "" : soundId;
        matchedX = x;
        matchedY = y;
        matchedZ = z;
    }

    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
