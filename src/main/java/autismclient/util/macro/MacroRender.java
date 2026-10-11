package autismclient.util.macro;

import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismMacro;
import autismclient.util.PacketRegenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

final class MacroRender {
    private MacroRender() {
    }
    public static void onRender(float tickDelta) {
        if (!MacroExecutor.isRotating.get()) return;
        MacroExecutor.RunState owner = MacroExecutor.ROTATION_OWNER.get();
        if (owner != null && !owner.running.get()) {
            cancelRotation(owner);
            return;
        }
        if (owner == null && !MacroExecutor.isRunning.get()) {
            synchronized (MacroExecutor.ROTATION_STATE_LOCK) {
                if (MacroExecutor.ROTATION_OWNER.get() == null) MacroExecutor.isRotating.set(false);
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        double deltaYaw = net.minecraft.util.Mth.wrapDegrees(MacroExecutor.targetYaw - currentYaw);
        double deltaPitch = MacroExecutor.targetPitch - currentPitch;
        double diagonalDistance = Math.hypot(deltaYaw, deltaPitch);
        if (diagonalDistance <= 0.35) {
            mc.player.setYRot(MacroExecutor.targetYaw);
            mc.player.setXRot(MacroExecutor.targetPitch);
            MacroExecutor.rotationAlignedFrames++;
            if (MacroExecutor.rotationAlignedFrames >= 2) {
                synchronized (MacroExecutor.ROTATION_STATE_LOCK) {
                    if (MacroExecutor.ROTATION_OWNER.get() == owner) {
                        MacroExecutor.isRotating.set(false);
                        MacroExecutor.ROTATION_OWNER.set(null);
                    }
                }
            }
            return;
        }
        MacroExecutor.rotationAlignedFrames = 0;

        double frameStep = Math.min(diagonalDistance, MacroExecutor.rotationSpeed * Math.max(tickDelta, 1.0f));
        double stepYaw = (deltaYaw / diagonalDistance) * frameStep;
        double stepPitch = (deltaPitch / diagonalDistance) * frameStep;

        mc.player.setYRot(currentYaw + (float) stepYaw);
        mc.player.setXRot(currentPitch + (float) stepPitch);
    }

    public static boolean hasRenderWork() {
        return MacroExecutor.isRotating.get();
    }

    static void startSmoothRotation(float yaw, float pitch, double speed) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        while (run != null && run.running.get()) {
            synchronized (MacroExecutor.ROTATION_STATE_LOCK) {
                MacroExecutor.RunState owner = MacroExecutor.ROTATION_OWNER.get();
                if (owner == null || owner == run || !owner.running.get()) {
                    MacroExecutor.ROTATION_OWNER.set(run);
                    MacroExecutor.targetYaw = yaw;
                    MacroExecutor.targetPitch = pitch;
                    MacroExecutor.rotationSpeed = speed;
                    MacroExecutor.rotationAlignedFrames = 0;
                    MacroExecutor.isRotating.set(true);
                    return;
                }
            }
            java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
        }
        if (run == null) {
            synchronized (MacroExecutor.ROTATION_STATE_LOCK) {
                if (MacroExecutor.ROTATION_OWNER.get() != null) return;
                MacroExecutor.targetYaw = yaw;
                MacroExecutor.targetPitch = pitch;
                MacroExecutor.rotationSpeed = speed;
                MacroExecutor.rotationAlignedFrames = 0;
                MacroExecutor.isRotating.set(true);
            }
        }
    }

    static void waitForSmoothRotationCompletion() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        while (MacroExecutor.isCurrentRunActive() && MacroExecutor.isRotating.get()
            && (run == null || MacroExecutor.ROTATION_OWNER.get() == run)) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    static void cancelRotation(MacroExecutor.RunState run) {
        if (run == null) return;
        synchronized (MacroExecutor.ROTATION_STATE_LOCK) {
            if (MacroExecutor.ROTATION_OWNER.get() != run) return;
            MacroExecutor.isRotating.set(false);
            MacroExecutor.rotationAlignedFrames = 0;
            MacroExecutor.ROTATION_OWNER.set(null);
        }
    }

    static LookAtBlockAction.RotationTarget resolveLookAtTargetOnClient(Minecraft mc, LookAtBlockAction action) {
        if (mc == null || action == null) return null;
        java.util.concurrent.atomic.AtomicReference<LookAtBlockAction.RotationTarget> targetRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        mc.execute(() -> {
            try {
                targetRef.set(action.resolveRotationTarget(mc));
            } finally {
                latch.countDown();
            }
        });
        while (MacroExecutor.isCurrentRunActive()) {
            try {
                if (latch.await(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return targetRef.get();
    }

    public static void stopAll() {
        MacroExecutor.stop();
    }

    static void executeBurstPackets(Minecraft mc, java.util.List<SendPacketAction> batch) {
        if (mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cNo network connection for burst!");
            return;
        }

        java.util.List<autismclient.util.AutismSharedState.QueuedPacket> allQueuedPackets = new java.util.ArrayList<>();
        for (SendPacketAction action : batch) {
            allQueuedPackets.addAll(action.getPackets());
        }

        if (allQueuedPackets.isEmpty()) {
            return;
        }

        java.util.List<Packet<?>> regeneratedPackets = new java.util.ArrayList<>(allQueuedPackets.size());
        for (autismclient.util.AutismSharedState.QueuedPacket qp : allQueuedPackets) {
            if (qp.packet == null) continue;
            regeneratedPackets.add(qp.packet);
        }

        if (regeneratedPackets.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cBurst: All packets failed to regenerate!");
            return;
        }

        net.minecraft.client.multiplayer.ClientPacketListener handler = mc.getConnection();

        for (Packet<?> packet : regeneratedPackets) {
            Packet<?> toSend = packet;

            if (packet instanceof ServerboundContainerClickPacket) {
                toSend = PacketRegenerator.regenerate(packet);
                if (toSend == null) {
                    continue;
                }
            }

            handler.send(toSend);
        }

        if (regeneratedPackets.size() > 1) {
            AutismClientMessaging.sendPrefixed("\u00a7a Burst sent " + regeneratedPackets.size() + " packets");
        }
    }

    static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count) {
        java.util.List<Packet<?>> result = new java.util.ArrayList<>();
        int maxIdx = count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size());
        return preGeneratePackets(macro, startIdx, count, maxIdx);
    }

    static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count, int endIdx) {
        java.util.List<Packet<?>> result = new java.util.ArrayList<>();
        int maxIdx = Math.min(endIdx, count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size()));

        Minecraft mc = Minecraft.getInstance();

        for (int j = startIdx; j < maxIdx; j++) {
            MacroAction act = macro.actions.get(j);
            if (!(act instanceof SendPacketAction)) break;

            SendPacketAction spa = (SendPacketAction) act;
            for (autismclient.util.AutismSharedState.QueuedPacket qp : spa.getPackets()) {
                if (qp.packet == null) continue;

                result.add(qp.packet);
            }
        }

        return result;
    }

    static void executePreGeneratedBurst(Minecraft mc, java.util.List<Packet<?>> packets) {
        if (packets.isEmpty() || mc.getConnection() == null) return;

        net.minecraft.client.multiplayer.ClientPacketListener handler = mc.getConnection();

        for (Packet<?> p : packets) {
            Packet<?> toSend = p;

            if (p instanceof ServerboundContainerClickPacket) {
                toSend = PacketRegenerator.regenerate(p);
                if (toSend == null) {
                    continue;
                }
            }

            handler.send(toSend);
        }

        AutismModule module = AutismModule.get();
        if (module.shouldForceChannelFlush()) {
            try {
                net.minecraft.network.Connection connection = handler.getConnection();
                if (connection != null) {

                    connection.flushChannel();
                }
            } catch (Exception e) {  }
        }
    }

    static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount) {
        return countPreGeneratedActions(macro, startIdx, packetCount, macro.actions.size());
    }

    static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount, int endIdx) {
        int actionsToSkip = 0;
        int packetsRemaining = packetCount;

        for (int j = startIdx; j < endIdx && packetsRemaining > 0; j++) {
            MacroAction act = macro.actions.get(j);
            if (!(act instanceof SendPacketAction)) break;

            SendPacketAction spa = (SendPacketAction) act;
            int actionPackets = spa.getPackets().size();
            packetsRemaining -= actionPackets;
            actionsToSkip++;
        }

        return actionsToSkip;
    }
}
