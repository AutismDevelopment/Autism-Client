package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.SearchableSelector;
import autismclient.gui.vanillaui.components.CompactWindow;
import autismclient.gui.vanillaui.components.CompactSymbolButton;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.vanillaui.components.Tooltip;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.util.macro.ClickAction;
import autismclient.util.macro.CloseGuiAction;
import autismclient.util.macro.AssertAction;
import autismclient.util.macro.BranchAction;
import autismclient.util.macro.ContainerClickSequenceAction;
import autismclient.util.macro.CraftAction;
import autismclient.util.macro.CustomMenuAction;
import autismclient.util.macro.PickUpAllAction;
import autismclient.util.macro.DelayAction;
import autismclient.util.macro.DelayPacketsAction;
import autismclient.util.macro.FpsAction;
import autismclient.util.macro.SPingAction;
import autismclient.util.macro.IfAction;
import autismclient.util.macro.FlowAction;
import autismclient.util.macro.LabelAction;
import autismclient.util.macro.DesyncAction;
import autismclient.util.macro.DisconnectAction;
import autismclient.util.macro.DropAction;
import autismclient.util.macro.EndPacketGateAction;
import autismclient.util.macro.FakeGamemodeAction;
import autismclient.util.macro.GoToAction;
import autismclient.util.macro.InventoryAuditAction;
import autismclient.util.macro.InventoryAction;
import autismclient.util.macro.InstaBreakAction;
import autismclient.util.macro.BreakAction;
import autismclient.util.macro.BundleDupeV2Action;
import autismclient.util.macro.RollbackAction;
import autismclient.util.macro.ItemAction;
import autismclient.util.macro.JumpAction;
import autismclient.util.macro.LookAtBlockAction;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;
import autismclient.util.macro.MacroVariablesAction;
import autismclient.util.macro.CaptureValueAction;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.MineAction;
import autismclient.util.macro.MoveAction;
import autismclient.util.macro.MultiAction;
import autismclient.util.macro.NbtBookAction;
import autismclient.util.macro.OpenContainerAction;
import autismclient.util.macro.PayAction;
import autismclient.util.macro.PacketBurstAction;
import autismclient.util.macro.PacketGateAction;
import autismclient.util.macro.PayloadAction;
import autismclient.util.macro.PlaceAction;
import autismclient.util.macro.RaceAction;
import autismclient.util.macro.RepeatAction;
import autismclient.util.macro.ReportAction;
import autismclient.util.macro.RestoreGuiAction;
import autismclient.util.macro.RevisionSyncAction;
import autismclient.util.macro.RotateAction;
import autismclient.util.macro.SaveGuiAction;
import autismclient.util.macro.SelectSlotAction;
import autismclient.util.macro.SendChatAction;
import autismclient.util.macro.SendCommandPacketAction;
import autismclient.util.macro.SendPacketAction;
import autismclient.util.macro.SendToggleAction;
import autismclient.util.macro.ServerTickSyncAction;
import autismclient.util.macro.SneakAction;
import autismclient.util.macro.SprintAction;
import autismclient.util.macro.StartMacroAction;
import autismclient.util.macro.StopMacroAction;
import autismclient.util.macro.StoreItemAction;
import autismclient.util.macro.SwapSlotsAction;
import autismclient.util.macro.FinallyAction;
import autismclient.util.macro.SignEditAction;
import autismclient.util.macro.TickSyncAction;
import autismclient.util.macro.ToggleModuleAction;
import autismclient.util.macro.UseItemAction;
import autismclient.util.macro.UseItemPhaseAction;
import autismclient.util.macro.WaitForBlockAction;
import autismclient.util.macro.WaitForChatAction;
import autismclient.util.macro.WaitForCooldownAction;
import autismclient.util.macro.WaitForEntityAction;
import autismclient.util.macro.WaitForGuiAction;
import autismclient.util.macro.WaitGamemodeChangeAction;
import autismclient.util.macro.WaitForHealthAction;
import autismclient.util.macro.WaitForLanStepAction;
import autismclient.util.macro.WaitForMacroStepAction;
import autismclient.util.macro.WaitForPacketAction;
import autismclient.util.macro.WaitForPositionDeltaAction;
import autismclient.util.macro.WaitForSlotChangeAction;
import autismclient.util.macro.WaitForSoundAction;
import autismclient.util.macro.WaitForTeleportAction;
import autismclient.util.macro.WaitMovementAction;
import autismclient.util.macro.WaitForWorldChangeAction;
import autismclient.util.macro.WaitEntityTargetAction;
import autismclient.util.macro.WaitGuiTypeAction;
import autismclient.util.macro.WaitDurabilityAction;
import autismclient.util.macro.WaitFreeSlotsAction;
import autismclient.util.macro.WaitInventoryPredicateAction;
import autismclient.util.macro.WaitPacketMatchAction;
import autismclient.util.macro.WaitPosAction;
import autismclient.util.macro.HClipAction;
import autismclient.util.macro.PacedTpAction;
import autismclient.util.macro.VClipAction;
import autismclient.util.macro.XCarryAction;
import autismclient.gui.macro.editor.ActionEditorOverlay;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

public class AutismMacroEditorOverlay extends AutismOverlayBase {
   public enum EditorContext {
      NORMAL,
      JOIN_MACRO_MENU
   }

   private static final Minecraft MC = Minecraft.getInstance();
   private static AutismMacroEditorOverlay sharedOverlay;
   private static final int DEFAULT_PANEL_WIDTH = 252;
   private static final int HISTORY_LIMIT = 30;
   private static final int PACKUI_HEADER_CONTROL = 12;
   private static final int PACKUI_HEADER_ARROW_WIDTH = 10;
   private static final int PACKUI_HEADER_ARROW_GAP = 3;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private static final int STEP_ROW_CONTROL_SIZE = 12;
   private static final int STEP_ROW_CONTROL_GAP = 4;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Macro Editor");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final DirectViewportSlot shellBody = new DirectViewportSlot();
   private boolean visible = false;
   private AutismMacro macro;
   private boolean isNew = false;
   private boolean reopenMacroListOnClose = false;
   private EditorContext editorContext = EditorContext.NORMAL;
   private Consumer<AutismMacro> saveCompleteCallback = null;
   private int panelX;
   private int panelY;
   private int PANEL_WIDTH = 258;
   private int PANEL_HEIGHT = 308;
   private List<String> btnLabels = new ArrayList<>();
   private List<int[]> btnBounds = new ArrayList<>();
   private List<Runnable> btnActions = new ArrayList<>();
   private List<Runnable> btnSecondaryActions = new ArrayList<>();
   private List<Boolean> btnEnabled = new ArrayList<>();
   private int runBtnIndex = -1;
   private final java.util.List<CompactDropdown> uiDropdowns = new ArrayList<>();
   private AutismChatField nameField;
   private AutismChatField loopCountField;
   private boolean isBindingKey = false;
   private int loopMode = 0;
   private int scrollOffset = 0;
   private static final int ACTION_HEIGHT = 18;
   private static final float STEP_DRAG_THRESHOLD = 3.0F;
   private static final long STEP_EXACT_SELECT_DOUBLE_CLICK_MS = 350L;
   private static final int STEP_DRAG_AUTO_SCROLL_EDGE = 18;
   private static final int STEP_DRAG_AUTO_SCROLL_MAX_SPEED = 12;
   private int draggingIndex = -1;
   private int pressedStepIndex = -1;
   private int stepDragTargetIndex = -1;
   private int stepDragGrabOffsetY = 0;
   private int stepDragMouseY = 0;
   private double stepPressMouseX = 0.0;
   private double stepPressMouseY = 0.0;
   private boolean stepPressWasSelected = false;
   private boolean stepPressCanDrag = false;
   private int lastRightClickedStepStableId = -1;
   private long lastRightClickedStepAtMs = 0L;
   private boolean isWindowDragging = false;
   private boolean isWindowResizing = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private double headerPressMouseX = 0.0;
   private double headerPressMouseY = 0.0;
   private int headerPressPanelX = 0;
   private int headerPressPanelY = 0;
   private boolean headerDragMoved = false;
   private double resizeStartMouseX = 0.0;
   private double resizeStartMouseY = 0.0;
   private int resizeStartWidth = 0;
   private int resizeStartHeight = 0;
   private boolean collapsed = false;
   private static final int MOD_LIST_ROWS = 8;
   private static final int MOD_LIST_ROW_H = 14;
   private String lastModuleSearch = "";
   private List<String> entityRegistryCache = null;
   private AutismMacroEditorOverlay.StepPickerMode stepPickerMode = null;
   private List<AutismMacroEditorOverlay.StepPickerCategory> stepPickerCategories = Collections.emptyList();
   private List<AutismMacroEditorOverlay.StepPickerEntry> stepPickerEntries = Collections.emptyList();
   private Map<String, List<AutismMacroEditorOverlay.StepPickerEntry>> stepPickerEntriesByCategory = Collections.emptyMap();
   private final SearchableSelector<AutismMacroEditorOverlay.StepPickerEntry> stepPickerSelector =
      new SearchableSelector<>(entry -> entry.categoryId + "\n" + entry.label + "\n" + entry.description);
   private AutismChatField stepPickerSearchField;
   private final StepPickerOverlay stepPickerOverlay = new StepPickerOverlay();
   private int stepPickerScrollOffset = 0;

   private final java.util.HashMap<String, Integer> pickerLabelWidthCache = new java.util.HashMap<>();
   private long pickerLayoutEpoch = 0L;
   private long cachedPickerViewportEpoch = -1L;
   private int cachedPickerViewportScreenW = -1;
   private int cachedPickerViewportScreenH = -1;
   private int cachedPickerViewportWidth = -1;
   private int activeScrollbarDrag = 0;
   private int scrollbarGrabOffset = 0;
   private final AutismContextMenu<Integer> stepCtxMenu;
   private static final int SCROLLBAR_NONE = 0;
   private static final int SCROLLBAR_STEP_LIST = 1;
   private static final int SCROLLBAR_STEP_PICKER = 2;
   private static final float STEP_ROW_ENTER_SECONDS = 0.14F;
   private static final float STEP_ROW_MOVE_SECONDS = 0.14F;
   private static final float STEP_ROW_EXIT_SECONDS = 0.12F;
   private static final int STEP_ROW_ENTER_OFFSET = 18;
   private static final int STEP_ROW_EXIT_OFFSET = 20;
   private String hoveredTooltip = null;
   private int tooltipX;
   private int tooltipY;
   private final Deque<MacroHistoryState> undoHistory = new ArrayDeque<>();
   private final Deque<MacroHistoryState> redoHistory = new ArrayDeque<>();
   private AutismMacro originalMacro;
   private boolean tempSavedForRun = false;
   private boolean tempRunCreatedNewMacro = false;
   private CompoundTag originalMacroSnapshot = null;
   private boolean lastStepListLockedForRun = false;
   private boolean configurationOnly = false;
   private long currentRunId = -1L;
   private long runLockArmedAtMs = 0L;
   private static final long RUN_LOCK_GRACE_MS = 100L;
   private long cachedRunStateRevision = Long.MIN_VALUE;
   private long cachedRunStateRunId = Long.MIN_VALUE;
   private boolean cachedEditingRunningMacro = false;
   private boolean saveBtnDirty = false;
   private final IdentityHashMap<MacroAction, Integer> stepRowStableIds = new IdentityHashMap<>();
   private final StepSelection stepSelection = new StepSelection();
   private final List<AutismMacroEditorOverlay.StepRowSnapshot> lastStepRowSnapshots = new ArrayList<>();
   private final List<AutismMacroEditorOverlay.StepRowMotion> stepRowMotions = new ArrayList<>();
   private int nextStepRowStableId = 1;
   private long stepRowAnimationClockNanos = System.nanoTime();

   public static AutismMacroEditorOverlay getSharedOverlay() {
      if (sharedOverlay == null) {
         sharedOverlay = new AutismMacroEditorOverlay(Minecraft.getInstance().font);
         sharedOverlay.restoreLayout();
         AutismOverlayManager.get().register(sharedOverlay);
      }

      sharedOverlay.restorePosition();
      return sharedOverlay;
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      if (this.configurationOnly == configurationOnly) return;
      this.configurationOnly = configurationOnly;
      ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
      if (actionEditor != null) {
         actionEditor.setConfigurationOnly(configurationOnly);
         actionEditor.setWorldCaptureAllowed(this.allowsWorldCaptureActions());
      }
      if (this.visible) this.recreateComponents();
   }

   private CompactOverlayButton createOverlayButton(
      int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action
   ) {
      return this.createOverlayButton(x, y, w, h, label, variant, enabled, action, null);
   }

   private CompactOverlayButton createOverlayButton(
      int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action, Runnable secondaryAction
   ) {
      CompactOverlayButton button = CompactOverlayButton.create(x, y, w, h, Component.literal(label), ignored -> {
         if (action != null) {
            action.run();
         }
      }, secondaryAction == null ? null : ignored -> secondaryAction.run());
      button.setWidth(w);
      button.setVariant(variant);
      button.active = enabled;
      return button;
   }

