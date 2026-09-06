package autismclient.util.mm;

import autismclient.util.mm.msg.MmMessages;

public final class MmChatLine {
    public enum Kind { TEXT, SYSTEM, MACRO_CARD, PACKET_CARD, COMMAND_CARD, BLOB_CARD }

    public final Kind kind;
    public final String senderFpHex;
    public final String senderName;
    public final String text;
    public final long timeMs;
    public final boolean self;
    public final boolean system;

    public final MmMessages.MacroOffer macro;
    public final MmMessages.CommandOffer command;
    public final MmMessages.PacketOffer packet;
    public final MmMessages.BlobOffer blob;

    public MmChatLine(String senderFpHex, String senderName, String text, boolean self, boolean system) {
        this(system ? Kind.SYSTEM : Kind.TEXT, senderFpHex, senderName, text, self, system, null, null, null, null);
    }

    private MmChatLine(Kind kind, String senderFpHex, String senderName, String text, boolean self, boolean system,
                       MmMessages.MacroOffer macro, MmMessages.CommandOffer command, MmMessages.PacketOffer packet,
                       MmMessages.BlobOffer blob) {
        this.kind = kind;
        this.senderFpHex = senderFpHex == null ? "" : senderFpHex;
        this.senderName = senderName == null ? "" : senderName;
        this.text = text == null ? "" : text;
        this.self = self;
        this.system = system;
        this.macro = macro;
        this.command = command;
        this.packet = packet;
        this.blob = blob;
        this.timeMs = System.currentTimeMillis();
    }

    public static MmChatLine system(String text) { return new MmChatLine("", "", text, false, true); }

    public static MmChatLine macroCard(String fpHex, String name, boolean self, MmMessages.MacroOffer m) {
        return new MmChatLine(Kind.MACRO_CARD, fpHex, name, "", self, false, m, null, null, null);
    }
    public static MmChatLine packetCard(String fpHex, String name, boolean self, MmMessages.PacketOffer p) {
        return new MmChatLine(Kind.PACKET_CARD, fpHex, name, "", self, false, null, null, p, null);
    }
    public static MmChatLine commandCard(String fpHex, String name, boolean self, MmMessages.CommandOffer c) {
        return new MmChatLine(Kind.COMMAND_CARD, fpHex, name, "", self, false, null, c, null, null);
    }
    public static MmChatLine blobCard(String fpHex, String name, boolean self, MmMessages.BlobOffer b) {
        return new MmChatLine(Kind.BLOB_CARD, fpHex, name, "", self, false, null, null, null, b);
    }

    public boolean isCard() {
        return kind == Kind.MACRO_CARD || kind == Kind.PACKET_CARD || kind == Kind.COMMAND_CARD || kind == Kind.BLOB_CARD;
    }

    public String cardHeadline() {
        switch (kind) {
            case MACRO_CARD:
                if (macro.actionCount == 1 && !macro.singleActionLabel.isBlank()) return macro.singleActionLabel;
                return macro.actionCount + (macro.actionCount == 1 ? " action" : " actions");
            case COMMAND_CARD:
                return command.body;
            case PACKET_CARD:
                return packet.friendlyName;
            case BLOB_CARD:
                return blob.friendlyName;
            default:
                return text;
        }
    }
}
