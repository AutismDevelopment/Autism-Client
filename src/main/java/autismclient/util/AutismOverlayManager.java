package autismclient.util;

import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.gui.vanillaui.UiInputRouter;
import autismclient.gui.vanillaui.UiLayer;
import autismclient.gui.vanillaui.UiLayerManager;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.gui.vanillaui.UiTheme;
import autismclient.gui.vanillaui.components.OperationalOverlayComponent;
import autismclient.gui.screen.AutismModuleScreen;
import autismclient.gui.screen.AutismOverlayHostScreen;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import autismclient.util.AutismUiScale;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutismOverlayManager {
    private static final AutismOverlayManager INSTANCE = new AutismOverlayManager();
    public static final int HOVER_BLOCKED_MOUSE = -10000;
    private static final double HEADER_CLICK_DRAG_THRESHOLD = 3.0;

    private final List<IAutismOverlay> overlays = new CopyOnWriteArrayList<>();
    private final List<IAutismOverlay> renderOverlays = new ArrayList<>();
    private final Map<IAutismOverlay, OperationalOverlayComponent> overlayComponents = new IdentityHashMap<>();
    private final Map<IAutismOverlay, IAutismOverlay.OverlayScope> overlayScopes = new IdentityHashMap<>();
    private final Set<String> temporarilyHiddenOverlayIds = new HashSet<>();
    private final UiTheme uiTheme = new UiTheme();
    private final UiLayerManager overlayLayers = new UiLayerManager();
    private final UiInputRouter overlayInput = new UiInputRouter(overlayLayers);
    private UiTextRenderer uiText;
    private boolean overlayLayersDirty = true;
    private IAutismOverlay focusedOverlay = null;
    private IAutismOverlay textFieldFocusOverlay;
    private boolean textFieldFocusDirty = true;
    private double cachedHoverBlockMouseX = Double.NaN;
    private double cachedHoverBlockMouseY = Double.NaN;
    private long cachedHoverBlockNanos;
    private boolean cachedHoverBlockResult;

    private AutismOverlayManager() {}

    public static AutismOverlayManager get() {
        return INSTANCE;
    }

    public List<IAutismOverlay> getOverlays() {
        return overlays;
    }

    public boolean hasRegisteredOverlays() {
        return !overlays.isEmpty();
    }

    public boolean hasVisibleOverlay() {
        if (PackHideState.isActive()) return false;

        return anyInteractiveOverlays;
    }

    public String censusSummary() {
        StringBuilder visible = new StringBuilder();
        int visibleCount = 0;
        for (IAutismOverlay overlay : overlays) {
            if (!isOverlayInteractive(overlay)) continue;
            if (visibleCount++ > 0) visible.append(", ");
            visible.append(overlay.getOverlayId());
        }
        return "registered=" + overlays.size() + " visible=" + visibleCount + " [" + visible + "]";
    }

    public void register(IAutismOverlay overlay) {
        register(overlay, inferScopeForCurrentScreen(overlay));
    }

    public void register(IAutismOverlay overlay, IAutismOverlay.OverlayScope scope) {
        if (overlay == null) return;
        if (!overlays.contains(overlay)) {
            overlays.add(overlay);
        }
        overlayScopes.put(overlay, scope == null ? overlay.getDefaultOverlayScope() : scope);
        overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
        overlayLayersDirty = true;
        textFieldFocusDirty = true;
        publishInteractiveOverlays(true);
        invalidateHoverBlockCache();
        restoreSavedOverlayOrder();
        normalizeOverlayStack();
        if (overlay instanceof AutismLauncherOverlay || "autism-launcher".equals(overlay.getOverlayId())) {
            reclampAllOverlays();
        }
    }

    public void unregister(IAutismOverlay overlay) {
        if (overlay == null) return;
        overlays.remove(overlay);
        overlayComponents.remove(overlay);
        overlayScopes.remove(overlay);
        overlayLayersDirty = true;
        temporarilyHiddenOverlayIds.remove(overlay.getOverlayId());
        if (focusedOverlay == overlay) focusedOverlay = null;
        if (textFieldFocusOverlay == overlay) textFieldFocusOverlay = null;
        textFieldFocusDirty = true;
        if (overlays.isEmpty()) publishInteractiveOverlays(false);
        invalidateHoverBlockCache();
        saveOverlayOrder();
    }

    public void clear() {

        overlays.removeIf(overlay -> {
            if (overlay.persistsAcrossScreenClose()) return false;
            overlayComponents.remove(overlay);
            overlayScopes.remove(overlay);
            return true;
        });
        overlayLayers.clear();
        overlayLayersDirty = true;
        temporarilyHiddenOverlayIds.clear();
        draggingOverlay = null;
        dragStartBounds = null;
        resizingOverlay = null;
        headerCollapseOverlay = null;
        focusedOverlay = null;
        textFieldFocusOverlay = null;
        textFieldFocusDirty = false;
        headerCollapseMoved = false;
        headerCollapseStartBounds = null;
        resizeStartBounds = null;
        inventoryMouseDown = false;
        publishInteractiveOverlays(!overlays.isEmpty());
        invalidateHoverBlockCache();
    }

    public void setTemporarilyHidden(IAutismOverlay overlay, boolean hidden) {
        if (overlay == null) return;
        String id = overlay.getOverlayId();
        if (id == null || id.isEmpty()) return;

        if (hidden) {
            temporarilyHiddenOverlayIds.add(id);
            overlay.clearTextFieldFocus();
            if (focusedOverlay == overlay) focusedOverlay = null;
            if (draggingOverlay == overlay) { draggingOverlay = null; dragStartBounds = null; }
            if (resizingOverlay == overlay) resizingOverlay = null;
            if (headerCollapseOverlay == overlay) headerCollapseOverlay = null;
        } else {
            temporarilyHiddenOverlayIds.remove(id);
        }
        textFieldFocusDirty = true;
        invalidateHoverBlockCache();
    }

    public void clearTemporaryHidden() {
        temporarilyHiddenOverlayIds.clear();
        textFieldFocusDirty = true;
        invalidateHoverBlockCache();
    }

    public boolean isTemporarilyHidden(IAutismOverlay overlay) {
        if (overlay == null) return false;
        String id = overlay.getOverlayId();
        return id != null && temporarilyHiddenOverlayIds.contains(id);
    }

    private boolean isOverlayInteractive(IAutismOverlay overlay) {
        Minecraft mc = Minecraft.getInstance();
        return isOverlayInteractive(overlay, mc == null ? null : mc.gui.screen());
    }

    private boolean isOverlayInteractive(IAutismOverlay overlay, Screen screen) {
        return overlay != null
            && overlay.isVisible()
            && !isTemporarilyHidden(overlay)
            && isScopeValid(overlay, screen);
    }

    private IAutismOverlay.OverlayScope inferScopeForCurrentScreen(IAutismOverlay overlay) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null ? null : mc.gui.screen();

        if (overlay != null && overlay.getDefaultOverlayScope() == IAutismOverlay.OverlayScope.BACKGROUND_STATUS) {
            return IAutismOverlay.OverlayScope.BACKGROUND_STATUS;
        }

        if (!autismclient.util.AutismLiteVariant.enabled() && screen instanceof AutismModuleScreen)
            return IAutismOverlay.OverlayScope.MODULE_MENU;
        if (screen instanceof AutismOverlayHostScreen || screen == null || screen instanceof ChatScreen) {
            return overlay == null ? IAutismOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope();
        }
        return IAutismOverlay.OverlayScope.CONTAINER_GUI;
    }

    private boolean isScopeValid(IAutismOverlay overlay, Screen screen) {
        IAutismOverlay.OverlayScope scope = overlayScopes.getOrDefault(
            overlay,
            overlay == null ? IAutismOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope()
        );
        if (scope == IAutismOverlay.OverlayScope.BACKGROUND_STATUS) return true;
        if (screen == null || screen instanceof ChatScreen || screen instanceof InBedChatScreen) return false;

        if (!autismclient.util.AutismLiteVariant.enabled() && screen instanceof AutismModuleScreen)
            return !isLauncherOverlay(overlay);
        if (screen instanceof AutismOverlayHostScreen) return true;
        return switch (scope) {
            case BACKGROUND_STATUS -> true;
            case HOST_SCREEN, MODULE_MENU, CONTAINER_GUI -> true;
        };
    }

    public void hideInvalidOverlaysForCurrentScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null ? null : mc.gui.screen();
        boolean changed = false;
        for (IAutismOverlay overlay : overlays) {
            if (overlay == null || !overlay.isVisible()) continue;
            if (!isScopeValid(overlay, screen)) {
                overlay.clearTextFieldFocus();
                if (focusedOverlay == overlay) focusedOverlay = null;
                if (draggingOverlay == overlay) { draggingOverlay = null; dragStartBounds = null; }
                if (resizingOverlay == overlay) resizingOverlay = null;
                if (headerCollapseOverlay == overlay) headerCollapseOverlay = null;
                changed = true;
            }
        }

        if (changed) {
            textFieldFocusDirty = true;
            invalidateHoverBlockCache();
        }
    }

    public void hideAllInteractiveOverlays() {
        for (IAutismOverlay overlay : overlays) {
            if (overlay == null || overlayScopes.getOrDefault(overlay, overlay.getDefaultOverlayScope()) == IAutismOverlay.OverlayScope.BACKGROUND_STATUS) {
                continue;
            }
            if (overlay.isVisible()) overlay.setVisible(false);
            overlay.clearTextFieldFocus();
        }
        temporarilyHiddenOverlayIds.clear();
        draggingOverlay = null;
        dragStartBounds = null;
        resizingOverlay = null;
        headerCollapseOverlay = null;
        focusedOverlay = null;
        textFieldFocusOverlay = null;
        textFieldFocusDirty = false;
        inventoryMouseDown = false;
        invalidateHoverBlockCache();
    }

    public void bringToFront(IAutismOverlay overlay) {
        if (overlay == null) return;
        overlays.remove(overlay);
        overlays.add(overlay);
        overlayLayersDirty = true;
        publishInteractiveOverlays(true);
        focusedOverlay = overlay;
        textFieldFocusDirty = true;
        AutismSharedState.get().setFocusedOverlayId(overlay.getOverlayId());
        invalidateHoverBlockCache();
        normalizeOverlayStack();
        saveOverlayOrder();
    }

    public void bringToFrontParent(Object childComponent) {
        if (childComponent == null) return;
        for (IAutismOverlay overlay : overlays) {
            if (overlay instanceof AutismCustomFilterOverlay filterOverlay
                && filterOverlay.getPacketSelectorOverlay() == childComponent) {
                bringToFront(overlay);
                return;
            }
        }
    }

    private void restoreSavedOverlayOrder() {
        if (overlays.size() < 2) {
            restoreFocusedOverlay();
            normalizeOverlayStack();
            return;
        }

        List<String> savedOrder = AutismSharedState.get().getOverlayOrder();
        if (savedOrder.isEmpty()) {
            restoreFocusedOverlay();
            normalizeOverlayStack();
            return;
        }

        String focusedId = AutismSharedState.get().getFocusedOverlayId();

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < savedOrder.size(); i++) {
            positions.putIfAbsent(savedOrder.get(i), i);
        }

        List<IAutismOverlay> ordered = new ArrayList<>(overlays);
        ordered.sort(Comparator
            .comparingInt((IAutismOverlay overlay) -> positions.getOrDefault(overlay.getOverlayId(), Integer.MAX_VALUE))
            .thenComparingInt(overlay -> focusedId.equals(overlay.getOverlayId()) ? 1 : 0));
        overlays.clear();
        overlays.addAll(ordered);
        overlayLayersDirty = true;
        restoreFocusedOverlay();
        normalizeOverlayStack();
    }

    private void restoreFocusedOverlay() {
        String focusedId = AutismSharedState.get().getFocusedOverlayId();
        if (focusedId.isEmpty()) {
            focusedOverlay = null;
            return;
        }
        focusedOverlay = null;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (overlay != null && focusedId.equals(overlay.getOverlayId()) && isOverlayInteractive(overlay)) {
                focusedOverlay = overlay;
                break;
            }
        }
    }

    private void saveOverlayOrder() {
        List<String> order = new ArrayList<>(overlays.size());
        for (IAutismOverlay overlay : overlays) {
            String id = overlay.getOverlayId();
            if (id == null || id.isEmpty() || order.contains(id) || isLauncherOverlay(overlay) || isTransientOverlay(overlay)) continue;
            order.add(id);
        }
        AutismSharedState.get().setOverlayOrder(order);
    }

    private void normalizeOverlayStack() {
        if (overlays.isEmpty()) return;

        List<IAutismOverlay> launchers = new ArrayList<>();
        List<IAutismOverlay> others = new ArrayList<>();
        for (IAutismOverlay overlay : overlays) {
            if (isLauncherOverlay(overlay)) launchers.add(overlay);
            else others.add(overlay);
        }

        if (launchers.isEmpty()) return;

        overlays.clear();
        overlays.addAll(launchers);
        overlays.addAll(others);
        overlayLayersDirty = true;
    }

    private boolean isLauncherOverlay(IAutismOverlay overlay) {
        return overlay instanceof AutismLauncherOverlay || (overlay != null && "autism-launcher".equals(overlay.getOverlayId()));
    }

    private boolean isTransientOverlay(IAutismOverlay overlay) {
        return overlay != null && "macro-step-picker".equals(overlay.getOverlayId());
    }

    public void reclampAllOverlays() {
        for (IAutismOverlay overlay : overlays) {
            reclampOverlayPreserving(overlay);
        }
        pruneClampedAwayBounds();
        invalidateHoverBlockCache();
    }

    private void pruneClampedAwayBounds() {
        if (clampedAwayBounds.isEmpty()) return;
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        if (sw <= 0 || sh <= 0) return;

        clampedAwayBounds.values().removeIf(stashed -> fitsOnScreen(stashed, sw, sh));
        if (clampedAwayBounds.size() <= MAX_CLAMPED_AWAY) return;

        Set<String> live = new HashSet<>();
        for (IAutismOverlay overlay : overlays) {
            if (overlay != null && overlay.getOverlayId() != null) live.add(overlay.getOverlayId());
        }
        clampedAwayBounds.keySet().removeIf(id -> !live.contains(id));
    }

    static boolean fitsOnScreen(AutismWindowLayout bounds, int screenWidth, int screenHeight) {
        if (bounds == null || screenWidth <= 0 || screenHeight <= 0) return false;
        return samePlacement(AutismWindow.clampToScreenSize(bounds, bounds.width, bounds.height,
            screenWidth, screenHeight), bounds);
    }

    private final Map<String, AutismWindowLayout> clampedAwayBounds = new HashMap<>();

    private static final int MAX_CLAMPED_AWAY = 64;

    private void reclampOverlayPreserving(IAutismOverlay overlay) {
        if (overlay == null) return;
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        if (sw <= 0 || sh <= 0) return;
        String id = overlay.getOverlayId();
        AutismWindowLayout stashed = id == null ? null : clampedAwayBounds.get(id);
        AutismWindowLayout basis = stashed != null ? stashed : trueBoundsOf(overlay, sw, sh);
        if (basis == null) return;

        basis = withLiveState(basis, overlay.getBounds());
        AutismWindowLayout clamped = AutismWindow.clampToScreenSize(basis, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
        if (samePlacement(clamped, basis)) {
            if (stashed != null) {
                clampedAwayBounds.remove(id);
                overlay.setBounds(basis);
            }
            return;
        }
        if (stashed == null && id != null) clampedAwayBounds.put(id, basis);
        overlay.setBounds(basis);
    }

    static AutismWindowLayout withLiveState(AutismWindowLayout geometry, AutismWindowLayout live) {
        if (geometry == null) return null;
        if (live == null) return geometry;
        return new AutismWindowLayout(geometry.x, geometry.y, geometry.width, geometry.height,
            live.visible, live.collapsed);
    }

    private AutismWindowLayout trueBoundsOf(IAutismOverlay overlay, int sw, int sh) {
        AutismWindowLayout current = overlay.getBounds();
        if (current == null) return null;
        AutismWindowLayout persisted = AutismSharedState.get().getWindowLayout(overlay.getOverlayId());
        if (persisted == null) return current;
        AutismWindowLayout clampOfPersisted = AutismWindow.clampToScreenSize(persisted, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
        if (!samePlacement(persisted, clampOfPersisted) && samePlacement(current, clampOfPersisted)) return persisted;
        return current;
    }

    private static boolean samePlacement(AutismWindowLayout a, AutismWindowLayout b) {
        return a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
    }

    public AutismWindowLayout clampedAwayTrueGeometry(String overlayId) {
        return overlayId == null ? null : clampedAwayBounds.get(overlayId);
    }

    public void restoreClampedAwayBounds() {
        if (clampedAwayBounds.isEmpty()) return;
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        if (sw <= 0 || sh <= 0) return;
        for (IAutismOverlay overlay : overlays) {
            if (overlay == null || overlay.getOverlayId() == null) continue;
            AutismWindowLayout stashed = clampedAwayBounds.get(overlay.getOverlayId());
            if (stashed == null) continue;

            stashed = withLiveState(stashed, overlay.getBounds());
            AutismWindowLayout clamped = AutismWindow.clampToScreenSize(stashed, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
            if (samePlacement(clamped, stashed)) {
                clampedAwayBounds.remove(overlay.getOverlayId());
                overlay.setBounds(stashed);
            }
        }
    }

    private IAutismOverlay draggingOverlay = null;
    private IAutismOverlay resizingOverlay = null;
    private IAutismOverlay headerCollapseOverlay = null;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

    private volatile boolean anyInteractiveOverlays = false;

    private void publishInteractiveOverlays(boolean active) {
        if (anyInteractiveOverlays == active) return;
        anyInteractiveOverlays = active;
        AutismRuntimeActivity.publish(AutismRuntimeActivity.OVERLAY, active);
    }
    private boolean headerCollapseMoved = false;

    private boolean inventoryMouseDown = false;
    private AutismWindowLayout headerCollapseStartBounds = null;
    private AutismWindowLayout resizeStartBounds = null;

    private AutismWindowLayout dragStartBounds = null;
    private double headerCollapseStartMouseX = 0;
    private double headerCollapseStartMouseY = 0;
    private double resizeStartMouseX = 0;
    private double resizeStartMouseY = 0;

    private long lastRenderErrorLogMs;

    private void logRenderError(Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastRenderErrorLogMs < 5000L) return;
        lastRenderErrorLogMs = now;
        autismclient.AutismClientAddon.LOG.warn("[Overlay] render failed; skipped to protect the UI", t);
    }

    public void renderAll(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (PackHideState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        hideInvalidOverlaysForCurrentScreen();
        if (mc.getWindow() != null) {
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();

            if (sw > 0 && sh > 0 && (sw != lastScreenWidth || sh != lastScreenHeight)) {
                lastScreenWidth = sw;
                lastScreenHeight = sh;
                invalidateHoverBlockCache();
                for (IAutismOverlay overlay : overlays) {
                    reclampOverlayPreserving(overlay);
                }
            }
        }

        Screen screen = mc.gui.screen();
        int virtualMouseX = (int) Math.round(AutismUiScale.toVirtual(mouseX));
        int virtualMouseY = (int) Math.round(AutismUiScale.toVirtual(mouseY));
        renderOverlays.clear();
        for (IAutismOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay, screen)) {
                renderOverlays.add(overlay);
            }
        }
        publishInteractiveOverlays(!renderOverlays.isEmpty());
        if (renderOverlays.isEmpty()) {
            if (AutismNotifications.hasVisible()) {
                AutismUiScale.pushOverlayScale(context);
                try {
                    context.nextStratum();
                    AutismNotifications.render(context);
                } finally {
                    AutismUiScale.popOverlayScale(context);
                }
            }
            return;
        }

        int hoveredOverlayIndex = -1;
        for (int i = renderOverlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = renderOverlays.get(i);
            if (overlay.isMouseOver(virtualMouseX, virtualMouseY)) {
                hoveredOverlayIndex = i;
                break;
            }
        }
        if (uiText == null || uiText.font() != mc.font) {
            uiText = UiContexts.textRenderer(mc.font);
        }
        UiContext uiContext = new UiContext(context, uiTheme, uiText, lastScreenWidth, lastScreenHeight, virtualMouseX, virtualMouseY, delta);
        int visibleIndex = 0;
        for (IAutismOverlay overlay : overlays) {
            OperationalOverlayComponent adapter = adapterFor(overlay);
            boolean interactive = isOverlayInteractive(overlay, screen);
            adapter.setRenderSuppressed(!interactive);
            adapter.setInputSuppressed(!interactive);
            adapter.setHoverBlocked(interactive && hoveredOverlayIndex > visibleIndex);
            if (interactive) visibleIndex++;
        }
        rebuildOverlayLayers();
        AutismUiScale.pushOverlayScale(context);
        try {
            UiScissorStack.global().clear(context);
            try {
                overlayLayers.render(uiContext);
            } catch (Throwable t) {

                UiScissorStack.global().clear(context);
                logRenderError(t);
            }
            if (AutismNotifications.hasVisible()) {
                context.nextStratum();
                AutismNotifications.render(context);
            }
        } finally {
            UiScissorStack.global().clear(context);
            AutismUiScale.popOverlayScale(context);
            renderOverlays.clear();
        }
    }

    public boolean isMouseOverAnyOverlay(double mouseX, double mouseY) {
        if (PackHideState.isActive()) return false;
        return isMouseOverAnyOverlayVirtual(AutismUiScale.toVirtual(mouseX), AutismUiScale.toVirtual(mouseY));
    }

    private boolean isMouseOverAnyOverlayVirtual(double mouseX, double mouseY) {
        for (IAutismOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldBlockUnderlyingHover(double mouseX, double mouseY) {
        if (PackHideState.isActive()) return false;
        if (overlays.isEmpty()) return false;

        if (!anyInteractiveOverlays) return false;
        long now = System.nanoTime();
        if (Double.compare(mouseX, cachedHoverBlockMouseX) == 0
            && Double.compare(mouseY, cachedHoverBlockMouseY) == 0
            && now - cachedHoverBlockNanos < 16_000_000L) {
            return cachedHoverBlockResult;
        }

        boolean result = isMouseOverAnyOverlayVirtual(
            AutismUiScale.toVirtual(mouseX),
            AutismUiScale.toVirtual(mouseY)
        );
        cachedHoverBlockMouseX = mouseX;
        cachedHoverBlockMouseY = mouseY;
        cachedHoverBlockNanos = now;
        cachedHoverBlockResult = result;
        return result;
    }

    private void invalidateHoverBlockCache() {
        cachedHoverBlockMouseX = Double.NaN;
        cachedHoverBlockMouseY = Double.NaN;
        cachedHoverBlockNanos = 0L;
        cachedHoverBlockResult = false;
    }

    private void clearFocusedTextFields() {
        for (IAutismOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
                overlay.clearTextFieldFocus();
            }
        }
        textFieldFocusOverlay = null;
        textFieldFocusDirty = false;
    }

    private IAutismOverlay getTopmostOverlayAt(double mouseX, double mouseY) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
                return overlay;
            }
        }
        return null;
    }

    public boolean isTopOverlay(IAutismOverlay overlay) {
        if (overlay == null || overlays.isEmpty()) return false;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay candidate = overlays.get(i);
            if (isOverlayInteractive(candidate)) {
                return candidate == overlay;
            }
        }
        return false;
    }

    public boolean isFocusedOverlay(IAutismOverlay overlay) {
        return overlay != null && overlay == focusedOverlay;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (PackHideState.isActive()) return false;
        inventoryMouseDown = false;
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        IAutismOverlay topOverlay = getTopmostOverlayAt(mouseX, mouseY);
        if (topOverlay == null) {

            clearFocusedTextFields();
            focusedOverlay = null;
            AutismSharedState.get().setFocusedOverlayId("");
            headerCollapseOverlay = null;
            headerCollapseMoved = false;
            headerCollapseStartBounds = null;
            inventoryMouseDown = true;
            return false;
        }

        clearFocusedTextFields();
        if (button == 0) {
            if (topOverlay.isOverResizeHandle(mouseX, mouseY)) {
                resizingOverlay = topOverlay;
                resizeStartBounds = topOverlay.getBounds();
                resizeStartMouseX = mouseX;
                resizeStartMouseY = mouseY;
                bringToFront(topOverlay);
                return true;
            }

            if (topOverlay.isOverDragBar(mouseX, mouseY)) {
                draggingOverlay = topOverlay;
                dragStartBounds = topOverlay.getBounds();
                if (topOverlay.usesSharedHeaderClickCollapse()) {
                    headerCollapseOverlay = topOverlay;
                    headerCollapseMoved = false;
                    headerCollapseStartMouseX = mouseX;
                    headerCollapseStartMouseY = mouseY;
                    headerCollapseStartBounds = topOverlay.getBounds();
                } else {
                    headerCollapseOverlay = null;
                    headerCollapseMoved = false;
                    headerCollapseStartBounds = null;
                }
                bringToFront(topOverlay);
                adapterFor(topOverlay).mouseClicked((int) mouseX, (int) mouseY, button);
                captureTextFieldFocus(topOverlay);
                return true;
            }
        }

        headerCollapseOverlay = null;
        headerCollapseMoved = false;
        headerCollapseStartBounds = null;

        bringToFront(topOverlay);
        syncOverlayLayerInput();
        OperationalOverlayComponent topComponent = adapterFor(topOverlay);
        var topHitBounds = topComponent.hitBounds();
        if (topHitBounds == null || !topHitBounds.contains((int) mouseX, (int) mouseY)) {
            topComponent.mouseClicked((int) mouseX, (int) mouseY, button);
        } else {
            overlayInput.mouseClicked((int) mouseX, (int) mouseY, button);
        }
        captureTextFieldFocus(topOverlay);
        return true;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        if (PackHideState.isActive()) return false;
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        boolean wasDraggingOrResizing = (draggingOverlay != null || resizingOverlay != null);
        IAutismOverlay prevDragging = draggingOverlay;
        IAutismOverlay prevResizing = resizingOverlay;
        boolean shouldToggleHeaderCollapse = button == 0
            && prevDragging != null
            && prevDragging == headerCollapseOverlay
            && prevDragging.usesSharedHeaderClickCollapse()
            && !headerCollapseMoved;
        AutismWindowLayout headerStartBounds = headerCollapseStartBounds;
        if (button == 0) {

            if (prevDragging != null && prevDragging.getOverlayId() != null && dragStartBounds != null) {

                if (!samePlacement(prevDragging.getBounds(), dragStartBounds)) {
                    clampedAwayBounds.remove(prevDragging.getOverlayId());
                }
            }
            if (prevResizing != null && prevResizing.getOverlayId() != null) {
                clampedAwayBounds.remove(prevResizing.getOverlayId());
            }
            draggingOverlay = null;
            dragStartBounds = null;
            if (resizingOverlay != null) resizingOverlay.saveLayout();
            resizingOverlay = null;
            resizeStartBounds = null;
            headerCollapseOverlay = null;
            headerCollapseMoved = false;
            headerCollapseStartBounds = null;
        }

        if (prevDragging != null) adapterFor(prevDragging).mouseReleased((int) mouseX, (int) mouseY, button);
        if (prevResizing != null && prevResizing != prevDragging) adapterFor(prevResizing).mouseReleased((int) mouseX, (int) mouseY, button);

        if (shouldToggleHeaderCollapse && isOverlayInteractive(prevDragging)) {
            if (headerStartBounds != null) {
                AutismWindowLayout current = prevDragging.getBounds();
                prevDragging.setBounds(new AutismWindowLayout(
                    headerStartBounds.x,
                    headerStartBounds.y,
                    current.width,
                    current.height,
                    current.visible,
                    current.collapsed
                ));
            }
            prevDragging.toggleCollapsed();
            prevDragging.saveLayout();
            invalidateHoverBlockCache();
            return true;
        }

        if (wasDraggingOrResizing) return true;

        if (inventoryMouseDown) {
            inventoryMouseDown = false;
            return false;
        }

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                if (adapterFor(overlay).mouseReleased((int) mouseX, (int) mouseY, button) == UiInputResult.HANDLED) {
                    return true;
                }
            }
        }

        return isMouseOverAnyOverlayVirtual(mouseX, mouseY);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (PackHideState.isActive()) return false;
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        deltaX = AutismUiScale.toVirtual(deltaX);
        deltaY = AutismUiScale.toVirtual(deltaY);
        if (resizingOverlay != null && resizeStartBounds != null && isOverlayInteractive(resizingOverlay)) {
            AutismWindowLayout current = resizingOverlay.getBounds();
            AutismWindowLayout resized = new AutismWindowLayout(
                resizeStartBounds.x,
                resizeStartBounds.y,
                Math.max(resizingOverlay.getMinWidth(), resizeStartBounds.width + (int) Math.round(mouseX - resizeStartMouseX)),
                Math.max(resizingOverlay.getMinHeight(), resizeStartBounds.height + (int) Math.round(mouseY - resizeStartMouseY)),
                current.visible,
                current.collapsed
            );
            resizingOverlay.setBounds(resized);
            invalidateHoverBlockCache();
            return true;
        }

        if (isOverlayInteractive(draggingOverlay)) {
            if (draggingOverlay == headerCollapseOverlay && !headerCollapseMoved) {
                if (Math.abs(mouseX - headerCollapseStartMouseX) >= HEADER_CLICK_DRAG_THRESHOLD
                    || Math.abs(mouseY - headerCollapseStartMouseY) >= HEADER_CLICK_DRAG_THRESHOLD) {
                    headerCollapseMoved = true;
                }
            }
            boolean handled = adapterFor(draggingOverlay).mouseDragged((int) mouseX, (int) mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED;
            invalidateHoverBlockCache();
            return handled;
        }

        if (inventoryMouseDown) return false;

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                if (adapterFor(overlay).mouseDragged((int) mouseX, (int) mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED) {
                    return true;
                }
            }
        }

        return isMouseOverAnyOverlayVirtual(mouseX, mouseY);
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double amount) {
        if (PackHideState.isActive()) return false;
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        IAutismOverlay topOverlay = getTopmostOverlayAt(mouseX, mouseY);
        if (topOverlay == null) return false;
        bringToFront(topOverlay);
        syncOverlayLayerInput();
        OperationalOverlayComponent topComponent = adapterFor(topOverlay);
        var topHitBounds = topComponent.hitBounds();
        if (topHitBounds == null || !topHitBounds.contains((int) mouseX, (int) mouseY)) {
            topComponent.mouseScrolled((int) mouseX, (int) mouseY, amount);
        } else {
            overlayInput.mouseScrolled((int) mouseX, (int) mouseY, amount);
        }
        return true;
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (PackHideState.isActive()) return false;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.wantsKeyboardCapture()) {
                if (adapterFor(overlay).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
                    bringToFront(overlay);
                    captureTextFieldFocus(overlay);
                    return true;
                }
            }
        }

        IAutismOverlay focusedTextOverlay = getTextFieldFocusOverlay();
        if (focusedTextOverlay != null) {
            adapterFor(focusedTextOverlay).keyPressed(keyCode, scanCode, modifiers);
            captureTextFieldFocus(focusedTextOverlay);
            focusedOverlay = focusedTextOverlay;
            AutismSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
        }

        IAutismOverlay keyboardTarget = getKeyboardTargetOverlay();
        if (keyboardTarget != null && adapterFor(keyboardTarget).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
            captureTextFieldFocus(keyboardTarget);
            return true;
        }

        return isAnyTextFieldFocused();
    }

    public boolean handleCharTyped(char chr, int modifiers) {
        if (PackHideState.isActive()) return false;
        IAutismOverlay focusedTextOverlay = getTextFieldFocusOverlay();
        if (focusedTextOverlay != null) {
            adapterFor(focusedTextOverlay).charTyped(chr, modifiers);
            captureTextFieldFocus(focusedTextOverlay);
            focusedOverlay = focusedTextOverlay;
            AutismSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
        }

        IAutismOverlay keyboardTarget = getKeyboardTargetOverlay();
        if (keyboardTarget != null && adapterFor(keyboardTarget).charTyped(chr, modifiers) == UiInputResult.HANDLED) {
            captureTextFieldFocus(keyboardTarget);
            return true;
        }
        return isAnyTextFieldFocused();
    }

    private IAutismOverlay getTextFieldFocusOverlay() {
        if (!textFieldFocusDirty) {
            IAutismOverlay cached = textFieldFocusOverlay;
            if (cached != null && isOverlayInteractive(cached) && cached.hasTextFieldFocused()) return cached;

        }
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
                textFieldFocusOverlay = overlay;
                textFieldFocusDirty = false;
                return overlay;
            }
        }
        textFieldFocusOverlay = null;
        textFieldFocusDirty = false;
        return null;
    }

    private void captureTextFieldFocus(IAutismOverlay overlay) {
        textFieldFocusOverlay = isOverlayInteractive(overlay) && overlay.hasTextFieldFocused() ? overlay : null;
        textFieldFocusDirty = false;
    }

    private IAutismOverlay getKeyboardTargetOverlay() {
        if (isOverlayInteractive(focusedOverlay)) {
            return focusedOverlay;
        }

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAutismOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                return overlay;
            }
        }

        return null;
    }

    public boolean isAnyTextFieldFocused() {
        if (PackHideState.isActive()) return false;
        return getTextFieldFocusOverlay() != null;
    }

    public void clearTextFieldFocus() {
        clearFocusedTextFields();
        focusedOverlay = null;
        AutismSharedState.get().setFocusedOverlayId("");
    }

    private OperationalOverlayComponent adapterFor(IAutismOverlay overlay) {
        return overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
    }

    private void syncOverlayLayerInput() {
        for (IAutismOverlay overlay : overlays) {
            adapterFor(overlay).setInputSuppressed(!isOverlayInteractive(overlay));
        }
        rebuildOverlayLayers();
    }

    private void rebuildOverlayLayers() {
        if (!overlayLayersDirty) return;
        overlayLayers.clear();
        for (IAutismOverlay overlay : overlays) {
            overlayLayers.add(isTransientOverlay(overlay) ? UiLayer.DROPDOWN : UiLayer.FLOATING, adapterFor(overlay));
        }
        overlayLayersDirty = false;
    }
}
