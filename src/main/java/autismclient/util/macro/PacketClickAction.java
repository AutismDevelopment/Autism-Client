package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismContainerHold;
import autismclient.util.AutismPacketClick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

public class PacketClickAction implements MacroAction {
    public AutismPacketClick.Target target;
    public String mode = AutismPacketClick.Mode.LEFT_CLICK.name();
    public int times = 1;
    public boolean queue = false;

    private transient boolean holdsContainer = false;

    public PacketClickAction() {}

    public PacketClickAction(AutismPacketClick.Target target, int times, boolean queue) {
        this.target = target;
        this.times = Math.max(1, times);
        this.queue = queue;
        acquireHold();
    }

    public void setTarget(AutismPacketClick.Target newTarget) {
        releaseHoldNoFlush();
        this.target = newTarget == null ? null : newTarget.withMode(effectiveMode());
        acquireHold();
    }

    private AutismPacketClick.Mode effectiveMode() {
        if (target != null && (mode == null || mode.isBlank())) {
            return target.mode() == null ? AutismPacketClick.Mode.LEFT_CLICK : target.mode();
        }
        return AutismPacketClick.Mode.fromName(mode);
    }

    private void acquireHold() {
        if (target == null || holdsContainer) return;
        AutismContainerHold.hold(target.containerId());
        holdsContainer = true;
    }

    private void releaseHold(ClientPacketListener conn) {
        if (!holdsContainer || target == null) {
            holdsContainer = false;
            return;
        }
        AutismContainerHold.release(target.containerId(), conn);
        holdsContainer = false;
    }

    private void releaseHoldNoFlush() {
        if (!holdsContainer || target == null) {
            holdsContainer = false;
            return;
        }
        AutismContainerHold.release(target.containerId(), null);
        holdsContainer = false;
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo network connection!");
            return;
        }
        if (target == null) {
            AutismClientMessaging.sendPrefixed("§cPacket Click has no captured target.");
            return;
        }

        int count = Math.max(1, times);
        AutismPacketClick.Target effectiveTarget = target.withMode(effectiveMode());
        for (int i = 0; i < count; i++) {
            ServerboundContainerClickPacket packet = effectiveTarget.buildPacket();
            if (queue) {
                autismclient.util.AutismSharedState.get().enqueueExactPacket(packet);
            } else {
                autismclient.util.AutismSharedState.get().sendPacketBypassDelay(mc.getConnection(), packet);
            }
        }

        releaseHold(mc.getConnection());
    }

    public void cancelHold() {
        releaseHoldNoFlush();
    }

    public void releasePendingClose(ClientPacketListener conn) {
        releaseHold(conn);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("mode", effectiveMode().name());
        tag.putInt("times", Math.max(1, times));
        tag.putBoolean("queue", queue);
        if (target != null) tag.put("target", target.withMode(effectiveMode()).toTag());
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        times = Math.max(1, tag.getIntOr("times", 1));
        queue = tag.getBooleanOr("queue", false);
        mode = tag.getStringOr("mode", "");
        target = tag.getCompound("target").map(AutismPacketClick.Target::fromTag).orElse(null);
        if (mode == null || mode.isBlank()) {
            mode = target == null || target.mode() == null ? AutismPacketClick.Mode.LEFT_CLICK.name() : target.mode().name();
        }
        if (target != null) target = target.withMode(effectiveMode());
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PACKET_CLICK;
    }

    @Override
    public String getDisplayName() {
        if (target == null) return "Packet Click (empty)";
        String suffix = times > 1 ? " x" + times : "";
        return "Packet Click " + target.withMode(effectiveMode()).summary() + suffix;
    }

    @Override
    public String getIcon() {
        return "Pkt";
    }
}
