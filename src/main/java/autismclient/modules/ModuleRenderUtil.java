package autismclient.modules;

import autismclient.util.AutismOverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModuleRenderUtil {
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile XraySnapshot xraySnapshot = XraySnapshot.inactive(-1, false);
    private static volatile FullbrightSnapshot fullbrightSnapshot = FullbrightSnapshot.inactive(-1, false);
    private static volatile WorldDarkenSnapshot worldDarkenSnapshot = WorldDarkenSnapshot.inactive(-1, false);
    private static volatile EntityRenderSnapshot espSnapshot = EntityRenderSnapshot.inactive("esp", -1, false);
    private static volatile ItemEspSnapshot itemEspSnapshot = ItemEspSnapshot.inactive(-1, false);
    private static volatile EntityRenderSnapshot tracerSnapshot = EntityRenderSnapshot.inactive("tracers", -1, false);
    private static volatile ChamsSnapshot chamsSnapshot = ChamsSnapshot.inactive(-1, false);
    private static volatile boolean xrayRenderWork;
    private static volatile boolean worldDarkenWork;
    private static volatile boolean fullbrightGammaWork;
    private static volatile boolean fullbrightLuminanceWork;
    private static volatile boolean brightLightmapWork;
    private static volatile boolean worldTracerWork;
    private static volatile boolean outlineWork;
    private static volatile boolean esp2dWork;
    private static final AtomicBoolean WORLD_REFRESH_QUEUED = new AtomicBoolean();
    private static final ClassValue<SodiumQuadAccess> SODIUM_QUAD_ACCESS = new ClassValue<>() {
        @Override
        protected SodiumQuadAccess computeValue(Class<?> type) {
            try {
                return new SodiumQuadAccess(
                    type.getMethod("baseColor", int.class),
                    type.getMethod("setColor", int.class, int.class),
                    type.getMethod("setRenderType", ChunkSectionLayer.class)
                );
            } catch (ReflectiveOperationException ignored) {
                return SodiumQuadAccess.UNAVAILABLE;
            }
        }
    };
    private static final ClassValue<java.util.Optional<Method>> SODIUM_REGION_RESOURCES = new ClassValue<>() {
        @Override
        protected java.util.Optional<Method> computeValue(Class<?> type) {
            try {
                return java.util.Optional.of(type.getMethod("getResources"));
            } catch (ReflectiveOperationException ignored) {
                return java.util.Optional.empty();
            }
        }
    };

    private ModuleRenderUtil() {
    }

    public static boolean xrayActive() {
        return xrayRenderWork;
    }

    public static int effectiveRenderChunkRadius() {
        if (MC == null || MC.options == null) return 8;
        return Math.max(1, MC.options.getEffectiveRenderDistance());
    }

    public static boolean hasXrayRenderWork() {
        return xrayRenderWork;
    }

    public static boolean hasWorldDarkenWork() {
        return worldDarkenWork;
    }

    public static int worldDarkenTint(BlockState state, BlockPos pos) {
        WorldDarkenSnapshot snapshot = worldDarkenSnapshot();
        if (!snapshot.active() || state == null || state.isAir()) return -1;
        boolean matched = matchesWorldDarkenList(state, snapshot);
        boolean darken = snapshot.whitelist() ? matched : !matched;
        return darken ? snapshot.tint() : -1;
    }

    public static boolean shouldBypassOcclusionCulling() {
        return xrayRenderWork || PackFreecamState.isActive();
    }

    public static boolean shouldUseFullbrightGamma() {
        return fullbrightGammaWork;
    }

    public static boolean shouldApplyFullbrightLuminance() {
        return fullbrightLuminanceWork;
    }

    public static boolean hasFullbrightLuminanceWork() {
        return fullbrightLuminanceWork;
    }

    public static boolean shouldUseBrightLightmap() {
        return brightLightmapWork;
    }

    public static boolean hasBrightLightmapWork() {
        return brightLightmapWork;
    }

    public static int fullbrightLuminance(LightLayer lightLayer) {
        FullbrightSnapshot snapshot = fullbrightSnapshot();
        if (!snapshot.luminance() || lightLayer == null) return 0;

        return lightLayer == LightLayer.SKY ? snapshot.skyLightValue()
            : lightLayer == LightLayer.BLOCK ? snapshot.blockLightValue() : 0;
    }

    public static boolean shouldRenderXrayBlock(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return !isXrayBlocked(xraySnapshot(), level, state, pos);
    }

    public static int xrayAlpha(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        XraySnapshot snapshot = xraySnapshot();
        if (!snapshot.active() || state == null || state.isAir()) return -1;
        if (!isXrayBlocked(snapshot, level, state, pos)) return -1;
        return snapshot.irisShaderPackInUse() ? 0 : snapshot.opacity();
    }

    public static int xrayAlpha(BlockState state, BlockPos pos) {
        return xrayAlpha(null, pos, state);
    }

    public static boolean modifyXrayFace(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos originalPos, boolean original) {
        XraySnapshot snapshot = xraySnapshot();
        if (!snapshot.active()) return original;
        if (!original && !isXrayBlocked(snapshot, level, state, originalPos)) {

            BlockPos adjPos = originalPos == null ? null : originalPos.relative(direction);
            BlockState adjState = level == null || adjPos == null ? null : level.getBlockState(adjPos);
            return !hidesXrayFace(snapshot, level, adjState, adjPos, direction);
        }
        return original;
    }

    private static boolean hidesXrayFace(XraySnapshot snapshot, BlockGetter level, BlockState adjState, BlockPos adjPos, Direction direction) {
        return adjState != null
            && adjState.isSolidRender()
            && adjState.getFaceOcclusionShape(direction.getOpposite()) == net.minecraft.world.phys.shapes.Shapes.block()
            && !isXrayBlocked(snapshot, level, adjState, adjPos);
    }

    public static boolean shouldForceXrayFace(BlockState state, BlockState neighborState, Direction direction) {
        XraySnapshot snapshot = xraySnapshot();
        if (!snapshot.active() || isXrayBlocked(snapshot, null, state, null)) return false;
        return !hidesXrayFace(snapshot, null, neighborState, null, direction);
    }

    public static boolean isXrayBlocked(BlockState state, BlockPos pos) {
        return isXrayBlocked(xraySnapshot(), state, pos);
    }

    public static int xrayFluidAlpha(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return xrayFluidAlpha(fluidState, pos);
    }

    public static int xrayFluidAlpha(FluidState fluidState, BlockPos pos) {
        XraySnapshot snapshot = xraySnapshot();
        if (!snapshot.active() || fluidState == null || fluidState.isEmpty()) return -1;
        boolean water = fluidState.is(FluidTags.WATER);
        boolean lava = fluidState.is(FluidTags.LAVA);
        boolean apply = switch (snapshot.fluidOpacityMode()) {
            case "None" -> false;
            case "Water" -> water;
            case "Lava" -> lava;
            default -> water || lava;
        };
        if (!apply) return -1;
        BlockState fluidBlock = fluidState.createLegacyBlock();
        if (!isXrayBlocked(snapshot, fluidBlock, pos)) return -1;
        return snapshot.irisShaderPackInUse() ? 0 : snapshot.opacity();
    }

    public static boolean shouldForceXrayFluidSides() {
        return xrayActive();
    }

    public static boolean xrayUsesShaderCullMode() {
        XraySnapshot snapshot = xraySnapshot();
        return snapshot.active() && snapshot.irisShaderPackInUse();
    }

    public static boolean shouldKeepXrayFluidSide(BlockState neighborState) {
        return isXrayBlocked(xraySnapshot(), neighborState, null);
    }

    public static int sodiumFullLight() {
        return 15 | 15 << 4 | 15 << 8;
    }

    public static int sodiumBlockLight(int current, BlockState state, BlockPos pos) {
        XraySnapshot snapshot = xraySnapshot();
        if (snapshot.active() && !isXrayBlocked(snapshot, state, pos)) return sodiumFullLight();
        FullbrightSnapshot fullbright = fullbrightSnapshot();
        if (!fullbright.luminance() || !"BLOCK".equals(fullbright.lightType())) return current;
        return Math.max(current, fullbright.minimumLightLevel());
    }

    public static boolean sodiumRegionHasNoResources(Object region) {
        if (region == null) return true;
        Method getResources = SODIUM_REGION_RESOURCES.get(region.getClass()).orElse(null);
        if (getResources == null) return false;
        try {
            return getResources.invoke(region) == null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void applySodiumQuadAlpha(Object quad, int alpha) {
        if (quad == null || alpha < 0) return;
        SODIUM_QUAD_ACCESS.get(quad.getClass()).applyAlpha(quad, alpha);
    }

    public static void applySodiumQuadTint(Object quad, int tint) {
        if (quad == null) return;
        SODIUM_QUAD_ACCESS.get(quad.getClass()).applyTint(quad, tint);
    }

    public static void applySodiumQuadRenderLayer(Object quad, ChunkSectionLayer layer) {
        if (quad == null || layer == null) return;
        SODIUM_QUAD_ACCESS.get(quad.getClass()).applyRenderLayer(quad, layer);
    }

    public static Object sodiumTranslucentMaterial(Object fallback) {
        try {
            Class<?> defaultMaterials = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials");
            Field translucent = defaultMaterials.getField("TRANSLUCENT");
            Object value = translucent.get(null);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static final Object SODIUM_SORT_TYPE_UNAVAILABLE = new Object();
    private static volatile Object sodiumNoneSortType;

    public static Object sodiumNoneSortType(Object fallback) {
        Object resolved = sodiumNoneSortType;
        if (resolved == null) {
            resolved = resolveSodiumNoneSortType();
            sodiumNoneSortType = resolved;
        }
        return resolved == SODIUM_SORT_TYPE_UNAVAILABLE ? fallback : resolved;
    }

    private static Object resolveSodiumNoneSortType() {
        try {
            Class<?> sortType = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType");
            Object[] constants = sortType.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (constant instanceof Enum<?> value && "NONE".equals(value.name())) return constant;
                }
            }
        } catch (Throwable ignored) {

        }
        return SODIUM_SORT_TYPE_UNAVAILABLE;
    }

    public static void refreshWorldRenderer() {
        if (MC == null || MC.level == null) return;
        WORLD_REFRESH_QUEUED.set(true);
    }

    public static void flushWorldRendererRefresh() {
        if (!WORLD_REFRESH_QUEUED.getAndSet(false)) return;
        if (MC == null || MC.level == null || MC.levelExtractor == null) return;
        autismclient.util.SodiumTerrainPassGuard.armForTransition();
        MC.levelExtractor.allChanged();
    }

    public static int applyFullbrightLuminance(BlockAndLightGetter level, BlockPos pos, int packedBrightness) {
        FullbrightSnapshot snapshot = fullbrightSnapshot();
        if (!snapshot.luminance()) return packedBrightness;
        int sky = snapshot.skyLightValue();
        int block = snapshot.blockLightValue();
        int originalSky = level == null || pos == null ? 0 : level.getBrightness(LightLayer.SKY, pos);
        int originalBlock = level == null || pos == null ? 0 : level.getBrightness(LightLayer.BLOCK, pos);
        return net.minecraft.util.LightCoordsUtil.pack(Math.max(block, originalBlock), Math.max(sky, originalSky));
    }

    public static boolean shouldTrace(Entity entity) {
        return shouldRenderEntity(tracerSnapshot(), entity);
    }

    public static boolean hasWorldTracerWork() {
        return worldTracerWork;
    }

    public static boolean shouldEsp(Entity entity) {
        if (entity instanceof ItemEntity) return false;
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled()) return false;
        if (shouldSuppressEspForUi()) return false;
        return shouldRenderEntity(snapshot, entity) && espFadeAlpha(snapshot, entity) > 0.0;
    }

    public static int tracerColor(Entity entity) {
        return entityColor(tracerSnapshot(), entity, 0xCCFFFFFF);
    }

    public static int espColor(Entity entity) {
        EntityRenderSnapshot snapshot = espSnapshot();
        int color = entityColor(snapshot, entity, 0xCCFFFFFF);
        return withAlphaMultiplier(color, espFadeAlpha(snapshot, entity));
    }

    public static int espOutlineColor(Entity entity) {
        return espColor(entity) | 0xFF000000;
    }

    public static boolean hasChamsWork() {
        return chamsSnapshot().enabled();
    }

    public static void applyChams(Entity entity, net.minecraft.client.renderer.entity.state.EntityRenderState state) {
        if (!(state instanceof autismclient.util.AutismChamsHolder holder)) return;
        ChamsSnapshot snapshot = chamsSnapshot();
        if (!snapshot.enabled() || entity instanceof ItemEntity || !shouldRenderChams(snapshot, entity)) {
            holder.autism$setChams(false, 0, 0);
            return;
        }

        if (snapshot.hitEnabled() && autismclient.util.AutismChamsHit.isFlashing(entity)) {
            int hit = snapshot.hitColor() | 0xFF000000;
            holder.autism$setChams(true, hit, hit);
            return;
        }
        if (snapshot.textureMode()) {

            holder.autism$setChams(true, 0x00FFFFFF, 0x00FFFFFF);
            return;
        }
        int alpha = Math.round(255f * Math.max(0f, Math.min(100f, snapshot.opacity())) / 100f);
        holder.autism$setChams(true,
            (alpha << 24) | (snapshot.visibleColor() & 0xFFFFFF),
            (alpha << 24) | (snapshot.occludedColor() & 0xFFFFFF));
    }

    private static boolean shouldRenderChams(ChamsSnapshot snapshot, Entity entity) {
        if (snapshot == null || !snapshot.enabled()) return false;
        if (MC == null || MC.player == null || entity == null) return false;
        if (entity == MC.player) return false;
        if (AutismAntiBot.suppress(entity)) return false;
        if (entity == MC.getCameraEntity() && MC.options.getCameraType().isFirstPerson()) return false;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        if (!entity.shouldRender(camera.x, camera.y, camera.z)) return false;
        double maxDistance = snapshot.maxDistance();
        if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) return false;
        if (snapshot.entityIds().isEmpty() && snapshot.entityPaths().isEmpty()) return true;
        return snapshot.matchCache().computeIfAbsent(entity.getType(), type -> {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return snapshot.entityIds().contains(id) || snapshot.entityPaths().contains(id.getPath());
        });
    }

    public static boolean shouldItemEsp(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) return false;
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled()) return false;
        if (shouldSuppressEspForUi()) return false;
        return shouldRenderItem(snapshot, itemEntity) && itemEspFadeAlpha(snapshot, itemEntity) > 0.0;
    }

    public static int itemEspColor(Entity entity) {
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!(entity instanceof ItemEntity itemEntity)) return snapshot.color();
        int base = snapshot.dynamicColor()
            ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color())
            : snapshot.color();
        return withAlphaMultiplier(base, itemEspFadeAlpha(snapshot, itemEntity));
    }

    private static int dynamicItemColor(ItemEspSnapshot snapshot, net.minecraft.world.item.ItemStack stack, int fallbackArgb) {
        int alpha = (fallbackArgb >>> 24) & 0xFF;

        int rgb = stack == null || stack.isEmpty()
            ? 0xFFD76A
            : snapshot.rgbCache().computeIfAbsent(stack.getItem(), ignored -> computeItemRgb(stack));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int computeItemRgb(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0xFFD76A;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();

        if (path.contains("netherite")) return 0xB7AFA9;
        if (path.contains("diamond"))   return 0x4AE3D6;
        if (path.contains("emerald"))   return 0x2FE05A;
        if (path.contains("lapis"))     return 0x2A55E0;
        if (path.contains("redstone"))  return 0xFF3030;
        if (path.contains("amethyst"))  return 0xB48CF2;
        if (path.contains("copper"))    return 0xE08A5A;
        if (path.contains("gold") || path.contains("golden") || path.contains("raw_gold")) return 0xFCE24B;
        if (path.contains("iron"))      return 0xDADADA;
        if (path.contains("coal"))      return 0x2C2C2C;
        if (path.contains("quartz"))    return 0xF2EAE0;
        if (path.contains("melon"))     return 0x67C84B;
        if (path.contains("pumpkin"))   return 0xE08020;
        if (path.contains("ender"))     return 0x12B79A;
        if (path.contains("blaze"))     return 0xFFB52E;
        if (path.contains("slime"))     return 0x7FD45A;
        if (path.contains("bone"))      return 0xE9E4D0;
        if (path.contains("netherrack") || path.contains("nether_brick")) return 0x7A3B3B;

        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            try {
                int col = blockItem.getBlock().defaultMapColor().col;
                if (col != 0) return col & 0xFFFFFF;
            } catch (Throwable ignored) {  }
        }

        int hash = (id == null ? path.hashCode() : id.toString().hashCode());
        float hue = ((hash & 0x7FFFFFFF) % 360) / 360.0f;
        return java.awt.Color.HSBtoRGB(hue, 0.65f, 0.95f) & 0xFFFFFF;
    }

    public static int itemEspOutlineColor(Entity entity) {
        return itemEspColor(entity) | 0xFF000000;
    }

    public static int itemOutlineColorOrZero(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) return 0;
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) return 0;
        if (!shouldRenderItem(snapshot, itemEntity)) return 0;
        double fade = itemEspFadeAlpha(snapshot, itemEntity);
        if (fade <= 0.0) return 0;
        int base = snapshot.dynamicColor() ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color()) : snapshot.color();
        return withAlphaMultiplier(base, fade) | 0xFF000000;
    }

    public static int entityOutlineColorOrZero(Entity entity) {
        if (entity instanceof ItemEntity) return 0;
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) return 0;
        if (!shouldRenderEntity(snapshot, entity)) return 0;
        double fade = espFadeAlpha(snapshot, entity);
        if (fade <= 0.0) return 0;
        return withAlphaMultiplier(entityColor(snapshot, entity, 0xCCFFFFFF), fade) | 0xFF000000;
    }

    public static boolean shouldUseItemOutline() {
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode())) return false;
        return !shouldSuppressEspForUi();
    }

    public static boolean shouldUseEntityOutline() {
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode())) return false;
        return !shouldSuppressEspForUi();
    }

    public static boolean hasAnyOutlineWork() {
        return outlineWork;
    }

    public static boolean has2dEspWork() {
        return esp2dWork;
    }

    public static void refreshFastFlags() {
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        synchronized (ModuleRenderUtil.class) {
            xraySnapshot = buildXraySnapshot(revision, hidden);
            worldDarkenSnapshot = buildWorldDarkenSnapshot(revision, hidden);
            fullbrightSnapshot = buildFullbrightSnapshot(revision, hidden);
            espSnapshot = buildEntityRenderSnapshot("esp", revision, hidden, false);
            itemEspSnapshot = buildItemEspSnapshot(revision, hidden);
            tracerSnapshot = buildEntityRenderSnapshot("tracers", revision, hidden, true);
            chamsSnapshot = buildChamsSnapshot(revision, hidden);

            xrayRenderWork = xraySnapshot.active();
            worldDarkenWork = worldDarkenSnapshot.active();
            autismclient.util.SodiumTerrainPassGuard.setXrayActive(xrayRenderWork);
            fullbrightGammaWork = fullbrightSnapshot.gamma();
            fullbrightLuminanceWork = fullbrightSnapshot.luminance();
            brightLightmapWork = fullbrightGammaWork || xrayRenderWork;
            worldTracerWork = tracerSnapshot.enabled();
            outlineWork = (itemEspSnapshot.enabled() && "Shader".equals(itemEspSnapshot.mode()))
                || (espSnapshot.enabled() && "Shader".equals(espSnapshot.mode()));
            esp2dWork = espSnapshot.enabled() && "2D".equals(espSnapshot.mode());
        }
    }

    public static boolean shouldSuppressEspForUi() {
        if (PackHideState.isActive()) return true;
        if (MC == null) return false;
        if (MC.gui.screen() != null && !(MC.gui.screen() instanceof ChatScreen) && !(MC.gui.screen() instanceof InBedChatScreen)) return true;
        AutismOverlayManager overlays = AutismOverlayManager.get();
        return overlays.hasRegisteredOverlays() && overlays.hasVisibleOverlay();
    }

    private static boolean shouldRenderEntity(EntityRenderSnapshot snapshot, Entity entity) {
        if (snapshot == null || !snapshot.enabled()) return false;
        if (MC == null || MC.player == null || entity == null) return false;
        if (entity == MC.player) return false;
        if (AutismAntiBot.suppress(entity)) return false;

        if (TeamsModule.isFriendOrTeam(entity) && !snapshot.friendsEnabled()) return false;
        if (entity == MC.getCameraEntity() && MC.options.getCameraType().isFirstPerson()) return false;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        if (!entity.shouldRender(camera.x, camera.y, camera.z)) return false;
        double maxDistance = snapshot.maxDistance();
        if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) return false;
        if (snapshot.entityIds().isEmpty() && snapshot.entityPaths().isEmpty()) return true;

        return snapshot.matchCache().computeIfAbsent(entity.getType(), type -> {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return snapshot.entityIds().contains(id) || snapshot.entityPaths().contains(id.getPath());
        });
    }

    private static boolean shouldRenderItem(ItemEspSnapshot snapshot, ItemEntity entity) {
        if (snapshot == null || !snapshot.enabled()) return false;
        if (MC == null || MC.player == null || entity == null) return false;
        if (entity.getItem() == null || entity.getItem().isEmpty()) return false;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        if (!entity.shouldRender(camera.x, camera.y, camera.z)) return false;
        double maxDistance = snapshot.maxDistance();
        if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) return false;
        if (!snapshot.someOnly()) return true;

        return snapshot.matchCache().computeIfAbsent(entity.getItem().getItem(), item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return snapshot.itemIds().contains(id) || snapshot.itemPaths().contains(id.getPath());
        });
    }

    private static EntityRenderSnapshot espSnapshot() {
        EntityRenderSnapshot snapshot = espSnapshot;
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = espSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildEntityRenderSnapshot("esp", revision, hidden, false);
            espSnapshot = snapshot;
            return snapshot;
        }
    }

    private static EntityRenderSnapshot tracerSnapshot() {
        EntityRenderSnapshot snapshot = tracerSnapshot;
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = tracerSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildEntityRenderSnapshot("tracers", revision, hidden, true);
            tracerSnapshot = snapshot;
            return snapshot;
        }
    }

    private static ChamsSnapshot chamsSnapshot() {
        ChamsSnapshot snapshot = chamsSnapshot;
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = chamsSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildChamsSnapshot(revision, hidden);
            chamsSnapshot = snapshot;
            return snapshot;
        }
    }

    private static ChamsSnapshot buildChamsSnapshot(int revision, boolean hidden) {
        if (hidden) return ChamsSnapshot.inactive(revision, true);
        Module module = ModuleRegistry.get("chams");
        if (module == null || !module.isEnabled()) return ChamsSnapshot.inactive(revision, false);
        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (String entry : module.list("entities")) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
            if (identifier != null) ids.add(identifier);
            else paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized);
        }
        return new ChamsSnapshot(
            revision,
            false,
            true,
            "Texture".equals(module.value("style")),
            parseDouble(module.value("max-distance"), 128.0),
            Set.copyOf(ids),
            Set.copyOf(paths),
            color(module, "visible-color", 0xFF35FF5B),
            color(module, "occluded-color", 0xFFB030FF),
            (int) parseDouble(module.value("opacity"), 100.0),
            Boolean.parseBoolean(module.value("hit-color")),
            color(module, "hit-color-value", 0xFFFF3B3B),
            Boolean.parseBoolean(module.value("draw-armor")),
            new java.util.concurrent.ConcurrentHashMap<>()
        );
    }

    public static boolean chamsDrawArmor() {
        return chamsSnapshot().drawArmor();
    }

    private static ItemEspSnapshot itemEspSnapshot() {
        ItemEspSnapshot snapshot = itemEspSnapshot;
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = itemEspSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildItemEspSnapshot(revision, hidden);
            itemEspSnapshot = snapshot;
            return snapshot;
        }
    }

    private static ItemEspSnapshot buildItemEspSnapshot(int revision, boolean hidden) {
        if (hidden) return ItemEspSnapshot.inactive(revision, true);
        Module module = ModuleRegistry.get("item-esp");
        if (module == null || !module.isEnabled()) return ItemEspSnapshot.inactive(revision, false);
        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (String entry : module.list("items")) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
            if (identifier != null) ids.add(identifier);
            else paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized);
        }
        return new ItemEspSnapshot(
            revision,
            false,
            true,
            module.value("mode"),
            "Some".equals(module.value("items-mode")),
            parseDouble(module.value("max-distance"), 64.0),
            parseDouble(module.value("fade-distance"), 3.0),
            Set.copyOf(ids),
            Set.copyOf(paths),
            color(module, "color", 0xCCFFD76A),
            !"Static".equals(module.value("color-mode")),
            new java.util.concurrent.ConcurrentHashMap<>(),
            new java.util.concurrent.ConcurrentHashMap<>()
        );
    }

    private static EntityRenderSnapshot buildEntityRenderSnapshot(String moduleId, int revision, boolean hidden, boolean useMaxDistance) {
        if (hidden) return EntityRenderSnapshot.inactive(moduleId, revision, true);
        Module module = ModuleRegistry.get(moduleId);
        if (module == null || !module.isEnabled()) return EntityRenderSnapshot.inactive(moduleId, revision, false);
        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (String entry : module.list("entities")) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
            if (identifier != null) ids.add(identifier);
            else paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized);
        }
        return new EntityRenderSnapshot(
            moduleId,
            revision,
            false,
            true,
            module.value("mode"),
            useMaxDistance ? parseDouble(module.value("max-distance"), 256.0) : 0.0,
            parseDouble(module.value("fade-distance"), 3.0),
            Set.copyOf(ids),
            Set.copyOf(paths),
            color(module, "players-color", 0xCCFFFFFF),
            color(module, "monsters-color", 0xCCFF4A4A),
            color(module, "animals-color", 0xCC74FF8F),
            color(module, "water-animals-color", 0xCC66D9FF),
            color(module, "ambient-color", 0xCCB78CFF),
            color(module, "misc-color", 0xCCCCCCCC),
            Boolean.parseBoolean(module.value("distance-color")),
            parseDouble(module.value("color-distance"), 40.0),
            TeamsModule.visualTargetsFriends(moduleId),
            TeamsModule.friendsColor(),
            new java.util.concurrent.ConcurrentHashMap<>()
        );
    }

    private static boolean isXrayBlocked(XraySnapshot snapshot, BlockState state, BlockPos pos) {
        return isXrayBlocked(snapshot, null, state, pos);
    }

    private static boolean isXrayBlocked(XraySnapshot snapshot, BlockGetter level, BlockState state, BlockPos pos) {
        if (!snapshot.active() || state == null || state.isAir()) return false;
        return !(matchesBlockList(state, snapshot)
            && (!snapshot.exposedOnly() || pos == null || isExposed(snapshot, level, pos)));
    }

    private static boolean matchesBlockList(BlockState state, XraySnapshot snapshot) {
        if (state == null || state.isAir()) return false;

        if (snapshot.blockIds().isEmpty() && snapshot.blockPaths().isEmpty()) return false;

        Block block = state.getBlock();
        Boolean cached = snapshot.matchCache().get(block);
        if (cached != null) return cached;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean match = snapshot.blockIds().contains(id) || snapshot.blockPaths().contains(id.getPath());
        snapshot.matchCache().put(block, match);
        return match;
    }

    private static boolean matchesWorldDarkenList(BlockState state, WorldDarkenSnapshot snapshot) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        Boolean cached = snapshot.matchCache().get(block);
        if (cached != null) return cached;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean match = snapshot.blockIds().contains(id) || snapshot.blockPaths().contains(id.getPath());
        snapshot.matchCache().put(block, match);
        return match;
    }

    private static boolean matchesBlockList(BlockState state, List<String> entries) {
        if (state == null || state.isAir()) return false;
        if (entries == null || entries.isEmpty()) return false;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (String entry : entries) {
            String normalized = entry.toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.endsWith(":" + normalized)) return true;
        }
        return false;
    }

    private static boolean isExposed(XraySnapshot snapshot, BlockGetter level, BlockPos pos) {
        if (pos == null) return true;
        BlockGetter source = level != null ? level : MC == null ? null : MC.level;
        if (source == null) return true;
        ExposureMemo memo = EXPOSURE_MEMO.get();
        if (memo.revision != snapshot.revision()) {
            java.util.Arrays.fill(memo.filled, false);
            memo.revision = snapshot.revision();
        }
        long key = pos.asLong();
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 60) & ExposureMemo.MASK;
        if (memo.filled[slot] && memo.keys[slot] == key) return memo.exposed[slot];
        boolean exposed = computeExposed(source, pos, memo.cursor);
        memo.keys[slot] = key;
        memo.exposed[slot] = exposed;
        memo.filled[slot] = true;
        return exposed;
    }

    private static boolean computeExposed(BlockGetter level, BlockPos pos, BlockPos.MutableBlockPos cursor) {
        for (Direction direction : DIRECTIONS) {
            cursor.setWithOffset(pos, direction);
            BlockState neighbor = level.getBlockState(cursor);
            if (neighbor == null || neighbor.isAir() || !neighbor.isSolidRender()) return true;
            FluidState fluid = neighbor.getFluidState();
            if (fluid != null && !fluid.isEmpty()) return true;
        }
        return false;
    }

    private static final Direction[] DIRECTIONS = Direction.values();

    private static final ThreadLocal<ExposureMemo> EXPOSURE_MEMO = ThreadLocal.withInitial(ExposureMemo::new);

    private static final class ExposureMemo {
        static final int MASK = 15;
        final long[] keys = new long[MASK + 1];
        final boolean[] exposed = new boolean[MASK + 1];
        final boolean[] filled = new boolean[MASK + 1];
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int revision = Integer.MIN_VALUE;
    }

    private static int entityColor(EntityRenderSnapshot snapshot, Entity entity, int fallback) {
        if (snapshot == null || entity == null) return fallback;

        if (snapshot.friendsEnabled() && TeamsModule.isFriendOrTeam(entity)) return snapshot.friendsColor();
        if (snapshot.distanceColor()) return distanceColor(entity, snapshot.colorDistance());
        if (entity.getType() == EntityTypes.PLAYER) return snapshot.playersColor();
        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case MONSTER -> snapshot.monstersColor();
            case CREATURE -> snapshot.animalsColor();
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> snapshot.waterAnimalsColor();
            case AMBIENT -> snapshot.ambientColor();
            default -> snapshot.miscColor();
        };
    }

    private static int distanceColor(Entity entity, double span) {
        double distance = 0.0;
        if (MC != null && MC.gameRenderer != null && entity != null) {
            Vec3 camera = MC.gameRenderer.mainCamera().position();
            double dx = entity.getX() - camera.x;
            double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
            double dz = entity.getZ() - camera.z;
            distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return distanceHueColor(distance, span);
    }

    public static int distanceHueColor(double distance, double span) {
        if (span <= 0.0) span = 40.0;
        float t = (float) Math.max(0.0, Math.min(1.0, distance / span));
        float hue = t * (120.0f / 360.0f);
        return 0xFF000000 | (hsbToRgb(hue, 1.0f, 1.0f) & 0xFFFFFF);
    }

    private static int hsbToRgb(float h, float s, float b) {
        h = (h % 1.0f + 1.0f) % 1.0f;
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float p = b * (1.0f - s);
        float q = b * (1.0f - s * f);
        float t = b * (1.0f - s * (1.0f - f));
        float r, g, bl;
        switch (i % 6) {
            case 0 -> { r = b; g = t; bl = p; }
            case 1 -> { r = q; g = b; bl = p; }
            case 2 -> { r = p; g = b; bl = t; }
            case 3 -> { r = p; g = q; bl = b; }
            case 4 -> { r = t; g = p; bl = b; }
            default -> { r = b; g = p; bl = q; }
        }
        int ri = Math.round(r * 255.0f);
        int gi = Math.round(g * 255.0f);
        int bi = Math.round(bl * 255.0f);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static double espFadeAlpha(EntityRenderSnapshot snapshot, Entity entity) {
        if (MC == null || MC.gameRenderer == null || entity == null) return 1.0;
        double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
        if (fadeDistance <= 0.0) return 1.0;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        double dx = entity.getX() - camera.x;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
        double dz = entity.getZ() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double alpha = Math.min(1.0, distance / fadeDistance);
        return alpha <= 0.075 ? 0.0 : alpha;
    }

    private static double itemEspFadeAlpha(ItemEspSnapshot snapshot, ItemEntity entity) {
        if (MC == null || MC.gameRenderer == null || entity == null) return 1.0;
        double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
        if (fadeDistance <= 0.0) return 1.0;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        double dx = entity.getX() - camera.x;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
        double dz = entity.getZ() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double alpha = Math.min(1.0, distance / fadeDistance);
        return alpha <= 0.075 ? 0.0 : alpha;
    }

    private static int withAlphaMultiplier(int color, double multiplier) {
        int alpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * multiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static int color(Module module, String option, int fallback) {
        try {
            String value = module.value(option).replace("#", "");
            if (value.length() == 6) value = "CC" + value;
            return (int) Long.parseLong(value, 16);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isIrisShaderPackInUse() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            Object result = irisApiClass.getMethod("isShaderPackInUse").invoke(instance);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static XraySnapshot xraySnapshot() {
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        XraySnapshot snapshot = xraySnapshot;
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = xraySnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildXraySnapshot(revision, hidden);
            xraySnapshot = snapshot;
            return snapshot;
        }
    }

    private static WorldDarkenSnapshot worldDarkenSnapshot() {
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        WorldDarkenSnapshot snapshot = worldDarkenSnapshot;
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = worldDarkenSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildWorldDarkenSnapshot(revision, hidden);
            worldDarkenSnapshot = snapshot;
            return snapshot;
        }
    }

    private static FullbrightSnapshot fullbrightSnapshot() {
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        FullbrightSnapshot snapshot = fullbrightSnapshot;
        if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
        synchronized (ModuleRenderUtil.class) {
            snapshot = fullbrightSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) return snapshot;
            snapshot = buildFullbrightSnapshot(revision, hidden);
            fullbrightSnapshot = snapshot;
            return snapshot;
        }
    }

    private static FullbrightSnapshot buildFullbrightSnapshot(int revision, boolean hidden) {
        if (hidden) return FullbrightSnapshot.inactive(revision, true);
        Module module = ModuleRegistry.get("fullbright");
        if (module == null || !module.isEnabled()) return FullbrightSnapshot.inactive(revision, false);
        String mode = module.value("mode");
        boolean gamma = "Gamma".equals(mode);
        boolean luminance = "Luminance".equals(mode);
        String lightType = module.value("light-type");
        int minLight = Math.max(0, Math.min(15, parseInt(module.value("minimum-light-level"), 8)));
        return new FullbrightSnapshot(
            revision,
            false,
            gamma,
            luminance,
            lightType,
            minLight,
            "SKY".equals(lightType) ? minLight : 0,
            "BLOCK".equals(lightType) ? minLight : 0
        );
    }

    private static WorldDarkenSnapshot buildWorldDarkenSnapshot(int revision, boolean hidden) {
        if (hidden) return WorldDarkenSnapshot.inactive(revision, true);
        Module module = ModuleRegistry.get("world");
        if (module == null || !module.isEnabled() || !Boolean.parseBoolean(module.value("darken-blocks"))) {
            return WorldDarkenSnapshot.inactive(revision, false);
        }
        int darkness = Math.max(0, Math.min(100, parseInt(module.value("darkness"), 50)));
        if (darkness <= 0) return WorldDarkenSnapshot.inactive(revision, false);

        double t = darkness / 100.0;
        int picked = color(module, "tint-color", 0xFF000000);
        int cr = Math.max(0, Math.min(255, (int) Math.round(255 + ((((picked >> 16) & 0xFF) - 255) * t))));
        int cg = Math.max(0, Math.min(255, (int) Math.round(255 + ((((picked >> 8) & 0xFF) - 255) * t))));
        int cb = Math.max(0, Math.min(255, (int) Math.round(255 + (((picked & 0xFF) - 255) * t))));
        int tint = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
        boolean whitelist = "Whitelist".equalsIgnoreCase(module.value("mode"));
        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (String entry : module.list("blocks")) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
            if (identifier != null) ids.add(identifier);
            else paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized);
        }
        return new WorldDarkenSnapshot(revision, false, true, tint, whitelist, Set.copyOf(ids), Set.copyOf(paths),
            new java.util.concurrent.ConcurrentHashMap<>());
    }

    private static XraySnapshot buildXraySnapshot(int revision, boolean hidden) {
        if (hidden) return XraySnapshot.inactive(revision, true);
        Module module = ModuleRegistry.get("xray");
        if (module == null || !module.isEnabled()) return XraySnapshot.inactive(revision, false);

        if (!ModuleOreSim.tintActive(module)) return XraySnapshot.inactive(revision, false);
        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();

        boolean oreSim = ModuleOreSim.oreSimMode(module);
        if (!oreSim) {
            for (String entry : module.list("whitelist")) {
                if (entry == null) continue;
                String normalized = entry.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) continue;
                Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                if (identifier != null) ids.add(identifier);
                else paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized);
            }
        }
        return new XraySnapshot(
            revision,
            false,
            true,
            Math.max(0, Math.min(255, parseInt(module.value("opacity"), 25))),
            !oreSim && Boolean.parseBoolean(module.value("exposed-only")),
            isIrisShaderPackInUse(),
            module.value("fluid-opacity"),
            Set.copyOf(ids),
            Set.copyOf(paths),
            new java.util.concurrent.ConcurrentHashMap<>()
        );
    }

    private record XraySnapshot(
        int revision,
        boolean hidden,
        boolean active,
        int opacity,
        boolean exposedOnly,
        boolean irisShaderPackInUse,
        String fluidOpacityMode,
        Set<Identifier> blockIds,
        Set<String> blockPaths,

        java.util.concurrent.ConcurrentHashMap<Block, Boolean> matchCache
    ) {
        static XraySnapshot inactive(int revision, boolean hidden) {
            return new XraySnapshot(revision, hidden, false, -1, false, false, "Both", Set.of(), Set.of(),
                new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

    private record WorldDarkenSnapshot(
        int revision,
        boolean hidden,
        boolean active,
        int tint,
        boolean whitelist,
        Set<Identifier> blockIds,
        Set<String> blockPaths,

        java.util.concurrent.ConcurrentHashMap<Block, Boolean> matchCache
    ) {
        static WorldDarkenSnapshot inactive(int revision, boolean hidden) {
            return new WorldDarkenSnapshot(revision, hidden, false, -1, false, Set.of(), Set.of(),
                new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

    private record FullbrightSnapshot(
        int revision,
        boolean hidden,
        boolean gamma,
        boolean luminance,
        String lightType,
        int minimumLightLevel,

        int skyLightValue,
        int blockLightValue
    ) {
        static FullbrightSnapshot inactive(int revision, boolean hidden) {
            return new FullbrightSnapshot(revision, hidden, false, false, "", 0, 0, 0);
        }
    }

    private record EntityRenderSnapshot(
        String moduleId,
        int revision,
        boolean hidden,
        boolean enabled,
        String mode,
        double maxDistance,
        double fadeDistance,
        Set<Identifier> entityIds,
        Set<String> entityPaths,
        int playersColor,
        int monstersColor,
        int animalsColor,
        int waterAnimalsColor,
        int ambientColor,
        int miscColor,
        boolean distanceColor,
        double colorDistance,
        boolean friendsEnabled,
        int friendsColor,

        java.util.concurrent.ConcurrentHashMap<net.minecraft.world.entity.EntityType<?>, Boolean> matchCache
    ) {
        static EntityRenderSnapshot inactive(String moduleId, int revision, boolean hidden) {
            return new EntityRenderSnapshot(
                moduleId,
                revision,
                hidden,
                false,
                "",
                0.0,
                0.0,
                Set.of(),
                Set.of(),
                0xCCFFFFFF,
                0xCCFF4A4A,
                0xCC74FF8F,
                0xCC66D9FF,
                0xCCB78CFF,
                0xCCCCCCCC,
                false,
                40.0,
                true,
                TeamsModule.DEFAULT_FRIENDS_COLOR,
                new java.util.concurrent.ConcurrentHashMap<>()
            );
        }
    }

    private record ChamsSnapshot(
        int revision,
        boolean hidden,
        boolean enabled,
        boolean textureMode,
        double maxDistance,
        Set<Identifier> entityIds,
        Set<String> entityPaths,
        int visibleColor,
        int occludedColor,
        int opacity,
        boolean hitEnabled,
        int hitColor,
        boolean drawArmor,
        java.util.concurrent.ConcurrentHashMap<net.minecraft.world.entity.EntityType<?>, Boolean> matchCache
    ) {
        static ChamsSnapshot inactive(int revision, boolean hidden) {
            return new ChamsSnapshot(revision, hidden, false, false, 0.0,
                Set.of(), Set.of(), 0xFF35FF5B, 0xFFB030FF, 100, true, 0xFFFF3B3B, true,
                new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

    private record ItemEspSnapshot(
        int revision,
        boolean hidden,
        boolean enabled,
        String mode,
        boolean someOnly,
        double maxDistance,
        double fadeDistance,
        Set<Identifier> itemIds,
        Set<String> itemPaths,
        int color,
        boolean dynamicColor,

        java.util.concurrent.ConcurrentHashMap<Item, Integer> rgbCache,

        java.util.concurrent.ConcurrentHashMap<Item, Boolean> matchCache
    ) {
        static ItemEspSnapshot inactive(int revision, boolean hidden) {
            return new ItemEspSnapshot(
                revision,
                hidden,
                false,
                "",
                false,
                0.0,
                0.0,
                Set.of(),
                Set.of(),
                0xCCFFD76A,
                true,
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>()
            );
        }
    }

    private record SodiumQuadAccess(Method baseColor, Method setColor, Method setRenderType) {
        private static final SodiumQuadAccess UNAVAILABLE = new SodiumQuadAccess(null, null, null);

        private void applyAlpha(Object quad, int alpha) {
            if (baseColor == null || setColor == null) return;
            try {
                for (int i = 0; i < 4; i++) {
                    int color = (Integer) baseColor.invoke(quad, i);
                    setColor.invoke(quad, i, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
                }
            } catch (ReflectiveOperationException ignored) {  }
        }

        private void applyTint(Object quad, int tint) {
            if (baseColor == null || setColor == null) return;
            try {
                for (int i = 0; i < 4; i++) {
                    int color = (Integer) baseColor.invoke(quad, i);
                    setColor.invoke(quad, i, net.minecraft.util.ARGB.multiply(color, tint));
                }
            } catch (ReflectiveOperationException ignored) {  }
        }

        private void applyRenderLayer(Object quad, ChunkSectionLayer layer) {
            if (setRenderType == null) return;
            try {
                setRenderType.invoke(quad, layer);
            } catch (ReflectiveOperationException ignored) {  }
        }
    }

}
