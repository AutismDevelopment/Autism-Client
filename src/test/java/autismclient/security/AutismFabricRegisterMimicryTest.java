package autismclient.security;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismFabricRegisterMimicryTest {

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void fallbackWithoutVoicechatIsExactlyTheStockFabricSet() {
        List<Identifier> channels = AutismFabricRegisterMimicry.fallbackReceivableChannels(false);
        assertEquals(List.of(id("fabric-screen-handler-api-v1", "open_screen")), channels);
    }

    @Test
    void fallbackWithVoicechatAppendsAllNineVoiceChannelsInStockOrder() {
        List<Identifier> channels = AutismFabricRegisterMimicry.fallbackReceivableChannels(true);
        assertEquals(List.of(
            id("fabric-screen-handler-api-v1", "open_screen"),
            id("voicechat", "secret"),
            id("voicechat", "state"),
            id("voicechat", "states"),
            id("voicechat", "remove_state"),
            id("voicechat", "add_group"),
            id("voicechat", "remove_group"),
            id("voicechat", "joined_group"),
            id("voicechat", "add_category"),
            id("voicechat", "remove_category")
        ), channels);
    }

    @Test
    void announcementSelectionNeverExceedsTheWhitelist() {

        List<Identifier> receivable = List.of(
            id("autismclient", "secret"),
            id("fabric-screen-handler-api-v1", "open_screen"));
        List<Identifier> kept = AutismProtectorChannelFilter.keepWhitelisted(
            receivable, channel -> !"autismclient".equals(channel.getNamespace()));
        assertEquals(List.of(id("fabric-screen-handler-api-v1", "open_screen")), kept);
    }

    @Test
    void synthesizesOnlyWhenModdedLateAndNoAuthenticRegister() {
        long grace = AutismFabricRegisterMimicry.minPlayTicksBeforeSynthesize();

        assertTrue(AutismFabricRegisterMimicry.shouldSynthesize(true, false, false, grace));

        assertFalse(AutismFabricRegisterMimicry.shouldSynthesize(true, true, false, grace + 100));

        assertFalse(AutismFabricRegisterMimicry.shouldSynthesize(false, false, false, grace + 100));

        assertFalse(AutismFabricRegisterMimicry.shouldSynthesize(true, false, false, grace - 1));

        assertFalse(AutismFabricRegisterMimicry.shouldSynthesize(true, false, true, grace + 100));
    }
}
