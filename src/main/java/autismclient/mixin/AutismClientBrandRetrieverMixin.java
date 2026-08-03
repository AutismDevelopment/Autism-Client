package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.security.AutismProtector;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class AutismClientBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void autism$spoofClientBrand(CallbackInfoReturnable<String> cir) {

        if (AutismProtector.isFullExternalProtectorPresent()) return;

        AutismModule module = AutismModule.get();
        if (module != null && module.isSpoofClientVanilla()) {
            cir.setReturnValue("vanilla");
            return;
        }

        if (AutismProtector.shouldSpoofBrand()) {
            cir.setReturnValue(AutismProtector.getEffectiveBrand());
        }
    }
}
