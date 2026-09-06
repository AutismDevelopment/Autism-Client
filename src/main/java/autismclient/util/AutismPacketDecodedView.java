package autismclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AutismPacketDecodedView(Packet<?> packet,
                                      AutismPacketSchemaRegistry.PacketSchema schema,
                                      List<AutismPacketFieldValue> fields,
                                      boolean sourceBacked,
                                      boolean complete,
                                      String status,
                                      String fallbackReason) {
    private static final int MAX_COLLECTION_PREVIEW = 12;
    private static final int MAX_STRING_LENGTH = 220;
    private static final Object UNAVAILABLE = new Object();

    public AutismPacketDecodedView {
        fields = fields == null ? List.of() : List.copyOf(fields);
        status = status == null ? "fallback" : status;
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
    }

    public static AutismPacketDecodedView decode(Packet<?> packet) {
        if (packet == null) {
            return new AutismPacketDecodedView(null, null, List.of(), false, false, "fallback", "packet object missing");
        }
        AutismPacketSchemaRegistry.PacketSchema schema = AutismPacketSchemaRegistry.find(packet.getClass());
        if (schema == null) {
            List<AutismPacketFieldValue> reflected = reflectFields(packet, null);
            return new AutismPacketDecodedView(packet, null, reflected, false, false, "fallback", "no generated schema for packet class");
        }
        if (schema.fields().isEmpty()) {
            if ("UNIT".equalsIgnoreCase(schema.codecStyle())) {
                return new AutismPacketDecodedView(packet, schema, List.of(), true, true, "complete", "");
            }
            List<AutismPacketFieldValue> reflected = reflectFields(packet, schema);
            return new AutismPacketDecodedView(packet, schema, reflected, true, false, "fallback", "schema has no source field list");
        }

        List<AutismPacketFieldValue> values = new ArrayList<>();
        boolean allReadable = true;
        for (AutismPacketSchemaRegistry.FieldSchema field : schema.fields()) {
            Object value = readValue(packet, field.name());
            boolean readable = value != UNAVAILABLE;
            if (!readable) allReadable = false;
            values.add(new AutismPacketFieldValue(
                field,
                readable ? value : null,
                formatValue(readable ? value : null, field, new IdentityHashMap<>(), 0),
                readable,
                readable && field.editableCandidate()
            ));
        }
        boolean complete = schema.complete() && allReadable && !schema.inheritedFallback();
        return new AutismPacketDecodedView(
            packet,
            schema,
            values,
            true,
            complete,
            complete ? "complete" : "fallback",
            complete ? "" : schema.inheritedFallback() ? "schema inherited from parent packet class" : "one or more schema fields were not readable"
        );
    }

    public Optional<AutismPacketFieldValue> field(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (AutismPacketFieldValue field : fields) {
            if (field.name().equals(name)) return Optional.of(field);
        }
        return Optional.empty();
    }

    private static Object readValue(Object target, String name) {
        if (target == null || name == null || name.isBlank()) return UNAVAILABLE;
        for (String methodName : accessorNames(name)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    return method.invoke(target);
                }
            } catch (Throwable ignored) {  }
        }
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field.get(target);
                }
            } catch (Throwable ignored) {  }
            current = current.getSuperclass();
        }
        return UNAVAILABLE;
    }

    private static List<String> accessorNames(String name) {
        String capitalized = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        return List.of(name, "get" + capitalized, "is" + capitalized);
    }

    private static List<AutismPacketFieldValue> reflectFields(Object packet, AutismPacketSchemaRegistry.PacketSchema schema) {
        List<AutismPacketSchemaRegistry.FieldSchema> fieldSchemas = new ArrayList<>();
        if (packet.getClass().isRecord()) {
            for (RecordComponent component : packet.getClass().getRecordComponents()) {
                String type = component.getGenericType().getTypeName();
                fieldSchemas.add(new AutismPacketSchemaRegistry.FieldSchema(component.getName(), type, kindForType(type), editableFor(kindForType(type))));
            }
        } else {
            List<Field> fields = new ArrayList<>();
            Class<?> current = packet.getClass();
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
                    fields.add(field);
                }
                current = current.getSuperclass();
            }
            fields.sort(Comparator.comparing(Field::getName));
            Set<String> seen = new LinkedHashSet<>();
            for (Field field : fields) {
                if (!seen.add(field.getName())) continue;
                String type = field.getGenericType().getTypeName();
                fieldSchemas.add(new AutismPacketSchemaRegistry.FieldSchema(field.getName(), type, kindForType(type), editableFor(kindForType(type))));
            }
        }

        List<AutismPacketFieldValue> values = new ArrayList<>();
        for (AutismPacketSchemaRegistry.FieldSchema field : fieldSchemas) {
            Object value = readValue(packet, field.name());
            boolean readable = value != UNAVAILABLE;
            values.add(new AutismPacketFieldValue(
                field,
                readable ? value : null,
                formatValue(readable ? value : null, field, new IdentityHashMap<>(), 0),
                readable,
                readable && field.editableCandidate()
            ));
        }
        return values;
    }

    private static String kindForType(String type) {
        if (type == null) return "object";
        String lower = type.toLowerCase(Locale.ROOT);
        if (Set.of("byte", "short", "int", "long", "float", "double").contains(lower)) return "number";
        if ("boolean".equals(lower)) return "boolean";
        if ("java.lang.string".equals(lower) || "string".equals(lower)) return "string";
        if (lower.contains("itemstack") || lower.contains("hashedstack")) return "item";
        if (lower.contains("component")) return "component";
        if (lower.contains("identifier") || lower.contains("resourcekey")) return "identifier";
        if (lower.contains("holder")) return "holder";
        if (lower.contains("blockpos") || lower.contains("chunkpos")) return "position";
        if (lower.contains("vec3") || lower.contains("positionmoverotation")) return "vector";
        if (lower.contains("uuid")) return "uuid";
        if (lower.contains("optional")) return "optional";
        if (lower.contains("list") || lower.contains("set")) return "list";
        if (lower.contains("map") || lower.contains("int2objectmap")) return "map";
        if (lower.contains("bitset")) return "bitset";
        if (lower.contains("containerinput") || lower.contains("relative")) return "enum";
        return "object";
    }

    private static boolean editableFor(String kind) {
        return Set.of("number", "boolean", "string", "identifier", "enum", "uuid").contains(kind);
    }

    private static List<String> formatValue(Object value, AutismPacketSchemaRegistry.FieldSchema field,
                                            IdentityHashMap<Object, Boolean> seen, int depth) {
        List<String> lines = new ArrayList<>();
        formatInto(lines, value, seen, depth);
        if (lines.isEmpty()) lines.add("null");
        return lines;
    }

    private static void formatInto(List<String> lines, Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null) {
            lines.add("null");
            return;
        }
        if (depth > 3) {
            lines.add(shorten(String.valueOf(value)));
            return;
        }
        if (value instanceof String string) {
            lines.add(quote(string));
            return;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof UUID || value instanceof Enum<?>) {
            lines.add(String.valueOf(value));
            return;
        }
        if (value instanceof Identifier identifier) {
            lines.add(identifier.toString());
            return;
        }
        if (value instanceof ResourceKey<?> key) {
            lines.add(key.identifier().toString());
            return;
        }
        if (value instanceof Component component) {
            lines.add(quote(component.getString()));
            return;
        }
        if (value instanceof ItemStack stack) {
            lines.add(formatItemStack(stack));
            return;
        }
        if (value instanceof HashedStack hashedStack) {
            lines.add(formatHashedStack(hashedStack));
            return;
        }
        if (value instanceof Holder<?> holder) {
            lines.add(formatHolder(holder));
            return;
        }
        if (value instanceof BlockPos pos) {
            lines.add("x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ());
            return;
        }
        if (value instanceof ChunkPos pos) {
            lines.add("x=" + pos.x() + ", z=" + pos.z());
            return;
        }
        if (value instanceof Vec3 vec) {
            lines.add(String.format(Locale.ROOT, "x=%.5f, y=%.5f, z=%.5f", vec.x, vec.y, vec.z));
            return;
        }
        if (value instanceof PositionMoveRotation movement) {
            lines.add("position=" + formatVec3(movement.position())
                + ", delta=" + formatVec3(movement.deltaMovement())
                + ", yaw=" + formatFloat(movement.yRot())
                + ", pitch=" + formatFloat(movement.xRot()));
            return;
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                lines.add("empty");
            } else {
                lines.add("present");
                List<String> nested = new ArrayList<>();
                formatInto(nested, optional.get(), seen, depth + 1);
                for (String line : nested) lines.add("  " + line);
            }
            return;
        }
        if (value instanceof Int2ObjectMap<?> fastMap) {
            lines.add("map entries=" + fastMap.size());
            int shown = 0;
            for (Int2ObjectMap.Entry<?> entry : fastMap.int2ObjectEntrySet()) {
                if (shown >= MAX_COLLECTION_PREVIEW) {
                    lines.add("  ... +" + (fastMap.size() - shown) + " more");
                    break;
                }
                lines.add("  " + entry.getIntKey() + " -> " + firstLine(entry.getValue(), seen, depth + 1));
                shown++;
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            lines.add("map entries=" + map.size());
            int shown = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (shown >= MAX_COLLECTION_PREVIEW) {
                    lines.add("  ... +" + (map.size() - shown) + " more");
                    break;
                }
                lines.add("  " + firstLine(entry.getKey(), seen, depth + 1) + " -> " + firstLine(entry.getValue(), seen, depth + 1));
                shown++;
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            lines.add("items=" + collection.size());
            int shown = 0;
            for (Object item : collection) {
                if (shown >= MAX_COLLECTION_PREVIEW) {
                    lines.add("  ... +" + (collection.size() - shown) + " more");
                    break;
                }
                lines.add("  #" + shown + " " + firstLine(item, seen, depth + 1));
                shown++;
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            lines.add("array length=" + length);
            for (int i = 0; i < Math.min(length, MAX_COLLECTION_PREVIEW); i++) {
                lines.add("  #" + i + " " + firstLine(Array.get(value, i), seen, depth + 1));
            }
            if (length > MAX_COLLECTION_PREVIEW) lines.add("  ... +" + (length - MAX_COLLECTION_PREVIEW) + " more");
            return;
        }
        if (value instanceof Tag tag) {
            lines.add(shorten(tag.toString()));
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            lines.add("<cycle " + value.getClass().getSimpleName() + ">");
            return;
        }
        lines.add(shorten(String.valueOf(value)));
        seen.remove(value);
    }

    private static String firstLine(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        List<String> lines = new ArrayList<>();
        formatInto(lines, value, seen, depth);
        return lines.isEmpty() ? "null" : lines.getFirst();
    }

    private static String formatItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        String itemId;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable ignored) {
            itemId = String.valueOf(stack.getItem());
        }
        StringBuilder out = new StringBuilder(itemId).append(" x").append(stack.getCount());
        try {
            if (stack.isDamageableItem()) {
                out.append(" damage=").append(stack.getDamageValue()).append('/').append(stack.getMaxDamage());
            }
        } catch (Throwable ignored) {  }
        try {
            Component customName = stack.getCustomName();
            if (customName != null) out.append(" name=").append(quote(customName.getString()));
        } catch (Throwable ignored) {  }
        try {
            String components = String.valueOf(stack.getComponentsPatch());
            if (!components.isBlank() && !"{}".equals(components)) out.append(" components=").append(shorten(components));
        } catch (Throwable ignored) {  }
        return out.toString();
    }

    private static String formatHashedStack(HashedStack stack) {
        if (stack == null || stack == HashedStack.EMPTY) return "empty";
        if (stack instanceof HashedStack.ActualItem actual) {
            return formatHolder(actual.item()) + " x" + actual.count() + " hashedComponents=" + shorten(String.valueOf(actual.components()));
        }
        return shorten(String.valueOf(stack));
    }

    private static String formatHolder(Holder<?> holder) {
        if (holder == null) return "null";
        try {
            Optional<? extends ResourceKey<?>> key = holder.unwrapKey();
            if (key.isPresent()) return key.get().identifier().toString();
        } catch (Throwable ignored) {  }
        Object value;
        try {
            value = holder.value();
        } catch (Throwable ignored) {
            value = holder;
        }
        if (value instanceof net.minecraft.world.item.Item item) return BuiltInRegistries.ITEM.getKey(item).toString();
        try {
            if (value instanceof Registry<?> registry) return String.valueOf(registry.key().identifier());
        } catch (Throwable ignored) {  }
        return shorten(String.valueOf(value));
    }

    private static String formatVec3(Vec3 vec) {
        if (vec == null) return "null";
        return String.format(Locale.ROOT, "(%.5f, %.5f, %.5f)", vec.x, vec.y, vec.z);
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        return "\"" + shorten(value.replace("\n", "\\n").replace("\r", "\\r")) + "\"";
    }

    private static String shorten(String value) {
        if (value == null) return "null";
        String trimmed = value.strip();
        return trimmed.length() <= MAX_STRING_LENGTH ? trimmed : trimmed.substring(0, MAX_STRING_LENGTH - 3) + "...";
    }

    public boolean isMovePlayerVariant() {
        return packet instanceof ServerboundMovePlayerPacket;
    }
}
