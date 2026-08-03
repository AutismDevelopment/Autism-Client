package autismclient.util.multi;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiPovSkinTest {
    @Test
    void takeoverProfileRetainsServerTexturePropertyAcrossProxyUuid() {
        UUID accountId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        Property texture = new Property("textures", "packed-skin", "signature");
        GameProfile known = new GameProfile(accountId, "Bot", new PropertyMap(
            ImmutableMultimap.of("textures", texture)));

        GameProfile rendered = MultiSession.skinAwareProfile(known, serverId, "Bot");

        assertEquals(serverId, rendered.id());
        assertTrue(rendered.properties().containsEntry("textures", texture));
    }

    @Test
    void exactServerProfileIsReusedWithoutAllocation() {
        GameProfile known = new GameProfile(UUID.randomUUID(), "Bot", new PropertyMap(
            ImmutableMultimap.of("textures", new Property("textures", "packed-skin"))));

        assertSame(known, MultiSession.skinAwareProfile(known, known.id(), known.name()));
    }
}
