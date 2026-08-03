package autismclient.util.mm;

import autismclient.util.AutismClientMessaging;
import autismclient.util.mm.msg.MmMessages;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

public final class MmChatComponents {
    private static final int TAG    = 0x7C89C8;
    private static final int MUTED  = 0x9A9A9A;
    private static final int BODY   = 0xE6E6E6;
    private static final int HILITE = 0xFFC857;
    private static final int BTN_DO  = 0x6FE3A0;
    private static final int BTN_VIEW = 0x8EA0FF;
    private static final int BTN_COPY = 0xB0B0B0;
    private static final int BTN_JOIN = 0xFFC857;

    private MmChatComponents() {}

    public static void emit(MmChatLine line) {
        if (line == null) return;
        try {
            AutismClientMessaging.send(build(line));
        } catch (Throwable ignored) {

        }
    }

    public static Component build(MmChatLine line) {
        MutableComponent root = Component.empty().append(tag());
        if (line.system) {
            return root.append(seg(trunc(line.text, 96), MUTED, true));
        }
        if (!line.isCard()) {
            return root.append(name(line)).append(seg(": ", MUTED, false)).append(seg(trunc(line.text, 220), BODY, false));
        }
        List<Btn> btns = new ArrayList<>();
        appendSentence(root, line, btns);
        if (!btns.isEmpty()) {
            int token = MmCardActions.register(line);
            for (Btn b : btns) root.append(seg("  ", MUTED, false)).append(btn(b, token));
        }
        return root;
    }

