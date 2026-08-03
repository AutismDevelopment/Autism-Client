package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PayAction implements MacroAction {
    public String commandTemplate = "/pay <player> <amount>";
    public String amountInput = "1";
    public int delayMs = 1000;
    public boolean delayEnabled = true;

    public boolean divideEnabled = false;

    public boolean probeHidden = true;
    public List<String> players = new ArrayList<>();
    private boolean enabled = true;

    public PayAction() {}

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("commandTemplate", commandTemplate);
        tag.putString("amountInput", amountInput);
        tag.putInt("delayMs", normalizeDelay(delayMs));
        tag.putBoolean("delayEnabled", delayEnabled);
        tag.putBoolean("divideEnabled", divideEnabled);
        tag.putBoolean("probeHidden", probeHidden);
        ListTag list = new ListTag();
        for (String player : players) {
            if (player != null && !player.isBlank()) list.add(StringTag.valueOf(player));
        }
        tag.put("players", list);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("commandTemplate")) commandTemplate = tag.getStringOr("commandTemplate", commandTemplate);
        if (tag.contains("amountInput")) amountInput = tag.getStringOr("amountInput", amountInput);
        if (tag.contains("delayMs")) delayMs = normalizeDelay(tag.getIntOr("delayMs", 1000));
        if (tag.contains("delayEnabled")) delayEnabled = tag.getBooleanOr("delayEnabled", true);
        if (tag.contains("divideEnabled")) divideEnabled = tag.getBooleanOr("divideEnabled", false);
        if (tag.contains("probeHidden")) probeHidden = tag.getBooleanOr("probeHidden", true);
        players.clear();
        if (tag.contains("players")) {
            ListTag list = tag.getList("players").orElse(new ListTag());
            for (Tag element : list) {
                String value = element.asString().orElse("").trim();
                if (!value.isEmpty() && !players.contains(value)) players.add(value);
            }
        }
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PAY;
    }

    @Override
    public String getDisplayName() {
        String target = players.isEmpty() ? "no players" : players.size() == 1 ? players.get(0) : players.size() + " players";
        String amount = amountInput == null || amountInput.isBlank() ? "?" : amountInput.trim();
        return "Pay " + target + (divideEnabled ? " split " : " ") + amount;
    }

    @Override
    public String getIcon() {
        return "$";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long resolvedAmount() {
        return parseAmount(amountInput);
    }

    public long totalAmount() {
        return resolvedAmount() * Math.max(0, players.size());
    }

    public static long[] distribute(long total, int count) {
        if (count <= 0) return new long[0];
        long safeTotal = Math.max(0L, total);
        long base = safeTotal / count;
        long remainder = safeTotal - base * count;
        long[] amounts = new long[count];
        for (int i = 0; i < count; i++) {
            amounts[i] = base + (i < remainder ? 1L : 0L);
        }
        return amounts;
    }

    public static int normalizeDelay(int delayMs) {
        return Math.max(0, delayMs);
    }

    public static long parseAmount(String input) {
        if (input == null) return 0L;
        String normalized = input.trim().replace(" ", "").replace("_", "");
        if (normalized.isEmpty()) return 0L;

        char suffix = Character.toUpperCase(normalized.charAt(normalized.length() - 1));
        BigDecimal multiplier = BigDecimal.ONE;
        if (suffix == 'K' || suffix == 'M' || suffix == 'B') {
            normalized = normalized.substring(0, normalized.length() - 1);
            multiplier = switch (suffix) {
                case 'K' -> new BigDecimal("1000");
                case 'M' -> new BigDecimal("1000000");
                case 'B' -> new BigDecimal("1000000000");
                default -> BigDecimal.ONE;
            };
        }

        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
        else if (normalized.contains(",")) normalized = normalized.replace(',', '.');

        if (normalized.isEmpty() || normalized.equals(".")) return 0L;

        try {
            BigDecimal value = new BigDecimal(normalized);
            return value.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String formatAmount(long amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        return format.format(amount);
    }
}
