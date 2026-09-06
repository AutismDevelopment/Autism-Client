package autismclient.mixin;

import autismclient.modules.ViewmodelState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public abstract class AutismViewmodelMixin {
    @Shadow
    private ItemStack offHandItem;

    @Shadow
    @Final
    private static float ITEM_POS_Y;

    @Inject(method = "submitArmWithItem",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER),
        require = 0)
    private void autism$handTransforms(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                       float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (!ViewmodelState.active()) return;

        boolean bothHands = hand == InteractionHand.MAIN_HAND && itemStack.has(DataComponents.MAP_ID) && offHandItem.isEmpty();
        boolean main = ViewmodelState.mainHandOn();
        boolean off = ViewmodelState.offHandOn();
        if (bothHands && main && off) {
            autism$applyTransformations(poseStack,
                (ViewmodelState.mainHandX() + ViewmodelState.offHandX()) / 2f,
                (ViewmodelState.mainHandY() + ViewmodelState.offHandY()) / 2f,
                (ViewmodelState.mainHandScale() + ViewmodelState.offHandScale()) / 2f,
                (ViewmodelState.mainHandRotX() + ViewmodelState.offHandRotX()) / 2f,
                (ViewmodelState.mainHandRotY() + ViewmodelState.offHandRotY()) / 2f,
                (ViewmodelState.mainHandRotZ() + ViewmodelState.offHandRotZ()) / 2f);
        } else if (bothHands && main) {
            poseStack.translate(0f, 0f, ViewmodelState.mainHandScale());
        } else if (hand == InteractionHand.MAIN_HAND && main) {
            autism$applyTransformations(poseStack, ViewmodelState.mainHandX(), ViewmodelState.mainHandY(),
                ViewmodelState.mainHandScale(), ViewmodelState.mainHandRotX(), ViewmodelState.mainHandRotY(),
                ViewmodelState.mainHandRotZ());
        } else if (off) {
            autism$applyTransformations(poseStack, ViewmodelState.offHandX(), ViewmodelState.offHandY(),
                ViewmodelState.offHandScale(), ViewmodelState.offHandRotX(), ViewmodelState.offHandRotY(),
                ViewmodelState.offHandRotZ());
        }
    }

    @Unique
    private static void autism$applyTransformations(PoseStack matrices, float tx, float ty, float tz,
                                                    float rx, float ry, float rz) {
        matrices.translate(tx, ty, tz);
        matrices.mulPose(Axis.XP.rotationDegrees(rx));
        matrices.mulPose(Axis.YP.rotationDegrees(ry));
        matrices.mulPose(Axis.ZP.rotationDegrees(rz));
    }

    @Unique
    private static void autism$applySwingOffset(PoseStack m, HumanoidArm arm, float swing) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin((float) (swing * swing * Math.PI));
        m.mulPose(Axis.YP.rotationDegrees(side * (45.0f + f * -20.0f)));
        float g = Mth.sin((float) (Mth.sqrt(swing) * Math.PI));
        m.mulPose(Axis.ZP.rotationDegrees(side * g * -20.0f));
        m.mulPose(Axis.XP.rotationDegrees(g * -80.0f));
        m.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
    }

    @Unique
    private static void autism$applyBlockAnimation(PoseStack m, HumanoidArm arm, float swing) {
        if (ViewmodelState.blockAnim() == 1) {
            m.translate(arm == HumanoidArm.RIGHT ? -0.1f : 0.1f, 0.1f, 0.0f);
            float g = Mth.sin((float) (Mth.sqrt(swing) * Math.PI));
            m.mulPose(Axis.ZP.rotationDegrees((arm == HumanoidArm.RIGHT ? 1 : -1) * g * 10.0f));
            m.mulPose(Axis.XP.rotationDegrees(g * -35.0f));
        } else {
            m.translate(arm == HumanoidArm.RIGHT ? -0.1f : 0.1f, ViewmodelState.oneSevenY(), 0.0f);
            autism$applySwingOffset(m, arm, swing * ViewmodelState.oneSevenSwingScale());
        }
    }

    @Inject(method = "submitArmWithItem",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;")),
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            ordinal = 0, shift = At.Shift.AFTER),
        require = 0)
    private void autism$blockAnimation(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                       float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (!ViewmodelState.active() || !itemStack.is(ItemTags.SWORDS)) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        autism$applyBlockAnimation(poseStack, arm, attack);
    }

    @ModifyArg(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
        ordinal = 3), index = 2, require = 0)
    private float autism$ignoreBlocking(float equipProgress) {
        if (ViewmodelState.equipOffsetOn() && ViewmodelState.ignoreBlocking()) return 0.0F;
        return equipProgress;
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$ignorePlace(InteractionHand hand, CallbackInfo ci) {
        if (ViewmodelState.active() && ViewmodelState.ignorePlace()) ci.cancel();
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"), cancellable = true, require = 0)
    private void autism$ignoreAmount(ItemStack currentlyVisibleItem, ItemStack expectedItem,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (ViewmodelState.active() && !cir.getReturnValueZ()) {
            cir.setReturnValue(!ViewmodelState.equipOffsetOn()
                || (currentlyVisibleItem.getCount() == expectedItem.getCount() || ViewmodelState.ignoreAmount())
                && ItemStack.isSameItemSameComponents(currentlyVisibleItem, expectedItem));
        }
    }

    @ModifyArg(method = "applyItemArmTransform",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 1, require = 0)
    private float autism$disableEquipOffset(float y) {
        if (ViewmodelState.active() && !ViewmodelState.equipOffsetOn()) return ITEM_POS_Y;
        return y;
    }
}
