package autismclient.mixin;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import autismclient.modules.AutismModule;
import autismclient.modules.InventoryTweaksModule;
import autismclient.modules.NameCensorModule;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.gui.vanillaui.components.Banner;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismColors;
import autismclient.util.AutismCustomFilterOverlay;
import autismclient.util.AutismCustomFilterPresetOverlay;
import autismclient.util.AutismFabricatorOverlay;
import autismclient.util.AutismFabricatorRegistry;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismLANSyncOverlay;
import autismclient.util.AutismLauncherOverlay;
import autismclient.util.AutismMacroListOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.IAutismOverlay;
import autismclient.util.AutismInventoryMoveHelper;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismItemNbtInspectOverlay;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismShulkerPreview;
import autismclient.util.AutismText;
import autismclient.util.AutismUiScale;

import autismclient.util.AutismSharedState;
import autismclient.util.AutismCursorClickHelper;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Shadow;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin(AbstractContainerScreen.class)
public abstract class AutismHandledScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow @Nullable protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput actionType);
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private Slot autism$blockedFocusedSlot;

    @Unique private AutismLauncherOverlay launcherOverlay;
    @Unique private AutismFabricatorOverlay fabricatorOverlay;
    @Unique private AutismLANSyncOverlay lanSyncOverlay;
    @Unique private AutismMacroListOverlay macroListOverlay;
    @Unique private AutismQueueEditorOverlay queueEditorOverlay;
    @Unique private AutismPacketLoggerOverlay packetLoggerOverlay;
    @Unique private AutismCustomFilterOverlay customFilterOverlay;
    @Unique private AutismCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private AutismMacroEditorOverlay macroEditorOverlay;
    @Unique private AutismItemNbtInspectOverlay itemNbtInspectOverlay;
    @Unique private autismclient.util.AutismKeybindOverlay keybindOverlay;
    @Unique private autismclient.util.AutismServerInfoOverlay serverInfoOverlay;
    @Unique private autismclient.util.AutismMatchmakingOverlay matchmakingOverlay;
    @Unique private autismclient.util.AutismProfilesOverlay profilesOverlay;
    @Unique private Button inventoryTweaksStealButton;
    @Unique private Button inventoryTweaksDumpButton;
    @Unique private int inventoryTweaksLastShiftDragSlot = -1;
    @Unique private ItemStack autism$cursorClickBeforeCarried = ItemStack.EMPTY;
    @Unique private ItemStack autism$cursorClickBeforeSlot = ItemStack.EMPTY;
    @Unique private int autism$cursorClickBeforeSlotId = -1;
    @Unique private int autism$cursorClickBeforeButton = 0;
    @Unique private ContainerInput autism$cursorClickBeforeInput = null;

    protected AutismHandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autism$syncInvMoveOnInit(CallbackInfo ci) {
        if (!isAutismActive()) return;
        AutismInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void autism$syncInvMoveOnTick(CallbackInfo ci) {
        if (!isAutismActive()) return;
        AutismInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 1,
        require = 0
    )
    private Component autism$censorContainerLabel(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$init(CallbackInfo ci) {

        Screen screen = (Screen)(Object)this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (!isAutismActive()) return;
            try {
                AutismOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
                AutismUiScale.pushOverlayScale(drawContext);
                try {
                    renderMacroCaptureBanner(drawContext);
                } finally {
                    AutismUiScale.popOverlayScale(drawContext);
                }
                autism$renderShulkerPreview(drawContext, mouseX, mouseY);
            } catch (Throwable t) {
                autism$logOverlayError(t);
            }
        });
        try {
            long perfStart = autismclient.util.AutismPerf.begin();
            autism$buildInventoryOverlays();
            autismclient.util.AutismPerf.endSpike("mixin.buildInventoryOverlays", perfStart, 10_000_000L);
        } catch (Throwable t) {
            autism$logOverlayError(t);
        }
    }

    @Unique
    private void autism$buildInventoryOverlays() {
        AutismLANSync.getInstance().setOnSessionStateChanged(() -> {});

        AbstractContainerScreen<?> handledScreen = (AbstractContainerScreen<?>) (Object) this;
        fabricatorOverlay = AutismFabricatorOverlay.getSharedOverlay(handledScreen);
        lanSyncOverlay = AutismLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new AutismMacroListOverlay(this.font);
        queueEditorOverlay = new AutismQueueEditorOverlay(this.font);
        customFilterOverlay = new AutismCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();
        fabricatorOverlay.restoreState();
        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) {
            customFilterPresetOverlay.restoreLayout();
        }

        macroEditorOverlay = AutismMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) {
            macroEditorOverlay.restoreState();
        }

        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.clear();
        manager.register(fabricatorOverlay);
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) {
            manager.register(customFilterPresetOverlay);
        }
        if (macroEditorOverlay != null) {
            manager.register(macroEditorOverlay);
        }

        manager.register(autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        itemNbtInspectOverlay = AutismItemNbtInspectOverlay.getSharedOverlay(this.font);
        if (itemNbtInspectOverlay != null) manager.register(itemNbtInspectOverlay);

        AutismModule autismModule = AutismModule.get();
        autismclient.util.AutismMultiOverlay multiOverlay = autismModule == null ? null : autismModule.getMultiOverlayIfExists();
        if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        keybindOverlay = new autismclient.util.AutismKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new AutismLauncherOverlay(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && autismModule != null) {
                packetLoggerOverlay = autismModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreState();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = autismclient.modules.AutismModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && autismclient.util.AutismPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = autismclient.modules.AutismModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && autismclient.util.AutismServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = autismclient.modules.AutismModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }

        matchmakingOverlay = autismclient.modules.AutismModule.get().getMatchmakingOverlay();
        if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) {
            manager.register(matchmakingOverlay);
        }

        profilesOverlay = autismclient.modules.AutismModule.get().getProfilesOverlay();
        if (profilesOverlay != null && profilesOverlay.isVisible()) {
            manager.register(profilesOverlay);
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        inventoryTweaksStealButton = Button.builder(Component.literal("Steal"), button -> InventoryTweaksModule.stealFromButton())
                .bounds(leftPos, topPos - 22, 40, 20)
                .build();
        inventoryTweaksDumpButton = Button.builder(Component.literal("Dump"), button -> InventoryTweaksModule.dumpFromButton())
                .bounds(leftPos + 42, topPos - 22, 40, 20)
                .build();
        addRenderableWidget(inventoryTweaksStealButton);
        addRenderableWidget(inventoryTweaksDumpButton);

        refreshButtonVisibility();
    }

    @Unique private static long autism$lastOverlayErrorMs;

    @Unique
    private void autism$logOverlayError(Throwable t) {
        long now = System.currentTimeMillis();
        if (now - autism$lastOverlayErrorMs < 5000L) return;
        autism$lastOverlayErrorMs = now;
        autismclient.AutismClientAddon.LOG.warn("[Autism] in-screen overlay render failed; isolated to protect the UI", t);
    }

    @Unique private boolean coreExpanded = true;
    @Unique private boolean queueExpanded = false;
    @Unique private boolean toolsExpanded = false;
    @Unique private int coreButtonsStartY, queueHeaderY, queueButtonsStartY, queueButtonsEndY;
    @Unique private int toolsHeaderY, toolsButtonsStartY, toolsButtonsEndY;

    @Unique
    private void refreshButtonVisibility() {
        boolean visible = isAutismActive() && MC != null && MC.player != null
                && InventoryTweaksModule.shouldShowButtons(MC.player.containerMenu);
        if (inventoryTweaksStealButton != null) {
            inventoryTweaksStealButton.visible = visible;
            inventoryTweaksStealButton.active = visible;
            inventoryTweaksStealButton.setX(leftPos);
            inventoryTweaksStealButton.setY(topPos - 22);
        }
        if (inventoryTweaksDumpButton != null) {
            inventoryTweaksDumpButton.visible = visible;
            inventoryTweaksDumpButton.active = visible;
            inventoryTweaksDumpButton.setX(leftPos + 42);
            inventoryTweaksDumpButton.setY(topPos - 22);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void yang$blockCoveredSlotHover(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            autism$blockedFocusedSlot = hoveredSlot;
            hoveredSlot = null;
        } else {
            autism$blockedFocusedSlot = null;
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$blockCoveredSlotLookup(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$blockCoveredSlotHitbox(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(pointX, pointY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$blockCoveredHandledTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void yang$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (autism$blockedFocusedSlot != null) {
            hoveredSlot = autism$blockedFocusedSlot;
            autism$blockedFocusedSlot = null;
        }
        refreshButtonVisibility();
    }

    @Unique
    private void renderMacroCaptureBanner(GuiGraphicsExtractor context) {
        autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        autismclient.util.AutismAdminToolsOverlay adminToolsOverlay =
                autismclient.util.AutismAdminToolsOverlay.getSharedOverlayIfExists();
        boolean macroCapture = macroEditorOverlay != null && macroEditorOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        boolean actionCapture = actionEditor != null && actionEditor.shouldRenderAbstractContainerScreenCaptureBanner();
        boolean adminCapture = adminToolsOverlay != null && adminToolsOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        if (!macroCapture && !actionCapture && !adminCapture) return;
        if (MC == null || MC.getWindow() == null || this.font == null) return;

        String title = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureTitle()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureTitle()
                : adminToolsOverlay.getAbstractContainerScreenCaptureTitle();
        String instruction = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureInstruction()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureInstruction()
                : adminToolsOverlay.getAbstractContainerScreenCaptureInstruction();
        String hover = "";

        if (hoveredSlot != null) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            String itemName = stack.isEmpty() ? "" : stack.getHoverName().getString();
            String registryId = stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            hover = macroCapture
                    ? macroEditorOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : actionCapture
                    ? actionEditor.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : adminToolsOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId);
        }

        UiTextRenderer text = UiContexts.textRenderer(this.font);
        int maxTextWidth = Math.max(text.width(title), text.width(instruction));
        if (!hover.isEmpty()) {
            maxTextWidth = Math.max(maxTextWidth, text.width(hover));
        }

        int screenWidth = AutismUiScale.getVirtualScreenWidth();
        int boxWidth = Math.min(screenWidth - 16, Math.max(250, maxTextWidth + 18));
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = 0;
        var uiContext = UiContexts.overlay(context, this.font, 0, 0);
        int bannerHeight = Banner.height(uiContext, boxWidth, instruction, hover);
        Banner.render(uiContext, UiBounds.of(boxX, boxY, boxWidth, bannerHeight),
            title, instruction, hover);
        if (actionCapture && actionEditor != null && actionEditor.hasAbstractContainerScreenCaptureToasts()) {
            actionEditor.renderAbstractContainerScreenCaptureToasts(context, boxX, boxY + bannerHeight + 6, boxWidth);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void yang$removed(CallbackInfo ci) {
        if (!isAutismActive()) return;
        AutismInventoryMoveHelper.releaseMovementKeysIfSafe();

        autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        boolean skipTransientCaptureSave = actionEditor != null && actionEditor.hasActiveCaptureSession();

        long perfStart = autismclient.util.AutismPerf.begin();
        if (!skipTransientCaptureSave) {
            if (fabricatorOverlay != null) {
                fabricatorOverlay.saveState();
            }
            if (lanSyncOverlay != null) {
                lanSyncOverlay.saveState();
            }
            if (macroListOverlay != null) {
                macroListOverlay.saveState();
            }
            if (queueEditorOverlay != null) {
                queueEditorOverlay.saveState();
            }
            if (macroEditorOverlay != null) {
                macroEditorOverlay.saveState();
            }
            if (launcherOverlay != null) {
                launcherOverlay.saveLayout();
            }
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.saveState();
            }
            if (customFilterOverlay != null) {
                customFilterOverlay.saveLayout();
            }
            if (customFilterPresetOverlay != null) {
                customFilterPresetOverlay.saveLayout();
            }
            if (keybindOverlay != null) {
                keybindOverlay.saveLayout();
            }
            if (serverInfoOverlay != null) {
                serverInfoOverlay.saveState();
            }
            if (matchmakingOverlay != null) {
                matchmakingOverlay.saveLayout();
            }
            if (profilesOverlay != null) {
                profilesOverlay.saveLayout();
            }
        }
        autismclient.util.AutismPerf.endSpike("mixin.removedSaveState", perfStart, 10_000_000L);

        AutismOverlayManager.get().clear();
    }

    @Unique
    private void autism$renderShulkerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (hoveredSlot == null) return;
        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) return;
        ItemStack stack = hoveredSlot.getItem();
        if (!AutismShulkerPreview.shouldPreview(stack)) return;

        context.nextStratum();
        AutismShulkerPreview.render(context, this.font, stack, hoveredSlot.index, mouseX, mouseY, this.width, this.height);
    }

    @Unique
    private boolean isAutismActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private void updateButtonLabels() {
    }

    @Unique
    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Unique
    private static String stateText(boolean value) {
        return value ? "enabled" : "disabled";
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yang$mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        AutismOverlayManager manager = AutismOverlayManager.get();

        if (manager.handleMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortMouse(button, hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (button == 1 && hoveredSlot != null && click.hasControlDown() && click.hasShiftDown()) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                if (itemNbtInspectOverlay == null) {
                    itemNbtInspectOverlay = new AutismItemNbtInspectOverlay(this.font);
                    manager.register(itemNbtInspectOverlay);
                }
                itemNbtInspectOverlay.open(stack, (int) Math.round(mouseX + 8), (int) Math.round(mouseY + 8));
                cir.setReturnValue(true);
                return;
            }
        }

        if (button == 1 && hoveredSlot != null) {
            net.minecraft.world.item.ItemStack captureStack = hoveredSlot.getItem();
            String captureItemName   = captureStack.isEmpty() ? "" : captureStack.getHoverName().getString();
            String captureRegistryId = captureStack.isEmpty() ? "" :
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(captureStack.getItem()).toString();

            AutismMacroEditorOverlay editor = macroEditorOverlay;
            if (editor != null && editor.wantsSlotCapture()
                    && editor.onSlotRightClick(hoveredSlot, captureItemName, captureRegistryId)) {
                cir.setReturnValue(true);
                return;
            }

            boolean captureSlotNumber = click.hasControlDown() && !click.hasShiftDown();
            autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.wantsItemSlotCapture()
                    && actionEditor.onInventorySlotCapture(hoveredSlot, captureItemName, captureRegistryId, captureSlotNumber)) {
                cir.setReturnValue(true);
                return;
            }

            autismclient.util.AutismAdminToolsOverlay adminToolsOverlay =
                    autismclient.util.AutismAdminToolsOverlay.getSharedOverlay();
            if (adminToolsOverlay != null && adminToolsOverlay.wantsItemStackCapture()
                    && adminToolsOverlay.onInventoryItemStackCapture(hoveredSlot)) {
                cir.setReturnValue(true);
                return;
            }

            if (autismclient.util.AutismContainerHold.hasPendingCapture()) {
                autismclient.util.AutismPacketClick.Target captured =
                        autismclient$buildPacketClickTarget(hoveredSlot, captureItemName);
                if (captured != null && autismclient.util.AutismContainerHold.deliverCapture(captured)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (fabricatorOverlay != null && fabricatorOverlay.isVisible() && button == 1 && hoveredSlot != null) {
            fabricatorOverlay.onSlotClick(hoveredSlot, button);
            cir.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private autismclient.util.AutismPacketClick.Target autismclient$buildPacketClickTarget(
            net.minecraft.world.inventory.Slot slot, String itemName) {
        if (MC == null || MC.player == null || slot == null) return null;
        net.minecraft.world.inventory.AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null) return null;
        net.minecraft.client.gui.screens.Screen screen = MC.gui.screen();
        String screenTitle = (screen != null && screen.getTitle() != null) ? screen.getTitle().getString() : "";
        String menuClass = handler.getClass().getName();
        int visibleSlot = autismclient.util.AutismInventoryHelper.toUserVisibleSlot(MC, slot.index);
        return new autismclient.util.AutismPacketClick.Target(
                handler.containerId,
                handler.getStateId(),
                slot.index,
                visibleSlot,
                screenTitle,
                menuClass,
                itemName == null ? "" : itemName,
                autismclient.util.AutismPacketClick.Mode.RIGHT_CLICK,
                System.currentTimeMillis()
        );
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        boolean inventoryKey = MC != null && MC.options != null && MC.options.keyInventory.matches(input);
        if (inventoryKey) {
            if (AutismSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortKey(input.key(), hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (AutismSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
            }
        }

        if (AutismInventoryMoveHelper.handleKeyEvent(input, true)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void yang$keyReleased(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        if (AutismInventoryMoveHelper.handleKeyEvent(input, false)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void yang$mouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;
        inventoryTweaksLastShiftDragSlot = -1;

        if (AutismOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), require = 0)
    private void autism$captureCursorClickOriginBefore(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        autism$cursorClickBeforeCarried = ItemStack.EMPTY;
        autism$cursorClickBeforeSlot = ItemStack.EMPTY;
        autism$cursorClickBeforeSlotId = slotId;
        autism$cursorClickBeforeButton = button;
        autism$cursorClickBeforeInput = actionType;
        if (MC.player == null || MC.player.containerMenu == null) return;
        AbstractContainerMenu handler = MC.player.containerMenu;
        autism$cursorClickBeforeCarried = handler.getCarried().copy();
        if (slotId >= 0 && slotId < handler.slots.size()) {
            autism$cursorClickBeforeSlot = handler.slots.get(slotId).getItem().copy();
        }
    }

    @Inject(method = "slotClicked", at = @At("TAIL"), require = 0)
    private void autism$captureCursorClickOriginAfter(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (MC.player == null || MC.player.containerMenu == null) return;
        if (slotId != autism$cursorClickBeforeSlotId || button != autism$cursorClickBeforeButton || actionType != autism$cursorClickBeforeInput) return;
        AutismCursorClickHelper.recordAfterContainerClick(
                MC,
                MC.player.containerMenu,
                slotId,
                button,
                actionType,
                autism$cursorClickBeforeCarried,
                autism$cursorClickBeforeSlot
        );
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void yang$mouseDragged(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.hasShiftDown()
                && hoveredSlot != null && !hoveredSlot.getItem().isEmpty()
                && InventoryTweaksModule.shouldShiftDragMove()
                && inventoryTweaksLastShiftDragSlot != hoveredSlot.index) {
            inventoryTweaksLastShiftDragSlot = hoveredSlot.index;
            slotClicked(hoveredSlot, hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void yang$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!isAutismActive()) return;

        if (AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        if (!isAutismActive()) return super.charTyped(input);

        if (AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }

        return super.charTyped(input);
    }
}
