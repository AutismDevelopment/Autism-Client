package autismclient.mixin;

import autismclient.AutismClientAddon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(ModelManager.class)
public class AutismModelManagerMixin {
    @Unique private static long autism$lastBrokenItemModelWarningMs;

    @WrapOperation(
        method = "getItemModel",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object autism$guardBrokenDynamicItemModel(
            Map<Identifier, ItemModel> models,
            Object id,
            Object fallback,
            Operation<Object> original) {
        try {
            Object model = original.call(models, id, fallback);
            return model != null ? model : fallback;
        } catch (RuntimeException error) {
            if (!autism$isNullDynamicModelLoad(error)) throw error;

            long now = System.currentTimeMillis();
            if (now - autism$lastBrokenItemModelWarningMs > 10_000L) {
                autism$lastBrokenItemModelWarningMs = now;
                AutismClientAddon.LOG.warn("[AUTISM] Resource/model pack returned a null item model for {}. Falling back to Minecraft's missing item model.", id);
            }
            return fallback;
        }
    }

    @Unique
    private static boolean autism$isNullDynamicModelLoad(Throwable error) {
        String className = error.getClass().getName();
        if (className.contains("CacheLoader$InvalidCacheLoadException")) return true;
        String message = error.getMessage();
        return message != null && message.contains("CacheLoader returned null");
    }
}
