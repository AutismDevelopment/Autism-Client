package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MultiAction implements MacroAction {
    public static final String WAIT_UNTIL_READY = "Until ready";
    public static final String WAIT_FIXED_DELAY = "Fixed delay";
    public static final String WAIT_NO_WAIT = "No wait";

    public LinkedHashSet<String> accountIds = new LinkedHashSet<>();
    public ArrayList<String> accounts = new ArrayList<>();
    public int stepCount = 1;
    public boolean connectIfDown = true;
    public boolean disconnectAfter = false;
    public String waitMode = WAIT_UNTIL_READY;
    public int waitMs = 30_000;

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.put("accounts", MacroStringList.toTag(effectiveAccounts()));
        tag.putInt("stepCount", Math.max(0, stepCount));
        tag.putBoolean("connectIfDown", connectIfDown);
        tag.putBoolean("disconnectAfter", disconnectAfter);
        tag.putString("waitMode", canonicalWaitMode(waitMode));
        tag.putInt("waitMs", Math.max(0, waitMs));
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        accounts = MacroStringList.fromTag(tag.getList("accounts").orElse(new ListTag()));
        accounts.removeIf(s -> s == null || s.isBlank());
        accountIds = new LinkedHashSet<>();
        for (String id : accounts) accountIds.add(id.trim());
        stepCount = Math.max(0, tag.getIntOr("stepCount", 1));
        connectIfDown = tag.getBooleanOr("connectIfDown", true);
        disconnectAfter = tag.getBooleanOr("disconnectAfter", false);
        waitMode = canonicalWaitMode(tag.getStringOr("waitMode", WAIT_UNTIL_READY));
        waitMs = Math.max(0, tag.getIntOr("waitMs", 30_000));
    }

    public List<String> effectiveAccounts() {
        ArrayList<String> out = new ArrayList<>();
        if (accounts != null) {
            for (String id : accounts) {
                if (id != null && !id.isBlank()) {
                    String trimmed = id.trim();
                    if (!out.contains(trimmed)) out.add(trimmed);
                }
            }
        }
        if (out.isEmpty() && accountIds != null) {
            for (String id : accountIds) {
                if (id != null && !id.isBlank()) out.add(id.trim());
            }
        }
        return out;
    }

    public LinkedHashSet<String> effectiveAccountIds() {
        return new LinkedHashSet<>(effectiveAccounts());
    }

    public static String canonicalWaitMode(String raw) {
        if (WAIT_FIXED_DELAY.equals(raw) || WAIT_NO_WAIT.equals(raw)) return raw;
        return WAIT_UNTIL_READY;
    }

    public static String anonymizedAccountLabel(int index) {
        return "empty " + (index + 1);
    }

    public static String placeholderAccountId(int index) {
        return "empty" + (index + 1);
    }

    @Override
    public void sanitizeForSharing() {

        ArrayList<String> sanitized = new ArrayList<>();
        for (int i = 0; i < effectiveAccounts().size(); i++) sanitized.add(placeholderAccountId(i));
        accounts = sanitized;
        accountIds = new LinkedHashSet<>(sanitized);
    }

    public int normalizedStepCount(List<MacroAction> actions, int headerIndex) {
        if (actions == null || headerIndex < 0 || headerIndex >= actions.size()) return 0;
        int max = Math.max(0, Math.min(stepCount, actions.size() - headerIndex - 1));
        int count = 0;
        for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction action = actions.get(i);
            if (action instanceof RaceAction || action instanceof ReportAction || action instanceof MultiAction
                || action instanceof PacketGateAction || action instanceof EndPacketGateAction) break;
            count++;
        }
        return count;
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.MULTI;
    }

    @Override
    public String getDisplayName() {
        int steps = Math.max(0, stepCount);
        int selected = effectiveAccounts().size();
        return "Multi [" + steps + (steps == 1 ? " step" : " steps") + " on "
            + selected + (selected == 1 ? " account" : " accounts") + "]";
    }

    @Override
    public String getIcon() {
        return "Mlt";
    }
}
