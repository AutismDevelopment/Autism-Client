package autismclient.util.mm;

import autismclient.util.mm.crypto.MmCrypto;
import autismclient.util.mm.crypto.MmIdentity;
import autismclient.util.mm.crypto.MmSession;
import autismclient.util.mm.crypto.RoomKey;

import java.nio.ByteBuffer;
import java.security.PublicKey;

public final class MmEnvelope {

    public static final byte VERSION = 2;
    private static final int HEADER_LEN = 1 + 1 + 8 + 8 + 16 + 8 + 8 + 12;

    public final int typeId;
    public final byte[] senderFp;
    public final byte[] lobbyTag;
    public final byte[] msgId;
    public final long timestamp;
    public final long seq;
    public final byte[] senderSpki;
    public final byte[] payload;

    private MmEnvelope(int typeId, byte[] senderFp, byte[] lobbyTag, byte[] msgId, long timestamp, long seq,
                       byte[] senderSpki, byte[] payload) {
        this.typeId = typeId;
        this.senderFp = senderFp;
        this.lobbyTag = lobbyTag;
        this.msgId = msgId;
        this.timestamp = timestamp;
        this.seq = seq;
        this.senderSpki = senderSpki;
        this.payload = payload;
    }

    public MmMessageType type() { return MmMessageType.byId(typeId); }
    public String senderFpHex() { return MmCrypto.hex(senderFp); }

    public static String peekSenderFpHex(byte[] frame) {
        if (frame == null || frame.length < HEADER_LEN) return null;
        byte[] fp = new byte[8];
        System.arraycopy(frame, 2, fp, 0, 8);
        return MmCrypto.hex(fp);
    }

    public static String peekMsgIdHex(byte[] frame) {
        if (frame == null || frame.length < HEADER_LEN) return null;
        byte[] id = new byte[16];
        System.arraycopy(frame, 18, id, 0, 16);
        return MmCrypto.hex(id);
    }

    public static int peekTypeId(byte[] frame) {
        if (frame == null || frame.length < HEADER_LEN) return -1;
        return frame[1] & 0xFF;
    }

    public static byte[] seal(MmIdentity id, MmSession session, RoomKey room, byte[] key,
                              MmMessageType type, byte[] payload) {
        byte[] senderFp = id.senderFp();
        byte[] lobbyTag = room.lobbyTag();
        byte[] msgId = MmCrypto.randomBytes(16);

        long timestamp = ServerClock.nowMs();
        long seq = session.nextSeq();
        byte[] nonce = session.nextNonce();

        byte[] header = ByteBuffer.allocate(HEADER_LEN)
            .put(VERSION)
            .put((byte) type.id)
            .put(senderFp, 0, 8)
            .put(lobbyTag, 0, 8)
            .put(msgId, 0, 16)
            .putLong(timestamp)
            .putLong(seq)
            .put(nonce, 0, 12)
            .array();

        byte[] senderSpki = id.publicKeySpki();
        byte[] signed = concat(header, senderSpki, payload);
        byte[] sig = id.sign(signed);

        byte[] inner = ByteBuffer.allocate(2 + senderSpki.length + 2 + sig.length + payload.length)
            .putShort((short) senderSpki.length).put(senderSpki)
            .putShort((short) sig.length).put(sig)
            .put(payload)
            .array();

        byte[] ciphertext = MmCrypto.aesGcmSeal(key, nonce, inner, header);
        byte[] frame = new byte[header.length + ciphertext.length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(ciphertext, 0, frame, header.length, ciphertext.length);
        return frame;
    }

    public static MmEnvelope open(byte[] frame, byte[] key) {
        try {
            if (frame.length <= HEADER_LEN) return null;
            byte[] header = new byte[HEADER_LEN];
            System.arraycopy(frame, 0, header, 0, HEADER_LEN);

            ByteBuffer h = ByteBuffer.wrap(header);
            byte version = h.get();
            if (version != VERSION) return null;
            int typeId = h.get() & 0xFF;
            byte[] senderFp = new byte[8]; h.get(senderFp);
            byte[] lobbyTag = new byte[8]; h.get(lobbyTag);
            byte[] msgId = new byte[16]; h.get(msgId);
            long timestamp = h.getLong();
            long seq = h.getLong();
            byte[] nonce = new byte[12]; h.get(nonce);

            byte[] ciphertext = new byte[frame.length - HEADER_LEN];
            System.arraycopy(frame, HEADER_LEN, ciphertext, 0, ciphertext.length);

            byte[] inner = MmCrypto.aesGcmOpen(key, nonce, ciphertext, header);
            if (inner == null) return null;

            ByteBuffer b = ByteBuffer.wrap(inner);
            int spkiLen = b.getShort() & 0xFFFF;
            if (spkiLen <= 0 || spkiLen > b.remaining()) return null;
            byte[] senderSpki = new byte[spkiLen]; b.get(senderSpki);
            int sigLen = b.getShort() & 0xFFFF;
            if (sigLen <= 0 || sigLen > b.remaining()) return null;
            byte[] sig = new byte[sigLen]; b.get(sig);
            byte[] payload = new byte[b.remaining()]; b.get(payload);

            byte[] expectedFp = MmCrypto.prefix(MmCrypto.sha256(senderSpki), 8);
            if (!MmCrypto.constantTimeEquals(expectedFp, senderFp)) return null;

            PublicKey pub = MmCrypto.ed25519PublicFromSpki(senderSpki);
            byte[] signed = concat(header, senderSpki, payload);
            if (!MmCrypto.ed25519Verify(pub, signed, sig)) return null;

            return new MmEnvelope(typeId, senderFp, lobbyTag, msgId, timestamp, seq, senderSpki, payload);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }
}
