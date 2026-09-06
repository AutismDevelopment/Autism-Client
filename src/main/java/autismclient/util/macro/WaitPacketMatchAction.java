package autismclient.util.macro;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;

public class WaitPacketMatchAction implements MacroAction, MacroCaptureOutput {
    public enum Direction { C2S, S2C, ANY }
    public enum Operator { EXISTS, EQUALS, CONTAINS, NOT_EQUALS }

    public static class Rule {
        public Direction direction = Direction.C2S;
        public String packetName = "";
        public String fieldName = "";
        public Operator operator = Operator.EXISTS;
        public String value = "";

        public Rule copy() {
            Rule copy = new Rule();
            copy.direction = direction;
            copy.packetName = packetName == null ? "" : packetName;
            copy.fieldName = fieldName == null ? "" : fieldName;
            copy.operator = operator;
            copy.value = value == null ? "" : value;
            return copy;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("direction", direction.name());
            tag.putString("packetName", packetName == null ? "" : packetName);
            tag.putString("fieldName", fieldName == null ? "" : fieldName);
            tag.putString("operator", operator.name());
            tag.putString("value", value == null ? "" : value);
            return tag;
        }

        static Rule fromTag(CompoundTag tag) {
            Rule rule = new Rule();
            rule.direction = MacroStringList.enumValue(Direction.class, tag.getStringOr("direction", "C2S"), Direction.C2S);
            rule.packetName = tag.getStringOr("packetName", "");
            rule.fieldName = tag.getStringOr("fieldName", "");
            rule.operator = MacroStringList.enumValue(Operator.class, tag.getStringOr("operator", "EXISTS"), Operator.EXISTS);
            rule.value = tag.getStringOr("value", "");
            return rule;
        }
    }

    public final List<Rule> rules = new ArrayList<>();

