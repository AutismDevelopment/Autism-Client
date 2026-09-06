package autismclient.util;

public final class AutismImageCodec {
    private AutismImageCodec() {
    }

    public static boolean isPng(byte[] data) {
        return data != null && data.length > 3
            && (data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50 && (data[2] & 0xFF) == 0x4E;
    }

    public static boolean isJpeg(byte[] data) {
        return data != null && data.length > 2
            && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
    }

    public static byte[] ensurePng(byte[] data) {
        if (isPng(data)) return data;
        if (!isJpeg(data)) return null;
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (image == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(data.length);
            if (!javax.imageio.ImageIO.write(image, "png", out)) return null;
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    public static com.mojang.blaze3d.platform.NativeImage decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        if (isPng(data)) {
            try {
                return com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(data));
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (image == null) return null;
            com.mojang.blaze3d.platform.NativeImage out =
                new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), true);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    out.setPixel(x, y, image.getRGB(x, y));
                }
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static com.mojang.blaze3d.platform.NativeImage[] mipChain(com.mojang.blaze3d.platform.NativeImage base) {
        java.util.List<com.mojang.blaze3d.platform.NativeImage> levels = new java.util.ArrayList<>();
        com.mojang.blaze3d.platform.NativeImage current = base;
        while (Math.max(current.getWidth(), current.getHeight()) > 512) {
            current = halve(current);
            levels.add(current);
        }
        if (levels.isEmpty()) levels.add(base);
        else base.close();
        while (current.getWidth() > 1 || current.getHeight() > 1) {
            current = halve(current);
            levels.add(current);
        }
        return levels.toArray(new com.mojang.blaze3d.platform.NativeImage[0]);
    }

    public static com.mojang.blaze3d.platform.NativeImage halve(com.mojang.blaze3d.platform.NativeImage src) {
        int w = Math.max(1, src.getWidth() / 2);
        int h = Math.max(1, src.getHeight() / 2);
        com.mojang.blaze3d.platform.NativeImage out = new com.mojang.blaze3d.platform.NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setPixel(x, y, boxAverage(src, x * 2, y * 2));
            }
        }
        return out;
    }

    private static int boxAverage(com.mojang.blaze3d.platform.NativeImage src, int x, int y) {
        int a = 0, r = 0, g = 0, b = 0, n = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int sx = x + dx;
                int sy = y + dy;
                if (sx >= src.getWidth() || sy >= src.getHeight()) continue;
                int pixel = src.getPixel(sx, sy);
                a += (pixel >>> 24) & 0xFF;
                r += (pixel >>> 16) & 0xFF;
                g += (pixel >>> 8) & 0xFF;
                b += pixel & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0;
        return ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }
}
