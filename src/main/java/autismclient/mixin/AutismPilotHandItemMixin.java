package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import autismclient.util.AutismRemoteView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public class AutismPilotHandItemMixin {

    private static AbstractClientPlayer autism$armOwner(AbstractClientPlayer fallback) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot instanceof AbstractClientPlayer clientBot ? clientBot : fallback;
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private AbstractClientPlayer autism$botArmOwner(AbstractClientPlayer player) {
        return autism$armOwner(player);
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"),
        require = 0)
    private AvatarRenderer<AbstractClientPlayer> autism$botArmRenderer(EntityRenderDispatcher dispatcher,
                                                                        AbstractClientPlayer player) {
        return dispatcher.getPlayerRenderer(autism$armOwner(player));
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"),
        require = 0)
    private PlayerSkin autism$botArmSkin(AbstractClientPlayer player) {
        return autism$armOwner(player).getSkin();
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"),
        require = 0)
    private boolean autism$botArmSleeve(AbstractClientPlayer player, PlayerModelPart part) {
        return autism$armOwner(player).isModelPartShown(part);
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"),
        require = 0)
    private AvatarRenderer<AbstractClientPlayer> autism$botMapArmRenderer(EntityRenderDispatcher dispatcher,
                                                                           AbstractClientPlayer player) {
        return dispatcher.getPlayerRenderer(autism$armOwner(player));
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"),
        require = 0)
    private PlayerSkin autism$botMapArmSkin(LocalPlayer player) {
        return autism$armOwner(player).getSkin();
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"),
        require = 0)
    private boolean autism$botMapArmSleeve(LocalPlayer player, PlayerModelPart part) {
        return autism$armOwner(player).isModelPartShown(part);
    }

    @Redirect(method = {"renderOneHandedMap", "renderTwoHandedMap"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"),
        require = 0)
    private boolean autism$botMapArmInvisible(LocalPlayer player) {
        return autism$armOwner(player).isInvisible();
    }

    @Redirect(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private ItemStack autism$tickMain(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
    }

    @Redirect(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private ItemStack autism$tickOff(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }

    @Redirect(method = "submitHandsWithItems",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"),
        require = 0)
    private float autism$botSwing(LocalPlayer player, float partialTicks) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getAttackAnim(partialTicks) : player.getAttackAnim(partialTicks);
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack autism$evalMain(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack autism$evalOff(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"),
        require = 0)
    private static boolean autism$evalUsing(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.isUsingItem() : player.isUsingItem();
    }

    @Redirect(method = "selectionUsingItemWhileHoldingBowLike",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
        require = 0)
    private static InteractionHand autism$bowHand(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getUsedItemHand() : player.getUsedItemHand();
    }

    @Redirect(method = "selectionUsingItemWhileHoldingBowLike",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack autism$bowOff(LocalPlayer player) {
        Player bot = AutismRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }
}
