package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

public final class AutismAutoTool {
    private AutismAutoTool() {
    }

    public static float destroySpeed(Minecraft mc, ItemStack stack, BlockState state) {
        if (stack == null || stack.isEmpty() || state == null) return 0f;
        float speed;
        try {
            speed = stack.getDestroySpeed(state);
        } catch (Throwable t) {
            return 0f;
        }

        if (speed <= 1.0f && stack.getItem() == net.minecraft.world.item.Items.SHEARS) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if ("cobweb".equals(path) || path.endsWith("_leaves")) speed = 15.0f;
            else if (path.endsWith("_wool")) speed = 5.0f;
        }
        if (speed > 1f) {
            int efficiency = efficiencyLevel(mc, stack);
            if (efficiency > 0) {
                float addition = (float) efficiency * efficiency + 1f;
                speed += Math.max(0f, Math.min(1024f, addition));
            }
        }
        return speed;
    }

    private static int efficiencyLevel(Minecraft mc, ItemStack stack) {
        return enchantLevel(mc, stack, Enchantments.EFFICIENCY);
    }

    public static boolean hasSilkTouch(Minecraft mc, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return enchantLevel(mc, stack, Enchantments.SILK_TOUCH) > 0;
    }

    private static int enchantLevel(Minecraft mc, ItemStack stack, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        try {
            if (mc == null || mc.level == null) return 0;
            var holder = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static float heldDestroySpeed(Minecraft mc, BlockState state) {
        if (mc == null || mc.player == null) return 0f;
        return destroySpeed(mc, mc.player.getMainHandItem(), state);
    }

    public static int bestToolSlot(Minecraft mc, BlockState state, int limit, boolean ignoreDurability) {
        return bestToolSlot(mc, state, limit, ignoreDurability, false);
    }

    public static int bestToolSlot(Minecraft mc, BlockState state, int limit, boolean ignoreDurability, boolean requireSilkTouch) {
        if (mc == null || mc.player == null || state == null) return -1;
        int best = -1;
        float bestSpeed = -1f;
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (!ignoreDurability && stack.isDamageableItem()
                && (stack.getMaxDamage() - stack.getDamageValue()) <= 2) {
                continue;
            }
            float speed = destroySpeed(mc, stack, state);

            if (speed <= 1.0f) continue;
            if (requireSilkTouch && !hasSilkTouch(mc, stack)) continue;
            boolean better = speed > bestSpeed
                || (speed == bestSpeed && best >= 0 && nearness(slot, selected) < nearness(best, selected));
            if (better) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private static int nearness(int slot, int selected) {
        return slot < 9 ? Math.abs(slot - selected) : 100 + slot;
    }

    public static boolean equipBestTool(Minecraft mc, BlockState state, boolean considerInventory, boolean ignoreDurability) {
        return equipBestTool(mc, state, considerInventory, ignoreDurability, false);
    }

    public static boolean equipBestTool(Minecraft mc, BlockState state, boolean considerInventory, boolean ignoreDurability, boolean preferSilkTouch) {
        if (mc == null || mc.player == null || state == null) return false;
        if (mc.player.hasInfiniteMaterials()) return true;

        int limit = considerInventory ? 36 : 9;
        int best = -1;
        if (preferSilkTouch) best = bestToolSlot(mc, state, limit, ignoreDurability, true);
        if (best < 0) best = bestToolSlot(mc, state, limit, ignoreDurability, false);
        if (best < 0) return false;

        int selected = mc.player.getInventory().getSelectedSlot();
        ItemStack bestStack = mc.player.getInventory().getItem(best);
        ItemStack heldStack = mc.player.getInventory().getItem(selected);
        float bestSpeed = destroySpeed(mc, bestStack, state);
        float heldSpeed = destroySpeed(mc, heldStack, state);

        boolean silkChosen = preferSilkTouch && bestSpeed > 1f && hasSilkTouch(mc, bestStack);
        if (silkChosen) {

            if (heldSpeed > 1f && hasSilkTouch(mc, heldStack)) return true;
        } else if (bestSpeed <= heldSpeed) {

            return true;
        }

        if (best < 9) {
            AutismInventoryHelper.selectHotbarSlot(mc, best);
            return true;
        }
        if (!AutismInventoryHelper.swapInventorySlots(mc, best, selected)) return false;
        AutismInventoryHelper.selectHotbarSlot(mc, selected);
        return true;
    }
}
