package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.List;

public class WaitForLanStepAction implements MacroAction {

    public boolean filterByUser = false;

    public int defaultStep = 1;

    public List<LanStepEntry> entries = new ArrayList<>();
    public boolean listenDuringPreviousAction = false;
    private boolean enabled = true;

    public static class LanStepEntry {
        public String username = "";
        public int step = 1;

        public LanStepEntry() {}

        public LanStepEntry(String username, int step) {
            this.username = username != null ? username : "";
            this.step = Math.max(1, step);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("username", username);
            tag.putInt("step", step);
            return tag;
        }

        public static LanStepEntry fromTag(CompoundTag tag) {
            LanStepEntry e = new LanStepEntry();
            e.username = tag.getStringOr("username", "");
            e.step = tag.getIntOr("step", 1);
            return e;
        }

        public String getDisplayName() {
            return (username.isEmpty() ? "Any" : username) + " @ step " + step;
        }
    }

    @Override public MacroActionType getType() { return MacroActionType.WAIT_LAN_STEP; }
    @Override public void execute(Minecraft mc) {  }

    @Override
    public String getDisplayName() {
        if (!filterByUser) {
            return "Wait LAN: any @ step " + defaultStep;
        }
        if (entries.isEmpty()) return "Wait LAN: any @ step " + defaultStep;
        if (entries.size() == 1) {
            LanStepEntry e = entries.get(0);
            return "Wait LAN: " + (e.username.isEmpty() ? "any" : e.username) + " @ " + e.step;
        }
        return "Wait LAN: " + entries.size() + " peers";
    }

    @Override public String getIcon() { return "LAN"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putBoolean("filterByUser", filterByUser);
        tag.putInt("defaultStep", defaultStep);
        tag.putBoolean("enabled", enabled);
        ListTag list = new ListTag();
        for (LanStepEntry entry : entries) list.add(entry.toTag());
        tag.put("entries", list);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        filterByUser = tag.getBooleanOr("filterByUser", false);
        defaultStep = tag.getIntOr("defaultStep", 1);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        entries.clear();
        if (tag.contains("entries")) {
            ListTag list = tag.getList("entries").orElse(new ListTag());
            for (Tag el : list) {
                if (el instanceof CompoundTag c) {
                    entries.add(LanStepEntry.fromTag(c));
                }
            }
        }
    }
}