   private void drawOverlayButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, int mouseX, int mouseY
   ) {
      CompactOverlayControls.action(context, this.textRenderer, x, y, w, h, label, variant, enabled, mouseX, mouseY);
   }

   private void drawOverlayToggleButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean toggleState, String animationKey, boolean enabled, int mouseX, int mouseY
   ) {
      CompactOverlayControls.toggle(context, this.textRenderer, x, y, w, h, label, toggleState, animationKey, enabled, mouseX, mouseY);
   }

   private void drawRunIconButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, boolean running, boolean enabled, int mouseX, int mouseY
   ) {
      boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
      Button.render(UiContexts.overlay(context, this.textRenderer, mouseX, mouseY), UiBounds.of(x, y, w, h), "",
         running ? Button.Tone.DANGER : Button.Tone.SUCCESS, hovered, true);
      int icon = 8;
      AutismUiIcons.blit(context, running ? AutismUiIcons.STOP : AutismUiIcons.PLAY,
         x + (w - icon) / 2, y + (h - icon) / 2, icon, enabled ? 0xF2FFFFFF : 0x66FFFFFF);
   }

   private void drawOverlayIconButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, net.minecraft.resources.Identifier iconId,
      CompactOverlayButton.Variant variant, boolean enabled, int mouseX, int mouseY
   ) {
      CompactOverlayControls.action(context, this.textRenderer, x, y, w, h, "", variant, enabled, mouseX, mouseY);
      int icon = 8;
      int gap = 3;
      int textW = UiText.width(this.textRenderer, label, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY));
      int groupW = icon + gap + textW;
      int startX = x + Math.max(2, (w - groupW) / 2);
      AutismUiIcons.blit(context, iconId, startX, y + (h - icon) / 2, icon, enabled ? 0xF2FFFFFF : 0x66FFFFFF);
      UiText.draw(context, this.textRenderer, label, this.theme.fontFor(UiTone.BODY),
         enabled ? this.theme.color(UiTone.BODY) : this.theme.color(UiTone.MUTED),
         startX + icon + gap, UiSizing.alignTextY(y, h, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge()), false);
   }

   public AutismMacroEditorOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.stepCtxMenu = new AutismContextMenu<>(this.theme, textRenderer, this::getStepCtxItems, 12);
      setContextMenu(this.stepCtxMenu);
      this.PANEL_WIDTH = this.defaultPanelWidth();
      this.PANEL_HEIGHT = this.defaultPanelHeight();
      this.windowNode.setCenterTitle(false);
      this.windowNode.setTitleTone(UiTone.LABEL);
      this.windowNode.setHeaderControls(true, true);
      this.windowNode.setTitleAreaInsets(this.titleLeftInset(), this.titleRightInset());
      this.windowNode.content().setGap(0).setPadding(DirectUiInsets.NONE);
      this.windowNode.content().add(this.shellBody);
   }

   private int getStepBuilderLabelY() {
      return this.getTopControlsBottomY() + this.stepBuilderLabelOffset();
   }

   private int getStepBuilderButtonY() {
      return this.getStepBuilderLabelY() + this.theme.lineHeight(UiTone.LABEL, 1) + this.stepBuilderLabelGap();
   }

   private int getStepListLabelY() {
      return this.getStepBuilderButtonY() + this.editorButtonHeight() + this.stepBuilderSectionGap();
   }

   private int getStepListY() {
      return this.getStepListLabelY() + this.theme.lineHeight(UiTone.LABEL, 1) + 1;
   }

   private int getStepListFrameX() {
      return this.panelX + 8;
   }

   private int getStepListFrameWidth() {
      return this.PANEL_WIDTH - 16;
   }

   private int getStepListContentLeft() {
      return this.getStepListFrameX() + 1;
    }

    private int getActionRowControlsWidth(MacroAction action) {
      return this.getActionRowControlsWidth(action, true, true, true);
   }

    private int getActionRowControlsWidth(MacroAction action, boolean allowDelete) {
      return this.getActionRowControlsWidth(action, true, true, allowDelete);
   }

    private int getActionRowControlsWidth(MacroAction action, boolean allowMove, boolean allowDuplicate, boolean allowDelete) {
      int count = 0;
      if (allowMove) count += 2;
      if (allowDuplicate) count++;
      if (action != null && this.hasActionEditor(action)) count++;
      if (allowDelete) count++;
      if (count <= 0) return 0;
      return count * this.stepRowControlSize() + (count - 1) * this.stepRowControlGap();
   }

   private int getActionRowControlsX(MacroAction action) {
      return this.getStepListContentRight() - this.getActionRowControlsWidth(action) - 1;
   }

   private int getActionRowControlsX(MacroAction action, boolean allowDelete) {
      return this.getStepListContentRight() - this.getActionRowControlsWidth(action, allowDelete) - 1;
   }

   private int getActionRowControlsX(MacroAction action, boolean allowMove, boolean allowDuplicate, boolean allowDelete) {
      int width = this.getActionRowControlsWidth(action, allowMove, allowDuplicate, allowDelete);
      return width <= 0 ? this.getStepListContentRight() : this.getStepListContentRight() - width - 1;
   }

   private int getFooterButtonsY() {
      return this.panelY + this.PANEL_HEIGHT - this.footerBottomInset();
   }

   private int getFooterTopY() {
      return this.getFooterButtonsY() - this.footerSectionGap();
   }

   private int getStepListAvailableHeight() {
      return Math.max(0, this.getFooterTopY() - this.getStepListY());
   }

   private int getStepListHeight() {
      return Math.max(0, this.getSteplistViewportHeight() + 2);
   }

   private int getSteplistViewportHeight() {
      return this.alignViewportHeight(Math.max(0, this.getStepListAvailableHeight() - 2), this.actionRowHeight());
   }

   private int getStepListClipTop() {
      return this.getStepListY() + 1;
   }

   private int getStepListClipBottom() {
      return this.getStepListClipTop() + this.getSteplistViewportHeight();
   }

   private int getMaxStepListScroll() {
      return this.macro == null ? 0 : Math.max(0, this.macro.actions.size() * this.actionRowHeight() - this.getSteplistViewportHeight());
   }

   private boolean isStepRowFullyVisible(int rowY) {
      return rowY >= this.getStepListY() && rowY + this.actionRowHeight() <= this.getStepListClipBottom();
   }

      private int defaultPanelWidth() {
      return 258;
   }

   private int defaultPanelHeight() {
      return 308;
   }

   private int minimumPanelHeight() {
      return 272;
   }

   private int titleLeftInset() {
      return 10;
   }

   private int titleRightInset() {
      return 39;
   }

   private int stepBuilderLabelOffset() {
      return 5;
   }

   private int stepBuilderLabelGap() {
      return 1;
   }

   private int stepBuilderSectionGap() {
      return 5;
   }

   private int stepRowControlSize() {
      return 13;
   }

   private int stepRowControlGap() {
      return 2;
   }

      private int stepListScrollbarGutter() {
         return 6;
      }

      private int getStepListContentRight() {
         return this.getStepListFrameX() + this.getStepListFrameWidth() - this.stepListScrollbarGutter() - 1;
      }

      private int getStepListContentRightForAction(MacroAction action) {
         int buttonsX = this.getActionRowControlsX(action);
         return buttonsX - 1;
      }

      private int getStepListContentRightForAction(MacroAction action, boolean allowDelete) {
         int buttonsX = this.getActionRowControlsX(action, allowDelete);
         return buttonsX - 1;
      }

      private int getStepListContentRightForAction(MacroAction action, boolean allowMove, boolean allowDuplicate, boolean allowDelete) {
         int width = this.getActionRowControlsWidth(action, allowMove, allowDuplicate, allowDelete);
         if (width <= 0) return this.getStepListContentRight();
         int buttonsX = this.getActionRowControlsX(action, allowMove, allowDuplicate, allowDelete);
         return buttonsX - 1;
      }

     private int getStepListScrollbarX() {
       return this.getStepListFrameX() + this.getStepListFrameWidth() - 4;
    }

    private CompactScrollbar.Metrics getStepListScrollbarMetrics() {
       this.clampStepListScrollOffset();
       int listHeight = this.getStepListHeight();
       int viewHeight = Math.max(1, this.getSteplistViewportHeight());
       return CompactScrollbar.compute(
          this.macro.actions.size() * this.actionRowHeight(),
          viewHeight,
          this.getStepListScrollbarX(),
          this.getStepListClipTop(),
          3,
          Math.max(1, listHeight - 2),
          this.scrollOffset
       );
    }

    private int contentInsetX() {
       return 8;
    }

   private int footerBottomInset() {
      return 16;
   }

   private int footerSectionGap() {
      return 12;
   }

   private int actionRowHeight() {
      return ACTION_HEIGHT;
   }

   private int defaultScreenInsetX() {
      return 16;
   }

   private int defaultScreenInsetY() {
      return 30;
   }

   private int editorButtonHeight() {
      return 14;
   }

   private int editorControlGap() {
      return 3;
   }

   private int deleteButtonWidth() {
      return 48;
   }

   private int textFieldHeight() {
      return 13;
   }

   private int secondRowGap() {
      return 15;
   }

   private int buttonTextPadding() {
      return 9;
   }

   private int runButtonWidth() {
      return 24;
   }

   private boolean povRunActive() {
      return autismclient.util.multi.MultiTakeoverState.isActive();
   }

   private String runButtonLabel(boolean localRunning) {
      return localRunning ? "Stop" : this.povRunActive() ? "Run on POV" : "Run";
   }

   private int lanButtonWidth() {
      return 28;
   }

   private int loopCountFieldWidth() {
      return 36;
   }

   private int loopCountFieldHeight() {
      return 13;
   }

   private int builderButtonGap() {
      return 2;
   }

   private int footerPrimaryButtonWidth() {
      return 49;
   }

   private int footerSecondaryButtonWidth() {
      return 44;
   }

   private int footerButtonGap() {
      return 3;
   }

   private int topRowOffset() {
      return 20;
   }

   private int getTopControlsBottomY() {
      return this.panelY + this.topRowOffset() + this.secondRowGap() + this.editorButtonHeight();
   }

   private int warningTextOffset() {
      return 32;
   }

   private int builderLabelTextOffset() {
      return 3;
   }

   private int stepsHeaderOffset() {
      return 9;
   }

   private int actionTextOffset() {
      return 7;
   }

   private int actionControlOffset() {
      return 5;
   }

   private int stepRowControlStride() {
      return this.stepRowControlSize() + this.stepRowControlGap();
   }

   private int stepRowTextY(int rowY) {
      return UiSizing.alignTextY(rowY, this.actionRowHeight(), this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
   }

   private int stepRowControlY(int rowY) {
      return UiSizing.alignMiddle(rowY, this.actionRowHeight(), this.stepRowControlSize());
   }

   private int stepRowStableId(MacroAction action) {
      if (action == null) {
         return -1;
      } else {
         Integer existing = this.stepRowStableIds.get(action);
         if (existing != null) {
            return existing;
         } else {
            int created = this.nextStepRowStableId++;
            this.stepRowStableIds.put(action, created);
            return created;
         }
      }
   }

   private StepUnit stepUnitForIndex(int index) {
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return StepUnit.EMPTY;
      MacroAction action = this.macro.actions.get(index);
      GroupRowInfo group = this.getGroupRowInfo(index, action);

      if (group.multi && !group.header) return new StepUnit(index, index + 1);
      int start = group.inGroup() ? group.headerIndex : index;
      int end = group.inGroup() ? group.headerIndex + group.childCount + 1 : index + 1;
      start = Math.max(0, Math.min(start, this.macro.actions.size()));
      end = Math.max(start, Math.min(end, this.macro.actions.size()));
      return new StepUnit(start, end);
   }

   private void setSelectedStepUnit(int index) {
      this.stepSelection.clear();
      this.addSelectedStepUnit(index);
   }

   private void toggleSelectedStepUnit(int index) {
      StepUnit unit = this.stepUnitForIndex(index);
      if (unit.isEmpty()) return;
      boolean allSelected = true;
      for (int i = unit.start; i < unit.endExclusive; i++) {
         if (!this.stepSelection.containsStableId(this.stepRowStableId(this.macro.actions.get(i)))) {
            allSelected = false;
            break;
         }
      }
      if (allSelected) {
         for (int i = unit.start; i < unit.endExclusive; i++) {
            this.stepSelection.removeStableId(this.stepRowStableId(this.macro.actions.get(i)));
         }
      } else {
         for (int i = unit.start; i < unit.endExclusive; i++) {
            this.stepSelection.addStableId(this.stepRowStableId(this.macro.actions.get(i)));
         }
      }
   }

   private void handlePlainStepClick(int index, boolean wasSelectedAtPress) {
      if (wasSelectedAtPress || this.isStepIndexSelected(index)) {
         this.toggleSelectedStepUnit(index);
      } else {
         this.setSelectedStepUnit(index);
      }
   }

   private void addSelectedStepUnit(int index) {
      StepUnit unit = this.stepUnitForIndex(index);
      if (unit.isEmpty() || this.macro == null) return;
      for (int i = unit.start; i < unit.endExclusive; i++) {
         this.stepSelection.addStableId(this.stepRowStableId(this.macro.actions.get(i)));
      }
   }

   private boolean isStepIndexSelected(int index) {
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return false;
      return this.stepSelection.containsStableId(this.stepRowStableId(this.macro.actions.get(index)));
   }

   private void clearStepSelection() {
      this.stepSelection.clear();
   }

   private List<StepUnit> selectedStepUnits() {
      List<StepUnit> ranges = new ArrayList<>();
      if (this.macro == null || this.stepSelection.isEmpty()) return ranges;
      for (int i = 0; i < this.macro.actions.size(); i++) {
         if (!this.isStepIndexSelected(i)) continue;
         StepUnit unit = this.stepUnitForIndex(i);
         if (!unit.isEmpty()) ranges.add(unit);
      }
      return mergeStepUnits(ranges);
   }

   private static List<StepUnit> mergeStepUnits(List<StepUnit> units) {
      if (units == null || units.isEmpty()) return List.of();
      List<StepUnit> sorted = new ArrayList<>(units);
      sorted.sort((a, b) -> Integer.compare(a.start, b.start));
      List<StepUnit> merged = new ArrayList<>();
      for (StepUnit unit : sorted) {
         if (unit == null || unit.isEmpty()) continue;
         if (merged.isEmpty()) {
            merged.add(unit);
            continue;
         }
         StepUnit previous = merged.get(merged.size() - 1);
         if (unit.start <= previous.endExclusive) {
            merged.set(merged.size() - 1, new StepUnit(previous.start, Math.max(previous.endExclusive, unit.endExclusive)));
         } else {
            merged.add(unit);
         }
      }
      return merged;
   }

   private List<MacroAction> selectedStepActionReferences() {
      List<MacroAction> out = new ArrayList<>();
      if (this.macro == null) return out;
      for (StepUnit unit : this.selectedStepUnits()) {
         for (int i = unit.start; i < unit.endExclusive && i < this.macro.actions.size(); i++) {
            out.add(this.macro.actions.get(i));
         }
      }
      return out;
   }

   private List<MacroAction> directlySelectedActionReferences() {
      List<MacroAction> out = new ArrayList<>();
      if (this.macro == null || this.stepSelection.isEmpty()) return out;
      for (MacroAction action : this.macro.actions) {
         if (this.stepSelection.containsStableId(this.stepRowStableId(action))) out.add(action);
      }
      return out;
   }

   private CompoundTag exactActionSettings(MacroAction action) {
      if (action == null) return null;
      try {
         return AutismMacro.serializeAction(action);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private boolean hasExactActionSettings(MacroAction action, CompoundTag expected) {
      CompoundTag actual = this.exactActionSettings(action);
      return actual != null && expected != null && actual.equals(expected);
   }

   private void selectAllExactlyMatchingActions(int index) {
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return;
      CompoundTag expected = this.exactActionSettings(this.macro.actions.get(index));
      if (expected == null) return;
      this.stepSelection.clear();
      for (MacroAction candidate : this.macro.actions) {
         if (this.hasExactActionSettings(candidate, expected)) {
            this.stepSelection.addStableId(this.stepRowStableId(candidate));
         }
      }
   }

   private void handleStepRightClick(int index, int mouseX, int mouseY) {
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return;
      int stableId = this.stepRowStableId(this.macro.actions.get(index));
      long now = System.currentTimeMillis();
      boolean doubleClick = stableId == this.lastRightClickedStepStableId
         && now - this.lastRightClickedStepAtMs >= 0L
         && now - this.lastRightClickedStepAtMs <= STEP_EXACT_SELECT_DOUBLE_CLICK_MS;
      if (doubleClick) {
         this.stepCtxMenu.close();
         this.selectAllExactlyMatchingActions(index);
         this.lastRightClickedStepStableId = -1;
         this.lastRightClickedStepAtMs = 0L;
      } else {
         if (!this.isStepIndexSelected(index)) this.setSelectedStepUnit(index);
         this.lastRightClickedStepStableId = stableId;
         this.lastRightClickedStepAtMs = now;
         this.stepCtxMenu.open(mouseX, mouseY, index);
      }
   }

   private String[] getStepCtxItems(Integer index) {
      if (index == null || this.macro == null || index < 0 || index >= this.macro.actions.size()) return new String[0];

      return new String[]{
         "Play From Here", "Play This Step",
         "Select Similar", "Delete Similar",
         "Copy", "Paste", "Duplicate", "Delete",
         "Undo", "Redo"
      };
   }

   private void executeStepCtxAction(int stepIndex, int itemIndex) {
      switch (itemIndex) {
         case 0 -> this.playFromStep(stepIndex, false);
         case 1 -> this.playFromStep(stepIndex, true);
         case 2 -> this.selectAllExactlyMatchingActions(stepIndex);
         case 3 -> {
            this.selectAllExactlyMatchingActions(stepIndex);
            this.deleteSelectedSteps();
         }
         case 4 -> this.copySelectedStepsToClipboard();
         case 5 -> this.pasteMacroStepsFromClipboardOrSelection();
         case 6 -> this.duplicateSelectedSteps();
         case 7 -> this.deleteSelectedSteps();
         case 8 -> this.performUndo();
         case 9 -> this.performRedo();
         default -> {
         }
      }
   }

   private void playFromStep(int startIndex, boolean singleStep) {
      if (this.macro == null || startIndex < 0 || startIndex >= this.macro.actions.size()) return;

      int endExclusive = this.macro.actions.size();
      if (singleStep) {
         GroupRowInfo group = this.getGroupRowInfo(startIndex, this.macro.actions.get(startIndex));
         endExclusive = group.inGroup() && group.header
            ? Math.min(this.macro.actions.size(), startIndex + 1 + group.childCount)
            : startIndex + 1;
      }

      for (int i = startIndex; i < endExclusive; i++) {
         if (this.macro.actions.get(i) instanceof InventoryAuditAction auditAction && !auditAction.hasValidTargetSelection()) {
            AutismClientMessaging.sendPrefixed("Inventory Audit needs at least one target.");
            return;
         }
      }

      this.stopEditorMacro();

      AutismMacro oneShot = new AutismMacro();
      oneShot.name = this.macro.name == null || this.macro.name.isBlank() ? "(Unsaved)" : this.macro.name;
      oneShot.loop = false;
      oneShot.loopCount = -1;
      for (int i = startIndex; i < endExclusive; i++) {
         MacroAction copy = AutismMacro.createActionFromTag(this.macro.actions.get(i).toTag());
         if (copy != null) oneShot.actions.add(copy);
      }
      if (oneShot.actions.isEmpty()) return;

      this.lockStepListForRun();
      this.currentRunId = oneShot.executeTracked();
      this.invalidateRunningMacroCache();
      if (this.currentRunId >= 0L) {
         this.runLockArmedAtMs = System.currentTimeMillis();
      }
      this.recreateComponents();
   }

   private boolean supportsBulkActionEdit(MacroAction action) {
      return !(action instanceof RaceAction)
         && !(action instanceof ReportAction)
         && !(action instanceof PacketGateAction)
         && !(action instanceof EndPacketGateAction);
   }

   private List<MacroAction> bulkEditTargets(MacroAction primary) {
      if (primary == null) return List.of();
      if (!this.isActionLive(primary) || !this.supportsBulkActionEdit(primary)) return List.of(primary);
      if (!this.stepSelection.containsStableId(this.stepRowStableId(primary))) return List.of(primary);
      List<MacroAction> selected = this.directlySelectedActionReferences();
      if (selected.size() <= 1) return List.of(primary);
      CompoundTag expected = this.exactActionSettings(primary);
      if (expected == null) return List.of(primary);
      for (MacroAction action : selected) {
         if (!this.supportsBulkActionEdit(action) || !this.hasExactActionSettings(action, expected)) return List.of(primary);
      }
      return List.copyOf(selected);
   }

   private boolean isActionLive(MacroAction action) {
      if (this.macro == null || action == null) return false;
      for (MacroAction candidate : this.macro.actions) {
         if (candidate == action) return true;
      }
      return false;
   }

   private void openActionEditor(int index, MacroAction primary) {
      if (primary == null || !this.hasActionEditor(primary)) return;
      List<MacroAction> targets = this.bulkEditTargets(primary);
      ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlay();
      actionEditor.setWorldCaptureAllowed(this.allowsWorldCaptureActions());
      actionEditor.setMacroContext(this.macro, index);
      actionEditor.open(
         primary,
         this::pushStructuralHistoryStep,
         () -> this.applyBulkActionEdit(primary, targets),
         targets.size()
      );
   }

   private void applyBulkActionEdit(MacroAction primary, List<MacroAction> targets) {
      if (primary == null) return;
      CompoundTag edited = this.exactActionSettings(primary);
      if (edited != null && targets != null && targets.size() > 1) {
         for (MacroAction target : targets) {
            if (target == primary || !this.isActionLive(target)) continue;
            CompoundTag copy = edited.copy();
            target.fromTag(copy);
            autismclient.util.macro.MacroDynamicBindings.load(target, copy);
         }
      }
      this.handleActionEditorSaved(primary);
   }

   private List<MacroAction> cloneStepActions(List<MacroAction> actions) {
      List<MacroAction> clones = new ArrayList<>();
      if (actions == null) return clones;
      for (MacroAction action : actions) {
         if (action == null) continue;
         MacroAction copy = AutismMacro.createActionFromTag(action.toTag());
         if (copy != null) clones.add(copy);
      }
      this.normalizePastedStepGroups(clones);
      return clones;
   }

   private void normalizePastedStepGroups(List<MacroAction> actions) {
      if (actions == null || actions.isEmpty()) return;
      String pendingGateId = null;
      for (MacroAction action : actions) {
         if (action instanceof PacketGateAction gate) {
            gate.gateId = UUID.randomUUID().toString();
            pendingGateId = gate.scope == PacketGateAction.GateScope.MACRO_PASS ? null : gate.gateId;
         } else if (action instanceof EndPacketGateAction endGate && pendingGateId != null) {
            endGate.gateId = pendingGateId;
            pendingGateId = null;
         }
      }
   }

   private int selectedPasteIndex() {
      List<StepUnit> units = this.selectedStepUnits();
      if (units.isEmpty()) return this.macro == null ? 0 : this.macro.actions.size();
      return Math.max(0, Math.min(this.macro.actions.size(), units.get(units.size() - 1).endExclusive));
   }

   private void selectPastedActions(List<MacroAction> pasted) {
      this.stepSelection.clear();
      if (pasted == null) return;
      for (MacroAction action : pasted) {
         if (action != null) this.stepSelection.addStableId(this.stepRowStableId(action));
      }
   }

   private void pruneStepSelection() {
      if (this.macro == null || this.stepSelection.isEmpty()) {
         this.stepSelection.clear();
         return;
      }
      Set<Integer> liveIds = new LinkedHashSet<>();
      for (MacroAction action : this.macro.actions) {
         liveIds.add(this.stepRowStableId(action));
      }
      this.stepSelection.retainAll(liveIds);
   }

   private boolean hasFocusedEditorField() {
      return this.nameField != null && this.nameField.isFocused()
         || this.loopMode == 1 && this.loopCountField != null && this.loopCountField.isFocused()
         || this.stepPickerSearchField != null && this.stepPickerSearchField.isFocused();
   }

   private boolean isControlModifierDown() {
      if (MC == null || MC.getWindow() == null) return false;
      long handle = MC.getWindow().handle();
      return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
         || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
   }

   private boolean isControlModifierDown(int modifiers) {
      return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || this.isControlModifierDown();
   }

   private boolean isShiftModifierDown() {
      if (MC == null || MC.getWindow() == null) return false;
      long handle = MC.getWindow().handle();
      return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
         || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
   }

   private boolean isShiftModifierDown(int modifiers) {
      return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || this.isShiftModifierDown();
   }

   private static String pluralSteps(int count) {
      return count == 1 ? "step" : "steps";
   }

   private boolean copySelectedStepsToClipboard() {
      if (this.isStepListLockedForRun()) return true;
      List<MacroAction> selected = this.selectedStepActionReferences();
      if (selected.isEmpty()) {
         AutismNotifications.warning("Select macro steps first.");
         return true;
      }
      if (AutismClipboardHelper.copyMacroStepsToClipboard(selected)) {
         AutismNotifications.copied("Copied " + selected.size() + " macro " + pluralSteps(selected.size()) + ".");
      } else {
         AutismNotifications.error("Failed to copy macro steps.");
      }
      return true;
   }

   private boolean pasteMacroStepsFromClipboardOrSelection() {
      if (this.isStepListLockedForRun()) return true;
      if (this.macro == null) return false;
      List<MacroAction> pasted = AutismClipboardHelper.pasteMacroStepsFromClipboard();
      if (pasted == null || pasted.isEmpty()) {
         pasted = this.cloneStepActions(this.selectedStepActionReferences());
      } else {
         this.normalizePastedStepGroups(pasted);
      }
      if (pasted == null || pasted.isEmpty()) {
         AutismNotifications.warning("Clipboard has no macro steps.");
         return true;
      }

      int insertIndex = this.selectedPasteIndex();
      this.pushStructuralHistoryStep();
      this.macro.actions.addAll(insertIndex, pasted);
      this.handleMacroActionsChanged();
      this.selectPastedActions(pasted);
      AutismNotifications.show("Pasted " + pasted.size() + " macro " + pluralSteps(pasted.size()) + ".", 0xFF35D873);
      return true;
   }

   private boolean duplicateSelectedSteps() {
      if (this.isStepListLockedForRun()) return true;
      if (this.macro == null || this.stepSelection.isEmpty()) return false;
      List<MacroAction> copies = this.cloneStepActions(this.selectedStepActionReferences());
      if (copies.isEmpty()) return false;
      int insertIndex = this.selectedPasteIndex();
      this.pushStructuralHistoryStep();
      this.macro.actions.addAll(insertIndex, copies);
      this.handleMacroActionsChanged();
      this.clearStepSelection();
      AutismNotifications.show("Duplicated " + copies.size() + " macro " + pluralSteps(copies.size()) + ".", 0xFF35D873);
      return true;
   }

   private boolean deleteSelectedSteps() {
      if (this.isStepListLockedForRun()) return true;
      if (this.macro == null || this.stepSelection.isEmpty()) return false;
      List<StepUnit> units = new ArrayList<>(this.selectedStepUnits());
      if (units.isEmpty()) {
         this.stepSelection.clear();
         return false;
      }

      ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlayIfExists();
      this.pushStructuralHistoryStep();
      java.util.TreeSet<Integer> deleteIndices = new java.util.TreeSet<>();
      for (StepUnit unit : units) {
         for (int i = unit.start; i < unit.endExclusive && i < this.macro.actions.size(); i++) deleteIndices.add(i);
      }

      for (int i : new ArrayList<>(deleteIndices)) {
         if (this.macro.actions.get(i) instanceof MultiAction multi) {
            int count = multi.normalizedStepCount(this.macro.actions, i);
            for (int j = i + 1; j <= i + count; j++) deleteIndices.remove(j);
         }
      }
      Map<MultiAction, List<MacroAction>> multiCoverage = this.multiCoverageSnapshot();
      int removed = 0;
      for (int i : deleteIndices.descendingSet()) {
         if (i < 0 || i >= this.macro.actions.size()) continue;
         if (editor != null) editor.closeIfEditingAction(this.macro.actions.get(i));
         this.macro.actions.remove(i);
         removed++;
      }
      this.reconcileMultiStepCounts(multiCoverage, null);
      this.stepSelection.clear();
      this.handleMacroActionsChanged();
      if (removed > 0) {
         AutismNotifications.show("Deleted " + removed + " macro " + pluralSteps(removed) + ".", 0xFFFF5B5B);
      }
      return true;
   }

   private boolean moveSelectedStepUnits(int direction) {
      if (this.isStepListLockedForRun()) return true;
      if (this.macro == null || this.stepSelection.isEmpty() || direction == 0) return false;
      List<StepUnit> orderedUnits = new ArrayList<>();
      for (int i = 0; i < this.macro.actions.size();) {
         StepUnit unit = this.stepUnitForIndex(i);
         if (unit.isEmpty()) {
            i++;
            continue;
         }
         orderedUnits.add(unit);
         i = unit.endExclusive;
      }
      if (orderedUnits.size() < 2) return false;

      boolean moved = false;
      if (direction < 0) {
         for (int i = 1; i < orderedUnits.size(); i++) {
            if (this.isStepUnitSelected(orderedUnits.get(i)) && !this.isStepUnitSelected(orderedUnits.get(i - 1))) {
               StepUnit unit = orderedUnits.remove(i);
               orderedUnits.add(i - 1, unit);
               moved = true;
            }
         }
      } else {
         for (int i = orderedUnits.size() - 2; i >= 0; i--) {
            if (this.isStepUnitSelected(orderedUnits.get(i)) && !this.isStepUnitSelected(orderedUnits.get(i + 1))) {
               StepUnit unit = orderedUnits.remove(i);
               orderedUnits.add(i + 1, unit);
               moved = true;
            }
         }
      }
      if (!moved) return false;

      Map<MultiAction, List<MacroAction>> multiCoverage = this.multiCoverageSnapshot();
      Set<MacroAction> movedRefs = Collections.newSetFromMap(new IdentityHashMap<>());
      movedRefs.addAll(this.selectedStepActionReferences());
      List<MacroAction> rebuilt = new ArrayList<>(this.macro.actions.size());
      for (StepUnit unit : orderedUnits) {
         for (int i = unit.start; i < unit.endExclusive && i < this.macro.actions.size(); i++) {
            rebuilt.add(this.macro.actions.get(i));
         }
      }
      this.pushStructuralHistoryStep();
      this.macro.actions.clear();
      this.macro.actions.addAll(rebuilt);
      this.reconcileMultiStepCounts(multiCoverage, movedRefs);
      this.handleMacroActionsChanged();
      return true;
   }

   private boolean isStepUnitSelected(StepUnit unit) {
      if (unit == null || unit.isEmpty() || this.macro == null) return false;
      for (int i = unit.start; i < unit.endExclusive && i < this.macro.actions.size(); i++) {
         if (this.stepSelection.containsStableId(this.stepRowStableId(this.macro.actions.get(i)))) return true;
      }
      return false;
   }

   private List<AutismMacroEditorOverlay.StepRowSnapshot> captureCurrentStepRowSnapshots() {
      List<AutismMacroEditorOverlay.StepRowSnapshot> snapshots = new ArrayList<>();
      if (this.macro == null) {
         return snapshots;
      } else {
         for (int index = 0; index < this.macro.actions.size(); index++) {
            MacroAction action = this.macro.actions.get(index);
            snapshots.add(
               new AutismMacroEditorOverlay.StepRowSnapshot(
                  action, this.stepRowStableId(action), action.getDisplayName(), this.isConditionalAction(action), index
               )
            );
         }

         return snapshots;
      }
   }

   private AutismMacroEditorOverlay.StepRowSnapshot findStepRowSnapshot(List<AutismMacroEditorOverlay.StepRowSnapshot> snapshots, int stableId) {
      for (AutismMacroEditorOverlay.StepRowSnapshot snapshot : snapshots) {
         if (snapshot.stableId == stableId) {
            return snapshot;
         }
      }

      return null;
   }

   private AutismMacroEditorOverlay.StepRowMotion findStepRowMotion(int stableId) {
      for (AutismMacroEditorOverlay.StepRowMotion motion : this.stepRowMotions) {
         if (motion.stableId == stableId) {
            return motion;
         }
      }

      return null;
   }

   private void removeStepRowMotion(int stableId) {
      this.stepRowMotions.removeIf(motion -> motion.stableId == stableId);
   }

   private void syncStepRowAnimations(boolean animateChanges) {
      List<AutismMacroEditorOverlay.StepRowSnapshot> current = this.captureCurrentStepRowSnapshots();
      if (!animateChanges) {
         this.stepRowMotions.clear();
         this.lastStepRowSnapshots.clear();
         this.lastStepRowSnapshots.addAll(current);
         this.stepRowAnimationClockNanos = System.nanoTime();
         return;
      }

      this.stepRowMotions.clear();
      for (AutismMacroEditorOverlay.StepRowSnapshot snapshot : current) {
         AutismMacroEditorOverlay.StepRowSnapshot previous = this.findStepRowSnapshot(this.lastStepRowSnapshots, snapshot.stableId);
         if (previous == null) {
            this.removeStepRowMotion(snapshot.stableId);
            this.stepRowMotions.add(
               new AutismMacroEditorOverlay.StepRowMotion(
                  snapshot.stableId,
                  snapshot.label,
                  snapshot.conditional,
                  snapshot.index,
                  snapshot.index,
                  AutismMacroEditorOverlay.StepRowMotionKind.ENTER
               )
            );
         } else if (previous.index != snapshot.index) {
            this.removeStepRowMotion(snapshot.stableId);
            this.stepRowMotions.add(
               new AutismMacroEditorOverlay.StepRowMotion(
                  snapshot.stableId,
                  snapshot.label,
                  snapshot.conditional,
                  previous.index,
                  snapshot.index,
                  AutismMacroEditorOverlay.StepRowMotionKind.MOVE
               )
            );
         }
      }

      for (AutismMacroEditorOverlay.StepRowSnapshot snapshot : this.lastStepRowSnapshots) {
         if (this.findStepRowSnapshot(current, snapshot.stableId) == null) {
            this.removeStepRowMotion(snapshot.stableId);
            this.stepRowMotions.add(
               new AutismMacroEditorOverlay.StepRowMotion(
                  snapshot.stableId,
                  snapshot.label,
                  snapshot.conditional,
                  snapshot.index,
                  snapshot.index,
                  AutismMacroEditorOverlay.StepRowMotionKind.EXIT
               )
            );
         }
      }

      this.lastStepRowSnapshots.clear();
      this.lastStepRowSnapshots.addAll(current);
   }

   private void resetStepRowAnimationState() {
      this.stepSelection.clear();
      this.stepRowStableIds.clear();
      this.lastStepRowSnapshots.clear();
      this.stepRowMotions.clear();
      this.nextStepRowStableId = 1;
      this.stepRowAnimationClockNanos = System.nanoTime();
      if (this.macro != null) {
         this.lastStepRowSnapshots.addAll(this.captureCurrentStepRowSnapshots());
      }
   }

   private float tickStepRowAnimations() {
      long now = System.nanoTime();
      float delta = Math.max(0.0F, Math.min(0.05F, (now - this.stepRowAnimationClockNanos) / 1_000_000_000.0F));
      this.stepRowAnimationClockNanos = now;
      if (this.stepRowMotions.isEmpty()) {
         return delta;
      } else {
         this.stepRowMotions.removeIf(motion -> {
            float duration = switch (motion.kind) {
               case ENTER -> STEP_ROW_ENTER_SECONDS;
               case MOVE -> STEP_ROW_MOVE_SECONDS;
               case EXIT -> STEP_ROW_EXIT_SECONDS;
            };
            motion.progress = Math.min(1.0F, motion.progress + (duration <= 0.0F ? 1.0F : delta / duration));
            return motion.progress >= 0.999F;
         });
         return delta;
      }
   }

   private float getStepRowEnterProgress(int stableId) {
      AutismMacroEditorOverlay.StepRowMotion motion = this.findStepRowMotion(stableId);
      if (motion != null && motion.kind == AutismMacroEditorOverlay.StepRowMotionKind.ENTER) {
         return this.easeOutCubic(motion.progress);
      } else {
         return 1.0F;
      }
   }

   private int getAnimatedStepRowY(AutismMacroEditorOverlay.StepRowMotion motion, int baseListY) {
      float eased = this.easeOutCubic(motion.progress);
      int fromY = baseListY + motion.fromRowIndex * this.actionRowHeight();
      int toY = baseListY + motion.toRowIndex * this.actionRowHeight();
      return Math.round(fromY + (toY - fromY) * eased);
   }

   private int getStepRowMoveOffsetX(AutismMacroEditorOverlay.StepRowMotion motion) {
      if (motion == null) {
         return 0;
      } else {
         float eased = this.easeOutCubic(motion.progress);
         return switch (motion.kind) {
            case ENTER -> Math.round((1.0F - eased) * (float)(-STEP_ROW_ENTER_OFFSET));
            case EXIT -> Math.round(eased * (float)STEP_ROW_EXIT_OFFSET);
            case MOVE -> 0;
         };
      }
   }

   private float getStepRowAlpha(AutismMacroEditorOverlay.StepRowMotion motion) {
      if (motion == null) {
         return 1.0F;
      } else {
         float eased = this.easeOutCubic(motion.progress);
         return switch (motion.kind) {
            case ENTER -> 0.45F + 0.55F * eased;
            case EXIT -> Math.max(0.0F, 1.0F - eased);
            case MOVE -> 1.0F;
         };
      }
   }

   private float easeOutCubic(float value) {
      float clamped = Math.max(0.0F, Math.min(1.0F, value));
      return 1.0F - (float)Math.pow(1.0F - clamped, 3.0);
   }

   private void clampStepListScrollOffset() {
      this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset, this.actionRowHeight(), this.getMaxStepListScroll());
   }

   private int getDraggedRowTop() {
      return this.stepDragMouseY - this.stepDragGrabOffsetY;
   }

   private int getStepInsertSlotForTarget(int sourceIndex, int targetIndex) {
      if (sourceIndex < 0 || targetIndex < 0) {
         return -1;
      } else {
         return targetIndex > sourceIndex ? targetIndex + 1 : targetIndex;
      }
   }

   private int getShiftedVisualIndex(int index, int sourceIndex, int targetIndex) {
      if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) {
         return index;
      } else if (sourceIndex < targetIndex) {
         return index > sourceIndex && index <= targetIndex ? index - 1 : index;
      } else {
         return index >= targetIndex && index < sourceIndex ? index + 1 : index;
      }
   }

   private void beginStepDragPress(int index, double mouseX, double mouseY, boolean canDrag) {
      if (this.isStepListLockedForRun()) return;
      this.pressedStepIndex = index;
      this.stepDragTargetIndex = index;
      this.stepDragGrabOffsetY = (int)Math.round(mouseY) - (this.getStepListY() - this.scrollOffset + index * this.actionRowHeight());
      this.stepDragMouseY = (int)Math.round(mouseY);
      this.stepPressMouseX = mouseX;
      this.stepPressMouseY = mouseY;
      this.stepPressWasSelected = this.isStepIndexSelected(index);
      this.stepPressCanDrag = canDrag;
   }

   private void tryStartStepDrag(double mouseX, double mouseY) {
      if (this.isStepListLockedForRun()) {
         this.clearStepDragState();
         return;
      }
      if (this.draggingIndex < 0 && this.pressedStepIndex >= 0 && this.stepPressCanDrag) {
         boolean movedEnough = Math.abs(mouseX - this.stepPressMouseX) >= STEP_DRAG_THRESHOLD
            || Math.abs(mouseY - this.stepPressMouseY) >= STEP_DRAG_THRESHOLD;
         if (movedEnough) {
            this.draggingIndex = this.pressedStepIndex;
            this.stepDragTargetIndex = this.pressedStepIndex;
            this.stepDragMouseY = (int)Math.round(mouseY);
         }
      }
   }

   private void updateStepDrag(double mouseY) {
      if (this.draggingIndex >= 0 && this.macro != null && !this.macro.actions.isEmpty()) {
         this.stepDragMouseY = (int)Math.round(mouseY);
         this.applyStepDragAutoScroll(mouseY);
         double draggedRowTop = mouseY - this.stepDragGrabOffsetY;
         double draggedRowCenter = draggedRowTop - this.getStepListY() + this.scrollOffset + this.actionRowHeight() / 2.0;
         this.stepDragTargetIndex = this.computeStepDragTargetIndex(draggedRowCenter);
      }
   }

   private int computeStepDragTargetIndex(double draggedRowCenter) {
      if (this.draggingIndex < 0 || this.macro == null || this.macro.actions.isEmpty()) {
         return -1;
      } else {
         int rowHeight = this.actionRowHeight();
         int targetIndex = this.draggingIndex;
         double sourceCenter = this.draggingIndex * rowHeight + rowHeight / 2.0;
         if (draggedRowCenter < sourceCenter) {
            while (targetIndex > 0) {
               double previousCenter = (targetIndex - 1) * rowHeight + rowHeight / 2.0;
               if (draggedRowCenter < previousCenter) {
                  --targetIndex;
               } else {
                  break;
               }
            }
         } else if (draggedRowCenter > sourceCenter) {
            int maxIndex = this.macro.actions.size() - 1;

            while (targetIndex < maxIndex) {
               double nextCenter = (targetIndex + 1) * rowHeight + rowHeight / 2.0;
               if (draggedRowCenter > nextCenter) {
                  ++targetIndex;
               } else {
                  break;
               }
            }
         }

         return targetIndex;
      }
   }

   private void applyStepDragAutoScroll(double mouseY) {
      int maxScroll = this.getMaxStepListScroll();
      if (maxScroll > 0) {
         int listTop = this.getStepListY();
         int listBottom = listTop + this.getStepListHeight();
         int delta = 0;
         if (mouseY < listTop + STEP_DRAG_AUTO_SCROLL_EDGE) {
            double depth = listTop + STEP_DRAG_AUTO_SCROLL_EDGE - mouseY;
            delta = -Math.min(STEP_DRAG_AUTO_SCROLL_MAX_SPEED, Math.max(1, (int)Math.ceil(depth / 3.0)));
         } else if (mouseY > listBottom - STEP_DRAG_AUTO_SCROLL_EDGE) {
            double depth = mouseY - (listBottom - STEP_DRAG_AUTO_SCROLL_EDGE);
            delta = Math.min(STEP_DRAG_AUTO_SCROLL_MAX_SPEED, Math.max(1, (int)Math.ceil(depth / 3.0)));
         }

         if (delta != 0) {
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
         }
      }
   }

   private void clearStepDragState() {
      this.draggingIndex = -1;
      this.pressedStepIndex = -1;
      this.stepDragTargetIndex = -1;
      this.stepDragGrabOffsetY = 0;
      this.stepDragMouseY = 0;
      this.stepPressMouseX = 0.0;
      this.stepPressMouseY = 0.0;
      this.stepPressWasSelected = false;
      this.stepPressCanDrag = false;
   }

   private void moveAction(int fromIndex, int toIndex) {
      if (this.isStepListLockedForRun()) return;
      if (this.macro != null && fromIndex >= 0 && toIndex >= 0 && fromIndex < this.macro.actions.size() && toIndex < this.macro.actions.size() && fromIndex != toIndex) {
         MacroAction source = this.macro.actions.get(fromIndex);
         if (this.isRaceBodyRow(fromIndex)) return;

         int moveCount = 1;
         if (source instanceof RaceAction race) {
            moveCount = race.normalizedBodyCount(this.macro.actions, fromIndex) + 1;
         } else if (source instanceof ReportAction report) {
            moveCount = report.normalizedConditionCount(this.macro.actions, fromIndex) + 1;
         } else if (source instanceof MultiAction multi) {
            moveCount = multi.normalizedStepCount(this.macro.actions, fromIndex) + 1;
         } else if (source instanceof PacketGateAction) {
            moveCount = this.normalizedPacketGateChildCount(fromIndex) + 1;
         } else if (source instanceof EndPacketGateAction) {
            this.movePacketGateEnd(fromIndex, toIndex);
            return;
         }

         GroupRowInfo targetGroup = this.getGroupRowInfo(toIndex, this.macro.actions.get(toIndex));
         if (targetGroup.inGroup() && targetGroup.headerIndex == fromIndex) return;

         if (targetGroup.multi) targetGroup = GroupRowInfo.NONE;

         int insertIndex;
         if (targetGroup.inGroup()) {
            insertIndex = toIndex > fromIndex
               ? targetGroup.headerIndex + targetGroup.childCount + 1
               : targetGroup.headerIndex;
         } else if (toIndex > fromIndex) {
            insertIndex = toIndex + 1;
         } else {
            insertIndex = toIndex;
         }
         Map<MultiAction, List<MacroAction>> multiCoverage = this.multiCoverageSnapshot();
         List<MacroAction> moved = new ArrayList<>(this.macro.actions.subList(fromIndex, fromIndex + moveCount));
         this.macro.actions.subList(fromIndex, fromIndex + moveCount).clear();
         if (insertIndex > fromIndex) insertIndex -= moveCount;
         insertIndex = Math.max(0, Math.min(insertIndex, this.macro.actions.size()));
         this.macro.actions.addAll(insertIndex, moved);
         Set<MacroAction> movedSet = Collections.newSetFromMap(new IdentityHashMap<>());
         movedSet.addAll(moved);
         this.reconcileMultiStepCounts(multiCoverage, movedSet);
         this.handleMacroActionsChanged();
      }
   }

   private void finishStepDrag() {
      if (this.isStepListLockedForRun()) {
         this.clearStepDragState();
         return;
      }
      int sourceIndex = this.draggingIndex;
      int pressIndex = this.pressedStepIndex;
      int targetIndex = this.stepDragTargetIndex;
      boolean wasSelectedAtPress = this.stepPressWasSelected;
      boolean dragged = sourceIndex >= 0;
      this.clearStepDragState();
      if (dragged) {
         if (this.macro != null && targetIndex >= 0 && sourceIndex != targetIndex) {
            this.pushStructuralHistoryStep();
            if (this.isStepIndexSelected(sourceIndex) && this.selectedStepActionReferences().size() > 1) {
               this.moveSelectedStepsToTarget(sourceIndex, targetIndex);
            } else {
               this.moveAction(sourceIndex, targetIndex);
            }
         }
      } else if (pressIndex >= 0 && !this.isControlModifierDown() && !this.isShiftModifierDown()) {
         this.handlePlainStepClick(pressIndex, wasSelectedAtPress);
      }
   }

   private void moveSelectedStepsToTarget(int sourceIndex, int targetIndex) {
      if (this.macro == null) return;
      List<MacroAction> refs = this.selectedStepActionReferences();
      if (refs.size() <= 1 || this.selectionContainsPacketGate(refs)) {
         this.moveAction(sourceIndex, targetIndex);
         return;
      }

      List<MacroAction> actions = this.macro.actions;
      Set<MacroAction> selectedSet = Collections.newSetFromMap(new IdentityHashMap<>());
      selectedSet.addAll(refs);

      int insertPos = this.selectedBlockInsertCompact(selectedSet, sourceIndex, targetIndex);
      if (insertPos < 0) return;

      Map<MultiAction, List<MacroAction>> multiCoverage = this.multiCoverageSnapshot();
      List<MacroAction> remaining = new ArrayList<>(actions.size());
      for (MacroAction action : actions) {
         if (!selectedSet.contains(action)) remaining.add(action);
      }
      insertPos = Math.max(0, Math.min(remaining.size(), insertPos));
      remaining.addAll(insertPos, refs);

      actions.clear();
      actions.addAll(remaining);
      this.reconcileMultiStepCounts(multiCoverage, selectedSet);
   }

   private boolean selectionContainsPacketGate(List<MacroAction> refs) {
      if (refs == null) return false;
      for (MacroAction ref : refs) {
         if (ref instanceof PacketGateAction || ref instanceof EndPacketGateAction) return true;
      }
      return false;
   }

   private int selectedBlockInsertCompact(Set<MacroAction> selectedSet, int sourceIndex, int targetIndex) {
      if (this.macro == null) return -1;
      List<MacroAction> actions = this.macro.actions;
      if (targetIndex < 0 || targetIndex >= actions.size()) return -1;
      MacroAction anchor = actions.get(targetIndex);
      if (selectedSet.contains(anchor)) return -1;

      boolean movingDown = targetIndex > sourceIndex;
      GroupRowInfo group = this.getGroupRowInfo(targetIndex, anchor);

      if (group.multi) group = GroupRowInfo.NONE;
      int anchorReal;
      boolean insertAfter;
      if (group.inGroup()) {
         int lastChild = Math.min(actions.size() - 1, group.headerIndex + group.childCount);
         anchorReal = movingDown ? lastChild : group.headerIndex;
         insertAfter = movingDown;
      } else {
         anchorReal = targetIndex;
         insertAfter = movingDown;
      }
      if (anchorReal < 0 || anchorReal >= actions.size() || selectedSet.contains(actions.get(anchorReal))) return -1;

      int selectedBefore = 0;
      for (int j = 0; j < anchorReal; j++) {
         if (selectedSet.contains(actions.get(j))) selectedBefore++;
      }
      int compactedAnchorPos = anchorReal - selectedBefore;
      return insertAfter ? compactedAnchorPos + 1 : compactedAnchorPos;
   }

   private int multiDragVisualSlot(int realIndex, Set<MacroAction> selectedSet, int insertCompact, int blockSize) {
      List<MacroAction> actions = this.macro.actions;
      int selectedBefore = 0;
      for (int j = 0; j < realIndex; j++) {
         if (selectedSet.contains(actions.get(j))) selectedBefore++;
      }
      int compactedPos = realIndex - selectedBefore;
      if (insertCompact >= 0 && compactedPos >= insertCompact) compactedPos += blockSize;
      return compactedPos;
   }

   private void drawDraggedGhostRow(GuiGraphicsExtractor context, MacroAction action, int top, int number) {
      CompactSurfaces.tintedRow(context, this.panelX + 9, top + 1,
         this.getStepListContentRight() - this.panelX - 9, this.actionRowHeight() - 2, this.theme.overlaySurface(0x00464D5A));
      int badgeColor = this.isConditionalAction(action) ? -30720 : -12268476;
      CompactSurfaces.indicator(context, this.panelX + 10, top + 3, 3, this.actionRowHeight() - 6, badgeColor);
      UiText.draw(context, this.textRenderer, String.valueOf(number), this.theme.fontFor(UiTone.BODY),
         AutismColors.textDim(), this.panelX + 16, this.stepRowTextY(top), false);
      int nameX = this.panelX + 30;
      int controlsX = this.getActionRowControlsX(action);
      int maxNameW = controlsX - nameX - 8;
      String displayName = UiText.trimToWidth(this.textRenderer, action.getDisplayName(), maxNameW, this.theme.fontFor(UiTone.BODY), -1);
      UiText.draw(context, this.textRenderer, displayName, this.theme.fontFor(UiTone.BODY), -1, nameX, this.stepRowTextY(top), false);
   }

   private int getStepPickerWidth() {
      return this.getStepPickerViewportWidth() + 19;
   }

   private int pickerLabelWidth(String label) {
      Integer cached = this.pickerLabelWidthCache.get(label);
      if (cached != null) return cached;
      int w = UiText.width(this.textRenderer, label, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY));
      w = Math.min(STEP_PICKER_LABEL_WEIGHT_CAP, w);
      this.pickerLabelWidthCache.put(label, w);
      return w;
   }

   private int getStepPickerViewportWidth() {
      int screenW = MC.getWindow() != null ? AutismUiScale.getVirtualScreenWidth() : 320;
      int screenH = MC.getWindow() != null ? AutismUiScale.getVirtualScreenHeight() : 320;

      if (this.cachedPickerViewportWidth >= 0
            && this.cachedPickerViewportEpoch == this.pickerLayoutEpoch
            && this.cachedPickerViewportScreenW == screenW
            && this.cachedPickerViewportScreenH == screenH) {
         return this.cachedPickerViewportWidth;
      }
      int result = this.computeStepPickerViewportWidth(screenW, screenH);
      this.cachedPickerViewportEpoch = this.pickerLayoutEpoch;
      this.cachedPickerViewportScreenW = screenW;
      this.cachedPickerViewportScreenH = screenH;
      this.cachedPickerViewportWidth = result;
      return result;
   }

   private int computeStepPickerViewportWidth(int screenW, int screenH) {
      int maxViewportWidth = Math.max(1, Math.min(456, screenW - 20));
      int minViewportWidth = Math.min(132, maxViewportWidth);
      int maxContentHeight = Math.max(1, screenH - 36);
      List<AutismMacroEditorOverlay.StepPickerSectionLayout> fallbackLayouts = null;
      int fallbackOverflow = Integer.MAX_VALUE;
      int fallbackWidth = maxViewportWidth;

      for (int candidateWidth = minViewportWidth; candidateWidth <= maxViewportWidth; candidateWidth += 6) {
         List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts = this.buildStepPickerLayouts(0, 0, candidateWidth);
         int contentHeight = this.getStepPickerContentHeight(layouts);
         if (contentHeight <= maxContentHeight) {
            return this.widenStepPickerViewportWidth(this.getStepPickerUsedWidth(layouts), minViewportWidth, maxViewportWidth);
         }

         int overflow = contentHeight - maxContentHeight;
         if (overflow < fallbackOverflow) {
            fallbackOverflow = overflow;
            fallbackLayouts = layouts;
            fallbackWidth = candidateWidth;
         }
      }

      return fallbackLayouts != null
         ? this.widenStepPickerViewportWidth(this.getStepPickerUsedWidth(fallbackLayouts), minViewportWidth, fallbackWidth)
         : maxViewportWidth;
   }

   private int widenStepPickerViewportWidth(int width, int minViewportWidth, int maxViewportWidth) {
      int widenedWidth = width + 8;
      return Math.max(minViewportWidth, Math.min(maxViewportWidth, widenedWidth));
   }

   private int getStepPickerContentHeight() {
      List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts = this.buildStepPickerLayouts(0, 0, this.getStepPickerViewportWidth());
      return this.getStepPickerContentHeight(layouts);
   }

   private int getStepPickerHeight() {
      int screenH = MC.getWindow() != null ? AutismUiScale.getVirtualScreenHeight() : 320;
      int desiredHeight = 48 + this.getStepPickerContentHeight();
      return Math.max(1, Math.min(desiredHeight, screenH - 8));
   }

   private int getStepPickerX() {
      int pickerW = this.getStepPickerWidth();
      int screenW = MC.getWindow() != null ? AutismUiScale.getVirtualScreenWidth() : this.panelX + this.PANEL_WIDTH + pickerW + 8;
      if (this.panelX + this.PANEL_WIDTH + pickerW + 5 <= screenW) {
         return this.panelX + this.PANEL_WIDTH + 5;
      } else {
         return this.panelX - pickerW - 5 >= 0
            ? this.panelX - pickerW - 5
            : Math.max(4, Math.min(screenW - pickerW - 4, this.panelX + (this.PANEL_WIDTH - pickerW) / 2));
      }
   }

   private int getStepPickerY() {
      int pickerH = this.getStepPickerHeight();
      int screenH = MC.getWindow() != null ? AutismUiScale.getVirtualScreenHeight() : this.panelY + pickerH + 8;
      int preferredY = this.panelY + 42;
      return Math.max(4, Math.min(screenH - pickerH - 4, preferredY));
   }

   private int getStepPickerCloseX() {
      return this.stepPickerCloseBounds().x();
   }

     private int getStepPickerCloseY() {
        return this.stepPickerCloseBounds().y();
     }

   private UiBounds stepPickerCloseBounds() {
      return OverlayTopBar.closeButton(UiBounds.of(this.getStepPickerX(), this.getStepPickerY(), this.getStepPickerWidth(), this.getStepPickerHeight()), 16);
   }

    private CompactScrollbar.Metrics getStepPickerScrollbarMetrics() {
      int pickerX = this.getStepPickerX();
      int pickerY = this.getStepPickerY();
      int pickerW = this.getStepPickerWidth();
      int pickerH = this.getStepPickerHeight();
      int gridX = pickerX + 6;
      int gridY = pickerY + 42;
      int gridW = Math.max(1, pickerW - 19);
      int gridH = Math.max(1, pickerH - 48);
      return CompactScrollbar.compute(this.getStepPickerContentHeight(), gridH, pickerX + pickerW - 9, gridY, 3, gridH, this.stepPickerScrollOffset);
   }

   private boolean isMouseOverStepPicker(int mouseX, int mouseY) {
      if (!this.isStepPickerOpen()) {
         return false;
      } else {
         int pickerX = this.getStepPickerX();
         int pickerY = this.getStepPickerY();
         int pickerW = this.getStepPickerWidth();
         int pickerH = this.getStepPickerHeight();
         return mouseX >= pickerX && mouseX < pickerX + pickerW && mouseY >= pickerY && mouseY < pickerY + pickerH;
      }
   }

   private boolean isStepPickerOpen() {
      return this.stepPickerMode != null;
   }

   private void closeStepPicker() {
      AutismOverlayManager.get().unregister(this.stepPickerOverlay);
      this.stepPickerMode = null;
      this.stepPickerCategories = Collections.emptyList();
      this.stepPickerEntries = Collections.emptyList();
      this.stepPickerEntriesByCategory = Collections.emptyMap();
      this.stepPickerSelector.setItems(Collections.emptyList());
      if (this.stepPickerSearchField != null) this.stepPickerSearchField.setFocused(false);
      this.stepPickerSearchField = null;
      this.stepPickerScrollOffset = 0;
   }

   private void addStepPickerEntry(
      List<AutismMacroEditorOverlay.StepPickerEntry> entries, String categoryId, String label, String description, Runnable action
   ) {
      entries.add(new AutismMacroEditorOverlay.StepPickerEntry(categoryId, label, description, action));
   }

   private void addDefaultRotateAction() {
      if (MC.player != null) {
         this.addAction(new RotateAction(MC.player.getYRot(), MC.player.getXRot(), false, 6));
      } else {
         this.addAction(new RotateAction(0.0F, 0.0F, false, 6));
      }
   }

   private void addDefaultLookAtAction() {
      LookAtBlockAction action = new LookAtBlockAction();
      if (MC.hitResult != null && MC.hitResult.getType() == Type.ENTITY && MC.crosshairPickEntity != null) {
         action.targetMode = LookAtBlockAction.TargetMode.ENTITY;
         action.entityIds.add(LookAtBlockAction.toSpecificEntityEntry(MC.crosshairPickEntity));
      } else if (MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)MC.hitResult;
         action.targetMode = LookAtBlockAction.TargetMode.SPECIFIC;
         action.blockX = hit.getBlockPos().getX();
         action.blockY = hit.getBlockPos().getY();
         action.blockZ = hit.getBlockPos().getZ();
      }

      this.addAction(action);
   }

   private boolean hasActionEditor(MacroAction action) {
      return ActionEditorOverlay.supportsActionEditor(action);
   }

   private boolean isConditionalAction(MacroAction action) {
      return action instanceof WaitForHealthAction
         || action instanceof WaitForBlockAction
         || action instanceof WaitForPacketAction
         || action instanceof WaitForGuiAction
         || action instanceof TickSyncAction
         || action instanceof RevisionSyncAction
         || action instanceof ServerTickSyncAction
         || action instanceof WaitForCooldownAction
         || action instanceof WaitPosAction
         || action instanceof WaitForChatAction
         || action instanceof WaitForEntityAction
         || action instanceof WaitForSlotChangeAction
         || action instanceof WaitForSoundAction
         || action instanceof WaitForWorldChangeAction
         || action instanceof WaitForPositionDeltaAction
         || action instanceof WaitForTeleportAction
         || autismclient.api.macro.MacroActionRegistry.isCondition(action.getTypeId());
   }

   private int findRaceHeaderForBody(int bodyIndex) {
      if (this.macro == null || bodyIndex <= 0 || bodyIndex >= this.macro.actions.size()) return -1;
      for (int i = bodyIndex - 1; i >= 0; i--) {
         MacroAction action = this.macro.actions.get(i);
         if (action instanceof RaceAction race) {
            int end = i + race.normalizedBodyCount(this.macro.actions, i);
            return bodyIndex <= end ? i : -1;
         }
         if (action instanceof ReportAction) return -1;
      }
      return -1;
   }

   private int findReportHeaderForBody(int bodyIndex) {
      if (this.macro == null || bodyIndex <= 0 || bodyIndex >= this.macro.actions.size()) return -1;
      for (int i = bodyIndex - 1; i >= 0; i--) {
         MacroAction action = this.macro.actions.get(i);
         if (action instanceof ReportAction report) {
            int end = i + report.normalizedConditionCount(this.macro.actions, i);
            return bodyIndex <= end ? i : -1;
         }
         if (action instanceof RaceAction) return -1;
      }
      return -1;
   }

   private boolean isRaceBodyRow(int index) {
      return this.findRaceHeaderForBody(index) >= 0 || this.findReportHeaderForBody(index) >= 0;
   }

   private int findMultiHeaderForBody(int bodyIndex) {
      if (this.macro == null || bodyIndex <= 0 || bodyIndex >= this.macro.actions.size()) return -1;
      for (int i = bodyIndex - 1; i >= 0; i--) {
         MacroAction action = this.macro.actions.get(i);
         if (action instanceof MultiAction multi) {
            int end = i + multi.normalizedStepCount(this.macro.actions, i);
            return bodyIndex <= end ? i : -1;
         }
         if (action instanceof RaceAction || action instanceof ReportAction) return -1;
      }
      return -1;
   }

   private Map<MultiAction, List<MacroAction>> multiCoverageSnapshot() {
      Map<MultiAction, List<MacroAction>> coverage = new IdentityHashMap<>();
      if (this.macro == null) return coverage;
      List<MacroAction> actions = this.macro.actions;
      for (int i = 0; i < actions.size(); i++) {
         if (actions.get(i) instanceof MultiAction multi) {
            int count = multi.normalizedStepCount(actions, i);
            coverage.put(multi, new ArrayList<>(actions.subList(i + 1, i + 1 + count)));
         }
      }
      return coverage;
   }

   private void reconcileMultiStepCounts(Map<MultiAction, List<MacroAction>> before, Set<MacroAction> moved) {
      if (this.macro == null || before == null || before.isEmpty()) return;
      List<MacroAction> actions = this.macro.actions;
      for (Map.Entry<MultiAction, List<MacroAction>> entry : before.entrySet()) {
         MultiAction multi = entry.getKey();
         int headerIndex = actions.indexOf(multi);
         if (headerIndex < 0) continue;
         List<MacroAction> members = entry.getValue();

         int windowEnd = headerIndex + multi.normalizedStepCount(actions, headerIndex);
         int lost = 0;
         for (MacroAction member : members) {
            int pos = actions.indexOf(member);
            if (pos <= headerIndex || pos > windowEnd) lost++;
         }
         int gained = 0;
         if (moved != null) {
            for (MacroAction action : moved) {
               if (members.contains(action)) continue;
               if (action instanceof RaceAction || action instanceof ReportAction || action instanceof MultiAction) continue;
               int pos = actions.indexOf(action);
               if (pos > headerIndex && pos <= windowEnd) gained++;
            }
         }
         multi.stepCount = Math.max(0, multi.stepCount - lost + gained);
      }
   }

   private int normalizedPacketGateChildCount(int headerIndex) {
      if (this.macro == null || headerIndex < 0 || headerIndex >= this.macro.actions.size()) return 0;
      if (!(this.macro.actions.get(headerIndex) instanceof PacketGateAction gate)) return 0;
      if (gate.scope == PacketGateAction.GateScope.MACRO_PASS) return 0;
      for (int i = headerIndex + 1; i < this.macro.actions.size(); i++) {
         MacroAction action = this.macro.actions.get(i);
         if (action instanceof EndPacketGateAction) return i - headerIndex;
         if (action instanceof PacketGateAction || action instanceof RaceAction || action instanceof ReportAction) break;
      }
      return 0;
   }

   private int findPacketGateHeaderForBody(int bodyIndex) {
      if (this.macro == null || bodyIndex <= 0 || bodyIndex >= this.macro.actions.size()) return -1;
      for (int i = bodyIndex - 1; i >= 0; i--) {
         MacroAction action = this.macro.actions.get(i);
         if (action instanceof PacketGateAction) {
            int end = i + this.normalizedPacketGateChildCount(i);
            return bodyIndex <= end ? i : -1;
         }
         if (action instanceof EndPacketGateAction || action instanceof RaceAction || action instanceof ReportAction) return -1;
      }
      return -1;
   }

   private boolean isPacketGateEndRow(int index) {
      return this.macro != null
         && index >= 0
         && index < this.macro.actions.size()
         && this.macro.actions.get(index) instanceof EndPacketGateAction
         && this.findPacketGateHeaderForBody(index) >= 0;
   }

   private void movePacketGateEnd(int fromIndex, int toIndex) {
      if (this.macro == null || fromIndex < 0 || fromIndex >= this.macro.actions.size()) return;
      int header = this.findPacketGateHeaderForBody(fromIndex);
      if (header < 0) return;
      int target = Math.max(header + 1, Math.min(toIndex, this.macro.actions.size() - 1));
      if (target == fromIndex) return;
      MacroAction end = this.macro.actions.remove(fromIndex);
      target = Math.max(header + 1, Math.min(target, this.macro.actions.size()));
      this.macro.actions.add(target, end);
      this.syncPacketGateEndIds();
      this.handleMacroActionsChanged();
   }

   private void reconcilePacketGate(PacketGateAction gate) {
      if (this.macro == null || gate == null) return;
      int index = this.macro.actions.indexOf(gate);
      if (index < 0) return;
      int end = index + this.normalizedPacketGateChildCount(index);
      if (gate.scope == PacketGateAction.GateScope.MACRO_PASS) {
         if (end > index && end < this.macro.actions.size() && this.macro.actions.get(end) instanceof EndPacketGateAction) {
            this.macro.actions.remove(end);
         }
         return;
      }
      if (end <= index || end >= this.macro.actions.size() || !(this.macro.actions.get(end) instanceof EndPacketGateAction)) {
         this.macro.actions.add(index + 1, new EndPacketGateAction(gate.gateId));
      }
      this.syncPacketGateEndIds();
   }

   private void syncPacketGateEndIds() {
      if (this.macro == null) return;
      for (int i = 0; i < this.macro.actions.size(); i++) {
         if (!(this.macro.actions.get(i) instanceof PacketGateAction gate)) continue;
         int end = i + this.normalizedPacketGateChildCount(i);
         if (end > i && end < this.macro.actions.size() && this.macro.actions.get(end) instanceof EndPacketGateAction endGate) {
            endGate.gateId = gate.gateId == null || gate.gateId.isBlank() ? "auto" : gate.gateId;
            endGate.flushOnDisable = gate.flushOnDisable;
         }
      }
   }

   private GroupRowInfo getGroupRowInfo(int index, MacroAction action) {
      if (this.macro == null || action == null || index < 0 || index >= this.macro.actions.size()) {
         return GroupRowInfo.NONE;
      }

      if (action instanceof RaceAction race) {
         return new GroupRowInfo(true, false, false, index, race.normalizedBodyCount(this.macro.actions, index), 0, "RACE");
      }
      if (action instanceof ReportAction report) {
         return new GroupRowInfo(true, true, false, index, report.normalizedConditionCount(this.macro.actions, index), 0, "REPORT");
      }
      if (action instanceof PacketGateAction) {
         return new GroupRowInfo(true, false, true, index, this.normalizedPacketGateChildCount(index), 0, "GATE START");
      }
      if (action instanceof MultiAction multi) {
         return new GroupRowInfo(true, false, false, true, index, multi.normalizedStepCount(this.macro.actions, index), 0, "MULTI");
      }

      int raceHeader = this.findRaceHeaderForBody(index);
      if (raceHeader >= 0 && this.macro.actions.get(raceHeader) instanceof RaceAction race) {
         int childCount = race.normalizedBodyCount(this.macro.actions, raceHeader);
         int conditionCount = race.normalizedConditionCount(this.macro.actions, raceHeader);
         int offset = index - raceHeader;
         return new GroupRowInfo(false, false, false, raceHeader, childCount, offset, offset <= conditionCount ? "CONDITION" : "ACTION");
      }

      int reportHeader = this.findReportHeaderForBody(index);
      if (reportHeader >= 0 && this.macro.actions.get(reportHeader) instanceof ReportAction report) {
         int childCount = report.normalizedConditionCount(this.macro.actions, reportHeader);
         int offset = index - reportHeader;
         return new GroupRowInfo(false, true, false, reportHeader, childCount, offset, offset == 1 ? "START" : "END");
      }

      int gateHeader = this.findPacketGateHeaderForBody(index);
      if (gateHeader >= 0) {
         int childCount = this.normalizedPacketGateChildCount(gateHeader);
         int offset = index - gateHeader;
         String role = action instanceof EndPacketGateAction ? "GATE END" : "GATED";
         return new GroupRowInfo(false, false, true, gateHeader, childCount, offset, role);
      }

      int multiHeader = this.findMultiHeaderForBody(index);
      if (multiHeader >= 0 && this.macro.actions.get(multiHeader) instanceof MultiAction multi) {
         int childCount = multi.normalizedStepCount(this.macro.actions, multiHeader);
         int offset = index - multiHeader;
         return new GroupRowInfo(false, false, false, true, multiHeader, childCount, offset, "STEP");
      }

      return GroupRowInfo.NONE;
   }

   private boolean isStepImpactHeader(MacroAction action) {
      if (action == null || !action.isEnabled()) return false;
      if (action instanceof autismclient.util.macro.FpsAction fps) {
         return fps.endMode() != autismclient.util.macro.FpsAction.EndMode.TIMEOUT;
      }
      if (action instanceof autismclient.util.macro.SPingAction sping) {
         return sping.endMode() != autismclient.util.macro.SPingAction.EndMode.TIMEOUT;
      }
      return false;
   }

   private int stepImpactSpanEnd(int headerIndex) {
      if (this.macro == null || headerIndex < 0 || headerIndex >= this.macro.actions.size()) return headerIndex;
      MacroAction action = this.macro.actions.get(headerIndex);
      int last = this.macro.actions.size() - 1;
      int covered;
      if (action instanceof autismclient.util.macro.FpsAction fps) {
         covered = fps.endMode() == autismclient.util.macro.FpsAction.EndMode.END
            ? last - headerIndex : fps.normalizedStepCount();
      } else if (action instanceof autismclient.util.macro.SPingAction sping) {
         covered = sping.endMode() == autismclient.util.macro.SPingAction.EndMode.END
            ? last - headerIndex : sping.normalizedStepCount();
      } else {
         return headerIndex;
      }
      long end = (long) headerIndex + Math.max(0, covered);
      return (int) Math.max(headerIndex, Math.min(last, end));
   }

   private int controllingStepImpactHeaderIndex(int index) {
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return -1;
      for (int h = index; h >= 0; h--) {
         MacroAction a = this.macro.actions.get(h);
         if (!isStepImpactHeader(a)) continue;
         if (h == index || index <= stepImpactSpanEnd(h)) return h;
      }
      return -1;
   }

   private StepImpactInfo getStepImpactInfo(int index) {
      int header = controllingStepImpactHeaderIndex(index);
      if (header < 0) return StepImpactInfo.NONE;
      boolean ping = this.macro.actions.get(header) instanceof autismclient.util.macro.SPingAction;
      boolean isHeaderRow = header == index;
      boolean first = index == 0 || controllingStepImpactHeaderIndex(index - 1) < 0;
      boolean last = index >= this.macro.actions.size() - 1 || controllingStepImpactHeaderIndex(index + 1) < 0;
      return new StepImpactInfo(true, isHeaderRow, ping, first, last);
   }

   private void addDefaultOpenContainerAction() {
      OpenContainerAction action = new OpenContainerAction();
      if (MC.hitResult instanceof EntityHitResult) {
         action.captureCurrentLookTarget(MC);
      } else if (MC.hitResult instanceof BlockHitResult hit) {
         action.blockPos = hit.getBlockPos();
      }

      this.addAction(action);
   }

   private void addDefaultInteractEntityAction() {
      autismclient.util.macro.InteractEntityAction action = new autismclient.util.macro.InteractEntityAction();

      action.captureCurrentLookTarget(MC);
      this.addAction(action);
   }

   private void addDefaultInventoryAction() {
      InventoryAction action = new InventoryAction();
      action.mode = InventoryAction.InvMode.OPEN;
      this.addAction(action);
   }

   private void addDefaultDelayPacketsAction() {
      DelayPacketsAction action = new DelayPacketsAction();
      if (AutismSharedState.get().shouldUseCustomPackets()) {
         action.applyModulePreset();
      } else {
         action.applyDefaultPreset();
      }

      this.addAction(action);
   }

   private void addDefaultWaitBlockAction() {
      WaitForBlockAction action = new WaitForBlockAction();
      if (MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)MC.hitResult;
         action.blockPos = hit.getBlockPos();
      }

      this.addAction(action);
   }

   private void addDefaultWaitEntityAction() {
      WaitForEntityAction action = new WaitForEntityAction();
      if (MC.player != null) {
         action.x = Math.round(MC.player.getX());
         action.y = Math.round(MC.player.getY());
         action.z = Math.round(MC.player.getZ());
      }

      this.addAction(action);
   }

   private void addDefaultInstaBreakAction() {
      InstaBreakAction action = new InstaBreakAction();
      if (MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)MC.hitResult;
         action.blockPos = hit.getBlockPos();
         action.direction = hit.getDirection();
      }

      this.addAction(action);
   }

   private void addDefaultBreakAction() {
      BreakAction action = new BreakAction();
      if (MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)MC.hitResult;
         action.blockPos = hit.getBlockPos();
         action.direction = hit.getDirection();
      }

      this.addAction(action);
   }

   private List<MacroAction> buildBundleDupeV3Preset() {
      List<MacroAction> actions = new ArrayList<>();
      AssertAction assertBundle = new AssertAction();
      assertBundle.check = AssertAction.CheckType.HAS_BUNDLE;
      assertBundle.message = "Must have a bundle";
      actions.add(assertBundle);
      DisconnectAction dupe = new DisconnectAction();
      dupe.mode = DisconnectAction.DisconnectMode.KICK_DUPE;
      dupe.lagMethod = DisconnectAction.LagMethod.CLICK_SLOT;
      dupe.kickMethod = DisconnectAction.KickMethod.HURT;
      dupe.packetCount = 200;
      actions.add(dupe);
      return actions;
   }

   private List<MacroAction> buildRollbackPreset() {
      return new ArrayList<>(List.of(new RollbackAction()));
   }

   private List<MacroAction> buildBundleDupeV2Preset() {
      List<MacroAction> actions = new ArrayList<>();

      SelectSlotAction selectSlotOne = new SelectSlotAction();
      selectSlotOne.slot = 0;
      actions.add(selectSlotOne);

      AssertAction assertBundle = new AssertAction();
      assertBundle.check = AssertAction.CheckType.BUNDLE_V2_READY;
      assertBundle.message = "Slot 1 must contain a one-item bundle and no container GUI can be open";
      actions.add(assertBundle);

      actions.add(new BundleDupeV2Action());
      return actions;
   }

   private List<MacroAction> buildBookDupePreset() {
      List<MacroAction> actions = new ArrayList<>();
      NbtBookAction book = new NbtBookAction();
      book.pages = 1;

      book.customComponent = "DupersUnited?";
      book.title = "NeedToHave42CharsInHereHAHA123456789012345";
      book.delayTicks = 0;
      book.bookCount = 1;
      book.requireHeldWritableBook = true;
      book.dropInventoryBefore = true;
      book.disconnectAfter = true;
      actions.add(book);
      return actions;
   }

   private List<MacroAction> buildShulkerDupePreset() {
      List<MacroAction> actions = new ArrayList<>();
      WaitForGuiAction waitGui = new WaitForGuiAction();
      waitGui.guiType = "CONTAINER";
      actions.add(waitGui);
      InstaBreakAction mine = new InstaBreakAction();
      if (MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)MC.hitResult;
         mine.blockPos = hit.getBlockPos();
         mine.direction = hit.getDirection();
      }
      mine.autoPickaxe = true;
      mine.times = 1;
      mine.delayTicks = 0;
      actions.add(mine);
      ContainerClickSequenceAction clicks = new ContainerClickSequenceAction();
      clicks.slotSource = ContainerClickSequenceAction.SlotSource.RANGE;
      clicks.startSlot = 100;
      clicks.endSlot = 126;
      clicks.containerInput = "QUICK_MOVE";
      actions.add(clicks);
      return actions;
   }

   private List<MacroAction> buildTridentDupePreset() {
      List<MacroAction> actions = new ArrayList<>();
      SelectSlotAction select = new SelectSlotAction();
      select.itemName = "Trident or bow";
      select.strategy = SelectSlotAction.Strategy.BEST_DURABILITY;
      actions.add(select);
      UseItemPhaseAction phase = new UseItemPhaseAction();
      phase.phase = UseItemPhaseAction.Phase.START_USE;
      phase.holdTicks = 100;
      phase.gateDuringHold = true;
      phase.gatePlayerActions = true;
      phase.gateContainerClicks = true;
      phase.useCustomSlotMapping = true;
      phase.swapSlotBeforeRelease = 103;
      phase.swapButton = 0;
      phase.releaseAfterHold = true;
      phase.dropSlotAfterRelease = true;
      phase.dropSlot = 8;
      phase.gateId = "trident-dupe";
      actions.add(phase);
      return actions;
   }

   private List<MacroAction> buildTradeAxPreset() {
      List<MacroAction> actions = new ArrayList<>();
      SignEditAction sign = new SignEditAction();
      sign.guiName = "SIGN";
      sign.waitForGuiBefore = true;
      sign.line1 = "1";
      sign.sendCommandAfter = true;
      sign.commandAfter = "a";
      actions.add(sign);
      return actions;
   }

   private List<MacroAction> buildTradeLoverFellaPreset() {
      List<MacroAction> actions = new ArrayList<>();
      SignEditAction sign = new SignEditAction();
      sign.guiName = "SIGN";
      sign.waitForGuiBefore = true;
      sign.line1 = "e";
      sign.sendClosePacketAfter = true;
      sign.closePacketContainerId = 0;
      actions.add(sign);
      return actions;
   }

   private List<MacroAction> buildBundleCrashPreset() {
      List<MacroAction> actions = new ArrayList<>();
      AssertAction assertBundle = new AssertAction();
      assertBundle.check = AssertAction.CheckType.HELD_ITEM;
      assertBundle.itemName = "Bundle";
      assertBundle.message = "Must hold a bundle";
      actions.add(assertBundle);
      PacketBurstAction burst = new PacketBurstAction();
      burst.mode = PacketBurstAction.BurstMode.BUNDLE_SELECT;
      burst.count = 1;
      burst.slot = -1;
      burst.bundleIndex = -1337;
      actions.add(burst);
      UseItemPhaseAction use = new UseItemPhaseAction();
      use.phase = UseItemPhaseAction.Phase.USE_ONCE;
      use.itemName = "Bundle";
      actions.add(use);
      return actions;
   }

   private void appendGateCleanup(List<MacroAction> actions, String gateId) {
      FinallyAction finallyAction = new FinallyAction();
      finallyAction.bodyCount = 1;
      actions.add(finallyAction);
      EndPacketGateAction disable = new EndPacketGateAction(gateId);
      actions.add(disable);
   }

   private List<AutismMacroEditorOverlay.StepPickerEntry> buildStepPickerEntries(AutismMacroEditorOverlay.StepPickerMode mode) {
      List<AutismMacroEditorOverlay.StepPickerEntry> entries = new ArrayList<>();
      if (mode == AutismMacroEditorOverlay.StepPickerMode.ACTION) {
         this.addStepPickerEntry(entries, "flow", "Chat", "Send a chat message or slash command.", () -> this.addAction(new SendChatAction()));
         this.addStepPickerEntry(entries, "flow", "Delay", "Pause for a time in ms or ticks.", () -> this.addAction(new DelayAction(1000)));
         this.addStepPickerEntry(entries, "flow", "FPS", "Cap max FPS (0 = freeze). End on a timer, after N following steps, or at the end of the macro.", () -> this.addAction(new FpsAction()));
         this.addStepPickerEntry(entries, "flow", "Ping", "Spoof your ping by adding latency (ms). End on a timer, after N following steps, or at the end of the macro.", () -> this.addAction(new SPingAction()));
         this.addStepPickerEntry(entries, "flow", "Repeat", "Repeat the next steps a chosen number of times.", () -> this.addAction(new RepeatAction()));
         this.addStepPickerEntry(entries, "flow", "If", "Run THEN steps when a condition holds, otherwise the ELSE steps.", () -> this.addAction(new IfAction()));
         this.addStepPickerEntry(entries, "flow", "Goto", "Jump: skip ahead, loop back, restart at top, go to a Label, or stop.", () -> this.addAction(new FlowAction()));
         this.addStepPickerEntry(entries, "flow", "Label", "Named anchor that Goto can jump to (build loops with Label + Goto).", () -> this.addAction(new LabelAction()));
         this.addStepPickerEntry(entries, "flow", "Stop", "Stop the macro immediately.", () -> this.addAction(new StopMacroAction()));
         this.addStepPickerEntry(entries, "network", "Send Toggle", "Turn packet sending on or off.", () -> this.addAction(new SendToggleAction()));
         this.addStepPickerEntry(entries, "network", "Delay Packets", "Queue packets until you flush them.", this::addDefaultDelayPacketsAction);
         this.addStepPickerEntry(entries, "flow", "Branch", "Run or skip compact step groups.", () -> this.addAction(new BranchAction()));
         this.addStepPickerEntry(entries, "flow", "Finally", "Register cleanup rows.", () -> this.addAction(new FinallyAction()));
         this.addStepPickerEntry(entries, "flow", "Variables", "Set macro variables.", () -> this.addAction(new MacroVariablesAction()));
         this.addStepPickerEntry(entries, "flow", "Capture Value", "Reuse game text later.", () -> this.addAction(new CaptureValueAction()));
         this.addStepPickerEntry(entries, "flow", "GM", "Change game mode for real, or fake it client-side.", () -> this.addAction(new FakeGamemodeAction()));
         this.addStepPickerEntry(entries, "flow", "Save GUI", "Remember the current GUI for later restore.", () -> this.addAction(new SaveGuiAction()));
         this.addStepPickerEntry(entries, "flow", "Restore GUI", "Reopen the last saved GUI state.", () -> this.addAction(new RestoreGuiAction()));
         this.addStepPickerEntry(entries, "movement", "Rotate", "Set player yaw and pitch.", this::addDefaultRotateAction);
         this.addStepPickerEntry(entries, "movement", "Look At", "Face a specific position, nearest block, or nearest entity.", this::addDefaultLookAtAction);
         this.addStepPickerEntry(entries, "movement", "Sneak", "Hold or release sneak.", () -> this.addAction(new SneakAction()));
         this.addStepPickerEntry(entries, "movement", "Jump", "Press jump for a chosen duration.", () -> this.addAction(new JumpAction()));
         this.addStepPickerEntry(entries, "movement", "Sprint", "Toggle sprint state.", () -> this.addAction(new SprintAction()));
         this.addStepPickerEntry(entries, "movement", "Move", "Walk in a direction for N ticks.", () -> this.addAction(new MoveAction()));
         this.addStepPickerEntry(entries, "movement", "VClip", "Vertical clip with configurable packet behavior.", () -> this.addAction(new VClipAction()));
         this.addStepPickerEntry(entries, "movement", "HClip", "Horizontal clip forward with configurable packet behavior.", () -> this.addAction(new HClipAction()));
         this.addStepPickerEntry(entries, "movement", "TP", "Ground-first HClip teleport.", () -> this.addAction(new PacedTpAction()));
         if (AutismCompatManager.isBaritoneAvailable()) {
            this.addStepPickerEntry(entries, "movement", "Go To", "Use Baritone to path to a target.", () -> this.addAction(new GoToAction()));
            this.addStepPickerEntry(entries, "movement", "Mine", "Mine blocks with Baritone rules.", () -> this.addAction(new MineAction()));
         }

         this.addStepPickerEntry(entries, "interaction", "Item Click", "Click items or slots in open inventories.", () -> this.addAction(new ItemAction()));
         this.addStepPickerEntry(entries, "interaction", "Pick Up All", "Pick up matching stacks into the carried item.", () -> this.addAction(new PickUpAllAction()));
         this.addStepPickerEntry(entries, "interaction", "Use Item", "Use the held item or select one by name.", () -> this.addAction(new UseItemAction()));
         this.addStepPickerEntry(entries, "interaction", "Use Phase", "Start, release, swing, or use once.", () -> this.addAction(new UseItemPhaseAction()));
         this.addStepPickerEntry(entries, "inventory", "Open Inventory", "Open the player inventory screen.", this::addDefaultInventoryAction);
         this.addStepPickerEntry(entries, "inventory", "Slot", "Select a hotbar slot or item by name.", () -> this.addAction(new SelectSlotAction()));
         this.addStepPickerEntry(entries, "interaction", "Click Sequence", "Click slots, ranges, or lists.", () -> this.addAction(new ContainerClickSequenceAction()));
         this.addStepPickerEntry(entries, "inventory", "XCarry", "Store four grid items plus one cursor item, or cursor-only when selected.", () -> this.addAction(new XCarryAction()));
         this.addStepPickerEntry(entries, "inventory", "Drop", "Drop items from chosen slots.", () -> this.addAction(new DropAction()));
         this.addStepPickerEntry(entries, "inventory", "Swap", "Swap two inventory slots.", () -> this.addAction(new SwapSlotsAction()));
         this.addStepPickerEntry(entries, "interaction", "Open Container", "Open a targeted container block.", this::addDefaultOpenContainerAction);
         this.addStepPickerEntry(entries, "interaction", "Interact Entity", "Right-click a captured entity or block.", this::addDefaultInteractEntityAction);
         this.addStepPickerEntry(entries, "interaction", "Place", "Place a selected item at a captured block position and face.", () -> this.addAction(new PlaceAction()));
         this.addStepPickerEntry(entries, "inventory", "Store", "Move items between player and containers.", () -> this.addAction(new StoreItemAction()));
         this.addStepPickerEntry(entries, "inventory", "Inventory Audit", "Snapshot slots, compare after reopen, or run automated dupe tests.", () -> this.addAction(new InventoryAuditAction()));
         this.addStepPickerEntry(entries, "inventory", "Rollback", "Duplicate selected local container items.", () -> this.addAction(new RollbackAction()));
         this.addStepPickerEntry(entries, "inventory", "Craft", "Craft one or more recipes.", () -> this.addAction(new CraftAction()));
         this.addStepPickerEntry(
            entries, "interaction", "Mouse Click", "Simulate mouse clicks in world.", () -> this.addAction(new ClickAction(ClickAction.ContainerInput.RIGHT))
         );
         this.addStepPickerEntry(entries, "network", "Packet", "Send captured packets from the queue.", () -> this.addAction(new SendPacketAction()));
         this.addStepPickerEntry(entries, "network", "Packet Gate", "Cancel, delay, or allow packet groups.", () -> {
            PacketGateAction g = new PacketGateAction();
            g.gateId = java.util.UUID.randomUUID().toString();
            boolean wholePassDefault = this.macro != null && this.macro.loop && this.macro.actions.isEmpty();
            if (wholePassDefault) g.scope = PacketGateAction.GateScope.MACRO_PASS;
            this.addAction(g);
            if (!wholePassDefault) this.addAction(new EndPacketGateAction(g.gateId));
         });
         this.addStepPickerEntry(entries, "network", "Packet Burst", "Generate packet bursts.", () -> this.addAction(new PacketBurstAction()));
         this.addStepPickerEntry(entries, "network", "Custom Menu", "Fill and submit a server-defined dialog or form.", () -> this.addAction(new CustomMenuAction()));
         this.addStepPickerEntry(entries, "network", "Payload", "Send and edit custom payload packets with raw bytes or runtime Java.", () -> this.addAction(new PayloadAction()));
         this.addStepPickerEntry(
            entries, "network", "Close GUI", "Close the current screen with optional no-packet mode.", () -> this.addAction(new CloseGuiAction())
         );
         this.addStepPickerEntry(entries, "network", "Desync", "Create GUI desync without closing locally.", () -> this.addAction(new DesyncAction()));
         this.addStepPickerEntry(entries, "network", "NBT Book", "Write large custom books.", () -> this.addAction(new NbtBookAction()));
         this.addStepPickerEntry(entries, "network", "Sign Edit", "Send sign update lines.", () -> this.addAction(new SignEditAction()));
         this.addStepPickerEntry(entries, "network", "Disconnect", "Disconnect or kick using selected mode.", () -> this.addAction(new DisconnectAction()));
         this.addStepPickerEntry(entries, "flow", "Module", "Toggle AUTISM or Meteor modules.", () -> this.addAction(new ToggleModuleAction()));
         this.addStepPickerEntry(entries, "flow", "Assert", "Validate preconditions.", () -> this.addAction(new AssertAction()));

         this.addStepPickerEntry(entries, "flow", "Pay", "Send payments to a player list.", () -> this.addAction(new PayAction()));
         this.addStepPickerEntry(entries, "interaction", "InstaBreak", "Instant rebreak for one captured block.", this::addDefaultInstaBreakAction);
         this.addStepPickerEntry(entries, "interaction", "Break", "Legit vanilla break with break-progress tracking + optional GUI-race interact.", this::addDefaultBreakAction);
         this.addStepPickerEntry(entries, "flow", "Race", "Run grouped actions when a condition fires.", () -> this.addAction(new RaceAction()));
         this.addStepPickerEntry(entries, "flow", "Report", "Measure time between macro, packet, GUI, teleport, or position events.", () -> this.addAction(new ReportAction()));

         if (AutismConfig.getGlobal().multiDisclaimerAccepted) {
            this.addStepPickerEntry(entries, "flow", "Multi", "Run the next N steps on selected Multi accounts, with connect + wait options.", () -> this.addAction(new MultiAction()));
         }
         this.addStepPickerEntry(entries, "flow", "Start Macro", "Start another macro from the macro library.", () -> this.addAction(new StartMacroAction()));
         this.addStepPickerEntry(entries, "flow", "Stop Macro", "Stop this, all, or a selected macro.", () -> this.addAction(new StopMacroAction()));
       } else if (mode == AutismMacroEditorOverlay.StepPickerMode.PRESET) {
         this.addStepPickerEntry(entries, "singleplayer", "Rollback", "Local container duplicate", () -> this.addActionStack(this.buildRollbackPreset()));
         this.addStepPickerEntry(entries, "dupe", "Bundle Dupe V3", "Paper / Spigot", () -> this.addActionStack(this.buildBundleDupeV3Preset()));
         this.addStepPickerEntry(entries, "dupe", "Bundle Dupe V2", "Paper / Spigot", () -> this.addActionStack(this.buildBundleDupeV2Preset()));
         this.addStepPickerEntry(entries, "dupe", "Book Dupe", "Paper < 1.21.1", () -> this.addActionStack(this.buildBookDupePreset()));
         this.addStepPickerEntry(entries, "dupe", "Shulker Dupe", "Vanilla 1.19 & below", () -> this.addActionStack(this.buildShulkerDupePreset()));
         this.addStepPickerEntry(entries, "dupe", "Trident Dupe", "Vanilla < 1.19", () -> this.addActionStack(this.buildTridentDupePreset()));
         this.addStepPickerEntry(entries, "trade", "AxTrade Sign", "Patched", () -> this.addActionStack(this.buildTradeAxPreset()));
         this.addStepPickerEntry(entries, "trade", "LoverFella Sign", "Patched", () -> this.addActionStack(this.buildTradeLoverFellaPreset()));
         this.addStepPickerEntry(entries, "crash", "Bundle Crash", "1.21.4", () -> this.addActionStack(this.buildBundleCrashPreset()));
      } else {
         this.addStepPickerEntry(
            entries, "player", "Health", "Pause until your health drops below or rises above a target.", () -> this.addAction(new WaitForHealthAction(20.0F, true))
         );
         this.addStepPickerEntry(
            entries, "player", "Cooldown", "Pause until an item cooldown is ready.", () -> this.addAction(new WaitForCooldownAction())
         );
         this.addStepPickerEntry(
            entries, "player", "Gamemode", "Pause until real or optionally fake gamemode changes.", () -> this.addAction(new WaitGamemodeChangeAction())
         );
         this.addStepPickerEntry(
            entries, "inventory", "Slot or Item", "Pause until slots or inventory contents change.", () -> this.addAction(new WaitForSlotChangeAction())
         );
         this.addStepPickerEntry(entries, "inventory", "Durability", "Pause until a held, selected, or slotted item reaches durability.", () -> this.addAction(new WaitDurabilityAction()));
         this.addStepPickerEntry(entries, "inventory", "Free Slots", "Pause until free or filled inventory slots match.", () -> this.addAction(new WaitFreeSlotsAction()));
         this.addStepPickerEntry(entries, "world", "Movement", "Pause until a position, move-distance, world change, or teleport — pick the type inside.", () -> this.addAction(new WaitMovementAction()));
         this.addStepPickerEntry(entries, "world", "Block", "Pause until a block is placed or broken.", this::addDefaultWaitBlockAction);
         this.addStepPickerEntry(entries, "world", "Entity", "Pause until a matching entity appears.", this::addDefaultWaitEntityAction);
         this.addStepPickerEntry(entries, "world", "Sound", "Pause until a matching sound plays.", () -> this.addAction(new WaitForSoundAction()));
         this.addStepPickerEntry(entries, "gui_chat", "GUI", "Pause until a GUI opens or closes.", () -> this.addAction(new WaitForGuiAction()));
         this.addStepPickerEntry(entries, "gui_chat", "Chat", "Pause until chat matches a pattern.", () -> this.addAction(new WaitForChatAction()));
         this.addStepPickerEntry(entries, "sync", "LAN", "Pause until a LAN peer reaches a step.", () -> this.addAction(new WaitForLanStepAction()));
         this.addStepPickerEntry(entries, "sync", "Macro", "Pause until another macro starts, completes a step, or finishes.", () -> this.addAction(new WaitForMacroStepAction()));
         this.addStepPickerEntry(
            entries, "packets", "Packet", "Pause until a chosen C2S or S2C packet appears.", () -> this.addAction(new WaitForPacketAction(""))
         );
         this.addStepPickerEntry(entries, "packets", "Packet Match", "Pause for packet plus field predicate.", () -> this.addAction(new WaitPacketMatchAction()));
         this.addStepPickerEntry(entries, "inventory", "Inventory", "Pause for item, count, slot, cursor, or fullness.", () -> this.addAction(new WaitInventoryPredicateAction()));
         this.addStepPickerEntry(entries, "sync", "Tick Sync", "Wait for the next client tick.", () -> this.addAction(new TickSyncAction()));
         this.addStepPickerEntry(entries, "sync", "Revision Sync", "Wait for handler revision sync.", () -> this.addAction(new RevisionSyncAction()));
         this.addStepPickerEntry(entries, "sync", "Server Sync", "Wait for the server tick tracker.", () -> this.addAction(new ServerTickSyncAction()));
      }

      if (mode == AutismMacroEditorOverlay.StepPickerMode.ACTION) {
         this.appendAddonStepEntries(entries, false);
      } else if (mode == AutismMacroEditorOverlay.StepPickerMode.PRESET) {
         this.appendAddonPresetEntries(entries);
      } else {

         this.appendAddonStepEntries(entries, true);
      }

      return entries;
   }

   private void appendAddonStepEntries(List<AutismMacroEditorOverlay.StepPickerEntry> entries, boolean conditions) {
      for (autismclient.api.macro.MacroActionEntry entry : autismclient.api.macro.MacroActionRegistry.entries()) {
         if (!entry.hasPicker() || entry.isCondition() != conditions) continue;
         java.util.function.Supplier<autismclient.util.macro.MacroAction> factory = entry.factory();
         this.addStepPickerEntry(entries, entry.pickerCategory(), entry.pickerLabel(), entry.pickerTip(), () -> {
            autismclient.util.macro.MacroAction action;
            try {
               action = factory.get();
            } catch (Throwable t) {
               autismclient.AutismClientAddon.LOG.warn("[MacroActions] Addon step '{}' factory failed", entry.pickerLabel(), t);
               return;
            }
            if (action != null) this.addAction(action);
         });
      }
   }

   private void appendAddonPresetEntries(List<AutismMacroEditorOverlay.StepPickerEntry> entries) {
      for (autismclient.api.macro.MacroPresetRegistry.PresetEntry preset : autismclient.api.macro.MacroPresetRegistry.entries()) {
         java.util.function.Supplier<java.util.List<autismclient.util.macro.MacroAction>> builder = preset.builder();
         this.addStepPickerEntry(entries, preset.categoryId(), preset.label(), preset.tip(), () -> {

            java.util.List<autismclient.util.macro.MacroAction> stack;
            try {
               stack = builder.get();
            } catch (Throwable t) {
               autismclient.AutismClientAddon.LOG.warn("[MacroPresets] Addon preset '{}' failed to build", preset.label(), t);
               autismclient.util.AutismClientMessaging.sendPrefixed("§cAddon preset '" + preset.label() + "' failed.");
               return;
            }
            if (stack != null && !stack.isEmpty()) this.addActionStack(stack);
         });
      }
   }

   private static final int ADDON_PICKER_CATEGORY_COLOR = 0xFFAA66FF;
   private static final int STEP_PICKER_LABEL_WEIGHT_CAP = 88;

   private List<AutismMacroEditorOverlay.StepPickerCategory> buildStepPickerCategories(
      AutismMacroEditorOverlay.StepPickerMode mode, List<AutismMacroEditorOverlay.StepPickerEntry> entries
   ) {
      List<AutismMacroEditorOverlay.StepPickerCategory> categories = new ArrayList<>();
      Consumer<AutismMacroEditorOverlay.StepPickerCategory> addCategory = category -> {
         for (AutismMacroEditorOverlay.StepPickerEntry entry : entries) {
            if (entry.categoryId.equals(category.id)) {
               categories.add(category);
               return;
            }
         }
      };
      if (mode == AutismMacroEditorOverlay.StepPickerMode.ACTION) {
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("flow", "Flow", -11352981));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("movement", "Movement", -42406));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("interaction", "Interaction", -12268476));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("inventory", "Inventory", -11182));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("network", "Packets", -10835457));
      } else if (mode == AutismMacroEditorOverlay.StepPickerMode.PRESET) {
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("singleplayer", "Singleplayer", -12268476));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("dupe", "Duplication", -10835457));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("trade", "Trade", -11182));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("crash", "Crash", -4555521));
      } else {
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("player", "Player", -42406));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("inventory", "Inventory", -11182));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("world", "World", -11352981));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("gui_chat", "GUI / Chat", -11182));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("packets", "Packets", -10835457));
         addCategory.accept(new AutismMacroEditorOverlay.StepPickerCategory("sync", "Sync", -10835457));
      }

      java.util.Set<String> known = new java.util.HashSet<>();
      for (AutismMacroEditorOverlay.StepPickerCategory c : categories) known.add(c.id);
      java.util.LinkedHashSet<String> extra = new java.util.LinkedHashSet<>();
      for (AutismMacroEditorOverlay.StepPickerEntry entry : entries) {
         if (!known.contains(entry.categoryId)) extra.add(entry.categoryId);
      }
      for (String catId : extra) {
         autismclient.api.macro.MacroActionRegistry.ActionCategory actionCat =
            autismclient.api.macro.MacroActionRegistry.category(catId);
         String label;
         int color;
         if (actionCat != null) {
            label = actionCat.label();
            color = actionCat.color();
         } else {
            String preset = autismclient.api.macro.MacroPresetRegistry.categoryLabel(catId);
            label = preset != null ? preset : prettyCategoryLabel(catId);
            color = ADDON_PICKER_CATEGORY_COLOR;
         }
         categories.add(new AutismMacroEditorOverlay.StepPickerCategory(catId, label, color));
      }

      return categories;
   }

   private static String prettyCategoryLabel(String id) {
      if (id == null || id.isBlank()) return "Addon";
      if (id.startsWith("addon:")) {
         String addonId = id.substring("addon:".length());
         int slash = addonId.indexOf('/');
         if (slash >= 0) addonId = addonId.substring(0, slash);
         autismclient.addons.AddonManager.AddonReport report = autismclient.addons.AddonManager.report(addonId);
         if (report != null && report.name() != null && !report.name().isBlank()) {
            return autismclient.addons.AddonManager.compactAddonName(report.name(), addonId);
         }
         return autismclient.addons.AddonManager.compactAddonName(addonId, "Addon");
      }
      String[] parts = id.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) {
         if (part.isEmpty()) continue;
         if (sb.length() > 0) sb.append(' ');
         sb.append(Character.toUpperCase(part.charAt(0)));
         if (part.length() > 1) sb.append(part.substring(1));
      }
      return sb.length() == 0 ? "Addon" : sb.toString();
   }

   private void openStepPicker(AutismMacroEditorOverlay.StepPickerMode mode) {
      if (this.isStepListLockedForRun()) return;
      if (mode != null) {
         this.clearStepDragState();
         this.stepPickerMode = mode;
         List<AutismMacroEditorOverlay.StepPickerEntry> entries = this.buildStepPickerEntries(mode);
         this.stepPickerSelector.setItems(entries);
         this.stepPickerSelector.setQuery("");
         this.stepPickerSearchField = new AutismChatField(MC, this.textRenderer, 0, 0, 1, 16, false);
         this.stepPickerSearchField.setPlaceholder(Component.literal("Search..."));
         this.stepPickerSearchField.setChangedListener(ignored -> this.updateStepPickerFilter());
         this.updateStepPickerFilter();
         this.stepPickerScrollOffset = 0;
         this.isBindingKey = false;
         if (this.nameField != null) {
            this.nameField.setFocused(false);
         }

         if (this.loopCountField != null) {
            this.loopCountField.setFocused(false);
         }
         AutismOverlayManager manager = AutismOverlayManager.get();
         manager.register(this.stepPickerOverlay);
         manager.bringToFront(this.stepPickerOverlay);
      }
   }

   private List<AutismMacroEditorOverlay.StepPickerEntry> getVisibleStepPickerEntries() {
      if (!this.isStepPickerOpen()) return Collections.emptyList();
      return this.stepPickerEntries;
   }

   private List<AutismMacroEditorOverlay.StepPickerEntry> getStepPickerEntriesForCategory(String categoryId) {
      if (categoryId == null || categoryId.isEmpty()) return Collections.emptyList();
      return this.stepPickerEntriesByCategory.getOrDefault(categoryId, Collections.emptyList());
   }

   private void updateStepPickerFilter() {
      String query = this.stepPickerSearchField == null ? "" : this.stepPickerSearchField.getText();
      this.stepPickerSelector.setQuery(query);
      this.stepPickerEntries = this.stepPickerSelector.items();
      this.stepPickerCategories = this.buildStepPickerCategories(this.stepPickerMode, this.stepPickerEntries);
      Map<String, List<AutismMacroEditorOverlay.StepPickerEntry>> grouped = new LinkedHashMap<>();
      for (AutismMacroEditorOverlay.StepPickerEntry entry : this.stepPickerEntries) {
         grouped.computeIfAbsent(entry.categoryId, ignored -> new ArrayList<>()).add(entry);
      }
      Map<String, List<AutismMacroEditorOverlay.StepPickerEntry>> frozen = new LinkedHashMap<>();
      for (Map.Entry<String, List<AutismMacroEditorOverlay.StepPickerEntry>> entry : grouped.entrySet()) {
         frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      this.stepPickerEntriesByCategory = Map.copyOf(frozen);
      this.pickerLayoutEpoch++;
      this.stepPickerScrollOffset = 0;
   }

   private void syncStepPickerSearchFieldBounds() {
      if (this.stepPickerSearchField == null || !this.isStepPickerOpen()) return;
      int pickerX = this.getStepPickerX();
      int pickerY = this.getStepPickerY();
      int pickerW = this.getStepPickerWidth();
      this.stepPickerSearchField.setX(pickerX + 6);
      this.stepPickerSearchField.setY(pickerY + 21);
      this.stepPickerSearchField.setWidth(Math.max(1, pickerW - 12));
      this.stepPickerSearchField.setHeight(16);
   }

   private int getStepPickerColumnCount(int gridW) {
      int columnGap = 6;
      int minColumnW = 128;
      int maxColumns = 1;
      return Math.max(1, Math.min(maxColumns, (gridW + columnGap) / (minColumnW + columnGap)));
   }

   private int getStepPickerBlockHeight(List<AutismMacroEditorOverlay.StepPickerEntry> entries, int columnW) {
      int cardGap = 3;
      int rowGap = 1;
      int cardH = 14;
      int sectionLabelH = this.stepPickerSectionLabelHeight();
      int buttonW = Math.max(1, (columnW - cardGap) / 2);
      int buttonX = 0;
      int buttonY = sectionLabelH + 2;

      for (AutismMacroEditorOverlay.StepPickerEntry entry : entries) {
         if (buttonX > 0 && buttonX + buttonW > columnW) {
            buttonX = 0;
            buttonY += cardH + rowGap;
         }
         buttonX += buttonW + cardGap;
      }

      return entries.isEmpty() ? sectionLabelH : buttonY + cardH;
   }

   private List<AutismMacroEditorOverlay.StepPickerSectionLayout> buildStepPickerLayouts(int gridX, int startY, int gridW) {
      int cardGap = 3;
      int rowGap = 1;
      int cardH = 14;
      int sectionLabelH = this.stepPickerSectionLabelHeight();
      int sectionGap = 3;
      int columnGap = 6;
      int columnCount = this.getStepPickerColumnCount(gridW);
      int columnW = Math.max(1, (gridW - columnGap * (columnCount - 1)) / columnCount);
      int targetColumnHeight = Math.max(96, (MC.getWindow() != null ? AutismUiScale.getVirtualScreenHeight() : 320) - 96);
      List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts = new ArrayList<>();
      int column = 0;
      int[] columnY = new int[columnCount];
      Arrays.fill(columnY, startY);

      for (AutismMacroEditorOverlay.StepPickerCategory category : this.stepPickerCategories) {
         List<AutismMacroEditorOverlay.StepPickerEntry> categoryEntries = this.getStepPickerEntriesForCategory(category.id);
         if (!categoryEntries.isEmpty()) {
            int blockHeight = this.getStepPickerBlockHeight(categoryEntries, columnW);
            if (column < columnCount - 1 && columnY[column] > startY && columnY[column] + blockHeight > startY + targetColumnHeight) {
               column++;
            }
            int sectionX = gridX + column * (columnW + columnGap);
            int sectionY = columnY[column];
            int firstRowY = sectionY + sectionLabelH + 2;
            List<AutismMacroEditorOverlay.StepPickerButtonLayout> buttons =
               this.layoutSectionButtons(categoryEntries, sectionX, firstRowY, columnW, cardGap, cardH, rowGap);

            int bottomY = buttons.isEmpty() ? sectionY + sectionLabelH : buttons.get(buttons.size() - 1).y + cardH;
            layouts.add(new AutismMacroEditorOverlay.StepPickerSectionLayout(category, sectionX, sectionY, columnW, buttons, bottomY));
            columnY[column] = bottomY + sectionGap;
         }
      }

      return layouts;
   }

   private List<AutismMacroEditorOverlay.StepPickerButtonLayout> layoutSectionButtons(
         List<AutismMacroEditorOverlay.StepPickerEntry> entries,
         int sectionX, int firstRowY, int columnW, int cardGap, int cardH, int rowGap) {
      List<AutismMacroEditorOverlay.StepPickerButtonLayout> buttons = new ArrayList<>();
      if (entries == null || entries.isEmpty()) return buttons;

      List<AutismMacroEditorOverlay.StepPickerEntry> ordered = this.pairShortAndLongEntries(entries);
      int minBtn = Math.min(34, Math.max(1, (columnW - cardGap) / 2));
      int y = firstRowY;

      for (int i = 0; i < ordered.size(); i += 2) {
         if (i + 1 >= ordered.size()) {

            buttons.add(new AutismMacroEditorOverlay.StepPickerButtonLayout(ordered.get(i), sectionX, y, columnW, cardH));
         } else {
            int avail = Math.max(2 * minBtn, columnW - cardGap);
            int n0 = this.pickerLabelWidth(ordered.get(i).label) + 10;
            int n1 = this.pickerLabelWidth(ordered.get(i + 1).label) + 10;
            int total = Math.max(1, n0 + n1);
            int w0 = Math.round(avail * (float) n0 / total);
            w0 = Math.max(minBtn, Math.min(avail - minBtn, w0));
            int w1 = avail - w0;
            buttons.add(new AutismMacroEditorOverlay.StepPickerButtonLayout(ordered.get(i), sectionX, y, w0, cardH));
            buttons.add(new AutismMacroEditorOverlay.StepPickerButtonLayout(ordered.get(i + 1), sectionX + w0 + cardGap, y, w1, cardH));
         }
         y += cardH + rowGap;
      }
      return buttons;
   }

   private List<AutismMacroEditorOverlay.StepPickerEntry> pairShortAndLongEntries(
         List<AutismMacroEditorOverlay.StepPickerEntry> entries) {
      if (entries.size() <= 2) return new ArrayList<>(entries);
      List<AutismMacroEditorOverlay.StepPickerEntry> sorted = new ArrayList<>(entries);
      sorted.sort(java.util.Comparator.comparingInt(e -> this.pickerLabelWidth(e.label)));
      List<AutismMacroEditorOverlay.StepPickerEntry> ordered = new ArrayList<>(entries.size());
      int lo = 0;
      int hi = sorted.size() - 1;
      while (lo <= hi) {
         if (lo == hi) {
            ordered.add(sorted.get(lo));
            break;
         }
         ordered.add(sorted.get(lo++));
         ordered.add(sorted.get(hi--));
      }
      return ordered;
   }

   private int getStepPickerContentHeight(List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts) {
      int maxBottom = 0;
      if (layouts != null) {
         for (AutismMacroEditorOverlay.StepPickerSectionLayout layout : layouts) {
            maxBottom = Math.max(maxBottom, layout.bottomY);
         }
      }
      return Math.max(0, maxBottom);
   }

   private int getStepPickerUsedWidth(List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts) {
      if (layouts != null && !layouts.isEmpty()) {
         int maxRight = 0;
         int minLeft = Integer.MAX_VALUE;
         Identifier pickerFont = this.theme.fontFor(UiTone.LABEL);
         int pickerColor = this.theme.color(UiTone.BODY);

         for (AutismMacroEditorOverlay.StepPickerSectionLayout section : layouts) {
            minLeft = Math.min(minLeft, section.x);
            maxRight = Math.max(maxRight, section.x + Math.min(section.width, UiText.width(this.textRenderer, section.category.label, pickerFont, pickerColor)));

            for (AutismMacroEditorOverlay.StepPickerButtonLayout button : section.buttons) {
               minLeft = Math.min(minLeft, button.x);
               maxRight = Math.max(maxRight, button.x + button.width);
            }
         }

         if (minLeft == Integer.MAX_VALUE) {
            minLeft = 0;
         }

         return Math.max(118, maxRight - minLeft);
      } else {
         return 118;
      }
   }

   @Override
   public int getMinWidth() {
      return this.defaultPanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.minimumPanelHeight();
   }

   @Override
   public AutismWindowLayout getBounds() {
      return new AutismWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(AutismWindowLayout bounds) {
      if (bounds != null) {
         int requestedWidth = bounds.width == 408 ? this.defaultPanelWidth() : bounds.width;
         AutismWindowLayout clamped = this.clampToScreen(
            this, new AutismWindowLayout(bounds.x, bounds.y, requestedWidth, bounds.height, bounds.visible, bounds.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.PANEL_WIDTH = clamped.width;
         this.PANEL_HEIGHT = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
         if (this.macro != null) {
            this.recreateComponents();
         }
      }
   }

   private CompoundTag captureMacroSnapshot() {
      return this.macro == null ? null : this.macro.toTag();
   }

   private static boolean snapshotsEqual(CompoundTag a, CompoundTag b) {
      if (a == b) {
         return true;
      } else {
         return a != null && b != null ? a.equals(b) : false;
      }
   }

   private MacroHistoryState captureHistoryState() {
      CompoundTag snapshot = this.captureMacroSnapshot();
      if (snapshot == null) return null;
      List<Integer> selectedIndices = new ArrayList<>();
      if (this.macro != null && !this.stepSelection.isEmpty()) {
         for (int i = 0; i < this.macro.actions.size(); i++) {
            if (this.isStepIndexSelected(i)) selectedIndices.add(i);
         }
      }
      return new MacroHistoryState(snapshot, List.copyOf(selectedIndices));
   }

   private void pushSnapshot(Deque<MacroHistoryState> history, MacroHistoryState state) {
      if (history == null || state == null || state.snapshot == null) return;
      MacroHistoryState head = history.peekFirst();
      if (head != null && snapshotsEqual(head.snapshot, state.snapshot)) {
         history.removeFirst();
      }
      history.addFirst(state);
      while (history.size() > 30) {
         history.removeLast();
      }
   }

   private void resetHistoryState() {
      this.undoHistory.clear();
      this.redoHistory.clear();
   }

   private void pushStructuralHistoryStep() {
      this.pushSnapshot(this.undoHistory, this.captureHistoryState());
      this.redoHistory.clear();
   }

   private boolean canUndoHistory() {
      return this.macro != null && !this.undoHistory.isEmpty();
   }

   private boolean canRedoHistory() {
      return this.macro != null && !this.redoHistory.isEmpty();
   }

   private void restoreHistorySnapshot(MacroHistoryState state) {
      if (this.macro != null && state != null && state.snapshot != null) {
         this.macro.fromTag(state.snapshot);
         this.handleMacroActionsChanged();
         this.stepSelection.clear();
         for (int index : state.selectedIndices) {
            if (index >= 0 && index < this.macro.actions.size()) {
               this.stepSelection.addStableId(this.stepRowStableId(this.macro.actions.get(index)));
            }
         }
         this.pruneStepSelection();
      }
   }

   private void performUndo() {
      if (this.isStepListLockedForRun()) return;
      if (this.canUndoHistory()) {
         MacroHistoryState previous = this.undoHistory.pollFirst();
         if (previous != null) {
            this.pushSnapshot(this.redoHistory, this.captureHistoryState());
            this.restoreHistorySnapshot(previous);
         }
      }
   }

   private void performRedo() {
      if (this.isStepListLockedForRun()) return;
      if (this.canRedoHistory()) {
         MacroHistoryState next = this.redoHistory.pollFirst();
         if (next != null) {
            this.pushSnapshot(this.undoHistory, this.captureHistoryState());
            this.restoreHistorySnapshot(next);
         }
      }
   }

   private record MacroHistoryState(CompoundTag snapshot, List<Integer> selectedIndices) {
   }

   public void open(AutismMacro macroToEdit) {
      this.open(macroToEdit, false);
   }

   public void open(AutismMacro macroToEdit, boolean reopenMacroListOnClose) {
      this.open(macroToEdit, reopenMacroListOnClose, EditorContext.NORMAL, null, false);
   }

   public void openForImport(AutismMacro sharedMacro) {
      this.open(sharedMacro, false, EditorContext.NORMAL, null, true);
   }

   public void openForJoinMacroMenu(AutismMacro macroToEdit, Consumer<AutismMacro> onSaveComplete) {
      this.open(macroToEdit, false, EditorContext.JOIN_MACRO_MENU, onSaveComplete, false);
   }

   public void openForMulti(AutismMacro macroToEdit, Consumer<AutismMacro> onSaveComplete) {
      this.open(macroToEdit, false, EditorContext.NORMAL, onSaveComplete, false);
   }

   private void open(AutismMacro macroToEdit, boolean reopenMacroListOnClose, EditorContext context, Consumer<AutismMacro> onSaveComplete, boolean forceNew) {
      if (this.visible) {
         this.closeStepPicker();
         this.isBindingKey = false;
      }

      for (IAutismOverlay overlay : AutismOverlayManager.get().getOverlays()) {
         if (overlay instanceof AutismMacroListOverlay macroListOverlay) {
            macroListOverlay.setVisible(false);
         }
      }

      this.originalMacro = null;
      this.macro = null;
      this.reopenMacroListOnClose = reopenMacroListOnClose;
      this.editorContext = context == null ? EditorContext.NORMAL : context;
      this.saveCompleteCallback = onSaveComplete;
      this.visible = true;
      AutismSharedState.get().setMacroEditorVisible(true);
      ActionEditorOverlay.getSharedOverlay().setWorldCaptureAllowed(this.allowsWorldCaptureActions());
      if (macroToEdit == null) {
         this.macro = new AutismMacro("");
         this.originalMacro = null;
         this.isNew = true;
      } else if (forceNew) {

         this.originalMacro = null;
         this.macro = new AutismMacro();
         this.macro.fromTag(macroToEdit.toTag());
         this.macro.name = "";
         this.macro.keyCode = -1;
         this.isNew = true;
      } else {
         this.originalMacro = macroToEdit;
         this.macro = new AutismMacro();
         this.macro.fromTag(macroToEdit.toTag());
         this.isNew = false;
      }

      AutismSharedState.get().setEditingMacro(this.macro);
      this.currentRunId = -1L;
      this.invalidateRunningMacroCache();
      this.tempSavedForRun = false;
      this.tempRunCreatedNewMacro = false;
      this.originalMacroSnapshot = null;
      this.lastStepListLockedForRun = false;
      this.resetHistoryState();
      this.clearStepDragState();
      this.clearStepSelection();
      this.scrollOffset = 0;
      if (!this.macro.loop) {
         this.loopMode = 0;
      } else if (this.macro.loopCount == -1) {
         this.loopMode = 2;
      } else {
         this.loopMode = 1;
      }

      this.resetStepRowAnimationState();
      this.init();
      this.windowNode.restoreShowBody(!this.collapsed);
      AutismOverlayManager.get().bringToFront(this);
   }

   public void cancel() {
      this.stopTemporaryEditedMacro();
      this.cleanupTemporaryRunSaveState();
      this.close();
   }

   private boolean isJoinMacroMenuContext() {
      return this.editorContext == EditorContext.JOIN_MACRO_MENU;
   }

   private boolean allowsWorldCaptureActions() {
      return !this.configurationOnly && !this.isJoinMacroMenuContext();
   }

   private void cleanupTemporaryRunSaveState() {
      if (!this.tempSavedForRun) return;
      if (this.tempRunCreatedNewMacro) {
         String name = this.macro == null ? "" : this.macro.name;
         AutismMacro savedMacro = name == null || name.isBlank() ? null : AutismMacroManager.get().get(name);
         if (savedMacro == null && this.originalMacro != null) savedMacro = this.originalMacro;
         if (savedMacro != null) {
            AutismMacroManager.get().getAll().remove(savedMacro);
            AutismMacroManager.get().save();
         }
      } else if (this.originalMacro != null && this.originalMacroSnapshot != null) {
         this.originalMacro.fromTag(this.originalMacroSnapshot);
         AutismMacroManager.get().save();
      }
      this.tempSavedForRun = false;
      this.tempRunCreatedNewMacro = false;
      this.originalMacroSnapshot = null;
      this.lastStepListLockedForRun = false;
   }

   private void stopTemporaryEditedMacro() {
      AutismMacro runningMacro = this.getRunningEditedMacro();
      if (runningMacro != null) {
         if (this.tempSavedForRun || !this.isPersistedMacro(runningMacro)) {
            MacroExecutor.stopMacro(runningMacro);
            return;
         }
      }
      if (this.macro != null && this.macro.name != null && !this.macro.name.isBlank()) {
         AutismMacro persisted = AutismMacroManager.get().get(this.macro.name);
         if (this.tempSavedForRun || persisted == null) {
            MacroExecutor.stopMacro(this.macro.name);
         }
      }
      if (this.originalMacro != null && this.originalMacro.name != null && !this.originalMacro.name.isBlank()) {
         AutismMacro persisted = AutismMacroManager.get().get(this.originalMacro.name);
         if (this.tempSavedForRun || persisted == null) {
            MacroExecutor.stopMacro(this.originalMacro.name);
         }
      }
   }

   private boolean isPersistedMacro(AutismMacro candidate) {
      if (candidate == null || candidate.name == null || candidate.name.isBlank()) return false;
      AutismMacro persisted = AutismMacroManager.get().get(candidate.name);
      return persisted == candidate;
   }

   public void close() {
      this.stopTemporaryEditedMacro();
      if (this.tempSavedForRun) {
         this.cleanupTemporaryRunSaveState();
      }
      boolean shouldReopenMacroList = this.reopenMacroListOnClose;
      this.closeMacroActionEditor();
      this.visible = false;
      AutismSharedState.get().setMacroEditorVisible(false);
      AutismSharedState.get().setEditingMacro(null);
      this.macro = null;
      this.originalMacro = null;
      this.currentRunId = -1L;
      this.invalidateRunningMacroCache();
      this.tempSavedForRun = false;
      this.tempRunCreatedNewMacro = false;
      this.originalMacroSnapshot = null;
      this.reopenMacroListOnClose = false;
      this.editorContext = EditorContext.NORMAL;
      this.saveCompleteCallback = null;
      ActionEditorOverlay.getSharedOverlay().setWorldCaptureAllowed(true);
      this.closeStepPicker();
      this.isBindingKey = false;
      this.resetHistoryState();
      this.clearStepDragState();
      this.clearStepSelection();
      this.resetStepRowAnimationState();

      if (shouldReopenMacroList) {
         for (IAutismOverlay overlay : AutismOverlayManager.get().getOverlays()) {
            if (overlay instanceof AutismMacroListOverlay macroListOverlay) {
               macroListOverlay.setVisible(true);
               AutismOverlayManager.get().bringToFront(macroListOverlay);
               break;
            }
         }
      }
   }

   private boolean isEditingRunningMacro() {

      this.refreshRunningMacroCache();
      return this.cachedEditingRunningMacro;
   }

   private boolean isStepListLockedForRun() {

      this.refreshRunningMacroCache();
      if (!this.cachedEditingRunningMacro) return false;
      return System.currentTimeMillis() - this.runLockArmedAtMs >= RUN_LOCK_GRACE_MS;
   }

   private void invalidateRunningMacroCache() {
      this.cachedRunStateRevision = Long.MIN_VALUE;
      this.cachedRunStateRunId = Long.MIN_VALUE;
   }

   private void refreshRunningMacroCache() {
      long revision = MacroExecutor.runStateRevision();
      long runId = this.currentRunId;
      if (this.cachedRunStateRevision == revision && this.cachedRunStateRunId == runId) {
         return;
      }

      boolean running = runId >= 0L && MacroExecutor.isRunActive(runId);
      if (!running && runId >= 0L) {
         this.currentRunId = -1L;
         runId = -1L;
      }

      if (!running && this.getRunningEditedMacro() != null) {
         running = true;
      }
      this.cachedRunStateRevision = revision;
      this.cachedRunStateRunId = runId;
      this.cachedEditingRunningMacro = running;
   }

   private void lockStepListForRun() {
      this.closeStepPicker();
      this.closeDetachedActionEditor();
      this.stepCtxMenu.close();
      this.clearStepSelection();
      this.clearStepDragState();
      if (this.nameField != null) this.nameField.setFocused(false);
      if (this.loopCountField != null) this.loopCountField.setFocused(false);
      this.isBindingKey = false;
   }

   private void refreshStepListRunLockState() {
      boolean locked = this.isStepListLockedForRun();
      if (locked == this.lastStepListLockedForRun) return;
      this.lastStepListLockedForRun = locked;
      if (locked) {
         this.lockStepListForRun();
      } else {
         this.clearStepDragState();
      }
      this.recreateComponents();
   }

   private AutismMacro getRunningEditedMacro() {
      if (this.originalMacro != null && MacroExecutor.isMacroRunning(this.originalMacro)) return this.originalMacro;
      if (this.macro != null && MacroExecutor.isMacroRunning(this.macro)) return this.macro;
      if (this.macro != null && this.macro.name != null && !this.macro.name.isBlank() && MacroExecutor.isMacroRunning(this.macro.name)) return this.macro;
      if (this.originalMacro != null
         && this.originalMacro.name != null
         && !this.originalMacro.name.isBlank()
         && MacroExecutor.isMacroRunning(this.originalMacro.name)) {
         return this.originalMacro;
      }
      return null;
   }

   private void handleMacroActionsChanged() {
      if (this.macro != null) {
         this.closeDetachedActionEditor();
         if (this.macro.actions.isEmpty() && this.isEditingRunningMacro()) {
            MacroExecutor.stopMacro(this.macro.name);
         }

         this.syncStepRowAnimations(true);
         this.pruneStepSelection();
         this.clampStepListScrollOffset();
         this.clearStepDragState();
         this.recreateComponents();
      }
   }

   private void closeDetachedActionEditor() {
      ActionEditorOverlay overlay = ActionEditorOverlay.getSharedOverlayIfExists();
      if (overlay != null && this.macro != null) {
         overlay.closeIfEditingAny(this.macro.actions);
      }
   }

   private void closeMacroActionEditor() {
      ActionEditorOverlay overlay = ActionEditorOverlay.getSharedOverlayIfExists();
      if (overlay != null && this.macro != null) {
         overlay.closeIfEditingAny(List.of());
      }
   }

   private static String getPlayerInfoName(PlayerInfo entry) {
      if (entry != null && entry.getProfile() != null) {
         String name = entry.getProfile().name();
         return name == null ? "" : name.trim();
      } else {
         return "";
      }
   }

   private static <E extends Enum<E>> E cycleEnum(E current, int step) {
      E[] values = (E[])current.getDeclaringClass().getEnumConstants();
      int index = (current.ordinal() + step) % values.length;
      if (index < 0) {
         index += values.length;
      }

      return values[index];
   }

   private static double cycleRotateSpeed(double current, int step) {
      double[] speeds = new double[]{1.0, 2.0, 3.0, 5.0, 10.0};
      int index = 0;

      for (int i = 0; i < speeds.length; i++) {
         if (current <= speeds[i] + 1.0E-4) {
            index = i;
            break;
         }

         index = speeds.length - 1;
      }

      index = (index + step) % speeds.length;
      if (index < 0) {
         index += speeds.length;
      }

      return speeds[index];
   }

   public boolean isEditingMacro(AutismMacro macroToCheck) {
      return macroToCheck != null && this.originalMacro != null
         ? this.originalMacro == macroToCheck || this.originalMacro.name != null && this.originalMacro.name.equals(macroToCheck.name)
         : false;
   }

   public void restorePosition() {
      AutismSharedState shared = AutismSharedState.get();
      this.panelX = shared.getMacroEditorPanelX();
      this.panelY = shared.getMacroEditorPanelY();
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      AutismSharedState.get().setMacroEditorVisible(visible);
      if (visible) {
         this.windowNode.restoreShowBody(!this.collapsed);
         AutismOverlayManager.get().bringToFront(this);
      }
   }

   private static boolean reopenEditorOnJoin;
   private static boolean reopenActionEditorOnJoin;

   public static void onPlayDisconnect() {
      reopenEditorOnJoin = getSharedOverlay().isVisible();
      autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
         autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
      reopenActionEditorOnJoin = actionEditor != null && actionEditor.isVisible();
   }

   public static void onPlayJoin() {
      if (!reopenEditorOnJoin && !reopenActionEditorOnJoin) return;
      boolean editor = reopenEditorOnJoin;
      boolean action = reopenActionEditorOnJoin;
      reopenEditorOnJoin = false;
      reopenActionEditorOnJoin = false;
      if (editor) getSharedOverlay().setVisible(true);
      autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
         autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
      if (action && actionEditor != null) actionEditor.setVisible(true);
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean collapsed) {
      if (this.collapsed == collapsed) {
         return;
      }
      this.collapsed = collapsed;
      this.isWindowDragging = false;
      this.headerDragMoved = false;
      this.clearStepDragState();
      if (this.nameField != null) {
         this.nameField.setFocused(false);
      }

      if (this.loopCountField != null) {
         this.loopCountField.setFocused(false);
      }

      if (collapsed) {
         this.closeStepPicker();
         this.clearHiddenInteractionState();
      }

      this.windowNode.syncShowBody(!collapsed);
      this.saveState();
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else if (!this.uiDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.uiDropdowns)) {
         return true;
      } else {
         int height = this.getRenderedPanelHeight();
         if (mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + height) {
            return true;
         } else {
            if (this.isStepPickerOpen()) {
               int pickerX = this.getStepPickerX();
               int pickerY = this.getStepPickerY();
               int pickerW = this.getStepPickerWidth();
               int pickerH = this.getStepPickerHeight();
               if (mouseX >= pickerX && mouseX <= pickerX + pickerW && mouseY >= pickerY && mouseY <= pickerY + pickerH) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         return mouseX >= this.panelX
               && mouseX <= this.panelX + this.PANEL_WIDTH
               && mouseY >= this.panelY
               && mouseY <= this.panelY + this.theme.headerHeight()
               && !this.isOverCloseButtonUi(mouseX, mouseY);
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      if (!this.visible) {
         return false;
      } else {
         return this.nameField != null && this.nameField.isFocused()
            || this.loopCountField != null && this.loopCountField.isFocused()
            || this.stepPickerSearchField != null && this.stepPickerSearchField.isFocused();
      }
   }

   @Override
   public void clearTextFieldFocus() {
      if (this.nameField != null) this.nameField.setFocused(false);
      if (this.loopCountField != null) this.loopCountField.setFocused(false);
      if (this.stepPickerSearchField != null) this.stepPickerSearchField.setFocused(false);
   }

   @Override
   public boolean wantsKeyboardCapture() {
      return this.visible && this.isBindingKey;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      boolean dropdownReleased = CompactDropdown.mouseReleased(this.uiDropdowns);
      if (this.stepPickerSearchField != null && this.stepPickerSearchField.mouseReleased(mouseX, mouseY, button)) return true;
      if (this.nameField != null && this.nameField.mouseReleased(mouseX, mouseY, button)) return true;
      if (this.loopCountField != null && this.loopCountField.mouseReleased(mouseX, mouseY, button)) return true;
      if (button == 0 && (this.draggingIndex >= 0 || this.pressedStepIndex >= 0)) {
         this.finishStepDrag();
         return true;
      } else if (button == 0 && this.activeScrollbarDrag != 0) {
         this.activeScrollbarDrag = 0;
         this.saveState();
         return true;
      } else if (dropdownReleased) {
         return true;
      } else if (button != 0) {
         return false;
      } else {
         boolean moved = this.headerDragMoved
            || Math.abs(mouseX - this.headerPressMouseX) >= 3.0
            || Math.abs(mouseY - this.headerPressMouseY) >= 3.0
            || this.panelX != this.headerPressPanelX
            || this.panelY != this.headerPressPanelY;
         this.isWindowDragging = false;
         this.isWindowResizing = false;
         if (!moved && this.isOverHeaderUi(mouseX, mouseY) && !this.isOverCloseButtonUi(mouseX, mouseY)) {
            this.setCollapsed(!this.collapsed);
         }

         this.saveState();
         this.headerDragMoved = false;
         return true;
      }
   }

   public void saveState() {
      AutismSharedState shared = AutismSharedState.get();
      shared.setMacroEditorVisible(this.visible);
      shared.setMacroEditorPanelX(this.panelX);
      shared.setMacroEditorPanelY(this.panelY);
      shared.setEditingMacro(this.macro);
      this.saveLayout();
   }

   public void restoreState() {
      AutismSharedState shared = AutismSharedState.get();
      this.restoreLayout();
      this.visible = shared.isMacroEditorVisible();
      this.panelX = shared.getMacroEditorPanelX();
      this.panelY = shared.getMacroEditorPanelY();
      this.macro = shared.getEditingMacro();
      this.windowNode.restoreShowBody(!this.collapsed);
      if (this.visible && this.macro != null) {
         this.resetStepRowAnimationState();
         this.recreateComponents();
      }
   }

   private void init() {
      if (MC.getWindow() != null) {
         int screenWidth = AutismUiScale.getVirtualScreenWidth();
         int screenHeight = AutismUiScale.getVirtualScreenHeight();
         if (this.panelX == 0 && this.panelY == 0) {
            this.panelX = screenWidth - this.PANEL_WIDTH - this.defaultScreenInsetX();
            this.panelY = this.defaultScreenInsetY();
         }

         this.recreateComponents();
      }
   }

   private boolean isValidName(String name) {
      if (name != null && !name.trim().isEmpty()) {
         return !this.isNew && name.equals(AutismMacroManager.get().get(name) != null ? name : "") ? true : AutismMacroManager.get().get(name) == null;
      } else {
         return false;
      }
   }

   private void recreateComponents() {
      this.btnLabels.clear();
      this.btnBounds.clear();
      this.btnActions.clear();
      this.btnSecondaryActions.clear();
      this.btnEnabled.clear();
      this.uiDropdowns.clear();
      this.runBtnIndex = -1;
      boolean stepListLocked = this.isStepListLockedForRun();

      this.lastStepListLockedForRun = stepListLocked;
      int bh = this.editorButtonHeight();
      int gap = this.editorControlGap();
      int row1Y = this.panelY + this.topRowOffset();
      int deleteTopW = !this.isNew ? this.deleteButtonWidth() : 0;
      int nameFieldW = this.PANEL_WIDTH - (this.contentInsetX() * 2) - (deleteTopW > 0 ? deleteTopW + gap : 0);
      if (this.nameField == null) {
         this.nameField = new AutismChatField(MC, this.textRenderer, this.panelX + this.contentInsetX(), row1Y, nameFieldW, this.textFieldHeight(), false);
         this.nameField.setPlaceholder(Component.literal("Macro Name"));
         this.nameField.setChangedListener(text -> {
            this.macro.name = text;
            this.updateSaveButtonState();
         });
      }

      this.nameField.setX(this.panelX + this.contentInsetX());
      this.nameField.setY(row1Y);
      this.nameField.setWidth(nameFieldW);
      this.nameField.setText(this.macro.name);
      if (!this.isNew) {
         this.addBtn("Delete", this.panelX + this.PANEL_WIDTH - this.contentInsetX() - deleteTopW, row1Y, deleteTopW, bh, () -> {
            if (this.isJoinMacroMenuContext()
               && this.macro != null
               && this.macro.name != null
               && this.macro.name.equalsIgnoreCase(AutismJoinMacroController.selectedMacroName())) {
               AutismJoinMacroController.setSelectedMacro("");
            }
            AutismMacroManager.get().delete(this.macro);
            this.close();
         }, !stepListLocked);
      }
      int row2Y = row1Y + this.secondRowGap();
      int bx = this.panelX + this.contentInsetX();
      String keyText = this.macro.keyCode == -1 ? "Bind Key" : "Key: " + AutismBindUtil.getBindName(this.macro.keyCode);

      if (this.isBindingKey) {
         keyText = "Press Key/Mouse...";
      }

      int keyW = UiText.width(this.textRenderer, keyText, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY)) + this.buttonTextPadding();
      this.addBtn(keyText, bx, row2Y, keyW, bh, () -> {
         this.isBindingKey = !this.isBindingKey;
         this.recreateComponents();
      }, !stepListLocked);
      bx += keyW + gap;
      if (!this.isJoinMacroMenuContext() && !this.configurationOnly) {
         this.addBtn(this.runButtonLabel(this.isEditingRunningMacro()), bx, row2Y, this.runButtonWidth(), bh,
            this::handleRunButton, this.isEditingRunningMacro() || this.canRunMacro());
         this.runBtnIndex = this.btnLabels.size() - 1;
         bx += this.runButtonWidth() + gap;
      }

      this.lastMultiRunAvailable = this.multiRunAvailable();
      this.lastPovRunActive = this.povRunActive();
      if (!this.isJoinMacroMenuContext() && this.lastMultiRunAvailable) {
         int multiRunW = UiText.width(this.textRenderer, "Multi", this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY))
            + this.buttonTextPadding() + 11;
         this.addBtn("Multi", bx, row2Y, multiRunW, bh, this::handleRunForMultiButton);
         bx += multiRunW + gap;
      }
      if (!this.configurationOnly && AutismLANSync.getInstance().isInSession() && !this.isNew && this.macro != null) {
         this.addBtn("LAN", bx, row2Y, this.lanButtonWidth(), bh, () -> {
            if (this.macro != null) {
               AutismLANSync.getInstance().executeMacroSynchronized(this.macro.name);
            }
         }, !stepListLocked);
         bx += this.lanButtonWidth() + gap;
      }

      java.util.List<String> loopOptions = java.util.List.of("Once", "Loop #", "Loop Inf");
      int loopW = Math.max(
              UiText.width(this.textRenderer, "Loop Inf", this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY)),
              UiText.width(this.textRenderer, "Loop #", this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY))
      ) + this.buttonTextPadding();

      CompactDropdown loopDropdown = new CompactDropdown(bx, row2Y, loopW, bh, loopOptions,
              Math.max(0, Math.min(this.loopMode, loopOptions.size() - 1)), idx -> {
         this.loopMode = Math.max(0, Math.min(idx, loopOptions.size() - 1));
         if (this.loopMode != 1 && this.loopCountField != null) {
            this.loopCountField.setFocused(false);
         }
         this.updateLoopState();
         this.recreateComponents();
      });
      loopDropdown.active = !stepListLocked;
      this.uiDropdowns.add(loopDropdown);
      bx += loopW + gap;
      if (this.loopMode == 1) {
         if (this.loopCountField == null) {
            this.loopCountField = new AutismChatField(MC, this.textRenderer, bx, row2Y + 1, this.loopCountFieldWidth(), this.loopCountFieldHeight(), false);
            this.loopCountField.setChangedListener(text -> {
               try {
                  this.macro.loopCount = Integer.parseInt(text.replaceAll("[^0-9-]", ""));
               } catch (NumberFormatException var3x) {  }
            });
         }

         this.loopCountField.setX(Math.min(bx, this.panelX + this.PANEL_WIDTH - this.contentInsetX() - this.loopCountFieldWidth()));
         this.loopCountField.setY(row2Y + 1);
         this.loopCountField.setWidth(this.loopCountFieldWidth());
         this.loopCountField.setText(String.valueOf(this.macro.loopCount == -1 ? 1 : this.macro.loopCount));
      }

      int builderButtonY = this.getStepBuilderButtonY();
      int builderGap = this.builderButtonGap();
      int builderTotalWidth = this.PANEL_WIDTH - (this.contentInsetX() * 2) - builderGap * 2;
      int conditionalExtra = Math.min(18, Math.max(0, builderTotalWidth / 14));
      int sideBuilderWidth = Math.max(1, (builderTotalWidth - conditionalExtra) / 3);
      int conditionalBuilderWidth = Math.max(1, builderTotalWidth - sideBuilderWidth * 2);
      int builderX = this.panelX + this.contentInsetX();
      this.addBtn("Add Action", builderX, builderButtonY, sideBuilderWidth, bh, () -> this.openStepPicker(AutismMacroEditorOverlay.StepPickerMode.ACTION), !stepListLocked);
      this.addBtn(
         "Add Conditional",
         builderX + sideBuilderWidth + builderGap,
         builderButtonY,
         conditionalBuilderWidth,
         bh,
         () -> this.openStepPicker(AutismMacroEditorOverlay.StepPickerMode.CONDITIONAL),
         !stepListLocked
      );
      this.addBtn(
         "Presets",
         builderX + sideBuilderWidth + builderGap + conditionalBuilderWidth + builderGap,
         builderButtonY,
         sideBuilderWidth,
         bh,
         () -> this.openStepPicker(AutismMacroEditorOverlay.StepPickerMode.PRESET),
         !stepListLocked
      );
      int bottomY = this.getFooterButtonsY();
      bx = this.panelX + this.contentInsetX();

      boolean nameEntered = this.macro.name != null && !this.macro.name.trim().isEmpty();
      this.addBtn("Save", bx, bottomY, this.footerPrimaryButtonWidth(), bh, this::save,
         !stepListLocked && nameEntered);
      bx += this.footerPrimaryButtonWidth() + this.footerButtonGap();
      this.addBtn("Cancel", bx, bottomY, this.footerPrimaryButtonWidth(), bh, this::cancel);
      bx += this.footerPrimaryButtonWidth() + this.footerButtonGap();
      this.addBtn("Undo", bx, bottomY, this.footerSecondaryButtonWidth(), bh, this::performUndo, !stepListLocked && this.canUndoHistory());
      bx += this.footerSecondaryButtonWidth() + this.footerButtonGap();
      this.addBtn("Redo", bx, bottomY, this.footerSecondaryButtonWidth(), bh, this::performRedo, !stepListLocked && this.canRedoHistory());
   }

   private void addBtn(String label, int x, int y, int w, int h, Runnable action) {
      this.addBtn(label, x, y, w, h, action, true);
   }

   private void addBtn(String label, int x, int y, int w, int h, Runnable action, boolean enabled) {
      this.addBtn(label, x, y, w, h, action, null, enabled);
   }

   private void addBtn(String label, int x, int y, int w, int h, Runnable action, Runnable secondaryAction) {
      this.addBtn(label, x, y, w, h, action, secondaryAction, true);
   }

   private void addBtn(String label, int x, int y, int w, int h, Runnable action, Runnable secondaryAction, boolean enabled) {
      this.btnLabels.add(label);
      this.btnBounds.add(new int[]{x, y, w, h});
      this.btnActions.add(action);
      this.btnSecondaryActions.add(secondaryAction);
      this.btnEnabled.add(enabled);
   }

   private void updateHistoryButtonState() {
      for (int i = 0; i < this.btnLabels.size(); i++) {
         String label = this.btnLabels.get(i);
         if ("Undo".equals(label)) {
            this.btnEnabled.set(i, !this.isStepListLockedForRun() && this.canUndoHistory());
         } else if ("Redo".equals(label)) {
            this.btnEnabled.set(i, !this.isStepListLockedForRun() && this.canRedoHistory());
         }
      }
   }

   private void updateRunButtonState() {
      if (this.runBtnIndex < 0 || this.runBtnIndex >= this.btnEnabled.size()) {
         return;
      }

      this.btnEnabled.set(this.runBtnIndex, this.isEditingRunningMacro() || this.canRunMacro());
   }

   private boolean lastMultiRunAvailable;
   private boolean lastPovRunActive;
   private long lastMultiRunCheckAt;

   private boolean multiRunAvailable() {
      autismclient.util.multi.MultiManager multi = autismclient.util.multi.MultiManager.getIfInitialized();
      return multi != null && multi.isActive();
   }

   private void refreshMultiRunButton() {
      long now = System.currentTimeMillis();
      if (now - this.lastMultiRunCheckAt < 500L) return;
      this.lastMultiRunCheckAt = now;
      if (this.multiRunAvailable() != this.lastMultiRunAvailable || this.povRunActive() != this.lastPovRunActive) {
         this.recreateComponents();
      }
   }

   private void handleRunForMultiButton() {
      autismclient.util.multi.MultiManager multi = autismclient.util.multi.MultiManager.getIfInitialized();
      if (multi == null || !multi.isActive()) {
         AutismNotifications.error("No Multi session is active anymore.");
         this.recreateComponents();
         return;
      }
      if (this.macro == null || this.macro.actions.isEmpty()) {
         AutismNotifications.error("Macro has no actions!");
         return;
      }

      autismclient.util.multi.MultiManager.BroadcastResult result = multi.runMacroDirectInteractive(this.macro, java.util.Set.of());
      AutismNotifications.show("Run for Multi: " + result.summary() + ". Details are in the Multi console.", 0xFF57F287);
   }

   private void handleRunButton() {

      if (this.macro == null) {
         AutismClientMessaging.sendPrefixed("No macro to run!");
      } else if (this.isEditorMacroRunning()) {
         this.stopEditorMacro();
         this.recreateComponents();
      } else if (this.macro.actions.isEmpty()) {
         AutismClientMessaging.sendPrefixed("Macro has no actions!");
         this.recreateComponents();
      } else if (!this.canRunMacro()) {
         AutismClientMessaging.sendPrefixed(this.getRunBlockedReason());
         this.recreateComponents();
      } else if (this.povRunActive()) {
         autismclient.util.multi.MultiManager multi = autismclient.util.multi.MultiManager.getIfInitialized();
         if (multi == null || !multi.isActive()) {
            AutismNotifications.error("The POV Multi session is no longer active.");
         } else {
            autismclient.util.multi.MultiManager.BroadcastResult result =
               multi.runMacroDirectInteractive(this.macro, java.util.Set.of());
            AutismNotifications.show("Run on POV: " + result.summary() + ".", 0xFF57F287);
         }
         this.recreateComponents();
      } else {
         this.runEditedMacro();
      }
   }

   private boolean isEditorMacroRunning() {
      return this.isEditingRunningMacro();
   }

   private void stopEditorMacro() {
      if (this.currentRunId >= 0L) {
         MacroExecutor.stopRun(this.currentRunId);
      }

      AutismMacro runningMacro = this.getRunningEditedMacro();
      if (runningMacro != null) {
         MacroExecutor.stopMacro(runningMacro);
      } else {
         String name = this.macro != null ? this.macro.name : "";
         if (name != null && !name.isBlank()) MacroExecutor.stopMacro(name);
      }
      this.currentRunId = -1L;
      this.invalidateRunningMacroCache();
   }

   private void runEditedMacro() {
      boolean hasValidName = this.macro.name != null && !this.macro.name.trim().isEmpty();
      if (hasValidName) {
         AutismMacro existingWithName = AutismMacroManager.get().get(this.macro.name);
         boolean isNameConflict = this.isNew && existingWithName != null;
         if (isNameConflict) {
            AutismClientMessaging.sendPrefixed("Name already exists: " + this.macro.name);
            return;
         }

         if (this.isNew) {
            AutismMacroManager.get().add(this.macro);
            this.originalMacro = AutismMacroManager.get().get(this.macro.name);
            this.isNew = false;
            this.tempSavedForRun = true;
            this.tempRunCreatedNewMacro = true;
         } else {
            if (!this.tempSavedForRun && this.originalMacro != null) {
               this.originalMacroSnapshot = this.originalMacro.toTag();
            }

            if (this.originalMacro != null) {
               this.originalMacro.fromTag(this.macro.toTag());
            }

            AutismMacroManager.get().save();
            this.tempSavedForRun = true;
            this.tempRunCreatedNewMacro = false;
         }

         AutismMacro savedMacro = AutismMacroManager.get().get(this.macro.name);
         this.lockStepListForRun();
         this.currentRunId = savedMacro != null ? savedMacro.executeTracked() : this.macro.executeTracked();
         this.invalidateRunningMacroCache();
      } else {
         String originalName = this.macro.name;
         this.macro.name = "(Unsaved)";
         this.lockStepListForRun();
         this.currentRunId = this.macro.executeTracked();
         this.invalidateRunningMacroCache();
         this.macro.name = originalName;
      }

      if (this.currentRunId >= 0L) {
         this.runLockArmedAtMs = System.currentTimeMillis();
      }
      this.recreateComponents();
   }

   private boolean canRunMacro() {
      return this.getRunBlockedReason() == null;
   }

   private String getRunBlockedReason() {
      if (this.macro == null) {
         return "No macro to run!";
      }

      for (MacroAction action : this.macro.actions) {
         if (action instanceof InventoryAuditAction auditAction && !auditAction.hasValidTargetSelection()) {
            return "Inventory Audit needs at least one target.";
         }
      }

      return null;
   }

   private void updateSaveButtonState() {
      this.saveBtnDirty = true;
   }

   private void updateLoopState() {
      if (this.loopMode == 0) {
         this.macro.loop = false;
      } else if (this.loopMode == 1) {
         this.macro.loop = true;

         try {
            if (this.loopCountField != null) {
               this.macro.loopCount = Integer.parseInt(this.loopCountField.getText());
            }
         } catch (Exception var2) {
            this.macro.loopCount = 1;
         }
      } else {
         this.macro.loop = true;
         this.macro.loopCount = -1;
      }
   }

   public void addAction(MacroAction action) {
      if (this.isStepListLockedForRun()) return;
      this.pushStructuralHistoryStep();
      this.macro.actions.add(action);
      this.handleMacroActionsChanged();
      if (this.hasActionEditor(action)) {
         ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlay();
         actionEditor.setWorldCaptureAllowed(this.allowsWorldCaptureActions());
         actionEditor.setMacroContext(this.macro, this.macro.actions.indexOf(action));
         actionEditor.open(
            action,
            this::pushStructuralHistoryStep,
            () -> this.handleActionEditorSaved(action)
         );
      }
   }

   private void addActionStack(List<MacroAction> actions) {
      if (this.isStepListLockedForRun()) return;
      if (actions == null || actions.isEmpty()) return;
      this.pushStructuralHistoryStep();
      for (MacroAction action : actions) {
         if (action != null) this.macro.actions.add(action);
      }
      this.handleMacroActionsChanged();
   }

   private void handleActionEditorSaved(MacroAction action) {
      if (this.isStepListLockedForRun()) return;
      if (action instanceof RaceAction race) {
         this.applyRaceBuilder(race);
         return;
      }
      if (action instanceof ReportAction report) {
         this.applyReportBuilder(report);
         return;
      }
      if (action instanceof PacketGateAction) {
         this.reconcilePacketGate((PacketGateAction) action);
      }
      this.handleMacroActionsChanged();
   }

   private void applyRaceBuilder(RaceAction race) {
      if (this.macro == null || race == null) return;
      int index = this.macro.actions.indexOf(race);
      if (index < 0) {
         this.handleMacroActionsChanged();
         return;
      }

      int oldCount = race.normalizedBodyCount(this.macro.actions, index);
      List<MacroAction> replacement = new ArrayList<>();
      boolean[] reused = new boolean[Math.max(0, oldCount)];
      for (String step : race.effectiveRaceSteps()) {
         boolean conditionStep = RaceAction.isConditionStep(step);
         String typeName = RaceAction.stepTypeName(step);
         MacroAction kept = null;
         for (int offset = 1; offset <= oldCount && index + offset < this.macro.actions.size(); offset++) {
            int reuseIndex = offset - 1;
            if (reuseIndex >= reused.length || reused[reuseIndex]) continue;
            MacroAction oldAction = this.macro.actions.get(index + offset);
            if (oldAction == null) continue;
            boolean sameType = conditionStep
                    ? RaceAction.isConditionAction(oldAction) && RaceAction.triggerTypeFor(oldAction).name().equalsIgnoreCase(typeName)
                    : RaceAction.isBodyAction(oldAction) && oldAction.getTypeId().equalsIgnoreCase(typeName);
            if (sameType) {
               kept = oldAction;
               reused[reuseIndex] = true;
               break;
            }
         }
         replacement.add(kept != null ? kept : RaceAction.createStepAction(step));
      }

      if (oldCount > 0) {
         this.macro.actions.subList(index + 1, index + 1 + oldCount).clear();
      }
      this.macro.actions.addAll(index + 1, replacement);
      race.raceSteps = new ArrayList<>(race.effectiveRaceSteps());
      race.syncLegacyListsFromRaceSteps();
      race.conditionType = race.conditionTypes.isEmpty() ? "" : race.conditionTypes.get(0);
      race.triggerAction = race.conditionTypes.isEmpty() ? new DelayAction(0) : race.createSelectedConditionAction();
      race.actionType = race.actionTypes.isEmpty() ? MacroActionType.PACKET_CLICK.name() : race.actionTypes.get(0);
      race.bodyCount = replacement.size();
      this.handleMacroActionsChanged();
   }

   private void applyReportBuilder(ReportAction report) {
      if (this.macro == null || report == null) return;
      int index = this.macro.actions.indexOf(report);
      if (index < 0) {
         this.handleMacroActionsChanged();
         return;
      }

      int oldCount = report.normalizedConditionCount(this.macro.actions, index);
      MacroAction oldStart = oldCount >= 1 && index + 1 < this.macro.actions.size() ? this.macro.actions.get(index + 1) : null;
      MacroAction oldEnd = oldCount >= 2 && index + 2 < this.macro.actions.size() ? this.macro.actions.get(index + 2) : null;

      MacroAction newStart = report.createSelectedStartAction();
      if (oldStart != null
              && RaceAction.isBodyAction(oldStart)
              && oldStart.getTypeId().equalsIgnoreCase(report.startActionType)) {
         newStart = oldStart;
      }

      MacroAction newEnd = report.createSelectedEndConditionAction();
      if (oldEnd != null
              && RaceAction.isConditionAction(oldEnd)
              && RaceAction.triggerTypeFor(oldEnd).name().equalsIgnoreCase(report.endConditionType)) {
         newEnd = oldEnd;
      }

      if (oldCount > 0) {
         this.macro.actions.subList(index + 1, index + 1 + oldCount).clear();
      }
      this.macro.actions.add(index + 1, newStart);
      this.macro.actions.add(index + 2, newEnd);
      report.conditionCount = 2;
      this.handleMacroActionsChanged();
   }

   private void save() {
      if (this.isStepListLockedForRun()) return;

      if (this.macro != null && this.macro.actions.isEmpty() && this.originalMacro != null
            && !this.originalMacro.actions.isEmpty()) {
         AutismClientMessaging.sendPrefixed("Refusing to save 0 steps over \"" + this.macro.name + "\".");
         return;
      }
      String trimmedName = this.macro == null || this.macro.name == null ? "" : this.macro.name.trim();
      if (trimmedName.isEmpty()) {
         AutismNotifications.show("Name your macro before saving.", 0xFFE0533A);
         return;
      }
      AutismMacro nameOwner = AutismMacroManager.get().get(trimmedName);
      if (nameOwner != null && nameOwner != this.originalMacro) {
         AutismNotifications.show("A macro named \"" + trimmedName + "\" already exists.", 0xFFE0533A);
         return;
      }
      if (this.isValidName(this.macro.name)) {
         String previousName = !this.isNew && this.originalMacro != null && this.originalMacro.name != null
                 ? this.originalMacro.name : "";
         AutismMacro savedOrUpdated = this.macro;
         if (this.isNew) {
            AutismMacroManager.get().add(this.macro);
            savedOrUpdated = this.macro;
         } else {
            if (this.originalMacro != null) {
               this.originalMacro.name = this.macro.name;
               this.originalMacro.description = this.macro.description;
               this.originalMacro.loop = this.macro.loop;
               this.originalMacro.loopCount = this.macro.loopCount;
               this.originalMacro.keyCode = this.macro.keyCode;

               this.originalMacro.actions = new ArrayList<>(this.macro.actions);
               savedOrUpdated = this.originalMacro;
            }

            AutismMacroManager.get().save();
         }

         if (!previousName.isBlank() && MacroNames.renamed(previousName, savedOrUpdated.name)) {
            autismclient.util.multi.MultiProfileManager.get().replaceMacroReferences(previousName, savedOrUpdated.name);
            autismclient.util.multi.MultiManager liveMulti = autismclient.util.multi.MultiManager.getIfInitialized();
            if (liveMulti != null) liveMulti.replaceMacroReference(previousName, savedOrUpdated.name);

            if (!MacroNames.equal(previousName, savedOrUpdated.name) && AutismLANSync.getInstance().isInSession()) {
               AutismLANSync.getInstance().broadcastMacroDeletion(previousName);
            }
         }

         this.tempSavedForRun = false;
         this.tempRunCreatedNewMacro = false;
         this.originalMacroSnapshot = null;
         Consumer<AutismMacro> callback = this.saveCompleteCallback;
         if (callback != null) {
            callback.accept(savedOrUpdated);
         }
         this.close();
      }
   }

   private MacroAction createActionCopy(MacroAction original) {
      if (original == null) {
         return null;
      } else {
         try {
            return AutismMacro.createActionFromTag(original.toTag());
         } catch (Exception var3) {
            return null;
         }
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.saveBtnDirty) {
         this.saveBtnDirty = false;
         this.recreateComponents();
      }

      this.refreshStepListRunLockState();
      this.updateHistoryButtonState();
      this.updateRunButtonState();
      this.refreshMultiRunButton();
      if (this.visible) {
         {
            boolean stepListLocked = this.isStepListLockedForRun();
            int panelMouseX = mouseX;
            int panelMouseY = mouseY;
            if (this.isMouseOverStepPicker(mouseX, mouseY) || this.stepCtxMenu.isMouseOver(mouseX, mouseY)
                  || (!this.uiDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.uiDropdowns))) {
               panelMouseX = -10000;
               panelMouseY = -10000;
            }

            String editorTitle = this.isNew ? "Create Macro" : "Edit Macro";
            boolean isRunning = this.isEditingRunningMacro();
            if (isRunning) {
               editorTitle = editorTitle + " [RUN]";
            }

            if (this.macro != null) {
               editorTitle = editorTitle + " (" + this.macro.actions.size() + " steps)";
            }

            DirectViewport viewport = this.surface.viewport();
            AutismWindowLayout clamped = this.clampToScreen(
               this,
               new AutismWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed)
            );
            if (clamped.x != this.panelX || clamped.y != this.panelY || clamped.width != this.PANEL_WIDTH || clamped.height != this.PANEL_HEIGHT) {
               this.panelX = clamped.x;
               this.panelY = clamped.y;
               this.PANEL_WIDTH = clamped.width;
               this.PANEL_HEIGHT = clamped.height;
               this.recreateComponents();
            }
            float uiMouseX = viewport.toUiX(mouseX);
            float uiMouseY = viewport.toUiY(mouseY);
            boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
            boolean headerHovered = uiMouseX >= this.panelX
               && uiMouseX < this.panelX + this.PANEL_WIDTH
               && uiMouseY >= this.panelY
               && uiMouseY < this.panelY + this.theme.headerHeight();
            this.windowNode.setTitle(editorTitle);
            this.windowNode.setShowBody(!this.collapsed);
            this.windowNode.setActive(active);
            this.windowNode.setHeaderHovered(headerHovered);
            int frameHeight = this.getRenderedPanelHeight();
            this.shellBody.setPreferredHeight(Math.max(0, frameHeight - this.theme.headerHeight()));
            this.windowNode.setBounds(this.panelX, this.panelY, this.PANEL_WIDTH, frameHeight);
            this.surface.render(context, mouseX, mouseY, delta);
            int bodyClipTop = this.panelY + this.theme.headerHeight();
            int bodyClipBottom = this.panelY + frameHeight;
            if (bodyClipBottom > bodyClipTop + 1) {
               viewport.enableScissor(context, this.panelX, bodyClipTop, this.panelX + this.PANEL_WIDTH, bodyClipBottom);

               try {
                  if (this.nameField != null) this.nameField.render(context, panelMouseX, panelMouseY, delta);
                  if (this.loopMode == 1 && this.loopCountField != null) {
                     this.loopCountField.render(context, panelMouseX, panelMouseY, delta);
                  }

                  if (!this.isValidName(this.macro.name)) {
                     String warning = this.macro.name.isEmpty() ? "Name Required" : "Name Taken";
                     UiText.draw(
                        context,
                        this.textRenderer,
                        warning,
                        this.theme.fontFor(UiTone.BODY),
                        -43691,
                        this.panelX + 10,
                        this.getFooterButtonsY() - 10,
                        false
                     );
                  }

                  int builderLabelY = this.getStepBuilderLabelY();
                  UiText.draw(
                     context,
                     this.textRenderer,
                     "Step Builder",
                     this.theme.fontFor(UiTone.LABEL),
                     AutismColors.textSecondary(),
                     this.panelX + 10,
                     UiSizing.alignTextY(builderLabelY, this.theme.lineHeight(UiTone.LABEL, 1), this.theme.fontHeight(UiTone.LABEL), this.theme.bodyTextNudge()),
                     false
                  );
                  int listLabelY = this.getStepListLabelY();
                  int listY = this.getStepListY();
                  int listHeight = this.getStepListHeight();
                  int listClipTop = this.getStepListClipTop();
                  int listClipBottom = this.getStepListClipBottom();
                  UiText.draw(
                     context,
                     this.textRenderer,
                     "Steps (" + this.macro.actions.size() + ")",
                     this.theme.fontFor(UiTone.LABEL),
                     AutismColors.textSecondary(),
                     this.panelX + 12,
                     UiSizing.alignTextY(listLabelY, this.theme.lineHeight(UiTone.LABEL, 1), this.theme.fontHeight(UiTone.LABEL), this.theme.bodyTextNudge()),
                     false
                  );
                  if (stepListLocked) {
                     String lockText = "Running - list locked";
                     int lockColor = DirectRenderContext.applyAlpha(0xFFFFA0A0, 0.86F);
                     int lockW = UiText.width(this.textRenderer, lockText, this.theme.fontFor(UiTone.LABEL), lockColor);
                     UiText.draw(
                        context,
                        this.textRenderer,
                        lockText,
                        this.theme.fontFor(UiTone.LABEL),
                        lockColor,
                        Math.max(this.panelX + 12, this.getStepListContentRight() - lockW),
                        UiSizing.alignTextY(listLabelY, this.theme.lineHeight(UiTone.LABEL, 1), this.theme.fontHeight(UiTone.LABEL), this.theme.bodyTextNudge()),
                        false
                     );
                  }
                  this.clampStepListScrollOffset();
                  AutismColors.drawInsetPanel(context, this.getStepListFrameX(), listY, this.getStepListFrameWidth(), listHeight, false);
                  autismclient.gui.vanillaui.UiScissorStack.global().push(context,
                     autismclient.gui.vanillaui.UiBounds.of(
                        this.getStepListContentLeft(),
                        listClipTop,
                        Math.max(0, this.getStepListContentRight() - this.getStepListContentLeft()),
                        Math.max(0, listClipBottom - listClipTop)
                     ));

                  try {
                     this.tickStepRowAnimations();
                     int y = listY - this.scrollOffset;
                     int activeDragIndex = stepListLocked ? -1 : this.draggingIndex;
                     int activeDragTarget = stepListLocked ? -1 : this.stepDragTargetIndex;

                     List<MacroAction> dragBlock = null;
                     List<Integer> dragBlockIndices = null;
                     Set<MacroAction> dragSet = null;
                     int dragInsertCompact = -1;
                     int dragPrimaryPos = 0;
                     if (activeDragIndex >= 0 && activeDragIndex < this.macro.actions.size() && this.isStepIndexSelected(activeDragIndex)) {
                        List<MacroAction> refs = this.selectedStepActionReferences();
                        if (refs.size() > 1 && !this.selectionContainsPacketGate(refs)) {
                           dragBlock = new ArrayList<>();
                           dragBlockIndices = new ArrayList<>();
                           dragSet = Collections.newSetFromMap(new IdentityHashMap<>());
                           dragSet.addAll(refs);
                           for (int i = 0; i < this.macro.actions.size(); i++) {
                              MacroAction a = this.macro.actions.get(i);
                              if (dragSet.contains(a)) {
                                 dragBlock.add(a);
                                 dragBlockIndices.add(i);
                              }
                           }
                           dragInsertCompact = this.selectedBlockInsertCompact(dragSet, activeDragIndex, activeDragTarget);
                           for (int p = 0; p < dragBlockIndices.size(); p++) {
                              if (dragBlockIndices.get(p) == activeDragIndex) {
                                 dragPrimaryPos = p;
                                 break;
                              }
                           }
                        }
                     }
                     boolean multiDrag = dragBlock != null;

                     for (int i = 0; i < this.macro.actions.size(); i++) {
                        MacroAction action = this.macro.actions.get(i);
                        if (multiDrag ? dragSet.contains(action) : (i == activeDragIndex)) {
                           continue;
                        }

                        int visualIndex = multiDrag
                           ? this.multiDragVisualSlot(i, dragSet, dragInsertCompact, dragBlock.size())
                           : this.getShiftedVisualIndex(i, activeDragIndex, activeDragTarget);
                        int actionY = y + visualIndex * this.actionRowHeight();
                        if (this.isStepRowFullyVisible(actionY)) {
                           AutismMacroEditorOverlay.StepRowMotion motion = this.findStepRowMotion(this.stepRowStableId(action));
                           int animatedY = motion != null && motion.kind == AutismMacroEditorOverlay.StepRowMotionKind.MOVE
                              ? this.getAnimatedStepRowY(motion, y)
                              : actionY;
                           int enterOffset = this.getStepRowMoveOffsetX(motion);
                           float enterAlpha = this.getStepRowAlpha(motion);
                           this.renderStepListRow(
                              context,
                              action,
                              action.getDisplayName(),
                              this.isConditionalAction(action),
                              i,
                              animatedY,
                              panelMouseX,
                              panelMouseY,
                              enterOffset,
                              stepListLocked ? enterAlpha * 0.54F : enterAlpha,
                              !stepListLocked
                           );
                        }
                     }

                     for (AutismMacroEditorOverlay.StepRowMotion motion : this.stepRowMotions) {
                        if (motion.kind == AutismMacroEditorOverlay.StepRowMotionKind.EXIT) {
                           int actionY = this.getAnimatedStepRowY(motion, y);
                           if (this.isStepRowFullyVisible(actionY)) {
                              this.renderStepListRow(
                                 context,
                                 null,
                                 motion.label,
                                 motion.conditional,
                                 motion.fromRowIndex,
                                 actionY,
                                 -10000,
                                 -10000,
                                 this.getStepRowMoveOffsetX(motion),
                                 this.getStepRowAlpha(motion),
                                 false
                              );
                           }
                        }
                     }

                     if (!stepListLocked && activeDragIndex >= 0 && activeDragIndex < this.macro.actions.size()) {
                        int insertSlot = multiDrag
                           ? dragInsertCompact
                           : this.getStepInsertSlotForTarget(activeDragIndex, activeDragTarget);
                        if (insertSlot >= 0) {
                           int insertY = y + insertSlot * this.actionRowHeight();
                           if (insertY >= listClipTop - 2 && insertY <= listClipBottom + 2) {
                              CompactSurfaces.tintedRow(context, this.panelX + 10, insertY - 1,
                                 this.getStepListContentRight() - this.panelX - 11, 2, this.theme.headerAccent());
                           }
                        }

                        int dragRowTop = this.getDraggedRowTop();
                        if (multiDrag) {

                           for (int j = 0; j < dragBlock.size(); j++) {
                              int top = dragRowTop + (j - dragPrimaryPos) * this.actionRowHeight();
                              if (this.isStepRowFullyVisible(top)) {
                                 this.drawDraggedGhostRow(context, dragBlock.get(j), top, dragBlockIndices.get(j) + 1);
                              }
                           }
                        } else if (this.isStepRowFullyVisible(dragRowTop)) {
                           this.drawDraggedGhostRow(context, this.macro.actions.get(activeDragIndex), dragRowTop, activeDragIndex + 1);
                        }
                     }
                  } finally {
                     autismclient.gui.vanillaui.UiScissorStack.global().pop(context);
                  }

                  CompactScrollbar.Metrics stepListScrollbar = this.getStepListScrollbarMetrics();
                  CompactScrollbar.draw(context, stepListScrollbar, stepListScrollbar.contains(panelMouseX, panelMouseY), this.activeScrollbarDrag == 1);
                  this.hoveredTooltip = null;
                  boolean macroCurrentlyRunning = this.isEditingRunningMacro();

                  for (int ix = 0; ix < this.btnLabels.size(); ix++) {
                     int[] b = this.btnBounds.get(ix);
                     boolean enabled = this.btnEnabled.get(ix);
                      String lbl = ix == this.runBtnIndex ? this.runButtonLabel(macroCurrentlyRunning) : this.btnLabels.get(ix);
                     CompactOverlayButton.Variant variant = CompactOverlayButton.Variant.SECONDARY;
                     if (ix == this.runBtnIndex) {
                        variant = macroCurrentlyRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS;
                     } else if ("Save".equals(lbl)) {
                        variant = CompactOverlayButton.Variant.SUCCESS;
                     }

                     if (ix == this.runBtnIndex) {
                        this.drawRunIconButton(context, b[0], b[1], b[2], b[3], macroCurrentlyRunning, enabled, panelMouseX, panelMouseY);
                     } else if ("Multi".equals(lbl)) {
                        this.drawOverlayIconButton(context, b[0], b[1], b[2], b[3], lbl, autismclient.util.AutismUiIcons.PLAY, variant, enabled, panelMouseX, panelMouseY);
                     } else if ("Add Action".equals(lbl) || "Add Conditional".equals(lbl)) {

                        this.drawOverlayIconButton(context, b[0], b[1], b[2], b[3], lbl.substring(4), autismclient.util.AutismUiIcons.PLUS, variant, enabled, panelMouseX, panelMouseY);
                     } else {
                        this.drawOverlayButton(context, b[0], b[1], b[2], b[3], lbl, variant, enabled, panelMouseX, panelMouseY);
                     }
                     if (panelMouseX >= b[0] && panelMouseX < b[0] + b[2] && panelMouseY >= b[1] && panelMouseY < b[1] + b[3]) {
                        String tip = this.getTooltipFor(lbl);
                        if (tip != null) {
                           this.hoveredTooltip = tip;
                           this.tooltipX = panelMouseX + 8;
                           this.tooltipY = panelMouseY + 12;
                        }
                     }
                  }
                  if (!this.uiDropdowns.isEmpty()) {
                     CompactDropdown.renderButtons(context, this.textRenderer, this.uiDropdowns, mouseX, mouseY);
                  }
               } finally {
                  viewport.disableScissor(context);
               }

               if (!this.collapsed && !this.uiDropdowns.isEmpty() && CompactDropdown.isMenuOpen(this.uiDropdowns)) {
                  context.nextStratum();
                  CompactDropdown.renderOpenMenu(context, this.textRenderer, this.uiDropdowns, mouseX, mouseY);
               }

               if (!this.collapsed) {
                  if (this.hoveredTooltip != null) {
                     context.nextStratum();
                     Tooltip.render(UiContexts.overlay(context, this.textRenderer, this.tooltipX, this.tooltipY),
                         this.hoveredTooltip, this.tooltipX, this.tooltipY, 220);
                  }
               }
               if (!this.collapsed && this.stepCtxMenu.isOpen()) {
                  context.nextStratum();
                  this.stepCtxMenu.render(context, mouseX, mouseY);
               }
            }
         }
      }
   }

   private void drawStepRowControlButton(
      GuiGraphicsExtractor context, int x, int y, int width, int height, boolean hovered, boolean danger, String label
   ) {
      CompactSymbolButton.render(context, this.textRenderer, UiBounds.of(x, y, width, height),
         label == null ? "" : label, hovered, true, danger);
   }

   private void drawStepRowControlIcon(
      GuiGraphicsExtractor context, int x, int y, int width, int height, boolean hovered, boolean danger, net.minecraft.resources.Identifier icon
   ) {
      CompactSymbolButton.renderIcon(context, this.textRenderer, UiBounds.of(x, y, width, height), icon, hovered, true, danger);
   }

   private boolean isBuilderPickerButton(String label) {
      return "Add Action".equals(label) || "Add Conditional".equals(label) || "Presets".equals(label);
   }

   private void drawStepPickerCard(
      GuiGraphicsExtractor context,
      AutismMacroEditorOverlay.StepPickerCategory category,
      AutismMacroEditorOverlay.StepPickerEntry entry,
      int x,
      int y,
      int width,
      int height,
      boolean hovered,
      int mouseX,
      int mouseY
   ) {
      float hover = hovered ? 1.0F : 0.0F;
      float emphasis = hover * 0.85F;
      int categoryRgb = category.color & 0x00FFFFFF;

      int fillColor = UiSizing.lerpColor(0x08000000, 0x22000000 | categoryRgb, emphasis);
      int borderColor = UiSizing.lerpColor(category.color, 0xFFFFFFFF, Math.min(0.22F, hover * 0.10F));
      int textColor = UiSizing.lerpColor(this.theme.color(UiTone.BODY), 0xFFFFFFFF, Math.min(0.32F, hover * 0.12F));

      UiRenderer.frame(context, UiBounds.of(x, y, width, height), fillColor, borderColor);

      if (emphasis > 0.0F) {
         int hoverTint = (((int)Math.min(30.0F, hover * 18.0F)) << 24) | categoryRgb;
         CompactSurfaces.tintedRow(context, x + 1, y + 1, width - 2, height - 2, hoverTint);
      }

      String label = entry.label == null ? "" : entry.label;
      int maxLabelW = Math.max(1, width - 6);
      int labelY = UiSizing.alignTextY(y, height, this.theme.fontHeight(UiTone.BODY), 0);
      this.drawStepPickerFittedLabel(context, label, x + 3, labelY, maxLabelW, textColor);
   }

   private void drawStepPickerFittedLabel(GuiGraphicsExtractor context, String label, int x, int y, int maxWidth, int color) {
      String safe = label == null ? "" : label;
      if (safe.isBlank() || maxWidth <= 0) return;

      var font = this.theme.fontFor(UiTone.BODY);
      int measured = UiText.width(this.textRenderer, safe, font, color);
      if (measured <= maxWidth) {
         int labelX = x + Math.max(0, (maxWidth - measured) / 2);
         UiText.draw(context, this.textRenderer, safe, font, color, labelX, y, false);
         return;
      }

      float scale = Math.max(0.72F, Math.min(1.0F, maxWidth / (float)Math.max(1, measured)));
      if (scale > 0.72F) {
         int scaledWidth = Math.round(measured * scale);
         int labelX = x + Math.max(0, (maxWidth - scaledWidth) / 2);
         context.pose().pushMatrix();
         try {
            context.pose().scale(scale, scale);
            UiText.draw(context, this.textRenderer, safe, font, color, Math.round(labelX / scale), Math.round(y / scale), false);
         } finally {
            context.pose().popMatrix();
         }
         return;
      }

      String displayLabel = UiText.trimToWidthEllipsis(this.textRenderer, safe, Math.round(maxWidth / scale), font, color);
      int scaledWidth = Math.round(UiText.width(this.textRenderer, displayLabel, font, color) * scale);
      int labelX = x + Math.max(0, (maxWidth - scaledWidth) / 2);
      context.pose().pushMatrix();
      try {
         context.pose().scale(scale, scale);
         UiText.draw(context, this.textRenderer, displayLabel, font, color, Math.round(labelX / scale), Math.round(y / scale), false);
      } finally {
         context.pose().popMatrix();
      }
   }

   private void renderStepListRow(
      GuiGraphicsExtractor context,
      MacroAction action,
      String label,
      boolean conditional,
      int actionIndex,
      int actionY,
      int mouseX,
      int mouseY,
      int xOffset,
      float alpha,
      boolean interactive
   ) {
      int rowLeft = this.panelX + 9 + xOffset;
      int rowRight = this.getStepListContentRight() + xOffset;
      GroupRowInfo group = this.getGroupRowInfo(actionIndex, action);
      boolean groupBody = group.inGroup() && !group.header;
      boolean multiBody = group.multi && !group.header;
      boolean groupHeader = group.inGroup() && group.header;
      boolean gateEnd = group.gate && action instanceof EndPacketGateAction;
      boolean allowMove = !groupBody || multiBody || gateEnd;
      boolean allowDuplicate = (!group.inGroup() || multiBody) && !(action instanceof PacketGateAction) && !(action instanceof EndPacketGateAction);
      boolean allowDelete = (!groupBody || multiBody) && !(action instanceof EndPacketGateAction);
      int bodyIndent = groupBody ? 10 : 0;
      boolean hoverRow = interactive
         && mouseX >= rowLeft - 1
         && mouseX <= rowRight + 1
         && mouseY >= actionY
         && mouseY < actionY + this.actionRowHeight();
      boolean selectedRow = interactive && this.isStepIndexSelected(actionIndex);
      int bgColor = selectedRow ? AutismTheme.recolor(0x663A161A, AutismTheme.Channel.ACCENT) : (hoverRow ? AutismColors.rowHover() : AutismColors.rowNormal());
      CompactSurfaces.tintedRow(context, rowLeft, actionY + 1, rowRight - rowLeft,
         this.actionRowHeight() - 2, DirectRenderContext.applyAlpha(bgColor, alpha));
      StepImpactInfo impact = this.getStepImpactInfo(actionIndex);
      if (impact.impacted) {
         int rh = this.actionRowHeight();
         int rgb = (impact.ping ? AutismColors.packetBlue() : AutismColors.packetOrange()) & 0x00FFFFFF;
         int tint = DirectRenderContext.applyAlpha(((impact.header ? 0x4D : 0x33) << 24) | rgb, alpha);
         int edge = DirectRenderContext.applyAlpha((0xD0 << 24) | rgb, alpha);
         CompactSurfaces.tintedRow(context, rowLeft, actionY + 1, rowRight - rowLeft, rh - 2, tint);

         CompactSurfaces.indicator(context, rowLeft, actionY + 1, 1, rh - 2, edge);
         if (impact.first) CompactSurfaces.indicator(context, rowLeft, actionY + 1, rowRight - rowLeft, 1, edge);
         if (impact.last) CompactSurfaces.indicator(context, rowLeft, actionY + rh - 2, rowRight - rowLeft, 1, edge);
      }
      if (selectedRow) {
         UiRenderer.outline(context, UiBounds.of(rowLeft, actionY + 1, rowRight - rowLeft, this.actionRowHeight() - 2),
            DirectRenderContext.applyAlpha(AutismTheme.recolor(0xFFFF5B5B, AutismTheme.Channel.OUTLINE), alpha));
      }
      if (group.inGroup()) {
         int railX = this.panelX + 10 + xOffset;
         int railTop = group.isFirstRow() ? actionY + 3 : actionY;
         int railBottom = group.isLastRow() ? actionY + this.actionRowHeight() - 3 : actionY + this.actionRowHeight();
         int railColor = DirectRenderContext.applyAlpha(group.gate ? AutismColors.packetCyan() : this.theme.headerAccent(), alpha);
         CompactSurfaces.indicator(context, railX, railTop, 2, railBottom - railTop, railColor);
      }
      int badgeColor = group.gate ? AutismColors.packetCyan() : (groupHeader ? this.theme.headerAccent() : (conditional ? -30720 : -12268476));
      if (!groupHeader) {
         CompactSurfaces.indicator(
            context,
            this.panelX + 10 + xOffset + bodyIndent,
            actionY + 3,
            3,
            this.actionRowHeight() - 6,
            DirectRenderContext.applyAlpha(badgeColor, alpha)
         );
      }
      UiText.draw(
         context,
         this.textRenderer,
         String.valueOf(actionIndex + 1),
         this.theme.fontFor(UiTone.BODY),
         DirectRenderContext.applyAlpha(AutismColors.textDim(), alpha),
         this.panelX + 16 + xOffset + bodyIndent,
         this.stepRowTextY(actionY),
         false
       );
       int nameX = this.panelX + 30 + xOffset + bodyIndent;
       int controlsX = this.getActionRowControlsX(action, allowMove, allowDuplicate, allowDelete) + xOffset;
       int contentRight = this.getStepListContentRightForAction(action, allowMove, allowDuplicate, allowDelete) + xOffset;
       int maxNameW = Math.max(1, contentRight - nameX - 8);
       if (group.inGroup() && !group.roleLabel.isEmpty()) {
          int roleColor = DirectRenderContext.applyAlpha(group.gate ? AutismColors.packetCyan() : (groupHeader ? 0xFFFFD7D7 : AutismColors.textSecondary()), alpha);
          String role = group.roleLabel;
          int roleW = UiText.width(this.textRenderer, role, this.theme.fontFor(UiTone.LABEL), roleColor);
          UiText.draw(
             context,
             this.textRenderer,
             role,
             this.theme.fontFor(UiTone.LABEL),
             roleColor,
             nameX,
             this.stepRowTextY(actionY),
             false
          );
          nameX += roleW + 6;
          maxNameW = Math.max(1, contentRight - nameX - 8);
       }

       int nameColor = action instanceof autismclient.util.macro.MissingAddonAction ? 0xFF9A9A9A : -1;
       String displayName = UiText.trimToWidth(
         this.textRenderer, label, maxNameW, this.theme.fontFor(UiTone.BODY), DirectRenderContext.applyAlpha(nameColor, alpha)
      );
      UiText.draw(
         context,
         this.textRenderer,
         displayName,
         this.theme.fontFor(UiTone.BODY),
         DirectRenderContext.applyAlpha(nameColor, alpha),
         nameX,
         this.stepRowTextY(actionY),
         false
      );
      if (interactive && action != null) {
         int controlY = this.stepRowControlY(actionY);
         int cx = controlsX;
         if (allowMove) {
            this.drawStepRowControlIcon(
               context,
               cx,
               controlY,
               this.stepRowControlSize(),
               this.stepRowControlSize(),
               mouseX >= cx && mouseX < cx + this.stepRowControlSize() && mouseY >= controlY && mouseY < controlY + this.stepRowControlSize(),
               false,
               AutismUiIcons.CHEVRON_UP
            );
            cx += this.stepRowControlStride();
            this.drawStepRowControlIcon(
               context,
               cx,
               controlY,
               this.stepRowControlSize(),
               this.stepRowControlSize(),
               mouseX >= cx && mouseX < cx + this.stepRowControlSize() && mouseY >= controlY && mouseY < controlY + this.stepRowControlSize(),
               false,
               AutismUiIcons.CHEVRON_DOWN
            );
            cx += this.stepRowControlStride();
         }
         if (allowDuplicate) {
            this.drawStepRowControlIcon(
               context,
               cx,
               controlY,
               this.stepRowControlSize(),
               this.stepRowControlSize(),
               mouseX >= cx && mouseX < cx + this.stepRowControlSize() && mouseY >= controlY && mouseY < controlY + this.stepRowControlSize(),
               false,
               AutismUiIcons.DUPLICATE
            );
            cx += this.stepRowControlStride();
         }
         boolean hasEditor = this.hasActionEditor(action);
         if (hasEditor) {
            this.drawStepRowControlIcon(
               context,
               cx,
               controlY,
               this.stepRowControlSize(),
               this.stepRowControlSize(),
               mouseX >= cx && mouseX < cx + this.stepRowControlSize() && mouseY >= controlY && mouseY < controlY + this.stepRowControlSize(),
               false,
               AutismUiIcons.EDIT
            );
            cx += this.stepRowControlStride();
         }
         if (allowDelete) {
            this.drawStepRowControlIcon(
               context,
               cx,
               controlY,
               this.stepRowControlSize(),
               this.stepRowControlSize(),
               mouseX >= cx && mouseX < cx + this.stepRowControlSize() && mouseY >= controlY && mouseY < controlY + this.stepRowControlSize(),
               true,
               AutismUiIcons.TRASH
            );
         }
      }
   }

   private void drawEditorListText(GuiGraphicsExtractor context, String text, int x, int y, int color) {
      UiText.draw(context, this.textRenderer, text, this.theme.fontFor(UiTone.BODY), color, x, y, false);
   }

   private void drawEditorListLabel(GuiGraphicsExtractor context, String text, int x, int y, int color) {
      UiText.draw(context, this.textRenderer, text, this.theme.fontFor(UiTone.LABEL), color, x, y, false);
   }

   private void drawEditorListDeleteButton(GuiGraphicsExtractor context, int x, int y, boolean hovered) {
      this.drawStepRowControlIcon(context, x, y, 14, 12, hovered, true, AutismUiIcons.TRASH);
   }

   private void drawEditorSelectableRegistryRow(GuiGraphicsExtractor context, int x, int y, int width, String label, boolean hovered, boolean selected) {
      int bg = selected ? AutismColors.rowSelected() : (hovered ? AutismColors.rowHover() : AutismColors.rowNormal());
      CompactSurfaces.tintedRow(context, x, y, width, 12, bg);
      int textX = x + 3;

      this.drawEditorListText(context, label, textX, y + 2, selected ? AutismColors.rowSelectedText() : AutismColors.textLight());
   }

   private boolean isOverHeaderUi(double mouseX, double mouseY) {
      return mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + this.theme.headerHeight();
   }

   private boolean isOverCloseButtonUi(double mouseX, double mouseY) {
      return OverlayTopBar.isOverClose(UiBounds.of(this.panelX, this.panelY, this.PANEL_WIDTH, this.getRenderedPanelHeight()),
         this.theme.headerHeight(), mouseX, mouseY);
   }

   private int getRenderedPanelHeight() {
      this.shellBody.setPreferredHeight(Math.max(0, this.PANEL_HEIGHT - this.theme.headerHeight()));
      this.windowNode.setShowBody(!this.collapsed);
      DirectRenderContext metrics = this.surface.measurementContext();
      if (metrics == null) {
         return this.collapsed ? this.theme.headerHeight() : this.PANEL_HEIGHT;
      } else {
         return Math.max(this.theme.headerHeight(), Math.round(this.windowNode.preferredHeight(metrics, this.PANEL_WIDTH)));
      }
   }

   private void renderStepPicker(GuiGraphicsExtractor context, int mouseX, int mouseY) {
      int pickerX = this.getStepPickerX();
      int pickerY = this.getStepPickerY();
      int pickerW = this.getStepPickerWidth();
      int pickerH = this.getStepPickerHeight();
      String title = this.stepPickerMode == AutismMacroEditorOverlay.StepPickerMode.ACTION
         ? "Add Action"
         : (this.stepPickerMode == AutismMacroEditorOverlay.StepPickerMode.PRESET ? "Presets" : "Add Conditional");
      CompactWindow.renderFrame(UiContexts.overlay(context, this.textRenderer, mouseX, mouseY),
         UiBounds.of(pickerX, pickerY, pickerW, pickerH), title, false, false, true,
         mouseX >= pickerX && mouseX < pickerX + pickerW && mouseY >= pickerY && mouseY < pickerY + 16,
         true, 4, 4, 16);
      this.syncStepPickerSearchFieldBounds();
      if (this.stepPickerSearchField != null) {
         this.stepPickerSearchField.render(context, mouseX, mouseY, 0.0F);
      }
      int contentX = pickerX + 6;
      int contentY = pickerY + 42;
      int scrollbarGutter = 7;
      int contentW = Math.max(1, pickerW - 12 - scrollbarGutter);
      int contentH = Math.max(1, pickerH - 48);
      int gridX = contentX;
      int gridY = contentY;
      int gridH = contentH;
      List<AutismMacroEditorOverlay.StepPickerSectionLayout> layouts = this.buildStepPickerLayouts(contentX, contentY - this.stepPickerScrollOffset, contentW);
      int contentHeight = this.getStepPickerContentHeight();
      int maxScroll = Math.max(0, contentHeight - contentH);
      this.stepPickerScrollOffset = Math.max(0, Math.min(this.stepPickerScrollOffset, maxScroll));
      autismclient.gui.vanillaui.UiScissorStack.global().push(context,
         autismclient.gui.vanillaui.UiBounds.of(contentX, contentY, Math.max(0, contentW), Math.max(0, contentH)));

      try {
         for (AutismMacroEditorOverlay.StepPickerSectionLayout section : layouts) {
            int sectionTop = section.headerY;
            int sectionBottom = section.bottomY;
            if (sectionBottom >= gridY && sectionTop <= gridY + gridH) {
               String headerLabel = UiText.trimToWidthEllipsis(
                  this.textRenderer,
                  section.category.label,
                  Math.max(1, section.width - 4),
                  this.theme.fontFor(UiTone.LABEL),
                  section.category.color
               );
               UiText.draw(
                  context,
                  this.textRenderer,
                  headerLabel,
                  this.theme.fontFor(UiTone.LABEL),
                  section.category.color,
                  section.x,
                  UiSizing.alignTextY(
                     sectionTop,
                     this.stepPickerSectionLabelHeight(),
                     this.theme.fontHeight(UiTone.LABEL),
                     0
                  ),
                  false
               );
            }

            for (AutismMacroEditorOverlay.StepPickerButtonLayout button : section.buttons) {
               int cardX = button.x;
               int cardY = button.y;
               int cardW = button.width;
               int cardH = button.height;
               if (cardY + cardH >= gridY && cardY <= gridY + gridH) {
                  boolean hovered = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cardY && mouseY < cardY + cardH;
                  this.drawStepPickerCard(context, section.category, button.entry, cardX, cardY, cardW, cardH, hovered, mouseX, mouseY);
                  if (hovered) {
                  this.hoveredTooltip = button.entry.description;
                  this.tooltipX = mouseX + 8;
                  this.tooltipY = mouseY + 12;
                  }
               }
            }
         }
      } finally {
         autismclient.gui.vanillaui.UiScissorStack.global().pop(context);
      }

      CompactScrollbar.Metrics stepPickerScrollbar = this.getStepPickerScrollbarMetrics();
      CompactScrollbar.draw(context, stepPickerScrollbar, stepPickerScrollbar.contains(mouseX, mouseY), this.activeScrollbarDrag == 2);
   }

   private int stepPickerSectionLabelHeight() {
      return Math.max(11, this.theme.fontHeight(UiTone.LABEL));
   }

   private boolean handleStepPickerClick(double mouseX, double mouseY, int button) {
      int pickerX = this.getStepPickerX();
      int pickerY = this.getStepPickerY();
      int pickerW = this.getStepPickerWidth();
      int pickerH = this.getStepPickerHeight();
      int closeX = this.getStepPickerCloseX();
      int closeY = this.getStepPickerCloseY();
      boolean inside = mouseX >= pickerX && mouseX < pickerX + pickerW && mouseY >= pickerY && mouseY < pickerY + pickerH;
      if (!inside) {
         this.closeStepPicker();
         return true;
      } else if (button != 0) {
         return true;
      } else if (mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12) {
         this.closeStepPicker();
         return true;
      } else {
         int contentX = pickerX + 6;
         int contentY = pickerY + 42;
         int contentW = pickerW - 19;
         int contentH = pickerH - 48;
         this.syncStepPickerSearchFieldBounds();
         if (this.stepPickerSearchField != null && this.stepPickerSearchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
         }
         if (this.stepPickerSearchField != null) this.stepPickerSearchField.setFocused(false);
         CompactScrollbar.Metrics stepPickerScrollbar = this.getStepPickerScrollbarMetrics();
         if (stepPickerScrollbar.hasScroll() && stepPickerScrollbar.contains((int)mouseX, (int)mouseY)) {
            this.activeScrollbarDrag = 2;
            boolean onThumb = mouseY >= stepPickerScrollbar.thumbY() && mouseY < stepPickerScrollbar.thumbY() + stepPickerScrollbar.thumbHeight();
            this.scrollbarGrabOffset = onThumb ? Math.max(0, (int)mouseY - stepPickerScrollbar.thumbY()) : stepPickerScrollbar.thumbHeight() / 2;
            this.stepPickerScrollOffset = CompactScrollbar.scrollFromThumb(stepPickerScrollbar, (int)mouseY, this.scrollbarGrabOffset);
            return true;
         }
         if (!(mouseX < contentX) && !(mouseX >= contentX + contentW) && !(mouseY < contentY) && !(mouseY >= contentY + contentH)) {
            for (AutismMacroEditorOverlay.StepPickerSectionLayout section : this.buildStepPickerLayouts(
               contentX, contentY - this.stepPickerScrollOffset, contentW
            )) {
               for (AutismMacroEditorOverlay.StepPickerButtonLayout buttonLayout : section.buttons) {
                  int cardX = buttonLayout.x;
                  int cardY = buttonLayout.y;
                  int cardW = buttonLayout.width;
                  int cardH = buttonLayout.height;
                  if (mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cardY && mouseY < cardY + cardH) {
                     this.closeStepPicker();
                     buttonLayout.entry.action.run();
                     return true;
                  }
               }
            }

            return true;
         } else {
            return true;
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      }
      if (this.stepCtxMenu.isOpen()) {
         if (button == 0) {

            if (this.stepCtxMenu.handleClick(mouseX, mouseY, button, (idx, label, i) -> this.executeStepCtxAction(idx, i))) return true;
         } else if (button == 1 && !this.stepCtxMenu.isMouseOver(mouseX, mouseY)) {

            this.stepCtxMenu.close();
         }
      }
      if (!this.uiDropdowns.isEmpty()) {
         if (CompactDropdown.mouseClicked(this.uiDropdowns, mouseX, mouseY, button)) return true;
         if (CompactDropdown.isMenuOpen(this.uiDropdowns)) return true;
      }
      if (this.isBindingKey && AutismBindUtil.isAllowedMouseButton(button)) {
         this.macro.keyCode = AutismBindUtil.encodeMouseButton(button);
         this.isBindingKey = false;
         this.recreateComponents();
         this.clearStepSelection();
         return true;
      } else if (button == 0 && this.isOverHeaderUi(mouseX, mouseY)) {
         if (this.isOverCloseButtonUi(mouseX, mouseY)) {
            this.close();
            return true;
         }

         this.clearStepSelection();
         this.isWindowDragging = true;
         this.headerDragMoved = false;
         this.dragOffsetX = mouseX - this.panelX;
         this.dragOffsetY = mouseY - this.panelY;
         this.headerPressMouseX = mouseX;
         this.headerPressMouseY = mouseY;
         this.headerPressPanelX = this.panelX;
         this.headerPressPanelY = this.panelY;
         return true;
      } else if (this.collapsed) {
         return false;
      } else if (this.isStepPickerOpen()) {
         return this.handleStepPickerClick(mouseX, mouseY, button);
      } else {
         MouseButtonEvent click = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
         boolean fieldClicked = false;
         boolean stepListLocked = this.isStepListLockedForRun();
         if (!stepListLocked && this.nameField.mouseClicked(click, false)) {
            this.nameField.setFocused(true);
            if (this.loopCountField != null) {
               this.loopCountField.setFocused(false);
            }

            fieldClicked = true;
         } else if (!stepListLocked && this.loopMode == 1 && this.loopCountField != null && this.loopCountField.mouseClicked(click, false)) {
            this.loopCountField.setFocused(true);
            this.nameField.setFocused(false);
            fieldClicked = true;
         }

         if (fieldClicked) {
            this.clearStepSelection();
            return true;
         }

         this.nameField.setFocused(false);
         if (this.loopCountField != null) {
            this.loopCountField.setFocused(false);
         }

          this.updateHistoryButtonState();
          for (int i = 0; i < this.btnLabels.size(); i++) {
             int[] b = this.btnBounds.get(i);
             if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                this.clearStepSelection();
                boolean enabled = this.btnEnabled.get(i);
                boolean macroCurrentlyRunning = this.isEditingRunningMacro();
                 String label = i == this.runBtnIndex ? this.runButtonLabel(macroCurrentlyRunning) : this.btnLabels.get(i);
                CompactOverlayButton.Variant variant = CompactOverlayButton.Variant.SECONDARY;
                if (i == this.runBtnIndex) {
                   variant = macroCurrentlyRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS;
                }

                Runnable secondary = i < this.btnSecondaryActions.size() ? this.btnSecondaryActions.get(i) : null;
                CompactOverlayButton buttonView = this.createOverlayButton(b[0], b[1], b[2], b[3], label, variant, enabled, this.btnActions.get(i), secondary);
                CompactOverlayButton.fireIfHit(buttonView, mouseX, mouseY, button);
                return true;
             }
          }

         int listY = this.getStepListY();
         int listHeight = this.getStepListHeight();
         if (mouseY >= listY && mouseY <= listY + listHeight && mouseX >= this.getStepListFrameX() && mouseX <= this.getStepListFrameX() + this.getStepListFrameWidth()) {
            CompactScrollbar.Metrics stepListScrollbar = this.getStepListScrollbarMetrics();
            if (button == 0 && stepListScrollbar.hasScroll() && stepListScrollbar.contains((int)mouseX, (int)mouseY)) {
               this.clearStepSelection();
               this.activeScrollbarDrag = 1;
               boolean onThumb = mouseY >= stepListScrollbar.thumbY() && mouseY < stepListScrollbar.thumbY() + stepListScrollbar.thumbHeight();
               this.scrollbarGrabOffset = onThumb ? Math.max(0, (int)mouseY - stepListScrollbar.thumbY()) : stepListScrollbar.thumbHeight() / 2;
               this.scrollOffset = this.quantizeScrollOffset(
                  CompactScrollbar.scrollFromThumb(stepListScrollbar, (int)mouseY, this.scrollbarGrabOffset),
                  this.actionRowHeight(),
                  stepListScrollbar.maxScroll()
               );
               return true;
            }

            int y = listY - this.scrollOffset;
            int controlSize = this.stepRowControlSize();
            int controlStride = this.stepRowControlStride();
            int index = (int)((mouseY - y) / this.actionRowHeight());
            if (this.isStepListLockedForRun()) {
               this.clearStepSelection();
               this.clearStepDragState();

               if (button == 1 && index >= 0 && index < this.macro.actions.size()) {
                  int lockedRowY = y + index * this.actionRowHeight();
                  if (this.isStepRowFullyVisible(lockedRowY) && mouseX < this.getStepListContentRight()) {
                     this.stepCtxMenu.open((int)mouseX, (int)mouseY, index);
                  }
               }
               return true;
            }
            if (index >= 0 && index < this.macro.actions.size()) {
               MacroAction action = this.macro.actions.get(index);
               GroupRowInfo group = this.getGroupRowInfo(index, action);
               boolean groupBody = group.inGroup() && !group.header;
               boolean multiBody = group.multi && !group.header;
               boolean gateEnd = group.gate && action instanceof EndPacketGateAction;
               boolean allowMove = !groupBody || multiBody || gateEnd;
               boolean allowDuplicate = (!group.inGroup() || multiBody) && !(action instanceof PacketGateAction) && !(action instanceof EndPacketGateAction);
               boolean allowDelete = (!groupBody || multiBody) && !(action instanceof EndPacketGateAction);
               int actionY = y + index * this.actionRowHeight();
               if (!this.isStepRowFullyVisible(actionY)) {
                  return true;
               }
               int controlY = this.stepRowControlY(actionY);
               int cx = this.getActionRowControlsX(action, allowMove, allowDuplicate, allowDelete);
               if (allowMove) {
                  if (mouseX >= cx && mouseX < cx + controlSize) {
                     if (this.isStepIndexSelected(index) && this.moveSelectedStepUnits(-1)) {
                        return true;
                     }
                     if (index > 0) {
                        this.pushStructuralHistoryStep();
                        this.moveAction(index, index - 1);
                     }

                     return true;
                  }

                  cx += controlStride;
                  if (mouseX >= cx && mouseX < cx + controlSize) {
                     if (this.isStepIndexSelected(index) && this.moveSelectedStepUnits(1)) {
                        return true;
                     }
                     if (index < this.macro.actions.size() - 1) {
                        this.pushStructuralHistoryStep();
                        int target = index + 1;
                        if (action instanceof RaceAction race) {
                           target = Math.min(this.macro.actions.size() - 1, index + race.normalizedBodyCount(this.macro.actions, index) + 1);
                        } else if (action instanceof ReportAction report) {
                           target = Math.min(this.macro.actions.size() - 1, index + report.normalizedConditionCount(this.macro.actions, index) + 1);
                        } else if (action instanceof MultiAction multi) {
                           target = Math.min(this.macro.actions.size() - 1, index + multi.normalizedStepCount(this.macro.actions, index) + 1);
                        } else if (action instanceof PacketGateAction) {
                           target = Math.min(this.macro.actions.size() - 1, index + this.normalizedPacketGateChildCount(index) + 1);
                        }
                        this.moveAction(index, target);
                     }

                     return true;
                  }
                  cx += controlStride;
               }

               if (allowDuplicate && mouseX >= cx && mouseX < cx + controlSize) {
                  this.duplicateAction(index);
                  this.clearStepSelection();
                  return true;
               }

               if (allowDuplicate) {
                  cx += controlStride;
               }
               if (mouseX >= cx && mouseX < cx + controlSize && this.hasActionEditor(action)) {
                  this.openActionEditor(index, action);
                  return true;
               }

               if (this.hasActionEditor(action)) {
                  cx += controlStride;
               }
               if (allowDelete && mouseX >= cx && mouseX < cx + controlSize) {
                  if (this.isStepIndexSelected(index)) {
                     this.deleteSelectedSteps();
                  } else {
                     this.pushStructuralHistoryStep();
                     this.deleteActionAt(index);
                  }
                  return true;
               }

               if (button == 0 && mouseX < this.getStepListContentRight()) {
                  boolean shiftDown = this.isShiftModifierDown();
                  boolean controlDown = this.isControlModifierDown();
                  if (controlDown || shiftDown) {
                     this.toggleSelectedStepUnit(index);
                  } else {
                     this.beginStepDragPress(index, mouseX, mouseY, allowMove);
                  }
                  return true;
               }
               if (button == 1 && mouseX < this.getStepListContentRight()) {
                  this.handleStepRightClick(index, (int)mouseX, (int)mouseY);
                  return true;
               }
            }

            if (button == 0) {
               this.clearStepSelection();
               return true;
            }
         }

         if (button == 0) {
            this.clearStepSelection();
         }
         return false;
      }
   }

   private void duplicateAction(int index) {
      if (this.isStepListLockedForRun()) return;
      if (index >= 0 && index < this.macro.actions.size()) {
         GroupRowInfo group = this.getGroupRowInfo(index, this.macro.actions.get(index));
         if (group.inGroup() && !group.multi) return;
         if (group.multi && group.header) return;
         MacroAction original = this.macro.actions.get(index);
         this.pushStructuralHistoryStep();
         CompoundTag tag = original.toTag();
         MacroAction copy = AutismMacro.createActionFromTag(tag);
         if (copy != null) {
            this.macro.actions.add(index + 1, copy);
            int header = this.findRaceHeaderForBody(index);
            if (header >= 0 && this.macro.actions.get(header) instanceof RaceAction race) {
               race.bodyCount = Math.max(0, race.bodyCount + 1);
            } else {
               int reportHeader = this.findReportHeaderForBody(index);
               if (reportHeader >= 0 && this.macro.actions.get(reportHeader) instanceof ReportAction report) {
                  report.conditionCount = Math.max(0, report.conditionCount + 1);
               } else {

                  int multiHeader = this.findMultiHeaderForBody(index);
                  if (multiHeader >= 0 && this.macro.actions.get(multiHeader) instanceof MultiAction multi) {
                     multi.stepCount = Math.max(0, multi.stepCount + 1);
                  }
               }
            }
            this.handleMacroActionsChanged();
         }
      }
   }

   private void deleteActionAt(int index) {
      if (this.isStepListLockedForRun()) return;
      if (this.macro == null || index < 0 || index >= this.macro.actions.size()) return;
      if (this.isRaceBodyRow(index)) return;
      MacroAction action = this.macro.actions.get(index);
      ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlayIfExists();
      if (action instanceof RaceAction race) {
         int count = race.normalizedBodyCount(this.macro.actions, index);
         int end = Math.min(this.macro.actions.size(), index + count + 1);
         if (editor != null) {
            for (int i = index; i < end; i++) editor.closeIfEditingAction(this.macro.actions.get(i));
         }
         this.macro.actions.subList(index, end).clear();
         this.handleMacroActionsChanged();
         return;
      }
      if (action instanceof ReportAction report) {
         int count = report.normalizedConditionCount(this.macro.actions, index);
         int end = Math.min(this.macro.actions.size(), index + count + 1);
         if (editor != null) {
            for (int i = index; i < end; i++) editor.closeIfEditingAction(this.macro.actions.get(i));
         }
         this.macro.actions.subList(index, end).clear();
         this.handleMacroActionsChanged();
         return;
      }
      if (action instanceof PacketGateAction) {
         int count = this.normalizedPacketGateChildCount(index);
         int end = Math.min(this.macro.actions.size(), index + count + 1);
         if (editor != null) {
            for (int i = index; i < end; i++) editor.closeIfEditingAction(this.macro.actions.get(i));
         }
         this.macro.actions.subList(index, end).clear();
         this.handleMacroActionsChanged();
         return;
      }
      if (action instanceof MultiAction) {

         if (editor != null) editor.closeIfEditingAction(action);
         this.macro.actions.remove(index);
         this.handleMacroActionsChanged();
         return;
      }
      if (editor != null) editor.closeIfEditingAction(action);
      int header = this.findRaceHeaderForBody(index);
      int reportHeader = header < 0 ? this.findReportHeaderForBody(index) : -1;
      int multiHeader = header < 0 && reportHeader < 0 ? this.findMultiHeaderForBody(index) : -1;
      this.macro.actions.remove(index);
      if (header >= 0 && header < this.macro.actions.size() && this.macro.actions.get(header) instanceof RaceAction race) {
         race.bodyCount = Math.max(0, race.bodyCount - 1);
      } else if (reportHeader >= 0 && reportHeader < this.macro.actions.size() && this.macro.actions.get(reportHeader) instanceof ReportAction report) {
         report.conditionCount = Math.max(0, report.conditionCount - 1);
      } else if (multiHeader >= 0 && multiHeader < this.macro.actions.size() && this.macro.actions.get(multiHeader) instanceof MultiAction multi) {
         multi.stepCount = Math.max(0, multi.stepCount - 1);
      }
      this.handleMacroActionsChanged();
   }

   private String getTooltipFor(String label) {
      String clean = label.replaceAll("§.", "");

      return switch (clean) {
         case "Save" -> {
            if (this.macro == null) {
               yield "Save this macro";
            }

            String name = this.macro.name == null ? "" : this.macro.name.trim();
            if (name.isEmpty()) {
               yield "Give the macro a name first";
            }

            if (!this.isValidName(this.macro.name)) {
               yield "Pick a unique macro name before saving";
            }

            yield "Save this macro";
         }
         case "Run" -> {
            String blockedReason = this.getRunBlockedReason();
            yield blockedReason != null ? blockedReason : "Run this macro";
         }
         case "Run on POV" -> "Run this editor draft only on the bot currently under POV";
         case "Multi" -> this.povRunActive()
            ? "Run this editor draft only on the bot currently under POV"
            : "Run this editor draft on the active Multi sessions";
         case "Stop" -> "Stop the running macro";
         case "Add Action" -> "Open categorized action picker";
         case "Add Conditional" -> "Open categorized wait/condition picker";
         case "Presets" -> "Append editable preset steps";
         case "Undo" -> "Undo the last macro edit";
         case "Redo" -> "Redo the last undone macro edit";
         case "Chat" -> "Send a chat message or /command";
         case "Delay" -> "Wait N milliseconds or ticks";
         case "Packet" -> "Send a captured network packet";
         case "Item Click" -> "Click inventory items or slots";
         case "Pick Up All" -> "Pick up matching stacks into the carried item";
         case "Mouse Clk" -> "Simulate L/R/M click in world";
         case "Rotate" -> "Set player yaw & pitch";
         case "Use Item" -> "Use held item (right-click action)";
         case "Open Inv" -> "Open player inventory screen";
         case "Slot" -> "Select a hotbar slot (1-9)";
         case "Drop" -> "Drop items from slot";
         case "Close GUI" -> "Close the current screen/GUI";
         case "Swap" -> "Swap two inventory slots";
         case "Wait HP" -> "Pause until health crosses the selected threshold";
         case "Wait Block" -> "Pause until block changes";
         case "Wait Pkt" -> "Pause until packet received";
         case "Wait Item" -> "Pause until item in inventory";
         case "WaitGUI", "Wait GUI" -> "Pause until screen opens";
         case "Tick Sync" -> "Wait for next client tick";
         case "Rev Sync" -> "Sync handler revision";
         case "Srv Sync" -> "Wait for server tick timing";
         case "Go To" -> "Send Baritone goto command";
         case "Disconn" -> "Disconnect / Kick (lag+kick) / Kick Dupe (lag+bundle/action+kick)";
         case "NBT Book" -> "Sign large-data books (random/custom text, multi-book, delay)";
         case "Wait LAN" -> "Wait for LAN sync peer to reach a macro step";
         case "Module" -> "Toggle AUTISM or Meteor modules";
         case "Sneak" -> "Hold or release sneak key";
         case "Jump" -> "Press jump key for N ticks";
         case "Sprint" -> "Toggle sprint on/off";
         case "Move" -> "Walk in direction for N ticks";
         case "Look At" -> "Look at a block position";
         case "Repeat" -> "Repeat next N steps M times";
         case "Open Cont" -> "Open a container at a block position";
         case "Desync" -> "Send close packet without closing GUI (desync server state)";
         case "Cls w/o Pkt" -> "Close GUI locally without sending close packet to server";
         case "Restore GUI" -> "Restore a previously saved GUI screen and handler";
         case "Save GUI" -> "Save the current GUI screen and handler for later restore";
         case "Send Toggle" -> "Toggle whether packets are sent to server (on/off)";
         case "Delay Pkts" -> "Toggle packet delay mode (queues outgoing packets)";
         case "Store" -> "Loot/store items between container and player, or move slot->slot";
         case "Wait CD" -> "Pause until cooldown expires";
         case "Wait Pos" -> "Pause until position reached";
         case "Wait Chat" -> "Pause until chat message received";
         case "Wait Ent" -> "Pause until entity appears nearby";
         case "Wait Slot" -> "Pause until slot content changes";
         case "Wait Snd" -> "Pause until a matching sound plays";
         case "Mine" -> "Mine target blocks via Baritone until stop conditions are met";
         case "Pay" -> "Pay a list of players with a configurable command, amount, and delay";
         default -> null;
      };
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.collapsed) {
         return false;
      } else if (this.isBindingKey) {
         if (keyCode == 256) {
            this.macro.keyCode = -1;
         } else {
            this.macro.keyCode = keyCode;
         }

         this.isBindingKey = false;
         this.recreateComponents();
         return true;
      } else {
         KeyEvent keyInput = new KeyEvent(keyCode, scanCode, modifiers);
         if (CompactDropdown.isMenuOpen(this.uiDropdowns)) {
            if (keyCode == 256 || keyCode == 257) {
               CompactDropdown.closeOpenMenu(this.uiDropdowns);
            }
            return true;
         }
         if (this.isStepPickerOpen()) {
            if (this.stepPickerSearchField != null && this.stepPickerSearchField.isFocused()) {
               if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                  this.stepPickerSearchField.setFocused(false);
                  return true;
               }
               if (this.stepPickerSearchField.keyPressed(keyInput)) return true;
            }
            if (keyCode == 256 || keyCode == 257) {
               this.closeStepPicker();
            }

            return true;
         } else if (!this.hasFocusedEditorField()
            && this.isStepListLockedForRun()
            && (this.isControlModifierDown(modifiers) && (keyCode == GLFW.GLFW_KEY_C
               || keyCode == GLFW.GLFW_KEY_V
               || keyCode == GLFW.GLFW_KEY_X
               || keyCode == GLFW.GLFW_KEY_Z
               || keyCode == GLFW.GLFW_KEY_R)
               || keyCode == GLFW.GLFW_KEY_DELETE
               || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            return true;
         } else if (!this.hasFocusedEditorField() && this.isControlModifierDown(modifiers) && keyCode == GLFW.GLFW_KEY_Z) {
            this.performUndo();
            return true;
         } else if (!this.hasFocusedEditorField() && this.isControlModifierDown(modifiers) && keyCode == GLFW.GLFW_KEY_R) {
            this.performRedo();
            return true;
         } else if (!this.hasFocusedEditorField() && this.isControlModifierDown(modifiers) && keyCode == GLFW.GLFW_KEY_X) {
            this.deleteSelectedSteps();
            return true;
         } else if (!this.hasFocusedEditorField() && this.isControlModifierDown(modifiers) && keyCode == GLFW.GLFW_KEY_C) {
            return this.copySelectedStepsToClipboard();
         } else if (!this.hasFocusedEditorField() && this.isControlModifierDown(modifiers) && keyCode == GLFW.GLFW_KEY_V) {
            return this.pasteMacroStepsFromClipboardOrSelection();
         } else if (!this.hasFocusedEditorField()
            && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
            && this.deleteSelectedSteps()) {
            return true;
         } else if (keyCode == 256) {
            boolean anyFocused = this.nameField.isFocused() || this.loopMode == 1 && this.loopCountField != null && this.loopCountField.isFocused();
            if (anyFocused) {
               this.nameField.setFocused(false);
               if (this.loopCountField != null) {
                  this.loopCountField.setFocused(false);
               }

               return true;
            } else if (this.configurationOnly) {
               this.close();
               return true;
            } else {

               if (this.isJoinMacroMenuContext()) {
                  this.close();
                  return true;
               }
               if (MC.gui.screen() != null) {
                  MC.execute(() -> MC.gui.setScreen(null));
               } else {

                  this.close();
               }
               return true;
            }
         } else {
            if (keyCode == 257) {
               if (this.nameField.isFocused()) {
                  this.nameField.setFocused(false);
                  return true;
               }

               if (this.loopMode == 1 && this.loopCountField != null && this.loopCountField.isFocused()) {
                  this.loopCountField.setFocused(false);
                  return true;
               }
            }

            if (this.nameField.keyPressed(keyInput)) {
               return true;
            } else {
               return this.loopMode == 1 && this.loopCountField != null && this.loopCountField.keyPressed(keyInput)
                  ? true
                  : this.nameField.isFocused() || this.loopMode == 1 && this.loopCountField != null && this.loopCountField.isFocused();
            }
         }
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.collapsed) {
         return false;
      } else if (this.isStepPickerOpen()) {
         if (this.stepPickerSearchField != null && this.stepPickerSearchField.isFocused()) {
            CharacterEvent charInput = new CharacterEvent(chr);
            if (this.stepPickerSearchField.charTyped(charInput)) return true;
            this.stepPickerSearchField.write(String.valueOf(chr));
         }
         return true;
      } else {
         CharacterEvent charInput = new CharacterEvent(chr);
         if (this.nameField.charTyped(charInput)) {
            return true;
         } else if (this.nameField.isFocused()) {
            this.nameField.write(String.valueOf(chr));
            return true;
         } else {
            if (this.loopMode == 1 && this.loopCountField != null) {
               if (this.loopCountField.charTyped(charInput)) {
                  return true;
               }

               if (this.loopCountField.isFocused()) {
                  this.loopCountField.write(String.valueOf(chr));
                  return true;
               }
            }

            return true;
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.visible) {
         return false;
      }
      if (!this.uiDropdowns.isEmpty()
              && (CompactDropdown.isMenuOpen(this.uiDropdowns)
                  || CompactDropdown.isInsideOpenMenu(this.uiDropdowns, mouseX, mouseY))) {
         if (CompactDropdown.mouseScrolled(this.uiDropdowns, mouseX, mouseY, amount)) return true;
      }
      if (this.isStepPickerOpen()) {
         int pickerX = this.getStepPickerX();
         int pickerY = this.getStepPickerY();
         int pickerW = this.getStepPickerWidth();
         int pickerH = this.getStepPickerHeight();
         int contentX = pickerX + 6;
         int contentY = pickerY + 42;
         int contentW = pickerW - 19;
         int contentH = pickerH - 48;
         if (mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY && mouseY < contentY + contentH) {
            int contentHeight = this.getStepPickerContentHeight();
            int maxScroll = Math.max(0, contentHeight - contentH);
            this.stepPickerScrollOffset = Math.max(0, Math.min(maxScroll, this.stepPickerScrollOffset - (int)(Math.signum(amount) * 36.0)));
            return true;
         }

         return true;
      } else {
        int listY = this.getStepListY();
        int listHeight = this.getStepListHeight();
        if (mouseY >= listY && mouseY <= listY + listHeight && mouseX >= this.panelX + 10 && mouseX <= this.panelX + this.PANEL_WIDTH - 10) {
           int maxScroll = this.getMaxStepListScroll();
            this.scrollOffset = this.quantizeScrollOffset(
               this.scrollOffset - (int)(Math.signum(amount) * (double)this.actionRowHeight()),
               this.actionRowHeight(),
               maxScroll
            );
            return true;
        }

         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (CompactDropdown.mouseDragged(this.uiDropdowns, mouseX, mouseY, button)) return true;
      if (this.stepPickerSearchField != null && this.stepPickerSearchField.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
      if (this.nameField != null && this.nameField.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
      if (this.loopCountField != null && this.loopCountField.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
      if (this.activeScrollbarDrag == 2) {
         this.stepPickerScrollOffset = CompactScrollbar.scrollFromThumb(this.getStepPickerScrollbarMetrics(), (int)mouseY, this.scrollbarGrabOffset);
         return true;
      } else if (this.activeScrollbarDrag == 1) {
         CompactScrollbar.Metrics metrics = this.getStepListScrollbarMetrics();
         this.scrollOffset = this.quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(metrics, (int)mouseY, this.scrollbarGrabOffset),
            this.actionRowHeight(),
            metrics.maxScroll()
         );
         return true;
      } else if (this.isStepListLockedForRun() && (this.draggingIndex >= 0 || this.pressedStepIndex >= 0)) {
         this.clearStepDragState();
         return true;
      } else if (button == 0 && (this.draggingIndex >= 0 || this.pressedStepIndex >= 0)) {
         this.tryStartStepDrag(mouseX, mouseY);
         if (this.draggingIndex >= 0) {
            this.updateStepDrag(mouseY);
         }

         return true;
      } else if (this.isWindowResizing && button == 0) {
         int nextWidth = this.resizeStartWidth + (int)Math.round(mouseX - this.resizeStartMouseX);
         int nextHeight = this.resizeStartHeight + (int)Math.round(mouseY - this.resizeStartMouseY);
         AutismWindowLayout nextBounds = this.clampToScreen(
            this, new AutismWindowLayout(this.panelX, this.panelY, nextWidth, nextHeight, this.visible, this.collapsed)
         );
         this.PANEL_WIDTH = nextBounds.width;
         this.PANEL_HEIGHT = nextBounds.height;
         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         this.recreateComponents();
         return true;
      } else if (this.isWindowDragging && button == 0) {
         this.panelX = (int)(mouseX - this.dragOffsetX);
         this.panelY = (int)(mouseY - this.dragOffsetY);
         int screenW = AutismUiScale.getVirtualScreenWidth();
         int screenH = AutismUiScale.getVirtualScreenHeight();
         int minVisibleWidth = Math.min(this.PANEL_WIDTH, 96);
         this.panelX = Math.max(Math.min(0, screenW - this.PANEL_WIDTH), Math.min(this.panelX, Math.max(0, screenW - minVisibleWidth)));
         this.panelY = Math.max(0, Math.min(screenH - HEADER_HEIGHT, this.panelY));
         if (this.panelX != this.headerPressPanelX
            || this.panelY != this.headerPressPanelY
            || Math.abs(mouseX - this.headerPressMouseX) >= 3.0
            || Math.abs(mouseY - this.headerPressMouseY) >= 3.0) {
            this.headerDragMoved = true;
         }

         this.recreateComponents();
         return true;
      } else {
         return false;
      }
   }

   public boolean shouldRenderAbstractContainerScreenCaptureBanner() {
      return false;
   }

   public String getAbstractContainerScreenCaptureTitle() {
      return "";
   }

   public String getAbstractContainerScreenCaptureInstruction() {
      return "";
   }

   public String getAbstractContainerScreenCaptureHoverText(Slot slot, String itemName, String registryId) {
      return "";
   }

   public boolean wantsSlotCapture() {
      return false;
   }

   public boolean onSlotRightClick(Slot slot, String itemName, String registryId) {
      return false;
   }

   private static final class StepRowSnapshot {
      private final MacroAction action;
      private final int stableId;
      private final String label;
      private final boolean conditional;
      private final int index;

      private StepRowSnapshot(MacroAction action, int stableId, String label, boolean conditional, int index) {
         this.action = action;
         this.stableId = stableId;
         this.label = label;
         this.conditional = conditional;
         this.index = index;
      }
   }

   private static final class GroupRowInfo {
      private static final GroupRowInfo NONE = new GroupRowInfo(false, false, false, -1, 0, 0, "");

      private final boolean header;
      private final boolean report;
      private final boolean gate;
      private final boolean multi;
      private final int headerIndex;
      private final int childCount;
      private final int offsetFromHeader;
      private final String roleLabel;

      private GroupRowInfo(boolean header, boolean report, boolean gate, int headerIndex, int childCount, int offsetFromHeader, String roleLabel) {
         this(header, report, gate, false, headerIndex, childCount, offsetFromHeader, roleLabel);
      }

      private GroupRowInfo(boolean header, boolean report, boolean gate, boolean multi, int headerIndex, int childCount, int offsetFromHeader, String roleLabel) {
         this.header = header;
         this.report = report;
         this.gate = gate;
         this.multi = multi;
         this.headerIndex = headerIndex;
         this.childCount = Math.max(0, childCount);
         this.offsetFromHeader = Math.max(0, offsetFromHeader);
         this.roleLabel = roleLabel == null ? "" : roleLabel;
      }

      private boolean inGroup() {
         return headerIndex >= 0;
      }

      private boolean isFirstRow() {
         return offsetFromHeader == 0;
      }

      private boolean isLastRow() {
         return offsetFromHeader >= childCount;
      }
   }

   private static final class StepImpactInfo {
      private static final StepImpactInfo NONE = new StepImpactInfo(false, false, false, false, false);

      private final boolean impacted;
      private final boolean header;
      private final boolean ping;
      private final boolean first;
      private final boolean last;

      private StepImpactInfo(boolean impacted, boolean header, boolean ping, boolean first, boolean last) {
         this.impacted = impacted;
         this.header = header;
         this.ping = ping;
         this.first = first;
         this.last = last;
      }
   }

   private enum StepRowMotionKind {
      ENTER,
      MOVE,
      EXIT
   }

   private static final class StepRowMotion {
      private final int stableId;
      private final String label;
      private final boolean conditional;
      private final int fromRowIndex;
      private final int toRowIndex;
      private final AutismMacroEditorOverlay.StepRowMotionKind kind;
      private float progress;

      private StepRowMotion(
         int stableId,
         String label,
         boolean conditional,
         int fromRowIndex,
         int toRowIndex,
         AutismMacroEditorOverlay.StepRowMotionKind kind
      ) {
         this.stableId = stableId;
         this.label = label;
         this.conditional = conditional;
         this.fromRowIndex = fromRowIndex;
         this.toRowIndex = toRowIndex;
         this.kind = kind;
      }
   }

   private static final class StepPickerButtonLayout {
      private final AutismMacroEditorOverlay.StepPickerEntry entry;
      private final int x;
      private final int y;
      private final int width;
      private final int height;

      private StepPickerButtonLayout(AutismMacroEditorOverlay.StepPickerEntry entry, int x, int y, int width, int height) {
         this.entry = entry;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }
   }

   private static final class StepUnit {
      private static final StepUnit EMPTY = new StepUnit(-1, -1);
      private final int start;
      private final int endExclusive;

      private StepUnit(int start, int endExclusive) {
         this.start = start;
         this.endExclusive = endExclusive;
      }

      private boolean isEmpty() {
         return this.start < 0 || this.endExclusive <= this.start;
      }
   }

   private static final class StepSelection {
      private final LinkedHashSet<Integer> stableIds = new LinkedHashSet<>();

      private void clear() {
         this.stableIds.clear();
      }

      private boolean isEmpty() {
         return this.stableIds.isEmpty();
      }

      private boolean containsStableId(int stableId) {
         return stableId >= 0 && this.stableIds.contains(stableId);
      }

      private void addStableId(int stableId) {
         if (stableId >= 0) this.stableIds.add(stableId);
      }

      private void removeStableId(int stableId) {
         if (stableId >= 0) this.stableIds.remove(stableId);
      }

      private void retainAll(Set<Integer> liveStableIds) {
         if (liveStableIds == null || liveStableIds.isEmpty()) {
            this.stableIds.clear();
         } else {
            this.stableIds.removeIf(stableId -> !liveStableIds.contains(stableId));
         }
      }
   }

   private static final class StepPickerCategory {
      private final String id;
      private final String label;
      private final int color;

      private StepPickerCategory(String id, String label, int color) {
         this.id = id;
         this.label = label;
         this.color = color;
      }
   }

   private static final class StepPickerEntry {
      private final String categoryId;
      private final String label;
      private final String description;
      private final Runnable action;

      private StepPickerEntry(String categoryId, String label, String description, Runnable action) {
         this.categoryId = categoryId;
         this.label = label;
         this.description = description;
         this.action = action;
      }
   }

   private static enum StepPickerMode {
      ACTION,
      CONDITIONAL,
      PRESET;
   }

   private final class StepPickerOverlay implements IAutismOverlay {
      @Override
      public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
         if (!isVisible()) return;
         context.nextStratum();
         AutismMacroEditorOverlay.this.renderStepPicker(context, mouseX, mouseY);
      }

      @Override
      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         return AutismMacroEditorOverlay.this.handleStepPickerClick(mouseX, mouseY, button);
      }

      @Override
      public boolean mouseReleased(double mouseX, double mouseY, int button) {
         return AutismMacroEditorOverlay.this.mouseReleased(mouseX, mouseY, button);
      }

      @Override
      public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
         return AutismMacroEditorOverlay.this.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }

      @Override
      public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
         return AutismMacroEditorOverlay.this.mouseScrolled(mouseX, mouseY, amount);
      }

      @Override
      public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
         return AutismMacroEditorOverlay.this.keyPressed(keyCode, scanCode, modifiers);
      }

      @Override
      public boolean charTyped(char chr, int modifiers) {
         return AutismMacroEditorOverlay.this.charTyped(chr, modifiers);
      }

      @Override
      public boolean isVisible() {
         return AutismMacroEditorOverlay.this.visible && AutismMacroEditorOverlay.this.isStepPickerOpen();
      }

      @Override
      public void setVisible(boolean visible) {
         if (!visible) AutismMacroEditorOverlay.this.closeStepPicker();
      }

      @Override
      public boolean isMouseOver(double mouseX, double mouseY) {
         return isVisible();
      }

      @Override
      public boolean isOverDragBar(double mouseX, double mouseY) {
         return false;
      }

      @Override
      public String getOverlayId() {
         return "macro-step-picker";
      }

      @Override
      public AutismWindowLayout getBounds() {
         int width = MC.getWindow() == null ? 640 : AutismUiScale.getVirtualScreenWidth();
         int height = MC.getWindow() == null ? 360 : AutismUiScale.getVirtualScreenHeight();
         return new AutismWindowLayout(0, 0, width, height, isVisible(), false);
      }

      @Override
      public int getMinWidth() {
         return 1;
      }

      @Override
      public int getMinHeight() {
         return 1;
      }
   }

   private static final class StepPickerSectionLayout {
      private final AutismMacroEditorOverlay.StepPickerCategory category;
      private final int x;
      private final int headerY;
      private final int width;
      private final List<AutismMacroEditorOverlay.StepPickerButtonLayout> buttons;
      private final int bottomY;

      private StepPickerSectionLayout(
         AutismMacroEditorOverlay.StepPickerCategory category,
         int x,
         int headerY,
         int width,
         List<AutismMacroEditorOverlay.StepPickerButtonLayout> buttons,
         int bottomY
      ) {
         this.category = category;
         this.x = x;
         this.headerY = headerY;
         this.width = width;
         this.buttons = buttons;
         this.bottomY = bottomY;
      }
   }
}
