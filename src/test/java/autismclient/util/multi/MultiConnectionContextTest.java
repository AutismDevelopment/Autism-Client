package autismclient.util.multi;

import autismclient.util.AutismProxy;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiConnectionContextTest {
    @Test
    void inventoryClickTimeoutIsAdaptiveAndBounded() {
        assertEquals(1_000L, MultiSession.clickTimeoutMillis(-1));
        assertEquals(1_000L, MultiSession.clickTimeoutMillis(100));
        assertEquals(1_700L, MultiSession.clickTimeoutMillis(300));
        assertEquals(5_000L, MultiSession.clickTimeoutMillis(10_000));
    }

    @Test
    void connectionAndChannelIdentityRemainExplicitAndIndependent() {
        Connection multi = new Connection(PacketFlow.CLIENTBOUND);
        Connection rendered = new Connection(PacketFlow.CLIENTBOUND);
        EmbeddedChannel multiChannel = new EmbeddedChannel();
        EmbeddedChannel renderedChannel = new EmbeddedChannel();
        try {
            MultiConnectionContext.register(multi, null);
            MultiConnectionContext.bindChannel(multi, multiChannel);

            assertTrue(MultiConnectionContext.isMulti(multi));
            assertTrue(MultiConnectionContext.isMulti(multiChannel));
            assertFalse(MultiConnectionContext.isMulti(rendered));
            assertFalse(MultiConnectionContext.isMulti(renderedChannel));

            MultiConnectionContext.remove(multi);
            assertFalse(MultiConnectionContext.isMulti(multi));
            assertTrue(MultiConnectionContext.isMulti(multiChannel),
                "channel identity protects packets until channelInactive even if the connection entry retires first");
            MultiConnectionContext.unbindChannel(multiChannel);
            assertFalse(MultiConnectionContext.isMulti(multiChannel));
        } finally {
            multiChannel.finishAndReleaseAll();
            renderedChannel.finishAndReleaseAll();
            MultiConnectionContext.remove(multi);
            MultiConnectionContext.remove(rendered);
        }
    }

    @Test
    void eachManagedConnectionKeepsItsOwnImmutableProxySpec() {
        AutismProxy firstProxy = new AutismProxy();
        firstProxy.address = "127.0.0.10";
        firstProxy.port = 1080;
        firstProxy.username = "first";
        AutismProxy secondProxy = new AutismProxy();
        secondProxy.address = "127.0.0.11";
        secondProxy.port = 1081;
        secondProxy.username = "second";
        Connection first = new Connection(PacketFlow.CLIENTBOUND);
        Connection second = new Connection(PacketFlow.CLIENTBOUND);
        try {
            MultiConnectionContext.register(first, firstProxy);
            MultiConnectionContext.register(second, secondProxy);
            firstProxy.address = "mutated-after-register";

            assertEquals("127.0.0.10", MultiConnectionContext.proxy(first).address());
            assertEquals(1080, MultiConnectionContext.proxy(first).port());
            assertEquals("127.0.0.11", MultiConnectionContext.proxy(second).address());
            assertEquals(1081, MultiConnectionContext.proxy(second).port());
        } finally {
            MultiConnectionContext.remove(first);
            MultiConnectionContext.remove(second);
        }
    }
}