    private static MutableComponent btn(Btn b, int token) {
        Style style = Style.EMPTY.withColor(b.color())
            .withClickEvent(new ClickEvent.RunCommand("/autismcard " + b.action() + " " + token))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(b.hover())));
        return Component.literal("[" + b.label() + "]").setStyle(style);
    }

    private static void appendSentence(MutableComponent root, MmChatLine line, List<Btn> btns) {
        boolean self = line.self;
        switch (line.kind) {
            case MACRO_CARD -> {
                int steps = line.macro.actionCount;

                root.append(name(line)).append(seg(" shared a macro", MUTED, false));
                if (line.macro.macroName != null && !line.macro.macroName.isBlank())
                    root.append(seg(" \"" + trunc(line.macro.macroName, 26) + "\"", HILITE, false));
                root.append(seg(" (" + steps + (steps == 1 ? " step)" : " steps)"), MUTED, false));
                if (!self) btns.add(new Btn("Inspect", "inspect", "Review and save this macro to your library", BTN_VIEW));
                btns.add(copy("Copy the macro"));
            }
            case COMMAND_CARD -> {
                boolean tpa = MmCardActions.isTpa(line);
                if (tpa) {
                    if (self) {
                        root.append(name(line)).append(seg(" sent a TPA invite", MUTED, false));
                    } else if (MatchmakingManager.sameServerAs(MatchmakingManager.get().peer(line.senderFpHex))) {
                        root.append(name(line)).append(seg(" invited you to TPA", BODY, false));
                        btns.add(new Btn("Accept", "run", "Send /tpa to accept", BTN_DO));
                    } else {

                        root.append(name(line)).append(seg(" invited you to TPA ", MUTED, false))
                            .append(seg("(not on their server)", MUTED, true));
                    }
                } else {
                    root.append(name(line)).append(seg(" shared a command ", MUTED, false))
                        .append(seg(trunc(line.command.body, 44), HILITE, false));
                    if (!self) btns.add(new Btn("Execute", "run", "Run this command", BTN_DO));
                }
                btns.add(copy("Copy the command"));
            }
            case PACKET_CARD -> {
                root.append(name(line))
                    .append(seg(" shared a packet queue ", MUTED, false))
                    .append(seg("(" + trunc(line.cardHeadline(), 26) + ")", HILITE, false));
                if (!self) btns.add(new Btn("Add to queue", "addqueue", "Add these packets to your queue", BTN_DO));
                btns.add(new Btn("Inspect", "inspect", "Inspect the packet", BTN_VIEW));
                btns.add(copy("Copy the packet data"));
            }
            case BLOB_CARD -> appendBlob(root, line, btns, self);
            default -> root.append(name(line)).append(seg(": ", MUTED, false)).append(seg(trunc(line.text, 200), BODY, false));
        }
    }

    private static void appendBlob(MutableComponent root, MmChatLine line, List<Btn> btns, boolean self) {
        MmMessages.BlobOffer b = line.blob;
        switch (b.kind) {
            case "gui" -> {

                root.append(name(line)).append(seg(" shared a GUI ", MUTED, false))
                    .append(seg("\"" + trunc(b.friendlyName, 26) + "\"", HILITE, false));
                btns.add(new Btn("View", "view", "View the shared GUI", BTN_VIEW));
            }
            case "item" -> {
                root.append(name(line)).append(seg(" shared an item ", MUTED, false))
                    .append(seg(trunc(b.friendlyName, 28), HILITE, false));
                btns.add(new Btn("Inspect", "inspect", "Inspect the shared item", BTN_VIEW));
                btns.add(copy("Copy"));
            }
            case "filter" -> {
                root.append(name(line)).append(seg(" shared a packet filter ", MUTED, false))
                    .append(seg("(" + b.count + ")", HILITE, false));
                if (!self) btns.add(new Btn("Import", "importfilter", "Merge this packet filter", BTN_DO));
                btns.add(new Btn("Inspect", "inspect", "See the packets in this filter", BTN_VIEW));
                btns.add(copy("Copy"));
            }
            case "position" -> {
                root.append(name(line)).append(seg(" shared a position ", MUTED, false))
                    .append(seg(trunc(b.friendlyName, 36), HILITE, false));
                btns.add(copy("Copy the position"));
            }
            case "server" -> {
                String ip = MmBlobs.serverIp(b);
                int players = MmBlobs.serverPlayers(b);
                int max = MmBlobs.serverPlayersMax(b);
                int ping = MmBlobs.serverPing(b);
                String stats = " · " + players + (max > 0 ? "/" + max : "") + " online" + (ping >= 0 ? " · " + ping + "ms" : "");
                root.append(name(line)).append(seg(" shared a server ", MUTED, false))
                    .append(seg(trunc(displayAddr(ip), 30), HILITE, false))
                    .append(seg(stats, MUTED, false));
                if (!self && !MatchmakingManager.alreadyOn(ip)) btns.add(new Btn("Join", "join", "Join this server", BTN_JOIN));
                btns.add(copy("Copy the address"));
            }
            case "steps" -> {
                root.append(name(line)).append(seg(" shared macro steps ", MUTED, false))
                    .append(seg("(" + b.count + (b.count == 1 ? " step)" : " steps)"), HILITE, false));
                if (!self) {
                    btns.add(new Btn("Import", "importsteps", "Import as a new macro", BTN_DO));
                    btns.add(new Btn("To editor", "toeditor", "Open these steps in the macro editor", BTN_VIEW));
                }
                btns.add(copy("Copy the steps"));
            }
            case "module" -> {
                root.append(name(line)).append(seg(" shared module settings ", MUTED, false))
                    .append(seg("\"" + trunc(b.friendlyName, 24) + "\"", HILITE, false));
                btns.add(new Btn("Preview", "previewmodule", "Preview the module settings", BTN_VIEW));
                if (!self) btns.add(new Btn("Apply", "applymodule", "Apply these settings to your module", BTN_DO));
                btns.add(copy("Copy"));
            }
            default -> {
                root.append(name(line)).append(seg(" shared something", MUTED, false));
                btns.add(copy("Copy"));
            }
        }
    }

    private static Component tag() {
        return Component.literal("Lobby ").setStyle(Style.EMPTY.withColor(TAG))
            .append(Component.literal("· ").setStyle(Style.EMPTY.withColor(MUTED)));
    }

    private static MutableComponent name(MmChatLine line) {
        String n = trunc(MatchmakingManager.get().displayNameFor(line.senderFpHex), 24);
        MutableComponent c;
        if (MatchmakingManager.isGradientName(line.senderFpHex, line.self)) {

            c = Component.empty();
            int len = n.length();
            for (int i = 0; i < len; i++) {
                c.append(Component.literal(String.valueOf(n.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(MatchmakingManager.gradientNameColor(line.senderFpHex, line.self, i, len))));
            }
        } else {
            c = seg(n, MatchmakingManager.nameColor(line.senderFpHex, line.self), false);
        }
        if (line.self) c.append(seg(" (you)", MUTED, false));
        return c;
    }

    private static MutableComponent seg(String s, int color, boolean italic) {
        return Component.literal(s == null ? "" : s).setStyle(Style.EMPTY.withColor(color).withItalic(italic));
    }

    private static Btn copy(String hover) { return new Btn("Copy", "copy", hover, BTN_COPY); }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)).strip() + "…";
    }

    private static String displayAddr(String ip) {
        return MmBlobs.displayAddr(ip);
    }

    private record Btn(String label, String action, String hover, int color) {}
}
