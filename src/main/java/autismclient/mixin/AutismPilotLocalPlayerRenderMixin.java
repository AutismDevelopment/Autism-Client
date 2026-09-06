package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class AutismPilotLocalPlayerRenderMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void autism$useBotObservedEntityPosition(Entity entity, Frustum frustum,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     CallbackInfoReturnable<Boolean> cir) {
        Boolean visible = MultiPilot.isEntityVisibleFromTruth(entity, frustum, cameraX, cameraY, cameraZ);
        if (visible != null) cir.setReturnValue(visible);
    }

    @Redirect(
        method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;", ordinal = 3))
    private Entity autism$renderOwnBodyWhilePiloting(Camera camera) {
        if (MultiPilot.isActive() || autismclient.util.AutismRemoteView.isActive()) {
            LocalPlayer self = Minecraft.getInstance().player;
            if (self != null) return self;
        }
        return camera.entity();
    }
}
