package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.macro.CraftAction;
import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.PacketClickAction;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class AutismFabricatorOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final CompactTheme THEME = new CompactTheme();
    private static AutismFabricatorOverlay sharedOverlay;
    private int DEFAULT_PANEL_WIDTH = 236;
    private int LINE_HEIGHT = 14;
    private int CRAFT_LIST_HEIGHT = 84;
    private static final int CRAFT_LIST_ROWS = 6;
    private int CRAFT_PLAN_HEIGHT = 52;
    private static final int CRAFT_PLAN_ROWS = 4;
    private static final int SCROLLBAR_GUTTER = 8;

    private enum FabricatorAction {
        CLICK("Click", "Use Left or Right on the resolved slot.") {
            @Override boolean usesClickSelector() { return true; }
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) { return AutismDropAction.PICKUP; }
        },
        QUICK_MOVE("Quick Move", "Shift-click the resolved slot.") {
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) { return AutismDropAction.QUICK_MOVE; }
        },
        PICKUP_ALL("Pick Up All", "Double-click the resolved slot. Usually needs you to hold a matching stack first.") {
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) { return AutismDropAction.PICKUP_ALL; }
        },
        CRAFT_RESULT("Craft", "Choose a craftable item, set the total amount, then send, queue, or add it to a macro.") {
            @Override boolean isCraftAction() { return true; }
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) { return AutismDropAction.QUICK_MOVE; }
        },
        DROP("Drop", "Drop from the resolved slot. If slot and item are both set, both must match.") {
            @Override boolean usesDropToggle() { return true; }
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) {
                return dropWholeStack ? AutismDropAction.DROP_STACK : AutismDropAction.DROP_ITEM;
            }
            @Override String getDescription(boolean dropWholeStack) {
                return dropWholeStack
                    ? "Drops the whole stack from the selected slot."
                    : "Drops one item at a time from the selected slot.";
            }
        },
        PACKET("Packet", "Replay a captured slot click with the captured container id and state.") {
            @Override boolean usesClickSelector() { return true; }
            @Override boolean isPacketAction() { return true; }
            @Override AutismDropAction toPacketAction(boolean dropWholeStack) { return AutismDropAction.PICKUP; }
        };

        final String displayName;
        final String description;

        FabricatorAction(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        boolean usesClickSelector() { return false; }
        boolean usesDropToggle() { return false; }
        boolean isCraftAction() { return false; }
        boolean isPacketAction() { return false; }
        String getDescription(boolean dropWholeStack) { return description; }
        abstract AutismDropAction toPacketAction(boolean dropWholeStack);
    }

    private int panelX = 220;
    private int panelY = 5;
    private int PANEL_WIDTH = DEFAULT_PANEL_WIDTH;
    private int PANEL_HEIGHT = 392;
    private int FIELD_WIDTH = 100;
    private int BUTTON_HEIGHT = 20;
    private int LABEL_WIDTH = 64;

    private boolean isDragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private boolean collapsed = false;

    private boolean visible = false;
    private AbstractContainerScreen<?> parentScreen;
    private final List<AutismChatField> textFields = new ArrayList<>();

    private AutismChatField slotField;
    private AutismChatField itemNameField;
    private AutismChatField timesField;
    private AutismChatField craftSearchField;
    private int currentActionIndex = 0;
    private final FabricatorAction[] actions = FabricatorAction.values();
    private final List<String> actionLabels = java.util.Arrays.stream(actions).map(action -> action.displayName).toList();
    private static final List<String> STANDARD_CLICK_LABELS = List.of("Left Click", "Right Click");
    private static final List<String> PACKET_CLICK_LABELS = List.of("Left Click", "Right Click", "Quick Move");
    private static final List<String> DROP_MODE_LABELS = List.of("Single", "Stack");

    private final java.util.List<CompactDropdown> uiDropdowns = new ArrayList<>();
    private CompactDropdown actionDropdown;
    private CompactDropdown clickModeDropdown;
    private CompactDropdown dropModeDropdown;

    private int currentButtonIndex = 0;
    private final String[] buttonTypes = {"Left Click", "Right Click", "Quick Move"};
    private boolean dropWholeStack = false;
    private AutismPacketClick.Target packetClickTarget = null;

    private Integer selectedSlotId = null;
    private String statusMessage = "";
    private ChatFormatting statusColor = ChatFormatting.GRAY;
    private long statusExpiresAtMs = 0L;
    private final List<AutismCraftingHelper.CraftableRecipeOption> craftableRecipes = new ArrayList<>();
    private List<AutismCraftingHelper.CraftableRecipeOption> filteredCraftableRecipes = new ArrayList<>();
    private int readyCraftableRecipeCount = 0;
    private int craftScrollOffset = 0;
    private final ScrollState craftListScrollState = new ScrollState();
    private AutismCraftingHelper.CraftableRecipeOption selectedCraftOption = null;
    private ItemStack selectedCraftResult = ItemStack.EMPTY;
    private final List<CraftAction.CraftEntry> plannedCraftEntries = new ArrayList<>();
    private int craftPlanSelectedIndex = -1;
    private int craftPlanScrollOffset = 0;
    private final ScrollState craftPlanScrollState = new ScrollState();

    private static final java.util.concurrent.ExecutorService CRAFT_SCAN_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "autism-craft-scan");
            t.setDaemon(true);
            return t;
        });
    private final java.util.concurrent.atomic.AtomicReference<ScanResult> pendingScanResult = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicLong scanGeneration = new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean scanInFlight;
    private int lastScanFingerprint;
    private boolean hasScannedOnce;

    private static final class ScanResult {
        final long generation;
        final Object connection;
        final List<AutismCraftingHelper.CraftableRecipeOption> options;

        ScanResult(long generation, Object connection, List<AutismCraftingHelper.CraftableRecipeOption> options) {
            this.generation = generation;
            this.connection = connection;
            this.options = options;
        }
    }

    private volatile boolean craftExecutionInProgress = false;
    private boolean craftUseMaxAmount = false;
    private int activeScrollbarDrag = 0;
    private int scrollbarGrabOffset = 0;

    private static final int SCROLLBAR_NONE = 0;
    private static final int SCROLLBAR_CRAFT_PLAN = 1;
    private static final int SCROLLBAR_CRAFT_LIST = 2;

    private String savedSlotValue = "0";
    private String savedItemNameValue = "";
    private ItemTarget savedItemTarget = new ItemTarget();
    private String savedTimesValue = "1";
    private String savedCraftSearchValue = "";
    private int savedCraftSelectedRecipeId = -1;
    private String savedCraftSelectedRecipeKey = "";

    private int getFieldWidth() {
        return Math.max(108, PANEL_WIDTH - LABEL_WIDTH - 26);
    }

    private int getBottomButtonY() {
        return panelY + PANEL_HEIGHT - BUTTON_HEIGHT - 18;
    }

    private int getStatusY() {
        return Math.max(getInfoFloorY(), getActionInfoY() - 11);
    }

    private int getActionInfoY() {
        return Math.max(getInfoFloorY() + 11, getBottomButtonY() - 3 - getActionDescriptionLines().size() * 10);
    }

    private List<String> getActionDescriptionLines() {
        return UiContexts.textRenderer(MC.font).wrapFully(
            currentAction.getDescription(dropWholeStack), Math.max(1, PANEL_WIDTH - 20));
    }

    private int getInfoFloorY() {
        if (isCraftMode()) return getCraftListY() + CRAFT_LIST_HEIGHT + 12;
        return getStandardTimesRowY() + 18;
    }

    private int requiredPanelHeightForInfoRows(int floorY) {
        int relativeFloor = Math.max(0, floorY - panelY);
        return relativeFloor + 11 + getActionDescriptionLines().size() * 10 + 3 + BUTTON_HEIGHT + 18;
    }

    private boolean isCraftMode() {
        return currentAction.isCraftAction();
    }

    private boolean showsTargetFields() {
        return !isCraftMode();
    }

    private boolean showsSlotItemRows() {
        return showsTargetFields() && !currentAction.isPacketAction();
    }

    private int getActionRowY() {
        return panelY + 24;
    }

    private int getSlotRowY() {
        return getActionRowY() + 22;
    }

    private int getItemRowY() {
        return getSlotRowY() + (showsSlotItemRows() ? 22 : 0);
    }

    private int getOptionRowY() {
        return getItemRowY() + (showsSlotItemRows() ? 22 : 0);
    }

    private int getStandardTimesRowY() {
        return (isClickSelectorRelevant() || isDropModeRelevant()) ? getOptionRowY() + 22 : getItemRowY() + 22;
    }

    private int getCraftSearchY() {
        return getCraftSearchLabelY() + 11;
    }

    private int getCraftAmountRowY() {
        return getCraftSummaryY() + 14;
    }

    private int getCraftAmountToggleWidth() {
        return 78;
    }

    private int getCraftAmountFieldX() {
        return panelX + LABEL_WIDTH + 15;
    }

    private int getCraftAmountToggleX() {
        return getCraftAmountFieldX() + getCraftAmountFieldWidth() + 4;
    }

    private int getCraftAddButtonWidth() {
        return 44;
    }

    private int getCraftAmountFieldWidth() {
        return Math.max(48, getFieldWidth() - getCraftAmountToggleWidth() - getCraftAddButtonWidth() - 8);
    }

    private int getCraftAddButtonX() {
        return getCraftAmountToggleX() + getCraftAmountToggleWidth() + 4;
    }

    private int getCraftPlanHeaderY() {
        return getSlotRowY() + 2;
    }

    private int getCraftPlanListY() {
        return getSlotRowY() + 13;
    }

    private int getCraftSummaryY() {
        return getCraftPlanListY() + CRAFT_PLAN_HEIGHT + 3;
    }

    private int getCraftSearchLabelY() {
        return getCraftAmountRowY() + 18;
    }

    private boolean isClickSelectorRelevant() {
        return currentAction.usesClickSelector();
    }

    private int getClickSelectorOptionCount() {
        return currentAction.isPacketAction() ? AutismPacketClick.Mode.values().length : 2;
    }

    private AutismPacketClick.Mode getCurrentPacketMode() {
        return AutismPacketClick.Mode.byIndex(currentButtonIndex);
    }

    private boolean isDropModeRelevant() {
        return currentAction.usesDropToggle();
    }

    private int getEffectiveButton() {
        if (currentAction.isPacketAction()) {
            return getCurrentPacketMode().button;
        }
        if (currentAction.usesDropToggle()) {
            return dropWholeStack ? 1 : 0;
        }
        AutismDropAction packetAction = currentAction.toPacketAction(dropWholeStack);
        return packetAction.usesFixedButton() ? packetAction.getButton() : Math.min(currentButtonIndex, 1);
    }

    private String getClickSelectorLabel() {
        if (currentAction.isPacketAction()) return getCurrentPacketMode().displayName;
        if (isClickSelectorRelevant()) return buttonTypes[Math.min(currentButtonIndex, 1)];
        if (isDropModeRelevant()) return dropWholeStack ? "Whole Stack" : "Single Item";
        return "Auto";
    }

    private void drawOverlayButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, int mouseX, int mouseY) {
        CompactOverlayControls.action(context, MC.font, x, y, w, h, label, variant, enabled, mouseX, mouseY);
    }

    private void drawOverlayToggleButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean enabled, String animationKey, int mouseX, int mouseY) {
        CompactOverlayControls.toggle(context, MC.font, x, y, w, h, label, enabled, animationKey, mouseX, mouseY);
    }

    public AutismFabricatorOverlay(AbstractContainerScreen<?> parentScreen) {
        this.parentScreen = parentScreen;
        applyPresetMetrics();
        this.PANEL_WIDTH = DEFAULT_PANEL_WIDTH;
        this.PANEL_HEIGHT = craftModePanelHeight();
    }

    public static synchronized AutismFabricatorOverlay getSharedOverlay(AbstractContainerScreen<?> parentScreen) {
        if (sharedOverlay == null) {
            sharedOverlay = new AutismFabricatorOverlay(parentScreen);
            sharedOverlay.restoreState();
        } else {
            sharedOverlay.parentScreen = parentScreen;
        }
        return sharedOverlay;
    }

    @Override
    public int getMinWidth() {
        return DEFAULT_PANEL_WIDTH;
    }

    @Override
    public int getMinHeight() {
        return standardPanelHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        PANEL_HEIGHT = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
        if (visible) initWidgets();
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            initWidgets();
            AutismOverlayManager.get().bringToFront(this);
        } else {
            clearWidgets();
        }
        saveState();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        int panelHeight = collapsed ? 18 : PANEL_HEIGHT;
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
               mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
               mouseY >= panelY && mouseY <= panelY + 18 &&
               !isOverWindowControl(mouseX, mouseY, bounds);
    }

    @Override
    public boolean hasTextFieldFocused() {
        for (AutismChatField field : textFields) {
            if (field.isFocused()) return true;
        }
        return false;
    }

    @Override
    public void clearTextFieldFocus() {
        for (AutismChatField field : textFields) {
            field.setFocused(false);
        }
    }

    public void saveState() {
        AutismSharedState shared = AutismSharedState.get();
        shared.setFabricatorOverlayVisible(visible);
        shared.setFabricatorOverlayX(panelX);
        shared.setFabricatorOverlayY(panelY);

        shared.setFabricatorSlotValue(savedSlotValue);
        shared.setFabricatorItemNameValue(savedItemNameValue);
        shared.setFabricatorTimesValue(savedTimesValue);
        shared.setFabricatorActionIndex(currentActionIndex);
        shared.setFabricatorButtonIndex(currentButtonIndex);
        shared.setFabricatorDropWholeStack(dropWholeStack);
        shared.setFabricatorCraftUseMaxAmount(craftUseMaxAmount);
        shared.setFabricatorCraftSearchValue(savedCraftSearchValue);
        shared.setFabricatorCraftSelectedRecipeId(savedCraftSelectedRecipeId);
        shared.setFabricatorCraftSelectedRecipeKey(savedCraftSelectedRecipeKey);
        shared.setFabricatorCraftScrollOffset(craftScrollOffset);
        shared.setFabricatorCraftPlanEntries(plannedCraftEntries);
        shared.setFabricatorCraftPlanSelectedIndex(craftPlanSelectedIndex);
        shared.setFabricatorCraftPlanScrollOffset(craftPlanScrollOffset);
        saveLayout();
    }

    public void restoreState() {
        AutismSharedState shared = AutismSharedState.get();
        restoreLayout();
        this.visible = shared.isFabricatorOverlayVisible();
        this.panelX = shared.getFabricatorOverlayX();
        this.panelY = shared.getFabricatorOverlayY();

        this.savedSlotValue = shared.getFabricatorSlotValue();
        this.savedItemNameValue = shared.getFabricatorItemNameValue();
        this.savedTimesValue = shared.getFabricatorTimesValue();
        this.currentActionIndex = shared.getFabricatorActionIndex();
        this.currentButtonIndex = shared.getFabricatorButtonIndex();
        this.dropWholeStack = shared.isFabricatorDropWholeStack();
        this.craftUseMaxAmount = shared.isFabricatorCraftUseMaxAmount();
        this.savedCraftSearchValue = shared.getFabricatorCraftSearchValue();
        this.savedCraftSelectedRecipeId = shared.getFabricatorCraftSelectedRecipeId();
        this.savedCraftSelectedRecipeKey = shared.getFabricatorCraftSelectedRecipeKey();
        this.craftScrollOffset = shared.getFabricatorCraftScrollOffset();
        this.craftListScrollState.restore(this.craftScrollOffset * LINE_HEIGHT);
        this.plannedCraftEntries.clear();
        this.plannedCraftEntries.addAll(shared.getFabricatorCraftPlanEntries());
        this.craftPlanSelectedIndex = shared.getFabricatorCraftPlanSelectedIndex();
        this.craftPlanScrollOffset = shared.getFabricatorCraftPlanScrollOffset();
        this.craftPlanScrollState.restore(this.craftPlanScrollOffset * 13);
        this.selectedCraftOption = null;
        this.selectedCraftResult = ItemStack.EMPTY;

        if (currentActionIndex < 0 || currentActionIndex >= actions.length) {
            currentActionIndex = 0;
        }
        this.currentAction = actions[currentActionIndex];
        if (currentButtonIndex < 0 || currentButtonIndex >= buttonTypes.length) {
            currentButtonIndex = 0;
        }

        if (visible) {
            initWidgets();
        }
    }

    private void initWidgets() {
        clearWidgets();

        if (!isDragging) {
            updateListHeights();
            PANEL_HEIGHT = isCraftMode() ? craftModePanelHeight() : standardPanelHeight();
        }

        int fieldX = panelX + LABEL_WIDTH + 15;
        int fieldWidth = getFieldWidth();
        currentAction = actions[currentActionIndex];

        slotField = new AutismChatField(MC, MC.font, fieldX, getSlotRowY(), fieldWidth, 16, false);
        slotField.setPlaceholder(Component.literal("Optional slot - right-click to fill"));
        slotField.setText(savedSlotValue);
        slotField.setMaxLength(5);
        slotField.setChangedListener(text -> {
            savedSlotValue = text;
            clearStatus();

            try {
                int stableSlot = Integer.parseInt(text);
                int slotId = AutismInventoryHelper.resolveConfiguredHandlerSlot(MC, stableSlot);
                ItemTarget slotTarget = getItemTargetFromSlot(slotId);
                String itemName = slotTarget.editorText();
                if (!itemName.isEmpty()) {
                    savedItemTarget = slotTarget;
                    savedItemNameValue = itemName;
                    if (itemNameField != null) {
                        itemNameField.setText(itemName);
                    }
                }
            } catch (NumberFormatException ignored) {  }
        });
        if (showsSlotItemRows()) {
            textFields.add(slotField);
        }

        itemNameField = new AutismChatField(MC, MC.font, fieldX, getItemRowY(), fieldWidth, 16, false);
        itemNameField.setPlaceholder(Component.literal("Optional item match").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)));
        itemNameField.setText(savedItemNameValue);
        itemNameField.setMaxLength(50);
        itemNameField.setDisplayTextProvider(value -> {
            Component rich = savedItemTarget == null ? null : savedItemTarget.editorComponent(value);
            return rich != null && value != null && value.equals(rich.getString()) ? rich.copy() : null;
        });
        itemNameField.setChangedListener(text -> {
            savedItemNameValue = text;
            if (savedItemTarget != null && !text.equals(savedItemTarget.editorText())) {
                savedItemTarget = savedItemTarget.hasRichText()
                        ? savedItemTarget.withEditedDisplay(text)
                        : ItemTarget.fromLegacyEntry(text);
            }
            clearStatus();
        });
        if (showsSlotItemRows()) {
            textFields.add(itemNameField);
        }

        int timesY = isCraftMode() ? getCraftAmountRowY() : getStandardTimesRowY();
        int timesX = isCraftMode() ? getCraftAmountFieldX() : fieldX;
        int timesWidth = isCraftMode() ? getCraftAmountFieldWidth() : fieldWidth;
        timesField = new AutismChatField(MC, MC.font, timesX, timesY, timesWidth, 16, false);
        timesField.setPlaceholder(Component.literal("1"));
        timesField.setText(savedTimesValue);
        timesField.setMaxLength(5);
        timesField.setEditable(!(isCraftMode() && craftUseMaxAmount));
        timesField.setChangedListener(text -> {
            savedTimesValue = text;
            if (isCraftMode() && craftPlanSelectedIndex >= 0 && craftPlanSelectedIndex < plannedCraftEntries.size()) {
                try {
                    int val = Integer.parseInt(text.replaceAll("[^0-9]", ""));
                    plannedCraftEntries.get(craftPlanSelectedIndex).amount = Math.max(1, val);
                } catch (Exception ignored) {  }
            }
            clearStatus();
        });
        textFields.add(timesField);

        if (isCraftMode()) {
            int searchY = getCraftSearchY();
            craftSearchField = new AutismChatField(MC, MC.font, panelX + 10, searchY, PANEL_WIDTH - 20, 16, false);
            craftSearchField.setPlaceholder(Component.literal("Search crafting recipes..."));
            craftSearchField.setText(savedCraftSearchValue);
            craftSearchField.setChangedListener(text -> {
                savedCraftSearchValue = text;
                clearStatus();
                updateCraftRecipeFilter(text, true);
                saveState();
            });
            textFields.add(craftSearchField);
            pollCraftScan();
        } else {
            craftSearchField = null;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (!uiDropdowns.isEmpty()) {
            if (CompactDropdown.mouseClicked(uiDropdowns, mouseX, mouseY, button)) return true;
            if (CompactDropdown.isMenuOpen(uiDropdowns)) return true;
        }

        if (button == 0 && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
            mouseY >= panelY && mouseY <= panelY + 20) {
            AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, collapsed ? 18 : PANEL_HEIGHT, visible, collapsed);

            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }

            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }

            isDragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            for (AutismChatField field : textFields) {
                field.setFocused(false);
            }
            return true;
        }

        if (collapsed) return false;

        MouseButtonEvent click = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));

        boolean clickedField = false;

        for (AutismChatField field : textFields) {
            boolean wasClicked = field.mouseClicked(click, false);
            if (wasClicked) {
                field.setFocused(true);
                clickedField = true;
            } else {
                field.setFocused(false);
            }
        }

        if (clickedField) return true;

        if (isCraftMode()) {
            pollCraftScan();
            int listX = getCraftListX();
            int listWidth = getCraftListWidth();
            int planContentWidth = getCraftPlanContentWidth();
            int recipeContentWidth = getCraftListContentWidth();
            int planHeaderY = getCraftPlanHeaderY();
            int planListY = getCraftPlanListY();
            int listY = getCraftListY();

            if (button == 0) {
                CompactScrollbar.Metrics craftPlanScrollbar = getCraftPlanScrollbarMetrics();
                if (craftPlanScrollbar.hasScroll() && craftPlanScrollbar.contains((int) mouseX, (int) mouseY)) {
                    activeScrollbarDrag = SCROLLBAR_CRAFT_PLAN;
                    scrollbarGrabOffset = Math.max(0, (int) mouseY - craftPlanScrollbar.thumbY());
                    craftPlanScrollOffset = toRowScroll(craftPlanScrollbar, (int) mouseY, scrollbarGrabOffset, 13, Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS));
                    craftPlanScrollState.jumpTo(craftPlanScrollOffset * 13, Math.max(0, plannedCraftEntries.size() * 13 - CRAFT_PLAN_HEIGHT));
                    saveState();
                    return true;
                }

                CompactScrollbar.Metrics craftListScrollbar = getCraftListScrollbarMetrics();
                if (craftListScrollbar.hasScroll() && craftListScrollbar.contains((int) mouseX, (int) mouseY)) {
                    activeScrollbarDrag = SCROLLBAR_CRAFT_LIST;
                    scrollbarGrabOffset = Math.max(0, (int) mouseY - craftListScrollbar.thumbY());
                    craftScrollOffset = toRowScroll(craftListScrollbar, (int) mouseY, scrollbarGrabOffset, LINE_HEIGHT, Math.max(0, filteredCraftableRecipes.size() - CRAFT_LIST_ROWS));
                    craftListScrollState.jumpTo(craftScrollOffset * LINE_HEIGHT, Math.max(0, filteredCraftableRecipes.size() * LINE_HEIGHT - CRAFT_LIST_HEIGHT));
                    saveState();
                    return true;
                }
            }

            if (!plannedCraftEntries.isEmpty()
                && mouseX >= panelX + PANEL_WIDTH - 74 && mouseX < panelX + PANEL_WIDTH - 10
                && mouseY >= planHeaderY - 2 && mouseY < planHeaderY + 10
                && button == 0) {
                plannedCraftEntries.clear();
                craftPlanSelectedIndex = -1;
                craftPlanScrollOffset = 0;
                craftPlanScrollState.jumpTo(0, 0);
                clearStatus();
                saveState();
                return true;
            }

            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= planListY && mouseY <= planListY + CRAFT_PLAN_HEIGHT && button == 0) {
                int maxPlanScrollPx = Math.max(0, plannedCraftEntries.size() * 13 - CRAFT_PLAN_HEIGHT);
                int drawPlanScroll = craftPlanScrollState.tick(0.0f, maxPlanScrollPx);
                int firstIndex = drawPlanScroll / 13;
                int rowY = planListY - (drawPlanScroll % 13);
                for (int index = firstIndex; index < plannedCraftEntries.size() && rowY < planListY + CRAFT_PLAN_HEIGHT; index++, rowY += 13) {
                    if (mouseY < rowY || mouseY >= rowY + 12) continue;
                    if (mouseX >= listX + planContentWidth - 16 && mouseX < listX + planContentWidth - 2) {
                        removePlannedCraftEntry(index);
                        return true;
                    }
                    if (mouseX >= listX && mouseX < listX + planContentWidth) {
                        if (craftPlanSelectedIndex == index) {
                            selectCraftPlanEntry(-1);
                        } else {
                            selectCraftPlanEntry(index);
                        }
                        for (AutismChatField field : textFields) field.setFocused(false);
                        return true;
                    }
                }
                if (mouseX >= listX && mouseX < listX + planContentWidth) {
                    selectCraftPlanEntry(-1);
                    for (AutismChatField field : textFields) field.setFocused(false);
                    return true;
                }
            }

            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + CRAFT_LIST_HEIGHT && button == 0) {
                int maxRecipeScrollPx = Math.max(0, filteredCraftableRecipes.size() * LINE_HEIGHT - CRAFT_LIST_HEIGHT);
                int drawRecipeScroll = craftListScrollState.tick(0.0f, maxRecipeScrollPx);
                int firstIndex = drawRecipeScroll / LINE_HEIGHT;
                int rowY = listY - (drawRecipeScroll % LINE_HEIGHT);
                for (int index = firstIndex; index < filteredCraftableRecipes.size() && rowY < listY + CRAFT_LIST_HEIGHT; index++, rowY += LINE_HEIGHT) {
                    if (mouseX >= listX && mouseX < listX + recipeContentWidth && mouseY >= rowY && mouseY < rowY + LINE_HEIGHT) {
                        selectCraftRecipe(filteredCraftableRecipes.get(index));
                        addOrUpdatePlannedCraftEntry();
                        for (AutismChatField field : textFields) field.setFocused(false);
                        return true;
                    }
                }
                if (mouseX >= listX && mouseX < listX + recipeContentWidth) {
                    clearCraftRecipeSelection();
                    for (AutismChatField field : textFields) field.setFocused(false);
                    saveState();
                    return true;
                }
            }
        }

        if (isCraftMode()) {
            int toggleX = getCraftAmountToggleX();
            int toggleY = getCraftAmountRowY();
            int toggleW = getCraftAmountToggleWidth();
            if (mouseY >= toggleY && mouseY < toggleY + 16 && mouseX >= toggleX && mouseX < toggleX + toggleW && (button == 0 || button == 1)) {
                craftUseMaxAmount = !craftUseMaxAmount;
                if (timesField != null) timesField.setEditable(!craftUseMaxAmount);
                if (craftPlanSelectedIndex >= 0 && craftPlanSelectedIndex < plannedCraftEntries.size()) {
                    plannedCraftEntries.get(craftPlanSelectedIndex).useMaxAmount = craftUseMaxAmount;
                }
                clearStatus();
                saveState();
                for (AutismChatField field : textFields) field.setFocused(false);
                return true;
            }
        }

        int btnY = getBottomButtonY();
        int btnArea = PANEL_WIDTH - 20;
        int gap = 2;
        int bw = (btnArea - gap * 2) / 3;
        int bx = panelX + 10;
        if (mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT && button == 0) {
            if (mouseX >= bx && mouseX < bx + bw) {
                send(false);
                for (AutismChatField field : textFields) field.setFocused(false);
                return true;
            }
            bx += bw + gap;
            if (mouseX >= bx && mouseX < bx + bw) {
                send(true);
                for (AutismChatField field : textFields) field.setFocused(false);
                return true;
            }
            bx += bw + gap;
            if (mouseX >= bx && mouseX < bx + bw) {

                AutismMacroEditorOverlay macroEditor = null;
                for (IAutismOverlay ov : AutismOverlayManager.get().getOverlays()) {
                    if (ov instanceof AutismMacroEditorOverlay) {
                        macroEditor = (AutismMacroEditorOverlay) ov;
                        break;
                    }
                }
                if (macroEditor != null && macroEditor.isVisible()) {
                    sendToMacro(macroEditor);
                } else {
                    setStatus("Open a macro editor first!", ChatFormatting.RED);
                }
                for (AutismChatField field : textFields) field.setFocused(false);
                return true;
            }
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        CompactDropdown.mouseReleased(uiDropdowns);
        if (button == 0 && activeScrollbarDrag != SCROLLBAR_NONE) {
            activeScrollbarDrag = SCROLLBAR_NONE;
            saveState();
            return true;
        }
        if (button == 0) {
            if (isDragging) {
                saveState();
            }
            isDragging = false;
        }
        for (AutismChatField field : textFields) {
            if (field.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (CompactDropdown.mouseDragged(uiDropdowns, mouseX, mouseY, button)) return true;
        if (activeScrollbarDrag == SCROLLBAR_CRAFT_PLAN) {
            craftPlanScrollOffset = toRowScroll(getCraftPlanScrollbarMetrics(), (int) mouseY, scrollbarGrabOffset, 13, Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS));
            craftPlanScrollState.jumpTo(craftPlanScrollOffset * 13, Math.max(0, plannedCraftEntries.size() * 13 - CRAFT_PLAN_HEIGHT));
            return true;
        }
        if (activeScrollbarDrag == SCROLLBAR_CRAFT_LIST) {
            craftScrollOffset = toRowScroll(getCraftListScrollbarMetrics(), (int) mouseY, scrollbarGrabOffset, LINE_HEIGHT, Math.max(0, filteredCraftableRecipes.size() - CRAFT_LIST_ROWS));
            craftListScrollState.jumpTo(craftScrollOffset * LINE_HEIGHT, Math.max(0, filteredCraftableRecipes.size() * LINE_HEIGHT - CRAFT_LIST_HEIGHT));
            return true;
        }
        if (isDragging) {
            AutismWindowLayout nextBounds = clampToScreen(this,
                new AutismWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            initWidgets();
            return true;
        }

        for (AutismChatField field : textFields) {
            if (field.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed) return false;
        if (!uiDropdowns.isEmpty()
                && (CompactDropdown.isMenuOpen(uiDropdowns)
                    || CompactDropdown.isInsideOpenMenu(uiDropdowns, mouseX, mouseY))) {
            if (CompactDropdown.mouseScrolled(uiDropdowns, mouseX, mouseY, amount)) return true;
        }
        if (isCraftMode()) {
            int planX = getCraftListX();
            int planY = getCraftPlanListY();
            int listWidth = getCraftListWidth();
            if (mouseX >= planX && mouseX <= planX + listWidth && mouseY >= planY && mouseY <= planY + CRAFT_PLAN_HEIGHT) {
            int maxPlanScroll = Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS);
            craftPlanScrollOffset = Math.max(0, Math.min(maxPlanScroll, craftPlanScrollOffset - (int) Math.signum(amount)));
            craftPlanScrollState.setTarget(craftPlanScrollOffset * 13, Math.max(0, plannedCraftEntries.size() * 13 - CRAFT_PLAN_HEIGHT));
            saveState();
            return true;
        }

            int listX = getCraftListX();
            int listY = getCraftListY();
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + CRAFT_LIST_HEIGHT) {
            int maxScroll = Math.max(0, filteredCraftableRecipes.size() - CRAFT_LIST_ROWS);
            craftScrollOffset = Math.max(0, Math.min(maxScroll, craftScrollOffset - (int) Math.signum(amount)));
            craftListScrollState.setTarget(craftScrollOffset * LINE_HEIGHT, Math.max(0, filteredCraftableRecipes.size() * LINE_HEIGHT - CRAFT_LIST_HEIGHT));
            saveState();
            return true;
        }
        }
        return false;
    }

    private String getItemNameFromSlot(int slotId) {
        ItemTarget target = getItemTargetFromSlot(slotId);
        return target.editorText();
    }

    private ItemTarget getItemTargetFromSlot(int slotId) {
        AbstractContainerMenu handler = getActiveHandler();
        if (handler == null || slotId < 0 || slotId >= handler.slots.size()) return new ItemTarget();

        Slot slot = handler.slots.get(slotId);
        if (slot == null || slot.getItem().isEmpty()) return new ItemTarget();

        int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(MC, slot.index);
        return ItemTarget.capture(slot.getItem(), visibleSlot);
    }

    private void pollCraftScan() {
        if (!isCraftMode() || MC.player == null || MC.level == null) return;

        ScanResult result = pendingScanResult.getAndSet(null);
        if (result != null && result.generation == scanGeneration.get() && result.connection == MC.getConnection()) {
            applyScanResult(result.options);
        }

        if (scanInFlight) return;
        int fingerprint = computeScanFingerprint();
        if (!hasScannedOnce || fingerprint != lastScanFingerprint) {
            hasScannedOnce = true;
            lastScanFingerprint = fingerprint;
            requestCraftScan();
        }
    }

    private void requestCraftScan() {
        long perfStart = AutismPerf.begin();
        AutismCraftingHelper.CraftScanSnapshot snapshot = AutismCraftingHelper.createScanSnapshot(MC);
        AutismPerf.endSpike("craft.snapshot", perfStart, 5_000_000L);
        if (snapshot == null) return;

        long generation = scanGeneration.incrementAndGet();
        scanInFlight = true;
        CRAFT_SCAN_EXECUTOR.execute(() -> {
            try {
                List<AutismCraftingHelper.CraftableRecipeOption> options = AutismCraftingHelper.computeCraftableRecipes(snapshot);
                pendingScanResult.set(new ScanResult(generation, snapshot.connection, options));
            } catch (Throwable t) {
                autismclient.AutismClientAddon.LOG.warn("[Autism] craft recipe scan failed", t);
            } finally {
                scanInFlight = false;
            }
        });
    }

    private void applyScanResult(List<AutismCraftingHelper.CraftableRecipeOption> options) {
        craftableRecipes.clear();
        craftableRecipes.addAll(options);
        readyCraftableRecipeCount = 0;
        for (AutismCraftingHelper.CraftableRecipeOption option : craftableRecipes) {
            if (option.craftableNow) readyCraftableRecipeCount++;
        }
        updateCraftRecipeFilter(craftSearchField != null ? craftSearchField.getText() : savedCraftSearchValue, false);
        clearSelectedCraftIfInvalid();
        restoreSelectedCraftIfNeeded();
    }

    private int computeScanFingerprint() {
        int hash = System.identityHashCode(MC.getConnection());
        if (MC.player == null) return hash;

        AbstractContainerMenu menu = MC.player.containerMenu;
        if (menu != null) {
            hash = hash * 31 + menu.containerId;
            hash = hash * 31 + menu.getStateId();
        }
        var inventory = MC.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            hash = hash * 31 + (stack == null || stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) + stack.getCount() * 31);
        }
        if (menu instanceof AbstractCraftingMenu crafting) {
            hash = hash * 31 + crafting.getGridWidth();
            hash = hash * 31 + crafting.getGridHeight();
            for (Slot slot : crafting.getInputGridSlots()) {
                ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
                hash = hash * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) + stack.getCount() * 31);
            }
        }
        return hash;
    }

    private void updateCraftRecipeFilter(String query, boolean resetScroll) {
        savedCraftSearchValue = query == null ? "" : query;
        filteredCraftableRecipes = AutismCraftingHelper.filterRecipes(craftableRecipes, query);
        int maxScroll = Math.max(0, filteredCraftableRecipes.size() - CRAFT_LIST_ROWS);
        craftScrollOffset = resetScroll ? 0 : Math.max(0, Math.min(maxScroll, craftScrollOffset));
    }

    private void clearSelectedCraftIfInvalid() {
        if (selectedCraftOption == null) return;
        for (AutismCraftingHelper.CraftableRecipeOption option : craftableRecipes) {
            if ((!option.recipeKey.isBlank() && option.recipeKey.equalsIgnoreCase(selectedCraftOption.recipeKey))
                || (option.recipeId >= 0 && option.recipeId == selectedCraftOption.recipeId)
                || (option.syncedRecipeId >= 0 && option.syncedRecipeId == selectedCraftOption.syncedRecipeId)) {
                selectedCraftOption = option;
                selectedCraftResult = option.result.copy();
                savedCraftSelectedRecipeId = option.recipeId;
                savedCraftSelectedRecipeKey = option.recipeKey;
                return;
            }
        }
        selectedCraftOption = null;
        selectedCraftResult = ItemStack.EMPTY;
        savedCraftSelectedRecipeId = -1;
        savedCraftSelectedRecipeKey = "";
    }

    private void restoreSelectedCraftIfNeeded() {
        if ((savedCraftSelectedRecipeKey == null || savedCraftSelectedRecipeKey.isBlank()) && savedCraftSelectedRecipeId < 0) return;
        if (selectedCraftOption != null) {
            if (!savedCraftSelectedRecipeKey.isBlank() && savedCraftSelectedRecipeKey.equalsIgnoreCase(selectedCraftOption.recipeKey)) return;
            if (savedCraftSelectedRecipeId >= 0 && (selectedCraftOption.recipeId == savedCraftSelectedRecipeId || selectedCraftOption.syncedRecipeId == savedCraftSelectedRecipeId)) return;
        }
        for (AutismCraftingHelper.CraftableRecipeOption option : craftableRecipes) {
            if ((!savedCraftSelectedRecipeKey.isBlank() && savedCraftSelectedRecipeKey.equalsIgnoreCase(option.recipeKey))
                || (savedCraftSelectedRecipeId >= 0 && option.recipeId == savedCraftSelectedRecipeId)
                || (savedCraftSelectedRecipeId >= 0 && option.syncedRecipeId == savedCraftSelectedRecipeId)) {
                selectedCraftOption = option;
                selectedCraftResult = option.result.copy();
                savedItemTarget = ItemTarget.capture(option.result, -1);
                savedItemNameValue = option.label;
                savedCraftSelectedRecipeKey = option.recipeKey;
                savedCraftSelectedRecipeId = option.recipeId;
                if (itemNameField != null) itemNameField.setText(savedItemNameValue);
                return;
            }
        }
    }

    private int getCraftListY() {
        return getCraftSearchY() + 20;
    }

    private int getCraftListX() {
        return panelX + 10;
    }

    private int getCraftListWidth() {
        return PANEL_WIDTH - 20;
    }

    private int getCraftPlanContentWidth() {
        return Math.max(40, getCraftListWidth() - (getCraftPlanScrollbarMetrics().hasScroll() ? SCROLLBAR_GUTTER : 0));
    }

    private int getCraftListContentWidth() {
        return Math.max(40, getCraftListWidth() - (getCraftListScrollbarMetrics().hasScroll() ? SCROLLBAR_GUTTER : 0));
    }

    private CompactScrollbar.Metrics getCraftPlanScrollbarMetrics() {
        int listX = getCraftListX();
        int listY = getCraftPlanListY();
        int listWidth = getCraftListWidth();
        int contentHeight = plannedCraftEntries.size() * 13;
        int maxScroll = Math.max(0, contentHeight - CRAFT_PLAN_HEIGHT);
        return CompactScrollbar.compute(
            contentHeight,
            CRAFT_PLAN_HEIGHT,
            listX + listWidth - 5,
            listY,
            3,
            CRAFT_PLAN_HEIGHT,
            craftPlanScrollState.tick(0.0f, maxScroll)
        );
    }

    private CompactScrollbar.Metrics getCraftListScrollbarMetrics() {
        int listX = getCraftListX();
        int listY = getCraftListY();
        int listWidth = getCraftListWidth();
        int contentHeight = filteredCraftableRecipes.size() * LINE_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - CRAFT_LIST_HEIGHT);
        return CompactScrollbar.compute(
            contentHeight,
            CRAFT_LIST_HEIGHT,
            listX + listWidth - 5,
            listY,
            3,
            CRAFT_LIST_HEIGHT,
            craftListScrollState.tick(0.0f, maxScroll)
        );
    }

    private int toRowScroll(CompactScrollbar.Metrics metrics, int mouseY, int grabOffset, int rowHeight, int maxScrollRows) {
        int pixelScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset);
        return Math.max(0, Math.min(maxScrollRows, Math.round(pixelScroll / (float) rowHeight)));
    }

    private void selectCraftRecipe(AutismCraftingHelper.CraftableRecipeOption option) {
        if (option == null) return;
        selectedCraftOption = option;
        selectedCraftResult = option.result.copy();
        savedItemTarget = ItemTarget.capture(option.result, -1);
        savedItemNameValue = option.label;
        savedCraftSelectedRecipeId = option.recipeId;
        savedCraftSelectedRecipeKey = option.recipeKey;
        if (itemNameField != null) itemNameField.setText(savedItemNameValue);
        setStatus(
            "Selected craft: " + option.label + " | " + (option.craftSource == AutismCraftingHelper.CraftSource.TABLE_3X3 ? "Table" : "2x2") + " | " + AutismCraftingHelper.getAvailabilityLabel(option),
            option.craftableNow ? ChatFormatting.GREEN : ChatFormatting.RED
        );
        saveState();
    }

    private void clearCraftRecipeSelection() {
        craftPlanSelectedIndex = -1;
        selectedCraftOption = null;
        selectedCraftResult = ItemStack.EMPTY;
        savedItemTarget = new ItemTarget();
        savedCraftSelectedRecipeId = -1;
        savedCraftSelectedRecipeKey = "";
    }

    private AutismCraftingHelper.CraftableRecipeOption resolveCraftOption(CraftAction.CraftEntry entry) {
        if (entry == null) return null;
        return AutismCraftingHelper.findInList(craftableRecipes, entry.recipeKey, entry.recipeId);
    }

    private CraftAction.CraftEntry buildConfiguredCraftEntry() {
        if (selectedCraftOption == null || selectedCraftResult.isEmpty()) return null;
        int configuredAmount = parseConfiguredCraftAmount();
        if (configuredAmount <= 0) return null;
        return CraftAction.CraftEntry.fromOption(selectedCraftOption, configuredAmount, craftUseMaxAmount);
    }

    private CraftAction.CraftEntry getSelectedPlannedEntry() {
        if (craftPlanSelectedIndex < 0 || craftPlanSelectedIndex >= plannedCraftEntries.size()) return null;
        return plannedCraftEntries.get(craftPlanSelectedIndex);
    }

    private void selectCraftPlanEntry(int index) {
        if (index < 0 || index >= plannedCraftEntries.size()) {
            craftPlanSelectedIndex = -1;
            selectedCraftOption = null;
            selectedCraftResult = ItemStack.EMPTY;
            savedCraftSelectedRecipeId = -1;
            savedCraftSelectedRecipeKey = "";
            saveState();
            return;
        }

        craftPlanSelectedIndex = index;
        CraftAction.CraftEntry entry = plannedCraftEntries.get(index);
        if (entry != null) {
            craftUseMaxAmount = entry.useMaxAmount;
            savedTimesValue = String.valueOf(Math.max(1, entry.amount));
            if (timesField != null) {
                timesField.setText(savedTimesValue);
                timesField.setEditable(!craftUseMaxAmount);
            }
            AutismCraftingHelper.CraftableRecipeOption option = resolveCraftOption(entry);
            if (option != null) {
                selectCraftRecipe(option);
            } else {
                savedCraftSelectedRecipeId = entry.recipeId;
                savedCraftSelectedRecipeKey = entry.recipeKey;
                selectedCraftOption = null;
                selectedCraftResult = ItemStack.EMPTY;
            }
        }
        saveState();
    }

    private void addOrUpdatePlannedCraftEntry() {
        CraftAction.CraftEntry draft = buildConfiguredCraftEntry();
        if (draft == null) {
            setStatus("Select a recipe and amount first", ChatFormatting.RED);
            return;
        }

        int existingIndex = -1;
        for (int i = 0; i < plannedCraftEntries.size(); i++) {
            CraftAction.CraftEntry existing = plannedCraftEntries.get(i);
            if (existing == null) continue;
            if ((!draft.recipeKey.isBlank() && draft.recipeKey.equalsIgnoreCase(existing.recipeKey))
                || (draft.recipeId >= 0 && draft.recipeId == existing.recipeId)) {
                existingIndex = i;
                break;
            }
        }

        if (craftPlanSelectedIndex >= 0 && craftPlanSelectedIndex < plannedCraftEntries.size()) {
            plannedCraftEntries.set(craftPlanSelectedIndex, draft);
        } else if (existingIndex >= 0) {
            plannedCraftEntries.set(existingIndex, draft);
            craftPlanSelectedIndex = existingIndex;
        } else {
            plannedCraftEntries.add(draft);
            craftPlanSelectedIndex = plannedCraftEntries.size() - 1;
        }

        int maxPlanScroll = Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS);
        craftPlanScrollOffset = Math.max(0, Math.min(maxPlanScroll, craftPlanSelectedIndex));
        saveState();
    }

    private void removePlannedCraftEntry(int index) {
        if (index < 0 || index >= plannedCraftEntries.size()) return;
        plannedCraftEntries.remove(index);
        if (craftPlanSelectedIndex == index) craftPlanSelectedIndex = -1;
        else if (craftPlanSelectedIndex > index) craftPlanSelectedIndex--;
        craftPlanScrollOffset = Math.max(0, Math.min(Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS), craftPlanScrollOffset));
        saveState();
    }

    private List<CraftAction.CraftEntry> getActiveCraftEntries() {
        if (!plannedCraftEntries.isEmpty()) {
            List<CraftAction.CraftEntry> copies = new ArrayList<>(plannedCraftEntries.size());
            for (CraftAction.CraftEntry entry : plannedCraftEntries) {
                if (entry != null && entry.hasRecipe()) copies.add(entry.copy());
            }
            return copies;
        }

        CraftAction.CraftEntry draft = buildConfiguredCraftEntry();
        return draft == null ? List.of() : List.of(draft);
    }

    private CraftAction buildCraftPlanAction() {
        List<CraftAction.CraftEntry> activeEntries = getActiveCraftEntries();
        if (activeEntries.isEmpty()) return null;
        CraftAction action = new CraftAction();
        action.setEntries(activeEntries);
        return action;
    }

    private Component getCraftPlanRowLabel(CraftAction.CraftEntry entry) {
        if (entry == null || !entry.hasRecipe()) return Component.literal("(empty)");
        AutismCraftingHelper.CraftableRecipeOption option = resolveCraftOption(entry);
        String amount = entry.useMaxAmount ? "Max" : "x" + Math.max(1, entry.amount);
        Component resultName = option != null ? option.result.getHoverName().copy() : Component.literal(entry.resultName);
        return Component.empty().append(resultName).append(Component.literal(" | " + amount + (option != null && option.craftableNow ? " | x" + option.maxCraftsNow : "")));
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || collapsed) return false;

        boolean anyFocused = false;
        for (AutismChatField field : textFields) {
            if (field.isFocused()) {
                anyFocused = true;
                break;
            }
        }

        if (anyFocused) {
            if (keyCode == 256) {
                for (AutismChatField field : textFields) {
                    field.setFocused(false);
                }
                return true;
            }
            KeyEvent input = new KeyEvent(keyCode, scanCode, modifiers);
            for (AutismChatField field : textFields) {
                if (field.isFocused()) {
                    field.keyPressed(input);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        int codepoint = (int) chr;
        if (!visible || collapsed) return false;

        CharacterEvent input = new CharacterEvent(codepoint);
        for (AutismChatField field : textFields) {
            if (field.isFocused() && field.charTyped(input)) {
                return true;
            }
        }

        return false;
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        expireTransientStatus();

        int rawMouseX = mouseX;
        int rawMouseY = mouseY;
        if (CompactDropdown.isMenuOpen(uiDropdowns)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        uiDropdowns.clear();
        updateListHeights();
        if (!isDragging) {
            PANEL_HEIGHT = isCraftMode() ? craftModePanelHeight() : standardPanelHeight();
        }

        int panelHeight = PANEL_HEIGHT;
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, "Fabricator", collapsed, isDragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
            return;
        }

        try {
        int labelX = panelX + 10;
        int fieldX = panelX + LABEL_WIDTH + 15;
        int fieldWidth = getFieldWidth();

        AutismText.draw(context, MC.font, "Action", AutismText.Tone.MUTED, labelX, getActionRowY() + 3, false);

        if (actionDropdown == null) {
            actionDropdown = new CompactDropdown(fieldX, getActionRowY(), fieldWidth, 16, actionLabels, currentActionIndex, idx -> {
                if (idx >= 0 && idx < actions.length && idx != currentActionIndex) {
                    currentActionIndex = idx;
                    currentAction = actions[idx];
                    clearStatus();
                    initWidgets();
                    saveState();
                }
            });
        }
        actionDropdown.setBounds(fieldX, getActionRowY(), fieldWidth, 16)
                .setOptions(actionLabels)
                .setSelectedIndex(currentActionIndex);
        uiDropdowns.add(actionDropdown);

        for (AutismChatField field : textFields) {
            field.render(context, mouseX, mouseY, delta);
        }

        if (showsSlotItemRows()) {
            AutismText.draw(context, MC.font, "Slot", AutismText.Tone.MUTED, labelX, getSlotRowY() + 3, false);
            AutismText.draw(context, MC.font, "Item", AutismText.Tone.MUTED, labelX, getItemRowY() + 3, false);
        }

        if (showsTargetFields()) {
            if (isClickSelectorRelevant() || isDropModeRelevant()) {
                AutismText.draw(context, MC.font, isDropModeRelevant() ? "Drop Mode" : "Click", AutismText.Tone.MUTED, labelX, getOptionRowY() + 3, false);
                if (isClickSelectorRelevant()) {
                    boolean packetMode = currentAction.isPacketAction();
                    java.util.List<String> options = packetMode ? PACKET_CLICK_LABELS : STANDARD_CLICK_LABELS;
                    int clamped = Math.min(currentButtonIndex, options.size() - 1);
                    if (clickModeDropdown == null) {
                        clickModeDropdown = new CompactDropdown(fieldX, getOptionRowY(), fieldWidth, 16, options, clamped, idx -> {
                            if (idx >= 0 && idx < 3) {
                                currentButtonIndex = idx;
                                clearStatus();
                                saveState();
                            }
                        });
                    }
                    clickModeDropdown.setBounds(fieldX, getOptionRowY(), fieldWidth, 16)
                            .setOptions(options)
                            .setSelectedIndex(clamped);
                    uiDropdowns.add(clickModeDropdown);
                } else {
                    java.util.List<String> options = DROP_MODE_LABELS;
                    int sel = dropWholeStack ? 1 : 0;
                    if (dropModeDropdown == null) {
                        dropModeDropdown = new CompactDropdown(fieldX, getOptionRowY(), fieldWidth, 16, options, sel, idx -> {
                            dropWholeStack = (idx == 1);
                            clearStatus();
                            saveState();
                        });
                    }
                    dropModeDropdown.setBounds(fieldX, getOptionRowY(), fieldWidth, 16)
                            .setOptions(options)
                            .setSelectedIndex(sel);
                    uiDropdowns.add(dropModeDropdown);
                }
            }

            AutismText.draw(context, MC.font, getTimesLabel(), AutismText.Tone.MUTED, labelX, getStandardTimesRowY() + 3, false);
        } else {
            pollCraftScan();
            int listX = getCraftListX();
            int listWidth = getCraftListWidth();

            CompactListRenderer.drawHeader(context, MC.font, "Craft Plan", labelX, getCraftPlanHeaderY());
            if (!plannedCraftEntries.isEmpty()) {
                CompactListRenderer.drawHeaderAction(context, MC.font, "Clear All", true, listX, listWidth, getCraftPlanHeaderY(), mouseX, mouseY);
            }

            int planListY = getCraftPlanListY();
            int planContentWidth = getCraftPlanContentWidth();
            CompactListRenderer.drawFrame(context, listX, planListY, listWidth, CRAFT_PLAN_HEIGHT, craftPlanSelectedIndex >= 0);
            if (plannedCraftEntries.isEmpty()) {
                CompactListRenderer.drawEmptyState(context, MC.font, "(no plan - pick recipe and Add)", listX, planListY + 2, planContentWidth);
            } else {
                int maxPlanScrollPx = Math.max(0, plannedCraftEntries.size() * 13 - CRAFT_PLAN_HEIGHT);
                craftPlanScrollOffset = Math.max(0, Math.min(craftPlanScrollOffset, Math.max(0, plannedCraftEntries.size() - CRAFT_PLAN_ROWS)));
                craftPlanScrollState.setTarget(craftPlanScrollOffset * 13, maxPlanScrollPx);
                int drawPlanScroll = craftPlanScrollState.tick(delta, maxPlanScrollPx);
                autismclient.gui.vanillaui.UiScissorStack.global().push(context,
                    autismclient.gui.vanillaui.UiBounds.of(listX + 1, planListY + 1, Math.max(0, listWidth - 2), Math.max(0, CRAFT_PLAN_HEIGHT - 2)));
                int startIndex = drawPlanScroll / 13;
                int rowY = planListY - (drawPlanScroll % 13);
                for (int index = startIndex; index < plannedCraftEntries.size() && rowY < planListY + CRAFT_PLAN_HEIGHT; index++, rowY += 13) {
                    boolean hovered = mouseX >= listX && mouseX < listX + planContentWidth && mouseY >= rowY && mouseY < rowY + 12;
                    boolean selected = index == craftPlanSelectedIndex;
                    CompactListRenderer.drawRow(
                        context,
                        MC.font,
                        getCraftPlanRowLabel(plannedCraftEntries.get(index)),
                        listX,
                        rowY,
                        planContentWidth,
                        12,
                        hovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        true
                    );
                    boolean removeHovered = mouseX >= listX + planContentWidth - 16 && mouseX < listX + planContentWidth - 2 && mouseY >= rowY && mouseY < rowY + 12;
                    CompactListRenderer.drawDeleteButton(context, listX + planContentWidth - 14, rowY, 12, removeHovered);
                }
                autismclient.gui.vanillaui.UiScissorStack.global().pop(context);
            }
            CompactScrollbar.Metrics craftPlanScrollbar = getCraftPlanScrollbarMetrics();
            CompactScrollbar.draw(context, craftPlanScrollbar, craftPlanScrollbar.contains(mouseX, mouseY), activeScrollbarDrag == SCROLLBAR_CRAFT_PLAN);

            if (selectedCraftResult.isEmpty()) {
                AutismText.draw(context, MC.font, "Recipe: none selected", AutismText.Tone.MUTED, listX, getCraftSummaryY(), false);
            } else {
                Component summaryText = Component.empty()
                    .append(selectedCraftResult.getHoverName().copy())
                    .append(Component.literal(" x" + selectedCraftResult.getCount()
                        + (selectedCraftOption != null && selectedCraftOption.craftableNow ? " | x" + selectedCraftOption.maxCraftsNow : "")));
            AutismText.draw(context, MC.font, summaryText.getString(), AutismColors.textMuted(), listX, getCraftSummaryY(), false);
            }

            AutismText.draw(context, MC.font, "Amount", AutismText.Tone.MUTED, labelX, getCraftAmountRowY() + 3, false);
            drawOverlayToggleButton(context, getCraftAmountToggleX(), getCraftAmountRowY(), getCraftAmountToggleWidth(), 16,
                "Use Max", craftUseMaxAmount, "fabricator:use-max", mouseX, mouseY);

            CompactListRenderer.drawHeader(context, MC.font, "Recipes", listX, getCraftSearchLabelY());

            int listY = getCraftListY();
            int recipeContentWidth = getCraftListContentWidth();
            CompactListRenderer.drawFrame(context, listX, listY, listWidth, CRAFT_LIST_HEIGHT, selectedCraftOption != null);
            autismclient.gui.vanillaui.UiScissorStack.global().push(context,
                autismclient.gui.vanillaui.UiBounds.of(listX + 1, listY + 1, Math.max(0, listWidth - 2), Math.max(0, CRAFT_LIST_HEIGHT - 2)));

            if (filteredCraftableRecipes.isEmpty()) {
                String emptyText;
                if (MC.player == null || MC.level == null) {
                    emptyText = "Join a world to load crafting recipes.";
                } else if (craftableRecipes.isEmpty() && scanInFlight) {
                    emptyText = "Scanning crafting recipes...";
                } else {
                    emptyText = "No crafting recipes match this search.";
                }
                CompactListRenderer.drawEmptyState(context, MC.font, emptyText, listX, listY + 4, recipeContentWidth);
            } else {
                int maxRecipeScrollPx = Math.max(0, filteredCraftableRecipes.size() * LINE_HEIGHT - CRAFT_LIST_HEIGHT);
                craftScrollOffset = Math.max(0, Math.min(craftScrollOffset, Math.max(0, filteredCraftableRecipes.size() - CRAFT_LIST_ROWS)));
                craftListScrollState.setTarget(craftScrollOffset * LINE_HEIGHT, maxRecipeScrollPx);
                int drawRecipeScroll = craftListScrollState.tick(delta, maxRecipeScrollPx);
                int startIndex = drawRecipeScroll / LINE_HEIGHT;
                int rowY = listY - (drawRecipeScroll % LINE_HEIGHT);
                for (int index = startIndex; index < filteredCraftableRecipes.size() && rowY < listY + CRAFT_LIST_HEIGHT; index++, rowY += LINE_HEIGHT) {
                    AutismCraftingHelper.CraftableRecipeOption option = filteredCraftableRecipes.get(index);
                    boolean hovered = mouseX >= listX && mouseX <= listX + recipeContentWidth && mouseY >= rowY && mouseY < rowY + LINE_HEIGHT;
                    boolean selected = selectedCraftOption != null && option.recipeKey.equalsIgnoreCase(selectedCraftOption.recipeKey);
                    Component richLabel = Component.empty()
                        .append(option.result.getHoverName().copy())
                        .append(Component.literal(" x" + option.result.getCount() + " | " + AutismCraftingHelper.getAvailabilityLabel(option)));
                    CompactListRenderer.drawRow(
                        context,
                        MC.font,
                        richLabel,
                        listX + 2,
                        rowY,
                        recipeContentWidth - 2,
                        LINE_HEIGHT,
                        hovered,
                        selected,
                        option.craftableNow ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.MISSING,
                        true
                    );
                    CompactListRenderer.drawDivider(context, listX + 2, rowY + LINE_HEIGHT - 1, recipeContentWidth - 2);
                }
            }

            autismclient.gui.vanillaui.UiScissorStack.global().pop(context);
            CompactScrollbar.Metrics craftListScrollbar = getCraftListScrollbarMetrics();
            CompactScrollbar.draw(context, craftListScrollbar, craftListScrollbar.contains(mouseX, mouseY), activeScrollbarDrag == SCROLLBAR_CRAFT_LIST);
            String recipeCountLine = AutismText.trimToWidth(
                MC.font,
                readyCraftableRecipeCount + " ready / " + craftableRecipes.size() + " total",
                listWidth,
                AutismText.Tone.MUTED
            );
            AutismText.draw(context, MC.font, recipeCountLine, AutismText.Tone.MUTED, listX, listY + CRAFT_LIST_HEIGHT + 2, false);
        }

        int btnY = getBottomButtonY();
        int btnArea = PANEL_WIDTH - 20;
        int gap = 2;
        int bw = (btnArea - gap * 2) / 3;
        int bx = panelX + 10;
        String sendLabel = isCraftMode() ? (craftExecutionInProgress ? "Crafting..." : "Craft") : "Send";
        drawOverlayButton(context, bx, btnY, bw, BUTTON_HEIGHT, sendLabel, CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);
        bx += bw + gap;
        drawOverlayButton(context, bx, btnY, bw, BUTTON_HEIGHT, "Queue", CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);
        bx += bw + gap;
        drawOverlayButton(context, bx, btnY, bw, BUTTON_HEIGHT, "Macro", CompactOverlayButton.Variant.SECONDARY, true, mouseX, mouseY);

        int infoWidth = PANEL_WIDTH - 20;
        int statusY = getStatusY();
        if (statusY + 9 < btnY) {
            if (!statusMessage.isEmpty()) {
                int color = chatFormattingColor(statusColor);
                UiText.drawFitted(context, MC.font, statusMessage, THEME.fontFor(UiTone.BODY),
                    color, panelX + 10, statusY, infoWidth, true);
            } else {
                UiText.drawFitted(context, MC.font, getTargetSummary(), THEME.fontFor(UiTone.MUTED),
                    THEME.color(UiTone.MUTED), panelX + 10, statusY, infoWidth, false);
            }
        }

        List<String> actionLines = getActionDescriptionLines();
        for (int i = 0; i < actionLines.size(); i++) {
            int lineY = getActionInfoY() + i * 10;
            if (lineY + 9 >= btnY) break;
            AutismText.draw(context, MC.font, actionLines.get(i), AutismText.Tone.MUTED, panelX + 10, lineY, false);
        }

        if (!uiDropdowns.isEmpty()) {
            CompactDropdown.renderButtons(context, MC.font, uiDropdowns, rawMouseX, rawMouseY);
        }
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
        }
        if (!uiDropdowns.isEmpty() && CompactDropdown.isMenuOpen(uiDropdowns)) {
            context.nextStratum();
            CompactDropdown.renderOpenMenu(context, MC.font, uiDropdowns, rawMouseX, rawMouseY);
        }
    }

    private static int chatFormattingColor(ChatFormatting formatting) {
        if (formatting == null) return 0xFFFFFFFF;
        return switch (formatting) {
            case BLACK -> 0xFF000000;
            case DARK_BLUE -> 0xFF0000AA;
            case DARK_GREEN -> 0xFF00AA00;
            case DARK_AQUA -> 0xFF00AAAA;
            case DARK_RED -> 0xFFAA0000;
            case DARK_PURPLE -> 0xFFAA00AA;
            case GOLD -> 0xFFFFAA00;
            case GRAY -> 0xFFAAAAAA;
            case DARK_GRAY -> 0xFF555555;
            case BLUE -> 0xFF5555FF;
            case GREEN -> 0xFF55FF55;
            case AQUA -> 0xFF55FFFF;
            case RED -> 0xFFFF5555;
            case LIGHT_PURPLE -> 0xFFFF55FF;
            case YELLOW -> 0xFFFFFF55;
            case WHITE -> 0xFFFFFFFF;
            default -> 0xFFFFFFFF;
        };
    }

    private FabricatorAction currentAction = FabricatorAction.CLICK;

    private void cycleAction() {
        currentActionIndex = (currentActionIndex + 1) % actions.length;
        currentAction = actions[currentActionIndex];
        clearStatus();
        initWidgets();
        saveState();
    }

    private void cycleActionBackwards() {
        currentActionIndex = (currentActionIndex - 1 + actions.length) % actions.length;
        currentAction = actions[currentActionIndex];
        clearStatus();
        initWidgets();
        saveState();
    }

    private void clearWidgets() {
        textFields.clear();
        craftSearchField = null;
    }

    public void onSlotClick(Slot slot, int button) {
        if (slot == null) return;
        if (isCraftMode()) return;

        int stableSlotId = AutismInventoryHelper.toUserVisibleSlot(MC, slot.index);
        ItemTarget clickedTarget = getItemTargetFromSlot(slot.index);
        String itemName = clickedTarget.editorText();

        if (currentAction.isPacketAction()) {
            AbstractContainerMenu handler = getActiveHandler();
            if (handler == null) {
                setStatus("No container is open to capture", ChatFormatting.RED);
                return;
            }
            String screenTitle = parentScreen != null ? parentScreen.getTitle().getString() : "";
            String menuClass = handler.getClass().getName();
            packetClickTarget = new AutismPacketClick.Target(
                handler.containerId,
                handler.getStateId(),
                slot.index,
                stableSlotId,
                screenTitle,
                menuClass,
                itemName == null ? "" : itemName,
                getCurrentPacketMode(),
                System.currentTimeMillis()
            );
            setStatus("Captured Packet " + packetClickTarget.summary(), ChatFormatting.GREEN);
            saveState();
            return;
        }

        selectedSlotId = stableSlotId;
        savedSlotValue = String.valueOf(stableSlotId);
        if (slotField != null) slotField.setText(savedSlotValue);

        if (!itemName.isEmpty()) {
            savedItemTarget = clickedTarget;
            savedItemNameValue = itemName;
            if (itemNameField != null) itemNameField.setText(itemName);
            setStatus("Selected slot " + stableSlotId + ": " + itemName, ChatFormatting.GREEN);
        } else {
            savedItemTarget = new ItemTarget();
            savedItemNameValue = "";
            if (itemNameField != null) itemNameField.setText("");
            setStatus("Selected slot " + stableSlotId + " (Empty)", ChatFormatting.YELLOW);
        }

        saveState();
    }
    private void sendToMacro(AutismMacroEditorOverlay macroEditor) {
        try {
            if (isCraftMode()) {
                CraftAction craftAction = buildCraftPlanAction();
                if (craftAction == null || !craftAction.hasEntries()) {
                    setStatus("Select a recipe or add a craft entry first!", ChatFormatting.RED);
                    return;
                }
                macroEditor.addAction(craftAction);
                return;
            }

            Integer enteredSlot = parseEnteredSlot();
            String itemName = getEnteredItemName();
            int repeats = resolveRepeatCount(-1);
            if (repeats <= 0) return;

            if (enteredSlot == null && itemName.isEmpty()) {
                if (!currentAction.isPacketAction()) {
                    setStatus("Pick a slot or enter an item name first!", ChatFormatting.RED);
                    return;
                }
            }

            if (currentAction.isPacketAction()) {
                AutismPacketClick.Target target = getEffectivePacketClickTarget();
                if (target == null) {
                    setStatus("Right-click a GUI slot to capture a Packet target first!", ChatFormatting.RED);
                    return;
                }
                PacketClickAction action = new PacketClickAction(target, repeats, false);
                macroEditor.addAction(action);
                setStatus("Added Packet Click action to macro!", ChatFormatting.GREEN);
                return;
            }

            AbstractContainerMenu handler = getActiveHandler();
            int resolvedSlot = enteredSlot == null ? -1 : AutismInventoryHelper.resolveConfiguredHandlerSlot(MC, enteredSlot);
            if (enteredSlot != null && !isValidHandlerSlot(handler, resolvedSlot)) {
                setStatus("Slot " + enteredSlot + " is not available in this screen!", ChatFormatting.RED);
                return;
            }

            if (currentAction == FabricatorAction.DROP) {
                autismclient.util.macro.DropAction dropAction = new autismclient.util.macro.DropAction();
                dropAction.useHandlerSlots = true;
                dropAction.mode = dropWholeStack
                    ? autismclient.util.macro.DropAction.DropMode.ALL
                    : autismclient.util.macro.DropAction.DropMode.TIMES;
                dropAction.dropCount = dropWholeStack ? 0 : repeats;
                String entry = enteredSlot != null
                    ? ("#" + enteredSlot + (!itemName.isEmpty() ? "|" + itemName : ""))
                    : itemName;
                if (!entry.isBlank()) {
                    ItemTarget target = buildEnteredItemTarget(enteredSlot, itemName);
                    entry = target.toLegacyEntry();
                    dropAction.itemTargets.add(target);
                    dropAction.itemNames.add(entry);
                    dropAction.itemCounts.add(dropWholeStack ? 0 : repeats);
                }
                macroEditor.addAction(dropAction);
                setStatus("Added Drop action to macro!", ChatFormatting.GREEN);
                return;
            }

            autismclient.util.macro.ItemAction itemAction = new autismclient.util.macro.ItemAction();
            itemAction.actionIndex = currentAction.toPacketAction(dropWholeStack).ordinal();
            itemAction.button = getEffectiveButton();
            itemAction.times = repeats;

            if (enteredSlot != null) {
                itemAction.useSlot = true;
                itemAction.targetSlot = enteredSlot;
            }

            if (!itemName.isEmpty()) {
                ItemTarget target = buildEnteredItemTarget(enteredSlot, itemName);
                itemAction.itemTargets.add(target);
                itemAction.itemNames.add(target.toLegacyEntry());
                itemAction.itemTimes.add(repeats);
                itemAction.itemActionIdx.add(itemAction.actionIndex);
                itemAction.itemButtons.add(itemAction.button);
            }

            macroEditor.addAction(itemAction);
            setStatus("Added " + currentAction.displayName + " action to macro!", ChatFormatting.GREEN);
        } catch (NumberFormatException e) {
            setStatus("Invalid value", ChatFormatting.RED);
        }
    }

    private ItemTarget buildEnteredItemTarget(Integer enteredSlot, String itemName) {
        String safeName = itemName == null ? "" : itemName.trim();
        ItemTarget target = savedItemTarget != null && safeName.equals(savedItemTarget.editorText())
                ? savedItemTarget.copy()
                : ItemTarget.fromLegacyEntry(safeName);
        if (enteredSlot != null) target.slot = enteredSlot;
        return target;
    }

    private AutismPacketClick.Target getEffectivePacketClickTarget() {
        if (packetClickTarget == null) return null;
        return packetClickTarget.withMode(getCurrentPacketMode());
    }

    private Packet<?> buildPacketClickPacket() {
        AutismPacketClick.Target target = getEffectivePacketClickTarget();
        return target == null ? null : target.buildPacket();
    }

    private net.minecraft.network.protocol.Packet<?> buildFabricatedPacket(int slot) {
        if (currentAction.isPacketAction()) {
            return buildPacketClickPacket();
        }
        if (MC.player == null) return null;
        net.minecraft.world.inventory.AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null) return null;

        net.minecraft.world.inventory.ContainerInput actionType = currentAction.toPacketAction(dropWholeStack).toContainerInput();
        return new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(
            handler.containerId, handler.getStateId(),
            (short) slot, (byte) getEffectiveButton(), actionType,
            new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<>(),
            net.minecraft.network.HashedStack.EMPTY
        );
    }

    private void send(boolean queue) {
        if (isCraftMode()) {
            sendCraft(queue);
            return;
        }

        if (currentAction.isPacketAction()) {
            sendPacketClick(queue);
            return;
        }

        try {
            int slot = resolveTargetSlotForBuild();
            if (slot < 0) return;
            int repeats = resolveRepeatCount(slot);
            if (repeats <= 0) return;

            ClientPacketListener handler = MC.getConnection();
            if (handler == null) return;

            if (currentAction == FabricatorAction.DROP && !queue) {
                int queuedCount = AutismSharedState.get().flushDelayedPackets(handler);
                int sent = AutismDropHelper.dropFromHandlerSlot(MC, slot, dropWholeStack ? 0 : repeats);
                if (sent <= 0) {
                    setStatus("Nothing to drop", ChatFormatting.YELLOW);
                } else if (queuedCount > 0) {
                    setStatus("Sent " + queuedCount + " queued + drop action", ChatFormatting.GREEN);
                } else {
                    setStatus(dropWholeStack ? "Dropped stack" : "Dropped " + repeats + " item(s)", ChatFormatting.GREEN);
                }
                return;
            }

            if (!queue) {
                AutismSharedState.get().flushDelayedPackets(handler);
                net.minecraft.world.inventory.ContainerInput actionType = currentAction.toPacketAction(dropWholeStack).toContainerInput();
                int effectiveButton = getEffectiveButton();
                for (int i = 0; i < repeats; i++) {
                    AutismInventoryClickHelper.click(MC, slot, effectiveButton, actionType);
                }
                return;
            }

            sendFabricatedPackets(slot, repeats, queue, handler);

        } catch (NumberFormatException e) {
            setStatus("Invalid number format", ChatFormatting.RED);
        }
    }

    private void sendPacketClick(boolean queue) {
        try {
            AutismPacketClick.Target target = getEffectivePacketClickTarget();
            if (target == null) {
                setStatus("Right-click a GUI slot to capture a Packet target first", ChatFormatting.RED);
                return;
            }

            int repeats = resolveRepeatCount(-1);
            if (repeats <= 0) return;

            ClientPacketListener handler = MC.getConnection();
            if (handler == null) {
                setStatus("No network connection", ChatFormatting.RED);
                return;
            }

            List<Packet<?>> packets = new ArrayList<>(repeats);
            for (int i = 0; i < repeats; i++) {
                packets.add(target.buildPacket());
            }

            if (queue) {
                for (Packet<?> packet : packets) {
                    AutismSharedState.get().enqueueExactPacket(packet);
                }
                setStatus("Queued " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
                return;
            }

            int queuedCount = AutismSharedState.get().flushDelayedPackets(handler);
            for (Packet<?> packet : packets) {
                AutismSharedState.get().sendPacketBypassDelay(handler, packet);
            }
            if (queuedCount > 0) {
                setStatus("Sent " + queuedCount + " queued + " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
            } else {
                setStatus("Sent " + packets.size() + " exact Packet Click(s)", ChatFormatting.GREEN);
            }
        } catch (NumberFormatException e) {
            setStatus("Invalid number format", ChatFormatting.RED);
        }
    }

    private void sendCraft(boolean queue) {
        List<CraftAction.CraftEntry> entries = getActiveCraftEntries();
        if (entries.isEmpty()) {
            setStatus("Select a recipe or add a craft entry first", ChatFormatting.RED);
            return;
        }

        if (queue) {
            for (CraftAction.CraftEntry entry : entries) {
                AutismCraftingHelper.CraftableRecipeOption option = AutismCraftingHelper.findCraftableRecipe(MC, entry.recipeKey);
                if (option == null) option = AutismCraftingHelper.findCraftableRecipe(MC, entry.recipeId);
                if (option == null) {
                    String msg = "Recipe not found: " + entry.resultName;
                    setStatus(msg, ChatFormatting.RED);
                    AutismClientMessaging.sendPrefixed("§c" + msg);
                    return;
                }
                int desiredAmount = AutismCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
                if (desiredAmount <= 0) {
                    String msg = "No space or materials for " + entry.resultName;
                    setStatus(msg, ChatFormatting.RED);
                    AutismClientMessaging.sendPrefixed("§c" + msg);
                    return;
                }

                List<Packet<?>> packets = AutismCraftingHelper.buildCraftSequence(MC, entry.recipeKey, entry.recipeId, desiredAmount);
                if (packets.isEmpty()) {
                    String msg = option.hasSyncedRecipe()
                        ? "Failed to build craft queue for " + entry.resultName
                        : "Manual queue cannot be built safely for " + entry.resultName + "; use Craft instead.";
                    setStatus(msg, ChatFormatting.RED);
                    AutismClientMessaging.sendPrefixed("§c" + msg);
                    return;
                }

                for (Packet<?> packet : packets) {
                    AutismSharedState.get().enqueuePacket(packet);
                }
            }
            return;
        }

        if (craftExecutionInProgress) {
            setStatus("A craft is already running", ChatFormatting.YELLOW);
            return;
        }

        craftExecutionInProgress = true;
        Thread craftThread = new Thread(() -> {
            AutismCraftingHelper.CraftExecutionResult result = null;
            for (CraftAction.CraftEntry entry : entries) {
                AutismCraftingHelper.CraftableRecipeOption option = AutismCraftingHelper.findCraftableRecipe(MC, entry.recipeKey);
                if (option == null) option = AutismCraftingHelper.findCraftableRecipe(MC, entry.recipeId);
                if (option == null) {
                    result = AutismCraftingHelper.CraftExecutionResult.failure("Recipe not found: " + entry.resultName);
                    break;
                }
                int desiredAmount = AutismCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
                if (desiredAmount <= 0) {
                    result = AutismCraftingHelper.CraftExecutionResult.failure("No space or materials for " + entry.resultName + ".");
                    break;
                }

                result = AutismCraftingHelper.executeCraftImmediately(MC, entry.recipeKey, entry.recipeId, desiredAmount);
                if (!result.success) break;
            }
            craftExecutionInProgress = false;
            AutismCraftingHelper.CraftExecutionResult finalResult = result;
            if (finalResult != null && !finalResult.success) {
                MC.execute(() -> {
                    setStatus(finalResult.message, ChatFormatting.RED);
                    AutismClientMessaging.sendPrefixed("§c" + finalResult.message);
                });
            }
        }, "Autism-Fabricator-Craft");
        craftThread.setDaemon(true);
        craftThread.start();
    }

    private void sendFabricatedPackets(int slot, int repeats, boolean queue, ClientPacketListener handler) {
        List<Packet<?>> packets = buildFabricatedSequence(slot, repeats);
        if (packets.isEmpty()) {
            setStatus("Failed to build packet", ChatFormatting.RED);
            return;
        }

        if (queue) {
            for (Packet<?> packet : packets) {
                AutismSharedState.get().enqueuePacket(packet);
            }
            setStatus("Queued " + packets.size() + " packet(s)", ChatFormatting.GREEN);
            return;
        }

        int queuedCount = AutismSharedState.get().flushDelayedPackets(handler);
        for (Packet<?> packet : packets) {
            AutismPacketSender.send(packet);
        }

        if (queuedCount > 0) {
            setStatus("Sent " + queuedCount + " queued + " + packets.size() + " new packet(s)", ChatFormatting.GREEN);
        } else {
            setStatus("Sent " + packets.size() + " packet(s)", ChatFormatting.GREEN);
        }
    }

    private void setStatus(String message, ChatFormatting color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusExpiresAtMs = 0L;
    }

    private void clearStatus() {
        this.statusMessage = "";
        this.statusColor = ChatFormatting.GRAY;
        this.statusExpiresAtMs = 0L;
    }

    private void expireTransientStatus() {
        if (statusExpiresAtMs <= 0L) return;
        if (System.currentTimeMillis() >= statusExpiresAtMs) {
            clearStatus();
        }
    }

    private String getTimesLabel() {
        if (isCraftMode()) return "Amount";
        if (isDropModeRelevant() && !dropWholeStack) return "Items";
        return "Times";
    }

    private String getTargetSummary() {
        if (isCraftMode()) {
            String planInfo = plannedCraftEntries.isEmpty()
                ? "Plan: none"
                : "Plan: " + plannedCraftEntries.size() + " entr" + (plannedCraftEntries.size() == 1 ? "y" : "ies");
            if (selectedCraftResult.isEmpty()) return planInfo + " | Recipe: none selected";
            String source = selectedCraftOption != null && selectedCraftOption.craftSource == AutismCraftingHelper.CraftSource.TABLE_3X3 ? "Table" : "2x2";
            String availability = selectedCraftOption != null ? AutismCraftingHelper.getAvailabilityLabel(selectedCraftOption) : "Unavailable";
            String amountMode = craftUseMaxAmount
                ? "Max"
                : "x" + Math.max(1, parseConfiguredCraftAmount());
            return planInfo + " | Recipe: " + selectedCraftResult.getHoverName().getString() + " | " + source + " | " + availability + " | " + amountMode;
        }

        if (currentAction.isPacketAction()) {
            AutismPacketClick.Target target = getEffectivePacketClickTarget();
            return target == null
                ? "Packet: right-click a GUI slot to capture"
                : "Packet: " + target.summary();
        }

        Integer slot = parseEnteredSlot();
        String itemName = getEnteredItemName();
        if (slot != null && !itemName.isEmpty()) {
            return "Target: '" + itemName + "' in exact slot " + slot;
        }
        if (slot != null) {
            return "Target: exact slot " + slot;
        }
        if (!itemName.isEmpty()) {
            return "Target: best matching '" + itemName + "'";
        }
        return "Target: choose a slot or enter an item name";
    }

    private AbstractContainerMenu getActiveHandler() {
        if (MC.player != null && MC.player.containerMenu != null) return MC.player.containerMenu;
        return parentScreen != null ? ((autismclient.mixin.accessor.AbstractContainerScreenAccessor) parentScreen).autism$getMenu() : null;
    }

    private Integer parseEnteredSlot() {
        if (slotField == null) return null;
        String text = slotField.getText().trim();
        if (text.isEmpty()) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getEnteredItemName() {
        return itemNameField == null ? "" : itemNameField.getText().trim();
    }

    private boolean isValidHandlerSlot(AbstractContainerMenu handler, int slotId) {
        return handler != null && slotId >= 0 && slotId < handler.slots.size();
    }

    private boolean stackMatchesQuery(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty() || query == null || query.isBlank()) return false;
        return ItemTarget.fromLegacyEntry(query).score(stack, -1) >= 0;
    }

    private boolean slotMatchesItemQuery(AbstractContainerMenu handler, int slotId, String itemQuery) {
        if (!isValidHandlerSlot(handler, slotId)) return false;
        Slot slot = handler.slots.get(slotId);
        return slot != null && stackMatchesQuery(slot.getItem(), itemQuery);
    }

    private int resolveTargetSlotForBuild() {
        AbstractContainerMenu handler = getActiveHandler();
        if (handler == null) {
            setStatus("No screen is open right now", ChatFormatting.RED);
            return -1;
        }

        Integer enteredSlot = parseEnteredSlot();
        String itemName = getEnteredItemName();
        int resolvedSlot = enteredSlot == null ? -1 : AutismInventoryHelper.resolveConfiguredHandlerSlot(MC, enteredSlot);

        if (enteredSlot != null && !isValidHandlerSlot(handler, resolvedSlot)) {
            setStatus("Slot " + enteredSlot + " is not available in this screen", ChatFormatting.RED);
            return -1;
        }

        if (enteredSlot != null && !itemName.isEmpty()) {
            if (slotMatchesItemQuery(handler, resolvedSlot, itemName)) return resolvedSlot;
            setStatus("Slot " + enteredSlot + " does not contain '" + itemName + "'", ChatFormatting.RED);
            return -1;
        }

        if (enteredSlot != null) {
            return resolvedSlot;
        }

        if (!itemName.isEmpty()) {
            Integer foundSlot = findSlotByItemName(itemName);
            if (foundSlot == null) {
                setStatus("No item matching '" + itemName + "' found", ChatFormatting.RED);
                return -1;
            }
            return foundSlot;
        }

        setStatus("Pick a slot or enter an item name first", ChatFormatting.RED);
        return -1;
    }

    private int resolveRepeatCount(int slot) {
        int value;
        try {
            value = Integer.parseInt(timesField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(currentAction.isCraftAction() ? "Invalid amount" : "Invalid repeat count", ChatFormatting.RED);
            return -1;
        }

        if (value <= 0) {
            setStatus(currentAction.isCraftAction() ? "Amount must be at least 1" : "Repeat count must be at least 1", ChatFormatting.RED);
            return -1;
        }

        if (!currentAction.isCraftAction()) {
            return value;
        }

        if (selectedCraftOption == null || selectedCraftResult.isEmpty()) {
            setStatus("Select a recipe first", ChatFormatting.RED);
            return -1;
        }

        if (!(MC.player != null && MC.player.containerMenu instanceof CraftingMenu craftingHandler)) {
            setStatus("Open a crafting screen first", ChatFormatting.RED);
            return -1;
        }

        int outputPerCraft = Math.max(1, selectedCraftResult.getCount());
        int repeats = Math.max(1, (int) Math.ceil((double) value / outputPerCraft));
        return repeats;
    }

    private int resolveCraftAmount() {
        if (selectedCraftOption == null || selectedCraftResult.isEmpty()) {
            setStatus("Select a recipe first", ChatFormatting.RED);
            return -1;
        }

        if (craftUseMaxAmount) {
            int maxAmount = selectedCraftOption.maxOutputNow;
            if (maxAmount <= 0) {
                setStatus(selectedCraftOption.hasMaterialsNow ? "No room to store crafted items" : "Missing materials", ChatFormatting.RED);
                return -1;
            }
            return maxAmount;
        }

        int value = parseConfiguredCraftAmount();
        if (value <= 0) {
            setStatus("Invalid craft amount", ChatFormatting.RED);
            return -1;
        }
        return value;
    }

    private int parseConfiguredCraftAmount() {
        if (timesField == null) return 1;
        try {
            int value = Integer.parseInt(timesField.getText().trim());
            return Math.max(1, value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<Packet<?>> buildFabricatedSequence(int slot, int repeats) {
        List<Packet<?>> packets = new ArrayList<>();

        for (int i = 0; i < repeats; i++) {
            Packet<?> built = buildFabricatedPacket(slot);
            if (built == null) return List.of();
            packets.add(normalizeFabricatorPacket(built));
        }

        return packets;
    }

    private Packet<?> normalizeFabricatorPacket(Packet<?> packet) {
        if (currentAction.isPacketAction()) return packet;
        if (packet instanceof ServerboundContainerClickPacket clickSlotPacket) {
            return PacketRegenerator.regenerate(clickSlotPacket);
        }
        return packet;
    }

    private Integer findSlotByItemName(String searchName) {
        if (searchName == null || searchName.trim().isEmpty()) {
            return null;
        }

        AbstractContainerMenu handler = getActiveHandler();
        if (handler == null) return null;

        ItemTarget target = ItemTarget.fromLegacyEntry(searchName);
        Integer bestMatch = null;
        int bestScore = -1;

        for (Slot slot : handler.slots) {
            if (slot == null || slot.getItem().isEmpty()) continue;

            int score = target.score(slot.getItem(), -1);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = slot.index;
            }
        }

        return bestScore >= 0 ? bestMatch : null;
    }

    private void applyPresetMetrics() {
        DEFAULT_PANEL_WIDTH = 236;
        LINE_HEIGHT = 14;
        CRAFT_LIST_HEIGHT = 84;
        CRAFT_PLAN_HEIGHT = 52;
        FIELD_WIDTH = 100;
        BUTTON_HEIGHT = 20;
        LABEL_WIDTH = 64;
    }

    private int standardPanelHeight() {
        return Math.max(140, requiredPanelHeightForInfoRows(getStandardTimesRowY() + 22));
    }

    private int craftModePanelHeight() {
        return Math.max(200, requiredPanelHeightForInfoRows(getCraftListY() + CRAFT_LIST_HEIGHT + 14));
    }

    private void updateListHeights() {
        int listHeight = Math.max(28, Math.min(84, filteredCraftableRecipes.size() * LINE_HEIGHT));
        int planHeight = Math.max(26, Math.min(52, plannedCraftEntries.size() * 13));
        if (listHeight == CRAFT_LIST_HEIGHT && planHeight == CRAFT_PLAN_HEIGHT) return;
        CRAFT_LIST_HEIGHT = listHeight;
        CRAFT_PLAN_HEIGHT = planHeight;

        if (isCraftMode()) {
            if (timesField != null) timesField.setY(getCraftAmountRowY());
            if (craftSearchField != null) craftSearchField.setY(getCraftSearchY());
        }
    }
}
