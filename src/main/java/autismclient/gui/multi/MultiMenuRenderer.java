package autismclient.gui.multi;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.multi.MultiSession;
import autismclient.util.multi.MultiSession.MenuExtras;
import autismclient.util.multi.MultiSession.MenuView;
import autismclient.util.multi.MultiSession.TradeView;
import autismclient.util.multi.MultiSession.ViewSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

public final class MultiMenuRenderer {
    private MultiMenuRenderer() {
    }

    public sealed interface MenuAction
        permits ButtonAct, TradeAct, BeaconAct, BeaconPick, RenameFocusAct, RecipeStep {
    }

    public record ButtonAct(int id) implements MenuAction {
    }

    public record TradeAct(int index) implements MenuAction {
    }

    public record BeaconAct(int primary, int secondary) implements MenuAction {
    }

    public record BeaconPick(boolean secondary, int effectId) implements MenuAction {
    }

    public record RenameFocusAct() implements MenuAction {
    }

    public record RecipeStep(int delta) implements MenuAction {
    }

    public record MenuHit(int x, int y, int w, int h, MenuAction action) {
    }

    private static final net.minecraft.network.chat.FontDescription ALT_FONT =
        new net.minecraft.network.chat.FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
    private static final int TEXT = 0xFFEDEDED;
    private static final int DIM = 0xFF9A9A9A;
    private static final int GREEN = 0xFF54FB54;
    private static final int RED = 0xFFFF5B5B;
    private static final int GOLD = 0xFFE8E8C0;
    private static final int OUTLINE = 0xFF5B5B5B;
    private static final int OUTLINE_OFF = 0xFF3A3A3A;
    private static final int TRACK = 0xFF20222A;
    private static final int COOK = 0xFFE0902A;
    private static final int FLAME = 0xFFFF7A1E;

    public static void render(GuiGraphicsExtractor g, Font font, MenuView view, int originX, int originY, int scrollY,
                              int mouseX, int mouseY, List<MenuHit> hitsOut, MultiMenuInput in) {
        if (view == null || view.extras() == null) return;
        Ctx c = new Ctx(g, font, originX, originY, scrollY, mouseX, mouseY, hitsOut);
        MenuExtras ex = view.extras();
        switch (strip(ex.typeId())) {
            case "enchantment" -> enchantment(c, ex);
            case "furnace", "blast_furnace", "smoker" -> furnace(c, ex);
            case "brewing_stand" -> brewing(c, ex);
            case "anvil" -> anvil(c, ex, view, in.rename.focused() ? in.rename.text() : null);
            case "beacon" -> beacon(c, ex, in);
            case "merchant" -> merchant(c, ex);
            case "lectern" -> lectern(c, ex);
            case "stonecutter", "loom" -> recipeStepper(c, ex);
            default -> {

            }
        }
    }

    public static int[] contentSize(MenuView view) {
        int w = 18;
        int h = 18;
        if (view != null) {
            for (ViewSlot s : view.slots()) {
                w = Math.max(w, s.x() + 18);
                h = Math.max(h, s.y() + 18);
            }
            int[] ext = widgetExtent(view);
            w = Math.max(w, ext[0]);
            h = Math.max(h, ext[1]);
        }
        return new int[]{w, h};
    }

    private static int[] widgetExtent(MenuView view) {
        MenuExtras ex = view.extras();
        if (ex == null) return new int[]{0, 0};
        return switch (strip(ex.typeId())) {
            case "enchantment" -> new int[]{MultiMenuGeometry.ENCH_ROW_X + MultiMenuGeometry.ENCH_ROW_W, 3 * MultiMenuGeometry.ENCH_ROW_H};
            case "merchant" -> new int[]{90 + 3 * 18,
                MultiMenuGeometry.MERCHANT_TRADE_TOP + Math.max(1, ex.trades().size()) * MultiMenuGeometry.MERCHANT_TRADE_H};
            case "beacon" -> new int[]{232, 108};
            case "lectern" -> new int[]{120, 44};
            case "anvil" -> new int[]{132, 50};
            case "stonecutter" -> new int[]{130, 54};
            case "loom" -> new int[]{130, 70};
            default -> new int[]{0, 0};
        };
    }

