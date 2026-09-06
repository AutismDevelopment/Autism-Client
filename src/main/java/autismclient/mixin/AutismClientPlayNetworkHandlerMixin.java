package autismclient.mixin;

import autismclient.commands.AutismCommands;
import autismclient.modules.AutismModule;
import autismclient.modules.InventoryTweaksModule;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class AutismClientPlayNetworkHandlerMixin {
    @Inject(method = "handleBlockChangedAck", at = @At("RETURN"))
    private void autism$onBlockPredictionAckApplied(
        ClientboundBlockChangedAckPacket packet, CallbackInfo ci
    ) {
        autismclient.modules.ScaffoldModule.onBlockChangedAckHandled(packet.sequence());
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
        shift = At.Shift.BEFORE))
    private void autism$observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        autismclient.modules.AntiVanishModule.observeSingleBlockUpdate(packet);
    }

    @Unique private boolean autism$viewCaptured;
    @Unique private float autism$viewYaw;
    @Unique private float autism$viewPitch;
    @Unique private float autism$viewYawO;
    @Unique private float autism$viewPitchO;

    @Unique
    private void autism$captureView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var wire = autismclient.modules.ScaffoldModule.wireContinuityRotation();
        if (wire == null) return;
        autism$viewYaw = mc.player.getYRot();
        autism$viewPitch = mc.player.getXRot();
        autism$viewYawO = mc.player.yRotO;
        autism$viewPitchO = mc.player.xRotO;
        autism$viewCaptured = true;

        mc.player.setYRot(wire.yaw());
        mc.player.setXRot(wire.pitch());
    }

    @Unique
    private void autism$restoreView(Minecraft mc) {
        if (!autism$viewCaptured) return;
        autism$viewCaptured = false;
        if (mc.player == null) return;

        autismclient.modules.ScaffoldModule.onServerRotationApplied(
            mc.player.getYRot(), mc.player.getXRot());
        mc.player.setYRot(autism$viewYaw);
        mc.player.setXRot(autism$viewPitch);
        mc.player.yRotO = autism$viewYawO;
        mc.player.xRotO = autism$viewPitchO;
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void autism$disarmViewCaptureMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        autism$viewCaptured = false;
    }

    @Inject(method = "handleRotatePlayer", at = @At("HEAD"))
    private void autism$disarmViewCaptureRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        autism$viewCaptured = false;
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private void autism$captureViewBeforeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        autism$captureView();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void autism$onServerPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        autismclient.util.SodiumTerrainPassGuard.armForPositionCorrection();
        Minecraft mc = Minecraft.getInstance();
        autism$restoreView(mc);
        if (mc.player != null) {
            autismclient.util.multi.PacketTeleportController.onMainCorrection(mc.player.position());
            autismclient.modules.ScaffoldModule.onServerPositionCorrection();
        }
    }

    @Inject(method = "handleRotatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;setYRot(F)V"))
    private void autism$captureViewBeforeRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        autism$captureView();
    }

    @Inject(method = "handleRotatePlayer", at = @At("RETURN"))
    private void autism$onServerRotationCorrection(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        autism$restoreView(Minecraft.getInstance());
    }

    @Inject(method = "handleMoveVehicle", at = @At("RETURN"))
    private void autism$onServerVehicleCorrection(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() != null) {
            autismclient.util.multi.PacketTeleportController.onMainVehicleCorrection(
                mc.player.getVehicle().position());
        }
    }

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void autism$onCommandTreeApplied(ClientboundCommandsPacket packet, CallbackInfo ci) {
        AutismModule module = AutismModule.get();
        if (module == null) return;
        var overlay = module.getServerDataOverlayIfExists();
        if (overlay != null) overlay.onCommandTreeChanged();
    }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void autism$interceptCardClick(String command, net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (autismclient.util.mm.MmCardActions.handleClickCommand(command)) ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$infiniChatSplit(String message, CallbackInfo ci) {
        if (!autismclient.util.AutismConfig.getGlobal().infiniChat
            || message == null || message.length() <= 256
            || (!AutismCommands.plainChatBypass() && AutismCommands.isAutismCommandMessage(message))) return;
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        for (int i = 0; i < message.length(); i += 256) {
            self.sendChat(message.substring(i, Math.min(message.length(), i + 256)));
        }
        ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$dispatchAutismCommand(String message, CallbackInfo ci) {
        try {

            if (AutismCommands.plainChatBypass()) return;
            if (!AutismCommands.isAutismCommandMessage(message)) return;
            if (AutismCommands.isBlockedPanicCommandMessage(message)) {
                ci.cancel();
                return;
            }
            String body = AutismCommands.commandBody(message);
            if (body.isBlank()) {
                ci.cancel();
                return;
            }

            autismclient.util.AutismClientMessaging.rememberRecentChat(message);
            AutismCommands.dispatch(body);
            ci.cancel();
        } catch (Throwable t) {

            autismclient.AutismClientAddon.LOG.warn("[Commands] sendChat interception failed for '{}'", message, t);
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void yang$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void yang$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onSlotUpdate(packet.getSlot());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.getContainerId());
    }

    @Inject(method = "handleSetCursorItem", at = @At("RETURN"))
    private void autism$onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        }
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("RETURN"))
    private void autism$onSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void yang$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        boolean macroWaits = MacroConditionRegistry.hasPendingSoundConditions();
        boolean moduleHooks = autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.SOUND);
        if (!macroWaits && !moduleHooks) return;
        if (macroWaits) autism$dispatchMacroSound(packet);
        if (moduleHooks) ModuleRegistry.onSoundPacket(packet);
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("RETURN"))
    private void autism$onPlayEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (!MacroConditionRegistry.hasPendingSoundConditions()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || packet == null) return;
            Entity entity = mc.level.getEntity(packet.getId());
            if (entity == null) return;
            String soundId = packet.getSound().value().location().toString();
            MacroConditionRegistry.onSoundPacket(soundId, entity.getX(), entity.getY(), entity.getZ());
        } catch (Exception ignored) {  }
    }

    @Inject(method = "handleSetTime", at = @At("RETURN"))
    private void yang$onWorldTimeUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (!autism$packetHooksActive()) return;
        AutismSharedState.get().onServerTimeSyncReceived();
    }

    @Unique
    private boolean autism$packetHooksActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private static void autism$dispatchMacroSound(ClientboundSoundPacket packet) {
        try {
            if (packet == null) return;
            String soundId = packet.getSound().value().location().toString();
            MacroConditionRegistry.onSoundPacket(soundId, packet.getX(), packet.getY(), packet.getZ());
        } catch (Exception ignored) {  }
    }
}
