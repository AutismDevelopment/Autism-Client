package autismclient.mixin.security;

import autismclient.security.AutismProtector;
import autismclient.security.AutismProtectorNumericSanity;

import com.mojang.logging.LogUtils;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Packet crash guard — a defensive protector feature that drops an incoming play packet whose raw position or
 * velocity is malformed (NaN / Infinity) or absurdly large (beyond {@link AutismProtectorNumericSanity#SANE_LIMIT},
 * far past the world border) before the game applies it and overflows its own section/collision math and crashes.
 *
 * <p>Some servers send such values deliberately to crash connected clients; observed crash reports carried
 * per-axis values around {@code 1.8e38 / 2.8e38 / 2.1e38}. The crash reproduces even with rendering optimisation
 * mods disabled, so it is a vanilla-math overflow rather than an optimisation-mod bug. Covered here:
 *
 * <ul>
 *   <li>explosion knockback ({@link ClientboundExplodePacket}) — center, radius and player-knockback vector;</li>
 *   <li>the local player teleport ({@link ClientboundPlayerPositionPacket}) — position and delta-movement;</li>
 *   <li>entity teleports ({@link ClientboundTeleportEntityPacket}) — position and delta-movement;</li>
 *   <li>entity position syncs ({@link ClientboundEntityPositionSyncPacket}) — position and delta-movement; and</li>
 *   <li>entity spawns ({@link ClientboundAddEntityPacket}) — spawn position.</li>
 * </ul>
 *
 * All share the one {@link AutismProtectorNumericSanity} check, so a new overflow vector is caught by the same
 * mechanism rather than needing another one-off fix.
 *
 * <p>Purely defensive and local: it only <em>drops</em> a malformed packet (the client keeps its current, valid
 * state) — it never edits anything the client sends and never fabricates a teleport acknowledgement. It only
 * fires on values orders of magnitude past anything legitimate, so real gameplay is never touched. Gated by
 * {@link AutismProtector#shouldGuardCrashPackets()} (and, like every {@code security} mixin, stood down when an
 * external protector such as opsec is present — see {@code AutismMixinPlugin}).
 *
 * <p>Injections use {@code require = 0} so that if a future mapping renames one of these handlers the guard
 * degrades to "that one packet is not guarded" rather than failing the whole required mixin config — a
 * deliberate choice for a defensive safeguard that must never be the reason a client won't launch.
 *
 * <p>Adapted for AUTISM from BossCrashGuard (https://github.com/WaterBoss11/BossCrashGuard).
 */
@Mixin(ClientPacketListener.class)
public abstract class AutismProtectorPacketCrashGuardMixin {

    @Unique
    private static final Logger AUTISM$LOGGER = LogUtils.getLogger();

    @Unique
    private static long autism$dropCount = 0L;

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$guardExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (!AutismProtector.shouldGuardCrashPackets()) return;

        boolean bad = autism$vecInsane(packet.center())
            || AutismProtectorNumericSanity.isInsane(packet.radius(), AutismProtectorNumericSanity.SANE_LIMIT);
        if (!bad) {
            Optional<Vec3> knockback = packet.playerKnockback();
            bad = knockback.isPresent() && autism$vecInsane(knockback.get());
        }
        if (bad) {
            autism$drop("explosion", "center=" + packet.center() + " r=" + packet.radius()
                + " knockback=" + packet.playerKnockback().orElse(null));
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$guardMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (autism$rejectMove(packet.change(), "player teleport")) ci.cancel();
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$guardTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (autism$rejectMove(packet.change(), "entity teleport")) ci.cancel();
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$guardPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (autism$rejectMove(packet.values(), "entity position sync")) ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$guardAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (!AutismProtector.shouldGuardCrashPackets()) return;
        if (AutismProtectorNumericSanity.isInsane(packet.getX(), packet.getY(), packet.getZ())) {
            autism$drop("entity spawn",
                "pos=(" + packet.getX() + ", " + packet.getY() + ", " + packet.getZ() + ")");
            ci.cancel();
        }
    }

    /**
     * Shared drop decision for a {@link PositionMoveRotation}-carrying packet: true (and recorded) when its
     * position or delta-movement is out of range.
     */
    @Unique
    private static boolean autism$rejectMove(PositionMoveRotation change, String kind) {
        if (!AutismProtector.shouldGuardCrashPackets() || change == null) return false;
        if (autism$vecInsane(change.position()) || autism$vecInsane(change.deltaMovement())) {
            autism$drop(kind, "pos=" + change.position() + " delta=" + change.deltaMovement());
            return true;
        }
        return false;
    }

    @Unique
    private static boolean autism$vecInsane(Vec3 v) {
        return v != null && AutismProtectorNumericSanity.isInsane(v.x, v.y, v.z);
    }

    /** Record a dropped packet: the first drop is logged loudly, the rest at debug to avoid a flood vector. */
    @Unique
    private static void autism$drop(String kind, String detail) {
        autism$dropCount++;
        if (autism$dropCount == 1L) {
            AUTISM$LOGGER.warn("[AutismProtector] crash guard dropped a malformed {} packet ({}); "
                + "further drops logged at debug", kind, detail);
        } else {
            AUTISM$LOGGER.debug("[AutismProtector] crash guard dropped a malformed {} packet #{} ({})",
                kind, autism$dropCount, detail);
        }
    }
}
