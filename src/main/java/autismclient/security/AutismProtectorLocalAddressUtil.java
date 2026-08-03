package autismclient.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class AutismProtectorLocalAddressUtil {

    public static volatile String serverAddress;

    private AutismProtectorLocalAddressUtil() {
    }

    public static boolean isLocalAddress(String host) throws UnknownHostException {
        if (host == null) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivateOrLocal(address)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isSiteLocalAddress()
            || address.isLinkLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (b0 == 0) return true;

            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;
        } else if (bytes.length == 16) {
            int b0 = bytes[0] & 0xFF;

            if ((b0 & 0xFE) == 0xFC) return true;
        }
        return false;
    }

    public static boolean shouldBlock(String host) {
        try {
            if (!isLocalAddress(host)) return false;

            if (isAlwaysBlocked(host)) return true;

            return !isLocalAddress(serverAddress);
        } catch (UnknownHostException unresolved) {

            return false;
        }
    }

    private static boolean isAlwaysBlocked(String host) throws UnknownHostException {
        if (host == null) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
                return true;
            }
        }
        return false;
    }
}
