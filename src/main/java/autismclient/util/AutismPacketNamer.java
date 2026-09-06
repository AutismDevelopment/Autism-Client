package autismclient.util;

import net.minecraft.network.protocol.Packet;

public class AutismPacketNamer {
    private static final String UNKNOWN_PACKET = "Unknown Packet";
    private static final java.util.concurrent.ConcurrentMap<Class<?>, String> CLASS_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<String, String> DIRECTIONAL_CLASS_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.ConcurrentMap<Class<?>, String> S2C_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Class<?>, String> C2S_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.ConcurrentMap<String, String> NORMALIZED_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> MANUAL_NAME_ALIASES = createManualNameAliases();

    private static String stripSuffix(String name) {
        if (name != null && name.endsWith("Packet")) {
            return name.substring(0, name.length() - 6);
        }
        return name;
    }

    public static String getFriendlyName(Packet<?> packet) {
        @SuppressWarnings("unchecked")
        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();
        String cached = CLASS_NAME_CACHE.get(packetClass);
        if (cached != null) return cached;
        String name = resolveRegistryName(packetClass);
        if (name != null) return cache(packetClass, stripSuffix(name));

        String typeName = resolveNameFromPacketType(packet);
        if (typeName != null) return cache(packetClass, stripSuffix(typeName));

        String inspected = inspectObfuscatedPacket(packet, packetClass.getSimpleName());
        if (inspected != null) return cache(packetClass, inspected);

        String readable = deriveReadableClassName(packetClass);
        return cache(packetClass, readable != null ? readable : UNKNOWN_PACKET);
    }

    public static String getFriendlyName(Packet<?> packet, String direction) {
        @SuppressWarnings("unchecked")
        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();
        java.util.concurrent.ConcurrentMap<Class<?>, String> classCache = directionalClassCache(direction);
        if (classCache != null) {
            String fast = classCache.get(packetClass);
            if (fast != null) return fast;
        }
        String cacheKey = directionalKey(packetClass, direction);
        String cached = DIRECTIONAL_CLASS_NAME_CACHE.get(cacheKey);
        if (cached != null) return cacheDirectional(classCache, packetClass, cached);
        String name = resolveRegistryName(packetClass, direction);
        if (name != null) return cacheDirectional(classCache, packetClass, cache(cacheKey, stripSuffix(name)));

        String typeName = resolveNameFromPacketType(packet, direction);
        if (typeName != null) return cacheDirectional(classCache, packetClass, cache(cacheKey, stripSuffix(typeName)));

        String inspected = inspectObfuscatedPacket(packet, packetClass.getSimpleName());
        if (inspected != null) return cacheDirectional(classCache, packetClass, cache(cacheKey, inspected));

        String readable = deriveReadableClassName(packetClass);
        return cacheDirectional(classCache, packetClass, cache(cacheKey, readable != null ? readable : UNKNOWN_PACKET));
    }

    private static java.util.concurrent.ConcurrentMap<Class<?>, String> directionalClassCache(String direction) {

        if ("S2C".equalsIgnoreCase(direction)) return S2C_NAME_CACHE;
        if ("C2S".equalsIgnoreCase(direction)) return C2S_NAME_CACHE;
        return null;
    }

    private static String cacheDirectional(java.util.concurrent.ConcurrentMap<Class<?>, String> classCache, Class<?> packetClass, String value) {
        if (classCache != null && value != null) classCache.putIfAbsent(packetClass, value);
        return value;
    }

    public static String getFriendlyName(Class<? extends Packet<?>> packetClass) {
        String cached = CLASS_NAME_CACHE.get(packetClass);
        if (cached != null) return cached;
        String name = resolveRegistryName(packetClass);
        if (name != null) return cache(packetClass, stripSuffix(name));

        String readable = deriveReadableClassName(packetClass);
        return cache(packetClass, readable != null ? readable : UNKNOWN_PACKET);
    }

    private static String cache(Class<?> packetClass, String value) {
        String safe = value == null ? UNKNOWN_PACKET : value;
        if (packetClass != null) CLASS_NAME_CACHE.putIfAbsent(packetClass, safe);
        return safe;
    }

    private static String cache(String key, String value) {
        String safe = value == null ? UNKNOWN_PACKET : value;
        if (key != null) DIRECTIONAL_CLASS_NAME_CACHE.putIfAbsent(key, safe);
        return safe;
    }

