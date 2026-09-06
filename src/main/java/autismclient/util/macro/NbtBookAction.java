package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismBookFileReader;
import autismclient.util.AutismBookPayloadBuilder;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class NbtBookAction implements MacroAction {
    public static final String SOURCE_RANDOM = "Random";
    public static final String SOURCE_PASTED = "Pasted";
    public static final String SOURCE_FILE = "File";

    public int pages = AutismBookPayloadBuilder.DEFAULT_PAGES;
    public int characters = AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE;
    public String title = "EldenRingLore";
    public boolean sign = true;
    public boolean appendCount = true;
    public boolean onlyAscii = false;
    public String randomType = AutismBookPayloadBuilder.RANDOM_UTF8;
    public String dataSource = SOURCE_RANDOM;
    public String customComponent = "";
    public String customFilePath = "";
    public boolean wordWrap = true;
    public int delayTicks = 20;
    public int bookCount = 1;
    public boolean requireHeldWritableBook = false;
    public boolean dropInventoryBefore = false;
    public boolean disconnectAfter = false;
    private boolean enabled = true;

    public transient String pasteInfo = "";

    private static final int MAX_PAGES = 100;
    private static final int LINES_PER_PAGE = 14;
    private static final float LINE_WIDTH_PX = 114f;

    public NbtBookAction() {}

    public boolean executeSingleBook(Minecraft mc, int bookIndex, int totalBooks) {
        if (PackHideState.isHardLocked()) return false;
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo player or network connection!");
            return false;
        }

        if (bookIndex == 0 && !prepareBeforeSigning(mc)) {
            return false;
        }

        int bookSlot = findWritableBook(mc);
        if (bookSlot < 0) {
            if (bookIndex == 0) {
                AutismClientMessaging.sendPrefixed("§cNo Book & Quill found in inventory!");
            } else {
                AutismClientMessaging.sendPrefixed("§eRan out of books after " + bookIndex + " signed.");
            }
            return false;
        }

        int hotbarSlot = bookSlot;
        if (bookSlot > 8) {
            int targetHotbar = 0;
            for (int i = 0; i <= 8; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    targetHotbar = i;
                    break;
                }
            }
            AutismInventoryHelper.swapInventorySlots(mc, bookSlot, targetHotbar);
            hotbarSlot = targetHotbar;
        }

        AutismInventoryHelper.selectHotbarSlot(mc, hotbarSlot);

        int numPages = Math.min(MAX_PAGES, Math.max(1, pages));
        List<String> pageList = buildPageList(mc, numPages);
        if (pageList.isEmpty()) return false;

        String bookTitle = title;
        if (appendCount && totalBooks > 1 && bookIndex > 0) {
            bookTitle = title + " #" + (bookIndex + 1);
        }

        mc.getConnection().send(new ServerboundEditBookPacket(hotbarSlot, pageList, sign ? Optional.of(bookTitle) : Optional.empty()));

        int estimatedBytes = AutismBookPayloadBuilder.estimateUtf8Bytes(pageList);
        AutismClientMessaging.sendPrefixed("§aWrote book " + (bookIndex + 1) + "/" + totalBooks +
            " (~" + (estimatedBytes / 1024) + "KB, " + pageList.size() + " pages)");
        return true;
    }

    private List<String> buildPageList(Minecraft mc, int numPages) {
        String source = normalizedSource();
        if (SOURCE_PASTED.equals(source)) {
            if (customComponent == null || customComponent.isBlank()) return generatedPages(numPages);
            return textPages(mc.font, customComponent, numPages);
        }
        if (SOURCE_FILE.equals(source)) {
            String text = readCustomFile();
            if (text == null || text.isBlank()) {
                AutismClientMessaging.sendPrefixed("§cNBT Book file is missing or empty.");
                return List.of();
            }
            return textPages(mc.font, text, numPages);
        }
        return generatedPages(numPages);
    }

    private List<String> textPages(Font font, String text, int numPages) {
        if (wordWrap) return splitTextIntoPages(font, text, numPages);
        return chunkTextIntoPages(text, numPages, characters);
    }

    private List<String> generatedPages(int numPages) {
        return AutismBookPayloadBuilder.randomPages(numPages, characters, effectiveRandomType(), new Random());
    }

    public static java.io.File bookSourcesDir() {
        java.io.File dir = new java.io.File(autismclient.AutismClientAddon.FOLDER, "book-sources");
        dir.mkdirs();
        return dir;
    }

    private String readCustomFile() {
        String path = customFilePath == null ? "" : customFilePath.trim();
        if (path.isBlank()) return "";
        try {
            Path base = bookSourcesDir().getCanonicalFile().toPath();

            Path resolved = base.resolve(path).toFile().getCanonicalFile().toPath();
            if (!resolved.startsWith(base)) {
                AutismClientMessaging.sendPrefixed("§cBook file must be inside book-sources/.");
                return "";
            }
            return AutismBookFileReader.read(resolved);
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (executeSingleBook(mc, 0, 1)) afterSigning(mc);
    }

    private boolean prepareBeforeSigning(Minecraft mc) {
        if (mc.player == null) return false;
        if (requireHeldWritableBook) {
            ItemStack held = mc.player.getMainHandItem();
            Identifier writableBookId = Identifier.fromNamespaceAndPath("minecraft", "writable_book");
            if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).equals(writableBookId)) {
                AutismClientMessaging.sendPrefixed("§cMust hold a Book & Quill!");
                return false;
            }
        }
        if (dropInventoryBefore) {
            int keepSlot = mc.player.getInventory().getSelectedSlot();
            for (int i = 0; i < 36; i++) {
                if (i == keepSlot) continue;
                if (!mc.player.getInventory().getItem(i).isEmpty()) {
                    autismclient.util.AutismDropHelper.dropFromInventorySlot(mc, i, 0);
                }
            }
        }
        return true;
    }

    public void afterSigning(Minecraft mc) {
        if (!disconnectAfter) return;
        if (mc.level != null && mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(
                net.minecraft.network.chat.Component.literal("Disconnected by Macro"));
        }
    }

    private int findWritableBook(Minecraft mc) {
        if (mc.player == null) return -1;
        Identifier writableBookId = Identifier.fromNamespaceAndPath("minecraft", "writable_book");
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(writableBookId)) {
                return i;
            }
        }
        return -1;
    }

    public static List<String> splitTextIntoPages(Font tr, String text, int maxPages) {
        if (text == null || text.isEmpty()) return new ArrayList<>(List.of(""));

        String normalized = AutismBookFileReader.normalize(text);
        String[] paragraphs = normalized.split("\n", -1);
        List<String> visualLines = new ArrayList<>();
        for (String para : paragraphs) {
            if (para.isEmpty()) {
                visualLines.add("");
            } else {
                wrapParagraph(tr, para, visualLines);
            }
        }

        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int linesOnPage = 0;
        List<String> boundedLines = new ArrayList<>(visualLines.size());
        for (String line : visualLines) {
            if (line.isEmpty()) {
                boundedLines.add("");
                continue;
            }
            int offset = 0;
            while (offset < line.length()) {
                int end = safePageEnd(line, offset, AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE);
                boundedLines.add(line.substring(offset, end));
                offset = end;
            }
        }

        for (String line : boundedLines) {
            int addedLength = line.length() + (linesOnPage > 0 ? 1 : 0);
            if (linesOnPage >= LINES_PER_PAGE
                || page.length() + addedLength > AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE) {
                pages.add(page.toString());
                if (pages.size() >= maxPages) return pages;
                page = new StringBuilder();
                linesOnPage = 0;
            }
            if (linesOnPage > 0) page.append('\n');
            page.append(line);
            linesOnPage++;
        }
        if (page.length() > 0) {
            pages.add(page.toString());
        }
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    private static void wrapParagraph(Font tr, String para, List<String> out) {
        int idx = 0;
        while (idx < para.length()) {
            float width = 0;
            int lineEnd = idx;
            int lastSpace = -1;

            while (lineEnd < para.length()) {
                int codePoint = para.codePointAt(lineEnd);
                int next = lineEnd + Character.charCount(codePoint);
                float cw = tr.width(para.substring(lineEnd, next));
                if (width + cw > LINE_WIDTH_PX && lineEnd > idx) break;
                if (Character.isWhitespace(codePoint)) lastSpace = lineEnd;
                width += cw;
                lineEnd = next;
            }

            if (lineEnd >= para.length()) {

                out.add(para.substring(idx));
                return;
            }

            if (lastSpace > idx) {

                out.add(para.substring(idx, lastSpace));
                idx = lastSpace + 1;
            } else {

                out.add(para.substring(idx, lineEnd));
                idx = lineEnd;
            }
        }
    }

    public static int calculatePagesNeeded(Font tr, String text) {
        if (text == null || text.isEmpty()) return 0;
        return splitTextIntoPages(tr, text, MAX_PAGES).size();
    }

    public static List<String> chunkTextIntoPages(String text, int maxPages, int charsPerPage) {
        if (text == null || text.isEmpty()) return new ArrayList<>(List.of(""));
        int max = Math.max(1, Math.min(AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE, charsPerPage));
        List<String> pages = new ArrayList<>();
        int index = 0;
        while (index < text.length() && pages.size() < maxPages) {
            int end = safePageEnd(text, index, max);
            pages.add(text.substring(index, end));
            index = end;
        }
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    private static int safePageEnd(String text, int start, int maxChars) {
        int end = Math.min(text.length(), start + Math.max(1, maxChars));
        if (end < text.length() && end > start
            && Character.isHighSurrogate(text.charAt(end - 1))
            && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }

        if (end == start && start < text.length()) {
            end = start + Character.charCount(text.codePointAt(start));
        }
        return end;
    }

    @Override
    public MacroActionType getType() { return MacroActionType.NBT_BOOK; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("pages", pages);
        tag.putInt("characters", characters);
        tag.putString("title", title);
        tag.putBoolean("sign", sign);
        tag.putBoolean("appendCount", appendCount);
        tag.putBoolean("onlyAscii", onlyAscii);
        tag.putString("randomType", effectiveRandomType());
        tag.putString("dataSource", normalizedSource());
        tag.putString("customText", customComponent);
        tag.putString("customFilePath", customFilePath);
        tag.putBoolean("wordWrap", wordWrap);
        tag.putInt("delayTicks", delayTicks);
        tag.putInt("bookCount", bookCount);
        tag.putBoolean("requireHeldWritableBook", requireHeldWritableBook);
        tag.putBoolean("dropInventoryBefore", dropInventoryBefore);
        tag.putBoolean("disconnectAfter", disconnectAfter);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("pages")) pages = tag.getIntOr("pages", AutismBookPayloadBuilder.DEFAULT_PAGES);
        else if (tag.contains("targetSizeKB")) pages = Math.min(AutismBookPayloadBuilder.MAX_PAGES, tag.getIntOr("targetSizeKB", AutismBookPayloadBuilder.DEFAULT_PAGES));
        characters = tag.getIntOr("characters", AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE);
        if (tag.contains("title")) title = tag.getStringOr("title", "EldenRingLore");
        sign = tag.getBooleanOr("sign", true);
        appendCount = tag.getBooleanOr("appendCount", true);
        if (tag.contains("onlyAscii")) onlyAscii = tag.getBooleanOr("onlyAscii", false);
        if (tag.contains("randomType")) randomType = AutismBookPayloadBuilder.normalizeRandomType(tag.getStringOr("randomType", AutismBookPayloadBuilder.RANDOM_UTF8));
        else if (onlyAscii) randomType = AutismBookPayloadBuilder.RANDOM_ASCII;
        if (tag.contains("customText")) customComponent = tag.getStringOr("customText", "");
        if (tag.contains("customFilePath")) customFilePath = tag.getStringOr("customFilePath", "");
        wordWrap = tag.getBooleanOr("wordWrap", true);
        if (tag.contains("dataSource")) dataSource = normalizeSource(tag.getStringOr("dataSource", SOURCE_RANDOM));
        else dataSource = legacySourceForCustomText(customComponent);
        if (tag.contains("delayTicks")) delayTicks = tag.getIntOr("delayTicks", 20);
        if (tag.contains("bookCount")) bookCount = tag.getIntOr("bookCount", 1);
        requireHeldWritableBook = tag.getBooleanOr("requireHeldWritableBook", false);
        dropInventoryBefore = tag.getBooleanOr("dropInventoryBefore", false);
        disconnectAfter = tag.getBooleanOr("disconnectAfter", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String mode = SOURCE_RANDOM.equals(normalizedSource())
            ? effectiveRandomType()
            : normalizedSource();
        String count = bookCount > 1 ? ", x" + bookCount : "";
        String extras = "";
        if (dropInventoryBefore) extras += ", drop inv";
        if (disconnectAfter) extras += ", disconnect";
        return "NBT Book (" + pages + "pg, " + mode + count + extras + ")";
    }

    private String normalizedSource() {
        dataSource = normalizeSource(dataSource);
        return dataSource;
    }

    private String effectiveRandomType() {
        randomType = AutismBookPayloadBuilder.normalizeRandomType(randomType);
        onlyAscii = AutismBookPayloadBuilder.RANDOM_ASCII.equals(randomType);
        return randomType;
    }

    private static String normalizeSource(String source) {
        if (source == null) return SOURCE_RANDOM;
        return switch (source.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "generated", "random" -> SOURCE_RANDOM;
            case "pasted", "paste", "custom" -> SOURCE_PASTED;
            case "file", "path" -> SOURCE_FILE;
            default -> SOURCE_RANDOM;
        };
    }

    private static String legacySourceForCustomText(String text) {
        if (text == null || text.isBlank() || "Autism Inc - EldenRingLore".equals(text)) return SOURCE_RANDOM;
        return SOURCE_PASTED;
    }

    @Override
    public void sanitizeForSharing() {

        this.customFilePath = "";
        if (SOURCE_FILE.equals(normalizeSource(this.dataSource))) {
            this.dataSource = SOURCE_RANDOM;
        }
    }

    @Override public String getIcon() { return "B"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
