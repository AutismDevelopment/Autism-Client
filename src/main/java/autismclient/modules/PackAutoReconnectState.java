package autismclient.modules;

import autismclient.ducks.AutismDisconnectedScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class PackAutoReconnectState {
    private static final Minecraft MC = Minecraft.getInstance();
    private static ServerData lastServer;
    private static ServerAddress lastAddress;
    private static Screen countdownScreen;
    private static int ticksLeft;

    private PackAutoReconnectState() {
    }

    public static void remember(ServerData server) {
        if (server == null || server.ip == null || server.ip.isBlank()) return;
        remember(server, ServerAddress.parseString(server.ip));
    }

    public static void remember(ServerData server, ServerAddress address) {
        if (server == null) return;
        if ((server.ip == null || server.ip.isBlank()) && address == null) return;
        ServerData copy = new ServerData(server.name, server.ip, server.type());
        copy.copyFrom(server);
        lastServer = copy;
        lastAddress = address != null ? address : ServerAddress.parseString(server.ip);
        countdownScreen = null;
        ticksLeft = delayTicks();
    }

    public static boolean shouldShow() {
        Module module = ModuleRegistry.get("auto-reconnect");
        return module != null && module.isEnabled() && lastServer != null && lastAddress != null && MC.allowsMultiplayer();
    }

    public static boolean moduleEnabled() {
        Module module = ModuleRegistry.get("auto-reconnect");
        return module != null && module.isEnabled();
    }

    public static boolean canShowToggle() {
        return lastServer != null && lastAddress != null && MC.allowsMultiplayer();
    }

    public static void toggle() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return;
        module.setConfiguredEnabled(!module.isEnabled());
    }

    public static String toggleLabel() {
        if (!moduleEnabled()) return "Auto Reconnect: OFF";
        double seconds = isCounting() ? Math.max(0.0, ticksLeft / 20.0) : delaySeconds();
        return String.format(java.util.Locale.ROOT, "Auto Reconnect: ON  (%.1fs)", seconds);
    }

    private static double delaySeconds() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return 3.5;
        try {
            return Math.max(0.0, Double.parseDouble(module.value("delay")));
        } catch (NumberFormatException ignored) {
            return 3.5;
        }
    }

    public static void tick(Screen screen, Screen parent) {
        if (!shouldShow()) {
            countdownScreen = null;
            return;
        }
        if (countdownScreen != screen) {
            countdownScreen = screen;
            ticksLeft = delayTicks();
        }
        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }
        reconnect(parent);
    }

    public static boolean isCounting() {
        return shouldShow() && ticksLeft > 0;
    }

    public static void tickCurrentScreen() {
        Screen screen = MC.gui.screen();
        if (screen instanceof DisconnectedScreen) {

            Screen parent = screen instanceof AutismDisconnectedScreenAccess access ? access.autism$parent() : screen;
            tick(screen, parent);
        } else {
            countdownScreen = null;
        }
    }

    public static void reconnect(Screen parent) {
        if (!shouldShow()) return;
        Screen nextParent = parent == null ? MC.gui.screen() : parent;
        ConnectScreen.startConnecting(nextParent, MC, lastAddress, lastServer, false, null);
        countdownScreen = null;
        ticksLeft = delayTicks();
    }

    public static String statusText() {
        if (!shouldShow()) return "";
        double seconds = Math.max(0.0, ticksLeft / 20.0);
        return String.format(java.util.Locale.ROOT, "Auto reconnect in %.1fs", seconds);
    }

    private static int delayTicks() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return 70;
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(module.value("delay")) * 20.0));
        } catch (NumberFormatException ignored) {
            return 70;
        }
    }
}
