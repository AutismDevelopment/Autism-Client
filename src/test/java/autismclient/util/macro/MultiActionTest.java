package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

class MultiActionTest {
    @Test
    void coversExactlyStepCountRows() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 2;
        List<MacroAction> actions = List.of(multi, new DelayAction(), new DelayAction(), new DelayAction());
        assertEquals(2, multi.normalizedStepCount(actions, 0));
    }

    @Test
    void clampsWhenStepCountExceedsRemainingRows() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 10;
        List<MacroAction> actions = List.of(multi, new DelayAction(), new DelayAction());
        assertEquals(2, multi.normalizedStepCount(actions, 0));
    }

    @Test
    void stopsBeforeStructuralHeaders() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 10;
        assertEquals(1, multi.normalizedStepCount(
            List.of(multi, new DelayAction(), new RaceAction(), new DelayAction()), 0));
        assertEquals(1, multi.normalizedStepCount(
            List.of(multi, new DelayAction(), new ReportAction(), new DelayAction()), 0));
        assertEquals(1, multi.normalizedStepCount(
            List.of(multi, new DelayAction(), new MultiAction(), new DelayAction()), 0));
        assertEquals(1, multi.normalizedStepCount(
            List.of(multi, new DelayAction(), new PacketGateAction(), new DelayAction()), 0));
        assertEquals(1, multi.normalizedStepCount(
            List.of(multi, new DelayAction(), new EndPacketGateAction(), new DelayAction()), 0));
    }

    @Test
    void zeroStepCountCoversNothing() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 0;
        assertEquals(0, multi.normalizedStepCount(List.of(multi, new DelayAction()), 0));
    }

    @Test
    void invalidHeaderIndexCoversNothing() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 3;
        List<MacroAction> actions = List.of(multi, new DelayAction());
        assertEquals(0, multi.normalizedStepCount(actions, -1));
        assertEquals(0, multi.normalizedStepCount(actions, 5));
        assertEquals(0, multi.normalizedStepCount(null, 0));
    }

    @Test
    void anonymizedAccountLabelsAreIndexBased() {
        assertEquals("empty 1", MultiAction.anonymizedAccountLabel(0));
        assertEquals("empty 2", MultiAction.anonymizedAccountLabel(1));
        assertEquals("empty 3", MultiAction.anonymizedAccountLabel(2));
    }

    @Test
    void sanitizeForSharingReplacesRealAccountIdsWithPlaceholders() {
        MultiAction multi = new MultiAction();
        multi.accounts = new java.util.ArrayList<>(List.of("RealNickOne", "real.nick@two.example"));
        multi.accountIds = new java.util.LinkedHashSet<>(multi.accounts);

        multi.sanitizeForSharing();

        assertEquals(List.of("empty1", "empty2"), multi.effectiveAccounts());
        assertEquals(new java.util.LinkedHashSet<>(List.of("empty1", "empty2")), multi.effectiveAccountIds());
        assertFalse(multi.toTag().getList("accounts").orElse(new net.minecraft.nbt.ListTag()).toString().contains("RealNick"));
        assertFalse(multi.toTag().toString().contains("real.nick"));
    }

    @Test
    void sanitizeForSharingPreservesAccountCount() {
        MultiAction multi = new MultiAction();
        multi.accounts = new java.util.ArrayList<>(List.of("a", "b", "c"));
        multi.sanitizeForSharing();
        assertEquals(3, multi.effectiveAccounts().size());
        assertEquals("empty3", multi.effectiveAccounts().get(2));
    }

    @Test
    void headerAtListEndCoversNothing() {
        MultiAction multi = new MultiAction();
        multi.stepCount = 5;
        List<MacroAction> actions = List.of(new DelayAction(), multi);
        assertEquals(0, multi.normalizedStepCount(actions, 1));
    }

    @Test
    void fromTagDedupesAndTrimsAccounts() {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.StringTag.valueOf("  alpha  "));
        list.add(net.minecraft.nbt.StringTag.valueOf("alpha"));
        list.add(net.minecraft.nbt.StringTag.valueOf(""));
        list.add(net.minecraft.nbt.StringTag.valueOf("beta"));
        list.add(net.minecraft.nbt.StringTag.valueOf("beta"));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("type", "MULTI");
        tag.put("accounts", list);

        MultiAction multi = new MultiAction();
        multi.fromTag(tag);

        assertEquals(List.of("alpha", "beta"), multi.effectiveAccounts());
        assertEquals(new java.util.LinkedHashSet<>(List.of("alpha", "beta")), multi.effectiveAccountIds());

        assertEquals(2, multi.toTag().getList("accounts").orElse(new net.minecraft.nbt.ListTag()).size());
    }

    @Test
    void effectiveAccountsFallsBackToAccountIdsSet() {
        MultiAction multi = new MultiAction();
        multi.accountIds = new java.util.LinkedHashSet<>(List.of("one", "two"));
        assertEquals(List.of("one", "two"), multi.effectiveAccounts());

        multi.accounts = new java.util.ArrayList<>(List.of("three"));
        assertEquals(List.of("three"), multi.effectiveAccounts());
    }
}
