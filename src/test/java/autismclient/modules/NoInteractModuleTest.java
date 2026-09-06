package autismclient.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoInteractModuleTest {
    @Test
    void blacklistCancelsOnlyListedRegistryTargets() {
        assertFalse(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of(), "BlackList"));
        assertTrue(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of("minecraft:chest"), "BlackList"));
        assertTrue(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of("chest"), "BlackList"));
        assertFalse(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:furnace", List.of("minecraft:chest"), "BlackList"));
    }

    @Test
    void whitelistAllowsListedRegistryTargetsWhenConfigured() {
        assertFalse(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of(), "WhiteList"));
        assertFalse(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of("minecraft:chest"), "WhiteList"));
        assertFalse(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:chest", List.of("chest"), "WhiteList"));
        assertTrue(BuiltinModules.NoInteractModule.shouldCancelRegistryTarget(
            "minecraft:furnace", List.of("minecraft:chest"), "WhiteList"));
    }
}
