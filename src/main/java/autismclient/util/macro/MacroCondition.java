package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class MacroCondition {

    public enum NodeType { GROUP, LEAF }
    public enum Combine { ALL, ANY }
    public enum Op { EQ, NEQ, LT, LE, GT, GE, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX, IS_EMPTY, IS_TRUE }
    public enum Cmp { BELOW, AT_MOST, EXACT, AT_LEAST, ABOVE }

    public enum Kind {
        ALWAYS,

        HELD_ITEM, INVENTORY_ITEM, ITEM_COUNT, SLOT_EMPTY, SLOT_FILLED,
        INVENTORY_FULL, INVENTORY_EMPTY, CURSOR_EMPTY, CURSOR_FILLED, CURSOR_MATCHES, SELECTED_SLOT,
        FREE_SLOTS, DURABILITY,

        GUI_OPEN,

        LOOKING_AT_ENTITY, LOOKING_AT_CONTAINER_ENTITY, MOUNTED_ENTITY, ENTITY_NEARBY, LOOKING_AT_BLOCK, ENTITY_TARGET,

        CONNECTION, HAS_BUNDLE, BUNDLE_V2_READY, HAS_WRITABLE_BOOK, HEALTH,

        VARIABLE
    }

    public NodeType nodeType = NodeType.GROUP;
    public boolean negate = false;

    public Combine combine = Combine.ALL;
    public List<MacroCondition> children = new ArrayList<>();

    public Kind kind = Kind.ALWAYS;
    public String item = "";
    public String varB = "";
    public Op op = Op.EQ;
    public Cmp cmp = Cmp.AT_LEAST;
    public int amount = 0;
    public int slot = 0;
    public double range = 5.0;
    public WaitDurabilityAction.TargetMode durMode = WaitDurabilityAction.TargetMode.HELD;
    public WaitDurabilityAction.Measurement durMeasure = WaitDurabilityAction.Measurement.PERCENT_REMAINING;
    public WaitFreeSlotsAction.CountMode freeMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;

    public static MacroCondition group(Combine c) {
        MacroCondition m = new MacroCondition();
        m.nodeType = NodeType.GROUP;
        m.combine = c;
        return m;
    }

    public static MacroCondition leaf(Kind k) {
        MacroCondition m = new MacroCondition();
        m.nodeType = NodeType.LEAF;
        m.kind = k;
        return m;
    }

    public static MacroCondition defaultRoot() {
        MacroCondition root = group(Combine.ALL);
        root.children.add(leaf(Kind.ALWAYS));
        return root;
    }

    public MacroCondition copy() {
        return fromTag(toTag());
    }

    public MacroCondition simpleLeafOrNull() {
        if (nodeType == NodeType.LEAF) return this;
        if (!negate && nodeType == NodeType.GROUP && children.size() == 1
                && children.get(0).nodeType == NodeType.LEAF) {
            return children.get(0);
        }
        return null;
    }

    public void writeFlat(CompoundTag t, String p) {
        MacroCondition leaf = simpleLeafOrNull();
        if (leaf == null) return;
        t.putString(p + "kind", leaf.kind.name());
        t.putBoolean(p + "negate", leaf.negate);
        t.putString(p + "item", leaf.item == null ? "" : leaf.item);
        t.putString(p + "op", leaf.op.name());
        t.putString(p + "value", leaf.varB == null ? "" : leaf.varB);
        t.putString(p + "cmp", leaf.cmp.name());
        t.putInt(p + "amount", leaf.amount);
        t.putInt(p + "slot", leaf.slot);
    }

    public static MacroCondition readForEditor(CompoundTag tag, String nestedKey, String flatPrefix) {
        MacroCondition nested = tag.get(nestedKey) instanceof CompoundTag c ? fromTag(c) : null;
        boolean nestedSimple = nested == null || nested.simpleLeafOrNull() != null;
        if (nestedSimple && tag.contains(flatPrefix + "kind")) {
            return readFlatLeaf(tag, flatPrefix);
        }
        return nested != null ? nested : defaultRoot();
    }

    public static MacroCondition readFlatLeaf(CompoundTag t, String p) {
        MacroCondition leaf = leaf(Kind.ALWAYS);
        leaf.kind = MacroStringList.enumValue(Kind.class, t.getStringOr(p + "kind", "ALWAYS"), Kind.ALWAYS);
        leaf.negate = t.getBooleanOr(p + "negate", false);
        leaf.item = t.getStringOr(p + "item", "");
        leaf.op = MacroStringList.enumValue(Op.class, t.getStringOr(p + "op", "EQ"), Op.EQ);
        leaf.varB = t.getStringOr(p + "value", "");
        leaf.cmp = MacroStringList.enumValue(Cmp.class, t.getStringOr(p + "cmp", "AT_LEAST"), Cmp.AT_LEAST);
        leaf.amount = t.getIntOr(p + "amount", 0);
        leaf.slot = t.getIntOr(p + "slot", 0);
        MacroCondition root = group(Combine.ALL);
        root.children.add(leaf);
        return root;
    }

    public boolean isAlways() {
        if (negate) return false;
        if (nodeType == NodeType.LEAF) return kind == Kind.ALWAYS;
        if (children.isEmpty()) return true;
        for (MacroCondition c : children) if (!c.isAlways()) return false;
        return true;
    }

    public boolean evaluate(Minecraft mc) {
        boolean result = nodeType == NodeType.GROUP ? evaluateGroup(mc) : evaluateLeaf(mc);
        return negate != result;
    }

    private boolean evaluateGroup(Minecraft mc) {
        if (children.isEmpty()) return true;
        if (combine == Combine.ANY) {
            for (MacroCondition c : children) if (c.evaluate(mc)) return true;
            return false;
        }
        for (MacroCondition c : children) if (!c.evaluate(mc)) return false;
        return true;
    }

    private boolean evaluateLeaf(Minecraft mc) {
        if (mc == null) return false;
        try {
            return switch (kind) {
                case ALWAYS -> true;
                case HELD_ITEM -> assertCheck(mc, AssertAction.CheckType.HELD_ITEM);
                case INVENTORY_ITEM -> assertCheck(mc, AssertAction.CheckType.INVENTORY_ITEM);
                case GUI_OPEN -> assertCheck(mc, AssertAction.CheckType.GUI_TYPE);
                case LOOKING_AT_ENTITY -> assertCheck(mc, AssertAction.CheckType.LOOKING_AT_ENTITY);
                case LOOKING_AT_CONTAINER_ENTITY -> assertCheck(mc, AssertAction.CheckType.LOOKING_AT_CONTAINER_ENTITY);
                case MOUNTED_ENTITY -> assertCheck(mc, AssertAction.CheckType.MOUNTED_ENTITY);
                case LOOKING_AT_BLOCK -> assertCheck(mc, AssertAction.CheckType.LOOKING_AT_BLOCK);
                case CONNECTION -> assertCheck(mc, AssertAction.CheckType.CONNECTION);
                case HAS_BUNDLE -> assertCheck(mc, AssertAction.CheckType.HAS_BUNDLE);
                case BUNDLE_V2_READY -> assertCheck(mc, AssertAction.CheckType.BUNDLE_V2_READY);
                case HAS_WRITABLE_BOOK -> assertCheck(mc, AssertAction.CheckType.HAS_WRITABLE_BOOK);
                case ENTITY_TARGET -> mc.crosshairPickEntity != null;
                case ITEM_COUNT -> compareNum(invCount(mc), amount);
                case SLOT_EMPTY -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SLOT_EMPTY);
                case SLOT_FILLED -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SLOT_FILLED);
                case INVENTORY_FULL -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.INVENTORY_FULL);
                case INVENTORY_EMPTY -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.INVENTORY_EMPTY);
                case CURSOR_EMPTY -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_EMPTY);
                case CURSOR_FILLED -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_FILLED);
                case CURSOR_MATCHES -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_MATCHES);
                case SELECTED_SLOT -> invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SELECTED_SLOT);
                case FREE_SLOTS -> freeSlots(mc);
                case DURABILITY -> durability(mc);
                case ENTITY_NEARBY -> entityNearby(mc);
                case HEALTH -> mc.player != null && compareNum(mc.player.getHealth(), amount);
                case VARIABLE -> variable(mc);
            };
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean assertCheck(Minecraft mc, AssertAction.CheckType type) {
        AssertAction a = new AssertAction();
        a.check = type;
        a.itemName = item == null ? "" : item;
        a.entityId = item == null ? "" : item;
        a.guiType = item == null || item.isBlank() ? "ANY" : item;
        return a.passes(mc);
    }

    private boolean invPredicate(Minecraft mc, WaitInventoryPredicateAction.InventoryCondition cond) {
        WaitInventoryPredicateAction w = new WaitInventoryPredicateAction();
        w.condition = cond;
        w.itemName = item == null ? "" : item;
        w.count = amount;
        w.slot = slot;
        return w.matches(mc);
    }

    private int invCount(Minecraft mc) {
        MacroTemplate.Resolution r = MacroVariables.resolve(item == null ? "" : item, mc);
        if (!r.success()) return 0;
        return WaitInventoryPredicateAction.countInventory(mc, ItemTarget.fromLegacyEntry(r.value()));
    }

    private boolean freeSlots(Minecraft mc) {
        WaitFreeSlotsAction w = new WaitFreeSlotsAction();
        w.countMode = freeMode;
        w.comparison = freeCmp();
        w.slots = amount;
        return w.matches(mc);
    }

    private boolean durability(Minecraft mc) {
        WaitDurabilityAction w = new WaitDurabilityAction();
        w.targetMode = durMode;
        w.itemName = item == null ? "" : item;
        w.slot = slot;
        w.measurement = durMeasure;
        w.comparison = durCmp();
        w.value = amount;
        return w.matches(mc);
    }

    private boolean entityNearby(Minecraft mc) {
        WaitEntityTargetAction w = new WaitEntityTargetAction();
        w.condition = WaitEntityTargetAction.EntityCondition.NEARBY;
        w.entityId = item == null ? "" : item;
        w.range = range;
        return w.matches(mc);
    }

    private boolean variable(Minecraft mc) {
        String a = resolveText(item, mc);
        if (op == Op.IS_EMPTY) return a.isBlank();
        if (op == Op.IS_TRUE) return parseTrue(a);
        String b = resolveText(varB, mc);
        Double da = tryNum(a);
        Double db = tryNum(b);
        if (da != null && db != null) {
            double x = da, y = db;
            return switch (op) {
                case EQ -> x == y;
                case NEQ -> x != y;
                case LT -> x < y;
                case LE -> x <= y;
                case GT -> x > y;
                case GE -> x >= y;
                case CONTAINS -> a.contains(b);
                case STARTS_WITH -> a.startsWith(b);
                case ENDS_WITH -> a.endsWith(b);
                case REGEX -> a.matches(b);
                default -> false;
            };
        }
        return switch (op) {
            case EQ -> a.equalsIgnoreCase(b);
            case NEQ -> !a.equalsIgnoreCase(b);
            case CONTAINS -> a.toLowerCase().contains(b.toLowerCase());
            case STARTS_WITH -> a.toLowerCase().startsWith(b.toLowerCase());
            case ENDS_WITH -> a.toLowerCase().endsWith(b.toLowerCase());
            case REGEX -> a.matches(b);
            case LT -> a.compareTo(b) < 0;
            case LE -> a.compareTo(b) <= 0;
            case GT -> a.compareTo(b) > 0;
            case GE -> a.compareTo(b) >= 0;
            default -> false;
        };
    }

    private boolean compareNum(double actual, double target) {
        return switch (cmp) {
            case BELOW -> actual < target;
            case AT_MOST -> actual <= target;
            case EXACT -> actual == target;
            case AT_LEAST -> actual >= target;
            case ABOVE -> actual > target;
        };
    }

    private WaitDurabilityAction.Comparison durCmp() {
        return switch (cmp) {
            case BELOW -> WaitDurabilityAction.Comparison.BELOW;
            case AT_MOST -> WaitDurabilityAction.Comparison.AT_MOST;
            case EXACT -> WaitDurabilityAction.Comparison.EXACT;
            case AT_LEAST -> WaitDurabilityAction.Comparison.AT_LEAST;
            case ABOVE -> WaitDurabilityAction.Comparison.ABOVE;
        };
    }

    private WaitFreeSlotsAction.Comparison freeCmp() {
        return switch (cmp) {
            case BELOW -> WaitFreeSlotsAction.Comparison.BELOW;
            case AT_MOST -> WaitFreeSlotsAction.Comparison.AT_MOST;
            case EXACT -> WaitFreeSlotsAction.Comparison.EXACT;
            case AT_LEAST -> WaitFreeSlotsAction.Comparison.AT_LEAST;
            case ABOVE -> WaitFreeSlotsAction.Comparison.ABOVE;
        };
    }

    private static String resolveText(String template, Minecraft mc) {
        MacroTemplate.Resolution r = MacroVariables.resolve(template == null ? "" : template, mc);
        return r.success() ? r.value() : "";
    }

    private static Double tryNum(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static boolean parseTrue(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on");
    }

    public String summary() {
        String s = nodeType == NodeType.GROUP ? groupSummary() : leafSummary();
        return negate ? "NOT(" + s + ")" : s;
    }

    private String groupSummary() {
        if (children.isEmpty()) return "always";
        StringBuilder sb = new StringBuilder();
        String join = combine == Combine.ANY ? " OR " : " AND ";
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) sb.append(join);
            sb.append(children.get(i).summary());
        }
        return children.size() > 1 ? "(" + sb + ")" : sb.toString();
    }

    private String leafSummary() {
        return switch (kind) {
            case ALWAYS -> "always";
            case HELD_ITEM -> "held=" + shortItem();
            case INVENTORY_ITEM -> "has " + shortItem();
            case ITEM_COUNT -> shortItem() + " " + cmpSym() + " " + amount;
            case GUI_OPEN -> "gui=" + (item.isBlank() ? "any" : item);
            case VARIABLE -> compactVar();
            case HEALTH -> "hp " + cmpSym() + " " + amount;
            case FREE_SLOTS -> (freeMode == WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? "filled" : "free") + " " + cmpSym() + " " + amount;
            case DURABILITY -> "dura " + cmpSym() + " " + amount;
            case ENTITY_NEARBY -> "near " + (item.isBlank() ? "entity" : item);
            default -> kind.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private String shortItem() {
        String s = item == null ? "" : item;
        int colon = s.indexOf(':');
        return colon >= 0 && colon + 1 < s.length() ? s.substring(colon + 1) : s;
    }

    private String compactVar() {
        String left = item == null ? "" : item;
        return switch (op) {
            case IS_EMPTY -> left + " empty";
            case IS_TRUE -> left + " true";
            default -> left + " " + opSym() + " " + varB;
        };
    }

    private String cmpSym() {
        return switch (cmp) {
            case BELOW -> "<";
            case AT_MOST -> "<=";
            case EXACT -> "=";
            case AT_LEAST -> ">=";
            case ABOVE -> ">";
        };
    }

    private String opSym() {
        return switch (op) {
            case EQ -> "=";
            case NEQ -> "!=";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case CONTAINS -> "has";
            case STARTS_WITH -> "starts";
            case ENDS_WITH -> "ends";
            case REGEX -> "regex";
            case IS_EMPTY -> "empty";
            case IS_TRUE -> "true";
        };
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("node", nodeType.name());
        t.putBoolean("negate", negate);
        if (nodeType == NodeType.GROUP) {
            t.putString("combine", combine.name());
            ListTag list = new ListTag();
            for (MacroCondition c : children) if (c != null) list.add(c.toTag());
            t.put("children", list);
        } else {
            t.putString("kind", kind.name());
            t.putString("item", item == null ? "" : item);
            t.putString("varB", varB == null ? "" : varB);
            t.putString("op", op.name());
            t.putString("cmp", cmp.name());
            t.putInt("amount", amount);
            t.putInt("slot", slot);
            t.putDouble("range", range);
            t.putString("durMode", durMode.name());
            t.putString("durMeasure", durMeasure.name());
            t.putString("freeMode", freeMode.name());
        }
        return t;
    }

    public static MacroCondition fromTag(CompoundTag t) {
        MacroCondition m = new MacroCondition();
        if (t == null) return defaultRoot();
        m.nodeType = MacroStringList.enumValue(NodeType.class, t.getStringOr("node", "GROUP"), NodeType.GROUP);
        m.negate = t.getBooleanOr("negate", false);
        if (m.nodeType == NodeType.GROUP) {
            m.combine = MacroStringList.enumValue(Combine.class, t.getStringOr("combine", "ALL"), Combine.ALL);
            m.children = new ArrayList<>();
            if (t.get("children") instanceof ListTag list) {
                for (Tag el : list) if (el instanceof CompoundTag ct) m.children.add(fromTag(ct));
            }
        } else {
            m.kind = MacroStringList.enumValue(Kind.class, t.getStringOr("kind", "ALWAYS"), Kind.ALWAYS);
            m.item = t.getStringOr("item", "");
            m.varB = t.getStringOr("varB", "");
            m.op = MacroStringList.enumValue(Op.class, t.getStringOr("op", "EQ"), Op.EQ);
            m.cmp = MacroStringList.enumValue(Cmp.class, t.getStringOr("cmp", "AT_LEAST"), Cmp.AT_LEAST);
            m.amount = t.getIntOr("amount", 0);
            m.slot = t.getIntOr("slot", 0);
            m.range = t.getDoubleOr("range", 5.0);
            m.durMode = MacroStringList.enumValue(WaitDurabilityAction.TargetMode.class, t.getStringOr("durMode", "HELD"), WaitDurabilityAction.TargetMode.HELD);
            m.durMeasure = MacroStringList.enumValue(WaitDurabilityAction.Measurement.class, t.getStringOr("durMeasure", "PERCENT_REMAINING"), WaitDurabilityAction.Measurement.PERCENT_REMAINING);
            m.freeMode = MacroStringList.enumValue(WaitFreeSlotsAction.CountMode.class, t.getStringOr("freeMode", "FREE_SLOTS"), WaitFreeSlotsAction.CountMode.FREE_SLOTS);
        }
        return m;
    }
}
