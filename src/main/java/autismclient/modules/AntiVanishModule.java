package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.StringListSetting;
import autismclient.api.module.StringSetting;
import autismclient.util.AntiVanishText;
import autismclient.util.AntiVanishHeuristics;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPlayerScanner;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class AntiVanishModule extends Module {
    private static final long SIGNAL_WINDOW_MS = 15_000L;
    private static final long DETECTION_TTL_MS = 20_000L;
    private static final long SELF_BREAK_TTL_MS = 6_000L;
    private static final long CRITICAL_COOLDOWN_MS = 12_000L;
    private static final long ANNOUNCE_COOLDOWN_MS = 1_500L;
    private static final long ANNOUNCE_WINDOW_MS = 4_000L;
    private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
    private static final int MAX_OBSERVATIONS_PER_TICK = 512;
    private static final int CRITICAL_SCORE = 35;
    private static final long PLACE_SOUND_MATCH_MS = 700L;
    private static final long BREAK_MATCH_MS = 900L;
    private static final long SELF_PLACE_TTL_MS = 6_000L;
    private static final long AMBIGUOUS_DEPARTURE_TTL_MS = 30_000L;
    private static final long REMOTE_DIG_TTL_MS = 3_000L;
    private static final long CONTAINER_SELF_GRACE_MS = 2_500L;
    private static final int[][] BLOCK_AXES = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockTransition> blockTransitions = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RawPlaceSound> rawPlaceSounds = new ConcurrentLinkedQueue<>();
    private final AtomicInteger blockTransitionCount = new AtomicInteger();
    private final AtomicInteger rawPlaceSoundCount = new AtomicInteger();
    private final Map<UUID, KnownPlayer> knownPlayers = new HashMap<>();
    private final Map<String, Detection> detections = new LinkedHashMap<>();
    private final Map<String, Long> signalCooldowns = new HashMap<>();
    private final Map<String, Long> announceCooldowns = new HashMap<>();
    private final Deque<Long> announceTimes = new ArrayDeque<>();

    private final Map<Long, Long> selfBrokenBlocks = new HashMap<>();

    private final Map<Long, Long> selfPlacedBlocks = new HashMap<>();
    private final Map<UUID, Long> confirmedDepartures = new HashMap<>();

    private final Map<UUID, AmbiguousDeparture> ambiguousDepartures = new HashMap<>();
    private final Map<Long, Long> automatedMechanisms = new HashMap<>();
    private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<>();
    private final Map<Long, Deque<Long>> chunkResends = new HashMap<>();
    private final Map<Long, Long> blockChunkQuietUntil = new HashMap<>();
    private final Deque<Signal> signals = new ArrayDeque<>();
    private final Deque<Long> cameraCorrections = new ArrayDeque<>();
    private final Deque<ExplosionEvent> recentExplosions = new ArrayDeque<>();

    private final Deque<PendingPlace> pendingPlaces = new ArrayDeque<>();
    private final Deque<PlaceSound> recentPlaceSounds = new ArrayDeque<>();

    private final Map<Long, RemoteDig> recentRemoteDigs = new HashMap<>();
    private final Map<Long, Removal> recentAirUpdates = new HashMap<>();
    private final Map<Long, BreakEffect> recentBreakEffects = new HashMap<>();
    private final Deque<PendingBreak> pendingBreaks = new ArrayDeque<>();
    private final Deque<PendingAnonymousBreak> pendingAnonymousBreaks = new ArrayDeque<>();
    private final Map<Integer, HiddenSwing> recentHiddenSwings = new HashMap<>();
    private final Deque<PendingVanish> pendingVanishes = new ArrayDeque<>();
    private final Set<Long> seenChunks = new HashSet<>();

    private final Deque<RecentMessage> recentMessages = new ArrayDeque<>();
    private boolean serverSendsLeaveMessages;

    private final Set<Integer> completionRequestIds = new HashSet<>();
    private volatile List<String> pendingCompletionNames;
    private int nextCompletionId = 30000;
    private static final long RECENT_MESSAGE_TTL_MS = 8_000L;

    private static final long TRUSTED_LISTED_MS = 1_500L;
    private final Map<UUID, Long> listedSinceMs = new HashMap<>();

    private Object lastLevel;
    private volatile Vec3 lastPosition;
    private volatile float lastYaw;
    private volatile float lastPitch;
    private int stationaryTicks;
    private int tickCounter;
    private volatile int localPlayerId = Integer.MIN_VALUE;
    private volatile long lastLocalActionMs;
    private long lastContainerActivityMs;
    private long lastServerCorrectionMs;
    private long lastCriticalMs;
    private long criticalUntilMs;
    private int currentScore;
    private String criticalSummary = "";
    private String lastTrigger = "";

    public AntiVanishModule() {
        super("anti-vanish", "AntiVanish", ModuleCategory.MISC, "Detects vanished players.");

        add(new BoolSetting("vanish-tracker", "Vanish Tracker", true).group("Detection")
            .description("Detect TAB disappearances.").build());
        add(new BoolSetting("completion-probe", "Tab Probe", true).group("Detection")
            .description("Active tab probe.").build());
        add(new StringSetting("probe-command", "Probe Command", "minecraft:msg").group("Detection")
            .description("Command for probe.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("gamemode-alerts", "Gamemode Alerts", false).group("Detection")
            .description("Notify gamemode switches.").build());
        add(new BoolSetting("player-filter", "Player Filter", false).group("Detection")
            .description("Scan listed names only.").build());
        add(new StringListSetting("players", "Players", "").group("Detection")
            .playerNameList()
            .visibleWhen(() -> bool("player-filter")).build());

        add(new BoolSetting("environmental", "Environmental", true).group("Sensors")
            .description("Sounds, blocks, particles.").build());
        add(new BoolSetting("sound-sensor", "Suspicious Sounds", true).group("Sensors")
            .description("Detect unexplained sounds.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("particle-sensor", "Ghost Particles", true).group("Sensors")
            .description("Detect ghost particles.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("block-sensor", "Block Updates", true).group("Sensors")
            .description("Detect unseen interactions.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("invisible-sensor", "Invisible Entities", true).group("Sensors")
            .description("Detect invisible players.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("camera-sensor", "Camera Aberrations", true).group("Sensors")
            .description("Detect forced camera resets.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("chunk-sensor", "Chunk Re-sends", true).group("Sensors")
            .description("Detect nearby chunk resends.")
            .visibleWhen(() -> false).build());
        add(new IntSetting("range", "Detection Range", 64, 8, 160, 8).group("Sensors")
            .description("Sensor watch distance.")
            .visibleWhen(() -> false).build());

        add(new BoolSetting("critical-alert", "Critical Alert", true).group("Alerts")
            .description("Combine recent signals.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("alert-sound", "Warning Sound", true).group("Alerts")
            .description("Play critical warning.")
            .visibleWhen(() -> false).build());
        add(new BoolSetting("hud-list", "Vanish HUD", true).group("Alerts")
            .description("Show detections.").build());
        add(new BoolSetting("chat-alerts", "Chat Alerts", true).group("Alerts")
            .description("Log detections to chat.").build());
    }

    @Override
    public String info() {
        return currentScore > 0 ? Integer.toString(currentScore) : "";
    }

    @Override
    public void onEnable() {
        resetRuntime();
        if (MC.player != null && MC.level != null) {
            lastLevel = MC.level;
            lastPosition = MC.player.position();
            lastYaw = MC.player.getYRot();
            lastPitch = MC.player.getXRot();
            localPlayerId = MC.player.getId();
            trackListedPlayers();
        }
    }

    @Override
    public void onDisable() {
        resetRuntime();
    }

    @Override
    public void onGameJoin() {
        resetRuntime();
    }

    @Override
    public void onGameLeft() {
        resetRuntime();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.getConnection() == null) {
            resetRuntime();
            return;
        }
        if (lastLevel != MC.level) {
            resetRuntime();
            lastLevel = MC.level;
        }

        tickCounter++;
        localPlayerId = MC.player.getId();

        if (MC.player.containerMenu != MC.player.inventoryMenu) lastContainerActivityMs = System.currentTimeMillis();
        updateStationaryState();
        drainObservations();
        drainBlockEvidence();
        processPendingPlaces();
        processPendingBreaks();
        processPendingAnonymousBreaks();
        processPendingVanishes();
        processCompletionProbe();
        if (bool("completion-probe") && tickCounter % 100 == 0) sendCompletionProbe();
        if (tickCounter % 10 == 0) {
            trackListedPlayers();
        }
        if (tickCounter % 5 == 0 && sensorOn("invisible-sensor")) scanInvisiblePlayers();
        pruneState();
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet == null) return false;
        if (packet instanceof ServerboundPlayerActionPacket action) {
            lastLocalActionMs = System.currentTimeMillis();

            ServerboundPlayerActionPacket.Action a = action.getAction();
            if (action.getPos() != null) {
                if (a == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                    markSelfBreakFootprint(action.getPos(), System.currentTimeMillis() + SELF_BREAK_TTL_MS, false);
                } else if (a == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                    markSelfBreakFootprint(action.getPos(), System.currentTimeMillis() + 2_000L, false);
                } else if (a == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                    markSelfBreakFootprint(action.getPos(), 0L, true);
                }
            }
            return false;
        }
        if (packet instanceof ServerboundUseItemOnPacket useOn) {
            lastLocalActionMs = System.currentTimeMillis();
            if (useOn.getHitResult() != null && useOn.getHitResult().getBlockPos() != null) {
                long until = System.currentTimeMillis() + SELF_PLACE_TTL_MS;
                BlockPos hit = useOn.getHitResult().getBlockPos();

                BlockPos adjacent = hit.relative(useOn.getHitResult().getDirection());
                selfPlacedBlocks.put(hit.asLong(), until);
                selfPlacedBlocks.put(adjacent.asLong(), until);
                String itemPath = "";
                if (MC.player != null && useOn.getHand() != null) {
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(MC.player.getItemInHand(useOn.getHand()).getItem());
                    itemPath = AntiVanishHeuristics.path(itemId == null ? "" : itemId.toString());
                }
                markSelfMultiBlockFootprint(hit, itemPath, selfPlacedBlocks, until, false);
                markSelfMultiBlockFootprint(adjacent, itemPath, selfPlacedBlocks, until, false);
            }
            return false;
        }
        String name = packet.getClass().getSimpleName();
        if (name.equals("ServerboundUseItemPacket")
            || name.equals("ServerboundInteractPacket")
            || name.equals("ServerboundSwingPacket")) {
            lastLocalActionMs = System.currentTimeMillis();
        }
        return false;
    }

    private void markSelfBreakFootprint(BlockPos pos, long until, boolean remove) {
        if (pos == null) return;
        String blockPath = "";
        if (MC.level != null) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
            blockPath = AntiVanishHeuristics.path(id == null ? "" : id.toString());
        }
        markSelfMultiBlockFootprint(pos, blockPath, selfBrokenBlocks, until, remove);
    }

    static void markSelfMultiBlockFootprint(BlockPos pos, String path, Map<Long, Long> targets,
                                            long until, boolean remove) {
        if (pos == null || targets == null) return;
        markSelfTarget(targets, pos, until, remove);
        String id = path == null ? "" : path;
        boolean vertical = id.endsWith("_door") && !id.endsWith("trapdoor")
            || id.contains("sunflower") || id.contains("lilac") || id.contains("rose_bush")
            || id.contains("peony") || id.contains("tall_grass") || id.contains("large_fern")
            || id.contains("pitcher_plant");
        if (vertical) {
            markSelfTarget(targets, pos.above(), until, remove);
            markSelfTarget(targets, pos.below(), until, remove);
        }
        if (id.endsWith("_bed")) {
            markSelfTarget(targets, pos.offset(1, 0, 0), until, remove);
            markSelfTarget(targets, pos.offset(-1, 0, 0), until, remove);
            markSelfTarget(targets, pos.offset(0, 0, 1), until, remove);
            markSelfTarget(targets, pos.offset(0, 0, -1), until, remove);
        }
    }

    private static void markSelfTarget(Map<Long, Long> targets, BlockPos pos, long until, boolean remove) {
        if (remove) targets.remove(pos.asLong());
        else targets.put(pos.asLong(), until);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {

            if (remove.profileIds().size() < 4) {
                for (UUID id : remove.profileIds()) observations.offer(Observation.tabRemove(id));
            }
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info
            && info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)) {
            int unlisted = 0;
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                if (entry != null && !entry.listed()) unlisted++;
            }

            if (unlisted > 0 && unlisted < 4) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                    if (entry != null && !entry.listed()) observations.offer(Observation.tabHide(entry.profileId()));
                }
            }
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info
            && info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
            handleGamemodeUpdates(info);
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            String text = chat.content() == null ? "" : chat.content().getString();
            if (!text.isBlank()) observations.offer(Observation.systemChat(text));
            String departedName = departedPlayerName(chat.content());
            if (!departedName.isBlank()) observations.offer(Observation.playerLeft(departedName));
        } else if (packet instanceof ClientboundCommandSuggestionsPacket suggestions
            && completionRequestIds.remove(suggestions.id())) {

            List<String> names = new ArrayList<>();
            for (Suggestion suggestion : suggestions.toSuggestions().getList()) {
                String text = suggestion.getText();
                if (text != null && !text.isBlank()) names.add(text.trim());
            }
            pendingCompletionNames = names;
            return true;
        } else if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            observations.offer(Observation.entityMetadata(metadata.id()));
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            observations.offer(cameraObservation(position));
        } else if (packet instanceof ClientboundExplodePacket explosion) {
            observations.offer(Observation.explosion(explosion.center(), explosion.radius()));
        } else if (packet instanceof ClientboundLevelEventPacket levelEvent
            && levelEvent.getType() == LevelEvent.PARTICLES_DESTROY_BLOCK) {

            BlockState broken = Block.stateById(levelEvent.getData());
            Identifier id = broken == null ? null : BuiltInRegistries.BLOCK.getKey(broken.getBlock());
            observations.offer(Observation.block(ObservationType.BLOCK_BREAK, levelEvent.getPos(),
                id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundBlockDestructionPacket destruction) {
            observations.offer(Observation.blockActor(ObservationType.BLOCK_DIG, destruction.getPos(),
                destruction.getId(), destruction.getProgress()));
        } else if (packet instanceof ClientboundAnimatePacket animation
            && (animation.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
                || animation.getAction() == ClientboundAnimatePacket.SWING_OFF_HAND)) {
            observations.offer(Observation.entityAction(ObservationType.ENTITY_SWING,
                animation.getId(), animation.getAction()));
        } else if (packet instanceof ClientboundSoundEntityPacket sound) {
            observations.offer(Observation.entitySound(sound.getId(), soundId(sound.getSound().value().location())));
        } else if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particles.getParticle().getType());
            observations.offer(Observation.position(ObservationType.PARTICLE, particles.getX(), particles.getY(),
                particles.getZ(), id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundBlockEventPacket blockEvent) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(blockEvent.getBlock());
            observations.offer(Observation.block(ObservationType.BLOCK_EVENT, blockEvent.getPos(),
                id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            observations.offer(Observation.chunk(chunk.getX(), chunk.getZ()));
        }
        return false;
    }

    @Override
    public void onSoundPacket(ClientboundSoundPacket packet) {
        if (packet == null || packet.getSound() == null || packet.getSound().value() == null) return;
        String id = soundId(packet.getSound().value().location());
        observations.offer(Observation.position(ObservationType.POSITIONAL_SOUND, packet.getX(), packet.getY(),
            packet.getZ(), id));
        if (sensorOn("block-sensor") && packet.getSource() == SoundSource.BLOCKS && id.endsWith(".place")) {
            offerRawPlaceSound(new RawPlaceSound(new Vec3(packet.getX(), packet.getY(), packet.getZ()), id,
                packet.getVolume(), packet.getPitch(), System.currentTimeMillis()));
        }
    }

    public static void observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet) {
        AntiVanishModule module = instance();
        if (module == null || !module.isEnabled() || !module.sensorOn("block-sensor")
            || packet == null || MC.level == null) return;
        module.observeBlockTransition(packet.getPos(), packet.getBlockState());
    }

    private void observeBlockTransition(BlockPos pos, BlockState after) {
        if (pos == null || after == null || MC.level == null) return;
        BlockState before = MC.level.getBlockState(pos);
        if (before == null || after == null || before.equals(after)) return;

        Identifier beforeKey = BuiltInRegistries.BLOCK.getKey(before.getBlock());
        Identifier afterKey = BuiltInRegistries.BLOCK.getKey(after.getBlock());
        String beforeId = beforeKey == null ? "" : beforeKey.toString();
        String afterId = afterKey == null ? "" : afterKey.toString();

        if (after.isAir()) {
            if (!before.isAir()) {
                offerBlockTransition(new BlockTransition(pos.immutable(), afterId, beforeId, "",
                    0.0F, 0.0F, true, System.currentTimeMillis()));
            }
            return;
        }
        if (before.getBlock() == after.getBlock()) {

            return;
        }
        boolean soundCandidate = AntiVanishHeuristics.crediblePlacementTransition(beforeId, afterId, before.canBeReplaced());
        boolean anonymousCandidate = AntiVanishHeuristics.credibleAnonymousPlacementTransition(beforeId, afterId);
        if (!soundCandidate && !anonymousCandidate) return;
        String expectedSound = after.getSoundType().getPlaceSound().location().toString();
        float expectedVolume = (after.getSoundType().getVolume() + 1.0F) / 2.0F;
        float expectedPitch = after.getSoundType().getPitch() * 0.8F;
        offerBlockTransition(new BlockTransition(pos.immutable(), afterId, beforeId, expectedSound,
            expectedVolume, expectedPitch, false, System.currentTimeMillis()));
    }

    private void offerBlockTransition(BlockTransition transition) {
        if (transition == null) return;
        if (blockTransitionCount.incrementAndGet() > 1_024) {
            blockTransitionCount.updateAndGet(value -> Math.max(0, value - 1));
            return;
        }
        blockTransitions.offer(transition);
    }

    private void offerRawPlaceSound(RawPlaceSound sound) {
        if (sound == null) return;
        if (rawPlaceSoundCount.incrementAndGet() > 1_024) {
            rawPlaceSoundCount.updateAndGet(value -> Math.max(0, value - 1));
            return;
        }
        rawPlaceSounds.offer(sound);
    }

    public static boolean shouldShowHud() {
        AntiVanishModule module = instance();
        return module != null && module.isEnabled() && module.bool("hud-list") && module.hasHudContent();
    }

    private boolean hasHudContent() {
        long now = System.currentTimeMillis();
        if (now < criticalUntilMs) return true;
        for (Detection detection : detections.values()) {
            if (detection.expiresAt > now && detectionWorthShowing(detection)) return true;
        }
        return false;
    }

    private static boolean detectionWorthShowing(Detection detection) {
        if (detection == null) return false;
        String name = detection.name == null ? "" : detection.name.trim();
        if (!name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name)
            && !"CRITICAL".equalsIgnoreCase(name)) {
            return true;
        }
        String reason = detection.reason == null ? "" : detection.reason.toLowerCase(Locale.ROOT);
        return reason.contains("rank detection:");
    }

    private final Map<String, String> tagByName = new HashMap<>();
    private long lastTagScanMs = 0L;

    private void refreshTags() {
        long now = System.currentTimeMillis();
        if (now - lastTagScanMs < 750L) return;
        lastTagScanMs = now;
        try {
            Map<String, String> next = new HashMap<>();
            for (AutismPlayerScanner.ScannedPlayer p : AutismPlayerScanner.scan(MC)) {
                if (p.hasPrefix()) next.put(p.name().toLowerCase(Locale.ROOT), p.prefix());
            }
            tagByName.clear();
            tagByName.putAll(next);
        } catch (Throwable ignored) {  }
    }

    public static String hudTag(HudEntry entry) {
        if (entry == null) return "WATCH";
        if ("CRITICAL".equalsIgnoreCase(entry.name())) return "ALERT";
        String reason = entry.reason() == null ? "" : entry.reason();
        String lower = reason.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("rank detection:");
        if (idx >= 0) {
            AntiVanishModule module = instance();
            String name = entry.name() == null ? "" : entry.name().trim();
            if (module != null && !name.isBlank()) {
                String glyph = module.tagByName.get(name.toLowerCase(Locale.ROOT));
                if (glyph != null && !glyph.isBlank()) return glyph.length() <= 12 ? glyph : glyph.substring(0, 12);
            }
            String word = reason.substring(idx + "rank detection:".length()).trim();
            if (!word.isBlank()) {
                String up = word.toUpperCase(Locale.ROOT);
                return up.length() <= 12 ? up : up.substring(0, 12);
            }
            return "RANK";
        }
        if (lower.startsWith("vanish event")) return "VANISH";
        if (lower.startsWith("invisible entity")) return "INVIS";
        if (lower.startsWith("suspicious sound")) return "SOUND";
        if (lower.startsWith("camera aberration")) return "CAMERA";
        if (lower.startsWith("ghost particle")) return "PARTICLE";
        if (lower.startsWith("block")) return "BLOCK";
        if (lower.startsWith("chunk")) return "CHUNK";
        return "WATCH";
    }

    public static String hudValue(HudEntry entry) {
        if (entry == null) return "Staff";
        if ("CRITICAL".equalsIgnoreCase(entry.name())) {
            AntiVanishModule module = instance();
            String summary = module == null ? "" : module.criticalSummary;
            if (summary == null || summary.isBlank()) return "watching";
            return summary;
        }
        return compactHudName(entry);
    }

    private static String shortSignal(SignalType type) {
        return switch (type) {
            case VANISH -> "Vanish";
            case CAMERA -> "Camera";
            case INVISIBLE -> "Invisible";
            case PARTICLE -> "Particles";
            case SOUND -> "Sounds";
            case BLOCK -> "Blocks";
            case CHUNK -> "Chunks";
        };
    }

    public static boolean criticalActive() {
        AntiVanishModule module = instance();
        return module != null && module.isEnabled() && System.currentTimeMillis() < module.criticalUntilMs;
    }

    public static String criticalSummary() {
        AntiVanishModule module = instance();
        return module == null ? "" : module.criticalSummary;
    }

    public static List<HudEntry> hudEntries() {
        AntiVanishModule module = instance();
        return module == null ? List.of() : module.hudSnapshot();
    }

    public static String compactHudName(HudEntry entry) {
        if (entry == null) return "Staff";
        String name = entry.name() == null ? "" : entry.name().trim();
        if (name.isBlank() || "CRITICAL".equalsIgnoreCase(name) || "Unknown".equalsIgnoreCase(name)
            || "You".equalsIgnoreCase(name)) return "Staff";
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    public static String compactHudReason(HudEntry entry) {
        if (entry == null) return "";
        String reason = entry.reason() == null ? "" : entry.reason().trim();
        String lower = reason.toLowerCase(Locale.ROOT);
        if ("CRITICAL".equalsIgnoreCase(entry.name())) return "WATCH";
        if (lower.startsWith("vanish event")) return "Vanish";
        if (lower.startsWith("rank detection")) return "Rank";
        if (lower.startsWith("invisible entity")) return "Invis";
        if (lower.startsWith("entity packet spike")) return "Packets";
        if (lower.startsWith("camera aberration")) return "Camera";
        if (lower.startsWith("ghost particle")) return "Particle";
        if (lower.startsWith("suspicious sound")) return "Sound";
        if (lower.startsWith("block")) return "Block";
        if (lower.startsWith("chunk")) return "Chunk";
        return reason.length() <= 10 ? reason : reason.substring(0, 10);
    }

    private static AntiVanishModule instance() {
        Module module = ModuleRegistry.get("anti-vanish");
        return module instanceof AntiVanishModule antiVanish ? antiVanish : null;
    }

    public static String censusSummary() {
        AntiVanishModule module = instance();
        if (module == null || !module.isEnabled()) return "off";
        return "obs=" + module.observations.size()
            + " known=" + module.knownPlayers.size()
            + " det=" + module.detections.size()
            + " chunks=" + module.seenChunks.size()
            + " pendingVanish=" + module.pendingVanishes.size()
            + " msgs=" + module.recentMessages.size();
    }

    private void updateStationaryState() {
        Vec3 position = MC.player.position();
        if (lastPosition != null) {
            double dx = position.x - lastPosition.x;
            double dz = position.z - lastPosition.z;
            if (dx * dx + dz * dz < 0.0004) stationaryTicks++;
            else stationaryTicks = 0;
        }
        lastPosition = position;
        lastYaw = MC.player.getYRot();
        lastPitch = MC.player.getXRot();
    }

    private Observation cameraObservation(ClientboundPlayerPositionPacket packet) {
        Vec3 base = lastPosition;
        Vec3 target = packet.change().position();
        Set<Relative> relatives = packet.relatives();
        double displacement = Double.POSITIVE_INFINITY;
        if (base != null && target != null) {
            double x = relatives.contains(Relative.X) ? base.x + target.x : target.x;
            double y = relatives.contains(Relative.Y) ? base.y + target.y : target.y;
            double z = relatives.contains(Relative.Z) ? base.z + target.z : target.z;
            displacement = base.distanceTo(new Vec3(x, y, z));
        }
        float targetYaw = relatives.contains(Relative.Y_ROT) ? lastYaw + packet.change().yRot() : packet.change().yRot();
        float targetPitch = relatives.contains(Relative.X_ROT) ? lastPitch + packet.change().xRot() : packet.change().xRot();
        double rotation = Math.hypot(wrapDegrees(targetYaw - lastYaw), targetPitch - lastPitch);
        return Observation.cameraCorrection(displacement, rotation);
    }

    private void drainObservations() {
        for (int i = 0; i < MAX_OBSERVATIONS_PER_TICK; i++) {
            Observation observation = observations.poll();
            if (observation == null) break;
            processObservation(observation);
        }
        while (observations.size() > 4096) observations.poll();
    }

    private void processObservation(Observation observation) {
        switch (observation.type) {
            case TAB_REMOVE -> handleTabRemoval(observation.profileId);
            case TAB_HIDE -> handleTabHidden(observation.profileId);
            case PLAYER_LEFT -> { serverSendsLeaveMessages = true; confirmDeparture(observation.detail); }
            case SYSTEM_CHAT -> cacheRecentMessage(observation.detail);
            case ENTITY_METADATA -> inspectInvisibleEntity(observation.entityId);
            case POSITIONAL_SOUND -> inspectPositionalSound(observation);
            case ENTITY_SOUND -> inspectEntitySound(observation);
            case ENTITY_SWING -> rememberHiddenSwing(observation);
            case PARTICLE -> inspectParticle(observation);
            case BLOCK_DIG -> rememberRemoteDig(observation);
            case BLOCK_EVENT, BLOCK_BREAK -> inspectBlockUpdate(observation);
            case BLOCK_UPDATE -> {  }
            case CHUNK_DATA -> inspectChunk(observation.chunkX, observation.chunkZ);
            case EXPLOSION -> rememberExplosion(observation);
            case CAMERA_CORRECTION -> inspectCameraCorrection(observation);
        }
    }

    private void trackListedPlayers() {
        if (MC.getConnection() == null) return;
        long now = System.currentTimeMillis();
        for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().id() == null) continue;
            UUID uuid = info.getProfile().id();
            if (MC.player != null && MC.player.getUUID().equals(uuid)) continue;
            ambiguousDepartures.remove(uuid);
            listedSinceMs.putIfAbsent(uuid, now);
            String name = info.getProfile().name();
            if (name != null && !knownPlayers.containsKey(uuid)) {
                knownPlayers.put(uuid, new KnownPlayer(uuid, name, "", "", false));
            }
        }

        if (listedSinceMs.size() > 1024 || knownPlayers.size() > 1024) {
            Set<UUID> connected = new HashSet<>();
            for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
                if (info != null && info.getProfile() != null) connected.add(info.getProfile().id());
            }
            listedSinceMs.keySet().retainAll(connected);
            knownPlayers.entrySet().removeIf(entry -> !connected.contains(entry.getKey()) && !entry.getValue().staff);
        }
    }

    private boolean trustedListed(UUID uuid) {
        Long since = uuid == null ? null : listedSinceMs.get(uuid);
        return since != null && System.currentTimeMillis() - since >= TRUSTED_LISTED_MS;
    }

    private boolean credibleSubject(UUID uuid, String name) {
        return uuid != null
            && AutismPlayerScanner.isUsername(name)
            && trustedListed(uuid)
            && !AutismAntiBot.isConfirmedBot(uuid)
            && passesPlayerFilter(name);
    }

    private boolean sensorOn(String id) {
        return bool("environmental") && bool(id);
    }

    private boolean passesPlayerFilter(String name) {
        if (!bool("player-filter") || name == null) return true;
        for (String watched : list("players")) {
            if (watched.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void handleTabRemoval(UUID uuid) {
        if (!bool("vanish-tracker") || uuid == null || MC.player.getUUID().equals(uuid)) return;

        String name = knownName(uuid);
        if (!credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) return;
        pendingVanishes.removeIf(pending -> pending.uuid.equals(uuid));
        pendingVanishes.addLast(new PendingVanish(uuid, name, tickCounter + 20));
    }

    private void handleTabHidden(UUID uuid) {
        if (!bool("vanish-tracker") || uuid == null || MC.player.getUUID().equals(uuid)) return;

        KnownPlayer known = knownPlayers.get(uuid);
        String name = known != null && known.name != null ? known.name : knownName(uuid);
        if (!credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) return;

        if (recentMessageNames(name)) {
            rememberAmbiguousDeparture(uuid, name, known != null && known.staff);
            return;
        }
        long now = System.currentTimeMillis();
        boolean staff = known != null && known.staff;
        String reason = staff ? "Vanish Event: staff hidden from TAB" : "Vanish Event: hidden from TAB";
        upsertDetection(uuid.toString(), name, reason, 100, now + DETECTION_TTL_MS);
        addSignal(SignalType.VANISH, name, reason, 100, 15_000L, true);
    }

    private String knownName(UUID uuid) {
        KnownPlayer known = knownPlayers.get(uuid);
        if (known != null && known.name != null && !known.name.isBlank()) return known.name;
        if (MC.getConnection() != null) {
            PlayerInfo info = MC.getConnection().getPlayerInfo(uuid);
            if (info != null && info.getProfile() != null) return info.getProfile().name();
        }
        return null;
    }

    private void confirmDeparture(String displayedName) {
        long now = System.currentTimeMillis();
        for (KnownPlayer known : knownPlayers.values()) {
            if (!AntiVanishText.containsPlayerName(displayedName, known.name)) continue;
            boolean hadPending = pendingVanishes.stream().anyMatch(candidate -> candidate.uuid.equals(known.uuid));
            if (hadPending) rememberAmbiguousDeparture(known.uuid, known.name, known.staff);
            confirmedDepartures.put(known.uuid, now + 5_000L);
            pendingVanishes.removeIf(pending -> pending.uuid.equals(known.uuid));
            Detection detection = detections.get(known.uuid.toString());
            if (detection != null && tabDepartureReason(detection.reason)) {
                detections.remove(known.uuid.toString());
            }
            signals.removeIf(signal -> signal.type == SignalType.VANISH
                && signal.subject.equalsIgnoreCase(known.name)
                && tabDepartureReason(signal.reason));
        }
    }

    private void processPendingVanishes() {
        while (!pendingVanishes.isEmpty() && pendingVanishes.peekFirst().dueTick <= tickCounter) {
            PendingVanish pending = pendingVanishes.removeFirst();
            if (MC.getConnection().getPlayerInfo(pending.uuid) != null) continue;
            long now = System.currentTimeMillis();
            if (confirmedDepartures.getOrDefault(pending.uuid, 0L) > now) continue;
            if (recentMessageNames(pending.name)) {
                serverSendsLeaveMessages = true;
                KnownPlayer known = knownPlayers.get(pending.uuid);
                rememberAmbiguousDeparture(pending.uuid, pending.name, known != null && known.staff);
                continue;
            }
            if (AutismAntiBot.isConfirmedBot(pending.uuid)) continue;
            Player remaining = MC.level.getPlayerByUUID(pending.uuid);
            KnownPlayer known = knownPlayers.get(pending.uuid);
            boolean staff = known != null && known.staff;
            if (remaining != null && !remaining.isRemoved()) {

                String reason = "Vanish Event: entity remained";
                upsertDetection(pending.uuid.toString(), pending.name, reason, 100, now + DETECTION_TTL_MS);
                addSignal(SignalType.VANISH, pending.name, reason, 100, 10_000L, true);
            } else {

                int score = silentTabRemovalScore(staff, serverSendsLeaveMessages);
                String reason = staff
                    ? "Vanish Event: staff left TAB silently"
                    : "Vanish Event: silent TAB disappearance";
                upsertDetection(pending.uuid.toString(), pending.name, reason, score, now + DETECTION_TTL_MS);
                addSignal(SignalType.VANISH, pending.name, reason, score, 10_000L, true);
            }
        }
    }

    static int silentTabRemovalScore(boolean staff, boolean serverSendsLeaveMessages) {
        return staff || serverSendsLeaveMessages ? 100 : 70;
    }

    static boolean tabDepartureReason(String reason) {
        if (reason == null) return false;
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.startsWith("vanish event:")
            && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
    }

    private void rememberAmbiguousDeparture(UUID uuid, String name, boolean staff) {
        if (uuid == null || name == null || name.isBlank()) return;
        ambiguousDepartures.put(uuid, new AmbiguousDeparture(name, staff,
            System.currentTimeMillis() + AMBIGUOUS_DEPARTURE_TTL_MS));
    }

    private void cacheRecentMessage(String text) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        recentMessages.addLast(new RecentMessage(text, now));
        while (!recentMessages.isEmpty()
            && (now - recentMessages.peekFirst().atMs > RECENT_MESSAGE_TTL_MS || recentMessages.size() > 64)) {
            recentMessages.removeFirst();
        }
    }

    private boolean recentMessageNames(String name) {
        if (name == null || name.isBlank()) return false;
        long now = System.currentTimeMillis();
        for (RecentMessage message : recentMessages) {
            if (now - message.atMs > RECENT_MESSAGE_TTL_MS) continue;
            if (AntiVanishText.looksLikeLeaveMessage(message.text, name)) return true;
        }
        return false;
    }

    private void sendCompletionProbe() {
        if (MC.getConnection() == null) return;
        String command = text("probe-command");
        if (command == null || command.isBlank()) command = "minecraft:msg";
        int id = nextCompletionId++;
        if (nextCompletionId > 40000) nextCompletionId = 30000;
        completionRequestIds.add(id);
        while (completionRequestIds.size() > 8) completionRequestIds.remove(completionRequestIds.iterator().next());
        try {
            MC.getConnection().send(new ServerboundCommandSuggestionPacket(id, command.trim() + " "));
        } catch (Throwable ignored) {

        }
    }

    private void processCompletionProbe() {
        List<String> current = pendingCompletionNames;
        if (current == null) return;
        pendingCompletionNames = null;
        if (!bool("completion-probe") || MC.getConnection() == null || MC.player == null) return;
        Set<String> tabNames = new HashSet<>();
        for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
            }
        }
        String self = MC.player.getName().getString();
        List<String> hidden = new ArrayList<>();
        for (String name : current) {
            if (!AntiVanishText.isPlausiblePlayerName(name)) continue;
            if (name.equalsIgnoreCase(self)) continue;
            if (!passesPlayerFilter(name)) continue;
            if (tabNames.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (recentMessageNames(name)) continue;
            hidden.add(name);
        }

        if (hidden.isEmpty() || hidden.size() > 3) return;
        long now = System.currentTimeMillis();
        for (String name : hidden) {
            String reason = "Vanish Event: hidden but targetable";
            upsertDetection(name, name, reason, 90, now + DETECTION_TTL_MS);
            addSignal(SignalType.VANISH, name, reason, 90, 10_000L, true);
        }
    }

    private static String departedPlayerName(Component component) {
        if (component == null || !(component.getContents() instanceof TranslatableContents translated)
            || !"multiplayer.player.left".equals(translated.getKey())) return "";
        Object[] args = translated.getArgs();
        if (args.length == 0 || args[0] == null) return "";
        return args[0] instanceof Component name ? name.getString() : String.valueOf(args[0]);
    }

    private void scanInvisiblePlayers() {
        double rangeSq = sensorRangeSq();
        for (Player player : MC.level.players()) {
            if (player == null || player == MC.player || !player.isInvisible()) continue;
            if (player.distanceToSqr(MC.player) > rangeSq) continue;
            if (!realPlayer(player)) continue;
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            triggerSensor(SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5_000L);
        }
    }

    private void inspectInvisibleEntity(int entityId) {
        if (!sensorOn("invisible-sensor")) return;
        Entity entity = MC.level.getEntity(entityId);
        if (!(entity instanceof Player player) || player == MC.player || !player.isInvisible()) return;
        if (player.distanceToSqr(MC.player) > sensorRangeSq()) return;
        if (!realPlayer(player)) return;
        String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
        triggerSensor(SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5_000L);
    }

    private boolean realPlayer(Player player) {
        String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
        return credibleSubject(player.getUUID(), name);
    }

    private void rememberHiddenSwing(Observation observation) {
        Entity entity = MC.level == null ? null : MC.level.getEntity(observation.entityId);
        if (!(entity instanceof Player player)) return;
        String subject = credibleHiddenSubject(player);
        if (subject.isBlank()) return;
        recentHiddenSwings.put(observation.entityId, new HiddenSwing(observation.entityId, subject,
            player.getEyePosition(), player.getLookAngle().normalize(), System.currentTimeMillis() + 750L));
    }

    private void rememberRemoteDig(Observation observation) {
        int progress;
        try {
            progress = Integer.parseInt(observation.detail);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (progress < 0 || observation.entityId == localPlayerId || MC.level == null) return;
        Entity entity = MC.level.getEntity(observation.entityId);
        if (!(entity instanceof Player player)) return;
        String subject = credibleHiddenSubject(player);
        Vec3 source = observation.position();
        if (subject.isBlank() || !aimedAt(player.getEyePosition(), player.getLookAngle(), source)) return;
        recentRemoteDigs.put(BlockPos.containing(source).asLong(),
            new RemoteDig(observation.entityId, subject, System.currentTimeMillis() + REMOTE_DIG_TTL_MS));
    }

    private String credibleHiddenSubject(Player player) {
        if (player == null || player == MC.player || !realPlayer(player)) return "";
        UUID uuid = player.getUUID();
        KnownPlayer known = knownPlayers.get(uuid);
        boolean staff = known != null && known.staff;
        boolean listed = false;
        if (MC.getConnection() != null) {
            for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
                if (info != null && info.getProfile() != null && uuid.equals(info.getProfile().id())) {
                    listed = true;
                    break;
                }
            }
        }

        if (listed && (!staff || !player.isInvisible())) return "";
        String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
        return name == null ? "" : name;
    }

    private HiddenSwing hiddenSwingNear(Vec3 source, long now) {
        HiddenSwing best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        var iterator = recentHiddenSwings.entrySet().iterator();
        while (iterator.hasNext()) {
            HiddenSwing swing = iterator.next().getValue();
            if (swing.expiresAt < now) {
                iterator.remove();
                continue;
            }
            double distance = swing.eye.distanceToSqr(source);
            if (distance < bestDistance && aimedAt(swing.eye, swing.look, source)) {
                best = swing;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean aimedAt(Vec3 eye, Vec3 look, Vec3 source) {
        if (eye == null || look == null || source == null) return false;
        Vec3 delta = source.subtract(eye);
        double distanceSq = delta.lengthSqr();
        if (distanceSq < 0.01 || distanceSq > 42.25) return false;
        return look.normalize().dot(delta.normalize()) >= 0.80;
    }

    private void inspectPositionalSound(Observation observation) {
        if (!sensorOn("sound-sensor") || !AntiVanishHeuristics.suspiciousSound(observation.detail)) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (!nearPlayer(source) || hasVisibleCause(source) || isExplosionRelated(source)
            || isPoweredMechanism(source, observation.detail) || now - lastLocalActionMs < 1_000L
            || (selfContainerActive() && isContainerSignal(observation.detail))) return;
        triggerSensor(SignalType.SOUND, locatedSubject(source), "Suspicious Sound: " + shortId(observation.detail), 14, 3_000L);
    }

    private void inspectEntitySound(Observation observation) {
        if (!sensorOn("sound-sensor") || !AntiVanishHeuristics.suspiciousSound(observation.detail)) return;
        Entity entity = MC.level.getEntity(observation.entityId);
        if (entity instanceof Player player && player != MC.player && player.isInvisible()
            && player.distanceToSqr(MC.player) <= sensorRangeSq() && realPlayer(player)) {
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            triggerSensor(SignalType.SOUND, name, "Suspicious Sound: invisible source", 16, 3_000L);
        }
    }

    private void inspectParticle(Observation observation) {
        if (!sensorOn("particle-sensor") || !AntiVanishHeuristics.suspiciousParticle(observation.detail)) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (!nearPlayer(source) || hasVisibleCause(source) || isExplosionRelated(source)
            || now - lastLocalActionMs < 900L || hasAmbientParticleSource(source, observation.detail)) return;
        String particle = shortId(observation.detail);

        if ((particle.equals("block") || particle.contains("smoke")) && nearSelf(source, 6.25)) return;
        if ((particle.equals("block") || particle.contains("smoke")) && !particleBurstReady(particle, now)) return;
        triggerSensor(SignalType.PARTICLE, locatedSubject(source), "Ghost Particle: " + shortId(observation.detail), 16, 3_000L);
    }

    private void inspectBlockUpdate(Observation observation) {
        if (!sensorOn("block-sensor")) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (observation.type == ObservationType.BLOCK_BREAK) {
            recentBreakEffects.put(BlockPos.containing(source).asLong(),
                new BreakEffect(observation.detail, now));
        }
        if (!blockWorldStable(source, now) || !nearPlayer(source) || hasVisibleBlockCause(source) || isExplosionRelated(source)
            || isPoweredMechanism(source, observation.detail)
            || recentlySelfBroke(source)) return;
        String label = classifyBlockChange(observation.type, observation.detail);
        if (label == null) return;
        if (label.equals("Block Interaction") && villagerToggledDoor(observation.detail, source)) return;
        if (label.equals("Block Interaction") && selfContainerActive() && isContainerSignal(observation.detail)) return;
        HiddenSwing swing = hiddenSwingNear(source, now);
        if (label.equals("Block Break")) {
            long key = BlockPos.containing(source).asLong();
            RemoteDig dig = recentRemoteDigs.get(key);
            String actorSubject = dig != null && dig.expiresAt >= now
                ? dig.subject
                : swing == null ? "" : swing.subject;

            if (actorSubject.isBlank()) return;
            String subject = blockEvidenceSubject(actorSubject, locatedSubject(source));
            int actorId = dig != null && dig.expiresAt >= now ? dig.entityId : swing == null ? -1 : swing.entityId;

            pendingBreaks.addLast(new PendingBreak(source, observation.detail, subject, actorId, now));
            while (pendingBreaks.size() > 32) pendingBreaks.removeFirst();
            return;
        }

        if (swing == null) return;
        String dedupKey = label + "@" + BlockPos.containing(source).asLong();
        triggerSensor(SignalType.BLOCK, swing.subject, dedupKey,
            label + ": " + shortId(observation.detail), 13, 800L);
    }

    private static String classifyBlockChange(ObservationType type, String id) {
        String path = AntiVanishHeuristics.path(id);
        if (type == ObservationType.BLOCK_BREAK) {
            return AntiVanishHeuristics.credibleBreakBlock(id) ? "Block Break" : null;
        }
        if (type == ObservationType.BLOCK_EVENT) {
            return AntiVanishHeuristics.blockEventInteraction(path) ? "Block Interaction" : null;
        }
        return null;
    }

    private boolean villagerToggledDoor(String blockId, Vec3 source) {
        String path = AntiVanishHeuristics.path(blockId);
        boolean doorLike = (path.contains("door") && !path.contains("trapdoor")) || path.contains("fence_gate");
        if (!doorLike || source == null || MC.level == null) return false;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof Villager && entity.position().distanceToSqr(source) <= 9.0) return true;
        }
        return false;
    }

    private void drainBlockEvidence() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 128; i++) {
            RawPlaceSound sound = rawPlaceSounds.poll();
            if (sound == null) break;
            rawPlaceSoundCount.updateAndGet(value -> Math.max(0, value - 1));
            recentPlaceSounds.addLast(new PlaceSound(sound.pos, sound.soundId, sound.volume, sound.pitch, sound.timeMs));
        }
        for (int i = 0; i < 128; i++) {
            BlockTransition transition = blockTransitions.poll();
            if (transition == null) break;
            blockTransitionCount.updateAndGet(value -> Math.max(0, value - 1));
            Vec3 source = Vec3.atCenterOf(transition.pos);
            long key = transition.pos.asLong();
            if (transition.removal) {
                recentAirUpdates.put(key, new Removal(transition.previousId, transition.timeMs));
                if (AntiVanishHeuristics.credibleAnonymousBreakTransition(transition.previousId, transition.nextId)
                    && blockWorldStable(source, now) && nearPlayer(source) && !recentlySelfBroke(source)
                    && !hasVisibleBlockCause(source) && !hasAutomatedBlockSource(source)
                    && !isExplosionRelated(source) && !hasNearbyFireOrLava(source)) {
                    pendingAnonymousBreaks.addLast(new PendingAnonymousBreak(source, transition.previousId,
                        transition.nextId, transition.timeMs));
                    while (pendingAnonymousBreaks.size() > 64) pendingAnonymousBreaks.removeFirst();
                }
                continue;
            }
            if (!blockWorldStable(source, now) || !nearPlayer(source) || hasVisibleBlockCause(source) || hasAutomatedBlockSource(source)
                || isExplosionRelated(source)
                || hasNearbyFluidOrFire(source) || recentlySelfPlaced(source)) continue;
            pendingPlaces.addLast(new PendingPlace(source, transition.previousId, transition.nextId, transition.expectedSound,
                transition.expectedVolume, transition.expectedPitch, transition.timeMs));
            while (pendingPlaces.size() > 64) pendingPlaces.removeFirst();
        }
        trimPlaceSounds(now);
    }

    private void trimPlaceSounds(long now) {
        while (!recentPlaceSounds.isEmpty() && now - recentPlaceSounds.peekFirst().timeMs > PLACE_SOUND_MATCH_MS + 300L) {
            recentPlaceSounds.removeFirst();
        }
        while (recentPlaceSounds.size() > 64) recentPlaceSounds.removeFirst();
    }

    private void processPendingPlaces() {
        if (pendingPlaces.isEmpty()) return;
        long now = System.currentTimeMillis();
        trimPlaceSounds(now);
        int pending = pendingPlaces.size();
        for (int i = 0; i < pending; i++) {
            PendingPlace place = pendingPlaces.pollFirst();
            if (place == null) break;
            HiddenSwing swing = hiddenSwingNear(place.pos, now);
            boolean heard = hasMatchingPlaceSound(place);
            if (heard) {

                if (swing != null && validAnonymousBlockContext(place.pos, false)
                    && blockStillMatches(place.pos, place.detail)) {
                    recentHiddenSwings.remove(swing.entityId, swing);
                    String dedupKey = "Block Place@" + BlockPos.containing(place.pos).asLong();
                    triggerSensor(SignalType.BLOCK, swing.subject, dedupKey,
                        "Block Place: " + shortId(place.detail), 13, 800L);
                }
                continue;
            }
            if (now - place.timeMs < PLACE_SOUND_MATCH_MS) {
                pendingPlaces.addLast(place);
                continue;
            }
            if (!anonymousPlacementReady(place.previousId, place.detail, false,
                blockStillMatches(place.pos, place.detail)) || !validAnonymousBlockContext(place.pos, false)) continue;
            String subject = anonymousBlockSubject(place.pos);
            String dedupKey = "Block Place@" + BlockPos.containing(place.pos).asLong();
            triggerSensor(SignalType.BLOCK, subject, dedupKey,
                "Block Place: " + shortId(place.detail), 13, 800L);
        }
    }

    private boolean hasMatchingPlaceSound(PendingPlace place) {
        BlockPos expectedPos = BlockPos.containing(place.pos);
        for (PlaceSound sound : recentPlaceSounds) {
            BlockPos soundPos = BlockPos.containing(sound.pos);
            int manhattan = Math.abs(soundPos.getX() - expectedPos.getX())
                + Math.abs(soundPos.getY() - expectedPos.getY())
                + Math.abs(soundPos.getZ() - expectedPos.getZ());
            if (manhattan > 1) continue;
            if (!AntiVanishHeuristics.sameEvidenceWindow(sound.timeMs, place.timeMs, PLACE_SOUND_MATCH_MS)) continue;
            if (!AntiVanishHeuristics.matchingPlaceSound(place.expectedSound, sound.soundId)) continue;
            if (Math.abs(place.expectedVolume - sound.volume) > 0.0001F
                || Math.abs(place.expectedPitch - sound.pitch) > 0.0001F) continue;
            return true;
        }
        return false;
    }

    private void processPendingBreaks() {
        if (pendingBreaks.isEmpty()) return;
        long now = System.currentTimeMillis();
        int pending = pendingBreaks.size();
        for (int i = 0; i < pending; i++) {
            PendingBreak broken = pendingBreaks.pollFirst();
            if (broken == null) break;
            if (now - broken.timeMs > BREAK_MATCH_MS) continue;
            long key = BlockPos.containing(broken.pos).asLong();
            Removal removal = recentAirUpdates.get(key);
            if (removal == null || !AntiVanishHeuristics.matchingBreakEvidence(
                broken.detail, broken.timeMs, removal.previousId, removal.timeMs, BREAK_MATCH_MS)) {
                pendingBreaks.addLast(broken);
                continue;
            }
            if (hasVisibleBlockCause(broken.pos) || isExplosionRelated(broken.pos)) continue;
            recentAirUpdates.remove(key);
            recentRemoteDigs.remove(key);
            if (broken.actorId >= 0) recentHiddenSwings.remove(broken.actorId);
            String dedupKey = "Block Break@" + key;
            triggerSensor(SignalType.BLOCK, broken.subject, dedupKey,
                "Block Break: " + shortId(broken.detail), 13, 800L);
        }
    }

    private void processPendingAnonymousBreaks() {
        if (pendingAnonymousBreaks.isEmpty()) return;
        long now = System.currentTimeMillis();
        int pending = pendingAnonymousBreaks.size();
        for (int i = 0; i < pending; i++) {
            PendingAnonymousBreak broken = pendingAnonymousBreaks.pollFirst();
            if (broken == null) break;
            if (now - broken.timeMs < BREAK_MATCH_MS) {
                pendingAnonymousBreaks.addLast(broken);
                continue;
            }
            long key = BlockPos.containing(broken.pos).asLong();
            BreakEffect effect = recentBreakEffects.get(key);
            boolean matchingEffect = effect != null && AntiVanishHeuristics.matchingBreakEffect(
                effect.blockId, effect.timeMs, broken.previousId, broken.timeMs, BREAK_MATCH_MS);
            if (!anonymousBreakReady(broken.previousId, broken.nextId, matchingEffect,
                blockStillMatches(broken.pos, broken.nextId)) || !validAnonymousBlockContext(broken.pos, true)) continue;
            recentAirUpdates.remove(key);
            String subject = anonymousBlockSubject(broken.pos);
            String dedupKey = "Block Break@" + key;
            triggerSensor(SignalType.BLOCK, subject, dedupKey,
                "Block Break: " + shortId(broken.previousId), 13, 800L);
        }
    }

    private void inspectChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        long now = System.currentTimeMillis();
        blockChunkQuietUntil.put(key, now + 750L);
        boolean resend = !seenChunks.add(key);
        if (!sensorOn("chunk-sensor") || !resend || tickCounter < 100 || stationaryTicks < 40
            || System.currentTimeMillis() - lastServerCorrectionMs < 5_000L) return;
        int playerChunkX = ((int) Math.floor(MC.player.getX())) >> 4;
        int playerChunkZ = ((int) Math.floor(MC.player.getZ())) >> 4;
        if (Math.abs(chunkX - playerChunkX) > 2 || Math.abs(chunkZ - playerChunkZ) > 2) return;
        Deque<Long> repeats = chunkResends.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        repeats.addLast(now);
        while (!repeats.isEmpty() && now - repeats.peekFirst() > 10_000L) repeats.removeFirst();
        if (repeats.size() < 2) return;
        repeats.clear();
        triggerSensor(SignalType.CHUNK, "near you", "Chunk Re-send: " + chunkX + ", " + chunkZ, 15, 15_000L);
    }

    private boolean recentlySelfBroke(Vec3 source) {
        if (source == null) return false;
        Long until = selfBrokenBlocks.get(BlockPos.containing(source).asLong());
        return until != null && until > System.currentTimeMillis();
    }

    private boolean recentlySelfPlaced(Vec3 source) {
        if (source == null) return false;
        Long until = selfPlacedBlocks.get(BlockPos.containing(source).asLong());
        return until != null && until > System.currentTimeMillis();
    }

    private boolean validAnonymousBlockContext(Vec3 source, boolean removal) {
        long now = System.currentTimeMillis();
        return blockWorldStable(source, now)
            && nearPlayer(source)
            && !hasVisibleBlockCause(source)
            && !hasAutomatedBlockSource(source)
            && !isExplosionRelated(source)
            && !(removal ? hasNearbyFireOrLava(source) : hasNearbyFluidOrFire(source))
            && !(removal ? recentlySelfBroke(source) : recentlySelfPlaced(source));
    }

    private boolean blockStillMatches(Vec3 source, String expectedId) {
        if (source == null || expectedId == null || MC.level == null) return false;
        BlockState current = MC.level.getBlockState(BlockPos.containing(source));
        if (current == null) return false;
        Identifier id = BuiltInRegistries.BLOCK.getKey(current.getBlock());
        return AntiVanishHeuristics.path(id == null ? "" : id.toString())
            .equals(AntiVanishHeuristics.path(expectedId));
    }

    private String anonymousBlockSubject(Vec3 source) {
        long now = System.currentTimeMillis();
        AmbiguousDeparture onlyStaff = null;
        int staffCount = 0;
        var iterator = ambiguousDepartures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AmbiguousDeparture> entry = iterator.next();
            AmbiguousDeparture candidate = entry.getValue();
            if (candidate.expiresAt <= now
                || (MC.getConnection() != null && MC.getConnection().getPlayerInfo(entry.getKey()) != null)) {
                iterator.remove();
                continue;
            }
            if (candidate.staff) {
                staffCount++;
                onlyStaff = candidate;
            }
        }
        if (staffCount == 1) return onlyStaff.name;
        return locatedSubject(source);
    }

    private void inspectCameraCorrection(Observation observation) {
        long now = System.currentTimeMillis();
        lastServerCorrectionMs = now;
        if (!sensorOn("camera-sensor") || stationaryTicks < 15 || tickCounter <= 60) return;
        double displacement = observation.x;
        double rotation = observation.y;
        boolean smallPositionReset = displacement >= 0.02 && displacement <= 1.5;
        boolean cameraJerk = rotation >= 2.0 && rotation <= 45.0;
        if (!smallPositionReset && !cameraJerk) return;
        cameraCorrections.addLast(now);
        while (!cameraCorrections.isEmpty() && now - cameraCorrections.peekFirst() > 4_000L) cameraCorrections.removeFirst();
        if (cameraCorrections.size() < 2) return;
        cameraCorrections.clear();
        String detail = cameraJerk
            ? String.format(Locale.ROOT, "Camera Aberration: %.1f° reset", rotation)
            : String.format(Locale.ROOT, "Camera Aberration: %.2fm reset", displacement);
        triggerSensor(SignalType.CAMERA, "on you", detail, 20, 8_000L);
    }

    private void rememberExplosion(Observation observation) {
        long now = System.currentTimeMillis();
        recentExplosions.addLast(new ExplosionEvent(observation.position(),
            Math.max(2.0, parseDouble(observation.detail, 4.0) + 4.0), now));
        while (recentExplosions.size() > 8) recentExplosions.removeFirst();
    }

    private boolean particleBurstReady(String particle, long now) {
        Deque<Long> burst = weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque<>());
        burst.addLast(now);
        while (!burst.isEmpty() && now - burst.peekFirst() > 2_000L) burst.removeFirst();
        if (burst.size() < 2) return false;
        burst.clear();
        return true;
    }

    private void triggerSensor(SignalType type, String subject, String reason, int weight, long cooldownMs) {
        addSignal(type, subject, subject, reason, weight, cooldownMs, false);
    }

    private void triggerSensor(SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs) {
        addSignal(type, subject, dedupKey, reason, weight, cooldownMs, false);
    }

    private void addSignal(SignalType type, String subject, String reason, int weight, long cooldownMs, boolean instant) {
        addSignal(type, subject, subject, reason, weight, cooldownMs, instant);
    }

    private void addSignal(SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs, boolean instant) {
        long now = System.currentTimeMillis();
        String cooldownKey = type.name() + "|" + dedupKey.toLowerCase(Locale.ROOT);
        long last = signalCooldowns.getOrDefault(cooldownKey, 0L);
        if (now - last < cooldownMs) return;
        signalCooldowns.put(cooldownKey, now);
        signals.addLast(new Signal(type, subject, reason, weight, now));
        upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + DETECTION_TTL_MS);
        lastTrigger = reason;
        announceTrigger(cooldownKey, subject, reason);
        evaluateCritical(instant);
    }

    private void evaluateCritical(boolean instant) {
        long now = System.currentTimeMillis();
        pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<>(SignalType.class);
        for (Signal signal : signals) strongest.merge(signal.type, signal.weight, Math::max);
        currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
        if (!bool("critical-alert")) return;
        if (!instant && (strongest.size() < 2 || currentScore < CRITICAL_SCORE)) return;
        if (now - lastCriticalMs < CRITICAL_COOLDOWN_MS) return;

        lastCriticalMs = now;
        criticalUntilMs = now + 7_000L;

        criticalSummary = strongest.keySet().stream().map(AntiVanishModule::shortSignal).sorted().reduce((a, b) -> a + " + " + b).orElse(lastTrigger);

        SignalType top = strongest.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        AutismNotifications.error(top == null ? "Staff may be watching you" : "Staff watching you: " + shortSignal(top));
        if (bool("alert-sound") && MC.getSoundManager() != null) {
            MC.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
        }
    }

    private void handleGamemodeUpdates(ClientboundPlayerInfoUpdatePacket info) {
        if (!bool("gamemode-alerts")) return;
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
            if (entry == null || entry.gameMode() == null) continue;
            UUID uuid = entry.profileId();
            if (uuid == null || (MC.player != null && MC.player.getUUID().equals(uuid))) continue;
            KnownPlayer known = knownPlayers.get(uuid);
            if (known == null || known.name == null || known.name.isBlank()) continue;
            if (!passesPlayerFilter(known.name)) continue;
            AutismClientMessaging.sendPrefixed("§bGamemode: §f" + known.name + " §7-> " + entry.gameMode().getName());
        }
    }

    private void announceTrigger(String eventKey, String subject, String reason) {
        if (!bool("chat-alerts")) return;
        long now = System.currentTimeMillis();
        Long last = announceCooldowns.get(eventKey);
        if (last != null && now - last < ANNOUNCE_COOLDOWN_MS) return;
        while (!announceTimes.isEmpty() && now - announceTimes.peekFirst() > ANNOUNCE_WINDOW_MS) announceTimes.removeFirst();
        if (announceTimes.size() >= MAX_ANNOUNCE_PER_WINDOW) return;
        announceCooldowns.put(eventKey, now);
        announceTimes.addLast(now);
        boolean located = subject != null && !subject.isBlank()
            && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
        String where = located ? " §7(" + subject + ")" : "";
        AutismClientMessaging.sendPrefixed("§b" + reason + where);
    }

    private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
        Detection existing = detections.get(key);
        if (existing == null) {
            detections.put(key, new Detection(name, reason, score, expiresAt));
            return;
        }
        existing.name = name;
        existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
        if (score >= existing.score) {
            existing.reason = reason;
            existing.score = score;
        }
    }

    private List<HudEntry> hudSnapshot() {
        refreshTags();
        long now = System.currentTimeMillis();
        List<HudEntry> out = new ArrayList<>();
        if (now < criticalUntilMs) out.add(new HudEntry("CRITICAL", criticalSummary, 100));

        Set<String> seenRows = new HashSet<>();
        detections.values().stream()
            .filter(detection -> detection.expiresAt > now)
            .filter(AntiVanishModule::detectionWorthShowing)
            .sorted(Comparator.comparingInt((Detection detection) -> detection.score).reversed()
                .thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER))
            .map(detection -> new HudEntry(detection.name, detection.reason, detection.score))
            .filter(entry -> seenRows.add(hudTag(entry) + '\0' + hudValue(entry)))
            .limit(4)
            .forEach(out::add);
        return List.copyOf(out);
    }

    private void pruneState() {
        long now = System.currentTimeMillis();
        detections.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        announceCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        selfBrokenBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        selfPlacedBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        confirmedDepartures.entrySet().removeIf(entry -> entry.getValue() <= now);
        ambiguousDepartures.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        automatedMechanisms.entrySet().removeIf(entry -> entry.getValue() <= now);
        blockChunkQuietUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        recentRemoteDigs.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        recentAirUpdates.entrySet().removeIf(entry -> now - entry.getValue().timeMs > BREAK_MATCH_MS + 500L);
        recentBreakEffects.entrySet().removeIf(entry -> now - entry.getValue().timeMs > BREAK_MATCH_MS + 500L);
        recentHiddenSwings.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        weakParticleBursts.values().removeIf(burst -> {
            while (!burst.isEmpty() && now - burst.peekFirst() > 2_000L) burst.removeFirst();
            return burst.isEmpty();
        });
        chunkResends.values().removeIf(repeats -> {
            while (!repeats.isEmpty() && now - repeats.peekFirst() > 10_000L) repeats.removeFirst();
            return repeats.isEmpty();
        });
        while (!cameraCorrections.isEmpty() && now - cameraCorrections.peekFirst() > 4_000L) cameraCorrections.removeFirst();
        while (!recentExplosions.isEmpty() && now - recentExplosions.peekFirst().timeMs > 3_000L) recentExplosions.removeFirst();
        pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<>(SignalType.class);
        for (Signal signal : signals) strongest.merge(signal.type, signal.weight, Math::max);
        currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void pruneSignals(long now) {
        while (!signals.isEmpty() && now - signals.peekFirst().timeMs > SIGNAL_WINDOW_MS) signals.removeFirst();
    }

    private boolean nearPlayer(Vec3 source) {
        return source != null && source.distanceToSqr(MC.player.position()) <= sensorRangeSq();
    }

    private boolean blockWorldStable(Vec3 source, long now) {
        if (source == null || tickCounter < 10) return false;
        int chunkX = ((int) Math.floor(source.x)) >> 4;
        int chunkZ = ((int) Math.floor(source.z)) >> 4;
        return blockChunkQuietUntil.getOrDefault(chunkKey(chunkX, chunkZ), 0L) <= now;
    }

    private boolean nearSelf(Vec3 source, double maxDistSq) {
        return source != null && MC.player != null && source.distanceToSqr(MC.player.position()) < maxDistSq;
    }

    private boolean selfContainerActive() {
        return System.currentTimeMillis() - lastContainerActivityMs < CONTAINER_SELF_GRACE_MS;
    }

    private static boolean isContainerSignal(String id) {
        String path = AntiVanishHeuristics.path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    private String locatedSubject(Vec3 source) {
        if (source == null || MC.player == null) return "nearby";
        Vec3 me = MC.player.position();
        double dx = source.x - me.x, dy = source.y - me.y, dz = source.z - me.z;
        long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String dir = compass(dx, dz);
        return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
    }

    static String blockEvidenceSubject(String actorSubject, String locatedSubject) {
        String actor = actorSubject == null ? "" : actorSubject.trim();
        if (!actor.isEmpty()) return actor;
        String located = locatedSubject == null ? "" : locatedSubject.trim();
        return located.isEmpty() ? "nearby" : located;
    }

    static boolean anonymousPlacementReady(String previousId, String nextId,
                                           boolean matchingSound, boolean finalStateMatches) {
        return !matchingSound && finalStateMatches
            && AntiVanishHeuristics.credibleAnonymousPlacementTransition(previousId, nextId);
    }

    static boolean anonymousBreakReady(String previousId, String nextId,
                                       boolean matchingBreakEffect, boolean finalStateMatches) {
        return !matchingBreakEffect && finalStateMatches
            && AntiVanishHeuristics.credibleAnonymousBreakTransition(previousId, nextId);
    }

    private static String compass(double dx, double dz) {
        String ns = dz < -1.0 ? "N" : dz > 1.0 ? "S" : "";
        String ew = dx > 1.0 ? "E" : dx < -1.0 ? "W" : "";
        return ns + ew;
    }

    private boolean hasVisibleCause(Vec3 source) {
        if (source == null) return true;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity == MC.player) continue;

            if (entity instanceof Player player) {
                if (player.isInvisible()) continue;
                if (entity.position().distanceToSqr(source) <= 16.0) return true;
            } else if (entity instanceof Projectile && entity.position().distanceToSqr(source) <= 16.0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVisibleBlockCause(Vec3 source) {
        if (source == null || MC.level == null) return true;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity == null || entity == MC.player) continue;
            if (entity instanceof Player player) {
                if (credibleHiddenSubject(player).isBlank()) {
                    double distanceSq = entity.position().distanceToSqr(source);
                    if (distanceSq <= 9.0 || aimedAt(player.getEyePosition(), player.getLookAngle(), source)) return true;
                }
                continue;
            }
            if (entity instanceof LivingEntity || entity instanceof Projectile
                || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) {
                double distanceSq = entity.position().distanceToSqr(source);
                if ((entity instanceof Projectile || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt)
                    && distanceSq <= 25.0) return true;
                if (entity instanceof LivingEntity && distanceSq <= 36.0 && entityCanModifyBlocks(entity)) return true;
            }
        }
        return false;
    }

    private static boolean entityCanModifyBlocks(Entity entity) {
        Identifier id = entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
        return path.equals("enderman") || path.equals("ravager") || path.equals("wither")
            || path.equals("ender_dragon") || path.equals("zombie") || path.equals("husk")
            || path.equals("drowned") || path.equals("silverfish") || path.equals("sheep")
            || path.equals("rabbit") || path.equals("fox") || path.equals("turtle")
            || path.equals("snow_golem") || path.equals("villager");
    }

    private boolean hasAutomatedBlockSource(Vec3 source) {
        if (source == null || MC.level == null) return true;
        BlockPos center = BlockPos.containing(source);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = MC.level.getBlockState(pos);
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
                    if (path.contains("moving_piston") || path.contains("piston_head")) return true;
                    if (!path.contains("dispenser") && !path.contains("dropper") && !path.contains("piston")) continue;
                    boolean active = MC.level.hasNeighborSignal(pos);
                    if (state.hasProperty(BlockStateProperties.TRIGGERED)) {
                        active |= state.getValue(BlockStateProperties.TRIGGERED);
                    }
                    if (state.hasProperty(BlockStateProperties.EXTENDED)) {
                        active |= state.getValue(BlockStateProperties.EXTENDED);
                    }
                    if (active) return true;
                }
            }
        }

        for (int[] axis : BLOCK_AXES) {
            for (int distance = 2; distance <= 12; distance++) {
                BlockPos pos = center.offset(axis[0] * distance, axis[1] * distance, axis[2] * distance);
                BlockState state = MC.level.getBlockState(pos);
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
                if (!path.contains("piston")) continue;
                if (path.contains("moving_piston") || path.contains("piston_head")
                    || MC.level.hasNeighborSignal(pos)
                    || state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isExplosionRelated(Vec3 source) {
        if (source == null) return false;
        long now = System.currentTimeMillis();
        while (!recentExplosions.isEmpty() && now - recentExplosions.peekFirst().timeMs > 3_000L) recentExplosions.removeFirst();
        for (ExplosionEvent explosion : recentExplosions) {
            if (explosion.center.distanceToSqr(source) <= explosion.radius * explosion.radius) return true;
        }
        return false;
    }

    private boolean hasNearbyFluidOrFire(Vec3 source) {
        return hasNearbyEnvironment(source, true);
    }

    private boolean hasNearbyFireOrLava(Vec3 source) {
        return hasNearbyEnvironment(source, false);
    }

    private boolean hasNearbyEnvironment(Vec3 source, boolean includeWater) {
        if (source == null || MC.level == null) return true;
        BlockPos center = BlockPos.containing(source);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(center.offset(dx, dy, dz)).getBlock());
                    String path = AntiVanishHeuristics.path(id == null ? "" : id.toString());
                    if (path.contains("lava") || path.contains("fire") || (includeWater && path.contains("water"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAmbientParticleSource(Vec3 source, String particleId) {
        if (source == null || !shortId(particleId).contains("smoke")) return false;
        BlockPos center = BlockPos.containing(source);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
                    String path = shortId(id == null ? "" : id.toString());
                    if (path.contains("campfire") || path.contains("furnace") || path.contains("smoker")
                        || path.contains("torch") || path.contains("fire") || path.contains("candle")
                        || path.contains("respawn_anchor")) return true;
                }
            }
        }
        return false;
    }

    private boolean isPoweredMechanism(Vec3 source, String blockOrSoundId) {
        String path = shortId(blockOrSoundId);
        if (!path.contains("door") && !path.contains("trapdoor")) return false;
        BlockPos center = BlockPos.containing(source);
        long now = System.currentTimeMillis();
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = center.offset(0, dy, 0);
            BlockState state = MC.level.getBlockState(pos);
            long key = pos.asLong();
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            if (powered || MC.level.hasNeighborSignal(pos)) {
                automatedMechanisms.put(key, now + 5_000L);
                return true;
            }
            if (automatedMechanisms.getOrDefault(key, 0L) > now) return true;
        }
        return false;
    }

    private double sensorRangeSq() {
        double r = Math.max(8, integer("range"));
        return r * r;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static String soundId(Identifier id) {
        return id == null ? "" : id.toString();
    }

    private static String shortId(String id) {
        return AntiVanishHeuristics.path(id);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void resetRuntime() {
        observations.clear();
        blockTransitions.clear();
        rawPlaceSounds.clear();
        blockTransitionCount.set(0);
        rawPlaceSoundCount.set(0);
        knownPlayers.clear();
        detections.clear();
        signalCooldowns.clear();
        announceCooldowns.clear();
        announceTimes.clear();
        selfBrokenBlocks.clear();
        selfPlacedBlocks.clear();
        confirmedDepartures.clear();
        ambiguousDepartures.clear();
        automatedMechanisms.clear();
        weakParticleBursts.clear();
        chunkResends.clear();
        blockChunkQuietUntil.clear();
        signals.clear();
        cameraCorrections.clear();
        recentExplosions.clear();
        pendingPlaces.clear();
        recentPlaceSounds.clear();
        recentRemoteDigs.clear();
        recentAirUpdates.clear();
        recentBreakEffects.clear();
        pendingBreaks.clear();
        pendingAnonymousBreaks.clear();
        recentHiddenSwings.clear();
        pendingVanishes.clear();
        seenChunks.clear();
        lastLevel = null;
        lastPosition = null;
        stationaryTicks = 0;
        tickCounter = 0;
        localPlayerId = Integer.MIN_VALUE;
        lastLocalActionMs = 0L;
        lastContainerActivityMs = 0L;
        lastServerCorrectionMs = 0L;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        lastCriticalMs = 0L;
        criticalUntilMs = 0L;
        currentScore = 0;
        criticalSummary = "";
        lastTrigger = "";
        recentMessages.clear();
        listedSinceMs.clear();
        serverSendsLeaveMessages = false;
        completionRequestIds.clear();
        pendingCompletionNames = null;
        nextCompletionId = 30000;
    }

    public record HudEntry(String name, String reason, int score) {
    }

    private enum SignalType {
        VANISH("Vanish Event"),
        CAMERA("Camera Aberration"),
        INVISIBLE("Invisible Entity"),
        PARTICLE("Ghost Particles"),
        SOUND("Suspicious Sounds"),
        BLOCK("Block Updates"),
        CHUNK("Chunk Re-sends");

        final String label;

        SignalType(String label) {
            this.label = label;
        }
    }

    private enum ObservationType {
        TAB_REMOVE,
        TAB_HIDE,
        PLAYER_LEFT,
        SYSTEM_CHAT,
        ENTITY_METADATA,
        ENTITY_SWING,
        POSITIONAL_SOUND,
        ENTITY_SOUND,
        PARTICLE,
        BLOCK_EVENT,
        BLOCK_UPDATE,
        BLOCK_BREAK,
        BLOCK_DIG,
        CHUNK_DATA,
        EXPLOSION,
        CAMERA_CORRECTION
    }

    private record Observation(
        ObservationType type,
        UUID profileId,
        int entityId,
        double x,
        double y,
        double z,
        String detail,
        int chunkX,
        int chunkZ
    ) {
        static Observation tabRemove(UUID id) {
            return new Observation(ObservationType.TAB_REMOVE, id, -1, 0, 0, 0, "", 0, 0);
        }

        static Observation tabHide(UUID id) {
            return new Observation(ObservationType.TAB_HIDE, id, -1, 0, 0, 0, "", 0, 0);
        }

        static Observation playerLeft(String name) {
            return new Observation(ObservationType.PLAYER_LEFT, null, -1, 0, 0, 0, name, 0, 0);
        }

        static Observation systemChat(String text) {
            return new Observation(ObservationType.SYSTEM_CHAT, null, -1, 0, 0, 0, text, 0, 0);
        }

        static Observation entityMetadata(int id) {
            return new Observation(ObservationType.ENTITY_METADATA, null, id, 0, 0, 0, "", 0, 0);
        }

        static Observation cameraCorrection(double displacement, double rotation) {
            return new Observation(ObservationType.CAMERA_CORRECTION, null, -1,
                displacement, rotation, 0, "", 0, 0);
        }

        static Observation explosion(Vec3 center, float radius) {
            return new Observation(ObservationType.EXPLOSION, null, -1,
                center.x, center.y, center.z, Float.toString(radius), 0, 0);
        }

        static Observation entitySound(int id, String sound) {
            return new Observation(ObservationType.ENTITY_SOUND, null, id, 0, 0, 0, sound, 0, 0);
        }

        static Observation position(ObservationType type, double x, double y, double z, String detail) {
            return new Observation(type, null, -1, x, y, z, detail, 0, 0);
        }

        static Observation block(ObservationType type, BlockPos pos, String detail) {
            return position(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, detail);
        }

        static Observation blockActor(ObservationType type, BlockPos pos, int entityId, int action) {
            return new Observation(type, null, entityId, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, Integer.toString(action), 0, 0);
        }

        static Observation entityAction(ObservationType type, int entityId, int action) {
            return new Observation(type, null, entityId, 0, 0, 0, Integer.toString(action), 0, 0);
        }

        static Observation chunk(int x, int z) {
            return new Observation(ObservationType.CHUNK_DATA, null, -1, 0, 0, 0, "", x, z);
        }

        Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    private record KnownPlayer(UUID uuid, String name, String rank, String rankSource, boolean staff) {
    }

    private record Signal(SignalType type, String subject, String reason, int weight, long timeMs) {
    }

    private record ExplosionEvent(Vec3 center, double radius, long timeMs) {
    }

    private record RawPlaceSound(Vec3 pos, String soundId, float volume, float pitch, long timeMs) {
    }

    private record PlaceSound(Vec3 pos, String soundId, float volume, float pitch, long timeMs) {
    }

    private record BlockTransition(BlockPos pos, String nextId, String previousId, String expectedSound,
                                   float expectedVolume, float expectedPitch, boolean removal, long timeMs) {
    }

    private record PendingPlace(Vec3 pos, String previousId, String detail, String expectedSound,
                                 float expectedVolume, float expectedPitch, long timeMs) {
    }

    private record Removal(String previousId, long timeMs) {
    }

    private record RemoteDig(int entityId, String subject, long expiresAt) {
    }

    private record HiddenSwing(int entityId, String subject, Vec3 eye, Vec3 look, long expiresAt) {
    }

    private record PendingBreak(Vec3 pos, String detail, String subject, int actorId, long timeMs) {
    }

    private record BreakEffect(String blockId, long timeMs) {
    }

    private record PendingAnonymousBreak(Vec3 pos, String previousId, String nextId, long timeMs) {
    }

    private record AmbiguousDeparture(String name, boolean staff, long expiresAt) {
    }

    private record PendingVanish(UUID uuid, String name, int dueTick) {
    }

    private record RecentMessage(String text, long atMs) {
    }

    private static final class Detection {
        String name;
        String reason;
        int score;
        long expiresAt;

        Detection(String name, String reason, int score, long expiresAt) {
            this.name = name;
            this.reason = reason;
            this.score = score;
            this.expiresAt = expiresAt;
        }
    }
}
