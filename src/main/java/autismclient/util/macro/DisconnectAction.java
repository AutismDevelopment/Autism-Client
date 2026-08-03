package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInventoryHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.network.HashedStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DisconnectAction implements MacroAction {
    private boolean enabled = true;
    public int delayMs = 0;

    public DisconnectMode mode = DisconnectMode.DISCONNECT;
    public LagMethod lagMethod = LagMethod.CLICK_SLOT;
    public KickMethod kickMethod = KickMethod.HURT;
    public int packetCount = 200;
    public boolean useNextAction = false;
    private transient List<MacroAction> nextActions = null;

    public AutoTrigger trigger = AutoTrigger.TELEPORT;
    public double targetX = 0;
    public double targetY = 0;
    public double targetZ = 0;
    public double tolerance = 10.0;
    public int bufferMs = 50;
    public int timeoutSec = 60;
    public boolean useExactPosition = false;

    public enum DisconnectMode { DISCONNECT, KICK, KICK_DUPE, AUTO_DISCONNECT }
    public enum LagMethod { CLICK_SLOT, BOAT_NBT, ENTITY_NBT }
    public enum KickMethod { HURT, CLIENT_SETTINGS, INVALID_SLOT }
    public enum AutoTrigger {
        TELEPORT,
        POSITION,
        WORLD_CHANGE,
        GUI_CLOSE,
        INVENTORY_CLEAR
    }

    public DisconnectAction() {}

    @Override
    public void execute(Minecraft mc) {
        switch (mode) {
            case DISCONNECT -> executeDisconnect(mc);
            case KICK -> executeKick(mc);
            case KICK_DUPE -> executeKickDupe(mc);
            case AUTO_DISCONNECT -> executeAutoDisconnect(mc);
        }
    }

    private void executeDisconnect(Minecraft mc) {
        if (mc.level != null && mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("Disconnected by Macro"));
        }
    }

    private void executeAutoDisconnect(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo player or network connection!");
            return;
        }

        AutismClientMessaging.sendPrefixed("§eAuto Disconnect: Monitoring for " + trigger.name() + "...");

        String initialWorld = mc.level != null ? mc.level.dimension().identifier().toString() : "";
        double initialX = mc.player.getX();
        double initialY = mc.player.getY();
        double initialZ = mc.player.getZ();
        Screen initialScreen = mc.gui.screen();
        boolean initialScreenOpen = mc.gui.screen() != null;
        boolean initialInventoryEmpty = isMainInventoryEmpty(mc);

        long startTime = System.currentTimeMillis();
        boolean triggered = false;
        long lastPositionCheck = startTime;
        Screen prevScreen = initialScreen;

        while (!triggered && MacroExecutor.isCurrentActionRunActive() && (System.currentTimeMillis() - startTime) < timeoutSec * 1000L) {
            if (mc.player == null || mc.getConnection() == null) {
                AutismClientMessaging.sendPrefixed("§cConnection lost during wait!");
                return;
            }

            switch (trigger) {
                case TELEPORT -> {

                    long now = System.currentTimeMillis();
                    if (now - lastPositionCheck >= 50) {
                        double dist = Math.sqrt(
                            Math.pow(mc.player.getX() - initialX, 2) +
                            Math.pow(mc.player.getY() - initialY, 2) +
                            Math.pow(mc.player.getZ() - initialZ, 2)
                        );
                        if (dist > 5.0) {
                            AutismClientMessaging.sendPrefixed(String.format("§aTeleport detected! Jumped %.1f blocks.", dist));
                            triggered = true;
                        }
                        if (!triggered && mc.level != null) {
            String cw = mc.level.dimension().identifier().toString();
                            if (!cw.isEmpty() && !cw.equals(initialWorld)) {
                                AutismClientMessaging.sendPrefixed("§aWorld change detected during teleport!");
                                triggered = true;
                            }
                        }
                        initialX = mc.player.getX();
                        initialY = mc.player.getY();
                        initialZ = mc.player.getZ();
                        lastPositionCheck = now;
                    }
                }
                case POSITION -> {

                    double dx = mc.player.getX() - initialX;
                    double dy = mc.player.getY() - initialY;
                    double dz = mc.player.getZ() - initialZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > tolerance) {
                        AutismClientMessaging.sendPrefixed(String.format(
                            "§aPosition jump detected! Moved %.1f blocks (tolerance: %.0f)", dist, tolerance));
                        triggered = true;
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastPositionCheck >= 500) {
                        initialX = mc.player.getX();
                        initialY = mc.player.getY();
                        initialZ = mc.player.getZ();
                        lastPositionCheck = now;
                    }
                }
                case WORLD_CHANGE -> {

        String currentWorld = mc.level != null ? mc.level.dimension().identifier().toString() : "";
                    if (!currentWorld.equals(initialWorld) && !currentWorld.isEmpty()) {
                        AutismClientMessaging.sendPrefixed("§aWorld change detected!");
                        triggered = true;
                    }
                }
                case GUI_CLOSE -> {

                    Screen curScreen = mc.gui.screen();
                    if (prevScreen != null && curScreen == null) {
                        AutismClientMessaging.sendPrefixed("§aGUI close detected!");
                        triggered = true;
                    }
                    prevScreen = curScreen;
                }
                case INVENTORY_CLEAR -> {

                    if (!initialInventoryEmpty && isMainInventoryEmpty(mc)) {
                        AutismClientMessaging.sendPrefixed("§aInventory clear detected!");
                        triggered = true;
                    }
                }
            }

            if (!triggered) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        if (!triggered) {
            AutismClientMessaging.sendPrefixed("§cAuto Disconnect: Timeout after " + timeoutSec + "s");
            return;
        }

        if (bufferMs > 0) {
            AutismClientMessaging.sendPrefixed("§eBuffer delay: " + bufferMs + "ms");
            try {
                Thread.sleep(bufferMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        AutismClientMessaging.sendPrefixed("§cExecuting disconnect!");
        executeDisconnect(mc);
    }

    private boolean isMainInventoryEmpty(Minecraft mc) {
        if (mc.player == null) return true;
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private void executeKick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo player or network connection!");
            return;
        }

        if (!validateLagMethod(mc)) return;

        sendLagPackets(mc);
        sendKickPacket(mc);
        sendLagPackets(mc);
        AutismClientMessaging.sendPrefixed("§aKick executed!");
    }

    public void setNextActions(List<MacroAction> actions) {
        this.nextActions = actions;
    }

    private void executeKickDupe(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNo player or network connection!");
            return;
        }

        if (!validateLagMethod(mc)) return;

        if (useNextAction) {

            List<MacroAction> actions = nextActions;
            nextActions = null;
            if (actions == null || actions.isEmpty()) {
                AutismClientMessaging.sendPrefixed("§cKick Dupe: no next action in macro! Add action(s) after this one.");
                return;
            }
            sendLagPackets(mc);

            for (MacroAction act : actions) {
                try {
                    act.execute(mc);
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("§cError in dupe action '" + act.getDisplayName() + "': " + e.getMessage());
                }
            }
            sendKickPacket(mc);
            sendLagPackets(mc);
            String actNames = actions.size() == 1
                ? actions.get(0).getDisplayName()
                : actions.size() + " actions";
            AutismClientMessaging.sendPrefixed("§aKick Dupe executed! (" + actNames + ")");
        } else {

            sendLagPackets(mc);
            if (!findAndUseBundle(mc)) return;
            sendKickPacket(mc);
            sendLagPackets(mc);
            AutismClientMessaging.sendPrefixed("§aKick Dupe executed! (bundle)");
        }
    }

    private boolean findAndUseBundle(Minecraft mc) {

        InteractionHand activeInteractionHand = null;
        if (!mc.player.getMainHandItem().isEmpty() && BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().endsWith("bundle")) {
            activeInteractionHand = InteractionHand.MAIN_HAND;
        } else if (!mc.player.getOffhandItem().isEmpty() && BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).getPath().endsWith("bundle")) {
            activeInteractionHand = InteractionHand.OFF_HAND;
        }

        if (activeInteractionHand != null) {
            mc.getConnection().send(
                new ServerboundUseItemPacket(activeInteractionHand, 0, mc.player.getYRot(), mc.player.getXRot()));
            return true;
        }

        int bundleSlot = -1;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("bundle")) {
                bundleSlot = i;
                break;
            }
        }

        if (bundleSlot < 0) {
            AutismClientMessaging.sendPrefixed("§cNo bundle found in inventory or hands!");
            return false;
        }

        int hotbarSlot = bundleSlot;
        if (bundleSlot > 8) {
            int target = 0;
            for (int i = 0; i <= 8; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    target = i;
                    break;
                }
            }
            AutismInventoryHelper.swapInventorySlots(mc, bundleSlot, target);
            hotbarSlot = target;
        }

        AutismInventoryHelper.selectHotbarSlot(mc, hotbarSlot);
        mc.getConnection().send(
            new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
        return true;
    }

    private boolean validateLagMethod(Minecraft mc) {
        switch (lagMethod) {
            case BOAT_NBT -> {
                Entity vehicle = mc.player.getVehicle();
                if (!(vehicle instanceof ChestBoat) && !(vehicle instanceof AbstractMinecartContainer)) {
                    AutismClientMessaging.sendPrefixed("§cYou must be in a Chest Boat or Minecart with Chest for BoatNBT!");
                    return false;
                }
            }
            case ENTITY_NBT -> {
                if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
                    AutismClientMessaging.sendPrefixed("§cYou must be looking at an entity for EntityNBT!");
                    return false;
                }
                Entity target = ((EntityHitResult) mc.hitResult).getEntity();
                if (!(target instanceof ChestBoat) && !(target instanceof AbstractMinecartContainer)) {
                    AutismClientMessaging.sendPrefixed("§cTarget must be a Chest Boat or Minecart with Chest!");
                    return false;
                }
            }
            default -> {}
        }
        return true;
    }

    private void sendLagPackets(Minecraft mc) {
        switch (lagMethod) {
            case CLICK_SLOT -> {
                for (int i = 0; i < packetCount; i++) {
                    mc.getConnection().send(
                        new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ContainerInput.PICKUP, new Int2ObjectArrayMap<>(), HashedStack.EMPTY));
                }
            }
            case BOAT_NBT -> {
                for (int i = 0; i < packetCount; i++) {
                    mc.getConnection().send(
                        new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
                }
            }
            case ENTITY_NBT -> {
                if (mc.hitResult instanceof EntityHitResult ehr) {
                    Entity target = ehr.getEntity();
                    for (int i = 0; i < packetCount; i++) {
                        mc.getConnection().send(
                            new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, new net.minecraft.world.phys.Vec3(target.getX(), target.getY(), target.getZ()), true));
                    }
                }
            }
        }
    }

    private void sendKickPacket(Minecraft mc) {
        switch (kickMethod) {
            case HURT -> mc.getConnection().send(
                new ServerboundInteractPacket(mc.player.getId(), InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO, false));
            case CLIENT_SETTINGS -> {
                ClientInformation opts = new ClientInformation(
                    mc.options.languageCode,
                    -2,
                    ChatVisiblity.FULL,
                    mc.options.chatColors().get(),
                    127,
                    mc.options.mainHand().get(),
                    false,
                    false,
                    mc.options.particles().get());
                mc.getConnection().send(new ServerboundClientInformationPacket(opts));
            }
            case INVALID_SLOT -> mc.getConnection().send(new ServerboundSetCarriedItemPacket(-1));
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("delayMs", delayMs);
        tag.putString("mode", mode.name());
        tag.putString("lagMethod", lagMethod.name());
        tag.putString("kickMethod", kickMethod.name());
        tag.putInt("packetCount", packetCount);
        tag.putBoolean("useNextAction", useNextAction);
        tag.putBoolean("enabled", enabled);

        tag.putString("trigger", trigger.name());
        tag.putDouble("targetX", targetX);
        tag.putDouble("targetY", targetY);
        tag.putDouble("targetZ", targetZ);
        tag.putDouble("tolerance", tolerance);
        tag.putInt("bufferMs", bufferMs);
        tag.putInt("timeoutSec", timeoutSec);
        tag.putBoolean("useExactPosition", useExactPosition);

        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("delayMs")) delayMs = tag.getIntOr("delayMs", 0);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        if (tag.contains("mode")) {
            try { mode = DisconnectMode.valueOf(tag.getStringOr("mode", "DISCONNECT")); }
            catch (IllegalArgumentException ignored) { mode = DisconnectMode.DISCONNECT; }
        }
        if (tag.contains("lagMethod")) {
            try { lagMethod = LagMethod.valueOf(tag.getStringOr("lagMethod", "CLICK_SLOT")); }
            catch (IllegalArgumentException ignored) { lagMethod = LagMethod.CLICK_SLOT; }
        }
        if (tag.contains("kickMethod")) {
            try { kickMethod = KickMethod.valueOf(tag.getStringOr("kickMethod", "HURT")); }
            catch (IllegalArgumentException ignored) { kickMethod = KickMethod.HURT; }
        }
        if (tag.contains("packetCount")) packetCount = tag.getIntOr("packetCount", 200);
        if (tag.contains("useNextAction")) useNextAction = tag.getBooleanOr("useNextAction", false);

        if (tag.contains("trigger")) {
            try { trigger = AutoTrigger.valueOf(tag.getStringOr("trigger", "TELEPORT")); }
            catch (IllegalArgumentException ignored) { trigger = AutoTrigger.TELEPORT; }
        }
        if (tag.contains("targetX")) targetX = tag.getDoubleOr("targetX", 0);
        if (tag.contains("targetY")) targetY = tag.getDoubleOr("targetY", 0);
        if (tag.contains("targetZ")) targetZ = tag.getDoubleOr("targetZ", 0);
        if (tag.contains("tolerance")) tolerance = tag.getDoubleOr("tolerance", 2.0);
        if (tag.contains("bufferMs")) bufferMs = tag.getIntOr("bufferMs", 50);
        if (tag.contains("timeoutSec")) timeoutSec = tag.getIntOr("timeoutSec", 60);
        if (tag.contains("useExactPosition")) useExactPosition = tag.getBooleanOr("useExactPosition", false);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.DISCONNECT;
    }

    @Override
    public String getDisplayName() {
        return switch (mode) {
            case DISCONNECT -> delayMs > 0 ? "Disconnect (" + delayMs + "ms)" : "Disconnect";
            case KICK -> "Kick (" + lagMethod.name() + ", " + kickMethod.name() + ", " + packetCount + ")";
            case KICK_DUPE -> {
                String action = useNextAction ? "Next Actions" : "Bundle";
                yield "Kick Dupe (" + lagMethod.name() + ", " + kickMethod.name() + ", " + packetCount + ", " + action + ")";
            }
            case AUTO_DISCONNECT -> {
                String triggerInfo = switch (trigger) {
                    case TELEPORT -> "Loading Screen";
                    case POSITION -> String.format("Pos Jump > %.0f blocks", tolerance);
                    case WORLD_CHANGE -> "World Change";
                    case GUI_CLOSE -> "GUI Close";
                    case INVENTORY_CLEAR -> "Inv Clear";
                };
                yield "Auto DC (" + triggerInfo + ", +" + bufferMs + "ms)";
            }
        };
    }

    @Override
    public String getIcon() {
        return mode == DisconnectMode.AUTO_DISCONNECT ? "ADC" : "DC";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
