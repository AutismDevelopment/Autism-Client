package autismclient.mixin;

import autismclient.util.macro.FpsLimitController;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.LockSupport;

@Mixin(FramerateLimiter.class)
public abstract class AutismFramerateLimiterMixin {

    private static final long FREEZE_RECHECK_NANOS = 20_000_000L;

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void autism$freezeWhenZero(int framerateLimit, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || !FpsLimitController.shouldFreeze()) {
            return;
        }

        while (FpsLimitController.shouldFreeze() && Minecraft.getInstance().level != null) {
            LockSupport.parkNanos(FREEZE_RECHECK_NANOS);
            if (Thread.interrupted()) break;
        }
        ci.cancel();
    }
}
