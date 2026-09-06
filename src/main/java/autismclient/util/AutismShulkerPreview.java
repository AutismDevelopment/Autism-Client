package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.modules.InventoryTweaksModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class AutismShulkerPreview {
    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 3;
    private static final int SLOT = 18;
    private static final int CAPACITY = COLUMNS * MAX_ROWS;

    private static final int CURSOR_GAP = 12;
    private static final int GAP = 4;
    private static final int SCREEN_MARGIN = 2;
    private static final int TOOLTIP_INSET = 6;

    private static final int TOOLTIP_LINE_HEIGHT = 10;
    private static final int TOOLTIP_MOUSE_DX = 12;
    private static final int TOOLTIP_MOUSE_DY = 12;

    private static final int SLOT_BG = 0xFF1A1215;
    private static final int SLOT_BORDER = 0xFF912E35;

    private static final float ANIM_SECONDS = 0.12f;
    private static final long ANIM_RESET_GAP_MS = 120L;
    private static final float SLIDE_PIXELS = 5.0f;

    private static final ItemStack[] BUFFER = new ItemStack[CAPACITY];

    private static int animKey = Integer.MIN_VALUE;
    private static float animProgress;
    private static long animLastMs;

    private AutismShulkerPreview() {
    }

    public static boolean shouldPreview(ItemStack stack) {
        return InventoryTweaksModule.shulkerPreviewEnabled() && hasPreviewContents(stack);
    }

    private static boolean hasPreviewContents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent();
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int targetKey,
                              int mouseX, int mouseY, int screenW, int screenH) {
        try {
            int count = fillItems(stack);
            if (count <= 0) return;

            int rows = Math.min(MAX_ROWS, Math.max(1, (count + COLUMNS - 1) / COLUMNS));
            int columns = Math.min(COLUMNS, count);
            int width = columns * SLOT;
            int height = rows * SLOT;

            int[] placement = place(font, stack, mouseX, mouseY, screenW, screenH, width, height);
            if (placement == null) return;
            int x = placement[0];
            int y = placement[1];
            int awayX = placement[2];
            int awayY = placement[3];

            float eased = advanceAnimation(targetKey);
            float slide = (1.0f - eased) * SLIDE_PIXELS;
            graphics.pose().pushMatrix();
            graphics.pose().translate(awayX * slide, awayY * slide);
            try {
                drawGrid(graphics, font, x, y, count);
            } finally {
                graphics.pose().popMatrix();
            }
        } catch (Throwable ignored) {

        }
    }

    private static int[] place(Font font, ItemStack stack, int mouseX, int mouseY, int screenW, int screenH, int pw, int ph) {
        int[] tip = tooltipRect(font, stack, mouseX, mouseY, screenW, screenH);
        if (tip == null) {

            int x = mouseX + CURSOR_GAP;
            if (x + pw > screenW - SCREEN_MARGIN) x = mouseX - pw - CURSOR_GAP;
            x = clamp(x, SCREEN_MARGIN, screenW - SCREEN_MARGIN - pw);
            int y = mouseY - ph - CURSOR_GAP;
            if (y < SCREEN_MARGIN) y = mouseY + SLOT;
            y = clamp(y, SCREEN_MARGIN, screenH - SCREEN_MARGIN - ph);
            return new int[]{x, y, 0, -1};
        }

        int ax = tip[0];
        int ay = tip[1];
        int aw = tip[2];
        int ah = tip[3];
        int alignedX = clamp(ax, SCREEN_MARGIN, screenW - SCREEN_MARGIN - pw);
        int alignedY = clamp(ay, SCREEN_MARGIN, screenH - SCREEN_MARGIN - ph);

        int aboveY = ay - GAP - ph;
        if (aboveY >= SCREEN_MARGIN) return new int[]{alignedX, aboveY, 0, -1};

        int belowY = ay + ah + GAP;
        if (belowY + ph <= screenH - SCREEN_MARGIN) return new int[]{alignedX, belowY, 0, 1};

        int rightX = ax + aw + GAP;
        if (rightX + pw <= screenW - SCREEN_MARGIN) return new int[]{rightX, alignedY, 1, 0};

        int leftX = ax - GAP - pw;
        if (leftX >= SCREEN_MARGIN) return new int[]{leftX, alignedY, -1, 0};

        return null;
    }

    private static int[] tooltipRect(Font font, ItemStack stack, int mouseX, int mouseY, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        List<Component> lines = Screen.getTooltipFromItem(mc, stack);
        if (lines == null || lines.isEmpty()) return null;

        int textWidth = 0;
        for (Component line : lines) textWidth = Math.max(textWidth, font.width(line));
        int textHeight = lines.size() == 1 ? TOOLTIP_LINE_HEIGHT - 2 : lines.size() * TOOLTIP_LINE_HEIGHT;

        int posX = mouseX + TOOLTIP_MOUSE_DX;
        int posY = mouseY - TOOLTIP_MOUSE_DY;
        if (posX + textWidth > screenW) posX = Math.max(posX - 24 - textWidth, 4);
        int paddedHeight = textHeight + 3;
        if (posY + paddedHeight > screenH) posY = screenH - paddedHeight;

        return new int[]{
            posX - TOOLTIP_INSET,
            posY - TOOLTIP_INSET,
            textWidth + TOOLTIP_INSET * 2,
            textHeight + TOOLTIP_INSET * 2
        };
    }

    private static void drawGrid(GuiGraphicsExtractor graphics, Font font, int x, int y, int count) {

        for (int index = 0; index < count; index++) {
            int slotX = x + (index % COLUMNS) * SLOT;
            int slotY = y + (index / COLUMNS) * SLOT;
            UiBounds cell = UiBounds.of(slotX, slotY, SLOT, SLOT);
            UiRenderer.rect(graphics, cell, SLOT_BG);
            UiRenderer.outline(graphics, cell, SLOT_BORDER);
            ItemStack item = BUFFER[index];
            if (item != null && !item.isEmpty()) {
                graphics.item(item, slotX + 1, slotY + 1);
                graphics.itemDecorations(font, item, slotX + 1, slotY + 1);
            }
        }
    }

    private static float advanceAnimation(int targetKey) {
        long now = System.currentTimeMillis();
        if (targetKey != animKey || now - animLastMs > ANIM_RESET_GAP_MS) {
            animKey = targetKey;
            animProgress = 0.0f;
        }
        float dt = Math.min(64L, Math.max(0L, now - animLastMs)) / 1000.0f;
        animLastMs = now;
        animProgress = Math.min(1.0f, animProgress + dt / ANIM_SECONDS);
        float inverse = 1.0f - animProgress;
        return 1.0f - inverse * inverse * inverse;
    }

    private static int fillItems(ItemStack stack) {
        Arrays.fill(BUFFER, ItemStack.EMPTY);
        ItemContainerContents contents = stack == null ? null : stack.get(DataComponents.CONTAINER);
        if (contents == null) return 0;
        int filled = 0;
        Iterator<ItemStack> iterator = contents.nonEmptyItemCopyStream().iterator();
        while (iterator.hasNext() && filled < CAPACITY) {
            ItemStack item = iterator.next();
            if (item != null && !item.isEmpty()) BUFFER[filled++] = item;
        }
        return filled;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
