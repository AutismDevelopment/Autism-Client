package autismclient.util.mm.msg;

import autismclient.util.mm.MmMessageType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class MmMessages {
    private MmMessages() {}

    public abstract static class Msg {
        public abstract MmMessageType type();
        protected abstract void write(DataOutputStream out) throws IOException;
        protected abstract void read(DataInputStream in) throws IOException;

        public final byte[] encode() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                write(new DataOutputStream(bos));
                return bos.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public static <T extends Msg> T decodeInto(T target, byte[] payload) {
            try {
                target.read(new DataInputStream(new ByteArrayInputStream(payload)));
                return target;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public static final class Presence extends Msg {
        public String nickname = "";
        public boolean shareServer;
        public String serverName = "";
        public String serverIp = "";
        public boolean shareLocation;
        public int dupeStatus = -1;
        public String identityToken = "";

        public Presence() {}
        public Presence(String nickname) {
            this.nickname = nickname != null ? nickname : "";
        }

        @Override public MmMessageType type() { return MmMessageType.PRESENCE; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeUTF(nickname);
            out.writeBoolean(shareServer);
            out.writeUTF(serverName);
            out.writeUTF(serverIp);
            out.writeBoolean(shareLocation);
            out.writeInt(dupeStatus);
            out.writeUTF(identityToken);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            nickname = in.readUTF();
            shareServer = in.readBoolean();
            serverName = in.readUTF();
            serverIp = in.readUTF();
            shareLocation = in.readBoolean();

            if (in.available() > 0) dupeStatus = in.readInt();
            if (in.available() > 0) identityToken = in.readUTF();
        }
    }

    public static final class Chat extends Msg {
        public String text = "";
        public Chat() {}
        public Chat(String text) { this.text = text != null ? text : ""; }
        @Override public MmMessageType type() { return MmMessageType.CHAT; }
        @Override protected void write(DataOutputStream out) throws IOException { out.writeUTF(text); }
        @Override protected void read(DataInputStream in) throws IOException { text = in.readUTF(); }
    }

    public static final class MacroOffer extends Msg {
        public String macroName = "";
        public int actionCount;
        public String singleActionLabel = "";
        public String hash = "";
        public MacroOffer() {}
        @Override public MmMessageType type() { return MmMessageType.MACRO_OFFER; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeUTF(macroName);
            out.writeInt(actionCount);
            out.writeUTF(singleActionLabel);
            out.writeUTF(hash);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            macroName = in.readUTF();
            actionCount = in.readInt();
            singleActionLabel = in.readUTF();
            hash = in.readUTF();
        }
    }

    public static final class CommandOffer extends Msg {
        public static final byte KIND_VANILLA = 0;
        public static final byte KIND_AUTISM = 1;
        public static final byte KIND_CHAT = 2;
        public byte kind;
        public String body = "";
        public CommandOffer() {}
        public CommandOffer(byte kind, String body) { this.kind = kind; this.body = body != null ? body : ""; }
        @Override public MmMessageType type() { return MmMessageType.COMMAND_OFFER; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeByte(kind);
            out.writeUTF(body);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            kind = in.readByte();
            body = in.readUTF();
        }
    }

    public static final class PacketOffer extends Msg {
        public String friendlyName = "";
        public String direction = "";
        public String data = "";
        public PacketOffer() {}
        @Override public MmMessageType type() { return MmMessageType.PACKET_OFFER; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeUTF(friendlyName);
            out.writeUTF(direction);
            out.writeUTF(data);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            friendlyName = in.readUTF();
            direction = in.readUTF();
            data = in.readUTF();
        }
    }

    public static final class BlobOffer extends Msg {
        public String kind = "";
        public String friendlyName = "";
        public int count;
        public String data = "";
        public BlobOffer() {}
        public BlobOffer(String kind, String friendlyName, int count, String data) {
            this.kind = kind == null ? "" : kind;
            this.friendlyName = friendlyName == null ? "" : friendlyName;
            this.count = count;
            this.data = data == null ? "" : data;
        }
        @Override public MmMessageType type() { return MmMessageType.BLOB_OFFER; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeUTF(kind);
            out.writeUTF(friendlyName);
            out.writeInt(count);
            byte[] d = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(d.length);
            out.write(d);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            kind = in.readUTF();
            friendlyName = in.readUTF();
            count = in.readInt();
            int len = in.readInt();
            if (len < 0 || len > 4_000_000) throw new IOException("blob too large");
            byte[] d = new byte[len];
            in.readFully(d);
            data = new String(d, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class Location extends Msg {
        public String dimension = "";
        public double x, y, z;
        public Location() {}
        public Location(String dimension, double x, double y, double z) {
            this.dimension = dimension != null ? dimension : "";
            this.x = x; this.y = y; this.z = z;
        }
        @Override public MmMessageType type() { return MmMessageType.LOCATION; }
        @Override protected void write(DataOutputStream out) throws IOException {
            out.writeUTF(dimension);
            out.writeDouble(x); out.writeDouble(y); out.writeDouble(z);
        }
        @Override protected void read(DataInputStream in) throws IOException {
            dimension = in.readUTF();
            x = in.readDouble(); y = in.readDouble(); z = in.readDouble();
        }
    }

    public static final class Leave extends Msg {
        public Leave() {}
        @Override public MmMessageType type() { return MmMessageType.LEAVE; }
        @Override protected void write(DataOutputStream out) {}
        @Override protected void read(DataInputStream in) {}
    }

    public static final class Receipt extends Msg {
        public byte[] msgId = new byte[16];
        public Receipt() {}
        public Receipt(byte[] msgId) { if (msgId != null && msgId.length == 16) this.msgId = msgId.clone(); }
        @Override public MmMessageType type() { return MmMessageType.RECEIPT; }
        @Override protected void write(DataOutputStream out) throws IOException { out.write(msgId, 0, 16); }
        @Override protected void read(DataInputStream in) throws IOException { byte[] b = new byte[16]; in.readFully(b); msgId = b; }
    }

    public static final class Kick extends Msg {
        public byte[] bannedFp = new byte[8];
        public Kick() {}
        public Kick(byte[] bannedFp) { if (bannedFp != null && bannedFp.length == 8) this.bannedFp = bannedFp.clone(); }
        @Override public MmMessageType type() { return MmMessageType.KICK; }
        @Override protected void write(DataOutputStream out) throws IOException { out.write(bannedFp, 0, 8); }
        @Override protected void read(DataInputStream in) throws IOException { byte[] b = new byte[8]; in.readFully(b); bannedFp = b; }
    }

}
