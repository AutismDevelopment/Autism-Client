package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class AutismPilotChatMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$pilotChat(String content, CallbackInfo ci) {
        if (autism$reroute(content)) ci.cancel();
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void autism$pilotCommand(String command, CallbackInfo ci) {
        if (autism$reroute("/" + command)) ci.cancel();
    }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void autism$pilotUnattendedCommand(String command, Screen screenAfter, CallbackInfo ci) {
        if (autism$reroute("/" + command)) ci.cancel();
    }

    private boolean autism$reroute(String line) {
        if (!MultiPilot.isActive()) return false;

        if ((Object) this != Minecraft.getInstance().getConnection()) return false;
        return MultiPilot.rerouteChat(line);
    }
}
