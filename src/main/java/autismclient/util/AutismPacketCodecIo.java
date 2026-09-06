package autismclient.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AutismPacketCodecIo {
    private static final Minecraft MC = Minecraft.getInstance();

    private AutismPacketCodecIo() {
    }

    public static DecodedPacket decodeFullPacket(Class<? extends Packet<?>> packetClass, byte[] plaintextBytes, String protocolPhase) {
        ByteBuf buf = Unpooled.wrappedBuffer(plaintextBytes == null ? new byte[0] : plaintextBytes);
        try {
            int packetId = readVarInt(buf);
            byte[] body = new byte[buf.readableBytes()];
            if (body.length > 0) buf.readBytes(body);
            Packet<?> packet = decodeBody(packetClass, body, protocolPhase);
            return new DecodedPacket(packet, packetId, body, "minecraft full packet STREAM_CODEC");
        } finally {
            buf.release();
        }
    }

    public static Packet<?> decodeBody(Class<? extends Packet<?>> packetClass, byte[] bodyBytes, String protocolPhase) {
        if (packetClass == null) throw new IllegalArgumentException("Packet class is missing.");
        byte[] body = bodyBytes == null ? new byte[0] : bodyBytes.clone();
        if (packetClass == ServerboundCustomPayloadPacket.class || packetClass == ClientboundCustomPayloadPacket.class) {
            return decodeRawCustomPayload(packetClass, body);
        }
        List<CodecCandidate> codecs = findStreamCodecs(packetClass, protocolPhase);
        if (codecs.isEmpty()) {
            throw new IllegalArgumentException("No STREAM_CODEC found for " + packetClass.getName() + ".");
        }

        Throwable firstFailure = null;
        for (CodecCandidate candidate : codecs) {
            for (BufferFactory factory : codecBufferFactories(body)) {
                ByteBuf buffer = factory.create();
                try {
                    Object decoded = candidate.decode(buffer);
                    if (!(decoded instanceof Packet<?> packet)) {
                        throw new IllegalArgumentException("Codec " + candidate.name + " returned "
                            + (decoded == null ? "null" : decoded.getClass().getName()) + ", not a packet.");
                    }
                    if (!packetClass.isInstance(packet)) {
                        throw new IllegalArgumentException("Codec " + candidate.name + " returned "
                            + packet.getClass().getName() + ", expected " + packetClass.getName() + ".");
                    }
                    if (buffer.readableBytes() > 0) {
                        throw new IllegalArgumentException("Codec " + candidate.name + " left "
                            + buffer.readableBytes() + " trailing byte(s).");
                    }
                    return packet;
                } catch (Throwable t) {
                    if (firstFailure == null) firstFailure = t;
                } finally {
                    buffer.release();
                }
            }
        }
        throw new IllegalArgumentException(safeMessage(firstFailure));
    }

    public static byte[] stripPacketId(byte[] plaintextBytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(plaintextBytes == null ? new byte[0] : plaintextBytes);
        try {
            readVarInt(buf);
            byte[] body = new byte[buf.readableBytes()];
            if (body.length > 0) buf.readBytes(body);
            return body;
        } finally {
            buf.release();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<CodecCandidate> findStreamCodecs(Class<?> packetClass, String protocolPhase) {
        List<CodecCandidate> candidates = new ArrayList<>();
        Class<?> cursor = packetClass;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!StreamCodec.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof StreamCodec codec) {
                        candidates.add(new CodecCandidate(field.getName(), codec));
                    }
                } catch (Throwable ignored) {  }
            }
            cursor = cursor.getSuperclass();
        }
        String phase = protocolPhase == null ? "" : protocolPhase.toLowerCase(Locale.ROOT);
        candidates.sort(Comparator.comparingInt(candidate -> codecPriority(candidate.name, phase)));
        return candidates;
    }

    private static int codecPriority(String fieldName, String phase) {
        String name = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (phase.contains("config") && name.contains("config")) return 0;
        if ((phase.contains("play") || phase.contains("game")) && (name.contains("game") || name.equals("stream_codec"))) return 0;
        if (name.equals("stream_codec")) return 1;
        if (name.contains("game")) return 2;
        if (name.contains("config")) return 3;
        return 4;
    }

    private static List<BufferFactory> codecBufferFactories(byte[] bodyBytes) {
        byte[] bytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
        List<BufferFactory> out = new ArrayList<>();
        RegistryAccess access = registryAccess();
        if (access != null) {
            out.add(() -> new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes.clone()), access));
        }
        out.add(() -> new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes.clone())));
        out.add(() -> Unpooled.wrappedBuffer(bytes.clone()));
        return out;
    }

    private static RegistryAccess registryAccess() {
        try {
            if (MC != null && MC.level != null) return MC.level.registryAccess();
            if (MC != null && MC.getConnection() != null) return MC.getConnection().registryAccess();
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Packet<?> decodeRawCustomPayload(Class<? extends Packet<?>> packetClass, byte[] body) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(body == null ? new byte[0] : body));
        try {
            Identifier channel = buf.readIdentifier();
            byte[] payload = new byte[buf.readableBytes()];
            if (payload.length > 0) buf.readBytes(payload);
            AutismPayloadSupport.RawCustomPacketPayload rawPayload =
                new AutismPayloadSupport.RawCustomPacketPayload(channel, payload);
            AutismPayloadSupport.rememberUnknownPayloadBytes(rawPayload, payload);
            if (packetClass == ServerboundCustomPayloadPacket.class) {
                AutismPayloadChannelSubscriptionManager.rememberRequestedChannel(channel.toString());
                return new ServerboundCustomPayloadPacket(rawPayload);
            }
            return new ClientboundCustomPayloadPacket(rawPayload);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Custom payload body must be <channel identifier><payload bytes>: " + safeMessage(t));
        } finally {
            buf.release();
        }
    }

    private static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        while (buf.isReadable()) {
            byte currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) return value;
            position += 7;
            if (position >= 32) throw new IllegalArgumentException("Packet id VarInt is too long.");
        }
        throw new IllegalArgumentException("Full packet bytes are missing the packet id VarInt.");
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private record CodecCandidate(String name, StreamCodec<ByteBuf, ?> codec) {
        Object decode(ByteBuf buffer) {
            return codec.decode(buffer);
        }
    }

    private interface BufferFactory {
        ByteBuf create();
    }

    public record DecodedPacket(Packet<?> packet, int packetId, byte[] bodyBytes, String source) {
        public DecodedPacket {
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
            source = source == null || source.isBlank() ? "minecraft STREAM_CODEC" : source;
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }
    }
}
