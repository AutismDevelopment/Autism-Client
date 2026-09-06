package autismclient.mixin;

import autismclient.commands.AutismCommands;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class AutismChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void autism$infiniChatMaxLength(CallbackInfo ci) {
        if (input != null && AutismConfig.getGlobal().infiniChat) {
            input.setMaxLength(Integer.MAX_VALUE);
        }
    }

    @Inject(method = "normalizeChatMessage", at = @At("HEAD"), cancellable = true)
    private void autism$infiniChatNoTruncate(String message, CallbackInfoReturnable<String> cir) {
        if (AutismConfig.getGlobal().infiniChat) {
            cir.setReturnValue(StringUtils.normalizeSpace(message.trim()));
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void yang$onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;
        if (AutismCommands.isBlockedPanicCommandMessage(trimmed)) {
            ci.cancel();
            return;
        }

        if ("^toggleautism".equalsIgnoreCase(trimmed)) {
            if (PackHideState.isActive()) {
                ci.cancel();
                return;
            }
            AutismModule module = AutismModule.get();
            module.toggle();
            Minecraft mc = Minecraft.getInstance();
            AutismClientMessaging.sendPrefixed("Autism is now " + (module.isActive() ? "enabled" : "disabled") + ".");
            mc.gui.setScreen(null);
            ci.cancel();
        }
    }
}
