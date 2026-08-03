package autismclient.mixin;

import autismclient.mixin.accessor.AutismKeyboardHandlerAccessor;
import autismclient.mixin.accessor.AutismMouseHandlerAccessor;
import autismclient.util.AutismKeyMappingBridge;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(KeyMapping.class)
public abstract class AutismKeyMappingMixin implements AutismKeyMappingBridge {
    @Shadow
    protected InputConstants.Key key;

    @Shadow
    public abstract void setDown(boolean down);

    @Override
    @Unique
    public boolean autism$isActuallyDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        Window window = mc.getWindow();
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), code) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, code);
    }

    @Override
    @Unique
    public void autism$resetPressedState() {
        setDown(autism$isActuallyDown());
    }

    @Override
    @Unique
    public void autism$simulatePress(boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.keyboardHandler == null || mc.mouseHandler == null) return;
        Window window = mc.getWindow();
        int action = pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
        switch (key.getType()) {
            case KEYSYM -> ((AutismKeyboardHandlerAccessor) mc.keyboardHandler).autism$invokeKeyPress(
                window.handle(), action, new KeyEvent(key.getValue(), 0, 0)
            );
            case SCANCODE -> ((AutismKeyboardHandlerAccessor) mc.keyboardHandler).autism$invokeKeyPress(
                window.handle(), action, new KeyEvent(GLFW.GLFW_KEY_UNKNOWN, key.getValue(), 0)
            );
            case MOUSE -> ((AutismMouseHandlerAccessor) mc.mouseHandler).autism$invokeOnButton(
                window.handle(), new MouseButtonInfo(key.getValue(), 0), action
            );
            default -> setDown(pressed);
        }
    }
}
