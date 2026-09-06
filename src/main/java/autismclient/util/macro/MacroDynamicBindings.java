package autismclient.util.macro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class MacroDynamicBindings {
    public static final String TAG_KEY = "_dynamicBindings";
    private static final Map<MacroAction, Map<String, String>> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    private MacroDynamicBindings() {}

    public static void load(MacroAction action, CompoundTag actionTag) {
        if (action == null) return;
        Map<String, String> values = new LinkedHashMap<>();
        if (actionTag != null && actionTag.get(TAG_KEY) instanceof CompoundTag bindings) {
            for (String key : bindings.keySet()) {
                String value = bindings.getStringOr(key, "");
                if (!value.isBlank()) values.put(key, value);
            }
        }
        if (values.isEmpty()) BINDINGS.remove(action);
        else BINDINGS.put(action, values);
    }

    public static void write(MacroAction action, CompoundTag actionTag) {
        if (action == null || actionTag == null) return;
        Map<String, String> values = BINDINGS.get(action);
        if (values == null || values.isEmpty()) {
            actionTag.remove(TAG_KEY);
            return;
        }
        CompoundTag bindings = new CompoundTag();
        values.forEach(bindings::putString);
        actionTag.put(TAG_KEY, bindings);
    }

    public static String get(MacroAction action, String key) {
        Map<String, String> values = BINDINGS.get(action);
        return values == null ? "" : values.getOrDefault(key, "");
    }

    public static void set(MacroAction action, String key, String template) {
        if (action == null || key == null || key.isBlank()) return;
        String value = template == null ? "" : template.trim();
        synchronized (BINDINGS) {
            Map<String, String> values = BINDINGS.computeIfAbsent(action, ignored -> new LinkedHashMap<>());
            if (value.isBlank()) values.remove(key);
            else values.put(key, value);
            if (values.isEmpty()) BINDINGS.remove(action);
        }
    }

    public static Map<String, String> snapshot(MacroAction action) {
        Map<String, String> values = BINDINGS.get(action);
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public static boolean apply(MacroAction action, Minecraft mc) {
        if (action == null) return true;
        for (Map.Entry<String, String> binding : snapshot(action).entrySet()) {
            MacroTemplate.Resolution resolution = MacroVariables.resolve(binding.getValue(), mc);
            if (!resolution.success()) return false;
            try {
                String value = resolution.value().trim();
                java.lang.reflect.Field field;
                try {
                    field = action.getClass().getField(binding.getKey());
                } catch (NoSuchFieldException missingField) {
                    if (applyBlockPositionBinding(action, binding.getKey(), value)) continue;
                    return false;
                }
                Class<?> type = field.getType();
                if (type == int.class || type == Integer.class) field.set(action, Integer.parseInt(value));
                else if (type == long.class || type == Long.class) field.set(action, Long.parseLong(value));
                else if (type == double.class || type == Double.class) field.set(action, Double.parseDouble(value));
                else if (type == float.class || type == Float.class) field.set(action, Float.parseFloat(value));
                else if (type == String.class) field.set(action, value);
                else if (type.isEnum()) {
                    Object selected = null;
                    for (Object constant : type.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(value)) { selected = constant; break; }
                    }
                    if (selected == null) return false;
                    field.set(action, selected);
                } else return false;
            } catch (ReflectiveOperationException | NumberFormatException invalid) {
                return false;
            }
        }
        return true;
    }

    private static boolean applyBlockPositionBinding(MacroAction action, String key, String rawValue) {
        String lower = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        int axis = lower.endsWith("x") ? 0 : lower.endsWith("y") ? 1 : lower.endsWith("z") ? 2 : -1;
        if (axis < 0) return false;
        int value;
        try { value = (int) Math.floor(Double.parseDouble(rawValue)); }
        catch (NumberFormatException ignored) { return false; }

        String preferred = lower.startsWith("container") ? "containerPos" : "blockPos";
        java.lang.reflect.Field positionField = null;
        try {
            positionField = action.getClass().getField(preferred);
        } catch (NoSuchFieldException ignored) {
            for (java.lang.reflect.Field candidate : action.getClass().getFields()) {
                if (candidate.getType() == net.minecraft.core.BlockPos.class) {
                    positionField = candidate;
                    break;
                }
            }
        }
        if (positionField == null || positionField.getType() != net.minecraft.core.BlockPos.class) return false;
        try {
            net.minecraft.core.BlockPos current = (net.minecraft.core.BlockPos) positionField.get(action);
            if (current == null) current = net.minecraft.core.BlockPos.ZERO;
            net.minecraft.core.BlockPos next = switch (axis) {
                case 0 -> new net.minecraft.core.BlockPos(value, current.getY(), current.getZ());
                case 1 -> new net.minecraft.core.BlockPos(current.getX(), value, current.getZ());
                default -> new net.minecraft.core.BlockPos(current.getX(), current.getY(), value);
            };
            positionField.set(action, next);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static int resolveInt(MacroAction action, String key, int fallback, Minecraft mc) {
        String template = get(action, key);
        if (template.isBlank()) return fallback;
        MacroTemplate.Resolution resolution = MacroVariables.resolve(template, mc);
        if (!resolution.success()) throw new MissingDynamicValueException();
        try { return Integer.parseInt(resolution.value().trim()); }
        catch (NumberFormatException ignored) {
            long compact = PayAction.parseAmount(resolution.value());
            if (compact > Integer.MAX_VALUE || compact < Integer.MIN_VALUE) throw new MissingDynamicValueException();
            return (int) compact;
        }
    }

    public static double resolveDouble(MacroAction action, String key, double fallback, Minecraft mc) {
        String template = get(action, key);
        if (template.isBlank()) return fallback;
        MacroTemplate.Resolution resolution = MacroVariables.resolve(template, mc);
        if (!resolution.success()) throw new MissingDynamicValueException();
        try { return Double.parseDouble(resolution.value().trim()); }
        catch (NumberFormatException ignored) { throw new MissingDynamicValueException(); }
    }

    public static final class MissingDynamicValueException extends RuntimeException {
        private MissingDynamicValueException() { super("Missing or invalid dynamic value", null, false, false); }
    }
}
