package autismclient.modules;
import autismclient.api.module.*;

import autismclient.mixin.accessor.AutismItemStackRenderStateAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GoldenLeverModule extends Module {
    public static final int GOLD_TINT = 0xFFFFD24A;
    private static volatile boolean active;
    private static volatile boolean femaleBody;
    private static volatile boolean femaleBodySelfOnly;
    private static volatile boolean femaleBodyOnlyOthers;
    private static volatile boolean femaleBodyCustomPlayers;
    private static volatile boolean femaleBodyCustomIncludeSelf;
    private static volatile Set<String> femaleBodyPlayerNames = Set.of();

    public GoldenLeverModule() {
        super("golden-lever", "GoldenLever", ModuleCategory.MISC, "Renames and recolors vanilla levers.");
        add(new BoolSetting("female-body", "Female Body", false)
            .group("Appearance")
            .description("Shape player bodies."));
        add(new BoolSetting("female-body-self-only", "Self Only", true)
            .group("Appearance")
            .description("Only affect you.")
            .visibleWhen(() -> Boolean.parseBoolean(value("female-body"))));
        add(new BoolSetting("female-body-only-others", "Only Others", false)
            .group("Appearance")
            .description("Only affect others.")
            .visibleWhen(() -> Boolean.parseBoolean(value("female-body"))
                && !Boolean.parseBoolean(value("female-body-self-only"))
                && !Boolean.parseBoolean(value("female-body-custom-players"))));
        add(new BoolSetting("female-body-custom-players", "Custom Players", false)
            .group("Appearance")
            .description("Use selected players.")
            .visibleWhen(() -> Boolean.parseBoolean(value("female-body"))
                && !Boolean.parseBoolean(value("female-body-self-only"))));
        add(new BoolSetting("female-body-custom-include-self", "Include Self", true)
            .group("Appearance")
            .description("Also include you.")
            .visibleWhen(() -> Boolean.parseBoolean(value("female-body"))
                && !Boolean.parseBoolean(value("female-body-self-only"))
                && Boolean.parseBoolean(value("female-body-custom-players"))));
        add(new StringListSetting("female-body-player-names", "Players", "")
            .group("Appearance")
            .description("Choose player names.")
            .playerNameList()
            .visibleWhen(() -> Boolean.parseBoolean(value("female-body"))
                && !Boolean.parseBoolean(value("female-body-self-only"))
                && Boolean.parseBoolean(value("female-body-custom-players"))));
        active = isEnabled();
        refreshFemaleBodySettings();
    }

    @Override
    public void onEnable() {
        active = true;
        ModuleRenderUtil.refreshWorldRenderer();
    }

    @Override
    public void onDisable() {
        active = false;
        ModuleRenderUtil.refreshWorldRenderer();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if (optionId != null && optionId.startsWith("female-body")) refreshFemaleBodySettings();
    }

    @Override
    protected void onSettingsReset() {
        femaleBody = false;
        femaleBodySelfOnly = true;
        femaleBodyOnlyOthers = false;
        femaleBodyCustomPlayers = false;
        femaleBodyCustomIncludeSelf = true;
        femaleBodyPlayerNames = Set.of();
    }

    public static boolean isStylingActive() {
        return active;
    }

    public static boolean isFemaleBodyActive() {
        return active && femaleBody && !PackHideState.isHardLocked();
    }

    public static boolean shouldApplyFemaleBody(int entityId) {
        if (!isFemaleBodyActive()) return false;
        if (MC.player == null) return false;
        boolean self = MC.player.getId() == entityId;
        if (femaleBodySelfOnly) return self;
        if (!femaleBodyCustomPlayers) return !femaleBodyOnlyOthers || !self;
        if (self) return femaleBodyCustomIncludeSelf;
        if (femaleBodyPlayerNames.isEmpty() || MC.level == null) return false;
        var entity = MC.level.getEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)
            || player.getGameProfile() == null || player.getGameProfile().name() == null) return false;
        return femaleBodyPlayerNames.contains(normalizePlayerName(player.getGameProfile().name()));
    }

    private void refreshFemaleBodySettings() {
        femaleBody = bool("female-body");
        femaleBodySelfOnly = bool("female-body-self-only");
        femaleBodyOnlyOthers = bool("female-body-only-others");
        femaleBodyCustomPlayers = bool("female-body-custom-players");
        femaleBodyCustomIncludeSelf = bool("female-body-custom-include-self");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String entry : list("female-body-player-names")) {
            String normalized = normalizePlayerName(entry);
            if (!normalized.isEmpty()) names.add(normalized);
        }
        femaleBodyPlayerNames = Set.copyOf(names);
    }

    private static String normalizePlayerName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean shouldStyle(ItemStack stack) {
        return isActive() && stack != null && stack.is(Items.LEVER);
    }

    public static boolean shouldStyle(BlockState state) {
        return isActive() && state != null && state.is(Blocks.LEVER);
    }

    public static Component leverName() {
        return Component.literal("Golden Lever").withStyle(ChatFormatting.GOLD);
    }

    public static void tintItemStackRenderState(ItemStackRenderState output) {
        if (output == null) return;
        AutismItemStackRenderStateAccessor accessor = (AutismItemStackRenderStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers = accessor.autism$getLayers();
        int count = Math.min(accessor.autism$getActiveLayerCount(), layers.length);
        for (int i = 0; i < count; i++) {
            tintLayer(layers[i]);
        }
    }

    private static void tintLayer(ItemStackRenderState.LayerRenderState layer) {
        if (layer == null) return;
        List<BakedQuad> quads = layer.prepareQuadList();
        for (int i = 0; i < quads.size(); i++) {
            quads.set(i, withTintIndex(quads.get(i)));
        }
        IntList tints = layer.tintLayers();
        tints.clear();
        tints.add(GOLD_TINT);
    }

    private static BakedQuad withTintIndex(BakedQuad quad) {
        BakedQuad.MaterialInfo materialInfo = quad.materialInfo();
        if (materialInfo.tintIndex() == 0) return quad;
        BakedQuad.MaterialInfo tintedInfo = new BakedQuad.MaterialInfo(
            materialInfo.sprite(),
            materialInfo.layer(),
            materialInfo.itemRenderType(),
            0,
            materialInfo.shade(),
            materialInfo.lightEmission()
        );
        return new BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            quad.packedUV0(),
            quad.packedUV1(),
            quad.packedUV2(),
            quad.packedUV3(),
            quad.direction(),
            tintedInfo
        );
    }

    private static boolean isActive() {
        return active;
    }
}
