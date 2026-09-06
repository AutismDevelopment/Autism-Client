package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismNetworkCaptureState;
import autismclient.util.AutismPacketCapture;
import autismclient.util.multi.MultiConnectionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class AutismPacketDecoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;

    @WrapMethod(method = "decode")
    private void autism$captureIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out,
                                                  Operation<Void> original) {
        long captureState = AutismNetworkCaptureState.state();
        if (AutismNetworkCaptureState.mode(captureState) == 0) {
            original.call(ctx, input, out);
            return;
        }
        int outSizeBeforeDecode = out == null ? 0 : out.size();
        boolean multi = ctx != null && MultiConnectionContext.isMulti(ctx.channel());
        boolean suppressPayloadCodec = multi && AutismNetworkCaptureState.capturesPayloads(captureState);
        if (suppressPayloadCodec) AutismNetworkCaptureState.beginMultiCodecSuppression();

        byte[] incomingPlaintext = !multi && AutismNetworkCaptureState.capturesPlaintext(captureState)
            ? AutismPacketCapture.copyReadableBytes(input)
            : AutismNetworkCaptureState.EMPTY_BYTES;
        try {
            original.call(ctx, input, out);
        } finally {
            if (suppressPayloadCodec) AutismNetworkCaptureState.endMultiCodecSuppression();
        }
        if (multi) return;

        if (out == null || out.isEmpty()) return;
        int start = Math.max(0, Math.min(outSizeBeforeDecode, out.size()));
        boolean capturePlaintext = AutismNetworkCaptureState.capturesPlaintext(captureState)
            && incomingPlaintext.length > 0;
        boolean capturePayloads = AutismNetworkCaptureState.capturesPayloads(captureState);
        AutismModule module = AutismModule.get();
        if (module == null) return;
        String protocol = protocolInfo.id().id();
        for (int i = start; i < out.size(); i++) {
            Object decoded = out.get(i);
            if (!(decoded instanceof Packet<?> packet)) continue;
            if (capturePlaintext) {
                AutismPacketCapture.capturePlaintextBytes(packet, "S2C", protocol, packet.type(),
                    incomingPlaintext);
            }
            if (capturePayloads && autism$isPayloadCarrier(packet)) {
                module.captureDecodedPayloadPacket(packet, "S2C", protocol, "decoder");
            }
        }
    }

    @Unique
    private static boolean autism$isPayloadCarrier(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
            || packet instanceof net.minecraft.network.protocol.BundlePacket<?>;
    }
}
