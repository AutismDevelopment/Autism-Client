package autismclient.util.mm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MmPeerTest {
    @Test
    void keepsDiscordDisplaySeparateFromMinecraftCommands() {
        MmPeer peer = new MmPeer(new byte[8]);
        peer.discordUser = "a_bright6f";
        peer.nickname = "MinecraftName";

        assertEquals("a_bright6f", peer.displayName());
        assertEquals("MinecraftName", peer.commandName());
    }

    @Test
    void missingDiscordIdentityNeverLeaksMinecraftName() {
        MmPeer peer = new MmPeer(new byte[8]);
        peer.nickname = "MinecraftName";

        assertEquals("Discord member", peer.displayName());
        assertEquals("MinecraftName", peer.commandName());
    }
}
