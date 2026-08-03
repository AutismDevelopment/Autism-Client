package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.mixin.accessor.AutismEntityAccessor;
import autismclient.mixin.accessor.AutismLivingEntityAccessor;
import autismclient.mixin.accessor.AutismPlayerAccessor;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AutoTotemModule extends Module {
    private static final int OFFHAND_INV = 40;

    private static final int MODE_NONE = 0;
    private static final int MODE_TOTEM = 1;
    private static final int MODE_BACK = 2;

    private static final Direction[] HOLE_DIRECTIONS = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final float BASE_HORIZONTAL_AIR_DRAG = 0.91F;
    private static final float BASE_VERTICAL_AIR_DRAG = 0.98F;
    private static final float ELYTRA_HORIZONTAL_AIR_DRAG = 0.99F;
    private static final float ELYTRA_VERTICAL_AIR_DRAG = 0.98F;

    private static final List<BlockPos> SPHERE_OFFSETS = buildSphere(10.0f);

    private long lastSwitchAt;
    private long switchBackSince = -1;
    private int lastMode = MODE_NONE;

    private ItemStack displacedItem = ItemStack.EMPTY;
    private int displacedSlot = -1;

    AutoTotemModule() {
        super("auto-totem", "AutoTotem", ModuleCategory.PLAYER, "Keeps a totem in offhand.");

        add(new ChoiceSetting("switch-mode", "Switch Mode", "Switch", "Smart", "Switch", "PickUp")
            .description("How the totem is swapped.").build());
        add(new IntSetting("switch-delay", "Switch Delay (ms)", 0, 0, 500, 5)
            .description("Delay between offhand swaps.").build());
        add(new IntSetting("switch-back-delay", "Switch Back Delay (ms)", 40, 0, 500, 5)
            .description("Delay before switching back.").build());
        add(new BoolSetting("send-directly", "Send Directly", false)
            .description("Swap even with screens open.").build());

        add(new BoolSetting("health", "Health", true)
            .group("Health").description("Totem only when endangered.").build());
        add(new IntSetting("health-threshold", "Health Threshold", 14, 0, 20, 1)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Totem below this health.").build());
        add(new BoolSetting("safety", "Safety", true)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Stricter threshold inside holes.").build());
        add(new IntSetting("safe-health-threshold", "Safe Health Threshold", 10, 0, 20, 1)
            .group("Health").visibleWhen(() -> bool("health") && bool("safety"))
            .description("Threshold while in holes.").build());
        add(new BoolSetting("subtract-calculated-damage", "Subtract Calculated Damage", false)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Subtract predicted damage too.").build());
        add(new BoolSetting("predict-explosions", "Predict Explosion Damage", true)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Predict entity explosion damage.").build());
        add(new BoolSetting("predict-block-explosions", "Predict Block Explosions", false)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Predict bed and anchor explosions.").build());
        add(new BoolSetting("predict-fall-damage", "Predict Fall Damage", true)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Predict incoming fall damage.").build());
        add(new BoolSetting("ignore-elytra", "Ignore Elytra", false)
            .group("Health").visibleWhen(() -> bool("health") && bool("predict-fall-damage"))
            .description("Ignore fall damage while gliding.").build());
        add(new BoolSetting("missing-armor", "Missing Armor", true)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Totem when armor missing.").build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("Health").visibleWhen(() -> bool("health"))
            .description("Restore item after healing.").build());
    }

    @Override
    public void onDisable() {
        lastMode = MODE_NONE;
        switchBackSince = -1;
        displacedItem = ItemStack.EMPTY;
        displacedSlot = -1;
    }

    @Override
    public void onGameLeft() {
        onDisable();
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null || MC.level == null) return;
        if (PackHideState.isHardLocked()) return;

        boolean health = bool("health");

        boolean wantTotem = health ? !isBlocked() && healthBelowThreshold() : true;

        int mode = wantTotem ? MODE_TOTEM : MODE_NONE;
        if (mode == MODE_NONE && health && bool("switch-back") && lastMode == MODE_TOTEM) {
            mode = MODE_BACK;
        }

        if (mode != lastMode && lastMode == MODE_TOTEM) {
            if (switchBackSince < 0) switchBackSince = System.currentTimeMillis();
            if (System.currentTimeMillis() - switchBackSince < integer("switch-back-delay")) return;
        }
        switchBackSince = -1;

        if (System.currentTimeMillis() - lastSwitchAt < integer("switch-delay")) return;

        int fromSlot;
        if (mode == MODE_TOTEM) {
            fromSlot = findTotemSlot();
            if (fromSlot < 0) return;
        } else if (mode == MODE_BACK) {
            fromSlot = findDisplacedSlot();
            if (fromSlot < 0) return;
        } else {
            return;
        }

        lastMode = mode;

        if (fromSlot == OFFHAND_INV) return;

        if (health && bool("switch-back")) {
            displacedItem = MC.player.getOffhandItem().copy();
            displacedSlot = fromSlot;
        }

        performSwitch(fromSlot, mode == MODE_TOTEM);
        lastSwitchAt = System.currentTimeMillis();
    }

    private static boolean isBlocked() {
        return MC.player.isCreative() || MC.player.isSpectator() || MC.player.isDeadOrDying();
    }

    private static int findTotemSlot() {
        LocalPlayer player = MC.player;
        if (isTotem(player.getOffhandItem())) return OFFHAND_INV;
        for (int i = 0; i < 9; i++) {
            if (isTotem(player.getInventory().getItem(i))) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (isTotem(player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.DEATH_PROTECTION);
    }

    private int findDisplacedSlot() {
        if (displacedSlot < 0 || displacedSlot == OFFHAND_INV) return -1;
        ItemStack stack = MC.player.getInventory().getItem(displacedSlot);
        if (displacedItem.isEmpty()) return stack.isEmpty() ? displacedSlot : -1;
        return !stack.isEmpty() && stack.getItem() == displacedItem.getItem() ? displacedSlot : -1;
    }

    private boolean performSwitch(int fromSlot, boolean isTotem) {
        boolean foreign = MC.player.containerMenu != MC.player.inventoryMenu;
        if (foreign) {

            if (!bool("send-directly") || !isTotem) return false;
            return smartHotbarSwitch(fromSlot);
        }

        return switch (choice("switch-mode")) {
            case "Smart" -> fromSlot >= 0 && fromSlot < 9 ? smartHotbarSwitch(fromSlot) : pickupSwitch(fromSlot);
            case "PickUp" -> pickupSwitch(fromSlot);
            default -> swapSwitch(fromSlot);
        };
    }

    private static boolean swapSwitch(int fromSlot) {
        int handlerSlot = AutismInventoryHelper.toHandlerSlot(MC, fromSlot);
        if (handlerSlot < 0) return false;
        return AutismInventoryClickHelper.click(MC, handlerSlot, 40, ContainerInput.SWAP);
    }

    private static boolean pickupSwitch(int fromSlot) {
        int from = AutismInventoryHelper.toHandlerSlot(MC, fromSlot);
        int offhand = AutismInventoryHelper.toHandlerSlot(MC, OFFHAND_INV);
        if (from < 0 || offhand < 0) return false;
        boolean offhandHadItem = !MC.player.getOffhandItem().isEmpty();
        boolean ok = AutismInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
        ok &= AutismInventoryClickHelper.click(MC, offhand, 0, ContainerInput.PICKUP);
        if (offhandHadItem) ok &= AutismInventoryClickHelper.click(MC, from, 0, ContainerInput.PICKUP);
        return ok;
    }

    private static boolean smartHotbarSwitch(int fromSlot) {
        if (fromSlot < 0 || fromSlot > 8) return false;
        LocalPlayer player = MC.player;
        ClientPacketListener connection = MC.getConnection();
        if (connection == null) return false;
        int selected = player.getInventory().getSelectedSlot();
        if (selected != fromSlot) connection.send(new ServerboundSetCarriedItemPacket(fromSlot));
        connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        if (selected != fromSlot) connection.send(new ServerboundSetCarriedItemPacket(selected));
        return true;
    }

    private boolean healthBelowThreshold() {
        if (!bool("health")) return true;

        if (bool("missing-armor")) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (MC.player.getItemBySlot(slot).isEmpty()) return true;
            }
        }

        float health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
        boolean safetyOperating = bool("safety") && (isBurrowed() || isInHole());
        float allowedDamage = health - (safetyOperating
            ? integer("safe-health-threshold")
            : integer("health-threshold"));

        if (allowedDamage <= 0.0f) return true;

        if (!bool("subtract-calculated-damage")) allowedDamage = health;

        float calculatedDamage = getDamageFromEntities(allowedDamage);
        if (calculatedDamage >= allowedDamage) return true;

        calculatedDamage = Math.max(calculatedDamage, getDamageFromBlocks(allowedDamage));
        if (calculatedDamage >= allowedDamage) return true;

        calculatedDamage += getFallDamage();
        return calculatedDamage >= allowedDamage;
    }

    private float getDamageFromEntities(float allowedDamage) {
        if (!bool("predict-explosions")) return 0.0f;
        float maxDamage = 0.0f;
        for (Entity entity : MC.level.entitiesForRendering()) {
            maxDamage = Math.max(maxDamage, getExplosionDamageFromEntity(entity));
            if (maxDamage >= allowedDamage) return maxDamage;
        }
        return maxDamage;
    }

    private static float getExplosionDamageFromEntity(Entity entity) {
        if (entity instanceof EndCrystal) {
            return getDamageFromExplosion(entity.position(), 6.0f, 12.0f, 144.0f, null,
                Explosion.getDefaultDamageSource(MC.level, entity));
        }
        if (entity instanceof PrimedTnt) {
            return getDamageFromExplosion(entity.position().add(0.0, 0.0625, 0.0), 4.0f, 8.0f, 64.0f, null,
                Explosion.getDefaultDamageSource(MC.level, entity));
        }
        if (entity instanceof MinecartTNT cart) {
            float speed = (float) Math.min(Math.sqrt(cart.getDeltaMovement().horizontalDistanceSqr()), 5.0);
            float power = 4.0f + speed * 1.5f;
            float range = power * 2.0f;
            return getDamageFromExplosion(cart.position(), power, range, range * range, null,
                Explosion.getDefaultDamageSource(MC.level, entity));
        }
        if (entity instanceof Creeper creeper) {
            float power = 3.0f * (creeper.isPowered() ? 2.0f : 1.0f);
            float range = power * 2.0f;
            return getDamageFromExplosion(creeper.position(), power, range, range * range, null,
                Explosion.getDefaultDamageSource(MC.level, entity));
        }
        return 0.0f;
    }

    private float getDamageFromBlocks(float allowedDamage) {
        if (!bool("predict-block-explosions")) return 0.0f;
        boolean bedsExplode = MC.level.environmentAttributes()
            .getDimensionValue(EnvironmentAttributes.BED_RULE) == BedRule.EXPLODES;
        boolean anchorsExplode = !MC.level.environmentAttributes()
            .getDimensionValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS);
        if (!bedsExplode && !anchorsExplode) return 0.0f;

        BlockPos center = MC.player.blockPosition();
        float maxDamage = 0.0f;
        for (BlockPos offset : SPHERE_OFFSETS) {
            BlockPos pos = center.offset(offset);
            BlockState state = MC.level.getBlockState(pos);
            Block block = state.getBlock();

            boolean bedExplosion = bedsExplode && block instanceof BedBlock;
            boolean anchorExplosion = anchorsExplode && block instanceof RespawnAnchorBlock
                && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
            if (!bedExplosion && !anchorExplosion) continue;

            List<BlockPos> exclude = bedExplosion
                ? List.of(pos, pos.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite()))
                : List.of(pos);

            Vec3 posCenter = Vec3.atCenterOf(pos);
            maxDamage = Math.max(maxDamage, getDamageFromExplosion(posCenter, 5.0f, 10.0f, 100.0f,
                exclude, MC.player.damageSources().badRespawnPointExplosion(posCenter)));
            if (maxDamage >= allowedDamage) return maxDamage;
        }
        return maxDamage;
    }

    private static float getDamageFromExplosion(Vec3 pos, float power, float range, float damageDistance,
                                                List<BlockPos> exclude, DamageSource source) {
        LocalPlayer player = MC.player;
        if (player.distanceToSqr(pos) > damageDistance) return 0.0f;

        float exposure = exclude == null
            ? ServerExplosion.getSeenPercent(pos, player)
            : getExposureToExplosion(player, pos, exclude);

        double distanceDecay = 1.0 - Math.sqrt(player.distanceToSqr(pos)) / range;
        double pre1 = exposure * distanceDecay;
        double preprocessedDamage = (pre1 * pre1 + pre1) / 2.0 * 7.0 * range + 1.0;
        if (preprocessedDamage == 0.0) return 0.0f;

        return getEffectiveDamage(source, (float) preprocessedDamage);
    }

    private static float getExposureToExplosion(LocalPlayer player, Vec3 source, List<BlockPos> exclude) {
        AABB box = player.getBoundingBox();
        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) return 0.0f;

        int hits = 0;
        int totalRays = 0;
        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3 sample = new Vec3(
                        Mth.lerp(x, box.minX, box.maxX) + offsetX,
                        Mth.lerp(y, box.minY, box.maxY),
                        Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    BlockHitResult hit = MC.level.clip(new net.minecraft.world.level.ClipContext(
                        sample, source,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        player));
                    if (hit.getType() == HitResult.Type.MISS || exclude.contains(hit.getBlockPos())) hits++;
                    totalRays++;
                }
            }
        }
        return (float) hits / (float) totalRays;
    }

    private float getFallDamage() {
        LocalPlayer player = MC.player;
        if (!bool("predict-fall-damage") || player.fallDistance <= 3.0f) return 0.0f;

        Module noFall = ModuleRegistry.get("no-fall");
        if (noFall != null && noFall.isEnabled()) return 0.0f;

        if (bool("ignore-elytra") && player.isFallFlying() && player.hasPose(Pose.FALL_FLYING)) return 0.0f;

        BlockPos collision = FallingPlayerSim.findCollision(player, 20);
        float multiplier = fallDamageMultiplier(collision, player);
        if (multiplier <= 0.0f) return 0.0f;

        float amount = ((AutismLivingEntityAccessor) player)
            .autism$calculateFallDamage(player.fallDistance, multiplier);
        return getEffectiveDamage(player.damageSources().fall(), amount);
    }

    private static float fallDamageMultiplier(BlockPos pos, LocalPlayer player) {
        if (pos == null) return 1.0f;
        Block block = MC.level.getBlockState(pos).getBlock();
        if (block == Blocks.WATER || block == Blocks.COBWEB || block == Blocks.POWDER_SNOW) return 0.0f;
        if (block == Blocks.HAY_BLOCK || block == Blocks.HONEY_BLOCK) return 0.2f;
        if (block == Blocks.SLIME_BLOCK) return player.isSuppressingBounce() ? 1.0f : 0.0f;
        if (block instanceof BedBlock) return 0.5f;
        return 1.0f;
    }

    private static float getEffectiveDamage(DamageSource source, float damage) {
        LocalPlayer player = MC.player;
        if (((AutismEntityAccessor) player).autism$isInvulnerableToBase(source) || player.isDeadOrDying()) {
            return 0.0f;
        }

        float amount = damage;
        if (player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return 0.0f;
        }

        if (source.scalesWithDifficulty()) {
            Difficulty difficulty = MC.level.getDifficulty();
            if (difficulty == Difficulty.PEACEFUL) {
                amount = 0.0f;
            } else if (difficulty == Difficulty.EASY) {
                amount = Math.min(amount / 2.0f + 1.0f, amount);
            } else if (difficulty == Difficulty.HARD) {
                amount = amount * 3.0f / 2.0f;
            }
        }

        if (amount == 0.0f) return 0.0f;
        if (source.is(DamageTypeTags.IS_FIRE) && player.hasEffect(MobEffects.FIRE_RESISTANCE)) return 0.0f;

        amount -= getBlockedDamage(player, source, amount);
        if (amount == 0.0f) return 0.0f;

        amount = ((AutismLivingEntityAccessor) player).autism$getDamageAfterArmorAbsorb(source, amount);
        amount = ((AutismLivingEntityAccessor) player).autism$getDamageAfterMagicAbsorb(source, amount);
        return amount;
    }

    private static float getBlockedDamage(LocalPlayer player, DamageSource source, float amount) {
        if (amount <= 0.0f) return 0.0f;
        ItemStack blocking = player.getItemBlockingWith();
        if (blocking == null) return 0.0f;
        BlocksAttacks blocksAttacks = blocking.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return 0.0f;
        if (blocksAttacks.bypassedBy().isPresent()
            && blocksAttacks.bypassedBy().get().contains(source.typeHolder())) {
            return 0.0f;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) return 0.0f;

        float horizontalAngle = (float) Math.PI;
        Vec3 sourcePosition = source.getSourcePosition();
        if (sourcePosition != null) {
            Vec3 view = viewVectorAtZeroPitch(player);
            Vec3 direction = sourcePosition.subtract(player.position());
            direction = new Vec3(direction.x, 0.0, direction.z).normalize();
            horizontalAngle = (float) Math.acos(direction.dot(view));
        }

        return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
    }

    private static Vec3 viewVectorAtZeroPitch(LocalPlayer player) {
        float yaw = player.getYHeadRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private static boolean isInHole() {
        BlockPos feet = feetBlockPos();
        for (Direction direction : HOLE_DIRECTIONS) {
            if (!isBlastResistant(feet.relative(direction))) return false;
        }
        return true;
    }

    private static boolean isBurrowed() {
        return isBlastResistant(feetBlockPos());
    }

    private static boolean isBlastResistant(BlockPos pos) {
        return MC.level.getBlockState(pos).getBlock().getExplosionResistance() >= 600.0f;
    }

    private static BlockPos feetBlockPos() {
        AABB box = MC.player.getBoundingBox();
        return new BlockPos(
            Mth.floor(Mth.lerp(0.5, box.minX, box.maxX)),
            Mth.ceil(box.minY),
            Mth.floor(Mth.lerp(0.5, box.minZ, box.maxZ)));
    }

    private static List<BlockPos> buildSphere(float radius) {
        List<BlockPos> out = new ArrayList<>();
        int r = Mth.ceil(radius);
        float rSq = radius * radius;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z <= rSq) out.add(new BlockPos(x, y, z));
                }
            }
        }
        out.sort(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ()));
        return out;
    }

    private static final class FallingPlayerSim {
        private final LocalPlayer player;
        private double x, y, z;
        private double motionX, motionY, motionZ;
        private int simulatedTicks;

        private FallingPlayerSim(LocalPlayer player) {
            this.player = player;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            Vec3 delta = player.getDeltaMovement();
            this.motionX = delta.x;
            this.motionY = delta.y;
            this.motionZ = delta.z;
        }

        static BlockPos findCollision(LocalPlayer player, int ticks) {
            FallingPlayerSim sim = new FallingPlayerSim(player);
            Vec3 rotationVec = player.getLookAngle();
            for (int i = 0; i < ticks; i++) {
                Vec3 start = new Vec3(sim.x, sim.y, sim.z);
                sim.calculateForTick(rotationVec);
                Vec3 end = new Vec3(sim.x, sim.y, sim.z);
                AABB box = player.getDimensions(player.getPose()).makeBoundingBox(start)
                    .expandTowards(end.subtract(start));
                Optional<BlockPos> support = MC.level.findSupportingBlock(player, box);
                if (support.isPresent()) return support.get();
            }
            return null;
        }

        private void calculateForTick(Vec3 rotationVec) {
            if (player.isFallFlying()) {
                calculateElytraTick(rotationVec);
            } else {
                calculateFreeFallTick();
            }
            this.x += this.motionX;
            this.y += this.motionY;
            this.z += this.motionZ;
            this.simulatedTicks++;
        }

        private void calculateFreeFallTick() {
            double gravity = player.getGravity();
            if (motionY <= 0.0 && hasStatusEffect(MobEffects.SLOW_FALLING)) {
                motionY -= Math.min(gravity, 0.01);
            } else {
                motionY -= gravity;
            }

            float speed = player.getSpeed() * 0.1f;
            if (speed > 0.0f) {
                Vec3 input = AutismEntityAccessor.autism$getInputVector(movementInput(), speed, player.getYRot());
                motionX += input.x;
                motionZ += input.z;
            }

            motionX *= BASE_HORIZONTAL_AIR_DRAG;
            motionY *= BASE_VERTICAL_AIR_DRAG;
            motionZ *= BASE_HORIZONTAL_AIR_DRAG;
        }

        private void calculateElytraTick(Vec3 rotationVec) {
            double gravity = 0.08;
            if (motionY <= 0.0 && hasStatusEffect(MobEffects.SLOW_FALLING)) {
                gravity = 0.01;
            }

            double pitchRad = player.getXRot() * Mth.DEG_TO_RAD;
            double k = Math.sqrt(rotationVec.x * rotationVec.x + rotationVec.z * rotationVec.z);
            double l = Math.sqrt(motionX * motionX + motionZ * motionZ);
            double m = rotationVec.length();
            double n = Mth.cos((float) pitchRad);
            n = n * n * Math.min(1.0, m / 0.4);

            Vec3 vec = new Vec3(motionX, motionY + gravity * (-1.0 + n * 0.75), motionZ);

            if (vec.y < 0.0 && k > 0.0) {
                double q = vec.y * -0.1 * n;
                vec = vec.add(rotationVec.x * q / k, q, rotationVec.z * q / k);
            }
            if (pitchRad < 0.0 && k > 0.0) {
                double q = l * (-Mth.sin((float) pitchRad)) * 0.04;
                vec = vec.add(-rotationVec.x * q / k, q * 3.2, -rotationVec.z * q / k);
            }
            if (k > 0.0) {
                vec = vec.add((rotationVec.x / k * l - vec.x) * 0.1, 0.0,
                    (rotationVec.z / k * l - vec.z) * 0.1);
            }

            vec.add(AutismEntityAccessor.autism$getInputVector(movementInput(), 0.02f, player.getYRot()));

            float blockSpeedFactor = ((AutismPlayerAccessor) player).autism$getBlockSpeedFactor();
            motionX = vec.x * ELYTRA_HORIZONTAL_AIR_DRAG * blockSpeedFactor;
            motionY = vec.y * ELYTRA_VERTICAL_AIR_DRAG;
            motionZ = vec.z * ELYTRA_HORIZONTAL_AIR_DRAG * blockSpeedFactor;
        }

        private boolean hasStatusEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
            MobEffectInstance instance = player.getEffect(effect);
            return instance != null && instance.getDuration() >= simulatedTicks;
        }

        private Vec3 movementInput() {
            var keyPresses = player.input.keyPresses;
            float forward = (keyPresses.forward() ? 1.0f : 0.0f) - (keyPresses.backward() ? 1.0f : 0.0f);
            float sideways = (keyPresses.left() ? 1.0f : 0.0f) - (keyPresses.right() ? 1.0f : 0.0f);
            return new Vec3(sideways * 0.98, 0.0, forward * 0.98);
        }
    }
}
