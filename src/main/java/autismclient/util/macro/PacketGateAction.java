package autismclient.util.macro;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class PacketGateAction implements MacroAction {
    public enum GateMode { CANCEL, DELAY, ALLOW_ONLY, DISABLE_GATE }
    public enum Direction { C2S, S2C, ANY }
    public enum DurationMode { UNTIL_DISABLED, TICKS, MS, UNTIL_PACKET, UNTIL_GUI, UNTIL_INVENTORY }
    public enum GateScope { END_MARKER, MACRO_PASS }

    public GateMode mode = GateMode.CANCEL;
    public GateScope scope = GateScope.END_MARKER;
    public Direction direction = Direction.ANY;
    public String gateId = "auto";
    public ArrayList<String> packetNames = new ArrayList<>();
    public DurationMode durationMode = DurationMode.UNTIL_DISABLED;
    public int durationValue = 0;
    public String untilPacketName = "";
    public String untilPacketField = "";
    public String untilPacketOperator = WaitPacketMatchAction.Operator.EXISTS.name();
    public String untilPacketValue = "";
    public String untilGuiType = "ANY";
    public String untilGuiTitle = "";
    public String untilInventoryCondition = WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS.name();
    public String untilInventoryItem = "";
    public int untilInventoryCount = 1;
    public int untilInventorySlot = 0;
    public boolean flushOnDisable = true;

    public PacketGateAction() {}

    @Override
    public void execute(Minecraft mc) {
        PacketGateManager.install(this, MacroExecutor.currentRunId());
    }

    ArrayList<String> effectivePackets() {
        return new ArrayList<>(packetNames);
    }

    @Override public MacroActionType getType() { return MacroActionType.PACKET_GATE; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "PACKET_GATE");
        tag.putString("mode", mode.name());
        tag.putString("scope", scope == null ? GateScope.END_MARKER.name() : scope.name());
        tag.putString("direction", Direction.ANY.name());
        tag.putString("gateId", gateId);
        tag.put("packetNames", MacroStringList.toTag(packetNames));
        tag.putString("durationMode", durationMode == null ? DurationMode.UNTIL_DISABLED.name() : durationMode.name());
        tag.putInt("durationValue", Math.max(0, durationValue));
        tag.putString("untilPacketName", untilPacketName == null ? "" : untilPacketName);
        tag.putString("untilPacketField", untilPacketField == null ? "" : untilPacketField);
        tag.putString("untilPacketOperator", untilPacketOperator == null ? WaitPacketMatchAction.Operator.EXISTS.name() : untilPacketOperator);
        tag.putString("untilPacketValue", untilPacketValue == null ? "" : untilPacketValue);
        tag.putString("untilGuiType", untilGuiType == null ? "ANY" : untilGuiType);
        tag.putString("untilGuiTitle", untilGuiTitle == null ? "" : untilGuiTitle);
        tag.putString("untilInventoryCondition", untilInventoryCondition == null ? WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS.name() : untilInventoryCondition);
        tag.putString("untilInventoryItem", untilInventoryItem == null ? "" : untilInventoryItem);
        tag.putInt("untilInventoryCount", Math.max(1, untilInventoryCount));
        tag.putInt("untilInventorySlot", Math.max(0, untilInventorySlot));
        tag.putBoolean("flushOnDisable", flushOnDisable);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        mode = MacroStringList.enumValue(GateMode.class, tag.getStringOr("mode", "CANCEL"), GateMode.CANCEL);
        String rawScope = tag.getStringOr("scope", "END_MARKER");
        if ("MACRO_RUN".equalsIgnoreCase(rawScope)) rawScope = GateScope.MACRO_PASS.name();
        scope = MacroStringList.enumValue(GateScope.class, rawScope, GateScope.END_MARKER);
        direction = Direction.ANY;
        gateId = tag.getStringOr("gateId", "auto");
        packetNames = MacroStringList.fromTag(tag.getList("packetNames").orElse(new net.minecraft.nbt.ListTag()));
        durationMode = MacroStringList.enumValue(DurationMode.class, tag.getStringOr("durationMode", "UNTIL_DISABLED"), DurationMode.UNTIL_DISABLED);
        durationValue = Math.max(0, tag.getIntOr("durationValue", 0));
        untilPacketName = tag.getStringOr("untilPacketName", "");
        untilPacketField = tag.getStringOr("untilPacketField", "");
        untilPacketOperator = tag.getStringOr("untilPacketOperator", WaitPacketMatchAction.Operator.EXISTS.name());
        untilPacketValue = tag.getStringOr("untilPacketValue", "");
        untilGuiType = tag.getStringOr("untilGuiType", "ANY");
        untilGuiTitle = tag.getStringOr("untilGuiTitle", "");
        untilInventoryCondition = tag.getStringOr("untilInventoryCondition", WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS.name());
        untilInventoryItem = tag.getStringOr("untilInventoryItem", "");
        untilInventoryCount = Math.max(1, tag.getIntOr("untilInventoryCount", 1));
        untilInventorySlot = Math.max(0, tag.getIntOr("untilInventorySlot", 0));
        flushOnDisable = tag.getBooleanOr("flushOnDisable", true);
    }

    @Override
    public String getDisplayName() {
        String target = packetNames.isEmpty() ? "packets" : String.join(", ", packetNames);
        String suffix = scope == GateScope.MACRO_PASS ? " (pass)" : "";
        return switch (mode) {
            case CANCEL -> "Gate cancel ANY " + target + suffix;
            case DELAY -> "Gate delay ANY " + target + suffix;
            case ALLOW_ONLY -> "Gate allow only ANY " + target + suffix;
            case DISABLE_GATE -> "Disable gate " + gateId;
        };
    }

    @Override public String getIcon() { return "G"; }
}