    private static String directionalKey(Class<?> packetClass, String direction) {
        String dir = direction == null ? "" : direction.toUpperCase(java.util.Locale.ROOT);
        return dir + "|" + (packetClass == null ? "" : packetClass.getName());
    }

    public static String getFriendlyName(String name) {
        if (name == null || name.isEmpty()) return "";

        Class<? extends Packet<?>> packetClass = AutismPacketRegistry.getPacket(name);
        if (packetClass != null) {
            String resolved = resolveRegistryName(packetClass);
            return resolved != null ? stripSuffix(resolved) : UNKNOWN_PACKET;
        }

        String resolved = resolveNameFromText(name);
        if (resolved != null) return stripSuffix(resolved);

        String readable = prettifyRawName(name);
        return readable != null ? readable : UNKNOWN_PACKET;
    }

    private static String resolveRegistryName(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null) return null;

        String direct = AutismPacketRegistry.getName(packetClass);
        if (direct != null) return direct;

        for (Class<? extends Packet<?>> registeredClass : AutismPacketRegistry.PACKETS) {
            if (registeredClass == packetClass || registeredClass.isAssignableFrom(packetClass)) {
                String name = AutismPacketRegistry.getName(registeredClass);
                if (name != null) return name;
            }
        }

        String byText = resolveNameFromText(packetClass.getName());
        if (byText != null) return byText;

