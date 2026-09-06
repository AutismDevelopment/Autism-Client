package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.util.Util;

import java.util.Locale;

public final class AutismLinks {
    public static final String DISCORD = "https://discord.com/invite/JZ7XgUCtBu";
    public static final String AUTISM_INC_DISCORD = "https://discord.gg/V8GsKP6k5u";
    public static final String KOFI = "https://ko-fi.com/melonik";
    public static final String CRYPTO_DONATE = "https://nowpayments.io/donation/melonikautismclient";
    public static final String WEBSITE = "https://autismclient.com";

    private AutismLinks() {
    }

    public static void open(String url) {
        if (!isOpenableUrl(url)) {
            AutismClientAddon.LOG.warn("[Autism] Refused to open non-http(s) URL: {}", url);
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable ignored) {  }
    }

    public static boolean isOpenableUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }
}
