package autismclient.util.macro;

import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class UseItemPhaseAction implements MacroAction {
    public enum Phase { USE_ONCE, START_USE, RELEASE_USE, USE_BLOCK, SWING }

    public Phase phase = Phase.USE_ONCE;
    public String itemName = "";
    public String hand = "MAIN_HAND";
    public int holdTicks = 0;
    public int repeat = 1;
    public boolean gateDuringHold = false;
    public boolean gatePlayerActions = true;
    public boolean gateContainerClicks = true;
    public boolean releaseAfterHold = false;
    public boolean useCustomSlotMapping = true;
    public int swapSlotBeforeRelease = -1;
    public int swapButton = 0;
    public boolean dropSlotAfterRelease = false;
    public int dropSlot = 44;
    public String gateId = "use-phase";

    @Override
    public void execute(Minecraft mc) {

        int times = repeatTimes();
        for (int i = 0; i < times; i++) {
            sendUsePacket(mc);
            boolean gateInstalled = installGate(mc);
            try {
                for (int t = 0; t < holdTicks; t++) {
                    try { MacroConditionRegistry.waitForNextTick().get(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
                    catch (Exception ignored) {  }
                }
            } finally {
                if (gateInstalled) removeGate();
            }
            finishRelease(mc);
        }
    }

    public int repeatTimes() {
        return Math.max(1, Math.min(1000, repeat));
    }

    public boolean shouldGate() {
        return gateDuringHold && holdTicks > 0;
    }

    private InteractionHand resolveHand() {
        return "OFF_HAND".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    public void sendUsePacket(Minecraft mc) {
        if (mc == null || mc.player == null || mc.getConnection() == null) return;
        ItemTarget target = ItemTarget.fromLegacyEntry(itemName);
        if (target.hasIdentity()) autismclient.util.AutismInventoryHelper.selectHotbarItem(mc, target, mc.player.getInventory().getSelectedSlot());
        InteractionHand h = resolveHand();
        switch (phase) {
            case USE_ONCE, START_USE -> {
                if (mc.gameMode != null) mc.gameMode.useItem(mc.player, h);
                else sendRawUse(mc, h);
            }
            case RELEASE_USE -> mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            case USE_BLOCK -> {
                if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    if (mc.gameMode != null) mc.gameMode.useItemOn(mc.player, h, bhr);
                    else mc.getConnection().send(new ServerboundUseItemOnPacket(h, bhr, 0));
                } else {
                    if (mc.gameMode != null) mc.gameMode.useItem(mc.player, h);
                    else sendRawUse(mc, h);
                }
            }
            case SWING -> mc.getConnection().send(new ServerboundSwingPacket(h));
        }
    }

    private void sendRawUse(Minecraft mc, InteractionHand hand) {
        mc.getConnection().send(new ServerboundUseItemPacket(hand, 0, mc.player.getYRot(), mc.player.getXRot()));
    }

    public boolean installGate(Minecraft mc) {
        if (!shouldGate()) return false;
        PacketGateAction gate = new PacketGateAction();
        gate.gateId = gateId;
        gate.mode = PacketGateAction.GateMode.CANCEL;
        if (gatePlayerActions) gate.packetNames.add("ServerboundPlayerActionPacket");
        if (gateContainerClicks) gate.packetNames.add("ServerboundContainerClickPacket");
        gate.execute(mc);
        return true;
    }

    public void removeGate() {
        PacketGateManager.disable(gateId);
    }

    public void finishRelease(Minecraft mc) {
        if (mc == null || mc.player == null || mc.getConnection() == null) return;
        if (swapSlotBeforeRelease >= 0) clickSlot(mc, swapSlotBeforeRelease, swapButton, ContainerInput.SWAP);
        if (releaseAfterHold) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
        }
        if (dropSlotAfterRelease) {
            clickSlot(mc, dropSlot, 0, ContainerInput.THROW);
        }
    }

    private void clickSlot(Minecraft mc, int slot, int button, ContainerInput input) {
        int handlerSlot = useCustomSlotMapping ? AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, slot) : slot;
        if (handlerSlot < 0) return;
        AutismInventoryClickHelper.click(mc, handlerSlot, button, input);
    }

    @Override public MacroActionType getType() { return MacroActionType.USE_ITEM_PHASE; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "USE_ITEM_PHASE");
        tag.putString("phase", phase.name());
        tag.putString("itemName", itemName);
        tag.putString("hand", hand);
        tag.putInt("holdTicks", holdTicks);
        tag.putInt("repeat", repeat);
        tag.putBoolean("gateDuringHold", gateDuringHold);
        tag.putBoolean("gatePlayerActions", gatePlayerActions);
        tag.putBoolean("gateContainerClicks", gateContainerClicks);
        tag.putBoolean("releaseAfterHold", releaseAfterHold);
        tag.putBoolean("useCustomSlotMapping", useCustomSlotMapping);
        tag.putInt("swapSlotBeforeRelease", swapSlotBeforeRelease);
        tag.putInt("swapButton", swapButton);
        tag.putBoolean("dropSlotAfterRelease", dropSlotAfterRelease);
        tag.putInt("dropSlot", dropSlot);
        tag.putString("gateId", gateId);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        phase = MacroStringList.enumValue(Phase.class, tag.getStringOr("phase", "USE_ONCE"), Phase.USE_ONCE);
        itemName = tag.getStringOr("itemName", "");
        hand = tag.getStringOr("hand", "MAIN_HAND");
        holdTicks = tag.getIntOr("holdTicks", 0);
        repeat = tag.getIntOr("repeat", 1);
        gateDuringHold = tag.getBooleanOr("gateDuringHold", false);
        gatePlayerActions = tag.getBooleanOr("gatePlayerActions", true);
        gateContainerClicks = tag.getBooleanOr("gateContainerClicks", true);
        releaseAfterHold = tag.getBooleanOr("releaseAfterHold", false);
        useCustomSlotMapping = tag.getBooleanOr("useCustomSlotMapping", false);
        swapSlotBeforeRelease = tag.getIntOr("swapSlotBeforeRelease", -1);
        swapButton = tag.getIntOr("swapButton", 0);
        dropSlotAfterRelease = tag.getBooleanOr("dropSlotAfterRelease", false);
        dropSlot = tag.getIntOr("dropSlot", 44);
        gateId = tag.getStringOr("gateId", "use-phase");
    }

    @Override public String getDisplayName() { return "Use item " + phase + (releaseAfterHold ? " + release" : ""); }
    @Override public String getIcon() { return "U"; }
}
