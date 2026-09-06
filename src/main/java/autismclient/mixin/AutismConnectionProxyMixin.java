package autismclient.mixin;

import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.multi.MultiConnectionContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.util.ArrayList;

@Mixin(value = Connection.class, priority = 2000)
public abstract class AutismConnectionProxyMixin {
    @Inject(method = "configureSerialization", at = @At("HEAD"))
    private static void autism$disableMeteorProxyBeforeHandlers(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (local || inboundDirection != PacketFlow.CLIENTBOUND) return;
        autism$disableMeteorProxy();
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void autism$applyProxy(ChannelPipeline pipeline, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;

        if (connection.getReceiving() != PacketFlow.CLIENTBOUND) return;
        if (pipeline.channel() instanceof io.netty.channel.local.LocalChannel) return;
        if (MultiConnectionContext.isMulti(connection)) {

            autism$removeAllProxyHandlers(pipeline);
            MultiConnectionContext.ProxySpec proxy = MultiConnectionContext.proxy(connection);
            if (proxy != null && !proxy.address().isBlank() && proxy.port() > 0) {
                autism$installProxy(pipeline, proxy.type(), proxy.address(), proxy.port(), proxy.username(), proxy.password());
            }
            return;
        }

        AutismProxy main = AutismProxyManager.get().getEnabled();
        if (main == null) return;
        autism$removeAllProxyHandlers(pipeline);
        autism$installProxy(pipeline, main.type, main.address, main.port, main.username, main.password);
    }

    @Unique
    private static void autism$installProxy(ChannelPipeline pipeline, autismclient.util.AutismProxyType type,
                                            String address, int port, String username, String password) {
        if (address == null || address.isBlank() || port <= 0) return;
        InetSocketAddress target = new InetSocketAddress(address, port);
        String user = username == null ? "" : username;
        String pass = password == null ? "" : password;
        switch (type) {
            case Socks4 -> pipeline.addFirst("autism_socks4_proxy", new Socks4ProxyHandler(target, user));
            case Socks5 -> pipeline.addFirst("autism_socks5_proxy", new Socks5ProxyHandler(target, user, pass));
        }
    }

    @Unique
    private static void autism$removeAllProxyHandlers(ChannelPipeline pipeline) {
        for (String name : new ArrayList<>(pipeline.names())) {
            if (pipeline.get(name) instanceof ProxyHandler) pipeline.remove(name);
        }
    }

    private static void autism$disableMeteorProxy() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        try {
            Class<?> proxiesClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxies");
            Class<?> proxyClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxy");
            Object proxies = proxiesClass.getMethod("get").invoke(null);
            Object enabled = proxiesClass.getMethod("getEnabled").invoke(proxies);
            if (enabled != null) proxiesClass.getMethod("setEnabled", proxyClass, boolean.class).invoke(proxies, enabled, false);
        } catch (ReflectiveOperationException ignored) {  }
    }
}
