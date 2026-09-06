package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MacroVariableContextTest {
    @Test
    void contextsAreIsolatedAndPropertiesAreAddressable() {
        MacroVariableContext first = new MacroVariableContext();
        MacroVariableContext second = new MacroVariableContext();
        first.set("gui", MacroValue.structured(MacroValue.Kind.GUI, "Shop", Map.of(
            "title", MacroValue.text("Shop"),
            "type", MacroValue.text("generic_9x6")
        )));

        assertEquals("Shop", first.get("gui.title").orElseThrow().value());
        assertTrue(second.get("gui").isEmpty());

        first.clear();
        assertTrue(first.get("gui").isEmpty());
    }

    @Test
    void typedItemTokenPreservesIdentityAndSlot() {
        MacroVariables.clear();
        MacroVariables.setValue("item", MacroValue.structured(MacroValue.Kind.ITEM, "Obsidian", Map.of(
            "name", MacroValue.text("Obsidian"),
            "id", MacroValue.identifier("minecraft:obsidian"),
            "count", MacroValue.number(4),
            "slot", MacroValue.slot(12)
        )));

        ItemTarget target = ItemTarget.manualText("{item}").resolveTemplate(null);
        assertEquals("minecraft:obsidian", target.registryId);
        assertEquals("Obsidian", target.display);
        assertEquals(12, target.slot);
        MacroVariables.clear();
    }
}
