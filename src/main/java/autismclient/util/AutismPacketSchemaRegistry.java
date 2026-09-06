package autismclient.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AutismPacketSchemaRegistry {
    private static volatile Map<String, PacketSchema> schemas;

    private AutismPacketSchemaRegistry() {
    }

    public static PacketSchema find(Class<?> packetClass) {
        if (packetClass == null) return null;
        Map<String, PacketSchema> map = load();
        PacketSchema exact = map.get(packetClass.getName());
        if (exact != null) return exact;
        exact = map.get(packetClass.getSimpleName());
        if (exact != null) return exact;
        Class<?> parent = packetClass.getSuperclass();
        while (parent != null && parent != Object.class) {
            exact = map.get(parent.getName());
            if (exact != null) return exact.asFallbackFor(packetClass);
            parent = parent.getSuperclass();
        }
        return null;
    }

    public static int schemaCount() {
        return load().values().stream().map(PacketSchema::className).collect(java.util.stream.Collectors.toSet()).size();
    }

    private static Map<String, PacketSchema> load() {
        Map<String, PacketSchema> current = schemas;
        if (current != null) return current;
        synchronized (AutismPacketSchemaRegistry.class) {
            current = schemas;
            if (current != null) return current;
            Map<String, PacketSchema> loaded = new LinkedHashMap<>();
            try (InputStream in = AutismPacketSchemaRegistry.class.getResourceAsStream("/autism-packet-schemas.tsv")) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || line.startsWith("#")) continue;
                            PacketSchema schema = parse(line);
                            if (schema == null) continue;
                            loaded.put(schema.className(), schema);
                            loaded.put(schema.simpleName(), schema);
                        }
                    }
                }
            } catch (Throwable ignored) {
                loaded.clear();
            }
            schemas = Collections.unmodifiableMap(loaded);
            return schemas;
        }
    }

    private static PacketSchema parse(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 8) return null;
        List<FieldSchema> fields = new ArrayList<>();
        if (!parts[7].isBlank()) {
            for (String fieldRaw : parts[7].split("\\|", -1)) {
                String[] fieldParts = fieldRaw.split("~", -1);
                if (fieldParts.length < 4) continue;
                fields.add(new FieldSchema(
                    fieldParts[0],
                    fieldParts[1],
                    fieldParts[2].toLowerCase(Locale.ROOT),
                    Boolean.parseBoolean(fieldParts[3])
                ));
            }
        }
        return new PacketSchema(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], Boolean.parseBoolean(parts[6]), fields, false);
    }

    public record PacketSchema(String className,
                               String protocol,
                               String direction,
                               String codecStyle,
                               String packetType,
                               String source,
                               boolean complete,
                               List<FieldSchema> fields,
                               boolean inheritedFallback) {
        public PacketSchema {
            protocol = blankTo(protocol, "unknown");
            direction = blankTo(direction, "ANY");
            codecStyle = blankTo(codecStyle, "unknown");
            packetType = packetType == null ? "" : packetType;
            source = blankTo(source, "fallback");
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public String simpleName() {
            String name = className == null ? "" : className.substring(className.lastIndexOf('.') + 1);
            return name.replace('$', '.');
        }

        public PacketSchema asFallbackFor(Class<?> concreteClass) {
            if (concreteClass == null) return this;
            return new PacketSchema(
                concreteClass.getName(),
                protocol,
                direction,
                codecStyle,
                packetType,
                source,
                complete,
                fields,
                true
            );
        }
    }

    public record FieldSchema(String name, String javaType, String valueKind, boolean editableCandidate) {
        public FieldSchema {
            name = blankTo(name, "field");
            javaType = blankTo(javaType, "Object");
            valueKind = blankTo(valueKind, "object");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
