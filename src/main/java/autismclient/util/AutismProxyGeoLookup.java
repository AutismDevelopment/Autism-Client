package autismclient.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AutismProxyGeoLookup {
    private static final String API_URL = "https://api.country.is/?fields=city,continent,subdivision,location,asn";

    private AutismProxyGeoLookup() {
    }

    public record ResolveResult(String ip, GeoResult immediateResult) {
    }

    public record GeoResult(
        AutismProxy.GeoStatus status,
        String resolvedIp,
        String countryCode,
        String countryName,
        String region,
        String city,
        String subdivision,
        long checkedAt
    ) {
        public static GeoResult failed(String ip, long now) {
            return new GeoResult(AutismProxy.GeoStatus.FAILED, safe(ip), "", "", "", "", "", now);
        }

        public static GeoResult privateAddress(String ip, long now) {
            return new GeoResult(AutismProxy.GeoStatus.PRIVATE, safe(ip), "", "", "", "", "", now);
        }
    }

    public static ResolveResult resolveAddress(String address) {
        long now = System.currentTimeMillis();
        try {
            String clean = cleanAddress(address);
            if (clean.isBlank()) return new ResolveResult("", GeoResult.failed("", now));
            InetAddress inetAddress = InetAddress.getByName(clean);
            String ip = inetAddress.getHostAddress();
            if (!isPublicAddress(inetAddress)) return new ResolveResult(ip, GeoResult.privateAddress(ip, now));
            return new ResolveResult(ip, null);
        } catch (Exception ignored) {
            return new ResolveResult("", GeoResult.failed("", now));
        }
    }

    public static Map<String, GeoResult> lookupBatch(List<String> ips) {
        Map<String, GeoResult> out = new HashMap<>();
        if (ips == null || ips.isEmpty()) return out;
        long now = System.currentTimeMillis();
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Autism-ProxyGeo");
            connection.setDoOutput(true);
            String body = jsonArray(ips);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return failedMap(ips, now);
            String raw;
            try (InputStream input = connection.getInputStream()) {
                raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed.isJsonArray()) {
                for (JsonElement element : parsed.getAsJsonArray()) {
                    GeoResult result = parseResult(element, now);
                    if (result != null && !result.resolvedIp().isBlank()) out.put(result.resolvedIp(), result);
                }
            } else {
                GeoResult result = parseResult(parsed, now);
                if (result != null && !result.resolvedIp().isBlank()) out.put(result.resolvedIp(), result);
            }
        } catch (Exception ignored) {
            return failedMap(ips, now);
        }
        for (String ip : ips) out.putIfAbsent(ip, GeoResult.failed(ip, now));
        return out;
    }

    private static GeoResult parseResult(JsonElement element, long now) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String ip = firstString(object, "ip", "query");
        if (ip.isBlank()) return null;
        String countryCode = firstString(object, "country", "countryCode", "country_code").toUpperCase(Locale.ROOT);
        String countryName = firstString(object, "countryName", "country_name");
        if (countryName.isBlank()) countryName = displayCountry(countryCode);
        String continent = firstString(object, "continent", "continentCode", "continent_code");
        String region = displayContinent(continent);
        String city = firstString(object, "city");
        String subdivision = subdivision(object.get("subdivision"));
        if (countryCode.isBlank() && region.isBlank() && city.isBlank()) {
            return GeoResult.failed(ip, now);
        }
        return new GeoResult(AutismProxy.GeoStatus.RESOLVED, ip, countryCode, countryName, region, city, subdivision, now);
    }

    private static Map<String, GeoResult> failedMap(List<String> ips, long now) {
        Map<String, GeoResult> out = new HashMap<>();
        for (String ip : ips) out.put(ip, GeoResult.failed(ip, now));
        return out;
    }

    private static String jsonArray(List<String> ips) {
        JsonArray array = new JsonArray();
        for (String ip : ips) array.add(ip);
        return array.toString();
    }

    private static String subdivision(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) return firstString(element.getAsJsonObject(), "name", "code", "iso_code");
        return "";
    }

    private static String displayCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return "";
        try {
            return new Locale.Builder()
                .setRegion(countryCode.toUpperCase(Locale.ROOT))
                .build()
                .getDisplayCountry(Locale.ENGLISH);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String displayContinent(String continent) {
        String code = safe(continent).toUpperCase(Locale.ROOT);
        return switch (code) {
            case "AF" -> "Africa";
            case "AN" -> "Antarctica";
            case "AS" -> "Asia";
            case "EU" -> "Europe";
            case "NA" -> "North America";
            case "OC" -> "Oceania";
            case "SA" -> "South America";
            default -> safe(continent);
        };
    }

    private static String firstString(JsonObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return "";
    }

    private static String cleanAddress(String address) {
        String clean = safe(address).trim();
        if (clean.startsWith("[") && clean.endsWith("]")) return clean.substring(1, clean.length() - 1);
        return clean;
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address == null
            || address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return isPublicIpv4(bytes);
        if (bytes.length == 16) return isPublicIpv6(bytes);
        return true;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        int c = Byte.toUnsignedInt(bytes[2]);
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false;
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && (b == 0 || b == 2 || b == 168)) return false;
        if (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) return false;
        if (a == 203 && b == 0 && c == 113) return false;
        return true;
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        int fourth = Byte.toUnsignedInt(bytes[3]);
        if ((first & 0xFE) == 0xFC) return false;
        return !(first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
