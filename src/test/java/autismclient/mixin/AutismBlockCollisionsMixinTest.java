package autismclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.BlockCollisions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismBlockCollisionsMixinTest {
    @Test
    void ghostBlockInjectionTargetsAnInvocationThatExistsInComputeNext() throws IOException {
        Method hook = null;
        for (Method candidate : AutismBlockCollisionsMixin.class.getDeclaredMethods()) {
            if (candidate.getName().equals("autism$ghostBlockShape")) {
                hook = candidate;
                break;
            }
        }
        assertNotNull(hook, "Ghost-block Mixin hook is missing");

        ModifyExpressionValue injection = hook.getAnnotation(ModifyExpressionValue.class);
        assertNotNull(injection, "Ghost-block hook is missing its injector annotation");
        At[] injectionPoints = injection.at();
        assertTrue(injectionPoints.length > 0, "Ghost-block injector has no target");
        String expectedTarget = injectionPoints[0].target();

        AtomicBoolean targetFound = new AtomicBoolean();
        String classResource = "/" + BlockCollisions.class.getName().replace('.', '/') + ".class";
        try (InputStream input = BlockCollisions.class.getResourceAsStream(classResource)) {
            assertNotNull(input, "Minecraft BlockCollisions bytecode is unavailable");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("computeNext")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            String target = "L" + owner + ";" + name + descriptor;
                            if (target.equals(expectedTarget)) targetFound.set(true);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(targetFound.get(), () -> "Ghost-block injector target is absent from "
            + "BlockCollisions.computeNext: " + expectedTarget);
    }
}
