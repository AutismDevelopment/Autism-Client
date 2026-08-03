package autismclient.util.multi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MultiPacketPolicy {
    public enum Direction {
        C2S,
        S2C
    }

    public record Rule(Direction direction, String packetClass) {
        public Rule {
            direction = direction == null ? Direction.C2S : direction;
            packetClass = packetClass == null ? "" : packetClass.trim();
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("direction", direction.name());
            tag.putString("packet", packetClass);
            return tag;
        }

        static Rule fromTag(CompoundTag tag) {
            Direction direction;
            try {
                direction = Direction.valueOf(tag.getStringOr("direction", Direction.C2S.name()).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                direction = Direction.C2S;
            }
            return new Rule(direction, tag.getStringOr("packet", ""));
        }
    }

    public record Slot(String packetClass, boolean enabled) {
        public Slot {
            packetClass = packetClass == null ? "" : packetClass.trim();
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("packet", packetClass);
            tag.putBoolean("enabled", enabled);
            return tag;
        }

        static Slot fromTag(CompoundTag tag) {
            return new Slot(tag.getStringOr("packet", ""), tag.getBooleanOr("enabled", true));
        }
    }

    private boolean gravity = true;

    private boolean autoPosition = true;
    private boolean autoLook = false;
    private boolean autoSwing = false;
    private final List<Slot> slots = new ArrayList<>(List.of(
        new Slot("", true), new Slot("", true), new Slot("", true), new Slot("", true)
    ));
    private final List<Rule> blocklist = new ArrayList<>();

    public MultiPacketPolicy() {
    }

    public MultiPacketPolicy(MultiPacketPolicy source) {
        if (source == null) return;
        gravity = source.gravity;
        autoPosition = source.autoPosition;
        autoLook = source.autoLook;
        autoSwing = source.autoSwing;
        slots.clear();
        slots.addAll(source.slots);
        blocklist.addAll(source.blocklist);
        normalize();
    }

    public boolean gravity() {
        return gravity;
    }

    public void setGravity(boolean gravity) {
        this.gravity = gravity;
    }

    public boolean autoPosition() {
        return autoPosition;
    }

    public void setAutoPosition(boolean autoPosition) {
        this.autoPosition = autoPosition;
    }

    public boolean autoLook() {
        return autoLook;
    }

    public void setAutoLook(boolean autoLook) {
        this.autoLook = autoLook;
    }

    public boolean autoSwing() {
        return autoSwing;
    }

    public void setAutoSwing(boolean autoSwing) {
        this.autoSwing = autoSwing;
    }

    public List<Slot> slots() {
        return List.copyOf(slots);
    }

    public void setSlot(int index, Slot slot) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        slots.set(index, slot == null ? new Slot("", true) : slot);
    }

    public List<Rule> blocklist() {
        return List.copyOf(blocklist);
    }

    public void setBlocklist(List<Rule> rules) {
        blocklist.clear();
        if (rules != null) blocklist.addAll(rules);
        normalize();
    }

    public boolean allows(Direction direction, String packetClass, boolean protocolCritical, boolean movementPacket) {
        if (protocolCritical) return true;
        String packet = packetClass == null ? "" : packetClass;
        if (isBlocked(direction, packet)) return false;
        if (direction == Direction.C2S) {
            for (Slot slot : slots) {
                if (!slot.packetClass().isBlank() && slot.packetClass().equals(packet)) return slot.enabled();
            }
        }
        return true;
    }

    public static boolean isProtected(Direction direction, String packetClass) {
        String name = packetClass == null ? "" : packetClass;
        if (direction == Direction.C2S) {
            return ends(name, "ServerboundKeepAlivePacket", "ServerboundPongPacket",
                "ServerboundAcceptTeleportationPacket", "ServerboundCookieResponsePacket",
                "ServerboundResourcePackPacket", "ServerboundLoginAcknowledgedPacket",
                "ServerboundFinishConfigurationPacket", "ServerboundSelectKnownPacks",
                "ServerboundAcceptCodeOfConductPacket", "ServerboundCustomQueryAnswerPacket",
                "ServerboundConfigurationAcknowledgedPacket", "ServerboundChatAckPacket",
                "ServerboundChatSessionUpdatePacket", "ServerboundPlayerLoadedPacket",
                "ServerboundClientInformationPacket", "ServerboundCustomPayloadPacket",
                "ServerboundCustomClickActionPacket");
        }
        return ends(name, "ClientboundKeepAlivePacket", "ClientboundPingPacket", "ClientboundDisconnectPacket",
            "ClientboundLoginDisconnectPacket", "ClientboundHelloPacket", "ClientboundLoginFinishedPacket",
            "ClientboundLoginCompressionPacket", "ClientboundCustomQueryPacket", "ClientboundFinishConfigurationPacket",
            "ClientboundRegistryDataPacket", "ClientboundUpdateEnabledFeaturesPacket",
            "ClientboundSelectKnownPacks", "ClientboundResetChatPacket", "ClientboundCodeOfConductPacket",
            "ClientboundUpdateTagsPacket", "ClientboundCookieRequestPacket", "ClientboundStoreCookiePacket",
            "ClientboundResourcePackPushPacket", "ClientboundTransferPacket", "ClientboundLoginPacket",
            "ClientboundStartConfigurationPacket", "ClientboundPlayerPositionPacket",
            "ClientboundPlayerRotationPacket", "ClientboundAddEntityPacket",
            "ClientboundMoveEntityPacket$Pos", "ClientboundMoveEntityPacket$PosRot",
            "ClientboundMoveEntityPacket$Rot", "ClientboundEntityPositionSyncPacket",
            "ClientboundTeleportEntityPacket", "ClientboundRotateHeadPacket",
            "ClientboundSetEntityDataPacket", "ClientboundSetEntityMotionPacket",
            "ClientboundRemoveEntitiesPacket", "ClientboundSetPassengersPacket",
            "ClientboundMoveMinecartPacket", "ClientboundShowDialogPacket", "ClientboundClearDialogPacket");
    }

    private static boolean ends(String value, String... names) {
        for (String name : names) if (value.endsWith(name)) return true;
        return false;
    }

    private boolean isBlocked(Direction direction, String packetClass) {
        for (Rule rule : blocklist) {
            if (rule.direction() == direction && rule.packetClass().equals(packetClass)) return true;
        }
        return false;
    }

    public CompoundTag toTag() {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("gravity", gravity);
        tag.putBoolean("autoPosition", autoPosition);
        tag.putBoolean("autoLook", autoLook);
        tag.putBoolean("autoSwing", autoSwing);
        ListTag slotTags = new ListTag();
        for (Slot slot : slots) slotTags.add(slot.toTag());
        tag.put("slots", slotTags);
        ListTag blockTags = new ListTag();
        for (Rule rule : blocklist) blockTags.add(rule.toTag());
        tag.put("blocklist", blockTags);
        return tag;
    }

    public static MultiPacketPolicy fromTag(CompoundTag tag) {
        MultiPacketPolicy policy = new MultiPacketPolicy();
        policy.gravity = tag.getBooleanOr("gravity", true);
        policy.autoPosition = tag.getBooleanOr("autoPosition", true);
        policy.autoLook = tag.getBooleanOr("autoLook", false);
        policy.autoSwing = tag.getBooleanOr("autoSwing", false);
        policy.slots.clear();
        ListTag slotTags = tag.getListOrEmpty("slots");
        for (Tag value : slotTags) {
            if (value instanceof CompoundTag compound) policy.slots.add(Slot.fromTag(compound));
        }
        ListTag blockTags = tag.getListOrEmpty("blocklist");
        for (Tag value : blockTags) {
            if (value instanceof CompoundTag compound) policy.blocklist.add(Rule.fromTag(compound));
        }
        policy.normalize();
        return policy;
    }

    private void normalize() {
        while (slots.size() < 4) slots.add(new Slot("", true));
        while (slots.size() > 4) slots.remove(slots.size() - 1);
        Set<String> seen = new HashSet<>();
        blocklist.removeIf(rule -> rule == null || rule.packetClass().isBlank() || isProtected(rule.direction(), rule.packetClass())
            || !seen.add(rule.direction().name() + "\u0000" + rule.packetClass()));
    }
}
