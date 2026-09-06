package autismclient.util.mm.crypto;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

final class WinDpapi {
    private WinDpapi() {}

    private static final MemoryLayout BLOB = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cbData"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.ADDRESS.withName("pbData"));
    private static final long PB_OFFSET = 8;
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;

    private static final MethodHandle PROTECT;
    private static final MethodHandle UNPROTECT;
    private static final MethodHandle LOCAL_FREE;
    private static final boolean AVAILABLE;

    static {
        MethodHandle protect = null, unprotect = null, localFree = null;
        boolean ok = false;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                Linker linker = Linker.nativeLinker();
                Arena global = Arena.global();
                SymbolLookup crypt32 = SymbolLookup.libraryLookup("Crypt32.dll", global);
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("Kernel32.dll", global);
                FunctionDescriptor blobFn = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
                protect = linker.downcallHandle(crypt32.find("CryptProtectData").orElseThrow(), blobFn);
                unprotect = linker.downcallHandle(crypt32.find("CryptUnprotectData").orElseThrow(), blobFn);
                localFree = linker.downcallHandle(kernel32.find("LocalFree").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
        }
        PROTECT = protect;
        UNPROTECT = unprotect;
        LOCAL_FREE = localFree;
        AVAILABLE = ok;
    }

    static boolean available() { return AVAILABLE; }

    static byte[] protect(byte[] plain) {
        return call(PROTECT, plain);
    }

    static byte[] unprotect(byte[] cipher) {
        return call(UNPROTECT, cipher);
    }

    private static byte[] call(MethodHandle fn, byte[] in) {
        if (!AVAILABLE || fn == null || in == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inData = arena.allocate(Math.max(1, in.length));
            MemorySegment.copy(MemorySegment.ofArray(in), 0L, inData, 0L, in.length);
            MemorySegment inBlob = arena.allocate(BLOB);
            inBlob.set(ValueLayout.JAVA_INT, 0, in.length);
            inBlob.set(ValueLayout.ADDRESS, PB_OFFSET, inData);

            MemorySegment outBlob = arena.allocate(BLOB);
            outBlob.set(ValueLayout.JAVA_INT, 0, 0);
            outBlob.set(ValueLayout.ADDRESS, PB_OFFSET, MemorySegment.NULL);

            int ok = (int) fn.invoke(inBlob, MemorySegment.NULL, MemorySegment.NULL,
                MemorySegment.NULL, MemorySegment.NULL, CRYPTPROTECT_UI_FORBIDDEN, outBlob);
            if (ok == 0) return null;

            int cb = outBlob.get(ValueLayout.JAVA_INT, 0);
            MemorySegment pb = outBlob.get(ValueLayout.ADDRESS, PB_OFFSET);
            if (cb < 0 || pb.address() == 0L) return null;
            byte[] out = new byte[cb];
            MemorySegment.copy(pb.reinterpret(cb), 0L, MemorySegment.ofArray(out), 0L, cb);
            LOCAL_FREE.invoke(pb);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
