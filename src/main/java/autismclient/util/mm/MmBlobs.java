package autismclient.util.mm;

import autismclient.mixin.accessor.AbstractContainerScreenAccessor;
import autismclient.modules.Module;
import autismclient.api.module.Setting;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismClipboardHelper;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismSharedState;
import autismclient.util.mm.msg.MmMessages;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MmBlobs {
    private MmBlobs() {}

    public record SlotView(int x, int y, ItemStack item) {}

    public record GuiSnapshot(Component title, List<SlotView> slots, int width, int height) {}

    private static HolderLookup.Provider reg() { return AutismClipboardHelper.registries(); }

    public static MmMessages.BlobOffer captureGui() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return null;
        AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) screen).autism$getMenu();
        if (menu == null) return null;
        try {
            HolderLookup.Provider provider = reg();
            CompoundTag root = new CompoundTag();
            JsonElement titleJson = ComponentSerialization.CODEC
                .encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), screen.getTitle())
                .result().orElse(null);
            root.putString("title", titleJson != null ? titleJson.toString() : "");

            ListTag slots = new ListTag();
            for (Slot slot : menu.slots) {

                if (slot.x < 0 || slot.y < 0) continue;
                CompoundTag s = new CompoundTag();
                s.putInt("x", slot.x);
                s.putInt("y", slot.y);
                ItemStack st = slot.getItem();
                if (st != null && !st.isEmpty()) {
                    Tag enc = ItemStack.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), st).result().orElse(null);
                    if (enc instanceof CompoundTag ct) s.put("item", ct);
                }
                slots.add(s);
            }
            root.put("slots", slots);
            String data = compress(root);
            if (data == null) return null;
            String name = screen.getTitle().getString();
            if (name == null || name.isBlank()) name = "GUI";
            return new MmMessages.BlobOffer("gui", name, slots.size(), data);
        } catch (Throwable t) {
            return null;
        }
    }

    public static GuiSnapshot decodeGui(MmMessages.BlobOffer b) {
        if (b == null || !"gui".equals(b.kind)) return null;
        try {
            CompoundTag root = decompress(b.data);
            if (root == null) return null;
            HolderLookup.Provider provider = reg();
            Component title;
            try {
                String titleJson = root.getStringOr("title", "");
                JsonElement el = JsonParser.parseString(titleJson.isBlank() ? "\"\"" : titleJson);
                title = ComponentSerialization.CODEC.parse(provider.createSerializationContext(JsonOps.INSTANCE), el)
                    .result().orElse(Component.literal(b.friendlyName));
            } catch (Throwable t) { title = Component.literal(b.friendlyName); }

            ListTag slots = root.getList("slots").orElse(new ListTag());
            List<int[]> coords = new ArrayList<>();
            List<ItemStack> stacks = new ArrayList<>();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int i = 0; i < slots.size(); i++) {
                if (!(slots.get(i) instanceof CompoundTag ct)) continue;
                int sx = ct.getIntOr("x", (i % 9) * 18);
                int sy = ct.getIntOr("y", (i / 9) * 18);

                if (sx < 0 || sy < 0) continue;
                Tag item = ct.get("item");

                if (item == null && (ct.contains("id") || ct.contains("count"))) item = ct;
                ItemStack st = ItemStack.EMPTY;
                if (item instanceof CompoundTag ic && !ic.isEmpty()) {
                    st = ItemStack.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), ic).result().orElse(ItemStack.EMPTY);
                }
                coords.add(new int[]{sx, sy});
                stacks.add(st);
                minX = Math.min(minX, sx); minY = Math.min(minY, sy);
                maxX = Math.max(maxX, sx); maxY = Math.max(maxY, sy);
            }
            if (coords.isEmpty()) { minX = 0; minY = 0; maxX = 0; maxY = 0; }
            List<SlotView> views = new ArrayList<>(coords.size());
            for (int i = 0; i < coords.size(); i++) {
                views.add(new SlotView(coords.get(i)[0] - minX, coords.get(i)[1] - minY, stacks.get(i)));
            }
            return new GuiSnapshot(title, views, (maxX - minX) + 16, (maxY - minY) + 16);
        } catch (Throwable t) {
            return null;
        }
    }

    public static MmMessages.BlobOffer captureHeldItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack st = player.getMainHandItem();
        if (st == null || st.isEmpty()) return null;
        try {
            Tag enc = ItemStack.CODEC.encodeStart(reg().createSerializationContext(NbtOps.INSTANCE), st).result().orElse(null);
            if (!(enc instanceof CompoundTag ct)) return null;
            CompoundTag root = new CompoundTag();
            root.put("item", ct);
            String data = compress(root);
            if (data == null) return null;
            return new MmMessages.BlobOffer("item", st.getHoverName().getString(), st.getCount(), data);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack decodeItem(MmMessages.BlobOffer b) {
        if (b == null || !"item".equals(b.kind)) return ItemStack.EMPTY;
        try {
            CompoundTag root = decompress(b.data);
            if (root == null) return ItemStack.EMPTY;
            Tag item = root.get("item");
            if (!(item instanceof CompoundTag ct)) return ItemStack.EMPTY;
            return ItemStack.CODEC.parse(reg().createSerializationContext(NbtOps.INSTANCE), ct).result().orElse(ItemStack.EMPTY);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    public static MmMessages.BlobOffer captureFilter() {
        AutismSharedState s = AutismSharedState.get();
        Set<Class<? extends Packet<?>>> c2s = s.getC2SPackets();
        Set<Class<? extends Packet<?>>> s2c = s.getS2CPackets();
        CompoundTag root = new CompoundTag();
        root.putString("c2s", classNames(c2s));
        root.putString("s2c", classNames(s2c));
        String data = compress(root);
        if (data == null) return null;
        return new MmMessages.BlobOffer("filter", "Packet Filter", c2s.size() + s2c.size(), data);
    }

    public static int importFilter(MmMessages.BlobOffer b) {
        if (b == null || !"filter".equals(b.kind)) return 0;
        CompoundTag root = decompress(b.data);
        if (root == null) return 0;
        AutismSharedState s = AutismSharedState.get();
        Set<Class<? extends Packet<?>>> c2s = new HashSet<>(s.getC2SPackets());
        Set<Class<? extends Packet<?>>> s2c = new HashSet<>(s.getS2CPackets());
        int added = resolve(root.getStringOr("c2s", ""), c2s) + resolve(root.getStringOr("s2c", ""), s2c);
        s.setC2SPackets(c2s);
        s.setS2CPackets(s2c);
        return added;
    }

    public record FilterView(List<String> c2s, List<String> s2c) {}

    public static FilterView decodeFilter(MmMessages.BlobOffer b) {
        if (b == null || !"filter".equals(b.kind)) return new FilterView(List.of(), List.of());
        CompoundTag root = decompress(b.data);
        if (root == null) return new FilterView(List.of(), List.of());
        return new FilterView(simpleNames(root.getStringOr("c2s", "")), simpleNames(root.getStringOr("s2c", "")));
    }

    private static List<String> simpleNames(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isBlank()) return out;
        for (String raw : joined.split("\n")) {
            String n = raw.trim();
            if (n.isEmpty()) continue;
            int dot = n.lastIndexOf('.');
            out.add(dot >= 0 ? n.substring(dot + 1) : n);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static String classNames(Set<Class<? extends Packet<?>>> set) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c : set) { if (sb.length() > 0) sb.append('\n'); sb.append(c.getName()); }
        return sb.toString();
    }

    private static volatile Map<String, Class<? extends Packet<?>>> knownPacketsByName;

    private static Map<String, Class<? extends Packet<?>>> knownPacketsByName() {
        Map<String, Class<? extends Packet<?>>> map = knownPacketsByName;
        if (map == null) {
            Map<String, Class<? extends Packet<?>>> built = new HashMap<>();
            try {
                for (Class<? extends Packet<?>> c : AutismPacketRegistry.getC2SPackets()) built.put(c.getName(), c);
                for (Class<? extends Packet<?>> c : AutismPacketRegistry.getS2CPackets()) built.put(c.getName(), c);
            } catch (Throwable ignored) {  }

            if (!built.isEmpty()) knownPacketsByName = built;
            map = built;
        }
        return map;
    }

    private static int resolve(String joined, Set<Class<? extends Packet<?>>> into) {
        if (joined == null || joined.isBlank()) return 0;
        Map<String, Class<? extends Packet<?>>> known = knownPacketsByName();
        int added = 0;
        for (String raw : joined.split("\n")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            Class<? extends Packet<?>> c = known.get(name);
            if (c != null && into.add(c)) added++;
        }
        return added;
    }

    public record ModuleView(String moduleId, String name, List<String[]> settings) {}

    public static MmMessages.BlobOffer captureModule(Module m) {
        if (m == null) return null;
        try {
            CompoundTag root = new CompoundTag();
            root.putString("id", m.id());
            root.putString("name", m.name());
            CompoundTag s = new CompoundTag();
            for (Setting<?, ?> opt : m.settings()) s.putString(opt.id(), m.value(opt.id()));
            root.put("settings", s);
            String data = compress(root);
            if (data == null) return null;
            return new MmMessages.BlobOffer("module", m.name(), m.settings().size(), data);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ModuleView decodeModule(MmMessages.BlobOffer b) {
        if (b == null || !"module".equals(b.kind)) return null;
        CompoundTag root = decompress(b.data);
        if (root == null) return null;
        String id = MmText.clean(root.getStringOr("id", ""), 64);
        String name = MmText.clean(root.getStringOr("name", b.friendlyName), 48);
        CompoundTag s = root.getCompound("settings").orElse(new CompoundTag());
        List<String[]> settings = new ArrayList<>();
        for (String key : s.keySet()) settings.add(new String[]{MmText.clean(key, 48), MmText.clean(s.getStringOr(key, ""), 96)});
        settings.sort((a, c) -> a[0].compareToIgnoreCase(c[0]));
        return new ModuleView(id, name, settings);
    }

    public static int applyModule(MmMessages.BlobOffer b) {
        if (b == null || !"module".equals(b.kind)) return -1;
        CompoundTag root = decompress(b.data);
        if (root == null) return -1;
        Module m = ModuleRegistry.get(root.getStringOr("id", ""));
        if (m == null) return -1;
        CompoundTag s = root.getCompound("settings").orElse(new CompoundTag());
        int applied = 0;
        for (String key : s.keySet()) {
            if (m.setting(key) != null) { m.setValue(key, s.getStringOr(key, "")); applied++; }
        }
        return applied;
    }

    public static String moduleName(MmMessages.BlobOffer b) {
        ModuleView mv = decodeModule(b);
        return mv == null ? (b == null ? "" : b.friendlyName) : mv.name();
    }

    public static MmMessages.BlobOffer capturePosition() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return null;
        String dim = player.level().dimension().identifier().toString();
        int x = (int) Math.floor(player.getX()), y = (int) Math.floor(player.getY()), z = (int) Math.floor(player.getZ());
        return new MmMessages.BlobOffer("position", shortDim(dim) + "  " + x + " " + y + " " + z, 0, x + " " + y + " " + z);
    }

    public static MmMessages.BlobOffer captureServer() {
        Minecraft mc = Minecraft.getInstance();
        ServerData sd = mc.getCurrentServer();
        if (sd == null || sd.ip == null || sd.ip.isBlank()) return null;
        String name = (sd.name == null || sd.name.isBlank()) ? sd.ip : sd.name;
        int players = 0;
        int ping = -1;
        try {
            var conn = mc.getConnection();
            if (conn != null && conn.getOnlinePlayers() != null) players = conn.getOnlinePlayers().size();

            if (conn != null && mc.player != null) {
                var info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = Math.max(0, Math.min(99999, info.getLatency()));
            }
        } catch (Throwable ignored) {  }
        if (ping < 0) { try { ping = (int) Math.max(0, Math.min(99999, sd.ping)); } catch (Throwable ignored) {  } }
        int maxPlayers = 0;
        try { if (sd.players != null) maxPlayers = Math.max(0, sd.players.max()); } catch (Throwable ignored) {  }
        String motd = serverMotdText(sd);
        String data = field(name) + "|" + field(sd.ip) + "|" + players + "|" + maxPlayers + "|" + ping + "|" + MmText.clean(motd, 120);
        return new MmMessages.BlobOffer("server", name, players, data);
    }

    private static String serverMotdText(ServerData sd) {
        try {
            java.lang.reflect.Field f;
            try { f = ServerData.class.getField("motd"); }
            catch (NoSuchFieldException e) { f = ServerData.class.getDeclaredField("motd"); }
            f.setAccessible(true);
            Object v = f.get(sd);
            if (v instanceof net.minecraft.network.chat.Component c) {
                String s = c.getString();
                return s == null ? "" : s.trim();
            }
        } catch (Throwable ignored) {  }
        return "";
    }

    private static String field(String s) { return MmText.clean(s, 80).replace('|', ' '); }

    private static String[] serverParts(MmMessages.BlobOffer b) {
        return b == null || b.data == null ? new String[0] : b.data.split("\\|", 6);
    }

    public static String serverName(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        return p.length >= 1 ? p[0] : "";
    }

    public static String serverIp(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        return p.length >= 2 ? p[1] : "";
    }

    public static int serverPlayers(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        if (p.length >= 3) { try { return Integer.parseInt(p[2].trim()); } catch (NumberFormatException ignored) {  } }
        return b == null ? 0 : b.count;
    }

    public static int serverPlayersMax(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        if (p.length >= 6) { try { return Math.max(0, Integer.parseInt(p[3].trim())); } catch (NumberFormatException ignored) {  } }
        return 0;
    }

    public static int serverPing(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        if (p.length >= 6) { try { return Integer.parseInt(p[4].trim()); } catch (NumberFormatException ignored) {  } }
        return -1;
    }

    public static String serverMotd(MmMessages.BlobOffer b) {
        String[] p = serverParts(b);
        if (p.length >= 6) return p[5];
        if (p.length == 4) return p[3];
        return "";
    }

    public static String displayAddr(String ip) {
        if (ip == null) return "";
        String s = ip.strip();
        if (s.endsWith(":25565")) return s.substring(0, s.length() - ":25565".length());
        return s;
    }

    public static String shortDim(String dim) {
        if (dim == null) return "?";
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    private static final String CLIPBOARD_TYPE = "autism_mm_blob";

    public static String encodeOffer(MmMessages.BlobOffer b) {
        if (b == null) return "";
        CompoundTag root = new CompoundTag();
        root.putString("type", CLIPBOARD_TYPE);
        root.putString("kind", b.kind == null ? "" : b.kind);
        root.putString("name", b.friendlyName == null ? "" : b.friendlyName);
        root.putInt("count", b.count);
        root.putString("data", b.data == null ? "" : b.data);
        String s = compress(root);
        return s == null ? "" : s;
    }

    public static MmMessages.BlobOffer decodeOffer(String base64) {
        try {
            CompoundTag root = decompress(base64);
            if (root == null || !CLIPBOARD_TYPE.equals(root.getStringOr("type", ""))) return null;
            String kind = root.getStringOr("kind", "");
            if (kind.isBlank()) return null;
            return new MmMessages.BlobOffer(kind, root.getStringOr("name", ""),
                root.getIntOr("count", 0), root.getStringOr("data", ""));
        } catch (Throwable t) {
            return null;
        }
    }

    private static String compress(CompoundTag root) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Throwable t) { return null; }
    }

    private static CompoundTag decompress(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);

            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), AutismClipboardHelper.safeNbtAccounter());
        } catch (Throwable t) { return null; }
    }
}
