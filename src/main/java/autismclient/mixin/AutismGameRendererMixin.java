package autismclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import autismclient.modules.ModuleRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AutismGameRendererMixin {
    @ModifyExpressionValue(
        method = {"update", "extract", "render"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;")
    )
    private ClientLevel autism$treatLevelAsUnavailableWithoutPlayer(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : level;
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void autism$onRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            ci.cancel();
            return;
        }
        if (!autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.LEVEL_RENDER)) return;
        ModuleRegistry.onRenderLevel(deltaTracker.getGameTimeDeltaPartialTick(false));
    }

}
