package autismclient.util;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismPayloadChannelRegistrations {
    public static final String SOURCE_CUSTOM = "custom";
    public static final String SOURCE_PRESET = "preset";
    public static final String SOURCE_LEARNED = "learned";

    private final List<AutismConfig.PayloadChannelRegistrationRule> rules = new ArrayList<>();

    public AutismPayloadChannelRegistrations() {
        load();
    }

    public void load() {
        rules.clear();
        AutismConfig config = AutismConfig.getGlobal();
        if (config.packetLoggerPayloadRegistrations == null) {
            config.packetLoggerPayloadRegistrations = new ArrayList<>();
        }

        Map<String, AutismConfig.PayloadChannelRegistrationRule> merged = new LinkedHashMap<>();
        for (AutismConfig.PayloadChannelRegistrationRule rule : config.packetLoggerPayloadRegistrations) {
            AutismConfig.PayloadChannelRegistrationRule clean = clean(rule);
            if (clean == null) continue;
            mergeInto(merged, clean);
        }

        if (merged.isEmpty() && config.packetLoggerPayloadFilters != null) {
            migrateExactFilters(merged, config.packetLoggerPayloadFilters);
        }

        rules.addAll(merged.values());
    }

    public List<AutismConfig.PayloadChannelRegistrationRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    List<AutismConfig.PayloadChannelRegistrationRule> mutableRules() {
        return rules;
    }

    public List<String> enabledChannels() {
        List<String> channels = new ArrayList<>();
        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled && isRegisterableChannel(rule.channel)) {
                channels.add(normalizeChannel(rule.channel));
            }
        }
        return channels;
    }

    public boolean hasEnabled(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank()) return false;
        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled && normalized.equals(normalizeChannel(rule.channel))) return true;
        }
        return false;
    }

    public int indexOf(String channel) {
        String normalized = normalizeChannel(channel);
        for (int i = 0; i < rules.size(); i++) {
            AutismConfig.PayloadChannelRegistrationRule rule = rules.get(i);
            if (rule != null && normalized.equals(normalizeChannel(rule.channel))) return i;
        }
        return -1;
    }

    public boolean addOrEnable(String label, String channel, String source) {
        boolean changed = addOrEnableInMemory(label, channel, source);
        if (changed) save();
        return changed;
    }

    public boolean addOrEnableInMemory(String label, String channel, String source) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        int index = indexOf(normalized);
        if (index >= 0) {
            AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
            String cleanLabel = cleanLabel(label, normalized);
            if ((rule.label == null || rule.label.isBlank() || rule.label.equals(rule.channel)) && !cleanLabel.equals(normalized)) {
                rule.label = cleanLabel;
                changed = true;
            }
            if (rule.source == null || rule.source.isBlank() || SOURCE_LEARNED.equals(rule.source)) {
                rule.source = cleanSource(source);
                changed = true;
            }
            return changed;
        }

        AutismConfig.PayloadChannelRegistrationRule rule = new AutismConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = true;
        rule.source = cleanSource(source);
        rules.add(rule);
        return true;
    }

    public boolean addLearnedSuggestion(String label, String channel) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        if (indexOf(normalized) >= 0) return false;
        AutismConfig.PayloadChannelRegistrationRule rule = new AutismConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = false;
        rule.source = SOURCE_LEARNED;
        rules.add(rule);
        save();
        return true;
    }

    public void toggle(int index) {
        if (index < 0 || index >= rules.size()) return;
        AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
        if (rule == null) return;
        rule.enabled = !rule.enabled;
        save();
    }

    public void toggleInMemory(int index) {
        if (index < 0 || index >= rules.size()) return;
        AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
        if (rule != null) rule.enabled = !rule.enabled;
    }

    public void remove(int index) {
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
        save();
    }

    public void removeInMemory(int index) {
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
    }

    public void disableAll() {
        boolean changed = false;
        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled) {
                rule.enabled = false;
                changed = true;
            }
        }
        if (changed) save();
    }

    public void disableAllInMemory() {
        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null) rule.enabled = false;
        }
    }

    public void replaceWithApplied(Collection<String> channels) {
        Set<String> applied = new LinkedHashSet<>();
        if (channels != null) {
            for (String channel : channels) {
                String normalized = normalizeChannel(channel);
                if (isRegisterableChannel(normalized)) applied.add(normalized);
            }
        }

        boolean changed = false;
        for (String channel : applied) {
            int index = indexOf(channel);
            if (index >= 0) {
                AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
            } else {
                AutismConfig.PayloadChannelRegistrationRule rule = new AutismConfig.PayloadChannelRegistrationRule();
                rule.channel = channel;
                rule.label = channel;
                rule.enabled = true;
                rule.source = SOURCE_CUSTOM;
                rules.add(rule);
                changed = true;
            }
        }
        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            boolean shouldEnable = applied.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
                rule.enabled = shouldEnable;
                changed = true;
            }
        }
        if (changed) save();
    }

    public void applyRecommendedOnly() {
        boolean changed = false;
        Set<String> defaults = new LinkedHashSet<>();
        for (AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
            if (!AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) defaults.add(pattern);
        }

        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            boolean shouldEnable = defaults.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
                rule.enabled = shouldEnable;
                changed = true;
            }
        }
        for (AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
            if (!AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            if (AutismPayloadChannelListeners.isRegisterablePreset(preset) && addOrEnableNoSave(preset.label(), pattern, SOURCE_PRESET)) {
                changed = true;
            }
        }
        if (changed) save();
    }

    public void applyRecommendedOnlyInMemory() {
        Set<String> defaults = new LinkedHashSet<>();
        for (AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
            if (!AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) defaults.add(pattern);
        }

        for (AutismConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            rule.enabled = defaults.contains(normalizeChannel(rule.channel));
        }
        for (AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
            if (!AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) addOrEnableNoSave(preset.label(), pattern, SOURCE_PRESET);
        }
    }

    public static boolean isRegisterableChannel(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank() || normalized.indexOf('*') >= 0 || normalized.indexOf(':') <= 0) return false;
        if ("minecraft:register".equals(normalized) || "minecraft:unregister".equals(normalized)
            || "minecraft:brand".equals(normalized)) return false;
        return Identifier.tryParse(normalized) != null;
    }

    public static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    public static String sourceLabel(String source) {
        return switch (cleanSource(source)) {
            case SOURCE_PRESET -> "Preset";
            case SOURCE_LEARNED -> "Learned";
            default -> "Custom";
        };
    }

    private void migrateExactFilters(Map<String, AutismConfig.PayloadChannelRegistrationRule> merged,
                                     List<AutismConfig.PayloadChannelFilterRule> filterRules) {
        for (AutismConfig.PayloadChannelFilterRule filter : filterRules) {
            if (filter == null || !filter.enabled) continue;
            String pattern = AutismPayloadChannelListeners.normalizePattern(filter.pattern);
            if (!isRegisterableChannel(pattern)) continue;
            AutismConfig.PayloadChannelRegistrationRule rule = new AutismConfig.PayloadChannelRegistrationRule();
            rule.channel = pattern;
            rule.label = cleanLabel(filter.label, pattern);
            rule.enabled = true;
            rule.source = filter.preset ? SOURCE_PRESET : SOURCE_CUSTOM;
            mergeInto(merged, rule);
        }
    }

    private boolean addOrEnableNoSave(String label, String channel, String source) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        int index = indexOf(normalized);
        if (index >= 0) {
            AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
            if (SOURCE_LEARNED.equals(rule.source)) {
                rule.source = cleanSource(source);
                changed = true;
            }
            return changed;
        }
        AutismConfig.PayloadChannelRegistrationRule rule = new AutismConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = true;
        rule.source = cleanSource(source);
        rules.add(rule);
        return true;
    }

    private static void mergeInto(Map<String, AutismConfig.PayloadChannelRegistrationRule> merged,
                                  AutismConfig.PayloadChannelRegistrationRule clean) {
        String key = normalizeChannel(clean.channel);
        AutismConfig.PayloadChannelRegistrationRule existing = merged.get(key);
        if (existing == null) {
            merged.put(key, clean);
            return;
        }
        existing.enabled |= clean.enabled;
        if (SOURCE_LEARNED.equals(existing.source) && !SOURCE_LEARNED.equals(clean.source)) existing.source = clean.source;
        if ((existing.label == null || existing.label.isBlank() || existing.label.equals(existing.channel))
            && clean.label != null && !clean.label.isBlank()) {
            existing.label = clean.label;
        }
    }

    private static AutismConfig.PayloadChannelRegistrationRule clean(AutismConfig.PayloadChannelRegistrationRule rule) {
        if (rule == null) return null;
        String channel = normalizeChannel(rule.channel);
        if (!isRegisterableChannel(channel)) return null;
        String label = rule.label == null ? "" : rule.label.trim();
        if (isPublicProbeChannel(channel) || isPublicProbeChannel(label)) return null;
        AutismConfig.PayloadChannelRegistrationRule clean = new AutismConfig.PayloadChannelRegistrationRule();
        clean.channel = channel;
        clean.label = cleanLabel(label, channel);
        clean.enabled = rule.enabled;
        clean.source = cleanSource(rule.source);
        return clean;
    }

    private static String cleanLabel(String label, String fallback) {
        String value = label == null ? "" : label.trim();
        return value.isBlank() ? fallback : value;
    }

    private static String cleanSource(String source) {
        String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case SOURCE_PRESET, SOURCE_LEARNED -> value;
            default -> SOURCE_CUSTOM;
        };
    }

    private static boolean isPublicProbeChannel(String value) {
        String normalized = normalizeChannel(value);
        if (normalized.isBlank()) return false;
        return normalized.contains("autismtest")
            || normalized.contains("payloadprobe")
            || normalized.contains("payload_probe")
            || normalized.contains("brandlike")
            || normalized.contains("mc_string")
            || normalized.contains("java_utf");
    }

    private void save() {
        commit(true);
    }

    public void commit(boolean writeFile) {
        AutismConfig config = AutismConfig.getGlobal();
        config.packetLoggerPayloadRegistrations = new ArrayList<>(rules);
        if (writeFile) config.save();
        autismclient.modules.AutismModule.get().invalidatePayloadListenerCache(false);
    }
}
