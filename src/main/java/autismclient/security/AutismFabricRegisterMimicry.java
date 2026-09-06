package autismclient.security;

import autismclient.AutismClientAddon;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AutismFabricRegisterMimicry {

    private static final boolean DEBUG = Boolean.getBoolean("autism.protector.debug");

    private static final long MIN_PLAY_TICKS_BEFORE_SYNTHESIZE = 5L;

    private static final String SCREEN_HANDLER_OPEN_SCREEN_NAMESPACE = "fabric-screen-handler-api-v1";
    private static final String SCREEN_HANDLER_OPEN_SCREEN_PATH = "open_screen";
    private static final String VOICECHAT_MOD_ID = "voicechat";
    private static final String[] VOICECHAT_CLIENT_CHANNELS = {
        "secret", "state", "states", "remove_state", "add_group",
        "remove_group", "joined_group", "add_category", "remove_category"
    };

    private static volatile boolean outboundRegisterSeen;
    private static volatile boolean synthesized;
    private static volatile boolean fallbackLogClaimed;
    private static volatile long playStartTick = -1L;
    private static long tickCounter;

    private AutismFabricRegisterMimicry() {
    }

    public static void noteOutboundRegister() {
        outboundRegisterSeen = true;
    }

    static boolean claimFallbackLog() {
        if (fallbackLogClaimed) return false;
        fallbackLogClaimed = true;
        return true;
    }

    public static void onClientTick(Minecraft client) {
        tickCounter++;
        ClientPacketListener listener = client == null ? null : client.getConnection();
        if (client == null || client.player == null || listener == null) {
            resetConnectionState();
            return;
        }
        if (synthesized || outboundRegisterSeen) return;

        if (!AutismProtector.isActive() || AutismProtector.isVanillaMode()) return;
        if (playStartTick < 0L) playStartTick = tickCounter;
        if (tickCounter - playStartTick < MIN_PLAY_TICKS_BEFORE_SYNTHESIZE) return;

        List<Identifier> channels = expectedAnnouncementChannels();

        synthesized = true;
        if (channels.isEmpty()) return;
        try {
            listener.send(new ServerboundCustomPayloadPacket(
                new RegistrationPayload(RegistrationPayload.REGISTER, channels)));
            if (DEBUG) {
                AutismClientAddon.LOG.debug("[AutismProtector] Synthesized late register with {} channel(s)", channels.size());
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[AutismProtector] Late register send failed: {}", t.getMessage());
        }
    }

    private static void resetConnectionState() {
        outboundRegisterSeen = false;
        synthesized = false;
        fallbackLogClaimed = false;
        playStartTick = -1L;
    }

    static List<Identifier> expectedAnnouncementChannels() {
        Collection<Identifier> receivable;
        try {
            receivable = ClientPlayNetworking.getGlobalReceivers();
        } catch (Throwable t) {
            if (DEBUG) {
                AutismClientAddon.LOG.debug("[AutismProtector] getGlobalReceivers unavailable, using stock fallback: {}", t.getMessage());
            }
            receivable = fallbackReceivableChannels(isVoicechatLoaded());
        }
        if (receivable == null) receivable = List.of();
        return AutismProtectorChannelFilter.keepWhitelisted(receivable, AutismProtectorTracker::isWhitelistedChannel);
    }

    static List<Identifier> fallbackReceivableChannels(boolean voicechatLoaded) {
        Set<Identifier> channels = new LinkedHashSet<>();
        channels.add(Identifier.fromNamespaceAndPath(SCREEN_HANDLER_OPEN_SCREEN_NAMESPACE, SCREEN_HANDLER_OPEN_SCREEN_PATH));
        if (voicechatLoaded) {
            for (String path : VOICECHAT_CLIENT_CHANNELS) {
                channels.add(Identifier.fromNamespaceAndPath(VOICECHAT_MOD_ID, path));
            }
        }
        return new ArrayList<>(channels);
    }

    private static boolean isVoicechatLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(VOICECHAT_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean shouldSynthesize(boolean moddedModeActive, boolean registerSeen, boolean alreadySynthesized, long ticksSincePlayStart) {
        return moddedModeActive
            && !registerSeen
            && !alreadySynthesized
            && ticksSincePlayStart >= MIN_PLAY_TICKS_BEFORE_SYNTHESIZE;
    }

    static long minPlayTicksBeforeSynthesize() {
        return MIN_PLAY_TICKS_BEFORE_SYNTHESIZE;
    }
}
