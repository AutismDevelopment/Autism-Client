package autismclient.util;

import autismclient.gui.macro.editor.ActionEditorOverlay;
import autismclient.util.macro.PayloadAction;
import autismclient.util.macro.SendPacketAction;
import autismclient.util.macro.WaitForPacketAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class AutismPacketEntryActions {
    private AutismPacketEntryActions() {
    }

    public static boolean canQueue(AutismPacketLoggerOverlay.LogEntry entry) {
        return entry != null && "C2S".equalsIgnoreCase(entry.direction) && entry.packetRef != null;
    }

    public static boolean canDirectSend(AutismPacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean directSend(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canDirectSend(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be sent.");
            return false;
        }

        if (entry.packetRef instanceof ServerboundCustomPayloadPacket) {
            return directSendPayload(entry);
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }
        try {
            AutismPacketSender.send(regenerated);
            AutismClientMessaging.sendPrefixed("Sent: " + entry.shortName);
            return true;
        } catch (Exception e) {
            AutismClientMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean directSendPayload(AutismPacketLoggerOverlay.LogEntry entry) {
        AutismPayloadSupport.PayloadSnapshot snapshot = AutismPayloadSupport.snapshotFromEntry(entry);
        if (snapshot == null || snapshot.channel() == null || snapshot.channel().isBlank()) {
            Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
            if (regenerated != null) {
                try {
                    AutismPacketSender.send(regenerated);
                    AutismClientMessaging.sendPrefixed("Sent: " + entry.shortName);
                    return true;
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
                    return false;
                }
            }
            AutismClientMessaging.sendPrefixed("\u00a7cCannot send: no payload data for " + entry.shortName);
            return false;
        }
        byte[] rawBytes = snapshot.rawBytes();
        if (!AutismPayloadSupport.sendPayload(snapshot.channel(), rawBytes, snapshot.protocolPhase())) {
            AutismClientMessaging.sendPrefixed("\u00a7cFailed to send payload: " + entry.shortName);
            return false;
        }
        AutismClientMessaging.sendPrefixed("Sent payload: " + snapshot.channel());
        return true;
    }

    public static boolean canAddSendAction(AutismPacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean canAddWaitAction(AutismPacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.shortName != null && !entry.shortName.isBlank() && entry.direction != null && !entry.direction.isBlank();
    }

    public static boolean canEditPayload(AutismPacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.isPayload && "C2S".equalsIgnoreCase(entry.direction);
    }

    public static boolean canAddPayloadAction(AutismPacketLoggerOverlay.LogEntry entry) {
        return canEditPayload(entry);
    }

    public static boolean queue(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canQueue(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be queued.");
            return false;
        }

        AutismSharedState.get().enqueuePacket(entry.packetRef);
        AutismClientMessaging.sendPrefixed("Queued: " + entry.shortName);
        return true;
    }

    public static boolean addSendActionToVisibleMacro(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canAddSendAction(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be added as send actions.");
            return false;
        }

        AutismMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }

        SendPacketAction action = new SendPacketAction();
        action.waitForGuiBefore = false;
        action.waitForGuiAfter = false;
        action.guiName = "";
        action.packets.add(new AutismSharedState.QueuedPacket(regenerated, 0));
        macroEditor.addAction(action);
        AutismOverlayManager.get().bringToFront(macroEditor);
        AutismClientMessaging.sendPrefixed("Added send action: " + entry.shortName);
        return true;
    }

    public static boolean addWaitActionToVisibleMacro(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canAddWaitAction(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        AutismMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        String target = WaitForPacketAction.withDirection(entry.direction, entry.shortName);
        if (target.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        WaitForPacketAction action = new WaitForPacketAction(target);
        action.packetNames.add(target);
        macroEditor.addAction(action);
        AutismOverlayManager.get().bringToFront(macroEditor);
        AutismClientMessaging.sendPrefixed("Added wait condition: " + WaitForPacketAction.getDisplayLabel(target));
        return true;
    }

    public static boolean openPayloadEditor(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canEditPayload(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be edited.");
            return false;
        }

        PayloadAction action = AutismPayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cFailed to seed payload editor from packet.");
            return false;
        }

        ActionEditorOverlay.getSharedOverlay().openStandalonePayloadEditor(action);
        AutismOverlayManager.get().bringToFront(ActionEditorOverlay.getSharedOverlay());
        return true;
    }

    public static boolean addPayloadActionToVisibleMacro(AutismPacketLoggerOverlay.LogEntry entry) {
        if (!canAddPayloadAction(entry)) {
            AutismClientMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be added as payload actions.");
            return false;
        }

        AutismMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        PayloadAction action = AutismPayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cFailed to create payload action from packet.");
            return false;
        }

        macroEditor.addAction(action);
        AutismOverlayManager.get().bringToFront(macroEditor);
        AutismClientMessaging.sendPrefixed("Added payload action: " + entry.shortName);
        return true;
    }

    public static boolean hasVisibleMacroEditor() {
        return findVisibleMacroEditor() != null;
    }

    private static AutismMacroEditorOverlay getOrOpenMacroEditor() {
        AutismMacroEditorOverlay macroEditor = findVisibleMacroEditor();
        if (macroEditor != null) {
            AutismOverlayManager.get().bringToFront(macroEditor);
            return macroEditor;
        }

            macroEditor = AutismMacroEditorOverlay.getSharedOverlay();
        if (macroEditor == null) return null;

        AutismMacro existingMacro = AutismSharedState.get().getEditingMacro();
        macroEditor.open(existingMacro);
        AutismOverlayManager.get().bringToFront(macroEditor);
        return macroEditor;
    }

    private static AutismMacroEditorOverlay findVisibleMacroEditor() {
        for (IAutismOverlay overlay : AutismOverlayManager.get().getOverlays()) {
            if (overlay instanceof AutismMacroEditorOverlay macroEditor && macroEditor.isVisible()) {
                return macroEditor;
            }
        }
        return null;
    }
}
