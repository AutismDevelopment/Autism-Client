package autismclient.util.mm.crypto;

import java.util.concurrent.atomic.AtomicLong;

public final class MmSession {
    private final AtomicLong seq = new AtomicLong(0);

    public MmSession() {}

    public long nextSeq() { return seq.incrementAndGet(); }

    public byte[] nextNonce() {
        return MmCrypto.randomBytes(12);
    }
}
