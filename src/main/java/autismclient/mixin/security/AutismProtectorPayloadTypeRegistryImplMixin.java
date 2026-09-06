package autismclient.mixin.security;

import autismclient.security.AutismProtectorModResolver;
import autismclient.security.AutismProtectorTracker;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(PayloadTypeRegistryImpl.class)
public class AutismProtectorPayloadTypeRegistryImplMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private void autism$trackPayloadDefaultMod(CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec,
                                               CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> cir) {
        for (String mod : AutismProtectorModResolver.modsFromStacktrace()) {
            AutismProtectorTracker.addDefaultAllowedMod(mod);
            AutismProtectorTracker.addDefaultAllowedMods(AutismProtectorModResolver.dependenciesFor(mod));
        }
    }
}
