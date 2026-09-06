package autismclient.util;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AutismBookFileReaderTest {
    @Test
    void decodesBomlessUtf16LittleEndianWithoutAlternatingMissingCharacters() {
        String source = "Linux check: alphabet 123\r\nSecond line";

        String decoded = AutismBookFileReader.decode(source.getBytes(StandardCharsets.UTF_16LE));

        assertEquals("Linux check: alphabet 123\nSecond line", decoded);
        assertFalse(decoded.contains("\u0000"));
    }

    @Test
    void honorsUtf16AndUtf8ByteOrderMarks() {
        String source = "Žluťoučký 😀\rline";
        byte[] utf16Body = source.getBytes(StandardCharsets.UTF_16LE);
        byte[] utf16 = new byte[utf16Body.length + 2];
        utf16[0] = (byte) 0xFF;
        utf16[1] = (byte) 0xFE;
        System.arraycopy(utf16Body, 0, utf16, 2, utf16Body.length);

        byte[] utf8Body = source.getBytes(StandardCharsets.UTF_8);
        byte[] utf8 = new byte[utf8Body.length + 3];
        utf8[0] = (byte) 0xEF;
        utf8[1] = (byte) 0xBB;
        utf8[2] = (byte) 0xBF;
        System.arraycopy(utf8Body, 0, utf8, 3, utf8Body.length);

        String expected = "Žluťoučký 😀\nline";
        assertEquals(expected, AutismBookFileReader.decode(utf16));
        assertEquals(expected, AutismBookFileReader.decode(utf8));
    }

    @Test
    void fallsBackDeterministicallyForWindowsTextFilesOnLinux() {
        byte[] windows1252 = {(byte) 0x93, 'q', 'u', 'o', 't', 'e', (byte) 0x94};

        assertEquals("“quote”", AutismBookFileReader.decode(windows1252));
    }
}
