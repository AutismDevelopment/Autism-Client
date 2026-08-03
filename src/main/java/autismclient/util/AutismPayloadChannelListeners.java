package autismclient.util;

import autismclient.AutismClientAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismPayloadChannelListeners {
    public enum Direction {
        ANY, C2S, S2C;

        public static Direction from(String value) {
            if (value == null) return ANY;
            try {
                return Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ANY;
            }
        }

        public boolean accepts(String packetDirection) {
            return true;
        }
    }

    public enum PresetKind {
        EXACT("Exact"),
        CORE("Core"),
        TEMPLATE("Template"),
        UNVERIFIED("Unver");

        private final String badge;

        PresetKind(String badge) {
            this.badge = badge;
        }

        public String badge() {
            return badge;
        }

        public boolean templateLike() {
            return this == TEMPLATE || this == UNVERIFIED;
        }
    }

    public record Preset(String label, String pattern, Direction direction, String group,
                         PresetKind kind, String evidence, String templatePrefix) {
        public Preset(String label, String pattern, Direction direction, String group) {
            this(label, pattern, direction, group, inferKind(pattern), "", deriveTemplatePrefix(pattern));
        }

        public String key() {
            return ruleKey(pattern);
        }
    }

    public record Match(String label, String pattern, Direction direction) {
        public String searchText() {
            return (label + " " + pattern).toLowerCase(Locale.ROOT);
        }
    }

    private static final List<Preset> PRESETS = createPresets();
    private static final List<String> CATALOG_WARNINGS = validateCatalog(PRESETS);
    static {
        for (String warning : CATALOG_WARNINGS) {
            AutismClientAddon.LOG.warn("[PayloadFilters] Preset catalog warning: {}", warning);
        }
    }
    private final List<AutismConfig.PayloadChannelFilterRule> rules = new ArrayList<>();

    public AutismPayloadChannelListeners() {
        load();
    }

    public void load() {
        rules.clear();
        AutismConfig config = AutismConfig.getGlobal();
        if (config.packetLoggerPayloadFilters == null) {
            config.packetLoggerPayloadFilters = new ArrayList<>();
        }
        Map<String, AutismConfig.PayloadChannelFilterRule> merged = new LinkedHashMap<>();
        for (AutismConfig.PayloadChannelFilterRule rule : config.packetLoggerPayloadFilters) {
            AutismConfig.PayloadChannelFilterRule clean = cleanRule(rule);
            if (clean == null) continue;
            String key = ruleKey(clean.pattern);
            AutismConfig.PayloadChannelFilterRule existing = merged.get(key);
            if (existing == null) {
                merged.put(key, clean);
            } else {
                existing.enabled |= clean.enabled;
                existing.preset |= clean.preset;
                if ((existing.label == null || existing.label.isBlank() || existing.label.equals(existing.pattern))
                    && clean.label != null && !clean.label.isBlank()) {
                    existing.label = clean.label;
                }
            }
        }
        rules.addAll(merged.values());
    }

    public List<AutismConfig.PayloadChannelFilterRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    List<AutismConfig.PayloadChannelFilterRule> mutableRules() {
        return rules;
    }

    public List<Preset> presets() {
        return PRESETS;
    }

    public static List<Preset> presetCatalog() {
        return PRESETS;
    }

    public static List<String> catalogWarnings() {
        return CATALOG_WARNINGS;
    }

    public static boolean presetMatchesChannel(Preset preset, String channelName) {
        if (preset == null) return false;
        return matchesPattern(preset.pattern(), normalizeChannel(channelName));
    }

    public static boolean patternMatchesChannel(String pattern, String channelName) {
        return matchesPattern(pattern, normalizeChannel(channelName));
    }

    public static boolean presetCanIdentifyPlugin(Preset preset) {
        if (preset == null) return false;
        if (preset.kind() != PresetKind.EXACT) return false;
        String label = preset.label() == null ? "" : preset.label().trim().toLowerCase(Locale.ROOT);
        String pattern = normalizePattern(preset.pattern());
        if (pattern.isBlank() || "Minecraft".equalsIgnoreCase(preset.group())) return false;
        if (label.contains("family") || label.contains("namespace")) return false;
        int colon = pattern.indexOf(':');
        if (colon <= 0) return false;
        String namespace = pattern.substring(0, colon);
        if (isGenericDiscoveryNamespace(namespace)) return false;
        return true;
    }

    private static boolean isGenericDiscoveryNamespace(String namespace) {
        return switch (namespace == null ? "" : namespace.toLowerCase(Locale.ROOT)) {
            case "ah", "auction", "auctions", "auctionhouse", "shop", "shops", "store", "stores",
                 "crate", "crates", "casino", "slots", "roulette", "gui", "menu", "menus",
                 "inventory", "inventories", "chest", "chests", "storage", "container", "containers",
                 "backpack", "backpacks", "shulker", "shulkers", "enderchest", "echest",
                 "pv", "vault", "vaults", "minion", "minions", "mine", "mines",
                 "claim", "claims", "region", "regions", "protection", "protections",
                 "c" -> true;
            default -> false;
        };
    }

    public boolean addCustom(String label, String pattern, Direction direction) {
        return addRule(label, pattern, direction, false, true);
    }

    public boolean addCustomInMemory(String label, String pattern, Direction direction) {
        return addRuleInMemory(label, pattern, direction, false, true);
    }

    public boolean addPreset(Preset preset) {
        if (preset == null) return false;
        return addRule(preset.label(), preset.pattern(), preset.direction(), true, true);
    }

    public boolean addOrEnablePreset(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (ruleKey(rule.pattern).equals(key)) {
                if (!rule.enabled) {
                    rule.enabled = true;
                    save();
                    return true;
                }
                return false;
            }
        }
        return addPreset(preset);
    }

    public boolean addOrEnablePresetInMemory(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (ruleKey(rule.pattern).equals(key)) {
                boolean changed = false;
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
                if (!rule.preset) {
                    rule.preset = true;
                    changed = true;
                }
                return changed;
            }
        }
        return addRuleInMemory(preset.label(), preset.pattern(), preset.direction(), true, true);
    }

    public void toggle(int index) {
        if (index < 0 || index >= rules.size()) return;
        AutismConfig.PayloadChannelFilterRule rule = rules.get(index);
        rule.enabled = !rule.enabled;
        save();
    }

    public void toggleInMemory(int index) {
        if (index < 0 || index >= rules.size()) return;
        AutismConfig.PayloadChannelFilterRule rule = rules.get(index);
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

    public void resetPresetRules() {
        boolean changed = false;
        for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelFilterRule rule = it.next();
            if (rule != null && rule.preset) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    public boolean hasRuleFor(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (ruleKey(rule.pattern).equals(key)) return true;
        }
        return false;
    }

    public boolean isRuleEnabledFor(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (ruleKey(rule.pattern).equals(key)) return rule.enabled;
        }
        return false;
    }

    public boolean hasEnabledRules() {
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (rule != null && rule.enabled) return true;
        }
        return false;
    }

    public List<String> registrationChannels() {
        return registrationChannels(false);
    }

    public List<String> registrationChannels(boolean includeLoggerDefaults) {
        return AutismPayloadChannelSubscriptionManager.buildPlan(includeLoggerDefaults).channels();
    }

    public int presetCountForGroup(String group) {
        if (group == null || group.isBlank()) return 0;
        int count = 0;
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group())) count++;
        }
        return count;
    }

    public int enabledPresetCountForGroup(String group) {
        if (group == null || group.isBlank()) return 0;
        int count = 0;
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group()) && isRuleEnabledFor(preset)) count++;
        }
        return count;
    }

    public boolean isGroupFullyEnabled(String group) {
        int total = presetCountForGroup(group);
        return total > 0 && enabledPresetCountForGroup(group) == total;
    }

    public void toggleGroup(String group) {
        if (group == null || group.isBlank()) return;
        setGroupEnabled(group, !isGroupFullyEnabled(group));
    }

    private void setGroupEnabled(String group, boolean enabled) {
        if (group == null || group.isBlank()) return;
        boolean changed = false;
        Set<String> groupKeys = new HashSet<>();
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group())) groupKeys.add(preset.key());
        }
        if (groupKeys.isEmpty()) return;

        if (!enabled) {
            for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
                AutismConfig.PayloadChannelFilterRule rule = it.next();
                if (rule != null && rule.preset && groupKeys.contains(ruleKey(rule.pattern))) {
                    it.remove();
                    changed = true;
                }
            }
        } else {
            for (Preset preset : PRESETS) {
                if (!group.equals(preset.group())) continue;
                if (enablePresetWithoutSaving(preset)) changed = true;
            }
        }
        if (changed) save();
    }

    public void toggleOrAddPreset(Preset preset) {
        if (preset == null) return;
        String key = preset.key();
        for (int i = 0; i < rules.size(); i++) {
            AutismConfig.PayloadChannelFilterRule rule = rules.get(i);
            if (ruleKey(rule.pattern).equals(key)) {
                if (rule.enabled) {
                    rules.remove(i);
                    save();
                } else {
                    rule.enabled = true;
                    rule.preset = true;
                    save();
                }
                return;
            }
        }
        addPreset(preset);
    }

    public void toggleOrAddPresetInMemory(Preset preset) {
        if (preset == null) return;
        String key = preset.key();
        for (int i = 0; i < rules.size(); i++) {
            AutismConfig.PayloadChannelFilterRule rule = rules.get(i);
            if (ruleKey(rule.pattern).equals(key)) {
                if (rule.enabled) {
                    rules.remove(i);
                } else {
                    rule.enabled = true;
                    rule.preset = true;
                }
                return;
            }
        }
        addRuleInMemory(preset.label(), preset.pattern(), preset.direction(), true, true);
    }

    private boolean enablePresetWithoutSaving(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (ruleKey(rule.pattern).equals(key)) {
                boolean changed = false;
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
                if (!rule.preset) {
                    rule.preset = true;
                    changed = true;
                }
                return changed;
            }
        }
        AutismConfig.PayloadChannelFilterRule rule = new AutismConfig.PayloadChannelFilterRule();
        rule.pattern = normalizePattern(preset.pattern());
        rule.label = preset.label();
        rule.enabled = true;
        rule.preset = true;
        rules.add(rule);
        return true;
    }

    public void enableAll() {
        boolean changed = false;
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (rule != null && !rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
        }
        for (Preset preset : PRESETS) {
            if (enablePresetWithoutSaving(preset)) changed = true;
        }
        if (changed) save();
    }

    public void disableAll() {
        boolean changed = false;
        for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelFilterRule rule = it.next();
            if (rule == null) {
                it.remove();
                changed = true;
            } else if (rule.preset) {
                it.remove();
                changed = true;
            } else if (rule.enabled) {
                rule.enabled = false;
                changed = true;
            }
        }
        if (changed) save();
    }

    public boolean isDefaultRecommendedFullyEnabled() {
        int total = 0;
        int enabled = 0;
        for (Preset preset : PRESETS) {
            if (!isDefaultRecommendedPreset(preset)) continue;
            total++;
            if (isRuleEnabledFor(preset)) enabled++;
        }
        return total > 0 && enabled == total;
    }

    public boolean isDefaultRecommendedProfileActive() {
        Set<String> defaultKeys = defaultRecommendedKeys();
        if (defaultKeys.isEmpty()) return false;
        for (String key : defaultKeys) {
            boolean keyEnabled = false;
            for (AutismConfig.PayloadChannelFilterRule rule : rules) {
                if (rule != null && rule.enabled && key.equals(ruleKey(rule.pattern))) {
                    keyEnabled = true;
                    break;
                }
            }
            if (!keyEnabled) return false;
        }
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (rule != null && rule.enabled && !defaultKeys.contains(ruleKey(rule.pattern))) {
                return false;
            }
        }
        return true;
    }

    public void applyDefaultRecommendedOnly() {
        Map<String, Preset> defaults = defaultRecommendedPresetMap();
        if (defaults.isEmpty()) return;
        boolean changed = false;
        for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelFilterRule rule = it.next();
            if (rule == null) {
                it.remove();
                changed = true;
                continue;
            }
            String key = ruleKey(rule.pattern);
            Preset defaultPreset = defaults.get(key);
            if (defaultPreset != null) {
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
                if (!rule.preset) {
                    rule.preset = true;
                    changed = true;
                }
                String label = defaultPreset.label();
                if (label != null && !label.isBlank()
                    && (rule.label == null || rule.label.isBlank() || rule.label.equals(rule.pattern))) {
                    rule.label = label;
                    changed = true;
                }
            } else if (rule.preset) {
                it.remove();
                changed = true;
            } else if (rule.enabled) {
                rule.enabled = false;
                changed = true;
            }
        }
        for (Preset preset : defaults.values()) {
            if (enablePresetWithoutSaving(preset)) changed = true;
        }
        if (changed) save();
    }

    public void applyDefaultRecommendedOnlyInMemory() {
        Map<String, Preset> defaults = defaultRecommendedPresetMap();
        if (defaults.isEmpty()) return;
        for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelFilterRule rule = it.next();
            if (rule == null) {
                it.remove();
                continue;
            }
            String key = ruleKey(rule.pattern);
            Preset defaultPreset = defaults.get(key);
            if (defaultPreset != null) {
                rule.enabled = true;
                rule.preset = true;
                String label = defaultPreset.label();
                if (label != null && !label.isBlank()
                    && (rule.label == null || rule.label.isBlank() || rule.label.equals(rule.pattern))) {
                    rule.label = label;
                }
            } else if (rule.preset) {
                it.remove();
            } else {
                rule.enabled = false;
            }
        }
        for (Preset preset : defaults.values()) {
            enablePresetWithoutSaving(preset);
        }
    }

    public void disableAllInMemory() {
        for (Iterator<AutismConfig.PayloadChannelFilterRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelFilterRule rule = it.next();
            if (rule == null || rule.preset) {
                it.remove();
            } else {
                rule.enabled = false;
            }
        }
    }

    public void enableDefaultRecommended() {
        boolean changed = false;
        for (Preset preset : PRESETS) {
            if (isDefaultRecommendedPreset(preset) && enablePresetWithoutSaving(preset)) {
                changed = true;
            }
        }
        if (changed) save();
    }

    private static Set<String> defaultRecommendedKeys() {
        return new HashSet<>(defaultRecommendedPresetMap().keySet());
    }

    private static Map<String, Preset> defaultRecommendedPresetMap() {
        Map<String, Preset> defaults = new LinkedHashMap<>();
        for (Preset preset : PRESETS) {
            if (isDefaultRecommendedPreset(preset)) defaults.put(preset.key(), preset);
        }
        return defaults;
    }

    public Match match(AutismPayloadSupport.PayloadSnapshot snapshot, String packetDirection) {
        if (snapshot == null || snapshot.channel() == null || snapshot.channel().isBlank()) return null;
        return matchChannel(snapshot.channel(), packetDirection);
    }

    public Match matchChannel(String channelName, String packetDirection) {
        String channel = normalizeChannel(channelName);
        if (channel.isBlank()) return null;
        for (AutismConfig.PayloadChannelFilterRule rule : rules) {
            if (rule == null || !rule.enabled) continue;
            if (matchesPattern(rule.pattern, channel)) {
                String label = rule.label == null || rule.label.isBlank() ? rule.pattern : rule.label;
                return new Match(label, rule.pattern, Direction.ANY);
            }
        }
        return null;
    }

    public String listenerSearchText(AutismPayloadSupport.PayloadSnapshot snapshot, String packetDirection) {
        Match match = match(snapshot, packetDirection);
        return match == null ? "" : match.searchText();
    }

    public static boolean isDefaultRecommendedPresetPublic(Preset preset) {
        if (preset == null) return false;
        String pattern = normalizePattern(preset.pattern());
        return switch (pattern) {
            case "minecraft:brand", "minecraft:register", "minecraft:unregister",
                 "register", "unregister", "bungeecord", "bungeecord:main" -> true;
            default -> false;
        };
    }

    public static boolean isRegisterablePreset(Preset preset) {
        if (preset == null || preset.kind() != PresetKind.EXACT) return false;
        return AutismPayloadChannelRegistrations.isRegisterableChannel(preset.pattern());
    }

    public static String templatePrefix(Preset preset) {
        if (preset == null) return "";
        String prefix = preset.templatePrefix();
        if (prefix != null && !prefix.isBlank()) return prefix;
        return deriveTemplatePrefix(preset.pattern());
    }

    private static boolean isDefaultRecommendedPreset(Preset preset) {
        return isDefaultRecommendedPresetPublic(preset);
    }

    @SuppressWarnings("deprecation")
    public void save() {
        commit(true);
    }

    public void commit(boolean writeFile) {
        AutismConfig config = AutismConfig.getGlobal();
        config.packetLoggerPayloadFilters = new ArrayList<>(rules);
        config.packetLoggerPayloadListeners = new ArrayList<>();
        if (writeFile) config.save();
        autismclient.modules.AutismModule.get().invalidatePayloadListenerCache();
    }

    public static String normalizePattern(String pattern) {
        if (pattern == null) return "";
        return normalizeChannel(pattern.trim());
    }

    private static PresetKind inferKind(String pattern) {
        String normalized = normalizePattern(pattern);
        if (isCorePresetPattern(normalized)) return PresetKind.CORE;
        if (normalized.indexOf('*') >= 0) return PresetKind.TEMPLATE;
        if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) return PresetKind.UNVERIFIED;
        return PresetKind.TEMPLATE;
    }

    private static String deriveTemplatePrefix(String pattern) {
        String normalized = normalizePattern(pattern);
        int star = normalized.indexOf('*');
        if (star < 0) return normalized;
        return normalized.substring(0, star);
    }

    private static boolean isCorePresetPattern(String normalized) {
        return switch (normalizePattern(normalized)) {
            case "minecraft:brand", "minecraft:register", "minecraft:unregister",
                 "register", "unregister", "bungeecord" -> true;
            default -> false;
        };
    }

    private static AutismConfig.PayloadChannelFilterRule cleanRule(AutismConfig.PayloadChannelFilterRule rule) {
        if (rule == null) return null;
        String pattern = normalizePattern(rule.pattern);
        if (pattern.isBlank()) return null;
        String label = rule.label == null ? "" : rule.label.trim();
        if (isPublicProbePattern(pattern) || isPublicProbePattern(label)) return null;
        AutismConfig.PayloadChannelFilterRule clean = new AutismConfig.PayloadChannelFilterRule();
        clean.pattern = pattern;
        clean.label = label.isBlank() ? pattern : label;
        clean.enabled = rule.enabled;
        clean.preset = rule.preset;
        return clean;
    }

    private boolean addRule(String label, String pattern, Direction direction, boolean preset, boolean enabled) {
        boolean changed = addRuleInMemory(label, pattern, direction, preset, enabled);
        if (changed) save();
        return changed;
    }

    private boolean addRuleInMemory(String label, String pattern, Direction direction, boolean preset, boolean enabled) {
        String normalized = normalizePattern(pattern);
        if (normalized.isBlank()) return false;
        Direction dir = Direction.ANY;
        String key = ruleKey(normalized);
        for (AutismConfig.PayloadChannelFilterRule existing : rules) {
            if (ruleKey(existing.pattern).equals(key)) {
                boolean changed = false;
                if (!existing.enabled && enabled) {
                    existing.enabled = true;
                    changed = true;
                }
                if (existing.label == null || existing.label.isBlank() || existing.label.equals(existing.pattern)) {
                    existing.label = label == null || label.isBlank() ? normalized : label.trim();
                    changed = true;
                }
                return changed;
            }
        }
        AutismConfig.PayloadChannelFilterRule rule = new AutismConfig.PayloadChannelFilterRule();
        rule.pattern = normalized;
        rule.label = label == null || label.isBlank() ? normalized : label.trim();
        rule.enabled = enabled;
        rule.preset = preset;
        rules.add(rule);
        return true;
    }

    private static boolean matchesPattern(String pattern, String normalizedChannel) {
        String normalizedPattern = normalizePattern(pattern);
        if (normalizedPattern.isEmpty() || normalizedChannel.isEmpty()) return false;
        if ("*".equals(normalizedPattern)) return true;
        if (normalizedPattern.indexOf('*') < 0) return normalizedChannel.equals(normalizedPattern);
        return wildcardMatches(normalizedPattern, normalizedChannel);
    }

    private static boolean wildcardMatches(String pattern, String text) {
        int p = 0;
        int t = 0;
        int star = -1;
        int mark = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                mark = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    private static String ruleKey(String pattern) {
        return normalizePattern(pattern);
    }

    private static boolean isPublicProbePattern(String value) {
        String normalized = normalizePattern(value);
        if (normalized.isBlank()) return false;
        return normalized.contains("autismtest")
            || normalized.contains("payloadprobe")
            || normalized.contains("payload_probe")
            || normalized.contains("brandlike")
            || normalized.contains("mc_string")
            || normalized.contains("java_utf");
    }

    private static List<String> validateCatalog(List<Preset> presets) {
        if (presets == null || presets.isEmpty()) return List.of("catalog is empty");
        List<String> warnings = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Map<String, String> groupCase = new LinkedHashMap<>();
        for (Preset preset : presets) {
            if (preset == null) {
                warnings.add("null preset row");
                continue;
            }
            String key = preset.key();
            if (!keys.add(key)) warnings.add("duplicate pattern " + preset.pattern());
            String group = preset.group() == null ? "" : preset.group().trim();
            if (group.isBlank()) warnings.add("missing group for " + preset.pattern());
            String folded = group.toLowerCase(Locale.ROOT);
            String existing = groupCase.putIfAbsent(folded, group);
            if (existing != null && !existing.equals(group)) {
                warnings.add("category casing differs: " + existing + " / " + group);
            }
            String pattern = normalizePattern(preset.pattern());
            if (preset.kind() == PresetKind.EXACT) {
                if (pattern.indexOf('*') >= 0) {
                    warnings.add("exact preset uses wildcard " + preset.pattern());
                }
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                    warnings.add("exact preset is not registerable " + preset.pattern());
                }
                if (preset.evidence() == null || preset.evidence().isBlank()) {
                    warnings.add("exact preset missing evidence " + preset.pattern());
                }
            }
            if (preset.kind() == PresetKind.CORE && !isCorePresetPattern(pattern)) {
                warnings.add("core preset has non-core channel " + preset.pattern());
            }
            if (preset.kind().templateLike() && pattern.indexOf('*') < 0
                && (preset.templatePrefix() == null || preset.templatePrefix().isBlank())) {
                warnings.add("template preset missing editable prefix " + preset.pattern());
            }
        }
        return Collections.unmodifiableList(warnings);
    }

    private static List<Preset> createPresets() {
        List<Preset> presets = new ArrayList<>();
        addCore(presets, "Minecraft Brand", "minecraft:brand", "Minecraft",
            "Vanilla brand custom payload; observed/logged, not a Bukkit subscription target.");
        addCore(presets, "Minecraft Register", "minecraft:register", "Minecraft",
            "Vanilla/legacy plugin-channel registration list; observed/logged, not registered.");
        addCore(presets, "Minecraft Unregister", "minecraft:unregister", "Minecraft",
            "Vanilla/legacy plugin-channel unregister list; observed/logged, not registered.");
        addCore(presets, "Legacy REGISTER", "REGISTER", "Minecraft",
            "Legacy registration payload name observed in plugin-channel traffic.");
        addCore(presets, "Legacy UNREGISTER", "UNREGISTER", "Minecraft",
            "Legacy unregister payload name observed in plugin-channel traffic.");
        addCore(presets, "BungeeCord", "BungeeCord", "Proxy",
            "Legacy BungeeCord plugin messaging channel; watch/highlight compatibility row.");
        addExact(presets, "BungeeCord Main", "bungeecord:main", "Proxy",
            "Spigot/Paper proxy plugin messaging channel documented by BungeeCord/Velocity compatibility guides.");
        addExact(presets, "ViaVersion Server Details", "vv:server_details", "Proxy",
            "ViaVersion docs/source: ConnectionDetails.SERVER_CHANNEL sends server details JSON to clients.");
        addExact(presets, "ViaVersion Proxy Details", "vv:proxy_details", "Proxy",
            "ViaVersion docs/source: ConnectionDetails.PROXY_CHANNEL sends proxy details JSON.");
        addExact(presets, "ViaVersion Mod Details", "vv:mod_details", "Proxy",
            "ViaVersion docs/source: ConnectionDetails.MOD_CHANNEL is used for client mod details.");
        addExact(presets, "ViaVersion App Details", "vv:app_details", "Proxy",
            "ViaVersion docs/source: ConnectionDetails.APP_CHANNEL is used for standalone app details.");
        add(presets, "Velocity", "velocity:*", "Proxy");
        add(presets, "ViaVersion", "viaversion:*", "Proxy");
        add(presets, "ViaVersion Details", "vv:*", "Proxy");
        add(presets, "ViaBackwards", "viabackwards:*", "Proxy");
        add(presets, "Geyser", "geyser:*", "Proxy");
        addExact(presets, "Floodgate Skin", "floodgate:skin", "Proxy",
            "Floodgate/Geyser source: SkinChannel / PluginMessageChannels.SKIN returns floodgate:skin.");
        addExact(presets, "Floodgate Form", "floodgate:form", "Proxy",
            "Floodgate/Geyser source: FormChannel / PluginMessageChannels.FORM returns floodgate:form.");
        addExact(presets, "Floodgate Transfer", "floodgate:transfer", "Proxy",
            "Floodgate/Geyser source: TransferChannel / PluginMessageChannels.TRANSFER returns floodgate:transfer.");
        addExact(presets, "Floodgate Packet", "floodgate:packet", "Proxy",
            "Floodgate/Geyser source: PacketChannel / PluginMessageChannels.PACKET returns floodgate:packet.");
        add(presets, "Floodgate", "floodgate:*", "Proxy");
        add(presets, "Fabric", "fabric:*", "Mod API");
        addExact(presets, "Fabric Split", "fabric:split", "Mod API",
            "Fabric API source: FabricSplitPacketPayload.TYPE is Identifier.fromNamespaceAndPath(\"fabric\", \"split\").");
        addExact(presets, "Fabric Common Register", "c:register", "Mod API",
            "Fabric API source: CommonRegisterPayload.TYPE is Identifier.parse(\"c:register\").");
        addExact(presets, "Fabric Common Version", "c:version", "Mod API",
            "Fabric API source: CommonVersionPayload.TYPE is Identifier.parse(\"c:version\").");
        add(presets, "WorldEdit", "worldedit:*", "Mod API");
        addExact(presets, "WorldEdit CUI", "worldedit:cui", "Mod API",
            "WorldEdit source: WorldEditPlugin.CUI_PLUGIN_CHANNEL registered incoming/outgoing and sent to CUI clients.");
        add(presets, "WorldEdit CUI Prefix", "worldedit:cui*", "Mod API");
        add(presets, "Oraxen", "oraxen:*", "Items");
        add(presets, "ItemsAdder", "itemsadder:*", "Items");
        add(presets, "Nexo", "nexo:*", "Items");
        add(presets, "ModelEngine", "modelengine:*", "Items");
        add(presets, "MythicCrucible", "mythiccrucible:*", "Items");
        add(presets, "MMOItems", "mmoitems:*", "Items");
        add(presets, "ExecutableItems", "executableitems:*", "Items");
        add(presets, "ExecutableBlocks", "executableblocks:*", "Items");
        add(presets, "Nova", "nova:*", "Items");
        add(presets, "EcoItems", "ecoitems:*", "Items");
        add(presets, "EcoArmor", "ecoarmor:*", "Items");
        add(presets, "EcoEnchants", "ecoenchants:*", "Items");
        add(presets, "Vault", "vault:*", "Economy");
        add(presets, "Treasury", "treasury:*", "Economy");
        add(presets, "EssentialsX", "essentials:*", "Economy");
        add(presets, "EssentialsX Alt", "essentialsx:*", "Economy");
        add(presets, "CMI", "cmi:*", "Economy");
        add(presets, "TheNewEconomy", "theneweconomy:*", "Economy");
        add(presets, "TNE", "tne:*", "Economy");
        add(presets, "PlayerPoints", "playerpoints:*", "Economy");
        add(presets, "VotingPlugin Points", "votingplugin:*", "Economy");
        add(presets, "CoinsEngine", "coinsengine:*", "Economy");
        add(presets, "UltraEconomy", "ultraeconomy:*", "Economy");
        add(presets, "XConomy", "xconomy:*", "Economy");
        add(presets, "PEconomy", "peconomy:*", "Economy");
        add(presets, "CMIEInjector", "cmieinjector:*", "Economy");
        add(presets, "GemsEconomy", "gemseconomy:*", "Economy");
        add(presets, "TokenManager", "tokenmanager:*", "Economy");
        add(presets, "RedisEconomy", "rediseconomy:*", "Economy");
        add(presets, "JobsReborn", "jobs:*", "Economy");
        add(presets, "JobsReborn Alt", "jobsreborn:*", "Economy");
        add(presets, "Aurelium", "aurelium:*", "Economy");
        add(presets, "AureliumSkills", "aureliumskills:*", "Economy");
        add(presets, "AuctionHouse", "auctionhouse:*", "Auctions");
        add(presets, "AuctionMaster", "auctionmaster:*", "Auctions");
        add(presets, "CrazyAuctions", "crazyauctions:*", "Auctions");
        add(presets, "CrazyAuctions Plus", "crazyauctionsplus:*", "Auctions");
        add(presets, "ExcellentAuctionHouse", "excellentauctionhouse:*", "Auctions");
        add(presets, "ExcellentAuctions", "excellentauctions:*", "Auctions");
        add(presets, "zAuctionHouse", "zauctionhouse:*", "Auctions");
        add(presets, "AuctionGUIPlus", "auctionguiplus:*", "Auctions");
        add(presets, "PlayerAuctions", "playerauctions:*", "Auctions");
        add(presets, "BetterAuction", "betterauction:*", "Auctions");
        add(presets, "EzAuction", "ezauction:*", "Auctions");
        add(presets, "NexusAuctionHouse", "nexusauctionhouse:*", "Auctions");
        add(presets, "AxAuctions", "axauctions:*", "Auctions");
        add(presets, "AdvancedAuctions", "advancedauctions:*", "Auctions");
        add(presets, "UltimateAuction", "ultimateauction:*", "Auctions");
        add(presets, "Auction", "auction:*", "Auctions");
        add(presets, "ShopGUIPlus", "shopguiplus:*", "Shops");
        add(presets, "ShopGUI+", "shopgui:*", "Shops");
        add(presets, "EconomyShopGUI", "economyshopgui:*", "Shops");
        add(presets, "EconomyShopGUI Premium", "economyshopguipremium:*", "Shops");
        add(presets, "BossShopPro", "bossshoppro:*", "Shops");
        add(presets, "BossShop", "bossshop:*", "Shops");
        add(presets, "ChestShop", "chestshop:*", "Shops");
        addExact(presets, "QuickShop Bungee", "quickshop:bungee", "Shops",
            "QuickShop-Hikari source: CHAT_FORWARD_CHANNEL / QUICKSHOP_BUNGEE_CHANNEL registered and sent.");
        add(presets, "QuickShop", "quickshop:*", "Shops");
        add(presets, "QuickShop Reremake", "quickshop-hikari:*", "Shops");
        add(presets, "QuickShop Reremake Alt", "quickshopreremake:*", "Shops");
        add(presets, "GUIShop", "guishop:*", "Shops");
        add(presets, "UltimateShop", "ultimateshop:*", "Shops");
        add(presets, "zShop", "zshop:*", "Shops");
        add(presets, "BetterShop", "bettershop:*", "Shops");
        add(presets, "Shopkeepers", "shopkeepers:*", "Shops");
        add(presets, "dtlTraders", "dtltraders:*", "Shops");
        add(presets, "PlayerShops", "playershops:*", "Shops");
        add(presets, "PlayerWarps", "playerwarps:*", "Shops");
        add(presets, "DeluxeMenus", "deluxemenus:*", "Shops");
        add(presets, "CommandPanels", "commandpanels:*", "Shops");
        add(presets, "TradeSystem", "tradesystem:*", "Shops");
        add(presets, "TradePlus", "tradeplus:*", "Shops");
        add(presets, "Trades", "trades:*", "Shops");
        add(presets, "ExcellentShop", "excellentshop:*", "Shops");
        add(presets, "SimpleShop", "simpleshop:*", "Shops");
        add(presets, "SimpleShops", "simpleshops:*", "Shops");
        add(presets, "SimpleShopGUI", "simpleshopgui:*", "Shops");
        add(presets, "SimpleShopX", "simpleshopx:*", "Shops");
        add(presets, "ShopX", "shopx:*", "Shops");
        add(presets, "Shop X", "shop_x:*", "Shops");
        add(presets, "ShopPlus", "shopplus:*", "Shops");
        add(presets, "ShopsPlus", "shopsplus:*", "Shops");
        add(presets, "ShopPro", "shoppro:*", "Shops");
        add(presets, "ShopsPro", "shopspro:*", "Shops");
        add(presets, "BetterShops", "bettershops:*", "Shops");
        add(presets, "BetterShopsPro", "bettershopspro:*", "Shops");
        add(presets, "UltimateShops", "ultimateshops:*", "Shops");
        add(presets, "UltraShop", "ultrashop:*", "Shops");
        add(presets, "UltraShops", "ultrashops:*", "Shops");
        add(presets, "PremiumShop", "premiumshop:*", "Shops");
        add(presets, "PremiumShops", "premiumshops:*", "Shops");
        add(presets, "DeluxeShop", "deluxeshop:*", "Shops");
        add(presets, "DeluxeShops", "deluxeshops:*", "Shops");
        add(presets, "DeluxeShopGUI", "deluxeshopgui:*", "Shops");
        add(presets, "ShopSystem", "shopsystem:*", "Shops");
        add(presets, "ShopsSystem", "shopssystem:*", "Shops");
        add(presets, "ShopCore", "shopcore:*", "Shops");
        add(presets, "ShopsCore", "shopscore:*", "Shops");
        add(presets, "ShopEngine", "shopengine:*", "Shops");
        add(presets, "ShopAPI", "shopapi:*", "Shops");
        add(presets, "ShopsAPI", "shopsapi:*", "Shops");
        add(presets, "ShopMenu", "shopmenu:*", "Shops");
        add(presets, "ShopMenus", "shopmenus:*", "Shops");
        add(presets, "ShopGUIs", "shopguis:*", "Shops");
        add(presets, "GUIShopPlus", "guishopplus:*", "Shops");
        add(presets, "GUIShopPro", "guishoppro:*", "Shops");
        add(presets, "EconomyShopGUIPlus", "economyshopguiplus:*", "Shops");
        add(presets, "ESGUI", "esgui:*", "Shops");
        add(presets, "BossShopGUI", "bossshopgui:*", "Shops");
        add(presets, "CommandShop", "commandshop:*", "Shops");
        add(presets, "CommandShops", "commandshops:*", "Shops");
        add(presets, "PlayerShop", "playershop:*", "Shops");
        add(presets, "PlayerShopGUI", "playershopgui:*", "Shops");
        add(presets, "GlobalShop", "globalshop:*", "Shops");
        add(presets, "GlobalShops", "globalshops:*", "Shops");
        add(presets, "CommunityShop", "communityshop:*", "Shops");
        add(presets, "CommunityShops", "communityshops:*", "Shops");
        add(presets, "ServerShop", "servershop:*", "Shops");
        add(presets, "ServerShops", "servershops:*", "Shops");
        add(presets, "VirtualShop", "virtualshop:*", "Shops");
        add(presets, "VirtualShops", "virtualshops:*", "Shops");
        add(presets, "ItemShop", "itemshop:*", "Shops");
        add(presets, "ItemShops", "itemshops:*", "Shops");
        add(presets, "DailyShop", "dailyshop:*", "Shops");
        add(presets, "DailyShops", "dailyshops:*", "Shops");
        add(presets, "RotatingShop", "rotatingshop:*", "Shops");
        add(presets, "RotatingShops", "rotatingshops:*", "Shops");
        add(presets, "VillagerShop", "villagershop:*", "Shops");
        add(presets, "VillagerShops", "villagershops:*", "Shops");
        add(presets, "NPCShop", "npcshop:*", "Shops");
        add(presets, "NPCShops", "npcshops:*", "Shops");
        add(presets, "CitizensShop", "citizensshop:*", "Shops");
        add(presets, "CitizensShops", "citizensshops:*", "Shops");
        add(presets, "HoloShop", "holoshop:*", "Shops");
        add(presets, "HoloShops", "holoshops:*", "Shops");
        add(presets, "ChestShops", "chestshops:*", "Shops");
        add(presets, "ChestShopPlus", "chestshopplus:*", "Shops");
        add(presets, "ShopChestPlus", "shopchestplus:*", "Shops");
        add(presets, "QuickShopHikari", "quickshophikari:*", "Shops");
        add(presets, "QS", "qs:*", "Shops");
        add(presets, "QShop", "qshop:*", "Shops");
        add(presets, "MMOShop", "mmoshop:*", "Shops");
        add(presets, "EpicShop", "epicshop:*", "Shops");
        add(presets, "BlackMarketPlus", "blackmarketplus:*", "Shops");
        add(presets, "BazaarPlus", "bazaarplus:*", "Shops");
        add(presets, "MarketPlus", "marketplus:*", "Shops");
        add(presets, "Markets", "markets:*", "Shops");
        add(presets, "MarketplaceGUI", "marketplacegui:*", "Shops");
        add(presets, "NightExpress", "nightexpress:*", "Frameworks");
        add(presets, "NexEngine", "nexengine:*", "Frameworks");
        add(presets, "PhoenixPlugins", "phoenixplugins:*", "Frameworks");
        add(presets, "PlaceholderAPI", "placeholderapi:*", "Frameworks");
        add(presets, "ProtocolLib", "protocollib:*", "Frameworks");
        add(presets, "PacketEvents", "packetevents:*", "Frameworks");
        add(presets, "LoneLibs", "lonelibs:*", "Frameworks");
        add(presets, "NBTAPI", "nbtapi:*", "Frameworks");

        add(presets, "DonutSMP", "donutsmp:*", "Protection");
        add(presets, "Donut", "donut:*", "Protection");
        add(presets, "Donut Claims", "donutclaims:*", "Protection");
        add(presets, "Claims", "claims:*", "Protection");
        add(presets, "Claim", "claim:*", "Protection");
        add(presets, "Regions", "regions:*", "Protection");
        add(presets, "Region", "region:*", "Protection");
        add(presets, "Protection", "protection:*", "Protection");
        add(presets, "Protections", "protections:*", "Protection");
        add(presets, "AntiGrief", "antigrief:*", "Protection");
        add(presets, "WorldGuard", "worldguard:*", "Protection");
        add(presets, "WorldGuard Regions", "worldguardregions:*", "Protection");
        add(presets, "WorldGuard ExtraFlags", "worldguardextraflags:*", "Protection");
        add(presets, "WG", "wg:*", "Protection");
        add(presets, "WG Region", "wgregion:*", "Protection");
        add(presets, "Residence", "residence:*", "Protection");
        add(presets, "Residence Plus", "residenceplus:*", "Protection");
        add(presets, "GriefPrevention", "griefprevention:*", "Protection");
        add(presets, "Grief Prevention", "grief_prevention:*", "Protection");
        add(presets, "GriefDefender", "griefdefender:*", "Protection");
        add(presets, "GPFlags", "gpflags:*", "Protection");
        add(presets, "Lands", "lands:*", "Protection");
        add(presets, "Land", "land:*", "Protection");
        add(presets, "LandClaim", "landclaim:*", "Protection");
        add(presets, "LandClaims", "landclaims:*", "Protection");
        add(presets, "UltimateClaims", "ultimateclaims:*", "Protection");
        add(presets, "UltimateLandClaim", "ultimatelandclaim:*", "Protection");
        add(presets, "ProtectionStones", "protectionstones:*", "Protection");
        add(presets, "Protection Stones", "protection_stones:*", "Protection");
        add(presets, "PreciousStones", "preciousstones:*", "Protection");
        add(presets, "RedProtect", "redprotect:*", "Protection");
        add(presets, "ClaimChunk", "claimchunk:*", "Protection");
        add(presets, "ChunkClaim", "chunkclaim:*", "Protection");
        add(presets, "ChunkClaims", "chunkclaims:*", "Protection");
        add(presets, "ChunkyClaims", "chunkyclaims:*", "Protection");
        add(presets, "ChunkyProtection", "chunkyprotection:*", "Protection");
        add(presets, "BetterChunkClaims", "betterchunkclaims:*", "Protection");
        add(presets, "RClaim", "rclaim:*", "Protection");
        add(presets, "BetterClaims", "betterclaims:*", "Protection");
        add(presets, "SimpleClaimSystem", "simpleclaimsystem:*", "Protection");
        add(presets, "ClaimSystem", "claimsystem:*", "Protection");
        add(presets, "ClaimPlus", "claimplus:*", "Protection");
        add(presets, "ClaimFly", "claimfly:*", "Protection");
        add(presets, "ClaimShield", "claimshield:*", "Protection");
        add(presets, "ClaimMessenger", "claimmessenger:*", "Protection");
        add(presets, "ClaimMaps", "claimmaps:*", "Protection");
        addExact(presets, "ClaimMessenger Claim", "claimmaps:claim", "Protection",
            "ClaimMessenger source: CLAIM_CHANNEL = channel(\"claim\") and registered as outgoing plugin channel.");
        addExact(presets, "ClaimMessenger No Claims", "claimmaps:no_claims", "Protection",
            "ClaimMessenger source: NO_CLAIMS_CHANNEL = channel(\"no_claims\") and registered as outgoing plugin channel.");
        addExact(presets, "ClaimMessenger Delete", "claimmaps:delete_claim", "Protection",
            "ClaimMessenger source: DELETE_CHANNEL = channel(\"delete_claim\") and registered as outgoing plugin channel.");
        add(presets, "ClaimMap", "claimmap:*", "Protection");
        add(presets, "MapClaims", "mapclaims:*", "Protection");
        add(presets, "ClaimMaps Plugin", "claimmapsplugin:*", "Protection");
        add(presets, "ProtectionCore", "protectioncore:*", "Protection");
        add(presets, "ProtectionPlus", "protectionplus:*", "Protection");
        add(presets, "Homestead", "homestead:*", "Protection");
        add(presets, "Bell Claims", "bellclaims:*", "Protection");
        add(presets, "ClaimsPlus", "claimsplus:*", "Protection");
        add(presets, "ClaimBlocks", "claimblocks:*", "Protection");
        add(presets, "HuskClaims", "huskclaims:*", "Protection");
        add(presets, "HuskTowns", "husktowns:*", "Protection");
        add(presets, "Towny", "towny:*", "Protection");
        add(presets, "TownyAdvanced", "townyadvanced:*", "Protection");
        add(presets, "Factions", "factions:*", "Protection");
        add(presets, "FactionsUUID", "factionsuuid:*", "Protection");
        add(presets, "MassiveFactions", "massivefactions:*", "Protection");
        add(presets, "SaberFactions", "saberfactions:*", "Protection");
        add(presets, "SuperiorFactions", "superiorfactions:*", "Protection");
        add(presets, "Kingdoms", "kingdoms:*", "Protection");
        add(presets, "KingdomsX", "kingdomsx:*", "Protection");
        add(presets, "Konquest", "konquest:*", "Protection");
        add(presets, "PlotSquared", "plotsquared:*", "Protection");
        add(presets, "Plots", "plots:*", "Protection");
        add(presets, "Plot", "plot:*", "Protection");
        add(presets, "PlotMe", "plotme:*", "Protection");
        add(presets, "SuperiorSkyblock", "superiorskyblock:*", "Protection");
        add(presets, "BentoBox", "bentobox:*", "Protection");
        add(presets, "ASkyBlock", "askyblock:*", "Protection");
        add(presets, "BSkyBlock", "bskyblock:*", "Protection");
        add(presets, "FabledSkyBlock", "fabledskyblock:*", "Protection");
        add(presets, "IridiumSkyblock", "iridiumskyblock:*", "Protection");
        add(presets, "LWC", "lwc:*", "Protection");
        add(presets, "LWCX", "lwcx:*", "Protection");
        add(presets, "Lockette", "lockette:*", "Protection");
        add(presets, "LockettePro", "lockettepro:*", "Protection");
        add(presets, "Bolt", "bolt:*", "Protection");
        add(presets, "ChestProtect", "chestprotect:*", "Protection");
        add(presets, "ChestProtection", "chestprotection:*", "Protection");
        add(presets, "CoreProtect", "coreprotect:*", "Protection");
        addExact(presets, "CoreProtect Handshake", "coreprotect:handshake", "Protection",
            "CoreProtect docs/source: PluginChannelHandshakeListener.pluginChannel is coreprotect:handshake.");
        addExact(presets, "CoreProtect Data", "coreprotect:data", "Protection",
            "CoreProtect docs/source: PluginChannelListener.pluginChannel is coreprotect:data.");
        add(presets, "Prism", "prism:*", "Protection");
        add(presets, "LogBlock", "logblock:*", "Protection");
        add(presets, "BlockLocker", "blocklocker:*", "Protection");
        add(presets, "Open Parties And Claims", "openpartiesandclaims:*", "Protection");
        addExact(presets, "OpenPAC Main", "openpartiesandclaims:main", "Protection",
            "Open Parties and Claims source: MOD_ID=openpartiesandclaims and MAIN_CHANNEL_LOCATION = ResourceLocation(MOD_ID, \"main\").");
        add(presets, "Open Parties Claims", "openpartiesclaims:*", "Protection");
        add(presets, "OpenPAC", "openpac:*", "Protection");
        add(presets, "Flan", "flan:*", "Protection");
        add(presets, "Cadmus", "cadmus:*", "Protection");
        add(presets, "FTB Chunks", "ftbchunks:*", "Protection");
        addExact(presets, "FTB Chunks Login Data", "ftbchunks:login_data_packet", "Protection",
            "FTB Chunks source: LoginDataPacket TYPE = FTBChunksAPI.id(\"login_data_packet\").");
        addExact(presets, "FTB Chunks General Data", "ftbchunks:send_general_data_packet", "Protection",
            "FTB Chunks source: SendGeneralDataPacket TYPE = FTBChunksAPI.id(\"send_general_data_packet\").");
        addExact(presets, "FTB Chunks Many Chunks", "ftbchunks:send_many_chunks_packet", "Protection",
            "FTB Chunks source: SendManyChunksPacket TYPE = FTBChunksAPI.id(\"send_many_chunks_packet\").");
        addExact(presets, "FTB Chunks Send Chunk", "ftbchunks:send_chunk_packet", "Protection",
            "FTB Chunks source: SendChunkPacket TYPE = FTBChunksAPI.id(\"send_chunk_packet\").");
        addExact(presets, "FTB Chunks Loaded View", "ftbchunks:loaded_chunk_view_packet", "Protection",
            "FTB Chunks source: LoadedChunkViewPacket TYPE = FTBChunksAPI.id(\"loaded_chunk_view_packet\").");
        addExact(presets, "FTB Chunks Request Map", "ftbchunks:request_map_data_packet", "Protection",
            "FTB Chunks source: RequestMapDataPacket TYPE = FTBChunksAPI.id(\"request_map_data_packet\").");
        addExact(presets, "FTB Chunks Request Change", "ftbchunks:request_chunk_change_packet", "Protection",
            "FTB Chunks source: RequestChunkChangePacket TYPE = FTBChunksAPI.id(\"request_chunk_change_packet\").");
        addExact(presets, "FTB Chunks Change Response", "ftbchunks:chunk_change_response_packet", "Protection",
            "FTB Chunks source: ChunkChangeResponsePacket TYPE = FTBChunksAPI.id(\"chunk_change_response_packet\").");
        addExact(presets, "FTB Chunks Open Claim GUI", "ftbchunks:open_claim_gui_packet", "Protection",
            "FTB Chunks source: OpenClaimGUIPacket TYPE = FTBChunksAPI.id(\"open_claim_gui_packet\").");
        addExact(presets, "FTB Chunks Force Expiry", "ftbchunks:update_force_load_expiry_packet", "Protection",
            "FTB Chunks source: UpdateForceLoadExpiryPacket TYPE = FTBChunksAPI.id(\"update_force_load_expiry_packet\").");
        addExact(presets, "FTB Chunks Teleport Map", "ftbchunks:teleport_from_map_packet", "Protection",
            "FTB Chunks source: TeleportFromMapPacket TYPE = FTBChunksAPI.id(\"teleport_from_map_packet\").");
        addExact(presets, "FTB Chunks TX Sync", "ftbchunks:sync_tx_packet", "Protection",
            "FTB Chunks source: SyncTXPacket TYPE = FTBChunksAPI.id(\"sync_tx_packet\").");
        addExact(presets, "FTB Chunks RX Sync", "ftbchunks:sync_rx_packet", "Protection",
            "FTB Chunks source: SyncRXPacket TYPE = FTBChunksAPI.id(\"sync_rx_packet\").");
        addExact(presets, "FTB Chunks Player Visibility", "ftbchunks:player_visibility_packet", "Protection",
            "FTB Chunks source: PlayerVisibilityPacket TYPE = FTBChunksAPI.id(\"player_visibility_packet\").");
        addExact(presets, "FTB Chunks Player Death", "ftbchunks:player_death_packet", "Protection",
            "FTB Chunks source: PlayerDeathPacket TYPE = FTBChunksAPI.id(\"player_death_packet\").");
        addExact(presets, "FTB Chunks Block Color", "ftbchunks:request_block_color_packet", "Protection",
            "FTB Chunks source: RequestBlockColorPacket TYPE = FTBChunksAPI.id(\"request_block_color_packet\").");
        addExact(presets, "FTB Chunks Add Waypoint", "ftbchunks:add_waypoint_packet", "Protection",
            "FTB Chunks source: AddWaypointPacket TYPE = FTBChunksAPI.id(\"add_waypoint_packet\").");
        addExact(presets, "FTB Chunks Share Waypoint", "ftbchunks:share_waypoint_packet", "Protection",
            "FTB Chunks source: ShareWaypointPacket TYPE = FTBChunksAPI.id(\"share_waypoint_packet\").");
        addExact(presets, "FTB Chunks Player Position", "ftbchunks:send_player_position_packet", "Protection",
            "FTB Chunks source: SendPlayerPositionPacket TYPE = FTBChunksAPI.id(\"send_player_position_packet\").");
        add(presets, "FTB Teams", "ftbteams:*", "Protection");
        addExact(presets, "FTB Teams Open GUI", "ftbteams:open_gui", "Protection",
            "FTB Teams source: OpenGUIMessage TYPE = FTBTeamsAPI.id(\"open_gui\").");
        addExact(presets, "FTB Teams Open My Team", "ftbteams:open_my_team_gui", "Protection",
            "FTB Teams source: OpenMyTeamGUIMessage TYPE = FTBTeamsAPI.id(\"open_my_team_gui\").");
        addExact(presets, "FTB Teams Create Party", "ftbteams:create_party", "Protection",
            "FTB Teams source: CreatePartyMessage TYPE = FTBTeamsAPI.id(\"create_party\").");
        addExact(presets, "FTB Teams Player GUI Op", "ftbteams:player_gui_operation", "Protection",
            "FTB Teams source: PlayerGUIOperationMessage TYPE = FTBTeamsAPI.id(\"player_gui_operation\").");
        addExact(presets, "FTB Teams Send Message", "ftbteams:send_message", "Protection",
            "FTB Teams source: SendMessageMessage TYPE = FTBTeamsAPI.id(\"send_message\").");
        addExact(presets, "FTB Teams Message Response", "ftbteams:send_message_response", "Protection",
            "FTB Teams source: SendMessageResponseMessage TYPE = FTBTeamsAPI.id(\"send_message_response\").");
        addExact(presets, "FTB Teams Sync Teams", "ftbteams:sync_teams", "Protection",
            "FTB Teams source: SyncTeamsMessage TYPE = FTBTeamsAPI.id(\"sync_teams\").");
        addExact(presets, "FTB Teams Sync History", "ftbteams:sync_msg_history", "Protection",
            "FTB Teams source: SyncMessageHistoryMessage TYPE = FTBTeamsAPI.id(\"sync_msg_history\").");
        addExact(presets, "FTB Teams Update Request", "ftbteams:update_properties_request", "Protection",
            "FTB Teams source: UpdatePropertiesRequestMessage TYPE = FTBTeamsAPI.id(\"update_properties_request\").");
        addExact(presets, "FTB Teams Update Response", "ftbteams:update_properties_response", "Protection",
            "FTB Teams source: UpdatePropertiesResponseMessage TYPE = FTBTeamsAPI.id(\"update_properties_response\").");
        addExact(presets, "FTB Teams Presence", "ftbteams:update_presence", "Protection",
            "FTB Teams source: UpdatePresenceMessage TYPE = FTBTeamsAPI.id(\"update_presence\").");
        addExact(presets, "FTB Teams Chat Redirect", "ftbteams:toggle_chat_redirection", "Protection",
            "FTB Teams source: ToggleChatRedirectionMessage TYPE = FTBTeamsAPI.id(\"toggle_chat_redirection\").");
        addExact(presets, "FTB Teams Chat Response", "ftbteams:toggle_chat_response", "Protection",
            "FTB Teams source: ToggleChatResponseMessage TYPE = FTBTeamsAPI.id(\"toggle_chat_response\").");
        add(presets, "WorldBorder", "worldborder:*", "Protection");
        add(presets, "World Border", "world_border:*", "Protection");
        add(presets, "WorldProtection", "worldprotection:*", "Protection");
        add(presets, "WorldProtector", "worldprotector:*", "Protection");
        add(presets, "RegionClaim", "regionclaim:*", "Protection");
        add(presets, "RegionClaims", "regionclaims:*", "Protection");
        add(presets, "RegionClaims Plus", "regionclaimsplus:*", "Protection");
        add(presets, "WorldGuard Events", "worldguardevents:*", "Protection");
        add(presets, "WorldGuard Events Alt", "worldguard_events:*", "Protection");
        add(presets, "WorldGuard Flags", "worldguardflags:*", "Protection");
        add(presets, "WG Flags", "wgflags:*", "Protection");
        add(presets, "FastAsyncWorldEdit", "fastasyncworldedit:*", "Protection");
        add(presets, "FAWE", "fawe:*", "Protection");
        add(presets, "AdvancedRegionMarket", "advancedregionmarket:*", "Protection");
        add(presets, "ARM", "arm:*", "Protection");
        add(presets, "AreaShop", "areashop:*", "Protection");
        add(presets, "AreaSell", "areasell:*", "Protection");
        add(presets, "Landlord", "landlord:*", "Protection");
        add(presets, "RealEstate", "realestate:*", "Protection");
        add(presets, "RegionMarket", "regionmarket:*", "Protection");
        add(presets, "RegionShop", "regionshop:*", "Protection");
        add(presets, "RegionForSale", "regionforsale:*", "Protection");
        add(presets, "RegionRent", "regionrent:*", "Protection");
        add(presets, "RentIt", "rentit:*", "Protection");
        add(presets, "PlotBorder", "plotborder:*", "Protection");
        add(presets, "PlotSquared v6", "plotsquared6:*", "Protection");
        add(presets, "PlotSquared v7", "plotsquared7:*", "Protection");
        add(presets, "PlotGUI", "plotgui:*", "Protection");
        add(presets, "PlotManager", "plotmanager:*", "Protection");
        add(presets, "PlotSystem", "plotsystem:*", "Protection");
        add(presets, "PlotClaim", "plotclaim:*", "Protection");
        add(presets, "PlotClaims", "plotclaims:*", "Protection");
        add(presets, "SuperiorSkyblock2", "superiorskyblock2:*", "Protection");
        add(presets, "IridiumSkyblock2", "iridiumskyblock2:*", "Protection");
        add(presets, "SkyBlock", "skyblock:*", "Protection");
        add(presets, "USkyBlock", "uskyblock:*", "Protection");
        add(presets, "AcidIsland", "acidisland:*", "Protection");
        add(presets, "IslandWorld", "islandworld:*", "Protection");
        add(presets, "OneBlock", "oneblock:*", "Protection");
        add(presets, "OneBlockSkyBlock", "oneblockskyblock:*", "Protection");
        add(presets, "Island", "island:*", "Protection");
        add(presets, "Islands", "islands:*", "Protection");
        add(presets, "MedievalFactions", "medievalfactions:*", "Protection");
        add(presets, "FactionsKore", "factionskore:*", "Protection");
        add(presets, "FactionsBlue", "factionsblue:*", "Protection");
        add(presets, "FactionsX", "factionsx:*", "Protection");
        add(presets, "FactionsOne", "factionsone:*", "Protection");
        add(presets, "FactionsTop", "factionstop:*", "Protection");
        add(presets, "Clans", "clans:*", "Protection");
        add(presets, "SimpleClans", "simpleclans:*", "Protection");
        add(presets, "UltimateClans", "ultimateclans:*", "Protection");
        add(presets, "BetterTeams", "betterteams:*", "Protection");
        add(presets, "Teams", "teams:*", "Protection");
        add(presets, "Guilds", "guilds:*", "Protection");
        add(presets, "Parties", "parties:*", "Protection");
        add(presets, "Party And Friends", "partyandfriends:*", "Protection");
        add(presets, "Nations", "nations:*", "Protection");
        add(presets, "NationsX", "nationsx:*", "Protection");
        add(presets, "Civs", "civs:*", "Protection");
        add(presets, "Civilizations", "civilizations:*", "Protection");
        add(presets, "Dominion", "dominion:*", "Protection");
        add(presets, "Settlements", "settlements:*", "Protection");
        add(presets, "Villages", "villages:*", "Protection");
        add(presets, "NationsGlory", "nationsglory:*", "Protection");
        add(presets, "BlockProt", "blockprot:*", "Protection");
        add(presets, "ColdContainerLock", "coldcontainerlock:*", "Protection");
        add(presets, "PrivateStorage", "privatestorage:*", "Protection");
        add(presets, "PrivateChests", "privatechests:*", "Protection");
        add(presets, "SecureChests", "securechests:*", "Protection");
        add(presets, "ChestLock", "chestlock:*", "Protection");
        add(presets, "ContainerLock", "containerlock:*", "Protection");
        add(presets, "ContainerLocks", "containerlocks:*", "Protection");
        add(presets, "LockSecurity", "locksecurity:*", "Protection");
        add(presets, "SafeDoors", "safedoors:*", "Protection");
        add(presets, "DoorsReloaded", "doorsreloaded:*", "Protection");
        add(presets, "Ledger", "ledger:*", "Protection");
        add(presets, "HawkEye", "hawkeye:*", "Protection");
        add(presets, "Guardian", "guardian:*", "Protection");
        add(presets, "BlockLogger", "blocklogger:*", "Protection");
        add(presets, "RollbackCore", "rollbackcore:*", "Protection");
        add(presets, "Rollback", "rollback:*", "Protection");
        add(presets, "Insights", "insights:*", "Protection");
        add(presets, "Plan AntiGrief", "planantigrief:*", "Protection");
        add(presets, "IllegalStack", "illegalstack:*", "Protection");
        add(presets, "ExploitFixer", "exploitfixer:*", "Protection");
        add(presets, "IllegalItems", "illegalitems:*", "Protection");
        add(presets, "CrazyCrates", "crazycrates:*", "Crates");
        add(presets, "ExcellentCrates", "excellentcrates:*", "Crates");
        add(presets, "GoldenCrates", "goldencrates:*", "Crates");
        add(presets, "SpecializedCrates", "specializedcrates:*", "Crates");
        add(presets, "CrateReloaded", "cratereloaded:*", "Crates");
        add(presets, "PhoenixCrates", "phoenixcrates:*", "Crates");
        add(presets, "Phoenix Crates Alt", "phoenix_crates:*", "Crates");
        add(presets, "AdvancedCrates", "advancedcrates:*", "Crates");
        add(presets, "AquaticCrates", "aquaticcrates:*", "Crates");
        add(presets, "PulseCrates", "pulsecrates:*", "Crates");
        add(presets, "MysteryCrates", "mysterycrates:*", "Crates");
        add(presets, "CratesPlus", "cratesplus:*", "Crates");
        add(presets, "CrateKeys", "cratekeys:*", "Crates");
        add(presets, "GoldenKey", "goldenkey:*", "Crates");
        add(presets, "DeluxeCrates", "deluxecrates:*", "Crates");
        add(presets, "VoidCrates", "voidcrates:*", "Crates");
        add(presets, "CrazyEnvoys", "crazyenvoys:*", "Crates");
        add(presets, "ExcellentEnchants Crates", "excellentenchants:*", "Crates");
        add(presets, "CrazyRewards", "crazyrewards:*", "Crates");
        add(presets, "LootChest", "lootchest:*", "Crates");
        add(presets, "CrazyCrates Plus", "crazycratesplus:*", "Crates");
        add(presets, "ExcellentCrates Plus", "excellentcratesplus:*", "Crates");
        add(presets, "GoldenCrates Plus", "goldencratesplus:*", "Crates");
        add(presets, "AdvancedCrates Plus", "advancedcratesplus:*", "Crates");
        add(presets, "CrateReloaded Plus", "cratereloadedplus:*", "Crates");
        add(presets, "MagicCrates", "magiccrates:*", "Crates");
        add(presets, "MagicCrate", "magiccrate:*", "Crates");
        add(presets, "SimpleLootCrates", "simplelootcrates:*", "Crates");
        add(presets, "SimpleLootCrate", "simplelootcrate:*", "Crates");
        add(presets, "MineCrates", "minecrates:*", "Crates");
        add(presets, "MineCrate", "minecrate:*", "Crates");
        add(presets, "WonderCrates", "wondercrates:*", "Crates");
        add(presets, "WonderCrate", "wondercrate:*", "Crates");
        add(presets, "SurpriseCrates", "surprisecrates:*", "Crates");
        add(presets, "SurpriseCrate", "surprisecrate:*", "Crates");
        add(presets, "NightCrates", "nightcrates:*", "Crates");
        add(presets, "NightCrate", "nightcrate:*", "Crates");
        add(presets, "CloudCrates", "cloudcrates:*", "Crates");
        add(presets, "CloudCrate", "cloudcrate:*", "Crates");
        add(presets, "EmberCrates", "embercrates:*", "Crates");
        add(presets, "EmberCrate", "embercrate:*", "Crates");
        add(presets, "RewardPro", "rewardpro:*", "Rewards");
        add(presets, "DailyRewards", "dailyrewards:*", "Rewards");
        add(presets, "AdvancedDailyRewards", "advanceddailyrewards:*", "Rewards");
        add(presets, "DeluxeRewards", "deluxerewards:*", "Rewards");
        add(presets, "ExcellentRewards", "excellentrewards:*", "Rewards");
        add(presets, "CasinoSlots", "casinoslots:*", "Gambling");
        add(presets, "SlotMachine", "slotmachine:*", "Gambling");
        add(presets, "SlotMachinePlus", "slotmachineplus:*", "Gambling");
        add(presets, "Slots", "slots:*", "Gambling");
        add(presets, "Roulette", "roulette:*", "Gambling");
        add(presets, "Casino", "casino:*", "Gambling");
        add(presets, "CrazyCasino", "crazycasino:*", "Gambling");
        add(presets, "GambleBar", "gamblebar:*", "Gambling");
        add(presets, "Gamble", "gamble:*", "Gambling");
        add(presets, "Lottery", "lottery:*", "Gambling");
        add(presets, "LotteryPlus", "lotteryplus:*", "Gambling");
        add(presets, "ExcellentCasino", "excellentcasino:*", "Gambling");
        add(presets, "CoinFlip", "coinflip:*", "Gambling");
        add(presets, "DeluxeCoinflip", "deluxecoinflip:*", "Gambling");
        add(presets, "CrazyCoinFlip", "crazycoinflip:*", "Gambling");
        add(presets, "Jackpot", "jackpot:*", "Gambling");
        add(presets, "Crash", "crash:*", "Gambling");
        add(presets, "Mines", "mines:*", "Gambling");
        add(presets, "Blackjack", "blackjack:*", "Gambling");
        add(presets, "RPS", "rps:*", "Gambling");
        add(presets, "Betting", "betting:*", "Gambling");
        add(presets, "Bets", "bets:*", "Gambling");
        add(presets, "BetonQuest", "betonquest:*", "Quests");
        add(presets, "Quests", "quests:*", "Quests");
        add(presets, "BeautyQuests", "beautyquests:*", "Quests");
        add(presets, "Citizens", "citizens:*", "NPCs");
        add(presets, "ZNPCsPlus", "znpcsplus:*", "NPCs");
        add(presets, "DecentHolograms", "decentholograms:*", "Holograms");
        add(presets, "HolographicDisplays", "holographicdisplays:*", "Holograms");
        addExact(presets, "TAB Bridge", "tab:bridge-6", "UI",
            "TAB source: TabConstants.PLUGIN_MESSAGE_CHANNEL_NAME = \"tab:bridge-6\" and proxy platforms register/send this channel.");
        add(presets, "TAB", "tab:*", "UI");
        add(presets, "LibsDisguises", "libsdisguises:*", "Network");
        addExact(presets, "Simple Voice Secret", "voicechat:secret", "Voice",
            "Simple Voice Chat source: SecretPacket type and proxy SECRET_CHANNEL use voicechat:secret.");
        addExact(presets, "Simple Voice Request Secret", "voicechat:request_secret", "Voice",
            "Simple Voice Chat source: RequestSecretPacket type and proxy REQUEST_SECRET_CHANNEL use voicechat:request_secret.");
        addExact(presets, "Simple Voice Secret Legacy", "vc:secret", "Voice",
            "Simple Voice Chat proxy source: SECRET_CHANNEL_1_12 uses vc:secret.");
        addExact(presets, "Simple Voice Request Legacy", "vc:request_secret", "Voice",
            "Simple Voice Chat proxy source: REQUEST_SECRET_CHANNEL_1_12 uses vc:request_secret.");
        addExact(presets, "Simple Voice States", "voicechat:states", "Voice",
            "Simple Voice Chat source: PlayerStatesPacket type is voicechat:states.");
        addExact(presets, "Simple Voice State", "voicechat:state", "Voice",
            "Simple Voice Chat source: PlayerStatePacket type is voicechat:state.");
        addExact(presets, "Simple Voice Update State", "voicechat:update_state", "Voice",
            "Simple Voice Chat source: UpdateStatePacket type is voicechat:update_state.");
        addExact(presets, "Simple Voice Remove State", "voicechat:remove_state", "Voice",
            "Simple Voice Chat source: RemovePlayerStatePacket type is voicechat:remove_state.");
        addExact(presets, "Simple Voice Set Group", "voicechat:set_group", "Voice",
            "Simple Voice Chat source: JoinGroupPacket type is voicechat:set_group.");
        addExact(presets, "Simple Voice Add Group", "voicechat:add_group", "Voice",
            "Simple Voice Chat source: AddGroupPacket type is voicechat:add_group.");
        addExact(presets, "Simple Voice Create Group", "voicechat:create_group", "Voice",
            "Simple Voice Chat source: CreateGroupPacket type is voicechat:create_group.");
        addExact(presets, "Simple Voice Leave Group", "voicechat:leave_group", "Voice",
            "Simple Voice Chat source: LeaveGroupPacket type is voicechat:leave_group.");
        addExact(presets, "Simple Voice Remove Group", "voicechat:remove_group", "Voice",
            "Simple Voice Chat source: RemoveGroupPacket type is voicechat:remove_group.");
        addExact(presets, "Simple Voice Joined Group", "voicechat:joined_group", "Voice",
            "Simple Voice Chat source: JoinedGroupPacket type is voicechat:joined_group.");
        addExact(presets, "Simple Voice Add Category", "voicechat:add_category", "Voice",
            "Simple Voice Chat source: AddCategoryPacket type is voicechat:add_category.");
        addExact(presets, "Simple Voice Remove Category", "voicechat:remove_category", "Voice",
            "Simple Voice Chat source: RemoveCategoryPacket type is voicechat:remove_category.");
        add(presets, "Simple Voice Chat", "voicechat:*", "Voice");
        add(presets, "Simple Voice Legacy", "vc:*", "Voice");
        addExact(presets, "Plasmo Voice", "plasmo:voice/v2", "Voice",
            "Plasmo Voice source: BaseVoiceServer.CHANNEL_STRING is plasmo:voice/v2.");
        addExact(presets, "Plasmo Voice Installed", "plasmo:voice/v2/installed", "Voice",
            "Plasmo Voice source: BaseVoiceServer.FLAG_CHANNEL_STRING is plasmo:voice/v2/installed.");
        addExact(presets, "Plasmo Voice Service", "plasmo:voice/v2/service", "Voice",
            "Plasmo Voice source: BaseVoiceServer.SERVICE_CHANNEL_STRING is plasmo:voice/v2/service.");
        add(presets, "Plasmo Voice", "plasmovoice:*", "Voice");
        add(presets, "Plasmo Voice Protocol", "plasmo:voice/*", "Voice");
        add(presets, "FancyHolograms", "fancyholograms:*", "Plugins");
        add(presets, "FancyNpcs", "fancynpcs:*", "Plugins");
        add(presets, "MythicMobs", "mythicmobs:*", "Plugins");
        addExact(presets, "LuckPerms Update", "luckperms:update", "Plugins",
            "LuckPerms source: AbstractPluginMessageMessenger.CHANNEL = \"luckperms:update\" and Bukkit/Velocity/Sponge/Forge/Fabric messengers register it.");
        add(presets, "LuckPerms", "luckperms:*", "Plugins");
        add(presets, "Forge", "forge:*", "Mod API");
        add(presets, "FML", "fml:*", "Mod API");
        add(presets, "FML Handshake", "fml:handshake*", "Mod API");
        add(presets, "NeoForge", "neoforge:*", "Mod API");
        add(presets, "Quilt", "quilt:*", "Mod API");
        add(presets, "Architectury", "architectury:*", "Mod API");
        add(presets, "Cloth Config", "cloth-config:*", "Mod API");
        add(presets, "ModMenu", "modmenu:*", "Mod API");
        add(presets, "Cardinal Components", "cardinal-components:*", "Mod API");
        addExact(presets, "CCA Entity Sync", "cardinal-components:entity_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsEntity.PACKET_ID = ComponentUpdatePayload.id(\"entity_sync\").");
        addExact(presets, "CCA Self Message", "cardinal-components:player_message_c2s", "Mod API",
            "Cardinal Components API source: CardinalComponentsEntity.C2S_SELF_PACKET_ID = ComponentUpdatePayload.id(\"player_message_c2s\").");
        addExact(presets, "CCA Block Entity Sync", "cardinal-components:block_entity_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsBlock.PACKET_ID = ComponentUpdatePayload.id(\"block_entity_sync\").");
        addExact(presets, "CCA Chunk Sync", "cardinal-components:chunk_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsChunk.PACKET_ID = ComponentUpdatePayload.id(\"chunk_sync\").");
        addExact(presets, "CCA World Sync", "cardinal-components:world_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsLevel.PACKET_ID = ComponentUpdatePayload.id(\"world_sync\").");
        addExact(presets, "CCA Level Sync", "cardinal-components:level_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsLevelData.PACKET_ID = ComponentUpdatePayload.id(\"level_sync\").");
        addExact(presets, "CCA Scoreboard Sync", "cardinal-components:scoreboard_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsScoreboard.SCOREBOARD_PACKET_ID = ComponentUpdatePayload.id(\"scoreboard_sync\").");
        addExact(presets, "CCA Team Sync", "cardinal-components:team_sync", "Mod API",
            "Cardinal Components API source: CardinalComponentsScoreboard.TEAM_PACKET_ID = ComponentUpdatePayload.id(\"team_sync\").");
        add(presets, "CCA", "cca:*", "Mod API");
        add(presets, "Polymer", "polymer:*", "Mod API");
        add(presets, "PolyMc", "polymc:*", "Mod API");
        add(presets, "Litematica", "litematica:*", "Client Mods");
        add(presets, "MiniHUD", "minihud:*", "Client Mods");
        add(presets, "Tweakeroo", "tweakeroo:*", "Client Mods");
        add(presets, "MaLiLib", "malilib:*", "Client Mods");
        add(presets, "Xaero Minimap", "xaerominimap:*", "Client Mods");
        addExact(presets, "Xaero Minimap Main", "xaerominimap:main", "Client Mods",
            "MapModCompanion/minimap-control source: XAERO_MINIMAP_CHANNEL / XAEROS_CHANNEL = \"xaerominimap:main\" and sends plugin messages on it.");
        add(presets, "Xaero World Map", "xaeroworldmap:*", "Client Mods");
        addExact(presets, "Xaero World Map Main", "xaeroworldmap:main", "Client Mods",
            "MapModCompanion/minimap-control source: XAERO_WORLDMAP_CHANNEL / XAEROS_MAP_CHANNEL = \"xaeroworldmap:main\" and sends plugin messages on it.");
        add(presets, "JourneyMap", "journeymap:*", "Client Mods");
        addExact(presets, "JourneyMap Permissions", "journeymap:perm_req", "Client Mods",
            "minimap-control source: JavaMinimapPlugin registers journeymap:perm_req and JMHandler replies on the same channel.");
        addExact(presets, "WorldInfo World ID", "worldinfo:world_id", "Client Mods",
            "MapModCompanion/minimap-control source: WORLDID_CHANNEL / WORLDINFO_CHANNEL = \"worldinfo:world_id\" and sends world-id responses on it.");
        add(presets, "VoxelMap", "voxelmap:*", "Client Mods");
        addExact(presets, "VoxelMap Settings", "voxelmap:settings", "Client Mods",
            "minimap-control source: VoxelHandler.VOXEL_SETTINGS_CHANNEL = \"voxelmap:settings\" and sends settings payloads on it.");
        add(presets, "Essential", "essential:*", "Client Mods");
        addExact(presets, "Essential Outfits", "essential:outfits", "Client Mods",
            "Essential source: OutfitUpdatesPayload.CHANNEL_ID = UIdentifier(\"essential\", \"outfits\") and the custom payload handler registers it.");
        addExact(presets, "Emotecraft Emote", "emotecraft:emote", "Client Mods",
            "Emotecraft source: MOD_ID=emotecraft and CommonData.playEmoteID = \"emote\"; Bukkit registers CommonData.getIDAsString(playEmoteID).");
        addExact(presets, "Emotecraft Stream", "emotecraft:stream", "Client Mods",
            "Emotecraft source: MOD_ID=emotecraft and CommonData.emoteStreamID = \"stream\"; Fabric/NeoForge register the stream payload.");
        add(presets, "Emotecraft", "emotecraft:*", "Client Mods");
        add(presets, "Feather", "feather:*", "Client Mods");
        add(presets, "Lunar Client", "lunarclient:*", "Client Mods");
        add(presets, "Badlion", "badlion:*", "Client Mods");
        addExact(presets, "LabyMod Neo", "labymod:neo", "Client Mods",
            "LabyMod 4 Server API source: LabyModProtocol uses PayloadChannelIdentifier.create(\"labymod\", \"neo\").");
        addExact(presets, "LabyMod 3 Main", "labymod3:main", "Client Mods",
            "LabyMod docs: protocol messages are sent on plugin channel labymod3:main.");
        add(presets, "LabyMod", "labymod:*", "Client Mods");
        add(presets, "Cosmetics", "cosmetics:*", "Client Mods");
        add(presets, "Cosmetic", "cosmetic:*", "Client Mods");
        add(presets, "SkinsRestorer", "skinsrestorer:*", "Network");
        addExact(presets, "SkinsRestorer Message", "sr:messagechannel", "Network",
            "SkinsRestorer source: SRHelpers.MESSAGE_CHANNEL = \"sr:messagechannel\" and Bukkit/Bungee/Velocity/Fabric/NeoForge register/send it.");
        add(presets, "ViaRewind", "viarewind:*", "Proxy");
        add(presets, "ProtocolSupport", "protocolsupport:*", "Proxy");
        add(presets, "RedisBungee", "redisbungee:*", "Proxy");
        add(presets, "LimboAPI", "limboapi:*", "Proxy");
        addExact(presets, "LimboAuth Mod", "limboauth:mod/541f59e4256a337ea252bc482a009d46", "Proxy",
            "LimboAuth source: MOD_CHANNEL is MinecraftChannelIdentifier.create(\"limboauth\", \"mod/541f59e4256a337ea252bc482a009d46\") and token messages are sent on getChannelIdentifier(player).");
        add(presets, "LimboAuth", "limboauth:*", "Proxy");
        addExact(presets, "FastLogin Success", "fastlogin:succ", "Proxy",
            "FastLogin source: SuccessMessage.SUCCESS_CHANNEL = \"succ\"; Bukkit/Velocity register/send namespace fastlogin:succ.");
        addExact(presets, "FastLogin Change", "fastlogin:ch-st", "Proxy",
            "FastLogin source: ChangePremiumMessage.CHANGE_CHANNEL = \"ch-st\"; Bukkit/Velocity register/send namespace fastlogin:ch-st.");
        addExact(presets, "FastLogin Force", "fastlogin:force", "Proxy",
            "FastLogin source: LoginActionMessage.FORCE_CHANNEL = \"force\"; Bukkit registers namespace fastlogin:force as incoming proxy message channel.");
        add(presets, "FastLogin", "fastlogin:*", "Proxy");
        addExact(presets, "AuthMe Main", "authme:main", "Proxy",
            "AuthMeReloaded source: Bukkit service sends authme:main; Bungee/Velocity bridges register and send the same authme:main channel.");
        add(presets, "AuthMe", "authme:*", "Proxy");
        add(presets, "AuthMeReloaded", "authmereloaded:*", "Proxy");
        add(presets, "LibreLogin", "librelogin:*", "Proxy");
        add(presets, "NLogin", "nlogin:*", "Proxy");
        add(presets, "OpeNLogin", "openlogin:*", "Proxy");
        add(presets, "LoginSecurity", "loginsecurity:*", "Proxy");
        add(presets, "BungeeGuard", "bungeeguard:*", "Proxy");
        add(presets, "Auth", "auth:*", "Proxy");
        add(presets, "ServerSelectorX", "serverselectorx:*", "Proxy");
        add(presets, "DeluxeHub", "deluxehub:*", "Proxy");
        add(presets, "HubBasics", "hubbasic:*", "Proxy");
        add(presets, "Lobby", "lobby:*", "Proxy");
        add(presets, "Hub", "hub:*", "Proxy");
        add(presets, "ServerListPlus", "serverlistplus:*", "Proxy");
        add(presets, "ServerList", "serverlist:*", "Proxy");
        add(presets, "Economy", "economy:*", "Economy");
        add(presets, "Money", "money:*", "Economy");
        add(presets, "Balance", "balance:*", "Economy");
        add(presets, "Bank", "bank:*", "Economy");
        add(presets, "Banks", "banks:*", "Economy");
        add(presets, "Currency", "currency:*", "Economy");
        add(presets, "Currencies", "currencies:*", "Economy");
        add(presets, "Coins", "coins:*", "Economy");
        add(presets, "Coin", "coin:*", "Economy");
        add(presets, "Tokens", "tokens:*", "Economy");
        add(presets, "Token", "token:*", "Economy");
        add(presets, "Credits", "credits:*", "Economy");
        add(presets, "Credit", "credit:*", "Economy");
        add(presets, "Points", "points:*", "Economy");
        add(presets, "Point", "point:*", "Economy");
        add(presets, "Gems", "gems:*", "Economy");
        add(presets, "Gem", "gem:*", "Economy");
        add(presets, "Crystals", "crystals:*", "Economy");
        add(presets, "Crystal", "crystal:*", "Economy");
        add(presets, "Shards", "shards:*", "Economy");
        add(presets, "Shard", "shard:*", "Economy");
        add(presets, "Bits", "bits:*", "Economy");
        add(presets, "Bit", "bit:*", "Economy");
        add(presets, "CraftConomy", "craftconomy:*", "Economy");
        add(presets, "iConomy", "iconomy:*", "Economy");
        add(presets, "BossEconomy", "bosseconomy:*", "Economy");
        add(presets, "BetterEconomy", "bettereconomy:*", "Economy");
        add(presets, "SimpleEconomy", "simpleeconomy:*", "Economy");
        add(presets, "CrownEconomy", "crowneconomy:*", "Economy");
        add(presets, "RoyaleEconomy", "royaleeconomy:*", "Economy");
        add(presets, "BeastTokens", "beasttokens:*", "Economy");
        add(presets, "TokenEnchant", "tokenenchant:*", "Economy");
        add(presets, "Shop", "shop:*", "Shops");
        add(presets, "Shops", "shops:*", "Shops");
        add(presets, "Store", "store:*", "Shops");
        add(presets, "Stores", "stores:*", "Shops");
        add(presets, "Market", "market:*", "Shops");
        add(presets, "Marketplace", "marketplace:*", "Shops");
        add(presets, "PlayerMarket", "playermarket:*", "Shops");
        add(presets, "PlayerMarketGUI", "playermarketgui:*", "Shops");
        add(presets, "CommunityMarket", "communitymarket:*", "Shops");
        add(presets, "ServerMarket", "servermarket:*", "Shops");
        add(presets, "TradeMarket", "trademarket:*", "Shops");
        add(presets, "TradePost", "tradepost:*", "Shops");
        add(presets, "Bazaar", "bazaar:*", "Shops");
        add(presets, "BlackMarket", "blackmarket:*", "Shops");
        add(presets, "SignShop", "signshop:*", "Shops");
        add(presets, "ShopChest", "shopchest:*", "Shops");
        add(presets, "AdminShop", "adminshop:*", "Shops");
        add(presets, "AutoSell", "autosell:*", "Shops");
        add(presets, "AutoSellChests", "autosellchests:*", "Shops");
        add(presets, "SellChest", "sellchest:*", "Shops");
        add(presets, "SellWands", "sellwands:*", "Shops");
        add(presets, "DynamicShop", "dynamicshop:*", "Shops");
        add(presets, "DynamicShopGUI", "dynamicshopgui:*", "Shops");
        add(presets, "AShop", "ashop:*", "Shops");
        add(presets, "AxShop", "axshop:*", "Shops");
        add(presets, "RoseStacker Shop", "roseshop:*", "Shops");
        add(presets, "AuctionHouse Pro", "auctionhousepro:*", "Auctions");
        add(presets, "AH", "ah:*", "Auctions");
        add(presets, "Auctions", "auctions:*", "Auctions");
        add(presets, "AuctionGUI", "auctiongui:*", "Auctions");
        add(presets, "AuctionPlus", "auctionplus:*", "Auctions");
        add(presets, "AuctionLite", "auctionlite:*", "Auctions");
        add(presets, "AuctionPro", "auctionpro:*", "Auctions");
        add(presets, "Auctioneer", "auctioneer:*", "Auctions");
        add(presets, "CrazyAH", "crazyah:*", "Auctions");
        add(presets, "ZAuction", "zauction:*", "Auctions");
        add(presets, "HuskAuctions", "huskauctions:*", "Auctions");
        add(presets, "PlayerAuction", "playerauction:*", "Auctions");
        add(presets, "CommunityAuction", "communityauction:*", "Auctions");
        add(presets, "MarketAuctions", "marketauctions:*", "Auctions");
        add(presets, "Crate", "crate:*", "Crates");
        add(presets, "Crates", "crates:*", "Crates");
        add(presets, "Keys", "keys:*", "Crates");
        add(presets, "Key", "key:*", "Crates");
        add(presets, "VaultCrates", "vaultcrates:*", "Crates");
        add(presets, "PhoenixCrates Lite", "phoenixcrateslite:*", "Crates");
        add(presets, "Phoenix Crates Lite", "phoenix_crates_lite:*", "Crates");
        add(presets, "LootCrates", "lootcrates:*", "Crates");
        add(presets, "LootCrate", "lootcrate:*", "Crates");
        add(presets, "VoteCrates", "votecrates:*", "Crates");
        add(presets, "VoteCrate", "votecrate:*", "Crates");
        add(presets, "ProCrates", "procrates:*", "Crates");
        add(presets, "EpicCrates", "epiccrates:*", "Crates");
        add(presets, "UltraCrates", "ultracrates:*", "Crates");
        add(presets, "RoyalCrates", "royalcrates:*", "Crates");
        add(presets, "CrateOpener", "crateopener:*", "Crates");
        add(presets, "CratePreview", "cratepreview:*", "Crates");
        add(presets, "MysteryBox", "mysterybox:*", "Crates");
        add(presets, "MysteryBoxes", "mysteryboxes:*", "Crates");
        add(presets, "Cases", "cases:*", "Crates");
        add(presets, "CaseOpening", "caseopening:*", "Crates");
        add(presets, "LootBoxes", "lootboxes:*", "Crates");
        add(presets, "LootBox", "lootbox:*", "Crates");
        add(presets, "RewardCrates", "rewardcrates:*", "Crates");
        add(presets, "CasinoGames", "casinogames:*", "Gambling");
        add(presets, "Vegas", "vegas:*", "Gambling");
        add(presets, "SlotMachines", "slotmachines:*", "Gambling");
        add(presets, "SlotsCasino", "slotscasino:*", "Gambling");
        add(presets, "RoulettePlus", "rouletteplus:*", "Gambling");
        add(presets, "LuckyWheel", "luckywheel:*", "Gambling");
        add(presets, "WheelOfFortune", "wheeloffortune:*", "Gambling");
        add(presets, "Wheel", "wheel:*", "Gambling");
        add(presets, "Dice", "dice:*", "Gambling");
        add(presets, "DiceGames", "dicegames:*", "Gambling");
        add(presets, "Poker", "poker:*", "Gambling");
        add(presets, "Keno", "keno:*", "Gambling");
        add(presets, "Baccarat", "baccarat:*", "Gambling");
        add(presets, "Plinko", "plinko:*", "Gambling");
        add(presets, "CaseBattles", "casebattles:*", "Gambling");
        add(presets, "CoinToss", "cointoss:*", "Gambling");
        add(presets, "HeadsOrTails", "heads_or_tails:*", "Gambling");
        add(presets, "LuckyBlock", "luckyblock:*", "Rewards");
        add(presets, "LuckyBlocks", "luckyblocks:*", "Rewards");
        add(presets, "VoteParty", "voteparty:*", "Rewards");
        add(presets, "Voting", "voting:*", "Rewards");
        add(presets, "Vote", "vote:*", "Rewards");
        add(presets, "NuVotifier", "nuvotifier:*", "Rewards");
        add(presets, "Votifier", "votifier:*", "Rewards");
        add(presets, "Reward", "reward:*", "Rewards");
        add(presets, "Rewards", "rewards:*", "Rewards");
        add(presets, "Daily", "daily:*", "Rewards");
        add(presets, "Streaks", "streaks:*", "Rewards");
        add(presets, "BattlePass", "battlepass:*", "Rewards");
        add(presets, "AdvancedBattlePass", "advancedbattlepass:*", "Rewards");
        add(presets, "Pass", "pass:*", "Rewards");
        add(presets, "QuestsPlus", "questsplus:*", "Quests");
        add(presets, "Quest", "quest:*", "Quests");
        add(presets, "QuestCreator", "questcreator:*", "Quests");
        add(presets, "NotQuests", "notquests:*", "Quests");
        add(presets, "Bounties", "bounties:*", "Quests");
        add(presets, "BountyHunters", "bountyhunters:*", "Quests");
        add(presets, "PlayerBounties", "playerbounties:*", "Quests");
        add(presets, "McMMO", "mcmmo:*", "Skills");
        add(presets, "AuraSkills", "auraskills:*", "Skills");
        add(presets, "AureliumSkills Alt", "skills:*", "Skills");
        add(presets, "LevelledMobs", "levelledmobs:*", "Mobs");
        add(presets, "MythicLib", "mythiclib:*", "Mobs");
        add(presets, "EliteMobs", "elitemobs:*", "Mobs");
        add(presets, "BossMania", "bossmania:*", "Mobs");
        add(presets, "Boss", "boss:*", "Mobs");
        add(presets, "Bosses", "bosses:*", "Mobs");
        add(presets, "ModelEngine4", "modelengine4:*", "Items");
        add(presets, "MythicDungeons", "mythicdungeons:*", "Items");
        add(presets, "CustomItems", "customitems:*", "Items");
        add(presets, "CustomBlocks", "customblocks:*", "Items");
        add(presets, "CustomCrops", "customcrops:*", "Items");
        add(presets, "CustomFishing", "customfishing:*", "Items");
        add(presets, "Items", "items:*", "Items");
        add(presets, "Item", "item:*", "Items");
        add(presets, "ItemBridge", "itembridge:*", "Items");
        add(presets, "Eco", "eco:*", "Items");
        add(presets, "EcoSkills", "ecoskills:*", "Items");
        add(presets, "EcoPets", "ecopets:*", "Items");
        add(presets, "EcoCrates", "ecocrates:*", "Items");
        add(presets, "EcoJobs", "ecojobs:*", "Items");
        add(presets, "CustomEnchants", "customenchants:*", "Items");
        add(presets, "AdvancedEnchantments", "advancedenchantments:*", "Items");
        add(presets, "CrazyEnchantments", "crazyenchantments:*", "Items");
        add(presets, "Enchants", "enchants:*", "Items");
        add(presets, "Enchantments", "enchantments:*", "Items");
        add(presets, "Menus", "menus:*", "Menus");
        add(presets, "Menu", "menu:*", "Menus");
        add(presets, "GUI", "gui:*", "Menus");
        add(presets, "GUIs", "guis:*", "Menus");
        add(presets, "InventoryGUI", "inventorygui:*", "Menus");
        add(presets, "SmartInvs", "smartinvs:*", "Menus");
        add(presets, "TriumphGUI", "triumphgui:*", "Menus");
        add(presets, "IF", "if:*", "Menus");
        add(presets, "InventoryFramework", "inventoryframework:*", "Menus");
        add(presets, "CommandPanel", "commandpanel:*", "Menus");
        add(presets, "AnimatedMenu", "animatedmenu:*", "Menus");
        add(presets, "DeluxeTags", "deluxetags:*", "Chat");
        add(presets, "ChatControl", "chatcontrol:*", "Chat");
        add(presets, "ChatControlRed", "chatcontrolred:*", "Chat");
        add(presets, "VentureChat", "venturechat:*", "Chat");
        add(presets, "DeluxeChat", "deluxechat:*", "Chat");
        add(presets, "HeroChat", "herochat:*", "Chat");
        addExact(presets, "InteractiveChat Main", "interchat:main", "Chat",
            "InteractiveChat source: Bukkit registers interchat:main incoming/outgoing and proxy code registers/sends interchat:main.");
        add(presets, "InteractiveChat", "interactivechat:*", "Chat");
        add(presets, "InteractiveChatDiscordSRV", "interactivechatdiscordsrv:*", "Chat");
        add(presets, "DiscordSRV", "discordsrv:*", "Chat");
        add(presets, "Chat", "chat:*", "Chat");
        add(presets, "ChatGames", "chatgames:*", "Chat");
        add(presets, "TAB Premium", "tabpremium:*", "UI");
        add(presets, "TAB Reborn", "tabreborn:*", "UI");
        add(presets, "NametagEdit", "nametagedit:*", "UI");
        add(presets, "AnimatedNames", "animatednames:*", "UI");
        add(presets, "Scoreboard", "scoreboard:*", "UI");
        add(presets, "Scoreboards", "scoreboards:*", "UI");
        add(presets, "FeatherBoard", "featherboard:*", "UI");
        add(presets, "AnimatedScoreboard", "animatedscoreboard:*", "UI");
        add(presets, "Hologram", "hologram:*", "Holograms");
        add(presets, "Holograms", "holograms:*", "Holograms");
        add(presets, "CMIHolograms", "cmiholograms:*", "Holograms");
        add(presets, "HD", "hd:*", "Holograms");
        add(presets, "FancyNPCs Alt", "fancynpc:*", "NPCs");
        add(presets, "ZNPCs", "znpcs:*", "NPCs");
        add(presets, "NPC", "npc:*", "NPCs");
        add(presets, "NPCs", "npcs:*", "NPCs");
        add(presets, "FancyCitizens", "fancycitizens:*", "NPCs");
        add(presets, "Sentinel", "sentinel:*", "NPCs");
        add(presets, "Denizen", "denizen:*", "NPCs");
        add(presets, "LibsDisguises Alt", "libsdisguise:*", "Network");
        add(presets, "Disguise", "disguise:*", "Network");
        add(presets, "Disguises", "disguises:*", "Network");
        add(presets, "Protocolize", "protocolize:*", "Network");
        add(presets, "PacketWrapper", "packetwrapper:*", "Network");
        add(presets, "CommandAPI", "commandapi:*", "Frameworks");
        add(presets, "Cloud", "cloud:*", "Frameworks");
        add(presets, "Aikar Commands", "acf:*", "Frameworks");
        add(presets, "InventoryLib", "inventorylib:*", "Frameworks");
        add(presets, "XSeries", "xseries:*", "Frameworks");
        add(presets, "RoseGarden", "rosegarden:*", "Frameworks");
        add(presets, "BlueSlimeCore", "blueslimecore:*", "Frameworks");
        add(presets, "Placeholder", "placeholder:*", "Frameworks");
        add(presets, "PAPI", "papi:*", "Frameworks");
        add(presets, "Worlds", "worlds:*", "Worlds");
        add(presets, "World", "world:*", "Worlds");
        add(presets, "Multiverse", "multiverse:*", "Worlds");
        add(presets, "MultiverseCore", "multiversecore:*", "Worlds");
        add(presets, "MultiverseInventories", "multiverseinventories:*", "Worlds");
        add(presets, "MultiWorld", "multiworld:*", "Worlds");
        add(presets, "WorldManager", "worldmanager:*", "Worlds");
        add(presets, "Terra", "terra:*", "Worlds");
        add(presets, "TerraformGenerator", "terraformgenerator:*", "Worlds");
        add(presets, "Iris", "iris:*", "Worlds");
        add(presets, "Dynmap", "dynmap:*", "Maps");
        add(presets, "BlueMap", "bluemap:*", "Maps");
        add(presets, "Squaremap", "squaremap:*", "Maps");
        add(presets, "Pl3xMap", "pl3xmap:*", "Maps");
        add(presets, "Overviewer", "overviewer:*", "Maps");
        addExact(presets, "Axiom Enable", "axiom:enable", "Admin",
            "Axiom Paper source: AxiomPaper registers/sends axiom:enable to clients.");
        addExact(presets, "Axiom Redo Handshake", "axiom:redo_handshake", "Admin",
            "Axiom Paper source: AxiomPaper sends axiom:redo_handshake when permissions recover.");
        addExact(presets, "Axiom Chunk Data", "axiom:response_chunk_data", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:response_chunk_data and RequestChunkDataPacketListener sends it.");
        addExact(presets, "Axiom Entity Data", "axiom:response_entity_data", "Admin",
            "Axiom Paper source: RequestEntityDataPacketListener RESPONSE_ID is axiom:response_entity_data.");
        addExact(presets, "Axiom Blueprint", "axiom:response_blueprint", "Admin",
            "Axiom Paper source: BlueprintRequestPacketListener RESPONSE_PACKET_IDENTIFIER is axiom:response_blueprint.");
        addExact(presets, "Axiom Blueprint Manifest", "axiom:blueprint_manifest", "Admin",
            "Axiom Paper source: ServerBlueprintManager PACKET_BLUEPRINT_MANIFEST_IDENTIFIER is axiom:blueprint_manifest.");
        addExact(presets, "Axiom World Properties", "axiom:register_world_properties", "Admin",
            "Axiom Paper source: AxiomPaper registers/sends axiom:register_world_properties.");
        addExact(presets, "Axiom Set World Property", "axiom:set_world_property", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:set_world_property as an outgoing plugin channel.");
        addExact(presets, "Axiom Ack World Properties", "axiom:ack_world_properties", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:ack_world_properties and SetWorldPropertyListener sends it.");
        addExact(presets, "Axiom Restrictions", "axiom:restrictions", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:restrictions and Restrictions sends it.");
        addExact(presets, "Axiom Marker Data", "axiom:marker_data", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:marker_data and WorldExtension sends it.");
        addExact(presets, "Axiom Marker NBT", "axiom:marker_nbt_response", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:marker_nbt_response and MarkerNbtRequestPacketListener sends it.");
        addExact(presets, "Axiom Annotation", "axiom:annotation_update", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:annotation_update and ServerAnnotations sends it.");
        addExact(presets, "Axiom Heightmap", "axiom:add_server_heightmap", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:add_server_heightmap and ServerHeightmaps sends it.");
        addExact(presets, "Axiom Custom Blocks", "axiom:custom_blocks", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:custom_blocks and ImplServerCustomBlocks sends it.");
        addExact(presets, "Axiom Custom Block V2", "axiom:register_custom_block_v2", "Admin",
            "Axiom Paper source: AxiomPaper registers/sends axiom:register_custom_block_v2.");
        addExact(presets, "Axiom Ignore Displays", "axiom:ignore_display_entities", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:ignore_display_entities as an outgoing plugin channel.");
        addExact(presets, "Axiom Custom Items", "axiom:register_custom_items", "Admin",
            "Axiom Paper source: AxiomPaper registers axiom:register_custom_items and ImplServerCustomDisplays sends it.");
        addExact(presets, "Axiom Dispatch Budget", "axiom:update_available_dispatch_sends", "Admin",
            "Axiom Paper source: AxiomPaper sends axiom:update_available_dispatch_sends.");
        add(presets, "Plan", "plan:*", "Admin");
        add(presets, "Spark", "spark:*", "Admin");
        add(presets, "LiteBans", "litebans:*", "Admin");
        add(presets, "AdvancedBan", "advancedban:*", "Admin");
        add(presets, "LibertyBans", "libertybans:*", "Admin");
        add(presets, "BanManager", "banmanager:*", "Admin");
        add(presets, "StaffPlus", "staffplus:*", "Admin");
        add(presets, "StaffPlusPlus", "staffplusplus:*", "Admin");
        add(presets, "StaffFacilities", "stafffacilities:*", "Admin");
        add(presets, "SuperVanish", "supervanish:*", "Admin");
        add(presets, "PremiumVanish", "premiumvanish:*", "Admin");
        add(presets, "Vanish", "vanish:*", "Admin");
        add(presets, "VanishNoPacket", "vanishnopacket:*", "Admin");
        add(presets, "Maintenance", "maintenance:*", "Admin");
        add(presets, "LuckPerms Verbose", "lp:*", "Admin");
        add(presets, "Permissions", "permissions:*", "Admin");
        add(presets, "PermissionsEx", "permissionsex:*", "Admin");
        add(presets, "UltraPermissions", "ultrapermissions:*", "Admin");
        add(presets, "Vulcan", "vulcan:*", "AntiCheat");
        add(presets, "Grim", "grim:*", "AntiCheat");
        add(presets, "GrimAC", "grimac:*", "AntiCheat");
        add(presets, "Matrix", "matrix:*", "AntiCheat");
        add(presets, "Karhu", "karhu:*", "AntiCheat");
        add(presets, "Spartan", "spartan:*", "AntiCheat");
        add(presets, "AAC", "aac:*", "AntiCheat");
        add(presets, "NoCheatPlus", "nocheatplus:*", "AntiCheat");
        add(presets, "NCP", "ncp:*", "AntiCheat");
        add(presets, "Intave", "intave:*", "AntiCheat");
        add(presets, "Polar", "polar:*", "AntiCheat");
        add(presets, "Verus", "verus:*", "AntiCheat");
        add(presets, "Themis", "themis:*", "AntiCheat");
        add(presets, "Negativity", "negativity:*", "AntiCheat");
        add(presets, "Horizon", "horizon:*", "AntiCheat");
        add(presets, "Reflex", "reflex:*", "AntiCheat");
        add(presets, "AntiAura", "antiaura:*", "AntiCheat");
        add(presets, "WatchCat", "watchcat:*", "AntiCheat");
        add(presets, "AntiCheat", "anticheat:*", "AntiCheat");
        add(presets, "AntiCheats", "anticheats:*", "AntiCheat");
        add(presets, "BedWars1058", "bedwars1058:*", "Minigames");
        add(presets, "BedWars", "bedwars:*", "Minigames");
        add(presets, "BW", "bw:*", "Minigames");
        add(presets, "SkyWars", "skywars:*", "Minigames");
        add(presets, "SW", "sw:*", "Minigames");
        add(presets, "SurvivalGames", "survivalgames:*", "Minigames");
        add(presets, "HungerGames", "hungergames:*", "Minigames");
        add(presets, "MurderMystery", "murdermystery:*", "Minigames");
        add(presets, "BuildBattle", "buildbattle:*", "Minigames");
        add(presets, "Spleef", "spleef:*", "Minigames");
        add(presets, "TNTRun", "tntrun:*", "Minigames");
        add(presets, "BlockHunt", "blockhunt:*", "Minigames");
        add(presets, "HideAndSeek", "hideandseek:*", "Minigames");
        add(presets, "Duels", "duels:*", "Minigames");
        add(presets, "KitPvP", "kitpvp:*", "Minigames");
        add(presets, "Arena", "arena:*", "Minigames");
        add(presets, "Arenas", "arenas:*", "Minigames");
        add(presets, "Minigames", "minigames:*", "Minigames");
        add(presets, "MiniGame", "minigame:*", "Minigames");
        add(presets, "PartyGames", "partygames:*", "Minigames");
        add(presets, "Parkour", "parkour:*", "Minigames");
        add(presets, "ParkourPlus", "parkourplus:*", "Minigames");
        add(presets, "TheBridge", "thebridge:*", "Minigames");
        add(presets, "Bridge", "bridge:*", "Minigames");
        add(presets, "SkyBlock Menu", "skyblockmenu:*", "Minigames");
        add(presets, "Prison", "prison:*", "Prison");
        add(presets, "PrisonMines", "prisonmines:*", "Prison");
        add(presets, "MinePacks", "minepacks:*", "Prison");
        add(presets, "Mine", "mine:*", "Prison");
        add(presets, "EzRanksPro", "ezrankspro:*", "Prison");
        add(presets, "AutoRank", "autorank:*", "Prison");
        add(presets, "Rankup", "rankup:*", "Prison");
        add(presets, "RankupX", "rankupx:*", "Prison");
        add(presets, "Rankup3", "rankup3:*", "Prison");
        add(presets, "Backpacks", "backpacks:*", "Storage");
        add(presets, "Backpack", "backpack:*", "Storage");
        add(presets, "AdvancedBackpacks", "advancedbackpacks:*", "Storage");
        add(presets, "PlayerVaults", "playervaults:*", "Storage");
        add(presets, "PlayerVaultsX", "playervaultsx:*", "Storage");
        add(presets, "EnderVaults", "endervaults:*", "Storage");
        add(presets, "PV", "pv:*", "Storage");
        add(presets, "Vaults", "vaults:*", "Storage");
        add(presets, "Lifesteal", "lifesteal:*", "SMP");
        add(presets, "LifeStealCore", "lifestealcore:*", "SMP");
        add(presets, "Hearts", "hearts:*", "SMP");
        add(presets, "Heart", "heart:*", "SMP");
        add(presets, "CombatLogX", "combatlogx:*", "SMP");
        add(presets, "CombatLog", "combatlog:*", "SMP");
        add(presets, "CombatPlus", "combatplus:*", "SMP");
        add(presets, "Combat", "combat:*", "SMP");
        addExact(presets, "Consumable Optimizer Disable", "consumable_optimizer:disable_payload", "Security",
            "Consumable Optimizer source/docs: Paper opt-out registers/sends consumable_optimizer:disable_payload.");
        addExact(presets, "Consumable Optimizer Handshake", "consumable_optimizer:handshake_payload", "Security",
            "Consumable Optimizer source/docs: Paper opt-out registers consumable_optimizer:handshake_payload as the client handshake channel.");
        addExact(presets, "FastCrystal Disable", "clientcrystal:disable_fast_crystal", "Security",
            "FastCrystal source/docs: Paper/Spigot opt-out sends clientcrystal:disable_fast_crystal.");
        addExact(presets, "FastCrystal Fabric Disable", "fastcrystal:disable_fast_crystal", "Security",
            "FastCrystal source/docs: Fabric server opt-out payload id is fastcrystal:disable_fast_crystal.");
        addExact(presets, "OptiPlus Disable", "optiplus:disable", "Security",
            "OptiPlus public docs: optiplus:disable disables both OptiPlus variants.");
        addExact(presets, "OptiPlus Crystal Disable", "optiplus-crystal:disable", "Security",
            "OptiPlus-Crystal docs: Paper/Spigot/Bukkit opt-out registers/sends optiplus-crystal:disable.");
        addExact(presets, "OptiPlus Anchor Disable", "optiplus-anchor:disable", "Security",
            "OptiPlus-Anchor docs: Paper/Spigot/Bukkit opt-out registers/sends optiplus-anchor:disable.");
        add(presets, "DupeFixes", "dupefixes:*", "Security");
        add(presets, "DupeFix", "dupefix:*", "Security");
        add(presets, "ExploitFixes", "exploitfixes:*", "Security");
        add(presets, "AntiExploit", "antiexploit:*", "Security");
        add(presets, "AntiDupe", "antidupe:*", "Security");
        add(presets, "ItemFilter", "itemfilter:*", "Security");
        add(presets, "IllegalItem", "illegalitem:*", "Security");
        add(presets, "IllegalItemsPlus", "illegalitemsplus:*", "Security");
        add(presets, "LifeStealZ", "lifestealz:*", "SMP");
        add(presets, "LifeStealPlus", "lifestealplus:*", "SMP");
        add(presets, "LifeSteal SMP", "lifestealsmp:*", "SMP");
        add(presets, "LifeStealCoreX", "lifestealcorex:*", "SMP");
        add(presets, "SimpleLifeSteal", "simplelifesteal:*", "SMP");
        add(presets, "PLifeSteal", "plifesteal:*", "SMP");
        add(presets, "LS SMP", "lssmp:*", "SMP");
        add(presets, "SMP", "smp:*", "SMP");
        add(presets, "Survival", "survival:*", "SMP");
        add(presets, "SurvivalCore", "survivalcore:*", "SMP");
        add(presets, "SurvivalPlus", "survivalplus:*", "SMP");
        add(presets, "SurvivalSystem", "survivalsystem:*", "SMP");
        add(presets, "SMPCore", "smpcore:*", "SMP");
        add(presets, "SMPSystem", "smpsystem:*", "SMP");
        add(presets, "SMPPlus", "smpplus:*", "SMP");
        add(presets, "HeartsPlus", "heartsplus:*", "SMP");
        add(presets, "HeartSystem", "heartsystem:*", "SMP");
        add(presets, "Revive", "revive:*", "SMP");
        add(presets, "Revives", "revives:*", "SMP");
        add(presets, "ReviveMe", "reviveme:*", "SMP");
        add(presets, "RevivePlus", "reviveplus:*", "SMP");
        add(presets, "DeathBan", "deathban:*", "SMP");
        add(presets, "DeathBans", "deathbans:*", "SMP");
        add(presets, "CombatTag", "combattag:*", "SMP");
        add(presets, "CombatTagPlus", "combattagplus:*", "SMP");
        add(presets, "CombatTimer", "combattimer:*", "SMP");
        add(presets, "CombatManager", "combatmanager:*", "SMP");
        add(presets, "PvPManager", "pvpmanager:*", "SMP");
        add(presets, "PvPManager Lite", "pvpmanagerlite:*", "SMP");
        add(presets, "PvPManager Premium", "pvpmanagerpremium:*", "SMP");
        add(presets, "PvPToggle", "pvptoggle:*", "SMP");
        add(presets, "PvPControl", "pvpcontrol:*", "SMP");
        add(presets, "KeepInventory", "keepinventory:*", "SMP");
        add(presets, "DeathChest", "deathchest:*", "SMP");
        add(presets, "DeathChests", "deathchests:*", "SMP");
        add(presets, "AngelChest", "angelchest:*", "SMP");
        add(presets, "Graves", "graves:*", "SMP");
        add(presets, "Grave", "grave:*", "SMP");
        add(presets, "GraveStones", "gravestones:*", "SMP");
        add(presets, "GraveStone", "gravestone:*", "SMP");
        add(presets, "BetterRTP", "betterrtp:*", "SMP");
        add(presets, "RandomTeleport", "randomteleport:*", "SMP");
        add(presets, "RTP", "rtp:*", "SMP");
        add(presets, "Homes", "homes:*", "SMP");
        add(presets, "Home", "home:*", "SMP");
        add(presets, "SetHome", "sethome:*", "SMP");
        add(presets, "Warp", "warp:*", "SMP");
        add(presets, "Warps", "warps:*", "SMP");
        add(presets, "BetterWarps", "betterwarps:*", "SMP");
        add(presets, "Teleport", "teleport:*", "SMP");
        add(presets, "TPA", "tpa:*", "SMP");
        add(presets, "Back", "back:*", "SMP");
        add(presets, "Spawn", "spawn:*", "SMP");
        add(presets, "SetSpawn", "setspawn:*", "SMP");
        add(presets, "SleepMost", "sleepmost:*", "SMP");
        add(presets, "Harbor", "harbor:*", "SMP");
        add(presets, "OnePlayerSleep", "oneplayersleep:*", "SMP");
        add(presets, "SinglePlayerSleep", "singleplayersleep:*", "SMP");
        add(presets, "VeinMiner", "veinminer:*", "SMP");
        add(presets, "OreAnnouncer", "oreannouncer:*", "SMP");
        add(presets, "TreeFeller", "treefeller:*", "SMP");
        add(presets, "TreeAssist", "treeassist:*", "SMP");
        add(presets, "Timber", "timber:*", "SMP");
        add(presets, "SilkSpawners", "silkspawners:*", "SMP");
        add(presets, "WildStacker", "wildstacker:*", "SMP");
        add(presets, "RoseStacker", "rosestacker:*", "SMP");
        add(presets, "StackMob", "stackmob:*", "SMP");
        add(presets, "Chunky", "chunky:*", "SMP");
        add(presets, "ChunkyBorder", "chunkyborder:*", "SMP");
        add(presets, "ChunkyBorder Alt", "chunky:border*", "SMP");
        add(presets, "AnarchyExploitFixes", "anarchyexploitfixes:*", "Security");
        add(presets, "AEF", "aef:*", "Security");
        add(presets, "ExploitFixer API", "exploitfixerapi:*", "Security");
        add(presets, "HamsterAPI", "hamsterapi:*", "Security");
        add(presets, "DupeFixer", "dupefixer:*", "Security");
        add(presets, "DupePatches", "dupepatches:*", "Security");
        add(presets, "DupeProtection", "dupeprotection:*", "Security");
        add(presets, "AntiDuplication", "antiduplication:*", "Security");
        add(presets, "AntiBookBan", "antibookban:*", "Security");
        add(presets, "BookExploitFix", "bookexploitfix:*", "Security");
        add(presets, "BookFix", "bookfix:*", "Security");
        add(presets, "BookLimiter", "booklimiter:*", "Security");
        add(presets, "BookGuard", "bookguard:*", "Security");
        add(presets, "PacketLimiter", "packetlimiter:*", "Security");
        add(presets, "PacketFilter", "packetfilter:*", "Security");
        add(presets, "PacketGuard", "packetguard:*", "Security");
        add(presets, "PacketSecurity", "packetsecurity:*", "Security");
        add(presets, "PacketEvents Security", "packeteventssecurity:*", "Security");
        add(presets, "PacketAntiCrash", "packetanticrash:*", "Security");
        add(presets, "AntiCrash", "anticrash:*", "Security");
        add(presets, "CrashFix", "crashfix:*", "Security");
        add(presets, "CrashFixer", "crashfixer:*", "Security");
        add(presets, "CrashGuard", "crashguard:*", "Security");
        add(presets, "AntiCrashPlus", "anticrashplus:*", "Security");
        add(presets, "ExploitPatch", "exploitpatch:*", "Security");
        add(presets, "ExploitPatcher", "exploitpatcher:*", "Security");
        add(presets, "ExploitProtection", "exploitprotection:*", "Security");
        add(presets, "ExploitGuard", "exploitguard:*", "Security");
        add(presets, "AntiExploitPlus", "antiexploitplus:*", "Security");
        add(presets, "AntiCrashReloaded", "anticrashreloaded:*", "Security");
        add(presets, "IllegalStackRemover", "illegalstackremover:*", "Security");
        add(presets, "IllegalStackFixer", "illegalstackfixer:*", "Security");
        add(presets, "IllegalStackFix", "illegalstackfix:*", "Security");
        add(presets, "IllegalItemRemover", "illegalitemremover:*", "Security");
        add(presets, "IllegalItemFixer", "illegalitemfixer:*", "Security");
        add(presets, "IllegalItemFix", "illegalitemfix:*", "Security");
        add(presets, "IllegalEnchants", "illegalenchants:*", "Security");
        add(presets, "OverstackFix", "overstackfix:*", "Security");
        add(presets, "OverstackFixer", "overstackfixer:*", "Security");
        add(presets, "StackFix", "stackfix:*", "Security");
        add(presets, "StackFixer", "stackfixer:*", "Security");
        add(presets, "ItemGuard", "itemguard:*", "Security");
        add(presets, "InventoryGuard", "inventoryguard:*", "Security");
        add(presets, "InventorySecurity", "inventorysecurity:*", "Security");
        add(presets, "OpenInvGuard", "openinvguard:*", "Security");
        add(presets, "CommandWhitelist", "commandwhitelist:*", "Security");
        add(presets, "CommandBlocker", "commandblocker:*", "Security");
        add(presets, "CommandGuard", "commandguard:*", "Security");
        add(presets, "ChatGuard", "chatguard:*", "Security");
        add(presets, "AntiSpam", "antispam:*", "Security");
        add(presets, "AntiSpamPlus", "antispamplus:*", "Security");
        add(presets, "AntiBot", "antibot:*", "Security");
        add(presets, "BotSentry", "botsentry:*", "Security");
        add(presets, "BotGuard", "botguard:*", "Security");
        add(presets, "BotFilter", "botfilter:*", "Security");
        add(presets, "AntiBotDeluxe", "antibotdeluxe:*", "Security");
        add(presets, "AntiVPN", "antivpn:*", "Security");
        add(presets, "VPNGuard", "vpnguard:*", "Security");
        add(presets, "VPNBlocker", "vpnblocker:*", "Security");
        add(presets, "ProxyCheck", "proxycheck:*", "Security");
        add(presets, "ProxyGuard", "proxyguard:*", "Security");
        add(presets, "FastAntiBot", "fastantibot:*", "Security");
        add(presets, "EpicGuard", "epicguard:*", "Security");
        add(presets, "FlameCord", "flamecord:*", "Security");
        add(presets, "BotFilterPlus", "botfilterplus:*", "Security");
        add(presets, "AccountGuard", "accountguard:*", "Security");
        add(presets, "LoginGuard", "loginguard:*", "Security");
        add(presets, "NameSecurity", "namesecurity:*", "Security");
        add(presets, "UUIDSpoofFix", "uuidspooffix:*", "Security");
        add(presets, "UUIDGuard", "uuidguard:*", "Security");
        add(presets, "NettyGuard", "nettyguard:*", "Security");
        add(presets, "PayloadGuard", "payloadguard:*", "Security");
        add(presets, "PayloadFilter", "payloadfilter:*", "Security");
        add(presets, "PayloadSecurity", "payloadsecurity:*", "Security");
        add(presets, "ChannelGuard", "channelguard:*", "Security");
        add(presets, "PluginMessageGuard", "pluginmessageguard:*", "Security");
        add(presets, "PluginMessageFilter", "pluginmessagefilter:*", "Security");
        add(presets, "AxCrates", "axcrates:*", "Crates");
        add(presets, "AxCrate", "axcrate:*", "Crates");
        add(presets, "CratesX", "cratesx:*", "Crates");
        add(presets, "CrateX", "cratex:*", "Crates");
        add(presets, "CratesPro", "cratespro:*", "Crates");
        add(presets, "CratePro", "cratepro:*", "Crates");
        add(presets, "CrateCore", "cratecore:*", "Crates");
        add(presets, "CratesCore", "cratescore:*", "Crates");
        add(presets, "CrateSystem", "cratesystem:*", "Crates");
        add(presets, "CratesSystem", "cratessystem:*", "Crates");
        add(presets, "CrateGUI", "crategui:*", "Crates");
        add(presets, "CratesGUI", "cratesgui:*", "Crates");
        add(presets, "CrateMenu", "cratemenu:*", "Crates");
        add(presets, "CratesMenu", "cratesmenu:*", "Crates");
        add(presets, "CrateMenus", "cratemenus:*", "Crates");
        add(presets, "CratesAPI", "cratesapi:*", "Crates");
        add(presets, "CrateAPI", "crateapi:*", "Crates");
        add(presets, "CratePreviewer", "cratepreviewer:*", "Crates");
        add(presets, "CratesPreview", "cratespreview:*", "Crates");
        add(presets, "CratesPreviewer", "cratespreviewer:*", "Crates");
        add(presets, "CrateAnimation", "crateanimation:*", "Crates");
        add(presets, "CrateAnimations", "crateanimations:*", "Crates");
        add(presets, "CrateRoll", "crateroll:*", "Crates");
        add(presets, "CrateRolls", "craterolls:*", "Crates");
        add(presets, "CrateWheel", "cratewheel:*", "Crates");
        add(presets, "CrateWheels", "cratewheels:*", "Crates");
        add(presets, "CrateSpin", "cratespin:*", "Crates");
        add(presets, "CrateSpins", "cratespins:*", "Crates");
        add(presets, "CrateOpenerPro", "crateopenerpro:*", "Crates");
        add(presets, "CrateOpeners", "crateopeners:*", "Crates");
        add(presets, "CrateKey", "cratekey:*", "Crates");
        add(presets, "CrateKeysPro", "cratekeyspro:*", "Crates");
        add(presets, "KeyCrates", "keycrates:*", "Crates");
        add(presets, "KeyCrate", "keycrate:*", "Crates");
        add(presets, "KeySystem", "keysystem:*", "Crates");
        add(presets, "KeysSystem", "keyssystem:*", "Crates");
        add(presets, "SimpleCrates", "simplecrates:*", "Crates");
        add(presets, "SimpleCrate", "simplecrate:*", "Crates");
        add(presets, "EasyCrates", "easycrates:*", "Crates");
        add(presets, "EasyCrate", "easycrate:*", "Crates");
        add(presets, "BetterCrates", "bettercrates:*", "Crates");
        add(presets, "BetterCrate", "bettercrate:*", "Crates");
        add(presets, "PremiumCrates", "premiumcrates:*", "Crates");
        add(presets, "PremiumCrate", "premiumcrate:*", "Crates");
        add(presets, "UltimateCrates", "ultimatecrates:*", "Crates");
        add(presets, "UltimateCrate", "ultimatecrate:*", "Crates");
        add(presets, "SupremeCrates", "supremecrates:*", "Crates");
        add(presets, "SupremeCrate", "supremecrate:*", "Crates");
        add(presets, "DivineCrates", "divinecrates:*", "Crates");
        add(presets, "DivineCrate", "divinecrate:*", "Crates");
        add(presets, "LegendaryCrates", "legendarycrates:*", "Crates");
        add(presets, "LegendaryCrate", "legendarycrate:*", "Crates");
        add(presets, "RareCrates", "rarecrates:*", "Crates");
        add(presets, "RareCrate", "rarecrate:*", "Crates");
        add(presets, "EpicCrate", "epiccrate:*", "Crates");
        add(presets, "UltraCrate", "ultracrate:*", "Crates");
        add(presets, "RoyalCrate", "royalcrate:*", "Crates");
        add(presets, "GoldenCrate", "goldencrate:*", "Crates");
        add(presets, "CrazyCrate", "crazycrate:*", "Crates");
        add(presets, "AdvancedCrate", "advancedcrate:*", "Crates");
        add(presets, "ExcellentCrate", "excellentcrate:*", "Crates");
        add(presets, "SpecializedCrate", "specializedcrate:*", "Crates");
        add(presets, "PhoenixCrate", "phoenixcrate:*", "Crates");
        add(presets, "AquaticCrate", "aquaticcrate:*", "Crates");
        add(presets, "AquaCrates", "aquacrates:*", "Crates");
        add(presets, "AquaCrate", "aquacrate:*", "Crates");
        add(presets, "PulseCrate", "pulsecrate:*", "Crates");
        add(presets, "MysticCrates", "mysticcrates:*", "Crates");
        add(presets, "MysticCrate", "mysticcrate:*", "Crates");
        add(presets, "MythicCrates", "mythiccrates:*", "Crates");
        add(presets, "MythicCrate", "mythiccrate:*", "Crates");
        add(presets, "OPCrates", "opcrates:*", "Crates");
        add(presets, "OPCrate", "opcrate:*", "Crates");
        add(presets, "VoteKey", "votekey:*", "Crates");
        add(presets, "VoteKeys", "votekeys:*", "Crates");
        add(presets, "Case", "case:*", "Crates");
        add(presets, "CasesPro", "casespro:*", "Crates");
        add(presets, "CaseSystem", "casesystem:*", "Crates");
        add(presets, "CaseOpeningPro", "caseopeningpro:*", "Crates");
        add(presets, "OpenCase", "opencase:*", "Crates");
        add(presets, "OpenCases", "opencases:*", "Crates");
        add(presets, "CaseKeys", "casekeys:*", "Crates");
        add(presets, "CaseKey", "casekey:*", "Crates");
        add(presets, "Boxes", "boxes:*", "Crates");
        add(presets, "Box", "box:*", "Crates");
        add(presets, "RewardBox", "rewardbox:*", "Crates");
        add(presets, "RewardBoxes", "rewardboxes:*", "Crates");
        add(presets, "PrizeCrates", "prizecrates:*", "Crates");
        add(presets, "PrizeCrate", "prizecrate:*", "Crates");
        add(presets, "PrizeBox", "prizebox:*", "Crates");
        add(presets, "PrizeBoxes", "prizeboxes:*", "Crates");
        add(presets, "LootCratesPro", "lootcratespro:*", "Crates");
        add(presets, "LootBoxPro", "lootboxpro:*", "Crates");
        add(presets, "LootBoxesPro", "lootboxespro:*", "Crates");
        add(presets, "RewardCase", "rewardcase:*", "Crates");
        add(presets, "RewardCases", "rewardcases:*", "Crates");
        add(presets, "CasinoCore", "casinocore:*", "Gambling");
        add(presets, "CasinoSystem", "casinosystem:*", "Gambling");
        add(presets, "CasinoPlus", "casinoplus:*", "Gambling");
        add(presets, "CasinoPro", "casinopro:*", "Gambling");
        add(presets, "CasinoGUI", "casinogui:*", "Gambling");
        add(presets, "CasinoMenu", "casinomenu:*", "Gambling");
        add(presets, "Casinos", "casinos:*", "Gambling");
        add(presets, "Gambling", "gambling:*", "Gambling");
        add(presets, "GamblingCore", "gamblingcore:*", "Gambling");
        add(presets, "GamblingSystem", "gamblingsystem:*", "Gambling");
        add(presets, "GamblingPlus", "gamblingplus:*", "Gambling");
        add(presets, "GamblingPro", "gamblingpro:*", "Gambling");
        add(presets, "Bet", "bet:*", "Gambling");
        add(presets, "BetsPlus", "betsplus:*", "Gambling");
        add(presets, "BettingPlus", "bettingplus:*", "Gambling");
        add(presets, "BettingSystem", "bettingsystem:*", "Gambling");
        add(presets, "BettingCore", "bettingcore:*", "Gambling");
        add(presets, "Wager", "wager:*", "Gambling");
        add(presets, "Wagers", "wagers:*", "Gambling");
        add(presets, "Wagering", "wagering:*", "Gambling");
        add(presets, "Stake", "stake:*", "Gambling");
        add(presets, "Stakes", "stakes:*", "Gambling");
        add(presets, "CoinFlips", "coinflips:*", "Gambling");
        add(presets, "CoinFlipPlus", "coinflipplus:*", "Gambling");
        add(presets, "CoinFlipPro", "coinflippro:*", "Gambling");
        add(presets, "CoinFlipSystem", "coinflipsystem:*", "Gambling");
        add(presets, "CoinFlipCore", "coinflipcore:*", "Gambling");
        add(presets, "CoinTossPlus", "cointossplus:*", "Gambling");
        add(presets, "CoinTossPro", "cointosspro:*", "Gambling");
        add(presets, "HeadsTails", "headstails:*", "Gambling");
        add(presets, "HeadsOrTailsPlus", "heads_or_tails_plus:*", "Gambling");
        add(presets, "Jackpots", "jackpots:*", "Gambling");
        add(presets, "JackpotPlus", "jackpotplus:*", "Gambling");
        add(presets, "JackpotPro", "jackpotpro:*", "Gambling");
        add(presets, "JackpotSystem", "jackpotsystem:*", "Gambling");
        add(presets, "LotteryCore", "lotterycore:*", "Gambling");
        add(presets, "LotterySystem", "lotterysystem:*", "Gambling");
        add(presets, "LotteryPro", "lotterypro:*", "Gambling");
        add(presets, "Lotto", "lotto:*", "Gambling");
        add(presets, "LottoPlus", "lottoplus:*", "Gambling");
        add(presets, "LottoSystem", "lottosystem:*", "Gambling");
        add(presets, "Raffle", "raffle:*", "Gambling");
        add(presets, "Raffles", "raffles:*", "Gambling");
        add(presets, "RafflePlus", "raffleplus:*", "Gambling");
        add(presets, "RouletteCore", "roulettecore:*", "Gambling");
        add(presets, "RouletteSystem", "roulettesystem:*", "Gambling");
        add(presets, "RoulettePro", "roulettepro:*", "Gambling");
        add(presets, "RouletteGUI", "roulettegui:*", "Gambling");
        add(presets, "RussianRoulette", "russianroulette:*", "Gambling");
        add(presets, "Slot", "slot:*", "Gambling");
        add(presets, "SlotPlus", "slotplus:*", "Gambling");
        add(presets, "SlotPro", "slotpro:*", "Gambling");
        add(presets, "SlotSystem", "slotsystem:*", "Gambling");
        add(presets, "SlotsCore", "slotscore:*", "Gambling");
        add(presets, "SlotsPlus", "slotsplus:*", "Gambling");
        add(presets, "SlotsPro", "slotspro:*", "Gambling");
        add(presets, "SlotsSystem", "slotssystem:*", "Gambling");
        add(presets, "SlotMachinesPlus", "slotmachinesplus:*", "Gambling");
        add(presets, "SlotMachinesPro", "slotmachinespro:*", "Gambling");
        add(presets, "OneArmedBandit", "onearmedbandit:*", "Gambling");
        add(presets, "LuckySlots", "luckyslots:*", "Gambling");
        add(presets, "LuckySlot", "luckyslot:*", "Gambling");
        add(presets, "WheelPlus", "wheelplus:*", "Gambling");
        add(presets, "WheelPro", "wheelpro:*", "Gambling");
        add(presets, "WheelSystem", "wheelsystem:*", "Gambling");
        add(presets, "LuckyWheelPlus", "luckywheelplus:*", "Gambling");
        add(presets, "LuckyWheelPro", "luckywheelpro:*", "Gambling");
        add(presets, "WheelSpin", "wheelspin:*", "Gambling");
        add(presets, "WheelSpins", "wheelspins:*", "Gambling");
        add(presets, "SpinWheel", "spinwheel:*", "Gambling");
        add(presets, "SpinToWin", "spintowin:*", "Gambling");
        add(presets, "DicePlus", "diceplus:*", "Gambling");
        add(presets, "DicePro", "dicepro:*", "Gambling");
        add(presets, "DiceSystem", "dicesystem:*", "Gambling");
        add(presets, "DiceRoll", "diceroll:*", "Gambling");
        add(presets, "DiceRolls", "dicerolls:*", "Gambling");
        add(presets, "RollDice", "rolldice:*", "Gambling");
        add(presets, "BlackjackPlus", "blackjackplus:*", "Gambling");
        add(presets, "BlackjackPro", "blackjackpro:*", "Gambling");
        add(presets, "BlackjackSystem", "blackjacksystem:*", "Gambling");
        add(presets, "PokerPlus", "pokerplus:*", "Gambling");
        add(presets, "PokerPro", "pokerpro:*", "Gambling");
        add(presets, "PokerSystem", "pokersystem:*", "Gambling");
        add(presets, "TexasHoldem", "texasholdem:*", "Gambling");
        add(presets, "Holdem", "holdem:*", "Gambling");
        add(presets, "BaccaratPlus", "baccaratplus:*", "Gambling");
        add(presets, "KenoPlus", "kenoplus:*", "Gambling");
        add(presets, "PlinkoPlus", "plinkoplus:*", "Gambling");
        add(presets, "PlinkoPro", "plinkopro:*", "Gambling");
        add(presets, "CrashGame", "crashgame:*", "Gambling");
        add(presets, "CrashPlus", "crashplus:*", "Gambling");
        add(presets, "CrashPro", "crashpro:*", "Gambling");
        add(presets, "MinesGame", "minesgame:*", "Gambling");
        add(presets, "MinesPlus", "minesplus:*", "Gambling");
        add(presets, "MinesPro", "minespro:*", "Gambling");
        add(presets, "RPSPlus", "rpsplus:*", "Gambling");
        add(presets, "RockPaperScissors", "rockpaperscissors:*", "Gambling");
        add(presets, "CaseBattle", "casebattle:*", "Gambling");
        add(presets, "CaseBattlePlus", "casebattleplus:*", "Gambling");
        add(presets, "CaseBattlePro", "casebattlepro:*", "Gambling");
        add(presets, "LootBattle", "lootbattle:*", "Gambling");
        add(presets, "LootBattles", "lootbattles:*", "Gambling");
        add(presets, "Ax Family", "ax*", "Frameworks");
        add(presets, "Ax Namespace", "ax:*", "Frameworks");
        add(presets, "Artillex", "artillex:*", "Frameworks");
        add(presets, "ArtillexStudios", "artillexstudios:*", "Frameworks");
        add(presets, "Artillex Studios", "artillex_studios:*", "Frameworks");
        add(presets, "AxVaults", "axvaults:*", "Storage");
        add(presets, "AxVault", "axvault:*", "Storage");
        add(presets, "AxBackpacks", "axbackpacks:*", "Storage");
        add(presets, "AxBackpack", "axbackpack:*", "Storage");
        add(presets, "AxStorage", "axstorage:*", "Storage");
        add(presets, "AxStorages", "axstorages:*", "Storage");
        add(presets, "AxShulkers", "axshulkers:*", "Storage");
        add(presets, "AxShulker", "axshulker:*", "Storage");
        add(presets, "AxEnderChest", "axenderchest:*", "Storage");
        add(presets, "AxEnderChests", "axenderchests:*", "Storage");
        add(presets, "AxTrade", "axtrade:*", "Shops");
        add(presets, "AxTrades", "axtrades:*", "Shops");
        add(presets, "AxMarket", "axmarket:*", "Shops");
        add(presets, "AxMarkets", "axmarkets:*", "Shops");
        add(presets, "AxEconomy", "axeconomy:*", "Economy");
        add(presets, "AxCoins", "axcoins:*", "Economy");
        add(presets, "AxGems", "axgems:*", "Economy");
        add(presets, "AxTokens", "axtokens:*", "Economy");
        add(presets, "AxRewards", "axrewards:*", "Rewards");
        add(presets, "AxReward", "axreward:*", "Rewards");
        add(presets, "AxDailyRewards", "axdailyrewards:*", "Rewards");
        add(presets, "AxKits", "axkits:*", "Rewards");
        add(presets, "AxKit", "axkit:*", "Rewards");
        add(presets, "AxMines", "axmines:*", "Prison");
        add(presets, "AxMine", "axmine:*", "Prison");
        add(presets, "AxPrison", "axprison:*", "Prison");
        add(presets, "AxRankup", "axrankup:*", "Prison");
        add(presets, "AxRanks", "axranks:*", "Prison");
        add(presets, "AxSellwands", "axsellwands:*", "Prison");
        add(presets, "AxSellwand", "axsellwand:*", "Prison");
        add(presets, "AxEnvoys", "axenvoys:*", "Crates");
        add(presets, "AxEnvoy", "axenvoy:*", "Crates");
        add(presets, "AxBoosters", "axboosters:*", "Prison");
        add(presets, "AxBooster", "axbooster:*", "Prison");
        add(presets, "AxGens", "axgens:*", "SMP");
        add(presets, "AxGen", "axgen:*", "SMP");
        add(presets, "AxGenerators", "axgenerators:*", "SMP");
        add(presets, "AxGenerator", "axgenerator:*", "SMP");
        add(presets, "AxCombat", "axcombat:*", "SMP");
        add(presets, "AxDuels", "axduels:*", "Minigames");
        add(presets, "AxDuelsPlus", "axduelsplus:*", "Minigames");
        add(presets, "AxParkour", "axparkour:*", "Minigames");
        add(presets, "AxQuests", "axquests:*", "Quests");
        add(presets, "AxQuest", "axquest:*", "Quests");
        add(presets, "AxMenus", "axmenus:*", "Menus");
        add(presets, "AxMenu", "axmenu:*", "Menus");
        add(presets, "AxChat", "axchat:*", "Chat");
        add(presets, "AxTags", "axtags:*", "Chat");
        add(presets, "AxScoreboard", "axscoreboard:*", "UI");
        add(presets, "AxTab", "axtab:*", "UI");
        add(presets, "AxHolograms", "axholograms:*", "Holograms");
        add(presets, "AxHologram", "axhologram:*", "Holograms");
        add(presets, "AxNPCs", "axnpcs:*", "NPCs");
        add(presets, "AxNPC", "axnpc:*", "NPCs");
        add(presets, "AxSecurity", "axsecurity:*", "Security");
        add(presets, "AxGuard", "axguard:*", "Security");
        add(presets, "AxAntiCrash", "axanticrash:*", "Security");
        add(presets, "AxAntiBot", "axantibot:*", "Security");
        add(presets, "AxAntiVPN", "axantivpn:*", "Security");
        add(presets, "Az Family", "az*", "Frameworks");
        add(presets, "Az Namespace", "az:*", "Frameworks");
        add(presets, "AzAuctionHouse", "azauctionhouse:*", "Auctions");
        add(presets, "AzAuctions", "azauctions:*", "Auctions");
        add(presets, "AzAuction", "azauction:*", "Auctions");
        add(presets, "AzShop", "azshop:*", "Shops");
        add(presets, "AzShops", "azshops:*", "Shops");
        add(presets, "AzMarket", "azmarket:*", "Shops");
        add(presets, "AzMarkets", "azmarkets:*", "Shops");
        add(presets, "AzVault", "azvault:*", "Storage");
        add(presets, "AzVaults", "azvaults:*", "Storage");
        add(presets, "AzStorage", "azstorage:*", "Storage");
        add(presets, "AzStorages", "azstorages:*", "Storage");
        add(presets, "AzBackpack", "azbackpack:*", "Storage");
        add(presets, "AzBackpacks", "azbackpacks:*", "Storage");
        add(presets, "AzMines", "azmines:*", "Prison");
        add(presets, "AzMine", "azmine:*", "Prison");
        add(presets, "AzPrison", "azprison:*", "Prison");
        add(presets, "AzCrates", "azcrates:*", "Crates");
        add(presets, "AzCrate", "azcrate:*", "Crates");
        add(presets, "AzCasino", "azcasino:*", "Gambling");
        add(presets, "AzGamble", "azgamble:*", "Gambling");
        add(presets, "AzRewards", "azrewards:*", "Rewards");
        add(presets, "AzReward", "azreward:*", "Rewards");
        add(presets, "AzKits", "azkits:*", "Rewards");
        add(presets, "AzKit", "azkit:*", "Rewards");
        add(presets, "AzLink", "azlink:*", "Proxy");
        add(presets, "AzAuth", "azauth:*", "Proxy");
        add(presets, "AzLogin", "azlogin:*", "Proxy");
        add(presets, "AzCore", "azcore:*", "Frameworks");
        add(presets, "AzLib", "azlib:*", "Frameworks");
        add(presets, "AzAPI", "azapi:*", "Frameworks");
        add(presets, "AzMenu", "azmenu:*", "Menus");
        add(presets, "AzMenus", "azmenus:*", "Menus");
        add(presets, "AzQuests", "azquests:*", "Quests");
        add(presets, "AzQuest", "azquest:*", "Quests");
        add(presets, "AzChat", "azchat:*", "Chat");
        add(presets, "AzTab", "aztab:*", "UI");
        add(presets, "AzHolograms", "azholograms:*", "Holograms");
        add(presets, "AzNPCs", "aznpcs:*", "NPCs");
        add(presets, "VirtualStorages", "virtualstorages:*", "Storage");
        add(presets, "VirtualStorage", "virtualstorage:*", "Storage");
        add(presets, "Virtual Storage", "virtual_storage:*", "Storage");
        add(presets, "VirtualStoragePlugin", "virtualstorageplugin:*", "Storage");
        add(presets, "VirtualBackpacks", "virtualbackpacks:*", "Storage");
        add(presets, "VirtualBackpack", "virtualbackpack:*", "Storage");
        add(presets, "Virtual Backpack", "virtual_backpack:*", "Storage");
        add(presets, "VirtualBags", "virtualbags:*", "Storage");
        add(presets, "VirtualBag", "virtualbag:*", "Storage");
        add(presets, "VirtualChest", "virtualchest:*", "Storage");
        add(presets, "VirtualChests", "virtualchests:*", "Storage");
        add(presets, "VirtualVault", "virtualvault:*", "Storage");
        add(presets, "VirtualVaults", "virtualvaults:*", "Storage");
        add(presets, "VirtualInventory", "virtualinventory:*", "Storage");
        add(presets, "VirtualInventories", "virtualinventories:*", "Storage");
        add(presets, "VirtualPack", "virtualpack:*", "Storage");
        add(presets, "VirtualPacks", "virtualpacks:*", "Storage");
        add(presets, "PersonalVaults", "personalvaults:*", "Storage");
        add(presets, "PersonalVault", "personalvault:*", "Storage");
        add(presets, "PersonalStorage", "personalstorage:*", "Storage");
        add(presets, "PersonalStorages", "personalstorages:*", "Storage");
        add(presets, "PlayerStorage", "playerstorage:*", "Storage");
        add(presets, "PlayerStorages", "playerstorages:*", "Storage");
        add(presets, "CloudStorage", "cloudstorage:*", "Storage");
        add(presets, "CloudStorages", "cloudstorages:*", "Storage");
        add(presets, "CloudVaults", "cloudvaults:*", "Storage");
        add(presets, "CloudVault", "cloudvault:*", "Storage");
        add(presets, "MineStorage", "minestorage:*", "Storage");
        add(presets, "MineStorages", "minestorages:*", "Storage");
        add(presets, "StoragePlus", "storageplus:*", "Storage");
        add(presets, "StoragePro", "storagepro:*", "Storage");
        add(presets, "StorageSystem", "storagesystem:*", "Storage");
        add(presets, "StorageCore", "storagecore:*", "Storage");
        add(presets, "EasyMines", "easymines:*", "Prison");
        add(presets, "EasyMine", "easymine:*", "Prison");
        add(presets, "EasyPrisonMines", "easyprisonmines:*", "Prison");
        add(presets, "EasyPrisonMine", "easyprisonmine:*", "Prison");
        add(presets, "EasyMineReset", "easyminereset:*", "Prison");
        add(presets, "EasyMineResets", "easymineresets:*", "Prison");
        add(presets, "CataMines", "catamines:*", "Prison");
        add(presets, "CataMine", "catamine:*", "Prison");
        add(presets, "PrisonMine", "prisonmine:*", "Prison");
        add(presets, "PrisonMinesPlus", "prisonminesplus:*", "Prison");
        add(presets, "PrisonMinePlus", "prisonmineplus:*", "Prison");
        add(presets, "PrisonMinesPro", "prisonminespro:*", "Prison");
        add(presets, "PrisonMinePro", "prisonminepro:*", "Prison");
        add(presets, "MineReset", "minereset:*", "Prison");
        add(presets, "MineResets", "mineresets:*", "Prison");
        add(presets, "MineResetLite", "mineresetlite:*", "Prison");
        add(presets, "MineResetPro", "mineresetpro:*", "Prison");
        add(presets, "AutoMineReset", "autominereset:*", "Prison");
        add(presets, "AutoMineResets", "automineresets:*", "Prison");
        add(presets, "AutoMines", "automines:*", "Prison");
        add(presets, "AutoMine", "automine:*", "Prison");
        add(presets, "MineManager", "minemanager:*", "Prison");
        add(presets, "MinesManager", "minesmanager:*", "Prison");
        add(presets, "MineSystem", "minesystem:*", "Prison");
        add(presets, "MinesSystem", "minessystem:*", "Prison");
        add(presets, "MineCore", "minecore:*", "Prison");
        add(presets, "MinesCore", "minescore:*", "Prison");
        add(presets, "BlockMines", "blockmines:*", "Prison");
        add(presets, "BlockMine", "blockmine:*", "Prison");
        add(presets, "AdvancedMines", "advancedmines:*", "Prison");
        add(presets, "AdvancedMine", "advancedmine:*", "Prison");
        add(presets, "UltimateMines", "ultimatemines:*", "Prison");
        add(presets, "UltimateMine", "ultimatemine:*", "Prison");
        add(presets, "MineWorld", "mineworld:*", "Prison");
        add(presets, "MineWorlds", "mineworlds:*", "Prison");
        add(presets, "MineRegion", "mineregion:*", "Prison");
        add(presets, "MineRegions", "mineregions:*", "Prison");
        add(presets, "Container", "container:*", "Storage");
        add(presets, "Containers", "containers:*", "Storage");
        add(presets, "ContainerGUI", "containergui:*", "Storage");
        add(presets, "ContainerMenu", "containermenu:*", "Storage");
        add(presets, "ContainerPlus", "containerplus:*", "Storage");
        add(presets, "ContainerPro", "containerpro:*", "Storage");
        add(presets, "OpenContainer", "opencontainer:*", "Storage");
        add(presets, "OpenContainers", "opencontainers:*", "Storage");
        add(presets, "PortableStorage", "portablestorage:*", "Storage");
        add(presets, "PortableStorages", "portablestorages:*", "Storage");
        add(presets, "PortableChest", "portablechest:*", "Storage");
        add(presets, "PortableChests", "portablechests:*", "Storage");
        add(presets, "PortableVault", "portablevault:*", "Storage");
        add(presets, "PortableVaults", "portablevaults:*", "Storage");
        add(presets, "PortableBackpack", "portablebackpack:*", "Storage");
        add(presets, "PortableBackpacks", "portablebackpacks:*", "Storage");
        add(presets, "PowerfulBackpacks", "powerfulbackpacks:*", "Storage");
        add(presets, "PowerfulBackpack", "powerfulbackpack:*", "Storage");
        add(presets, "MyBackpack", "mybackpack:*", "Storage");
        add(presets, "MyBackpacks", "mybackpacks:*", "Storage");
        add(presets, "My Backpack", "my_backpack:*", "Storage");
        add(presets, "BackpackPlus", "backpackplus:*", "Storage");
        add(presets, "Backpack Plus", "backpack_plus:*", "Storage");
        add(presets, "BetterBackpacks", "betterbackpacks:*", "Storage");
        add(presets, "BetterBackpack", "betterbackpack:*", "Storage");
        add(presets, "AdvancedBackpack", "advancedbackpack:*", "Storage");
        add(presets, "AdvancedBackpacks NBT", "advancedbackpacksnbt:*", "Storage");
        add(presets, "ExpendableBackpacks", "expendablebackpacks:*", "Storage");
        add(presets, "ExpendableBackpack", "expendablebackpack:*", "Storage");
        add(presets, "Minepack", "minepack:*", "Storage");
        add(presets, "MineBackpacks", "minebackpacks:*", "Storage");
        add(presets, "MineBackpack", "minebackpack:*", "Storage");
        add(presets, "SimpleBackpacks", "simplebackpacks:*", "Storage");
        add(presets, "SimpleBackpack", "simplebackpack:*", "Storage");
        add(presets, "EasyBackpacks", "easybackpacks:*", "Storage");
        add(presets, "EasyBackpack", "easybackpack:*", "Storage");
        add(presets, "UltimateBackpacks", "ultimatebackpacks:*", "Storage");
        add(presets, "UltimateBackpack", "ultimatebackpack:*", "Storage");
        add(presets, "EpicBackpacks", "epicbackpacks:*", "Storage");
        add(presets, "EpicBackpack", "epicbackpack:*", "Storage");
        add(presets, "DeluxeBackpacks", "deluxebackpacks:*", "Storage");
        add(presets, "DeluxeBackpack", "deluxebackpack:*", "Storage");
        add(presets, "PremiumBackpacks", "premiumbackpacks:*", "Storage");
        add(presets, "PremiumBackpack", "premiumbackpack:*", "Storage");
        add(presets, "RoyalBackpacks", "royalbackpacks:*", "Storage");
        add(presets, "RoyalBackpack", "royalbackpack:*", "Storage");
        add(presets, "UpgradeableBackpacks", "upgradeablebackpacks:*", "Storage");
        add(presets, "UpgradeableBackpack", "upgradeablebackpack:*", "Storage");
        add(presets, "SmartBackpacks", "smartbackpacks:*", "Storage");
        add(presets, "SmartBackpack", "smartbackpack:*", "Storage");
        add(presets, "BackpackItem", "backpackitem:*", "Storage");
        add(presets, "BackpackItems", "backpackitems:*", "Storage");
        add(presets, "BackpackGUI", "backpackgui:*", "Storage");
        add(presets, "BackpackMenu", "backpackmenu:*", "Storage");
        add(presets, "BackpackSystem", "backpacksystem:*", "Storage");
        add(presets, "BackpacksSystem", "backpackssystem:*", "Storage");
        add(presets, "BackpackCore", "backpackcore:*", "Storage");
        add(presets, "BackpacksCore", "backpackscore:*", "Storage");
        add(presets, "OpenShulker", "openshulker:*", "Storage");
        add(presets, "OpenShulkers", "openshulkers:*", "Storage");
        add(presets, "OpenShulkerBox", "openshulkerbox:*", "Storage");
        add(presets, "OpenShulkerBoxes", "openshulkerboxes:*", "Storage");
        add(presets, "Open Shulker", "open_shulker:*", "Storage");
        add(presets, "Open Shulker Box", "open_shulker_box:*", "Storage");
        add(presets, "ShulkerBackpacks", "shulkerbackpacks:*", "Storage");
        add(presets, "ShulkerBackpack", "shulkerbackpack:*", "Storage");
        add(presets, "Shulker Backpacks", "shulker_backpacks:*", "Storage");
        add(presets, "Shulker Backpack", "shulker_backpack:*", "Storage");
        add(presets, "ShulkerBoxBackpacks", "shulkerboxbackpacks:*", "Storage");
        add(presets, "ShulkerBoxBackpack", "shulkerboxbackpack:*", "Storage");
        add(presets, "ShulkerBoxPlus", "shulkerboxplus:*", "Storage");
        add(presets, "ShulkerPlus", "shulkerplus:*", "Storage");
        add(presets, "EShulkerBox", "eshulkerbox:*", "Storage");
        add(presets, "EShulker", "eshulker:*", "Storage");
        add(presets, "BetterShulker", "bettershulker:*", "Storage");
        add(presets, "BetterShulkers", "bettershulkers:*", "Storage");
        add(presets, "BetterShulkerBoxes", "bettershulkerboxes:*", "Storage");
        add(presets, "BetterShulkerBox", "bettershulkerbox:*", "Storage");
        add(presets, "ShulkerUtils", "shulkerutils:*", "Storage");
        add(presets, "ShulkerUtility", "shulkerutility:*", "Storage");
        add(presets, "ShulkerUtilities", "shulkerutilities:*", "Storage");
        add(presets, "ShulkerOpener", "shulkeropener:*", "Storage");
        add(presets, "ShulkerOpen", "shulkeropen:*", "Storage");
        add(presets, "ShulkerPreview", "shulkerpreview:*", "Storage");
        add(presets, "ShulkerPreviews", "shulkerpreviews:*", "Storage");
        add(presets, "ShulkerViewer", "shulkerviewer:*", "Storage");
        add(presets, "ShulkerView", "shulkerview:*", "Storage");
        add(presets, "ShulkerPacks", "shulkerpacks:*", "Storage");
        add(presets, "ShulkerPack", "shulkerpack:*", "Storage");
        add(presets, "EnderChestPlus", "enderchestplus:*", "Storage");
        add(presets, "EnderChestVault", "enderchestvault:*", "Storage");
        add(presets, "EnderChestVaults", "enderchestvaults:*", "Storage");
        add(presets, "EnderChest", "enderchest:*", "Storage");
        add(presets, "EnderChests", "enderchests:*", "Storage");
        add(presets, "Ender Chest", "ender_chest:*", "Storage");
        add(presets, "Ender Chests", "ender_chests:*", "Storage");
        add(presets, "EnderChestX", "enderchestx:*", "Storage");
        add(presets, "EnderChestPro", "enderchestpro:*", "Storage");
        add(presets, "EnderChestCore", "enderchestcore:*", "Storage");
        add(presets, "EnderChestSystem", "enderchestsystem:*", "Storage");
        add(presets, "EnderStorage", "enderstorage:*", "Storage");
        add(presets, "EnderStorages", "enderstorages:*", "Storage");
        add(presets, "EnderContainer", "endercontainer:*", "Storage");
        add(presets, "EnderContainers", "endercontainers:*", "Storage");
        add(presets, "EnderBackpack", "enderbackpack:*", "Storage");
        add(presets, "EnderBackpacks", "enderbackpacks:*", "Storage");
        add(presets, "EnderPack", "enderpack:*", "Storage");
        add(presets, "EnderPacks", "enderpacks:*", "Storage");
        add(presets, "BetterEnderChest", "betterenderchest:*", "Storage");
        add(presets, "BetterEnderChests", "betterenderchests:*", "Storage");
        add(presets, "AdvancedEnderChest", "advancedenderchest:*", "Storage");
        add(presets, "AdvancedEnderChests", "advancedenderchests:*", "Storage");
        add(presets, "DeluxeEnderChest", "deluxeenderchest:*", "Storage");
        add(presets, "DeluxeEnderChests", "deluxeenderchests:*", "Storage");
        add(presets, "PrivateEnderChest", "privateenderchest:*", "Storage");
        add(presets, "PrivateEnderChests", "privateenderchests:*", "Storage");
        add(presets, "PVaults", "pvaults:*", "Storage");
        add(presets, "PVault", "pvault:*", "Storage");
        add(presets, "PVs", "pvs:*", "Storage");
        add(presets, "PlayerVault", "playervault:*", "Storage");
        add(presets, "PlayerVaultPlus", "playervaultplus:*", "Storage");
        add(presets, "PlayerVaultsPlus", "playervaultsplus:*", "Storage");
        add(presets, "PlayerVaultPro", "playervaultpro:*", "Storage");
        add(presets, "PlayerVaultsPro", "playervaultspro:*", "Storage");
        add(presets, "PlayerVaultsGUI", "playervaultsgui:*", "Storage");
        add(presets, "PlayerVaultGUI", "playervaultgui:*", "Storage");
        add(presets, "PlayerVaultsMenu", "playervaultsmenu:*", "Storage");
        add(presets, "PlayerVaultMenu", "playervaultmenu:*", "Storage");
        add(presets, "PlayerVaultsCore", "playervaultscore:*", "Storage");
        add(presets, "PlayerVaultCore", "playervaultcore:*", "Storage");
        add(presets, "PlayerVaultsSystem", "playervaultssystem:*", "Storage");
        add(presets, "PlayerVaultSystem", "playervaultsystem:*", "Storage");
        add(presets, "BetterPlayerVaults", "betterplayervaults:*", "Storage");
        add(presets, "BetterPlayerVault", "betterplayervault:*", "Storage");
        add(presets, "AdvancedPlayerVaults", "advancedplayervaults:*", "Storage");
        add(presets, "AdvancedPlayerVault", "advancedplayervault:*", "Storage");
        add(presets, "DeluxePlayerVaults", "deluxeplayervaults:*", "Storage");
        add(presets, "DeluxePlayerVault", "deluxeplayervault:*", "Storage");
        add(presets, "InsaneVaults", "insanevaults:*", "Storage");
        add(presets, "InsaneVault", "insanevault:*", "Storage");
        add(presets, "SuperVaults", "supervaults:*", "Storage");
        add(presets, "SuperVault", "supervault:*", "Storage");
        add(presets, "PrivateVaults", "privatevaults:*", "Storage");
        add(presets, "PrivateVault", "privatevault:*", "Storage");
        add(presets, "VaultPlus", "vaultplus:*", "Storage");
        add(presets, "VaultPro", "vaultpro:*", "Storage");
        add(presets, "VaultSystem", "vaultsystem:*", "Storage");
        add(presets, "VaultCore", "vaultcore:*", "Storage");
        add(presets, "VaultGUI", "vaultgui:*", "Storage");
        add(presets, "VaultMenu", "vaultmenu:*", "Storage");
        add(presets, "StorageGUI", "storagegui:*", "Storage");
        add(presets, "StorageMenu", "storagemenu:*", "Storage");
        add(presets, "StorageVaults", "storagevaults:*", "Storage");
        add(presets, "StorageVault", "storagevault:*", "Storage");
        add(presets, "TopMinions", "topminions:*", "Minions");
        add(presets, "TopMinion", "topminion:*", "Minions");
        add(presets, "JetsMinions", "jetsminions:*", "Minions");
        add(presets, "JetsMinion", "jetsminion:*", "Minions");
        add(presets, "JetMinions", "jetminions:*", "Minions");
        add(presets, "JetMinion", "jetminion:*", "Minions");
        add(presets, "MiniMinions", "miniminions:*", "Minions");
        add(presets, "MiniMinion", "miniminion:*", "Minions");
        add(presets, "Minions", "minions:*", "Minions");
        add(presets, "Minion", "minion:*", "Minions");
        add(presets, "MinionPlugin", "minionplugin:*", "Minions");
        add(presets, "MinionsPlugin", "minionsplugin:*", "Minions");
        add(presets, "MinionCore", "minioncore:*", "Minions");
        add(presets, "MinionsCore", "minionscore:*", "Minions");
        add(presets, "MinionSystem", "minionsystem:*", "Minions");
        add(presets, "MinionsSystem", "minionssystem:*", "Minions");
        add(presets, "MinionPlus", "minionplus:*", "Minions");
        add(presets, "MinionsPlus", "minionsplus:*", "Minions");
        add(presets, "MinionPro", "minionpro:*", "Minions");
        add(presets, "MinionsPro", "minionspro:*", "Minions");
        add(presets, "MinionGUI", "miniongui:*", "Minions");
        add(presets, "MinionsGUI", "minionsgui:*", "Minions");
        add(presets, "MinionMenu", "minionmenu:*", "Minions");
        add(presets, "MinionsMenu", "minionsmenu:*", "Minions");
        add(presets, "MinionUpgrades", "minionupgrades:*", "Minions");
        add(presets, "MinionUpgrade", "minionupgrade:*", "Minions");
        add(presets, "MinionStorage", "minionstorage:*", "Minions");
        add(presets, "MinionsStorage", "minionsstorage:*", "Minions");
        add(presets, "MinionChest", "minionchest:*", "Minions");
        add(presets, "MinionChests", "minionchests:*", "Minions");
        add(presets, "MinionShop", "minionshop:*", "Minions");
        add(presets, "MinionsShop", "minionsshop:*", "Minions");
        add(presets, "MinionManager", "minionmanager:*", "Minions");
        add(presets, "MinionsManager", "minionsmanager:*", "Minions");
        add(presets, "SuperiorMinions", "superiorminions:*", "Minions");
        add(presets, "SuperiorMinion", "superiorminion:*", "Minions");
        add(presets, "EpicMinions", "epicminions:*", "Minions");
        add(presets, "EpicMinion", "epicminion:*", "Minions");
        add(presets, "AdvancedMinions", "advancedminions:*", "Minions");
        add(presets, "AdvancedMinion", "advancedminion:*", "Minions");
        add(presets, "UltimateMinions", "ultimateminions:*", "Minions");
        add(presets, "UltimateMinion", "ultimateminion:*", "Minions");
        add(presets, "DeluxeMinions", "deluxeminions:*", "Minions");
        add(presets, "DeluxeMinion", "deluxeminion:*", "Minions");
        add(presets, "BetterMinions", "betterminions:*", "Minions");
        add(presets, "BetterMinion", "betterminion:*", "Minions");
        add(presets, "SimpleMinions", "simpleminions:*", "Minions");
        add(presets, "SimpleMinion", "simpleminion:*", "Minions");
        add(presets, "SkyblockMinions", "skyblockminions:*", "Minions");
        add(presets, "SkyblockMinion", "skyblockminion:*", "Minions");
        add(presets, "HypixelMinions", "hypixelminions:*", "Minions");
        add(presets, "HypixelMinion", "hypixelminion:*", "Minions");
        add(presets, "Robots", "robots:*", "Minions");
        add(presets, "Robot", "robot:*", "Minions");
        add(presets, "Worker", "worker:*", "Minions");
        add(presets, "Workers", "workers:*", "Minions");
        add(presets, "Helpers", "helpers:*", "Minions");
        add(presets, "Helper", "helper:*", "Minions");
        add(presets, "AutoFarmers", "autofarmers:*", "Minions");
        add(presets, "AutoFarmer", "autofarmer:*", "Minions");
        add(presets, "AutoMiners", "autominers:*", "Minions");
        add(presets, "AutoMiner", "autominer:*", "Minions");
        add(presets, "MinerMinions", "minerminions:*", "Minions");
        add(presets, "MinerMinion", "minerminion:*", "Minions");
        add(presets, "FarmerMinions", "farmerminions:*", "Minions");
        add(presets, "FarmerMinion", "farmerminion:*", "Minions");
        add(presets, "Vaults Fabricate", "vaultsfabricate:*", "Storage");
        add(presets, "Vault Fabricate", "vaultfabricate:*", "Storage");
        add(presets, "VaultsFabricated", "vaultsfabricated:*", "Storage");
        add(presets, "FabricatedVaults", "fabricatedvaults:*", "Storage");
        add(presets, "FabricatedVault", "fabricatedvault:*", "Storage");
        add(presets, "FabricateVaults", "fabricatevaults:*", "Storage");
        add(presets, "FabricateVault", "fabricatevault:*", "Storage");
        add(presets, "BsruEnderChest", "bsruenderchest:*", "Storage");
        add(presets, "BSRUEnderChest", "bsru_enderchest:*", "Storage");
        add(presets, "BSRU Ender Chest", "bsru_ender_chest:*", "Storage");
        add(presets, "TH Backpacks", "th_backpacks:*", "Storage");
        add(presets, "TH Backpack", "th_backpack:*", "Storage");
        add(presets, "THBackpacks", "thbackpacks:*", "Storage");
        add(presets, "THBackpack", "thbackpack:*", "Storage");
        add(presets, "Kix Simple Backpacks", "kixsimplebackpacks:*", "Storage");
        add(presets, "Kix Simple Backpack", "kixsimplebackpack:*", "Storage");
        add(presets, "Kix's Simple Backpacks", "kixssimplebackpacks:*", "Storage");
        add(presets, "Kix Backpacks", "kixbackpacks:*", "Storage");
        add(presets, "Kix Backpack", "kixbackpack:*", "Storage");
        add(presets, "Kix", "kix:*", "Storage");
        add(presets, "BundleBack", "bundleback:*", "Storage");
        add(presets, "BundleBackpack", "bundlebackpack:*", "Storage");
        add(presets, "BundleBackpacks", "bundlebackpacks:*", "Storage");
        add(presets, "BundleBag", "bundlebag:*", "Storage");
        add(presets, "BundleBags", "bundlebags:*", "Storage");
        add(presets, "BundleStorage", "bundlestorage:*", "Storage");
        add(presets, "BundleStorages", "bundlestorages:*", "Storage");
        add(presets, "BundleChest", "bundlechest:*", "Storage");
        add(presets, "BundleChests", "bundlechests:*", "Storage");
        add(presets, "BundleVault", "bundlevault:*", "Storage");
        add(presets, "BundleVaults", "bundlevaults:*", "Storage");
        add(presets, "EasyShulkers", "easyshulkers:*", "Storage");
        add(presets, "EasyShulker", "easyshulker:*", "Storage");
        add(presets, "EasyShulkerBox", "easyshulkerbox:*", "Storage");
        add(presets, "EasyShulkerBoxes", "easyshulkerboxes:*", "Storage");
        add(presets, "Xiaoklunar Shulker", "xiaoklunarshulker:*", "Storage");
        add(presets, "Xiaoklunar Shulkers", "xiaoklunarshulkers:*", "Storage");
        add(presets, "XiaoklunarShulker", "xiaoklunar_shulker:*", "Storage");
        add(presets, "XiaoklunarShulkers", "xiaoklunar_shulkers:*", "Storage");
        add(presets, "Xiaoklunar", "xiaoklunar:*", "Storage");
        add(presets, "Bigger Chest", "biggerchest:*", "Storage");
        add(presets, "Bigger Chests", "biggerchests:*", "Storage");
        add(presets, "BiggerChest", "bigger_chest:*", "Storage");
        add(presets, "BiggerChests", "bigger_chests:*", "Storage");
        add(presets, "LargeChest", "largechest:*", "Storage");
        add(presets, "LargeChests", "largechests:*", "Storage");
        add(presets, "HugeChest", "hugechest:*", "Storage");
        add(presets, "HugeChests", "hugechests:*", "Storage");
        add(presets, "ExpandedChest", "expandedchest:*", "Storage");
        add(presets, "ExpandedChests", "expandedchests:*", "Storage");
        add(presets, "ExtendedChest", "extendedchest:*", "Storage");
        add(presets, "ExtendedChests", "extendedchests:*", "Storage");
        add(presets, "DeathChestPro", "deathchestpro:*", "Storage");
        add(presets, "DeathChestPlus", "deathchestplus:*", "Storage");
        add(presets, "DeathChestX", "deathchestx:*", "Storage");
        add(presets, "DeathChestReloaded", "deathchestreloaded:*", "Storage");
        add(presets, "DeathChestSystem", "deathchestsystem:*", "Storage");
        add(presets, "DeathChestsSystem", "deathchestssystem:*", "Storage");
        add(presets, "GraveChest", "gravechest:*", "Storage");
        add(presets, "GraveChests", "gravechests:*", "Storage");
        add(presets, "CorpseChest", "corpsechest:*", "Storage");
        add(presets, "CorpseChests", "corpsechests:*", "Storage");
        add(presets, "RoseStacker GUI", "rosestackergui:*", "Storage");
        add(presets, "RoseStacker Menu", "rosestackermenu:*", "Storage");
        add(presets, "RoseStacker Stacker", "rosestackerstacker:*", "Storage");
        add(presets, "EnderEX", "enderex:*", "Storage");
        add(presets, "EnderEx", "ender_ex:*", "Storage");
        add(presets, "EnderEX Storage", "enderexstorage:*", "Storage");
        add(presets, "EnderEX Chest", "enderexchest:*", "Storage");
        add(presets, "EnderEX Vault", "enderexvault:*", "Storage");
        add(presets, "EnderExpansion", "enderexpansion:*", "Storage");
        add(presets, "EnderExtended", "enderextended:*", "Storage");
        add(presets, "EnderExtender", "enderextender:*", "Storage");
        add(presets, "EnderPlus", "enderplus:*", "Storage");
        add(presets, "EnderPro", "enderpro:*", "Storage");
        add(presets, "GUIContainers", "guicontainers:*", "Storage");
        add(presets, "GUIContainer", "guicontainer:*", "Storage");
        add(presets, "VirtualContainers", "virtualcontainers:*", "Storage");
        add(presets, "VirtualContainer", "virtualcontainer:*", "Storage");
        add(presets, "RemoteStorage", "remotestorage:*", "Storage");
        add(presets, "RemoteStorages", "remotestorages:*", "Storage");
        add(presets, "RemoteChest", "remotechest:*", "Storage");
        add(presets, "RemoteChests", "remotechests:*", "Storage");
        add(presets, "RemoteVault", "remotevault:*", "Storage");
        add(presets, "RemoteVaults", "remotevaults:*", "Storage");
        add(presets, "AnywhereStorage", "anywherestorage:*", "Storage");
        add(presets, "AnywhereChest", "anywherechest:*", "Storage");
        add(presets, "AnywhereVault", "anywherevault:*", "Storage");
        add(presets, "PortableShulker", "portableshulker:*", "Storage");
        add(presets, "PortableShulkers", "portableshulkers:*", "Storage");
        add(presets, "PortableShulkerBox", "portableshulkerbox:*", "Storage");
        add(presets, "PortableShulkerBoxes", "portableshulkerboxes:*", "Storage");
        add(presets, "PocketChest", "pocketchest:*", "Storage");
        add(presets, "PocketChests", "pocketchests:*", "Storage");
        add(presets, "PocketStorage", "pocketstorage:*", "Storage");
        add(presets, "PocketVault", "pocketvault:*", "Storage");
        add(presets, "PocketVaults", "pocketvaults:*", "Storage");
        add(presets, "PocketBackpack", "pocketbackpack:*", "Storage");
        add(presets, "PocketBackpacks", "pocketbackpacks:*", "Storage");
        add(presets, "PersonalChest", "personalchest:*", "Storage");
        add(presets, "PersonalChests", "personalchests:*", "Storage");
        add(presets, "PersonalEnderChest", "personalenderchest:*", "Storage");
        add(presets, "PersonalEnderChests", "personalenderchests:*", "Storage");
        add(presets, "CustomChest", "customchest:*", "Storage");
        add(presets, "CustomChests", "customchests:*", "Storage");
        add(presets, "CustomVault", "customvault:*", "Storage");
        add(presets, "CustomVaults", "customvaults:*", "Storage");
        add(presets, "CustomBackpack", "custombackpack:*", "Storage");
        add(presets, "CustomBackpacks", "custombackpacks:*", "Storage");
        add(presets, "MinionBackpack", "minionbackpack:*", "Minions");
        add(presets, "MinionBackpacks", "minionbackpacks:*", "Minions");
        add(presets, "MinionChestPlus", "minionchestplus:*", "Minions");
        add(presets, "MinionVault", "minionvault:*", "Minions");
        add(presets, "MinionVaults", "minionvaults:*", "Minions");
        add(presets, "MinionContainer", "minioncontainer:*", "Minions");
        add(presets, "MinionContainers", "minioncontainers:*", "Minions");
        add(presets, "VShulker", "vshulker:*", "Storage");
        add(presets, "VShulkers", "vshulkers:*", "Storage");
        add(presets, "VShulkerBox", "vshulkerbox:*", "Storage");
        add(presets, "VShulkerBoxes", "vshulkerboxes:*", "Storage");
        add(presets, "VirtualShulker", "virtualshulker:*", "Storage");
        add(presets, "VirtualShulkers", "virtualshulkers:*", "Storage");
        add(presets, "VirtualShulkerBox", "virtualshulkerbox:*", "Storage");
        add(presets, "VirtualShulkerBoxes", "virtualshulkerboxes:*", "Storage");
        add(presets, "DonutEC", "donutec:*", "Storage");
        add(presets, "Donut EChest", "donutechest:*", "Storage");
        add(presets, "Donut EnderChest", "donutenderchest:*", "Storage");
        add(presets, "Donut EnderChests", "donutenderchests:*", "Storage");
        add(presets, "Donut Ender Chest", "donut_ender_chest:*", "Storage");
        add(presets, "Donut Ender Chests", "donut_ender_chests:*", "Storage");
        add(presets, "Donut Ender Storage", "donutenderstorage:*", "Storage");
        add(presets, "Donut Storage", "donutstorage:*", "Storage");
        add(presets, "Donut Vault", "donutvault:*", "Storage");
        add(presets, "Donut Vaults", "donutvaults:*", "Storage");
        add(presets, "Donut Backpack", "donutbackpack:*", "Storage");
        add(presets, "Donut Backpacks", "donutbackpacks:*", "Storage");
        add(presets, "GUI Family", "gui*", "Menus");
        add(presets, "Menu Family", "menu*", "Menus");
        add(presets, "Inventory Family", "inventory*", "Menus");
        add(presets, "Inventory Namespace", "inventory:*", "Menus");
        add(presets, "Chest Family", "chest*", "Storage");
        add(presets, "Chest Namespace", "chest:*", "Storage");
        add(presets, "OpenInv Family", "openinv*", "Storage");
        add(presets, "OpenInventory Family", "openinventory*", "Storage");
        add(presets, "ChestCommands", "chestcommands:*", "Menus");
        add(presets, "ChestCommand", "chestcommand:*", "Menus");
        add(presets, "ChestCommandsPlus", "chestcommandsplus:*", "Menus");
        add(presets, "ChestCommandsPro", "chestcommandspro:*", "Menus");
        add(presets, "ChestGUI", "chestgui:*", "Menus");
        add(presets, "ChestGUIs", "chestguis:*", "Menus");
        add(presets, "ChestMenu", "chestmenu:*", "Menus");
        add(presets, "ChestMenus", "chestmenus:*", "Menus");
        add(presets, "ChestInventory", "chestinventory:*", "Menus");
        add(presets, "ChestInventories", "chestinventories:*", "Menus");
        add(presets, "ZMenu", "zmenu:*", "Menus");
        add(presets, "ZMenus", "zmenus:*", "Menus");
        add(presets, "ZMenuPlus", "zmenuplus:*", "Menus");
        add(presets, "ZMenuPro", "zmenupro:*", "Menus");
        add(presets, "ZMenuGui", "zmenugui:*", "Menus");
        add(presets, "TrMenu", "trmenu:*", "Menus");
        add(presets, "TrMenus", "trmenus:*", "Menus");
        add(presets, "TrMenuPlus", "trmenuplus:*", "Menus");
        add(presets, "TrMenuPro", "trmenupro:*", "Menus");
        add(presets, "CustomGUI", "customgui:*", "Menus");
        add(presets, "CustomGUIs", "customguis:*", "Menus");
        add(presets, "CustomGuiPlus", "customguiplus:*", "Menus");
        add(presets, "CustomGuiPro", "customguipro:*", "Menus");
        add(presets, "CustomMenu", "custommenu:*", "Menus");
        add(presets, "CustomMenus", "custommenus:*", "Menus");
        add(presets, "CustomMenuPlus", "custommenuplus:*", "Menus");
        add(presets, "CustomMenuPro", "custommenupro:*", "Menus");
        add(presets, "CustomInventory", "custominventory:*", "Menus");
        add(presets, "CustomInventories", "custominventories:*", "Menus");
        add(presets, "InventoryMenu", "inventorymenu:*", "Menus");
        add(presets, "InventoryMenus", "inventorymenus:*", "Menus");
        add(presets, "InventoryGUIPlus", "inventoryguiplus:*", "Menus");
        add(presets, "InventoryGUIPro", "inventoryguipro:*", "Menus");
        add(presets, "InventoryMenuPlus", "inventorymenuplus:*", "Menus");
        add(presets, "InventoryMenuPro", "inventorymenupro:*", "Menus");
        add(presets, "GUIPlus", "guiplus:*", "Menus");
        add(presets, "GUIPro", "guipro:*", "Menus");
        add(presets, "GUICore", "guicore:*", "Menus");
        add(presets, "GUISystem", "guisystem:*", "Menus");
        add(presets, "GUIMenu", "guimenu:*", "Menus");
        add(presets, "GUIMenus", "guimenus:*", "Menus");
        add(presets, "BetterGUI", "bettergui:*", "Menus");
        add(presets, "BetterGUIs", "betterguis:*", "Menus");
        add(presets, "BetterMenu", "bettermenu:*", "Menus");
        add(presets, "BetterMenus", "bettermenus:*", "Menus");
        add(presets, "SimpleGUI", "simplegui:*", "Menus");
        add(presets, "SimpleGUIs", "simpleguis:*", "Menus");
        add(presets, "SimpleMenu", "simplemenu:*", "Menus");
        add(presets, "SimpleMenus", "simplemenus:*", "Menus");
        add(presets, "EasyGUI", "easygui:*", "Menus");
        add(presets, "EasyGUIs", "easyguis:*", "Menus");
        add(presets, "EasyMenu", "easymenu:*", "Menus");
        add(presets, "EasyMenus", "easymenus:*", "Menus");
        add(presets, "AdvancedGUI", "advancedgui:*", "Menus");
        add(presets, "AdvancedGUIs", "advancedguis:*", "Menus");
        add(presets, "AdvancedMenu", "advancedmenu:*", "Menus");
        add(presets, "AdvancedMenus", "advancedmenus:*", "Menus");
        add(presets, "DeluxeGUI", "deluxegui:*", "Menus");
        add(presets, "DeluxeGUIs", "deluxeguis:*", "Menus");
        add(presets, "UltraGUI", "ultragui:*", "Menus");
        add(presets, "UltraGUIs", "ultraguis:*", "Menus");
        add(presets, "UltraMenu", "ultramenu:*", "Menus");
        add(presets, "UltraMenus", "ultramenus:*", "Menus");
        add(presets, "PremiumGUI", "premiumgui:*", "Menus");
        add(presets, "PremiumGUIs", "premiumguis:*", "Menus");
        add(presets, "PremiumMenu", "premiummenu:*", "Menus");
        add(presets, "PremiumMenus", "premiummenus:*", "Menus");
        add(presets, "SmartMenus", "smartmenus:*", "Menus");
        add(presets, "SmartMenu", "smartmenu:*", "Menus");
        add(presets, "MenuGUI", "menugui:*", "Menus");
        add(presets, "MenuGUIs", "menuguis:*", "Menus");
        add(presets, "MenuBuilder", "menubuilder:*", "Menus");
        add(presets, "GUIBuilder", "guibuilder:*", "Menus");
        add(presets, "InventoryBuilder", "inventorybuilder:*", "Menus");
        add(presets, "MenuEngine", "menuengine:*", "Menus");
        add(presets, "GUIEngine", "guiengine:*", "Menus");
        add(presets, "InventoryEngine", "inventoryengine:*", "Menus");
        add(presets, "MenuAPI", "menuapi:*", "Menus");
        add(presets, "GUIAPI", "guiapi:*", "Menus");
        add(presets, "InventoryAPI", "inventoryapi:*", "Menus");
        add(presets, "NoobShulkerBoxes", "noobshulkerboxes:*", "Storage");
        add(presets, "NoobShulkerBox", "noobshulkerbox:*", "Storage");
        add(presets, "NoobShulker", "noobshulker:*", "Storage");
        add(presets, "NoobShulkers", "noobshulkers:*", "Storage");
        add(presets, "FastShulker", "fastshulker:*", "Storage");
        add(presets, "FastShulkers", "fastshulkers:*", "Storage");
        add(presets, "FastShulkerBox", "fastshulkerbox:*", "Storage");
        add(presets, "FastShulkerBoxes", "fastshulkerboxes:*", "Storage");
        add(presets, "UltimateShulker", "ultimateshulker:*", "Storage");
        add(presets, "UltimateShulkers", "ultimateshulkers:*", "Storage");
        add(presets, "UltimateShulkerBox", "ultimateshulkerbox:*", "Storage");
        add(presets, "UltimateShulkerBoxes", "ultimateshulkerboxes:*", "Storage");
        add(presets, "SuperShulker", "supershulker:*", "Storage");
        add(presets, "SuperShulkers", "supershulkers:*", "Storage");
        add(presets, "SmartShulker", "smartshulker:*", "Storage");
        add(presets, "SmartShulkers", "smartshulkers:*", "Storage");
        add(presets, "ShulkerFast", "shulkerfast:*", "Storage");
        add(presets, "ShulkersFast", "shulkersfast:*", "Storage");
        add(presets, "AH Family", "ah*", "Auctions");
        add(presets, "Auction Family", "auction*", "Auctions");
        add(presets, "AuctionHouse Family", "auctionhouse*", "Auctions");
        add(presets, "ZelAuction", "zelauction:*", "Auctions");
        add(presets, "ZelAuctionHouse", "zelauctionhouse:*", "Auctions");
        add(presets, "Zel Auction", "zel_auction:*", "Auctions");
        add(presets, "Zel Auction House", "zel_auction_house:*", "Auctions");
        add(presets, "ZelAH", "zelah:*", "Auctions");
        add(presets, "Zel AH", "zel_ah:*", "Auctions");
        add(presets, "OutAuction", "outauction:*", "Auctions");
        add(presets, "OutAuctions", "outauctions:*", "Auctions");
        add(presets, "OutAuctionHouse", "outauctionhouse:*", "Auctions");
        add(presets, "Out Auction", "out_auction:*", "Auctions");
        add(presets, "Out Auction House", "out_auction_house:*", "Auctions");
        add(presets, "AuctionHousePlus", "auctionhouseplus:*", "Auctions");
        add(presets, "AuctionHouse+", "auctionhouse_plus:*", "Auctions");
        add(presets, "AuctionHousePlugin", "auctionhouseplugin:*", "Auctions");
        add(presets, "Auction House Plugin", "auction_house_plugin:*", "Auctions");
        add(presets, "Auction-House", "auction-house:*", "Auctions");
        add(presets, "Auction House", "auction_house:*", "Auctions");
        add(presets, "AuctionHouseRewrite", "auctionhouserewrite:*", "Auctions");
        add(presets, "Auction House Rewrite", "auction_house_rewrite:*", "Auctions");
        add(presets, "AuctionHouseReloaded", "auctionhousereloaded:*", "Auctions");
        add(presets, "AuctionHouseRevived", "auctionhouserevived:*", "Auctions");
        add(presets, "AuctionHouseModern", "auctionhousemodern:*", "Auctions");
        add(presets, "AuctionHouseLite", "auctionhouselite:*", "Auctions");
        add(presets, "AuctionHouseFree", "auctionhousefree:*", "Auctions");
        add(presets, "AuctionHousePremium", "auctionhousepremium:*", "Auctions");
        add(presets, "AuctionHouseRedis", "auctionhouseredis:*", "Auctions");
        add(presets, "AuctionRedis", "auctionredis:*", "Auctions");
        add(presets, "AuctionHouseIlius", "auctionhouseilius:*", "Auctions");
        add(presets, "KiranAuctionHouse", "kiranauctionhouse:*", "Auctions");
        add(presets, "KiranAuction", "kiranauction:*", "Auctions");
        add(presets, "FAuction", "fauction:*", "Auctions");
        add(presets, "FAuctions", "fauctions:*", "Auctions");
        add(presets, "FAuctionHouse", "fauctionhouse:*", "Auctions");
        add(presets, "F Auction", "f_auction:*", "Auctions");
        add(presets, "F Auction House", "f_auction_house:*", "Auctions");
        add(presets, "AdvancedAuctionHouse", "advancedauctionhouse:*", "Auctions");
        add(presets, "Advanced Auction House", "advanced_auction_house:*", "Auctions");
        add(presets, "UltimateAuctionHouse", "ultimateauctionhouse:*", "Auctions");
        add(presets, "Ultimate Auction House", "ultimate_auction_house:*", "Auctions");
        add(presets, "TheUltimateAuctionHouse", "theultimateauctionhouse:*", "Auctions");
        add(presets, "The Ultimate Auction House", "the_ultimate_auction_house:*", "Auctions");
        add(presets, "FateAuctionHouse", "fateauctionhouse:*", "Auctions");
        add(presets, "FateAuction", "fateauction:*", "Auctions");
        add(presets, "FADAH", "fadah:*", "Auctions");
        add(presets, "FADAuctionHouse", "fadauctionhouse:*", "Auctions");
        add(presets, "FAD Auction House", "fad_auction_house:*", "Auctions");
        add(presets, "ZAuctionHouse Alt", "z_auctionhouse:*", "Auctions");
        add(presets, "Z Auction House", "z_auction_house:*", "Auctions");
        add(presets, "Z Auctions", "zauctions:*", "Auctions");
        add(presets, "zAuctionHousePro", "zauctionhousepro:*", "Auctions");
        add(presets, "zAuctionHousePlus", "zauctionhouseplus:*", "Auctions");
        add(presets, "AzAuctionHouse Plus", "azauctionhouseplus:*", "Auctions");
        add(presets, "AzAuctionHouse Pro", "azauctionhousepro:*", "Auctions");
        add(presets, "AxAuctionHouse", "axauctionhouse:*", "Auctions");
        add(presets, "AxAuction", "axauction:*", "Auctions");
        add(presets, "AxAuctionHouse Plus", "axauctionhouseplus:*", "Auctions");
        add(presets, "AxAuctionHouse Pro", "axauctionhousepro:*", "Auctions");
        add(presets, "NexusAuction", "nexusauction:*", "Auctions");
        add(presets, "NexusAuctions", "nexusauctions:*", "Auctions");
        add(presets, "NexusAH", "nexusah:*", "Auctions");
        add(presets, "Nexus Auction House", "nexus_auction_house:*", "Auctions");
        add(presets, "PlayerAuctionHouse", "playerauctionhouse:*", "Auctions");
        add(presets, "Player Auction House", "player_auction_house:*", "Auctions");
        add(presets, "PlayerAH", "playerah:*", "Auctions");
        add(presets, "Player AH", "player_ah:*", "Auctions");
        add(presets, "OlziePlayerAuctions", "olzieplayerauctions:*", "Auctions");
        add(presets, "Olzie Auctions", "olzieauctions:*", "Auctions");
        add(presets, "CrazyAuctionHouse", "crazyauctionhouse:*", "Auctions");
        add(presets, "Crazy Auction House", "crazy_auction_house:*", "Auctions");
        add(presets, "CrazyAuction", "crazyauction:*", "Auctions");
        add(presets, "Crazy Auction", "crazy_auction:*", "Auctions");
        add(presets, "ExcellentAuction", "excellentauction:*", "Auctions");
        add(presets, "ExcellentAuctionHouse Plus", "excellentauctionhouseplus:*", "Auctions");
        add(presets, "ExcellentAuctionHouse Pro", "excellentauctionhousepro:*", "Auctions");
        add(presets, "ExcellentAH", "excellentah:*", "Auctions");
        add(presets, "AuctionMasterPro", "auctionmasterpro:*", "Auctions");
        add(presets, "AuctionMasterPlus", "auctionmasterplus:*", "Auctions");
        add(presets, "AuctionMasters", "auctionmasters:*", "Auctions");
        add(presets, "AuctionGUIs", "auctionguis:*", "Auctions");
        add(presets, "AuctionGUIPro", "auctionguipro:*", "Auctions");
        add(presets, "AuctionGUIPlus Alt", "auctiongui_plus:*", "Auctions");
        add(presets, "AuctionMenu", "auctionmenu:*", "Auctions");
        add(presets, "AuctionMenus", "auctionmenus:*", "Auctions");
        add(presets, "AuctionMenuPlus", "auctionmenuplus:*", "Auctions");
        add(presets, "AuctionMenuPro", "auctionmenupro:*", "Auctions");
        add(presets, "AuctionSystem", "auctionsystem:*", "Auctions");
        add(presets, "AuctionsSystem", "auctionssystem:*", "Auctions");
        add(presets, "AuctionCore", "auctioncore:*", "Auctions");
        add(presets, "AuctionsCore", "auctionscore:*", "Auctions");
        add(presets, "AuctionAPI", "auctionapi:*", "Auctions");
        add(presets, "AuctionsAPI", "auctionsapi:*", "Auctions");
        add(presets, "AuctionEngine", "auctionengine:*", "Auctions");
        add(presets, "AuctionMarket", "auctionmarket:*", "Auctions");
        add(presets, "AuctionMarkets", "auctionmarkets:*", "Auctions");
        add(presets, "AuctionMarketplace", "auctionmarketplace:*", "Auctions");
        add(presets, "AuctionMarketPlace", "auctionmarket_place:*", "Auctions");
        add(presets, "AuctionListings", "auctionlistings:*", "Auctions");
        add(presets, "AuctionListing", "auctionlisting:*", "Auctions");
        add(presets, "ListingAuction", "listingauction:*", "Auctions");
        add(presets, "ListingsAuction", "listingsauction:*", "Auctions");
        add(presets, "ItemAuction", "itemauction:*", "Auctions");
        add(presets, "ItemAuctions", "itemauctions:*", "Auctions");
        add(presets, "ItemAuctionHouse", "itemauctionhouse:*", "Auctions");
        add(presets, "ItemAH", "itemah:*", "Auctions");
        add(presets, "SellAuction", "sellauction:*", "Auctions");
        add(presets, "SellAuctions", "sellauctions:*", "Auctions");
        add(presets, "BidAuction", "bidauction:*", "Auctions");
        add(presets, "BidAuctions", "bidauctions:*", "Auctions");
        add(presets, "Bidding", "bidding:*", "Auctions");
        add(presets, "Bids", "bids:*", "Auctions");
        add(presets, "Bid", "bid:*", "Auctions");
        add(presets, "MarketplaceAuctions", "marketplaceauctions:*", "Auctions");
        add(presets, "MarketplaceAuction", "marketplaceauction:*", "Auctions");
        add(presets, "MarketAuctionHouse", "marketauctionhouse:*", "Auctions");
        add(presets, "MarketAH", "marketah:*", "Auctions");
        add(presets, "BazaarAuction", "bazaarauction:*", "Auctions");
        add(presets, "BazaarAuctions", "bazaarauctions:*", "Auctions");
        add(presets, "BazaarAH", "bazaarah:*", "Auctions");
        add(presets, "TradeAuction", "tradeauction:*", "Auctions");
        add(presets, "TradeAuctions", "tradeauctions:*", "Auctions");
        add(presets, "TradeAH", "tradeah:*", "Auctions");
        add(presets, "GlobalAuction", "globalauction:*", "Auctions");
        add(presets, "GlobalAuctions", "globalauctions:*", "Auctions");
        add(presets, "GlobalAuctionHouse", "globalauctionhouse:*", "Auctions");
        add(presets, "GlobalMarket", "globalmarket:*", "Auctions");
        add(presets, "GlobalMarketPlus", "globalmarketplus:*", "Auctions");
        add(presets, "GlobalMarketChestShop", "globalmarketchestshop:*", "Auctions");
        add(presets, "ServerAuction", "serverauction:*", "Auctions");
        add(presets, "ServerAuctions", "serverauctions:*", "Auctions");
        add(presets, "ServerAuctionHouse", "serverauctionhouse:*", "Auctions");
        add(presets, "CommunityAuctionHouse", "communityauctionhouse:*", "Auctions");
        add(presets, "CommunityAH", "communityah:*", "Auctions");
        add(presets, "LiteAuction", "liteauction:*", "Auctions");
        add(presets, "LiteAuctions", "liteauctions:*", "Auctions");
        add(presets, "SimpleAuction", "simpleauction:*", "Auctions");
        add(presets, "SimpleAuctions", "simpleauctions:*", "Auctions");
        add(presets, "SimpleAuctionHouse", "simpleauctionhouse:*", "Auctions");
        add(presets, "BetterAuctionHouse", "betterauctionhouse:*", "Auctions");
        add(presets, "BetterAuctions", "betterauctions:*", "Auctions");
        add(presets, "BetterAH", "betterah:*", "Auctions");
        add(presets, "EasyAuction", "easyauction:*", "Auctions");
        add(presets, "EasyAuctions", "easyauctions:*", "Auctions");
        add(presets, "EasyAuctionHouse", "easyauctionhouse:*", "Auctions");
        add(presets, "DeluxeAuction", "deluxeauction:*", "Auctions");
        add(presets, "DeluxeAuctions", "deluxeauctions:*", "Auctions");
        add(presets, "DeluxeAuctionHouse", "deluxeauctionhouse:*", "Auctions");
        add(presets, "DeluxeAuctionsRedis", "deluxeauctionsredis:*", "Auctions");
        add(presets, "Deluxe Auction Redis", "deluxe_auction_redis:*", "Auctions");
        add(presets, "Deluxe Auctions Redis", "deluxe_auctions_redis:*", "Auctions");
        add(presets, "PremiumAuction", "premiumauction:*", "Auctions");
        add(presets, "PremiumAuctions", "premiumauctions:*", "Auctions");
        add(presets, "PremiumAuctionHouse", "premiumauctionhouse:*", "Auctions");
        add(presets, "UltraAuction", "ultraauction:*", "Auctions");
        add(presets, "UltraAuctions", "ultraauctions:*", "Auctions");
        add(presets, "UltraAuctionHouse", "ultraauctionhouse:*", "Auctions");
        add(presets, "RoyalAuction", "royalauction:*", "Auctions");
        add(presets, "RoyalAuctions", "royalauctions:*", "Auctions");
        add(presets, "RoyalAuctionHouse", "royalauctionhouse:*", "Auctions");
        add(presets, "SupremeAuction", "supremeauction:*", "Auctions");
        add(presets, "SupremeAuctions", "supremeauctions:*", "Auctions");
        add(presets, "SupremeAuctionHouse", "supremeauctionhouse:*", "Auctions");
        add(presets, "HuskAuction", "huskauction:*", "Auctions");
        add(presets, "HuskAuctionHouse", "huskauctionhouse:*", "Auctions");
        add(presets, "HuskAH", "huskah:*", "Auctions");
        return Collections.unmodifiableList(presets);
    }

    private static void add(List<Preset> presets, String label, String pattern, String group) {
        String normalized = normalizePattern(pattern);
        PresetKind kind = inferKind(normalized);
        String evidence = kind == PresetKind.TEMPLATE ? "Template only; no exact public channel proof." : "";
        addPresetRow(presets, label, normalized, group, kind, evidence, deriveTemplatePrefix(normalized));
    }

    private static void addExact(List<Preset> presets, String label, String pattern, String group, String evidence) {
        addPresetRow(presets, label, normalizePattern(pattern), group, PresetKind.EXACT, evidence, "");
    }

    private static void addCore(List<Preset> presets, String label, String pattern, String group, String evidence) {
        addPresetRow(presets, label, normalizePattern(pattern), group, PresetKind.CORE, evidence, "");
    }

    private static void addPresetRow(List<Preset> presets, String label, String normalized, String group,
                                     PresetKind kind, String evidence, String templatePrefix) {
        String key = ruleKey(normalized);
        for (Preset preset : presets) {
            if (preset.key().equals(key)) return;
        }
        PresetKind safeKind = kind == null ? inferKind(normalized) : kind;
        String safeEvidence = evidence == null ? "" : evidence;
        String safeTemplate = templatePrefix == null ? "" : templatePrefix;
        boolean invalidExact = safeKind == PresetKind.EXACT
            && (safeEvidence.isBlank()
                || normalized.indexOf('*') >= 0
                || !AutismPayloadChannelRegistrations.isRegisterableChannel(normalized));
        if (invalidExact) {
            safeKind = PresetKind.UNVERIFIED;
            safeTemplate = safeTemplate.isBlank() ? deriveTemplatePrefix(normalized) : safeTemplate;
            safeEvidence = safeEvidence.isBlank() ? "Exact channel proof missing; kept as editable template." : safeEvidence;
        }
        presets.add(new Preset(label, normalized, Direction.ANY, group, safeKind, safeEvidence, safeTemplate));
    }
}
