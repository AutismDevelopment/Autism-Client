package autismclient.mixin;

import autismclient.modules.FreeLookModule;
import autismclient.modules.ModuleRenderUtil;
import autismclient.modules.PackFreecamState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class AutismCameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void move(float zoom, float yOffset, float xOffset);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);

    @Inject(
        method = "update(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void autism$freecamCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PackFreecamState.isActive()) return;
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(new Vec3(PackFreecamState.getX(partialTicks), PackFreecamState.getY(partialTicks), PackFreecamState.getZ(partialTicks)));
        setRotation(PackFreecamState.getYaw(partialTicks), PackFreecamState.getPitch(partialTicks));
    }

    @Inject(
        method = "update(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void autism$freeLookCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreeLookModule freeLook = FreeLookModule.lookingInstance();
        if (freeLook == null || PackFreecamState.isActive()) return;
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(new Vec3(
            Mth.lerp(partialTicks, entity.xo, entity.getX()),
            Mth.lerp(partialTicks, entity.yo, entity.getY()) + entity.getEyeHeight(),
            Mth.lerp(partialTicks, entity.zo, entity.getZ())));
        if (freeLook.isInvertedView()) {
            setRotation(freeLook.cameraYaw() + 180.0F, -freeLook.cameraPitch());
        } else {
            setRotation(freeLook.cameraYaw(), freeLook.cameraPitch());
        }
        float scale = entity instanceof LivingEntity living ? living.getScale() : 1.0F;
        move(-getMaxZoom(4.0F * scale), 0.0F, 0.0F);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autism$disableSmartCull(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        if (ModuleRenderUtil.shouldBypassOcclusionCulling()) {
            cameraState.smartCull = false;
        }
    }
}
