package autismclient.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import autismclient.util.multi.MultiProxyVerifier;

import autismclient.util.mm.crypto.AtRestSeal;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class AutismProxy {
    public enum Status {
        UNCHECKED,
        CHECKING,
        DEAD,
        ALIVE;

        public String display() {
            return switch (this) {
                case UNCHECKED -> "";
                case CHECKING -> "...";
                case DEAD -> "X";
                case ALIVE -> "O";
            };
        }

        public int color() {
            return switch (this) {
                case UNCHECKED, CHECKING -> 0xFFA8A8A8;
                case DEAD -> 0xFFFF5A5A;
                case ALIVE -> 0xFF5AFF91;
            };
        }
    }

    public enum GeoStatus {
        UNKNOWN,
        LOOKING_UP,
        RESOLVED,
        FAILED,
        PRIVATE
    }

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public AutismProxyType type = AutismProxyType.Socks5;
    public String address = "";
    public int port = 0;
    public boolean enabled = false;
    public String username = "";
    public String password = "";
    public volatile Status status = Status.UNCHECKED;
    public volatile long latency = 0L;
    public volatile GeoStatus geoStatus = GeoStatus.UNKNOWN;
    public volatile String geoResolvedIp = "";
    public volatile String geoCountryCode = "";
    public volatile String geoCountryName = "";
    public volatile String geoRegion = "";
    public volatile String geoCity = "";
    public volatile String geoSubdivision = "";
    public volatile long geoCheckedAt = 0L;
    transient boolean generatedStableId;

    public record CheckResult(Status status, long latency, int code) {
        public CheckResult {
            status = status == null ? Status.DEAD : status;
            latency = Math.max(0L, latency);
        }
    }

    private record TypeProbeResult(boolean alive, boolean timeout, long latency) {
    }

    public AutismProxy() {
    }

    public AutismProxy(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) fromTag(compoundTag);
    }

    public boolean isValid() {
        return address != null && !address.isBlank() && port > 0 && port <= 65535;
    }

    public String displayName() {
        String label = name == null || name.isBlank() ? address : name;
        return label == null ? "" : label;
    }

    public String geoLabel() {
        GeoStatus state = geoStatus == null ? GeoStatus.UNKNOWN : geoStatus;
        if (state == GeoStatus.LOOKING_UP) return "Geo...";
        if (state != GeoStatus.RESOLVED) return "Unknown";
        String code = safe(geoCountryCode).toUpperCase(Locale.ROOT);
        String region = safe(geoRegion);
        if (!code.isBlank() && !region.isBlank()) return code + " " + region;
        if (!code.isBlank()) return code;
        if (!region.isBlank()) return region;
        return "Unknown";
    }

    public int geoColor() {
        GeoStatus state = geoStatus == null ? GeoStatus.UNKNOWN : geoStatus;
        return switch (state) {
            case RESOLVED -> 0xFFF2F2F2;
            case LOOKING_UP -> 0xFF8EA0FF;
            case PRIVATE -> 0xFF9A9A9A;
            case FAILED, UNKNOWN -> 0xFF9A9A9A;
        };
    }

    public String geoSearchText() {
        return String.join(" ",
            geoLabel(),
            safe(geoResolvedIp),
            safe(geoCountryCode),
            safe(geoCountryName),
            safe(geoRegion),
            safe(geoCity),
            safe(geoSubdivision)
        );
    }

    public boolean needsGeoLookup(long now, boolean force) {
        if (!isValid()) return false;
        if (force) return true;
        GeoStatus state = geoStatus == null ? GeoStatus.UNKNOWN : geoStatus;
        long age = geoCheckedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, now - geoCheckedAt);
        return switch (state) {
            case UNKNOWN -> true;
            case LOOKING_UP -> age > 30_000L;
            case FAILED -> age > 6L * 60L * 60L * 1000L;
            case RESOLVED, PRIVATE -> age > 7L * 24L * 60L * 60L * 1000L;
        };
    }

    public void markGeoLookupPending(long now) {
        geoStatus = GeoStatus.LOOKING_UP;
        geoCheckedAt = Math.max(0L, now);
    }

    public void clearGeo() {
        geoStatus = GeoStatus.UNKNOWN;
        geoResolvedIp = "";
        geoCountryCode = "";
        geoCountryName = "";
        geoRegion = "";
        geoCity = "";
        geoSubdivision = "";
        geoCheckedAt = 0L;
    }

    public void applyGeoResult(AutismProxyGeoLookup.GeoResult result) {
        if (result == null) {
            geoStatus = GeoStatus.FAILED;
            geoCheckedAt = System.currentTimeMillis();
            return;
        }
        geoStatus = result.status();
        geoResolvedIp = safe(result.resolvedIp());
        geoCountryCode = safe(result.countryCode()).toUpperCase(Locale.ROOT);
        geoCountryName = safe(result.countryName());
        geoRegion = safe(result.region());
        geoCity = safe(result.city());
        geoSubdivision = safe(result.subdivision());
        geoCheckedAt = result.checkedAt() <= 0L ? System.currentTimeMillis() : result.checkedAt();
    }

    public synchronized int checkStatus(int timeoutMs) {
        status = Status.CHECKING;
        CheckResult result = probeStatus(timeoutMs);
        applyCheckResult(result);
        return result.code();
    }

    public synchronized CheckResult probeStatus(int timeoutMs) {

        MultiProxyVerifier.Result verified = MultiProxyVerifier.verify(this, "example.com", 80, timeoutMs);
        if (!verified.ok()) return new CheckResult(Status.DEAD, 0L, 2);
        if (verified.workingType() != null) type = verified.workingType();
        return new CheckResult(Status.ALIVE, verified.latencyMs(), 1);
    }

    public synchronized void applyCheckResult(CheckResult result) {
        if (result == null) {
            status = Status.UNCHECKED;
            latency = 0L;
            return;
        }
        status = result.status();
        latency = result.status() == Status.ALIVE ? result.latency() : 0L;
    }

    private TypeProbeResult checkType(AutismProxyType checkType, int timeoutMs) {
        try {
            Instant before = Instant.now();
            boolean alive = checkType == AutismProxyType.Socks4 ? isSocks4(timeoutMs) : isSocks5(timeoutMs);
            if (alive) {
                return new TypeProbeResult(true, false, Duration.between(before, Instant.now()).toMillis());
            }
        } catch (SocketTimeoutException e) {
            return new TypeProbeResult(false, true, 0L);
        } catch (IOException ignored) {  }
        return new TypeProbeResult(false, false, 0L);
    }

    private boolean isSocks4(int timeoutMs) throws IOException {
        ByteBuffer bb;
        byte[] user = safe(username).getBytes();
        if (isIpv4Address(address)) {
            bb = ByteBuffer.allocate(9 + user.length)
                .put((byte) 4)
                .put((byte) 1)
                .putShort((short) port)
                .put(InetAddress.getByName(address).getAddress())
                .put(user)
                .put((byte) 0);
        } else {
            byte[] addr = safe(address).getBytes();
            bb = ByteBuffer.allocate(10 + user.length + addr.length)
                .put((byte) 4)
                .put((byte) 1)
                .putShort((short) port)
                .put(new byte[]{0, 0, 0, 1})
                .put(user)
                .put((byte) 0)
                .put(addr)
                .put((byte) 0);
        }
        byte[] data = sendData(bb.array(), 8, timeoutMs);
        return data.length >= 2 && data[0] == 0 && data[1] == 90;
    }

    private boolean isSocks5(int timeoutMs) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4)
            .put((byte) 5)
            .put((byte) 2)
            .put((byte) 0)
            .put((byte) 2);
        byte[] data = sendData(bb.array(), 2, timeoutMs);
        return data.length >= 2 && data[0] == 5 && (data[1] == 0 || data[1] == 2);
    }

    private byte[] sendData(byte[] data, int read, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            int timeout = Math.max(1, timeoutMs);
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(address, port), timeout);
            OutputStream output = socket.getOutputStream();
            output.write(data);
            return socket.getInputStream().readNBytes(read);
        }
    }

    private static boolean isIpv4Address(String value) {
        if (value == null) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int i = Integer.parseInt(part);
                if (i < 0 || i > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sealString(String value) {
        if (value == null || value.isEmpty()) return "";
        byte[] sealed = AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
        return sealed == null ? "" : Base64.getEncoder().encodeToString(sealed);
    }

    private static String unsealString(String encoded, String plainFallback) {
        String fallback = plainFallback == null ? "" : plainFallback;
        if (encoded == null || encoded.isEmpty()) return fallback;
        try {
            byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
            return plain == null ? fallback : new String(plain, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", stableId());
        tag.putString("name", safe(name));
        tag.putString("type", type == null ? AutismProxyType.Socks5.name() : type.name());
        tag.putString("address", safe(address));
        tag.putInt("port", port);
        tag.putBoolean("enabled", enabled);

        tag.putString("encUsername", sealString(username));
        tag.putString("encPassword", sealString(password));
        tag.putString("geoStatus", geoStatus == null ? GeoStatus.UNKNOWN.name() : geoStatus.name());
        tag.putString("geoResolvedIp", safe(geoResolvedIp));
        tag.putString("geoCountryCode", safe(geoCountryCode));
        tag.putString("geoCountryName", safe(geoCountryName));
        tag.putString("geoRegion", safe(geoRegion));
        tag.putString("geoCity", safe(geoCity));
        tag.putString("geoSubdivision", safe(geoSubdivision));
        tag.putLong("geoCheckedAt", geoCheckedAt);
        return tag;
    }

    public AutismProxy fromTag(CompoundTag tag) {
        String storedId = tag.getStringOr("id", "");
        if (storedId.isBlank()) {
            id = UUID.randomUUID().toString();
            generatedStableId = true;
        } else {
            id = storedId;
        }
        name = tag.getStringOr("name", "");
        String typeName = tag.getStringOr("type", AutismProxyType.Socks5.name());
        try {
            type = AutismProxyType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = typeName.toLowerCase(Locale.ROOT).contains("4") ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
        }
        address = tag.getStringOr("address", "");
        port = tag.getIntOr("port", 0);
        enabled = tag.getBooleanOr("enabled", false);

        username = unsealString(tag.getStringOr("encUsername", ""), tag.getStringOr("username", ""));
        password = unsealString(tag.getStringOr("encPassword", ""), tag.getStringOr("password", ""));
        try {
            geoStatus = GeoStatus.valueOf(tag.getStringOr("geoStatus", GeoStatus.UNKNOWN.name()));
        } catch (IllegalArgumentException e) {
            geoStatus = GeoStatus.UNKNOWN;
        }
        geoResolvedIp = tag.getStringOr("geoResolvedIp", "");
        geoCountryCode = tag.getStringOr("geoCountryCode", "");
        geoCountryName = tag.getStringOr("geoCountryName", "");
        geoRegion = tag.getStringOr("geoRegion", "");
        geoCity = tag.getStringOr("geoCity", "");
        geoSubdivision = tag.getStringOr("geoSubdivision", "");
        geoCheckedAt = tag.getLongOr("geoCheckedAt", 0L);
        status = Status.UNCHECKED;
        latency = 0L;
        return this;
    }

    public String stableId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            generatedStableId = true;
        }
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AutismProxy proxy)) return false;
        return port == proxy.port && Objects.equals(address, proxy.address) && type == proxy.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, address, port);
    }
}
