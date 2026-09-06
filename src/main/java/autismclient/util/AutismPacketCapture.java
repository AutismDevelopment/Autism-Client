package autismclient.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ProtocolInfo.DetailsProvider;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class AutismPacketCapture {
    private static final Map<Packet<?>, PacketSnapshot> PACKET_SNAPSHOTS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Channel, EncryptionSnapshot> ENCRYPTION_SNAPSHOTS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Map<String, Integer>> PACKET_ID_TABLE_CACHE = new ConcurrentHashMap<>();

    private AutismPacketCapture() {
    }

    public static void capturePlaintext(Packet<?> packet, String direction, String protocolPhase,
                                        PacketType<?> packetType, ByteBuf source) {
        if (packet == null || source == null) return;
        capturePlaintextBytes(packet, direction, protocolPhase, packetType, copyReadableBytes(source));
    }

    public static void capturePlaintextBytes(Packet<?> packet, String direction, String protocolPhase,
                                             PacketType<?> packetType, byte[] bytes) {
        if (packet == null || bytes == null) return;
        int numericPacketId = readLeadingVarInt(bytes);
        if (numericPacketId < 0) {
            numericPacketId = resolvePacketId(protocolPhase, direction, packetType);
        }
        PACKET_SNAPSHOTS.put(packet, new PacketSnapshot(
            direction,
            protocolPhase,
            packet.getClass().getName(),
            packetType == null ? "" : packetType.toString(),
            numericPacketId,
            bytes,
            System.currentTimeMillis()
        ));
    }

    public static PacketSnapshot snapshot(Packet<?> packet) {
        return packet == null ? null : PACKET_SNAPSHOTS.get(packet);
    }

    public static void markEncryptionEnabled(Channel channel) {
        if (channel == null) return;
        EncryptionSnapshot previous = ENCRYPTION_SNAPSHOTS.get(channel);
        ENCRYPTION_SNAPSHOTS.put(channel, previous == null
            ? EncryptionSnapshot.newEnabled()
            : previous.withEnabled(true));
    }

    public static void captureCiphertext(Channel channel, String direction, ByteBuf source) {
        if (channel == null || source == null) return;
        EncryptionSnapshot previous = ENCRYPTION_SNAPSHOTS.get(channel);
        if (previous == null) previous = EncryptionSnapshot.newEnabled();
        ENCRYPTION_SNAPSHOTS.put(channel, previous.withCiphertext(direction, copyReadableBytes(source)));
    }

    public static EncryptionSnapshot encryptionSnapshot(Channel channel) {
        return channel == null ? null : ENCRYPTION_SNAPSHOTS.get(channel);
    }

    public static byte[] copyReadableBytes(ByteBuf source) {
        if (source == null) return AutismNetworkCaptureState.EMPTY_BYTES;
        int length = Math.max(0, source.readableBytes());
        if (length == 0) return AutismNetworkCaptureState.EMPTY_BYTES;
        byte[] bytes = new byte[length];
        source.getBytes(source.readerIndex(), bytes);
        return bytes;
    }

    public static String compactHex(byte[] bytes, int maxBytes) {
        return AutismPayloadSupport.toCompactHex(bytes, maxBytes);
    }

    private static int readLeadingVarInt(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return -1;
        int value = 0;
        int shift = 0;
        for (int i = 0; i < Math.min(5, bytes.length); i++) {
            int b = bytes[i] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        return -1;
    }

    public static int resolvePacketId(String protocolPhase, String direction, PacketType<?> packetType) {
        if (packetType == null) return -1;
        String phase = normalizePhase(protocolPhase);
        String flow = normalizeDirection(direction);
        if (phase.isBlank() || flow.isBlank()) return -1;
        Map<String, Integer> ids = PACKET_ID_TABLE_CACHE.computeIfAbsent(phase + "|" + flow,
            key -> buildPacketIdTable(phase, flow));
        Integer id = ids.get(packetType.toString());
        return id == null ? -1 : id;
    }

    private static Map<String, Integer> buildPacketIdTable(String phase, String flow) {
        DetailsProvider provider = detailsProvider(phase, flow);
        if (provider == null) return Map.of();
        Map<String, Integer> ids = new HashMap<>();
        try {
            ProtocolInfo.Details details = provider.details();
            details.listPackets((type, networkId) -> {
                if (type != null && networkId >= 0) ids.put(type.toString(), networkId);
            });
        } catch (Throwable ignored) {  }
        return ids.isEmpty() ? Map.of() : Map.copyOf(ids);
    }

    private static DetailsProvider detailsProvider(String phase, String flow) {
        boolean c2s = "C2S".equals(flow);
        if ("CONFIGURATION".equals(phase)) {
            return c2s ? ConfigurationProtocols.SERVERBOUND_TEMPLATE : ConfigurationProtocols.CLIENTBOUND_TEMPLATE;
        }
        if ("PLAY".equals(phase)) {
            return c2s ? GameProtocols.SERVERBOUND_TEMPLATE : GameProtocols.CLIENTBOUND_TEMPLATE;
        }
        return null;
    }

    private static String normalizePhase(String protocolPhase) {
        if (protocolPhase == null || protocolPhase.isBlank()) return "";
        String value = protocolPhase.strip().toUpperCase(java.util.Locale.ROOT);
        if (value.contains("CONFIG")) return "CONFIGURATION";
        if (value.contains("PLAY") || value.contains("GAME")) return "PLAY";
        return value;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) return "";
        String value = direction.strip().toUpperCase(java.util.Locale.ROOT);
        if (value.contains("SERVERBOUND") || value.contains("OUT") || value.contains("C2S")) return "C2S";
        if (value.contains("CLIENTBOUND") || value.contains("IN") || value.contains("S2C")) return "S2C";
        return value;
    }

    public record PacketSnapshot(String direction, String protocolPhase, String packetClassName,
                                 String packetType, int numericPacketId, byte[] plaintextBytes,
                                 long capturedAtMs) {
        public byte[] plaintextBytes() {
            return plaintextBytes == null ? new byte[0] : plaintextBytes.clone();
        }
    }

    public record EncryptionSnapshot(boolean enabled, long enabledAtMs, byte[] lastInboundCiphertext,
                                     byte[] lastOutboundCiphertext, long lastInboundAtMs,
                                     long lastOutboundAtMs) {
        static EncryptionSnapshot newEnabled() {
            return new EncryptionSnapshot(true, System.currentTimeMillis(), new byte[0], new byte[0], 0L, 0L);
        }

        EncryptionSnapshot withEnabled(boolean value) {
            return new EncryptionSnapshot(value, value && enabledAtMs == 0L ? System.currentTimeMillis() : enabledAtMs,
                lastInboundCiphertext, lastOutboundCiphertext, lastInboundAtMs, lastOutboundAtMs);
        }

        EncryptionSnapshot withCiphertext(String direction, byte[] bytes) {
            long now = System.currentTimeMillis();
            if ("C2S".equalsIgnoreCase(direction) || "OUT".equalsIgnoreCase(direction)) {
                return new EncryptionSnapshot(enabled, enabledAtMs, lastInboundCiphertext,
                    bytes == null ? new byte[0] : bytes.clone(), lastInboundAtMs, now);
            }
            return new EncryptionSnapshot(enabled, enabledAtMs, bytes == null ? new byte[0] : bytes.clone(),
                lastOutboundCiphertext, now, lastOutboundAtMs);
        }

        public byte[] lastInboundCiphertext() {
            return lastInboundCiphertext == null ? new byte[0] : lastInboundCiphertext.clone();
        }

        public byte[] lastOutboundCiphertext() {
            return lastOutboundCiphertext == null ? new byte[0] : lastOutboundCiphertext.clone();
        }
    }
}
