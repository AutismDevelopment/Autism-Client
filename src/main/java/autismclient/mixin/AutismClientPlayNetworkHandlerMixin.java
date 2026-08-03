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
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class AutismClientPlayNetworkHandlerMixin {
    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
        shift = At.Shift.BEFORE))
    private void autism$observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        autismclient.modules.AntiVanishModule.observeSingleBlockUpdate(packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void autism$onServerPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        autismclient.util.SodiumTerrainPassGuard.armForPositionCorrection();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            autismclient.util.multi.PacketTeleportController.onMainCorrection(mc.player.position());
        }
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
        if (autismclient.util.mm.MmCardActions.handleClickCommand(command)) ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$infiniChatSplit(String message, CallbackInfo ci) {
        if (!autismclient.util.AutismConfig.getGlobal().infiniChat
            || message == null || message.length() <= 256
            || AutismCommands.isAutismCommandMessage(message)) return;
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        for (int i = 0; i < message.length(); i += 256) {
            self.sendChat(message.substring(i, Math.min(message.length(), i + 256)));
        }
        ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$dispatchAutismCommand(String message, CallbackInfo ci) {
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
