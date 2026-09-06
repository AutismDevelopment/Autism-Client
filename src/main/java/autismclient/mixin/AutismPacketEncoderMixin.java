package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismNetworkCaptureState;
import autismclient.util.AutismPacketCapture;
import autismclient.util.multi.MultiConnectionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PacketEncoder.class)
public abstract class AutismPacketEncoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void autism$captureEncodedPlaintext(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output,
                                                 Operation<Void> original) {
        long captureState = AutismNetworkCaptureState.state();
        if (AutismNetworkCaptureState.mode(captureState) == 0) {
            original.call(ctx, packet, output);
            return;
        }
        boolean multi = ctx != null && MultiConnectionContext.isMulti(ctx.channel());
        boolean suppressPayloadCodec = multi && AutismNetworkCaptureState.capturesPayloads(captureState);
        if (suppressPayloadCodec) AutismNetworkCaptureState.beginMultiCodecSuppression();
        try {
            original.call(ctx, packet, output);
        } finally {
            if (suppressPayloadCodec) AutismNetworkCaptureState.endMultiCodecSuppression();
        }
        if (multi) return;
        AutismModule module = AutismModule.get();
        if (module == null) return;
        String protocol = protocolInfo.id().id();
        if (AutismNetworkCaptureState.capturesPlaintext(captureState)) {
            AutismPacketCapture.capturePlaintext(packet, "C2S", protocol, packet.type(), output);
        }
        if (AutismNetworkCaptureState.capturesPayloads(captureState) && autism$isPayloadCarrier(packet)) {
            module.captureDecodedPayloadPacket(packet, "C2S", protocol, "encoder");
        }
    }

    @Unique
    private static boolean autism$isPayloadCarrier(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
            || packet instanceof net.minecraft.network.protocol.BundlePacket<?>;
    }
}
