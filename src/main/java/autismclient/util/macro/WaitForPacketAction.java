package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import autismclient.util.AutismPacketNamer;

import java.util.ArrayList;
import java.util.List;

public class WaitForPacketAction implements MacroAction, MacroCaptureOutput {
    public static final String C2S_PREFIX = "C2S:";
    public static final String S2C_PREFIX = "S2C:";

    public String packetName = "";
    public List<String> packetNames = new ArrayList<>();
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    public transient volatile net.minecraft.network.protocol.Packet<?> matchedPacket;
    public transient volatile String matchedDirection = "";
    private boolean enabled = true;

    public WaitForPacketAction() {}

    public WaitForPacketAction(String packetName) {
        this.packetName = packetName;
    }

    public static String getDirection(String target) {
        if (target == null) return "";
        String trimmed = target.trim();
        if (trimmed.regionMatches(true, 0, C2S_PREFIX, 0, C2S_PREFIX.length())) return "C2S";
        if (trimmed.regionMatches(true, 0, S2C_PREFIX, 0, S2C_PREFIX.length())) return "S2C";
        return "";
    }

    public static String getPacketName(String target) {
        if (target == null) return "";
        String trimmed = target.trim();
        if (trimmed.regionMatches(true, 0, C2S_PREFIX, 0, C2S_PREFIX.length())) {
            return trimmed.substring(C2S_PREFIX.length()).trim();
        }
        if (trimmed.regionMatches(true, 0, S2C_PREFIX, 0, S2C_PREFIX.length())) {
            return trimmed.substring(S2C_PREFIX.length()).trim();
        }
        return trimmed;
    }

    public static String normalizeTarget(String target) {
        String direction = getDirection(target);
        String packet = getPacketName(target);
        if (packet.isEmpty()) return "";
        return direction.isEmpty() ? packet : direction + ":" + packet;
    }

    public static String withDirection(String direction, String packetName) {
        String packet = getPacketName(packetName);
        if (packet.isEmpty()) return "";
        if ("C2S".equalsIgnoreCase(direction)) return C2S_PREFIX + packet;
        if ("S2C".equalsIgnoreCase(direction)) return S2C_PREFIX + packet;
        return packet;
    }

    public static String getDisplayLabel(String target) {
        String packet = getPacketName(target);
        String friendly = AutismPacketNamer.getFriendlyName(packet);
        String direction = getDirection(target);
        return direction.isEmpty() ? friendly : direction + " " + friendly;
    }

    public List<String> effectiveList() {
        if (!packetNames.isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (String target : packetNames) {
                String safe = normalizeTarget(target);
                if (!safe.isEmpty()) normalized.add(safe);
            }
            if (!normalized.isEmpty()) return normalized;
        }
        String legacy = normalizeTarget(packetName);
        if (!legacy.isEmpty()) return java.util.Collections.singletonList(legacy);
        return java.util.Collections.emptyList();
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("packetName", packetName);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        ListTag list = new ListTag();
        for (String n : packetNames) list.add(StringTag.valueOf(n));
        tag.put("packetNames", list);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("packetName")) packetName = tag.getStringOr("packetName", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
        packetNames.clear();
        if (tag.contains("packetNames")) {
            ListTag list = (ListTag) tag.get("packetNames");
            if (list != null) for (int i = 0; i < list.size(); i++) packetNames.add(list.getString(i).orElse(""));
        }
    }

    @Override
    public MacroActionType getType() { return MacroActionType.WAIT_PACKET; }

    @Override
    public String getDisplayName() {
        List<String> eff = effectiveList();
        if (eff.isEmpty()) return "Wait Pkt: Any";
        if (eff.size() == 1) return "Wait Pkt: " + getDisplayLabel(eff.get(0));

        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String n : eff) counts.merge(n, 1, Integer::sum);
        if (counts.size() == 1) {
            String n = counts.keySet().iterator().next();
            int c = counts.get(n);
            return "Wait Pkt: " + getDisplayLabel(n) + " x" + c;
        }
        return "Wait Pkts (" + eff.size() + ")";
    }

    @Override
    public String getIcon() { return "Ptk"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
