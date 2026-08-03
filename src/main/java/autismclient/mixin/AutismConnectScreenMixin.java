package autismclient.mixin;

import autismclient.modules.PackAutoReconnectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class AutismConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void autism$rememberConnectAttempt(Screen parent, Minecraft minecraft, ServerAddress hostAndPort,
                                                      ServerData data, boolean isQuickPlay, TransferState transferState,
                                                      CallbackInfo ci) {

        PackAutoReconnectState.remember(data, hostAndPort);

        try {
            autismclient.util.AutismProfileManager.get().applyForServerConnect(hostAndPort, data);
        } catch (Throwable t) {
            autismclient.AutismClientAddon.LOG.error("Profiles: failed to apply on-connect profile", t);
        }
    }
}
