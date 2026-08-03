package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.util.AutismConfig;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AutoArmorModule extends Module {

    private static final EquipmentSlot[] ARMOR_ORDER = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final Map<EquipmentSlot, Integer> ARMOR_MENU_SLOT = new EnumMap<>(EquipmentSlot.class);

    static {
        ARMOR_MENU_SLOT.put(EquipmentSlot.HEAD, 5);
        ARMOR_MENU_SLOT.put(EquipmentSlot.CHEST, 6);
        ARMOR_MENU_SLOT.put(EquipmentSlot.LEGS, 7);
        ARMOR_MENU_SLOT.put(EquipmentSlot.FEET, 8);
    }

    private static final float EXPECTED_DAMAGE = 6.0f;

    private static final ResourceKey<Enchantment>[] DR_ENCHANTS = keys(
        Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION,
        Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION);
    private static final float[] DR_ENCHANT_FACTOR = {1.2f, 0.4f, 0.39f, 0.38f};
    private static final float[] DR_ENCHANT_REDUCTION = {0.04f, 0.08f, 0.15f, 0.08f};

    private static final ResourceKey<Enchantment>[] OTHER_ENCHANTS = keys(
        Enchantments.FEATHER_FALLING, Enchantments.THORNS,
        Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.UNBREAKING);
    private static final float[] OTHER_ENCHANT_PER_LEVEL = {3.0f, 1.0f, 0.1f, 0.05f, 0.01f};

    private int cooldown;
    private int prevArmorValue = -1;
    private boolean openedInventory;

    AutoArmorModule() {
        super("auto-armor", "AutoArmor", ModuleCategory.COMBAT, "Equips your best armor.");

        add(new BoolSetting("prefer-elytra", "Prefer Elytra", false)
            .description("Wear elytra over chestplate.").build());
        add(new BoolSetting("allow-cursed", "Allow Cursed", false)
            .description("Equip curse-of-binding armor.").build());

        add(new IntSetting("start-delay", "Start Delay", 1, 0, 20, 1)
            .group("Constraints").description("Ticks before first move.").build());
        add(new IntSetting("click-delay", "Click Delay", 3, 0, 20, 1)
            .group("Constraints").description("Ticks between moves.").build());
        add(new IntSetting("close-delay", "Close Delay", 1, 0, 20, 1)
            .group("Constraints").description("Ticks before closing inventory.").build());
        add(new BoolSetting("no-movement", "No Movement", true)
            .group("Constraints").description("Only swap while still.").build());
        add(new BoolSetting("not-using-item", "Not Using Item", true)
            .group("Constraints").description("Not while using item.").build());

        add(new BoolSetting("hotbar", "Hotbar", true)
            .group("Hotbar").description("Equip via hotbar swap.").build());
        add(new BoolSetting("can-swap-armor", "Can Swap Armor", false)
            .group("Hotbar").visibleWhen(() -> bool("hotbar"))
            .description("Direct armor swap.").build());

        add(new BoolSetting("save-armor", "Save Armor", false)
            .group("Save Armor").description("Swap before armor breaks.").build());
        add(new IntSetting("durability-threshold", "Durability Threshold", 24, 0, 100, 1)
            .group("Save Armor").visibleWhen(() -> bool("save-armor"))
            .description("Save below this durability.").build());
        add(new BoolSetting("auto-open-inventory", "Auto Open Inventory", true)
            .group("Save Armor").visibleWhen(() -> bool("save-armor"))
            .description("Open inventory to save.").build());

        boolean firstInstall = !AutismConfig.getGlobal().modules.containsKey("auto-armor");
        if (firstInstall) setEnabledSilently(true);
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        prevArmorValue = -1;
        closeOpenedInventory();
    }

    @Override
    public void onGameLeft() {
        cooldown = 0;
        prevArmorValue = -1;
        openedInventory = false;
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null || MC.level == null) return;
        if (PackHideState.isHardLocked() || MC.player.isSpectator()) return;

        trackArmorBreak();

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (MC.player.containerMenu != MC.player.inventoryMenu) {
            handleForeignScreen();
            return;
        }

        if (bool("not-using-item") && MC.player.isUsingItem()) return;

        if (bool("no-movement") && isMoving()) {
            if (shouldOpenInventoryToSave()) {
                MC.gui.setScreen(new InventoryScreen(MC.player));
                openedInventory = true;
                cooldown = Math.max(1, integer("start-delay"));
            }
            return;
        }

        runOnce();
    }

    private boolean shouldOpenInventoryToSave() {

        return bool("save-armor") && bool("auto-open-inventory")
            && MC.gui.screen() == null && !isInvMoveActive() && hasLowArmorWithReplacement();
    }

    private static boolean isInvMoveActive() {
        Module module = ModuleRegistry.get("inv-move");
        return module != null && module.isEnabled();
    }

    private void runOnce() {
        int threshold = bool("save-armor") ? integer("durability-threshold") : Integer.MIN_VALUE;
        Map<EquipmentSlot, Candidate> best = findBestArmor(threshold);

        if (bool("prefer-elytra")) {
            Candidate elytra = preferredElytra();
            if (elytra != null) best.put(EquipmentSlot.CHEST, elytra);
        }

        for (EquipmentSlot slot : ARMOR_ORDER) {
            Candidate candidate = best.get(slot);
            if (candidate == null || candidate.kind == Kind.ARMOR) continue;

            ItemStack worn = MC.player.getItemBySlot(slot);

            if (!worn.isEmpty() && (worn.is(Items.ELYTRA) || hasCurseOfBinding(worn))) continue;

            if (equip(slot, candidate, worn)) {
                cooldown = Math.max(1, integer("click-delay"));
                return;
            }
        }

        if (openedInventory) {
            closeOpenedInventory();
            cooldown = Math.max(1, integer("close-delay"));
        } else {
            cooldown = Math.max(1, integer("click-delay"));
        }
    }

    private boolean equip(EquipmentSlot slot, Candidate candidate, ItemStack worn) {
        boolean occupied = !worn.isEmpty();
        int armorMenuSlot = ARMOR_MENU_SLOT.get(slot);
        boolean hotbarFast = candidate.kind == Kind.HOTBAR && bool("hotbar");

        if (!occupied) {

            if (hotbarFast) return click(armorMenuSlot, candidate.invIndex, ContainerInput.SWAP);
            return click(sourceMenuSlot(candidate), 0, ContainerInput.QUICK_MOVE);
        }

        if (hotbarFast && bool("can-swap-armor")) {
            return click(armorMenuSlot, candidate.invIndex, ContainerInput.SWAP);
        }
        if (MC.player.getInventory().getFreeSlot() >= 0) {
            return click(armorMenuSlot, 0, ContainerInput.QUICK_MOVE);
        }
        return click(armorMenuSlot, 1, ContainerInput.THROW);
    }

    private boolean click(int menuSlot, int button, ContainerInput input) {
        if (menuSlot < 0) return false;
        return AutismInventoryClickHelper.click(MC, menuSlot, button, input);
    }

    private int sourceMenuSlot(Candidate candidate) {
        return AutismInventoryHelper.toHandlerSlot(MC, candidate.invIndex);
    }

    private static Candidate preferredElytra() {
        ItemStack worn = MC.player.getItemBySlot(EquipmentSlot.CHEST);
        if (worn.is(Items.ELYTRA)) return new Candidate(worn, EquipmentSlot.CHEST, Kind.ARMOR, -1);
        for (int inv = 0; inv < 36; inv++) {
            ItemStack stack = MC.player.getInventory().getItem(inv);
            if (stack.is(Items.ELYTRA)) {
                return new Candidate(stack, EquipmentSlot.CHEST, inv < 9 ? Kind.HOTBAR : Kind.INVENTORY, inv);
            }
        }
        ItemStack offhand = MC.player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.is(Items.ELYTRA)) return new Candidate(offhand, EquipmentSlot.CHEST, Kind.INVENTORY, 40);
        return null;
    }

    private void trackArmorBreak() {
        if (!bool("save-armor")) {
            prevArmorValue = -1;
            return;
        }
        int current = MC.player.getArmorValue();
        boolean handledScreen = MC.gui.screen() instanceof AbstractContainerScreen<?>
            && !(MC.gui.screen() instanceof InventoryScreen);
        if (handledScreen && prevArmorValue >= 0 && current < prevArmorValue && bool("auto-open-inventory")) {
            Screen screen = MC.gui.screen();
            if (screen != null) screen.onClose();
        }
        prevArmorValue = current;
    }

    private void handleForeignScreen() {
        if (!bool("save-armor") || !bool("auto-open-inventory")) return;
        if (!hasLowArmorWithReplacement()) return;

        Screen screen = MC.gui.screen();
        if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)) {
            screen.onClose();
            cooldown = Math.max(1, integer("click-delay"));
        }
    }

    private boolean hasLowArmorWithReplacement() {
        int threshold = integer("durability-threshold");
        for (EquipmentSlot slot : ARMOR_ORDER) {
            ItemStack worn = MC.player.getItemBySlot(slot);

            if (worn.isEmpty() || !isPlayerArmor(worn) || durability(worn) > threshold || hasCurseOfBinding(worn)) continue;
            for (int inv = 0; inv < 36; inv++) {
                ItemStack stack = MC.player.getInventory().getItem(inv);
                if (stack.isEmpty() || !isPlayerArmor(stack)) continue;
                if (!bool("allow-cursed") && hasCurseOfBinding(stack)) continue;
                if (slot.equals(equipmentSlotOf(stack)) && durability(stack) > threshold) return true;
            }
        }
        return false;
    }

    private void closeOpenedInventory() {
        if (!openedInventory) return;
        openedInventory = false;
        if (MC != null && MC.gui.screen() instanceof InventoryScreen screen) screen.onClose();
    }

    private Map<EquipmentSlot, Candidate> findBestArmor(int threshold) {
        Map<EquipmentSlot, List<Candidate>> byType = gatherCandidates();

        Map<EquipmentSlot, Candidate> current = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, List<Candidate>> entry : byType.entrySet()) {
            current.put(entry.getKey(), maxBy(entry.getValue(),
                Comparator.comparingDouble(c -> armorToughness(c.stack, entry.getKey()))));
        }

        for (int pass = 0; pass < 2; pass++) {
            Map<EquipmentSlot, KitParam> kit = kitParamsExcludingSelf(current);
            Comparator<Candidate> comparator = armorComparator(kit, threshold);
            Map<EquipmentSlot, Candidate> next = new EnumMap<>(EquipmentSlot.class);
            for (Map.Entry<EquipmentSlot, List<Candidate>> entry : byType.entrySet()) {
                next.put(entry.getKey(), maxBy(entry.getValue(), comparator));
            }
            current = next;
        }
        return current;
    }

    private Map<EquipmentSlot, List<Candidate>> gatherCandidates() {
        Map<EquipmentSlot, List<Candidate>> byType = new EnumMap<>(EquipmentSlot.class);

        addCandidateRange(byType, 0, 9, Kind.HOTBAR);
        addCandidateRange(byType, 9, 36, Kind.INVENTORY);
        addCandidate(byType, MC.player.getItemBySlot(EquipmentSlot.OFFHAND), 40, Kind.INVENTORY);

        for (EquipmentSlot slot : ARMOR_ORDER) {
            addCandidate(byType, MC.player.getItemBySlot(slot), -1, Kind.ARMOR);
        }
        return byType;
    }

    private void addCandidateRange(Map<EquipmentSlot, List<Candidate>> byType, int from, int to, Kind kind) {
        for (int inv = from; inv < to; inv++) {
            addCandidate(byType, MC.player.getInventory().getItem(inv), inv, kind);
        }
    }

    private void addCandidate(Map<EquipmentSlot, List<Candidate>> byType, ItemStack stack, int invIndex, Kind kind) {
        if (stack == null || stack.isEmpty() || !isPlayerArmor(stack)) return;

        if (kind != Kind.ARMOR && !bool("allow-cursed") && hasCurseOfBinding(stack)) return;
        EquipmentSlot slot = equipmentSlotOf(stack);
        if (slot == null || ARMOR_MENU_SLOT.get(slot) == null) return;
        byType.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(new Candidate(stack, slot, kind, invIndex));
    }

    private static Map<EquipmentSlot, KitParam> kitParamsExcludingSelf(Map<EquipmentSlot, Candidate> current) {
        double totalDefense = 0;
        double totalToughness = 0;
        for (Map.Entry<EquipmentSlot, Candidate> entry : current.entrySet()) {
            Candidate candidate = entry.getValue();
            if (candidate == null) continue;
            totalDefense += armorValue(candidate.stack, entry.getKey());
            totalToughness += armorToughness(candidate.stack, entry.getKey());
        }
        Map<EquipmentSlot, KitParam> kit = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, Candidate> entry : current.entrySet()) {
            Candidate candidate = entry.getValue();
            double defense = candidate == null ? 0 : armorValue(candidate.stack, entry.getKey());
            double toughness = candidate == null ? 0 : armorToughness(candidate.stack, entry.getKey());
            kit.put(entry.getKey(), new KitParam(totalDefense - defense, totalToughness - toughness));
        }
        return kit;
    }

    private static Comparator<Candidate> armorComparator(Map<EquipmentSlot, KitParam> kit, int threshold) {
        Comparator<Candidate> byReduction =
            Comparator.comparingDouble(c -> round3(thresholdedDamageReduction(c, kit)));
        return Comparator
            .comparing((Candidate c) -> durability(c.stack) > threshold)
            .thenComparing(byReduction.reversed())
            .thenComparingDouble(c -> round3(enchantmentScore(c.stack)))
            .thenComparingInt(c -> enchantmentCount(c.stack))
            .thenComparingInt(c -> enchantability(c.stack))
            .thenComparing(c -> c.kind == Kind.ARMOR)
            .thenComparing(c -> c.kind == Kind.HOTBAR);
    }

    private static double thresholdedDamageReduction(Candidate candidate, Map<EquipmentSlot, KitParam> kit) {
        KitParam param = kit.getOrDefault(candidate.slot, KitParam.ZERO);
        double defense = param.defense + armorValue(candidate.stack, candidate.slot);
        double toughness = param.toughness + armorToughness(candidate.stack, candidate.slot);
        return damageFactor(EXPECTED_DAMAGE, defense, toughness) * (1.0 - enchantmentDamageReduction(candidate.stack));
    }

    private static double damageFactor(double damage, double defense, double toughness) {
        double f = 2.0 + toughness / 4.0;
        double g = clamp(defense - damage / f, defense * 0.2, 20.0);
        return 1.0 - g / 25.0;
    }

    private static double enchantmentDamageReduction(ItemStack stack) {
        double sum = 0;
        for (int i = 0; i < DR_ENCHANTS.length; i++) {
            sum += enchantLevel(stack, DR_ENCHANTS[i]) * DR_ENCHANT_FACTOR[i] * DR_ENCHANT_REDUCTION[i];
        }
        return sum;
    }

    private static double enchantmentScore(ItemStack stack) {
        double sum = 0;
        for (int i = 0; i < OTHER_ENCHANTS.length; i++) {
            sum += enchantLevel(stack, OTHER_ENCHANTS[i]) * OTHER_ENCHANT_PER_LEVEL[i];
        }
        return sum;
    }

    private static boolean isPlayerArmor(ItemStack stack) {
        Holder<Item> holder = stack.typeHolder();
        return holder.is(ItemTags.HEAD_ARMOR) || holder.is(ItemTags.CHEST_ARMOR)
            || holder.is(ItemTags.LEG_ARMOR) || holder.is(ItemTags.FOOT_ARMOR);
    }

    private static EquipmentSlot equipmentSlotOf(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    private static double armorValue(ItemStack stack, EquipmentSlot slot) {
        return attributeValue(stack, Attributes.ARMOR, slot);
    }

    private static double armorToughness(ItemStack stack, EquipmentSlot slot) {
        return attributeValue(stack, Attributes.ARMOR_TOUGHNESS, slot);
    }

    private static double attributeValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        Attribute value = attribute.value();
        double base = value.getDefaultValue();
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return value.sanitizeValue(base);
        return value.sanitizeValue(modifiers.compute(attribute, base, slot));
    }

    private static int durability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private static int enchantmentCount(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments == null ? 0 : enchantments.size();
    }

    private static int enchantability(ItemStack stack) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        return enchantable == null ? 0 : enchantable.value();
    }

    private static boolean hasCurseOfBinding(ItemStack stack) {
        return enchantLevel(stack, Enchantments.BINDING_CURSE) > 0;
    }

    private static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        try {
            Holder<Enchantment> holder = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isMoving() {
        Input input = MC.player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right() || input.jump();
    }

    private static Candidate maxBy(List<Candidate> candidates, Comparator<Candidate> comparator) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (best == null || comparator.compare(candidate, best) > 0) best = candidate;
        }
        return best;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @SafeVarargs
    private static ResourceKey<Enchantment>[] keys(ResourceKey<Enchantment>... keys) {
        return keys;
    }

    private enum Kind { HOTBAR, INVENTORY, ARMOR }

    private record Candidate(ItemStack stack, EquipmentSlot slot, Kind kind, int invIndex) {
    }

    private record KitParam(double defense, double toughness) {
        private static final KitParam ZERO = new KitParam(0, 0);
    }
}
