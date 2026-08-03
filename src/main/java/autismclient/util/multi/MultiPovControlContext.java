package autismclient.util.multi;

import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

final class MultiPovControlContext {
    enum PacketDestination { MAIN, BOT }

    private final Minecraft minecraft;
    private final Player player;
    private final MultiSession botSession;
    private final PacketDestination packetDestination;

    private MultiPovControlContext(Minecraft minecraft, Player player, MultiSession botSession,
                                   PacketDestination packetDestination) {
        this.minecraft = minecraft;
        this.player = player;
        this.botSession = botSession;
        this.packetDestination = packetDestination;
    }

    static MultiPovControlContext resolve(MultiSession session, int botEntityId) {
        Minecraft mc = Minecraft.getInstance();
        if (session != null && mc.level != null
            && mc.level.getEntity(botEntityId) instanceof RemotePlayer bot
            && MultiPilot.isManualControlEntity(bot)) {
            return new MultiPovControlContext(mc, bot, session, PacketDestination.BOT);
        }
        return new MultiPovControlContext(mc, mc.player, null, PacketDestination.MAIN);
    }

    boolean controls(Entity entity) {
        return packetDestination == PacketDestination.BOT && player != null && player == entity;
    }

    Player player() {
        return player;
    }

    MultiSession botSession() {
        return botSession;
    }

    PacketDestination packetDestination() {
        return packetDestination;
    }

    int gameModeId() {
        if (botSession != null) return botSession.takeoverGameModeId();
        return minecraft.gameMode == null ? -1 : minecraft.gameMode.getPlayerMode().getId();
    }

    Inventory inventory() {
        return player == null ? null : player.getInventory();
    }

    int selectedHotbar() {
        return botSession == null
            ? player == null ? -1 : player.getInventory().getSelectedSlot()
            : botSession.selectedHotbar();
    }

    boolean send(Packet<?> packet) {
        if (packet == null) return false;
        if (botSession != null) return botSession.pilotSend(packet);
        if (minecraft.getConnection() == null) return false;
        minecraft.getConnection().send(packet);
        return true;
    }

    boolean selectHotbar(int slot) {
        int clamped = Math.max(0, Math.min(8, slot));
        if (botSession != null) return botSession.pilotSelectHotbar(clamped);
        if (player == null || minecraft.getConnection() == null) return false;
        AutismInventoryHelper.selectHotbarSlot(minecraft, clamped);
        return true;
    }

    boolean swapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
        if (botSession != null) return botSession.pilotSwapInventoryToHotbar(inventorySlot, hotbarSlot);
        return player != null && AutismInventoryHelper.swapInventorySlots(
            minecraft, inventorySlot, Math.max(0, Math.min(8, hotbarSlot)));
    }
}
