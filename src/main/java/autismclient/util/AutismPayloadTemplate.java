package autismclient.util;

import autismclient.util.macro.PayloadAction;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AutismPayloadTemplate {
    private static final int WARN_PAYLOAD_BYTES = 8192;
    private static final int MAX_PAYLOAD_BYTES = 32767;

    private AutismPayloadTemplate() {
    }

    public enum PayloadDirection {
        C2S,
        S2C;

        public static PayloadDirection parse(String value) {
            if (value == null) return C2S;
            return "S2C".equalsIgnoreCase(value.strip()) ? S2C : C2S;
        }
    }

    public enum PayloadPhase {
        PLAY,
        CONFIGURATION;

        public static PayloadPhase parse(String value) {
            if (value == null) return PLAY;
            String normalized = value.strip().toUpperCase(Locale.ROOT);
            return normalized.contains("CONFIG") ? CONFIGURATION : PLAY;
        }
    }

    public enum EncodingMode {
        RAW_UTF8("Raw UTF-8"),
        MANUAL_HEX("Manual Hex"),
        JAVA_DATA_OUTPUT("Java DataOutput"),
        MINECRAFT_BYTEBUF("Minecraft ByteBuf"),
        JSON_TEXT("JSON/Text"),
        ADVANCED_MIXED("Advanced Mixed");

        private final String label;

        EncodingMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static EncodingMode parse(String value, String channel) {
            if (value != null && !value.isBlank()) {
                String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                for (EncodingMode mode : values()) {
                    if (mode.name().equals(normalized)) return mode;
                }
            }
            if (AutismPayloadSupport.isBrandChannel(channel)) return MINECRAFT_BYTEBUF;
            if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip())) return JAVA_DATA_OUTPUT;
            return ADVANCED_MIXED;
        }
    }

    public enum FieldType {
        BYTE("writeByte", "0"),
        UNSIGNED_BYTE("writeUnsignedByte", "0"),
        BOOLEAN("writeBoolean", "true"),
        SHORT("writeShort", "0"),
        UNSIGNED_SHORT("writeUnsignedShort", "0"),
        CHAR("writeChar", "A"),
        INT("writeInt", "0"),
        LONG("writeLong", "0"),
        FLOAT("writeFloat", "0.0"),
        DOUBLE("writeDouble", "0.0"),
        JAVA_WRITE_UTF("writeUTF", "GetServer"),
        RAW_BYTES("writeBytes", ""),
        HEX_BYTES("writeBytesHex", ""),
        BYTE_ARRAY("writeByteArray", "raw:"),
        STRING_BYTES("writeStringBytes", "hello-from-client"),
        RAW_UTF8_STRING("writeStringBytes", "hello-from-client"),
        VAR_INT("writeVarInt", "0"),
        VAR_LONG("writeVarLong", "0"),
        MINECRAFT_STRING("writeMcString", AutismPayloadSupport.defaultBrandPayloadString()),
        IDENTIFIER("writeIdentifier", "minecraft:brand"),
        UUID_FIELD("writeUUID", "00000000-0000-0000-0000-000000000000"),
        BLOCK_POS("writeBlockPos", "0,64,0"),
        ENUM_VAR_INT("writeEnumVarInt", "0"),
        OPTIONAL_VALUE("writeOptional", "false"),
        NBT("writeNbt", "{}"),
        ITEM_STACK("writeItemStack", "{\"id\":\"minecraft:stone\",\"count\":1}"),
        TEXT_COMPONENT("writeComponent", "{\"text\":\"hello\"}"),
        JSON_STRING("writeComponent", "{\"text\":\"hello\"}");

        private final String label;
        private final String defaultValue;

        FieldType(String label, String defaultValue) {
            this.label = label;
            this.defaultValue = defaultValue;
        }

        public String label() {
            return label;
        }

        public String defaultValue() {
            return defaultValue;
        }

        public static FieldType parse(String value) {
            if (value == null || value.isBlank()) return RAW_UTF8_STRING;
            String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if ("UUID".equals(normalized)) normalized = "UUID_FIELD";
            if ("UTF".equals(normalized) || "WRITEUTF".equals(normalized)) normalized = "JAVA_WRITE_UTF";
            if ("MINECRAFT_STRING".equals(normalized) || "MC_STRING".equals(normalized)) normalized = "MINECRAFT_STRING";
            if ("TEXT_COMPONENT".equals(normalized) || "COMPONENT".equals(normalized)) normalized = "TEXT_COMPONENT";
            for (FieldType type : values()) {
                if (type.name().equals(normalized)) return type;
                if (type.label().equalsIgnoreCase(value.strip())) return type;
            }
            return RAW_UTF8_STRING;
        }
    }

    public record Field(FieldType type, String value, boolean enabled) {
        public Field {
            type = type == null ? FieldType.RAW_UTF8_STRING : type;
            value = value == null ? "" : value;
        }

        public String displayLine() {
            return type.label() + " = " + value;
        }
    }

    public record Template(String channel, PayloadDirection direction, PayloadPhase phase, EncodingMode mode, List<Field> fields) {
        public Template {
            channel = channel == null || channel.isBlank() ? "minecraft:brand" : channel.strip();
            direction = direction == null ? PayloadDirection.C2S : direction;
            phase = phase == null ? PayloadPhase.PLAY : phase;
            mode = mode == null ? EncodingMode.parse("", channel) : mode;
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public BuildResult build() {
            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            validateHeader(channel, direction, phase, mode, warnings, errors);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Field> enabledFields = fields.stream().filter(Field::enabled).toList();
            if (enabledFields.isEmpty()) {
                warnings.add("Payload is empty. Some servers reject empty custom payloads.");
            }

            for (Field field : enabledFields) {
                try {
                    writeField(out, field, mode, warnings);
                } catch (Exception e) {
                    errors.add(field.type().label() + ": " + AutismPayloadSupport.safeMessage(e));
                }
            }

            byte[] bytes = out.toByteArray();
            if (bytes.length > WARN_PAYLOAD_BYTES) {
                warnings.add("Payload is large (" + bytes.length + "B). Use low repeat rates.");
            }
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                errors.add("Payload is " + bytes.length + "B; serverbound custom payload max is " + MAX_PAYLOAD_BYTES + "B.");
            }

            Preview preview = preview(bytes);
            return new BuildResult(bytes, warnings, errors, preview);
        }
    }

    public record Preview(String hex, String utf8, String minecraftString, String javaWriteUtf) {
    }

    public record BuildResult(byte[] bytes, List<String> warnings, List<String> errors, Preview preview) {
        public BuildResult {
            bytes = bytes == null ? new byte[0] : bytes.clone();
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            errors = errors == null ? List.of() : List.copyOf(errors);
            preview = preview == null ? AutismPayloadTemplate.preview(bytes) : preview;
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static Template fromAction(PayloadAction action) {
        if (action == null) return defaultBrandTemplate();
        String channel = action.channel == null || action.channel.isBlank() ? "minecraft:brand" : action.channel.strip();
        PayloadDirection direction = PayloadDirection.parse(action.payloadDirection == null || action.payloadDirection.isBlank()
            ? action.sourceDirection
            : action.payloadDirection);
        PayloadPhase phase = PayloadPhase.parse(action.payloadPhase == null || action.payloadPhase.isBlank()
            ? action.sourceProtocol
            : action.payloadPhase);
        EncodingMode mode = EncodingMode.parse(action.payloadEncodingMode, channel);
        List<Field> fields = parseFields(action.payloadFields);
        if (fields.isEmpty() && action.payloadData != null && !action.payloadData.isBlank()) {
            fields = List.of(new Field(FieldType.HEX_BYTES, action.payloadData, true));
            mode = EncodingMode.MANUAL_HEX;
        }
        if (fields.isEmpty()) {
            if (AutismPayloadSupport.isBrandChannel(channel)) {
                fields = List.of(new Field(FieldType.MINECRAFT_STRING, AutismPayloadSupport.defaultBrandPayloadString(), true));
                mode = EncodingMode.MINECRAFT_BYTEBUF;
            } else {
                fields = List.of(new Field(FieldType.RAW_UTF8_STRING, "", true));
            }
        }
        return new Template(channel, direction, phase, mode, fields);
    }

    public static Template defaultBrandTemplate() {
        return new Template("minecraft:brand", PayloadDirection.C2S, PayloadPhase.PLAY, EncodingMode.MINECRAFT_BYTEBUF,
            List.of(new Field(FieldType.MINECRAFT_STRING, AutismPayloadSupport.defaultBrandPayloadString(), true)));
    }

    public static Template presetPaperPing() {
        return new Template("testmod:ping", PayloadDirection.C2S, PayloadPhase.PLAY, EncodingMode.RAW_UTF8,
            List.of(new Field(FieldType.RAW_UTF8_STRING, "hello-from-client", true)));
    }

    public static Template presetBrand(String brand) {
        return new Template("minecraft:brand", PayloadDirection.C2S, PayloadPhase.PLAY, EncodingMode.MINECRAFT_BYTEBUF,
            List.of(new Field(FieldType.MINECRAFT_STRING,
                brand == null || brand.isBlank() ? AutismPayloadSupport.defaultBrandPayloadString() : brand, true)));
    }

    public static Template presetBungeeGetServer() {
        return new Template("bungeecord:main", PayloadDirection.C2S, PayloadPhase.PLAY, EncodingMode.JAVA_DATA_OUTPUT,
            List.of(new Field(FieldType.JAVA_WRITE_UTF, "GetServer", true)));
    }

    public static Template presetBungeePlayerCount() {
        return new Template("bungeecord:main", PayloadDirection.C2S, PayloadPhase.PLAY, EncodingMode.JAVA_DATA_OUTPUT,
            List.of(new Field(FieldType.JAVA_WRITE_UTF, "PlayerCount", true), new Field(FieldType.JAVA_WRITE_UTF, "ALL", true)));
    }

    public static String serializeFields(List<Field> fields) {
        if (fields == null || fields.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            if (field == null) continue;
            if (!sb.isEmpty()) sb.append('\n');
            if (!field.enabled()) sb.append("# ");
            sb.append(field.type().label()).append(" = ").append(field.value());
        }
        return sb.toString();
    }

    public static List<Field> parseFields(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<Field> fields = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine.strip();
            if (line.isEmpty()) continue;
            boolean enabled = true;
            if (line.startsWith("#")) {
                enabled = false;
                line = line.substring(1).strip();
            }
            int split = line.indexOf('=');
            if (split < 0) split = line.indexOf(':');
            String typeText = split < 0 ? "RAW_UTF8_STRING" : line.substring(0, split).strip();
            String value = split < 0 ? line : line.substring(split + 1).strip();
            fields.add(new Field(FieldType.parse(typeText), value, enabled));
        }
        return fields;
    }

    public static String nextModeName(String currentMode, String channel) {
        EncodingMode current = EncodingMode.parse(currentMode, channel);
        EncodingMode[] values = EncodingMode.values();
        return values[(current.ordinal() + 1) % values.length].name();
    }

    public static String nextDirectionName(String current) {
        return PayloadDirection.parse(current) == PayloadDirection.C2S ? PayloadDirection.S2C.name() : PayloadDirection.C2S.name();
    }

    public static String nextPhaseName(String current) {
        return PayloadPhase.parse(current) == PayloadPhase.PLAY ? PayloadPhase.CONFIGURATION.name() : PayloadPhase.PLAY.name();
    }

    public static Preview preview(byte[] bytes) {
        byte[] raw = bytes == null ? new byte[0] : bytes;
        return new Preview(
            AutismPayloadSupport.toCompactHex(raw, 96),
            previewUtf8(raw),
            previewMinecraftString(raw),
            previewJavaUtf(raw)
        );
    }

    public static void applyTemplate(PayloadAction action, Template template) {
        if (action == null || template == null) return;
        action.channel = template.channel();
        action.payloadDirection = template.direction().name();
        action.payloadPhase = template.phase().name();
        action.payloadEncodingMode = template.mode().name();
        action.payloadFields = serializeFields(template.fields());
        action.sourceDirection = action.payloadDirection;
        action.sourceProtocol = "";
        BuildResult result = template.build();
        if (result.ok()) {
            action.payloadData = AutismPayloadSupport.toHex(result.bytes());
        }
    }

    private static void validateHeader(String channel, PayloadDirection direction, PayloadPhase phase, EncodingMode mode,
                                       List<String> warnings, List<String> errors) {
        if (direction == PayloadDirection.S2C) {
            warnings.add("S2C is inspect/replay metadata only. Sending from macros is serverbound C2S.");
        }
        if (phase == PayloadPhase.CONFIGURATION) {
            warnings.add("Configuration payloads usually cannot be replayed during Play.");
        }
        try {
            AutismPayloadSupport.parseChannel(channel);
        } catch (Exception e) {
            errors.add(AutismPayloadSupport.safeMessage(e));
        }
        if (channel != null && !channel.contains(":")) {
            warnings.add("Use a namespaced channel like namespace:path.");
        }
        if (AutismPayloadSupport.isBrandChannel(channel) && mode != EncodingMode.MINECRAFT_BYTEBUF) {
            warnings.add("minecraft:brand normally expects a Minecraft String.");
        }
        if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip()) && mode != EncodingMode.JAVA_DATA_OUTPUT) {
            warnings.add("bungeecord:main normally uses Java DataOutput/writeUTF fields.");
        }
    }

    private static void writeField(ByteArrayOutputStream out, Field field, EncodingMode mode, List<String> warnings) throws Exception {
        String value = field.value();
        switch (field.type()) {
            case RAW_UTF8_STRING, STRING_BYTES -> out.write(decodeEscapedText(value).getBytes(StandardCharsets.UTF_8));
            case JSON_STRING, TEXT_COMPONENT -> writeComponent(out, value);
            case NBT -> writeNbt(out, value);
            case ITEM_STACK -> writeItemStack(out, value);
            case JAVA_WRITE_UTF -> {
                DataOutputStream dos = new DataOutputStream(out);
                dos.writeUTF(decodeEscapedText(value));
                dos.flush();
            }
            case MINECRAFT_STRING, IDENTIFIER -> out.write(AutismPayloadSupport.encodeMinecraftStringPayload(decodeEscapedText(value)));
            case BYTE -> out.write((byte) parseLong(value, Byte.MIN_VALUE, Byte.MAX_VALUE));
            case UNSIGNED_BYTE -> out.write((byte) parseLong(value, 0, 255));
            case CHAR -> writeShort(out, (short) parseChar(value));
            case SHORT -> writeShort(out, (short) parseLong(value, Short.MIN_VALUE, Short.MAX_VALUE));
            case UNSIGNED_SHORT -> writeShort(out, (short) parseLong(value, 0, 65535));
            case INT -> writeInt(out, (int) parseLong(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case LONG -> writeLong(out, parseLong(value, Long.MIN_VALUE, Long.MAX_VALUE));
            case FLOAT -> writeInt(out, Float.floatToIntBits(Float.parseFloat(value.strip())));
            case DOUBLE -> writeLong(out, Double.doubleToLongBits(Double.parseDouble(value.strip())));
            case VAR_INT, ENUM_VAR_INT -> writeVarInt(out, (int) parseLong(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case VAR_LONG -> writeVarLong(out, parseLong(value, Long.MIN_VALUE, Long.MAX_VALUE));
            case BOOLEAN -> out.write(Boolean.parseBoolean(value.strip()) ? 1 : 0);
            case UUID_FIELD -> writeUuid(out, UUID.fromString(value.strip()));
            case RAW_BYTES, HEX_BYTES -> out.write(parseByteValue(value));
            case BYTE_ARRAY -> writeByteArray(out, value);
            case OPTIONAL_VALUE -> writeOptional(out, value, mode, warnings);
            case BLOCK_POS -> writeLong(out, packBlockPos(value));
        }
    }

    private static void writeNbt(ByteArrayOutputStream out, String value) throws Exception {
        String text = value == null || value.isBlank() ? "{}" : value.strip();
        CompoundTag tag = TagParser.parseCompoundFully(text);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeNbt(tag);
            writeBuffer(out, buf);
        } finally {
            buf.release();
        }
    }

    private static void writeComponent(ByteArrayOutputStream out, String value) {
        Component component = parseComponent(value);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
        try {
            ComponentSerialization.STREAM_CODEC.encode(buf, component);
            writeBuffer(out, buf);
        } finally {
            buf.release();
        }
    }

    private static Component parseComponent(String value) {
        String text = value == null || value.isBlank() ? "{\"text\":\"\"}" : value.strip();
        try {
            JsonElement element = JsonParser.parseString(text);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return Component.literal(element.getAsString());
            }
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
        } catch (Throwable ignored) {
            return Component.literal(decodeEscapedText(value));
        }
    }

    private static void writeItemStack(ByteArrayOutputStream out, String value) {
        ItemStack stack = parseItemStack(value);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            writeBuffer(out, buf);
        } finally {
            buf.release();
        }
    }

    private static ItemStack parseItemStack(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || "{}".equals(text)) return ItemStack.EMPTY;
        if (looksLikeSimpleItemId(text)) {
            return itemStackFromId(text, 1);
        }
        try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return itemStackFromId(json.getAsString(), 1);
            }
            return ItemStack.CODEC.parse(registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        } catch (Throwable jsonFailure) {
            try {
                Tag tag = TagParser.parseCompoundFully(text);
                return ItemStack.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
            } catch (Throwable snbtFailure) {
                IllegalArgumentException ex = new IllegalArgumentException("Invalid item stack JSON/SNBT/id");
                ex.addSuppressed(jsonFailure);
                ex.addSuppressed(snbtFailure);
                throw ex;
            }
        }
    }

    private static boolean looksLikeSimpleItemId(String text) {
        return text != null && text.matches("[a-z0-9_./-]+(:[a-z0-9_./-]+)?");
    }

    private static ItemStack itemStackFromId(String rawId, int count) {
        String idText = rawId == null ? "" : rawId.strip();
        Identifier id = Identifier.parse(idText.contains(":") ? idText : "minecraft:" + idText);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item id: " + id);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) throw new IllegalArgumentException("Unknown item id: " + id);
        return new ItemStack(item, Math.max(1, count));
    }

    private static RegistryAccess registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static void writeBuffer(ByteArrayOutputStream out, FriendlyByteBuf buf) {
        byte[] bytes = AutismPayloadSupport.toByteArray(buf);
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] parseByteValue(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) return new byte[0];
        try {
            return AutismPayloadSupport.parsePayloadBytes(text);
        } catch (Exception ignored) {
            return Base64.getDecoder().decode(text);
        }
    }

    private static String decodeEscapedText(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);
            if (!escaping) {
                if (chr == '\\') {
                    escaping = true;
                } else {
                    sb.append(chr);
                }
                continue;
            }
            switch (chr) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '\\' -> sb.append('\\');
                default -> {
                    sb.append('\\');
                    sb.append(chr);
                }
            }
            escaping = false;
        }
        if (escaping) sb.append('\\');
        return sb.toString();
    }

    private static long parseLong(String value, long min, long max) {
        String text = value == null ? "" : value.strip();
        long parsed = Long.decode(text.isEmpty() ? "0" : text);
        if (parsed < min || parsed > max) throw new IllegalArgumentException("Value out of range: " + parsed);
        return parsed;
    }

    private static int parseChar(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) return 0;
        if (text.length() == 1) return text.charAt(0);
        return (int) parseLong(text, Character.MIN_VALUE, Character.MAX_VALUE);
    }

    private static void writeByteArray(ByteArrayOutputStream out, String value) {
        String text = value == null ? "" : value.strip();
        String prefix = "raw";
        String body = text;
        int split = text.indexOf(':');
        if (split > 0) {
            prefix = text.substring(0, split).strip().toLowerCase(Locale.ROOT);
            body = text.substring(split + 1).strip();
        }
        byte[] bytes = parseByteValue(body);
        switch (prefix) {
            case "varint", "var" -> writeVarInt(out, bytes.length);
            case "short", "ushort" -> writeShort(out, (short) bytes.length);
            case "int" -> writeInt(out, bytes.length);
            case "raw", "" -> {
            }
            default -> throw new IllegalArgumentException("Byte Array prefix must be raw, varint, short, or int");
        }
        out.write(bytes, 0, bytes.length);
    }

    private static void writeOptional(ByteArrayOutputStream out, String value, EncodingMode mode, List<String> warnings) throws Exception {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || "false".equalsIgnoreCase(text) || "empty".equalsIgnoreCase(text)) {
            out.write(0);
            return;
        }
        out.write(1);
        String body = text;
        int split = text.indexOf(':');
        FieldType nestedType = FieldType.MINECRAFT_STRING;
        if (split > 0) {
            nestedType = FieldType.parse(text.substring(0, split));
            body = text.substring(split + 1).strip();
        }
        writeField(out, new Field(nestedType, body, true), mode, warnings);
    }

    private static void writeShort(ByteArrayOutputStream out, short value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((value >>> shift) & 0xFF));
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeVarLong(ByteArrayOutputStream out, long value) {
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
            out.write((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static void writeUuid(ByteArrayOutputStream out, UUID uuid) {
        writeLong(out, uuid.getMostSignificantBits());
        writeLong(out, uuid.getLeastSignificantBits());
    }

    private static long packBlockPos(String value) {
        String[] parts = (value == null ? "" : value).split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Block Pos must be x,y,z");
        long x = Long.parseLong(parts[0].strip()) & 0x3FFFFFFL;
        long y = Long.parseLong(parts[1].strip()) & 0xFFFL;
        long z = Long.parseLong(parts[2].strip()) & 0x3FFFFFFL;
        return (x << 38) | (z << 12) | y;
    }

    private static String previewUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String text = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
        return text.isBlank() ? "<binary>" : shorten(text.replace('\n', ' '), 96);
    }

    private static String previewMinecraftString(byte[] bytes) {
        String decoded = AutismPayloadSupport.decodeMinecraftStringPayload(bytes);
        return decoded == null ? "<not a full Minecraft String>" : shorten(decoded, 96);
    }

    private static String previewJavaUtf(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return "<not writeUTF>";
        int len = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
        if (len + 2 != bytes.length) return "<not writeUTF>";
        try {
            return shorten(new String(bytes, 2, len, StandardCharsets.UTF_8), 96);
        } catch (Throwable ignored) {
            return "<invalid writeUTF>";
        }
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        String text = value.strip();
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
