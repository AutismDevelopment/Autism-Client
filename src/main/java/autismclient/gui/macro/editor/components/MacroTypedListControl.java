package autismclient.gui.macro.editor.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public final class MacroTypedListControl {
    private static final int GAP = 2;
    private static final int ADD_WIDTH = 34;
    private static final int SLOT_WIDTH = 44;

    private MacroTypedListControl() {
    }

    public static ToolbarLayout toolbar(UiBounds bounds, boolean slotField, boolean capture) {
        int captureWidth = capture ? MacroCaptureButton.WIDTH : 0;
        int captureX = bounds.right() - captureWidth;
        int addX = (capture ? captureX : bounds.right()) - 3 - ADD_WIDTH;
        int slotX = slotField ? addX - GAP - SLOT_WIDTH : 0;
        int textRight = slotField ? slotX : addX;
        return new ToolbarLayout(
                UiBounds.of(bounds.x(), bounds.y(), Math.max(1, textRight - bounds.x() - GAP), MacroCaptureButton.HEIGHT),
                slotField ? UiBounds.of(slotX, bounds.y(), SLOT_WIDTH, MacroCaptureButton.HEIGHT) : null,
                UiBounds.of(addX, bounds.y(), ADD_WIDTH, MacroCaptureButton.HEIGHT),
                capture ? UiBounds.of(captureX, bounds.y(), captureWidth, MacroCaptureButton.HEIGHT) : null
        );
    }

    public static <T> List<Integer> refilter(List<T> values, List<Integer> target, Predicate<T> predicate) {
        target.clear();
        if (values == null || predicate == null) return target;
        for (int index = 0; index < values.size(); index++) {
            if (predicate.test(values.get(index))) target.add(index);
        }
        return target;
    }

    public static <T> List<T> refilterValues(List<T> values, List<T> target, Predicate<T> predicate) {
        target.clear();
        if (values == null || predicate == null) return target;
        for (T value : values) {
            if (predicate.test(value)) target.add(value);
        }
        return target;
    }

    public static void renderRow(
            GuiGraphicsExtractor graphics,
            Font font,
            Component label,
            ItemStack icon,
            UiBounds bounds,
            boolean hovered,
            boolean selected,
            CompactListRenderer.RowTone tone,
            boolean minecraftText
    ) {
        int color = switch (tone == null ? CompactListRenderer.RowTone.NORMAL : tone) {
            case READY -> selected ? 0xFFE8FFF0 : 0xFF9DF0BA;
            case WARNING -> selected ? 0xFFFFF3E0 : 0xFFFFC67A;
            case MISSING -> selected ? 0xFFFFECEC : 0xFFFFAFAF;
            case DANGER -> selected ? 0xFFFFEAEA : 0xFFFF8C8C;
            case NORMAL -> selected ? autismclient.util.AutismTheme.recolor(0xFFFFF4F4, autismclient.util.AutismTheme.Channel.TEXT) : 0xFFFFFFFF;
        };
        CompactListRenderer.drawRowWithColorAndIcon(
                graphics,
                font,
                label,
                icon == null ? ItemStack.EMPTY : icon,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                hovered,
                selected,
                color,
                minecraftText
        );
    }

    public static void renderRow(
            GuiGraphicsExtractor graphics,
            Font font,
            Component label,
            UiBounds bounds,
            boolean hovered,
            boolean selected,
            CompactListRenderer.RowTone tone,
            boolean minecraftText
    ) {
        renderRow(graphics, font, label, ItemStack.EMPTY, bounds, hovered, selected, tone, minecraftText);
    }

    public static void renderRowWithColor(
            GuiGraphicsExtractor graphics,
            Font font,
            Component label,
            UiBounds bounds,
            boolean hovered,
            boolean selected,
            int textColor,
            boolean minecraftText
    ) {
        CompactListRenderer.drawRowWithColor(
                graphics,
                font,
                label,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                hovered,
                selected,
                textColor,
                minecraftText
        );
    }

    public static void renderDelete(GuiGraphicsExtractor graphics, UiBounds bounds, boolean hovered) {
        CompactListRenderer.drawDeleteButton(
                graphics,
                bounds.x(),
                bounds.y() + 1,
                bounds.width(),
                Math.max(1, bounds.height() - 2),
                hovered
        );
    }

    public static void renderSymbol(GuiGraphicsExtractor graphics, UiBounds bounds, String symbol, boolean hovered, boolean danger) {
        CompactListRenderer.drawStructuralButton(graphics, bounds.x(), bounds.y(), bounds.width(), symbol, hovered, danger);
    }

    public record ToolbarLayout(UiBounds text, UiBounds slot, UiBounds add, UiBounds capture) {
    }
}
