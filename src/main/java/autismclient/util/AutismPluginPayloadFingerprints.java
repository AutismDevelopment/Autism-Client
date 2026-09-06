package autismclient.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutismPluginPayloadFingerprints {
    private static final int MAX_CHANNELS_PER_SERVER = 512;
    private static final Pattern CHANNEL_TOKEN = Pattern.compile("(?i)\\b[a-z0-9_.-]{2,64}:[a-z0-9_./-]{1,128}\\b");

    private static final Map<String, State> STATES = new HashMap<>();

    private static final Map<String, String> NAMESPACE_ALIASES = Map.ofEntries(
        Map.entry("vv", "ViaVersion"),
        Map.entry("viaversion", "ViaVersion"),
        Map.entry("viabackwards", "ViaBackwards"),
        Map.entry("viarewind", "ViaRewind"),
        Map.entry("bungeecord", "BungeeCord"),
        Map.entry("velocity", "Velocity"),
        Map.entry("geyser", "Geyser"),
        Map.entry("floodgate", "Floodgate"),
        Map.entry("worldedit", "WorldEdit"),
        Map.entry("fawe", "FAWE"),
        Map.entry("fastasyncworldedit", "FastAsyncWorldEdit"),
        Map.entry("worldguard", "WorldGuard"),
        Map.entry("coreprotect", "CoreProtect"),
        Map.entry("claimmaps", "ClaimMessenger"),
        Map.entry("claimmessenger", "ClaimMessenger"),
        Map.entry("openpartiesandclaims", "Open Parties and Claims"),
        Map.entry("openpac", "Open Parties and Claims"),
        Map.entry("ftbchunks", "FTB Chunks"),
        Map.entry("ftbteams", "FTB Teams"),
        Map.entry("griefprevention", "GriefPrevention"),
        Map.entry("griefdefender", "GriefDefender"),
        Map.entry("residence", "Residence"),
        Map.entry("lands", "Lands"),
        Map.entry("towny", "Towny"),
        Map.entry("townyadvanced", "Towny"),
        Map.entry("plotsquared", "PlotSquared"),
        Map.entry("bentobox", "BentoBox"),
        Map.entry("huskclaims", "HuskClaims"),
        Map.entry("husktowns", "HuskTowns"),
        Map.entry("protectionstones", "ProtectionStones"),
        Map.entry("redprotect", "RedProtect"),
        Map.entry("claimchunk", "ClaimChunk"),
        Map.entry("lwc", "LWC"),
        Map.entry("lwcx", "LWCX"),
        Map.entry("factions", "Factions"),
        Map.entry("kingdoms", "Kingdoms"),
        Map.entry("kingdomsx", "KingdomsX"),
        Map.entry("luckperms", "LuckPerms"),
        Map.entry("tab", "TAB"),
        Map.entry("sr", "SkinsRestorer"),
        Map.entry("skinsrestorer", "SkinsRestorer"),
        Map.entry("authme", "AuthMeReloaded"),
        Map.entry("authmereloaded", "AuthMeReloaded"),
        Map.entry("librelogin", "LibreLogin"),
        Map.entry("nlogin", "nLogin"),
        Map.entry("openlogin", "OpeNLogin"),
        Map.entry("limboauth", "LimboAuth"),
        Map.entry("loginsecurity", "LoginSecurity"),
        Map.entry("fastlogin", "FastLogin"),
        Map.entry("bungeeguard", "BungeeGuard"),
        Map.entry("emotecraft", "Emotecraft"),
        Map.entry("journeymap", "JourneyMap"),
        Map.entry("worldinfo", "WorldInfo"),
        Map.entry("xaerominimap", "Xaero Minimap"),
        Map.entry("xaeroworldmap", "Xaero World Map"),
        Map.entry("voxelmap", "VoxelMap"),
        Map.entry("cardinal-components", "Cardinal Components API"),
        Map.entry("cca", "Cardinal Components API"),
        Map.entry("polymer", "Polymer"),
        Map.entry("quickshop", "QuickShop-Hikari"),
        Map.entry("quickshophikari", "QuickShop-Hikari"),
        Map.entry("quickshop-hikari", "QuickShop-Hikari"),
        Map.entry("quickshopreremake", "QuickShop Reremake"),
        Map.entry("economyshopgui", "EconomyShopGUI"),
        Map.entry("economyshopguipremium", "EconomyShopGUI Premium"),
        Map.entry("economyshopguiplus", "EconomyShopGUI"),
        Map.entry("esgui", "EconomyShopGUI"),
        Map.entry("shopguiplus", "ShopGUI+"),
        Map.entry("shopgui", "ShopGUI+"),
        Map.entry("bossshoppro", "BossShopPro"),
        Map.entry("bossshop", "BossShop"),
        Map.entry("chestshop", "ChestShop"),
        Map.entry("shopchest", "ShopChest"),
        Map.entry("shopkeepers", "Shopkeepers"),
        Map.entry("ultimateshop", "UltimateShop"),
        Map.entry("excellentshop", "ExcellentShop"),
        Map.entry("zshop", "zShop"),
        Map.entry("guishop", "GUIShop"),
        Map.entry("simpleshop", "SimpleShop"),
        Map.entry("simpleshopgui", "SimpleShopGUI"),
        Map.entry("shopx", "Shop X"),
        Map.entry("bettershop", "BetterShop"),
        Map.entry("bettershops", "Better Shops"),
        Map.entry("deluxemenus", "DeluxeMenus"),
        Map.entry("commandpanels", "CommandPanels"),
        Map.entry("interactivechat", "InteractiveChat"),
        Map.entry("interchat", "InteractiveChat"),
        Map.entry("auctionmaster", "AuctionMaster"),
        Map.entry("crazyauctions", "CrazyAuctions"),
        Map.entry("zauctionhouse", "zAuctionHouse"),
        Map.entry("playerauctions", "PlayerAuctions"),
        Map.entry("auctionguiplus", "AuctionGUI+"),
        Map.entry("excellentauctionhouse", "ExcellentAuctionHouse"),
        Map.entry("excellentauctions", "ExcellentAuctions"),
        Map.entry("axauctions", "AxAuctions"),
        Map.entry("axauctionhouse", "AxAuctionHouse"),
        Map.entry("nexusauctionhouse", "NexusAuctionHouse"),
        Map.entry("deluxeauctions", "DeluxeAuctions"),
        Map.entry("outauction", "OutAuction"),
        Map.entry("fauction", "FAuction"),
        Map.entry("auctionhouseplus", "AuctionHousePlus"),
        Map.entry("crazycrates", "CrazyCrates"),
        Map.entry("crazycrate", "CrazyCrates"),
        Map.entry("excellentcrates", "ExcellentCrates"),
        Map.entry("excellentcrate", "ExcellentCrates"),
        Map.entry("goldencrates", "GoldenCrates"),
        Map.entry("goldencrate", "GoldenCrates"),
        Map.entry("cratereloaded", "CrateReloaded"),
        Map.entry("magiccrates", "MagicCrates"),
        Map.entry("magiccrate", "MagicCrates"),
        Map.entry("simplelootcrates", "SimpleLootCrates"),
        Map.entry("simplelootcrate", "SimpleLootCrates"),
        Map.entry("axcrates", "AxCrates"),
        Map.entry("azcrates", "AzCrates"),
        Map.entry("cratesplus", "CratesPlus"),
        Map.entry("cratekeys", "CrateKeys"),
        Map.entry("mysterycrates", "MysteryCrates"),
        Map.entry("phoenixcrates", "PhoenixCrates"),
        Map.entry("advancedcrates", "AdvancedCrates"),
        Map.entry("specializedcrates", "SpecializedCrates"),
        Map.entry("oraxen", "Oraxen"),
        Map.entry("itemsadder", "ItemsAdder"),
        Map.entry("nexo", "Nexo"),
        Map.entry("modelengine", "ModelEngine"),
        Map.entry("mythicmobs", "MythicMobs"),
        Map.entry("mythiclib", "MythicLib"),
        Map.entry("mmoitems", "MMOItems"),
        Map.entry("placeholderapi", "PlaceholderAPI"),
        Map.entry("protocollib", "ProtocolLib"),
        Map.entry("packetevents", "PacketEvents"),
        Map.entry("axiom", "Axiom Paper Plugin"),
        Map.entry("vault", "Vault"),
        Map.entry("essentials", "EssentialsX"),
        Map.entry("essentialsx", "EssentialsX"),
        Map.entry("cmi", "CMI"),
        Map.entry("citizens", "Citizens"),
        Map.entry("fancyholograms", "FancyHolograms"),
        Map.entry("fancynpcs", "FancyNpcs"),
        Map.entry("voicechat", "Simple Voice Chat"),
        Map.entry("vc", "Simple Voice Chat"),
        Map.entry("plasmo", "Plasmo Voice"),
        Map.entry("plasmovoice", "Plasmo Voice"),
        Map.entry("minecraftafk", "MinecraftAFK"),
        Map.entry("donutaddon", "DonutAddon"),
        Map.entry("donutsmp", "DonutSMP"),
        Map.entry("nighthawk", "Nighthawk"),
        Map.entry("feather", "Feather"),
        Map.entry("proantitab", "ProAntiTab"),
        Map.entry("pat", "ProAntiTab"),
        Map.entry("consumable_optimizer", "Consumable Optimizer"),
        Map.entry("clientcrystal", "FastCrystal"),
        Map.entry("fastcrystal", "FastCrystal"),
        Map.entry("optiplus", "OptiPlus"),
        Map.entry("optiplus-crystal", "OptiPlus Crystal"),
        Map.entry("optiplus-anchor", "OptiPlus Anchor"),
        Map.entry("fabric", "Fabric"),
        Map.entry("fabric-networking-api-v1", "Fabric Networking"),
        Map.entry("fabric-networking-v0", "Fabric Networking"),
        Map.entry("labymod", "LabyMod"),
        Map.entry("labymod3", "LabyMod"),
        Map.entry("essential", "Essential")
    );

    private static final Set<String> NON_PLUGIN_NAMESPACES = Set.of(
        "minecraft", "brigadier", "autism", "autismclient", "c"
    );

    private static final Set<String> DISCOVERY_CHANNELS = Set.of(
        "minecraft:register", "minecraft:unregister", "register", "unregister",
        "minecraft:brand", "brand", "bungeecord", "bungeecord:main"
    );

    private AutismPluginPayloadFingerprints() {
    }

    public record PluginFingerprint(String plugin, String key, List<String> channels, int score, String basis) {
    }

    public static boolean shouldObserveChannel(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank()) return false;
        if (DISCOVERY_CHANNELS.contains(normalized)) return true;
        int colon = normalized.indexOf(':');
        if (colon <= 0) return isLegacyInterestingChannel(normalized);
        String namespace = normalized.substring(0, colon);
        return !NON_PLUGIN_NAMESPACES.contains(namespace);
    }

    public static synchronized boolean observe(String serverAddress, String brand, AutismPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return false;
        if (!"S2C".equalsIgnoreCase(snapshot.direction())) return false;
        String addressKey = normalizeServerAddress(serverAddress);
        if (addressKey.isBlank()) return false;

        State state = STATES.computeIfAbsent(addressKey, unused -> new State());
        state.brand = normalizeBrand(brand);
        boolean changed = false;

        String channel = normalizeChannel(snapshot.channel());
        if (!channel.isBlank() && shouldObserveChannel(channel)) {
            changed |= state.addChannel(channel, snapshot.direction());
        }

        if (isRegistrationChannel(channel)) {
            for (String registered : extractRegisteredChannels(snapshot.rawBytes())) {
                if (shouldObserveChannel(registered)) {
                    changed |= state.addRegisteredChannel(registered, snapshot.direction());
                }
            }
        } else {
            for (String embedded : extractChannelTokens(snapshot.rawBytes())) {
                if (shouldObserveChannel(embedded)) {
                    changed |= state.addEmbeddedChannel(embedded, snapshot.direction());
                }
            }
        }

        if (changed) state.revision++;
        return changed;
    }

    public static synchronized List<PluginFingerprint> pluginsFor(String serverAddress, String brand) {
        State state = STATES.get(normalizeServerAddress(serverAddress));
        if (state == null) return List.of();

        Map<String, PluginAccumulator> byPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ChannelStats stats : state.allChannelStats()) {
            if (!stats.hasServerEvidence()) continue;
            PluginIdentity identity = pluginFromChannel(stats.channel);
            if (identity == null || identity.displayName().isBlank() || identity.key().isBlank()) continue;
            PluginAccumulator acc = byPlugin.computeIfAbsent(identity.key(), unused -> new PluginAccumulator(identity.displayName(), identity.key()));
            acc.add(stats);
        }

        if (byPlugin.isEmpty()) return List.of();
        List<PluginFingerprint> out = new ArrayList<>();
        for (PluginAccumulator acc : byPlugin.values()) {
            out.add(acc.toFingerprint());
        }
        return out;
    }

    public static synchronized long revisionFor(String serverAddress, String brand) {
        State state = STATES.get(normalizeServerAddress(serverAddress));
        return state == null ? 0L : state.revision;
    }

    public static synchronized String signatureFor(String serverAddress, String brand) {
        State state = STATES.get(normalizeServerAddress(serverAddress));
        if (state == null) return "";
        List<String> channels = new ArrayList<>(state.allChannels());
        channels.sort(String.CASE_INSENSITIVE_ORDER);
        if (channels.size() > 96) {
            channels = new ArrayList<>(channels.subList(0, 96));
        }
        return String.join(",", channels).toLowerCase(Locale.ROOT);
    }

    public static synchronized void clearSession() {
        STATES.clear();
    }

    private static boolean isRegistrationChannel(String channel) {
        String normalized = normalizeChannel(channel);
        return "minecraft:register".equals(normalized)
            || "register".equals(normalized)
            || "minecraft:unregister".equals(normalized)
            || "unregister".equals(normalized);
    }

    private static boolean isLegacyInterestingChannel(String channel) {
        String normalized = normalizeChannel(channel);
        return "bungeecord".equals(normalized)
            || "register".equals(normalized)
            || "unregister".equals(normalized);
    }

    private static List<String> extractRegisteredChannels(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return List.of();
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        String text = new String(rawBytes, StandardCharsets.UTF_8);

        for (String part : text.split("[\\u0000\\r\\n\\t ,;]+")) {
            String token = normalizeChannel(part);
            if (token.isBlank()) continue;
            if (token.indexOf(':') > 0 || isLegacyInterestingChannel(token)) {
                channels.add(token);
            }
        }

        channels.addAll(extractChannelTokens(rawBytes));
        return List.copyOf(channels);
    }

    private static List<String> extractChannelTokens(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return List.of();
        String text = new String(rawBytes, StandardCharsets.UTF_8);
        Matcher matcher = CHANNEL_TOKEN.matcher(text);
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        while (matcher.find()) {
            String channel = normalizeChannel(matcher.group());
            if (!channel.isBlank()) channels.add(channel);
            if (channels.size() >= MAX_CHANNELS_PER_SERVER) break;
        }
        return List.copyOf(channels);
    }

    private static PluginIdentity pluginFromChannel(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank()) return null;

        if ("bungeecord".equals(normalized) || "bungeecord:main".equals(normalized)) return new PluginIdentity("BungeeCord", "bungeecord");
        if ("register".equals(normalized) || "unregister".equals(normalized)) return null;

        int colon = normalized.indexOf(':');
        if (colon <= 0) return null;
        String namespace = normalized.substring(0, colon);
        if (NON_PLUGIN_NAMESPACES.contains(namespace)) return null;

        String alias = NAMESPACE_ALIASES.get(namespace);
        if (alias != null) return new PluginIdentity(alias, namespace);
        String presetLabel = pluginLabelFromPreset(normalized);
        if (presetLabel != null && !presetLabel.isBlank()) return new PluginIdentity(presetLabel, namespace);
        return new PluginIdentity(humanizeNamespace(namespace), namespace);
    }

    private static String pluginLabelFromPreset(String channel) {
        for (AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
            if (!AutismPayloadChannelListeners.presetCanIdentifyPlugin(preset)) continue;
            if (AutismPayloadChannelListeners.presetMatchesChannel(preset, channel)) {
                return preset.label();
            }
        }
        return null;
    }

    private static String humanizeNamespace(String namespace) {
        String clean = namespace == null ? "" : namespace.trim();
        if (clean.isBlank()) return "";
        String[] parts = clean.split("[_\\-.]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            if (part.length() <= 3 && part.chars().allMatch(Character::isLetter)) {
                sb.append(part.toUpperCase(Locale.ROOT));
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? clean : sb.toString();
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeServerAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeBrand(String brand) {
        return brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
    }

    public static String canonicalPluginKey(String value) {
        if (value == null) return "";
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.isBlank()) return "";
        int colon = clean.indexOf(':');
        if (colon > 0) clean = clean.substring(0, colon);
        String alias = NAMESPACE_ALIASES.get(clean);
        if (alias != null) return compactKey(alias);
        return compactKey(clean);
    }

    public static List<String> probableNamespacesForPlugin(String pluginName) {
        String canonical = canonicalPluginKey(pluginName);
        if (canonical.isBlank()) return List.of();
        LinkedHashSet<String> namespaces = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : NAMESPACE_ALIASES.entrySet()) {
            if (canonicalPluginKey(entry.getKey()).equals(canonical)
                || canonicalPluginKey(entry.getValue()).equals(canonical)) {
                namespaces.add(entry.getKey());
            }
        }

        String fromName = namespaceFromDisplay(pluginName);
        if (!fromName.isBlank() && !NON_PLUGIN_NAMESPACES.contains(fromName)) {
            namespaces.add(fromName);
        }
        return List.copyOf(namespaces);
    }

    private static String compactKey(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) out.append(ch);
        }
        return out.toString();
    }

    private static String namespaceFromDisplay(String pluginName) {
        if (pluginName == null) return "";
        String clean = pluginName.trim().toLowerCase(Locale.ROOT);
        if (clean.isBlank()) return "";
        int colon = clean.indexOf(':');
        if (colon > 0) clean = clean.substring(0, colon);
        clean = clean.replace('+', 'p');
        StringBuilder sb = new StringBuilder(clean.length());
        boolean lastWasSeparator = false;
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                lastWasSeparator = false;
            } else if ((ch == '_' || ch == '-' || ch == '.' || Character.isWhitespace(ch)) && !lastWasSeparator && !sb.isEmpty()) {
                sb.append('_');
                lastWasSeparator = true;
            }
        }
        while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '_') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private record PluginIdentity(String displayName, String key) {
        PluginIdentity {
            displayName = displayName == null ? "" : displayName.trim();
            key = canonicalPluginKey(key == null || key.isBlank() ? displayName : key);
        }
    }

    private static final class PluginAccumulator {
        final String key;
        String displayName;
        int score;
        String basis = "";
        final Set<String> labels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        PluginAccumulator(String displayName, String key) {
            this.displayName = displayName == null ? "" : displayName;
            this.key = key == null ? "" : key;
        }

        void add(ChannelStats stats) {
            if (stats == null || stats.channel == null || stats.channel.isBlank()) return;
            score += stats.score();
            if (basis.isBlank() || stats.sourceRank() > sourceRank(basis)) basis = stats.bestSource();
            labels.add(stats.label());
        }

        PluginFingerprint toFingerprint() {
            return new PluginFingerprint(displayName, key, List.copyOf(labels), score, basis);
        }

        private int sourceRank(String source) {
            return switch (source == null ? "" : source) {
                case "live" -> 4;
                case "registered" -> 3;
                case "embedded" -> 1;
                default -> 0;
            };
        }
    }

    private static final class ChannelStats {
        final String channel;
        int liveCount;
        int registeredCount;
        int embeddedCount;
        final Set<String> directions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        ChannelStats(String channel) {
            this.channel = normalizeChannel(channel);
        }

        void add(String source, String direction) {
            switch (source) {
                case "registered" -> registeredCount++;
                case "embedded" -> embeddedCount++;
                default -> liveCount++;
            }
            if (direction != null && !direction.isBlank()) directions.add(direction.trim().toUpperCase(Locale.ROOT));
        }

        int score() {
            return liveCount * 4 + registeredCount * 3 + embeddedCount;
        }

        int sourceRank() {
            return liveCount > 0 ? 4 : registeredCount > 0 ? 3 : embeddedCount > 0 ? 1 : 0;
        }

        String bestSource() {
            return liveCount > 0 ? "live" : registeredCount > 0 ? "registered" : embeddedCount > 0 ? "embedded" : "";
        }

        boolean hasServerEvidence() {
            return directions.contains("S2C");
        }

        String label() {
            StringBuilder sb = new StringBuilder(channel);
            String source = bestSource();
            if (!source.isBlank()) sb.append(" [").append(source);
            if (!directions.isEmpty()) sb.append(" ").append(String.join("/", directions));
            int count = liveCount + registeredCount + embeddedCount;
            if (count > 1) sb.append(" x").append(count);
            if (!source.isBlank()) sb.append(']');
            return sb.toString();
        }
    }

    private static final class State {
        String brand = "";
        long revision;
        final Set<String> directChannels = new LinkedHashSet<>();
        final Set<String> registeredChannels = new LinkedHashSet<>();
        final Set<String> embeddedChannels = new LinkedHashSet<>();
        final Map<String, ChannelStats> channelStats = new LinkedHashMap<>();

        boolean addChannel(String channel, String direction) {
            return addCapped(directChannels, channel, "live", direction);
        }

        boolean addRegisteredChannel(String channel, String direction) {
            return addCapped(registeredChannels, channel, "registered", direction);
        }

        boolean addEmbeddedChannel(String channel, String direction) {
            return addCapped(embeddedChannels, channel, "embedded", direction);
        }

        Set<String> allChannels() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            out.addAll(directChannels);
            out.addAll(registeredChannels);
            out.addAll(embeddedChannels);
            return Collections.unmodifiableSet(out);
        }

        List<ChannelStats> allChannelStats() {
            return List.copyOf(channelStats.values());
        }

        private boolean addCapped(Set<String> set, String channel, String source, String direction) {
            String normalized = normalizeChannel(channel);
            if (normalized.isBlank()) return false;
            if (directChannels.size() + registeredChannels.size() + embeddedChannels.size() >= MAX_CHANNELS_PER_SERVER
                && !directChannels.contains(normalized)
                && !registeredChannels.contains(normalized)
                && !embeddedChannels.contains(normalized)) {
                return false;
            }
            ChannelStats stats = channelStats.computeIfAbsent(normalized, ChannelStats::new);
            int beforeScore = stats.score();
            stats.add(source, direction);
            return set.add(normalized) || stats.score() != beforeScore;
        }
    }
}
