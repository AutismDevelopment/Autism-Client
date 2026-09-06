package autismclient.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static autismclient.util.AutismFarmPlanner.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

final class AutismFarmPlannerTest {
    private static AutismFarmPlanner.Option<String> option(String id, AutismFarmPlanner.Kind kind,
                                                           float yaw, int slot, boolean urgent) {
        return new AutismFarmPlanner.Option<>(id, kind, yaw, 40, slot, urgent);
    }

    @Test void completesReplantBeforeWalkingLeavesItBehind() {
        var harvest = option("ripe", HARVEST, 0, -1, false);
        var replant = option("debt", REPLANT_RECENT, 160, 3, true);
        assertEquals("debt", AutismFarmPlanner.choose(List.of(harvest, replant), 0, 40, 0, "ripe"));
    }

    @Test void availableOffhandAvoidsASlotChangeWithoutLosingHarvestTool() {
        var harvest = option("ripe", HARVEST, 60, 0, false);
        var hotbarPlant = option("seed", REPLANT_RECENT, 0, 3, false);
        var offhandPlant = option("seed", REPLANT_RECENT, 0, -1, false);
        assertEquals("ripe", AutismFarmPlanner.choose(List.of(harvest, hotbarPlant), 0, 40, 0, null));
        assertEquals("seed", AutismFarmPlanner.choose(List.of(harvest, offhandPlant), 0, 40, 0, null));
    }

    @Test void looksPastTheNearestRotationToLeaveTheNextHandReady() {
        var first = option("near", HARVEST, 0, 1, false);
        var second = option("continuation", HARVEST, 20, 0, false);
        assertEquals("continuation", AutismFarmPlanner.choose(List.of(first, second), 0, 40, 0, null));
    }

    @Test void plantsFieldBeforeSpendingMealOnRepeatedGrowthCycle() {
        var meal = option("meal", BONEMEAL, 0, -1, false);
        var seed = option("seed", REPLANT, 60, 1, false);
        assertEquals("seed", AutismFarmPlanner.choose(List.of(meal, seed), 0, 40, 0, "meal"));
    }

    @Test void angularSeamDoesNotLookLikeAFullTurn() {
        var across = option("across", HARVEST, -179, -1, false);
        var away = option("away", HARVEST, 145, -1, false);
        assertEquals("across", AutismFarmPlanner.choose(List.of(across, away), 179, 40, 0, null));
    }

    @Test void removedOrDisabledWorkCannotRemainSticky() {
        var current = option("current", HARVEST, 90, -1, false);
        assertEquals("current", AutismFarmPlanner.choose(List.of(current), 0, 40, 0, "removed"));
        assertNull(AutismFarmPlanner.choose(List.of(), 0, 40, 0, "removed"));
    }
}
