package autismclient.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AutismLocalCraftingRegistry {
    private static final Object LOCK = new Object();
    private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.registry(Registries.RECIPE);

    private static ResourceManager cachedResourceManager;
    private static RegistryAccess cachedRegistryAccess;
    private static FeatureFlagSet cachedEnabledFeatures;
    private static int cachedModCount = -1;
    private static List<LocalCraftingRecipe> cachedRecipes = List.of();

    private AutismLocalCraftingRegistry() {
    }

    static List<LocalCraftingRecipe> getRecipes(Minecraft mc) {
        if (mc == null || mc.level == null) return List.of();

        ResourceManager resourceManager = mc.getResourceManager();
        RegistryAccess registryAccess = mc.getConnection() != null ? mc.getConnection().registryAccess() : mc.level.registryAccess();
        FeatureFlagSet enabledFeatures = mc.getConnection() != null ? mc.getConnection().enabledFeatures() : mc.level.enabledFeatures();
        ContextMap slotContext = SlotDisplayContext.fromLevel(mc.level);
        int modCount = FabricLoader.getInstance().getAllMods().size();
        return getRecipes(resourceManager, registryAccess, enabledFeatures, slotContext, modCount);
    }

    static List<LocalCraftingRecipe> getRecipes(
        ResourceManager resourceManager,
        RegistryAccess registryAccess,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext,
        int modCount
    ) {
        if (resourceManager == null || registryAccess == null) return List.of();

        synchronized (LOCK) {
            if (cachedResourceManager == resourceManager
                && cachedRegistryAccess == registryAccess
                && cachedEnabledFeatures == enabledFeatures
                && cachedModCount == modCount
                && !cachedRecipes.isEmpty()) {
                return cachedRecipes;
            }

            cachedRecipes = loadRecipes(resourceManager, registryAccess, enabledFeatures, slotContext);
            cachedResourceManager = resourceManager;
            cachedRegistryAccess = registryAccess;
            cachedEnabledFeatures = enabledFeatures;
            cachedModCount = modCount;
            return cachedRecipes;
        }
    }

    static final class LocalCraftingRecipe {
        final String recipeKey;
        final ItemStack result;
        final int width;
        final int height;
        final boolean shaped;
        final List<Ingredient> gridIngredients;
        final List<Ingredient> requirements;
        final String signature;

        LocalCraftingRecipe(
            String recipeKey,
            ItemStack result,
            int width,
            int height,
            boolean shaped,
            List<Ingredient> gridIngredients,
            List<Ingredient> requirements,
            String signature
        ) {
            this.recipeKey = recipeKey;
            this.result = result.copy();
            this.width = width;
            this.height = height;
            this.shaped = shaped;
            this.gridIngredients = immutableListAllowingNulls(gridIngredients);
            this.requirements = immutableListAllowingNulls(requirements);
            this.signature = signature;
        }

        boolean fits(int gridWidth, int gridHeight) {
            if (shaped) return width <= gridWidth && height <= gridHeight;
            return requirements.size() <= gridWidth * gridHeight;
        }
    }

    private static <T> List<T> immutableListAllowingNulls(List<T> values) {
        if (values == null || values.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    static LocalCraftingRecipe createFromSyncedDisplay(
        String recipeKey,
        RecipeDisplay display,
        Optional<List<Ingredient>> craftingRequirements,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext
    ) {
        if (recipeKey == null || recipeKey.isBlank() || display == null || slotContext == null) return null;
        if (!display.isEnabled(enabledFeatures)) return null;

        ItemStack result = display.result().resolveForFirstStack(slotContext);
        if (result == null || result.isEmpty() || !result.isItemEnabled(enabledFeatures)) return null;

        boolean shaped = display instanceof ShapedCraftingRecipeDisplay;
        if (!shaped && !(display instanceof ShapelessCraftingRecipeDisplay)) return null;

        List<Ingredient> explicitRequirements = craftingRequirements != null && craftingRequirements.isPresent()
            ? presentIngredients(craftingRequirements.get())
            : List.of();

        int width;
        int height;
        List<Ingredient> gridIngredients;
        List<Ingredient> requirements;

        if (display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
            width = shapedDisplay.width();
            height = shapedDisplay.height();
            gridIngredients = ingredientsFromDisplays(shapedDisplay.ingredients(), slotContext);
            requirements = explicitRequirements.isEmpty() ? presentIngredients(gridIngredients) : explicitRequirements;
        } else {
            List<SlotDisplay> displays = ingredientDisplays(display);
            width = Math.max(1, displays.size());
            height = 1;
            requirements = explicitRequirements.isEmpty() ? presentIngredients(ingredientsFromDisplays(displays, slotContext)) : explicitRequirements;
            gridIngredients = new ArrayList<>(requirements);
        }

        if (width <= 0 || height <= 0 || requirements.isEmpty()) return null;
        if (requirements.stream().anyMatch(ingredient -> ingredient == null || ingredient.isEmpty())) return null;

        String signature = buildSignature(result, shaped, width, height, signatureParts(shaped, gridIngredients, requirements, slotContext));
        return new LocalCraftingRecipe(recipeKey, result, width, height, shaped, gridIngredients, requirements, signature);
    }

    private static List<LocalCraftingRecipe> loadRecipes(
        ResourceManager resourceManager,
        RegistryAccess registryAccess,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext
    ) {
        if (resourceManager == null || registryAccess == null || slotContext == null) return List.of();

        LinkedHashMap<String, LocalCraftingRecipe> byKey = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = RECIPE_LISTER.listMatchingResources(resourceManager);
        if (!resources.isEmpty()) {
            List<Map.Entry<Identifier, Resource>> ordered = new ArrayList<>(resources.entrySet());
            ordered.sort(Comparator.comparing(entry -> entry.getKey().toString(), String.CASE_INSENSITIVE_ORDER));
            for (Map.Entry<Identifier, Resource> entry : ordered) {
                Identifier recipeId = RECIPE_LISTER.fileToId(entry.getKey());
                for (LocalCraftingRecipe recipe : parseRecipe(recipeId, entry.getValue(), registryAccess, enabledFeatures, slotContext)) {
                    byKey.put(recipe.recipeKey, recipe);
                }
            }
        }

        List<LocalCraftingRecipe> recipes = new ArrayList<>(byKey.values());
        recipes.sort(Comparator.comparing(recipe -> recipe.recipeKey, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(recipes);
    }

    private static List<LocalCraftingRecipe> parseRecipe(
        Identifier recipeId,
        Resource resource,
        RegistryAccess registryAccess,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext
    ) {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Recipe<?> decoded = Recipe.CODEC
                .parse(registryAccess.createSerializationContext(JsonOps.INSTANCE), root)
                .getOrThrow(JsonParseException::new);
            if (!(decoded instanceof CraftingRecipe craftingRecipe)) return List.of();
            return createLocalRecipes(recipeId, craftingRecipe, enabledFeatures, slotContext);
        } catch (IOException | IllegalStateException | JsonParseException ignored) {
            return List.of();
        }
    }

    private static List<LocalCraftingRecipe> createLocalRecipes(
        Identifier recipeId,
        CraftingRecipe recipe,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext
    ) {
        if (recipe == null || recipe.isSpecial() || recipe.placementInfo().isImpossibleToPlace()) return List.of();

        List<LocalCraftingRecipe> result = new ArrayList<>();
        List<RecipeDisplay> displays = recipe.display();
        for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
            RecipeDisplay display = displays.get(displayIndex);
            LocalCraftingRecipe localRecipe = createLocalRecipe(recipeId, recipe, display, displayIndex, enabledFeatures, slotContext);
            if (localRecipe != null) {
                result.add(localRecipe);
            }
        }
        return result;
    }

    private static LocalCraftingRecipe createLocalRecipe(
        Identifier recipeId,
        CraftingRecipe recipe,
        RecipeDisplay display,
        int displayIndex,
        FeatureFlagSet enabledFeatures,
        ContextMap slotContext
    ) {
        if (display == null || !display.isEnabled(enabledFeatures)) return null;

        ItemStack result = display.result().resolveForFirstStack(slotContext);
        if (result == null || result.isEmpty() || !result.isItemEnabled(enabledFeatures)) return null;

        boolean shaped = display instanceof ShapedCraftingRecipeDisplay;
        if (!shaped && !(display instanceof ShapelessCraftingRecipeDisplay)) return null;

        int width;
        int height;
        List<Ingredient> gridIngredients;
        List<Ingredient> requirements;

        if (recipe instanceof ShapedRecipe shapedRecipe && display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
            width = shapedRecipe.getWidth();
            height = shapedRecipe.getHeight();
            gridIngredients = toGridIngredients(shapedRecipe.getIngredients(), width * height);
            requirements = presentIngredients(gridIngredients);
            if (width != shapedDisplay.width() || height != shapedDisplay.height()) {
                width = shapedDisplay.width();
                height = shapedDisplay.height();
                gridIngredients = ingredientsFromDisplays(shapedDisplay.ingredients(), slotContext);
                requirements = presentIngredients(gridIngredients);
            }
        } else if (display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
            width = shapedDisplay.width();
            height = shapedDisplay.height();
            gridIngredients = ingredientsFromDisplays(shapedDisplay.ingredients(), slotContext);
            requirements = presentIngredients(gridIngredients);
        } else {
            width = recipe instanceof ShapelessRecipe ? recipe.placementInfo().ingredients().size() : ingredientDisplays(display).size();
            height = 1;
            requirements = shapelessRequirements(recipe, display, slotContext);
            gridIngredients = new ArrayList<>(requirements);
        }

        if (width <= 0 || height <= 0 || requirements.isEmpty()) return null;
        if (requirements.stream().anyMatch(ingredient -> ingredient == null || ingredient.isEmpty())) return null;

        String recipeKey = recipeId.toString();
        if (displayIndex > 0) {
            recipeKey += "#" + displayIndex;
        }
        String signature = buildSignature(result, shaped, width, height, signatureParts(shaped, gridIngredients, requirements, slotContext));
        return new LocalCraftingRecipe(recipeKey, result, width, height, shaped, gridIngredients, requirements, signature);
    }

    private static List<Ingredient> toGridIngredients(List<Optional<Ingredient>> ingredients, int size) {
        List<Ingredient> grid = new ArrayList<>(Math.max(size, ingredients == null ? 0 : ingredients.size()));
        if (ingredients != null) {
            for (Optional<Ingredient> ingredient : ingredients) {
                grid.add(ingredient.orElse(null));
            }
        }
        while (grid.size() < size) {
            grid.add(null);
        }
        return grid;
    }

    private static List<Ingredient> ingredientsFromDisplays(List<SlotDisplay> displays, ContextMap slotContext) {
        List<Ingredient> ingredients = new ArrayList<>(displays == null ? 0 : displays.size());
        if (displays == null) return ingredients;
        for (SlotDisplay display : displays) {
            ingredients.add(ingredientFromDisplay(display, slotContext));
        }
        return ingredients;
    }

    private static Ingredient ingredientFromDisplay(SlotDisplay display, ContextMap slotContext) {
        if (display == null) return null;
        List<ItemStack> stacks = display.resolveForStacks(slotContext);
        if (stacks == null || stacks.isEmpty()) return null;

        LinkedHashMap<Identifier, Item> items = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getItem() == null) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                items.put(id, stack.getItem());
            }
        }
        if (items.isEmpty()) return null;
        Ingredient ingredient = Ingredient.of(items.values().stream());
        return ingredient.isEmpty() ? null : ingredient;
    }

    private static List<Ingredient> shapelessRequirements(CraftingRecipe recipe, RecipeDisplay display, ContextMap slotContext) {
        PlacementInfo placement = recipe.placementInfo();
        if (placement != null && !placement.ingredients().isEmpty()) {
            return new ArrayList<>(placement.ingredients());
        }
        return presentIngredients(ingredientsFromDisplays(ingredientDisplays(display), slotContext));
    }

    private static List<SlotDisplay> ingredientDisplays(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        return List.of();
    }

    private static List<Ingredient> presentIngredients(List<Ingredient> ingredients) {
        List<Ingredient> present = new ArrayList<>();
        if (ingredients == null) return present;
        for (Ingredient ingredient : ingredients) {
            if (ingredient != null && !ingredient.isEmpty()) {
                present.add(ingredient);
            }
        }
        return present;
    }

    private static List<String> signatureParts(
        boolean shaped,
        List<Ingredient> gridIngredients,
        List<Ingredient> requirements,
        ContextMap slotContext
    ) {
        List<String> parts = new ArrayList<>();
        if (shaped) {
            if (gridIngredients != null) {
                for (Ingredient ingredient : gridIngredients) {
                    parts.add(signatureForIngredient(ingredient, slotContext));
                }
            }
        } else if (requirements != null) {
            for (Ingredient ingredient : requirements) {
                parts.add(signatureForIngredient(ingredient, slotContext));
            }
            parts.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return parts;
    }

    private static String signatureForIngredient(Ingredient ingredient, ContextMap slotContext) {
        if (ingredient == null || ingredient.isEmpty()) return "_";
        return signatureForStacks(ingredient.display().resolveForStacks(slotContext));
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

    private static String buildSignature(ItemStack result, boolean shaped, int width, int height, Collection<String> parts) {
        Identifier resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        String resultKey = resultId == null ? result.getHoverName().getString() : resultId.toString();
        return resultKey + "|" + result.getCount() + "|" + (shaped ? "shaped" : "shapeless") + "|" + width + "x" + height + "|" + String.join(";", parts);
    }
}
