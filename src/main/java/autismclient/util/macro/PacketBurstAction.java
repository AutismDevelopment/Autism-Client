package autismclient.util.macro;

import autismclient.modules.PackHideState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec3;

public class PacketBurstAction implements MacroAction {
    public enum BurstMode { CONTAINER_CLICK, ENTITY_INTERACT, CLIENT_COMMAND, BUNDLE_SELECT, USE_ITEM, RELEASE_ITEM, SET_CARRIED_ITEM, CLIENT_INFORMATION, CLOSE_CONTAINER }

    public BurstMode mode = BurstMode.CONTAINER_CLICK;
    public int count = 1;
    public int delayTicks = 0;
    public int slot = 0;
    public int button = 0;
    public String containerInput = "PICKUP";
    public int entityId = -1;
    public String hand = "MAIN_HAND";
    public String clientCommand = "REQUEST_STATS";
    public String playerCommand = "OPEN_INVENTORY";
    public int bundleIndex = 0;
    public int carriedSlot = 0;
    public int containerId = 0;
    public boolean flushBefore = false;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc == null || mc.getConnection() == null) return;
        if (flushBefore) autismclient.util.AutismSharedState.get().flushDelayedPackets(mc.getConnection());
        int times = effectiveCount();
        for (int i = 0; i < times; i++) {
            if (PackHideState.isHardLocked()) return;
            sendOne(mc);
        }
    }

    int effectiveCount() {
        return Math.max(1, Math.min(10000, count));
    }

    boolean sendOne(Minecraft mc) {
        if (PackHideState.isHardLocked() || mc == null || mc.getConnection() == null) return false;
        Packet<?> packet = createPacket(mc);
        if (packet == null) return false;
        mc.getConnection().send(packet);
        return true;
    }

    private Packet<?> createPacket(Minecraft mc) {
        return switch (mode) {
            case CONTAINER_CLICK -> new ServerboundContainerClickPacket(
                mc.player == null || mc.player.containerMenu == null ? 0 : mc.player.containerMenu.containerId,
                mc.player == null || mc.player.containerMenu == null ? 0 : mc.player.containerMenu.getStateId(),
                (short) slot,
                (byte) button,
                input(),
                new Int2ObjectOpenHashMap<>(),
                HashedStack.EMPTY
            );
            case ENTITY_INTERACT -> {
                int id = entityId;
                if (id < 0 && mc.crosshairPickEntity != null) id = mc.crosshairPickEntity.getId();
                yield id < 0 ? null : new ServerboundInteractPacket(id, interactionHand(), Vec3.ZERO, false);
            }
            case CLIENT_COMMAND -> mc.player == null ? null : new ServerboundPlayerCommandPacket(mc.player, MacroStringList.enumValue(ServerboundPlayerCommandPacket.Action.class, playerCommand, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            case BUNDLE_SELECT -> new ServerboundSelectBundleItemPacket(slot >= 0 ? slot : 36 + (mc.player == null ? 0 : mc.player.getInventory().getSelectedSlot()), bundleIndex);
            case USE_ITEM -> new ServerboundUseItemPacket(interactionHand(), 0, mc.player == null ? 0 : mc.player.getYRot(), mc.player == null ? 0 : mc.player.getXRot());
            case RELEASE_ITEM -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN);
            case SET_CARRIED_ITEM -> new ServerboundSetCarriedItemPacket(Math.max(0, Math.min(8, carriedSlot)));
            case CLIENT_INFORMATION -> new ServerboundClientInformationPacket(ClientInformation.createDefault());
            case CLOSE_CONTAINER -> new ServerboundContainerClosePacket(containerId);
        };
    }

    private ContainerInput input() {
        return MacroStringList.enumValue(ContainerInput.class, containerInput, ContainerInput.PICKUP);
    }

    private InteractionHand interactionHand() {
        return "OFF_HAND".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override public MacroActionType getType() { return MacroActionType.PACKET_BURST; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "PACKET_BURST");
        tag.putString("mode", mode.name());
        tag.putInt("count", count);
        tag.putInt("delayTicks", delayTicks);
        tag.putInt("slot", slot);
        tag.putInt("button", button);
        tag.putString("containerInput", containerInput);
        tag.putInt("entityId", entityId);
        tag.putString("hand", hand);
        tag.putString("clientCommand", clientCommand);
        tag.putString("playerCommand", playerCommand);
        tag.putInt("bundleIndex", bundleIndex);
        tag.putInt("carriedSlot", carriedSlot);
        tag.putInt("containerId", containerId);
        tag.putBoolean("flushBefore", flushBefore);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        mode = MacroStringList.enumValue(BurstMode.class, tag.getStringOr("mode", "CONTAINER_CLICK"), BurstMode.CONTAINER_CLICK);
        count = tag.getIntOr("count", 1);
        delayTicks = tag.getIntOr("delayTicks", 0);
        slot = tag.getIntOr("slot", 0);
        button = tag.getIntOr("button", 0);
        containerInput = tag.getStringOr("containerInput", "PICKUP");
        entityId = tag.getIntOr("entityId", -1);
        hand = tag.getStringOr("hand", "MAIN_HAND");
        clientCommand = tag.getStringOr("clientCommand", "REQUEST_STATS");
        playerCommand = tag.getStringOr("playerCommand", "OPEN_INVENTORY");
        bundleIndex = tag.getIntOr("bundleIndex", 0);
        carriedSlot = tag.getIntOr("carriedSlot", 0);
        containerId = tag.getIntOr("containerId", 0);
        flushBefore = tag.getBooleanOr("flushBefore", false);
    }

    @Override public String getDisplayName() { return "Packet burst " + mode + " x" + count; }
    @Override public String getIcon() { return "B"; }
}
