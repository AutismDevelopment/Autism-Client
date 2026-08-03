package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.List;

public class MineAction implements MacroAction {
    public List<String> targetBlocks = new ArrayList<>();
    public boolean stopInventoryFull = false;
    public boolean stopSlotsUsed = false;
    public int slotsUsedThreshold = 18;
    public boolean stopMinedCount = false;
    public int minedCountTarget = 64;
    public boolean stopAfterTime = false;
    public int timeoutSeconds = 60;
    private boolean enabled = true;

    @Override public MacroActionType getType() { return MacroActionType.MINE; }
    @Override public void execute(Minecraft mc) {  }

    @Override
    public String getDisplayName() {
        if (targetBlocks.isEmpty()) return "Mine: (none)";
        String first = AutismRegistryLabels.block(targetBlocks.get(0));
        return targetBlocks.size() == 1 ? "Mine: " + first : "Mine: " + first + " (+" + (targetBlocks.size() - 1) + ")";
    }

    @Override public String getIcon() { return "MN"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        ListTag list = new ListTag();
        for (String id : targetBlocks) list.add(StringTag.valueOf(id));
        tag.put("targetBlocks", list);
        tag.putBoolean("stopInventoryFull", stopInventoryFull);
        tag.putBoolean("stopSlotsUsed", stopSlotsUsed);
        tag.putInt("slotsUsedThreshold", slotsUsedThreshold);
        tag.putBoolean("stopMinedCount", stopMinedCount);
        tag.putInt("minedCountTarget", minedCountTarget);
        tag.putBoolean("stopAfterTime", stopAfterTime);
        tag.putInt("timeoutSeconds", timeoutSeconds);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        targetBlocks.clear();
        if (tag.contains("targetBlocks")) {
            ListTag list = tag.getList("targetBlocks").orElse(new ListTag());
            for (Tag el : list) {
                String s = el.asString().orElse("");
                if (!s.isEmpty()) targetBlocks.add(s);
            }
        }
        stopInventoryFull = tag.getBooleanOr("stopInventoryFull", false);
        stopSlotsUsed = tag.getBooleanOr("stopSlotsUsed", false);
        slotsUsedThreshold = tag.getIntOr("slotsUsedThreshold", 18);
        stopMinedCount = tag.getBooleanOr("stopMinedCount", false);
        minedCountTarget = tag.getIntOr("minedCountTarget", 64);
        stopAfterTime = tag.getBooleanOr("stopAfterTime", false);
        timeoutSeconds = tag.getIntOr("timeoutSeconds", 60);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }
}
