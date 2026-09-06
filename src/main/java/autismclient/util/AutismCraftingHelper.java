package autismclient.util;

import autismclient.modules.PackHideState;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AutismCraftingHelper {
    private static Field recipeBookRecipesField;

    private AutismCraftingHelper() {
    }

    public enum CraftSource {
        PLAYER_2X2,
        TABLE_3X3
    }

    public static final class CraftableRecipeOption {
        public final String recipeKey;
        public final int recipeId;
        public final int syncedRecipeId;
        public final RecipeDisplayEntry syncedEntry;
        public final AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe;
        public final ItemStack result;
        public final Component labelComponent;
        public final String label;
        public final String registryId;
        public final String searchKey;
        public final boolean hasMaterialsNow;
        public final boolean hasInventoryRoomNow;
        public final int maxCraftsByMaterials;
        public final int maxCraftsByStorage;
        public final boolean craftableNow;
        public final int maxCraftsNow;
        public final int maxOutputNow;
        public final CraftSource craftSource;

        public CraftableRecipeOption(
            AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
            RecipeDisplayEntry syncedEntry,
            int maxCraftsByMaterials,
            int maxCraftsByStorage,
            CraftSource craftSource
        ) {
            this.localRecipe = localRecipe;
            this.syncedEntry = syncedEntry;
            this.recipeKey = localRecipe.recipeKey;
            this.recipeId = localRecipe.recipeKey.hashCode();
            this.syncedRecipeId = syncedEntry != null ? syncedEntry.id().index() : -1;
            this.result = localRecipe.result.copy();
            this.labelComponent = result.getHoverName().copy();
            this.label = labelComponent.getString();
            this.maxCraftsByMaterials = Math.max(0, maxCraftsByMaterials);
            this.maxCraftsByStorage = Math.max(0, maxCraftsByStorage);
            this.hasMaterialsNow = this.maxCraftsByMaterials > 0;
            this.hasInventoryRoomNow = this.maxCraftsByStorage > 0;
            this.maxCraftsNow = Math.max(0, Math.min(this.maxCraftsByMaterials, this.maxCraftsByStorage));
            this.craftableNow = this.maxCraftsNow > 0;
            this.maxOutputNow = this.maxCraftsNow * Math.max(1, result.getCount());
            this.craftSource = craftSource == null ? CraftSource.PLAYER_2X2 : craftSource;

            Identifier id = result.getItem() == null ? null : BuiltInRegistries.ITEM.getKey(result.getItem());
            this.registryId = id == null ? "" : id.toString();
            this.searchKey = (label + " " + registryId + " " + recipeKey).toLowerCase();
        }

        public boolean hasSyncedRecipe() {
            return syncedEntry != null && syncedRecipeId >= 0;
        }
    }

    public static final class CraftExecutionResult {
        public final boolean success;
        public final String message;
        public final int craftedAmount;

        private CraftExecutionResult(boolean success, String message, int craftedAmount) {
            this.success = success;
            this.message = message;
            this.craftedAmount = craftedAmount;
        }

        public static CraftExecutionResult success(String message, int craftedAmount) {
            return new CraftExecutionResult(true, message, craftedAmount);
        }

        public static CraftExecutionResult failure(String message) {
            return new CraftExecutionResult(false, message, 0);
        }
    }

    private static final class CraftPhasePlan {
        private final int totalCraftsAvailable;
        private final int maxCraftsPerPhase;

        private CraftPhasePlan(int totalCraftsAvailable, int maxCraftsPerPhase) {
            this.totalCraftsAvailable = Math.max(0, totalCraftsAvailable);
            this.maxCraftsPerPhase = Math.max(0, maxCraftsPerPhase);
        }
    }

    private static final class ExpectedGridSlot {
        private final int slotId;
        private final Ingredient ingredient;
        private final int expectedCount;

        private ExpectedGridSlot(int slotId, Ingredient ingredient, int expectedCount) {
            this.slotId = slotId;
            this.ingredient = ingredient;
            this.expectedCount = expectedCount;
        }
    }

    private static final class RecipeListingContext {
        private final CraftSource source;
        private final int gridWidth;
        private final int gridHeight;
        private final List<ItemStack> inputStacks;
        private final Map<String, RecipeDisplayEntry> syncedBySignature;
        private final List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes;

        private RecipeListingContext(
            CraftSource source,
            int gridWidth,
            int gridHeight,
            List<ItemStack> inputStacks,
            Map<String, RecipeDisplayEntry> syncedBySignature,
            List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes
        ) {
            this.source = source;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.inputStacks = inputStacks == null ? List.of() : List.copyOf(inputStacks);
            this.syncedBySignature = syncedBySignature == null ? Map.of() : Map.copyOf(syncedBySignature);
            this.syncedRecipes = syncedRecipes == null ? List.of() : List.copyOf(syncedRecipes);
        }
    }

    private static final class PreparedCraftContext {
        private final AbstractCraftingMenu handler;
        private final boolean closeAfter;
        private final Screen restoreScreen;

        private PreparedCraftContext(AbstractCraftingMenu handler, boolean closeAfter, Screen restoreScreen) {
            this.handler = handler;
            this.closeAfter = closeAfter;
            this.restoreScreen = restoreScreen;
        }
    }

    public static final class CraftScanSnapshot {
        final List<ItemStack> inventoryStacks;
        final boolean hasActiveCraftingMenu;
        final boolean activeIsPlayerMenu;
        final int activeGridWidth;
        final int activeGridHeight;
        final List<ItemStack> activeGridStacks;
        final List<RecipeDisplayEntry> rawSyncedEntries;
        final ContextMap slotContext;
        final FeatureFlagSet enabledFeatures;
        final net.minecraft.server.packs.resources.ResourceManager resourceManager;
        final net.minecraft.core.RegistryAccess registryAccess;
        final int modCount;

        public final Object connection;

        private CraftScanSnapshot(
            List<ItemStack> inventoryStacks,
            boolean hasActiveCraftingMenu,
            boolean activeIsPlayerMenu,
            int activeGridWidth,
            int activeGridHeight,
            List<ItemStack> activeGridStacks,
            List<RecipeDisplayEntry> rawSyncedEntries,
            ContextMap slotContext,
            FeatureFlagSet enabledFeatures,
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            net.minecraft.core.RegistryAccess registryAccess,
            int modCount,
            Object connection
        ) {
            this.inventoryStacks = inventoryStacks;
            this.hasActiveCraftingMenu = hasActiveCraftingMenu;
            this.activeIsPlayerMenu = activeIsPlayerMenu;
            this.activeGridWidth = activeGridWidth;
            this.activeGridHeight = activeGridHeight;
            this.activeGridStacks = activeGridStacks;
            this.rawSyncedEntries = rawSyncedEntries;
            this.slotContext = slotContext;
            this.enabledFeatures = enabledFeatures;
            this.resourceManager = resourceManager;
            this.registryAccess = registryAccess;
            this.modCount = modCount;
            this.connection = connection;
        }
    }

    public static CraftScanSnapshot createScanSnapshot(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return null;
        AbstractCraftingMenu activeCrafting = mc.player.containerMenu instanceof AbstractCraftingMenu handler ? handler : null;

        Inventory playerInventory = mc.player.getInventory();
        List<ItemStack> inventoryStacks = new ArrayList<>(playerInventory.getContainerSize());
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            inventoryStacks.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }

        boolean hasActive = activeCrafting != null;
        boolean activeIsPlayer = hasActive && isPlayerCraftingHandler(mc, activeCrafting);
        return new CraftScanSnapshot(
            inventoryStacks,
            hasActive,
            activeIsPlayer,
            hasActive ? activeCrafting.getGridWidth() : 0,
            hasActive ? activeCrafting.getGridHeight() : 0,
            hasActive ? getInputStacks(activeCrafting) : List.of(),
            new ArrayList<>(getRawSyncedRecipeEntries(mc)),
            SlotDisplayContext.fromLevel(mc.level),
            getEnabledFeatures(mc),
            mc.getResourceManager(),
            mc.getConnection() != null ? mc.getConnection().registryAccess() : mc.level.registryAccess(),
            net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size(),
            mc.getConnection()
        );
    }

    public static List<CraftableRecipeOption> getCraftableRecipes(Minecraft mc) {
        return computeCraftableRecipes(createScanSnapshot(mc));
    }

    public static List<CraftableRecipeOption> computeCraftableRecipes(CraftScanSnapshot snap) {
        if (snap == null) return List.of();
        long perfStart = AutismPerf.begin();

        List<RecipeListingContext> contexts = new ArrayList<>();
        if (snap.hasActiveCraftingMenu) {
            contexts.add(createListingContext(
                snap,
                snap.activeIsPlayerMenu ? CraftSource.PLAYER_2X2 : CraftSource.TABLE_3X3,
                snap.activeGridWidth,
                snap.activeGridHeight,
                snap.activeGridStacks
            ));
            if (snap.activeIsPlayerMenu) {
                contexts.add(createListingContext(snap, CraftSource.TABLE_3X3, 3, 3, List.of()));
            }
        } else {
            contexts.add(createListingContext(snap, CraftSource.TABLE_3X3, 3, 3, List.of()));
        }

        Map<String, CraftableRecipeOption> optionsByKey = new HashMap<>();
        List<AutismLocalCraftingRegistry.LocalCraftingRecipe> localRecipes = AutismLocalCraftingRegistry.getRecipes(
            snap.resourceManager, snap.registryAccess, snap.enabledFeatures, snap.slotContext, snap.modCount);
        for (RecipeListingContext context : contexts) {
            List<AutismLocalCraftingRegistry.LocalCraftingRecipe> candidates = mergeLocalAndSyncedRecipes(localRecipes, context.syncedRecipes);
            for (AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe : candidates) {
                if (!localRecipe.fits(context.gridWidth, context.gridHeight)) continue;
                if (localRecipe.result.isEmpty()) continue;

                int maxCraftsByMaterials = computeMaxCraftsByMaterials(localRecipe, context.gridWidth, context.gridHeight, context.inputStacks, snap.inventoryStacks);
                int maxCraftsByStorage = computeMaxCraftsByStorage(localRecipe, context.inputStacks, snap.inventoryStacks, maxCraftsByMaterials);
                RecipeDisplayEntry syncedEntry = context.syncedBySignature.get(localRecipe.signature);
                CraftableRecipeOption option = new CraftableRecipeOption(localRecipe, syncedEntry, maxCraftsByMaterials, maxCraftsByStorage, context.source);
                mergeRecipeOption(optionsByKey, option);
            }
        }

        List<CraftableRecipeOption> options = new ArrayList<>(optionsByKey.values());
        options.sort(
            Comparator.comparing((CraftableRecipeOption option) -> !option.craftableNow)
                .thenComparing((CraftableRecipeOption option) -> option.craftSource != CraftSource.PLAYER_2X2)
                .thenComparing((CraftableRecipeOption option) -> -option.maxCraftsNow)
                .thenComparing(option -> option.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(option -> option.recipeKey, String.CASE_INSENSITIVE_ORDER)
        );
        AutismPerf.endSpike("craft.scan", perfStart, 250_000_000L);
        return options;
    }

    private static RecipeListingContext createListingContext(
        CraftScanSnapshot snap,
        CraftSource source,
        int gridWidth,
        int gridHeight,
        List<ItemStack> inputStacks
    ) {
        SyncedResolution resolution = resolveSyncedEntriesCached(snap, gridWidth, gridHeight);
        return new RecipeListingContext(source, gridWidth, gridHeight, inputStacks, resolution.bySignature, resolution.recipes);
    }

    private static boolean isPlayerCraftingHandler(Minecraft mc, AbstractCraftingMenu craftingHandler) {
        return mc != null && mc.player != null && craftingHandler == mc.player.inventoryMenu;
    }

    private static FeatureFlagSet getEnabledFeatures(Minecraft mc) {
        if (mc == null) return FeatureFlagSet.of();
        if (mc.getConnection() != null) return mc.getConnection().enabledFeatures();
        if (mc.level != null) return mc.level.enabledFeatures();
        return FeatureFlagSet.of();
    }

    private static List<ItemStack> getInputStacks(AbstractCraftingMenu craftingHandler) {
        List<ItemStack> inputStacks = new ArrayList<>();
        for (Slot slot : craftingHandler.getInputGridSlots()) {
            if (slot == null || slot.getItem().isEmpty()) continue;
            inputStacks.add(slot.getItem().copy());
        }
        return inputStacks;
    }

    private static void mergeRecipeOption(Map<String, CraftableRecipeOption> optionsByKey, CraftableRecipeOption candidate) {
        if (candidate == null || candidate.recipeKey == null || candidate.recipeKey.isBlank()) return;
        String mergeKey = candidate.recipeKey;
        CraftableRecipeOption existing = optionsByKey.get(mergeKey);
        if (existing == null) {
            optionsByKey.put(mergeKey, candidate);
            return;
        }

        boolean preferCandidate = false;

        if (candidate.result.getCount() != existing.result.getCount()) {
            preferCandidate = candidate.result.getCount() > existing.result.getCount();
        } else if (candidate.craftableNow != existing.craftableNow) {
            preferCandidate = candidate.craftableNow;
        } else if (candidate.craftSource != existing.craftSource) {
            preferCandidate = candidate.craftSource == CraftSource.PLAYER_2X2;
        } else if (candidate.maxCraftsNow != existing.maxCraftsNow) {
            preferCandidate = candidate.maxCraftsNow > existing.maxCraftsNow;
        } else if (!existing.hasSyncedRecipe() && candidate.hasSyncedRecipe()) {
            preferCandidate = true;
        }

        if (preferCandidate) {
            optionsByKey.put(mergeKey, candidate);
        }
    }

    private static List<AutismLocalCraftingRegistry.LocalCraftingRecipe> mergeLocalAndSyncedRecipes(
        List<AutismLocalCraftingRegistry.LocalCraftingRecipe> localRecipes,
        List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes
    ) {
        List<AutismLocalCraftingRegistry.LocalCraftingRecipe> merged = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        if (localRecipes != null) {
            for (AutismLocalCraftingRegistry.LocalCraftingRecipe recipe : localRecipes) {
                if (recipe == null) continue;
                merged.add(recipe);
                if (recipe.signature != null && !recipe.signature.isBlank()) {
                    seenSignatures.add(recipe.signature);
                }
            }
        }

        if (syncedRecipes != null) {
            for (AutismLocalCraftingRegistry.LocalCraftingRecipe recipe : syncedRecipes) {
                if (recipe == null) continue;
                if (recipe.signature != null && seenSignatures.contains(recipe.signature)) continue;
                merged.add(recipe);
                if (recipe.signature != null && !recipe.signature.isBlank()) {
                    seenSignatures.add(recipe.signature);
                }
            }
        }

        return merged;
    }

    public static CraftableRecipeOption findCraftableRecipe(Minecraft mc, int recipeId) {
        for (CraftableRecipeOption option : getCraftableRecipes(mc)) {
            if (option.recipeId == recipeId || option.syncedRecipeId == recipeId) return option;
        }
        return null;
    }

    public static CraftableRecipeOption findCraftableRecipe(Minecraft mc, String recipeKey) {
        if (recipeKey == null || recipeKey.isBlank()) return null;
        for (CraftableRecipeOption option : getCraftableRecipes(mc)) {
            if (recipeKey.equalsIgnoreCase(option.recipeKey)) return option;
        }
        return null;
    }

    public static CraftableRecipeOption findRecipeMatchingOutput(Minecraft mc, ItemStack output) {
        if (output == null || output.isEmpty()) return null;

        CraftableRecipeOption best = null;
        for (CraftableRecipeOption option : getCraftableRecipes(mc)) {
            if (!sameResult(option.result, output)) continue;
            if (best == null) {
                best = option;
                continue;
            }
            if (option.craftableNow && !best.craftableNow) {
                best = option;
                continue;
            }
            if (option.craftableNow == best.craftableNow && option.maxCraftsNow > best.maxCraftsNow) {
                best = option;
            }
        }

        return best;
    }

    public static ItemStack getCurrentCraftOutput(Minecraft mc) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        if (!(mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler)) return ItemStack.EMPTY;
        return craftingHandler.getResultSlot().getItem().copy();
    }

    public static int getRequiredCraftClicks(int outputPerCraft, int desiredAmount) {
        int safeOutput = Math.max(1, outputPerCraft);
        int safeDesired = Math.max(1, desiredAmount);
        return Math.max(1, (int) Math.ceil((double) safeDesired / safeOutput));
    }

    public static int getRoundedCraftOutput(int outputPerCraft, int desiredAmount) {
        int safeOutput = Math.max(1, outputPerCraft);
        return getRequiredCraftClicks(safeOutput, desiredAmount) * safeOutput;
    }

    public static int getRoundedCraftOutput(CraftableRecipeOption option, int desiredAmount) {
        if (option == null) return getRoundedCraftOutput(1, desiredAmount);
        int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(option.result.getCount(), desiredAmount));
        return Math.max(0, desiredCrafts * Math.max(1, option.result.getCount()));
    }

    public static int getEffectiveRequestedOutput(CraftableRecipeOption option, int desiredAmount, boolean useMaxAmount) {
        if (option == null) return useMaxAmount ? 0 : Math.max(1, desiredAmount);
        if (useMaxAmount) return option.maxOutputNow;
        return Math.max(1, desiredAmount);
    }

    public static String getAvailabilityLabel(CraftableRecipeOption option) {
        if (option == null) return "No recipe";
        if (option.craftableNow) return "Crafts x" + option.maxCraftsNow;
        if (option.hasMaterialsNow && !option.hasInventoryRoomNow) return "No Room";
        return "Need Mats";
    }

    public static List<Packet<?>> buildCraftSequence(Minecraft mc, int recipeId, int desiredAmount) {
        return buildCraftSequence(mc, null, recipeId, desiredAmount);
    }

    public static List<Packet<?>> buildCraftSequence(Minecraft mc, String recipeKey, int recipeId, int desiredAmount) {
        if (mc == null || mc.player == null || mc.level == null) return List.of();
        if (!(mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler)) return List.of();

        CraftableRecipeOption option = resolveCraftOption(mc, recipeKey, recipeId);
        if (option == null || option.maxCraftsNow <= 0 || !option.hasSyncedRecipe()) return List.of();

        int remainingCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(option.result.getCount(), desiredAmount));
        if (remainingCrafts <= 0) return List.of();

        CraftPhasePlan phasePlan = createSyncedCraftPhasePlan(mc, option, craftingHandler);
        if (phasePlan.maxCraftsPerPhase <= 0) return List.of();

        List<Packet<?>> packets = new ArrayList<>();
        int availableCraftsLeft = phasePlan.totalCraftsAvailable;
        while (remainingCrafts > 0) {
            int phaseCrafts = Math.min(remainingCrafts, phasePlan.maxCraftsPerPhase);
            boolean useCraftAll = shouldUseCraftAll(phaseCrafts, availableCraftsLeft, phasePlan.maxCraftsPerPhase);
            int fillRequests = useCraftAll ? 1 : phaseCrafts;

            for (int i = 0; i < fillRequests; i++) {
                packets.add(new ServerboundPlaceRecipePacket(craftingHandler.containerId, option.syncedEntry.id(), useCraftAll));
            }

            ServerboundContainerClickPacket click = new ServerboundContainerClickPacket(
                craftingHandler.containerId,
                craftingHandler.getStateId(),
                (short) craftingHandler.getResultSlot().index,
                (byte) 0,
                ContainerInput.QUICK_MOVE,
                new Int2ObjectArrayMap<>(),
                HashedStack.EMPTY
            );
            packets.add(PacketRegenerator.regenerate(click));
            remainingCrafts -= phaseCrafts;
            availableCraftsLeft = Math.max(0, availableCraftsLeft - phaseCrafts);
        }

        return packets;
    }

    public static CraftExecutionResult executeCraftImmediately(Minecraft mc, int recipeId, int desiredAmount) {
        return executeCraftImmediately(mc, null, recipeId, desiredAmount);
    }

    public static CraftExecutionResult executeCraftImmediately(Minecraft mc, String recipeKey, int recipeId, int desiredAmount) {
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            return CraftExecutionResult.failure("No network connection.");
        }

        CraftableRecipeOption requestedOption = resolveCraftOption(mc, recipeKey, recipeId);
        if (requestedOption == null) {
            return CraftExecutionResult.failure("That recipe is not available right now.");
        }
        if (!requestedOption.craftableNow || requestedOption.maxCraftsNow <= 0) {
            return CraftExecutionResult.failure("Missing materials for " + requestedOption.label + ".");
        }

        PreparedCraftContext preparedContext = prepareCraftExecution(mc, requestedOption);
        if (preparedContext == null || preparedContext.handler == null) {
            return requestedOption.craftSource == CraftSource.TABLE_3X3
                ? CraftExecutionResult.failure("No reachable crafting table is available.")
                : CraftExecutionResult.failure("Open your inventory or a crafting screen first.");
        }

        try {
            CraftableRecipeOption option = resolveCraftOption(mc, recipeKey, recipeId);
            if (option == null) option = requestedOption;
            if (!option.craftableNow || option.maxCraftsNow <= 0) {
                return CraftExecutionResult.failure("Missing materials for " + option.label + ".");
            }

            if (option.hasSyncedRecipe()) {
                CraftExecutionResult fastResult = executeCraftViaSyncedRecipe(mc, preparedContext.handler, option, desiredAmount);
                if (fastResult.success || fastResult.craftedAmount > 0) {
                    return fastResult;
                }
            }

            return executeCraftManually(mc, preparedContext.handler, option, desiredAmount);
        } finally {
            finishPreparedCraftContext(mc, preparedContext);
        }
    }

    private static CraftableRecipeOption resolveCraftOption(Minecraft mc, String recipeKey, int recipeId) {
        CraftableRecipeOption byKey = recipeKey == null || recipeKey.isBlank() ? null : findCraftableRecipe(mc, recipeKey);
        if (byKey != null) return byKey;
        return findCraftableRecipe(mc, recipeId);
    }

    private static PreparedCraftContext prepareCraftExecution(Minecraft mc, CraftableRecipeOption option) {
        if (mc == null || mc.player == null || option == null) return null;

        if (mc.player.containerMenu instanceof AbstractCraftingMenu activeHandler
            && option.localRecipe.fits(activeHandler.getGridWidth(), activeHandler.getGridHeight())) {
            return new PreparedCraftContext(activeHandler, false, null);
        }

        if (option.craftSource == CraftSource.PLAYER_2X2 && mc.player.containerMenu == mc.player.inventoryMenu) {
            return new PreparedCraftContext(mc.player.inventoryMenu, false, null);
        }

        BlockPos tablePos = findReachableCraftingTable(mc);
        if (tablePos == null) return null;

        Screen restoreScreen = mc.player.containerMenu == mc.player.inventoryMenu ? mc.gui.screen() : null;
        if (!openCraftingTable(mc, tablePos)) return null;

        AbstractCraftingMenu openedHandler = waitForCraftingHandler(mc, 3, 3, 1500L);
        if (openedHandler == null) return null;
        return new PreparedCraftContext(openedHandler, true, restoreScreen);
    }

    private static void finishPreparedCraftContext(Minecraft mc, PreparedCraftContext context) {
        if (mc == null || mc.player == null || context == null || !context.closeAfter) return;

        runClientTask(mc, () -> {
            if (mc.player == null) return;
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            if (context.restoreScreen != null) {
                mc.gui.setScreen(context.restoreScreen);
                mc.player.containerMenu = mc.player.inventoryMenu;
            }
        });
    }

    private static boolean openCraftingTable(Minecraft mc, BlockPos tablePos) {
        if (mc == null || mc.player == null || mc.gameMode == null || tablePos == null) return false;
        BlockHitResult hitResult = createCraftingTableHitResult(mc, tablePos);
        if (hitResult == null) return false;

        return runClientTask(mc, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult));
    }

    private static AbstractCraftingMenu waitForCraftingHandler(Minecraft mc, int requiredWidth, int requiredHeight, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
                if (craftingHandler.getGridWidth() >= requiredWidth
                    && craftingHandler.getGridHeight() >= requiredHeight
                    && craftingHandler.getStateId() > 0) {
                    return craftingHandler;
                }
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static BlockPos findReachableCraftingTable(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return null;
        double reach = Math.max(4.5D, mc.player.blockInteractionRange());
        double reachSq = reach * reach;
        int radius = (int) Math.ceil(reach);
        BlockPos origin = mc.player.blockPosition();
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (!mc.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) continue;

            Vec3 center = Vec3.atCenterOf(pos);
            double distanceSq = mc.player.distanceToSqr(center);
            if (distanceSq > reachSq || distanceSq >= bestDistanceSq) continue;

            BlockHitResult hitResult = createCraftingTableHitResult(mc, pos);
            if (hitResult == null) continue;

            bestPos = pos.immutable();
            bestDistanceSq = distanceSq;
        }

        return bestPos;
    }

    private static BlockHitResult createCraftingTableHitResult(Minecraft mc, BlockPos tablePos) {
        if (mc == null || mc.player == null || mc.level == null || tablePos == null) return null;
        Vec3 center = Vec3.atCenterOf(tablePos);
        double reach = Math.max(4.5D, mc.player.blockInteractionRange());
        if (mc.player.distanceToSqr(center) > reach * reach) return null;

        ClipContext context = new ClipContext(
            mc.player.getEyePosition(),
            center,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player
        );
        BlockHitResult result = mc.level.clip(context);
        if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(tablePos)) {
            return result;
        }

        return new BlockHitResult(center, Direction.UP, tablePos, false);
    }

    private static CraftExecutionResult executeCraftViaSyncedRecipe(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        CraftableRecipeOption option,
        int desiredAmount
    ) {
        if (mc.gameMode == null) {
            return CraftExecutionResult.failure("Crafting interaction manager is unavailable.");
        }

        int safeDesiredAmount = Math.max(1, desiredAmount);
        int outputPerCraft = Math.max(1, option.result.getCount());
        int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(outputPerCraft, safeDesiredAmount));
        int roundedTarget = Math.min(option.maxOutputNow, desiredCrafts * outputPerCraft);
        int syncId = craftingHandler.containerId;
        int outputSlotId = craftingHandler.getResultSlot().index;
        int beforeCount = countInventoryItems(mc, option.result);
        AtomicInteger remainingCrafts = new AtomicInteger(desiredCrafts);
        AtomicInteger dispatchedCrafts = new AtomicInteger(0);

        if (!runClientTask(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            if (!(mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler)) return;
            if (currentHandler.containerId != syncId) return;

            while (remainingCrafts.get() > 0) {
                CraftableRecipeOption currentOption = resolveCraftOption(mc, option.recipeKey, option.recipeId);
                if (currentOption == null || currentOption.maxCraftsNow <= 0 || !currentOption.hasSyncedRecipe()) break;

                CraftPhasePlan phasePlan = createSyncedCraftPhasePlan(mc, currentOption, currentHandler);
                if (phasePlan.maxCraftsPerPhase <= 0) break;

                int phaseCrafts = Math.min(remainingCrafts.get(), Math.min(currentOption.maxCraftsNow, phasePlan.maxCraftsPerPhase));
                boolean useCraftAll = shouldUseCraftAll(phaseCrafts, currentOption.maxCraftsNow, phasePlan.maxCraftsPerPhase);
                int fillRequests = useCraftAll ? 1 : phaseCrafts;

                for (int i = 0; i < fillRequests; i++) {
                    mc.gameMode.handlePlaceRecipe(syncId, currentOption.syncedEntry.id(), useCraftAll);
                }

                mc.gameMode.handleContainerInput(syncId, outputSlotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                dispatchedCrafts.addAndGet(phaseCrafts);
                remainingCrafts.addAndGet(-phaseCrafts);
            }
        })) {
            return CraftExecutionResult.failure("Crafting interaction manager is unavailable.");
        }

        int expectedCount = beforeCount + Math.min(roundedTarget, dispatchedCrafts.get() * outputPerCraft);
        waitForInventoryCount(mc, option.result, expectedCount, 700L);

        int craftedAmount = Math.max(0, countInventoryItems(mc, option.result) - beforeCount);
        if (craftedAmount <= 0) {
            return CraftExecutionResult.failure("Craft failed or your inventory had no room.");
        }

        return buildCraftResult(option, craftedAmount, roundedTarget);
    }

    private static CraftExecutionResult executeCraftManually(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        CraftableRecipeOption option,
        int desiredAmount
    ) {
        int outputPerCraft = Math.max(1, option.result.getCount());
        int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(outputPerCraft, desiredAmount));
        if (desiredCrafts <= 0) {
            return CraftExecutionResult.failure("Missing materials for " + option.label + ".");
        }

        int beforeCount = countInventoryItems(mc, option.result);
        int craftedCrafts = 0;
        int syncId = craftingHandler.containerId;
        int outputSlotId = craftingHandler.getResultSlot().index;
        int roundedTarget = Math.min(option.maxOutputNow, desiredCrafts * outputPerCraft);

        while (craftedCrafts < desiredCrafts) {
            CraftableRecipeOption currentOption = resolveCraftOption(mc, option.recipeKey, option.recipeId);
            if (currentOption == null || currentOption.maxCraftsNow <= 0) break;

            int phaseLimit = computeManualMaxCraftsPerPhase(currentOption.localRecipe, craftingHandler, mc.player.getInventory());
            if (phaseLimit <= 0) break;

            int phaseCrafts = Math.min(desiredCrafts - craftedCrafts, Math.min(currentOption.maxCraftsNow, phaseLimit));
            if (!clearCraftGrid(mc, craftingHandler, syncId)) break;
            if (!placeRecipeCrafts(mc, craftingHandler, currentOption.localRecipe, syncId, phaseCrafts)) break;
            if (!waitForOutput(mc, syncId, currentOption.result, 400L)) break;

            if (!runClientTask(mc, () -> {
                if (mc.player == null || mc.gameMode == null) return;
                if (!(mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler)) return;
                if (currentHandler.containerId != syncId) return;
                mc.gameMode.handleContainerInput(syncId, outputSlotId, 0, ContainerInput.QUICK_MOVE, mc.player);
            })) {
                break;
            }

            craftedCrafts += phaseCrafts;
            waitForInventoryCount(mc, option.result, beforeCount + craftedCrafts * outputPerCraft, 500L);
        }

        int craftedAmount = Math.max(0, countInventoryItems(mc, option.result) - beforeCount);
        if (craftedAmount <= 0) {
            return CraftExecutionResult.failure("Craft failed or your inventory had no room.");
        }

        return buildCraftResult(option, craftedAmount, roundedTarget);
    }

    private static boolean clearCraftGrid(Minecraft mc, AbstractCraftingMenu craftingHandler, int syncId) {
        for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
            if (inputSlot == null || inputSlot.getItem().isEmpty()) continue;
            if (!runClientTask(mc, () -> {
                if (mc.player == null || mc.gameMode == null) return;
                if (!(mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler)) return;
                if (currentHandler.containerId != syncId) return;
                mc.gameMode.handleContainerInput(syncId, inputSlot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            })) {
                return false;
            }
        }

        return waitForInputGridClear(mc, syncId, 300L);
    }

    private static boolean placeRecipeOnce(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        AutismLocalCraftingRegistry.LocalCraftingRecipe recipe,
        int syncId
    ) {
        return placeRecipeCrafts(mc, craftingHandler, recipe, syncId, 1);
    }

    private static boolean placeRecipeCrafts(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        AutismLocalCraftingRegistry.LocalCraftingRecipe recipe,
        int syncId,
        int craftCount
    ) {
        if (mc.player == null) return false;
        int requiredPerSlot = Math.max(1, craftCount);

        List<Slot> inputSlots = craftingHandler.getInputGridSlots();
        if (recipe.shaped) {
            for (int row = 0; row < recipe.height; row++) {
                for (int col = 0; col < recipe.width; col++) {
                    int recipeIndex = row * recipe.width + col;
                    if (recipeIndex < 0 || recipeIndex >= recipe.gridIngredients.size()) continue;
                    Ingredient ingredient = recipe.gridIngredients.get(recipeIndex);
                    if (ingredient == null || ingredient.isEmpty()) continue;

                    int gridIndex = row * craftingHandler.getGridWidth() + col;
                    if (gridIndex < 0 || gridIndex >= inputSlots.size()) return false;
                    if (!placeIngredientCountIntoSlot(mc, craftingHandler, syncId, inputSlots.get(gridIndex).index, ingredient, requiredPerSlot)) {
                        return false;
                    }
                }
            }
            return true;
        }

        for (int i = 0; i < recipe.requirements.size(); i++) {
            if (i < 0 || i >= inputSlots.size()) return false;
            Ingredient ingredient = recipe.requirements.get(i);
            if (ingredient == null || ingredient.isEmpty()) continue;
            if (!placeIngredientCountIntoSlot(mc, craftingHandler, syncId, inputSlots.get(i).index, ingredient, requiredPerSlot)) {
                return false;
            }
        }
        return true;
    }

    private static boolean placeIngredientIntoSlot(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        int syncId,
        int targetSlotId,
        Ingredient ingredient
    ) {
        return placeIngredientCountIntoSlot(mc, craftingHandler, syncId, targetSlotId, ingredient, 1);
    }

    private static boolean placeIngredientCountIntoSlot(
        Minecraft mc,
        AbstractCraftingMenu craftingHandler,
        int syncId,
        int targetSlotId,
        Ingredient ingredient,
        int requiredCount
    ) {
        if (mc.player == null || mc.gameMode == null) return false;
        int remaining = Math.max(1, requiredCount);

        while (remaining > 0) {
            Integer sourceSlotId = findBestMatchingInventorySlot(craftingHandler, mc.player.getInventory(), ingredient, remaining);
            if (sourceSlotId == null) return false;

            Slot sourceSlot = craftingHandler.getSlot(sourceSlotId);
            if (sourceSlot == null || sourceSlot.getItem().isEmpty()) return false;

            int sourceCount = sourceSlot.getItem().getCount();
            int placeCount = Math.min(sourceCount, remaining);
            if (!runClientTask(mc, () -> {
                clickSlot(mc, syncId, sourceSlotId, 0, ContainerInput.PICKUP);
                if (placeCount >= sourceCount) {
                    clickSlot(mc, syncId, targetSlotId, 0, ContainerInput.PICKUP);
                    return;
                }

                for (int i = 0; i < placeCount; i++) {
                    clickSlot(mc, syncId, targetSlotId, 1, ContainerInput.PICKUP);
                }
                clickSlot(mc, syncId, sourceSlotId, 0, ContainerInput.PICKUP);
            })) {
                return false;
            }

            remaining -= placeCount;
        }

        return waitForTargetIngredientCount(mc, syncId, targetSlotId, ingredient, requiredCount, 300L);
    }

    private static void clickSlot(Minecraft mc, int syncId, int slotId, int button, ContainerInput actionType) {
        if (PackHideState.isHardLocked()) return;
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(syncId, slotId, button, actionType, mc.player);
    }

    private static Integer findMatchingInventorySlot(
        AbstractCraftingMenu craftingHandler,
        Inventory inventory,
        Ingredient ingredient
    ) {
        Integer exactOne = null;
        Integer fallback = null;
        int fallbackCount = Integer.MAX_VALUE;

        for (Slot slot : craftingHandler.slots) {
            if (slot == null || slot.container != inventory || slot.getItem().isEmpty()) continue;
            ItemStack stack = slot.getItem();
            if (!ingredient.test(stack)) continue;
            if (stack.getCount() == 1) {
                exactOne = slot.index;
                break;
            }
            if (stack.getCount() < fallbackCount) {
                fallback = slot.index;
                fallbackCount = stack.getCount();
            }
        }

        return exactOne != null ? exactOne : fallback;
    }

    private static Integer findBestMatchingInventorySlot(
        AbstractCraftingMenu craftingHandler,
        Inventory inventory,
        Ingredient ingredient,
        int desiredCount
    ) {
        Integer exact = null;
        Integer bestWhole = null;
        int bestWholeCount = -1;
        Integer bestPartial = null;
        int bestPartialCount = Integer.MAX_VALUE;

        for (Slot slot : craftingHandler.slots) {
            if (slot == null || slot.container != inventory || slot.getItem().isEmpty()) continue;
            ItemStack stack = slot.getItem();
            if (!ingredient.test(stack)) continue;

            int count = stack.getCount();
            if (count == desiredCount) {
                exact = slot.index;
                break;
            }
            if (count < desiredCount && count > bestWholeCount) {
                bestWhole = slot.index;
                bestWholeCount = count;
            } else if (count > desiredCount && count < bestPartialCount) {
                bestPartial = slot.index;
                bestPartialCount = count;
            }
        }

        if (exact != null) return exact;
        if (bestWhole != null) return bestWhole;
        return bestPartial != null ? bestPartial : findMatchingInventorySlot(craftingHandler, inventory, ingredient);
    }

    private static boolean waitForInputGridClear(Minecraft mc, int syncId, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
                if (craftingHandler.containerId != syncId) return false;
                boolean anyInput = false;
                for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
                    if (inputSlot != null && !inputSlot.getItem().isEmpty()) {
                        anyInput = true;
                        break;
                    }
                }
                if (!anyInput) return true;
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean waitForTargetIngredient(
        Minecraft mc,
        int syncId,
        int targetSlotId,
        Ingredient ingredient,
        long timeoutMs
    ) {
        return waitForTargetIngredientCount(mc, syncId, targetSlotId, ingredient, 1, timeoutMs);
    }

    private static boolean waitForTargetIngredientCount(
        Minecraft mc,
        int syncId,
        int targetSlotId,
        Ingredient ingredient,
        int requiredCount,
        long timeoutMs
    ) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (mc.player != null && mc.player.containerMenu instanceof AbstractContainerMenu handler) {
                if (handler.containerId != syncId) return false;
                Slot slot = handler.getSlot(targetSlotId);
                if (slot != null) {
                    ItemStack stack = slot.getItem();
                    if (stack != null && !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= requiredCount) {
                        return true;
                    }
                }
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean waitForOutput(Minecraft mc, int syncId, ItemStack expectedResult, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
                if (craftingHandler.containerId != syncId) return false;
                ItemStack output = craftingHandler.getResultSlot().getItem();
                if (!output.isEmpty() && output.getItem() == expectedResult.getItem()) {
                    return true;
                }
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static final class SyncedResolution {
        final List<AutismLocalCraftingRegistry.LocalCraftingRecipe> recipes;
        final Map<String, RecipeDisplayEntry> bySignature;

        SyncedResolution(List<AutismLocalCraftingRegistry.LocalCraftingRecipe> recipes, Map<String, RecipeDisplayEntry> bySignature) {
            this.recipes = recipes;
            this.bySignature = bySignature;
        }
    }

    private static final Object SYNCED_CACHE_LOCK = new Object();
    private static java.lang.ref.WeakReference<Object> cachedSyncedConnection = new java.lang.ref.WeakReference<>(null);
    private static int cachedSyncedEntryCount = -1;
    private static final Map<Long, SyncedResolution> cachedSyncedByGrid = new HashMap<>();

    private static SyncedResolution resolveSyncedEntriesCached(CraftScanSnapshot snap, int gridWidth, int gridHeight) {
        long gridKey = ((long) gridWidth << 16) | (gridHeight & 0xFFFFL);
        synchronized (SYNCED_CACHE_LOCK) {
            if (cachedSyncedConnection.get() != snap.connection || cachedSyncedEntryCount != snap.rawSyncedEntries.size()) {
                cachedSyncedByGrid.clear();
                cachedSyncedConnection = new java.lang.ref.WeakReference<>(snap.connection);
                cachedSyncedEntryCount = snap.rawSyncedEntries.size();
            }
            SyncedResolution cached = cachedSyncedByGrid.get(gridKey);
            if (cached != null) return cached;

            long perfStart = AutismPerf.begin();
            List<RecipeDisplayEntry> supported = new ArrayList<>();
            for (RecipeDisplayEntry entry : snap.rawSyncedEntries) {
                if (isSupportedCraftingEntry(entry, gridWidth, gridHeight)) {
                    supported.add(entry);
                }
            }
            SyncedResolution fresh = resolveSyncedEntries(supported, snap.slotContext, snap.enabledFeatures);
            AutismPerf.endSpike("craft.syncedResolve", perfStart, 100_000_000L);
            cachedSyncedByGrid.put(gridKey, fresh);
            return fresh;
        }
    }

    private static SyncedResolution resolveSyncedEntries(
        List<RecipeDisplayEntry> entries,
        ContextMap params,
        FeatureFlagSet enabledFeatures
    ) {
        List<AutismLocalCraftingRegistry.LocalCraftingRecipe> recipes = new ArrayList<>();
        Map<String, RecipeDisplayEntry> bySignature = new HashMap<>();
        if (entries == null || params == null) return new SyncedResolution(recipes, bySignature);

        Set<String> seenKeys = new HashSet<>();
        for (RecipeDisplayEntry entry : entries) {
            if (entry == null || entry.display() == null) continue;
            AutismLocalCraftingRegistry.LocalCraftingRecipe recipe = AutismLocalCraftingRegistry.createFromSyncedDisplay(
                "synced:" + entry.id().index(),
                entry.display(),
                entry.craftingRequirements(),
                enabledFeatures,
                params
            );
            if (recipe == null) continue;
            if (recipe.signature != null) bySignature.putIfAbsent(recipe.signature, entry);
            if (seenKeys.add(recipe.recipeKey)) recipes.add(recipe);
        }
        return new SyncedResolution(recipes, bySignature);
    }

    private static String signatureForIngredient(Ingredient ingredient, ContextMap params) {
        if (ingredient == null || ingredient.isEmpty()) return "_";
        return signatureForStacks(ingredient.display().resolveForStacks(params));
    }

    private static String signatureForStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return "_";
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getItem() == null) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) ids.add(id.toString());
        }
        if (ids.isEmpty()) return "_";
        List<String> ordered = new ArrayList<>(ids);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", ordered);
    }

    private static int computeMaxCraftsByMaterials(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        AbstractCraftingMenu craftingHandler,
        Inventory playerInventory
    ) {
        return computeMaxCraftsByMaterials(
            localRecipe,
            craftingHandler == null ? 0 : craftingHandler.getGridWidth(),
            craftingHandler == null ? 0 : craftingHandler.getGridHeight(),
            getInputStacks(craftingHandler),
            playerInventory
        );
    }

    private static int computeMaxCraftsByMaterials(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        int gridWidth,
        int gridHeight,
        List<ItemStack> inputStacks,
        Inventory playerInventory
    ) {
        if (localRecipe == null || localRecipe.requirements.isEmpty()) return 0;
        if (!localRecipe.fits(gridWidth, gridHeight)) return 0;

        StackedItemContents matcher = new StackedItemContents();
        addInventoryToMatcher(matcher, playerInventory);
        addInputStacksToMatcher(matcher, inputStacks);
        return getMaximumCrafts(matcher, localRecipe.requirements);
    }

    private static int computeMaxCraftsByMaterials(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        int gridWidth,
        int gridHeight,
        List<ItemStack> inputStacks,
        List<ItemStack> inventoryStacks
    ) {
        if (localRecipe == null || localRecipe.requirements.isEmpty()) return 0;
        if (!localRecipe.fits(gridWidth, gridHeight)) return 0;

        StackedItemContents matcher = new StackedItemContents();
        addInputStacksToMatcher(matcher, inventoryStacks);
        addInputStacksToMatcher(matcher, inputStacks);
        return getMaximumCrafts(matcher, localRecipe.requirements);
    }

    private static int getMaximumCrafts(StackedItemContents matcher, List<Ingredient> requirements) {
        if (matcher == null || requirements == null || requirements.isEmpty()) return 0;
        return matcher.canCraft(requirements, null) ? 64 : 0;
    }

    private static int computeMaxCraftsByStorage(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        List<ItemStack> inputStacks,
        List<ItemStack> inventoryStacks,
        int maxCraftsByMaterials
    ) {
        if (localRecipe == null || localRecipe.result.isEmpty() || inventoryStacks == null) return 0;
        int craftLimit = Math.max(0, maxCraftsByMaterials);
        if (craftLimit <= 0) return 0;

        List<ItemStack> inventorySlots = new ArrayList<>(36);
        int playerSlotCount = Math.min(36, inventoryStacks.size());
        for (int i = 0; i < playerSlotCount; i++) {
            ItemStack stack = inventoryStacks.get(i);
            inventorySlots.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }

        List<ItemStack> gridPools = new ArrayList<>();
        if (inputStacks != null) {
            for (ItemStack stack : inputStacks) {
                if (stack != null && !stack.isEmpty()) {
                    gridPools.add(stack.copy());
                }
            }
        }

        int crafted = 0;
        while (crafted < craftLimit) {
            if (!consumeOneCraftFromPools(localRecipe, inventorySlots, gridPools)) break;
            if (!storeCraftResult(localRecipe.result, inventorySlots)) break;
            crafted++;
        }
        return crafted;
    }

    private static boolean consumeOneCraftFromPools(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        List<ItemStack> inventorySlots,
        List<ItemStack> gridPools
    ) {
        if (localRecipe == null || localRecipe.requirements.isEmpty()) return false;
        for (Ingredient ingredient : localRecipe.requirements) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            if (consumeMatchingIngredient(inventorySlots, ingredient, true)) continue;
            if (consumeMatchingIngredient(gridPools, ingredient, false)) continue;
            return false;
        }
        return true;
    }

    private static boolean consumeMatchingIngredient(List<ItemStack> stacks, Ingredient ingredient, boolean preferSmallestStack) {
        if (stacks == null || stacks.isEmpty() || ingredient == null || ingredient.isEmpty()) return false;

        int bestIndex = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty() || !ingredient.test(stack)) continue;

            int count = stack.getCount();
            if (bestIndex < 0 || (preferSmallestStack && count < bestCount)) {
                bestIndex = i;
                bestCount = count;
                if (preferSmallestStack && bestCount <= 1) break;
            }
        }

        if (bestIndex < 0) return false;

        ItemStack chosen = stacks.get(bestIndex);
        chosen.shrink(1);
        if (chosen.isEmpty()) {
            stacks.set(bestIndex, ItemStack.EMPTY);
        }
        return true;
    }

    private static boolean storeCraftResult(ItemStack resultStack, List<ItemStack> inventorySlots) {
        if (resultStack == null || resultStack.isEmpty() || inventorySlots == null) return false;

        int remaining = resultStack.getCount();
        int maxStackSize = Math.max(1, resultStack.getMaxStackSize());

        for (int i = 0; i < inventorySlots.size() && remaining > 0; i++) {
            ItemStack existing = inventorySlots.get(i);
            if (existing == null || existing.isEmpty()) continue;
            if (existing.getItem() != resultStack.getItem()) continue;

            int existingMax = Math.max(1, Math.min(existing.getMaxStackSize(), maxStackSize));
            if (existing.getCount() >= existingMax) continue;

            int add = Math.min(existingMax - existing.getCount(), remaining);
            existing.grow(add);
            remaining -= add;
        }

        for (int i = 0; i < inventorySlots.size() && remaining > 0; i++) {
            ItemStack existing = inventorySlots.get(i);
            if (existing != null && !existing.isEmpty()) continue;

            int add = Math.min(maxStackSize, remaining);
            ItemStack placed = resultStack.copy();
            placed.setCount(add);
            inventorySlots.set(i, placed);
            remaining -= add;
        }

        return remaining <= 0;
    }

    private static int computeManualMaxCraftsPerPhase(
        AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
        AbstractCraftingMenu craftingHandler,
        Inventory playerInventory
    ) {
        if (localRecipe == null || playerInventory == null) return 0;

        int maxCrafts = Integer.MAX_VALUE;
        List<Ingredient> ingredients = localRecipe.shaped ? localRecipe.gridIngredients : localRecipe.requirements;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            int slotLimit = findMaxIngredientStackSize(craftingHandler, playerInventory, ingredient);
            if (slotLimit <= 0) return 0;
            maxCrafts = Math.min(maxCrafts, slotLimit);
        }

        return maxCrafts == Integer.MAX_VALUE ? 0 : Math.max(1, maxCrafts);
    }

    private static void addInventoryToMatcher(StackedItemContents matcher, Inventory playerInventory) {
        if (matcher == null || playerInventory == null) return;
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            matcher.accountStack(stack);
        }
    }

    private static int findMaxIngredientStackSize(
        AbstractCraftingMenu craftingHandler,
        Inventory playerInventory,
        Ingredient ingredient
    ) {
        int best = 0;
        for (Slot slot : craftingHandler.slots) {
            if (slot == null || slot.container != playerInventory || slot.getItem().isEmpty()) continue;
            ItemStack stack = slot.getItem();
            if (!ingredient.test(stack)) continue;
            best = Math.max(best, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack)));
        }
        return best;
    }

    private static void addInputGridToMatcher(StackedItemContents matcher, AbstractCraftingMenu craftingHandler) {
        if (matcher == null || craftingHandler == null) return;
        for (Slot slot : craftingHandler.getInputGridSlots()) {
            if (slot == null) continue;
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;
            matcher.accountStack(stack);
        }
    }

    private static void addInputStacksToMatcher(StackedItemContents matcher, List<ItemStack> inputStacks) {
        if (matcher == null || inputStacks == null) return;
        for (ItemStack stack : inputStacks) {
            if (stack == null || stack.isEmpty()) continue;
            matcher.accountStack(stack);
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<RecipeDisplayEntry> getRawSyncedRecipeEntries(Minecraft mc) {
        if (mc == null || mc.player == null) return List.of();
        if (!(mc.player.getRecipeBook() instanceof ClientRecipeBook recipeBook)) return List.of();

        try {
            if (recipeBookRecipesField == null) {
                recipeBookRecipesField = ClientRecipeBook.class.getDeclaredField("known");
                recipeBookRecipesField.setAccessible(true);
            }
            Object raw = recipeBookRecipesField.get(recipeBook);
            if (raw instanceof Map<?, ?> recipeMap) {
                List<RecipeDisplayEntry> entries = new ArrayList<>(recipeMap.size());
                for (Object value : recipeMap.values()) {
                    if (value instanceof RecipeDisplayEntry entry) {
                        entries.add(entry);
                    }
                }
                if (!entries.isEmpty()) return entries;
            }
        } catch (Exception ignored) {  }

        recipeBook.rebuildCollections();
        List<RecipeDisplayEntry> fallback = new ArrayList<>();
        for (var collection : recipeBook.getCollections()) {
            fallback.addAll(collection.getRecipes());
        }
        return fallback;
    }

    private static boolean isSupportedCraftingEntry(RecipeDisplayEntry entry, int gridWidth, int gridHeight) {
        if (entry == null || entry.display() == null) return false;
        if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() <= gridWidth && shaped.height() <= gridHeight;
        }
        if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() <= gridWidth * gridHeight;
        }
        return false;
    }

    private static CraftPhasePlan createSyncedCraftPhasePlan(Minecraft mc, CraftableRecipeOption option, AbstractCraftingMenu craftingHandler) {
        if (mc.level == null || option == null || option.maxCraftsNow <= 0 || option.syncedEntry == null) {
            return new CraftPhasePlan(0, 0);
        }
        ContextMap params = SlotDisplayContext.fromLevel(mc.level);
        int maxCraftsPerPhase = computeMaxCraftsPerPhase(option.syncedEntry, craftingHandler, params, option.maxCraftsNow);
        return new CraftPhasePlan(option.maxCraftsNow, maxCraftsPerPhase);
    }

    private static int computeMaxCraftsPerPhase(RecipeDisplayEntry entry, AbstractCraftingMenu craftingHandler, ContextMap params, int totalCraftsAvailable) {
        int safeTotal = Math.max(0, totalCraftsAvailable);
        if (safeTotal <= 0) return 0;

        int phaseCrafts = safeTotal;
        List<Ingredient> ingredients = getPlanningIngredients(entry, params);
        if (ingredients.isEmpty()) return Math.min(phaseCrafts, 64);

        for (Ingredient ingredient : ingredients) {
            int bestMaxCount = 0;
            for (Slot slot : craftingHandler.slots) {
                if (slot == null) continue;
                ItemStack stack = slot.getItem();
                if (stack != null && !stack.isEmpty() && ingredient.test(stack)) {
                    bestMaxCount = Math.max(bestMaxCount, stack.getMaxStackSize());
                }
            }
            if (bestMaxCount <= 0) return 0;
            phaseCrafts = Math.min(phaseCrafts, bestMaxCount);
        }

        return Math.max(1, phaseCrafts);
    }

    private static List<Ingredient> getPlanningIngredients(RecipeDisplayEntry entry, ContextMap params) {
        if (entry.craftingRequirements().isPresent() && !entry.craftingRequirements().get().isEmpty()) {
            return new ArrayList<>(entry.craftingRequirements().get());
        }

        List<Ingredient> derived = new ArrayList<>();
        for (SlotDisplay slotDisplay : getIngredientDisplays(entry)) {
            if (slotDisplay == null) continue;
            List<ItemStack> displayStacks = slotDisplay.resolveForStacks(params);
            List<Item> items = new ArrayList<>();
            for (ItemStack stack : displayStacks) {
                if (stack != null && !stack.isEmpty() && stack.getItem() != null) {
                    items.add(stack.getItem());
                }
            }
            if (!items.isEmpty()) derived.add(Ingredient.of(items.stream()));
        }
        return derived;
    }

    private static List<ExpectedGridSlot> buildExpectedGridSlots(RecipeDisplayEntry entry, AbstractCraftingMenu craftingHandler, ContextMap params, int craftCount) {
        List<ExpectedGridSlot> expected = new ArrayList<>();
        List<Ingredient> ingredients = getPlanningIngredients(entry, params);
        if (ingredients.isEmpty()) return expected;

        List<Slot> inputSlots = craftingHandler.getInputGridSlots();
        int ingredientIndex = 0;

        if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
            for (int row = 0; row < shaped.height(); row++) {
                for (int col = 0; col < shaped.width(); col++) {
                    int shapedIndex = row * shaped.width() + col;
                    if (shapedIndex < 0 || shapedIndex >= shaped.ingredients().size()) continue;
                    SlotDisplay slotDisplay = shaped.ingredients().get(shapedIndex);
                    if (isEmptySlotDisplay(slotDisplay, params)) continue;

                    int gridIndex = row * craftingHandler.getGridWidth() + col;
                    if (gridIndex < 0 || gridIndex >= inputSlots.size() || ingredientIndex >= ingredients.size()) continue;
                    expected.add(new ExpectedGridSlot(inputSlots.get(gridIndex).index, ingredients.get(ingredientIndex), craftCount));
                    ingredientIndex++;
                }
            }
            return expected;
        }

        if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
            for (SlotDisplay slotDisplay : shapeless.ingredients()) {
                if (isEmptySlotDisplay(slotDisplay, params) || ingredientIndex >= ingredients.size() || ingredientIndex >= inputSlots.size()) continue;
                expected.add(new ExpectedGridSlot(inputSlots.get(ingredientIndex).index, ingredients.get(ingredientIndex), craftCount));
                ingredientIndex++;
            }
        }

        return expected;
    }

    private static boolean isEmptySlotDisplay(SlotDisplay slotDisplay, ContextMap params) {
        return slotDisplay == null || slotDisplay.resolveForStacks(params).stream().noneMatch(stack -> stack != null && !stack.isEmpty());
    }

    private static List<SlotDisplay> getIngredientDisplays(RecipeDisplayEntry entry) {
        if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        return List.of();
    }

    private static boolean shouldUseCraftAll(int phaseCrafts, int totalCraftsAvailable, int maxCraftsPerPhase) {
        return phaseCrafts > 0 && (phaseCrafts >= totalCraftsAvailable || phaseCrafts >= maxCraftsPerPhase);
    }

    private static boolean sameResult(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty() && left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
    }

    private static int countInventoryItems(Minecraft mc, ItemStack template) {
        if (mc.player == null || template == null || template.isEmpty()) return 0;
        int total = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == template.getItem()) total += stack.getCount();
        }
        return total;
    }

    private static boolean waitForCraftGridState(Minecraft mc, int syncId, List<ExpectedGridSlot> expectedSlots, ItemStack expectedResult, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
                if (craftingHandler.containerId != syncId) return false;
                if (matchesExpectedCraftGrid(craftingHandler, expectedSlots, expectedResult)) return true;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean matchesExpectedCraftGrid(AbstractCraftingMenu craftingHandler, List<ExpectedGridSlot> expectedSlots, ItemStack expectedResult) {
        if (expectedResult == null || expectedResult.isEmpty()) return false;
        ItemStack output = craftingHandler.getResultSlot().getItem();
        if (output.isEmpty() || output.getItem() != expectedResult.getItem()) return false;

        Set<Integer> expectedSlotIds = new HashSet<>();
        for (ExpectedGridSlot expectedSlot : expectedSlots) {
            expectedSlotIds.add(expectedSlot.slotId);
            Slot slot = craftingHandler.getSlot(expectedSlot.slotId);
            if (slot == null) return false;
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty() || stack.getCount() < expectedSlot.expectedCount) return false;
            if (expectedSlot.ingredient != null && !expectedSlot.ingredient.test(stack)) return false;
        }

        for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
            if (inputSlot == null || expectedSlotIds.contains(inputSlot.index)) continue;
            if (!inputSlot.getItem().isEmpty()) return false;
        }
        return true;
    }

    private static boolean waitForInventoryCount(Minecraft mc, ItemStack expectedResult, int requiredCount, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (countInventoryItems(mc, expectedResult) >= requiredCount) return true;
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static CraftExecutionResult buildCraftResult(CraftableRecipeOption option, int craftedAmount, int roundedTarget) {
        if (craftedAmount < roundedTarget) {
            return CraftExecutionResult.success("Crafted " + craftedAmount + "/" + roundedTarget + " " + option.label + ".", craftedAmount);
        }
        return CraftExecutionResult.success("Crafted " + craftedAmount + " " + option.label + ".", craftedAmount);
    }

    private static boolean runClientTask(Minecraft mc, Runnable task) {
        if (PackHideState.isHardLocked()) return false;
        if (mc.isSameThread()) {
            task.run();
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicReference<RuntimeException> runtimeError = new AtomicReference<>();

        mc.execute(() -> {
            try {
                task.run();
                ran.set(true);
            } catch (RuntimeException e) {
                runtimeError.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(2, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (runtimeError.get() != null) throw runtimeError.get();
        return ran.get();
    }

    public static List<CraftableRecipeOption> filterRecipes(List<CraftableRecipeOption> all, String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(all);
        String lower = query.toLowerCase();
        return all.stream().filter(o -> o.searchKey.contains(lower)).toList();
    }

    public static CraftableRecipeOption findInList(List<CraftableRecipeOption> recipes, String recipeKey, int recipeId) {
        if (recipes == null) return null;
        for (CraftableRecipeOption option : recipes) {
            if (!recipeKey.isBlank() && recipeKey.equalsIgnoreCase(option.recipeKey)) return option;
            if (recipeId >= 0 && (option.recipeId == recipeId || option.syncedRecipeId == recipeId)) return option;
        }
        return null;
    }
}
