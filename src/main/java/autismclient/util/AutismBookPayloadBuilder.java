package autismclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Random;

public final class AutismBookPayloadBuilder {
    public static final int MAX_PAGES = 100;
    public static final int DEFAULT_PAGES = 100;
    public static final int DEFAULT_CHARS_PER_PAGE = 1024;
    public static final String RANDOM_ASCII = "Ascii";
    public static final String RANDOM_UTF8 = "Utf8";
    public static final String RANDOM_PAPERMC = "PaperMC";

    private AutismBookPayloadBuilder() {
    }

    public static List<String> randomPages(int requestedPages, int requestedCharsPerPage, String randomType, Random random) {
        String type = normalizeRandomType(randomType);
        if (RANDOM_PAPERMC.equals(type)) return paperMcPages(random);
        int pages = Math.max(1, Math.min(MAX_PAGES, requestedPages));
        int charsPerPage = Math.max(1, Math.min(DEFAULT_CHARS_PER_PAGE, requestedCharsPerPage));
        Random rng = random == null ? new Random() : random;
        PrimitiveIterator.OfInt chars = RANDOM_ASCII.equals(type)
            ? rng.ints(0x21, 0x80).filter(AutismBookPayloadBuilder::validBookCodepoint).iterator()
            : rng.ints(0x21, 0xD800).filter(AutismBookPayloadBuilder::validBookCodepoint).iterator();
        List<String> out = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            StringBuilder builder = new StringBuilder(charsPerPage);
            for (int i = 0; i < charsPerPage && chars.hasNext(); i++) builder.appendCodePoint(chars.nextInt());
            out.add(builder.toString());
        }
        return out;
    }

    public static String normalizeRandomType(String value) {
        if (value == null) return RANDOM_UTF8;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "ascii" -> RANDOM_ASCII;
            case "papermc", "paper" -> RANDOM_PAPERMC;
            default -> RANDOM_UTF8;
        };
    }

    private static List<String> paperMcPages(Random random) {
        Random rng = random == null ? new Random() : random;
        List<String> pages = new ArrayList<>(MAX_PAGES);
        StringBuilder page = new StringBuilder(DEFAULT_CHARS_PER_PAGE);
        PrimitiveIterator.OfInt oneByte = rng.ints(0x21, 0x80).filter(AutismBookPayloadBuilder::validBookCodepoint).iterator();
        PrimitiveIterator.OfInt twoBytes = rng.ints(0x0080, 0x0800).filter(AutismBookPayloadBuilder::validBookCodepoint).iterator();
        PrimitiveIterator.OfInt threeBytes = rng.ints(0x0800, 0xD800).filter(AutismBookPayloadBuilder::validBookCodepoint).iterator();
        for (int pageIndex = 0; pageIndex < MAX_PAGES; pageIndex++) {
            if (pageIndex < 50) {
                page.appendCodePoint(threeBytes.nextInt());
                for (int i = 1; i < DEFAULT_CHARS_PER_PAGE; i++) page.appendCodePoint(oneByte.nextInt());
            } else if (pageIndex == 50) {
                for (int i = 0; i < 110; i++) page.appendCodePoint(threeBytes.nextInt());
                page.appendCodePoint(twoBytes.nextInt());
                for (int i = 0; i < 913; i++) page.appendCodePoint(oneByte.nextInt());
            } else {
                for (int i = 0; i < DEFAULT_CHARS_PER_PAGE; i++) page.appendCodePoint(threeBytes.nextInt());
            }
            pages.add(page.toString());
            page.setLength(0);
        }
        return pages;
    }

    public static int estimateUtf8Bytes(List<String> pages) {
        if (pages == null || pages.isEmpty()) return 0;
        int bytes = 0;
        for (String page : pages) {
            if (page == null || page.isEmpty()) continue;
            for (int i = 0; i < page.length(); i++) {
                char c = page.charAt(i);
                if (c < 0x80) bytes += 1;
                else if (c < 0x800) bytes += 2;
                else bytes += 3;
            }
        }
        return bytes;
    }

    private static boolean validBookCodepoint(int value) {
        return !Character.isWhitespace(value) && value != '\r' && value != '\n';
    }
}
