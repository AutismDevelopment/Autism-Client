package autismclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerNameResolver.class)
public abstract class AutismServerNameResolverMixin {
    @ModifyExpressionValue(method = "resolveAddress", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/resolver/AddressCheck;isAllowed(Lnet/minecraft/client/multiplayer/resolver/ResolvedServerAddress;)Z"))
    private boolean autism$allowResolved(boolean original) {
        return true;
    }

    @ModifyExpressionValue(method = "resolveAddress", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/resolver/AddressCheck;isAllowed(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Z"))
    private boolean autism$allowAddress(boolean original) {
        return true;
    }
}
