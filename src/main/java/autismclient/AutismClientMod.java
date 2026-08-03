package autismclient;

import autismclient.gui.vanillaui.components.UiText;
import autismclient.modules.PackAutoReconnectState;
import autismclient.modules.AutismModule;
import autismclient.render.AutismFemaleBodyRenderer;
import autismclient.security.AutismProtector;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismProtectorServerPackFailureGuard;
import autismclient.security.AutismProtectorTracker;
import autismclient.security.AutismProtectorVanillaKeys;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismFakeGamemode;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismSvgHudLogo;
import autismclient.util.AutismWindowBranding;
import autismclient.util.AutismSharedState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public final class AutismClientMod implements ClientModInitializer {

    private static void runSafe(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Autism] client-event '{}' failed; isolated to protect the client", where, t);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_TICK_ERROR_MS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void logTickError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_TICK_ERROR_MS.get(where);
        if (last != null && now - last < 5000L) return;
        LAST_TICK_ERROR_MS.put(where, now);
        AutismClientAddon.LOG.warn("[Autism] tick '{}' failed; isolated to protect the client", where, t);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        AutismClientAddon.FOLDER.mkdirs();

        runSafe("initialize", () -> AutismModule.get().initialize());

        AutismProtectorTracker.bootstrap();

        if (!AutismProtector.isOverlapExternalProtectorPresent()) {
            AutismProtectorVanillaKeys.primeAsync();
        }
        if (AutismProtector.isFullExternalProtectorPresent()) {
            AutismClientAddon.LOG.info(
                "[AutismProtector] External protection mod detected; deferring all anti-fingerprint mixins to it.");
        } else if (AutismProtector.isExploitPreventerPresent()) {
            AutismClientAddon.LOG.info(
                "[AutismProtector] ExploitPreventer detected; deferring overlapping protections while keeping brand/channel hiding active.");
        } else {
            AutismClientAddon.LOG.info(
                "[AutismProtector] Built-in anti-fingerprint layer active.");
        }
        AutismInstaBreakRenderer.initialize();
        autismclient.util.AutismFreecamHighlightRenderer.initialize();
        autismclient.util.AutismSkeletonRenderer.initialize();
        autismclient.util.AutismWorldHighlightRenderer.initialize();
        autismclient.util.AutismScaffoldPlaceRenderer.initialize();
        autismclient.util.AutismKillAuraRenderer.initialize();
        AutismFemaleBodyRenderer.initialize();
        autismclient.commands.AutismCommands.init();

        autismclient.addons.AddonManager.init();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public net.minecraft.resources.Identifier getFabricId() {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath("autismclient", "ui_assets");
            }

            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                UiText.onClientResourceReload();
                AutismSvgHudLogo.clear();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            try {
                if (autismclient.util.AutismBlockNbtCapture.hasTickWork()) {
                    autismclient.util.AutismBlockNbtCapture.tick(client);
                }
            } catch (Throwable t) {
                logTickError("blockNbtCapture", t);
            }
            try {
                AutismWindowBranding.apply(client);
            } catch (Throwable ignored) {  }
            try {
                autismclient.security.AutismPackResponseScheduler.tick();
            } catch (Throwable t) {
                logTickError("packResponses", t);
            }
            try {
                AutismModule.get().tick();
            } catch (Throwable t) {
                logTickError("module", t);
            }
            try {
                autismclient.util.multi.PacketTeleportController.tick(client);
            } catch (Throwable t) {
                logTickError("pacedTp", t);
            }
            try {
                AutismJoinMacroController.onClientTick(client);
            } catch (Throwable t) {
                logTickError("joinMacro", t);
            }
            try {
                autismclient.util.AutismLagWatchdog.onClientTick(client);
            } catch (Throwable t) {
                logTickError("lagWatchdog", t);
            }
            try {
                autismclient.util.multi.MultiTakeoverState.tick();
            } catch (Throwable t) {
                logTickError("multiTakeover", t);
            }
            try {
                autismclient.util.AutismRemoteView.tick();
            } catch (Throwable t) {
                logTickError("remoteView", t);
            }
            try {

                if (autismclient.util.AutismDiscordLogin.hasSession() && autismclient.util.mm.MmPrefs.get().autoJoinPublic())
                    autismclient.util.mm.MatchmakingManager.get().autoJoinNudge();
            } catch (Throwable t) {
                logTickError("mmAutoJoin", t);
            }
        });
        ClientTickEvents.END_LEVEL_TICK.register(level ->
            runSafe("lan.levelTick", () -> AutismLANSync.getInstance().onLevelTick(level.getGameTime())));
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> {
            runSafe("cfg.hideMenuOverlays", () -> AutismModule.get().hideMenuOverlays());
            runSafe("cfg.configStarted", () -> AutismModule.get().onConfigurationConnectionStarted());
            runSafe("cfg.joinMacro", () -> AutismJoinMacroController.onConfigurationInit(listener));
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
            runSafe("cfg.remoteView", () -> autismclient.util.AutismRemoteView.stop(false));
            runSafe("cfg.persist", autismclient.util.AutismConfig::enqueuePendingSaveNow);
            runSafe("cfg.pacedTp", () -> autismclient.util.multi.PacketTeleportController.cancelAll("connection changed"));
            runSafe("cfg.fakeGamemode", AutismFakeGamemode::clear);
            runSafe("cfg.instaBreak", AutismInstaBreakRenderer::clear);
            runSafe("cfg.packFailureGuard", AutismProtectorServerPackFailureGuard::clear);
            runSafe("cfg.packResponses", autismclient.security.AutismPackResponseScheduler::clearAll);
            runSafe("cfg.hideOverlays", () -> AutismOverlayManager.get().hideAllInteractiveOverlays());
            runSafe("cfg.onGameLeft", () -> AutismModule.get().onGameLeft());
            runSafe("cfg.joinMacro", AutismJoinMacroController::onConfigurationDisconnect);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            runSafe("join.pacedTp", () -> autismclient.util.multi.PacketTeleportController.cancelAll("connection changed"));

            runSafe("join.movementGrace", autismclient.modules.AutismJoinGrace::onJoin);
            runSafe("join.hideMenuOverlays", () -> AutismModule.get().hideMenuOverlays());
            runSafe("join.remember", () -> PackAutoReconnectState.remember(client.getCurrentServer()));
            runSafe("join.onGameJoin", () -> AutismModule.get().onGameJoin());
            runSafe("join.joinMacro", AutismJoinMacroController::onPlayJoin);
            runSafe("join.surfaceFailures", autismclient.addons.AddonManager::surfaceFailuresOnJoin);
            runSafe("join.lagWatchdog", autismclient.util.AutismLagWatchdog::reset);
            runSafe("join.macroEditor", autismclient.util.AutismMacroEditorOverlay::onPlayJoin);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            runSafe("leave.remoteView", () -> autismclient.util.AutismRemoteView.stop(false));
            runSafe("leave.persist", autismclient.util.AutismConfig::enqueuePendingSaveNow);
            runSafe("leave.stopMacro", () -> {
                if (autismclient.util.AutismConfig.getGlobal().stopMacroOnLeave) autismclient.util.macro.MacroExecutor.stop();
            });
            runSafe("leave.pacedTp", () -> autismclient.util.multi.PacketTeleportController.cancelAll("disconnected"));
            runSafe("leave.movementGrace", autismclient.modules.AutismJoinGrace::clear);
            runSafe("leave.fakeGamemode", AutismFakeGamemode::clear);
            runSafe("leave.instaBreak", AutismInstaBreakRenderer::clear);
            runSafe("leave.packStrip", AutismProtectorPackStrip::clearAll);
            runSafe("leave.packResponses", autismclient.security.AutismPackResponseScheduler::clearAll);
            runSafe("leave.packFailureGuard", AutismProtectorServerPackFailureGuard::clear);
            runSafe("leave.macroEditor", autismclient.util.AutismMacroEditorOverlay::onPlayDisconnect);
            runSafe("leave.lanSync", () -> AutismLANSync.getInstance().onGameDisconnected());
            runSafe("leave.hideOverlays", () -> AutismOverlayManager.get().hideAllInteractiveOverlays());
            runSafe("leave.joinMacro", AutismJoinMacroController::onGameLeft);
            runSafe("leave.onGameLeft", () -> AutismModule.get().onGameLeft());
            runSafe("leave.lagWatchdog", autismclient.util.AutismLagWatchdog::reset);
        });
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
            AutismModule.get().appendTooltip(stack, lines)
        );

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            autismclient.util.mm.MatchmakingManager.get().shutdownLeave();

            autismclient.util.AutismConfig.flushPendingSaves(2000L);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            autismclient.util.mm.MatchmakingManager.get().shutdownLeave();
            autismclient.util.AutismConfig.flushPendingSaves(2000L);
        }, "mm-shutdown-leave"));
    }
}
