package autismclient.util.macro;

import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismMacroSneak;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PlaceAction implements MacroAction, WaitsForGui {
    public String itemName = "";
    public ItemTarget itemTarget = new ItemTarget();
    public BlockPos blockPos = BlockPos.ZERO;
    public Direction direction = Direction.UP;
    public boolean manualDirection = false;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public boolean waitForItem = true;
    public boolean silentSwitch = false;
    public boolean sneak = false;
    public String sneakMode = "Packet";
    public boolean interact = false;
    public InteractTiming interactTiming = InteractTiming.AFTER;
    public int interactCustomMs = 0;
    public String guiName = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (blockPos == null || mc.level.isOutsideBuildHeight(blockPos)) return;
        if (!AutismContainerTarget.isWithinBlockReach(mc, blockPos)) return;

        BlockHitResult hit = resolvePlaceHit(mc, blockPos, manualDirection, direction);
        if (hit == null) return;

        int originalSlot = mc.player.getInventory().getSelectedSlot();

        ItemTarget target = resolvedItemTarget().resolveTemplate(mc);
        if (target == null) return;
        selectItemForPlace(mc, target);

        boolean sneaking = sneak;
        if (sneaking) AutismMacroSneak.hold(mc, sneakMode);
        try {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        } finally {
            if (sneaking) AutismMacroSneak.release(mc, sneakMode);
        }

        if (silentSwitch && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            AutismInventoryHelper.selectHotbarSlot(mc, originalSlot);
        }
    }

    static BlockHitResult resolvePlaceHit(Minecraft mc, BlockPos targetPos, boolean manualDirection, Direction manualFace) {
        if (mc == null || mc.level == null || targetPos == null) return null;
        if (mc.level.isOutsideBuildHeight(targetPos)) return null;

        Direction[] order;
        if (manualDirection) {
            Direction face = manualFace == null ? Direction.UP : manualFace;
            order = new Direction[]{face};
        } else {
            order = Direction.values();
        }

        for (Direction face : order) {

            BlockPos supportPos = targetPos.relative(face.getOpposite());
            if (mc.level.isOutsideBuildHeight(supportPos)) continue;
            if (mc.level.getBlockState(supportPos).isAir()) continue;
            if (!AutismContainerTarget.isWithinBlockReach(mc, supportPos)) continue;

            if (!mc.level.getBlockState(targetPos).isAir()) continue;

            Vec3 hitPos = Vec3.atCenterOf(supportPos).add(
                    face.getStepX() * 0.5D,
                    face.getStepY() * 0.5D,
                    face.getStepZ() * 0.5D
            );
            return new BlockHitResult(hitPos, face, supportPos, false);
        }

        return null;
    }

    private BlockHitResult resolvePlaceHit(Minecraft mc) {
        return resolvePlaceHit(mc, blockPos, manualDirection, direction);
    }

    private static void selectItemForPlace(Minecraft mc, ItemTarget target) {
        if (target == null || (!target.hasSlot() && !target.hasIdentity())) return;

        if (target.hasIdentity()) {
            AutismInventoryHelper.selectHotbarItem(mc, target, mc.player.getInventory().getSelectedSlot());
            return;
        }

        int visibleSlot = target.slot;
        if (visibleSlot >= 0 && visibleSlot <= 8) {
            AutismInventoryHelper.selectHotbarSlot(mc, visibleSlot);
            return;
        }

        int handlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, visibleSlot);
        if (handlerSlot < 0) return;

        int hotbarSlot = mc.player.getInventory().getSelectedSlot();
        int hotbarHandlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, hotbarSlot);
        if (hotbarHandlerSlot >= 0) {
            AutismInventoryHelper.swapHandlerSlots(mc, handlerSlot, hotbarHandlerSlot);
        }
    }

    public void captureCurrentLookTarget(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            blockPos = blockHit.getBlockPos();
        }
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }

    @Override public MacroActionType getType() { return MacroActionType.PLACE; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getIcon() { return "P"; }

    @Override
    public String getDisplayName() {
        ItemTarget target = resolvedItemTarget();
        String item = (target.hasSlot() || target.hasIdentity()) ? target.summaryText() : "current";
        String pos = blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
        String face = manualDirection ? direction.name().toLowerCase() : "auto";
        return "Place " + item + " at " + pos + " " + face + WaitsForGui.timingLabel(this);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "PLACE");
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) tag.put("itemName", target.toTag());
        tag.putInt("blockX", blockPos.getX());
        tag.putInt("blockY", blockPos.getY());
        tag.putInt("blockZ", blockPos.getZ());
        tag.putBoolean("manualDirection", manualDirection);
        if (manualDirection) tag.putString("direction", direction.name());
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putBoolean("waitForItem", waitForItem);
        tag.putBoolean("silentSwitch", silentSwitch);
        tag.putBoolean("sneak", sneak);
        tag.putString("sneakMode", sneakMode);
        tag.putBoolean("interact", interact);
        tag.putString("interactTiming", interactTiming.name());
        tag.putInt("interactCustomMs", interactCustomMs);
        tag.putString("guiName", guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> {
            String legacyName = tag.getStringOr("itemName", "");
            if (!legacyName.isBlank()) return ItemTarget.fromLegacyEntry(legacyName);
            int legacySlot = tag.getIntOr("slot", -1);
            return legacySlot >= 0 ? ItemTarget.slotOnly(legacySlot) : new ItemTarget();
        });
        itemName = itemTarget.toLegacyEntry();
        int x = tag.getIntOr("blockX", 0);
        int y = tag.getIntOr("blockY", 0);
        int z = tag.getIntOr("blockZ", 0);
        blockPos = new BlockPos(x, y, z);
        manualDirection = tag.getBooleanOr("manualDirection", false);
        direction = manualDirection ? parseDirection(tag) : Direction.UP;
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        waitForItem = tag.getBooleanOr("waitForItem", true);
        silentSwitch = tag.getBooleanOr("silentSwitch", false);
        sneak = tag.getBooleanOr("sneak", false);
        sneakMode = tag.getStringOr("sneakMode", "Packet");
        interact = tag.getBooleanOr("interact", false);
        interactTiming = InteractTiming.parse(tag.getStringOr("interactTiming", "AFTER"), InteractTiming.AFTER);
        interactCustomMs = Math.max(-5000, Math.min(5000, tag.getIntOr("interactCustomMs", 0)));
        guiName = tag.getStringOr("guiName", "");
        enabled = tag.getBooleanOr("enabled", true);
    }

    private static Direction parseDirection(CompoundTag tag) {
        if (!tag.contains("direction")) return Direction.UP;
        String named = tag.getStringOr("direction", "");
        if (!named.isBlank()) {
            try {
                return Direction.valueOf(named.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {  }
        }
        int dirOrd = tag.getIntOr("direction", Direction.UP.ordinal());
        Direction[] values = Direction.values();
        return values[Math.max(0, Math.min(values.length - 1, dirOrd))];
    }

    public ItemTarget resolvedItemTarget() {
        if (itemTarget != null && (itemTarget.hasSlot() || itemTarget.hasIdentity())) return itemTarget;
        itemTarget = ItemTarget.fromLegacyEntry(itemName);
        return itemTarget;
    }
}
