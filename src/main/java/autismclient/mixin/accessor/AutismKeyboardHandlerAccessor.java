package autismclient.mixin.accessor;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface AutismKeyboardHandlerAccessor {
    @Invoker("keyPress")
    void autism$invokeKeyPress(long handle, int action, KeyEvent event);
}
