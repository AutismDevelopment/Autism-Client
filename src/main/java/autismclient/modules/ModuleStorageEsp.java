package autismclient.modules;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ModuleStorageEsp {

    @FunctionalInterface
    interface BoxEmitter {
        void accept(AABB box, int color, boolean meshable);
    }

    public static final class Id {
        public static final String CHEST = "minecraft:chest";
        public static final String TRAPPED_CHEST = "minecraft:trapped_chest";
        public static final String ENDER_CHEST = "minecraft:ender_chest";
        public static final String BARREL = "minecraft:barrel";
        public static final String SHULKER_BOX = "minecraft:shulker_box";
        public static final String HOPPER = "minecraft:hopper";
        public static final String DISPENSER = "minecraft:dispenser";
        public static final String DROPPER = "minecraft:dropper";
        public static final String FURNACE = "minecraft:furnace";
        public static final String SMOKER = "minecraft:smoker";
        public static final String BLAST_FURNACE = "minecraft:blast_furnace";
        public static final String BREWING_STAND = "minecraft:brewing_stand";
        public static final String CRAFTER = "minecraft:crafter";
        public static final String DECORATED_POT = "minecraft:decorated_pot";
        public static final String CHISELED_BOOKSHELF = "minecraft:chiseled_bookshelf";
        public static final String CAMPFIRE = "minecraft:campfire";
        public static final String CHEST_MINECART = "minecraft:chest_minecart";
        public static final String HOPPER_MINECART = "minecraft:hopper_minecart";
        public static final String FURNACE_MINECART = "minecraft:furnace_minecart";

        public static final String CHEST_BOAT = "minecraft:chest_boat";

        private Id() {
        }
    }

    public static final class Target {
        public final String id;
        public final String label;
        public final String group;
        private final java.util.function.Supplier<ItemStack> iconSupplier;
        private final boolean isBlockTarget;
        private final Class<? extends BlockEntity> beClass;
        private final Class<? extends Entity> entityClass;

        private Target(String id, String label, String group,
                       java.util.function.Supplier<ItemStack> iconSupplier,
                       Class<? extends BlockEntity> beClass,
                       Class<? extends Entity> entityClass) {
            this.id = id;
            this.label = label;
            this.group = group;
            this.iconSupplier = iconSupplier;
            this.isBlockTarget = beClass != null;
            this.beClass = beClass;
            this.entityClass = entityClass;
        }

        public ItemStack icon() {
            try {
                ItemStack stack = iconSupplier.get();
                return stack == null ? ItemStack.EMPTY : stack;
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }

        public boolean isBlock() {
            return isBlockTarget;
        }
    }

    public static final List<Target> TARGETS = List.of(
        block(Id.CHEST,              "Chest",              () -> Blocks.CHEST.asItem().getDefaultInstance(),              ChestBlockEntity.class),
        block(Id.TRAPPED_CHEST,      "Trapped Chest",      () -> Blocks.TRAPPED_CHEST.asItem().getDefaultInstance(),      TrappedChestBlockEntity.class),
        block(Id.ENDER_CHEST,        "Ender Chest",        () -> Blocks.ENDER_CHEST.asItem().getDefaultInstance(),        EnderChestBlockEntity.class),
        block(Id.BARREL,             "Barrel",             () -> Blocks.BARREL.asItem().getDefaultInstance(),             BarrelBlockEntity.class),
        block(Id.SHULKER_BOX,        "Shulker Box",        () -> Blocks.SHULKER_BOX.asItem().getDefaultInstance(),        ShulkerBoxBlockEntity.class),
        block(Id.HOPPER,             "Hopper",             () -> Blocks.HOPPER.asItem().getDefaultInstance(),             HopperBlockEntity.class),
        block(Id.DISPENSER,          "Dispenser",          () -> Blocks.DISPENSER.asItem().getDefaultInstance(),          DispenserBlockEntity.class),

        block(Id.DROPPER,            "Dropper",            () -> Blocks.DROPPER.asItem().getDefaultInstance(),            net.minecraft.world.level.block.entity.DropperBlockEntity.class),
        block(Id.FURNACE,            "Furnace",            () -> Blocks.FURNACE.asItem().getDefaultInstance(),            net.minecraft.world.level.block.entity.FurnaceBlockEntity.class),
        block(Id.SMOKER,             "Smoker",             () -> Blocks.SMOKER.asItem().getDefaultInstance(),             net.minecraft.world.level.block.entity.SmokerBlockEntity.class),
        block(Id.BLAST_FURNACE,      "Blast Furnace",      () -> Blocks.BLAST_FURNACE.asItem().getDefaultInstance(),      net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity.class),
        block(Id.BREWING_STAND,      "Brewing Stand",      () -> Items.BREWING_STAND.getDefaultInstance(),                BrewingStandBlockEntity.class),
        block(Id.CRAFTER,            "Crafter",            () -> Blocks.CRAFTER.asItem().getDefaultInstance(),            CrafterBlockEntity.class),
        block(Id.DECORATED_POT,      "Decorated Pot",      () -> Blocks.DECORATED_POT.asItem().getDefaultInstance(),      DecoratedPotBlockEntity.class),
        block(Id.CHISELED_BOOKSHELF, "Chiseled Bookshelf", () -> Blocks.CHISELED_BOOKSHELF.asItem().getDefaultInstance(), ChiseledBookShelfBlockEntity.class),
        block(Id.CAMPFIRE,           "Campfire",           () -> Blocks.CAMPFIRE.asItem().getDefaultInstance(),           CampfireBlockEntity.class),
        entity(Id.CHEST_MINECART,   "Chest Minecart",   () -> Items.CHEST_MINECART.getDefaultInstance(),   MinecartChest.class),
        entity(Id.HOPPER_MINECART,  "Hopper Minecart",  () -> Items.HOPPER_MINECART.getDefaultInstance(),  MinecartHopper.class),
        entity(Id.FURNACE_MINECART, "Furnace Minecart", () -> Items.FURNACE_MINECART.getDefaultInstance(), MinecartFurnace.class),
        entity(Id.CHEST_BOAT,       "Chest Boat",       () -> Items.OAK_CHEST_BOAT.getDefaultInstance(),   AbstractChestBoat.class)
    );

    private static final Map<String, Target> BY_ID;

    public static final String DEFAULT_VALUE;
    private static volatile TargetSelection cachedSelection = new TargetSelection("", List.of(), List.of());

    static {
        Map<String, Target> map = new LinkedHashMap<>();
        StringBuilder def = new StringBuilder();
        for (Target target : TARGETS) {
            map.put(target.id, target);
            if (def.length() > 0) def.append('|');
            def.append(target.id);
        }
        BY_ID = Map.copyOf(map);
        DEFAULT_VALUE = def.toString();
    }

    public static Target byId(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    private static Target block(String id, String label,
                                 java.util.function.Supplier<ItemStack> iconSupplier,
                                 Class<? extends BlockEntity> beClass) {
        return new Target(id, label, "Blocks", iconSupplier, beClass, null);
    }

    private static Target entity(String id, String label,
                                  java.util.function.Supplier<ItemStack> iconSupplier,
                                  Class<? extends Entity> entityClass) {
        return new Target(id, label, "Entities", iconSupplier, null, entityClass);
    }

    private ModuleStorageEsp() {
    }

    static void collect(Module module, ClientLevel level, Player player, float tickDelta, BiConsumer<AABB, Integer> emit) {
        collectTargets(module, level, player, tickDelta,
            emit == null ? null : (box, color, meshable) -> emit.accept(box, color), null);
    }

    static void collectTracePoints(Module module, ClientLevel level, Player player, float tickDelta, BiConsumer<Vec3, Integer> emit) {
        collectTargets(module, level, player, tickDelta, null, emit);
    }

    static void collectBoth(Module module, ClientLevel level, Player player, float tickDelta,
                            BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
        collectTargets(module, level, player, tickDelta,
            boxEmit == null ? null : (box, color, meshable) -> boxEmit.accept(box, color), traceEmit);
    }

    static void collectBothDetailed(Module module, ClientLevel level, Player player, float tickDelta,
                                    BoxEmitter boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
        collectTargets(module, level, player, tickDelta, boxEmit, traceEmit);
    }

    private static void collectTargets(Module module, ClientLevel level, Player player, float tickDelta,
                                       BoxEmitter boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
        if (module == null || level == null || player == null || (boxEmit == null && traceEmit == null)) return;

        TargetSelection selection = targetSelection(module.value("storage-list"));
        List<Target> blockTargets = selection.blockTargets();
        List<Target> entityTargets = selection.entityTargets();
        if (blockTargets.isEmpty() && entityTargets.isEmpty()) return;
        ColorSet colors = colorSet(module);

        int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
        double maxDistSq = 2.0 * (chunkRadius * 16.0 + 16.0) * (chunkRadius * 16.0 + 16.0);

        Vec3 playerPos = player.position();
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();

        if (!blockTargets.isEmpty()) {
            ModuleEspChunkCache.onLevel(level);

            String stamp = module.value("storage-list") + "|" + colors;
            long gameTime = level.getGameTime();
            ClientChunkCache chunks = level.getChunkSource();
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    LevelChunk chunk = chunks.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    var entries = ModuleEspChunkCache.STORAGE_BE.chunkEntries(chunk, gameTime, stamp,
                        (scanned, out) -> scanChunkBlockEntities(level, scanned, blockTargets, colors, out));
                    for (ModuleEspChunkCache.Entry entry : entries) {
                        Vec3 trace = entry.trace();
                        if (sqDist(playerPos, trace.x, trace.y, trace.z) > maxDistSq) continue;
                        if (boxEmit != null) boxEmit.accept(entry.box(), entry.color(), true);
                        if (traceEmit != null) traceEmit.accept(trace, entry.color());
                    }
                }
            }
        }

        if (!entityTargets.isEmpty()) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == null || !entity.isAlive()) continue;
                Target matched = matchEntityTarget(entity, entityTargets);
                if (matched == null) continue;
                if (sqDist(playerPos, entity.getX(), entity.getY(), entity.getZ()) > maxDistSq) continue;
                AABB box = interpolatedBox(entity, tickDelta).inflate(0.05);
                int color = entityColor(colors, entity);
                if (boxEmit != null) boxEmit.accept(box, color, false);
                if (traceEmit != null) traceEmit.accept(box.getCenter(), color);
            }
        }
    }

    private static void scanChunkBlockEntities(ClientLevel level, LevelChunk chunk, List<Target> blockTargets,
                                               ColorSet colors, List<ModuleEspChunkCache.Entry> out) {
        for (var entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity be = entry.getValue();
            if (pos == null || be == null) continue;
            if (matchBlockTarget(be, blockTargets) == null) continue;
            out.add(new ModuleEspChunkCache.Entry(blockShapeBox(level, pos),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), blockColor(colors, be)));
        }
    }

    private static AABB blockShapeBox(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape == null || shape.isEmpty()) return new AABB(pos);
        return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Target matchBlockTarget(BlockEntity be, List<Target> targets) {
        Target best = null;
        int bestDepth = -1;
        for (Target target : targets) {
            if (target.beClass == null) continue;
            if (!target.beClass.isInstance(be)) continue;
            int depth = ancestorDepth(be.getClass(), target.beClass);
            if (depth >= 0 && (best == null || depth < bestDepth)) {
                best = target;
                bestDepth = depth;
            }
        }
        return best;
    }

    private static Target matchEntityTarget(Entity entity, List<Target> targets) {
        Target best = null;
        int bestDepth = -1;
        for (Target target : targets) {
            if (target.entityClass == null) continue;
            if (!target.entityClass.isInstance(entity)) continue;
            int depth = ancestorDepth(entity.getClass(), target.entityClass);
            if (depth >= 0 && (best == null || depth < bestDepth)) {
                best = target;
                bestDepth = depth;
            }
        }
        return best;
    }

    private static int ancestorDepth(Class<?> clazz, Class<?> target) {
        int depth = 0;
        Class<?> c = clazz;
        while (c != null) {
            if (c == target) return depth;
            c = c.getSuperclass();
            depth++;
        }
        return -1;
    }

    private static int blockColor(ColorSet colors, BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return colors.trappedChest();
        if (be instanceof ChestBlockEntity) return colors.chest();
        if (be instanceof EnderChestBlockEntity) return colors.enderChest();
        if (be instanceof BarrelBlockEntity) return colors.barrel();
        if (be instanceof ShulkerBoxBlockEntity) return colors.shulker();
        if (be instanceof HopperBlockEntity) return colors.hopper();
        if (be instanceof DispenserBlockEntity) return colors.dispenser();
        if (be instanceof AbstractFurnaceBlockEntity) return colors.furnace();
        if (be instanceof BrewingStandBlockEntity) return colors.furnace();
        if (be instanceof CrafterBlockEntity) return colors.crafter();
        if (be instanceof DecoratedPotBlockEntity) return colors.crafter();
        if (be instanceof ChiseledBookShelfBlockEntity) return colors.crafter();
        if (be instanceof CampfireBlockEntity) return colors.crafter();
        return colors.other();
    }

    private static int entityColor(ColorSet colors, Entity entity) {
        if (entity instanceof MinecartChest) return colors.chest();
        if (entity instanceof AbstractChestBoat) return colors.chest();
        if (entity instanceof MinecartHopper) return colors.hopper();
        if (entity instanceof MinecartFurnace) return colors.furnace();
        return colors.other();
    }

    private static AABB interpolatedBox(Entity entity, float tickDelta) {
        double dx = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - entity.getX();
        double dy = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - entity.getY();
        double dz = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
        return entity.getBoundingBox().move(dx, dy, dz);
    }

    private static double sqDist(Vec3 from, double x, double y, double z) {
        double dx = from.x - x;
        double dy = from.y - y;
        double dz = from.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int color(Module module, String option, int fallback) {
        return ModuleRenderUtil.color(module, option, fallback);
    }

    private static TargetSelection targetSelection(String value) {
        String safe = value == null ? "" : value;
        TargetSelection cached = cachedSelection;
        if (cached.value().equals(safe)) return cached;
        List<Target> blockTargets = new ArrayList<>();
        List<Target> entityTargets = new ArrayList<>();
        for (String raw : safe.split("\\|")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            Target target = byId(token);
            if (target == null) continue;
            if (target.isBlock()) blockTargets.add(target);
            else entityTargets.add(target);
        }
        TargetSelection next = new TargetSelection(safe, List.copyOf(blockTargets), List.copyOf(entityTargets));
        cachedSelection = next;
        return next;
    }

    private static ColorSet colorSet(Module module) {
        return new ColorSet(
            color(module, "trapped-chest-color", 0xCCFF2020),
            color(module, "chest-color", 0xCCFFA000),
            color(module, "ender-chest-color", 0xCC7800FF),
            color(module, "barrel-color", 0xCCFFA000),
            color(module, "shulker-color", 0xCCB766FF),
            color(module, "hopper-color", 0xCC7C8AFF),
            color(module, "dispenser-color", 0xCCB04848),
            color(module, "furnace-color", 0xCCCCB266),
            color(module, "crafter-color", 0xCCD8AE6B),
            color(module, "other-color", 0xFF8C8C8C)
        );
    }

    private record TargetSelection(String value, List<Target> blockTargets, List<Target> entityTargets) {
    }

    private record ColorSet(
        int trappedChest,
        int chest,
        int enderChest,
        int barrel,
        int shulker,
        int hopper,
        int dispenser,
        int furnace,
        int crafter,
        int other
    ) {
    }

    @SuppressWarnings("unused")
    private static Identifier beTypeId(BlockEntityType<?> type) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    }
}
