package autismclient.modules;
import autismclient.api.module.*;

import autismclient.mixin.accessor.AutismChatComponentAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameCensorModule extends Module {
    private static final Random RANDOM = new Random();
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();
    private static final Set<String> USED_ALIASES = ConcurrentHashMap.newKeySet();
    private static final List<String> CENSOR_WORDS = buildCensorWords();
    private static final Pattern USERNAME_TOKEN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");
    private static final int MAX_OBSERVED_NAMES = 2048;
    private static final int MAX_AGGRESSIVE_NAMES = 2048;
    private static final long TARGET_CACHE_BUCKET_MS = 250L;
    private static final Object OBSERVED_LOCK = new Object();
    private static final Object TARGET_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, String> RELIABLE_NAMES = new LinkedHashMap<>(128, 0.75f, true);
    private static final LinkedHashMap<String, String> AGGRESSIVE_NAMES = new LinkedHashMap<>(128, 0.75f, true);
    private static final AtomicLong NAME_REVISION = new AtomicLong();
    private static volatile TargetSnapshot TARGET_CACHE = TargetSnapshot.empty();
    private static volatile boolean ACTIVE;
    private static volatile boolean HIDE_ALL_SKINS;
    private static volatile boolean HIDE_SELF_SKIN;
    private static volatile boolean LEARN_AGGRESSIVELY;

    private int lastSkinState;

    public NameCensorModule() {
        super("name-censor", "NameCensor", ModuleCategory.MISC, "Replaces player names with aliases.");
        add(new BoolSetting("censor-self", "Censor Self", true).description("Hide your own name."));
        add(new StringListSetting("names", "Names", "").description("Extra names to hide.").playerNameList());
        add(new StringSetting("self-alias", "Self Alias", "").description("Your alias"));
        add(new StringListSetting("custom-aliases", "Custom Aliases", "").description("name=alias pairs.").playerNameList());
        add(new BoolSetting("censor-everyone", "Censor Everyone", false).description("Hide known player names."));
        add(new BoolSetting("censor-everything", "Censor Everything", false).description("Aggressively hide username-shaped text."));
        add(new BoolSetting("hide-skins-all", "Hide All Skins", false).description("Default skin for everyone."));

        add(new BoolSetting("hide-skin-self", "Hide My Skin", false).description("Your default skin")
                .visibleWhen(() -> !Boolean.parseBoolean(value("hide-skins-all"))));
    }

    @Override
    public void onEnable() {
        refreshFastFlags();
        lastSkinState = skinState();
        learnCurrentKnownPlayers();
        refreshChat();
        refreshVisuals();
    }

    @Override
    public void onDisable() {
        refreshFastFlags();
        lastSkinState = 0;
        clearObservedNames();
        refreshChat();
        refreshVisuals();
    }

    @Override
    public void onGameJoin() {
        clearObservedNames();
        learnCurrentKnownPlayers();
    }

    @Override
    public void onGameLeft() {
        clearObservedNames();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        learnFromPacket(packet);
        return false;
    }

    @Override
    public void tick() {
        int currentSkinState = skinState();
        if (currentSkinState != lastSkinState) {
            lastSkinState = currentSkinState;
            refreshVisuals();
        }
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        int previousSkinState = skinState();
        refreshFastFlags();
        TARGET_CACHE = TargetSnapshot.empty();
        NAME_REVISION.incrementAndGet();
        if (isEnabled()) {
            refreshChat();
            if (skinState() != previousSkinState) refreshVisuals();
        }
    }

    @Override
    protected void onSettingsReset() {
        refreshFastFlags();
        TARGET_CACHE = TargetSnapshot.empty();
        NAME_REVISION.incrementAndGet();
        if (isEnabled()) {
            refreshChat();
            refreshVisuals();
        }
    }

    public static void refreshFastFlagsFromRegistry() {
        Module module = module();
        if (module instanceof NameCensorModule censor) {
            censor.refreshFastFlags();
        } else {
            ACTIVE = false;
            HIDE_ALL_SKINS = false;
            HIDE_SELF_SKIN = false;
            LEARN_AGGRESSIVELY = false;
        }
    }

    private void refreshFastFlags() {
        boolean enabled = isEnabled();
        ACTIVE = enabled;
        HIDE_ALL_SKINS = enabled && bool("hide-skins-all");
        HIDE_SELF_SKIN = enabled && bool("hide-skin-self");
        LEARN_AGGRESSIVELY = enabled && bool("censor-everything");
    }

    private static int skinState() {
        return (HIDE_ALL_SKINS ? 2 : 0) | (HIDE_SELF_SKIN ? 1 : 0);
    }

    public static Component censorComponent(Component component) {
        return censorComponent(component, false);
    }

    public static Component censorServerComponent(Component component) {
        return censorComponent(component, true);
    }

    private static Component censorComponent(Component component, boolean learn) {
        if (!isActive() || component == null) return component;

        boolean learnActive = learn && LEARN_AGGRESSIVELY;
        TargetSnapshot targets = targetSnapshot();
        if (!learnActive && targets.names().isEmpty()) return component;

        List<TextPart> parts = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        for (Component part : component.toFlatList()) {
            String text = part.getString();
            if (text.isEmpty()) continue;
            int start = raw.length();
            raw.append(text);
            parts.add(new TextPart(text, part.getStyle(), start, raw.length()));
        }

        if (learnActive) {
            learnAggressiveNamesFromText(raw.toString());
            targets = targetSnapshot();
        }
        if (targets.names().isEmpty()) return component;

        NormalizedText normalized = normalizeFormattingCodes(raw.toString());
        List<Replacement> replacements = replacements(normalized, targets);
        if (replacements.isEmpty()) return component;

        MutableComponent out = Component.empty();
        int cursor = 0;
        for (Replacement replacement : replacements) {
            appendOriginal(out, parts, cursor, replacement.start());
            out.append(Component.literal(replacement.text()).withStyle(styleAt(parts, replacement.start())));
            cursor = replacement.end();
        }
        appendOriginal(out, parts, cursor, raw.length());
        return out;
    }

    public static String censorText(String text) {
        return censorText(text, false);
    }

    public static String censorServerText(String text) {
        return censorText(text, true);
    }

    private static String censorText(String text, boolean learn) {
        if (!isActive() || text == null || text.isEmpty()) return text;
        if (learn && LEARN_AGGRESSIVELY) learnAggressiveNamesFromText(text);
        return censorText(text, targetSnapshot());
    }

    private static boolean hideAllSkins() {
        return HIDE_ALL_SKINS;
    }

    private static boolean hideSelfSkin() {
        return HIDE_SELF_SKIN;
    }

    public static boolean shouldDisableSkinFor(GameProfile profile) {
        if (HIDE_ALL_SKINS) return true;
        if (!HIDE_SELF_SKIN || profile == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) return false;
        if (profile.id() != null && profile.id().equals(mc.getUser().getProfileId())) return true;
        String localName = mc.getUser().getName();
        return localName != null && localName.equalsIgnoreCase(profile.name());
    }

    private static String censorText(String text, TargetSnapshot targets) {
        if (targets.names().isEmpty() || text == null || text.isEmpty()) return text;
        NormalizedText normalized = normalizeFormattingCodes(text);
        List<Replacement> replacements = replacements(normalized, targets);
        if (replacements.isEmpty()) return text;

        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Replacement replacement : replacements) {
            out.append(text, cursor, replacement.start());
            out.append(replacement.text());
            cursor = replacement.end();
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }

    private static List<Replacement> replacements(NormalizedText normalized, TargetSnapshot targets) {
        if (normalized.text().isEmpty()) return List.of();
        List<Replacement> replacements = new ArrayList<>();
        boolean[] used = new boolean[normalized.rawLength()];
        for (NamePattern target : targets.patterns()) {
            Matcher matcher = target.pattern().matcher(normalized.text());
            while (matcher.find()) {
                int rawStart = normalized.rawIndex(matcher.start());
                int rawEnd = normalized.rawIndex(matcher.end() - 1) + 1;
                if (rawStart < 0 || rawEnd <= rawStart || overlaps(used, rawStart, rawEnd)) continue;
                for (int i = rawStart; i < rawEnd; i++) used[i] = true;
                replacements.add(new Replacement(rawStart, rawEnd, replacementFor(target.name())));
            }
        }
        replacements.sort(Comparator.comparingInt(Replacement::start));
        return replacements;
    }

    private static boolean overlaps(boolean[] used, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(used.length, end); i++) {
            if (used[i]) return true;
        }
        return false;
    }

    private static NormalizedText normalizeFormattingCodes(String raw) {
        StringBuilder text = new StringBuilder(raw.length());
        List<Integer> rawIndexes = new ArrayList<>(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00a7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            rawIndexes.add(i);
            text.append(c);
        }
        return new NormalizedText(text.toString(), rawIndexes, raw.length());
    }

    private static void appendOriginal(MutableComponent out, List<TextPart> parts, int start, int end) {
        if (end <= start) return;
        for (TextPart part : parts) {
            int overlapStart = Math.max(start, part.start());
            int overlapEnd = Math.min(end, part.end());
            if (overlapEnd <= overlapStart) continue;
            int localStart = overlapStart - part.start();
            int localEnd = overlapEnd - part.start();
            out.append(Component.literal(part.text().substring(localStart, localEnd)).withStyle(part.style()));
        }
    }

    private static Style styleAt(List<TextPart> parts, int rawIndex) {
        for (TextPart part : parts) {
            if (rawIndex >= part.start() && rawIndex < part.end()) return part.style();
        }
        return Style.EMPTY;
    }

    private static String replacementFor(String name) {
        Module module = module();
        if (module != null) {
            String custom = customReplacement(module, name);
            if (!custom.isBlank()) return custom;
        }
        return aliasFor(name);
    }

    private static String customReplacement(Module module, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && Boolean.parseBoolean(module.value("censor-self")) && mc.getUser().getName().equalsIgnoreCase(name)) {
            String selfAlias = module.value("self-alias").trim();
            if (!selfAlias.isEmpty()) return selfAlias;
        }

        for (String entry : module.list("custom-aliases")) {
            int split = aliasSplitIndex(entry);
            if (split <= 0) continue;
            String rawName = entry.substring(0, split).trim();
            String alias = entry.substring(split + 1).trim();
            if (!rawName.isEmpty() && !alias.isEmpty() && rawName.equalsIgnoreCase(name)) return alias;
        }
        return "";
    }

    private static int aliasSplitIndex(String entry) {
        int split = entry.indexOf('=');
        return split >= 0 ? split : entry.indexOf(':');
    }

    private static TargetSnapshot targetSnapshot() {
        Module module = module();
        if (module == null || !module.isEnabled()) return TargetSnapshot.empty();

        long revision = NAME_REVISION.get();
        long refreshBucket = System.currentTimeMillis() / TARGET_CACHE_BUCKET_MS;
        String settingsKey = settingsKey(module, refreshBucket);
        TargetSnapshot cached = TARGET_CACHE;
        if (cached.revision() == revision && cached.settingsKey().equals(settingsKey)) return cached;

        synchronized (TARGET_CACHE_LOCK) {
            cached = TARGET_CACHE;
            if (cached.revision() == revision && cached.settingsKey().equals(settingsKey)) return cached;

            Set<String> names = new LinkedHashSet<>();
            Minecraft mc = Minecraft.getInstance();
            if (Boolean.parseBoolean(module.value("censor-self")) && mc != null && mc.getUser() != null) {
                addConfiguredName(names, mc.getUser().getName());
            }
            for (String entry : module.list("names")) addConfiguredName(names, entry);
            for (String entry : module.list("custom-aliases")) {
                int split = aliasSplitIndex(entry);
                if (split > 0) addConfiguredName(names, entry.substring(0, split));
            }
            boolean censorEveryone = Boolean.parseBoolean(module.value("censor-everyone"));
            boolean censorEverything = Boolean.parseBoolean(module.value("censor-everything"));
            if (censorEveryone || censorEverything) {
                addReliableNames(names);
                addCurrentKnownNames(names, mc);
            }
            if (censorEverything) addAggressiveNames(names);

            List<String> sorted = new ArrayList<>(names);
            sorted.sort(Comparator.comparingInt(String::length).reversed());
            List<NamePattern> patterns = sorted.stream()
                .map(name -> new NamePattern(name, Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])")))
                .toList();
            TargetSnapshot snapshot = new TargetSnapshot(revision, settingsKey, List.copyOf(sorted), List.copyOf(patterns));
            TARGET_CACHE = snapshot;
            return snapshot;
        }
    }

    private static String settingsKey(Module module, long refreshBucket) {
        return refreshBucket
            + "|" + module.value("censor-self")
            + "|" + module.value("censor-everyone")
            + "|" + module.value("censor-everything")
            + "|" + module.value("self-alias")
            + "|" + String.join("\u001f", module.list("names"))
            + "|" + String.join("\u001f", module.list("custom-aliases"));
    }

    private static void addConfiguredName(Set<String> names, String name) {
        if (name == null) return;
        String trimmed = name.trim();
        if (!trimmed.isEmpty()) names.add(trimmed);
    }

    private static void addReliableNames(Set<String> names) {
        synchronized (OBSERVED_LOCK) {
            names.addAll(RELIABLE_NAMES.values());
        }
    }

    private static void addAggressiveNames(Set<String> names) {
        synchronized (OBSERVED_LOCK) {
            names.addAll(AGGRESSIVE_NAMES.values());
        }
    }

    private static void addCurrentKnownNames(Set<String> names, Minecraft mc) {
        if (mc == null) return;
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info != null && info.getProfile() != null) addReliableTarget(names, info.getProfile().name());
            }
        }
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player != null && player.getGameProfile() != null) addReliableTarget(names, player.getGameProfile().name());
            }
        }
    }

    private static void addReliableTarget(Set<String> names, String name) {
        String cleaned = normalizeReliableName(name);
        if (cleaned != null) names.add(cleaned);
    }

    private static void learnCurrentKnownPlayers() {
        Module module = module();
        if (module == null || !module.isEnabled() || !shouldCollectReliablePlayers(module)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info != null && info.getProfile() != null) rememberReliableName(info.getProfile().name());
            }
        }
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player != null && player.getGameProfile() != null) rememberReliableName(player.getGameProfile().name());
            }
        }
    }

    private static void learnFromPacket(Packet<?> packet) {
        Module module = module();
        if (module == null || !module.isEnabled() || packet == null) return;
        boolean reliable = shouldCollectReliablePlayers(module);
        boolean aggressive = shouldLearnAggressively(module);
        if (!reliable && !aggressive) return;
        if (packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoPacket.entries()) {
                if (entry == null) continue;
                GameProfile profile = entry.profile();
                if (profile != null && reliable) rememberReliableName(profile.name());
                if (aggressive) learnAggressiveNamesFromComponent(entry.displayName());
            }
            return;
        }
        if (packet instanceof ClientboundSetPlayerTeamPacket teamPacket) {
            if (reliable) {
                for (String player : teamPacket.getPlayers()) rememberReliableName(player);
            }
            if (aggressive) {
                teamPacket.getParameters().ifPresent(parameters -> {
                    learnAggressiveNamesFromComponent(parameters.displayName());
                    learnAggressiveNamesFromComponent(parameters.playerPrefix());
                    learnAggressiveNamesFromComponent(parameters.playerSuffix());
                });
            }
            return;
        }
        if (packet instanceof ClientboundSetObjectivePacket objectivePacket) {
            if (aggressive) {
                rememberAggressiveName(objectivePacket.getObjectiveName());
                learnAggressiveNamesFromComponent(objectivePacket.getDisplayName());
            }
            return;
        }
        if (packet instanceof ClientboundSetScorePacket scorePacket) {
            if (aggressive) {
                rememberAggressiveName(scorePacket.owner());
                rememberAggressiveName(scorePacket.objectiveName());
                scorePacket.display().ifPresent(NameCensorModule::learnAggressiveNamesFromComponent);
            }
            return;
        }
        if (packet instanceof ClientboundResetScorePacket resetScorePacket) {
            if (aggressive) {
                rememberAggressiveName(resetScorePacket.owner());
                rememberAggressiveName(resetScorePacket.objectiveName());
            }
            return;
        }
        if (packet instanceof ClientboundSetDisplayObjectivePacket displayObjectivePacket) {
            if (aggressive) rememberAggressiveName(displayObjectivePacket.getObjectiveName());
            return;
        }
        if (packet instanceof ClientboundTabListPacket tabListPacket) {
            if (aggressive) {
                learnAggressiveNamesFromComponent(tabListPacket.header());
                learnAggressiveNamesFromComponent(tabListPacket.footer());
            }
            return;
        }
        if (packet instanceof ClientboundSystemChatPacket chatPacket) {
            if (aggressive) learnAggressiveNamesFromComponent(chatPacket.content());
            return;
        }
        if (packet instanceof ClientboundDisguisedChatPacket disguisedChatPacket) {
            if (aggressive) {
                learnAggressiveNamesFromComponent(disguisedChatPacket.message());
                learnAggressiveNamesFromComponent(disguisedChatPacket.chatType().name());
                disguisedChatPacket.chatType().targetName().ifPresent(NameCensorModule::learnAggressiveNamesFromComponent);
            }
            return;
        }
        if (packet instanceof ClientboundPlayerChatPacket playerChatPacket) {
            if (aggressive) {
                learnAggressiveNamesFromText(playerChatPacket.body().content());
                learnAggressiveNamesFromComponent(playerChatPacket.unsignedContent());
                learnAggressiveNamesFromComponent(playerChatPacket.chatType().name());
                playerChatPacket.chatType().targetName().ifPresent(NameCensorModule::learnAggressiveNamesFromComponent);
            }
        }
    }

    private static void learnAggressiveNamesFromComponent(Component component) {
        if (component == null) return;
        learnAggressiveNamesFromText(component.getString());
    }

    private static void learnAggressiveNamesFromText(String text) {
        if (!shouldLearnAggressively() || text == null || text.isEmpty()) return;
        NormalizedText normalized = normalizeFormattingCodes(text);
        Matcher matcher = USERNAME_TOKEN.matcher(normalized.text());
        while (matcher.find()) {
            rememberAggressiveName(matcher.group(1));
        }
    }

    private static boolean shouldLearnAggressively() {
        return LEARN_AGGRESSIVELY;
    }

    private static boolean shouldLearnAggressively(Module module) {
        return Boolean.parseBoolean(module.value("censor-everything"));
    }

    private static boolean shouldCollectReliablePlayers(Module module) {
        return Boolean.parseBoolean(module.value("censor-everyone")) || Boolean.parseBoolean(module.value("censor-everything"));
    }

    private static void rememberReliableName(String name) {
        String cleaned = normalizeReliableName(name);
        rememberName(RELIABLE_NAMES, MAX_OBSERVED_NAMES, cleaned);
    }

    private static void rememberAggressiveName(String name) {
        String cleaned = normalizeUsernameToken(name);
        rememberName(AGGRESSIVE_NAMES, MAX_AGGRESSIVE_NAMES, cleaned);
    }

    private static void rememberName(LinkedHashMap<String, String> names, int maxSize, String cleaned) {
        if (cleaned == null) return;
        String key = cleaned.toLowerCase(Locale.ROOT);
        synchronized (OBSERVED_LOCK) {
            if (names.containsKey(key)) {
                names.get(key);
                return;
            }
            while (names.size() >= maxSize) {
                String eldest = names.keySet().iterator().next();
                names.remove(eldest);
            }
            names.put(key, cleaned);
        }
        NAME_REVISION.incrementAndGet();
    }

    private static String normalizeReliableName(String name) {
        String cleaned = normalizeUsernameToken(name);
        if (cleaned == null) return null;
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.matches("slot_\\d+")) return null;
        return cleaned;
    }

    private static String normalizeUsernameToken(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        return USERNAME_TOKEN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static void clearObservedNames() {
        boolean changed;
        synchronized (OBSERVED_LOCK) {
            changed = !RELIABLE_NAMES.isEmpty() || !AGGRESSIVE_NAMES.isEmpty();
            RELIABLE_NAMES.clear();
            AGGRESSIVE_NAMES.clear();
        }
        if (changed) NAME_REVISION.incrementAndGet();
        TARGET_CACHE = TargetSnapshot.empty();
    }

    private static String aliasFor(String name) {
        return ALIASES.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> nextAlias());
    }

    private static String nextAlias() {
        synchronized (USED_ALIASES) {
            if (USED_ALIASES.size() >= CENSOR_WORDS.size()) return CENSOR_WORDS.get(RANDOM.nextInt(CENSOR_WORDS.size()));
            String alias;
            do {
                alias = CENSOR_WORDS.get(RANDOM.nextInt(CENSOR_WORDS.size()));
            } while (!USED_ALIASES.add(alias));
            return alias;
        }
    }

    public static boolean isActive() {
        return ACTIVE;
    }

    private static Module module() {
        return ModuleRegistry.get("name-censor");
    }

    private static void refreshChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud.getChat() == null) return;
        try {
            ((AutismChatComponentAccessor) mc.gui.hud.getChat()).autism$refreshTrimmedMessages();
        } catch (Throwable ignored) {  }
    }

    private static void refreshVisuals() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        ModuleRenderUtil.refreshWorldRenderer();
    }

    private static List<String> buildCensorWords() {
        String[] first = {
            "\u81ea\u95ed", "\u5305\u5305", "\u6570\u636e", "\u65b9\u5757", "\u6df7\u51dd", "\u7c97\u7b51", "\u786c\u6838", "\u5ef6\u8fdf", "\u7ea2\u77f3", "\u5b57\u8282",
            "\u961f\u5217", "\u63e1\u624b", "\u540c\u6b65", "\u50cf\u7d20", "\u6808\u5e27", "\u7f13\u5b58", "\u62bd\u8c61", "\u6c34\u6ce5", "\u88c2\u7eb9", "\u7070\u5899"
        };
        String[] second = {
            "\u5927\u5e08", "\u72c2\u70ed", "\u5c0f\u5b50", "\u5de5\u5934", "\u6307\u6325", "\u5e7d\u9ed8", "\u7206\u7b11", "\u98de\u5305", "\u786c\u5899", "\u4e71\u6d41",
            "\u795e\u7ecf", "\u7535\u6ce2", "\u94c1\u677f", "\u5947\u89c2", "\u56de\u58f0", "\u5de8\u6784"
        };
        List<String> words = new ArrayList<>(first.length * second.length);
        for (String a : first) {
            for (String b : second) {
                String word = a + b;
                if (word.length() <= 6) words.add(word);
            }
        }
        return List.copyOf(words);
    }

    private record TextPart(String text, Style style, int start, int end) {
    }

    private record Replacement(int start, int end, String text) {
    }

    private record NamePattern(String name, Pattern pattern) {
    }

    private record TargetSnapshot(long revision, String settingsKey, List<String> names, List<NamePattern> patterns) {
        private static TargetSnapshot empty() {
            return new TargetSnapshot(-1L, "", List.of(), List.of());
        }
    }

    private record NormalizedText(String text, List<Integer> rawIndexes, int rawLength) {
        int rawIndex(int normalizedIndex) {
            return normalizedIndex < 0 || normalizedIndex >= rawIndexes.size() ? -1 : rawIndexes.get(normalizedIndex);
        }
    }
}
