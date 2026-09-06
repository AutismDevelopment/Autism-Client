package autismclient.util.multi;

import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismAutoTool;
import autismclient.util.AutismNotifications;
import autismclient.util.RegistryListCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MultiPovModuleController {
    public record WireMove(Vec3 position, boolean onGround) {}

    private static MultiSession session;
    private static MultiPovControlContext control;
    private static int botEntityId = -1;
    private static boolean botAbilitiesTouched;
    private static Boolean lastAbilitiesFlying;
    private static String lastFlightMode = "";
    private static int antiKickDelayLeft;
    private static int antiKickOffLeft;
    private static double lastWireY = Double.NaN;
    private static int vulcanJumpCooldown;
    private static int vulcanGlideCooldown;
    private static boolean vulcanGlideRequested;
    private static double vulcanTargetY;
    private static long lastElytraEquipRevision = Long.MIN_VALUE;
    private static long lastElytraNoticeAt;
    private static int speedStage;
    private static double strafeSpeed = 0.2873D;
    private static double speedLastDistance;
    private static Vec3 speedLastPosition;
    private static long speedLimitTimer;
    private static BlockPos autoToolTarget;
    private static int autoToolPreviousSlot = -1;
    private static boolean autoToolSwitched;
    private static int autoToolInventorySwapSlot = -1;
    private static long autoToolSwitchAt;
    private static long autoToolRestoreAt = Long.MAX_VALUE;
    private static BlockPos fastBreakTarget;
    private static boolean fastBreakActivated;
    private static BlockPos instantRebreakTarget;
    private static Direction instantRebreakDirection = Direction.UP;
    private static int instantRebreakDelay;
    private static int noFallUseCooldown;

    private MultiPovModuleController() {}

    static void begin(MultiSession active, RemotePlayer bot) {
        session = active;
        botEntityId = bot == null ? -1 : bot.getId();
        control = MultiPovControlContext.resolve(active, botEntityId);
        resetTransient();

        Minecraft mc = Minecraft.getInstance();
        if (enabled("flight") && mc.player != null && !mc.player.isSpectator()) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().setFlyingSpeed(0.05F);
            if (!mc.player.getAbilities().instabuild) mc.player.getAbilities().mayfly = false;
        }
    }

    static void end(RemotePlayer bot) {
        clearAutoTool(true);
        clearBotFlight(bot);
        session = null;
        botEntityId = -1;
        control = MultiPovControlContext.resolve(null, -1);
        resetTransient();
    }

    static void suspendForMacro(RemotePlayer bot) {
        clearAutoTool(false);
        clearBotFlight(bot);
        vulcanGlideRequested = false;
        vulcanGlideCooldown = 0;
        fastBreakTarget = null;
        fastBreakActivated = false;
        instantRebreakTarget = null;
        noFallUseCooldown = 0;
    }

    private static void resetTransient() {
        botAbilitiesTouched = false;
        lastAbilitiesFlying = null;
        lastFlightMode = "";
        antiKickDelayLeft = 0;
        antiKickOffLeft = 0;
        lastWireY = Double.NaN;
        vulcanJumpCooldown = 0;
        vulcanGlideCooldown = 0;
        vulcanGlideRequested = false;
        vulcanTargetY = 0.0D;
        lastElytraEquipRevision = Long.MIN_VALUE;
        speedStage = 0;
        strafeSpeed = 0.2873D;
        speedLastDistance = 0.0D;
        speedLastPosition = null;
        speedLimitTimer = 0L;
        autoToolTarget = null;
        autoToolPreviousSlot = -1;
        autoToolSwitched = false;
        autoToolInventorySwapSlot = -1;
        autoToolSwitchAt = 0L;
        autoToolRestoreAt = Long.MAX_VALUE;
        fastBreakTarget = null;
        fastBreakActivated = false;
        instantRebreakTarget = null;
        instantRebreakDirection = Direction.UP;
        instantRebreakDelay = 0;
        noFallUseCooldown = 0;
    }

    public static boolean isControlling(Entity entity) {
        MultiPovControlContext active = control;
        return session != null && active != null && active.controls(entity)
            && entity.getId() == botEntityId && MultiPilot.isManualControlEntity(entity);
    }

    static void preparePhysics(RemotePlayer bot, boolean jump, boolean shift) {
        if (!isControlling(bot)) return;
        updateSpeedDistance(bot);
        Module flight = active("flight");
        if (flight == null) {
            clearBotFlight(bot);
            lastFlightMode = "";
            return;
        }

        String mode = setting(flight, "mode", "Abilities");
        if (!mode.equals(lastFlightMode)) {
            clearBotFlight(bot);
            lastFlightMode = mode;
            antiKickDelayLeft = integer(flight, "delay", 20);
            antiKickOffLeft = 0;
            lastWireY = Double.NaN;
            vulcanTargetY = bot.getY();
            vulcanJumpCooldown = 0;
            vulcanGlideCooldown = 0;
            vulcanGlideRequested = bot.isFallFlying();
        }

        if (vulcanJumpCooldown > 0) vulcanJumpCooldown--;
        if (vulcanGlideCooldown > 0) vulcanGlideCooldown--;
        switch (mode) {
            case "Velocity" -> prepareVelocityFlight(bot, flight, jump, shift);
            case "Vulcan" -> prepareVulcanFlight(bot, flight, jump, shift);
            default -> prepareAbilitiesFlight(bot, flight, jump, shift);
        }
    }

    private static void prepareAbilitiesFlight(RemotePlayer bot, Module flight, boolean jump, boolean shift) {
        tickNormalAntiKick(flight);
        boolean flying = antiKickOffLeft <= 0;
        bot.getAbilities().setFlyingSpeed((float) decimal(flight, "speed", 0.10D));
        bot.getAbilities().flying = flying;
        if (!bot.getAbilities().instabuild) bot.getAbilities().mayfly = true;
        botAbilitiesTouched = true;

        if (flying) {
            double vertical = abilitiesVerticalImpulse(bot.getAbilities().getFlyingSpeed(), jump, shift);
            if (vertical != 0.0D) bot.setDeltaMovement(bot.getDeltaMovement().add(0.0D, vertical, 0.0D));
        }
        sendAbilitiesIfChanged(bot, flying);
    }

    static double abilitiesVerticalImpulse(double flyingSpeed, boolean jump, boolean shift) {
        return ((jump ? 1.0D : 0.0D) - (shift ? 1.0D : 0.0D)) * flyingSpeed * 3.0D;
    }

    private static void prepareVelocityFlight(RemotePlayer bot, Module flight, boolean jump, boolean shift) {
        tickNormalAntiKick(flight);
        bot.getAbilities().flying = false;
        if (!bot.getAbilities().instabuild) bot.getAbilities().mayfly = false;
        botAbilitiesTouched = true;
        double speed = decimal(flight, "speed", 0.10D);
        double vertical = speed * (bool(flight, "vertical-speed-match", false) ? 10.0D : 5.0D);
        double y = 0.0D;
        if (jump) y += vertical;
        if (shift && !bool(flight, "no-sneak", false)) y -= vertical;
        bot.setDeltaMovement(0.0D, y, 0.0D);
        sendAbilitiesIfChanged(bot, false);
    }

    private static void tickNormalAntiKick(Module flight) {
        if (!"Normal".equals(setting(flight, "anti-kick-mode", "Packet"))) {
            antiKickOffLeft = 0;
            return;
        }
        if (antiKickOffLeft > 0) antiKickOffLeft--;
        if (antiKickDelayLeft > 0) antiKickDelayLeft--;
        if (antiKickDelayLeft <= 0) {
            antiKickDelayLeft = Math.max(1, integer(flight, "delay", 20));
            antiKickOffLeft = Math.max(1, integer(flight, "off-time", 1));
        }
    }

    private static void prepareVulcanFlight(RemotePlayer bot, Module flight, boolean jump, boolean shift) {
        clearBotAbilities(bot);
        if (!ensureVulcanElytra(bot, flight)) return;
        if (bot.onGround()) vulcanGlideRequested = false;
        if (bot.onGround() && vulcanJumpCooldown <= 0) {
            bot.jumpFromGround();
            vulcanJumpCooldown = 3;
        }
        requestFallFlying(bot);
        if (jump) {
            vulcanTargetY = Math.max(vulcanTargetY + (bot.isFallFlying() ? 0.04D : 0.12D), bot.getY());
            if ((!bot.isFallFlying() || bot.getY() < vulcanTargetY - 0.08D
                || bot.getDeltaMovement().y < -0.055D) && vulcanJumpCooldown <= 0) {
                bot.jumpFromGround();
                vulcanJumpCooldown = bot.isFallFlying() ? 12 : 3;
            }
        } else if (shift) {
            vulcanTargetY = bot.getY() - 0.35D;
        } else if (bot.getY() < vulcanTargetY - 0.22D && bot.getDeltaMovement().y < -0.02D
            && vulcanJumpCooldown <= 0) {
            bot.jumpFromGround();
            vulcanJumpCooldown = bot.isFallFlying() ? 12 : 3;
        }
    }

    private static void requestFallFlying(RemotePlayer bot) {
        if (session == null || bot.onGround() || bot.isFallFlying() || vulcanGlideRequested
            || vulcanGlideCooldown > 0) return;
        bot.tryToStartFallFlying();
        if (session.pilotPlayerCommand(bot, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)) {
            vulcanGlideRequested = true;
            vulcanGlideCooldown = 20;
        }
    }

    private static boolean ensureVulcanElytra(RemotePlayer bot, Module flight) {
        ItemStack chest = bot.getItemBySlot(EquipmentSlot.CHEST);
        boolean antiBreak = bool(flight, "anti-break-elytra", true);
        if (usableElytra(chest) && (!antiBreak || safeElytra(chest))) return true;
        int replacement = findElytra(bot, antiBreak);
        if (replacement >= 0 && session != null) {
            long revision = session.menuRevision();
            if (revision != lastElytraEquipRevision) {
                lastElytraEquipRevision = revision;
                session.pilotEquipChestFromInventory(replacement);
            }
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastElytraNoticeAt >= 2500L) {
            lastElytraNoticeAt = now;
            AutismNotifications.show("POV Flight needs a usable bot elytra.", 0xFFFF5A67);
        }
        return false;
    }

    private static int findElytra(RemotePlayer bot, boolean safeOnly) {
        int best = -1;
        int durability = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (!usableElytra(stack) || safeOnly && !safeElytra(stack)) continue;
            int remaining = remaining(stack);
            if (remaining > durability) {
                best = slot;
                durability = remaining;
            }
        }
        return best;
    }

    private static boolean usableElytra(ItemStack stack) {
        return stack != null && stack.is(Items.ELYTRA) && !stack.isBroken() && remaining(stack) > 0;
    }

    private static boolean safeElytra(ItemStack stack) {
        int threshold = Math.max(40, (int) Math.ceil(stack.getMaxDamage() * 0.10D));
        return usableElytra(stack) && remaining(stack) > threshold;
    }

    private static int remaining(ItemStack stack) {
        return stack == null || !stack.isDamageableItem() ? Integer.MAX_VALUE
            : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static void sendAbilitiesIfChanged(RemotePlayer bot, boolean flying) {
        if (session == null || java.util.Objects.equals(lastAbilitiesFlying, flying)) return;
        lastAbilitiesFlying = flying;
        session.pilotAbilities(bot.getAbilities());
    }

    private static void clearBotFlight(RemotePlayer bot) {
        if (bot == null) return;
        clearBotAbilities(bot);
        if ("Vulcan".equals(lastFlightMode) && bot.isFallFlying()) bot.stopFallFlying();
    }

    private static void clearBotAbilities(RemotePlayer bot) {
        if (bot == null) return;
        if (botAbilitiesTouched) {
            bot.getAbilities().setFlyingSpeed(0.05F);
            bot.getAbilities().flying = false;
            if (!bot.getAbilities().instabuild) bot.getAbilities().mayfly = false;
            if (session != null) session.pilotAbilities(bot.getAbilities());
        }
        botAbilitiesTouched = false;
        lastAbilitiesFlying = null;
    }

    static boolean forceSneak(RemotePlayer bot) {
        return isControlling(bot) && enabled("sneak") && !bot.getAbilities().flying;
    }

    static boolean wantsSprint(boolean forward, boolean back, boolean left, boolean right,
                               boolean sneaking, boolean usingItem, boolean hungerOk,
                               boolean horizontalCollision) {
        if (sneaking || usingItem || !hungerOk) return false;
        Module sprint = active("sprint");
        boolean omni = sprint != null && ("Omnidirectional".equals(setting(sprint, "mode", "Legit"))
            || "Omnirotational".equals(setting(sprint, "mode", "Legit")));
        return !horizontalCollision && (forward || omni && (back || left || right));
    }

    public static float flyingSpeed(Player player) {
        if (!isControlling(player)) return -1.0F;
        Module flight = active("flight");
        if (flight == null || !"Velocity".equals(setting(flight, "mode", "Abilities"))) return -1.0F;
        return (float) decimal(flight, "speed", 0.10D) * (player.isSprinting() ? 15.0F : 10.0F);
    }

    public static boolean flightNoSneak(Player player) {
        Module flight = isControlling(player) ? active("flight") : null;
        return flight != null && "Velocity".equals(setting(flight, "mode", "Abilities"))
            && bool(flight, "no-sneak", false);
    }

    static boolean usesAbilitiesFlight(Player player) {
        Module flight = isControlling(player) ? active("flight") : null;
        return flight != null && "Abilities".equals(setting(flight, "mode", "Abilities"));
    }

    public static Vec3 modifyMovement(Entity entity, MoverType type, Vec3 movement) {
        if (!isControlling(entity) || type != MoverType.SELF || movement == null || !(entity instanceof Player bot)) {
            return movement;
        }
        Module speed = active("speed");
        if (speed == null || !speedAllowed(bot, speed)) return movement;
        boolean liquid = bot.isInWater() || bot.isInLava();
        if (liquid && !bool(speed, "in-liquids", false)) return movement;
        if ("Strafe".equals(setting(speed, "mode", "Vanilla"))) {
            return strafeMovement(bot, speed, movement);
        }
        return vanillaSpeedMovement(bot, speed, movement);
    }

    public static Vec3 afterLiquidTravel(Entity entity, Vec3 movement) {
        if (!isControlling(entity) || !(entity instanceof Player bot) || movement == null) return movement;
        Module speed = active("speed");
        if (speed == null || !bool(speed, "in-liquids", false) || !speedAllowed(bot, speed)) return movement;
        return "Strafe".equals(setting(speed, "mode", "Vanilla"))
            ? strafeMovement(bot, speed, movement) : vanillaSpeedMovement(bot, speed, movement);
    }

    private static boolean speedAllowed(Player bot, Module speed) {
        if (bot.isFallFlying() || bot.onClimbable() || bot.getVehicle() != null) return false;
        if (!bool(speed, "when-sneaking", false) && bot.isShiftKeyDown()) return false;
        boolean liquid = bot.isInWater() || bot.isInLava();
        return !bool(speed, "only-on-ground", false) || bot.onGround()
            || liquid && bool(speed, "in-liquids", false)
            || !"Vanilla".equals(setting(speed, "mode", "Vanilla"));
    }

    private static Vec3 vanillaSpeedMovement(Player bot, Module speed, Vec3 movement) {
        Vec3 horizontal = horizontalFromInput(bot, decimal(speed, "vanilla-speed", 5.6D) / 20.0D);
        return new Vec3(horizontal.x, movement.y, horizontal.z);
    }

    private static Vec3 strafeMovement(Player bot, Module speed, Vec3 movement) {
        boolean moving = Math.abs(bot.xxa) > 1.0E-5F || Math.abs(bot.zza) > 1.0E-5F;
        if (!moving) {
            speedStage = 0;
            return movement;
        }
        double base = defaultSpeed(bot);
        if (speedStage == 0) {
            speedStage = 1;
            strafeSpeed = 1.1799999475479126D * base - 0.01D;
        } else if (speedStage == 1 && bot.onGround()) {
            movement = new Vec3(movement.x, jumpVelocity(bot, 0.40123128D), movement.z);
            strafeSpeed *= decimal(speed, "strafe-speed", 1.6D);
            speedStage = 2;
        } else if (speedStage == 2) {
            strafeSpeed = speedLastDistance - 0.76D * (speedLastDistance - base);
            speedStage = 3;
        } else {
            strafeSpeed = speedLastDistance - speedLastDistance / 159.0D;
        }
        strafeSpeed = Math.max(base, strafeSpeed);
        if (bool(speed, "speed-limit", false)) {
            long now = System.currentTimeMillis();
            if (now - speedLimitTimer > 2500L) speedLimitTimer = now;
            strafeSpeed = Math.min(strafeSpeed, now - speedLimitTimer > 1250L ? 0.44D : 0.43D);
        }
        Vec3 horizontal = strafeFromInput(bot, strafeSpeed);
        return new Vec3(horizontal.x, movement.y, horizontal.z);
    }

    private static Vec3 horizontalFromInput(Player player, double speed) {
        double forward = Math.signum(player.zza);
        double side = Math.signum(player.xxa);
        if (forward == 0.0D && side == 0.0D) return Vec3.ZERO;
        double length = forward != 0.0D && side != 0.0D ? speed / Math.sqrt(2.0D) : speed;
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new Vec3((-sin * forward + cos * side) * length, 0.0D,
            (cos * forward + sin * side) * length);
    }

    private static Vec3 strafeFromInput(Player player, double speed) {
        float forward = Math.signum(player.zza);
        float side = Math.signum(player.xxa);
        if (forward == 0.0F && side == 0.0F) return Vec3.ZERO;
        float yaw = player.getYRot();
        float strafe = 90.0F * side;
        if (forward != 0.0F) strafe *= forward * 0.5F;
        yaw -= strafe;
        if (forward < 0.0F) yaw -= 180.0F;
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians) * speed, 0.0D, Math.cos(radians) * speed);
    }

    private static double defaultSpeed(Player bot) {
        double speed = 0.2873D;
        net.minecraft.world.effect.MobEffectInstance fast = bot.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
        if (fast != null) speed *= 1.0D + 0.2D * (fast.getAmplifier() + 1);
        net.minecraft.world.effect.MobEffectInstance slow = bot.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        if (slow != null) speed /= 1.0D + 0.2D * (slow.getAmplifier() + 1);
        return speed;
    }

    private static double jumpVelocity(Player bot, double base) {
        net.minecraft.world.effect.MobEffectInstance jump = bot.getEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST);
        return jump == null ? base : base + (jump.getAmplifier() + 1) * 0.1D;
    }

    private static void updateSpeedDistance(RemotePlayer bot) {
        Vec3 current = bot.position();
        if (speedLastPosition != null) {
            double dx = current.x - speedLastPosition.x;
            double dz = current.z - speedLastPosition.z;
            speedLastDistance = Math.sqrt(dx * dx + dz * dz);
        }
        speedLastPosition = current;
    }

    public static boolean cancelNoFallBounce(Entity entity) {
        Module noFall = isControlling(entity) ? active("no-fall") : null;
        return noFall != null && bool(noFall, "anti-bounce", true);
    }

    static WireMove transformWireMove(Vec3 position, boolean onGround, Vec3 deltaMovement) {
        Vec3 wirePosition = position;
        boolean wireGround = onGround;
        Module flight = active("flight");
        if (flight != null && !"Vulcan".equals(setting(flight, "mode", "Abilities"))
            && "Packet".equals(setting(flight, "anti-kick-mode", "Packet")) && !onGround) {
            if (antiKickDelayLeft > 0) antiKickDelayLeft--;
            if (antiKickDelayLeft <= 0) {
                double baseline = Double.isFinite(lastWireY) ? lastWireY : position.y;
                wirePosition = new Vec3(position.x, baseline - 0.03130D, position.z);
                antiKickDelayLeft = Math.max(1, integer(flight, "delay", 20));
            }
            lastWireY = position.y;
        } else {
            lastWireY = position.y;
        }

        Module noFall = active("no-fall");
        if (noFall != null && "Packet".equals(setting(noFall, "mode", "Packet"))
            && !wireGround && shouldSpoofNoFall(noFall, deltaMovement)) {
            wireGround = true;
        }
        return new WireMove(wirePosition, wireGround);
    }

    private static boolean shouldSpoofNoFall(Module noFall, Vec3 delta) {
        RemotePlayer bot = botEntity();
        if (bot == null || bot.getAbilities().instabuild || bot.isFallFlying()) return false;
        if (bool(noFall, "pause-on-mace", true) && bot.getMainHandItem().is(Items.MACE)) return false;
        return active("flight") != null || delta != null && delta.y <= -0.5D;
    }

    static boolean shouldCancelBlockMine(Minecraft mc, BlockPos pos) {
        Module noInteract = active("no-interact");
        if (noInteract == null || mc == null || mc.level == null || pos == null) return false;
        List<String> configured = list(noInteract, "block-mine");
        if (configured.isEmpty()) return false;
        String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
        boolean listed = configured.stream().anyMatch(entry -> RegistryListCodec.matches(id, entry));
        return "WhiteList".equalsIgnoreCase(setting(noInteract, "block-mine-mode", "BlackList"))
            ? !listed : listed;
    }

    static void onBlockStart(RemotePlayer bot, BlockPos pos, Direction direction, BlockState state) {
        if (!isControlling(bot) || pos == null) return;
        long now = System.currentTimeMillis();
        Module autoTool = active("auto-tool");
        if (autoTool != null && (!bool(autoTool, "require-sneaking", false) || bot.isShiftKeyDown())) {
            if (!pos.equals(autoToolTarget)) {
                autoToolTarget = pos.immutable();
                autoToolSwitched = false;

                if (autoToolPreviousSlot < 0) {
                    autoToolPreviousSlot = session == null ? -1 : session.selectedHotbar();
                }
                autoToolSwitchAt = now + sampledDelay(integer(autoTool, "switch-delay-ms", 25));
                autoToolRestoreAt = Long.MAX_VALUE;
            }
        } else {
            clearAutoTool(false);
        }

        Module fast = active("fast-break");
        if (fast != null && "Packet".equals(setting(fast, "mode", "Damage"))
            && fastBreakPasses(fast, state)) {
            if (!pos.equals(fastBreakTarget)) {
                fastBreakTarget = pos.immutable();
                int chance = Math.max(0, Math.min(100, integer(fast, "activation-chance", 100)));
                fastBreakActivated = ThreadLocalRandom.current().nextInt(100) < chance;
            }
        } else {
            fastBreakTarget = null;
            fastBreakActivated = false;
        }

        if (active("instant-rebreak") != null) {
            instantRebreakTarget = pos.immutable();
            instantRebreakDirection = direction == null ? Direction.UP : direction;
            instantRebreakDelay = 0;
        }
    }

    static void onBlockEnd(boolean restoreAutoTool) {
        autoToolTarget = null;
        if (!restoreAutoTool || autoToolPreviousSlot < 0) {
            clearAutoTool(false);
            return;
        }
        Module autoTool = active("auto-tool");
        if (autoTool == null || !bool(autoTool, "switch-back", false)) {
            clearAutoTool(false);
            return;
        }
        autoToolRestoreAt = System.currentTimeMillis()
            + sampledDelay(integer(autoTool, "switch-back-delay-ms", 100));
    }

    private static void clearAutoTool(boolean restore) {
        if (restore && session != null && autoToolPreviousSlot >= 0) {
            if (autoToolInventorySwapSlot >= 9) {
                session.pilotSwapInventoryToHotbar(autoToolInventorySwapSlot, autoToolPreviousSlot);
            }
            session.pilotSelectHotbar(autoToolPreviousSlot);
        }
        autoToolTarget = null;
        autoToolPreviousSlot = -1;
        autoToolSwitched = false;
        autoToolInventorySwapSlot = -1;
        autoToolSwitchAt = 0L;
        autoToolRestoreAt = Long.MAX_VALUE;
    }

    static boolean fastBreakStartsInstant(RemotePlayer bot, BlockState state, BlockPos pos, float baseProgress) {
        Module fast = active("fast-break");
        return fast != null && "Damage".equals(setting(fast, "mode", "Damage"))
            && bool(fast, "instamine", true) && baseProgress > 0.5F && fastBreakPasses(fast, state);
    }

    static float modifyDestroyProgress(float progress, BlockState state) {
        Module fast = active("fast-break");
        if (fast == null) return progress;
        String mode = setting(fast, "mode", "Damage");
        if ("Normal".equals(mode) && fastBreakPasses(fast, state)) {
            return (float) (progress * Math.max(0.0D, decimal(fast, "modifier", 1.4D)));
        }
        if ("Haste".equals(mode)) {
            int amplifier = Math.max(1, integer(fast, "haste-amplifier", 2));
            return progress * (1.0F + amplifier * 0.2F);
        }
        return progress;
    }

    static float applyDamageFinishThreshold(float accumulated, float delta) {
        Module fast = active("fast-break");
        if (fast != null && "Damage".equals(setting(fast, "mode", "Damage"))
            && bool(fast, "instamine", true) && accumulated > 0.0F && accumulated + delta >= 0.7F) {
            return 1.0F;
        }
        return accumulated + delta;
    }

    static void onBlockProgress(BlockPos pos, Direction direction) {
        Module fast = active("fast-break");
        if (session == null || fast == null || !"Packet".equals(setting(fast, "mode", "Damage"))
            || !fastBreakActivated || pos == null || !pos.equals(fastBreakTarget)) return;
        sendStop(pos, direction, false);
    }

    static void afterStop(BlockPos pos, Direction direction) {
        Module fast = active("fast-break");
        if (session == null || fast == null || !"Damage".equals(setting(fast, "mode", "Damage"))
            || !bool(fast, "grim-bypass", false) || pos == null) return;
        session.pilotSend(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
            pos.above(), direction == null ? Direction.UP : direction));
    }

    static void tickModules(Minecraft mc, RemotePlayer bot, boolean breaking, BlockPos breakPos, Direction breakDir) {
        if (!isControlling(bot) || session == null || mc == null || mc.level == null) return;
        if (PacketTeleportController.ownsPov(session)) return;
        tickAutoTool(mc, bot, breaking, breakPos);
        tickInstantRebreak(mc, bot);
        tickNoFall(mc, bot);
    }

    private static void tickAutoTool(Minecraft mc, RemotePlayer bot, boolean breaking, BlockPos breakPos) {
        if (autoToolRestoreAt != Long.MAX_VALUE && System.currentTimeMillis() >= autoToolRestoreAt) {
            clearAutoTool(true);
        }
        Module autoTool = active("auto-tool");
        if (autoTool == null) {
            clearAutoTool(true);
            return;
        }
        if (!breaking || breakPos == null || !breakPos.equals(autoToolTarget)
            || autoToolSwitched || System.currentTimeMillis() < autoToolSwitchAt) return;
        if (bool(autoTool, "require-sneaking", false) && !bot.isShiftKeyDown()) return;
        BlockState state = mc.level.getBlockState(breakPos);
        int selected = control == null ? session.selectedHotbar() : control.selectedHotbar();
        int limit = bool(autoTool, "consider-inventory", false) ? 36 : 9;
        int best = bestToolSlot(mc, bot, state, limit,
            bool(autoTool, "ignore-durability", true), bool(autoTool, "prefer-silk-touch", false), selected);
        if (best < 0 || best == selected) {
            autoToolSwitched = true;
            return;
        }
        boolean sent = best < 9 ? session.pilotSelectHotbar(best)
            : session.pilotSwapInventoryToHotbar(best, selected);
        if (sent) {
            autoToolSwitched = true;
            autoToolInventorySwapSlot = best >= 9 ? best : -1;
        }
    }

    private static int bestToolSlot(Minecraft mc, RemotePlayer bot, BlockState state, int limit,
                                    boolean ignoreDurability, boolean preferSilk, int selected) {
        int best = -1;
        float bestSpeed = -1.0F;
        for (int slot = 0; slot < Math.min(36, limit); slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (!ignoreDurability && stack.isDamageableItem() && remaining(stack) <= 2) continue;
            float speed = AutismAutoTool.destroySpeed(mc, stack, state);
            if (speed <= 1.0F) continue;
            if (preferSilk && !AutismAutoTool.hasSilkTouch(mc, stack)) continue;
            if (speed > bestSpeed || speed == bestSpeed && slotDistance(slot, selected) < slotDistance(best, selected)) {
                best = slot;
                bestSpeed = speed;
            }
        }
        if (best < 0 && preferSilk) {
            return bestToolSlot(mc, bot, state, limit, ignoreDurability, false, selected);
        }
        if (best >= 0) {
            float held = AutismAutoTool.destroySpeed(mc, bot.getInventory().getItem(selected), state);
            if (bestSpeed <= held && !(preferSilk
                && !AutismAutoTool.hasSilkTouch(mc, bot.getInventory().getItem(selected)))) return selected;
        }
        return best;
    }

    private static int slotDistance(int slot, int selected) {
        if (slot < 0) return Integer.MAX_VALUE;
        return slot < 9 ? Math.abs(slot - selected) : 100 + slot;
    }

    private static void tickInstantRebreak(Minecraft mc, RemotePlayer bot) {
        Module instant = active("instant-rebreak");
        if (instant == null) {
            instantRebreakTarget = null;
            return;
        }
        if (instantRebreakTarget == null || mc.level.isOutsideBuildHeight(instantRebreakTarget)
            || mc.level.getBlockState(instantRebreakTarget).isAir()) return;
        if (bool(instant, "only-pick", true) && !bot.getMainHandItem().is(ItemTags.PICKAXES)) return;
        if (instantRebreakDelay++ < Math.max(0, integer(instant, "delay", 0))) return;
        instantRebreakDelay = 0;
        if (bool(instant, "rotate", false)) {
            Vec3 center = Vec3.atCenterOf(instantRebreakTarget);
            float yaw = yawTo(bot, center);
            float pitch = pitchTo(bot, center);
            session.pilotSend(new ServerboundMovePlayerPacket.Rot(yaw, pitch, bot.onGround(), bot.horizontalCollision));
        }
        sendStop(instantRebreakTarget, instantRebreakDirection, true);
    }

    private static void sendStop(BlockPos pos, Direction direction, boolean swing) {
        if (session == null || pos == null) return;
        session.pilotSend(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos,
            direction == null ? Direction.UP : direction, session.nextUseSeq()));
        if (swing) session.pilotSend(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    private static void tickNoFall(Minecraft mc, RemotePlayer bot) {
        if (noFallUseCooldown > 0) noFallUseCooldown--;
        Module noFall = active("no-fall");
        if (noFall == null || noFallUseCooldown > 0 || bot.getAbilities().instabuild || bot.isFallFlying()
            || bot.isInWater() || bot.isInLava()) return;
        String mode = setting(noFall, "mode", "Packet");
        if ("Packet".equals(mode)) return;
        if (bool(noFall, "pause-on-mace", true) && bot.getMainHandItem().is(Items.MACE)) return;
        boolean airPlace = "AirPlace".equals(mode);
        double threshold = airPlace && "BeforeDeath".equals(setting(noFall, "air-place-mode", "BeforeDeath"))
            ? Math.max(2.0D, bot.getHealth() + bot.getAbsorptionAmount()) : airPlace ? 2.0D : 3.0D;
        if (bot.fallDistance <= threshold) return;
        if (bool(noFall, "anchor", true)) {
            bot.setPos(Math.floor(bot.getX()) + 0.5D, bot.getY(), Math.floor(bot.getZ()) + 0.5D);
        }
        int old = session.selectedHotbar();
        int slot = findNoFallSlot(bot, noFall, airPlace);
        if (slot < 0 || !session.pilotSelectHotbar(slot)) return;
        try {
            ItemStack stack = bot.getInventory().getItem(slot);
            BlockHitResult placementHit;
            if (airPlace) {
                BlockPos below = bot.blockPosition().below();
                placementHit = new BlockHitResult(Vec3.atCenterOf(below).add(0.0D, 0.5D, 0.0D),
                    Direction.UP, below, false);
            } else {
                HitResult ray = mc.level.clip(new ClipContext(
                    bot.position(), bot.position().subtract(0.0D, 5.0D, 0.0D),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, bot));
                if (!(ray instanceof BlockHitResult blockRay) || ray.getType() != HitResult.Type.BLOCK) return;
                placementHit = new BlockHitResult(
                    Vec3.atCenterOf(blockRay.getBlockPos()).add(0.0D, 0.5D, 0.0D),
                    Direction.UP, blockRay.getBlockPos(), false);
            }
            Vec3 aim = placementHit.getLocation();
            float yaw = yawTo(bot, aim);
            float pitch = pitchTo(bot, aim);
            session.pilotSend(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, bot.onGround(), bot.horizontalCollision));
            if (bool(noFall, "client-rotate", false)) MultiPilot.applyModuleRotation(bot, yaw, pitch);
            if (!airPlace && (stack.is(Items.WATER_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET))) {
                session.pilotSend(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND, session.nextUseSeq(), yaw, pitch));
            } else {
                session.pilotSend(new ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND, placementHit, session.nextUseSeq()));
            }
            session.pilotSend(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            noFallUseCooldown = 10;
        } finally {
            session.pilotSelectHotbar(old);
        }
    }

    private static int findNoFallSlot(RemotePlayer bot, Module noFall, boolean blockOnly) {
        net.minecraft.world.item.Item preferred = switch (setting(noFall, "placed-item", "Bucket")) {
            case "PowderSnow" -> Items.POWDER_SNOW_BUCKET;
            case "HayBale" -> Items.HAY_BLOCK;
            case "Cobweb" -> Items.COBWEB;
            case "SlimeBlock" -> Items.SLIME_BLOCK;
            default -> session != null && session.takeoverDimension().endsWith("the_nether")
                ? Items.POWDER_SNOW_BUCKET : Items.WATER_BUCKET;
        };
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(preferred)
                && (!blockOnly || stack.getItem() instanceof net.minecraft.world.item.BlockItem)) return i;
        }
        if (blockOnly) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = bot.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
        }
        return -1;
    }

    private static boolean fastBreakPasses(Module fast, BlockState state) {
        if (fast == null || state == null || state.isAir()) return false;
        List<String> values = list(fast, "blocks");
        boolean whitelist = "Whitelist".equalsIgnoreCase(setting(fast, "blocks-filter", "Blacklist"));
        if (values.isEmpty()) return !whitelist;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean listed = values.stream().anyMatch(entry -> RegistryListCodec.matches(id, entry));
        return whitelist ? listed : !listed;
    }

    private static long sampledDelay(int base) {
        if (base <= 0) return 0L;
        int spread = Math.max(15, Math.min(180, Math.round(base * 0.55F)));
        return base + ThreadLocalRandom.current().nextInt(spread + 1);
    }

    private static float yawTo(Player player, Vec3 target) {
        return (float) (Math.toDegrees(Math.atan2(target.z - player.getZ(), target.x - player.getX())) - 90.0D);
    }

    private static float pitchTo(Player player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private static RemotePlayer botEntity() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(botEntityId) instanceof RemotePlayer bot ? bot : null;
    }

    private static Module active(String id) {
        Module module = ModuleRegistry.get(id);
        return module != null && module.isEnabled() ? module : null;
    }

    private static boolean enabled(String id) {
        return active(id) != null;
    }

    private static String setting(Module module, String id, String fallback) {
        if (module == null) return fallback;
        String value = module.value(id);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean bool(Module module, String id, boolean fallback) {
        String value = setting(module, id, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value);
    }

    private static int integer(Module module, String id, int fallback) {
        try {
            return Integer.parseInt(setting(module, id, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double decimal(Module module, String id, double fallback) {
        try {
            return Double.parseDouble(setting(module, id, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> list(Module module, String id) {
        String raw = setting(module, id, "");
        if (raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("\\|"))
            .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
