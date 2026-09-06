package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class WaitForBlockAction implements MacroAction {
    public enum CheckMode { AT_POSITION, IN_REACH, LOOKING_AT }
    public enum WaitBehavior { PLACED, DESTROYED }

    public CheckMode checkMode = CheckMode.AT_POSITION;
    public WaitBehavior waitBehavior = WaitBehavior.PLACED;

    public boolean anyBlock = true;

    public List<String> blockIds = new ArrayList<>();

    public BlockPos blockPos = BlockPos.ZERO;

    public boolean mustBeInReach = false;

    public double searchRadius = 8.0;
    public boolean listenDuringPreviousAction = false;
    private boolean enabled = true;

    @Override public MacroActionType getType() { return MacroActionType.WAIT_BLOCK; }
    @Override public void execute(Minecraft mc) {}

    @Override
    public String getDisplayName() {
        String blockDesc;
        if (anyBlock) {
            blockDesc = waitBehavior == WaitBehavior.DESTROYED ? "any block gone" : "any block";
        } else if (blockIds.isEmpty()) {
            blockDesc = "no blocks set";
        } else {
            blockDesc = AutismRegistryLabels.block(blockIds.get(0));
            if (blockIds.size() > 1) blockDesc += " (+" + (blockIds.size() - 1) + ")";
        }
        String verb = waitBehavior == WaitBehavior.DESTROYED ? "Destroyed" : "Placed";
        return switch (checkMode) {
            case AT_POSITION -> "Wait " + verb + " @ " + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
            case IN_REACH    -> "Wait " + verb + ": " + blockDesc + " nearby";
            case LOOKING_AT  -> "Wait " + verb + ": look at " + blockDesc;
        };
    }

    @Override public String getIcon() { return "Blk"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("checkMode", checkMode.name());
        tag.putString("waitBehavior", waitBehavior.name());
        tag.putBoolean("anyBlock", anyBlock);
        tag.putInt("x", blockPos.getX());
        tag.putInt("y", blockPos.getY());
        tag.putInt("z", blockPos.getZ());
        tag.putBoolean("mustBeInReach", mustBeInReach);
        tag.putDouble("searchRadius", searchRadius);
        ListTag list = new ListTag();
        for (String id : blockIds) list.add(StringTag.valueOf(id));
        tag.put("blockIds", list);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("checkMode")) {
            try { checkMode = CheckMode.valueOf(tag.getStringOr("checkMode", "AT_POSITION")); }
            catch (IllegalArgumentException ignored) { checkMode = CheckMode.AT_POSITION; }
        }
        if (tag.contains("waitBehavior")) {
            try { waitBehavior = WaitBehavior.valueOf(tag.getStringOr("waitBehavior", "PLACED")); }
            catch (IllegalArgumentException ignored) { waitBehavior = WaitBehavior.PLACED; }
        }
        if (tag.contains("anyBlock")) anyBlock = tag.getBooleanOr("anyBlock", true);
        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            blockPos = new BlockPos(
                tag.getIntOr("x", 0),
                tag.getIntOr("y", 0),
                tag.getIntOr("z", 0)
            );
        }
        mustBeInReach = tag.getBooleanOr("mustBeInReach", false);
        searchRadius = tag.getDoubleOr("searchRadius", 8.0);
        blockIds.clear();
        if (tag.contains("blockIds")) {
            ListTag list = tag.getList("blockIds").orElse(new ListTag());
            for (Tag el : list) {
                String s = el.asString().orElse("");
                if (!s.isEmpty()) blockIds.add(s);
            }
        } else if (tag.contains("blocks")) {

            ListTag list = tag.getList("blocks").orElse(new ListTag());
            for (Tag el : list) {
                String s = el.asString().orElse("");
                if (!s.isEmpty()) blockIds.add(s);
            }
        }
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }
}
