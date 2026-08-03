package autismclient.util;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

public final class AutismAuthNetwork {
    private static final List<Proxy> DIRECT_ROUTE = List.of(Proxy.NO_PROXY);
    private static final ProxySelector DIRECT_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null) throw new IllegalArgumentException("URI is required");
            return DIRECT_ROUTE;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException error) {

        }
    };

    private AutismAuthNetwork() {
    }

    public static Proxy directProxy() {
        return Proxy.NO_PROXY;
    }

    public static ProxySelector directProxySelector() {
        return DIRECT_SELECTOR;
    }
}
