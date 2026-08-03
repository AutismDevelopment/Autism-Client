package autismclient.mixin;

import autismclient.util.multi.MultiPovChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class AutismMultiPovChatMixin {
    @Unique private ChatComponent.State autism$povMessageState;
    @Unique private ChatComponent.State autism$povDeleteState;
    @Unique private ChatComponent.State autism$povClearState;

    @Inject(method = "addMessage", at = @At("HEAD"))
    private void autism$beforeMainChatMessage(Component content, MessageSignature signature,
                                               GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        autism$povMessageState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "addMessage", at = @At("RETURN"))
    private void autism$afterMainChatMessage(Component content, MessageSignature signature,
                                              GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatComponent.State state = autism$povMessageState;
        autism$povMessageState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"))
    private void autism$beforeMainChatDelete(MessageSignature signature, CallbackInfo ci) {
        autism$povDeleteState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "deleteMessage", at = @At("RETURN"))
    private void autism$afterMainChatDelete(MessageSignature signature, CallbackInfo ci) {
        ChatComponent.State state = autism$povDeleteState;
        autism$povDeleteState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void autism$beforeMainChatClear(boolean clearHistory, CallbackInfo ci) {
        autism$povClearState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "clearMessages", at = @At("RETURN"))
    private void autism$afterMainChatClear(boolean clearHistory, CallbackInfo ci) {
        ChatComponent.State state = autism$povClearState;
        autism$povClearState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }
}
