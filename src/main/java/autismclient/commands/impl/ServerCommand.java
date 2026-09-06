package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.gui.screen.AutismOverlayHostScreen;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismServerInfoOverlay;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class ServerCommand extends Command {
    public ServerCommand() { super("server", "Open the server info panel, or the plugin scanner."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> { openInfo(); return SUCCESS; });
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("info").executes(ctx -> { openInfo(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("plugins").executes(ctx -> { openPlugins(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("tps").executes(ctx -> { tps(); return SUCCESS; }));
    }

    static void openInfo() {
        openOverlay(false);
    }

    static void openPlugins() {
        openOverlay(true);
    }

    private static void openOverlay(boolean pluginsTab) {
        AutismServerInfoOverlay overlay = AutismModule.get().getServerDataOverlay();
        if (overlay == null) { AutismClientMessaging.sendPrefixed("§cServer overlay unavailable."); return; }
        AutismOverlayManager.get().register(overlay);
        if (pluginsTab) overlay.openPluginsTab();
        else overlay.openInfoTab();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new AutismOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void tps() {
        double tps = autismclient.util.macro.ServerTickTracker.getEstimatedTps();
        AutismClientMessaging.sendPrefixed(String.format("§eTPS: §f%.2f §7(estimated)", tps));
    }
}
