package autismclient.util;

import autismclient.util.macro.PayloadAction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AutismPayloadJsonSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_DEPTH = 16;

    private AutismPayloadJsonSupport() {
    }

    public static void seedActionFromPayload(PayloadAction action, String channel, CustomPacketPayload payload) {
        if (action == null) return;
        action.channel = channel == null ? "" : channel;
        if (payload == null) {
            action.payloadClassName = "";
            action.payloadJson = buildMinimalJson("", "");
            return;
        }
        action.payloadClassName = payload.getClass().getName();
        action.payloadJson = buildEditableJson("C2S", action.channel, payload);
    }

    public static String buildEditableJson(String direction, String channel, CustomPacketPayload payload) {
        JsonObject root = new JsonObject();
        root.addProperty("direction", direction == null ? "" : direction);
        root.addProperty("channel", channel == null ? "" : channel);
        if (payload != null) {
            root.addProperty("payloadType", payload.getClass().getSimpleName());
            byte[] rawBytes = AutismPayloadSupport.extractPayloadBytes(payload);
            root.addProperty("payloadHex", AutismPayloadSupport.toHex(rawBytes));
            root.addProperty("payloadBase64", AutismPayloadSupport.toBase64(rawBytes));
            String payloadText = AutismPayloadSupport.decodeLikelyUtf8Text(rawBytes);
            if (!payloadText.isBlank()) {
                root.addProperty("payloadText", payloadText);
            }
            Integer commandApiValue = AutismPayloadSupport.tryParseCommandApiValue(
                payload, channel, rawBytes);
            if (commandApiValue != null) {
                root.addProperty("commandApiValue", commandApiValue);
            }
        }
        root.add("payload", payload != null
            ? toJsonElement(payload, new IdentityHashMap<>(), 0)
            : new JsonObject());
        return GSON.toJson(root);
    }

    public static String buildEditableJson(String channel, CustomPacketPayload payload) {
        return buildEditableJson("", channel, payload);
    }

    public static String buildEditableJson(PayloadAction action) {
        if (action == null) return buildMinimalJson("", "");
        if (action.payloadJson != null && !action.payloadJson.isBlank()) {
            return normalizeJson(action.payloadJson);
        }
        if (action.payloadClassName != null && !action.payloadClassName.isBlank()
            && action.payloadData != null && !action.payloadData.isBlank()) {
            try {
                CustomPacketPayload payload = decodePayload(action.payloadClassName, AutismPayloadSupport.parsePayloadBytes(action.payloadData));
                if (payload != null) {
                    return buildEditableJson(action.sourceDirection, action.channel, payload);
                }
            } catch (Throwable ignored) {  }
        }
        return buildMinimalJson(action.sourceDirection, action.channel);
    }

    public static String normalizeJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) return buildMinimalJson("", "");
        try {
            return GSON.toJson(JsonParser.parseString(jsonText));
        } catch (Throwable ignored) {
            return jsonText;
        }
    }

    public static EncodedPayload encodeAction(PayloadAction action) {
        if (action == null) throw new IllegalArgumentException("Missing payload action");
        String jsonText = action.payloadJson == null || action.payloadJson.isBlank()
            ? buildEditableJson(action)
            : action.payloadJson;
        JsonObject root;
        try {
            root = JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid payload JSON: " + t.getMessage(), t);
        }

        String channel = root.has("channel") && root.get("channel").isJsonPrimitive()
            ? root.get("channel").getAsString().trim()
            : (action.channel == null ? "" : action.channel.trim());
        if (channel.isBlank()) {
            throw new IllegalArgumentException("Payload JSON must contain a non-empty channel");
        }

        byte[] rawFallbackBytes = rawBytesFromJson(root);
        if (rawFallbackBytes == null) {
            rawFallbackBytes = action.payloadData == null || action.payloadData.isBlank()
                ? new byte[0]
                : AutismPayloadSupport.parsePayloadBytes(action.payloadData);
        }

        if (action.payloadClassName == null || action.payloadClassName.isBlank()) {
            return new EncodedPayload(channel, rawFallbackBytes);
        }

        try {
            return encodeKnownPayload(action, root, channel, rawFallbackBytes);
        } catch (Throwable primaryFailure) {
            try {
                EncodedPayload fallback = encodeFromRawFallback(action, root, channel, rawFallbackBytes);
                if (fallback.bytes().length == 0 && root.has("payload")) {
                    throw new IllegalArgumentException("Known payload rebuild failed and no raw fallback bytes were available", primaryFailure);
                }
                return fallback;
            } catch (Throwable fallbackFailure) {
                IllegalArgumentException error = new IllegalArgumentException(
                    "Could not rebuild payload " + action.payloadClassName + ": " + AutismPayloadSupport.safeMessage(primaryFailure));
                error.addSuppressed(fallbackFailure);
                throw error;
            }
        }
    }

    private static boolean isAllowedPayloadClassName(String name) {
        return name != null && (name.startsWith("net.minecraft.") || name.startsWith("autismclient."));
    }

    private static EncodedPayload encodeKnownPayload(PayloadAction action, JsonObject root, String channel, byte[] rawFallbackBytes) {
        Class<?> payloadClass;
        if (!isAllowedPayloadClassName(action.payloadClassName)) {
            return encodeFromRawFallback(action, root, channel, rawFallbackBytes);
        }
        try {
            payloadClass = Class.forName(action.payloadClassName);
        } catch (ClassNotFoundException e) {
            return encodeFromRawFallback(action, root, channel, rawFallbackBytes);
        }
        if (!CustomPacketPayload.class.isAssignableFrom(payloadClass)) {
            return encodeFromRawFallback(action, root, channel, rawFallbackBytes);
        }

        JsonElement payloadElement = root.has("payload") ? root.get("payload") : JsonNull.INSTANCE;

        if (AutismPayloadSupport.isCommandApiPayload(
            buildDummyPayloadForCheck(payloadClass, channel, rawFallbackBytes))) {
            return encodeCommandApiPayload(action, root, channel, payloadClass, rawFallbackBytes);
        }

        if (payloadClass.isRecord()) {
            return encodeRecordPayload(payloadElement, payloadClass, channel, rawFallbackBytes);
        }

        return encodeGenericPayload(payloadElement, payloadClass, channel, rawFallbackBytes);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CustomPacketPayload buildDummyPayloadForCheck(Class<?> payloadClass, String channel, byte[] rawFallbackBytes) {
        try {
            StreamCodec codec = AutismPayloadSupport.findPayloadCodec(payloadClass);
            if (codec != null && rawFallbackBytes.length > 0) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(rawFallbackBytes.clone()));
                Object decoded = codec.decode(buf);
                if (decoded instanceof CustomPacketPayload cp) return cp;
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static EncodedPayload encodeCommandApiPayload(PayloadAction action, JsonObject root, String channel,
                                                           Class<?> payloadClass, byte[] rawFallbackBytes) {
        int value;
        if (root.has("commandApiValue") && root.get("commandApiValue").isJsonPrimitive()) {
            value = root.get("commandApiValue").getAsInt();
        } else if (action.commandApiRecognized && action.commandApiOverride) {
            value = action.commandApiValue;
        } else {
            value = readInt32(rawFallbackBytes);
        }

        try {
            Constructor<?> intCtor = findSingleIntConstructor(payloadClass);
            if (intCtor != null) {
                if (!intCtor.canAccess(null)) intCtor.setAccessible(true);
                Object instance = intCtor.newInstance(value);
                if (instance instanceof CustomPacketPayload cp) {
                    byte[] bytes = AutismPayloadSupport.extractPayloadBytes(cp);
                    if (bytes.length > 0) {
                        return new EncodedPayload(channel, bytes);
                    }
                }
            }
        } catch (Throwable ignored) {  }

        return new EncodedPayload(channel, AutismPayloadSupport.withCommandApiValue(rawFallbackBytes, value));
    }

    private static Constructor<?> findSingleIntConstructor(Class<?> clazz) {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 1 && (params[0] == int.class || params[0] == Integer.class)) {
                return ctor;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static EncodedPayload encodeRecordPayload(JsonElement payloadElement, Class<?> payloadClass,
                                                       String channel, byte[] rawFallbackBytes) {
        try {
            CustomPacketPayload templatePayload = decodePayload(payloadClass.getName(), rawFallbackBytes);
            Object rebuilt = fromJsonElement(payloadElement, payloadClass, payloadClass, templatePayload, 0);
            if (rebuilt instanceof CustomPacketPayload cp) {
                byte[] bytes = AutismPayloadSupport.extractPayloadBytes(cp);
                if (bytes.length > 0) {
                    return new EncodedPayload(channel, bytes);
                }
            }
        } catch (Throwable ignored) {  }

        byte[] bytes = encodeViaCodec(payloadClass, rawFallbackBytes);
        return new EncodedPayload(channel, bytes != null ? bytes : rawFallbackBytes);
    }

    @SuppressWarnings("unchecked")
    private static EncodedPayload encodeGenericPayload(JsonElement payloadElement, Class<?> payloadClass,
                                                        String channel, byte[] rawFallbackBytes) {
        try {
            CustomPacketPayload templatePayload = decodePayload(payloadClass.getName(), rawFallbackBytes);
            Object rebuilt = fromJsonElement(payloadElement, payloadClass, payloadClass, templatePayload, 0);
            if (rebuilt instanceof CustomPacketPayload cp) {
                byte[] bytes = AutismPayloadSupport.extractPayloadBytes(cp);
                if (bytes.length > 0) {
                    return new EncodedPayload(channel, bytes);
                }
            }
        } catch (Throwable ignored) {  }

        byte[] bytes = encodeViaCodec(payloadClass, rawFallbackBytes);
        return new EncodedPayload(channel, bytes != null ? bytes : rawFallbackBytes);
    }

    private static EncodedPayload encodeFromRawFallback(PayloadAction action, JsonObject root, String channel, byte[] rawFallbackBytes) {
        if (root.has("commandApiValue") && root.get("commandApiValue").isJsonPrimitive()) {
            int value = root.get("commandApiValue").getAsInt();
            return new EncodedPayload(channel, AutismPayloadSupport.withCommandApiValue(rawFallbackBytes, value));
        }
        if (action.commandApiRecognized && action.commandApiOverride) {
            return new EncodedPayload(channel, AutismPayloadSupport.withCommandApiValue(rawFallbackBytes, action.commandApiValue));
        }
        return new EncodedPayload(channel, rawFallbackBytes);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static byte[] encodeViaCodec(Class<?> payloadClass, byte[] rawFallbackBytes) {
        if (rawFallbackBytes.length == 0) return null;
        try {
            CustomPacketPayload decoded = decodePayload(payloadClass.getName(), rawFallbackBytes);
            if (decoded != null) {
                StreamCodec codec = AutismPayloadSupport.findPayloadCodec(payloadClass);
                if (codec != null) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    codec.encode(buf, decoded);
                    return AutismPayloadSupport.toByteArray(buf);
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    public static String buildInspectionJson(String direction, String channel, CustomPacketPayload payload) {
        try {
            return buildEditableJson(direction, channel, payload);
        } catch (Throwable t) {
            return buildMinimalJson(direction, channel);
        }
    }

    public static String buildInspectionJson(String channel, CustomPacketPayload payload) {
        return buildInspectionJson("", channel, payload);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CustomPacketPayload decodePayload(String payloadClassName, byte[] bytes) {
        if (payloadClassName == null || payloadClassName.isBlank()) return null;
        if (!isAllowedPayloadClassName(payloadClassName)) return null;
        try {
            Class<?> payloadClass = Class.forName(payloadClassName);
            StreamCodec codec = AutismPayloadSupport.findPayloadCodec(payloadClass);
            if (codec == null) return null;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes == null ? new byte[0] : bytes.clone()));
            Object decoded = codec.decode(buf);
            return decoded instanceof CustomPacketPayload payload ? payload : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String buildMinimalJson(String direction, String channel) {
        JsonObject root = new JsonObject();
        root.addProperty("direction", direction == null ? "" : direction);
        root.addProperty("channel", channel == null ? "" : channel);
        root.addProperty("payloadHex", "");
        root.add("payload", new JsonObject());
        return GSON.toJson(root);
    }

    private static byte[] rawBytesFromJson(JsonObject root) {
        if (root == null) return null;
        String hex = stringProperty(root, "payloadHex");
        if (!hex.isBlank()) {
            return AutismPayloadSupport.parsePayloadBytes(hex);
        }
        String base64 = stringProperty(root, "payloadBase64");
        if (!base64.isBlank()) {
            return Base64.getDecoder().decode(base64);
        }
        String text = stringProperty(root, "payloadText");
        if (!text.isBlank()) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
            ? root.getAsJsonObject("payload")
            : null;
        if (payload != null && payload.has("bytes") && payload.get("bytes").isJsonArray()) {
            JsonArray bytes = payload.getAsJsonArray("bytes");
            byte[] out = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                out[i] = (byte) bytes.get(i).getAsInt();
            }
            return out;
        }
        return null;
    }

    private static String stringProperty(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key)) return "";
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }

    private static JsonElement toJsonElement(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null) return JsonNull.INSTANCE;
        if (depth >= MAX_DEPTH) return new JsonPrimitive("<max-depth>");

        if (value instanceof CustomPacketPayload cp && AutismPayloadSupport.isCommandApiPayload(cp)) {
            byte[] rawBytes = AutismPayloadSupport.extractPayloadBytes(cp);
            Integer cmdVal = AutismPayloadSupport.tryParseCommandApiValue(cp, null, rawBytes);
            if (cmdVal != null) {
                JsonObject cmdObj = new JsonObject();
                cmdObj.addProperty("commandApiValue", cmdVal);
                return cmdObj;
            }
        }

        if (isSimpleLeaf(value)) return toSimpleJson(value);

        if (value instanceof Optional<?> optional) {
            return optional.map(v -> toJsonElement(v, visited, depth + 1)).orElse(JsonNull.INSTANCE);
        }

        if (value instanceof Component text) {
            try {
                return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, text).getOrThrow();
            } catch (Throwable t) {
                return new JsonPrimitive(text.getString());
            }
        }

        if (value instanceof ItemStack stack) {
            try {
                return ItemStack.CODEC.encodeStart(getRegistryManager().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();
            } catch (Throwable t) {
                return new JsonPrimitive(stack.toString());
            }
        }

        if (value instanceof Tag nbt) {
            try {
                return new JsonPrimitive(nbt.asString().orElse(String.valueOf(nbt)));
            } catch (Throwable t) {
                return new JsonPrimitive(String.valueOf(nbt));
            }
        }

        if (visited.containsKey(value)) {
            return new JsonPrimitive("<cycle>");
        }
        visited.put(value, Boolean.TRUE);

        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.add(toJsonElement(Array.get(value, i), visited, depth + 1));
            }
            return array;
        }

        if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            for (Object element : collection) {
                array.add(toJsonElement(element, visited, depth + 1));
            }
            return array;
        }

        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = mapKeyToString(entry.getKey());
                object.add(key, toJsonElement(entry.getValue(), visited, depth + 1));
            }
            return object;
        }

        JsonObject object = new JsonObject();
        if (value.getClass().isRecord()) {
            RecordComponent[] components = value.getClass().getRecordComponents();
            for (RecordComponent component : components) {
                String label = recordComponentLabel(component);
                Object fieldValue = invokeAccessor(component.getAccessor(), value);
                object.add(label, toJsonElement(fieldValue, visited, depth + 1));
            }
            return object;
        }

        for (Field field : editableFields(value.getClass())) {
            String label = fieldLabel(field);
            Object fieldValue = readField(field, value);
            object.add(label, toJsonElement(fieldValue, visited, depth + 1));
        }
        return object;
    }

    private static Object fromJsonElement(JsonElement element, Type genericType, Class<?> targetType, Object template, int depth) {
        if (targetType == null) return null;
        if (depth >= MAX_DEPTH) return template;

        if (element == null || element.isJsonNull()) {
            if (targetType == Optional.class) return Optional.empty();
            return primitiveDefault(targetType, template);
        }

        if (targetType == String.class) return element.getAsString();
        if (targetType == boolean.class || targetType == Boolean.class) return element.getAsBoolean();
        if (targetType == byte.class || targetType == Byte.class) return element.getAsByte();
        if (targetType == short.class || targetType == Short.class) return element.getAsShort();
        if (targetType == int.class || targetType == Integer.class) return element.getAsInt();
        if (targetType == long.class || targetType == Long.class) return element.getAsLong();
        if (targetType == float.class || targetType == Float.class) return element.getAsFloat();
        if (targetType == double.class || targetType == Double.class) return element.getAsDouble();
        if (targetType == char.class || targetType == Character.class) {
            String text = element.getAsString();
            return text.isEmpty() ? '\0' : text.charAt(0);
        }
        if (targetType == Identifier.class) return Identifier.parse(element.getAsString());
        if (targetType == UUID.class) return UUID.fromString(element.getAsString());
        if (targetType.isEnum()) return parseEnum(targetType, element.getAsString(), template);
        if (targetType == Component.class) return decodeText(element);
        if (targetType == BlockPos.class) return decodeBlockPos(element);
        if (targetType == Vec3.class) return decodeVec3(element);
        if (ItemStack.class.isAssignableFrom(targetType)) {
            return ItemStack.CODEC.parse(getRegistryManager().createSerializationContext(JsonOps.INSTANCE), element).result().orElse(ItemStack.EMPTY);
        }
        if (Tag.class.isAssignableFrom(targetType)) {
            return template;
        }
        if (Optional.class.isAssignableFrom(targetType)) {
            Type innerType = parameterType(genericType, 0);
            Class<?> innerClass = rawClass(innerType, template == null ? Object.class : template.getClass());
            return Optional.ofNullable(fromJsonElement(element, innerType, innerClass, optionalValue(template), depth + 1));
        }
        if (targetType.isArray()) {
            return decodeArray(element, targetType.getComponentType(), componentType(genericType), template, depth);
        }
        if (Collection.class.isAssignableFrom(targetType)) {
            return decodeCollection(element, genericType, targetType, template, depth);
        }
        if (Map.class.isAssignableFrom(targetType)) {
            return decodeMap(element, genericType, targetType, template, depth);
        }
        if (targetType.isRecord()) {
            return decodeRecord(element, targetType, template, depth);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return template != null ? template : element.getAsString();
        }

        return decodeMutableObject(element, targetType, template, depth);
    }

    private static Object decodeRecord(JsonElement element, Class<?> targetType, Object template, int depth) {
        if (!element.isJsonObject()) return template;
        try {
            JsonObject jsonObj = element.getAsJsonObject();
            RecordComponent[] components = targetType.getRecordComponents();
            Class<?>[] componentTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                componentTypes[i] = component.getType();
                String label = recordComponentLabel(component);
                JsonElement valueElement = jsonObj.has(label) ? jsonObj.get(label) : null;
                Object templateValue = template == null ? null : invokeAccessor(component.getAccessor(), template);
                args[i] = fromJsonElement(valueElement, component.getGenericType(), component.getType(), templateValue, depth + 1);
            }
            Constructor<?> ctor = targetType.getDeclaredConstructor(componentTypes);
            if (!ctor.canAccess(null)) ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Throwable t) {
            return template;
        }
    }

    private static Object decodeMutableObject(JsonElement element, Class<?> targetType, Object template, int depth) {
        if (!element.isJsonObject()) return template;
        Object instance = template != null ? template : instantiate(targetType);
        JsonObject object = element.getAsJsonObject();
        for (Field field : editableFields(targetType)) {
            String label = fieldLabel(field);
            if (!object.has(label)) continue;
            Object currentValue = readField(field, instance);
            Object decoded = fromJsonElement(object.get(label), field.getGenericType(), field.getType(), currentValue, depth + 1);
            writeField(field, instance, decoded);
        }
        return instance;
    }

    private static Object decodeArray(JsonElement element, Class<?> componentType, Type componentGenericType, Object template, int depth) {
        if (!element.isJsonArray()) return template;
        JsonArray array = element.getAsJsonArray();
        Object out = Array.newInstance(componentType, array.size());
        for (int i = 0; i < array.size(); i++) {
            Object templateValue = template != null && i < Array.getLength(template) ? Array.get(template, i) : null;
            Array.set(out, i, fromJsonElement(array.get(i), componentGenericType, componentType, templateValue, depth + 1));
        }
        return out;
    }

    private static Object decodeCollection(JsonElement element, Type genericType, Class<?> targetType, Object template, int depth) {
        if (!element.isJsonArray()) return template;
        Collection<Object> values = Set.class.isAssignableFrom(targetType) ? new LinkedHashSet<>() : new ArrayList<>();
        Type itemType = parameterType(genericType, 0);
        Class<?> itemClass = rawClass(itemType, Object.class);
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            Object templateValue = template instanceof List<?> list && index < list.size() ? list.get(index) : null;
            values.add(fromJsonElement(entry, itemType, itemClass, templateValue, depth + 1));
            index++;
        }
        return values;
    }

    private static Object decodeMap(JsonElement element, Type genericType, Class<?> targetType, Object template, int depth) {
        if (!element.isJsonObject()) return template;
        Map<Object, Object> map = new LinkedHashMap<>();
        Type keyType = parameterType(genericType, 0);
        Type valueType = parameterType(genericType, 1);
        Class<?> keyClass = rawClass(keyType, String.class);
        Class<?> valueClass = rawClass(valueType, Object.class);
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Object key = parseMapKey(entry.getKey(), keyClass);
            map.put(key, fromJsonElement(entry.getValue(), valueType, valueClass, null, depth + 1));
        }
        return map;
    }

    private static Object instantiate(Class<?> targetType) {
        try {
            Constructor<?> ctor = targetType.getDeclaredConstructor();
            if (!ctor.canAccess(null)) ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot instantiate payload type " + targetType.getName(), t);
        }
    }

    private static Object primitiveDefault(Class<?> targetType, Object template) {
        if (template != null) return template;
        if (!targetType.isPrimitive()) return null;
        if (targetType == boolean.class) return false;
        if (targetType == char.class) return '\0';
        if (targetType == byte.class) return (byte) 0;
        if (targetType == short.class) return (short) 0;
        if (targetType == int.class) return 0;
        if (targetType == long.class) return 0L;
        if (targetType == float.class) return 0F;
        if (targetType == double.class) return 0D;
        return null;
    }

    private static JsonElement toSimpleJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Character character) return new JsonPrimitive(character);
        if (value instanceof String string) return new JsonPrimitive(string);
        if (value instanceof Identifier identifier) return new JsonPrimitive(identifier.toString());
        if (value instanceof UUID uuid) return new JsonPrimitive(uuid.toString());
        if (value instanceof Enum<?> enumValue) return new JsonPrimitive(enumValue.name());
        if (value instanceof BlockPos pos) {
            JsonObject object = new JsonObject();
            object.addProperty("x", pos.getX());
            object.addProperty("y", pos.getY());
            object.addProperty("z", pos.getZ());
            return object;
        }
        if (value instanceof Vec3 vec) {
            JsonObject object = new JsonObject();
            object.addProperty("x", vec.x);
            object.addProperty("y", vec.y);
            object.addProperty("z", vec.z);
            return object;
        }
        if (value instanceof byte[] bytes) {
            JsonArray array = new JsonArray();
            for (byte b : bytes) array.add((int) b);
            return array;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    private static boolean isSimpleLeaf(Object value) {
        return value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof String
            || value instanceof Identifier
            || value instanceof UUID
            || value instanceof Enum<?>
            || value instanceof BlockPos
            || value instanceof Vec3
            || value instanceof byte[];
    }

    private static List<Field> editableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isTransient(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;
                fields.add(field);
            }
            cursor = cursor.getSuperclass();
        }
        return fields;
    }

    private static String fieldLabel(Field field) {
        String mapped = AutismYarnMappings.lookupFieldLabel(field);
        if (mapped != null && !mapped.isBlank()) return mapped;
        return field.getName();
    }

    private static String recordComponentLabel(RecordComponent component) {
        String mapped = AutismYarnMappings.lookupMethodLabel(component.getAccessor());
        if (mapped != null && !mapped.isBlank()) return mapped;
        return component.getName();
    }

    private static Object invokeAccessor(Method accessor, Object owner) {
        try {
            if (!accessor.canAccess(owner)) accessor.setAccessible(true);
            return accessor.invoke(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object readField(Field field, Object owner) {
        try {
            if (!field.canAccess(owner)) field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeField(Field field, Object owner, Object value) {
        try {
            if (!field.canAccess(owner)) field.setAccessible(true);
            field.set(owner, value);
        } catch (Throwable ignored) {  }
    }

    private static Object parseEnum(Class<?> enumType, String name, Object template) {
        for (Object constant : enumType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumValue && enumValue.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return template;
    }

    private static Component decodeText(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) {
                return Component.literal(element.getAsString());
            }
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
        } catch (Throwable t) {
            return Component.literal(element.toString());
        }
    }

    private static BlockPos decodeBlockPos(JsonElement element) {
        if (!element.isJsonObject()) return BlockPos.ZERO;
        JsonObject object = element.getAsJsonObject();
        return new BlockPos(
            object.has("x") ? object.get("x").getAsInt() : 0,
            object.has("y") ? object.get("y").getAsInt() : 0,
            object.has("z") ? object.get("z").getAsInt() : 0
        );
    }

    private static Vec3 decodeVec3(JsonElement element) {
        if (!element.isJsonObject()) return Vec3.ZERO;
        JsonObject object = element.getAsJsonObject();
        return new Vec3(
            object.has("x") ? object.get("x").getAsDouble() : 0D,
            object.has("y") ? object.get("y").getAsDouble() : 0D,
            object.has("z") ? object.get("z").getAsDouble() : 0D
        );
    }

    private static String mapKeyToString(Object key) {
        if (key == null) return "null";
        if (key instanceof Identifier identifier) return identifier.toString();
        if (key instanceof Enum<?> enumValue) return enumValue.name();
        return String.valueOf(key);
    }

    private static Object parseMapKey(String key, Class<?> keyClass) {
        if (keyClass == Identifier.class) return Identifier.parse(key);
        if (keyClass == UUID.class) return UUID.fromString(key);
        if (keyClass.isEnum()) return parseEnum(keyClass, key, null);
        if (keyClass == Integer.class || keyClass == int.class) return Integer.parseInt(key);
        if (keyClass == Long.class || keyClass == long.class) return Long.parseLong(key);
        return key;
    }

    private static Type parameterType(Type type, int index) {
        if (type instanceof ParameterizedType parameterizedType) {
            Type[] args = parameterizedType.getActualTypeArguments();
            if (index >= 0 && index < args.length) return args[index];
        }
        return Object.class;
    }

    private static Type componentType(Type type) {
        if (type instanceof Class<?> cls && cls.isArray()) return cls.getComponentType();
        if (type instanceof GenericArrayType genericArrayType) return genericArrayType.getGenericComponentType();
        return Object.class;
    }

    private static Class<?> rawClass(Type type, Class<?> fallback) {
        if (type instanceof Class<?> cls) return cls;
        if (type instanceof ParameterizedType parameterizedType) {
            return rawClass(parameterizedType.getRawType(), fallback);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return Array.newInstance(rawClass(genericArrayType.getGenericComponentType(), Object.class), 0).getClass();
        }
        return fallback;
    }

    private static Object optionalValue(Object template) {
        if (template instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return null;
    }

    private static net.minecraft.core.HolderLookup.Provider getRegistryManager() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static int readInt32(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length < 4) return 0;
        return ByteBuffer.wrap(rawBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public record EncodedPayload(String channel, byte[] bytes) {
        public EncodedPayload {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
