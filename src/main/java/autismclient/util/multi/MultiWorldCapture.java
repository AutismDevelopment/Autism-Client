package autismclient.util.multi;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MultiWorldCapture {

    private static final int CHUNK_CAP = 2048;
    private static final int ENTITY_CAP = 4096;
    private static final int PLAYER_CAP = 4096;

    private ClientboundLoginPacket login;
    private ClientboundRespawnPacket respawn;
    private ClientboundSetChunkCacheCenterPacket cacheCenter;
    private ClientboundSetChunkCacheRadiusPacket cacheRadius;

    private ClientboundOpenScreenPacket openScreen;
    private ClientboundContainerSetContentPacket containerContent;
    private final Map<Long, ClientboundLevelChunkWithLightPacket> chunks = new LinkedHashMap<>();
    private final Map<Integer, ClientboundAddEntityPacket> entitySpawns = new LinkedHashMap<>();
    private final Map<Integer, ClientboundSetEntityDataPacket> entityData = new LinkedHashMap<>();

    private final Map<UUID, ClientboundPlayerInfoUpdatePacket> playerInfoByUuid = new LinkedHashMap<>();

    record Snapshot(ClientboundLoginPacket login, ClientboundRespawnPacket respawn,
                    ClientboundSetChunkCacheCenterPacket cacheCenter, ClientboundSetChunkCacheRadiusPacket cacheRadius,
                    List<ClientboundLevelChunkWithLightPacket> chunks,
                    List<ClientboundPlayerInfoUpdatePacket> playerInfo,
                    List<ClientboundAddEntityPacket> entities,
                    List<ClientboundSetEntityDataPacket> entityData,
                    ClientboundOpenScreenPacket openScreen, ClientboundContainerSetContentPacket containerContent) {
    }

    synchronized void capture(Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            login = loginPacket;
            respawn = null;
            resetWorld();
        } else if (packet instanceof ClientboundRespawnPacket respawnPacket) {

            respawn = respawnPacket;
            resetWorld();
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            long key = ChunkPos.pack(chunk.getX(), chunk.getZ());
            chunks.remove(key);
            chunks.put(key, chunk);
            if (chunks.size() > CHUNK_CAP) evictOldest(chunks);
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            chunks.remove(forget.pos().pack());
        } else if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            cacheCenter = center;
        } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            cacheRadius = radius;
        } else if (packet instanceof ClientboundAddEntityPacket add) {
            entitySpawns.remove(add.getId());
            entitySpawns.put(add.getId(), add);
            if (entitySpawns.size() > ENTITY_CAP) evictOldest(entitySpawns);
        } else if (packet instanceof ClientboundSetEntityDataPacket data) {

            if (entitySpawns.containsKey(data.id())) entityData.put(data.id(), data);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            for (int i = 0; i < remove.getEntityIds().size(); i++) {
                int id = remove.getEntityIds().getInt(i);
                entitySpawns.remove(id);
                entityData.remove(id);
            }
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                playerInfoByUuid.put(entry.profileId(), info);
            }
            while (playerInfoByUuid.size() > PLAYER_CAP) evictOldest(playerInfoByUuid);
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
            for (UUID id : remove.profileIds()) playerInfoByUuid.remove(id);
        } else if (packet instanceof ClientboundOpenScreenPacket open) {
            openScreen = open;
            containerContent = null;
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            containerContent = content;
        } else if (packet instanceof ClientboundContainerClosePacket) {
            openScreen = null;
            containerContent = null;
        }
    }

    private void resetWorld() {
        chunks.clear();
        entitySpawns.clear();
        entityData.clear();
        playerInfoByUuid.clear();
        cacheCenter = null;
        cacheRadius = null;
        openScreen = null;
        containerContent = null;
    }

    private static void evictOldest(Map<?, ?> map) {
        var it = map.keySet().iterator();
        if (it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    synchronized boolean hasWorld() {
        return login != null;
    }

    synchronized Snapshot snapshot() {

        List<ClientboundPlayerInfoUpdatePacket> distinctInfo = new ArrayList<>(new LinkedHashSet<>(playerInfoByUuid.values()));
        return new Snapshot(login, respawn, cacheCenter, cacheRadius,
            new ArrayList<>(chunks.values()), distinctInfo,
            new ArrayList<>(entitySpawns.values()), new ArrayList<>(entityData.values()),
            openScreen, containerContent);
    }

    synchronized void clear() {
        login = null;
        respawn = null;
        resetWorld();
    }
}
