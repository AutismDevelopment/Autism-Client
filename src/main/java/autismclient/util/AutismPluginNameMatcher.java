package autismclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AutismPluginNameMatcher {
    private AutismPluginNameMatcher() {
    }

    static String normalizeVersionless(String value) {
        if (value == null) return "";
        String text = value.toLowerCase(Locale.ROOT)
            .replaceAll("(?i)(?:^|[\\s_\\-\\[\\(])v?\\d+(?:\\.\\d+)+(?:[-+][a-z0-9_.-]+)?(?=$|[\\s_\\-\\]\\)])", " ")
            .replaceAll("(?i)\\b(?:free|premium|pro|plus|paid|plugin|addon|spigot|bukkit|paper)\\b", " ");
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) normalized.append(c);
        }
        return normalized.toString();
    }

    static double similarity(String detected, String provider) {
        String a = normalizeVersionless(detected);
        String b = normalizeVersionless(provider);
        if (a.isBlank() || b.isBlank()) return 0.0D;
        if (a.equals(b)) return 1.0D;
        if (a.length() >= 6 && b.contains(a) && ((double) a.length() / Math.max(1, b.length())) >= 0.70D) return 0.93D;
        if (b.length() >= 6 && a.contains(b) && ((double) b.length() / Math.max(1, a.length())) >= 0.70D) return 0.92D;
        int distance = levenshtein(a, b, 8);
        if (distance > 8) return 0.0D;
        return 1.0D - ((double) distance / Math.max(a.length(), b.length()));
    }

    static String bestMatchingReference(String detected, Collection<String> providerReferences, double minimumScore) {
        if (providerReferences == null || providerReferences.isEmpty()) return null;
        String best = null;
        double bestScore = minimumScore;
        for (String reference : providerReferences) {
            double score = similarity(detected, reference);
            if (score < bestScore) continue;
            if (best == null || score > bestScore) {
                best = reference;
                bestScore = score;
            }
        }
        return best;
    }

    static List<String> extractProviderReferences(JsonObject object) {
        if (object == null) return List.of();
        Set<String> references = new LinkedHashSet<>();
        addProviderReference(references,
            firstString(object, "pluginName", "plugin_name", "affectedPlugin", "affected_plugin"),
            firstString(object, "pluginVersion", "plugin_version", "affectedPluginVersion", "affected_plugin_version"));
        JsonElement plugin = object.get("plugin");
        if (plugin != null && plugin.isJsonPrimitive()) {
            try {
                addProviderReference(references, plugin.getAsString(), null);
            } catch (Exception ignored) {  }
        }
        JsonElement plugins = firstPresent(object, "plugins", "affectedPlugins", "affected_plugins");
        collectProviderReferences(plugins, references);
        return List.copyOf(references);
    }

    private static void collectProviderReferences(JsonElement element, Set<String> out) {
        if (element == null || element.isJsonNull() || out == null) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectProviderReferences(child, out);
            return;
        }
        if (element.isJsonPrimitive()) {
            try {
                addProviderReference(out, element.getAsString(), null);
            } catch (Exception ignored) {  }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject reference = element.getAsJsonObject();
        addProviderReference(out,
            firstString(reference, "name", "pluginName", "plugin_name", "plugin"),
            firstString(reference, "version", "pluginVersion", "plugin_version"));
    }

    private static void addProviderReference(Set<String> out, String name, String version) {
        if (out == null || name == null || name.isBlank()) return;
        String cleanName = name.trim();
        if (version != null && !version.isBlank()) out.add(cleanName + " " + version.trim());
        out.add(cleanName);
    }

    private static JsonElement firstPresent(JsonObject object, String... keys) {
        if (object == null || keys == null) return null;
        for (String key : keys) {
            if (key != null && object.has(key)) return object.get(key);
        }
        return null;
    }

    private static String firstString(JsonObject object, String... keys) {
        if (object == null || keys == null) return null;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()) continue;
            try {
                String text = value.getAsString();
                if (text != null && !text.isBlank()) return text.trim();
            } catch (Exception ignored) {  }
        }
        return null;
    }

    private static int levenshtein(String a, String b, int max) {
        if (a == null || b == null) return max + 1;
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowBest = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowBest = Math.min(rowBest, curr[j]);
            }
            if (rowBest > max) return max + 1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
