package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import autismclient.util.AutismMacro;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MacroDynamicBindingsTest {
    @Test
    void bindingSidecarRoundTripsWithoutReplacingLiteralFallback() {
        DelayAction original = new DelayAction();
        original.delayTicks = 7;
        MacroDynamicBindings.set(original, "delayTicks", "{captured_delay|default:3}");

        CompoundTag saved = original.toTag();
        MacroDynamicBindings.write(original, saved);

        DelayAction restored = new DelayAction();
        restored.fromTag(saved);
        MacroDynamicBindings.load(restored, saved);

        assertEquals(7, restored.delayTicks);
        assertEquals("{captured_delay|default:3}", MacroDynamicBindings.get(restored, "delayTicks"));
    }

    @Test
    void appliesTypedEnumAndBlockPositionBindings() {
        MacroVariables.clear();
        MacroVariables.set("target_x", "42");
        MacroVariables.set("strategy", "largest_stack");

        PlaceAction place = new PlaceAction();
        place.blockPos = new BlockPos(1, 2, 3);
        MacroDynamicBindings.set(place, "blockX", "{target_x}");
        SelectSlotAction select = new SelectSlotAction();
        MacroDynamicBindings.set(select, "strategy", "{strategy}");

        assertEquals(true, MacroDynamicBindings.apply(place, null));
        assertEquals(new BlockPos(42, 2, 3), place.blockPos);
        assertEquals(true, MacroDynamicBindings.apply(select, null));
        assertEquals(SelectSlotAction.Strategy.LARGEST_STACK, select.strategy);
        MacroVariables.clear();
    }

    @Test
    void macroDeepCopyPreservesBindingsWithoutSharingActionInstances() {
        AutismMacro original = new AutismMacro("Dynamic");
        DelayAction action = new DelayAction();
        action.delayTicks = 7;
        MacroDynamicBindings.set(action, "delayTicks", "{delay|default:2}");
        original.actions.add(action);

        AutismMacro copy = original.deepCopy();
        DelayAction copiedAction = (DelayAction) copy.actions.get(0);

        assertNotSame(action, copiedAction);
        assertEquals(7, copiedAction.delayTicks);
        assertEquals("{delay|default:2}", MacroDynamicBindings.get(copiedAction, "delayTicks"));
    }
}
