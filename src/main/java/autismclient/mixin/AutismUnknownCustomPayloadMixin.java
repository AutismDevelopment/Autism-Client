package autismclient.mixin;

import autismclient.util.AutismPayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DiscardedPayload.class)
public abstract class AutismUnknownCustomPayloadMixin {
    @Inject(method = "codec", at = @At("RETURN"), cancellable = true)
    private static <T extends FriendlyByteBuf> void autism$wrapUnknownCodec(Identifier id, int maxBytes,
                                                                            CallbackInfoReturnable<StreamCodec<T, DiscardedPayload>> cir) {
        StreamCodec<T, DiscardedPayload> original = cir.getReturnValue();
        if (original == null) return;
        @SuppressWarnings("unchecked")
        StreamCodec<T, DiscardedPayload> wrapped = (StreamCodec<T, DiscardedPayload>) AutismPayloadSupport.wrapUnknownCodec(id, maxBytes, original);
        cir.setReturnValue(wrapped);
    }
}
