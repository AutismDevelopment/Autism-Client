package autismclient.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutismBookFileReader {
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Charset UTF_32_LE = Charset.forName("UTF-32LE");
    private static final Charset UTF_32_BE = Charset.forName("UTF-32BE");

    private AutismBookFileReader() {
    }

    public static String read(Path path) throws IOException {
        if (path == null) return "";
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("file exceeds the 4 MiB BookBot limit");
        }
        return decode(bytes);
    }

    static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        Encoding encoding = detectEncoding(bytes);
        String decoded;
        if (encoding != null) {
            decoded = new String(bytes, encoding.offset(), bytes.length - encoding.offset(), encoding.charset());
        } else {
            decoded = decodeUtf8OrLegacy(bytes);
        }
        return normalize(decoded);
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        int start = text.charAt(0) == '\uFEFF' ? 1 : 0;
        String value = start == 0 ? text : text.substring(start);

        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "");
    }

    private static String decodeUtf8OrLegacy(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {

            return new String(bytes, WINDOWS_1252);
        }
    }

    private static Encoding detectEncoding(byte[] bytes) {
        int length = bytes.length;
        if (length >= 4 && u(bytes[0]) == 0x00 && u(bytes[1]) == 0x00
            && u(bytes[2]) == 0xFE && u(bytes[3]) == 0xFF) {
            return new Encoding(UTF_32_BE, 4);
        }
        if (length >= 4 && u(bytes[0]) == 0xFF && u(bytes[1]) == 0xFE
            && u(bytes[2]) == 0x00 && u(bytes[3]) == 0x00) {
            return new Encoding(UTF_32_LE, 4);
        }
        if (length >= 3 && u(bytes[0]) == 0xEF && u(bytes[1]) == 0xBB && u(bytes[2]) == 0xBF) {
            return new Encoding(StandardCharsets.UTF_8, 3);
        }
        if (length >= 2 && u(bytes[0]) == 0xFE && u(bytes[1]) == 0xFF) {
            return new Encoding(StandardCharsets.UTF_16BE, 2);
        }
        if (length >= 2 && u(bytes[0]) == 0xFF && u(bytes[1]) == 0xFE) {
            return new Encoding(StandardCharsets.UTF_16LE, 2);
        }

        int sample = Math.min(length & ~1, 8192);
        int pairs = sample / 2;
        if (pairs >= 4) {
            int evenNuls = 0;
            int oddNuls = 0;
            for (int i = 0; i < sample; i += 2) {
                if (bytes[i] == 0) evenNuls++;
                if (bytes[i + 1] == 0) oddNuls++;
            }
            int threshold = Math.max(3, pairs / 3);
            if (oddNuls >= threshold && oddNuls >= evenNuls * 3) {
                return new Encoding(StandardCharsets.UTF_16LE, 0);
            }
            if (evenNuls >= threshold && evenNuls >= oddNuls * 3) {
                return new Encoding(StandardCharsets.UTF_16BE, 0);
            }
        }
        return null;
    }

    private static int u(byte value) {
        return value & 0xFF;
    }

    private record Encoding(Charset charset, int offset) {
    }
}
