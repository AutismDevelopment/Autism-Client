package autismclient.util.multi;

import autismclient.util.macro.MacroCondition;

import java.util.Locale;
import java.util.Map;

final class MultiMacroConditions {
    private MultiMacroConditions() {
    }

    static boolean evaluate(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
        if (c == null) return true;
        boolean result = c.nodeType == MacroCondition.NodeType.GROUP ? group(c, h, vars) : leaf(c, h, vars);
        return c.negate != result;
    }

    private static boolean group(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
        if (c.children.isEmpty()) return true;
        if (c.combine == MacroCondition.Combine.ANY) {
            for (MacroCondition child : c.children) if (evaluate(child, h, vars)) return true;
            return false;
        }
        for (MacroCondition child : c.children) if (!evaluate(child, h, vars)) return false;
        return true;
    }

    private static boolean leaf(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
        String item = c.item == null ? "" : c.item;
        try {
            return switch (c.kind) {
                case ALWAYS -> true;
                case HELD_ITEM -> matchesName(h.heldItemName(), item, c.op);
                case INVENTORY_ITEM -> h.countItem(item) > 0;
                case ITEM_COUNT -> cmp(h.countItem(item), c.amount, c.cmp);
                case SLOT_EMPTY -> !h.slotFilled(c.slot);
                case SLOT_FILLED -> h.slotFilled(c.slot);
                case INVENTORY_FULL -> h.freeSlots() <= 0;
                case INVENTORY_EMPTY -> h.countItem("") <= 0;
                case CURSOR_EMPTY -> h.cursorEmpty();
                case CURSOR_FILLED -> !h.cursorEmpty();
                case CURSOR_MATCHES -> matchesName(h.cursorName(), item, c.op);
                case SELECTED_SLOT -> cmp(h.selectedHotbar(), c.slot, c.cmp);
                case FREE_SLOTS -> cmp(h.freeSlots(), c.amount, c.cmp);
                case GUI_OPEN -> item.isBlank() || item.equalsIgnoreCase("ANY")
                    ? h.containerOpen() : matchesName(h.openScreenTitle(), item, c.op);
                case HEALTH -> cmp(h.health(), c.amount, c.cmp);
                case CONNECTION -> h.macroReady();
                case VARIABLE -> variable(c, vars);
                case ENTITY_NEARBY, ENTITY_TARGET, LOOKING_AT_ENTITY, LOOKING_AT_CONTAINER_ENTITY ->
                    h.fullMode() && h.nearestEntity(item) >= 0;
                case DURABILITY -> durabilityLeaf(c, h);
                case HAS_BUNDLE -> h.countItem("bundle") > 0;
                case HAS_WRITABLE_BOOK -> h.countItem("writable_book") > 0;

                case LOOKING_AT_BLOCK, MOUNTED_ENTITY, BUNDLE_V2_READY -> false;
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean durabilityLeaf(MacroCondition c, MultiMacroHost h) {
        autismclient.util.macro.WaitDurabilityAction w = new autismclient.util.macro.WaitDurabilityAction();
        w.targetMode = c.durMode;
        w.itemName = c.item == null ? "" : c.item;
        w.slot = c.slot;
        w.measurement = c.durMeasure;
        w.comparison = durCmp(c.cmp);
        w.value = c.amount;
        return MultiMacroRun.durabilityMet(w, h);
    }

    private static autismclient.util.macro.WaitDurabilityAction.Comparison durCmp(MacroCondition.Cmp cmp) {
        return switch (cmp == null ? MacroCondition.Cmp.AT_LEAST : cmp) {
            case BELOW -> autismclient.util.macro.WaitDurabilityAction.Comparison.BELOW;
            case AT_MOST -> autismclient.util.macro.WaitDurabilityAction.Comparison.AT_MOST;
            case EXACT -> autismclient.util.macro.WaitDurabilityAction.Comparison.EXACT;
            case AT_LEAST -> autismclient.util.macro.WaitDurabilityAction.Comparison.AT_LEAST;
            case ABOVE -> autismclient.util.macro.WaitDurabilityAction.Comparison.ABOVE;
        };
    }

    static boolean cmp(double value, double amount, MacroCondition.Cmp cmp) {
        return switch (cmp == null ? MacroCondition.Cmp.AT_LEAST : cmp) {
            case BELOW -> value < amount;
            case AT_MOST -> value <= amount;
            case EXACT -> value == amount;
            case AT_LEAST -> value >= amount;
            case ABOVE -> value > amount;
        };
    }

    private static boolean matchesName(String actual, String expected, MacroCondition.Op op) {
        String a = normalizeItem(actual);
        String e = normalizeItem(stripId(expected));
        return switch (op == null ? MacroCondition.Op.EQ : op) {
            case NEQ -> !a.equals(e);
            case CONTAINS -> a.contains(e);
            case STARTS_WITH -> a.startsWith(e);
            case ENDS_WITH -> a.endsWith(e);
            case IS_EMPTY -> a.isEmpty();
            case REGEX -> {
                try {
                    yield a.matches(expected == null ? "" : expected);
                } catch (RuntimeException ex) {
                    yield false;
                }
            }
            default -> a.equals(e) || (!e.isEmpty() && a.contains(e));
        };
    }

    private static boolean variable(MacroCondition c, Map<String, String> vars) {
        String left = vars == null ? "" : vars.getOrDefault(stripBraces(c.item), "");
        String right = c.varB == null ? "" : c.varB;
        return switch (c.op == null ? MacroCondition.Op.EQ : c.op) {
            case NEQ -> !left.equals(right);
            case CONTAINS -> left.contains(right);
            case STARTS_WITH -> left.startsWith(right);
            case ENDS_WITH -> left.endsWith(right);
            case IS_EMPTY -> left.isEmpty();
            case IS_TRUE -> left.equalsIgnoreCase("true") || left.equals("1");
            case LT, LE, GT, GE -> numberCompare(left, right, c.op);
            case REGEX -> {
                try {
                    yield left.matches(right);
                } catch (RuntimeException ex) {
                    yield false;
                }
            }
            case EQ -> left.equals(right);
        };
    }

    private static boolean numberCompare(String left, String right, MacroCondition.Op op) {
        try {
            double l = Double.parseDouble(left.trim());
            double r = Double.parseDouble(right.trim());
            return switch (op) {
                case LT -> l < r;
                case LE -> l <= r;
                case GT -> l > r;
                case GE -> l >= r;
                default -> false;
            };
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String stripId(String s) {
        if (s == null) return "";
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    private static String normalizeItem(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    private static String stripBraces(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("{") && t.endsWith("}") && t.length() >= 2) return t.substring(1, t.length() - 1);
        return t;
    }
}
