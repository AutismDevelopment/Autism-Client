package autismclient.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismImageCodecTest {

    private static byte[] encode(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), "test image must encode as " + format);
        return out.toByteArray();
    }

    private static BufferedImage sample() {
        BufferedImage image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (x * 10 << 16) | (y * 15 << 8) | 0x40);
            }
        }
        return image;
    }

    @Test
    void magicSniffing() throws Exception {
        byte[] png = encode(sample(), "png");
        byte[] jpeg = encode(sample(), "jpeg");
        assertTrue(AutismImageCodec.isPng(png));
        assertFalse(AutismImageCodec.isJpeg(png));
        assertTrue(AutismImageCodec.isJpeg(jpeg));
        assertFalse(AutismImageCodec.isPng(jpeg));
        assertFalse(AutismImageCodec.isPng(null));
        assertFalse(AutismImageCodec.isJpeg(null));
        assertFalse(AutismImageCodec.isPng(new byte[0]));
        assertFalse(AutismImageCodec.isJpeg(new byte[]{(byte) 0xFF}));
    }

    @Test
    void pngPassesThroughUntouched() throws Exception {
        byte[] png = encode(sample(), "png");
        assertArrayEquals(png, AutismImageCodec.ensurePng(png));
    }

    @Test
    void jpegIsReencodedAsPng() throws Exception {
        byte[] jpeg = encode(sample(), "jpeg");
        byte[] converted = AutismImageCodec.ensurePng(jpeg);
        assertNotNull(converted, "a valid JPEG must convert, not strand the artwork");
        assertTrue(AutismImageCodec.isPng(converted), "converted bytes must be a real PNG");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(converted));
        assertNotNull(decoded);
        assertEquals(24, decoded.getWidth());
        assertEquals(16, decoded.getHeight());
    }

    @Test
    void garbageAndEmptyReturnNull() {
        assertNull(AutismImageCodec.ensurePng(null));
        assertNull(AutismImageCodec.ensurePng(new byte[0]));
        assertNull(AutismImageCodec.ensurePng("not an image at all".getBytes()));

        assertNull(AutismImageCodec.ensurePng(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}));
    }

    @Test
    void mipChainHalvesToOneByOneAndPreservesFlatColor() {

        com.mojang.blaze3d.platform.NativeImage base = new com.mojang.blaze3d.platform.NativeImage(4, 4, true);
        int color = 0xFF804020;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                base.setPixel(x, y, color);
            }
        }
        com.mojang.blaze3d.platform.NativeImage[] chain = AutismImageCodec.mipChain(base);
        try {
            assertEquals(3, chain.length, "4x4 -> 4x4, 2x2, 1x1");
            assertEquals(4, chain[0].getWidth());
            assertEquals(2, chain[1].getWidth());
            assertEquals(1, chain[2].getWidth());
            assertEquals(color, chain[2].getPixel(0, 0), "a flat color survives averaging exactly");
        } finally {
            for (com.mojang.blaze3d.platform.NativeImage level : chain) level.close();
        }
    }

    @Test
    void mipChainCapsTheTopLevelAt512() {

        com.mojang.blaze3d.platform.NativeImage base = new com.mojang.blaze3d.platform.NativeImage(600, 600, true);
        base.setPixel(0, 0, 0xFF101010);
        com.mojang.blaze3d.platform.NativeImage[] chain = AutismImageCodec.mipChain(base);
        try {
            assertEquals(300, chain[0].getWidth(), "anything above 512 is capped away");
            assertEquals(1, chain[chain.length - 1].getWidth());
            assertEquals(1, chain[chain.length - 1].getHeight());
        } finally {
            for (com.mojang.blaze3d.platform.NativeImage level : chain) level.close();
        }
    }

    @Test
    void boxAverageMixesChannelsCorrectly() {

        com.mojang.blaze3d.platform.NativeImage base = new com.mojang.blaze3d.platform.NativeImage(2, 2, true);
        base.setPixel(0, 0, 0xFF000000);
        base.setPixel(1, 0, 0xFF808080);
        base.setPixel(0, 1, 0xFF404040);
        base.setPixel(1, 1, 0xFFC0C0C0);
        assertEquals(0xFF000000, base.getPixel(0, 0));
        assertEquals(0xFF808080, base.getPixel(1, 0));
        assertEquals(0xFF404040, base.getPixel(0, 1));
        assertEquals(0xFFC0C0C0, base.getPixel(1, 1));
        com.mojang.blaze3d.platform.NativeImage half = AutismImageCodec.halve(base);
        try {
            assertEquals(1, half.getWidth());
            assertEquals(0xFF606060, half.getPixel(0, 0), "the 2x2 box average is per-channel exact");
        } finally {
            half.close();
            base.close();
        }
    }
}
