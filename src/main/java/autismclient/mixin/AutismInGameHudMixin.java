package autismclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.gui.vanillaui.components.Banner;
import autismclient.modules.AutismModule;
import autismclient.modules.Module;
import autismclient.modules.AntiVanishModule;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRenderUtil;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ModuleScreenRenderer;
import autismclient.modules.ModuleNameTagRenderer;
import autismclient.util.AutismHudManager;
import autismclient.util.AutismCaptureBannerSpec;
import autismclient.util.AutismMacroProgressRenderer;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPayloadStudySession;
import autismclient.util.AutismQueueRenderer;
import autismclient.util.AutismServerInfoOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismUiScale;
import autismclient.util.macro.MacroExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;

@Mixin(Hud.class)
public abstract class AutismInGameHudMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private static final int PACKUTIL_RIGHT_PANEL_W = 172;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void yang$renderAutismQueue(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {

        try {
            autism$renderHudBody(context);
        } catch (Throwable t) {
            autism$logRenderError("hudRoot", t);
        }
    }

    @Unique
    private void autism$renderHudBody(GuiGraphicsExtractor context) {
        if (!isAutismActive()) return;
        boolean macroFrameWork = MacroExecutor.hasRenderWork();
        autismclient.util.AutismRuntimeActivity.Snapshot activity = autismclient.util.AutismRuntimeActivity.current();
        long hudWork = autismclient.util.AutismRuntimeActivity.HUD_MODULE
            | autismclient.util.AutismRuntimeActivity.NAMETAGS
            | autismclient.util.AutismRuntimeActivity.OVERLAY
            | autismclient.util.AutismRuntimeActivity.HUD_AUX;
        if (!macroFrameWork && !activity.has(hudWork)) return;

        autismclient.gui.vanillaui.UiScissorStack.global().clear(context);
        if (macroFrameWork) MacroExecutor.onRender(1.0f);
        if (MC.gui.hud.isHidden()) return;
        if (PackHideState.isActive()) return;

        AutismSharedState shared = AutismSharedState.get();
        var screen = MC.gui.screen();
        boolean macroRunning = MacroExecutor.isVisibleRunning();
        boolean queueSending = shared.hasStaggeredPackets();
        boolean queueVisible = shared.shouldDelayGuiPackets()
            || shared.hasDelayedPackets()
            || queueSending;
        boolean captureActive = hasAnyCaptureSession(shared);
        boolean payloadStudyActive = AutismPayloadStudySession.isActive();
        Module hud = activity.has(autismclient.util.AutismRuntimeActivity.HUD_MODULE) ? ModuleRegistry.get("hud") : null;
        boolean nativeHudVisible = hud != null && AutismHudManager.shouldRenderInGame(screen, hud);
        boolean esp2dVisible = ModuleRenderUtil.has2dEspWork();
        boolean nametagsVisible = activity.has(autismclient.util.AutismRuntimeActivity.NAMETAGS);
        boolean antiVanishHudVisible = screen == null && AntiVanishModule.shouldShowHud();
        boolean mainHudVisible = nativeHudVisible || antiVanishHudVisible || macroRunning || queueVisible
            || captureActive || payloadStudyActive || esp2dVisible || nametagsVisible;

        AutismServerInfoOverlay serverInfoOverlay = null;
        boolean serverProbeBannerVisible = false;
        AutismOverlayManager overlayManager = null;
        boolean overlayVisible = false;
        if (screen == null) {
            serverInfoOverlay = AutismModule.get().getServerDataOverlayIfExists();
            serverProbeBannerVisible = serverInfoOverlay != null && serverInfoOverlay.shouldRenderBackgroundProbeBanner();
            if (activity.has(autismclient.util.AutismRuntimeActivity.OVERLAY)) {
                overlayManager = AutismOverlayManager.get();
                overlayVisible = overlayManager.hasVisibleOverlay();
            }
        }
        boolean notificationsVisible = AutismNotifications.hasVisible();
        if (!mainHudVisible && !serverProbeBannerVisible && !overlayVisible && !notificationsVisible) return;

        Runnable renderHudElements = () -> {
            int screenWidth = AutismUiScale.getVirtualScreenWidth();
            int x = Math.max(0, screenWidth - PACKUTIL_RIGHT_PANEL_W);
            int y = 0;
            AutismCaptureBannerSpec captureBanner = captureActive ? captureBannerSpec(shared, context) : null;
            AutismCaptureBannerSpec payloadStudyBanner = payloadStudyActive ? payloadStudyBannerSpec(context, captureBanner == null ? 0 : captureBanner.height()) : null;
            java.util.ArrayList<AutismHudManager.ElementBounds> hudOccluders = new java.util.ArrayList<>(3);
            if (captureBanner != null) {
                hudOccluders.add(new AutismHudManager.ElementBounds("capture_banner",
                    captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()));
            }
            if (payloadStudyBanner != null) {
                hudOccluders.add(new AutismHudManager.ElementBounds("payload_study_banner",
                    payloadStudyBanner.x(), payloadStudyBanner.y(), payloadStudyBanner.width(), payloadStudyBanner.height()));
            } else if (captureActive) {
                int fallbackW = Math.min(screenWidth - 16, 300);
                hudOccluders.add(new AutismHudManager.ElementBounds("capture_banner",
                    Math.max(0, (screenWidth - fallbackW) / 2), 0, fallbackW, 56));
            }
            if (queueVisible) {
                int queueHeight = AutismQueueRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 8);
                if (queueHeight > 0) {
                    hudOccluders.add(new AutismHudManager.ElementBounds("packet_queue", x, y, PACKUTIL_RIGHT_PANEL_W, queueHeight));
                    y += queueHeight;
                }
            }
            if (macroRunning) {
                int macroHeight = AutismMacroProgressRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 10);
                if (macroHeight > 0) hudOccluders.add(new AutismHudManager.ElementBounds("macro_queue", x, y, PACKUTIL_RIGHT_PANEL_W, macroHeight));
            }

            if (nativeHudVisible) AutismHudManager.render(context, MC.font, false, null, -1, -1, hudOccluders);
            else if (antiVanishHudVisible) AutismHudManager.renderSingle(context, MC.font, AutismHudManager.ANTI_VANISH);

            if (captureBanner != null) {
                Banner.render(UiContexts.overlay(context, MC.font, 0, 0),
                    UiBounds.of(captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()),
                    captureBanner.title(), captureBanner.line1(), captureBanner.line2());
            }
            if (payloadStudyBanner != null) {
                Banner.render(UiContexts.overlay(context, MC.font, 0, 0),
                    UiBounds.of(payloadStudyBanner.x(), payloadStudyBanner.y(), payloadStudyBanner.width(), payloadStudyBanner.height()),
                    payloadStudyBanner.title(), payloadStudyBanner.line1(), payloadStudyBanner.line2());
            }

            y = 0;

            if (queueVisible) {
                int queueHeight = AutismQueueRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 8,
                    false, !macroRunning, false);
                y += queueHeight;
            }

            if (macroRunning) {
                AutismMacroProgressRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 10,
                    false, true, false);
            }

            if (esp2dVisible) ModuleScreenRenderer.render(context);
            if (nametagsVisible) ModuleNameTagRenderer.render(context);
        };

        if (mainHudVisible) {
            long perfStart = autismclient.util.AutismPerf.beginSampled();
            AutismUiScale.pushOverlayScale(context);
            try {
                renderHudElements.run();
            } catch (Throwable t) {
                autism$logRenderError("hudElements", t);
            } finally {
                AutismUiScale.popOverlayScale(context);
            }
            autismclient.util.AutismPerf.end("hud.section.hudElements", perfStart);
        }

        if (MC.gui.screen() == null) {
            if (serverProbeBannerVisible) {
                long perfStart = autismclient.util.AutismPerf.beginSampled();
                AutismUiScale.pushOverlayScale(context);
                try {
                    serverInfoOverlay.renderBackgroundProbeBanner(context);
                } catch (Throwable t) {
                    autism$logRenderError("probeBanner", t);
                } finally {
                    AutismUiScale.popOverlayScale(context);
                }
                autismclient.util.AutismPerf.end("hud.section.probeBanner", perfStart);
            }

            if (overlayVisible) {
                long perfStart = autismclient.util.AutismPerf.beginSampled();
                try {
                    overlayManager.renderAll(context, -1, -1, 0f);
                } catch (Throwable t) {
                    autism$logRenderError("overlays", t);
                }
                autismclient.util.AutismPerf.end("hud.section.overlays", perfStart);
            }
        }

        if (notificationsVisible) {
            long perfStart = autismclient.util.AutismPerf.beginSampled();
            AutismUiScale.pushOverlayScale(context);
            try {
                AutismNotifications.render(context);
            } catch (Throwable t) {
                autism$logRenderError("notifications", t);
            } finally {
                AutismUiScale.popOverlayScale(context);
            }
            autismclient.util.AutismPerf.end("hud.section.notifications", perfStart);
        }
    }

    @Unique private static long autism$lastRenderErrorMs;

    @Unique private net.minecraft.world.level.block.state.BlockState autism$cachedBlockState;
    @Unique private String autism$cachedBlockName = "";
    @Unique private net.minecraft.world.entity.EntityType<?> autism$cachedEntityType;
    @Unique private String autism$cachedEntityLabel = "";

    @Unique
    private void autism$logRenderError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - autism$lastRenderErrorMs < 5000L) return;
        autism$lastRenderErrorMs = now;
        autismclient.AutismClientAddon.LOG.warn("[Autism] HUD section '{}' failed; isolated to protect the UI", where, t);
    }

    @Unique
    private boolean isAutismActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private AutismCaptureBannerSpec captureBannerSpec(AutismSharedState shared, GuiGraphicsExtractor graphics) {
        boolean blockCap = shared.hasBlockCaptureCallback();
        boolean entityCap = shared.hasEntityCaptureCallback();
        boolean attackCap = shared.hasAttackCaptureCallback();
        boolean gbreakCap = shared.isGBreakCapturing();
        if (!blockCap && !entityCap && !attackCap && !gbreakCap) return null;

        String title = gbreakCap
            ? "GBreak Capture"
            : (blockCap ? "Block Capture" : (entityCap ? "Entity Capture" : "Position Capture"));
        String line1 = gbreakCap
            ? "Break a block to capture the insta-break packet. Esc = cancel"
            : (blockCap
                ? "Right-click a block to capture it. Esc = cancel"
                : (entityCap
                    ? "Right-click an entity to capture it. Esc = cancel"
                    : "Left-click to capture the target position. Esc = cancel"));
        String line2 = "";
        if (gbreakCap) {
            line2 = "Waiting for the block-break packet from your next block break";
        } else if (blockCap && MC.hitResult != null
                && MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && MC.level != null) {
            net.minecraft.world.phys.BlockHitResult bhr = (net.minecraft.world.phys.BlockHitResult) MC.hitResult;
            net.minecraft.core.BlockPos bp = bhr.getBlockPos();
            net.minecraft.world.level.block.state.BlockState state = MC.level.getBlockState(bp);
            if (state != autism$cachedBlockState) {
                autism$cachedBlockName = state.getBlock().getName().getString();
                autism$cachedBlockState = state;
            }
            line2 = "Aimed at: " + autism$cachedBlockName + " (" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + ")";
        } else if (entityCap && MC.crosshairPickEntity != null && MC.crosshairPickEntity != MC.player) {
            net.minecraft.world.entity.EntityType<?> type = MC.crosshairPickEntity.getType();
            if (type != autism$cachedEntityType) {
                String eName = type.getDescription().getString();
                String eId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                autism$cachedEntityLabel = eName + " (" + eId + ")";
                autism$cachedEntityType = type;
            }
            line2 = "Aimed at: " + autism$cachedEntityLabel;
        }

        int sw = AutismUiScale.getVirtualScreenWidth();
        UiTextRenderer text = UiContexts.textRenderer(MC.font);
        int boxWidth = Math.min(sw - 16, Math.max(270, Math.max(
            text.width(title),
            Math.max(
                text.width(line1),
                line2.isEmpty() ? 0 : text.width(line2)
            )
        ) + 18));
        int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
        return new AutismCaptureBannerSpec((sw - boxWidth) / 2, 0, boxWidth, height, title, line1, line2);
    }

    @Unique
    private AutismCaptureBannerSpec payloadStudyBannerSpec(GuiGraphicsExtractor graphics, int topOffset) {
        String title = AutismPayloadStudySession.bannerTitle();
        if (title.isBlank()) return null;
        String line1 = AutismPayloadStudySession.bannerLine1();
        String line2 = AutismPayloadStudySession.bannerLine2();
        int sw = AutismUiScale.getVirtualScreenWidth();
        UiTextRenderer text = UiContexts.textRenderer(MC.font);
        int boxWidth = Math.min(sw - 16, Math.max(276, Math.max(
            text.width(title),
            Math.max(text.width(line1), text.width(line2))
        ) + 18));
        int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
        return new AutismCaptureBannerSpec((sw - boxWidth) / 2, Math.max(0, topOffset), boxWidth, height, title, line1, line2);
    }

    @Unique
    private boolean hasAnyCaptureSession(AutismSharedState shared) {
        if (shared == null) return false;
        if (shared.isCaptureMode() || shared.hasCaptureCancelCallback() || shared.hasAttackCaptureCallback()
            || shared.hasBlockCaptureCallback() || shared.hasEntityCaptureCallback() || shared.isGBreakCapturing()) {
            return true;
        }
        autismclient.gui.macro.editor.ActionEditorOverlay editor =
            autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        return editor != null && editor.hasActiveCaptureSession();
    }
}
