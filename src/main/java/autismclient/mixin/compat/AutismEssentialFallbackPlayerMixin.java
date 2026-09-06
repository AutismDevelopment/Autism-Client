package autismclient.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "gg.essential.gui.common.UI3DPlayer$FallbackPlayer", remap = false)
public abstract class AutismEssentialFallbackPlayerMixin {
    @WrapMethod(method = "render")
    private void autism$skipBrokenFallbackFrame(
            @Coerce Object matrix,
            @Coerce Object commandQueue,
            @Coerce Object vertexConsumers,
            Operation<Void> original) {
        try {
            original.call(matrix, commandQueue, vertexConsumers);
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            if (message == null || !message.contains("\"o\" is null")) throw npe;
        }
    }
}
