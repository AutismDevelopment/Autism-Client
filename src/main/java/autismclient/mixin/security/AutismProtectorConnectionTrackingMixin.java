package autismclient.mixin.security;

import autismclient.security.AutismProtectorLocalAddressUtil;
import autismclient.util.multi.MultiConnectionContext;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(Connection.class)
public class AutismProtectorConnectionTrackingMixin {

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void autism$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        if (MultiConnectionContext.isMulti((Connection) (Object) this)
            || (context != null && MultiConnectionContext.isMulti(context.channel()))) return;
        try {
            if (context.channel() == null) return;
            SocketAddress addr = context.channel().remoteAddress();
            if (addr instanceof InetSocketAddress inet && inet.getAddress() != null) {
                AutismProtectorLocalAddressUtil.serverAddress = inet.getAddress().getHostAddress();
            } else {
                AutismProtectorLocalAddressUtil.serverAddress = null;
            }
        } catch (Throwable ignored) {
            AutismProtectorLocalAddressUtil.serverAddress = null;
        }
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void autism$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        if (MultiConnectionContext.isMulti((Connection) (Object) this)
            || (context != null && MultiConnectionContext.isMulti(context.channel()))) return;
        AutismProtectorLocalAddressUtil.serverAddress = null;
    }
}
