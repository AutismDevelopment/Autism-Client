package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import autismclient.modules.PackHideState;
import autismclient.util.PacketRegenerator;
import autismclient.util.AutismClipboardHelper;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketSender;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismClientMessaging;
import java.util.ArrayList;
import java.util.List;

public class SendPacketAction implements MacroAction, WaitsForGui {
    public List<AutismSharedState.QueuedPacket> packets = new ArrayList<>();
    public String customName = "";
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";

    public SendPacketAction() {}

    public List<AutismSharedState.QueuedPacket> getPackets() {
        return packets;
    }

    public void regeneratePackets() {
        List<AutismSharedState.QueuedPacket> newPackets = new ArrayList<>(packets.size());
        for (AutismSharedState.QueuedPacket qp : packets) {
            if (qp.packet != null) {
                if (qp.isExactReplay()) {
                    newPackets.add(new AutismSharedState.QueuedPacket(qp.packet, qp.getDelay(), qp.getId(), qp.getReplayMode()));
                    continue;
                }
                Packet<?> regenerated = PacketRegenerator.regenerate(qp.packet);
                if (regenerated != null) {

                    newPackets.add(new AutismSharedState.QueuedPacket(regenerated, qp.getDelay(), qp.getId(), qp.getReplayMode()));
                }

            }
        }
        packets.clear();
        packets.addAll(newPackets);
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo network connection!");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (AutismSharedState.QueuedPacket qp : packets) {
            if (qp.packet == null) {
                failCount++;
                continue;
            }

            try {
                AutismPacketSender.send(qp.packet);
                successCount++;
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("§cSend failed: " + e.getMessage());
                failCount++;
            }
        }

        if (failCount > 0) {
            AutismClientMessaging.sendPrefixed(String.format("§eSent %d/%d packets (%d failed)",
                successCount, packets.size(), failCount));
        }
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name != null ? name : ""; }

    @Override
    public MacroActionType getType() {
        return MacroActionType.SEND_PACKET;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SEND_PACKET");
        tag.putString("customName", customName);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);

        ListTag packetList = new ListTag();
        for (AutismSharedState.QueuedPacket qp : packets) {
            CompoundTag packetTag = AutismClipboardHelper.serializeQueuedPacket(qp);
            if (packetTag != null) {
                packetList.add(packetTag);
            }
        }
        tag.put("packets", packetList);

        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        packets.clear();
        customName = tag.getStringOr("customName", "");
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        if (tag.contains("guiName")) guiName = tag.getStringOr("guiName", "");
        if (tag.contains("packets")) {
            ListTag packetList = (ListTag) tag.get("packets");
            if (packetList != null) {
                for (int i = 0; i < packetList.size(); i++) {
                    Tag element = packetList.get(i);
                    if (element instanceof CompoundTag) {
                        AutismSharedState.QueuedPacket qp = AutismClipboardHelper.deserializeQueuedPacket((CompoundTag) element);
                        if (qp != null) {
                            packets.add(qp);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDisplayName() {

        if (customName != null && !customName.trim().isEmpty()) {
            return "Send " + customName.trim() + WaitsForGui.timingLabel(this);
        }

        if (packets.isEmpty()) {
            return "Send (empty)" + WaitsForGui.timingLabel(this);
        }

        java.util.Map<String, Integer> nameCounts = new java.util.LinkedHashMap<>();
        for (AutismSharedState.QueuedPacket qp : packets) {
            if (qp.packet != null) {
                String name = AutismPacketNamer.getFriendlyName(qp.packet);
                nameCounts.merge(name, 1, Integer::sum);
            }
        }

        if (nameCounts.isEmpty()) {
            return "Send (empty)" + WaitsForGui.timingLabel(this);
        }

        if (nameCounts.size() == 1) {
            java.util.Map.Entry<String, Integer> entry = nameCounts.entrySet().iterator().next();
            String pktName = entry.getKey();
            int count = entry.getValue();

            if (count == 1) {

                return "Send " + pktName + WaitsForGui.timingLabel(this);
            } else {

                return "Send " + pktName + " x" + count + WaitsForGui.timingLabel(this);
            }
        }

        return "Send Group (" + packets.size() + ")" + WaitsForGui.timingLabel(this);
    }

    @Override
    public String getIcon() {
        return "P";
    }
}
