package autismclient.util.mm.crypto;

import autismclient.AutismClientAddon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class AtRestSeal {
    private static final byte SCHEME_PLAIN = 0;
    private static final byte SCHEME_DPAPI = 1;
    private static final byte SCHEME_AESKEY = 2;

    private AtRestSeal() {}

    private static File keyFile() {
        try {
            if (AutismClientAddon.FOLDER != null) return new File(AutismClientAddon.FOLDER, "autism-seal.key");
        } catch (Throwable ignored) {  }
        File fallback = new File(System.getProperty("java.io.tmpdir", "."), "autismclient-seal");
        return new File(fallback, "autism-seal.key");
    }

    private static File keyTmp() {
        File key = keyFile();
        return new File(key.getParentFile(), key.getName() + ".tmp");
    }

    public static byte[] seal(byte[] plain) {
        if (plain == null) plain = new byte[0];
        byte[] dpapi = WinDpapi.protect(plain);
        if (dpapi != null) return prepend(SCHEME_DPAPI, dpapi);
        byte[] aes = aesSeal(plain);
        if (aes != null) return prepend(SCHEME_AESKEY, aes);
        return prepend(SCHEME_PLAIN, plain);
    }

    public static byte[] sealStrict(byte[] plain) {
        byte[] sealed = seal(plain);
        return sealed.length > 0 && sealed[0] != SCHEME_PLAIN ? sealed : null;
    }

    public static byte[] unseal(byte[] stored) {
        if (stored == null || stored.length < 1) return null;
        byte[] payload = Arrays.copyOfRange(stored, 1, stored.length);
        return switch (stored[0]) {
            case SCHEME_DPAPI -> WinDpapi.unprotect(payload);
            case SCHEME_AESKEY -> aesOpen(payload);
            case SCHEME_PLAIN -> payload;
            default -> null;
        };
    }

    public static byte[] unsealStrict(byte[] stored) {
        if (stored == null || stored.length < 1 || stored[0] == SCHEME_PLAIN) return null;
        return unseal(stored);
    }

    private static byte[] prepend(byte scheme, byte[] body) {
        byte[] out = new byte[body.length + 1];
        out[0] = scheme;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    private static byte[] aesSeal(byte[] plain) {
        try {
            byte[] key = getOrCreateAesKey();
            if (key == null) return null;
            byte[] nonce = MmCrypto.randomBytes(12);
            byte[] ct = MmCrypto.aesGcmSeal(key, nonce, plain, null);
            byte[] out = new byte[12 + ct.length];
            System.arraycopy(nonce, 0, out, 0, 12);
            System.arraycopy(ct, 0, out, 12, ct.length);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] aesOpen(byte[] stored) {
        try {
            if (stored == null || stored.length < 12 + 16) return null;
            byte[] key = readAesKey();
            if (key == null) return null;
            byte[] nonce = Arrays.copyOfRange(stored, 0, 12);
            byte[] ct = Arrays.copyOfRange(stored, 12, stored.length);
            return MmCrypto.aesGcmOpen(key, nonce, ct, null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] getOrCreateAesKey() {
        byte[] existing = readAesKey();
        if (existing != null) return existing;
        try {
            File keyFile = keyFile();
            File keyTmp = keyTmp();
            byte[] key = MmCrypto.randomBytes(32);
            File dir = keyFile.getParentFile();
            if (dir != null) dir.mkdirs();
            Files.write(keyTmp.toPath(), key);
            if (!restrictToOwner(keyTmp)) {
                Files.deleteIfExists(keyTmp.toPath());
                return null;
            }
            try {
                Files.move(keyTmp.toPath(), keyFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                Files.move(keyTmp.toPath(), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (!restrictToOwner(keyFile)) return null;
            return key;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAesKey() {
        try {
            File keyFile = keyFile();
            if (!keyFile.exists()) return null;
            if (!restrictToOwner(keyFile)) return null;
            byte[] k = Files.readAllBytes(keyFile.toPath());
            return (k != null && k.length == 32) ? k : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean restrictToOwner(File f) {
        try {
            java.nio.file.attribute.PosixFileAttributeView posix = Files.getFileAttributeView(
                f.toPath(), java.nio.file.attribute.PosixFileAttributeView.class);
            if (posix != null) {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> ownerOnly = java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(f.toPath(), ownerOnly);
                return Files.getPosixFilePermissions(f.toPath()).equals(ownerOnly);
            }
            f.setReadable(false, false);
            f.setWritable(false, false);
            return f.setReadable(true, true) && f.setWritable(true, true);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
