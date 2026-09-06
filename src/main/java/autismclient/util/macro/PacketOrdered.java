package autismclient.util.macro;

import net.minecraft.nbt.CompoundTag;

public interface PacketOrdered {
    String PACKET_ORDER_KEY = "packetOrder";

    PacketOrder getPacketOrder();

    default boolean isTickAligned() { return getPacketOrder() == PacketOrder.GRIM; }

    static void save(CompoundTag tag, PacketOrder order) {
        tag.putString(PACKET_ORDER_KEY, (order == null ? PacketOrder.INSTANT : order).name());
    }

    static PacketOrder load(CompoundTag tag) {
        if (tag == null || !tag.contains(PACKET_ORDER_KEY)) return PacketOrder.INSTANT;
        return PacketOrder.parse(tag.getStringOr(PACKET_ORDER_KEY, "INSTANT"), PacketOrder.INSTANT);
    }
}
