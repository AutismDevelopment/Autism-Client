package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCraftingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CraftAction implements MacroAction {
    public static final class CraftEntry {
        public int recipeId = -1;
        public String recipeKey = "";
        public String resultName = "";
        public String resultNameJson = "";
        public int resultCount = 1;
        public int amount = 1;
        public boolean useMaxAmount = false;

        public CraftEntry copy() {
            CraftEntry copy = new CraftEntry();
            copy.recipeId = recipeId;
            copy.recipeKey = recipeKey;
            copy.resultName = resultName;
            copy.resultNameJson = resultNameJson;
            copy.resultCount = resultCount;
            copy.amount = amount;
            copy.useMaxAmount = useMaxAmount;
            return copy;
        }

        public boolean hasRecipe() {
            return (recipeId >= 0 || !recipeKey.isBlank()) && !resultName.isBlank();
        }

        public String getDisplayName() {
            if (!hasRecipe()) return "unset";
            return useMaxAmount
                ? resultName + " [Max]"
                : resultName + " x" + Math.max(1, amount);
        }

        public Component resultNameComponent() {
            Component rich = MacroExecutor.deserializeTextComponent(resultNameJson);
            if (rich != null && !rich.getString().isBlank()) return rich.copy();
            return Component.literal(resultName == null ? "" : resultName);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("recipeId", recipeId);
            tag.putString("recipeKey", recipeKey == null ? "" : recipeKey);
            tag.putString("resultName", resultName == null ? "" : resultName);
            if (resultNameJson != null && !resultNameJson.isBlank()) tag.putString("resultNameJson", resultNameJson);
            tag.putInt("resultCount", Math.max(1, resultCount));
            tag.putInt("amount", Math.max(1, amount));
            tag.putBoolean("useMaxAmount", useMaxAmount);
            return tag;
        }

        public static CraftEntry fromTag(CompoundTag tag) {
            CraftEntry entry = new CraftEntry();
            if (tag == null) return entry;
            entry.recipeId = tag.getIntOr("recipeId", -1);
            entry.recipeKey = tag.getStringOr("recipeKey", "");
            entry.resultName = tag.getStringOr("resultName", "");
            entry.resultNameJson = tag.getStringOr("resultNameJson", "");
            entry.resultCount = Math.max(1, tag.getIntOr("resultCount", 1));
            entry.amount = Math.max(1, tag.getIntOr("amount", 1));
            entry.useMaxAmount = tag.getBooleanOr("useMaxAmount", false);
            return entry;
        }

        public static CraftEntry fromOption(AutismCraftingHelper.CraftableRecipeOption option, int amount, boolean useMaxAmount) {
            CraftEntry entry = new CraftEntry();
            if (option == null) return entry;
            entry.recipeId = option.recipeId;
            entry.recipeKey = option.recipeKey;
            entry.resultName = option.label;
            entry.resultNameJson = MacroExecutor.serializeTextComponent(option.labelComponent);
            entry.resultCount = Math.max(1, option.result.getCount());
            entry.amount = Math.max(1, amount);
            entry.useMaxAmount = useMaxAmount;
            return entry;
        }
    }

    public final List<CraftEntry> entries = new ArrayList<>();

    public void clearEntries() {
        entries.clear();
    }

    public boolean hasEntries() {
        return entries.stream().anyMatch(CraftEntry::hasRecipe);
    }

    public List<CraftEntry> copyEntries() {
        List<CraftEntry> copies = new ArrayList<>(entries.size());
        for (CraftEntry entry : entries) {
            if (entry != null) copies.add(entry.copy());
        }
        return copies;
    }

    public void setEntries(List<CraftEntry> values) {
        entries.clear();
        if (values == null) return;
        for (CraftEntry entry : values) {
            if (entry != null) entries.add(entry.copy());
        }
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("No network connection!");
            return;
        }
        if (!hasEntries()) {
            AutismClientMessaging.sendPrefixed("No craft recipe selected!");
            return;
        }

        for (CraftEntry entry : entries) {
            if (entry == null || !entry.hasRecipe()) continue;

            AutismCraftingHelper.CraftableRecipeOption option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeKey);
            if (option == null) option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeId);
            if (option == null) {
                AutismClientMessaging.sendPrefixed("§cRecipe not found: " + entry.resultName);
                return;
            }

            int desiredAmount = AutismCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
            if (desiredAmount <= 0) {
                AutismClientMessaging.sendPrefixed("§cNo space or materials for " + entry.resultName + ".");
                return;
            }

            AutismCraftingHelper.CraftExecutionResult result = AutismCraftingHelper.executeCraftImmediately(
                mc,
                entry.recipeKey,
                entry.recipeId,
                desiredAmount
            );
            if (!result.success) {
                AutismClientMessaging.sendPrefixed("§c" + result.message);
                return;
            }
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "CRAFT");

        ListTag entryTags = new ListTag();
        for (CraftEntry entry : entries) {
            if (entry != null && entry.hasRecipe()) entryTags.add(entry.toTag());
        }
        tag.put("entries", entryTags);

        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        entries.clear();
        if (tag == null) return;

        if (tag.contains("entries")) {
            ListTag entryTags = tag.getList("entries").orElse(new ListTag());
            for (Tag element : entryTags) {
                if (element instanceof CompoundTag entryTag) {
                    CraftEntry entry = CraftEntry.fromTag(entryTag);
                    if (entry.hasRecipe()) entries.add(entry);
                }
            }
        }

        if (entries.isEmpty()) {
            CraftEntry legacyEntry = CraftEntry.fromTag(tag);
            if (legacyEntry.hasRecipe()) entries.add(legacyEntry);
        }
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.CRAFT;
    }

    @Override
    public String getDisplayName() {
        if (!hasEntries()) return "Craft (empty)";
        CraftEntry first = entries.get(0);
        if (entries.size() == 1) return "Craft " + first.getDisplayName();
        String firstName = first.resultName == null || first.resultName.isBlank() ? "item" : first.resultName;
        return "Craft " + firstName + " (+" + (entries.size() - 1) + ")";
    }

    @Override
    public String getIcon() {
        return "C";
    }
}
