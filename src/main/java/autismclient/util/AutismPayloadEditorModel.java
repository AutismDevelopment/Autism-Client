package autismclient.util;

import autismclient.util.macro.PayloadAction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutismPayloadEditorModel {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    public enum Provenance {
        CAPTURED_EXACT("capturedExact"),
        REENCODED_FROM_PAYLOAD("reencodedFromPayload"),
        USER_EDITED("userEdited");

        private final String tag;

        Provenance(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }

        public static Provenance parse(String raw) {
            if (raw == null || raw.isBlank()) return USER_EDITED;
            String normalized = raw.strip().toLowerCase(Locale.ROOT);
            for (Provenance value : values()) {
                if (value.tag.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) return value;
            }
            return USER_EDITED;
        }
    }

    public String direction = "C2S";
    public String phase = "PLAY";
    public String channel = "minecraft:brand";
    public String packetClass = "";
    public int packetId = -1;
    public String sourceProtocol = "";
    public String encodingMode = "";
    public List<AutismPayloadTemplate.Field> bodyFields = new ArrayList<>();
    public byte[] bodyBytes = new byte[0];
    public byte[] lastValidBytes = new byte[0];
    public Provenance provenance = Provenance.USER_EDITED;
    public String validationError = "";
    public String decodedKind = "Binary";
    public boolean commandApiRecognized = false;
    public boolean commandApiOverride = false;
    public int commandApiValue = Integer.MAX_VALUE - 8;
    private long revision = 1L;
    private long packetScriptRevision = Long.MIN_VALUE;
    private String cachedPacketScript = "";
    private long bodyHexRevision = Long.MIN_VALUE;
    private String cachedBodyHex = "";
    private long utf8Revision = Long.MIN_VALUE;
    private String cachedUtf8 = "";
    private long logicalTextRevision = Long.MIN_VALUE;
    private String cachedLogicalText = "";

    public long revision() {
        return revision;
    }

    public void touch() {
        revision++;
    }

    public static AutismPayloadEditorModel fromAction(PayloadAction action) {
        AutismPayloadEditorModel model = new AutismPayloadEditorModel();
        if (action == null) {
            model.bodyFields = new ArrayList<>(AutismPayloadTemplate.defaultBrandTemplate().fields());
            model.rebuildFromFields(false);
            return model;
        }
        model.direction = cleanDirection(action.payloadDirection == null || action.payloadDirection.isBlank()
            ? action.sourceDirection
            : action.payloadDirection);
        model.phase = cleanPhase(action.payloadPhase);
        model.channel = action.channel == null || action.channel.isBlank() ? "minecraft:brand" : action.channel.strip();
        model.packetClass = action.payloadClassName == null ? "" : action.payloadClassName;
        model.packetId = action.payloadPacketId;
        model.sourceProtocol = action.sourceProtocol == null ? "" : action.sourceProtocol;
        model.encodingMode = action.payloadEncodingMode == null ? "" : action.payloadEncodingMode;
        model.provenance = Provenance.parse(action.payloadProvenance);
        model.commandApiRecognized = action.commandApiRecognized;
        model.commandApiOverride = action.commandApiOverride;
        model.commandApiValue = action.commandApiValue;

        byte[] raw = new byte[0];
        boolean haveRaw = false;
        if (action.payloadData != null && !action.payloadData.isBlank()) {
            try {
                raw = AutismPayloadSupport.parsePayloadBytes(action.payloadData);
                haveRaw = true;
            } catch (Throwable ignored) {  }
        }
        List<AutismPayloadTemplate.Field> fields = AutismPayloadTemplate.parseFields(action.payloadFields);
        if (!fields.isEmpty()) {
            model.bodyFields = new ArrayList<>(fields);
            if (!model.rebuildFromFields(false) && haveRaw) {
                model.setBodyBytes(raw, Provenance.parse(action.payloadProvenance));
            }
        } else {
            model.setBodyBytes(raw, Provenance.parse(action.payloadProvenance));
        }
        return model;
    }

    public PayloadAction toAction(PayloadAction base) {
        PayloadAction action = new PayloadAction();
        if (base != null) action.fromTag(base.toTag());
        action.channel = channel == null || channel.isBlank() ? "minecraft:brand" : channel.strip();
        action.payloadDirection = cleanDirection(direction);
        action.sourceDirection = action.payloadDirection;
        action.payloadPhase = cleanPhase(phase);
        action.sourceProtocol = sourceProtocol == null || sourceProtocol.isBlank()
            ? ("CONFIGURATION".equals(action.payloadPhase) ? "CONFIGURATION" : "")
            : sourceProtocol;
        action.payloadClassName = packetClass == null ? "" : packetClass;
        action.payloadPacketId = packetId;
        action.payloadProvenance = provenance == null ? Provenance.USER_EDITED.tag() : provenance.tag();
        action.payloadEncodingMode = inferMode();
        action.payloadFields = AutismPayloadTemplate.serializeFields(bodyFields);
        action.payloadData = AutismPayloadSupport.toHex(bodyBytes);
        action.payloadJson = "";
        action.commandApiRecognized = commandApiRecognized;
        action.commandApiOverride = commandApiOverride;
        action.commandApiValue = commandApiValue;
        action.payloadScriptEnabled = false;
        action.javaSource = "";
        return action;
    }

    public AutismPayloadTemplate.Template toTemplate() {
        return new AutismPayloadTemplate.Template(
            channel,
            AutismPayloadTemplate.PayloadDirection.parse(direction),
            AutismPayloadTemplate.PayloadPhase.parse(phase),
            AutismPayloadTemplate.EncodingMode.parse(inferMode(), channel),
            bodyFields == null ? List.of() : bodyFields
        );
    }

    public boolean applyPacketScript(String script) {
        AutismPayloadEditorModel parsed = copy();
        List<String> writerLines = new ArrayList<>();
        String[] lines = (script == null ? "" : script).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty()) {
                writerLines.add(raw == null ? "" : raw);
                continue;
            }
            int split = firstDelimiter(line);
            if (split > 0) {
                String key = line.substring(0, split).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(split + 1).strip();
                switch (key) {
                    case "direction" -> parsed.direction = cleanDirection(value);
                    case "phase" -> parsed.phase = cleanPhase(value);
                    case "channel" -> parsed.channel = value.isBlank() ? "minecraft:brand" : value;
                    case "packetclass", "packet_class" -> parsed.packetClass = value;
                    case "packetid", "packet_id" -> parsed.packetId = parseIntOr(value, -1);
                    case "sourceprotocol", "source_protocol" -> parsed.sourceProtocol = value;
                    default -> writerLines.add(raw == null ? "" : raw);
                }
            } else {
                writerLines.add(raw == null ? "" : raw);
            }
        }
        parsed.bodyFields = new ArrayList<>(AutismPayloadTemplate.parseFields(String.join("\n", writerLines)));
        if (!parsed.rebuildFromFields(true)) {
            validationError = parsed.validationError;
            touch();
            return false;
        }
        copyFrom(parsed);
        provenance = Provenance.USER_EDITED;
        touch();
        return true;
    }

    public boolean applyBodyHex(String hex) {
        try {
            setBodyBytes(parseStrictHex(hex), Provenance.USER_EDITED);
            validationError = "";
            return true;
        } catch (Throwable t) {
            validationError = AutismPayloadSupport.safeMessage(t);
            touch();
            return false;
        }
    }

    public boolean applyUtf8(String text) {
        setBodyBytes((text == null ? "" : text).getBytes(StandardCharsets.UTF_8), Provenance.USER_EDITED);
        return true;
    }

    public boolean applyLogicalText(String text) {
        AutismPayloadEditorModel parsed = copy();
        String bodyHex = null;
        String logicalHex = null;
        String[] lines = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = firstDelimiter(line);
            if (split < 0) continue;
            String key = line.substring(0, split).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(split + 1).strip();
            switch (key) {
                case "direction" -> parsed.direction = cleanDirection(value);
                case "phase" -> parsed.phase = cleanPhase(value);
                case "channel" -> parsed.channel = value.isBlank() ? parsed.channel : value;
                case "packetid", "packet_id" -> parsed.packetId = parseIntOr(value, -1);
                case "bodyhex", "body_hex" -> bodyHex = value;
                case "logicalhex", "logical_hex" -> logicalHex = value;
                case "packetclass", "packet_class" -> parsed.packetClass = value;
                case "sourceprotocol", "source_protocol" -> parsed.sourceProtocol = value;
            }
        }
        try {
            if (logicalHex != null && !logicalHex.isBlank()) {
                LogicalPacket logical = parseLogicalPacketHex(logicalHex);
                if (parsed.packetId >= 0 && logical.packetId != parsed.packetId) {
                    throw new IllegalArgumentException("Logical packet id does not match packetId");
                }
                parsed.packetId = logical.packetId;
                parsed.channel = logical.channel;
                parsed.setBodyBytes(logical.body, Provenance.USER_EDITED);
            } else if (bodyHex != null) {
                parsed.setBodyBytes(parseStrictHex(bodyHex), Provenance.USER_EDITED);
            }
        } catch (Throwable t) {
            validationError = AutismPayloadSupport.safeMessage(t);
            touch();
            return false;
        }
        copyFrom(parsed);
        provenance = Provenance.USER_EDITED;
        validationError = "";
        touch();
        return true;
    }

    public String packetScript() {
        if (packetScriptRevision == revision) return cachedPacketScript;
        StringBuilder sb = new StringBuilder();
        sb.append("direction = ").append(cleanDirection(direction)).append('\n');
        sb.append("phase = ").append(cleanPhase(phase)).append('\n');
        sb.append("channel = ").append(channel == null || channel.isBlank() ? "minecraft:brand" : channel.strip()).append('\n');
        if (packetId >= 0) sb.append("packetId = ").append(packetId).append('\n');
        if (packetClass != null && !packetClass.isBlank()) sb.append("packetClass = ").append(packetClass).append('\n');
        if (sourceProtocol != null && !sourceProtocol.isBlank()) sb.append("sourceProtocol = ").append(sourceProtocol).append('\n');
        sb.append('\n');
        sb.append(AutismPayloadTemplate.serializeFields(bodyFields));
        cachedPacketScript = sb.toString();
        packetScriptRevision = revision;
        return cachedPacketScript;
    }

    public String bodyHexMultiline() {
        if (bodyBytes == null || bodyBytes.length == 0) return "";
        if (bodyHexRevision == revision) return cachedBodyHex;
        StringBuilder sb = new StringBuilder(bodyBytes.length * 3 + bodyBytes.length / 16 * 10);
        for (int i = 0; i < bodyBytes.length; i++) {
            if (i > 0) sb.append(i % 16 == 0 ? '\n' : ' ');
            int value = bodyBytes[i] & 0xFF;
            sb.append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
        }
        cachedBodyHex = sb.toString();
        bodyHexRevision = revision;
        return cachedBodyHex;
    }

    public String utf8View() {
        if (utf8Revision == revision) return cachedUtf8;
        String text = AutismPayloadSupport.decodeLikelyUtf8Text(bodyBytes);
        cachedUtf8 = text == null ? "" : text;
        utf8Revision = revision;
        return cachedUtf8;
    }

    public String logicalText() {
        if (logicalTextRevision == revision) return cachedLogicalText;
        StringBuilder sb = new StringBuilder();
        sb.append("direction = ").append(cleanDirection(direction)).append('\n');
        sb.append("phase = ").append(cleanPhase(phase)).append('\n');
        sb.append("channel = ").append(channel == null ? "" : channel).append('\n');
        sb.append("packetId = ").append(packetId >= 0 ? packetId : "unavailable").append('\n');
        if (packetClass != null && !packetClass.isBlank()) sb.append("packetClass = ").append(packetClass).append('\n');
        if (sourceProtocol != null && !sourceProtocol.isBlank()) sb.append("sourceProtocol = ").append(sourceProtocol).append('\n');
        sb.append("provenance = ").append(provenance == null ? Provenance.USER_EDITED.tag() : provenance.tag()).append('\n');
        sb.append("bodyBytes = ").append(bodyBytes == null ? 0 : bodyBytes.length).append('\n');
        sb.append("decoded = ").append(decodedSummary()).append('\n');
        sb.append("bodyHex = ").append(AutismPayloadSupport.toHex(bodyBytes)).append('\n');
        if (packetId >= 0) {
            sb.append("logicalHex = ").append(AutismPayloadSupport.toHex(logicalBytes())).append('\n');
        } else {
            sb.append("logicalHex = unavailable; packet id was not captured/resolved\n");
        }
        cachedLogicalText = sb.toString();
        logicalTextRevision = revision;
        return cachedLogicalText;
    }

    public byte[] logicalBytes() {
        if (packetId < 0) return new byte[0];
        byte[] packet = encodeVarIntBytes(packetId);
        byte[] channelBytes = AutismPayloadSupport.encodeMinecraftStringPayload(channel == null ? "" : channel);
        byte[] body = bodyBytes == null ? new byte[0] : bodyBytes;
        byte[] out = new byte[packet.length + channelBytes.length + body.length];
        System.arraycopy(packet, 0, out, 0, packet.length);
        System.arraycopy(channelBytes, 0, out, packet.length, channelBytes.length);
        System.arraycopy(body, 0, out, packet.length + channelBytes.length, body.length);
        return out;
    }

    public String decodedSummary() {
        if (bodyFields == null || bodyFields.isEmpty()) return "empty";
        return bodyFields.get(0).displayLine();
    }

    private boolean rebuildFromFields(boolean strict) {
        AutismPayloadTemplate.Template template = toTemplate();
        AutismPayloadTemplate.BuildResult built = template.build();
        if (!built.ok()) {
            validationError = String.join("; ", built.errors());
            if (strict) {
                touch();
                return false;
            }
        }
        channel = template.channel();
        direction = template.direction().name();
        phase = template.phase().name();
        encodingMode = template.mode().name();
        bodyBytes = built.bytes();
        lastValidBytes = bodyBytes.clone();
        decodedKind = inferDecodedKind(bodyBytes);
        refreshCommandApiFromBytes();
        validationError = "";
        touch();
        return true;
    }

    private void setBodyBytes(byte[] bytes, Provenance source) {
        bodyBytes = bytes == null ? new byte[0] : bytes.clone();
        lastValidBytes = bodyBytes.clone();
        bodyFields = new ArrayList<>(AutismPayloadSupport.inferEditablePayloadFields(bodyBytes));
        encodingMode = AutismPayloadSupport.inferEditablePayloadMode(channel, bodyBytes);
        decodedKind = inferDecodedKind(bodyBytes);
        provenance = source == null ? Provenance.USER_EDITED : source;
        refreshCommandApiFromBytes();
        validationError = "";
        touch();
    }

    private void refreshCommandApiFromBytes() {
        Integer parsed = AutismPayloadSupport.tryParseCommandApiValue(null, channel, bodyBytes);
        if (parsed != null) {
            commandApiRecognized = true;
            if (!commandApiOverride) {
                commandApiValue = parsed;
            }
        } else if (!commandApiOverride) {
            commandApiRecognized = false;
        }
    }

    private String inferMode() {
        if (encodingMode != null && !encodingMode.isBlank()) return encodingMode;
        return AutismPayloadSupport.inferEditablePayloadMode(channel, bodyBytes);
    }

    private static String inferDecodedKind(byte[] bytes) {
        if (AutismPayloadSupport.decodeMinecraftStringPayload(bytes) != null) return "Minecraft String";
        if (AutismPayloadSupport.decodeJavaWriteUtfPayload(bytes) != null) return "Java writeUTF";
        String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
        if (utf8 != null && !utf8.isBlank()) return "Raw UTF-8";
        return "Binary Hex";
    }

    private AutismPayloadEditorModel copy() {
        AutismPayloadEditorModel copy = new AutismPayloadEditorModel();
        copy.copyFrom(this);
        return copy;
    }

    private void copyFrom(AutismPayloadEditorModel other) {
        direction = other.direction;
        phase = other.phase;
        channel = other.channel;
        packetClass = other.packetClass;
        packetId = other.packetId;
        sourceProtocol = other.sourceProtocol;
        encodingMode = other.encodingMode;
        bodyFields = new ArrayList<>(other.bodyFields == null ? List.of() : other.bodyFields);
        bodyBytes = other.bodyBytes == null ? new byte[0] : other.bodyBytes.clone();
        lastValidBytes = other.lastValidBytes == null ? new byte[0] : other.lastValidBytes.clone();
        provenance = other.provenance;
        validationError = other.validationError;
        decodedKind = other.decodedKind;
        commandApiRecognized = other.commandApiRecognized;
        commandApiOverride = other.commandApiOverride;
        commandApiValue = other.commandApiValue;
        touch();
    }

    private static byte[] parseStrictHex(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) return new byte[0];
        String compact = value.replaceAll("(?i)0x", "").replaceAll("\\s+", "");
        if (compact.isEmpty()) return new byte[0];
        if ((compact.length() & 1) != 0) throw new IllegalArgumentException("Hex needs byte pairs");
        if (!compact.matches("(?i)[0-9a-f]+")) throw new IllegalArgumentException("Only hex digits are allowed");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < compact.length(); i += 2) {
            out[i / 2] = (byte) Integer.parseInt(compact.substring(i, i + 2), 16);
        }
        return out;
    }

    private static LogicalPacket parseLogicalPacketHex(String hex) {
        byte[] bytes = parseStrictHex(hex);
        int[] index = {0};
        Integer packetId = readVarInt(bytes, index);
        if (packetId == null || packetId < 0) throw new IllegalArgumentException("Missing logical packet id");
        String channel = readMinecraftString(bytes, index);
        byte[] body = new byte[Math.max(0, bytes.length - index[0])];
        if (body.length > 0) System.arraycopy(bytes, index[0], body, 0, body.length);
        return new LogicalPacket(packetId, channel, body);
    }

    private static String readMinecraftString(byte[] bytes, int[] index) {
        Integer len = readVarInt(bytes, index);
        if (len == null || len < 0) throw new IllegalArgumentException("Invalid channel length");
        if (index[0] + len > bytes.length) throw new IllegalArgumentException("Channel length exceeds logical packet");
        String value = new String(bytes, index[0], len, StandardCharsets.UTF_8);
        index[0] += len;
        if (value.isBlank()) throw new IllegalArgumentException("Channel cannot be blank");
        return value;
    }

    private static Integer readVarInt(byte[] bytes, int[] indexRef) {
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

    private static int firstDelimiter(String line) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }

    private static String cleanDirection(String raw) {
        return "S2C".equalsIgnoreCase(raw == null ? "" : raw.strip()) ? "S2C" : "C2S";
    }

    private static String cleanPhase(String raw) {
        String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        return value.contains("CONFIG") ? "CONFIGURATION" : "PLAY";
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.strip());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private record LogicalPacket(int packetId, String channel, byte[] body) {
    }
}
