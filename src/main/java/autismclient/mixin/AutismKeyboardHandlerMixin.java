package autismclient.mixin;

import autismclient.commands.AutismCommands;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class AutismKeyboardHandlerMixin {

    private static long autism$lastScreenSeenMs;

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void autism$prefixOpensChat(long window, CharacterEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || event == null || mc.player == null) return;
        if (mc.gui.screen() != null) {
            autism$lastScreenSeenMs = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - autism$lastScreenSeenMs < 250L) return;
        String prefix = AutismCommands.effectivePrefix();
        if (prefix == null || prefix.length() != 1 || "/".equals(prefix)) return;
        if (event.codepoint() != prefix.charAt(0)) return;
        mc.gui.setScreen(new ChatScreen(prefix, false));
        autism$lastScreenSeenMs = System.currentTimeMillis();
        ci.cancel();
    }
}
