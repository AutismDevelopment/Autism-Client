package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SelectSlotAction implements MacroAction {
    public enum Strategy { FIRST_MATCH, BEST_DURABILITY, WORST_DURABILITY, LARGEST_STACK, EMPTY_SLOT }

    public int slot = 0;
    public String itemName = "";
    public ItemTarget itemTarget = new ItemTarget();
    public Strategy strategy = Strategy.FIRST_MATCH;
    public String outputVariable = "";

    public SelectSlotAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null) return;

        java.util.List<ItemTarget> targets = new java.util.ArrayList<>();
        for (ItemTarget candidate : resolvedTargets()) {
            ItemTarget resolved = candidate.resolveTemplate(mc);
            if (resolved != null) targets.add(resolved);
        }
        if (strategy == Strategy.EMPTY_SLOT) {
            int empty = findEmptyHotbar(mc);
            if (empty >= 0) {
                AutismInventoryHelper.selectHotbarSlot(mc, empty);
                MacroVariables.set(outputVariable, empty);
                return;
            }
        }

        if (!targets.isEmpty()) {
            if (strategy != Strategy.FIRST_MATCH) {
                int best = findStrategicHotbarSlot(mc, targets);
                if (best >= 0) {
                    AutismInventoryHelper.selectHotbarSlot(mc, best);
                    MacroVariables.set(outputVariable, best);
                    return;
                }
            }
            for (ItemTarget target : targets) {
                int selectedSlot = AutismInventoryHelper.selectHotbarItem(mc, target, slot);
                if (selectedSlot >= 0) {
                    MacroVariables.set(outputVariable, selectedSlot);
                    return;
                }
            }
        }

        int actualSlot = Math.max(0, Math.min(8, slot));
        AutismInventoryHelper.selectHotbarSlot(mc, actualSlot);
        MacroVariables.set(outputVariable, actualSlot);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.SELECT_SLOT;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SELECT_SLOT");
        tag.putInt("slot", slot);
        tag.putString("strategy", strategy.name());
        tag.putString("outputVariable", outputVariable);
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) tag.put("itemName", target.toTag());
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        this.slot = tag.getIntOr("slot", 0);
        this.strategy = MacroStringList.enumValue(Strategy.class, tag.getStringOr("strategy", "FIRST_MATCH"), Strategy.FIRST_MATCH);
        this.outputVariable = tag.getStringOr("outputVariable", "");
        this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
        this.itemName = itemTarget.toLegacyEntry();
    }

    @Override
    public String getDisplayName() {
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) {
            return "Select " + strategy + " \"" + target.summaryText() + "\"";
        }
        return "Select Slot " + (slot + 1);
    }

    @Override
    public String getIcon() {
        return "S";
    }

    private ItemTarget resolvedItemTarget() {
        if (itemTarget != null && (itemTarget.hasSlot() || itemTarget.hasIdentity())) return itemTarget;
        itemTarget = ItemTarget.fromLegacyEntry(shortcut(itemName));
        return itemTarget;
    }

    private String shortcut(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case "bundle" -> "minecraft:bundle";
            case "writable book", "book" -> "minecraft:writable_book";
            case "pickaxe" -> "#minecraft:pickaxes";
            case "trident or bow" -> "minecraft:trident|minecraft:bow";
            case "chest boat/minecart", "chest boat" -> "minecraft:chest_minecart";
            default -> raw;
        };
    }

    private int findEmptyHotbar(Minecraft mc) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }

    private java.util.List<ItemTarget> resolvedTargets() {
        String raw = shortcut(itemName);
        if (raw == null || raw.isBlank()) {
            ItemTarget target = resolvedItemTarget();
            return target.hasSlot() || target.hasIdentity() ? java.util.List.of(target) : java.util.List.of();
        }
        if (!raw.contains("|")) {
            ItemTarget target = resolvedItemTarget();
            return target.hasSlot() || target.hasIdentity() ? java.util.List.of(target) : java.util.List.of();
        }
        java.util.ArrayList<ItemTarget> out = new java.util.ArrayList<>();
        for (String part : raw.split("\\|")) {
            ItemTarget target = ItemTarget.fromLegacyEntry(part.trim());
            if (target.hasSlot() || target.hasIdentity()) out.add(target);
        }
        return out;
    }

    private int findStrategicHotbarSlot(Minecraft mc, java.util.List<ItemTarget> targets) {
        int bestSlot = -1;
        int bestMetric = strategy == Strategy.WORST_DURABILITY ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            boolean matches = false;
            for (ItemTarget target : targets) {
                if (target.score(stack, i) >= 0) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;
            int metric = switch (strategy) {
                case BEST_DURABILITY -> stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
                case WORST_DURABILITY -> stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
                case LARGEST_STACK -> stack.getCount();
                default -> 0;
            };
            if ((strategy == Strategy.WORST_DURABILITY && metric < bestMetric)
                || (strategy != Strategy.WORST_DURABILITY && metric > bestMetric)) {
                bestMetric = metric;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
