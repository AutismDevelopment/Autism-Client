package autismclient.mixin;

import autismclient.util.AutismCiphertextTap;
import autismclient.util.AutismNetworkCaptureState;
import autismclient.util.AutismPacketCapture;
import autismclient.util.multi.MultiConnectionContext;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(Connection.class)
public abstract class AutismConnectionEncryptionMixin {
    @Shadow private Channel channel;

    @Inject(method = "setEncryptionKey", at = @At("TAIL"))
    private void autism$observeEncryptionBoundary(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (MultiConnectionContext.isMulti((Connection) (Object) this)) return;
        if (channel == null) return;
        if (AutismNetworkCaptureState.capturesPlaintext()) {
            AutismPacketCapture.markEncryptionEnabled(channel);
        }
        try {
            if (channel.pipeline().get("autism_ciphertext_in") == null && channel.pipeline().get("decrypt") != null) {
                channel.pipeline().addBefore("decrypt", "autism_ciphertext_in", new AutismCiphertextTap("S2C"));
            }
            if (channel.pipeline().get("autism_ciphertext_out") == null && channel.pipeline().get("encrypt") != null) {
                channel.pipeline().addBefore("encrypt", "autism_ciphertext_out", new AutismCiphertextTap("C2S"));
            }
        } catch (Throwable ignored) {  }
    }
}
