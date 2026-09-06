package autismclient.mixin;

import com.mojang.blaze3d.platform.Monitor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Monitor.class)
public class AutismMonitorMixin {
    @Redirect(
        method = "queryMonitorName",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetMonitorName(J)Ljava/lang/String;"),
        require = 0
    )
    private static String autism$safeMonitorName(long monitor) {
        try {
            Object name = GLFW.class.getMethod("glfwGetMonitorName", long.class).invoke(null, monitor);
            if (name instanceof String text && !text.isEmpty()) return text;
        } catch (Throwable ignored) {

        }
        return "unknown";
    }
}
