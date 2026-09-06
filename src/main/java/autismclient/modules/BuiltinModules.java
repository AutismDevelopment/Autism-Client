package autismclient.modules;
import autismclient.api.module.*;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismBookPayloadBuilder;
import autismclient.util.AutismBookFileReader;
import autismclient.util.AutismConfig;
import autismclient.util.AutismFakeCoords;
import autismclient.util.AutismInputGate;
import autismclient.util.AutoFishStopMacroFactory;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismHudManager;
import autismclient.util.AutismHumanRng;
import autismclient.util.AutismInputClicker;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismPlacementTick;
import autismclient.security.AutismItemNbtSanity;
import autismclient.util.AutismItemNbtInspector;
import autismclient.util.AutismItemCommandSerializer;
import autismclient.util.AutismKeyMappingBridge;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismWaypoints;
import autismclient.util.oresim.AutismOreGhostModels;
import autismclient.util.oresim.AutismOreSimEngine;
import autismclient.util.oresim.AutismOreSimOre;
import autismclient.util.oresim.AutismOreSimSeedStore;
import autismclient.util.AutismMouseInputSimulator;
import autismclient.util.QuantizedRotationSmoother;
import autismclient.util.AutismRotationUtil;
import autismclient.gui.screen.AutismHudEditorScreen;
import autismclient.util.PacketListCodec;
import autismclient.mixin.accessor.AutismFishingHookAccessor;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.mixin.accessor.AutismMobEffectInstanceAccessor;
import autismclient.mixin.accessor.AutismLocalPlayerAccessor;
import autismclient.mixin.accessor.AutismMovePlayerPacketAccessor;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.util.AutismAutoTool;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.RegistryListCodec;
import net.minecraft.world.level.block.state.BlockState;
import autismclient.util.macro.ServerTickTracker;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;
import autismclient.util.macro.MacroConditionUtil;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.NbtBookAction;
import autismclient.util.macro.WaitDurabilityAction;
import autismclient.util.macro.WaitFreeSlotsAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Unit;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class BuiltinModules {
    private static FastUseModule fastUseInstance;

    private BuiltinModules() {
    }

    static void register() {
        migrateUtilityModuleState("inv-move", AutismConfig.getGlobal().inventoryMove);
        migrateUtilityModuleState("xcarry", AutismConfig.getGlobal().xCarry);
        migrateUtilityModuleState("golden-lever", true);

        if (autismclient.util.AutismLiteVariant.enabled()) {
            ModuleRegistry.register(new HideModule());
            return;
        }
        ModuleRegistry.register(new FlightModule());
        ModuleRegistry.register(new SprintModule());
        ModuleRegistry.register(new ParkourModule());
        ModuleRegistry.register(new SpeedModule());
        ModuleRegistry.register(new BoatFlyModule());
        ModuleRegistry.register(new EntityControlModule());
        ModuleRegistry.register(new InvMoveModule());
        ModuleRegistry.register(new SneakModule());
        ModuleRegistry.register(new AirJumpModule());
        ModuleRegistry.register(new NoClipModule());
        ModuleRegistry.register(new PhaseModule());
        ModuleRegistry.register(new WaypointsModule());
        ModuleRegistry.register(new FastUseModule());
        ModuleRegistry.register(new FastBreakModule());
        ModuleRegistry.register(new AimAssistModule());
        ModuleRegistry.register(new AutoClickerModule());
        ModuleRegistry.register(new TriggerBotModule());

        ModuleRegistry.register(new AutoTotemModule());
        ModuleRegistry.register(new BedDefenderModule());
        ModuleRegistry.register(new SurroundModule());
        ModuleRegistry.register(new AnchorAuraModule());
        ModuleRegistry.register(new CrystalAuraModule());
        ModuleRegistry.register(new AutoTrapModule());
        ModuleRegistry.register(new KillAuraModule());
        ModuleRegistry.register(new AutoFarmModule());
        ModuleRegistry.register(new AutoArmorModule());
        ModuleRegistry.register(new AutoSignModule());
        ModuleRegistry.register(new AutoLoginModule());
        ModuleRegistry.register(new TpClickModule());
        migrateUtilityModuleState("antibot", true);
        ModuleRegistry.register(new AntiBotModule());
        ModuleRegistry.register(new AntiVanishModule());
        ModuleRegistry.register(new AutoFishModule());
        ModuleRegistry.register(new InstantRebreakModule());
        ModuleRegistry.register(new AutoToolModule());
        ModuleRegistry.register(new ScaffoldModule());
        ModuleRegistry.register(new NoFallModule());
        ModuleRegistry.register(new NoInteractModule());
        ModuleRegistry.register(new AirPlaceModule());
        ModuleRegistry.register(new GhostBlockModule());
        ModuleRegistry.register(new OffhandInteractModule());
        ModuleRegistry.register(new SpamModule());
        ModuleRegistry.register(new PayAllModule());
        ModuleRegistry.register(new BookBotModule());
        ModuleRegistry.register(new PacketCancellerModule());
        ModuleRegistry.register(new AutoReconnectModule());
        ModuleRegistry.register(new HideModule());
        ModuleRegistry.register(new InventoryTweaksModule());
        ModuleRegistry.register(new AntiHungerModule());
        ModuleRegistry.register(new SafeWalkModule());
        ModuleRegistry.register(new SpawnerEspModule());
        ModuleRegistry.register(new BetterTooltipsModule());
        ModuleRegistry.register(new FullbrightModule());
        ModuleRegistry.register(new FakeCoordsModule());
        ModuleRegistry.register(new XrayModule());
        ModuleRegistry.register(new WorldModule());
        ModuleRegistry.register(new ViewmodelModule());
        ModuleRegistry.register(new FreecamModule());
        ModuleRegistry.register(new FreeLookModule());
        ModuleRegistry.register(new EspModule());
        ModuleRegistry.register(new ChamsModule());
        ModuleRegistry.register(new ItemEspModule());
        ModuleRegistry.register(new NameTagsModule());
        ModuleRegistry.register(new TracersModule());
        ModuleRegistry.register(new TrajectoriesModule());
        ModuleRegistry.register(new StorageEspModule());
        ModuleRegistry.register(new BlockEspModule());
        ModuleRegistry.register(new HoleEspModule());
        ModuleRegistry.register(new NoRenderModule());
        ModuleRegistry.register(new XCarryModule());
        ModuleRegistry.register(new HudModule());
        ModuleRegistry.register(new AdminToolsModule());
        ModuleRegistry.register(new GoldenLeverModule());
        ModuleRegistry.register(new NameCensorModule());
        migrateUtilityModuleState("teams", true);
        ModuleRegistry.register(new TeamsModule());
        ModuleRegistry.register(new PingSpoofModule());
        ModuleRegistry.register(new BlinkModule());
    }

    private static void migrateUtilityModuleState(String id, boolean enabled) {
        AutismConfig config = AutismConfig.getGlobal();
        if (config.modules.containsKey(id)) return;
        AutismConfig.ModuleState state = config.modules.computeIfAbsent(id, ignored -> new AutismConfig.ModuleState());
        state.enabled = enabled;
    }

    public static boolean ownsManualFastUse() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.ownsManualInput();
    }

    public static boolean ownsManualFastExp() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.manualExpActive;
    }

    public static boolean beginManualFastUseClick() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.beginManualInputClick();
    }

    public static float manualFastExpUseYaw(float vanillaYaw) {
        if (!AutismInputClicker.isFastExpUseInProgress()) return vanillaYaw;
        FastUseModule fastUse = activeFastUse();
        AutismRotationUtil.Rotation rotation = fastUse == null ? null : fastUse.activeExpRotation();
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float manualFastExpUsePitch(float vanillaPitch) {
        if (!AutismInputClicker.isFastExpUseInProgress()) return vanillaPitch;
        FastUseModule fastUse = activeFastUse();
        AutismRotationUtil.Rotation rotation = fastUse == null ? null : fastUse.activeExpRotation();
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static float outgoingFastExpMovementYaw(net.minecraft.client.player.LocalPlayer player, float vanillaYaw) {
        AutismRotationUtil.Rotation rotation = activeFastExpRotation(player);
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingFastExpMovementPitch(net.minecraft.client.player.LocalPlayer player, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeFastExpRotation(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    private static AutismRotationUtil.Rotation activeFastExpRotation(net.minecraft.client.player.LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || client == null || player != client.player) return null;
        FastUseModule fastUse = activeFastUse();
        return fastUse == null ? null : fastUse.activeExpRotation();
    }

    private static FastUseModule activeFastUse() {
        FastUseModule fastUse = fastUseInstance;
        return fastUse != null && fastUse.isEnabled() ? fastUse : null;
    }

    static final class HideModule extends Module {
        HideModule() {
            super(PackHideState.HIDE_ID, "PanicMode", ModuleCategory.MISC, "Temporarily hides client modules and UI.");
        }

        @Override
        public void onEnable() {
            PackHideState.enable(this);
            autism$refreshDisguise();
        }

        @Override
        public void onDisable() {
            PackHideState.disableAndRestore(this);
            autism$refreshDisguise();
        }

        private void autism$refreshDisguise() {
            if (MC == null) return;

            autismclient.util.AutismWindowBranding.refresh(MC);

            if (!autismclient.util.AutismLiteVariant.enabled()
                    && MC.gui.screen() instanceof autismclient.gui.screen.AutismTitleScreen) {
                MC.gui.setScreen(new autismclient.gui.screen.AutismTitleScreen());
            }
        }

        @Override
        public boolean emitsToggleMessage() {
            return false;
        }
    }

    static final class InvMoveModule extends Module {
        InvMoveModule() {
            super("inv-move", "InvMove", ModuleCategory.MOVEMENT, "Allows movement keys in normal container screens.");
        }

        @Override
        public void onEnable() {
            AutismConfig.getGlobal().inventoryMove = true;
        }

        @Override
        public void onDisable() {
            AutismConfig.getGlobal().inventoryMove = false;
        }

        @Override
        public void tick() {
            autismclient.util.AutismInventoryMoveHelper.syncHeldMovementKeysIfSafe();
        }
    }

    static final class XCarryModule extends Module {
        XCarryModule() {
            super("xcarry", "XCarry", ModuleCategory.PLAYER, "Keeps stored items in crafting / result / armor / offhand slots when closing inventory.");
            add(new BoolSetting("use-crafting", "Use Crafting Grid", true)
                .description("Use all craft slots").build());
            add(new BoolSetting("use-armor",    "Use Armor Slots", true)
                .description("Use armor slots 5-8.").build());
            add(new BoolSetting("use-offhand",  "Use Offhand", true)
                .description("Use offhand slot.").build());
            add(new BoolSetting("carry-cursor", "Carry Cursor", true)
                .description("Keep cursor stack.").build());
        }

        @Override
        public void onEnable() {
            AutismConfig.getGlobal().xCarry = true;
        }

        @Override
        public void onDisable() {
            AutismConfig.getGlobal().xCarry = false;
        }
    }

    static final class FlightModule extends Module {
        private int delayLeft;
        private int offLeft;
        private double lastPacketY = Double.MAX_VALUE;
        private boolean touchedAbilities;
        private String lastMode = "Abilities";
        private double vulcanTargetY;
        private int vulcanJumpCooldown;
        private int vulcanGlideRequestCooldown;
        private boolean vulcanGlideRequestedThisAir;
        private Boolean meteorFlightActiveCache;
        private long meteorFlightActiveCacheAt;
        private int cachedSettingsRevision = Integer.MIN_VALUE;
        private String cachedMode = "Abilities";
        private String cachedAntiKickMode = "Packet";
        private double cachedSpeed = 0.10;
        private boolean cachedVerticalSpeedMatch;
        private boolean cachedNoSneak;
        private int cachedDelay = 20;
        private int cachedOffTime = 1;
        private long cachedAirTick = Long.MIN_VALUE;
        private boolean cachedOnAir;
        private boolean sendingAntiKickReplacement;
        private boolean vulcanNoElytraDisableQueued;

        FlightModule() {
            super("flight", "Flight", ModuleCategory.MOVEMENT, "Client-side flight.");
            add(new ChoiceSetting("mode", "Mode", "Abilities", "Abilities", "Velocity", "Vulcan").description("Flight method").build());
            add(new DoubleSetting("speed", "Speed", 0.10, 0.0, 2.0, 0.01).visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Your speed when flying.").build());
            add(new BoolSetting("vertical-speed-match", "Vertical Speed Match", false).description("Match vertical speed.").visibleWhen(() -> "Velocity".equals(value("mode"))).build());
            add(new BoolSetting("no-sneak", "No Sneak", false).description("Ignore sneak input.").visibleWhen(() -> "Velocity".equals(value("mode"))).build());
            add(new BoolSetting("anti-break-elytra", "Anti Break Elytra", true).visibleWhen(() -> "Vulcan".equals(value("mode"))).description("Preserve low-durability elytra.").build());
            add(new BoolSetting("anti-hunger", "AntiHunger", false)
                .description("Hide sprint from server.").build());
            add(new ChoiceSetting("anti-kick-mode", "Anti Kick", "Packet", "Normal", "Packet", "None").group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Reduce floating kicks").build());
            add(new IntSetting("delay", "Delay", 20, 1, 200, 1).group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Ticks between anti-kick nudges.").build());
            add(new IntSetting("off-time", "Off Time", 1, 1, 20, 1).group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Ticks spent nudging down.").build());
        }

        @Override
        public void onEnable() {
            updateFlightCache();
            resetFlightState();
            lastMode = cachedMode;
            vulcanNoElytraDisableQueued = false;
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if ("Vulcan".equals(lastMode)) {
                if (!ensureVulcanElytra()) {
                    disableVulcanNoElytra(true);
                    return;
                }
                vulcanTargetY = MC.player.getY();
                boolean airborne = !MC.player.onGround();
                vulcanJumpCooldown = airborne ? 10 : 0;
                vulcanGlideRequestCooldown = 0;
                vulcanGlideRequestedThisAir = MC.player.isFallFlying();
                if (airborne) requestVulcanGlide();
                else vulcanJump();
                return;
            }
            if ("Abilities".equals(lastMode)) abilitiesOn();
        }

        @Override
        public void tick() {
            updateFlightCache();
            if (MC.player == null || shouldYieldToMeteorFlight() || "Vulcan".equals(cachedMode)) return;
        }

        @Override
        public void preMovementTick() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.gameMode == null || MC.player.isSpectator() || shouldYieldToMeteorFlight()) return;
            if (AutismJoinGrace.isMovementPaused()) {

                if (touchedAbilities) abilitiesOff();
                return;
            }
            updateFlightCache();
            String mode = cachedMode;
            syncModeChange(mode);
            if ("Vulcan".equals(mode)) {
                tickVulcan();
                return;
            }

            if (delayLeft > 0) delayLeft--;
            if (offLeft <= 0 && delayLeft <= 0) {
                delayLeft = cachedDelay;
                offLeft = cachedOffTime;
                if (usesPacketAntiKick()) ((AutismLocalPlayerAccessor) MC.player).autism$setPositionReminder(20);
            } else if (delayLeft <= 0) {
                boolean shouldReturn = false;
                if ("Normal".equals(cachedAntiKickMode) && "Abilities".equals(mode)) {
                    abilitiesOff();
                    shouldReturn = true;
                } else if (usesPacketAntiKick() && offLeft == cachedOffTime) {
                    ((AutismLocalPlayerAccessor) MC.player).autism$setPositionReminder(20);
                }
                offLeft--;
                if (shouldReturn) return;
            }

            if ("Velocity".equals(mode)) {

                MC.player.getAbilities().flying = false;
                MC.player.setDeltaMovement(Vec3.ZERO);
                Vec3 velocity = MC.player.getDeltaMovement();
                double vertical = cachedSpeed * (cachedVerticalSpeedMatch ? 10.0 : 5.0);
                if (MC.options.keyJump.isDown()) velocity = velocity.add(0.0, vertical, 0.0);
                if (MC.options.keyShift.isDown()) velocity = velocity.subtract(0.0, vertical, 0.0);
                MC.player.setDeltaMovement(velocity);
                if (cachedNoSneak) MC.player.setOnGround(false);
            } else if ("Abilities".equals(mode)) {

                if (MC.player.isSpectator()) return;
                MC.player.getAbilities().setFlyingSpeed((float) cachedSpeed);
                MC.player.getAbilities().flying = true;
                touchedAbilities = true;
                if (!MC.player.getAbilities().instabuild) {
                    MC.player.getAbilities().mayfly = true;
                }
            }
        }

        @Override
        public void onDisable() {
            if (autismclient.util.multi.MultiPilot.isActive()) {
                resetFlightState();
                touchedAbilities = false;
                return;
            }
            boolean wasVulcan = "Vulcan".equals(lastMode) || "Vulcan".equals(cachedMode);
            if (wasVulcan) {
                stopVulcanGlideState();
            }
            resetFlightState();
            if (MC.player == null || MC.player.isSpectator()) return;
            if (touchedAbilities || "Abilities".equals(lastMode)) abilitiesOff();
        }

        @Override
        public void onGameLeft() {
            resetFlightState();
            sendingAntiKickReplacement = false;
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (autismclient.util.multi.MultiPilot.isActive()) return false;
            if (sendingAntiKickReplacement) return false;
            if (AutismJoinGrace.isMovementPaused()) return false;
            if (MC.player == null || MC.getConnection() == null || shouldYieldToMeteorFlight()) return false;
            updateFlightCache();
            if ("Vulcan".equals(cachedMode)) return false;
            if (!usesPacketAntiKick() || !(packet instanceof ServerboundMovePlayerPacket move)) return false;
            double currentY = move.getY(Double.MAX_VALUE);
            if (currentY != Double.MAX_VALUE) {
                antiKickPacket(move, currentY);
                return false;
            }

            ServerboundMovePlayerPacket fullPacket = move.hasRotation()
                ? new ServerboundMovePlayerPacket.PosRot(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(),
                    move.getYRot(0.0f), move.getXRot(0.0f), move.isOnGround(), MC.player.horizontalCollision
                )
                : new ServerboundMovePlayerPacket.Pos(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(), move.isOnGround(), MC.player.horizontalCollision
                );
            antiKickPacket(fullPacket, MC.player.getY());
            sendingAntiKickReplacement = true;
            try {
                MC.getConnection().send(fullPacket);
            } finally {
                sendingAntiKickReplacement = false;
            }
            return true;
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (autismclient.util.multi.MultiPilot.isActive()) return false;
            if (MC.player == null || shouldYieldToMeteorFlight()) return false;
            updateFlightCache();
            if (!(packet instanceof ClientboundPlayerAbilitiesPacket abilities) || !"Abilities".equals(cachedMode)) return false;
            MC.player.getAbilities().invulnerable = abilities.isInvulnerable();
            MC.player.getAbilities().instabuild = abilities.canInstabuild();
            MC.player.getAbilities().setWalkingSpeed(abilities.getWalkingSpeed());
            return true;
        }

        float getFlyingSpeed() {
            if (AutismJoinGrace.isMovementPaused()) return -1.0f;
            updateFlightCache();
            if (!isEnabled() || shouldYieldToMeteorFlight() || !"Velocity".equals(cachedMode)) return -1.0f;

            return (float) cachedSpeed * (MC.player != null && MC.player.isSprinting() ? 15.0f : 10.0f);
        }

        boolean antiHungerToggle() {
            return bool("anti-hunger");
        }

        boolean noSneak() {
            if (AutismJoinGrace.isMovementPaused()) return false;
            updateFlightCache();
            return isEnabled() && !shouldYieldToMeteorFlight() && "Velocity".equals(cachedMode) && cachedNoSneak;
        }

        private void abilitiesOn() {
            if (MC.player == null || MC.player.isSpectator()) return;
            MC.player.getAbilities().flying = true;
            touchedAbilities = true;
            if (!MC.player.getAbilities().instabuild) MC.player.getAbilities().mayfly = true;
        }

        private void abilitiesOff() {
            if (MC.player == null || MC.player.isSpectator()) return;
            MC.player.getAbilities().setFlyingSpeed(0.05f);
            MC.player.getAbilities().flying = false;
            if (!MC.player.getAbilities().instabuild) {
                MC.player.getAbilities().mayfly = false;
            }
            touchedAbilities = false;
        }

        private void antiKickPacket(ServerboundMovePlayerPacket packet, double currentY) {
            if (delayLeft <= 0 && lastPacketY != Double.MAX_VALUE && shouldFlyDown(currentY, lastPacketY) && isOnAirCached()) {
                ((AutismMovePlayerPacketAccessor) packet).autism$setY(lastPacketY - 0.03130D);
            } else {
                lastPacketY = currentY;
            }
        }

        private boolean shouldFlyDown(double currentY, double lastY) {
            return currentY >= lastY || lastY - currentY < 0.03130D;
        }

        private boolean isOnAirCached() {
            long tick = MC.level == null ? Long.MIN_VALUE : MC.level.getGameTime();
            if (cachedAirTick == tick) return cachedOnAir;
            cachedAirTick = tick;
            cachedOnAir = isOnAirNow();
            return cachedOnAir;
        }

        private boolean isOnAirNow() {

            if (MC.level == null || MC.player == null) return false;
            net.minecraft.world.phys.AABB box = MC.player.getBoundingBox()
                .inflate(0.0625)
                .expandTowards(0.0, -0.55, 0.0);
            return MC.level.getBlockStates(box)
                .allMatch(state -> state.isAir());
        }

        private void syncModeChange(String mode) {
            if (mode.equals(lastMode)) return;
            if ("Abilities".equals(lastMode)) abilitiesOff();
            lastMode = mode;
            if ("Abilities".equals(mode)) abilitiesOn();
            if ("Vulcan".equals(mode)) resetFlightState();
        }

        private boolean usesPacketAntiKick() {
            return "Packet".equals(cachedAntiKickMode);
        }

        private void tickVulcan() {
            if (!ensureVulcanElytra()) {
                disableVulcanNoElytra(false);
                return;
            }
            if (vulcanJumpCooldown > 0) vulcanJumpCooldown--;
            if (vulcanGlideRequestCooldown > 0) vulcanGlideRequestCooldown--;
            if (MC.player.onGround()) vulcanGlideRequestedThisAir = false;
            requestVulcanGlide();
            boolean jump = MC.options != null && MC.options.keyJump.isDown();
            boolean shift = MC.options != null && MC.options.keyShift.isDown();
            boolean gliding = MC.player.isFallFlying();
            if (jump) {
                vulcanTargetY = Math.max(vulcanTargetY + (gliding ? 0.04 : 0.12), MC.player.getY());
                if (!gliding || MC.player.getY() < vulcanTargetY - 0.08 || MC.player.getDeltaMovement().y < -0.055) {
                    vulcanJump();
                }
            } else if (shift) {
                vulcanTargetY = MC.player.getY() - 0.35;
            } else if (MC.player.getY() < vulcanTargetY - 0.22 && MC.player.getDeltaMovement().y < -0.02) {
                vulcanJump();
            }
        }

        private void disableVulcanNoElytra(boolean duringEnable) {
            if (vulcanNoElytraDisableQueued) return;
            vulcanNoElytraDisableQueued = true;
            String message = "Flight disabled: elytra not found.";
            if (duringEnable) disableSilentlyWithToggleMessage(message);
            else disableWithToggleMessage(message);
            vulcanNoElytraDisableQueued = false;
        }

        private void vulcanJump() {
            if (vulcanJumpCooldown > 0 || MC.player == null) return;
            if (MC.player.onGround()) {
                if (MC.player.isFallFlying()) MC.player.stopFallFlying();
                MC.player.jumpFromGround();
                vulcanJumpCooldown = 3;
                return;
            }
            MC.player.jumpFromGround();
            vulcanJumpCooldown = MC.player.isFallFlying() ? 12 : 3;
        }

        private void requestVulcanGlide() {
            if (vulcanGlideRequestCooldown > 0 || MC.player == null || MC.getConnection() == null) return;
            if (MC.player.onGround() || MC.player.isFallFlying()) return;
            if (vulcanGlideRequestedThisAir) return;
            MC.player.tryToStartFallFlying();
            MC.getConnection().send(new ServerboundPlayerCommandPacket(MC.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            vulcanGlideRequestedThisAir = true;
            vulcanGlideRequestCooldown = 20;
        }

        private void stopVulcanGlideState() {
            if (MC.player == null) return;
            if (MC.player.isFallFlying()) MC.player.stopFallFlying();
            vulcanGlideRequestCooldown = 0;
            vulcanJumpCooldown = 0;
            vulcanGlideRequestedThisAir = false;
        }

        private boolean ensureVulcanElytra() {
            if (MC.player == null) return false;
            ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
            if (isVulcanUsableElytra(chest)) {
                if (!bool("anti-break-elytra") || isVulcanSafeElytra(chest)) return true;
                int replacement = findVulcanElytraSlot(true);
                if (replacement >= 0 && AutismInventoryHelper.swapInventorySlots(MC, replacement, 38)) return true;
                int emptySlot = findEmptyInventorySlot();
                if (emptySlot >= 0) AutismInventoryHelper.swapInventorySlots(MC, 38, emptySlot);
                return false;
            }

            int elytraSlot = findVulcanElytraSlot(bool("anti-break-elytra"));
            if (elytraSlot < 0) return false;
            return AutismInventoryHelper.swapInventorySlots(MC, elytraSlot, 38)
                && isVulcanUsableElytra(MC.player.getItemBySlot(EquipmentSlot.CHEST));
        }

        private int findVulcanElytraSlot(boolean safeOnly) {
            if (MC.player == null) return -1;
            int bestSlot = -1;
            int bestRemaining = -1;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = MC.player.getInventory().getItem(slot);
                if (!isVulcanUsableElytra(stack)) continue;
                if (safeOnly && !isVulcanSafeElytra(stack)) continue;
                int remaining = vulcanElytraRemainingDurability(stack);
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        }

        private int findEmptyInventorySlot() {
            if (MC.player == null) return -1;
            for (int slot = 0; slot < 36; slot++) {
                if (MC.player.getInventory().getItem(slot).isEmpty()) return slot;
            }
            return -1;
        }

        private boolean isVulcanUsableElytra(ItemStack stack) {
            return stack != null && stack.is(Items.ELYTRA) && !stack.isBroken() && vulcanElytraRemainingDurability(stack) > 0;
        }

        private boolean isVulcanSafeElytra(ItemStack stack) {
            return isVulcanUsableElytra(stack) && vulcanElytraRemainingDurability(stack) > vulcanElytraSafeDurability(stack);
        }

        private int vulcanElytraRemainingDurability(ItemStack stack) {
            if (stack == null || !stack.isDamageableItem()) return Integer.MAX_VALUE;
            return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }

        private int vulcanElytraSafeDurability(ItemStack stack) {
            int max = stack == null || !stack.isDamageableItem() ? 0 : stack.getMaxDamage();
            return Math.max(40, (int) Math.ceil(max * 0.10));
        }

        private void resetFlightState() {
            updateFlightCache();
            delayLeft = cachedDelay;
            offLeft = cachedOffTime;
            lastPacketY = Double.MAX_VALUE;
            cachedAirTick = Long.MIN_VALUE;
            cachedOnAir = false;
            vulcanGlideRequestCooldown = 0;
            vulcanGlideRequestedThisAir = false;
        }

        private void updateFlightCache() {
            int revision = ModuleRegistry.revision();
            if (cachedSettingsRevision == revision) return;
            cachedSettingsRevision = revision;
            cachedMode = choice("mode");
            cachedAntiKickMode = choice("anti-kick-mode");
            cachedSpeed = decimal("speed");
            cachedVerticalSpeedMatch = bool("vertical-speed-match");
            cachedNoSneak = bool("no-sneak");
            cachedDelay = Math.max(1, integer("delay"));
            cachedOffTime = Math.max(1, integer("off-time"));
        }

        private boolean shouldYieldToMeteorFlight() {
            long now = System.currentTimeMillis();
            if (meteorFlightActiveCache != null && now - meteorFlightActiveCacheAt < 250L) return meteorFlightActiveCache;
            meteorFlightActiveCacheAt = now;
            meteorFlightActiveCache = queryMeteorFlightActive();
            return meteorFlightActiveCache;
        }

        private boolean queryMeteorFlightActive() {
            try {
                Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, FlightModule.class.getClassLoader());
                Class<?> flightClass = Class.forName("meteordevelopment.meteorclient.systems.modules.movement.Flight", false, FlightModule.class.getClassLoader());
                Object modules = modulesClass.getMethod("get").invoke(null);
                Object flight = modulesClass.getMethod("get", Class.class).invoke(modules, flightClass);
                return flight != null && Boolean.TRUE.equals(flight.getClass().getMethod("isActive").invoke(flight));
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    static final class SprintModule extends Module {
        SprintModule() {
            super("sprint", "Sprint", ModuleCategory.MOVEMENT, "Sprints automatically.");
            add(new BoolSetting("ignore-blindness", "Blindness", false)
                .description("Sprint while blind").group("Ignore").build());
            add(new BoolSetting("ignore-collision", "Collision", false)
                .description("Sprint against walls").group("Ignore").build());
        }

        boolean sprintDecision(boolean original, boolean movementTick) {
            boolean sprint = original;
            if (isMoving()) sprint = true;
            if (movementTick && shouldPreventSprint()) sprint = false;
            return sprint;
        }

        private boolean shouldPreventSprint() {
            if (((autismclient.mixin.accessor.AutismLocalPlayerAccessor) MC.player).autism$isSlowDueToUsingItem()) return true;
            if (MC.player.isShiftKeyDown()) return true;

            autismclient.util.AutismRotationUtil.Rotation current = autismclient.util.AutismSilentAim.packetRotation();
            if (current == null) return false;
            float deltaYawRad = (MC.player.getYRot() - current.yaw()) * net.minecraft.util.Mth.DEG_TO_RAD;
            net.minecraft.world.phys.Vec2 move = MC.player.input.getMoveVector();
            boolean hasForwardMovement =
                move.y * net.minecraft.util.Mth.cos(deltaYawRad) + move.x * net.minecraft.util.Mth.sin(deltaYawRad) > 1.0E-5F;

            return !hasForwardMovement;
        }

        boolean shouldPreventSprintPublic() {
            return shouldPreventSprint();
        }

        boolean omnidirectional() {
            return false;
        }

        boolean jumpUsesMovementYaw() {
            return false;
        }

        boolean ignoreCollision() {
            return bool("ignore-collision");
        }

        boolean ignoreBlindness() {
            return bool("ignore-blindness");
        }

        private boolean isMoving() {
            return MC.player.input.getMoveVector().lengthSquared() > 0.0f;
        }
    }

    static final class ParkourModule extends Module {
        private boolean releaseJumpNextTick;

        ParkourModule() {
            super("parkour", "Parkour", ModuleCategory.MOVEMENT, "Jumps at block edges.");
        }

        @Override
        public void tick() {
            if (MC.player == null || MC.level == null) return;
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (releaseJumpNextTick) {
                autismclient.util.AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(false);
                releaseJumpNextTick = false;
                return;
            }

            if (!MC.player.onGround() || MC.player.isShiftKeyDown()) return;
            if (MC.options.keyShift.isDown() || MC.options.keyJump.isDown()) return;
            if (MC.player.input.getMoveVector().lengthSquared() <= 0.0F) return;
            if (onGroundNextTick()) return;

            autismclient.util.AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(true);
            releaseJumpNextTick = true;
        }

        private boolean onGroundNextTick() {
            net.minecraft.world.phys.Vec3 d = MC.player.getDeltaMovement();
            net.minecraft.world.phys.AABB next = MC.player.getBoundingBox()
                .move(d.x, d.y - 0.08, d.z)
                .expandTowards(0.0, -0.02, 0.0);
            return !MC.level.noCollision(MC.player, next);
        }

        @Override
        public void onDisable() {
            if (releaseJumpNextTick) {
                releaseJumpNextTick = false;
                if (MC != null && MC.options != null) {
                    autismclient.util.AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(false);
                }
            }
        }
    }

    static final class AutoToolModule extends Module {
        private final Random delayRandom = new Random();
        private BlockPos currentTargetPos;
        private boolean switchedForTarget;
        private long mineStartMs;
        private long switchDueMs;
        private int previousSlot = -1;

        private int switchedToSlot = -1;
        private long lastMineMs;
        private long restoreDueMs = -1L;

        AutoToolModule() {
            super("auto-tool", "AutoTool", ModuleCategory.PLAYER, "Switches to the best tool to mine a block.");
            add(new IntSetting("switch-delay-ms", "Switch Delay", 12, 0, 1000, 1)
                .description("Delay before switching.").build());
            add(new BoolSetting("consider-inventory", "Consider Inventory", false));
            add(new BoolSetting("ignore-durability", "Ignore Durability", true));
            add(new BoolSetting("prefer-silk-touch", "Prefer Silk Touch", false));
            add(new BoolSetting("require-sneaking", "Require Sneaking", false));
            add(new BoolSetting("switch-back", "Switch Back", false));
            add(new IntSetting("switch-back-delay-ms", "Restore Delay", 100, 0, 1000, 25)
                .description("Delay before restoring.").build());
        }

        @Override
        public void onDisable() {

            previousSlot = -1;
            switchedToSlot = -1;
            currentTargetPos = null;
            switchedForTarget = false;
            switchDueMs = 0L;
            restoreDueMs = -1L;
        }

        @Override
        public void tick() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.level == null || MC.gameMode == null) return;

            if (AutoTotemModule.operationActive()) return;
            if (!(MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor)) return;

            boolean destroying = accessor.autism$isDestroying();
            if (destroying) {
                restoreDueMs = -1L;
                if (bool("require-sneaking") && !MC.player.isShiftKeyDown()) return;
                BlockPos pos = accessor.autism$getDestroyBlockPos();
                if (pos == null) return;
                BlockState state = MC.level.getBlockState(pos);
                if (state.isAir()) return;

                lastMineMs = nowMs();
                if (!pos.equals(currentTargetPos)) {

                    currentTargetPos = pos;
                    switchedForTarget = false;
                    mineStartMs = nowMs();
                    switchDueMs = mineStartMs + sampledDelayMs(integer("switch-delay-ms"));
                    if (previousSlot < 0) previousSlot = MC.player.getInventory().getSelectedSlot();
                }
                if (!switchedForTarget && nowMs() >= switchDueMs) {
                    int slot = resolveToolHotbarSlot(state);
                    if (slot >= 0) selectHotbarWithRealKey(slot, true);
                }
            } else {
                if (bool("switch-back")) {

                    if (switchedToSlot >= 0
                        && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {
                        previousSlot = -1;
                        switchedToSlot = -1;
                        restoreDueMs = -1L;
                    }
                    if (previousSlot >= 0 && restoreDueMs < 0L) {
                        restoreDueMs = lastMineMs + sampledDelayMs(integer("switch-back-delay-ms"));
                    }
                    if (previousSlot >= 0 && nowMs() >= restoreDueMs) {
                        selectHotbarWithRealKey(previousSlot, false);
                        currentTargetPos = null;
                        switchedForTarget = false;
                        switchDueMs = 0L;
                        restoreDueMs = -1L;
                        previousSlot = -1;
                        switchedToSlot = -1;
                    }
                } else {
                    previousSlot = -1;
                    switchedToSlot = -1;
                    currentTargetPos = null;
                    switchedForTarget = false;
                    switchDueMs = 0L;
                    restoreDueMs = -1L;
                }
            }
        }

        private void selectHotbarWithRealKey(int slot, boolean forward) {
            if (MC.player == null) return;
            int clamped = Math.max(0, Math.min(8, slot));
            if (MC.player.getInventory().getSelectedSlot() == clamped) {
                switchedForTarget = true;
                if (forward) switchedToSlot = clamped;
                return;
            }
            if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
            try {
                AutismInputClicker.queueHotbarSlot(clamped);
                if (forward) switchedToSlot = clamped;
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
        }

        private int resolveToolHotbarSlot(BlockState state) {
            if (MC.player == null || state == null) return -1;

            int limit = bool("consider-inventory") ? 36 : 9;
            int best = -1;
            if (bool("prefer-silk-touch")) best = AutismAutoTool.bestToolSlot(MC, state, limit, bool("ignore-durability"), true);
            if (best < 0) best = AutismAutoTool.bestToolSlot(MC, state, limit, bool("ignore-durability"), false);
            if (best < 0) return -1;

            if (AutismHandArbiter.slotReserved(best, id())) return -1;

            int selectedSlot = MC.player.getInventory().getSelectedSlot();
            if (best < 9) {
                if (!isToolBetterThanSelectedSlot(state, best, selectedSlot)) return selectedSlot;
                return best;
            }

            int selected = MC.player.getInventory().getSelectedSlot();
            if (!bool("consider-inventory")) return -1;

            if (!AutismHandArbiter.beginHandPacketGroup(id())) return -1;
            try {
                if (!AutismInventoryHelper.swapInventorySlots(MC, best, selected)) return -1;
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
            return selected;
        }

        private boolean isToolBetterThanSelectedSlot(BlockState state, int candidateSlot, int selectedSlot) {
            if (MC.player == null) return false;
            if (candidateSlot == selectedSlot) return true;
            ItemStack candidate = MC.player.getInventory().getItem(candidateSlot);
            ItemStack current = MC.player.getInventory().getItem(Math.max(0, Math.min(8, selectedSlot)));
            float candidateSpeed = AutismAutoTool.destroySpeed(MC, candidate, state);
            float currentSpeed = AutismAutoTool.destroySpeed(MC, current, state);
            if (bool("prefer-silk-touch") && candidateSpeed > 1f && AutismAutoTool.hasSilkTouch(MC, candidate)) {
                return !(currentSpeed > 1f && AutismAutoTool.hasSilkTouch(MC, current));
            }
            return candidateSpeed > currentSpeed;
        }

        private int sampledDelayMs(int baseMs) {
            if (baseMs <= 0) return 0;
            int spread = Math.max(15, Math.min(180, Math.round(baseMs * 0.55f)));
            int extra = delayRandom.nextInt(spread + 1);
            if (delayRandom.nextInt(12) == 0) extra += 15 + delayRandom.nextInt(Math.max(16, spread / 2 + 1));
            return Math.min(1000, baseMs + extra);
        }

        private static long nowMs() {
            return System.currentTimeMillis();
        }
    }

    static final class SpeedModule extends Module {
        private int strafeStage;
        private double strafeSpeed;
        private double lastDistance;
        private long speedLimitTimer;
        private String lastSpeedMode = "Vanilla";
        private Boolean meteorSpeedActiveCache;
        private long meteorSpeedActiveCacheAt;

        SpeedModule() {
            super("speed", "Speed", ModuleCategory.MOVEMENT, "Boosts movement speed.");
            add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Strafe").description("Speed method").build());
            add(new DoubleSetting("vanilla-speed", "Vanilla Speed", 5.6, 0.0, 20.0, 0.1).visibleWhen(() -> "Vanilla".equals(value("mode"))).description("Blocks per second").build());
            add(new DoubleSetting("strafe-speed", "Strafe Speed", 1.6, 0.0, 3.0, 0.05).visibleWhen(() -> "Strafe".equals(value("mode"))).description("Strafe speed multiplier.").build());
            add(new BoolSetting("speed-limit", "Speed Limit", false).visibleWhen(() -> "Strafe".equals(value("mode"))).description("Limit max speed.").build());
            add(new DoubleSetting("timer", "Timer", 1.0, 0.01, 10.0, 0.05).description("Client timer speed."));
            add(new BoolSetting("in-liquids", "In Liquids", false));
            add(new BoolSetting("when-sneaking", "When Sneaking", false));
            add(new BoolSetting("only-on-ground", "Only Ground", false).visibleWhen(() -> "Vanilla".equals(value("mode"))).build());
        }

        @Override
        public void onEnable() {
            lastSpeedMode = choice("mode");
            resetStrafe();
        }

        @Override
        public void onDisable() {
            resetStrafe();
        }

        @Override
        public void preMovementTick() {
            if (MC.player == null || shouldYieldToMeteorSpeed() || stopSpeed()) return;
            syncSpeedMode();
            if (isInLiquid()) {
                resetStrafe();
                return;
            }
            lastDistance = Math.sqrt((MC.player.getX() - MC.player.xo) * (MC.player.getX() - MC.player.xo) + (MC.player.getZ() - MC.player.zo) * (MC.player.getZ() - MC.player.zo));
        }

        @Override
        public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
            if (type != MoverType.SELF || MC.player == null || MC.options == null || movement == null) return movement;
            if (shouldYieldToMeteorSpeed() || stopSpeed()) return movement;
            syncSpeedMode();
            if (isInLiquid()) return liquidMovement(movement);
            return "Strafe".equals(choice("mode")) ? strafeMovement(movement) : vanillaMovement(movement);
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (packet instanceof ClientboundPlayerPositionPacket) resetStrafe();
            return false;
        }

        @Override
        public boolean shouldApplySpeedTimer() {
            return !shouldYieldToMeteorSpeed() && !stopSpeed() && isMoving();
        }

        @Override
        public float speedTimerMultiplier() {
            if (!shouldApplySpeedTimer()) return 1.0f;
            try {
                return Math.max(0.01f, Math.min(10.0f, (float) decimal("timer")));
            } catch (Exception ignored) {
                return 1.0f;
            }
        }

        private Vec3 vanillaMovement(Vec3 movement) {
            Vec3 horizontal = horizontalVelocity(decimal("vanilla-speed"));
            double x = horizontal.x;
            double z = horizontal.z;
            if (MC.player.hasEffect(MobEffects.SPEED)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SPEED);
                if (effect != null) {
                    double multiplier = (effect.getAmplifier() + 1) * 0.205;
                    x += x * multiplier;
                    z += z * multiplier;
                }
            }
            return new Vec3(x, movement.y, z);
        }

        private Vec3 liquidMovement(Vec3 movement) {
            if (!"Strafe".equals(choice("mode"))) return vanillaMovement(movement);
            double liquidSpeed = defaultSpeed() * Math.max(0.0, decimal("strafe-speed"));
            Vec3 horizontal = transformStrafe(liquidSpeed);
            return new Vec3(horizontal.x, movement.y, horizontal.z);
        }

        Vec3 afterLiquidTravel(Vec3 movement) {
            if (movement == null || MC.player == null || !bool("in-liquids") || !isInLiquid()) return movement;
            if (shouldYieldToMeteorSpeed() || stopSpeed()) return movement;
            syncSpeedMode();
            return liquidMovement(movement);
        }

        private Vec3 strafeMovement(Vec3 movement) {

            if (strafeStage == 0) {
                if (isMoving()) {
                    strafeStage = 1;
                    strafeSpeed = 1.1799999475479126 * defaultSpeed() - 0.01;

                }
            }
            if (strafeStage == 1) {
                if (isMoving() && MC.player.onGround()) {
                    movement = new Vec3(movement.x, hop(0.40123128), movement.z);
                    strafeSpeed *= decimal("strafe-speed");
                    strafeStage = 2;
                }
            } else if (strafeStage == 2) {
                strafeSpeed = lastDistance - 0.76 * (lastDistance - defaultSpeed());
                strafeStage = 3;
            } else if (strafeStage == 3) {
                if (!hasVerticalMoveSpace() || (MC.player.verticalCollision && strafeStage > 0)) strafeStage = 0;
                strafeSpeed = lastDistance - lastDistance / 159.0;
            } else if (strafeStage > 3 || strafeStage < 0) {
                strafeStage = 0;
            }

            strafeSpeed = Math.max(strafeSpeed, defaultSpeed());
            if (bool("speed-limit")) {
                long now = System.currentTimeMillis();
                if (now - speedLimitTimer > 2500L) speedLimitTimer = now;
                strafeSpeed = Math.min(strafeSpeed, now - speedLimitTimer > 1250L ? 0.44 : 0.43);
            }

            Vec3 horizontal = transformStrafe(strafeSpeed);
            return new Vec3(horizontal.x, movement.y, horizontal.z);
        }

        private Vec3 horizontalVelocity(double blocksPerSecond) {
            if (MC.player == null || MC.player.input == null) return Vec3.ZERO;
            double speed = blocksPerSecond / 20.0;
            Vec3 forward = Vec3.directionFromRotation(0.0f, MC.player.getYRot());
            Vec3 right = Vec3.directionFromRotation(0.0f, MC.player.getYRot() + 90.0f);
            double x = 0.0;
            double z = 0.0;
            boolean hasForward = false;
            if (MC.options.keyUp.isDown()) {
                x += forward.x * speed;
                z += forward.z * speed;
                hasForward = true;
            }
            if (MC.options.keyDown.isDown()) {
                x -= forward.x * speed;
                z -= forward.z * speed;
                hasForward = true;
            }
            boolean hasSide = false;
            if (MC.options.keyRight.isDown()) {
                x += right.x * speed;
                z += right.z * speed;
                hasSide = true;
            }
            if (MC.options.keyLeft.isDown()) {
                x -= right.x * speed;
                z -= right.z * speed;
                hasSide = true;
            }
            if (hasForward && hasSide) {
                double diagonal = 1.0 / Math.sqrt(2.0);
                x *= diagonal;
                z *= diagonal;
            }
            return new Vec3(x, 0.0, z);
        }

        private Vec3 transformStrafe(double speed) {
            if (MC.player == null || MC.player.input == null) return Vec3.ZERO;
            Vec2 input = MC.player.input.getMoveVector();
            float forward = Math.signum(input.y);
            float side = Math.signum(input.x);
            if (forward == 0.0f && side == 0.0f) return Vec3.ZERO;

            float yaw = MC.player.getYRot(movementPartialTick());
            float strafe = 90.0f * side;
            if (forward != 0.0f) strafe *= forward * 0.5f;
            yaw -= strafe;
            if (forward < 0.0f) yaw -= 180.0f;
            double radians = Math.toRadians(yaw);
            return new Vec3(-Math.sin(radians) * speed, 0.0, Math.cos(radians) * speed);
        }

        private float movementPartialTick() {
            try {
                return MC.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            } catch (Exception ignored) {
                return 1.0f;
            }
        }

        private double defaultSpeed() {
            double speed = 0.2873;
            if (MC.player.hasEffect(MobEffects.SPEED)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SPEED);
                if (effect != null) speed *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            }
            if (MC.player.hasEffect(MobEffects.SLOWNESS)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SLOWNESS);
                if (effect != null) speed /= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            }
            return speed;
        }

        private double hop(double base) {
            MobEffectInstance effect = MC.player.getEffect(MobEffects.JUMP_BOOST);
            return effect == null ? base : base + (effect.getAmplifier() + 1) * 0.1f;
        }

        private boolean hasVerticalMoveSpace() {
            return MC.level != null && MC.level.noCollision(MC.player.getBoundingBox().move(0.0, MC.player.getDeltaMovement().y, 0.0));
        }

        private boolean stopSpeed() {

            if (MC.player == null) return true;
            if (MC.player.isFallFlying() || MC.player.onClimbable() || MC.player.getVehicle() != null) return true;
            if (!bool("when-sneaking") && MC.player.isShiftKeyDown()) return true;
            boolean inLiquid = isInLiquid();
            boolean liquidSpeedAllowed = inLiquid && bool("in-liquids");
            if (bool("only-on-ground") && !MC.player.onGround() && "Vanilla".equals(choice("mode")) && !liquidSpeedAllowed) return true;
            return inLiquid && !liquidSpeedAllowed;
        }

        private boolean isMoving() {
            if (MC.player == null) return false;
            if (MC.player.input != null) {
                Vec2 input = MC.player.input.getMoveVector();
                if (input.x != 0.0f || input.y != 0.0f) return true;
            }
            return MC.player.xxa != 0.0f || MC.player.zza != 0.0f;
        }

        private boolean isInLiquid() {
            return MC.player != null && (MC.player.isInWater() || MC.player.isInLava());
        }

        private void syncSpeedMode() {
            String mode = choice("mode");
            if (mode.equals(lastSpeedMode)) return;
            lastSpeedMode = mode;
            resetStrafe();
        }

        private boolean shouldYieldToMeteorSpeed() {
            long now = System.currentTimeMillis();
            if (meteorSpeedActiveCache != null && now - meteorSpeedActiveCacheAt < 250L) return meteorSpeedActiveCache;
            meteorSpeedActiveCacheAt = now;
            meteorSpeedActiveCache = queryMeteorSpeedActive();
            return meteorSpeedActiveCache;
        }

        private boolean queryMeteorSpeedActive() {
            try {
                Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, SpeedModule.class.getClassLoader());
                Class<?> speedClass = Class.forName("meteordevelopment.meteorclient.systems.modules.movement.speed.Speed", false, SpeedModule.class.getClassLoader());
                Object modules = modulesClass.getMethod("get").invoke(null);
                Object speed = modulesClass.getMethod("get", Class.class).invoke(modules, speedClass);
                return speed != null && Boolean.TRUE.equals(speed.getClass().getMethod("isActive").invoke(speed));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void resetStrafe() {

            strafeStage = 0;
            strafeSpeed = 0.2873;
            lastDistance = 0.0;
        }

    }

    static final class SneakModule extends Module {
        private boolean sentPacketSneak;

        SneakModule() {
            super("sneak", "Sneak", ModuleCategory.MOVEMENT, "Keeps sneak held for you.");
            add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Packet").description("Which method to sneak.").build());
        }

        boolean holdsSneakDown() {
            return isEnabled()
                && !autismclient.util.multi.MultiPilot.isActive()
                && !autismclient.util.multi.PacketTeleportController.ownsMainMovement()
                && !"Packet".equals(choice("mode"))
                && MC != null && MC.options != null && MC.player != null
                && !MC.player.getAbilities().flying;
        }

        @Override
        public void tick() {
            holdSneak();
        }

        @Override
        public void preMovementTick() {

            if (!"Packet".equals(choice("mode"))) holdSneak();
        }

        private void holdSneak() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;

            if (autismclient.util.multi.PacketTeleportController.ownsMainMovement()) return;
            if (MC.options == null || MC.player == null || MC.player.getAbilities().flying) return;
            if ("Packet".equals(choice("mode"))) {
                sendInputSneak(true);
                sentPacketSneak = true;
            } else {
                MC.options.keyShift.setDown(true);
            }
        }

        @Override
        public void onDisable() {
            if (autismclient.util.multi.MultiPilot.isActive()) {
                sentPacketSneak = false;
                return;
            }
            if (sentPacketSneak) {
                sendInputSneak(false);
                sentPacketSneak = false;
            }
            if (MC.options != null) MC.options.keyShift.setDown(false);
        }

        private void sendInputSneak(boolean sneak) {
            if (MC.options == null || MC.getConnection() == null) return;
            Input input = new Input(
                MC.options.keyUp.isDown(),
                MC.options.keyDown.isDown(),
                MC.options.keyLeft.isDown(),
                MC.options.keyRight.isDown(),
                MC.options.keyJump.isDown(),
                sneak,
                MC.options.keySprint.isDown()
            );
            MC.getConnection().send(new ServerboundPlayerInputPacket(input));
        }
    }

    static final class FakeCoordsModule extends Module {
        FakeCoordsModule() {
            super("fake-coords", "FakeCoords", ModuleCategory.RENDER,
                "Spoofs displayed coordinates only.");
            add(new ChoiceSetting("mode", "Mode", "Offset", "Offset", "Scaled", "Rotated", "Scrambled", "Frozen", "Custom")
                .description("Choose spoofing mode.").build());
            add(new BoolSetting("fake-y", "Fake Y", false)
                .description("Spoof vertical coordinate.").build());
            add(new DoubleSetting("custom-x", "Custom X", 0, -30_000_000, 30_000_000, 1)
                .description("Set displayed X.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new DoubleSetting("custom-y", "Custom Y", 64, -30_000_000, 30_000_000, 1)
                .description("Set displayed Y.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new DoubleSetting("custom-z", "Custom Z", 0, -30_000_000, 30_000_000, 1)
                .description("Set displayed Z.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new ActionSetting("anchor", "Anchor", this::captureCurrentPosition)
                .buttonLabel("Capture Position")
                .description("Use current position.")
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
        }

        @Override public boolean showInArrayList() { return false; }

        @Override
        public void onEnable() {
            AutismFakeCoords.Mode m = mode();
            AutismFakeCoords.enable(m, bool("fake-y"));
            if (m == AutismFakeCoords.Mode.CUSTOM) pushCustom();
        }

        @Override
        public void onDisable() {
            AutismFakeCoords.disable();
        }

        @Override
        public void tick() {
            AutismFakeCoords.Mode m = mode();
            AutismFakeCoords.setMode(m);
            AutismFakeCoords.setFakeY(bool("fake-y"));
            if (m == AutismFakeCoords.Mode.CUSTOM) pushCustom();
        }

        private void pushCustom() {
            AutismFakeCoords.setCustom(decimal("custom-x"), decimal("custom-y"), decimal("custom-z"));
        }

        private void captureCurrentPosition() {
            if (MC.player == null) return;
            setValue("custom-x", Double.toString(MC.player.getX()));
            setValue("custom-y", Double.toString(MC.player.getY()));
            setValue("custom-z", Double.toString(MC.player.getZ()));
            pushCustom();
        }

        private AutismFakeCoords.Mode mode() {
            return switch (choice("mode")) {
                case "Scaled" -> AutismFakeCoords.Mode.SCALED;
                case "Rotated" -> AutismFakeCoords.Mode.ROTATED;
                case "Scrambled" -> AutismFakeCoords.Mode.SCRAMBLED;
                case "Frozen" -> AutismFakeCoords.Mode.FROZEN;
                case "Custom" -> AutismFakeCoords.Mode.CUSTOM;
                default -> AutismFakeCoords.Mode.OFFSET;
            };
        }
    }

    static final class NoFallModule extends Module {
        private boolean placedFluid;
        private BlockPos placedTarget;
        private int placedTimer;

        NoFallModule() {
            super("no-fall", "NoFall", ModuleCategory.MOVEMENT, "Reduces fall damage.");
            add(new ChoiceSetting("mode", "Mode", "Packet", "Packet", "AirPlace", "Place").description("Fall protection method.").build());
            add(new ChoiceSetting("placed-item", "Placed Item", "Bucket", "Bucket", "PowderSnow", "HayBale", "Cobweb", "SlimeBlock").visibleWhen(() -> "Place".equals(value("mode")) || "AirPlace".equals(value("mode"))).build());
            add(new ChoiceSetting("air-place-mode", "Air Place Mode", "BeforeDeath", "BeforeDamage", "BeforeDeath").visibleWhen(() -> "AirPlace".equals(value("mode"))).build());
            add(new BoolSetting("anchor", "Anchor", true).visibleWhen(() -> !"Packet".equals(value("mode"))).description("Slow before placing.").build());
            add(new BoolSetting("anti-bounce", "Anti Bounce", true).description("Stop bounce effects."));
            add(new BoolSetting("pause-on-mace", "Pause On Mace", true).description("Pause holding mace"));

            add(new BoolSetting("client-rotate", "Client Rotation", false).visibleWhen(() -> !"Packet".equals(value("mode"))).description("Also rotate camera client-side.").build());
        }

        @Override
        public void onEnable() {
            placedFluid = false;
            placedTarget = null;
            placedTimer = 0;
        }

        @Override
        public void onDisable() {
            placedFluid = false;
            placedTarget = null;
            placedTimer = 0;
        }

        @Override
        public void tick() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (AutismJoinGrace.isMovementPaused()) return;
            if (MC.player == null || MC.gameMode == null || "Packet".equals(choice("mode"))) return;

            if (AutismKillAuraRotation.currentOwner() != null && AutismKillAuraRotation.hasCurrentRotation()) return;
            cleanupPlacedFluid();
            if (!shouldPlaceModeAct()) return;
            if (bool("anchor")) centerPlayer();
            if ("AirPlace".equals(choice("mode"))) tryAirPlaceBelow();
            else tryPlaceBelow();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (autismclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Packet".equals(choice("mode")) || !(packet instanceof ServerboundMovePlayerPacket move) || !shouldPacketSpoof()) return false;
            if (move.isOnGround()) return false;
            ((AutismMovePlayerPacketAccessor) move).autism$setOnGround(true);
            return false;
        }

        private boolean shouldPacketSpoof() {
            if (AutismJoinGrace.isMovementPaused()) return false;
            if (MC.player == null || MC.player.getAbilities().instabuild) return false;
            if (bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) return false;
            Module flight = ModuleRegistry.get("flight");
            if (flight != null && flight.isEnabled()) return true;
            if (MC.player.isFallFlying()) return false;
            return MC.player.getDeltaMovement().y <= -0.5;
        }

        private boolean shouldPlaceModeAct() {

            if (MC.player == null || MC.player.getAbilities().instabuild || MC.player.isFallFlying() || MC.player.isInWater() || MC.player.isInLava()) return false;
            if (bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) return false;
            double fall = MC.player.fallDistance;
            if ("AirPlace".equals(choice("mode"))) {
                if ("BeforeDeath".equals(choice("air-place-mode"))) {

                    return fall > Math.max(2.0, MC.player.getHealth() + MC.player.getAbsorptionAmount());
                }
                return fall > 2.0;
            }
            return fall > 3.0 && !isAboveWater();
        }

        private boolean tryPlaceBelow() {

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return false;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int slot = findHotbarItem();
            if (slot < 0) return false;
            BlockHitResult ray = MC.level.clip(new ClipContext(
                MC.player.position(),
                MC.player.position().subtract(0, 5, 0),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                MC.player
            ));
            if (ray == null || ray.getType() != HitResult.Type.BLOCK) return false;
            BlockPos target = ray.getBlockPos().above();

            if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
            try {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                try {
                    Item item = placeItem();
                    if (item == Items.WATER_BUCKET || item == Items.POWDER_SNOW_BUCKET) {

                        Vec3 hitVec = Vec3.atCenterOf(target);
                        if (ModuleRegistry.shouldCancelUseExcept(ray, InteractionHand.MAIN_HAND, id())) return false;

                        if (!AutismPlacementTick.claim(id())) return false;
                        rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                            MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
                        });
                        placedFluid = true;
                        placedTarget = target;
                        placedTimer = 0;
                    } else {
                        BlockPos below = target.below();
                        Vec3 hitVec = Vec3.atCenterOf(below).add(0, 0.5, 0);
                        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, below, false);
                        if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return false;
                        if (!AutismPlacementTick.claim(id())) return false;
                        rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
                        });
                    }
                    return true;
                } finally {
                    AutismInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
        }

        private boolean tryAirPlaceBelow() {

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return false;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int slot = findHotbarBlockItem();
            if (slot < 0) return false;
            Vec3 preVelocity = MC.player.getDeltaMovement();

            if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
            try {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                try {
                    MC.player.setDeltaMovement(preVelocity.x, 0.0, preVelocity.z);
                    BlockPos below = MC.player.blockPosition().below();
                    Vec3 hitVec = Vec3.atCenterOf(below).add(0, 0.5, 0);
                    BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, below, false);
                    if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return false;
                    if (!AutismPlacementTick.claim(id())) return false;

                    rotateAndAct(MC.player.getYRot(), 90.0, () -> {
                        MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
                    });
                    return true;
                } finally {
                    MC.player.setDeltaMovement(preVelocity);
                    AutismInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
        }

        private void rotateAndAct(double yaw, double pitch, Runnable action) {
            if (MC.player == null || MC.getConnection() == null) {
                action.run();
                return;
            }
            boolean clientRotate = bool("client-rotate");
            float ny = (float) yaw;
            float np = (float) pitch;
            float oldYaw = MC.player.getYRot();
            float oldPitch = MC.player.getXRot();
            float oldYawO = MC.player.yRotO;
            float oldPitchO = MC.player.xRotO;
            float oldHeadYaw = MC.player.yHeadRot;
            float oldHeadYawO = MC.player.yHeadRotO;
            float oldBodyYaw = MC.player.yBodyRot;
            float oldBodyYawO = MC.player.yBodyRotO;

            MC.player.setYRot(ny);
            MC.player.setXRot(np);
            if (clientRotate) {

                MC.player.yRotO = ny;
                MC.player.xRotO = np;
                MC.player.yHeadRot = ny;
                MC.player.yHeadRotO = ny;
                MC.player.yBodyRot = ny;
                MC.player.yBodyRotO = ny;
            }
            MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                ny, np, MC.player.onGround(), MC.player.horizontalCollision));
            try {
                action.run();
            } finally {
                if (!clientRotate) {

                    MC.player.setYRot(oldYaw);
                    MC.player.setXRot(oldPitch);
                    MC.player.yRotO = oldYawO;
                    MC.player.xRotO = oldPitchO;
                    MC.player.yHeadRot = oldHeadYaw;
                    MC.player.yHeadRotO = oldHeadYawO;
                    MC.player.yBodyRot = oldBodyYaw;
                    MC.player.yBodyRotO = oldBodyYawO;
                }
            }
        }

        private static double yawTo(Vec3 target) {
            double dx = target.x - MC.player.getX();
            double dz = target.z - MC.player.getZ();
            return Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        }

        private static double pitchTo(Vec3 target) {
            Vec3 eye = MC.player.getEyePosition();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            return -Math.toDegrees(Math.atan2(dy, horiz));
        }

        private void cleanupPlacedFluid() {

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return;
            if (!placedFluid || placedTarget == null || MC.level == null || MC.player == null || MC.gameMode == null) return;
            if (++placedTimer > 20) {
                placedFluid = false;
                placedTarget = null;
                placedTimer = 0;
                return;
            }
            boolean insidePlaced = MC.player.getInBlockState().is(Blocks.WATER) || MC.player.getInBlockState().is(Blocks.POWDER_SNOW);
            boolean snowBelow = MC.level.getBlockState(MC.player.blockPosition().below()).is(Blocks.POWDER_SNOW) && MC.player.fallDistance == 0;
            if (!insidePlaced && !snowBelow) return;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int bucket = findHotbarItem(Items.BUCKET);
            if (bucket < 0) return;

            if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
            try {
                AutismInventoryHelper.selectHotbarSlot(MC, bucket);
                try {

                    Vec3 hitVec = Vec3.atCenterOf(placedTarget);

                    BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, placedTarget, false);
                    if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return;
                    if (!AutismPlacementTick.claim(id())) return;
                    rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                        MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
                    });
                    placedFluid = false;
                    placedTarget = null;
                    placedTimer = 0;
                } finally {
                    AutismInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
        }

        private void centerPlayer() {

            double x = Math.floor(MC.player.getX()) + 0.5;
            double z = Math.floor(MC.player.getZ()) + 0.5;
            MC.player.setPos(x, MC.player.getY(), z);

            if (!AutismBlinkManager.isActive() && MC.getConnection() != null) {
                MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(),
                    MC.player.onGround(), MC.player.horizontalCollision));
            }
        }

        @SuppressWarnings("deprecation")
        private boolean isAboveWater() {

            if (MC.level == null || MC.player == null) return false;
            BlockPos.MutableBlockPos pos = MC.player.blockPosition().mutable();
            int bottom = MC.level.getMinY();
            while (pos.getY() > bottom) {
                net.minecraft.world.level.block.state.BlockState state = MC.level.getBlockState(pos);
                if (state.blocksMotion()) break;
                if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return true;
                pos.move(Direction.DOWN);
            }
            return false;
        }

        private int findHotbarItem() {
            Item item = placeItem();
            return findHotbarItem(item);
        }

        private int findHotbarBlockItem() {
            Item preferred = placeItem();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(preferred) && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
            return -1;
        }

        private int findHotbarItem(Item item) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(item)) return i;
            }
            return -1;
        }

        private Item placeItem() {
            return switch (choice("placed-item")) {
                case "PowderSnow" -> Items.POWDER_SNOW_BUCKET;
                case "HayBale" -> Items.HAY_BLOCK;
                case "Cobweb" -> Items.COBWEB;
                case "SlimeBlock" -> Items.SLIME_BLOCK;
                default -> MC.level != null && "the_nether".equals(MC.level.dimension().identifier().getPath()) ? Items.POWDER_SNOW_BUCKET : Items.WATER_BUCKET;
            };
        }
    }

    static final class FastUseModule extends Module {

        private static final int EXP_ROTATION_RESET_TICKS = 5;

        private static final ValueRange DEFAULT_CPS = new ValueRange(8, 12);

        private final autismclient.util.AutismClickPacer usePacer = new autismclient.util.AutismClickPacer();
        private boolean manualExpActive;
        private boolean manualBlockActive;
        private int expRotationResetTicks;
        private AutismRotationUtil.Rotation expSilentRotation;
        private AutismRotationUtil.Rotation serverRotation;

        FastUseModule() {
            super("fast-use", "FastUse", ModuleCategory.PLAYER, "Speeds up item use.");
            fastUseInstance = this;
            add(new BoolSetting("exp", "Fast EXP", true).description("Speed experience bottles."));
            add(new RangeSetting("exp-cps", "EXP CPS", DEFAULT_CPS, 5, 20, 0.5)
                .minSeparation(0.5).visibleWhen(() -> bool("exp")).build());
            add(new BoolSetting("blocks", "Fast Blocks", true).description("Speed block use."));
            add(new RangeSetting("blocks-cps", "Blocks CPS", DEFAULT_CPS, 5, 20, 0.5)
                .minSeparation(0.5).visibleWhen(() -> bool("blocks")).build());
        }

        @Override
        public void preMovementTick() {
            if (MC == null || MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
                resetExpState();
                return;
            }

            if (AutoTotemModule.operationActive()) {
                stopManualUse();
                return;
            }

            if (MC.player.isUsingItem()) {
                stopManualUse();
                return;
            }

            InteractionHand expHand = bool("exp") && canUseManualUse() && !mainHandWouldPreempt(true)
                ? experienceBottleHand() : null;
            if (expHand != null && isPhysicalUseHeld()) {
                activateManualUse(true);
                handleExperienceBottles(expHand);
                return;
            }

            if (bool("blocks") && canUseManualUse() && isHoldingBlockItem() && isPhysicalUseHeld()
                && !mainHandWouldPreempt(false)) {
                activateManualUse(false);
                tickExpRotationReset();
                handleBlock();
                return;
            }

            stopManualUse();
        }

        @Override
        public void onDisable() {
            resetExpState();
        }

        @Override
        public void onGameJoin() {
            resetExpState();
        }

        @Override
        public void onGameLeft() {
            resetExpState();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()
                && MC != null && MC.player != null) {
                AutismRotationUtil.Rotation base = serverRotation();
                serverRotation = new AutismRotationUtil.Rotation(
                    movement.getYRot(base.yaw()), movement.getXRot(base.pitch()));
            }
            return false;
        }

        private void handleExperienceBottles(InteractionHand preferredHand) {
            if (expSilentRotation == null) {
                AutismRotationUtil.Rotation accepted = serverRotation();

                expSilentRotation = AutismRotationUtil.normalizeToSensitivity(
                    new AutismRotationUtil.Rotation(accepted.yaw(), 90.0F), accepted);

                expRotationResetTicks = EXP_ROTATION_RESET_TICKS;
            }

            tickExpRotationReset();

            if (AutismSilentAim.packetRotation() == null && !AutismSilentAim.scaffoldOwnsRotation()) {
                expRotationResetTicks = EXP_ROTATION_RESET_TICKS;
            }

            if (!sameRotation(expSilentRotation, serverRotation())) return;

            if (bottleStillHeld(preferredHand) != null && scheduleUseClick()) {
                AutismInputClicker.queueFastExpUseClick();
            }
        }

        private boolean ownsManualInput() {
            return manualExpActive || manualBlockActive;
        }

        private boolean beginManualInputClick() {

            if (AutismSilentAim.packetRotation() != null || AutismSilentAim.scaffoldOwnsRotation()
                || ScaffoldModule.reservesRageInput() || AutoTotemModule.operationActive()) {
                return false;
            }
            if (manualExpActive) {
                return experienceBottleHand() != null
                    && activeExpRotation() != null
                    && AutismInputClicker.beginFastExpUseClick();
            }
            return manualBlockActive
                && isHoldingBlockItem()
                && AutismInputClicker.beginFastBlockUseClick();
        }

        private boolean canUseManualUse() {
            return MC.options != null
                && MC.level != null
                && MC.gui.screen() == null
                && MC.gui.overlay() == null
                && !MC.player.isDeadOrDying()
                && !MC.player.isSpectator()
                && !PackHideState.isHardLocked()
                && !PackFreecamState.isActive()
                && !autismclient.util.AutismRemoteView.isActive()
                && !autismclient.util.multi.MultiPilot.isActive()
                && !autismclient.util.multi.PacketTeleportController.ownsMainMovement()
                && !MacroExecutor.isRunning();
        }

        private boolean isPhysicalUseHeld() {
            return MC.options != null
                && AutismKeyMappingBridge.of(MC.options.keyUse).autism$isActuallyDown();
        }

        private InteractionHand experienceBottleHand() {
            if (MC.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.MAIN_HAND;
            if (MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.OFF_HAND;
            return null;
        }

        private InteractionHand bottleStillHeld(InteractionHand preferredHand) {
            if (MC.player.getItemInHand(preferredHand).is(Items.EXPERIENCE_BOTTLE)) return preferredHand;
            return experienceBottleHand();
        }

        private void activateManualUse(boolean experience) {
            if (experience && manualExpActive || !experience && manualBlockActive) return;
            stopManualUse();
            manualExpActive = experience;
            manualBlockActive = !experience;
            usePacer.reset();
        }

        private ValueRange useBand() {
            return ValueRange.parse(value(manualExpActive ? "exp-cps" : "blocks-cps"), DEFAULT_CPS);
        }

        private boolean scheduleUseClick() {
            ValueRange band = useBand();
            return usePacer.shouldClick(band.min(), band.max());
        }

        private void stopManualUse() {
            if (manualExpActive) AutismInputClicker.cancelFastExpUseClick();
            if (manualBlockActive) AutismInputClicker.cancelFastBlockUseClick();
            manualExpActive = false;
            manualBlockActive = false;
            usePacer.reset();
            tickExpRotationReset();
        }

        private void tickExpRotationReset() {
            if (expRotationResetTicks <= 0) return;
            expRotationResetTicks--;
            if (expRotationResetTicks <= 0) expSilentRotation = null;
        }

        private void resetExpState() {
            AutismInputClicker.cancelFastExpUseClick();
            AutismInputClicker.cancelFastBlockUseClick();
            manualExpActive = false;
            manualBlockActive = false;
            usePacer.reset();
            expRotationResetTicks = 0;
            expSilentRotation = null;
            serverRotation = null;
        }

        private AutismRotationUtil.Rotation serverRotation() {
            if (serverRotation != null) return serverRotation;
            if (MC != null && MC.player != null) {
                return new AutismRotationUtil.Rotation(MC.player.getYRot(), MC.player.getXRot());
            }
            return new AutismRotationUtil.Rotation(0.0F, 0.0F);
        }

        private AutismRotationUtil.Rotation activeExpRotation() {
            return expRotationResetTicks > 0 ? expSilentRotation : null;
        }

        private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                            AutismRotationUtil.Rotation second) {
            return first != null && second != null
                && Float.compare(first.yaw(), second.yaw()) == 0
                && Float.compare(first.pitch(), second.pitch()) == 0;
        }

        private void handleBlock() {

            if (activeExpRotation() != null) return;
            if (AutismSilentAim.packetRotation() != null || AutismSilentAim.scaffoldOwnsRotation()) return;
            if (scheduleUseClick()) AutismInputClicker.queueFastBlockUseClick();
        }

        private boolean mainHandWouldPreempt(boolean bottlesIntended) {
            ItemStack main = MC.player.getMainHandItem();
            if (main.isEmpty()) return false;
            if (main.getItem() instanceof BlockItem) {

                return bottlesIntended && !MC.player.onGround()
                    && MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
            }
            if (bottlesIntended && main.is(Items.EXPERIENCE_BOTTLE)) return false;
            if (main.getUseAnimation() != ItemUseAnimation.NONE) return true;

            return main.is(Items.EXPERIENCE_BOTTLE) || main.is(Items.ENDER_PEARL)
                || main.is(Items.SNOWBALL) || main.is(Items.EGG)
                || main.is(Items.FISHING_ROD) || main.getItem() instanceof net.minecraft.world.item.BucketItem
                || main.is(Items.WIND_CHARGE) || main.is(Items.FIRE_CHARGE)
                || main.is(Items.ENDER_EYE) || main.is(Items.SPLASH_POTION) || main.is(Items.LINGERING_POTION);
        }

        private boolean isHoldingBlockItem() {
            return MC.player.getMainHandItem().getItem() instanceof BlockItem
                || MC.player.getOffhandItem().getItem() instanceof BlockItem;
        }
    }

    static final class FastBreakModule extends Module {

        FastBreakModule() {
            super("fast-break", "FastBreak", ModuleCategory.PLAYER, "Speeds up block breaking.");
            add(new ChoiceSetting("mode", "Mode", "Damage", "Normal", "Haste", "Damage", "Packet")
                .description("Break method.")
                .build());
            add(RegistryListSetting.blocks("blocks", "Blocks", "")
                .visibleWhen(() -> !"Haste".equals(value("mode")))
                .description("Selected blocks.")
                .build());
            add(new ChoiceSetting("blocks-filter", "Blocks Filter", "Blacklist", "Whitelist", "Blacklist")
                .visibleWhen(() -> !"Haste".equals(value("mode")))
                .description("List mode.")
                .build());
            add(new DoubleSetting("modifier", "Modifier", 1.4, 0.0, 999.0, 0.1)
                .sliderRange(0.0, 10.0)
                .visibleWhen(() -> normalMode(this))
                .description("Speed multiplier.")
                .build());
            add(new IntSetting("haste-amplifier", "Haste Amplifier", 2, 1, 10, 1)
                .visibleWhen(() -> hasteMode(this))
                .description("Haste amplifier to apply.")
                .build());
            add(new BoolSetting("instamine", "Instamine", true)
                .visibleWhen(() -> damageMode(this))
                .description("Finish blocks early.")
                .build());
            add(new BoolSetting("grim-bypass", "Grim Bypass", false)
                .visibleWhen(() -> damageMode(this))
                .description("Abort after stop.")
                .build());
            add(new IntSetting("activation-chance", "Activation Chance", 100, 0, 100, 1)
                .visibleWhen(() -> packetMode(this))
                .unit("%").formatter(v -> v + "%")
                .description("% to fast-break.")
                .build());
        }

        private final Random fastBreakRandom = new Random();
        private BlockPos lastFastBreakPos;
        private boolean fastBreakActivated;

        @Override
        public String info() {
            return choice("mode");
        }

        @Override
        public void onDisable() {
            removeHaste();
            lastFastBreakPos = null;
            fastBreakActivated = false;
        }

        @Override
        public void tick() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            String mode = choice("mode");
            if ("Haste".equals(mode)) {
                tickHasteMode();
            } else {
                removeHaste();
            }

            if ("Damage".equals(mode)) {
                tickDamageMode();
            } else if ("Packet".equals(mode) && MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor) {
                accessor.autism$setDestroyDelay(0);
            }
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (autismclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Damage".equals(choice("mode")) || !bool("grim-bypass") || MC.getConnection() == null) return false;
            if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                MC.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    action.getPos().above(),
                    action.getDirection()
                ));
            }
            return false;
        }

        @Override
        public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
            if (autismclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Damage".equals(choice("mode")) || !bool("instamine")) return false;
            if (MC.player == null || MC.level == null || MC.gameMode == null || MC.getConnection() == null || pos == null) return false;
            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return false;
            if (state.getDestroyProgress(MC.player, MC.level, pos) <= 0.5f) return false;
            if (!(MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor)) return false;

            Direction dir = direction == null ? Direction.UP : direction;
            MC.gameMode.destroyBlock(pos);
            accessor.autism$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos.immutable(), dir, sequence));
            accessor.autism$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos.immutable(), dir, sequence));
            return true;
        }

        @Override
        public void onBlockBreakingProgress(BlockPos pos, Direction direction) {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (!"Packet".equals(choice("mode"))) return;

            if (MC.player == null || MC.level == null || MC.getConnection() == null || pos == null) return;
            if (!(MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor)) return;
            if (accessor.autism$getDestroyProgress() >= 1.0f) return;

            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return;

            if (!pos.equals(lastFastBreakPos)) {
                lastFastBreakPos = pos.immutable();
                fastBreakActivated = fastBreakRandom.nextDouble() * 100.0 < Math.max(0, integer("activation-chance"));
            }
            if (!fastBreakActivated) return;

            Direction dir = direction == null ? Direction.UP : direction;
            MC.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos.immutable(), dir));
        }

        @Override
        public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {

            return false;
        }

        float modifyNormalDestroyProgress(float original, BlockState state, BlockPos pos) {
            if (!"Normal".equals(choice("mode"))) return original;
            if (MC.player == null || MC.level == null || pos == null) return original;
            if (!canMine(state, pos) || !passesBlockFilter(state)) return original;
            return (float) (original * Math.max(0.0, decimal("modifier")));
        }

        boolean usesNormalDestroyModifier() {
            return "Normal".equals(choice("mode"));
        }

        private void tickHasteMode() {
            if (MC.player == null) return;
            int amplifier = Math.max(1, integer("haste-amplifier"));
            MobEffectInstance haste = MC.player.getEffect(MobEffects.HASTE);
            if (haste == null || !haste.showIcon() || haste.getAmplifier() <= amplifier - 1) {
                MC.player.addEffect(new MobEffectInstance(MobEffects.HASTE, -1, amplifier - 1, false, false, false), null);
            }
        }

        private void removeHaste() {
            if (MC.player == null) return;
            MobEffectInstance haste = MC.player.getEffect(MobEffects.HASTE);
            if (haste != null && !haste.showIcon()) MC.player.removeEffect(MobEffects.HASTE);
        }

        private void tickDamageMode() {
            if (MC.player == null || MC.level == null || MC.gameMode == null || !bool("instamine")) return;
            if (!(MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor)) return;
            BlockPos pos = accessor.autism$getDestroyBlockPos();
            if (pos == null || accessor.autism$getDestroyProgress() <= 0.0f) return;
            finishDamageIfReady(pos);
        }

        private void finishDamageIfReady(BlockPos pos) {
            if (MC.player == null || MC.level == null || MC.gameMode == null || pos == null) return;
            if (!(MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor)) return;
            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return;
            float progress = accessor.autism$getDestroyProgress();
            float delta = state.getDestroyProgress(MC.player, MC.level, pos);
            if (progress > 0.0f && progress + delta >= 0.7f) {
                accessor.autism$setDestroyProgress(1.0f);
            }
        }

        private boolean canMine(BlockState state, BlockPos pos) {
            return state != null && !state.isAir() && MC.level != null && state.getDestroySpeed(MC.level, pos) >= 0.0f;
        }

        private boolean passesBlockFilter(BlockState state) {
            if (state == null) return false;
            List<String> configured = list("blocks");
            if (configured.isEmpty()) return "Blacklist".equals(choice("blocks-filter"));
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            boolean listed = false;
            for (String raw : configured) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if (value.equals(id) || value.equals(path)) {
                    listed = true;
                    break;
                }
            }
            return "Whitelist".equals(choice("blocks-filter")) ? listed : !listed;
        }

        private static boolean normalMode(Module module) {
            return module != null && "Normal".equals(module.value("mode"));
        }

        private static boolean hasteMode(Module module) {
            return module != null && "Haste".equals(module.value("mode"));
        }

        private static boolean damageMode(Module module) {
            return module != null && "Damage".equals(module.value("mode"));
        }

        private static boolean packetMode(Module module) {
            return module != null && "Packet".equals(module.value("mode"));
        }
    }

    static final class AimAssistModule extends Module {
        private static final int TARGET_SWITCH_CONFIRM_TICKS = 4;
        private final QuantizedRotationSmoother rotationSmoother = new QuantizedRotationSmoother();
        private AutismRotationUtil.Rotation lastTargetRotation;
        private AutismRotationUtil.Rotation filteredTargetRotation;
        private float sampledHorizontalSpeed = 0.4f;
        private float sampledVerticalSpeed = 0.3f;
        private float sampledDirectionChange;
        private int trackedTargetId = Integer.MIN_VALUE;
        private int switchCandidateId = Integer.MIN_VALUE;
        private int switchCandidateTicks;
        private String cachedEntityListSource = "";
        private Set<String> cachedEntityIds = Set.of();

        AimAssistModule() {
            super("aim-assist", "AimAssist", ModuleCategory.COMBAT, "Smoothly assists your aim toward configured entities.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").build());
            add(new DoubleSetting("range", "Range", 4.5, 1.0, 8.0, 0.1).build());
            add(new IntSetting("fov", "FOV", 80, 0, 180, 1).build());
            add(new ChoiceSetting("target-point", "Target Point", "Nearest", "Nearest", "Center", "Head", "Body", "Feet").build());
            add(new IntSetting("safe-zone", "Safe Zone", 5, 0, 100, 1)
                .formatter(v -> v + "%")
                .description("Deadzone around aim point.")
                .build());
            add(new ChoiceSetting("priority", "Priority", "Direction", "Direction", "Type", "Health", "Distance", "HurtTime", "Age").build());
            add(new ChoiceSetting("axis", "Axis", "Both", "Both", "Horizontal", "Vertical").build());
            add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1).build());
            add(new ChoiceSetting("smooth-mode", "Smooth", "Interpolation", "Interpolation", "Instant").group("Smoothing").build());
            add(new IntSetting("horizontal-speed", "Horizontal", 40, 1, 100, 1).group("Smoothing").formatter(v -> v + "%").visibleWhen(() -> usesSmoothing(this)).build());
            add(new IntSetting("vertical-speed", "Vertical", 10, 1, 100, 1).group("Smoothing").formatter(v -> v + "%").visibleWhen(() -> usesSmoothing(this)).build());
            add(new IntSetting("direction-factor", "Direction Factor", 100, 0, 100, 1).group("Smoothing").formatter(v -> v + "%").visibleWhen(() -> usesSmoothing(this)).build());
            add(new DoubleSetting("midpoint", "Midpoint", 0.35, 0.0, 1.0, 0.05).group("Smoothing").visibleWhen(() -> usesSmoothing(this)).build());
        }

        private static boolean usesSmoothing(Module module) {
            return !"Instant".equals(module.value("smooth-mode"));
        }

        @Override
        public void onDisable() {
            clearAimState();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || AutismSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || AutoTotemModule.operationActive()) {
                clearAimState();
                return;
            }
            LivingEntity target = selectTarget();
            if (target == null) {
                clearAimState();
                return;
            }
            Vec3 eyes = MC.player.getEyePosition();
            Vec3 point = aimPoint(eyes, target);
            if (point == null) {
                clearAimState();
                return;
            }
            AutismRotationUtil.Rotation wanted = AutismRotationUtil.lookingAt(point, eyes);
            AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
            boolean switchedTarget = trackedTargetId != target.getId();
            if (switchedTarget) {
                trackedTargetId = target.getId();
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                sampledDirectionChange = 0.0f;
                sampledHorizontalSpeed = configuredPercent("horizontal-speed");
                sampledVerticalSpeed = configuredPercent("vertical-speed");
                filteredTargetRotation = filterTargetRotation(current, wanted, true);
                rotationSmoother.reset((((long) target.getId()) << 32) ^ System.nanoTime());
                AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
            } else {
                float measuredDirectionChange = lastTargetRotation == null ? 0.0f
                    : (AutismRotationUtil.angleTo(lastTargetRotation, wanted) / 180.0f)
                        * (integer("direction-factor") / 100.0f);
                sampledDirectionChange = approach(sampledDirectionChange, measuredDirectionChange, 0.2f);
                sampledHorizontalSpeed = approach(sampledHorizontalSpeed, configuredPercent("horizontal-speed"), 0.25f);
                sampledVerticalSpeed = approach(sampledVerticalSpeed, configuredPercent("vertical-speed"), 0.25f);
                filteredTargetRotation = filterTargetRotation(current, wanted, false);
            }

            AutismRotationUtil.Rotation guidance = "Instant".equals(choice("smooth-mode"))
                ? wanted
                : filteredTargetRotation;
            String axis = choice("axis");
            if (insideSafeZone(current, wanted, eyes, point, target, axis)) {
                rotationSmoother.halt();
                AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
                lastTargetRotation = wanted;
                return;
            }
            if ("Instant".equals(choice("smooth-mode"))) {
                AutismRotationUtil.Rotation applied = new AutismRotationUtil.Rotation(
                    "Vertical".equals(axis) ? current.yaw() : guidance.yaw(),
                    "Horizontal".equals(axis) ? current.pitch() : guidance.pitch()
                );
                AutismMouseInputSimulator.queueRotation(AutismMouseInputSimulator.Source.AIM_ASSIST, current, applied);
            } else {
                QuantizedRotationSmoother.Step step = rotationSmoother.step(
                    AutismRotationUtil.angleDifference(guidance.yaw(), current.yaw()),
                    AutismRotationUtil.angleDifference(guidance.pitch(), current.pitch()),
                    AutismRotationUtil.mouseDegreesPerRawInput(),
                    sampledHorizontalSpeed,
                    sampledVerticalSpeed,
                    sampledDirectionChange,
                    (float) decimal("midpoint"),
                    !"Vertical".equals(axis),
                    !"Horizontal".equals(axis)
                );
                AutismMouseInputSimulator.queueRotationCounts(
                    AutismMouseInputSimulator.Source.AIM_ASSIST,
                    step.yawCounts(),
                    step.pitchCounts()
                );
            }
            lastTargetRotation = wanted;
        }

        private void clearAimState() {
            lastTargetRotation = null;
            filteredTargetRotation = null;
            trackedTargetId = Integer.MIN_VALUE;
            switchCandidateId = Integer.MIN_VALUE;
            switchCandidateTicks = 0;
            rotationSmoother.reset();
            AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
        }

        private LivingEntity selectTarget() {
            double rangeSq = decimal("range") * decimal("range");
            AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
            Vec3 eyes = MC.player.getEyePosition();
            String priority = choice("priority");
            LivingEntity best = null;
            LivingEntity tracked = null;
            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (!valid(living, rangeSq, current)) continue;
                if (living.getId() == trackedTargetId) tracked = living;
                if (best == null || compareTargets(living, best, priority, current, eyes) < 0) best = living;
            }
            if (tracked == null || best == null || best == tracked) {
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                return tracked == null ? best : tracked;
            }
            if (compareTargets(best, tracked, priority, current, eyes) >= 0) {
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                return tracked;
            }
            if (switchCandidateId == best.getId()) switchCandidateTicks++;
            else {
                switchCandidateId = best.getId();
                switchCandidateTicks = 1;
            }
            if (switchCandidateTicks < TARGET_SWITCH_CONFIRM_TICKS) return tracked;
            switchCandidateId = Integer.MIN_VALUE;
            switchCandidateTicks = 0;
            return best;
        }

        private boolean valid(LivingEntity entity, double rangeSq, AutismRotationUtil.Rotation current) {
            if (entity == MC.player || entity.isRemoved() || !entity.isAlive()) return false;
            if (AutismAntiBot.suppress(entity)) return false;
            if (TeamsModule.combatExcluded(entity, "aimassist")) return false;
            if (entity.hurtTime > integer("hurt-time")) return false;
            if (!matchesEntity(entity)) return false;
            if (entity.distanceToSqr(MC.player) > rangeSq) return false;
            AutismRotationUtil.Rotation toEntity = AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), MC.player.getEyePosition());
            return AutismRotationUtil.angleTo(current, toEntity) <= integer("fov");
        }

        private int compareTargets(LivingEntity a, LivingEntity b, String priority, AutismRotationUtil.Rotation current, Vec3 eyes) {
            return switch (priority) {
                case "Type" -> Integer.compare(typeWeight(a), typeWeight(b));
                case "Health" -> Float.compare(a.getHealth() + a.getAbsorptionAmount(), b.getHealth() + b.getAbsorptionAmount());
                case "Distance" -> Double.compare(a.distanceToSqr(MC.player), b.distanceToSqr(MC.player));
                case "HurtTime" -> Integer.compare(a.hurtTime, b.hurtTime);
                case "Age" -> Integer.compare(b.tickCount, a.tickCount);
                default -> Float.compare(crosshairAngle(a, current, eyes), crosshairAngle(b, current, eyes));
            };
        }

        private int typeWeight(LivingEntity entity) {
            if (entity instanceof net.minecraft.world.entity.player.Player) return 0;
            if (entity instanceof net.minecraft.world.entity.monster.Enemy) return 1;
            return 100;
        }

        private float crosshairAngle(LivingEntity entity, AutismRotationUtil.Rotation current, Vec3 eyes) {
            AutismRotationUtil.Rotation toEntity = AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
            return AutismRotationUtil.angleTo(current, toEntity);
        }

        private boolean matchesEntity(Entity entity) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            Set<String> ids = cachedEntityIds();
            return ids.contains(id) || ids.contains(id.substring(id.indexOf(':') + 1));
        }

        private Set<String> cachedEntityIds() {
            List<String> entries = list("entities");
            String source = String.join("|", entries);
            if (source.equals(cachedEntityListSource)) return cachedEntityIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : entries) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedEntityListSource = source;
            cachedEntityIds = Set.copyOf(normalized);
            return cachedEntityIds;
        }

        private Vec3 aimPoint(Vec3 eyes, LivingEntity entity) {
            if (entity instanceof net.minecraft.world.entity.player.Player) {
                return playerAimPoint(eyes, entity);
            }

            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            double cx = (box.minX + box.maxX) * 0.5;
            double cz = (box.minZ + box.maxZ) * 0.5;
            Vec3 preferred = switch (choice("target-point")) {
                case "Center", "Body" -> box.getCenter();
                case "Head" -> entity.getEyePosition();
                case "Feet" -> new Vec3(cx, box.minY + 0.1, cz);
                default -> nearestPoint(eyes, box);
            };
            if (visible(eyes, preferred)) return preferred;
            return bestVisiblePoint(eyes, box);
        }

        private Vec3 playerAimPoint(Vec3 eyes, LivingEntity player) {

            AABB modelBox = player.getBoundingBox();
            Vec3 preferred = playerModelPoint(
                eyes,
                modelBox,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                choice("target-point")
            );
            if (visible(eyes, preferred)) return preferred;
            return bestVisiblePlayerPoint(eyes, modelBox, player.getX(), player.getZ(), preferred.y);
        }

        static Vec3 playerModelPoint(Vec3 eyes,
                                     AABB modelBox,
                                     double modelX,
                                     double eyeY,
                                     double modelZ,
                                     String targetPoint) {
            double height = Math.max(1.0E-9, modelBox.maxY - modelBox.minY);
            double low = modelBox.minY + Math.min(0.1, height * 0.1);
            double high = modelBox.maxY - Math.min(0.1, height * 0.1);
            String point = targetPoint == null ? "nearest" : targetPoint.trim().toLowerCase(java.util.Locale.ROOT);
            double y = switch (point) {
                case "center", "body" -> (modelBox.minY + modelBox.maxY) * 0.5;
                case "head" -> Mth.clamp(Double.isFinite(eyeY) ? eyeY : modelBox.maxY, low, high);
                case "feet" -> low;
                default -> Mth.clamp(eyes == null ? (modelBox.minY + modelBox.maxY) * 0.5 : eyes.y, low, high);
            };
            return new Vec3(modelX, y, modelZ);
        }

        private Vec3 bestVisiblePlayerPoint(Vec3 eyes,
                                            AABB modelBox,
                                            double modelX,
                                            double modelZ,
                                            double preferredY) {
            double height = Math.max(0.001, modelBox.maxY - modelBox.minY);
            Vec3 best = null;
            double bestVerticalDistance = Double.MAX_VALUE;
            for (int iy = 0; iy < 9; iy++) {
                double y = modelBox.minY + height * ((iy + 0.5) / 9.0);
                Vec3 point = new Vec3(modelX, y, modelZ);
                if (!visible(eyes, point)) continue;
                double verticalDistance = Math.abs(y - preferredY);
                if (verticalDistance < bestVerticalDistance) {
                    bestVerticalDistance = verticalDistance;
                    best = point;
                }
            }
            return best;
        }

        private Vec3 nearestPoint(Vec3 eyes, AABB box) {
            Vec3 closest = new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ)
            );
            if (closest.distanceToSqr(eyes) < 1.0E-6) return box.getCenter();
            return closest;
        }

        private Vec3 bestVisiblePoint(Vec3 eyes, AABB box) {
            AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
            Vec3 best = null;
            float bestAngle = Float.MAX_VALUE;
            for (int ix = 0; ix <= 2; ix++) {
                double x = Mth.lerp(ix / 2.0, box.minX, box.maxX);
                for (int iy = 0; iy <= 2; iy++) {
                    double y = Mth.lerp(iy / 2.0, box.minY, box.maxY);
                    for (int iz = 0; iz <= 2; iz++) {
                        double z = Mth.lerp(iz / 2.0, box.minZ, box.maxZ);
                        Vec3 point = new Vec3(x, y, z);
                        if (!visible(eyes, point)) continue;
                        float angle = AutismRotationUtil.angleTo(current, AutismRotationUtil.lookingAt(point, eyes));
                        if (angle < bestAngle) {
                            bestAngle = angle;
                            best = point;
                        }
                    }
                }
            }
            return best;
        }

        private boolean visible(Vec3 eyes, Vec3 point) {
            if (MC.level == null || MC.player == null || point == null) return false;
            HitResult hit = MC.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, MC.player));
            return hit == null || hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.05;
        }

        private boolean insideSafeZone(AutismRotationUtil.Rotation current,
                                       AutismRotationUtil.Rotation targetRotation,
                                       Vec3 eyes,
                                       Vec3 targetPoint,
                                       LivingEntity target,
                                       String axis) {
            int configured = integer("safe-zone");
            if (configured <= 0 || current == null || targetRotation == null
                || eyes == null || targetPoint == null || target == null) {
                return false;
            }
            if (!crosshairIntersectsTarget(eyes, target)) return false;

            AABB box = target.getBoundingBox().inflate(target.getPickRadius());
            double targetWidth = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);
            double radius = targetWidth * (configured / 100.0);
            double distance = Math.max(1.0E-4, eyes.distanceTo(targetPoint));
            double angularRadius = Math.toDegrees(Math.atan2(radius, distance));
            double hardwareStep = AutismRotationUtil.mouseDegreesPerRawInput();
            if (hardwareStep > 0.0 && Double.isFinite(hardwareStep)) {
                angularRadius = Math.max(angularRadius, hardwareStep);
            }

            boolean yawInside = "Vertical".equals(axis)
                || Math.abs(AutismRotationUtil.angleDifference(targetRotation.yaw(), current.yaw())) <= angularRadius;
            boolean pitchInside = "Horizontal".equals(axis)
                || Math.abs(AutismRotationUtil.angleDifference(targetRotation.pitch(), current.pitch())) <= angularRadius;
            return yawInside && pitchInside;
        }

        private boolean crosshairIntersectsTarget(Vec3 eyes, LivingEntity target) {
            if (MC.player == null || eyes == null || target == null) return false;
            AABB hitbox = target.getBoundingBox().inflate(target.getPickRadius());
            double rayLength = Math.max(decimal("range"), eyes.distanceTo(hitbox.getCenter()) + hitbox.getSize());
            Vec3 rayEnd = eyes.add(MC.player.getViewVector(1.0f).scale(rayLength));
            Optional<Vec3> intersection = hitbox.clip(eyes, rayEnd);
            return intersection.isPresent() && visible(eyes, intersection.get());
        }

        private AutismRotationUtil.Rotation filterTargetRotation(AutismRotationUtil.Rotation current,
                                                                  AutismRotationUtil.Rotation target,
                                                                  boolean switchedTarget) {
            AutismRotationUtil.Rotation base = switchedTarget || filteredTargetRotation == null
                ? current
                : filteredTargetRotation;
            float blend = switchedTarget ? 0.35f : 0.5f;
            float yawStep = Math.abs(AutismRotationUtil.angleDifference(target.yaw(), base.yaw())) * blend;
            float pitchStep = Math.abs(AutismRotationUtil.angleDifference(target.pitch(), base.pitch())) * blend;
            return AutismRotationUtil.towardsLinear(base, target, yawStep, pitchStep);
        }

        private float configuredPercent(String option) {
            return Mth.clamp(integer(option) / 100.0f, 0.01f, 1.0f);
        }

        private static float approach(float current, float target, float factor) {
            return current + (target - current) * Mth.clamp(factor, 0.0f, 1.0f);
        }
    }

    static final class AutoClickerModule extends Module {

        private static final ValueRange DEFAULT_CPS = new ValueRange(6, 11);

        private final autismclient.util.AutismClickPacer pacer = new autismclient.util.AutismClickPacer();

        AutoClickerModule() {
            super("auto-clicker", "AutoClicker", ModuleCategory.COMBAT, "Auto clicks or holds");
            add(new RangeSetting("tps", "CPS", DEFAULT_CPS, 5, 20, 0.5).minSeparation(0.5)
                .visibleWhen(() -> !"Hold".equals(value("mode"))).build());
            add(new ChoiceSetting("mode", "Mode", "Constant", "Constant", "Hold Down", "Hold")
                .description("Click or hold").build());
            add(new ChoiceSetting("button", "Button", "Left", "Left", "Right").description("Mouse button").build());
        }

        @Override
        public void onEnable() {
            pacer.reset();
        }

        @Override
        public void onDisable() {
            standDown();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || AutismSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || AutoTotemModule.operationActive()) {
                standDown();
                return;
            }
            String mode = choice("mode");
            if ("Hold".equals(mode)) {

                pacer.reset();
                holdConfiguredButton();
                return;
            }
            releaseHeldButton();
            if ("Hold Down".equals(mode) && !configuredButtonDown()) {
                pacer.reset();
                return;
            }

            ValueRange band = ValueRange.parse(value("tps"), DEFAULT_CPS);
            if (pacer.shouldClick(band.min(), band.max())) {
                if ("Right".equals(choice("button"))) AutismInputClicker.queueUseClick();
                else AutismInputClicker.queueAttackClick();
            }
        }

        private void standDown() {
            pacer.reset();
            releaseHeldButton();
        }

        private void holdConfiguredButton() {
            boolean right = "Right".equals(choice("button"));
            AutismInputClicker.setAttackHeld(!right);
            AutismInputClicker.setUseHeld(right);
        }

        private void releaseHeldButton() {
            AutismInputClicker.setAttackHeld(false);
            AutismInputClicker.setUseHeld(false);
        }

        private boolean configuredButtonDown() {
            if (MC.options == null) return false;
            return "Right".equals(choice("button"))
                ? AutismKeyMappingBridge.of(MC.options.keyUse).autism$isActuallyDown()
                : AutismKeyMappingBridge.of(MC.options.keyAttack).autism$isActuallyDown();
        }

    }

    static final class TriggerBotModule extends Module {
        private long readyAtNanos;
        private boolean armed;
        private String cachedEntityListSource = "";
        private Set<String> cachedEntityIds = Set.of();

        TriggerBotModule() {
            super("trigger-bot", "TriggerBot", ModuleCategory.COMBAT, "Attacks configured entities when your crosshair is on them.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
                .description("Trigger entities").build());
            add(new BoolSetting("respect-cooldown", "Respect Cooldown", true).description("Wait for full cooldown"));
            add(new BoolSetting("randomize-delay", "Randomize Delay", false)
                .description("Small delay after cooldown."));
        }

        @Override
        public void onEnable() {
            resetTrigger();
        }

        @Override
        public void onDisable() {
            resetTrigger();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || AutismSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || ownsManualFastExp()

                || AutoTotemModule.operationActive()) {
                resetTrigger();
                return;
            }
            if (!(MC.hitResult instanceof EntityHitResult entityHit) || !matchesTarget(entityHit.getEntity())) {
                resetTrigger();
                return;
            }

            boolean respectCooldown = bool("respect-cooldown");
            if (respectCooldown && MC.player.getAttackStrengthScale(0.0f) < 1.0f) {
                resetTrigger();
                return;
            }

            long now = System.nanoTime();
            if (respectCooldown && bool("randomize-delay")) {
                if (!armed) {
                    readyAtNanos = now + AutismHumanRng.triggerJitterMs() * 1_000_000L;
                    armed = true;
                }
                if (now < readyAtNanos) return;
            }

            AutismInputClicker.queueAttackClick();
            resetTrigger();
        }

        private boolean matchesTarget(Entity entity) {
            if (entity == null || entity == MC.player) return false;
            if (AutismAntiBot.suppress(entity)) return false;
            if (TeamsModule.combatExcluded(entity, "triggerbot")) return false;
            Set<String> ids = cachedEntityIds();
            if (ids.isEmpty()) return false;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            return ids.contains(id) || ids.contains(id.substring(id.indexOf(':') + 1));
        }

        private Set<String> cachedEntityIds() {
            List<String> entries = list("entities");
            String source = String.join("|", entries);
            if (source.equals(cachedEntityListSource)) return cachedEntityIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : entries) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedEntityListSource = source;
            cachedEntityIds = Set.copyOf(normalized);
            return cachedEntityIds;
        }

        private void resetTrigger() {
            readyAtNanos = 0L;
            armed = false;
        }
    }

    static final class AutoFishModule extends Module {
        private final Random rng = new Random();
        private String cachedSoundListSource = "";
        private Set<String> cachedSoundIds = Set.of();
        private boolean pendingCatch;
        private int catchTimer;
        private boolean pendingRecast;
        private int recastTimer;
        private boolean movementActive;
        private int movementTick;
        private int movementDuration;
        private float movementStartYaw;
        private float movementTargetYaw;
        private float movementQueuedYaw;
        private float movementJitter;
        private float anchorYaw;
        private boolean anchorSet;
        private int stepIndex;
        private int stepDirection = 1;
        private boolean bobberWasBiting;
        private boolean stopMacroStarted;
        private long stopMacroRunId = -1L;
        private boolean stopMacroWarningShown;
        private int rodSwitchWaitTicks;
        private int autoCastTimer;
        private int awaitingHookTicks;
        private boolean virtualCastActive;
        private int virtualCastTimeoutTicks;
        private int ownedUsePacketTicks;
        private int ownedUsePackets;
        private int ownedSlotPacketTicks;
        private int ownedSlotPackets;
        private int manualInputCooldownTicks;
        private boolean screenWasOpen;
        private boolean awaitingReelConfirmation;
        private int reelConfirmationTicks;
        private int reelAttempts;

        private static final int REEL_CONFIRMATION_TICKS = 4;
        private static final int MAX_REEL_ATTEMPTS = 3;

        AutoFishModule() {
            super("auto-fish", "AutoFish", ModuleCategory.PLAYER, "Automatically reels and recasts fishing rods.");
            add(new ChoiceSetting("trigger-mode", "Trigger", "Sound", "Sound", "Bobber").build());
            add(RegistryListSetting.soundEvents("sounds", "Sounds", "minecraft:entity.fishing_bobber.splash")
                .visibleWhen(() -> "Sound".equals(value("trigger-mode"))).build());
            add(new BoolSetting("sound-distance", "Sound Distance", true)
                .visibleWhen(() -> "Sound".equals(value("trigger-mode"))).description("Require sound near player."));
            add(new DoubleSetting("max-sound-distance", "Max Distance", 12.0, 1.0, 128.0, 0.5)
                .visibleWhen(() -> "Sound".equals(value("trigger-mode")) && Boolean.parseBoolean(value("sound-distance")))
                .description("Max player sound range.").build());
            add(new ChoiceSetting("click-mode", "Click Mode", "Real Input", "Real Input", "Interaction API").build());
            add(new BoolSetting("auto-cast", "Auto Cast", true).group("Casting")
                .description("Recast when missing").build());
            add(new IntSetting("cast-timeout", "No Bite Retry", 60, 0, 300, 1).group("Casting")
                .description("0 = wait forever.").unit("s"));
            add(new BoolSetting("randomize-delays", "Randomize Delays", true).group("Delays").build());
            add(new IntSetting("catch-min", "Catch Min", 2, 0, 32, 1).group("Delays").visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("catch-max", "Catch Max", 5, 0, 40, 1).group("Delays").visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-min", "Recast Min", 8, 0, 32, 1).group("Delays").visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-max", "Recast Max", 12, 0, 40, 1).group("Delays").visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("catch-delay", "Catch Delay", 3, 0, 40, 1).group("Delays").visibleWhen(() -> !Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-delay", "Recast Delay", 10, 0, 40, 1).group("Delays").visibleWhen(() -> !Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("movement-steps", "Steps", 6, 0, 6, 1).group("Movement").build());
            add(new DoubleSetting("degrees-per-step", "Degrees / Step", 5.0, 0.1, 20.0, 0.1)
                .group("Movement").visibleWhen(() -> !"0".equals(value("movement-steps"))).build());
            add(new IntSetting("movement-duration", "Duration", 6, 1, 30, 1)
                .group("Movement").visibleWhen(() -> !"0".equals(value("movement-steps"))).build());
            add(new BoolSetting("anti-break", "Anti Break", true).group("Rod Safety").build());
            add(new IntSetting("min-durability", "Min Durability", 2, 1, 32, 1)
                .group("Rod Safety").visibleWhen(() -> Boolean.parseBoolean(value("anti-break"))).build());
            add(new BoolSetting("auto-select-rod", "Auto Select Best Rod", true).group("Rod Safety").build());
            add(new BoolSetting("stop-macro-enabled", "Auto Stop", false).group("Auto Stop")
                .description("Silent conditional rule").build());
            add(new StringSetting("stop-macro", "Rule", "").group("Auto Stop")
                .visibleWhen(() -> Boolean.parseBoolean(value("stop-macro-enabled"))).conditionalMacroPicker());
            add(new ChoiceSetting("stop-macro-trigger", "On Trigger", "Stop AutoFish",
                    "Stop AutoFish", "Run Steps Only", "Stop, Run, Resume").group("Auto Stop")
                .visibleWhen(() -> Boolean.parseBoolean(value("stop-macro-enabled")))
                .description("Action on condition").build());
        }

        @Override
        protected void onOptionValueChanged(String optionId) {
            normalizeDelayPairForEditedOption(optionId);
            if ("stop-macro".equals(optionId) || "stop-macro-enabled".equals(optionId) || "stop-macro-trigger".equals(optionId)
                || "anti-break".equals(optionId) || "min-durability".equals(optionId) || "auto-select-rod".equals(optionId)) {
                tuneSelectedAutoFishPresetMacro();
                cancelStopMacroIfExternal();
            }
        }

        @Override
        public void onEnable() {
            normalizeDelayPairs();
            migrateLegacyStopSettings();
            tuneSelectedAutoFishPresetMacro();
            resetStopMacroSession();
        }

        @Override
        public void onDisable() {
            cancelStopMacroIfExternal();
            reset();
        }

        @Override
        public void onGameLeft() {
            cancelStopMacro();
            reset();
        }

        @Override
        public void tick() {
            normalizeDelayPairs();
            tickOwnedPacketBudgets();
            if (MC == null || MC.player == null || MC.level == null) {
                resetRuntimeActions();
                return;
            }

            if (AutoTotemModule.operationActive()
                || AutismKillAuraRotation.currentOwner() != null && AutismKillAuraRotation.hasCurrentRotation()) {
                pendingCatch = false;
                catchTimer = 0;
                pendingRecast = false;
                recastTimer = 0;
                clearReelConfirmation();
                return;
            }
            startStopMacroIfNeeded();
            if (MC.gui.screen() != null) {
                pauseForOpenScreen();
                return;
            }
            if (screenWasOpen) resumeAfterScreenClose();
            if (manualInputCooldownTicks > 0) {
                manualInputCooldownTicks--;
                tickVirtualCastState(false);
                return;
            }
            RodDecision rodDecision = updateRodSafety();
            if (!rodDecision.canProceed()) {
                resetRuntimeActions();
                return;
            }
            tickVirtualCastState(true);
            if (tickReelConfirmation()) return;
            if (tickAutoCast()) return;

            if ("Bobber".equals(choice("trigger-mode"))) {
                FishingHook hook = reliableHook();
                boolean biting = hook != null && ((AutismFishingHookAccessor) hook).autism$isBiting();
                if (biting && !bobberWasBiting) scheduleCatch();
                bobberWasBiting = biting;
            }

            if (pendingCatch) {
                if (catchTimer > 0) {
                    catchTimer--;
                } else {
                    boolean clicked = isCastActive() && performUseClick();
                    pendingCatch = false;
                    if (clicked) {
                        awaitingReelConfirmation = true;
                        reelConfirmationTicks = REEL_CONFIRMATION_TICKS;
                        reelAttempts = 1;
                    } else {
                        autoCastTimer = Math.max(autoCastTimer, 2);
                    }
                }
            }

            tickMovement();

            if (pendingRecast) {
                if (recastTimer > 0) recastTimer--;
                if (recastTimer <= 0 && !movementActive) {
                    if (performUseClick()) {
                        markCastAttempt();
                    }
                    pendingRecast = false;
                }
            }
        }

        @Override
        public void onRenderLevel(float partialTick) {
            if (!movementActive || MC == null || MC.player == null) return;
            int duration = Math.max(1, movementDuration);
            float progress = Mth.clamp((movementTick + partialTick) / duration, 0.0f, 1.0f);
            float eased = progress * progress * (3.0f - 2.0f * progress);
            float yaw = movementStartYaw + AutismRotationUtil.angleDifference(movementTargetYaw, movementStartYaw) * eased;
            if (progress < 1.0f) yaw += (float) Math.sin(progress * Math.PI) * movementJitter;
            queueMovementYaw(yaw);
        }

        @Override
        public void onSoundPacket(ClientboundSoundPacket packet) {
            if (!"Sound".equals(choice("trigger-mode"))
                || MC == null || MC.player == null || MC.level == null) return;
            if (!isCastActive()) return;
            if (!matchesSound(packet)) return;
            if (bool("sound-distance")) {
                double max = effectiveSoundDistance(packet);
                FishingHook hook = reliableHook();
                Vec3 origin = hook == null ? MC.player.position() : hook.position();
                if (origin.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > max * max) return;
            }
            scheduleCatch();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {

            if (AutismHandArbiter.handPacketOwner() != null) return false;
            if (packet instanceof ServerboundUseItemPacket usePacket) {
                if (consumeOwnedUsePacket()) return false;
                if (isRodHand(usePacket.getHand())) handleManualRodUse();
            } else if (packet instanceof ServerboundUseItemOnPacket useOnPacket) {
                if (consumeOwnedUsePacket()) return false;
                if (isRodHand(useOnPacket.getHand())) handleManualRodUse();
            } else if (packet instanceof ServerboundSetCarriedItemPacket carriedPacket) {
                if (consumeOwnedSlotPacket()) return false;
                handleManualSlotSwitch(carriedPacket.getSlot());
            }
            return false;
        }

        private double effectiveSoundDistance(ClientboundSoundPacket packet) {
            double configured = Math.max(1.0, decimal("max-sound-distance"));
            if (packet == null) return configured;
            double audible = Math.max(1.0, packet.getVolume()) * 16.0;
            return Math.min(configured, audible);
        }

        private void scheduleCatch() {
            if (pendingCatch || pendingRecast || awaitingReelConfirmation) return;
            pendingCatch = true;
            catchTimer = nextDelay("catch-delay", "catch-min", "catch-max");
        }

        private int nextDelay(String fixedOption, String minOption, String maxOption) {
            normalizeDelayPairs();
            if (!bool("randomize-delays")) return Math.max(0, integer(fixedOption));
            int min = Math.max(0, integer(minOption));
            int max = Math.max(min, integer(maxOption));
            return min + rng.nextInt(max - min + 1);
        }

        private boolean matchesSound(ClientboundSoundPacket packet) {
            if (packet == null || packet.getSound() == null || packet.getSound().value() == null) return false;
            Identifier id = packet.getSound().value().location();
            if (id == null) return false;
            String soundId = id.toString().toLowerCase(Locale.ROOT);
            Set<String> sounds = cachedSoundIds();
            return sounds.contains(soundId) || sounds.contains(soundId.substring(soundId.indexOf(':') + 1));
        }

        private Set<String> cachedSoundIds() {
            List<String> sounds = list("sounds");
            if (sounds.isEmpty()) sounds = List.of("minecraft:entity.fishing_bobber.splash");
            String source = String.join("|", sounds);
            if (source.equals(cachedSoundListSource)) return cachedSoundIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : sounds) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedSoundListSource = source;
            cachedSoundIds = Set.copyOf(normalized);
            return cachedSoundIds;
        }

        private boolean hasFishingRod() {
            InteractionHand hand = rodHand();
            if (hand == null) return false;
            return isRodSafe(MC.player.getItemInHand(hand));
        }

        private boolean isFishingNow() {
            return reliableHook() != null;
        }

        private boolean isCastActive() {
            return virtualCastActive || isFishingNow();
        }

        private InteractionHand rodHand() {
            if (MC.player == null) return null;
            if (MC.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof FishingRodItem) return InteractionHand.MAIN_HAND;

            if (AutismHandArbiter.offhandClaimedByOther(id())) return null;
            if (MC.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof FishingRodItem) return InteractionHand.OFF_HAND;
            return null;
        }

        private boolean isRodHand(InteractionHand hand) {
            return MC.player != null
                && hand != null
                && MC.player.getItemInHand(hand).getItem() instanceof FishingRodItem;
        }

        private boolean performUseClick() {

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return false;
            if (!hasFishingRod()) return false;
            markOwnedUsePacket();
            if ("Interaction API".equals(choice("click-mode"))) {
                InteractionHand hand = rodHand();
                if (hand == null || MC.gameMode == null || MC.player == null) return false;

                if (ModuleRegistry.shouldCancelUseExcept(MC.hitResult, hand, id())) return false;
                InteractionResult result = MC.gameMode.useItem(MC.player, hand);
                if (result != null && result.consumesAction()) MC.player.swing(hand);
            } else {
                AutismInputClicker.queueUseClick();
            }
            return true;
        }

        private boolean tickAutoCast() {
            if (!bool("auto-cast")) {
                autoCastTimer = 0;
                awaitingHookTicks = 0;
                return false;
            }
            FishingHook hook = reliableHook();
            if (hook != null) {
                autoCastTimer = 0;
                awaitingHookTicks = 0;
                virtualCastActive = true;
                return false;
            }
            bobberWasBiting = false;
            if (virtualCastActive) return false;
            if (pendingCatch || pendingRecast || awaitingReelConfirmation || movementActive) return false;
            if (awaitingHookTicks > 0) {
                awaitingHookTicks--;
                return true;
            }
            if (autoCastTimer > 0) {
                autoCastTimer--;
                return true;
            }
            if (performUseClick()) markCastAttempt();
            else autoCastTimer = 1;
            return true;
        }

        private void markCastAttempt() {
            virtualCastActive = true;
            virtualCastTimeoutTicks = castTimeoutTicks();
            awaitingHookTicks = 8;
            autoCastTimer = nextDelay("recast-delay", "recast-min", "recast-max");
        }

        private void tickVirtualCastState(boolean allowRetryClick) {
            if (!virtualCastActive || pendingCatch || pendingRecast || awaitingReelConfirmation) return;
            if (castTimeoutTicks() <= 0) return;
            if (virtualCastTimeoutTicks <= 0) virtualCastTimeoutTicks = castTimeoutTicks();
            virtualCastTimeoutTicks--;
            if (virtualCastTimeoutTicks > 0) return;

            boolean hadReliableHook = isFishingNow();
            clearVirtualCastState();
            if (!allowRetryClick || !bool("auto-cast")) {
                autoCastTimer = Math.max(autoCastTimer, 2);
                return;
            }
            if (hadReliableHook && performUseClick()) {
                pendingRecast = true;
                recastTimer = nextDelay("recast-delay", "recast-min", "recast-max");
            } else {
                autoCastTimer = Math.max(autoCastTimer, nextDelay("recast-delay", "recast-min", "recast-max"));
            }
        }

        private void clearVirtualCastState() {
            virtualCastActive = false;
            virtualCastTimeoutTicks = 0;
            awaitingHookTicks = 0;
        }

        private boolean tickReelConfirmation() {
            if (!awaitingReelConfirmation) return false;
            if (!isFishingNow()) {
                awaitingReelConfirmation = false;
                reelConfirmationTicks = 0;
                reelAttempts = 0;
                clearVirtualCastState();
                startMovement();
                pendingRecast = bool("auto-cast");
                recastTimer = pendingRecast ? nextDelay("recast-delay", "recast-min", "recast-max") : 0;
                return false;
            }

            if (reelConfirmationTicks > 0) {
                reelConfirmationTicks--;
                return true;
            }
            if (reelAttempts < MAX_REEL_ATTEMPTS && performUseClick()) {
                reelAttempts++;
                reelConfirmationTicks = REEL_CONFIRMATION_TICKS;
                return true;
            }

            awaitingReelConfirmation = false;
            reelConfirmationTicks = 0;
            reelAttempts = 0;
            autoCastTimer = Math.max(autoCastTimer, 2);
            return false;
        }

        private int castTimeoutTicks() {
            return Math.max(0, integer("cast-timeout")) * 20;
        }

        private void handleManualRodUse() {
            if (MC == null || MC.player == null || MC.level == null) return;
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 3);
            pendingCatch = false;
            catchTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();

            if (isFishingNow()) {
                clearVirtualCastState();
                pendingRecast = bool("auto-cast");
                recastTimer = pendingRecast ? nextDelay("recast-delay", "recast-min", "recast-max") : 0;
                autoCastTimer = Math.max(autoCastTimer, 2);
            } else {
                pendingRecast = false;
                recastTimer = 0;
                markCastAttempt();
            }
        }

        private void handleManualSlotSwitch(int slot) {
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 8);
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();
            bobberWasBiting = false;
            if (!isFishingNow()) clearVirtualCastState();
            autoCastTimer = Math.max(autoCastTimer, 6);
        }

        private void pauseForOpenScreen() {
            screenWasOpen = true;
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();
            bobberWasBiting = false;
            autoCastTimer = Math.max(autoCastTimer, 4);
        }

        private void resumeAfterScreenClose() {
            screenWasOpen = false;
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 4);
            if (isFishingNow()) {
                virtualCastActive = true;
                if (virtualCastTimeoutTicks <= 0) virtualCastTimeoutTicks = castTimeoutTicks();
            } else if (virtualCastActive) {
                clearVirtualCastState();
                autoCastTimer = Math.max(autoCastTimer, nextDelay("recast-delay", "recast-min", "recast-max"));
            }
        }

        private FishingHook reliableHook() {
            if (MC == null || MC.player == null || MC.level == null) return null;
            FishingHook playerHook = MC.player.fishing;
            if (isReliableHookEntity(playerHook)) return playerHook;
            FishingHook fallback = null;
            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof FishingHook hook)) continue;
                if (!isReliableHookEntity(hook)) continue;
                if (fallback == null || hook.distanceToSqr(MC.player) < fallback.distanceToSqr(MC.player)) fallback = hook;
            }
            return fallback;
        }

        private boolean isReliableHookEntity(FishingHook hook) {
            if (hook == null || hook.isRemoved() || MC.player == null) return false;
            if (hook.distanceToSqr(MC.player) > 1024.0) return false;
            Entity owner = hook.getOwner();
            return owner == MC.player || owner != null && owner.getUUID().equals(MC.player.getUUID());
        }

        private void markOwnedUsePacket() {
            ownedUsePackets = Math.min(ownedUsePackets + 2, 4);
            ownedUsePacketTicks = 4;
        }

        private boolean consumeOwnedUsePacket() {
            if (ownedUsePackets <= 0) return false;
            ownedUsePackets--;
            return true;
        }

        private void markOwnedSlotPacket() {
            ownedSlotPackets = Math.min(ownedSlotPackets + 1, 4);
            ownedSlotPacketTicks = 4;
        }

        private boolean consumeOwnedSlotPacket() {
            if (ownedSlotPackets <= 0) return false;
            ownedSlotPackets--;
            return true;
        }

        private void tickOwnedPacketBudgets() {
            if (ownedUsePacketTicks > 0 && --ownedUsePacketTicks <= 0) ownedUsePackets = 0;
            if (ownedSlotPacketTicks > 0 && --ownedSlotPacketTicks <= 0) ownedSlotPackets = 0;
        }

        private void startMovement() {
            int steps = integer("movement-steps");
            if (steps <= 0 || MC.player == null) return;
            if (!anchorSet) {
                anchorYaw = MC.player.getYRot();
                anchorSet = true;
            }
            if (stepDirection == 0) stepDirection = 1;
            stepIndex += stepDirection;
            if (stepIndex >= steps) {
                stepIndex = steps;
                stepDirection = -1;
            } else if (stepIndex <= -steps) {
                stepIndex = -steps;
                stepDirection = 1;
            }
            movementActive = true;
            movementTick = 0;
            movementDuration = Math.max(1, integer("movement-duration"));
            movementStartYaw = MC.player.getYRot();
            movementTargetYaw = anchorYaw + (float) (stepIndex * decimal("degrees-per-step"));
            movementQueuedYaw = movementStartYaw;
            movementJitter = (rng.nextFloat() - 0.5f) * 0.08f;
        }

        private void tickMovement() {
            if (!movementActive || MC.player == null) return;
            movementTick++;
            if (movementTick >= Math.max(1, movementDuration)) {
                queueMovementYaw(movementTargetYaw);
                movementActive = false;
            }
        }

        private void queueMovementYaw(float yaw) {
            float yawDelta = AutismRotationUtil.angleDifference(yaw, movementQueuedYaw);
            movementQueuedYaw = yaw;
            AutismMouseInputSimulator.queueRotationDelta(
                AutismMouseInputSimulator.Source.AUTO_FISH, yawDelta, 0.0f);
        }

        private void resetRuntimeActions() {
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            autoCastTimer = 0;
            clearVirtualCastState();
            ownedUsePacketTicks = 0;
            ownedUsePackets = 0;
            ownedSlotPacketTicks = 0;
            ownedSlotPackets = 0;
            manualInputCooldownTicks = 0;
            screenWasOpen = false;
            movementActive = false;
            movementTick = 0;
            bobberWasBiting = false;
            AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AUTO_FISH);
            clearReelConfirmation();
        }

        private void clearReelConfirmation() {
            awaitingReelConfirmation = false;
            reelConfirmationTicks = 0;
            reelAttempts = 0;
        }

        private void reset() {
            resetRuntimeActions();
            anchorSet = false;
            anchorYaw = 0.0f;
            stepIndex = 0;
            stepDirection = 1;
            rodSwitchWaitTicks = 0;
        }

        private void normalizeDelayPairs() {
            normalizeDelayPair("catch-min", "catch-max");
            normalizeDelayPair("recast-min", "recast-max");
        }

        private void normalizeDelayPairForEditedOption(String optionId) {
            if ("catch-min".equals(optionId) || "catch-max".equals(optionId)) normalizeDelayPair("catch-min", "catch-max", optionId);
            else if ("recast-min".equals(optionId) || "recast-max".equals(optionId)) normalizeDelayPair("recast-min", "recast-max", optionId);
        }

        private void normalizeDelayPair(String minId, String maxId) {
            normalizeDelayPair(minId, maxId, "");
        }

        private void normalizeDelayPair(String minId, String maxId, String editedId) {
            int min = integer(minId);
            int max = integer(maxId);
            if (min <= max) return;
            if (minId.equals(editedId)) {
                setValue(maxId, Integer.toString(min));
            } else {
                setValue(minId, Integer.toString(max));
            }
        }

        private RodDecision updateRodSafety() {
            if (MC.player == null) return RodDecision.pause();

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return RodDecision.pause();
            RodChoice best = findBestRod(true);
            if (best == null) {
                return RodDecision.pause();
            }

            if (bool("auto-select-rod")) {
                RodChoice selected = selectedMainHandRod();
                if (selected == null || !selected.safe() || best.score() > selected.score()) {

                    if (best.slot() >= 0 && best.slot() < 9 && !AutismHandArbiter.slotReserved(best.slot(), id())) {

                        if (!AutismHandArbiter.beginHandPacketGroup(id())) return RodDecision.pause();
                        try {
                            markOwnedSlotPacket();
                            AutismInventoryHelper.selectHotbarSlot(MC, best.slot());
                            rodSwitchWaitTicks = 1;
                        } finally {
                            AutismHandArbiter.endHandPacketGroup(id());
                        }
                        return RodDecision.pause();
                    }
                    if (best.slot() >= 9 && best.slot() < 36 && !AutismHandArbiter.slotReserved(best.slot(), id())) {
                        int target = MC.player.getInventory().getSelectedSlot();

                        if (!AutismHandArbiter.slotReserved(target, id())) {

                            if (!AutismHandArbiter.beginHandPacketGroup(id())) return RodDecision.pause();
                            try {
                                if (AutismInventoryHelper.swapInventorySlots(MC, best.slot(), target)) {
                                    markOwnedSlotPacket();
                                    AutismInventoryHelper.selectHotbarSlot(MC, target);
                                    rodSwitchWaitTicks = 1;
                                    return RodDecision.pause();
                                }
                            } finally {
                                AutismHandArbiter.endHandPacketGroup(id());
                            }
                        }
                    }
                }
            }

            if (rodSwitchWaitTicks > 0) {
                rodSwitchWaitTicks--;
                return RodDecision.pause();
            }
            return hasFishingRod() ? RodDecision.proceed() : RodDecision.pause();
        }

        private RodChoice selectedMainHandRod() {
            if (MC.player == null) return null;
            int slot = MC.player.getInventory().getSelectedSlot();
            return rodChoice(slot, MC.player.getInventory().getItem(slot), false);
        }

        private RodChoice findBestRod(boolean safeOnly) {
            if (MC.player == null) return null;
            RodChoice best = null;
            int size = MC.player.getInventory().getContainerSize();
            for (int slot = 0; slot < Math.min(36, size); slot++) {
                RodChoice choice = rodChoice(slot, MC.player.getInventory().getItem(slot), safeOnly);
                if (choice != null && (best == null || choice.score() > best.score())) best = choice;
            }
            RodChoice offhand = rodChoice(40, MC.player.getOffhandItem(), safeOnly);
            if (offhand != null && (best == null || offhand.score() > best.score())) best = offhand;
            return best;
        }

        private RodChoice rodChoice(int slot, ItemStack stack, boolean safeOnly) {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) return null;
            boolean safe = isRodSafe(stack);
            if (safeOnly && !safe) return null;
            int durability = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : 999;
            int score = enchantLevel(stack, Enchantments.LUCK_OF_THE_SEA) * 900
                + enchantLevel(stack, Enchantments.LURE) * 900
                + enchantLevel(stack, Enchantments.UNBREAKING) * 200
                + enchantLevel(stack, Enchantments.MENDING) * 100
                + noVanishingBonus(stack) * 50
                + Math.min(49, durability);
            return new RodChoice(slot, score, safe);
        }

        private boolean isRodSafe(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) return false;
            if (!bool("anti-break") || !stack.isDamageableItem()) return true;
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            return remaining > Math.max(1, integer("min-durability"));
        }

        private int enchantLevel(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
            try {
                if (MC == null || MC.level == null || stack == null || stack.isEmpty()) return 0;
                var holder = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
                return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        private int noVanishingBonus(ItemStack stack) {
            try {
                return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) ? 0 : 1;
            } catch (Throwable ignored) {
                return 1;
            }
        }

        private void migrateLegacyStopSettings() {
            if (bool("stop-macro-enabled") || !value("stop-macro").trim().isEmpty()) return;
            if (Boolean.parseBoolean(value("stop-inv-slots"))) {
                autismclient.util.AutismMacro macro = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.FREE_SLOTS);
                if (macro != null) {
                    tuneAutoFishPresetMacro(macro, AutoFishStopMacroFactory.Preset.FREE_SLOTS);
                    setValue("stop-macro-enabled", "true");
                    setValue("stop-macro", macro.name);
                }
            } else if (Boolean.parseBoolean(value("stop-out-of-rods"))) {
                autismclient.util.AutismMacro macro = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.DURABILITY);
                if (macro != null) {
                    tuneAutoFishPresetMacro(macro, AutoFishStopMacroFactory.Preset.DURABILITY);
                    setValue("stop-macro-enabled", "true");
                    setValue("stop-macro", macro.name);
                }
            }
        }

        private void tuneAutoFishPresetMacro(autismclient.util.AutismMacro macro, AutoFishStopMacroFactory.Preset preset) {
            if (macro == null || macro.actions == null || macro.actions.isEmpty() || preset == null) return;
            if (preset == AutoFishStopMacroFactory.Preset.FREE_SLOTS && macro.actions.get(0) instanceof WaitFreeSlotsAction wait) {
                int slots = Math.max(0, Math.min(36, integer("min-free-slots")));
                boolean changed = wait.countMode != WaitFreeSlotsAction.CountMode.FREE_SLOTS
                    || wait.comparison != WaitFreeSlotsAction.Comparison.AT_MOST
                    || wait.slots != slots;
                wait.countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
                wait.comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
                wait.slots = slots;
                if (changed) autismclient.util.AutismMacroManager.get().save();
            } else if (preset == AutoFishStopMacroFactory.Preset.DURABILITY && macro.actions.get(0) instanceof WaitDurabilityAction wait) {
                int value = Math.max(1, integer("min-durability"));
                boolean changed = wait.targetMode != WaitDurabilityAction.TargetMode.ITEM
                    || !"minecraft:fishing_rod".equals(wait.itemName)
                    || wait.measurement != WaitDurabilityAction.Measurement.REMAINING
                    || wait.comparison != WaitDurabilityAction.Comparison.AT_MOST
                    || wait.value != value
                    || !wait.useNext;
                wait.targetMode = WaitDurabilityAction.TargetMode.ITEM;
                wait.itemName = "minecraft:fishing_rod";
                wait.measurement = WaitDurabilityAction.Measurement.REMAINING;
                wait.comparison = WaitDurabilityAction.Comparison.AT_MOST;
                wait.value = value;
                wait.useNext = true;
                if (changed) autismclient.util.AutismMacroManager.get().save();
            }
        }

        private void startStopMacroIfNeeded() {
            if (!bool("stop-macro-enabled")) return;
            if (stopMacroStarted) return;
            stopMacroStarted = true;
            tuneSelectedAutoFishPresetMacro();
            String macroName = value("stop-macro").trim();
            if (macroName.isBlank()) {
                warnStopMacroOnce("AutoFish auto stop rule missing.");
                return;
            }
            autismclient.util.AutismMacro macro = autismclient.util.AutismMacroManager.get().get(macroName);
            if (macro == null) {
                warnStopMacroOnce("AutoFish auto stop rule not found: " + macroName);
                return;
            }
            if (!AutoFishStopMacroFactory.isValidStopMacro(macro)) {
                warnStopMacroOnce("AutoFish auto stop rule must start with a conditional.");
                return;
            }
            autismclient.util.AutismMacro runtimeMacro = buildAutoStopRuntimeMacro(macro);
            if (runtimeMacro == null || runtimeMacro.actions == null || runtimeMacro.actions.isEmpty()) {
                warnStopMacroOnce("AutoFish auto stop could not build a valid rule.");
                return;
            }
            runtimeMacro.regenerateAllPackets();
            stopMacroRunId = MacroExecutor.executeTracked(runtimeMacro, MacroExecutor.RunOptions.silentBackground());
            if (stopMacroRunId < 0L) warnStopMacroOnce("AutoFish auto stop could not start.");
        }

        private void tuneSelectedAutoFishPresetMacro() {
            String macroName = value("stop-macro").trim();
            AutoFishStopMacroFactory.Preset preset = AutoFishStopMacroFactory.presetForGeneratedName(macroName);
            if (preset == null) return;
            autismclient.util.AutismMacro macro = autismclient.util.AutismMacroManager.get().get(macroName);
            if (macro == null) return;
            tuneAutoFishPresetMacro(macro, preset);
        }

        private autismclient.util.AutismMacro buildAutoStopRuntimeMacro(autismclient.util.AutismMacro source) {
            if (source == null || source.actions == null) return null;
            autismclient.util.AutismMacro runtime = source.deepCopy(source.name);
            runtime.keyCode = -1;
            runtime.loop = false;
            runtime.loopCount = -1;
            int conditionIndex = firstEnabledConditionIndex(runtime.actions);
            if (conditionIndex < 0) return null;

            AutoStopTrigger trigger = AutoStopTrigger.from(choice("stop-macro-trigger"));
            boolean injectStop = trigger != AutoStopTrigger.RUN_STEPS_ONLY;
            boolean skipImmediateDuplicateDisable = injectStop
                && conditionIndex + 1 < runtime.actions.size()
                && AutoFishStopMacroFactory.disablesAutoFish(runtime.actions.get(conditionIndex + 1));
            boolean injectResume = trigger == AutoStopTrigger.STOP_RUN_RESUME
                && !hasExplicitAutoFishToggleAfter(runtime.actions, conditionIndex, skipImmediateDuplicateDisable);

            ArrayList<MacroAction> rebuilt = new ArrayList<>();
            for (int i = 0; i < runtime.actions.size(); i++) {
                MacroAction action = runtime.actions.get(i);
                if (i == conditionIndex) {
                    rebuilt.add(action);
                    if (injectStop) rebuilt.add(new AutoFishRuntimeToggleAction(this, false));
                    continue;
                }
                if (skipImmediateDuplicateDisable && i == conditionIndex + 1) continue;
                rebuilt.add(action);
            }
            if (injectResume) rebuilt.add(new AutoFishRuntimeToggleAction(this, true));
            runtime.actions = rebuilt;
            return runtime;
        }

        private int firstEnabledConditionIndex(List<MacroAction> actions) {
            if (actions == null) return -1;
            for (int i = 0; i < actions.size(); i++) {
                MacroAction action = actions.get(i);
                if (action == null || !action.isEnabled()) continue;
                return MacroConditionUtil.isWaitConditionAction(action) ? i : -1;
            }
            return -1;
        }

        private boolean hasExplicitAutoFishToggleAfter(List<MacroAction> actions, int conditionIndex, boolean ignoreImmediateDuplicateDisable) {
            if (actions == null) return false;
            for (int i = Math.max(0, conditionIndex + 1); i < actions.size(); i++) {
                if (ignoreImmediateDuplicateDisable && i == conditionIndex + 1) continue;
                MacroAction action = actions.get(i);
                if (action != null && action.isEnabled() && AutoFishStopMacroFactory.isAutoFishToggleAction(action)) return true;
            }
            return false;
        }

        private enum AutoStopTrigger {
            STOP_AUTO_FISH,
            RUN_STEPS_ONLY,
            STOP_RUN_RESUME;

            static AutoStopTrigger from(String value) {
                String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if ("run steps only".equals(normalized)) return RUN_STEPS_ONLY;
                if ("stop, run, resume".equals(normalized)) return STOP_RUN_RESUME;
                return STOP_AUTO_FISH;
            }
        }

        private static final class AutoFishRuntimeToggleAction implements MacroAction {
            private final AutoFishModule owner;
            private final boolean enable;

            private AutoFishRuntimeToggleAction(AutoFishModule owner, boolean enable) {
                this.owner = owner;
                this.enable = enable;
            }

            @Override
            public void execute(Minecraft mc) {
                if (owner != null) owner.setEnabledSilently(enable);
            }

            @Override
            public CompoundTag toTag() {
                CompoundTag tag = new CompoundTag();
                tag.putString("type", MacroActionType.TOGGLE_MODULE.name());
                return tag;
            }

            @Override public void fromTag(CompoundTag tag) {}
            @Override public MacroActionType getType() { return MacroActionType.TOGGLE_MODULE; }
            @Override public String getDisplayName() { return enable ? "Resume AutoFish" : "Stop AutoFish"; }
            @Override public String getIcon() { return "M"; }
        }

        private void warnStopMacroOnce(String message) {
            if (stopMacroWarningShown) return;
            stopMacroWarningShown = true;
            AutismNotifications.warning(message);
        }

        private void resetStopMacroSession() {
            stopMacroStarted = false;
            stopMacroWarningShown = false;
            stopMacroRunId = -1L;
        }

        private void cancelStopMacroIfExternal() {
            long runId = stopMacroRunId;
            if (runId >= 0L && MacroExecutor.isRunActive(runId) && MacroExecutor.currentRunId() != runId) {
                MacroExecutor.stopRun(runId);
            }
            resetStopMacroSession();
        }

        private void cancelStopMacro() {
            long runId = stopMacroRunId;
            if (runId >= 0L && MacroExecutor.isRunActive(runId)) MacroExecutor.stopRun(runId);
            resetStopMacroSession();
        }

        private record RodChoice(int slot, int score, boolean safe) {
        }

        private record RodDecision(boolean canProceed) {
            static RodDecision proceed() { return new RodDecision(true); }
            static RodDecision pause() { return new RodDecision(false); }
        }
    }

    static final class InstantRebreakModule extends Module {
        private BlockPos target;
        private Direction direction = Direction.UP;
        private int delay;

        InstantRebreakModule() {
            super("instant-rebreak", "InstantRebreak", ModuleCategory.PLAYER, "Rebreaks the last block.");
            add(new IntSetting("delay", "Delay", 0, 0, 20, 1).description("Delay between rebreak packets."));
            add(new BoolSetting("only-pick", "Only Pickaxe", true).description("Require pickaxe."));
            add(new BoolSetting("rotate", "Rotate", false).description("Face target first."));
            add(new BoolSetting("render", "Render", true).group("Render").description("Show the rebreak target."));
            add(new ColorSetting("color", "Color", 0xFFFF3B3B).group("Render").description("Single outline color.").build());
        }

        @Override
        public void onDisable() {
            AutismInstaBreakRenderer.clear();
            target = null;
        }

        @Override
        public void onStartBreakingBlock(BlockPos pos, Direction direction) {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            target = pos == null ? null : pos.immutable();
            this.direction = direction == null ? Direction.UP : direction;
            if (bool("render")) AutismInstaBreakRenderer.setTarget(target, colorValue("color", colorValue("line-color", 0xFF39D77A)));
            delay = 0;
        }

        @Override
        public void tick() {
            if (autismclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.level == null || MC.getConnection() == null || target == null) return;

            if (AutoTotemModule.operationActive()) return;

            if (!bool("render")) AutismInstaBreakRenderer.clear();
            else AutismInstaBreakRenderer.setTarget(target, colorValue("color", colorValue("line-color", 0xFF39D77A)));

            if (MC.level.isOutsideBuildHeight(target) || MC.level.getBlockState(target).isAir()) return;
            if (bool("only-pick") && !MC.player.getMainHandItem().is(ItemTags.PICKAXES)) return;
            if (delay++ < integer("delay")) return;
            delay = 0;
            if (bool("rotate")) MC.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));

            BlockPos pos = target;
            Direction dir = direction == null ? Direction.UP : direction;

            if (MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor) {
                accessor.autism$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence));
            } else {
                MC.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
            }
            MC.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        private int colorValue(String id, int fallback) {
            try {
                String value = value(id).replace("#", "");
                if (value.length() == 6) value = "FF" + value;
                return (int) Long.parseLong(value, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    static final class NoInteractModule extends Module {
        NoInteractModule() {
            super("no-interact", "NoInteract", ModuleCategory.PLAYER, "Cancels selected inputs.");
            add(RegistryListSetting.blocks("block-mine", "Block Mine", "").group("Blocks").description("Mining block list.").build());
            add(new ChoiceSetting("block-mine-mode", "Mine Mode", "BlackList", "WhiteList", "BlackList").group("Blocks").build());
            add(RegistryListSetting.blocks("block-interact", "Block Interact", "").group("Blocks").description("Use block list.").build());
            add(new ChoiceSetting("block-interact-mode", "Block Mode", "BlackList", "WhiteList", "BlackList").group("Blocks").build());
            add(new ChoiceSetting("block-interact-hand", "Block Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Blocks").build());
            add(RegistryListSetting.entityTypes("entity-interact", "Entity Interact", "").group("Entities").description("Use entity list.").build());
            add(new ChoiceSetting("entity-interact-mode", "Entity Mode", "BlackList", "WhiteList", "BlackList").group("Entities").build());
            add(new ChoiceSetting("entity-interact-hand", "Entity Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Entities").build());
            add(RegistryListSetting.entityTypes("entity-hit", "Entity Hit", "").group("Attacks").build());
            add(new ChoiceSetting("entity-hit-mode", "Hit Mode", "WhiteList", "WhiteList", "BlackList").group("Attacks").build());
            add(new BoolSetting("friends", "Friends", false).group("Entities"));
            add(new BoolSetting("babies", "Babies", false).group("Entities"));
            add(new BoolSetting("nametagged", "Nametagged", false).group("Entities"));
        }

        @Override
        public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
            if (MC.level == null || pos == null) return matchesRegistry("", list("block-mine"), choice("block-mine-mode"));
            String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock()).toString();
            return matchesRegistry(id, list("block-mine"), choice("block-mine-mode"));
        }

        @Override
        public boolean shouldCancelAttack(HitResult hitResult) {
            if (!(hitResult instanceof EntityHitResult entityHit)) return false;
            Entity entity = entityHit.getEntity();
            if (!entityFilterAllowed(entity)) return false;
            return matchesRegistry(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), list("entity-hit"), choice("entity-hit-mode"));
        }

        @Override
        public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
            if (hitResult instanceof BlockHitResult blockHit) {
                if (!matchesHand(choice("block-interact-hand"), hand)) return false;
                if (MC.level == null) return matchesRegistry("", list("block-interact"), choice("block-interact-mode"));
                String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(blockHit.getBlockPos()).getBlock()).toString();
                return matchesRegistry(id, list("block-interact"), choice("block-interact-mode"));
            }
            if (hitResult instanceof EntityHitResult entityHit) {
                if (!matchesHand(choice("entity-interact-hand"), hand)) return false;
                if (!entityFilterAllowed(entityHit.getEntity())) return false;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType()).toString();
                return matchesRegistry(id, list("entity-interact"), choice("entity-interact-mode"));
            }
            return false;
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (packet instanceof ServerboundUseItemOnPacket use) {
                if (!matchesHand(choice("block-interact-hand"), use.getHand())) return false;
                if (MC.level == null) return matchesRegistry("", list("block-interact"), choice("block-interact-mode"));
                String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(use.getHitResult().getBlockPos()).getBlock()).toString();
                return matchesRegistry(id, list("block-interact"), choice("block-interact-mode"));
            }
            if (packet instanceof ServerboundPlayerActionPacket action && action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                return shouldCancelStartBreakingBlock(action.getPos(), action.getDirection());
            }
            if (packet instanceof ServerboundInteractPacket interact) {
                if (!matchesHand(choice("entity-interact-hand"), interact.hand())) return false;
                if (MC.level == null) return false;
                Entity entity = MC.level.getEntity(interact.entityId());
                if (entity == null || !entityFilterAllowed(entity)) return false;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                return matchesRegistry(id, list("entity-interact"), choice("entity-interact-mode"));
            }
            return false;
        }

        private boolean matchesHand(String mode, InteractionHand hand) {
            if ("None".equals(mode)) return false;
            if ("Both".equals(mode)) return true;
            return ("Mainhand".equals(mode) && hand == InteractionHand.MAIN_HAND) || ("Offhand".equals(mode) && hand == InteractionHand.OFF_HAND);
        }

        private boolean matchesRegistry(String id, List<String> entries, String mode) {
            return shouldCancelRegistryTarget(id, entries, mode);
        }

        static boolean shouldCancelRegistryTarget(String id, List<String> entries, String mode) {
            boolean whitelist = "WhiteList".equalsIgnoreCase(mode);
            if (entries == null || entries.isEmpty()) return false;
            boolean listed = false;
            for (String entry : entries) {
                if (RegistryListCodec.matches(id, entry)) {
                    listed = true;
                    break;
                }
            }
            return whitelist ? !listed : listed;
        }

        private boolean entityFilterAllowed(Entity entity) {
            if (entity == null) return false;
            if (!bool("nametagged") && entity.hasCustomName()) return false;
            if (!bool("babies") && entity instanceof LivingEntity living && living.isBaby()) return false;
            return true;
        }
    }

    static final class OffhandInteractModule extends Module {
        private boolean sendingSynthetic;

        OffhandInteractModule() {
            super("offhand-interact-placement", "OffhandInteract", ModuleCategory.PLAYER, "Uses the offhand too.");
            add(new BoolSetting("cancelMainhand", "Cancel Mainhand", false));
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (sendingSynthetic || MC.gameMode == null || MC.player == null) return false;
            if (packet instanceof ServerboundUseItemOnPacket use && use.getHand() == InteractionHand.MAIN_HAND) {

                if (AutismBlinkManager.holdsActionsWithoutMovement()) return false;

                if (AutismHandArbiter.offhandClaimedByOther(id())) return false;

                if (AutoTotemModule.operationActive()) return false;

                if (ModuleRegistry.shouldCancelUseExcept(use.getHitResult(), InteractionHand.OFF_HAND, id())) return false;

                String placementOwner = AutismPlacementTick.owner();
                if (placementOwner != null && !placementOwner.equals(id())) return false;

                if (!AutismPlacementTick.claim(id())) return false;
                sendingSynthetic = true;
                try {

                    MC.gameMode.useItemOn(MC.player, InteractionHand.OFF_HAND, use.getHitResult());
                } finally {
                    sendingSynthetic = false;
                }
                return bool("cancelMainhand");
            }
            return false;
        }
    }

    static final class SpamModule extends Module {
        private int timer;
        private int index;

        SpamModule() {
            super("spam", "Spam", ModuleCategory.MISC, "Sends timed chat.");
            add(new StringListSetting("messages", "Messages", "你好，世界！").description("Messages separated by |."));
            add(new IntSetting("delay", "Delay", 20, 0, 200, 1));
            add(new BoolSetting("disable-on-leave", "Disable On Leave", true).group("Safety"));
            add(new BoolSetting("disable-on-disconnect", "Disable On Disconnect", true).group("Safety"));
            add(new BoolSetting("randomise", "Randomise", false));
            add(new BoolSetting("auto-split-messages", "Auto Split", false).group("Splitting"));
            add(new IntSetting("split-length", "Split Length", 256, 8, 256, 8).group("Splitting").visibleWhen(() -> Boolean.parseBoolean(value("auto-split-messages"))).build());
            add(new IntSetting("split-delay", "Split Delay", 20, 0, 200, 1).group("Splitting").visibleWhen(() -> Boolean.parseBoolean(value("auto-split-messages"))).build());
            add(new BoolSetting("bypass", "Bypass", false).group("Bypass"));
            add(new BoolSetting("include-uppercase-characters", "Uppercase", false).group("Bypass").visibleWhen(() -> Boolean.parseBoolean(value("bypass"))).build());
            add(new IntSetting("length", "Bypass Length", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> Boolean.parseBoolean(value("bypass"))).build());
            add(new BoolSetting("uppercase", "Uppercase Legacy", false).group("Bypass").visibleWhen(() -> false).build());
            add(new IntSetting("bypass-length", "Bypass Length Legacy", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> false).build());
        }

        @Override
        public void onEnable() {
            timer = integer("delay");
            index = 0;
        }

        @Override
        public void tick() {
            if (MC.getConnection() == null) return;
            if (timer-- > 0) return;
            timer = integer("delay");
            List<String> messages = splitMessages();
            if (messages.isEmpty()) return;
            int selected = bool("randomise") ? new Random().nextInt(messages.size()) : index++ % messages.size();
            String msg = messages.get(selected);
            if (bool("bypass")) msg += " " + bypassToken();
            if (bool("auto-split-messages") && msg.length() > integer("split-length")) {
                int split = Math.max(1, integer("split-length"));
                msg = msg.substring(0, split);
                timer = integer("split-delay");
            } else if (msg.length() > 256) msg = msg.substring(0, 256);
            if (msg.startsWith("/") && msg.length() > 1) MC.getConnection().sendCommand(msg.substring(1));

            else autismclient.commands.AutismCommands.sendPlainChat(MC.getConnection(), msg);
        }

        @Override
        public void onGameLeft() {
            if (bool("disable-on-leave") || bool("disable-on-disconnect")) setEnabled(false);
        }

        private List<String> splitMessages() {
            List<String> messages = new ArrayList<>();
            for (String raw : text("messages").split("\\|")) {
                String msg = raw.trim();
                if (!msg.isEmpty()) messages.add(msg);
            }
            return messages;
        }

        private String bypassToken() {
            String chars = bool("include-uppercase-characters") || bool("uppercase") ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" : "abcdefghijklmnopqrstuvwxyz0123456789";
            Random rng = new Random();
            StringBuilder out = new StringBuilder();
            int length = integer("length") != 4 || integer("bypass-length") == 4 ? integer("length") : integer("bypass-length");
            for (int i = 0; i < length; i++) out.append(chars.charAt(rng.nextInt(chars.length())));
            return out.toString();
        }
    }

    static final class BookBotModule extends Module {
        private int timer;
        private int count;
        private final Random random = new Random();

        BookBotModule() {
            super("bookbot", "BookBot", ModuleCategory.MISC, "Writes into books.");
            add(new ChoiceSetting("mode", "Mode", "Random", "Random", "File").description("Random or file text.").build());
            add(new ChoiceSetting("random-type", "Random Type", "Utf8", "Utf8", "Ascii", "PaperMC").visibleWhen(() -> "Random".equals(value("mode"))).build());
            add(new IntSetting("pages", "Pages", 100, 1, 100, 1).visibleWhen(() -> "Random".equals(value("mode")) && !"PaperMC".equals(value("random-type"))).build());
            add(new IntSetting("characters", "Chars/Page", AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE, 1, 1024, 16).visibleWhen(() -> "Random".equals(value("mode")) && !"PaperMC".equals(value("random-type"))).build());
            add(new IntSetting("delay", "Delay", 20, 1, 200, 1));
            add(new BoolSetting("sign", "Sign", true));
            add(new StringSetting("name", "Name", "Autism").visibleWhen(() -> Boolean.parseBoolean(value("sign"))).build());
            add(new BoolSetting("append-count", "Append Count", true).visibleWhen(() -> Boolean.parseBoolean(value("sign"))).build());
            add(new BoolSetting("word-wrap", "Word Wrap", true).visibleWhen(() -> "File".equals(value("mode"))).build());
            add(new StringSetting("file-path", "File", "").visibleWhen(() -> "File".equals(value("mode"))).formatter(BookBotModule::displayFileName).description("Selected file.").filePicker("pick-file"));
            add(new ActionSetting("pick-file", "Pick File", this::pickFile).availableOffline().visibleWhen(() -> false).description("Choose text file.").build());
            add(new StringSetting("text", "Text", "").visibleWhen(() -> false).build());
            add(new BoolSetting("full-default-migrated", "Full Default Migrated", false).visibleWhen(() -> false).build());
        }

        @Override
        public void onEnable() {
            upgradeOldLightweightDefaults();
            timer = integer("delay");
            count = 0;
        }

        @Override
        public void tick() {
            if (MC.player == null || MC.getConnection() == null) return;
            if ("File".equals(choice("mode"))) {
                String fileFailure = fileSelectionFailureMessage();
                if (fileFailure != null) {
                    disableWithToggleMessage(fileFailure);
                    return;
                }
            }
            if (timer-- > 0) return;
            timer = integer("delay");
            if (!prepareWritableBookInHand()) {
                disableWithToggleMessage("BookBot disabled: no empty writable book.");
                return;
            }
            ItemStack stack = MC.player.getMainHandItem();
            List<String> pages = buildPages();
            List<Filterable<Component>> filtered = new ArrayList<>();
            for (String page : pages) filtered.add(Filterable.passThrough(Component.literal(page)));
            String title = text("name").isBlank() ? "Autism" : text("name");
            if (bool("append-count") && count > 0) title += " #" + count;
            if (bool("sign")) {
                stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), MC.player.getGameProfile().name(), 0, filtered, true));
            } else {
                List<Filterable<String>> writablePages = new ArrayList<>();
                for (String page : pages) writablePages.add(Filterable.passThrough(page));
                stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(writablePages));
            }
            MC.getConnection().send(new ServerboundEditBookPacket(MC.player.getInventory().getSelectedSlot(), pages, bool("sign") ? Optional.of(title) : Optional.empty()));
            count++;
        }

        private boolean prepareWritableBookInHand() {
            if (MC.player == null) return false;
            if (isEmptyWritableBook(MC.player.getMainHandItem())) return true;
            int slot = findEmptyWritableBookSlot();
            if (slot < 0) return false;
            int selected = MC.player.getInventory().getSelectedSlot();
            if (slot > 8) {
                AutismInventoryHelper.swapInventorySlots(MC, slot, selected);
                return true;
            }
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            return true;
        }

        private int findEmptyWritableBookSlot() {
            if (MC.player == null) return -1;
            for (int i = 0; i < MC.player.getInventory().getContainerSize(); i++) {
                if (isEmptyWritableBook(MC.player.getInventory().getItem(i))) return i;
            }
            return -1;
        }

        private boolean isEmptyWritableBook(ItemStack stack) {
            if (stack == null || !stack.is(Items.WRITABLE_BOOK)) return false;
            WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return content == null || content.pages().isEmpty();
        }

        private List<String> buildPages() {
            List<String> pages = new ArrayList<>();
            String custom = "File".equals(choice("mode")) ? fileText() : "";
            boolean randomMode = "Random".equals(choice("mode"));
            int pageCount = "File".equals(choice("mode")) ? 100 : Math.min(100, Math.max(1, integer("pages")));
            if (randomMode) {
                return AutismBookPayloadBuilder.randomPages(pageCount, integer("characters"), choice("random-type"), random);
            }
            int chars = integer("characters");
            if (!custom.isBlank() && bool("word-wrap")) {

                pages.addAll(NbtBookAction.splitTextIntoPages(MC.font, custom, pageCount));
                return pages.isEmpty() ? List.of("Autism") : pages;
            }
            pages.addAll(NbtBookAction.chunkTextIntoPages(custom, pageCount, chars));
            return pages.isEmpty() ? List.of("Autism") : pages;
        }

        private void upgradeOldLightweightDefaults() {
            if (!"Random".equals(choice("mode"))) return;
            if (bool("full-default-migrated")) return;
            if (integer("pages") == 50) setValue("pages", "100");
            if (integer("characters") == 128 || integer("characters") == 512) setValue("characters", Integer.toString(AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE));
            if ("Ascii".equals(choice("random-type"))) {
                setValue("random-type", "Utf8");
            }
            setValue("full-default-migrated", "true");
        }

        private String fileText() {
            String path = text("file-path").trim();
            if (path.isBlank()) return "";
            try {
                return AutismBookFileReader.read(Path.of(path));
            } catch (Exception ex) {
                AutismClientMessaging.sendPrefixed("BookBot file read failed: " + ex.getMessage());
                return "";
            }
        }

        private void pickFile() {
            String current = text("file-path").trim();
            PointerBuffer filters = BufferUtils.createPointerBuffer(4);
            java.nio.ByteBuffer txt = MemoryUtil.memASCII("*.txt");
            java.nio.ByteBuffer md = MemoryUtil.memASCII("*.md");
            java.nio.ByteBuffer json = MemoryUtil.memASCII("*.json");
            java.nio.ByteBuffer nbtTxt = MemoryUtil.memASCII("*.nbt.txt");
            filters.put(txt).put(md).put(json).put(nbtTxt).rewind();
            try {
                String selected = TinyFileDialogs.tinyfd_openFileDialog("BookBot Text File", current.isBlank() ? null : current, filters, "Text files", false);
                if (selected != null && !selected.isBlank()) setValue("file-path", selected);
            } finally {
                MemoryUtil.memFree(txt);
                MemoryUtil.memFree(md);
                MemoryUtil.memFree(json);
                MemoryUtil.memFree(nbtTxt);
            }
        }

        private String fileSelectionFailureMessage() {
            String path = text("file-path").trim();
            if (path.isBlank()) {
                return "BookBot disabled: choose a file first.";
            }
            try {
                Path file = Path.of(path);
                if (!Files.isRegularFile(file)) {
                    return "BookBot disabled: file missing.";
                }
                if (Files.size(file) <= 0) {
                    return "BookBot disabled: file is empty.";
                }
                return null;
            } catch (Exception ex) {
                return "BookBot disabled: invalid file.";
            }
        }

        private static String displayFileName(String value) {
            if (value == null || value.isBlank()) return "No file selected";
            try {
                Path name = Paths.get(value).getFileName();
                return name == null ? value : name.toString();
            } catch (Exception ignored) {
                return value;
            }
        }

    }

    static final class PacketCancellerModule extends Module {
        private String cachedC2SValue = null;
        private String cachedS2CValue = null;
        private Set<Class<? extends Packet<?>>> cachedC2S = Set.of();
        private Set<Class<? extends Packet<?>>> cachedS2C = Set.of();

        PacketCancellerModule() {
            super("packet-canceller", "PacketCanceller", ModuleCategory.MISC, "Cancels selected packets.");
            add(new PacketListSetting("C2S-packets", "C2S Packets", "").description("Outgoing packet list."));
            add(new PacketListSetting("S2C-packets", "S2C Packets", "").description("Incoming packet list."));
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            return matches(packet, true);
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            return matches(packet, false);
        }

        @Override
        public String info() {
            return "C2S " + packets(true).size() + " | S2C " + packets(false).size();
        }

        private boolean matches(Packet<?> packet, boolean c2s) {
            if (packet == null) return false;
            if (packets(c2s).contains(packet.getClass())) return true;
            return legacyMatches(c2s ? text("C2S-packets") : text("S2C-packets"), packet);
        }

        private Set<Class<? extends Packet<?>>> packets(boolean c2s) {
            String value = c2s ? text("C2S-packets") : text("S2C-packets");
            if (c2s) {
                if (!value.equals(cachedC2SValue)) {
                    cachedC2SValue = value;
                    cachedC2S = PacketListCodec.resolvePackets(value, true);
                }
                return cachedC2S;
            }
            if (!value.equals(cachedS2CValue)) {
                cachedS2CValue = value;
                cachedS2C = PacketListCodec.resolvePackets(value, false);
            }
            return cachedS2C;
        }

        private boolean legacyMatches(String list, Packet<?> packet) {
            if (packet == null || list == null || list.isBlank()) return false;
            String simple = packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String full = packet.getClass().getName().toLowerCase(Locale.ROOT);
            @SuppressWarnings("unchecked")
            Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();
            String registry = String.valueOf(AutismPacketRegistry.getName(packetClass)).toLowerCase(Locale.ROOT);
            for (String token : list.split("[,|]")) {
                String needle = token.trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty() && (simple.equals(needle) || full.equals(needle) || registry.equals(needle))) return true;
            }
            return false;
        }
    }

    static final class AutoReconnectModule extends Module {
        AutoReconnectModule() {
            super("auto-reconnect", "AutoReconnect", ModuleCategory.MISC, "Reconnects after disconnect.");
            add(new DoubleSetting("delay", "Delay Sec", 3.5, 0.0, 60.0, 0.5));
        }
    }

    static final class BetterTooltipsModule extends Module {
        private ItemStack tooltipSizeCacheStack = ItemStack.EMPTY;
        private int tooltipSizeCacheBytes = -1;

        BetterTooltipsModule() {
            super("better-tooltips", "BetterTooltips", ModuleCategory.RENDER, "Adds item tooltip info.");
            boolean firstInstall = !AutismConfig.getGlobal().modules.containsKey("better-tooltips");
            add(new StringSetting("nbt-shortcut", "NBT Shortcut", "Ctrl+Shift+Right-click").group("Info").readonlySummary());
            add(new BoolSetting("block-nbt-inspect", "Block NBT Inspect", false).group("Info").description("Ctrl+Shift+Right-click blocks.").build());
            add(new BoolSetting("open-contents", "Open Contents", true).group("Containers").description("Preview only.").visibleWhen(() -> false).build());
            add(new BoolSetting("containers", "Containers", true).group("Containers"));
            add(new BoolSetting("compact-shulker-tooltip", "Compact Shulker", true).group("Containers").description("Compact counts.").build());
            add(new BoolSetting("echests", "Ender Chests", true).group("Containers").description("Server-side data.").visibleWhen(() -> false).build());
            add(new BoolSetting("maps", "Maps", true).group("Items"));
            add(new DoubleSetting("map-scale", "Map Scale", 1.0, 0.5, 4.0, 0.1).group("Items").description("Map preview scale.").visibleWhen(() -> false).build());
            add(new BoolSetting("books", "Books", true).group("Items"));
            add(new BoolSetting("banners", "Banners", true).group("Items"));
            add(new BoolSetting("buckets", "Buckets", true).group("Items"));
            add(new BoolSetting("bundles", "Bundles", true).group("Items"));
            add(new BoolSetting("minecraft-name", "Minecraft Name", true).group("Info"));
            add(new BoolSetting("durability", "Durability", true).group("Info"));
            add(new BoolSetting("food-info", "Food Info", true).group("Info"));
            add(new BoolSetting("byte-size", "Byte Size", true).group("Info"));
            add(new ChoiceSetting("byte-size-format", "Byte Format", "Both", "Raw", "Rounded", "Both").group("Info").build());
            if (firstInstall) setEnabledSilently(true);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public void appendTooltip(ItemStack stack, List<?> lines) {
            if (stack == null || stack.isEmpty()) return;
            List raw = lines;
            raw.add(Component.literal("NBT: Ctrl+Shift+Right-click").withStyle(ChatFormatting.DARK_GRAY));
            if (bool("minecraft-name")) raw.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("durability") && stack.isDamageableItem()) {
                int maxDamage = Math.max(1, stack.getMaxDamage());
                int remaining = Math.max(0, maxDamage - stack.getDamageValue());
                raw.add(Component.literal("Durability: " + remaining + " / " + maxDamage).withStyle(ChatFormatting.GRAY));
            }
            if (bool("food-info") && stack.has(DataComponents.FOOD)) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null) raw.add(Component.literal("Food: " + food.nutrition() + " / " + String.format(Locale.ROOT, "%.1f", food.saturation())).withStyle(ChatFormatting.GRAY));
            }
            if (bool("containers") && stack.has(DataComponents.CONTAINER)) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    long stacks = contents.nonEmptyItemCopyStream().count();
                    raw.add(Component.literal("Container: " + stacks + " stacks").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            if (bool("bundles") && stack.has(DataComponents.BUNDLE_CONTENTS)) {
                BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
                if (contents != null) raw.add(Component.literal("Bundle: " + contents.size() + " stacks").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (bool("maps") && stack.has(DataComponents.MAP_ID)) raw.add(Component.literal("Map: " + stack.get(DataComponents.MAP_ID)).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("banners") && stack.has(DataComponents.BANNER_PATTERNS)) raw.add(Component.literal("Banner patterns: " + stack.get(DataComponents.BANNER_PATTERNS)).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("buckets") && stack.has(DataComponents.BUCKET_ENTITY_DATA)) raw.add(Component.literal("Bucket entity data").withStyle(ChatFormatting.DARK_GRAY));
            if (bool("books") && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null) raw.add(Component.literal("Pages: " + content.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
            } else if (bool("books") && stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
                WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
                if (content != null) raw.add(Component.literal("Pages: " + content.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (bool("byte-size")) {
                int bytes = getTooltipSizeBytes(stack);
                if (bytes >= 0) raw.add(Component.literal("Bytes: " + formatBytes(bytes)).withStyle(ChatFormatting.DARK_GRAY));
                else raw.add(Component.literal("Bytes: unreadable (protected)").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        private String formatBytes(int bytes) {
            String mode = choice("byte-size-format");
            double kib = bytes / 1024.0;
            return switch (mode) {
                case "Raw" -> Integer.toString(bytes);
                case "Rounded" -> String.format(Locale.ROOT, "%.1f KiB", kib);
                default -> bytes + " (" + String.format(Locale.ROOT, "%.1f KiB", kib) + ")";
            };
        }

        private int getTooltipSizeBytes(ItemStack stack) {
            if (!tooltipSizeCacheStack.isEmpty()
                && tooltipSizeCacheStack.getCount() == stack.getCount()
                && ItemStack.isSameItemSameComponents(tooltipSizeCacheStack, stack)) {
                return tooltipSizeCacheBytes;
            }

            if (MC == null || MC.player == null) return -1;

            int bytes = AutismItemNbtSanity.encodedSizeBytes(stack, MC.player.registryAccess());
            tooltipSizeCacheStack = stack.copy();
            tooltipSizeCacheBytes = bytes;
            return bytes;
        }
    }

    static final class FullbrightModule extends Module {
        private String lastMode = "";
        private String lastLightType = "";
        private int lastMinimumLightLevel = Integer.MIN_VALUE;
        private boolean appliedNightVision;

        FullbrightModule() {
            super("fullbright", "Fullbright", ModuleCategory.RENDER, "Brightens the world.");
            boolean firstInstall = !AutismConfig.getGlobal().modules.containsKey("fullbright");
            add(new ChoiceSetting("mode", "Mode", "Gamma", "Gamma", "Potion", "Luminance").description("How lighting is boosted.").build());
            add(new ChoiceSetting("light-type", "Light Type", "BLOCK", "SKY", "BLOCK").visibleWhen(() -> "Luminance".equals(value("mode"))).build());
            add(new IntSetting("minimum-light-level", "Min Light", 8, 0, 15, 1).visibleWhen(() -> "Luminance".equals(value("mode"))).build());
            if (firstInstall) setEnabledSilently(true);
        }

        @Override
        public void onEnable() {
            ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onDisable() {
            disableNightVision();
            appliedNightVision = false;
            ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void tick() {
            String mode = choice("mode");
            String lightType = choice("light-type");
            int minimumLightLevel = integer("minimum-light-level");
            boolean changed = !mode.equals(lastMode)
                || !lightType.equals(lastLightType)
                || minimumLightLevel != lastMinimumLightLevel;
            if (changed) {
                lastMode = mode;
                lastLightType = lightType;
                lastMinimumLightLevel = minimumLightLevel;
                if (!"Potion".equals(mode) && appliedNightVision) {
                    disableNightVision();
                    appliedNightVision = false;
                }
                ModuleRenderUtil.refreshWorldRenderer();
            }
            if ("Potion".equals(mode)) applyNightVision();
        }

        public boolean gammaActive() {
            return isEnabled() && "Gamma".equals(choice("mode"));
        }

        public int luminance(String lightType) {
            if (!isEnabled() || !"Luminance".equals(choice("mode")) || !choice("light-type").equals(lightType)) return 0;
            return integer("minimum-light-level");
        }

        private void applyNightVision() {
            if (MC.player == null) return;
            MobEffectInstance instance = MC.player.getEffect(MobEffects.NIGHT_VISION);
            if (instance != null) {
                if (instance.getDuration() < 420) ((AutismMobEffectInstanceAccessor) instance).autism$setDuration(420);
            } else {
                MC.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0));
            }
            appliedNightVision = true;
        }

        private void disableNightVision() {
            if (MC.player != null && MC.player.hasEffect(MobEffects.NIGHT_VISION)) MC.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    static final class XrayModule extends Module {
        private String lastTintKey = "";

        XrayModule() {
            super("xray", "Xray", ModuleCategory.RENDER, "Shows selected blocks.");

            add(new ChoiceSetting("mode", "Mode", "Normal", "Normal", "OreSim")
                .description("Real blocks or seed.").build());
            add(new ChoiceSetting("render-style", "Rendering Style", "Default", "Default", "ESP")
                .description("See-through or boxes.").build());
            add(RegistryListSetting.blocks("whitelist", "Whitelist", defaultOreList())
                .description("Blocks to show.").visibleWhen(() -> !oreSimMode()).build());

            add(RegistryListSetting.oreSimOres("oresim-ores", "Ores",
                    String.join("|", AutismOreSimOre.ORE_SIM_BLOCK_IDS))
                .description("Ores to predict.").visibleWhen(this::oreSimMode).build());

            add(new IntSetting("opacity", "Opacity", 25, 0, 255, 1)
                .description("Hidden block alpha.").visibleWhen(this::tintStyle).build());
            add(new ChoiceSetting("fluid-opacity", "Fluid Opacity", "Both", "None", "Water", "Lava", "Both")
                .description("Visible fluids.").visibleWhen(this::tintStyle).build());
            add(new BoolSetting("exposed-only", "Exposed Only", false)
                .description("Only exposed ores.").visibleWhen(() -> tintStyle() && !oreSimMode()).build());
            add(new BoolSetting("fill", "Fill", true)
                .description("Shade box interiors.").visibleWhen(this::espStyle).build());

            add(new StringSetting("oresim-seed", "World Seed", "")
                .description("Seed for this world")
                .keepOnReset()
                .requiresWorld().visibleWhen(this::oreSimMode).build());
            add(new IntSetting("oresim-radius", "Simulation Radius", 2, 1,
                    AutismOreSimEngine.MAX_SIM_RADIUS, 1)
                .description("Chunk radius to simulate")
                .visibleWhen(this::oreSimMode).build());

            for (AutismOreSimOre.Kind kind : AutismOreSimOre.Kind.values()) {
                add(new ColorSetting(kind.colorId(), kind.label, kind.defaultColor).group("Colors")
                    .visibleWhen(() -> espStyle() && ModuleOreSim.whitelistHasFamily(this, kind)).build());
            }
            syncCustomColorSettings();
        }

        private boolean tintStyle() {
            return !espStyle();
        }

        private boolean espStyle() {
            return "ESP".equals(value("render-style"));
        }

        private boolean oreSimMode() {
            return "OreSim".equals(value("mode"));
        }

        @Override
        protected String externalSettingValue(String settingId) {
            if (!"oresim-seed".equals(settingId)) return null;
            AutismConfig.ModuleState configured = AutismConfig.getGlobal().modules.get(id());
            String legacy = configured == null || configured.settings == null
                ? "" : configured.settings.getOrDefault(settingId, "");
            return AutismOreSimSeedStore.get().value(AutismWaypoints.scopeKey(MC), legacy);
        }

        @Override
        protected boolean setExternalSettingValue(String settingId, String value) {
            if (!"oresim-seed".equals(settingId)) return false;

            AutismOreSimSeedStore.get().put(AutismWaypoints.scopeKey(MC), value);
            return true;
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("whitelist".equals(settingId)) syncCustomColorSettings();
            if ("oresim-seed".equals(settingId)) AutismOreSimEngine.clear();
        }

        private void syncCustomColorSettings() {
            for (String id : ModuleOreSim.nonOreWhitelistIds(this)) {
                String key = "block-color-" + id;
                if (setting(key) != null) continue;
                String label = autismclient.util.AutismRegistryLabels.block(id);
                add(new ColorSetting(key, label.isBlank() ? id : label, ModuleOreSim.OTHER_COLOR).group("Colors")
                    .visibleWhen(() -> espStyle() && !oreSimMode() && ModuleOreSim.whitelistHasId(this, id)).build());
            }
        }

        @Override
        public void onEnable() {
            if (tintStyle()) ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onDisable() {

            AutismOreSimEngine.suspend();
            if (tintStyle()) ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onGameLeft() {

            AutismOreSimEngine.clear();
            AutismOreSimEngine.forgetDisproven();
            AutismOreGhostModels.clear();
        }

        @Override
        public void tick() {

            String tintKey = value("mode") + '|' + value("render-style") + '|' + integer("opacity")
                + '|' + value("fluid-opacity") + '|' + bool("exposed-only")
                + '|' + (oreSimMode() ? "" : value("whitelist"));
            if (!tintKey.equals(lastTintKey)) {
                lastTintKey = tintKey;
                ModuleRenderUtil.refreshWorldRenderer();
            }
            ModuleOreSim.tick(this, MC.level, MC.player);
        }

        @Override
        public String info() {
            return ModuleOreSim.info(this);
        }

        private static String defaultOreList() {
            return String.join("|", AutismOreSimOre.ORE_SIM_BLOCK_IDS);
        }
    }

    static final class FreecamModule extends Module {
        FreecamModule() {
            super("freecam", "Freecam", ModuleCategory.RENDER, "Move the camera away from the player.");
            add(new DoubleSetting("speed", "Speed", 1.0, 0.05, 30.0, 0.05).description("Horizontal camera speed."));
            add(new DoubleSetting("vertical-speed", "Vertical Speed", 1.0, 0.05, 30.0, 0.05).description("Vertical camera speed."));
            add(new BoolSetting("reload-chunks", "Reload Chunks", true).description("Refresh chunk culling."));
            add(new BoolSetting("interact", "Interact", true).description("Interact from freecam view"));
        }

        @Override
        public void onEnable() {
            PackFreecamState.enable(bool("reload-chunks"));
            PackFreecamState.setInteractEnabled(bool("interact"));
        }

        @Override
        public void onDisable() {
            PackFreecamState.disable();
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("interact".equals(settingId)) PackFreecamState.setInteractEnabled(bool("interact"));
        }

        @Override
        public void onGameLeft() {
            setEnabledSilently(false);
        }

        @Override
        public void tick() {

            if (MC.player != null && MC.player.isDeadOrDying()) {
                disableWithToggleMessage("Freecam disabled: you died.");
            }
        }

        @Override
        public void preMovementTick() {
            PackFreecamState.tickMovement(decimal("speed"), decimal("vertical-speed"));
        }

        @Override
        public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
            return PackFreecamState.onPlayerMove(type, movement);
        }

        @Override
        public String info() {
            return String.format(Locale.ROOT, "%.2f", decimal("speed"));
        }
    }

    static final class EspModule extends Module {
        EspModule() {
            super("esp", "ESP", ModuleCategory.RENDER, "Highlights entities.");
            add(new ChoiceSetting("mode", "Mode", "2D", "Shader", "2D").description("Entity highlight style.").build());
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").group("General").build());
            add(new BoolSetting("fill", "Fill", false).group("General")
                .visibleWhen(() -> "2D".equals(choice("mode"))).build());
            add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General"));
            add(new ColorSetting("players-color", "Players", 0xCCFFFFFF).group("Colors"));
            add(new ColorSetting("animals-color", "Animals", 0xCC74FF8F).group("Colors"));
            add(new ColorSetting("water-animals-color", "Water Animals", 0xCC66D9FF).group("Colors"));
            add(new ColorSetting("monsters-color", "Monsters", 0xCCFF4A4A).group("Colors"));
            add(new ColorSetting("ambient-color", "Ambient", 0xCCB78CFF).group("Colors"));
            add(new ColorSetting("misc-color", "Misc", 0xCCCCCCCC).group("Colors"));

            add(new BoolSetting("skeleton", "Skeleton", false).group("Skeleton")
                .description("Draw animated skeletons.").build());
            add(new ChoiceSetting("skeleton-color-mode", "Color Mode", "Static", "Static", "Distance")
                .group("Skeleton").visibleWhen(() -> bool("skeleton")).description("Skeleton color source.").build());
            add(new ColorSetting("skeleton-color", "Color", 0xFFFFFFFF).group("Skeleton")
                .visibleWhen(() -> bool("skeleton") && "Static".equals(value("skeleton-color-mode"))));
            add(new DoubleSetting("skeleton-width", "Line Width", 2.0, 1.0, 6.0, 0.25).group("Skeleton")
                .visibleWhen(() -> bool("skeleton")).description("Skeleton line thickness.").build());
        }
    }

    static final class ItemEspModule extends Module {
        ItemEspModule() {
            super("item-esp", "ItemESP", ModuleCategory.RENDER, "Outlines dropped items with the shader outline.");
            add(new ChoiceSetting("mode", "Mode", "Shader", "Shader").description("Dropped item highlight style.").build());
            add(new ChoiceSetting("items-mode", "Items", "All", "All", "Some").group("General").description("Which items to outline").build());
            add(RegistryListSetting.items("items", "Item List", "")
                .group("General")
                .visibleWhen(() -> "Some".equals(value("items-mode")))
                .description("Item ids to outline")
                .build());
            add(new DoubleSetting("max-distance", "Max Distance", 64.0, 0.0, 256.0, 1.0)
                .group("General")
                .description("0 = unlimited.")
                .build());
            add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General"));
            add(new ChoiceSetting("color-mode", "Color Mode", "Dynamic", "Dynamic", "Static")
                .group("Colors")
                .description("Color mode")
                .build());
            add(new ColorSetting("color", "Color", 0xCCFFD76A)
                .group("Colors")
                .visibleWhen(() -> "Static".equals(value("color-mode"))));
        }
    }

    static final class TracersModule extends Module {
        TracersModule() {
            super("tracers", "Tracers", ModuleCategory.RENDER, "Draws tracer lines to entities.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Traced entities.").build());
            add(new IntSetting("max-distance", "Max Distance", 256, 0, 512, 16));
            add(new DoubleSetting("line-width", "Line Width", 2.0, 2.0, 6.0, 0.25).group("General").description("Tracer thickness."));
            add(new BoolSetting("height-line", "Height Line", false).group("General").description("Vertical line up the entity."));
            add(new BoolSetting("distance-color", "Distance Color", false).group("Colors").description("Color by distance"));
            add(new IntSetting("color-distance", "Color Distance", 40, 8, 256, 4).group("Colors")
                .description("Far color distance").visibleWhen(() -> bool("distance-color")));
            add(new ColorSetting("players-color", "Players", 0xCCFFFFFF).group("Colors").visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("animals-color", "Animals", 0xCC74FF8F).group("Colors").visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("water-animals-color", "Water Animals", 0xCC66D9FF).group("Colors").visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("monsters-color", "Monsters", 0xCCFF4A4A).group("Colors").visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("ambient-color", "Ambient", 0xCCB78CFF).group("Colors").visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("misc-color", "Misc", 0xCCCCCCCC).group("Colors").visibleWhen(() -> !bool("distance-color")));
        }

        @Override
        public boolean shouldTraceEntity(Entity entity) {
            return ModuleRenderUtil.shouldTrace(entity);
        }

        @Override
        public int traceColor(Entity entity) {
            return ModuleRenderUtil.tracerColor(entity);
        }
    }

    static final class StorageEspModule extends Module {
        StorageEspModule() {
            super("storage-esp", "StorageESP", ModuleCategory.RENDER, "Highlights chests, shulkers, and other storage.");

            add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
            add(new BoolSetting("meshing", "Meshing", true).group("General")
                .description("Merge adjacent storage."));

            add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each target"));

            add(RegistryListSetting.storages("storage-list", "Storage", ModuleStorageEsp.DEFAULT_VALUE)
                .group("Storage").description("Blocks and entities"));

            add(new ColorSetting("chest-color", "Chest", 0xCCFFA000).group("Colors").description("Chest + chest minecart/boat."));
            add(new ColorSetting("trapped-chest-color", "Trapped Chest", 0xCCFF2020).group("Colors"));
            add(new ColorSetting("ender-chest-color", "Ender Chest", 0xCC7800FF).group("Colors"));
            add(new ColorSetting("barrel-color", "Barrel", 0xCCFFA000).group("Colors"));
            add(new ColorSetting("shulker-color", "Shulker Box", 0xCCB766FF).group("Colors"));
            add(new ColorSetting("hopper-color", "Hopper", 0xCC7C8AFF).group("Colors").description("Hopper + minecart."));
            add(new ColorSetting("dispenser-color", "Dispenser", 0xCCB04848).group("Colors").description("Dispenser + dropper."));
            add(new ColorSetting("furnace-color", "Furnace", 0xCCCCB266).group("Colors").description("Furnaces + brewing stand."));
            add(new ColorSetting("crafter-color", "Crafter", 0xCCD8AE6B).group("Colors").description("Crafter, pot, bookshelf"));
            add(new ColorSetting("other-color", "Other", 0xFF8C8C8C).group("Colors").description("Fallback."));

            for (ModuleStorageStructures.Structure structure : ModuleStorageStructures.ALL) {
                add(new BoolSetting(structure.settingId(), structure.label(), false).group("Ignore Structures"));
            }
        }
    }

    static final class SpawnerEspModule extends Module {
        SpawnerEspModule() {
            super("spawner-esp", "SpawnerESP", ModuleCategory.RENDER, "Highlights monster spawners by spawned mob.");

            add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
            add(new BoolSetting("meshing", "Meshing", true).group("General")
                .description("Merge adjacent spawners."));
            add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each spawner"));
            add(RegistryListSetting.entityTypes("spawner-list", "Spawners", ModuleSpawnerEsp.NATURAL_DEFAULT_VALUE)
                .group("Spawners").description("Mobs to highlight."));

            for (ModuleSpawnerEsp.NaturalTarget target : ModuleSpawnerEsp.NATURAL) {
                String id = target.id();
                add(new ColorSetting("color-" + id, target.label(), target.color()).group("Colors")
                    .visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, id)));
            }
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("spawner-list".equals(settingId)) syncCustomColorSettings();
        }

        private void syncCustomColorSettings() {
            for (String id : ModuleSpawnerEsp.enabledIds(this)) {
                if (ModuleSpawnerEsp.isNatural(id)) continue;
                String key = "color-" + id;
                if (setting(key) != null) continue;
                String label = autismclient.util.AutismRegistryLabels.entity(id);
                add(new ColorSetting(key, label.isBlank() ? id : label, 0xCCFF3B3B).group("Colors")
                    .visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, id)));
            }
        }
    }

    static final class BlockEspModule extends Module {
        BlockEspModule() {
            super("block-esp", "BlockESP", ModuleCategory.RENDER, "Highlights selected blocks.");
            add(new IntSetting("max-targets", "Max Blocks", 1024, 64, 8192, 64)
                .group("General")
                .description("Max rendered blocks"));
            add(new BoolSetting("fill", "Fill", false)
                .group("General")
                .description("Draw translucent filled boxes."));
            add(new BoolSetting("meshing", "Meshing", true)
                .group("General")
                .description("Merge adjacent targets."));
            add(new BoolSetting("tracers", "Tracers", false)
                .group("Tracers")
                .description("Line to each target"));
            add(RegistryListSetting.blocks("blocks", "Blocks", "minecraft:diamond_ore")
                .group("Blocks")
                .description("Blocks to highlight."));
            add(new BoolSetting("barriers", "Barriers", false)
                .group("Blocks")
                .description("Show invisible barrier blocks."));
            add(new ColorSetting("color", "Color", 0xCCFF3B3B).group("Colors"));
        }
    }

    static final class HudModule extends Module {
        HudModule() {
            super("hud", "HUD", ModuleCategory.MISC, "Shows HUD elements.");
            boolean firstInstall = !AutismConfig.getGlobal().modules.containsKey("hud");
            add(new ActionSetting("edit-hud", "Edit HUD", this::openHudEditor).availableOffline().description("Open HUD editor.").group("Editor"));
            add(new BoolSetting("editor-grid", "Editor Grid", true).description("Show editor grid.").group("Editor"));
            add(new BoolSetting("hide-in-guis", "Hide In GUIs", true).description("Hide in screens.").group("Visibility"));
            add(new BoolSetting("show-in-chat", "Show In Chat", false).description("Show in chat.").group("Visibility"));
            add(new BoolSetting("show-in-pause", "Show In Pause", false).description("Show in pause.").group("Visibility"));
            add(new BoolSetting("show-in-hud-editor", "Show In HUD Editor", true).description("Show while editing.").group("Visibility"));
            if (firstInstall) setEnabledSilently(true);
        }

        @Override
        public void onEnable() {
            AutismHudManager.ensureDefaults();
            ServerTickTracker.reset();
        }

        @Override
        public void tick() {

            AutismHudManager.tickHeartbeat();
        }

        @Override
        protected void onSettingsReset() {
            AutismHudManager.resetAllElements();
        }

        private void openHudEditor() {
            AutismHudManager.ensureDefaults();
            MC.gui.setScreen(new AutismHudEditorScreen(MC.gui.screen()));
        }
    }

    public static final class AdminToolsModule extends Module {
        private static final List<String> ITEM_EDITOR_SIGNATURE_FIELDS = List.of(
            "nbt-item-id",
            "nbt-item-count",
            "nbt-item-name",
            "nbt-item-lore",
            "nbt-unbreakable",
            "nbt-glint",
            "nbt-rarity",
            "nbt-max-damage",
            "nbt-damage",
            "nbt-max-stack",
            "nbt-enchants",
            "nbt-attributes",
            "nbt-custom-data",
            "nbt-command"
        );
        private final List<String> queuedCommands = new ArrayList<>();
        private int commandDelay;
        private int commandIndex;
        private String queuedDelayOption = "fireball-delay";
        private final Random random = new Random();
        private boolean fireballStreamEnabled;
        private boolean firestormEnabled;
        private boolean fireballStreamBindDown;
        private boolean firestormBindDown;
        private int liveFireballDelay;
        private int liveFirestormDelay;
        private int liveFirestormIndex;
        private long suppressSummonMessagesUntilMs;

        private boolean forceOpRunning;
        private Thread forceOpThread;
        private int forceOpIndex;
        private volatile boolean gotWrongPwMsg;
        private static final String[] RAW_FORCEOP_LIST = {
            "password", "passwort", "password1", "passwort1", "password123", "password1234", "passwort123", "pass", "pw", "pw1", "pw123",
            "hallo", "1122", "112233", "1234", "12345", "123456", "1234567", "12345678", "123456789", "login", "register", "test", "sicher", "me",
            "minecraft", "minecraft1", "minecraft123", "mc", "admin", "server", "tester", "account",
            "creeper", "gronkh", "lol", "auth", "authme", "qwerty", "qwertz",
            "2112", "1212", "cocacola", "xavier", "dolphin", "testing", "dragon", "baseball", "football", "letmein", "monkey", "696969",
            "abc123", "mustang", "michael", "shadow", "master", "jennifer", "111111", "2000", "jordan", "superman", "harley", "hunter", "trustno1",
            "ranger", "buster", "thomas", "tigger", "robert", "soccer", "batman", "killer", "hockey", "george", "charlie", "andrew", "michelle",
            "love", "sunshine", "jessica", "6969", "pepper", "daniel", "access", "654321", "joshua", "maggie", "starwars", "silver",
            "william", "dallas", "yankees", "123123", "ashley", "666666", "hello", "amanda", "orange", "freedom", "computer",
            "thunder", "nicole", "ginger", "heather", "hammer", "summer", "corvette", "taylor", "austin", "1111", "merlin", "matthew",
            "121212", "golfer", "cheese", "princess", "martin", "chelsea", "patrick", "richard", "diamond", "yellow", "bigdog", "secret", "asdfgh",
            "sparky", "cowboy", "camaro", "anthony", "matrix", "falcon", "iloveyou", "bailey", "guitar", "jackson", "purple", "scooter", "phoenix",
            "aaaaaa", "morgan", "tigers", "porsche", "mickey", "maverick", "cookie", "nascar", "peanut", "justin", "131313", "money",
            "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234", "111111", "1234567", "admin", "1234567890", "123123",
            "000000", "123321", "654321", "qwertyuiop", "qwertzuiop", "1q2w3e4r5t", "1qaz2wsx", "zxcvbnm", "asdfgh", "7777777", "666666",
            "555555", "888888", "123qwe", "abc123", "aa123456", "password123", "password1", "secret"
        };
        private final String[] defaultForceOpList = java.util.Arrays.stream(RAW_FORCEOP_LIST).distinct().toArray(String[]::new);
        private String[] forceOpPasswords = defaultForceOpList;

        AdminToolsModule() {
            super("admin-tools", "AdminTools", ModuleCategory.MISC, "Creative/admin helpers.");
            add(new ActionSetting("admin-status", "Show Permission Status", this::showPermissionStatus).group("Status").build());
            add(new ActionSetting("admin-stop-queue", "Stop Queued Commands", this::stopQueuedCommands).group("Status").build());
            add(new IntSetting("fireball-count", "Count", 12, 1, 256, 1).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-spread", "Spread", 8.0, 0.0, 90.0, 1.0).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-speed", "Speed", 1.8, 0.1, 10.0, 0.1).group("Fireball Storm").build());
            add(new IntSetting("fireball-power", "Power", 3, 0, 127, 1).group("Fireball Storm").build());
            add(new IntSetting("fireball-delay", "Delay", 1, 0, 20, 1).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-distance", "Spawn Dist", 2.0, 0.5, 16.0, 0.5).group("Fireball Storm").build());
            add(new ChoiceSetting("fireball-aim", "Aim", "Cone", "Look", "Cone", "Ring").group("Fireball Storm").build());
            add(new BoolSetting("fireball-randomize", "Randomize", true).group("Fireball Storm").build());
            add(new KeybindSetting("fireball-stream-bind", "Stream Bind", -1).group("Fireball Storm").build());
            add(new KeybindSetting("firestorm-bind", "Firestorm Bind", -1).group("Fireball Storm").build());
            add(new DoubleSetting("firestorm-distance", "Firestorm Dist", 8.0, 2.0, 48.0, 1.0).group("Fireball Storm").build());
            add(new DoubleSetting("firestorm-height", "Firestorm Height", 2.0, 0.0, 32.0, 1.0).group("Fireball Storm").build());
            add(new BoolSetting("fireball-stream-active", "Stream Active", false).visibleWhen(() -> false).build());
            add(new BoolSetting("firestorm-active", "Firestorm Active", false).visibleWhen(() -> false).build());
            add(new ActionSetting("fireball-preset-max", "Preset Max Storm", this::presetMaxFireballStorm).group("Fireball Storm").build());
            add(new ActionSetting("fireball-preview", "Preview Command", this::previewFireballCommand).group("Fireball Storm").build());
            add(new ActionSetting("fireball-copy", "Copy Command", this::copyFireballCommand).group("Fireball Storm").build());
            add(new ActionSetting("fireball-storm", "Run Storm", this::startFireballStorm).group("Fireball Storm").build());
            add(new IntSetting("airstrike-count", "Count", 24, 1, 256, 1).group("Airstrike").build());
            add(new DoubleSetting("airstrike-radius", "Radius", 12.0, 0.0, 96.0, 1.0).group("Airstrike").build());
            add(new DoubleSetting("airstrike-height", "Height", 48.0, 8.0, 256.0, 4.0).group("Airstrike").build());
            add(new DoubleSetting("airstrike-speed", "Fall Speed", 2.0, 0.1, 10.0, 0.1).group("Airstrike").build());
            add(new IntSetting("airstrike-power", "Power", 4, 0, 127, 1).group("Airstrike").build());
            add(new IntSetting("airstrike-delay", "Delay", 1, 0, 20, 1).group("Airstrike").build());
            add(new ChoiceSetting("airstrike-target", "Target", "Look", "Look", "Self").group("Airstrike").build());
            add(new ActionSetting("airstrike-preset-small", "Preset Tight Strike", this::presetTightAirstrike).group("Airstrike").build());
            add(new ActionSetting("airstrike-preset-max", "Preset Max Strike", this::presetMaxAirstrike).group("Airstrike").build());
            add(new ActionSetting("airstrike-run", "Run Airstrike", this::startAirstrike).group("Airstrike").build());
            add(new IntSetting("forceop-delay", "Delay (ms)", 500, 0, 5000, 50).group("ForceOP").build());
            add(new BoolSetting("forceop-wait", "Wait for Chat", true).group("ForceOP").build());
            add(new IntSetting("forceop-min-length", "Min Length", 0, 0, 32, 1).group("ForceOP").build());
            add(new IntSetting("forceop-max-length", "Max Length", 0, 0, 32, 1).group("ForceOP").build());
            add(new StringSetting("nbt-item-id", "Item ID", "minecraft:stick").group("NBT Item Editor").build());
            add(new IntSetting("nbt-item-count", "Count", 1, 1, 99, 1).group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-name", "Display Name", "Custom Item").group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-lore", "Lore Lines", "").group("NBT Item Editor").build());
            add(new BoolSetting("nbt-unbreakable", "Unbreakable", false).group("NBT Item Editor").build());
            add(new ChoiceSetting("nbt-glint", "Glint", "Default", "Default", "On", "Off").group("NBT Item Editor").build());
            add(new ChoiceSetting("nbt-rarity", "Rarity", "Default", "Default", "Common", "Uncommon", "Rare", "Epic").group("NBT Item Editor").build());
            add(new IntSetting("nbt-max-damage", "Max Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
            add(new IntSetting("nbt-damage", "Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
            add(new IntSetting("nbt-max-stack", "Max Stack", 64, 1, 99, 1).group("NBT Item Editor").build());
            add(new StringSetting("nbt-enchants", "Enchantments", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-attributes", "Attributes", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-custom-data", "Custom Data", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-command", "Embedded Command", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-components", "Raw Components", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-imported-components", "Imported Components", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-imported-signature", "Imported Signature", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-item-stack", "Full Item SNBT", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-item-stack-signature", "Full Item Signature", "").visibleWhen(() -> false).build());
            add(new ActionSetting("fill-held-item", "Fill Held", this::fillHeldItemEditor).group("NBT Item Editor").build());
            add(new ActionSetting("copy-target-item", "Copy Target", this::copyTargetItem).group("NBT Item Editor").build());
            add(new ActionSetting("apply-held-item", "Apply to Held", this::applyHeldItemEditor).group("NBT Item Editor").build());
            add(new StringSetting("fireballPower", "Fireball Power Legacy", "3").visibleWhen(() -> false).build());
        }

        @Override
        public boolean opensSettingsOnClick() {
            return true;
        }

        @Override
        public boolean hasActivationToggle() {
            return false;
        }

        @Override
        public boolean showInModuleMenu() {
            return false;
        }

        @Override
        public boolean ticksWhenDisabled() {
            return true;
        }

        @Override
        public boolean hasDisabledTickWork() {
            boolean hasFireballBind = integer("fireball-stream-bind") != -1 || integer("firestorm-bind") != -1;
            return hasFireballBind || fireballStreamEnabled || firestormEnabled || !queuedCommands.isEmpty();
        }

        @Override
        protected String displayValueOverride(autismclient.api.module.Setting<?, ?> option) {
            if (option != null && "nbt-item-components".equals(option.id())) return buildEditorComponents();
            return null;
        }

        @Override
        public void tick() {
            boolean hasFireballBind = integer("fireball-stream-bind") != -1 || integer("firestorm-bind") != -1;
            boolean fireballActive = fireballStreamEnabled || firestormEnabled;
            if (!hasFireballBind && !fireballActive && queuedCommands.isEmpty()) return;
            if (hasFireballBind || fireballActive) tickFireballBinds();
            if (fireballActive) tickLiveFireballs();
            if (queuedCommands.isEmpty()) return;
            if (commandDelay-- > 0) return;
            if (!sendAdminCommand(queuedCommands.remove(0), true)) {
                queuedCommands.clear();
                commandDelay = 0;
                commandIndex = 0;
                return;
            }
            commandDelay = integer(queuedDelayOption);
            commandIndex++;
        }

        @Override
        public void onGameLeft() {
            fireballStreamEnabled = false;
            firestormEnabled = false;
            setValue("fireball-stream-active", "false");
            setValue("firestorm-active", "false");
            fireballStreamBindDown = false;
            firestormBindDown = false;
            queuedCommands.clear();
            commandDelay = 0;
            commandIndex = 0;
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (!(packet instanceof ClientboundSystemChatPacket chatPacket)) return false;
            String text = chatPacket.content().getString();
            if (text == null) return false;
            String lower = text.toLowerCase(Locale.ROOT);

            if (forceOpRunning) {
                if (lower.contains("wrong") || lower.contains("incorrect") ||
                    lower.contains("falsch") || lower.contains("mauvais") ||
                    lower.contains("mal") || lower.contains("sbagliato")) {
                    gotWrongPwMsg = true;
                }
            }

            if (System.currentTimeMillis() > suppressSummonMessagesUntilMs && !fireballStreamEnabled && !firestormEnabled) return false;

            return lower.contains("fireball") || lower.contains("summoned");
        }

        private void showPermissionStatus() {
            AutismClientMessaging.sendPrefixed("Admin Tools: gamemaster=" + isAdminContext() + ", creative=" + isCreativeContext() + ", queued=" + queuedCommands.size() + ".");
        }

        private void stopQueuedCommands() {
            int count = queuedCommands.size();
            queuedCommands.clear();
            commandDelay = 0;
            commandIndex = 0;
            AutismClientMessaging.sendPrefixed(count == 0 ? "Admin Tools: no queued commands." : "Admin Tools: stopped " + count + " queued commands.");
        }

        private void tickFireballBinds() {
            if (!AutismInputGate.canRunAutismKeybinds()) {
                fireballStreamBindDown = false;
                return;
            }
            boolean streamPressed = bindPressed("fireball-stream-bind");
            if (streamPressed && !fireballStreamBindDown) toggleFireballStream();
            fireballStreamBindDown = streamPressed;

            boolean stormPressed = bindPressed("firestorm-bind");
            if (stormPressed && !firestormBindDown) toggleFirestorm();
            firestormBindDown = stormPressed;
        }

        private boolean bindPressed(String optionId) {
            int bind = integer(optionId);
            return bind != -1 && AutismBindUtil.isBindPressed(MC, bind);
        }

        private void tickLiveFireballs() {
            if (MC.player == null || MC.getConnection() == null) return;
            if (fireballStreamEnabled) {
                if (liveFireballDelay-- <= 0) {
                    sendLiveFireball(0, 1, false);
                    liveFireballDelay = Math.max(0, integer("fireball-delay"));
                }
            }
            if (firestormEnabled) {
                if (liveFirestormDelay-- <= 0) {
                    int count = Math.max(1, integer("fireball-count"));
                    int burst = Math.max(1, Math.min(8, count));
                    for (int i = 0; i < burst; i++) {
                        sendLiveFireball(liveFirestormIndex++, count, true);
                    }
                    liveFirestormDelay = Math.max(0, integer("fireball-delay"));
                }
            }
        }

        private void sendLiveFireball(int index, int total, boolean firestorm) {
            String command = firestorm
                ? fireballCommand(index, total, firestormDistance(), firestormHeight())
                : fireballCommand(index, total);
            if (command.isBlank()) return;
            sendAdminCommand(command, true, false);
        }

        private void toggleFireballStream() {
            fireballStreamEnabled = !fireballStreamEnabled;
            setValue("fireball-stream-active", Boolean.toString(fireballStreamEnabled));
            liveFireballDelay = 0;
            AutismClientMessaging.sendPrefixed("Admin Tools: fireball stream " + (fireballStreamEnabled ? "enabled." : "disabled."));
        }

        private void toggleFirestorm() {
            firestormEnabled = !firestormEnabled;
            setValue("firestorm-active", Boolean.toString(firestormEnabled));
            liveFirestormDelay = 0;
            liveFirestormIndex = 0;
            AutismClientMessaging.sendPrefixed("Admin Tools: firestorm " + (firestormEnabled ? "enabled." : "disabled."));
        }

        public boolean isFireballStreamEnabled() {
            return fireballStreamEnabled;
        }

        public boolean isFirestormEnabled() {
            return firestormEnabled;
        }

        private void startFireballStorm() {
            queuedCommands.clear();
            commandIndex = 0;
            queuedDelayOption = "fireball-delay";
            int count = integer("fireball-count");
            for (int i = 0; i < count; i++) {
                String command = fireballCommand(i, count, firestormDistance(), firestormHeight());
                if (!command.isBlank()) queuedCommands.add(command);
            }
            if (queuedCommands.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not queue fireballs, join a world first.");
                return;
            }
            commandDelay = 0;
            AutismClientMessaging.sendPrefixed("Admin Tools: queued " + count + " fireballs.");
        }

        private void previewFireballCommand() {
            String command = fireballCommand(0, Math.max(1, integer("fireball-count")));
            AutismClientMessaging.sendPrefixed(command.isBlank() ? "Admin Tools: join a world first." : "Preview: /" + command);
        }

        private void copyFireballCommand() {
            copyCommandPreview(fireballCommand(0, Math.max(1, integer("fireball-count"))), "fireball command");
        }

        private void startAirstrike() {
            queuedCommands.clear();
            commandIndex = 0;
            queuedDelayOption = "airstrike-delay";
            int count = integer("airstrike-count");
            for (int i = 0; i < count; i++) {
                String command = airstrikeCommand(i, count);
                if (!command.isBlank()) queuedCommands.add(command);
            }
            if (queuedCommands.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not queue airstrike, join a world first.");
                return;
            }
            commandDelay = 0;
            AutismClientMessaging.sendPrefixed("Admin Tools: queued " + count + " airstrike fireballs.");
        }

        public void loadForceOpPasswords() {
            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
                java.nio.ByteBuffer txt = org.lwjgl.system.MemoryUtil.memASCII("*.txt");
                filters.put(txt).rewind();
                try {
                    String selected = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Load Passwords", null, filters, "Text files", false);
                    if (selected != null && !selected.isBlank()) {
                        java.util.List<String> loadedPWs = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(selected), java.nio.charset.StandardCharsets.UTF_8);
                        java.util.Set<String> parsed = new java.util.LinkedHashSet<>();
                        for (String line : loadedPWs) {
                            String pw = line.trim();
                            if (pw.isEmpty()) continue;
                            if (pw.contains(":")) {
                                String[] parts = pw.split(":");
                                pw = parts[parts.length - 1].trim();
                            }
                            if (!pw.isEmpty()) parsed.add(pw);
                        }
                        forceOpPasswords = parsed.toArray(new String[0]);
                        forceOpIndex = 0;
                        AutismClientMessaging.sendPrefixed("Admin Tools: Loaded " + forceOpPasswords.length + " passwords.");
                    }
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("Admin Tools: Failed to load passwords.");
                    forceOpPasswords = defaultForceOpList;
                    forceOpIndex = 0;
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(txt);
                }
            } finally {
                stack.close();
            }
        }

        public void unloadForceOpPasswords() {
            forceOpPasswords = defaultForceOpList;
            forceOpIndex = 0;
            AutismClientMessaging.sendPrefixed("Admin Tools: Unloaded custom passwords. Reverted to default.");
        }

        public String[] getForceOpPasswords() {
            return forceOpPasswords;
        }

        public void startForceOp() {
            if (forceOpRunning) return;
            if (MC.player == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            forceOpRunning = true;
            forceOpIndex = 0;
            int delay = integer("forceop-delay");
            boolean waitForMsg = bool("forceop-wait");

            forceOpThread = new Thread(() -> {
                MC.execute(() -> MC.getConnection().sendCommand("login " + MC.getUser().getName()));

                for (int i = 0; i < forceOpPasswords.length; i++) {
                    if (!forceOpRunning) return;

                    if (waitForMsg) gotWrongPwMsg = false;

                    long startWait = System.currentTimeMillis();
                    while (waitForMsg && !gotWrongPwMsg && MC.player != null) {
                        if (!forceOpRunning) return;
                        if (System.currentTimeMillis() - startWait > 2000L) {
                            AutismClientMessaging.sendPrefixed("Admin Tools: Timed out waiting for chat message. Stopping ForceOP.");
                            forceOpRunning = false;
                            return;
                        }
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {  }
                    }

                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {  }
                    if (!forceOpRunning) return;

                    boolean sent = false;
                    while (!sent && forceOpRunning && MC.player != null) {
                        try {
                            String pw = forceOpPasswords[i];
                            int min = integer("forceop-min-length");
                            int max = integer("forceop-max-length");

                            if (min > 0 && pw.length() < min) {
                                sent = true;
                                continue;
                            }
                            if (max > 0 && pw.length() > max) {
                                sent = true;
                                continue;
                            }

                            MC.execute(() -> MC.getConnection().sendCommand("login " + pw));
                            sent = true;
                        } catch (Exception e) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {  }
                        }
                    }

                    forceOpIndex = i + 1;
                }

                if (forceOpRunning) {
                    MC.execute(() -> AutismClientMessaging.sendPrefixed("§c[§4§lFAILURE§c]§f All " + forceOpIndex + " passwords were wrong."));
                    forceOpRunning = false;
                }
            }, "ForceOP");
            forceOpThread.start();
            AutismClientMessaging.sendPrefixed("Admin Tools: ForceOP started.");
        }

        public void stopForceOp() {
            if (!forceOpRunning) return;
            forceOpRunning = false;
            if (forceOpThread != null) forceOpThread.interrupt();
            AutismClientMessaging.sendPrefixed("Admin Tools: ForceOP stopped.");
        }

        public int getForceOpTotal() {
            return forceOpPasswords.length;
        }

        public int getForceOpIndex() {
            return forceOpIndex;
        }

        public boolean isForceOpRunning() {
            return forceOpRunning;
        }

        private void presetMaxFireballStorm() {
            setValue("fireball-count", "256");
            setValue("fireball-spread", "16.0");
            setValue("fireball-speed", "3.5");
            setValue("fireball-power", "8");
            setValue("fireball-delay", "0");
            setValue("fireball-distance", "3.0");
            setValue("fireball-aim", "Cone");
            setValue("fireball-randomize", "true");
            AutismClientMessaging.sendPrefixed("Admin Tools: loaded max fireball storm preset.");
        }

        private void presetTightAirstrike() {
            setValue("airstrike-count", "32");
            setValue("airstrike-radius", "8.0");
            setValue("airstrike-height", "56.0");
            setValue("airstrike-speed", "2.4");
            setValue("airstrike-power", "5");
            setValue("airstrike-delay", "1");
            setValue("airstrike-target", "Look");
            AutismClientMessaging.sendPrefixed("Admin Tools: loaded tight airstrike preset.");
        }

        private void presetMaxAirstrike() {
            setValue("airstrike-count", "256");
            setValue("airstrike-radius", "48.0");
            setValue("airstrike-height", "128.0");
            setValue("airstrike-speed", "4.5");
            setValue("airstrike-power", "10");
            setValue("airstrike-delay", "0");
            setValue("airstrike-target", "Look");
            AutismClientMessaging.sendPrefixed("Admin Tools: loaded max airstrike preset.");
        }

        private String fireballCommand(int index, int total) {
            return fireballCommand(index, total, decimal("fireball-distance"), 0.0);
        }

        private double firestormDistance() {
            return Math.max(2.0, decimal("firestorm-distance"));
        }

        private double firestormHeight() {
            return Math.max(0.0, decimal("firestorm-height"));
        }

        private String fireballCommand(int index, int total, double spawnDistance, double upOffset) {
            if (MC.player == null) return "";
            Vec3 look = MC.player.getLookAngle();
            Vec3 dir = fireballDirection(look, index, total).normalize();
            double speed = decimal("fireball-speed");
            Vec3 motion = dir.scale(speed);

            Vec3 pos = MC.player.getEyePosition().add(look.scale(spawnDistance)).add(0.0, upOffset, 0.0);
            return String.format(Locale.ROOT,
                "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[%.4f,%.4f,%.4f],Motion:[%.4f,%.4f,%.4f]}",
                pos.x, pos.y, pos.z, integer("fireball-power"), dir.x, dir.y, dir.z, motion.x, motion.y, motion.z);
        }

        private Vec3 fireballDirection(Vec3 look, int index, int total) {
            double spread = Math.toRadians(decimal("fireball-spread"));
            if (spread <= 0.0 || "Look".equals(choice("fireball-aim"))) return look;
            double yaw;
            double pitch;
            if ("Ring".equals(choice("fireball-aim"))) {
                double angle = (Math.PI * 2.0 * index) / Math.max(1, total);
                yaw = Math.cos(angle) * spread;
                pitch = Math.sin(angle) * spread;
            } else {
                yaw = (random.nextDouble() * 2.0 - 1.0) * spread;
                pitch = (random.nextDouble() * 2.0 - 1.0) * spread;
                if (!bool("fireball-randomize")) {
                    double step = total <= 1 ? 0.0 : (index / (double) (total - 1)) * 2.0 - 1.0;
                    yaw = step * spread;
                    pitch = 0.0;
                }
            }
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(look).normalize();
            return look.add(right.scale(Math.sin(yaw))).add(up.scale(Math.sin(pitch)));
        }

        private String airstrikeCommand(int index, int total) {
            if (MC.player == null) return "";
            Vec3 center = airstrikeCenter();
            double radius = decimal("airstrike-radius");
            double angle = total <= 1 ? 0.0 : Math.PI * 2.0 * (index / (double) total);
            double distance = radius <= 0.0 ? 0.0 : Math.sqrt(random.nextDouble()) * radius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + decimal("airstrike-height");
            double speed = -Math.abs(decimal("airstrike-speed"));
            return String.format(Locale.ROOT,
                "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[0.0000,%.4f,0.0000],Motion:[0.0000,%.4f,0.0000]}",
                x, y, z, integer("airstrike-power"), speed, speed);
        }

        private Vec3 airstrikeCenter() {
            if (MC.player == null) return Vec3.ZERO;
            if ("Self".equals(choice("airstrike-target"))) return MC.player.position();
            HitResult hit = MC.hitResult;
            if (hit != null && hit.getType() != HitResult.Type.MISS) return hit.getLocation();
            return MC.player.getEyePosition().add(MC.player.getLookAngle().scale(32.0));
        }

        private void createNamedNbtItem() {
            if (MC.player == null || MC.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = buildEditorStack(true);
            if (stack.isEmpty()) return;
            if (createCreativeStack(stack)) {
                AutismClientMessaging.sendPrefixed("Sent edited item packet.");
            }
        }

        private void fillHeldItemEditor() {
            if (MC.player == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            ItemStack stack = MC.player.getMainHandItem();
            if (stack.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: hold an item first.");
                return;
            }
            if (fillItemEditorFromStack(stack, true)) {
                AutismClientMessaging.sendPrefixed("Admin Tools: filled editor from held item.");
            }
        }

        private void copyTargetItem() {
            if (MC.player == null || MC.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            if (!isCreativeContext()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
                return;
            }
            if (!(MC.hitResult instanceof EntityHitResult entityHit)) {
                AutismClientMessaging.sendPrefixed("Admin Tools: look directly at an entity holding the item.");
                return;
            }

            Entity entity = entityHit.getEntity();
            ItemStack source = entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity
                ? itemEntity.getItem()
                : entity instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
            if (source.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: targeted entity has no visible held item.");
                return;
            }

            ItemStack copy = source.copy();
            if (!fillItemEditorFromStack(copy, false)) return;
            if (createCreativeStack(copy)) {
                AutismClientMessaging.sendPrefixed("Copied targeted item with all client-synced data.");
            }
        }

        public boolean fillItemEditorFromStack(ItemStack stack, boolean includeStatusMessage) {
            if (stack == null || stack.isEmpty()) return false;
            selectEditorItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), Math.max(1, Math.min(99, stack.getMaxStackSize())));
            setValue("nbt-item-count", Integer.toString(Math.max(1, Math.min(99, stack.getCount()))));
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            setValue("nbt-item-name", name == null ? "" : name.getString());
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) {
                setValue("nbt-item-lore", "");
            } else {
                List<String> lines = new ArrayList<>();
                for (Component line : lore.lines()) lines.add(line.getString());
                setValue("nbt-item-lore", String.join("|", lines));
            }
            setValue("nbt-unbreakable", Boolean.toString(stack.has(DataComponents.UNBREAKABLE)));
            Boolean glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            setValue("nbt-glint", glint == null ? "Default" : (glint ? "On" : "Off"));
            Rarity rarity = explicitComponent(stack, DataComponents.RARITY);
            setValue("nbt-rarity", rarity == null ? "Default" : switch (rarity) {
                case COMMON -> "Common";
                case UNCOMMON -> "Uncommon";
                case RARE -> "Rare";
                case EPIC -> "Epic";
            });
            setValue("nbt-max-damage", Integer.toString(Math.max(0, stack.getMaxDamage())));
            setValue("nbt-damage", Integer.toString(Math.max(0, stack.getDamageValue())));
            setValue("nbt-enchants", editorEnchantments(stack));
            setValue("nbt-attributes", editorAttributes(stack));
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag customTag = customData == null ? new CompoundTag() : customData.copyTag();
            setValue("nbt-custom-data", customTag.isEmpty() ? "" : customTag.toString());
            String embeddedCommand = customTag.getStringOr("command", "");
            TypedEntityData<?> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (embeddedCommand.isBlank() && blockEntityData != null) {
                embeddedCommand = blockEntityData.copyTagWithoutId().getStringOr("Command", "");
            }
            setValue("nbt-command", embeddedCommand);
            String imported = AutismItemCommandSerializer.componentPatch(stack);
            setValue("nbt-imported-components", imported);
            setValue("nbt-imported-signature", editorSignature());
            setValue("nbt-item-components", imported);
            setValue("nbt-item-stack", AutismItemNbtInspector.prettySnbt(AutismItemCommandSerializer.itemStackSnbt(stack)));
            setValue("nbt-item-stack-signature", editorSignature());
            return true;
        }

        public void syncEditorMaxStackForItemId() {
            selectEditorItemId(value("nbt-item-id"));
        }

        public void selectEditorItemId(String itemId) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId == null ? "" : itemId.trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            selectEditorItemId(BuiltInRegistries.ITEM.getKey(item).toString(), editorItemDefaultMaxStack(item));
        }

        private void selectEditorItemId(String itemId, int newMaxStack) {
            int oldMaxStack = Math.max(1, Math.min(99, integer("nbt-max-stack")));
            int currentCount = Math.max(1, Math.min(99, integer("nbt-item-count")));
            int clampedNewMax = Math.max(1, Math.min(99, newMaxStack));
            int nextCount = currentCount;
            if (currentCount > clampedNewMax || currentCount == oldMaxStack) {
                nextCount = clampedNewMax;
            }
            setValue("nbt-item-id", itemId == null || itemId.isBlank() ? BuiltInRegistries.ITEM.getKey(Items.STICK).toString() : itemId);
            setValue("nbt-max-stack", Integer.toString(clampedNewMax));
            setValue("nbt-item-count", Integer.toString(nextCount));
        }

        private void applyHeldItemEditor() {
            if (MC.player == null || MC.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = buildEditorStack(true);
            if (stack.isEmpty()) return;
            if (createCreativeStack(stack)) {
                fillItemEditorFromStack(stack, false);
                AutismClientMessaging.sendPrefixed("Sent edited held item packet.");
            }
        }

        private ItemStack buildEditorStack() {
            return buildEditorStack(false);
        }

        private ItemStack buildEditorStack(boolean notifyErrors) {
            String rawStack = value("nbt-item-stack").trim();
            boolean hasRawBase = !rawStack.isBlank();
            ItemStack rawBase = hasRawBase ? AutismItemCommandSerializer.itemStackFromSnbt(rawStack) : ItemStack.EMPTY;
            if (hasRawBase && rawBase.isEmpty()) {
                if (notifyErrors) AutismClientMessaging.sendPrefixed("Admin Tools: invalid full item SNBT.");
                return ItemStack.EMPTY;
            }

            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(value("nbt-item-id").trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            boolean itemChanged = hasRawBase && rawBase.getItem() != item;
            ItemStack stack;
            if (hasRawBase && !itemChanged) {
                stack = rawBase.copy();
            } else {
                stack = new ItemStack(item);
                if (hasRawBase) stack.applyComponents(rawBase.getComponentsPatch());
            }

            boolean applyAll = !hasRawBase;
            if (applyAll || itemChanged || structuredFieldChanged("nbt-item-count")) {
                stack.setCount(Math.max(1, Math.min(99, integer("nbt-item-count"))));
            }
            if (!applyStructuredComponents(stack, item, applyAll, notifyErrors)) return ItemStack.EMPTY;

            String validationError = AutismItemCommandSerializer.validationError(stack);
            if (!validationError.isBlank()) {
                if (notifyErrors) AutismClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + validationError);
                return ItemStack.EMPTY;
            }
            return stack;
        }

        private boolean applyStructuredComponents(ItemStack stack, Item item, boolean applyAll, boolean notifyErrors) {
            if (applyAll || structuredFieldChanged("nbt-item-name")) {
                String name = value("nbt-item-name").trim();
                if (name.isBlank()) stack.remove(DataComponents.CUSTOM_NAME);
                else stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
            if (applyAll || structuredFieldChanged("nbt-item-lore")) {
                List<Component> lore = editorLoreComponents();
                if (lore.isEmpty()) stack.remove(DataComponents.LORE);
                else stack.set(DataComponents.LORE, new ItemLore(lore));
            }
            if (applyAll || structuredFieldChanged("nbt-unbreakable")) {
                if (bool("nbt-unbreakable")) stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                else stack.remove(DataComponents.UNBREAKABLE);
            }
            if (applyAll || structuredFieldChanged("nbt-glint")) {
                switch (choice("nbt-glint")) {
                    case "On" -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    case "Off" -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                    default -> stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
                }
            }
            if (applyAll || structuredFieldChanged("nbt-rarity")) {
                switch (choice("nbt-rarity")) {
                    case "Common" -> stack.set(DataComponents.RARITY, Rarity.COMMON);
                    case "Uncommon" -> stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
                    case "Rare" -> stack.set(DataComponents.RARITY, Rarity.RARE);
                    case "Epic" -> stack.set(DataComponents.RARITY, Rarity.EPIC);
                    default -> stack.remove(DataComponents.RARITY);
                }
            }

            boolean maxDamageChanged = applyAll || structuredFieldChanged("nbt-max-damage");
            boolean damageChanged = applyAll || structuredFieldChanged("nbt-damage");
            if (maxDamageChanged) {
                int maxDamage = integer("nbt-max-damage");
                if (maxDamage > 0) stack.set(DataComponents.MAX_DAMAGE, maxDamage);
                else {
                    stack.remove(DataComponents.MAX_DAMAGE);
                    stack.remove(DataComponents.DAMAGE);
                }
            }
            if (damageChanged && stack.getMaxDamage() > 0) {
                stack.set(DataComponents.DAMAGE, Math.min(Math.max(0, integer("nbt-damage")), stack.getMaxDamage()));
            }
            if (applyAll || structuredFieldChanged("nbt-max-stack")) {
                int maxStack = Math.max(1, Math.min(99, integer("nbt-max-stack")));
                int defaultMaxStack = editorItemDefaultMaxStack(item);
                if (maxStack == defaultMaxStack) stack.remove(DataComponents.MAX_STACK_SIZE);
                else stack.set(DataComponents.MAX_STACK_SIZE, maxStack);
            }
            if (applyAll || structuredFieldChanged("nbt-enchants")) {
                if (!applyEditorEnchantments(stack, notifyErrors)) return false;
            }
            if (applyAll || structuredFieldChanged("nbt-attributes")) {
                ItemAttributeModifiers attributes = parseEditorAttributes(value("nbt-attributes"), notifyErrors);
                if (attributes == null) return false;
                if (attributes.modifiers().isEmpty()) stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
                else stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
            }

            boolean customChanged = applyAll || structuredFieldChanged("nbt-custom-data")
                || structuredFieldChanged("nbt-command");
            if (customChanged) {
                CompoundTag customData = buildCustomDataTag(notifyErrors);
                if (customData == null) return false;
                if (customData.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

                String embeddedCommand = normalizedCommand(value("nbt-command"));
                if (isCommandBlockItem() && !embeddedCommand.isBlank()) {
                    stack.set(DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(BlockEntityTypes.COMMAND_BLOCK, commandBlockEntityTag(embeddedCommand)));
                } else if (structuredFieldChanged("nbt-command")) {
                    stack.remove(DataComponents.BLOCK_ENTITY_DATA);
                }
            }
            return true;
        }

        private int editorItemDefaultMaxStack() {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(value("nbt-item-id").trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            return editorItemDefaultMaxStack(item);
        }

        private int editorItemDefaultMaxStack(Item item) {
            ItemStack stack = new ItemStack(item == null ? Items.STICK : item);
            return Math.max(1, Math.min(99, stack.getMaxStackSize()));
        }

        private List<Component> editorLoreComponents() {
            String raw = value("nbt-item-lore");
            if (raw == null || raw.isBlank()) return List.of();
            List<Component> lines = new ArrayList<>();
            for (String part : raw.split("\\|")) {
                String line = part.trim();
                if (!line.isEmpty()) lines.add(Component.literal(line));
            }
            return lines;
        }

        private void syncEditorComponentsPatch() {
            setValue("nbt-item-components", buildEditorComponents());
            setValue("nbt-imported-components", "");
            setValue("nbt-imported-signature", "");
            setValue("nbt-item-stack", "");
            setValue("nbt-item-stack-signature", "");
        }

        public void setRawItemComponents(String rawComponents) {
            String raw = rawComponents == null ? "" : rawComponents.trim();
            setValue("nbt-item-components", raw);
            setValue("nbt-imported-components", raw);
            setValue("nbt-imported-signature", editorSignature());
            setValue("nbt-item-stack", "");
            setValue("nbt-item-stack-signature", "");
        }

        public boolean setRawItemStackSnbt(String rawSnbt) {
            ItemStack parsed = AutismItemCommandSerializer.itemStackFromSnbt(rawSnbt);
            if (parsed.isEmpty() || !AutismItemCommandSerializer.validationError(parsed).isBlank()) return false;
            return fillItemEditorFromStack(parsed, false);
        }

        public void prepareRawItemStackEditor() {
            if (hasActiveImportedStack()) return;
            ItemStack stack = buildEditorStack(false);
            if (stack.isEmpty()) return;
            fillItemEditorFromStack(stack, false);
        }

        private boolean hasActiveImportedStack() {
            String raw = value("nbt-item-stack").trim();
            return !raw.isBlank() && value("nbt-item-stack-signature").equals(editorSignature());
        }

        private String buildEditorComponents() {
            String components = buildEditorComponents(false);
            return components == null ? "" : components;
        }

        private String buildEditorComponents(boolean notifyErrors) {
            String imported = activeImportedComponents();
            if (!imported.isBlank()) return imported;
            return buildManualEditorComponents(notifyErrors);
        }

        private String buildManualEditorComponents(boolean notifyErrors) {
            List<String> parts = new ArrayList<>();
            String name = value("nbt-item-name").trim();
            if (!name.isBlank()) parts.add("custom_name=[" + jsonText(name, "white") + "]");
            String lore = loreComponentString();
            if (!lore.isBlank()) parts.add("lore=" + lore);
            if (bool("nbt-unbreakable")) parts.add("unbreakable={}");
            String glint = choice("nbt-glint");
            if ("On".equals(glint)) parts.add("enchantment_glint_override=true");
            else if ("Off".equals(glint)) parts.add("enchantment_glint_override=false");
            String rarity = choice("nbt-rarity").toLowerCase(Locale.ROOT);
            if (!"default".equals(rarity)) parts.add("rarity=" + rarity);
            int maxDamage = integer("nbt-max-damage");
            if (maxDamage > 0) {
                parts.add("max_damage=" + maxDamage);
                parts.add("damage=" + Math.min(integer("nbt-damage"), maxDamage));
            } else if (integer("nbt-max-stack") != editorItemDefaultMaxStack()) {
                parts.add("max_stack_size=" + Math.max(1, Math.min(99, integer("nbt-max-stack"))));
            }
            String enchantments = enchantmentsComponent(value("nbt-enchants"), notifyErrors);
            if (enchantments == null) return null;
            if (!enchantments.isBlank()) parts.add("enchantments=" + enchantments);
            appendRawListComponent(parts, "attribute_modifiers", value("nbt-attributes"));
            String embeddedCommand = normalizedCommand(value("nbt-command"));
            boolean commandBlockCommand = !embeddedCommand.isBlank() && isCommandBlockItem();
            if (commandBlockCommand) {
                parts.add("block_entity_data=" + commandBlockEntityComponent(embeddedCommand));
            }
            String customData = customDataComponentString(commandBlockCommand ? "" : embeddedCommand);
            if (!customData.isBlank()) parts.add("custom_data=" + customData);
            return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
        }

        private String activeImportedComponents() {
            String imported = value("nbt-imported-components").trim();
            if (imported.isBlank()) return "";
            String signature = value("nbt-imported-signature");
            if (!signature.equals(editorSignature())) return "";
            return imported.startsWith("[") ? imported : "[" + imported + "]";
        }

        private String editorSignature() {
            StringBuilder signature = new StringBuilder();
            for (String field : ITEM_EDITOR_SIGNATURE_FIELDS) {
                String fieldValue = value(field);
                signature.append(fieldValue.length()).append(':').append(fieldValue);
            }
            return signature.toString();
        }

        private boolean structuredFieldChanged(String field) {
            int index = ITEM_EDITOR_SIGNATURE_FIELDS.indexOf(field);
            if (index < 0) return true;
            String signature = value("nbt-item-stack-signature");
            if (signature.isBlank()) return true;
            if (signature.indexOf('\u001F') >= 0) {
                String[] legacyBaseline = signature.split("\u001F", -1);
                return index >= legacyBaseline.length || !value(field).equals(legacyBaseline[index]);
            }
            String baseline = signatureField(signature, index);
            return baseline == null || !value(field).equals(baseline);
        }

        private String signatureField(String signature, int wantedIndex) {
            int cursor = 0;
            for (int index = 0; index <= wantedIndex; index++) {
                int separator = signature.indexOf(':', cursor);
                if (separator < cursor) return null;
                int length;
                try {
                    length = Integer.parseInt(signature.substring(cursor, separator));
                } catch (NumberFormatException ignored) {
                    return null;
                }
                int start = separator + 1;
                int end = start + length;
                if (length < 0 || end < start || end > signature.length()) return null;
                if (index == wantedIndex) return signature.substring(start, end);
                cursor = end;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private <T> T explicitComponent(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type) {
            if (stack == null || stack.isEmpty() || type == null) return null;
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry
                : stack.getComponentsPatch().entrySet()) {
                if (entry.getKey() == type) return (T) entry.getValue().orElse(null);
            }
            return null;
        }

        private String editorEnchantments(ItemStack stack) {
            List<String> entries = new ArrayList<>();
            appendEditorEnchantments(entries, stack.get(DataComponents.ENCHANTMENTS));
            appendEditorEnchantments(entries, stack.get(DataComponents.STORED_ENCHANTMENTS));
            return String.join(",", entries);
        }

        private boolean applyEditorEnchantments(ItemStack stack, boolean notifyErrors) {
            List<String> normalized = normalizedEnchantmentEntries(value("nbt-enchants"), notifyErrors);
            if (normalized == null) return false;
            Map<String, Integer> desired = new LinkedHashMap<>();
            for (String entry : normalized) {
                int split = entry.lastIndexOf(':');
                if (split <= 0 || split >= entry.length() - 1) return false;
                String id = stripQuotes(entry.substring(0, split));
                if (!id.contains(":")) id = "minecraft:" + id;
                try {
                    desired.put(id, Integer.parseInt(entry.substring(split + 1)));
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            ItemEnchantments regular = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments.Mutable regularMutable = new ItemEnchantments.Mutable(regular);
            ItemEnchantments.Mutable storedMutable = new ItemEnchantments.Mutable(stored);
            Set<String> assigned = new LinkedHashSet<>();

            for (Holder<Enchantment> holder : List.copyOf(regular.keySet())) {
                String id = enchantmentId(holder);
                Integer level = desired.get(id);
                regularMutable.set(holder, level == null ? 0 : level);
                if (level != null) assigned.add(id);
            }
            for (Holder<Enchantment> holder : List.copyOf(stored.keySet())) {
                String id = enchantmentId(holder);
                Integer level = desired.get(id);
                storedMutable.set(holder, level == null ? 0 : level);
                if (level != null) assigned.add(id);
            }

            boolean preferStored = stack.is(Items.ENCHANTED_BOOK) || (!stored.isEmpty() && regular.isEmpty());
            for (Map.Entry<String, Integer> entry : desired.entrySet()) {
                if (assigned.contains(entry.getKey())) continue;
                Holder<Enchantment> holder = enchantmentHolder(entry.getKey());
                if (holder == null) {
                    if (notifyErrors) {
                        AutismClientMessaging.sendPrefixed("Admin Tools: unknown enchantment '" + entry.getKey() + "'.");
                    }
                    return false;
                }
                if (preferStored) storedMutable.set(holder, entry.getValue());
                else regularMutable.set(holder, entry.getValue());
            }

            ItemEnchantments updatedRegular = regularMutable.toImmutable();
            ItemEnchantments updatedStored = storedMutable.toImmutable();
            if (updatedRegular.isEmpty()) stack.remove(DataComponents.ENCHANTMENTS);
            else stack.set(DataComponents.ENCHANTMENTS, updatedRegular);
            if (updatedStored.isEmpty()) stack.remove(DataComponents.STORED_ENCHANTMENTS);
            else stack.set(DataComponents.STORED_ENCHANTMENTS, updatedStored);
            return true;
        }

        private String enchantmentId(Holder<Enchantment> holder) {
            return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        }

        private Holder<Enchantment> enchantmentHolder(String rawId) {
            try {
                if (MC.player == null) return null;
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(rawId);
                if (id == null) return null;
                net.minecraft.resources.ResourceKey<Enchantment> key =
                    net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id);
                return MC.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private String editorAttributes(ItemStack stack) {
            ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (attributes == null || attributes.modifiers().isEmpty() || MC.player == null) return "";
            try {
                Tag encoded = ItemAttributeModifiers.CODEC.encodeStart(
                    MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), attributes
                ).getOrThrow();
                String raw = encoded.toString();
                return raw.length() >= 2 && raw.startsWith("[") && raw.endsWith("]")
                    ? raw.substring(1, raw.length() - 1)
                    : raw;
            } catch (RuntimeException ignored) {
                return "";
            }
        }

        private ItemAttributeModifiers parseEditorAttributes(String raw, boolean notifyErrors) {
            if (raw == null || raw.isBlank()) return ItemAttributeModifiers.EMPTY;
            if (MC.player == null) return null;
            try {
                CompoundTag wrapper = TagParser.parseCompoundFully("{value:[" + raw + "]}");
                Tag encoded = wrapper.get("value");
                if (encoded == null) return ItemAttributeModifiers.EMPTY;
                return ItemAttributeModifiers.CODEC.parse(
                    MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), encoded
                ).getOrThrow();
            } catch (Throwable ignored) {
                if (notifyErrors) AutismClientMessaging.sendPrefixed("Admin Tools: invalid attribute modifiers.");
                return null;
            }
        }

        private void appendEditorEnchantments(List<String> entries, ItemEnchantments enchantments) {
            if (enchantments == null || enchantments.isEmpty()) return;
            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> entry : enchantments.entrySet()) {
                String id = entry.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse("").trim();
                if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
                if (!id.isBlank()) entries.add(id + ":" + entry.getIntValue());
            }
        }

        private String customDataComponentString(String embeddedCommand) {
            String raw = value("nbt-custom-data").trim();
            String merged = mergeCustomDataPayload(raw, embeddedCommand);
            return merged.isBlank() ? "" : wrapCompound(merged);
        }

        private CompoundTag buildCustomDataTag(boolean notifyErrors) {
            String embeddedCommand = normalizedCommand(value("nbt-command"));
            String payload = customDataComponentString(isCommandBlockItem() ? "" : embeddedCommand);
            if (payload.isBlank()) return new CompoundTag();
            try {
                return TagParser.parseCompoundFully(payload);
            } catch (Exception e) {
                if (notifyErrors) {
                    AutismClientMessaging.sendPrefixed("Admin Tools: invalid custom data / embedded command data.");
                }
                return null;
            }
        }

        private String mergeCustomDataPayload(String rawCustomData, String embeddedCommand) {
            String raw = rawCustomData == null ? "" : rawCustomData.trim();
            String command = normalizedCommand(embeddedCommand);
            if (command.isBlank()) return raw;

            List<String> entries = new ArrayList<>();
            String inner = unwrapCompound(raw);
            if (!inner.isBlank()) {
                for (String entry : splitTopLevelEntries(inner)) {
                    String key = topLevelSnbtKey(entry);
                    if ("autism_admin_tool".equals(key) || "command".equals(key)) continue;
                    entries.add(entry.trim());
                }
            }

            entries.add("autism_admin_tool:1b");
            entries.add("command:\"" + snbtString(command) + "\"");
            return String.join(",", entries);
        }

        private String commandBlockEntityComponent(String command) {
            String normalized = normalizedCommand(command);
            if (normalized.isBlank()) normalized = "say Autism";
            return "{id:\"minecraft:command_block\",Command:\"" + snbtString(normalized) + "\",auto:0b,TrackOutput:1b}";
        }

        private CompoundTag commandBlockEntityTag(String command) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:command_block");
            tag.putString("Command", normalizedCommand(command).isBlank() ? "say Autism" : normalizedCommand(command));
            tag.putByte("auto", (byte) 0);
            tag.putByte("TrackOutput", (byte) 1);
            return tag;
        }

        private boolean isCommandBlockItem() {
            String item = value("nbt-item-id").trim();
            return "minecraft:command_block".equals(item) || "command_block".equals(item)
                || "minecraft:chain_command_block".equals(item) || "chain_command_block".equals(item)
                || "minecraft:repeating_command_block".equals(item) || "repeating_command_block".equals(item);
        }

        private String loreComponentString() {
            List<String> lines = new ArrayList<>();
            String raw = value("nbt-item-lore");
            if (raw == null || raw.isBlank()) return "";
            for (String part : raw.split("\\|")) {
                String line = part.trim();
                if (!line.isEmpty()) lines.add(jsonText(line, "dark_purple"));
            }
            if (lines.isEmpty()) return "";
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) wrapped.add("[" + line + "]");
            return "[" + String.join(",", wrapped) + "]";
        }

        private void appendRawListComponent(List<String> parts, String name, String raw) {
            if (raw == null || raw.isBlank()) return;
            String trimmed = raw.trim();
            parts.add(name + "=" + (trimmed.startsWith("[") ? trimmed : "[" + trimmed + "]"));
        }

        private String enchantmentsComponent(String raw, boolean notifyErrors) {
            List<String> entries = normalizedEnchantmentEntries(raw, notifyErrors);
            if (entries == null) return null;
            return entries.isEmpty() ? "" : "{" + String.join(",", entries) + "}";
        }

        private List<String> normalizedEnchantmentEntries(String raw, boolean notifyErrors) {
            List<String> entries = new ArrayList<>();
            if (raw == null || raw.isBlank()) return entries;
            for (String entry : splitTopLevelEntries(raw)) {
                String normalized = normalizeEnchantmentEntry(entry);
                if (normalized == null) {
                    if (notifyErrors) AutismClientMessaging.sendPrefixed("Admin Tools: invalid enchantment entry '" + entry + "'. Use id:level, for example binding_curse:1.");
                    return null;
                }
                entries.add(normalized);
            }
            return entries;
        }

        private String normalizeEnchantmentEntry(String entry) {
            if (entry == null) return null;
            String text = entry.trim();
            if (text.isBlank()) return null;
            if (text.startsWith("[") && text.endsWith("]") && text.length() > 2) text = text.substring(1, text.length() - 1).trim();
            if (text.startsWith("{") && text.endsWith("}") && text.contains("id:") && text.contains("level:")) {
                String id = extractComponentValue(text, "id");
                String level = extractComponentValue(text, "level");
                return normalizeEnchantmentIdAndLevel(id, level);
            }
            int oldLevelIndex = text.indexOf(":{level:");
            if (oldLevelIndex > 0) {
                String id = text.substring(0, oldLevelIndex);
                String levelPart = text.substring(oldLevelIndex + ":{level:".length());
                int levelEnd = levelPart.indexOf('}');
                String level = levelEnd >= 0 ? levelPart.substring(0, levelEnd) : levelPart;
                return normalizeEnchantmentIdAndLevel(id, level);
            }
            int levelIndex = text.lastIndexOf(':');
            if (levelIndex <= 0 || levelIndex >= text.length() - 1) return null;
            String id = text.substring(0, levelIndex);
            while (id.endsWith(":")) id = id.substring(0, id.length() - 1);
            String level = text.substring(levelIndex + 1);
            return normalizeEnchantmentIdAndLevel(id, level);
        }

        private String normalizeEnchantmentIdAndLevel(String rawId, String rawLevel) {
            String id = normalizeEnchantmentId(rawId);
            if (id.isBlank()) return null;
            String levelText = stripQuotes(rawLevel).trim();
            try {
                int level = Integer.parseInt(levelText);
                if (level < 1 || level > 255) return null;
                String commandId = id.contains(":") ? "\"" + snbtString(id) + "\"" : id;
                return commandId + ":" + level;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String normalizeEnchantmentId(String rawId) {
            String id = stripQuotes(rawId).trim();
            if (id.isBlank()) return "";
            if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
            net.minecraft.resources.Identifier parsed = id.contains(":")
                ? net.minecraft.resources.Identifier.tryParse(id)
                : net.minecraft.resources.Identifier.tryParse("minecraft:" + id);
            return parsed == null ? "" : id;
        }

        private String extractComponentValue(String entry, String key) {
            String needle = key + ":";
            int start = entry.indexOf(needle);
            if (start < 0) return "";
            start += needle.length();
            int end = start;
            boolean quoted = false;
            char quote = 0;
            boolean escaped = false;
            while (end < entry.length()) {
                char c = entry.charAt(end);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (quoted) {
                    if (c == quote) quoted = false;
                } else if (c == '\'' || c == '"') {
                    quoted = true;
                    quote = c;
                } else if (c == ',' || c == '}' || c == ']') {
                    break;
                }
                end++;
            }
            return entry.substring(start, end).trim();
        }

        private List<String> splitTopLevelEntries(String raw) {
            List<String> out = new ArrayList<>();
            if (raw == null || raw.isBlank()) return out;
            String text = raw.trim();
            if ((text.startsWith("[") && text.endsWith("]")) || (text.startsWith("{") && text.endsWith("}"))) {
                text = text.substring(1, text.length() - 1);
            }
            StringBuilder current = new StringBuilder();
            int depth = 0;
            boolean quoted = false;
            char quote = 0;
            boolean escaped = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (escaped) {
                    current.append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    current.append(c);
                    escaped = true;
                    continue;
                }
                if (quoted) {
                    current.append(c);
                    if (c == quote) quoted = false;
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quoted = true;
                    quote = c;
                    current.append(c);
                    continue;
                }
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
                if (c == ',' && depth == 0) {
                    String entry = current.toString().trim();
                    if (!entry.isBlank()) out.add(entry);
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            String entry = current.toString().trim();
            if (!entry.isBlank()) out.add(entry);
            return out;
        }

        private String stripQuotes(String text) {
            if (text == null) return "";
            String value = text.trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        private String wrapCompound(String raw) {
            String trimmed = raw.trim();
            return trimmed.startsWith("{") ? trimmed : "{" + trimmed + "}";
        }

        private String unwrapCompound(String raw) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
            return trimmed;
        }

        private String topLevelSnbtKey(String entry) {
            if (entry == null) return "";
            String text = entry.trim();
            int separator = text.indexOf(':');
            if (separator <= 0) return "";
            return stripQuotes(text.substring(0, separator)).trim();
        }

        private String normalizedCommand(String command) {
            if (command == null) return "";
            command = command.trim();
            return command.startsWith("/") ? command.substring(1) : command;
        }

        private void createCommandBlockPreset() {
            setValue("nbt-item-id", "minecraft:command_block");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "Admin Command Block");
            setValue("nbt-command", commandTemplate().isBlank() ? "say Autism" : commandTemplate());
            syncEditorComponentsPatch();
            if (MC.player != null && MC.getConnection() != null) {
                ItemStack stack = buildEditorStack(true);
                if (!stack.isEmpty() && createCreativeStack(stack)) {
                    AutismClientMessaging.sendPrefixed("Loaded preset and sent command block item packet.");
                }
            } else {
                AutismClientMessaging.sendPrefixed("Loaded command block preset. Join a world to send the item packet.");
            }
        }

        private String adminTarget() {
            return MC.player == null ? "" : MC.player.getGameProfile().name();
        }

        private void createForceOpStickPreset() {
            String target = adminTarget().isBlank() ? "{target}" : adminTarget();
            setValue("nbt-item-id", "minecraft:stick");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "ForceOP Tool");
            setValue("nbt-command", "op " + target);
            setValue("nbt-custom-data", "autism_admin_tool:1b,command:\"" + snbtString("op " + target) + "\"");
            syncEditorComponentsPatch();
            if (MC.player != null && MC.getConnection() != null) {
                ItemStack stack = buildEditorStack(true);
                if (!stack.isEmpty() && createCreativeStack(stack)) {
                    AutismClientMessaging.sendPrefixed("Loaded preset and sent ForceOP tool item packet.");
                }
            } else {
                AutismClientMessaging.sendPrefixed("Loaded ForceOP stick preset. Join a world to send the item packet.");
            }
        }

        private void presetForceOpCommandBlock() {
            String target = adminTarget().isBlank() ? "{target}" : adminTarget();
            setValue("nbt-item-id", "minecraft:command_block");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "ForceOP Command Block");
            setValue("nbt-command", "op " + target);
            syncEditorComponentsPatch();
            AutismClientMessaging.sendPrefixed("Loaded ForceOP command block preset. Use Apply Item in creative mode.");
        }

        private ItemStack namedStack(Item item, int count, String name) {
            ItemStack stack = new ItemStack(item == null ? Items.STICK : item, Math.max(1, Math.min(99, count)));
            if (name != null && !name.isBlank()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            return stack;
        }

        private boolean createCreativeStack(ItemStack stack) {
            if (MC.player == null || MC.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return false;
            }
            if (!isCreativeContext()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
                return false;
            }
            if (stack == null || stack.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet, item stack is empty.");
                return false;
            }
            String validationError = AutismItemCommandSerializer.validationError(stack);
            if (!validationError.isBlank()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + validationError);
                return false;
            }
            int selected = MC.player.getInventory().getSelectedSlot();
            int slot = 36 + selected;
            MC.player.getInventory().setItem(selected, stack.copy());
            try {
                MC.getConnection().send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
                return true;
            } catch (RuntimeException e) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet: " + e.getClass().getSimpleName());
                return false;
            }
        }

        private void createFireChargeStack() {
            if (MC.player == null || MC.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = new ItemStack(Items.FIRE_CHARGE, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Fireball Storm"));
            if (createCreativeStack(stack)) {
                AutismClientMessaging.sendPrefixed("Sent Fireball Storm stack item packet.");
            }
        }

        public void giveEditorItem() {
            String id = value("nbt-item-id").trim();
            if (id.startsWith("/")) id = id.substring(1);
            if (id.isBlank()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: set an item id first.");
                return;
            }
            String components = buildEditorComponents(true);
            if (components == null) return;
            int count = Math.max(1, Math.min(99, integer("nbt-item-count")));
            sendAdminCommand("give @p " + id + components + " " + count, false);
        }

        private boolean sendAdminCommand(String command, boolean queued) {
            return sendAdminCommand(command, queued, true);
        }

        private boolean sendAdminCommand(String command, boolean queued, boolean notifyFailures) {
            if (MC == null || MC.getConnection() == null) {
                if (notifyFailures) AutismClientMessaging.sendPrefixed("Admin Tools: could not send command, join a world first.");
                return false;
            }
            if (command == null || command.isBlank()) {
                if (notifyFailures) AutismClientMessaging.sendPrefixed("Admin Tools: could not send command, command is empty.");
                return false;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            try {
                MC.getConnection().sendCommand(normalized);

                String lowerCmd = normalized.toLowerCase(Locale.ROOT);
                if (lowerCmd.startsWith("summon") && lowerCmd.contains("fireball")) {
                    suppressSummonMessagesUntilMs = System.currentTimeMillis() + 5000L;
                }
                return true;
            } catch (RuntimeException e) {
                if (notifyFailures) AutismClientMessaging.sendPrefixed("Admin Tools: could not send " + (queued ? "queued " : "") + "command: " + e.getClass().getSimpleName());
                return false;
            }
        }

        private void copyCommandPreview(String command, String label) {
            if (command == null || command.isBlank()) {
                AutismClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            copyToClipboard("/" + command, "Copied " + label + ".");
        }

        private void copyToClipboard(String text, String message) {
            if (MC == null || MC.keyboardHandler == null || text == null || text.isBlank()) {
                AutismNotifications.error("Nothing to copy.");
                return;
            }
            MC.keyboardHandler.setClipboard(text);
            AutismNotifications.copied(message);
        }

        private String commandTemplate() {
            String command = value("nbt-command").trim();
            if (command.isBlank()) command = "op {target}";
            String target = adminTarget();
            if (!target.isBlank()) command = command.replace("{target}", target);
            return command.startsWith("/") ? command.substring(1) : command;
        }

        private String namedToolComponents(String name, String lore, String command) {
            return "[custom_name=[" + jsonText(name, "red") + "],lore=[[" + jsonText(lore, "gray") + "]],custom_data={autism_admin_tool:1b,command:\"" + snbtString(command) + "\"}]";
        }

        private String commandBlockComponents(String command) {
            String normalized = command == null || command.isBlank() ? "say Autism" : command;
            normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized;
            return "[custom_name=[" + jsonText(value("nbt-item-name").isBlank() ? "Admin Command Block" : value("nbt-item-name"), "red") + "],block_entity_data={id:\"minecraft:command_block\",Command:\"" + snbtString(normalized) + "\",auto:0b,TrackOutput:1b}]";
        }

        private String jsonText(String text, String color) {
            return "{\"text\":\"" + jsonString(text) + "\",\"color\":\"" + jsonString(color) + "\",\"italic\":false}";
        }

        private String jsonString(String text) {
            if (text == null) return "";
            return text.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String snbtString(String text) {
            return jsonString(text);
        }
    }
}
