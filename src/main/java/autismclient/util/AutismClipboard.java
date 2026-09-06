package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AutismClipboard {
    private static final long TOOL_TIMEOUT_MS = 3000L;

    private static final Map<String, Boolean> TOOL_AVAILABILITY = new ConcurrentHashMap<>();
    private static volatile boolean loggedFallback;

    private static volatile String shadow;

    private static volatile boolean awtBroken;

    private AutismClipboard() {
    }

    public static String get() {
        for (String[] command : getCommands()) {
            if (!toolAvailable(command[0])) continue;
            String out = runForOutput(command);
            if (out != null && !out.isEmpty()) return out;
        }
        String awt = awtGet();
        if (awt != null && !awt.isEmpty()) return awt;
        String glfw = glfwGet();
        if (!glfw.isEmpty()) return glfw;
        String local = shadow;
        if (local != null) {
            logFallbackOnce("read");
            return local;
        }
        return "";
    }

    public static void set(String text) {
        if (text == null) text = "";
        shadow = text;
        for (String[] command : setCommands()) {
            if (!toolAvailable(command[0])) continue;
            if (runWithInput(command, text)) return;
        }
        if (awtSet(text)) return;
        logFallbackOnce("write");
        glfwSet(text);
    }

    private static String[][] getCommands() {
        return isWaylandSession()
            ? new String[][]{{"wl-paste"}, {"xclip", "-selection", "clipboard", "-o"}, {"xsel", "--clipboard", "--output"}}
            : new String[][]{{"xclip", "-selection", "clipboard", "-o"}, {"xsel", "--clipboard", "--output"}, {"wl-paste"}};
    }

    private static String[][] setCommands() {
        return isWaylandSession()
            ? new String[][]{{"wl-copy"}, {"xclip", "-selection", "clipboard", "-i"}, {"xsel", "--clipboard", "--input"}}
            : new String[][]{{"xclip", "-selection", "clipboard", "-i"}, {"xsel", "--clipboard", "--input"}, {"wl-copy"}};
    }

    private static boolean isWaylandSession() {
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        if (sessionType != null && sessionType.toLowerCase(Locale.ROOT).contains("wayland")) return true;
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return waylandDisplay != null && !waylandDisplay.isBlank();
    }

    private static boolean toolAvailable(String tool) {
        return TOOL_AVAILABILITY.computeIfAbsent(tool, AutismClipboard::probeTool);
    }

    private static boolean probeTool(String tool) {
        Process process = null;
        try {

            process = new ProcessBuilder("sh", "-c", "command -v " + tool)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return false;
        }
    }

    private static boolean runWithInput(String[] command, String text) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(text.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return false;
        }
    }

    private static String runForOutput(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            final Process child = process;
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try {
                    return child.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            byte[] out = reader.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) return null;
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return null;
        }
    }

    private static String awtGet() {
        if (awtUnavailable()) return null;
        FutureTask<String> task = new FutureTask<>(() -> {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String s ? s : null;
        });
        runAwtTask(task);
        try {
            return task.get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            awtBroken = true;
            task.cancel(true);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean awtSet(String text) {
        if (awtUnavailable()) return false;
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        });
        runAwtTask(task);
        try {
            return Boolean.TRUE.equals(task.get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            awtBroken = true;
            task.cancel(true);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean awtUnavailable() {
        if (awtBroken) return true;
        try {
            if (GraphicsEnvironment.isHeadless()) {
                awtBroken = true;
            }
        } catch (Throwable t) {
            awtBroken = true;
        }
        return awtBroken;
    }

    private static void runAwtTask(FutureTask<?> task) {
        Thread thread = new Thread(task, "autism-clipboard-awt");
        thread.setDaemon(true);
        thread.start();
    }

    private static String glfwGet() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return "";
        long window = mc.getWindow().handle();
        if (window == 0L) return "";
        if (mc.isSameThread()) return glfwGetOnMain(window);
        CompletableFuture<String> future = new CompletableFuture<>();
        mc.execute(() -> future.complete(glfwGetOnMain(window)));
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private static void glfwSet(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().handle();
        if (window == 0L) return;
        if (mc.isSameThread()) {
            glfwSetOnMain(window, text);
        } else {
            mc.execute(() -> glfwSetOnMain(window, text));
        }
    }

    private static String glfwGetOnMain(long window) {
        try {
            String value = GLFW.glfwGetClipboardString(window);
            return value != null ? value : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static void glfwSetOnMain(long window, String text) {
        try {
            GLFW.glfwSetClipboardString(window, text);
        } catch (Throwable ignored) {

        }
    }

    private static void logFallbackOnce(String operation) {
        if (loggedFallback) return;
        loggedFallback = true;
        AutismClientAddon.LOG.info(
            "[Autism] Clipboard {}: no native tool (wl-copy/xclip/xsel) or AWT path worked; using GLFW/in-client fallback", operation);
    }
}