    public Direction direction = Direction.C2S;
    public String packetName = "";
    public String fieldName = "";
    public Operator operator = Operator.EXISTS;
    public String value = "";
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    public transient volatile Packet<?> matchedPacket;
    public transient volatile String matchedDirection = "";

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.WAIT_PACKET_MATCH; }

    public List<Rule> effectiveRules() {
        if (!rules.isEmpty()) return rules;
        Rule legacy = legacyRule();
        return legacy.packetName.isBlank() && legacy.fieldName.isBlank() ? List.of() : List.of(legacy);
    }

    public Rule addRule(Direction direction, String packetName) {
        Rule rule = new Rule();
        rule.direction = direction == null ? Direction.ANY : direction;
        rule.packetName = packetName == null ? "" : packetName;
        rules.add(rule);
        syncLegacyFromFirstRule();
        return rule;
    }

    public void syncLegacyFromFirstRule() {
        Rule first = rules.isEmpty() ? null : rules.get(0);
        if (first == null) return;
        direction = first.direction;
        packetName = first.packetName == null ? "" : first.packetName;
        fieldName = first.fieldName == null ? "" : first.fieldName;
        operator = first.operator == null ? Operator.EXISTS : first.operator;
        value = first.value == null ? "" : first.value;
    }

    public boolean matches(Packet<?> packet, String packetDirection) {
        List<Rule> activeRules = effectiveRules();
        if (activeRules.isEmpty()) return true;
        for (Rule rule : activeRules) {
            if (matchesRule(rule, packet, packetDirection)) return true;
        }
        return false;
    }

    private boolean matchesRule(Rule rule, Packet<?> packet, String packetDirection) {
        if (rule.direction != Direction.ANY && !rule.direction.name().equalsIgnoreCase(packetDirection)) return false;
        if (!PacketGateManager.matchesPacket(rule.packetName, packet, packetDirection)) return false;
        if (rule.fieldName == null || rule.fieldName.isBlank()) return true;
        Object field = readField(packet, rule.fieldName);
        Operator op = rule.operator == null ? Operator.EXISTS : rule.operator;
        if (op == Operator.EXISTS) return field != null;
        String actual = field == null ? "" : String.valueOf(field);
        String expected = rule.value == null ? "" : rule.value;
        return switch (op) {
            case EXISTS -> field != null;
            case EQUALS -> actual.equalsIgnoreCase(expected);
            case CONTAINS -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case NOT_EQUALS -> !actual.equalsIgnoreCase(expected);
        };
    }

    private Rule legacyRule() {
        Rule rule = new Rule();
        rule.direction = direction == null ? Direction.C2S : direction;
        rule.packetName = packetName == null ? "" : packetName;
        rule.fieldName = fieldName == null ? "" : fieldName;
        rule.operator = operator == null ? Operator.EXISTS : operator;
        rule.value = value == null ? "" : value;
        return rule;
    }

    private Object readField(Packet<?> packet, String name) {
        if (packet == null || name == null || name.isBlank()) return null;
        String wanted = name.trim();
        try {
            Method m = packet.getClass().getMethod(wanted);
            if (m.getParameterCount() == 0) return m.invoke(packet);
        } catch (Exception ignored) {  }
        String getter = "get" + Character.toUpperCase(wanted.charAt(0)) + wanted.substring(1);
        try {
            Method m = packet.getClass().getMethod(getter);
            if (m.getParameterCount() == 0) return m.invoke(packet);
        } catch (Exception ignored) {  }
        Class<?> type = packet.getClass();
        while (type != null && type != Object.class) {
            try {
                Field f = type.getDeclaredField(wanted);
                f.setAccessible(true);
                return f.get(packet);
            } catch (Exception ignored) {  }
            type = type.getSuperclass();
        }
        return null;
    }

    public static List<String> packetFieldNames(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null) return List.of();
        Map<String, Class<?>> fields = packetFields(packetClass);
        return fields.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static List<String> valueOptions(Class<? extends Packet<?>> packetClass, String fieldName) {
        Class<?> type = packetFields(packetClass).get(fieldName);
        if (type == null) return List.of();
        if (type == boolean.class || type == Boolean.class) return List.of("true", "false");
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            List<String> out = new ArrayList<>();
            if (constants != null) for (Object constant : constants) out.add(String.valueOf(constant));
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        return List.of();
    }

    public static Map<String, Class<?>> packetFields(Class<? extends Packet<?>> packetClass) {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        if (packetClass == null) return out;

        RecordComponent[] components = packetClass.getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                out.putIfAbsent(component.getName(), component.getType());
            }
        }

        Class<?> type = packetClass;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                out.putIfAbsent(field.getName(), field.getType());
            }
            type = type.getSuperclass();
        }

        for (Method method : packetClass.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE || method.isSynthetic()) continue;
            String name = method.getName();
            if (name.equals("getClass") || name.equals("hashCode") || name.equals("toString")
                    || name.equals("packetType") || name.equals("getPacketType")) continue;
            if (name.startsWith("lambda$") || name.startsWith("access$")) continue;
            if (name.startsWith("get") && name.length() > 3) {
                name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            }
            out.putIfAbsent(name, method.getReturnType());
        }

        return out.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "WAIT_PACKET_MATCH");
        ListTag list = new ListTag();
        for (Rule rule : effectiveRules()) list.add(rule.toTag());
        tag.put("rules", list);
        tag.putInt("timeoutMs", timeoutMs);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        rules.clear();
        if (tag.contains("rules")) {
            ListTag list = tag.getList("rules").orElse(new ListTag());
            for (Tag element : list) {
                if (element instanceof CompoundTag compound) rules.add(Rule.fromTag(compound));
            }
        }
        direction = MacroStringList.enumValue(Direction.class, tag.getStringOr("direction", "C2S"), Direction.C2S);
        packetName = tag.getStringOr("packetName", "");
        fieldName = tag.getStringOr("fieldName", "");
        operator = MacroStringList.enumValue(Operator.class, tag.getStringOr("operator", "EXISTS"), Operator.EXISTS);
        value = tag.getStringOr("value", "");
        if (rules.isEmpty() && (tag.contains("packetName") || tag.contains("fieldName"))) {
            rules.add(legacyRule());
        }
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        syncLegacyFromFirstRule();
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    @Override public String getDisplayName() {
        List<Rule> activeRules = effectiveRules();
        if (activeRules.isEmpty()) return "Wait Packet Match: Any";
        Rule first = activeRules.get(0);
        String label = "Wait " + first.direction + " " + first.packetName;
        if (first.fieldName != null && !first.fieldName.isBlank()) {
            label += " " + first.fieldName + " " + first.operator + " " + first.value;
        }
        return activeRules.size() == 1 ? label : label + " (+" + (activeRules.size() - 1) + ")";
    }

    @Override public String getIcon() { return "P"; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