    private static void enchantment(Ctx c, MenuExtras ex) {
        int[] d = ex.data();
        EnchantmentNames names = EnchantmentNames.getInstance();
        names.initSeed(idx(d, 3, 0));
        Registry<Enchantment> reg = enchantRegistry();
        int rw = MultiMenuGeometry.ENCH_ROW_W;
        int rh = MultiMenuGeometry.ENCH_ROW_H - 2;
        for (int i = 0; i < 3; i++) {
            int cost = idx(d, i, 0);
            boolean enabled = cost > 0;
            int sx = c.sx(MultiMenuGeometry.ENCH_ROW_X);
            int sy = c.sy(i * MultiMenuGeometry.ENCH_ROW_H);
            boolean hover = enabled && c.hover(sx, sy, rw, rh);
            c.frame(sx, sy, rw, rh, hover ? 0x33FFFFFF : (enabled ? 0x1AFFFFFF : 0x0DFFFFFF), enabled ? OUTLINE : OUTLINE_OFF);

            String sga = names.getRandomName(c.font, rw - 22).getString();
            c.text(Component.literal(sga).setStyle(Style.EMPTY.withFont(ALT_FONT)), sx + 4, sy + 3, enabled ? 0xFF6A6A6A : 0xFF3F3F3F);

            int clueId = idx(d, 4 + i, -1);
            int clueLvl = idx(d, 7 + i, 1);
            if (enabled && clueId >= 0 && reg != null) {
                try {
                    reg.get(clueId).ifPresent(h -> c.text(Enchantment.getFullname(h, clueLvl), sx + 4, sy + 12, GOLD));
                } catch (Throwable ignored) {

                }
            }
            if (enabled) {
                String cs = Integer.toString(cost);
                c.text(cs, sx + rw - 4 - c.font.width(cs), sy + 3, GREEN);
                c.hits.add(new MenuHit(sx, sy, rw, rh, new ButtonAct(i)));
            }
        }
    }

    private static void furnace(Ctx c, MenuExtras ex) {
        int[] d = ex.data();
        int litTime = idx(d, 0, 0);
        int litDur = idx(d, 1, 0);
        int cook = idx(d, 2, 0);
        int cookTotal = idx(d, 3, 0);
        double burn = litDur > 0 ? clamp01(litTime / (double) litDur) : 0;
        double prog = cookTotal > 0 ? clamp01(cook / (double) cookTotal) : 0;
        int ax = c.sx(20);
        int ay = c.sy(23);
        c.rect(ax, ay, 46, 6, TRACK);
        c.rect(ax, ay, (int) (46 * prog), 6, COOK);
        int fx = c.sx(6);
        int fyTop = c.sy(20);
        int fh = 14;
        c.rect(fx, fyTop, 6, fh, TRACK);
        int lit = (int) (fh * burn);
        c.rect(fx, fyTop + (fh - lit), 6, lit, FLAME);
    }

    private static void brewing(Ctx c, MenuExtras ex) {
        int[] d = ex.data();
        int brewTime = idx(d, 0, 0);
        int fuel = idx(d, 1, 0);
        double brew = brewTime > 0 ? clamp01((400 - brewTime) / 400.0) : 0;
        int bx = c.sx(50);
        int byTop = c.sy(18);
        c.rect(bx, byTop, 6, 30, TRACK);
        c.rect(bx, byTop, 6, (int) (30 * brew), 0xFFD070E0);
        int gx = c.sx(0);
        int gy = c.sy(50);
        c.rect(gx, gy, 18, 4, TRACK);
        c.rect(gx, gy, (int) (18 * clamp01(fuel / 20.0)), 4, 0xFFF0B030);
    }

    private static void anvil(Ctx c, MenuExtras ex, MenuView view, String renameText) {
        int cost = idx(ex.data(), 0, 0);
        int bx = c.sx(0);
        int by = c.sy(0);
        int bw = 132;
        int bh = 14;
        boolean focused = renameText != null;
        c.frame(bx, by, bw, bh, 0x22000000, focused ? 0xFFE8E8C0 : OUTLINE);
        ItemStack result = slot(view, 2);
        String label;
        int color;
        if (focused) {
            label = renameText + "_";
            color = TEXT;
        } else {
            boolean has = !result.isEmpty();
            label = has ? result.getHoverName().getString() : "Rename...";
            color = has ? TEXT : DIM;
        }
        c.text(trim(c.font, label, bw - 6), bx + 3, by + 3, color);
        c.hits.add(new MenuHit(bx, by, bw, bh, new RenameFocusAct()));
        if (cost > 0) {
            c.text("Cost: " + cost, c.sx(0), c.sy(38), cost >= 40 ? RED : GREEN);
        }
    }

