package autismclient.mixin.security;

import autismclient.security.AutismProtector;
import autismclient.security.AutismProtectorLocalAddressUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.Socket;
import java.net.URL;
import java.util.Map;

@Mixin(HttpUtil.class)
public class AutismProtectorHttpUtilMixin {

    @SuppressWarnings("deprecation")
    @WrapOperation(
        method = "downloadFile",
        at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;"),
        require = 1
    )
    private static InputStream autism$blockLocal(
            HttpURLConnection instance,
            Operation<InputStream> original,
            @Local(argsOnly = true) Proxy proxy,
            @Local(argsOnly = true) Map<String, String> requestProperties,
            @Local LocalRef<HttpURLConnection> connectionRef) throws IOException {
        if (!AutismProtector.shouldBlockLocalUrls()) return original.call(instance);

        rejectLocal(instance.getURL());
        instance.setInstanceFollowRedirects(false);

        int redirects = 0;
        int maxRedirects = maxRedirects();
        int status = instance.getResponseCode();
        while (instance.getHeaderField("Location") != null && isRedirect(status)) {
            if (redirects >= maxRedirects - 1) {
                leakVanillaCapSocket(instance);
                throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");
            }

            if (status == HttpURLConnection.HTTP_USE_PROXY) {
                URL proxyUrl = resolveRedirect(instance);
                if (proxyUrl == null) break;
                rejectLocal(proxyUrl);

                int proxyPort = proxyUrl.getPort() == -1 ? proxyUrl.getDefaultPort() : proxyUrl.getPort();
                Proxy hopProxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyUrl.getHost(), proxyPort));
                URL originalUrl = instance.getURL();
                instance = (HttpURLConnection) originalUrl.openConnection(hopProxy);
            } else {
                URL next = resolveRedirect(instance);
                if (next == null || !sameProtocol(instance.getURL(), next)) break;
                rejectLocal(next);
                instance = (HttpURLConnection) next.openConnection(proxy);
            }

            instance.setAuthenticator(new Authenticator() {});
            instance.setInstanceFollowRedirects(false);
            requestProperties.forEach(instance::setRequestProperty);
            status = instance.getResponseCode();
            redirects++;
        }

        if (connectionRef != null) connectionRef.set(instance);
        return original.call(instance);
    }

    private static void rejectLocal(URL url) {
        String host = url == null ? null : url.getHost();
        if (AutismProtectorLocalAddressUtil.shouldBlock(host)) {
            throw new IllegalStateException("[AutismProtector] Refused resource pack download from local address: " + url);
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MULT_CHOICE
            || status == HttpURLConnection.HTTP_MOVED_PERM
            || status == HttpURLConnection.HTTP_MOVED_TEMP
            || status == HttpURLConnection.HTTP_SEE_OTHER
            || status == HttpURLConnection.HTTP_USE_PROXY
            || status == 307;
    }

    private static URL resolveRedirect(HttpURLConnection connection) throws IOException {
        String location = connection.getHeaderField("Location");
        if (location == null || location.isBlank()) return null;
        try {
            URI uri = URI.create(location);
            if (uri.isAbsolute()) return uri.toURL();
        } catch (IllegalArgumentException ignored) {  }
        try {
            return connection.getURL().toURI().resolve(location).toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            MalformedURLException malformed = new MalformedURLException(location);
            malformed.initCause(e);
            throw malformed;
        }
    }

    private static boolean sameProtocol(URL a, URL b) {
        return a != null && b != null && a.getProtocol().equalsIgnoreCase(b.getProtocol());
    }

    private static int maxRedirects() {
        String value = System.getProperty("http.maxRedirects");
        if (value == null) return 20;
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 20;
        }
    }

    private static void leakVanillaCapSocket(HttpURLConnection connection) {
        try {
            URL url = resolveRedirect(connection);
            if (url == null) return;
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();

            new Socket(url.getHost(), port);
        } catch (Exception ignored) {  }
    }
}
