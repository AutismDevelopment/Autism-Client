package autismclient.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;

public final class AutismAttest {
    private AutismAttest() {}

    private static volatile Path jarPath;

    public static String formSuffix(String authBase, String version) {
        try {
            JsonObject challenge = AutismHttp.postForm(authBase + "/challenge", "version=" + enc(version));
            if (challenge == null) {
                autismclient.AutismClientAddon.LOG.debug("[attest] /challenge unreachable for version {}", version);
                return "";
            }
            if (!challenge.has("cid") || !challenge.has("nonce")) {

                return "";
            }
            String cid = challenge.get("cid").getAsString();
            byte[] nonce = Base64.getDecoder().decode(challenge.get("nonce").getAsString());
            byte[] jar = ownJarBytes();
            if (jar == null) {
                autismclient.AutismClientAddon.LOG.debug("[attest] cannot read own jar for version {}", version);
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(nonce);
            JsonArray ranges = challenge.has("ranges") ? challenge.getAsJsonArray("ranges") : null;
            if (ranges == null || ranges.size() == 0) {
                digest.update(jar);
            } else {
                for (int i = 0; i < ranges.size(); i++) {
                    JsonArray range = ranges.get(i).getAsJsonArray();
                    int start = (int) Math.max(0L, Math.min((long) jar.length, range.get(0).getAsLong()));
                    int end = (int) Math.max(start, Math.min((long) jar.length, range.get(1).getAsLong()));
                    digest.update(jar, start, end - start);
                }
            }
            return "&cid=" + enc(cid) + "&answer=" + hex(digest.digest());
        } catch (Throwable t) {
            autismclient.AutismClientAddon.LOG.debug("[attest] answer failed for version {}: {}", version, t.toString());
            return "";
        }
    }

    private static byte[] ownJarBytes() {
        try {
            Path jar = jarPath;
            if (jar == null) {
                jar = FabricLoader.getInstance().getModContainer("autism")
                    .flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
                    .filter(path -> path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .orElse(null);
                jarPath = jar;
            }
            return jar == null ? null : Files.readAllBytes(jar);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return out.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