        return resolveNameFromText(packetClass.getCanonicalName());
    }

    private static String resolveRegistryName(Class<? extends Packet<?>> packetClass, String direction) {
        if (packetClass == null) return null;

        String direct = resolveDirectionalRegistryName(packetClass, direction);
        if (direct != null) return direct;

        String byText = resolveNameFromText(packetClass.getName(), direction);
        if (byText != null) return byText;

        return resolveNameFromText(packetClass.getCanonicalName(), direction);
    }

    private static String resolveNameFromPacketType(Packet<?> packet) {
        if (packet == null) return null;

        try {
            Object packetType = packet.getClass().getMethod("getPacketType").invoke(packet);
            if (packetType == null) return null;

            return resolveNameFromText(packetType.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveNameFromPacketType(Packet<?> packet, String direction) {
        if (packet == null) return null;

        try {
            Object packetType = packet.getClass().getMethod("getPacketType").invoke(packet);
            if (packetType == null) return null;

            return resolveNameFromText(packetType.toString(), direction);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveNameFromText(String text) {
        if (text == null || text.isEmpty()) return null;

        String normalizedText = normalizePacketKey(text);
        if (normalizedText.isEmpty()) return null;

        String manual = MANUAL_NAME_ALIASES.get(normalizedText);
        if (manual != null) return manual;

        for (Class<? extends Packet<?>> registeredClass : AutismPacketRegistry.PACKETS) {
            String registeredName = AutismPacketRegistry.getName(registeredClass);
            if (registeredName == null) continue;

            if (matchesRegisteredName(normalizedText, registeredName, registeredClass)) {
                return registeredName;
            }
        }

        return null;
    }

    private static String resolveNameFromText(String text, String direction) {
        if (text == null || text.isEmpty()) return null;

        String normalizedText = normalizePacketKey(text);
        if (normalizedText.isEmpty()) return null;

        String manual = MANUAL_NAME_ALIASES.get(normalizedText);
        if (manual != null) return manual;

        for (Class<? extends Packet<?>> registeredClass : getDirectionalRegistry(direction)) {
            String registeredName = AutismPacketRegistry.getName(registeredClass);
            if (registeredName == null) continue;

            if (matchesRegisteredName(normalizedText, registeredName, registeredClass)) {
                return registeredName;
            }
        }

        return null;
    }

    private static String resolveDirectionalRegistryName(Class<? extends Packet<?>> packetClass, String direction) {
        for (Class<? extends Packet<?>> registeredClass : getDirectionalRegistry(direction)) {
            if (registeredClass == packetClass) {
                return AutismPacketRegistry.getName(registeredClass);
            }
        }
        return null;
    }

    private static Iterable<Class<? extends Packet<?>>> getDirectionalRegistry(String direction) {
        if ("C2S".equalsIgnoreCase(direction)) return AutismPacketRegistry.getC2SPackets();
        if ("S2C".equalsIgnoreCase(direction)) return AutismPacketRegistry.getS2CPackets();
        return AutismPacketRegistry.PACKETS;
    }

    private static boolean matchesRegisteredName(String normalizedText, String registeredName, Class<? extends Packet<?>> registeredClass) {
        String normalizedRegistered = normalizePacketKey(registeredName);
        if (normalizedRegistered.equals(normalizedText)) return true;

        String strippedRegistered = normalizePacketKey(stripSuffix(registeredName));
        if (strippedRegistered.equals(normalizedText)) return true;

        String className = normalizePacketKey(registeredClass.getName());
        if (className.equals(normalizedText) || normalizedText.endsWith(className)) return true;

        String simpleName = normalizePacketKey(registeredClass.getSimpleName());
        if (simpleName.equals(normalizedText) || normalizedText.endsWith(simpleName)) return true;

        String canonicalName = normalizePacketKey(registeredClass.getCanonicalName());
        if (!canonicalName.isEmpty() && (canonicalName.equals(normalizedText) || normalizedText.endsWith(canonicalName))) return true;

        return normalizedText.endsWith(normalizedRegistered) || normalizedText.endsWith(strippedRegistered);
    }

    private static java.util.Map<String, String> createManualNameAliases() {
        java.util.Map<String, String> aliases = new java.util.HashMap<>();
        registerManualAlias(aliases, "BundleS2CPacket",
            "BundleS2C",
            "class_8042",
            "net.minecraft.class_8042",
            "net.minecraft.network.protocol.game.BundleS2CPacket",
            "net.minecraft.network.protocol.game.ClientboundBundlePacket");
        return java.util.Collections.unmodifiableMap(aliases);
    }

    private static void registerManualAlias(java.util.Map<String, String> aliases, String canonicalName, String... variants) {
        aliases.put(normalizePacketKey(canonicalName), canonicalName);
        aliases.put(normalizePacketKey(stripSuffix(canonicalName)), canonicalName);
        for (String variant : variants) {
            if (variant != null && !variant.isEmpty()) {
                aliases.put(normalizePacketKey(variant), canonicalName);
            }
        }
    }

    private static String deriveReadableClassName(Class<?> packetClass) {
        if (packetClass == null) return null;

        String canonical = prettifyRawName(packetClass.getCanonicalName());
        if (canonical != null) return canonical;

        return prettifyRawName(packetClass.getName());
    }

    private static String prettifyRawName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;

        int lastDot = rawName.lastIndexOf('.');
        String shortName = lastDot >= 0 ? rawName.substring(lastDot + 1) : rawName;
        shortName = shortName.replace('$', '.');
        if (isObfuscatedName(shortName)) return null;

        return stripSuffix(shortName);
    }

    private static boolean isObfuscatedName(String name) {
        String normalized = normalizePacketKey(name);
        return normalized.startsWith("class") && normalized.length() > 5;
    }

    public static String normalizePacketKey(String value) {
        if (value == null || value.isEmpty()) return "";
        if (NORMALIZED_CACHE.size() > 4096) NORMALIZED_CACHE.clear();
        return NORMALIZED_CACHE.computeIfAbsent(value, AutismPacketNamer::normalizePacketKeyUncached);
    }

    private static String normalizePacketKeyUncached(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }

    private static String inspectObfuscatedPacket(Packet<?> packet, String className) {
        try {
            String packetStr = packet.toString();

            if (packetStr.contains("slot=") || packetStr.contains("Slot")) {
                if (packetStr.contains("button=") || packetStr.contains("Button")) {
                    return "Click Slot";
                }
                return "Slot Action";
            }

            if (packetStr.contains("hand=") || packetStr.contains("Hand")) {
                return "Swing Hand";
            }

            if (packetStr.contains("button=") || packetStr.contains("Button")) {
                return "Click Button";
            }

            if (packetStr.contains("action=") || packetStr.contains("Action")) {
                if (packetStr.contains("DROP_ITEM") || packetStr.contains("drop")) return "Drop Item";
                if (packetStr.contains("DROP_ALL") || packetStr.contains("drop_all")) return "Drop Stack";
                if (packetStr.contains("SWAP") || packetStr.contains("swap")) return "Swap Hands";
                return "Player Action";
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
