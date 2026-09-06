package autismclient.util;

import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class AutismAuthNetworkTest {
    @Test
    void authenticationAlwaysUsesDirectRoute() {
        assertSame(Proxy.NO_PROXY, AutismAuthNetwork.directProxy());
        assertEquals(
            java.util.List.of(Proxy.NO_PROXY),
            AutismAuthNetwork.directProxySelector().select(URI.create("https://login.microsoftonline.com/"))
        );
    }
}
