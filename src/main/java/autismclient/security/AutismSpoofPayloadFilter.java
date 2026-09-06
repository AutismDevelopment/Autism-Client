package autismclient.security;

import autismclient.modules.AutismModule;
import autismclient.util.AutismPayloadSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class AutismSpoofPayloadFilter extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof ServerboundCustomPayloadPacket packet) {
            if (shouldBlockForVanillaSpoof(AutismModule.get(), packet)) {
                AutismProtector.consumeUserBypass(packet);
                promise.setSuccess();
                return;
            }

            AutismProtectorChannelFilter.Verdict verdict = AutismProtectorChannelFilter.filter(packet);
            switch (verdict.kind) {
                case DROP -> {
                    AutismProtector.consumeUserBypass(packet);
                    promise.setSuccess();
                    return;
                }
                case REPLACE -> {
                    AutismProtector.consumeUserBypass(packet);
                    super.write(ctx, verdict.replacement, promise);
                    return;
                }
                case PASS -> AutismProtector.consumeUserBypass(packet);
            }
        }
        super.write(ctx, msg, promise);
    }

    public static boolean shouldBlockForVanillaSpoof(AutismModule module, Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayload)) return false;
        if (AutismProtector.isUserBypass(packet)) return false;
        if (module == null || !module.isSpoofClientVanilla()) return false;
        String channel = AutismPayloadSupport.payloadChannel(customPayload.payload());
        return !AutismPayloadSupport.isBrandChannel(channel);
    }

    public static boolean shouldDropForProtector(Packet<?> packet) {
        AutismProtectorChannelFilter.Verdict verdict = AutismProtectorChannelFilter.filter(packet);
        return verdict.kind == AutismProtectorChannelFilter.Verdict.Kind.DROP;
    }
}
