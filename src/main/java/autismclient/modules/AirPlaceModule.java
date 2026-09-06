package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismPlacementTick;
import autismclient.util.multi.MultiPilot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AirPlaceModule extends Module {
    static final String MODULE_DESCRIPTION = "Places blocks in air.";
    static final String RANGE_TIP = "Set placement reach";
    static final String GUIDE_TIP = "Show placement guide";
    static final String FILL_TIP = "Fill placement guide";
    static final String COLOR_TIP = "Set guide color";

    public record Placement(BlockHitResult hit, InteractionHand hand) {}

    private volatile BlockPos renderTarget;

    AirPlaceModule() {
        super("air-place", "AirPlace", ModuleCategory.PLAYER, MODULE_DESCRIPTION);
        add(new DoubleSetting("range", "Range", 5.0D, 1.0D, 6.0D, 0.05D)
            .description(RANGE_TIP).build());
        add(new BoolSetting("guide", "Guide", true)
            .description(GUIDE_TIP).build());
        add(new BoolSetting("fill", "Fill", true)
            .description(FILL_TIP).build());
        add(new ColorSetting("guide-color", "Guide Color", 0xFFFF3B3B)
            .description(COLOR_TIP).build());
    }

    @Override
    public void onDisable() {
        renderTarget = null;
    }

    @Override
    public void tick() {
        renderTarget = null;
        if (MC == null || MC.level == null) return;
        Player player = MultiPilot.commandPlayer();
        Placement placement = placement(player, MC.hitResult, decimal("range"));
        if (placement == null || ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), id())) {
            return;
        }
        if (bool("guide")) {
            renderTarget = placement.hit().getBlockPos().immutable();
        }

        executePlacement(placement);
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand ignoredHand) {
        if (MC == null || MC.level == null || MC.player == null || MC.gameMode == null || MultiPilot.isActive()) {
            return false;
        }
        Placement placement = placement(MC.player, hitResult, decimal("range"));
        if (placement == null) return false;
        if (MC.player.isHandsBusy()) return true;
        if (ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), id())) return true;

        return true;
    }

    private void executePlacement(Placement placement) {
        if (MC.player == null || MC.gameMode == null || MultiPilot.isActive()) return;

        if (!MC.options.keyUse.isDown()) return;

        if (((AutismMinecraftAccessor) MC).autism$getRightClickDelay() > 0) return;

        if (AutismBlinkManager.holdsActionsWithoutMovement()) return;

        if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
        try {

            if (!AutismPlacementTick.claim(id())) return;
            simulateVanillaUse(placement.hit());
            ((AutismMinecraftAccessor) MC).autism$setRightClickDelay(4);
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
    }

    private void simulateVanillaUse(BlockHitResult hit) {
        for (InteractionHand hand : InteractionHand.values()) {

            if (hand == InteractionHand.OFF_HAND && AutismHandArbiter.offhandClaimedByOther(id())) continue;
            ItemStack stack = MC.player.getItemInHand(hand);
            if (!stack.isItemEnabled(MC.level.enabledFeatures())) return;
            int oldCount = stack.getCount();
            InteractionResult blockResult = MC.gameMode.useItemOn(MC.player, hand, hit);
            if (blockResult instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) MC.player.swing(hand);
                if (!stack.isEmpty() && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials())) {
                    MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
                }
                return;
            }
            if (blockResult instanceof InteractionResult.Fail) return;

            if (stack.isEmpty()) continue;
            InteractionResult itemResult = MC.gameMode.useItem(MC.player, hand);
            if (!(itemResult instanceof InteractionResult.Success success)) continue;
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) MC.player.swing(hand);
            MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
            return;
        }
    }

    public BlockPos renderTarget() {
        return renderTarget;
    }

    public boolean renderFill() {
        return bool("fill");
    }

    public int guideColor() {
        try {
            String value = value("guide-color").replace("#", "");
            if (value.length() == 6) value = "FF" + value;
            return (int) Long.parseLong(value, 16);
        } catch (RuntimeException ignored) {
            return 0xFFFF3B3B;
        }
    }

    public static Placement activePlacement(Player player, HitResult currentHit) {
        Module module = ModuleRegistry.get("air-place");
        if (!(module instanceof AirPlaceModule airPlace) || !airPlace.isEnabled()) return null;
        return placement(player, currentHit, airPlace.decimal("range"));
    }

    static Placement placement(Player player, HitResult currentHit, double range) {
        if (player == null || player.level() == null || currentHit == null
            || currentHit.getType() != HitResult.Type.MISS || handsBusy(player)) return null;
        InteractionHand hand = placementHand(player);
        if (hand == null) return null;
        HitResult picked = player.pick(Math.max(1.0D, Math.min(6.0D, range)), 0.0F, false);
        if (!usesAirMiss(currentHit, picked) || !(picked instanceof BlockHitResult blockHit)) return null;
        BlockPos pos = blockHit.getBlockPos();
        if (player.level().isOutsideBuildHeight(pos) || !player.level().getBlockState(pos).canBeReplaced()) return null;
        return new Placement(blockHit, hand);
    }

    static InteractionHand placementHand(Player player) {
        if (player == null) return null;
        ItemStack main = player.getMainHandItem();
        if (isPlaceable(main, player)) return InteractionHand.MAIN_HAND;

        if (!main.isEmpty()) return null;
        return isPlaceable(player.getOffhandItem(), player) ? InteractionHand.OFF_HAND : null;
    }

    static boolean usesAirMiss(HitResult currentHit, HitResult pickedHit) {
        return currentHit != null && pickedHit instanceof BlockHitResult
            && currentHit.getType() == HitResult.Type.MISS && pickedHit.getType() == HitResult.Type.MISS;
    }

    private static boolean isPlaceable(ItemStack stack, Player player) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem
            && stack.isItemEnabled(player.level().enabledFeatures());
    }

    private static boolean handsBusy(Player player) {
        return player instanceof LocalPlayer local ? local.isHandsBusy() : player.isUsingItem();
    }

    static java.util.List<String> settingTips() {
        return java.util.List.of(RANGE_TIP, GUIDE_TIP, FILL_TIP, COLOR_TIP);
    }
}
