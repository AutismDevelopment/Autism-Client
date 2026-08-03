package autismclient.util;

import autismclient.modules.PackHideState;
import io.netty.buffer.Unpooled;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class AutismPayloadSupport {
    private static final Map<CustomPacketPayload, byte[]> UNKNOWN_PAYLOAD_BYTES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.network.protocol.Packet<?>, String> PAYLOAD_PROTOCOL_HINTS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final int COMMAND_API_SENTINEL = Integer.MAX_VALUE - 8;
    private static final int COMMAND_API_SENTINEL_WINDOW = 64;
    private static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 32767;

    private AutismPayloadSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends FriendlyByteBuf> StreamCodec<T, ?> wrapUnknownCodec(Identifier id, int maxBytes, StreamCodec<T, ?> original) {
        return StreamCodec.of((buf, payload) -> {
            if (!shouldCapturePayloadBytes()) {
                ((StreamCodec) original).encode(buf, payload);
                return;
            }

            byte[] rawBytes = extractUnknownCodecBytes(payload);
            if (rawBytes != null) {
                if (rawBytes.length > maxBytes) {
                    throw new IllegalArgumentException("Payload may not be larger than " + maxBytes + " bytes");
                }
                buf.writeBytes(rawBytes);
                return;
            }
            ((StreamCodec) original).encode(buf, payload);
        }, buf -> {
            int start = buf.readerIndex();
            Object decoded = ((StreamCodec) original).decode(buf);
            int end = buf.readerIndex();
            if (shouldCapturePayloadBytes() && decoded instanceof CustomPacketPayload payload) {
                byte[] rawBytes = new byte[Math.max(0, end - start)];
                if (rawBytes.length > 0) {
                    buf.getBytes(start, rawBytes);
                }
                rememberUnknownPayloadBytesOwned(payload, rawBytes);
            }
            return decoded;
        });
    }

    private static boolean shouldCapturePayloadBytes() {
        return AutismNetworkCaptureState.capturesPayloads(AutismNetworkCaptureState.codecState());
    }

    public static void rememberUnknownPayloadBytes(CustomPacketPayload payload, byte[] rawBytes) {
        if (PackHideState.isHardLocked()) return;
        if (payload == null) return;
        UNKNOWN_PAYLOAD_BYTES.put(payload, rawBytes == null ? new byte[0] : rawBytes.clone());
    }

    public static void rememberDecodedPayloadBytes(CustomPacketPayload payload, String channel, byte[] encodedBytes) {
        if (PackHideState.isHardLocked()) return;
        if (payload == null) return;
        byte[] body = extractBodyFromEncodedCustomPayloadBytes(encodedBytes, channel);
        rememberUnknownPayloadBytesOwned(payload, body);
    }

    private static void rememberUnknownPayloadBytesOwned(CustomPacketPayload payload, byte[] rawBytes) {
        if (payload == null) return;
        UNKNOWN_PAYLOAD_BYTES.put(payload, rawBytes == null ? AutismNetworkCaptureState.EMPTY_BYTES : rawBytes);
    }

    public static byte[] getRememberedUnknownPayloadBytes(CustomPacketPayload payload) {
        if (payload == null) return null;
        byte[] bytes = UNKNOWN_PAYLOAD_BYTES.get(payload);
        return bytes == null ? null : bytes.clone();
    }

    public static PayloadSnapshot snapshot(net.minecraft.network.protocol.Packet<?> packet, String direction) {
        if (packet == null) return null;
        CustomPacketPayload payload = extractPayload(packet);
        if (payload == null) return null;

        String channel = payloadChannel(payload);
        AutismPacketCapture.PacketSnapshot packetSnapshot = AutismPacketCapture.snapshot(packet);
        PayloadBytes payloadBytes = extractPayloadBytesFromPacketSnapshot(packetSnapshot, channel);
        if (payloadBytes == null) {
            payloadBytes = extractPayloadBytesWithProvenance(payload);
        }
        byte[] rawBytes = payloadBytes.bytes();
        Integer commandApiValue = tryParseCommandApiValue(payload, channel, rawBytes);
        String dump = buildDump(direction, channel, rawBytes, commandApiValue);
        String protocolPhase = packetSnapshot == null ? PAYLOAD_PROTOCOL_HINTS.getOrDefault(packet, "") : packetSnapshot.protocolPhase();
        int packetId = packetSnapshot == null ? -1 : packetSnapshot.numericPacketId();
        if (packetId < 0) {
            packetId = AutismPacketCapture.resolvePacketId(protocolPhase, direction, packet.type());
        }
        return new PayloadSnapshot(direction, channel, rawBytes, rawBytes.length, commandApiValue, dump,
            packet instanceof ServerboundCustomPayloadPacket, packet instanceof ClientboundCustomPayloadPacket,
            payload.getClass().getName(), protocolPhase, packetId, payloadBytes.provenance());
    }

    public static PayloadSnapshot snapshotFromEncodedPacketFrame(byte[] frameBytes, String direction, String protocolPhase) {
        if (frameBytes == null || frameBytes.length == 0) return null;
        byte[] frame = frameBytes.clone();
        int[] index = {0};
        Integer packetId = readVarInt(frame, index);
        if (packetId == null || packetId < 0 || index[0] >= frame.length) return null;

        boolean c2s = direction != null && direction.equalsIgnoreCase("C2S");
        boolean s2c = direction != null && direction.equalsIgnoreCase("S2C");
        int expectedId = AutismPacketCapture.resolvePacketId(protocolPhase, direction, c2s
            ? CommonPacketTypes.SERVERBOUND_CUSTOM_PAYLOAD
            : CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD);
        if (expectedId >= 0 && packetId != expectedId) return null;

        int channelStart = index[0];
        String channel = readMinecraftString(frame, index);
        if (channel == null || channel.isBlank()) return null;
        try {
            parseChannel(channel);
        } catch (Throwable ignored) {
            return null;
        }
        if (index[0] <= channelStart || index[0] > frame.length) return null;

        byte[] body = new byte[Math.max(0, frame.length - index[0])];
        if (body.length > 0) System.arraycopy(frame, index[0], body, 0, body.length);

        RawCustomPacketPayload payload = new RawCustomPacketPayload(parseChannel(channel), body);
        Integer commandApiValue = tryParseCommandApiValue(payload, channel, body);
        String dump = buildDump(direction, channel, body, commandApiValue);
        String payloadClassName = c2s
            ? ServerboundCustomPayloadPacket.class.getName()
            : ClientboundCustomPayloadPacket.class.getName();
        return new PayloadSnapshot(direction, channel, body, body.length, commandApiValue, dump,
            c2s, s2c, payloadClassName, protocolPhase, packetId,
            AutismPayloadEditorModel.Provenance.CAPTURED_EXACT.tag());
    }

    public static void rememberPayloadProtocol(net.minecraft.network.protocol.Packet<?> packet, String protocolPhase) {
        if (packet == null || protocolPhase == null || protocolPhase.isBlank()) return;
        PAYLOAD_PROTOCOL_HINTS.put(packet, protocolPhase);
    }

    public static CustomPacketPayload extractPayload(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket c2s) return c2s.payload();
        if (packet instanceof ClientboundCustomPayloadPacket s2c) return s2c.payload();
        return null;
    }

    public static String payloadChannel(CustomPacketPayload payload) {
        if (payload == null) return "";
        try {
            CustomPacketPayload.Type<?> type = payload.type();
            if (type != null && type.id() != null) {
                return type.id().toString();
            }
        } catch (Throwable ignored) {  }
        if (payload instanceof DiscardedPayload unknown && unknown.id() != null) {
            return unknown.id().toString();
        }
        return "";
    }

    public static byte[] extractPayloadBytes(CustomPacketPayload payload) {
        return extractPayloadBytesWithProvenance(payload).bytes();
    }

    private static PayloadBytes extractPayloadBytesWithProvenance(CustomPacketPayload payload) {
        if (payload == null) return new PayloadBytes(new byte[0], AutismPayloadEditorModel.Provenance.REENCODED_FROM_PAYLOAD.tag());
        if (payload instanceof RawCustomPacketPayload raw) return new PayloadBytes(raw.bytes(), AutismPayloadEditorModel.Provenance.CAPTURED_EXACT.tag());

        byte[] remembered = getRememberedUnknownPayloadBytes(payload);
        if (remembered != null) return new PayloadBytes(remembered, AutismPayloadEditorModel.Provenance.CAPTURED_EXACT.tag());

        byte[] codecBytes = extractPayloadBytesViaCodec(payload);
        if (codecBytes != null) return new PayloadBytes(codecBytes, AutismPayloadEditorModel.Provenance.REENCODED_FROM_PAYLOAD.tag());

        try {
            Method writeMethod = payload.getClass().getMethod("write", FriendlyByteBuf.class);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            writeMethod.invoke(payload, buf);
            return new PayloadBytes(toByteArray(buf), AutismPayloadEditorModel.Provenance.REENCODED_FROM_PAYLOAD.tag());
        } catch (Throwable ignored) {  }

        return new PayloadBytes(new byte[0], AutismPayloadEditorModel.Provenance.REENCODED_FROM_PAYLOAD.tag());
    }

    private static PayloadBytes extractPayloadBytesFromPacketSnapshot(AutismPacketCapture.PacketSnapshot packetSnapshot, String expectedChannel) {
        if (packetSnapshot == null) return null;
        byte[] bytes = packetSnapshot.plaintextBytes();
        if (bytes.length == 0) return null;
        int[] index = {0};
        Integer packetId = readVarInt(bytes, index);
        if (packetId == null || packetId < 0 || index[0] >= bytes.length) return null;
        String channel = readMinecraftString(bytes, index);
        if (channel == null || channel.isBlank()) return null;
        if (expectedChannel != null && !expectedChannel.isBlank() && !channel.equals(expectedChannel)) return null;
        byte[] body = new byte[Math.max(0, bytes.length - index[0])];
        if (body.length > 0) System.arraycopy(bytes, index[0], body, 0, body.length);
        return new PayloadBytes(body, AutismPayloadEditorModel.Provenance.CAPTURED_EXACT.tag());
    }

    public static boolean isCommandApiPayload(CustomPacketPayload payload) {
        if (payload == null) return false;
        String className = payload.getClass().getName();
        String simpleName = payload.getClass().getSimpleName();
        if (matchesCommandApiHint(className)) return true;
        if (matchesCommandApiHint(simpleName)) return true;
        String channel = payloadChannel(payload);
        if (matchesCommandApiHint(channel)) return true;
        return isSingleIntPayload(payload.getClass());
    }

    public static boolean isSingleIntPayload(Class<?> clazz) {
        if (clazz == null) return false;
        if (!clazz.isRecord()) return false;
        RecordComponent[] components = clazz.getRecordComponents();
        if (components.length != 1) return false;
        return components[0].getType() == int.class || components[0].getType() == Integer.class;
    }

    public static Integer tryParseCommandApiValue(CustomPacketPayload payload, String channel, byte[] rawBytes) {
        Integer value = readInt32(rawBytes);
        if (value == null) return null;
        return isLikelyCommandApiPayload(payload, channel, rawBytes, value) ? value : null;
    }

    public static String summarizeForLogger(PayloadSnapshot snapshot, boolean includeCommandValue) {
        if (snapshot == null) return "";
        StringBuilder sb = new StringBuilder();
        String compactChannel = compactChannel(snapshot.channel());
        if (!compactChannel.isBlank()) {
            sb.append('[').append(compactChannel).append(']');
        }
        if (snapshot.sizeBytes() > 0 || sb.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(snapshot.sizeBytes()).append('B');
        }
        if (includeCommandValue && snapshot.commandApiValue() != null) {
            sb.append(" cmd=").append(snapshot.commandApiValue());
        }
        String guess = payloadPreviewHint(snapshot.rawBytes());
        if (!guess.isBlank()) {
            sb.append(" ").append(guess);
        }
        return sb.toString();
    }

    private static String payloadPreviewHint(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String minecraftString = decodeMinecraftStringPayload(bytes);
        if (minecraftString != null && !minecraftString.isBlank()) {
            return "mc=\"" + trimPayloadHint(minecraftString) + "\"";
        }
        String text = summarizeLikelyUtf8Text(bytes, 32);
        if (text != null && !text.isBlank()) {
            return "utf8=\"" + trimPayloadHint(text) + "\"";
        }
        return "";
    }

    private static String trimPayloadHint(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() <= 32) return text;
        return text.substring(0, 29) + "...";
    }

    public static String compactChannel(String channel) {
        String value = channel == null ? "" : channel.trim();
        if (value.isEmpty() || value.length() <= 30) return value;

        int colon = value.indexOf(':');
        if (colon > 0 && colon < value.length() - 1) {
            String namespace = value.substring(0, colon);
            String path = value.substring(colon + 1);
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('.'));
            String tail = slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
            String compact = namespace + ":" + tail;
            if (compact.length() <= 30) return compact;
        }

        return value.substring(0, 14) + "..." + value.substring(value.length() - 12);
    }

    public static byte[] parsePayloadBytes(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return new byte[0];

        String compactHex = value.replace("0x", "").replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        if (!compactHex.isEmpty() && compactHex.length() % 2 == 0 && compactHex.matches("(?i)[0-9a-f]+")) {
            byte[] out = new byte[compactHex.length() / 2];
            for (int i = 0; i < compactHex.length(); i += 2) {
                out[i / 2] = (byte) Integer.parseInt(compactHex.substring(i, i + 2), 16);
            }
            return out;
        }

        return Base64.getDecoder().decode(value);
    }

    public static byte[] extractBodyFromEncodedCustomPayloadBytes(byte[] encodedBytes, String expectedChannel) {
        if (encodedBytes == null || encodedBytes.length == 0) return new byte[0];
        byte[] raw = encodedBytes.clone();
        int[] index = {0};
        String channel = readMinecraftString(raw, index);
        if (channel == null || channel.isBlank()) return raw;
        if (expectedChannel != null && !expectedChannel.isBlank() && !channel.equals(expectedChannel)) {
            return raw;
        }
        byte[] body = new byte[Math.max(0, raw.length - index[0])];
        if (body.length > 0) System.arraycopy(raw, index[0], body, 0, body.length);
        return body;
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String toBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static boolean isBrandChannel(String channel) {
        return channel != null && "minecraft:brand".equals(channel.trim());
    }

    public static String defaultBrandPayloadString() {
        try {
            String brand = ClientBrandRetriever.getClientModName();
            if (brand != null && !brand.isBlank()) return brand;
        } catch (Throwable ignored) {  }
        return "vanilla";
    }

    public static byte[] encodeMinecraftStringPayload(String value) {
        byte[] stringBytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        byte[] lengthBytes = encodeVarIntBytes(stringBytes.length);
        byte[] out = new byte[lengthBytes.length + stringBytes.length];
        System.arraycopy(lengthBytes, 0, out, 0, lengthBytes.length);
        System.arraycopy(stringBytes, 0, out, lengthBytes.length, stringBytes.length);
        return out;
    }

    public static String decodeMinecraftStringPayload(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return null;
        int[] index = {0};
        Integer length = readVarInt(rawBytes, index);
        if (length == null || length < 0) return null;
        if (index[0] + length != rawBytes.length) return null;
        try {
            return new String(rawBytes, index[0], length, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String toCompactHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) return "";
        int shown = maxBytes <= 0 ? bytes.length : Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(shown * 3 + 16);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        if (shown < bytes.length) {
            sb.append(" ... +").append(bytes.length - shown).append("B");
        }
        return sb.toString();
    }

    public static String decodeLikelyUtf8Text(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!isLikelyReadableText(text)) return "";
        return text;
    }

    public static List<String> extractRegisterChannelList(String channel, byte[] rawBytes) {
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        if (!"minecraft:register".equals(normalizedChannel)
            && !"register".equals(normalizedChannel)
            && !"minecraft:unregister".equals(normalizedChannel)
            && !"unregister".equals(normalizedChannel)) {
            return List.of();
        }
        if (rawBytes == null || rawBytes.length == 0) return List.of();
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        addRegisterTokens(channels, decodeLikelyUtf8Text(rawBytes));
        String mcString = decodeMinecraftStringPayload(rawBytes);
        if (mcString != null) addRegisterTokens(channels, mcString);
        String javaUtf = decodeJavaWriteUtfPayload(rawBytes);
        if (javaUtf != null) addRegisterTokens(channels, javaUtf);
        return List.copyOf(channels);
    }

    private static void addRegisterTokens(Set<String> out, String text) {
        if (out == null || text == null || text.isBlank()) return;
        String[] parts = text.split("[\\u0000,;\\s]+");
        for (String part : parts) {
            String token = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (token.isBlank()) continue;
            try {
                Identifier id = Identifier.tryParse(token);
                if (id != null && token.indexOf(':') > 0
                    && !"minecraft:register".equals(token)
                    && !"minecraft:unregister".equals(token)) {
                    out.add(token);
                }
            } catch (Throwable ignored) {  }
        }
    }

    public static String summarizeLikelyUtf8Text(byte[] bytes, int maxChars) {
        String text = decodeLikelyUtf8Text(bytes);
        if (text.isEmpty()) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (maxChars > 0 && normalized.length() > maxChars) {
            return normalized.substring(0, Math.max(0, maxChars - 3)) + "...";
        }
        return normalized;
    }

    public static ServerboundCustomPayloadPacket createC2SPacket(String channel, byte[] bytes) {
        Identifier id = parseChannel(channel);
        AutismPayloadChannelSubscriptionManager.rememberRequestedChannel(id.toString());
        RawCustomPacketPayload payload = new RawCustomPacketPayload(id, bytes);
        rememberUnknownPayloadBytes(payload, bytes);
        return new ServerboundCustomPayloadPacket(payload);
    }

    public static boolean sendPayload(String channel, byte[] bytes) {
        return sendPayload(channel, bytes, "");
    }

    public static boolean sendPayload(String channel, byte[] bytes, String sourceProtocol) {
        return sendPayloadInternal(channel, bytes, sourceProtocol, true);
    }

    public static boolean sendPayloadSilent(String channel, byte[] bytes, String sourceProtocol) {
        return sendPayloadInternal(channel, bytes, sourceProtocol, false);
    }

    private static boolean sendPayloadInternal(String channel, byte[] bytes, String sourceProtocol, boolean notifyUser) {
        if (PackHideState.isHardLocked()) return false;
        try {
            validateServerboundPayload(channel, bytes, sourceProtocol);
            ServerboundCustomPayloadPacket packet = createC2SPacket(channel, bytes);
            preflightEncode(packet);
            AutismPacketSender.send(packet);
            return true;
        } catch (Throwable t) {
            if (notifyUser) {
                AutismClientMessaging.sendPrefixed("§cFailed to send payload: " + safeMessage(t));
            }
            return false;
        }
    }

    private static void validateServerboundPayload(String channel, byte[] bytes, String sourceProtocol) {
        Identifier id = parseChannel(channel);
        if (isRegistrationControlChannel(id.toString())
            && !AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
            throw new IllegalStateException("Payload registration is locked. Unlock it in Packet Logger > Payload > Channels.");
        }
        int size = bytes == null ? 0 : bytes.length;
        if (size > MAX_SERVERBOUND_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload is " + size + " bytes, max is " + MAX_SERVERBOUND_PAYLOAD_BYTES);
        }
        if (isConfigurationProtocol(sourceProtocol) && isClientInPlay()) {
            throw new IllegalStateException("Configuration payloads must be sent during join/configuration, not after spawning.");
        }
    }

    private static boolean isRegistrationControlChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("minecraft:register")
            || normalized.equals("minecraft:unregister")
            || normalized.equals("register")
            || normalized.equals("unregister");
    }

    private static boolean isConfigurationProtocol(String sourceProtocol) {
        if (sourceProtocol == null || sourceProtocol.isBlank()) return false;
        String normalized = sourceProtocol.toLowerCase(Locale.ROOT);
        return normalized.contains("config");
    }

    private static boolean isClientInPlay() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    private static void preflightEncode(ServerboundCustomPayloadPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buf, packet);
        } finally {
            buf.release();
        }
    }

    private static void writeVarInt(io.netty.buffer.ByteBuf buf, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static void writeString(io.netty.buffer.ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static void writeRawPayloadToBuffer(RawCustomPacketPayload payload, io.netty.buffer.ByteBuf buf) {

        String channelStr = payload.id().toString();
        byte[] channelBytes = channelStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(buf, channelBytes.length);
        buf.writeBytes(channelBytes);

        byte[] data = payload.bytes();
        buf.writeBytes(data);
    }

    public static Identifier parseChannel(String channel) {
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("Channel cannot be blank");
        try {
            return Identifier.parse(channel.trim());
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid channel identifier: " + channel);
        }
    }

    public static PayloadSnapshot snapshotFromEntry(AutismPacketLoggerOverlay.LogEntry entry) {
        if (entry == null) return null;
        PayloadSnapshot cached = entry.payloadSnapshot;
        if (entry.packetRef == null) return cached;
        PayloadSnapshot refreshed = snapshot(entry.packetRef, entry.direction);
        if (cached == null) return refreshed;
        if (shouldRefreshSnapshot(cached, refreshed)) return refreshed;
        return cached;
    }

    public static autismclient.util.macro.PayloadAction seedActionFromEntry(AutismPacketLoggerOverlay.LogEntry entry) {
        PayloadSnapshot snapshot = snapshotFromEntry(entry);
        if (snapshot == null) return null;
        autismclient.util.macro.PayloadAction action = new autismclient.util.macro.PayloadAction();
        action.channel = snapshot.channel();
        action.payloadData = toHex(snapshot.rawBytes());
        action.payloadClassName = "";
        CustomPacketPayload payload = entry == null || entry.packetRef == null ? null : extractPayload(entry.packetRef);
        action.payloadJson = AutismPayloadJsonSupport.buildEditableJson(snapshot.direction(), action.channel, payload == null
            ? new RawCustomPacketPayload(parseChannel(action.channel), snapshot.rawBytes())
            : payload);
        action.javaSource = "";
        action.payloadScriptEnabled = false;
        action.commandApiRecognized = snapshot.commandApiValue() != null;
        action.commandApiOverride = false;
        action.commandApiValue = snapshot.commandApiValue() == null ? 0 : snapshot.commandApiValue();
        action.sourceDirection = snapshot.direction();
        action.sourceProtocol = snapshot.protocolPhase();
        action.payloadDirection = snapshot.direction();
        action.payloadPhase = snapshot.protocolPhase() != null && snapshot.protocolPhase().toLowerCase(Locale.ROOT).contains("config")
            ? "CONFIGURATION"
            : "PLAY";
        action.payloadPacketId = snapshot.packetId();
        action.payloadProvenance = snapshot.provenance();
        action.payloadFields = AutismPayloadTemplate.serializeFields(inferEditablePayloadFields(snapshot.rawBytes()));
        action.payloadEncodingMode = inferEditablePayloadMode(action.channel, snapshot.rawBytes());
        return action;
    }

    public static java.util.List<AutismPayloadTemplate.Field> inferEditablePayloadFields(byte[] bytes) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        String mcString = decodeMinecraftStringPayload(safeBytes);
        if (mcString != null) {
            return java.util.List.of(new AutismPayloadTemplate.Field(AutismPayloadTemplate.FieldType.MINECRAFT_STRING, mcString, true));
        }
        String writeUtf = decodeJavaWriteUtfPayload(safeBytes);
        if (writeUtf != null) {
            return java.util.List.of(new AutismPayloadTemplate.Field(AutismPayloadTemplate.FieldType.JAVA_WRITE_UTF, writeUtf, true));
        }
        String utf8 = decodeLikelyUtf8Text(safeBytes);
        if (!utf8.isBlank() || safeBytes.length == 0) {
            return java.util.List.of(new AutismPayloadTemplate.Field(AutismPayloadTemplate.FieldType.STRING_BYTES, utf8, true));
        }
        return java.util.List.of(new AutismPayloadTemplate.Field(AutismPayloadTemplate.FieldType.HEX_BYTES, toHex(safeBytes), true));
    }

    public static String inferEditablePayloadMode(String channel, byte[] bytes) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        if (decodeMinecraftStringPayload(safeBytes) != null) return "MINECRAFT_BYTEBUF";
        if (decodeJavaWriteUtfPayload(safeBytes) != null) return "JAVA_DATA_OUTPUT";
        if (!decodeLikelyUtf8Text(safeBytes).isBlank() || safeBytes.length == 0) return "RAW_UTF8";
        return "MANUAL_HEX";
    }

    public static String decodeJavaWriteUtfPayload(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String value = in.readUTF();
            if (in.available() != 0) return null;
            return value;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    public static byte[] withCommandApiValue(byte[] source, int value) {
        byte[] bytes = source == null ? new byte[4] : source.clone();
        if (bytes.length < 4) bytes = new byte[4];
        ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(value);
        return bytes;
    }

    public static byte[] toByteArray(FriendlyByteBuf buf) {
        if (buf == null) return new byte[0];
        byte[] bytes = new byte[buf.writerIndex()];
        if (bytes.length > 0) {
            buf.getBytes(0, bytes);
        }
        return bytes;
    }

    public static String safeMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private static byte[] extractUnknownCodecBytes(Object payload) {
        if (payload instanceof RawCustomPacketPayload raw) return raw.bytes();
        if (payload instanceof CustomPacketPayload customPayload) return getRememberedUnknownPayloadBytes(customPayload);
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static byte[] extractPayloadBytesViaCodec(CustomPacketPayload payload) {
        StreamCodec codec = findPayloadCodec(payload);
        if (codec == null) return null;
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            codec.encode(buf, payload);
            return toByteArray(buf);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static StreamCodec<?, ?> findPayloadCodec(CustomPacketPayload payload) {
        return payload == null ? null : findPayloadCodec(payload.getClass());
    }

    public static StreamCodec<?, ?> findPayloadCodec(Class<?> payloadClass) {
        if (payloadClass == null) return null;
        Object codecValue = findNamedStaticValue(payloadClass, "CODEC", StreamCodec.class);
        if (codecValue == null) {
            codecValue = findStaticValue(payloadClass, StreamCodec.class);
        }
        if (codecValue instanceof StreamCodec<?, ?> codec) {
            return codec;
        }
        return null;
    }

    private static Object findNamedStaticValue(Class<?> owner, String fieldName, Class<?> expectedType) {
        if (owner == null || fieldName == null || fieldName.isBlank() || expectedType == null) return null;
        Class<?> cursor = owner;
        while (cursor != null && cursor != Object.class) {
            try {
                java.lang.reflect.Field field = cursor.getDeclaredField(fieldName);
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) return null;
                if (!expectedType.isAssignableFrom(field.getType())) return null;
                if (!field.canAccess(null)) field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {  }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static Object findStaticValue(Class<?> owner, Class<?> expectedType) {
        if (owner == null || expectedType == null) return null;
        Class<?> cursor = owner;
        while (cursor != null && cursor != Object.class) {
            for (java.lang.reflect.Field field : cursor.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!expectedType.isAssignableFrom(field.getType())) continue;
                try {
                    if (!field.canAccess(null)) field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null) return value;
                } catch (Throwable ignored) {  }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static boolean isLikelyCommandApiPayload(CustomPacketPayload payload, String channel, byte[] rawBytes, Integer intValue) {
        if (matchesCommandApiHint(payload == null ? null : payload.getClass().getName())) return true;
        if (matchesCommandApiHint(payload == null ? null : payload.getClass().getSimpleName())) return true;
        if (matchesCommandApiHint(channel)) return true;
        if (rawBytes == null || rawBytes.length != 4 || intValue == null) return false;
        return Math.abs((long) intValue - COMMAND_API_SENTINEL) <= COMMAND_API_SENTINEL_WINDOW;
    }

    private static boolean matchesCommandApiHint(String value) {
        String normalized = normalizeHint(value);
        if (normalized.isEmpty()) return false;
        return normalized.contains("commandapi") || (normalized.contains("command") && normalized.contains("api"));
    }

    private static String normalizeHint(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Integer readInt32(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length < 4) return null;
        try {
            return ByteBuffer.wrap(rawBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] encodeVarIntBytes(int value) {
        byte[] out = new byte[5];
        int index = 0;
        while ((value & 0xFFFFFF80) != 0) {
            out[index++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out[index++] = (byte) value;
        byte[] trimmed = new byte[index];
        System.arraycopy(out, 0, trimmed, 0, index);
        return trimmed;
    }

    private static Integer readVarInt(byte[] bytes, int[] indexRef) {
        if (bytes == null || indexRef == null || indexRef.length == 0) return null;
        int value = 0;
        int position = 0;
        int index = Math.max(0, indexRef[0]);
        while (index < bytes.length) {
            byte currentByte = bytes[index++];
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                indexRef[0] = index;
                return value;
            }
            position += 7;
            if (position >= 32) return null;
        }
        return null;
    }

    private static String readMinecraftString(byte[] bytes, int[] indexRef) {
        Integer length = readVarInt(bytes, indexRef);
        if (length == null || length < 0) return null;
        int index = Math.max(0, indexRef[0]);
        if (index + length > bytes.length) return null;
        try {
            String value = new String(bytes, index, length, StandardCharsets.UTF_8);
            indexRef[0] = index + length;
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean shouldRefreshSnapshot(PayloadSnapshot cached, PayloadSnapshot refreshed) {
        if (cached == null || refreshed == null) return false;
        if ((cached.channel() == null || cached.channel().isBlank()) && refreshed.channel() != null && !refreshed.channel().isBlank()) {
            return true;
        }
        if ((cached.payloadClassName() == null || cached.payloadClassName().isBlank())
            && refreshed.payloadClassName() != null && !refreshed.payloadClassName().isBlank()) {
            return true;
        }
        return cached.rawBytes().length == 0 && refreshed.rawBytes().length > 0;
    }

    private static boolean isLikelyReadableText(String text) {
        if (text == null || text.isBlank()) return false;
        int printable = 0;
        int suspicious = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') {
                suspicious += 3;
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t') {
                printable++;
                continue;
            }
            if (Character.isISOControl(c)) {
                suspicious++;
                continue;
            }
            printable++;
        }
        if (printable == 0) return false;
        return suspicious * 5 < printable;
    }

    private static String buildDump(String direction, String channel, byte[] rawBytes, Integer commandApiValue) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"direction\":\"").append(escape(direction)).append("\"");
        sb.append(",\"channel\":\"").append(escape(channel)).append("\"");
        sb.append(",\"size\":").append(rawBytes == null ? 0 : rawBytes.length);
        sb.append(",\"payloadHex\":\"").append(escape(toHex(rawBytes))).append("\"");
        sb.append(",\"payloadBase64\":\"").append(escape(toBase64(rawBytes))).append("\"");
        if (commandApiValue != null) {
            sb.append(",\"commandApiValue\":").append(commandApiValue);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record PayloadBytes(byte[] bytes, String provenance) {
        private PayloadBytes {
            bytes = bytes == null ? new byte[0] : bytes.clone();
            provenance = provenance == null || provenance.isBlank()
                ? AutismPayloadEditorModel.Provenance.REENCODED_FROM_PAYLOAD.tag()
                : provenance;
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? new byte[0] : bytes.clone();
        }
    }

    public record PayloadSnapshot(String direction, String channel, byte[] rawBytes, int sizeBytes, Integer commandApiValue,
                                  String rawDump, boolean c2s, boolean s2c, String payloadClassName, String protocolPhase,
                                  int packetId, String provenance) {
        public byte[] rawBytes() {
            return rawBytes == null ? new byte[0] : rawBytes.clone();
        }
    }

    public record RawCustomPacketPayload(Identifier id, byte[] bytes) implements CustomPacketPayload {
        public RawCustomPacketPayload {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public CustomPacketPayload.Type<RawCustomPacketPayload> type() {
            return new CustomPacketPayload.Type<>(id);
        }

        public void write(FriendlyByteBuf buf) {
            if (bytes.length > 0) {
                buf.writeBytes(bytes);
            }
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
