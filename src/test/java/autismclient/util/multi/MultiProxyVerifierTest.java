package autismclient.util.multi;

import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProxyVerifierTest {
    @Test
    void reportsTheProtocolThatActuallyOpenedTheTunnel() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress())) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            Future<?> serving = worker.submit(() -> serveSocks5AfterRejectedSocks4(server));
            try {
                AutismProxy proxy = new AutismProxy();
                proxy.address = InetAddress.getLoopbackAddress().getHostAddress();
                proxy.port = server.getLocalPort();
                proxy.type = AutismProxyType.Socks4;

                MultiProxyVerifier.Result result = MultiProxyVerifier.verify(
                    proxy, "minecraft.example", 25565, 1_000);

                assertTrue(result.ok());
                assertEquals(AutismProxyType.Socks5, result.workingType());
                serving.get(2, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
            }
        }
    }

    private static void serveSocks5AfterRejectedSocks4(ServerSocket server) {
        try {

            try (Socket rejected = server.accept()) {
                rejected.setSoTimeout(1_000);
                rejected.getInputStream().read();
            }
            try (Socket accepted = server.accept()) {
                accepted.setSoTimeout(1_000);
                InputStream in = accepted.getInputStream();
                OutputStream out = accepted.getOutputStream();
                assertEquals(5, in.read());
                int methods = in.read();
                in.readNBytes(methods);
                out.write(new byte[]{5, 0});
                out.flush();

                byte[] request = in.readNBytes(4);
                assertEquals(5, request[0]);
                int addressBytes = switch (request[3] & 0xFF) {
                    case 1 -> 4;
                    case 3 -> in.read();
                    case 4 -> 16;
                    default -> throw new IllegalStateException("Unknown address type");
                };
                in.readNBytes(addressBytes + 2);
                out.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 0, 0});
                out.flush();
            }
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }
}
