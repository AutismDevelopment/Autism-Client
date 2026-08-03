package autismclient.util;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AutismWindowBranding {
    public static final String WINDOW_TITLE = "AUTISM Client";
    private static final String ICON_ROOT = "assets/autismclient/icons/window/";
    private static final int[] ICON_SIZES = {16, 32, 48, 128, 256};

    private static final int MAX_RETRY_TICKS = 60;
    private static boolean applied = false;

    private static int pendingTicks;

    private AutismWindowBranding() {
    }

    public static void apply(Minecraft client) {
        if (applied || client == null || client.getWindow() == null) return;
        applied = true;
        refresh(client);
    }

    public static void refresh(Minecraft client) {
        pendingTicks = MAX_RETRY_TICKS;
        applyPending(client);
    }

    public static void tick(Minecraft client) {
        if (pendingTicks <= 0) return;
        applyPending(client);
    }

    private static void applyPending(Minecraft client) {
        if (pendingTicks <= 0) return;
        pendingTicks--;
        if (client == null || client.getWindow() == null) return;

        boolean hidden = autismclient.modules.PackHideState.isActive();
        boolean ok;
        if (hidden) {

            client.updateTitle();
            ok = applyVanillaIcon(client);
        } else {
            client.getWindow().setTitle(WINDOW_TITLE);
            ok = applyIcon(client.getWindow().handle());
        }
        if (ok) pendingTicks = 0;
    }

    private static boolean applyVanillaIcon(Minecraft client) {
        try {
            IconSet iconSet = SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT;
            client.getWindow().setIcon(client.getVanillaPackResources(), iconSet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean applyIcon(long windowHandle) {
        if (windowHandle == 0L) return false;

        if (MacosUtil.IS_MACOS) {
            try {
                MacosUtil.loadIcon(() -> openIconStream("mac_icon.png"));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        List<ByteBuffer> allocatedBuffers = new ArrayList<>(ICON_SIZES.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer icons = GLFWImage.malloc(ICON_SIZES.length, stack);

            for (int i = 0; i < ICON_SIZES.length; i++) {
                int size = ICON_SIZES[i];
                try (InputStream stream = openIconStream("icon_" + size + "x" + size + ".png");
                     NativeImage image = NativeImage.read(stream)) {
                    ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                    allocatedBuffers.add(pixels);
                    pixels.asIntBuffer().put(image.getPixelsABGR());
                    icons.position(i);
                    icons.width(image.getWidth());
                    icons.height(image.getHeight());
                    icons.pixels(pixels);
                }
            }

            GLFW.glfwSetWindowIcon(windowHandle, icons.position(0));
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            allocatedBuffers.forEach(MemoryUtil::memFree);
        }
    }

    private static InputStream openIconStream(String fileName) throws IOException {
        InputStream stream = AutismWindowBranding.class.getClassLoader().getResourceAsStream(ICON_ROOT + fileName);
        if (stream == null) {
            throw new IOException("Missing Autism window icon resource: " + fileName);
        }
        return stream;
    }
}
