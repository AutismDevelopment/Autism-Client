package autismclient.mixin;

import autismclient.modules.FreeLookModule;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismCpsTracker;
import autismclient.util.AutismMouseInputSimulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class AutismMouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique private float autism$turnStartYaw;
    @Unique private float autism$turnStartPitch;
    @Unique private boolean autism$turnHadPlayer;

    @Unique private double autism$simulatedDX;
    @Unique private double autism$simulatedDY;

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void autism$freeLookTurn(LocalPlayer player, double x, double y, Operation<Void> original) {
        double assistX = autism$simulatedDX;
        double assistY = autism$simulatedDY;
        if (assistX != 0.0D || assistY != 0.0D) {
            autism$simulatedDX = 0.0D;
            autism$simulatedDY = 0.0D;
            if (FreeLookModule.consumeMouseTurn(x, y)) {

                original.call(player, assistX, assistY);
                return;
            }

            original.call(player, x + assistX, y + assistY);
            return;
        }
        if (FreeLookModule.consumeMouseTurn(x, y)) return;
        original.call(player, x, y);
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void autism$clearQueuedMouseInputWhenUnavailable(CallbackInfo ci) {
        AutismMouseInputSimulator.clearIfUnavailable();
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void autism$trackCps(long window, net.minecraft.client.input.MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS || button == null) return;
        if (minecraft == null || minecraft.gui.screen() != null) return;
        int b = button.button();
        if (b == 0) AutismCpsTracker.recordLeft();
        else if (b == 1) AutismCpsTracker.recordRight();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void autism$freecamScrollSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {

        if (autismclient.modules.AutoTotemModule.operationActive()
            || autismclient.modules.AutoArmorModule.operationActive()) {
            ci.cancel();
            return;
        }

        if (autismclient.modules.PackFreecamState.onMouseScroll(yOffset)) {
            ci.cancel();
            return;
        }

        if (minecraft != null && minecraft.gui.screen() == null
            && autismclient.util.multi.MultiPilot.handleHotbarScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleAccumulatedMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V")
    )
    private void autism$applyQueuedRawMouseInput(CallbackInfo ci) {
        AutismMouseInputSimulator.Delta delta = AutismMouseInputSimulator.consume();
        if (AutismMouseInputSimulator.hasExclusiveInput()) {

            accumulatedDX = delta.x();
            accumulatedDY = delta.y();
            return;
        }
        if (delta.isZero()) return;
        if (minecraft.player != null && FreeLookModule.lookingInstance() != null) {

            autism$simulatedDX += delta.x();
            autism$simulatedDY += delta.y();
            return;
        }
        accumulatedDX += autism$additiveAssist(accumulatedDX, delta.x());
        accumulatedDY += autism$additiveAssist(accumulatedDY, delta.y());
    }

    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean autism$avoidDoubleSmoothingTelly(boolean original) {
        return original && !AutismMouseInputSimulator.hasExclusiveInput(
            AutismMouseInputSimulator.Source.SCAFFOLD_TELLY);
    }

    @Unique
    private static double autism$additiveAssist(double realInput, double assistInput) {
        if (Math.abs(assistInput) < 1.0E-7) return 0.0;
        if (Math.abs(realInput) < 1.0E-4) return assistInput;
        return Math.signum(realInput) == Math.signum(assistInput) ? assistInput : 0.0;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void autism$swallowTurnDuringTotemOperation(double deltaTime, CallbackInfo ci) {
        if (!autismclient.modules.AutoTotemModule.operationActive()
            && !autismclient.modules.AutoArmorModule.movementInputPaused()) return;
        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
        autism$simulatedDX = 0.0D;
        autism$simulatedDY = 0.0D;
        AutismMouseInputSimulator.clear();
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void autism$beforeTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.MOUSE_ROTATION)) {
            autism$turnHadPlayer = false;
            return;
        }
        autism$turnHadPlayer = minecraft != null && minecraft.player != null;
        if (autism$turnHadPlayer) {
            autism$turnStartYaw = minecraft.player.getYRot();
            autism$turnStartPitch = minecraft.player.getXRot();
        }
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void autism$afterTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!autism$turnHadPlayer || minecraft == null || minecraft.player == null) return;
        double deltaYaw = minecraft.player.getYRot() - autism$turnStartYaw;
        double deltaPitch = minecraft.player.getXRot() - autism$turnStartPitch;
        if (Math.abs(deltaYaw) > 1.0E-6 || Math.abs(deltaPitch) > 1.0E-6) {
            ModuleRegistry.onMouseRotation(deltaYaw, deltaPitch);
        }
    }
}
