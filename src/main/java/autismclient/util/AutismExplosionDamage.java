package autismclient.util;

import autismclient.mixin.accessor.AutismEntityAccessor;
import autismclient.mixin.accessor.AutismLivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class AutismExplosionDamage {

    public static final float END_CRYSTAL_POWER = 6.0f;

    public static final float RESPAWN_ANCHOR_POWER = 5.0f;

    public static final float BED_POWER = 5.0f;

    public static final float TERRAIN_BLAST_RESISTANCE = 9.0f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final int CACHE_MAX_ENTRIES = 256;

    private static final long NO_SCAN = -1L;

    private static final Object CACHE_LOCK = new Object();

    private static final Map<CacheKey, Double> CACHE =
        new LinkedHashMap<>(CACHE_MAX_ENTRIES, 1.0f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, Double> eldest) {
                return size() > CACHE_MAX_ENTRIES;
            }
        };

    private static final AtomicLong GENERATION = new AtomicLong();

    private static long cachePassId = NO_SCAN;
    private static long cacheGeneration = NO_SCAN;
    private static WeakReference<Level> cacheLevel = new WeakReference<>(null);
    private static int cacheTick = Integer.MIN_VALUE;

    private AutismExplosionDamage() {
    }

    public record Options(List<BlockPos> exclude,
                          BlockPos include,
                          Float maxBlastResistance,
                          AABB overrideBox,
                          DamageSource damageSource,
                          boolean estimateProtection) {

        public static final Options DEFAULT = new Options(List.of(), null, null, null, null, true);

        public Options {
            exclude = exclude == null ? List.of() : List.copyOf(exclude);
        }

        public Options withExclude(List<BlockPos> positions) {
            return new Options(positions, include, maxBlastResistance, overrideBox, damageSource,
                estimateProtection);
        }

        public Options withInclude(BlockPos pos) {
            return new Options(exclude, pos, maxBlastResistance, overrideBox, damageSource, estimateProtection);
        }

        public Options withMaxBlastResistance(Float resistance) {
            return new Options(exclude, include, resistance, overrideBox, damageSource, estimateProtection);
        }

        public Options withTerrain(boolean terrain) {
            return withMaxBlastResistance(terrain ? TERRAIN_BLAST_RESISTANCE : null);
        }

        public Options withOverrideBox(AABB box) {
            return new Options(exclude, include, maxBlastResistance, box, damageSource, estimateProtection);
        }

        public Options withDamageSource(DamageSource source) {
            return new Options(exclude, include, maxBlastResistance, overrideBox, source, estimateProtection);
        }

        public Options withEstimateProtection(boolean estimate) {
            return new Options(exclude, include, maxBlastResistance, overrideBox, damageSource, estimate);
        }

        public boolean needsTweakedRays() {
            return !exclude.isEmpty() || include != null || maxBlastResistance != null || overrideBox != null;
        }
    }

    public record Ranking(double targetDamage, double selfDamage) {

        public double margin() {
            return targetDamage - selfDamage;
        }

        public boolean isEfficient() {
            return targetDamage > selfDamage;
        }

        public boolean passes(double minTargetDamage, double maxSelfDamage) {
            return targetDamage >= minTargetDamage && selfDamage <= maxSelfDamage;
        }
    }

    public static double damageTo(LivingEntity target, Vec3 explosionPos, float power) {
        return damageTo(target, explosionPos, power, Options.DEFAULT);
    }

    public static double damageTo(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        return damage(target, explosionPos, power, options == null ? Options.DEFAULT : options, true);
    }

    private static double damage(LivingEntity target, Vec3 explosionPos, float power, Options options,
                                 boolean sampleExposure) {
        if (target == null || explosionPos == null || !(power > 0.0f)) return 0.0;
        Level level = target.level();
        if (level == null || isExplosionImmune(target)) return 0.0;

        float range = power * 2.0f;
        double distanceSqr = distanceSqr(target, explosionPos, options);
        if (distanceSqr > (double) range * (double) range) return 0.0;

        double decay = 1.0 - Math.sqrt(distanceSqr) / (double) range;
        double seen = (sampleExposure ? exposure(target, explosionPos, options) : 1.0f) * decay;

        double raw = (seen * seen + seen) / 2.0 * 7.0 * (double) range + 1.0;
        if (raw <= 0.0) return 0.0;

        DamageSource source = options.damageSource() != null
            ? options.damageSource()
            : explosionSource(level, explosionPos);
        return effectiveDamage(target, source, (float) raw, options.estimateProtection());
    }

    private static double distanceSqr(LivingEntity target, Vec3 explosionPos, Options options) {
        AABB box = options.overrideBox();
        if (box == null) return target.distanceToSqr(explosionPos);
        double dx = (box.minX + box.maxX) / 2.0 - explosionPos.x;
        double dy = box.minY - explosionPos.y;
        double dz = (box.minZ + box.maxZ) / 2.0 - explosionPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double selfDamage(Vec3 explosionPos, float power) {
        return selfDamage(explosionPos, power, Options.DEFAULT);
    }

    public static double selfDamage(Vec3 explosionPos, float power, Options options) {
        return damageTo(Minecraft.getInstance().player, explosionPos, power, options);
    }

    public static boolean wouldKill(LivingEntity target, Vec3 explosionPos, float power) {
        return wouldKill(target, explosionPos, power, Options.DEFAULT);
    }

    public static boolean wouldKill(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        double health = effectiveHealth(target);
        if (health <= 0.0) return false;
        return damageTo(target, explosionPos, power, options) >= health;
    }

    public static boolean wouldKillSelf(Vec3 explosionPos, float power) {
        return wouldKill(Minecraft.getInstance().player, explosionPos, power, Options.DEFAULT);
    }

    public static double effectiveHealth(LivingEntity target) {
        if (target == null || target.isDeadOrDying()) return 0.0;
        return (double) target.getHealth() + (double) target.getAbsorptionAmount();
    }

    public static final int HURT_COOLDOWN_GRACE = 10;

    public static final double SELF_LETHAL_MARGIN = 1.15;

    public static final double SELF_LETHAL_HEADROOM = 2.0;

    public static final double TARGET_LETHAL_MARGIN = 1.0;

    public static boolean inHurtCooldown(LivingEntity target, DamageSource source) {
        if (target == null) return false;
        if (source != null && source.is(DamageTypeTags.BYPASSES_COOLDOWN)) return false;
        return target.invulnerableTime > HURT_COOLDOWN_GRACE;
    }

    public record Lethality(boolean killsTarget, boolean killsSelf,
                            double targetDamage, double selfDamage) {

        public boolean lethalOverride() {
            return killsTarget && !killsSelf;
        }
    }

    public static boolean killsTarget(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        return killsTarget(target, damageTo(target, explosionPos, power, options), options);
    }

    public static boolean killsTarget(LivingEntity target, double predictedDamage, Options options) {
        if (target == null || !(predictedDamage > 0.0)) return false;
        double health = effectiveHealth(target);
        if (health <= 0.0) return false;
        if (inHurtCooldown(target, options == null ? null : options.damageSource())) return false;
        return predictedDamage * TARGET_LETHAL_MARGIN >= health;
    }

    public static boolean killsSelf(Vec3 explosionPos, float power, Options options) {
        return killsSelf(selfDamage(explosionPos, power, options));
    }

    public static boolean killsSelf(double predictedSelfDamage) {
        LocalPlayer player = Minecraft.getInstance().player;
        double health = effectiveHealth(player);

        if (health <= 0.0) return false;
        return predictedSelfDamage * SELF_LETHAL_MARGIN + SELF_LETHAL_HEADROOM >= health;
    }

    public static boolean killsSelfThroughTotem(double predictedSelfDamage) {
        if (!killsSelf(predictedSelfDamage)) return false;
        return !totemHeld();
    }

    private static boolean totemHeld() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    public static Lethality lethality(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        return lethalityOf(rank(target, explosionPos, power, options), target, options);
    }

    public static Lethality cachedLethality(LivingEntity target, Vec3 explosionPos, float power,
                                            Options options) {
        return lethalityOf(cachedRank(target, explosionPos, power, options), target, options);
    }

    public static Lethality lethalityOf(Ranking ranking, LivingEntity target, Options options) {
        if (ranking == null) return new Lethality(false, false, 0.0, 0.0);
        return new Lethality(
            killsTarget(target, ranking.targetDamage(), options),
            killsSelf(ranking.selfDamage()),
            ranking.targetDamage(), ranking.selfDamage());
    }

    public static Ranking rank(LivingEntity target, Vec3 explosionPos, float power) {
        return rank(target, explosionPos, power, Options.DEFAULT);
    }

    public static Ranking rank(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        double targetDamage = damageTo(target, explosionPos, power, options);
        LocalPlayer player = Minecraft.getInstance().player;

        double self = target == player ? targetDamage : damageTo(player, explosionPos, power, options);
        return new Ranking(targetDamage, self);
    }

    public static double maxDamageTo(LivingEntity target, Vec3 explosionPos, float power) {
        return maxDamageTo(target, explosionPos, power, Options.DEFAULT);
    }

    public static double maxDamageTo(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        return damage(target, explosionPos, power, options == null ? Options.DEFAULT : options, false);
    }

    public static double maxSelfDamage(Vec3 explosionPos, float power, Options options) {
        return maxDamageTo(Minecraft.getInstance().player, explosionPos, power, options);
    }

    public static final class ScanPass implements AutoCloseable {
        private final long id;

        private ScanPass(long id) {
            this.id = id;
        }

        @Override
        public void close() {
            endScan(id);
        }
    }

    public static ScanPass beginScan() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        int tick = currentTick(mc);
        synchronized (CACHE_LOCK) {
            cachePassId = GENERATION.incrementAndGet();
            rearmLocked(level, tick);
            return new ScanPass(cachePassId);
        }
    }

    private static void endScan(long passId) {
        synchronized (CACHE_LOCK) {
            if (cachePassId != passId) return;
            CACHE.clear();
            GENERATION.incrementAndGet();
            cachePassId = NO_SCAN;
            cacheGeneration = NO_SCAN;
            cacheLevel = new WeakReference<>(null);
            cacheTick = Integer.MIN_VALUE;
        }
    }

    public static double cachedDamageTo(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        if (target == null || explosionPos == null || !(power > 0.0f)) return 0.0;
        Options opts = options == null ? Options.DEFAULT : options;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;

        long generation;
        synchronized (CACHE_LOCK) {

            if (cachePassId == NO_SCAN) {
                generation = NO_SCAN;
            } else {
                int tick = currentTick(mc);

                if (cacheLevel.get() != level || cacheTick != tick) rearmLocked(level, tick);
                generation = cacheGeneration;
            }
        }
        if (generation == NO_SCAN) return damageTo(target, explosionPos, power, opts);

        CacheKey key = new CacheKey(target, target.getBoundingBox(), explosionPos, power, opts,
            stateFingerprint(target));
        synchronized (CACHE_LOCK) {
            if (cacheGeneration != generation) return damageTo(target, explosionPos, power, opts);
            Double cached = CACHE.get(key);
            if (cached != null) return cached;
        }

        double damage = damageTo(target, explosionPos, power, opts);
        boolean moved = stateMoved(key, target);
        synchronized (CACHE_LOCK) {

            if (cacheGeneration != generation) return damage;
            if (cacheLevel.get() != level || cacheTick != currentTick(mc)) return damage;
            if (!moved) CACHE.put(key, damage);
        }
        return damage;
    }

    private static int currentTick(Minecraft mc) {
        if (mc.player == null) return Integer.MIN_VALUE;
        return AutismSharedState.get().getClientTickCounter();
    }

    private static void rearmLocked(Level level, int tick) {
        CACHE.clear();
        cacheGeneration = GENERATION.incrementAndGet();
        cacheLevel = new WeakReference<>(level);
        cacheTick = tick;
    }

    public static double cachedSelfDamage(Vec3 explosionPos, float power, Options options) {
        return cachedDamageTo(Minecraft.getInstance().player, explosionPos, power, options);
    }

    public static Ranking cachedRank(LivingEntity target, Vec3 explosionPos, float power, Options options) {
        double targetDamage = cachedDamageTo(target, explosionPos, power, options);
        LocalPlayer player = Minecraft.getInstance().player;
        double self = target == player
            ? targetDamage
            : cachedDamageTo(player, explosionPos, power, options);
        return new Ranking(targetDamage, self);
    }

    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
            long generation = GENERATION.incrementAndGet();
            if (cachePassId != NO_SCAN) cacheGeneration = generation;
        }
    }

    private static boolean stateMoved(CacheKey key, LivingEntity target) {
        if (!key.targetBox().equals(target.getBoundingBox())) return true;
        return key.fingerprint() != stateFingerprint(target);
    }

    private static long stateFingerprint(LivingEntity target) {
        long hash = 17L;
        hash = hash * 31L + (target.isRemoved() ? 1L : 0L);
        hash = hash * 31L + (target.isDeadOrDying() ? 1L : 0L);
        hash = hash * 31L + (target.isSpectator() ? 1L : 0L);
        hash = hash * 31L + (target.isInvulnerable() ? 1L : 0L);
        if (target instanceof Player player) {
            hash = hash * 31L + (player.getAbilities().invulnerable ? 1L : 0L);
        }
        Level level = target.level();
        hash = hash * 31L + (level == null ? -1L : level.getDifficulty().ordinal());
        hash = hash * 31L + target.getArmorValue();
        hash = hash * 31L + Double.doubleToLongBits(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        hash = hash * 31L + (resistance == null ? -1L : resistance.getAmplifier());
        hash = hash * 31L + (target.hasEffect(MobEffects.FIRE_RESISTANCE) ? 1L : 0L);
        hash = hash * 31L + Float.floatToIntBits(target.getYHeadRot());
        hash = hash * 31L + stackFingerprint(target.getItemBlockingWith());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            hash = hash * 31L + stackFingerprint(target.getItemBySlot(slot));
        }
        return hash;
    }

    private static long stackFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        long hash = stack.getItem().hashCode();
        hash = hash * 31L + stack.getDamageValue();
        ItemEnchantments enchantments = stack.getEnchantments();
        return hash * 31L + (enchantments == null ? 0L : enchantments.hashCode());
    }

    public static float exposure(LivingEntity target, Vec3 explosionPos, Options options) {
        if (target == null || explosionPos == null) return 0.0f;
        Level level = target.level();
        if (level == null) return 0.0f;

        Options opts = options == null ? Options.DEFAULT : options;
        if (!opts.needsTweakedRays()) return ServerExplosion.getSeenPercent(explosionPos, target);

        AABB box = opts.overrideBox() != null ? opts.overrideBox() : target.getBoundingBox();

        CollisionContext shapeContext = opts.overrideBox() != null
            ? CollisionContext.withPosition(target, box.minY)
            : CollisionContext.of(target);

        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) return 0.0f;

        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        int hits = 0;
        int totalRays = 0;
        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3 sample = new Vec3(
                        Mth.lerp(x, box.minX, box.maxX) + offsetX,
                        Mth.lerp(y, box.minY, box.maxY),
                        Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    ClipContext clip = new ClipContext(sample, explosionPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shapeContext);
                    if (raycast(level, clip, opts).getType() == HitResult.Type.MISS) hits++;
                    totalRays++;
                }
            }
        }
        return (float) hits / (float) totalRays;
    }

    private static BlockHitResult raycast(Level level, ClipContext context, Options options) {
        List<BlockPos> exclude = options.exclude();
        BlockPos include = options.include();
        Float maxBlastResistance = options.maxBlastResistance();

        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context,
            (ctx, pos) -> {
                boolean excluded = !exclude.isEmpty() && exclude.contains(pos);

                BlockState blockState;
                if (excluded) {
                    blockState = Blocks.VOID_AIR.defaultBlockState();
                } else if (pos.equals(include)) {
                    blockState = Blocks.OBSIDIAN.defaultBlockState();
                } else {
                    blockState = level.getBlockState(pos);
                    if (maxBlastResistance != null
                        && blockState.getBlock().getExplosionResistance() < maxBlastResistance) {
                        blockState = Blocks.VOID_AIR.defaultBlockState();
                    }
                }

                FluidState fluidState;
                if (excluded) {
                    fluidState = Fluids.EMPTY.defaultFluidState();
                } else {
                    fluidState = level.getFluidState(pos);
                    if (maxBlastResistance != null
                        && fluidState.getExplosionResistance() < maxBlastResistance) {
                        fluidState = Fluids.EMPTY.defaultFluidState();
                    }
                }

                Vec3 from = ctx.getFrom();
                Vec3 to = ctx.getTo();
                BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, pos,
                    ctx.getBlockShape(blockState, level, pos), blockState);
                BlockHitResult fluidHit = ctx.getFluidShape(fluidState, level, pos).clip(from, to, pos);

                double blockDistance = blockHit == null
                    ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
                double fluidDistance = fluidHit == null
                    ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
                return blockDistance <= fluidDistance ? blockHit : fluidHit;
            },
            ctx -> {
                Vec3 delta = ctx.getFrom().subtract(ctx.getTo());
                return BlockHitResult.miss(ctx.getTo(),
                    Direction.getApproximateNearest(delta.x, delta.y, delta.z),
                    BlockPos.containing(ctx.getTo()));
            });
    }

    public static double effectiveDamage(LivingEntity target, DamageSource source, float damage,
                                         boolean estimateProtection) {
        if (target == null || source == null) return 0.0;
        if (((AutismEntityAccessor) target).autism$isInvulnerableToBase(source)) return 0.0;
        if (target.isDeadOrDying()) return 0.0;

        float amount = damage;
        if (target instanceof Player player) {
            if (player.getAbilities().invulnerable
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return 0.0;
            }

            if (source.scalesWithDifficulty()) {
                Difficulty difficulty = target.level().getDifficulty();
                if (difficulty == Difficulty.PEACEFUL) {
                    amount = 0.0f;
                } else if (difficulty == Difficulty.EASY) {
                    amount = Math.min(amount / 2.0f + 1.0f, amount);
                } else if (difficulty == Difficulty.HARD) {
                    amount = amount * 3.0f / 2.0f;
                }
            }
        }

        if (amount <= 0.0f) return 0.0;
        if (source.is(DamageTypeTags.IS_FIRE) && target.hasEffect(MobEffects.FIRE_RESISTANCE)) return 0.0;

        amount -= blockedDamage(target, source, amount);
        if (amount <= 0.0f) return 0.0;

        AutismLivingEntityAccessor accessor = (AutismLivingEntityAccessor) target;
        amount = accessor.autism$getDamageAfterArmorAbsorb(source, amount);
        amount = accessor.autism$getDamageAfterMagicAbsorb(source, amount);
        if (estimateProtection) amount = afterProtection(target, source, amount);
        return Math.max(amount, 0.0f);
    }

    private static float blockedDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f) return 0.0f;
        ItemStack blocking = target.getItemBlockingWith();
        if (blocking == null) return 0.0f;
        BlocksAttacks blocksAttacks = blocking.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return 0.0f;
        if (blocksAttacks.bypassedBy().isPresent()
            && blocksAttacks.bypassedBy().get().contains(source.typeHolder())) {
            return 0.0f;
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            return 0.0f;
        }

        double horizontalAngle = Math.PI;
        Vec3 sourcePosition = source.getSourcePosition();
        if (sourcePosition != null) {
            float yaw = target.getYHeadRot() * Mth.DEG_TO_RAD;
            Vec3 view = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 direction = sourcePosition.subtract(target.position());
            direction = new Vec3(direction.x, 0.0, direction.z).normalize();
            horizontalAngle = Math.acos(direction.dot(view));
        }
        return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
    }

    private static float afterProtection(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f) return amount;
        if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return amount;
        if (target.level() instanceof ServerLevel) return amount;

        float points = protectionPoints(target, source);
        if (points <= 0.0f) return amount;
        return amount * (1.0f - Mth.clamp(points, 0.0f, 20.0f) / 25.0f);
    }

    private static float protectionPoints(LivingEntity target, DamageSource source) {
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean generic = !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!explosion && !generic) return 0.0f;

        float points = 0.0f;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ItemEnchantments enchantments = stack.getEnchantments();
            if (enchantments.isEmpty()) continue;
            for (Holder<Enchantment> holder : enchantments.keySet()) {
                int level = enchantments.getLevel(holder);
                if (level <= 0) continue;
                if (generic && holder.is(Enchantments.PROTECTION)) points += level;
                if (explosion && holder.is(Enchantments.BLAST_PROTECTION)) points += level * 2.0f;
            }
        }
        return points;
    }

    public static DamageSource explosionSource(Level level, Vec3 explosionPos) {
        return new DamageSource(level.damageSources().explosion((Explosion) null).typeHolder(), explosionPos);
    }

    public static DamageSource badRespawnPointSource(Level level, Vec3 explosionPos) {
        return level.damageSources().badRespawnPointExplosion(explosionPos);
    }

    private static boolean isExplosionImmune(LivingEntity target) {
        if (target.isRemoved() || target.isDeadOrDying() || target.isSpectator()) return true;
        if (target instanceof Player player && player.getAbilities().invulnerable) return true;
        return target.isInvulnerable();
    }

    private record CacheKey(LivingEntity target, AABB targetBox, Vec3 explosionPos, float power,
                            Options options, long fingerprint) {
    }
}