    private static void beacon(Ctx c, MenuExtras ex, MultiMenuInput in) {
        int[] d = ex.data();
        int tier = idx(d, 0, 0);
        int effPrimary = in.beaconPrimary >= 0 ? in.beaconPrimary : idx(d, 1, -1);
        int effSecondary = in.beaconSecondary >= 0 ? in.beaconSecondary : idx(d, 2, -1);
        c.text("Beacon tier " + tier, c.sx(24), c.sy(2), tier > 0 ? TEXT : DIM);
        List<List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>>> effects =
            net.minecraft.world.level.block.entity.BeaconBlockEntity.BEACON_EFFECTS;
        int y = 14;

        c.text("Power", c.sx(24), c.sy(y), DIM);
        y += 10;
        int primaryBottom = y;
        for (int row = 0; row < 3 && row < effects.size(); row++) {
            if (row >= tier) break;
            for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h : effects.get(row)) {
                int id = effectId(h);
                effectButton(c, c.sx(24), c.sy(primaryBottom), 100, 11, effectName(id), id == effPrimary,
                    new BeaconPick(false, id));
                primaryBottom += 12;
            }
        }

        if (tier >= 4) {
            int sy = 14;
            c.text("Secondary", c.sx(132), c.sy(sy), DIM);
            sy += 10;
            if (effPrimary >= 0) {
                effectButton(c, c.sx(132), c.sy(sy), 100, 11, effectName(effPrimary) + " II", effSecondary == effPrimary,
                    new BeaconPick(true, effPrimary));
                sy += 12;
            }
            if (effects.size() >= 4) {
                for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h : effects.get(3)) {
                    int id = effectId(h);
                    effectButton(c, c.sx(132), c.sy(sy), 100, 11, effectName(id), effSecondary == id, new BeaconPick(true, id));
                    sy += 12;
                }
            }
        }

        button(c, c.sx(24), c.sy(primaryBottom + 6), 80, 12, "Confirm", new BeaconAct(effPrimary, effSecondary));
    }

    private static void effectButton(Ctx c, int sx, int sy, int w, int h, String label, boolean selected, MenuAction act) {
        boolean hover = c.hover(sx, sy, w, h);
        c.frame(sx, sy, w, h, selected ? 0x3354FB54 : (hover ? 0x33FFFFFF : 0x1AFFFFFF), selected ? GREEN : OUTLINE);
        c.text(trim(c.font, label, w - 6), sx + 3, sy + 2, selected ? GREEN : TEXT);
        c.hits.add(new MenuHit(sx, sy, w, h, act));
    }

    private static void recipeStepper(Ctx c, MenuExtras ex) {
        boolean loom = strip(ex.typeId()).equals("loom");
        int sy = loom ? 56 : 40;
        button(c, c.sx(36), c.sy(sy), 40, 12, "< Prev", new RecipeStep(-1));
        button(c, c.sx(80), c.sy(sy), 40, 12, "Next >", new RecipeStep(1));
    }

    private static void merchant(Ctx c, MenuExtras ex) {
        List<TradeView> trades = ex.trades();
        int rw = MultiMenuGeometry.MERCHANT_TRADE_W;
        int rh = MultiMenuGeometry.MERCHANT_TRADE_H - 2;
        for (int i = 0; i < trades.size(); i++) {
            TradeView t = trades.get(i);
            int sx = c.sx(MultiMenuGeometry.MERCHANT_TRADE_X);
            int sy = c.sy(MultiMenuGeometry.MERCHANT_TRADE_TOP + i * MultiMenuGeometry.MERCHANT_TRADE_H);
            boolean hover = c.hover(sx, sy, rw, rh);
            c.frame(sx, sy, rw, rh, hover ? 0x33FFFFFF : 0x14FFFFFF, t.outOfStock() ? 0xFF804040 : OUTLINE);
            c.item(t.costA(), sx + 2, sy + 1);
            if (!t.costB().isEmpty()) c.item(t.costB(), sx + 20, sy + 1);
            c.text(">", sx + 40, sy + 5, 0xFFBFBFBF);
            c.item(t.result(), sx + 50, sy + 1);
            if (t.outOfStock()) c.rect(sx, sy + rh / 2, rw, 1, 0xC0FF4040);
            c.hits.add(new MenuHit(sx, sy, rw, rh, new TradeAct(i)));
        }
    }

    private static void lectern(Ctx c, MenuExtras ex) {
        c.text("Page " + (idx(ex.data(), 0, 0) + 1), c.sx(24), c.sy(2), TEXT);
        button(c, c.sx(24), c.sy(14), 40, 12, "< Prev", new ButtonAct(1));
        button(c, c.sx(70), c.sy(14), 40, 12, "Next >", new ButtonAct(2));
        button(c, c.sx(24), c.sy(30), 86, 12, "Take Book", new ButtonAct(3));
    }

    private static void button(Ctx c, int sx, int sy, int w, int h, String label, MenuAction act) {
        boolean hover = c.hover(sx, sy, w, h);
        c.frame(sx, sy, w, h, hover ? 0x33FFFFFF : 0x1AFFFFFF, OUTLINE);
        c.text(label, sx + Math.max(2, (w - c.font.width(label)) / 2), sy + 2, TEXT);
        c.hits.add(new MenuHit(sx, sy, w, h, act));
    }

    private static Registry<Enchantment> enchantRegistry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String effectName(int id) {
        if (id < 0) return "-";
        return BuiltInRegistries.MOB_EFFECT.get(id).map(h -> h.value().getDisplayName().getString()).orElse("?");
    }

    private static int effectId(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h) {
        return h == null ? -1 : BuiltInRegistries.MOB_EFFECT.getId(h.value());
    }

    private static ItemStack slot(MenuView view, int handler) {
        for (ViewSlot s : view.slots()) {
            if (s.handler() == handler) return s.item() == null ? ItemStack.EMPTY : s.item();
        }
        return ItemStack.EMPTY;
    }

    private static int idx(int[] d, int i, int def) {
        return d != null && i >= 0 && i < d.length ? d[i] : def;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1.0, v);
    }

    private static String strip(String typeId) {
        if (typeId == null) return "";
        int c = typeId.indexOf(':');
        return c >= 0 ? typeId.substring(c + 1) : typeId;
    }

    private static String trim(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(b.toString() + s.charAt(i) + "...") > maxW) break;
            b.append(s.charAt(i));
        }
        return b + "...";
    }

    private static final class Ctx {
        private final GuiGraphicsExtractor g;
        private final Font font;
        private final int ox;
        private final int oy;
        private final int scrollY;
        private final int mx;
        private final int my;
        private final List<MenuHit> hits;

        Ctx(GuiGraphicsExtractor g, Font font, int ox, int oy, int scrollY, int mx, int my, List<MenuHit> hits) {
            this.g = g;
            this.font = font;
            this.ox = ox;
            this.oy = oy;
            this.scrollY = scrollY;
            this.mx = mx;
            this.my = my;
            this.hits = hits;
        }

        int sx(int wx) {
            return ox + wx;
        }

        int sy(int wy) {
            return oy + wy - scrollY;
        }

        boolean hover(int sx, int sy, int w, int h) {
            return mx >= sx && mx < sx + w && my >= sy && my < sy + h;
        }

        void rect(int sx, int sy, int w, int h, int color) {
            if (w <= 0 || h <= 0) return;
            UiRenderer.rect(g, UiBounds.of(sx, sy, w, h), color);
        }

        void frame(int sx, int sy, int w, int h, int fill, int outline) {
            UiRenderer.frame(g, UiBounds.of(sx, sy, w, h), fill, outline);
        }

        void text(String s, int sx, int sy, int color) {
            g.text(font, Component.literal(s), sx, sy, color, false);
        }

        void text(Component comp, int sx, int sy, int color) {
            g.text(font, comp, sx, sy, color, false);
        }

        void item(ItemStack stack, int sx, int sy) {
            if (stack == null || stack.isEmpty()) return;
            try {
                g.item(stack, sx, sy);
                g.itemDecorations(font, stack, sx, sy);
            } catch (Throwable ignored) {

            }
        }
    }
}
