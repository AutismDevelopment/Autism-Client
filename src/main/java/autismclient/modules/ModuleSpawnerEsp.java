package autismclient.modules;

import autismclient.mixin.accessor.AutismBaseSpawnerAccessor;
import autismclient.mixin.accessor.AutismTrialSpawnerStateDataAccessor;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ModuleSpawnerEsp {
    private static final int PER_CHUNK_CAP = 512;

    public record NaturalTarget(String id, String label, int color) {}

    public static final List<NaturalTarget> NATURAL = List.of(
        new NaturalTarget("minecraft:zombie", "Zombie", 0xCC6BA35A),
        new NaturalTarget("minecraft:skeleton", "Skeleton", 0xCCE8E8E8),
        new NaturalTarget("minecraft:spider", "Spider", 0xCC8B3A3A),
        new NaturalTarget("minecraft:cave_spider", "Cave Spider", 0xCC3FA9B5),
        new NaturalTarget("minecraft:blaze", "Blaze", 0xCCFF8C1A),
        new NaturalTarget("minecraft:silverfish", "Silverfish", 0xCCC9C9C9),
        new NaturalTarget("minecraft:breeze", "Breeze", 0xCCA9CBE0),
        new NaturalTarget("minecraft:husk", "Husk", 0xCCA8906A),
        new NaturalTarget("minecraft:stray", "Stray", 0xCC9FC7DE),
        new NaturalTarget("minecraft:bogged", "Bogged", 0xCC5E7D4F),
        new NaturalTarget("minecraft:slime", "Slime", 0xCC7BD64A)
    );

    public static final String NATURAL_DEFAULT_VALUE;

    private static final Map<String, NaturalTarget> NATURAL_BY_ID = new LinkedHashMap<>();
    private static volatile String cachedListValue;
    private static volatile List<String> cachedEnabledIds = List.of();

    static {
        StringBuilder def = new StringBuilder();
        for (NaturalTarget target : NATURAL) {
            NATURAL_BY_ID.put(target.id(), target);
            if (def.length() > 0) def.append('|');
            def.append(target.id());
        }
        NATURAL_DEFAULT_VALUE = def.toString();
    }

    private static final int CUSTOM_COLOR = 0xCCFF3B3B;

    private ModuleSpawnerEsp() {
    }

    public static boolean enabledContains(Module module, String entityId) {
        if (entityId == null) return false;
        return enabledIds(module).contains(entityId.toLowerCase(Locale.ROOT));
    }

    public static List<String> enabledIds(Module module) {
        String value = module == null ? "" : module.value("spawner-list");
        if (value == null) value = "";
        if (!value.equals(cachedListValue)) {
            List<String> ids = new ArrayList<>();
            for (String raw : value.split("\\|")) {
                String token = raw.trim().toLowerCase(Locale.ROOT);
                if (!token.isEmpty()) ids.add(token);
            }
            cachedEnabledIds = List.copyOf(ids);
            cachedListValue = value;
        }
        return cachedEnabledIds;
    }

    public static boolean isNatural(String entityId) {
        return entityId != null && NATURAL_BY_ID.containsKey(entityId.toLowerCase(Locale.ROOT));
    }

    public static int colorFor(Module module, String entityId) {
        String key = "color-" + entityId;
        String stored = module == null ? "" : module.value(key);
        if (stored != null && !stored.isBlank()) return ModuleRenderUtil.color(module, key, CUSTOM_COLOR);
        NaturalTarget natural = NATURAL_BY_ID.get(entityId);
        return natural != null ? natural.color() : CUSTOM_COLOR;
    }

    static void collectBoth(Module module, ClientLevel level, Player player,
                            BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
        if (module == null || level == null || player == null || (boxEmit == null && traceEmit == null)) return;
        String listValue = module.value("spawner-list");
        if (listValue == null || listValue.isBlank()) return;

        int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
        double maxDistSq = 2.0 * (chunkRadius * 16.0 + 16.0) * (chunkRadius * 16.0 + 16.0);

        Vec3 playerPos = player.position();
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();

        ModuleEspChunkCache.onLevel(level);

        String stamp = "spawner|" + listValue + "|" + colorSignature(module, listValue);
        long gameTime = level.getGameTime();
        ClientChunkCache chunks = level.getChunkSource();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = chunks.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                List<ModuleEspChunkCache.Entry> entries = ModuleEspChunkCache.SPAWNER_ESP.chunkEntries(
                    chunk, gameTime, stamp, (scanned, out) -> scanChunk(level, scanned, module, listValue, out));
                for (ModuleEspChunkCache.Entry entry : entries) {
                    Vec3 trace = entry.trace();
                    if (sqDist(playerPos, trace.x, trace.y, trace.z) > maxDistSq) continue;
                    if (boxEmit != null) boxEmit.accept(entry.box(), entry.color());
                    if (traceEmit != null) traceEmit.accept(trace, entry.color());
                }
            }
        }
    }

    private static void scanChunk(ClientLevel level, LevelChunk chunk, Module module, String listValue,
                                  List<ModuleEspChunkCache.Entry> out) {
        for (var entry : chunk.getBlockEntities().entrySet()) {
            if (out.size() >= PER_CHUNK_CAP) break;
            BlockPos pos = entry.getKey();
            BlockEntity be = entry.getValue();
            if (pos == null) continue;
            String mobId;
            if (be instanceof SpawnerBlockEntity spawner) {
                mobId = spawnedMobId(spawner);
            } else if (be instanceof TrialSpawnerBlockEntity trialSpawner) {
                mobId = trialSpawnedMobId(trialSpawner);
            } else {
                continue;
            }
            if (mobId == null || !enabledContains(module, mobId)) continue;
            out.add(new ModuleEspChunkCache.Entry(
                blockShapeBox(level, pos), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                colorFor(module, mobId)));
        }
    }

    private static String spawnedMobId(SpawnerBlockEntity spawner) {
        try {
            BaseSpawner baseSpawner = spawner.getSpawner();
            SpawnData data = ((AutismBaseSpawnerAccessor) baseSpawner).autism$getNextSpawnData();
            return mobIdFromSpawnData(data);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String trialSpawnedMobId(TrialSpawnerBlockEntity blockEntity) {
        try {
            TrialSpawner trialSpawner = blockEntity.getTrialSpawner();
            if (trialSpawner == null) return null;
            TrialSpawnerStateData data = trialSpawner.getStateData();
            if (data == null) return null;
            Optional<SpawnData> next = ((AutismTrialSpawnerStateDataAccessor) data).autism$getNextSpawnData();
            return next == null || next.isEmpty() ? null : mobIdFromSpawnData(next.get());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String mobIdFromSpawnData(SpawnData data) {
        if (data == null) return null;
        CompoundTag tag = data.getEntityToSpawn();
        String id = tag == null ? "" : tag.getString("id").orElse("");
        return id.isBlank() ? null : id.toLowerCase(Locale.ROOT);
    }

    private static String colorSignature(Module module, String listValue) {
        StringBuilder sig = new StringBuilder();
        for (String raw : listValue.split("\\|")) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            sig.append(id).append('=').append(colorFor(module, id)).append(';');
        }
        return sig.toString();
    }

    private static AABB blockShapeBox(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape == null || shape.isEmpty()) return new AABB(pos);
        return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static double sqDist(Vec3 from, double x, double y, double z) {
        double dx = from.x - x;
        double dy = from.y - y;
        double dz = from.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
