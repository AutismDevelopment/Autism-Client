package autismclient.util.mm;

import autismclient.util.AutismClipboardHelper;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroAction;
import autismclient.util.mm.msg.MmMessages;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;

public final class MmShare {
    private MmShare() {}

    public static MmMessages.MacroOffer buildMacroOffer(AutismMacro macro) {
        if (macro == null) return null;

        AutismMacro stripped = macro.deepCopy();
        stripped.name = "";
        stripped.keyCode = -1;
        String hash = AutismClipboardHelper.serializeMacroToBase64(stripped);
        if (hash == null) return null;
        MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
        offer.macroName = "";
        List<MacroAction> actions = macro.actions;
        offer.actionCount = actions == null ? 0 : actions.size();
        if (offer.actionCount == 1) {
            MacroAction only = actions.get(0);
            offer.singleActionLabel = only == null ? "" : safe(only.getDisplayName());
        }
        offer.hash = hash;
        return offer;
    }

    public static MmMessages.PacketOffer buildPacketOffer(List<AutismSharedState.QueuedPacket> queue, String friendlyName) {
        if (queue == null || queue.isEmpty()) return null;
        String data = AutismClipboardHelper.serializeQueueToBase64(queue);
        if (data == null) return null;
        MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
        int n = queue.size();
        offer.friendlyName = (friendlyName == null || friendlyName.isBlank())
            ? (n + (n == 1 ? " packet" : " packets")) : friendlyName;
        offer.direction = "C2S";
        offer.data = data;
        return offer;
    }

    public static List<AutismSharedState.QueuedPacket> queueFromOffer(MmMessages.PacketOffer offer) {
        if (offer == null || offer.data == null) return null;
        return AutismClipboardHelper.deserializeQueueFromBase64(offer.data);
    }

    public static int addToQueue(MmMessages.PacketOffer offer) {
        List<AutismSharedState.QueuedPacket> add = queueFromOffer(offer);
        if (add == null || add.isEmpty()) return -1;
        List<AutismSharedState.QueuedPacket> cur = new ArrayList<>(AutismSharedState.get().getDelayedPackets());
        cur.addAll(add);
        AutismSharedState.get().setDelayedPackets(cur);
        return add.size();
    }

    public static AutismMacro importMacro(MmMessages.MacroOffer offer) {
        if (offer == null) return null;
        AutismMacro macro = AutismClipboardHelper.deserializeMacroFromBase64(offer.hash);
        if (macro == null) {
            AutismNotifications.show("Could not import macro (corrupt or unsupported).", 0xFFE0533A);
            return null;
        }

        macro.keyCode = -1;
        String name = (offer.macroName == null || offer.macroName.isBlank()) ? macro.name : offer.macroName;
        AutismMacro imported = AutismMacroManager.get().addImportedCopy(macro, name);
        AutismNotifications.show("Imported macro: " + (imported != null ? imported.name : name), 0xFF35D873);
        return imported;
    }

    public static Packet<?> packetFromOffer(MmMessages.PacketOffer offer) {
        if (offer == null || offer.data == null) return null;
        return AutismClipboardHelper.deserializePacketFromBase64(offer.data);
    }

    public static AutismPacketLoggerOverlay.LogEntry inspectableEntry(MmMessages.PacketOffer offer) {
        Packet<?> packet = packetFromOffer(offer);
        if (packet == null) return null;
        String dir = (offer.direction == null || offer.direction.isBlank()) ? "C2S" : offer.direction;
        return new AutismPacketLoggerOverlay.LogEntry(
            System.currentTimeMillis(), 0, dir,
            offer.friendlyName == null ? packet.getClass().getSimpleName() : offer.friendlyName,
            packet.getClass(), packet, false, false, false, null, null, null, null);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
