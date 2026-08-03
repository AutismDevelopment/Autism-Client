package autismclient.mixin.security;

import autismclient.security.AutismFromPacketAccess;
import autismclient.security.AutismProtector;
import autismclient.security.AutismProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(KeybindContents.class)
public abstract class AutismProtectorKeybindContentsMixin implements AutismFromPacketAccess {

    @Shadow @Final private String name;

    @Unique
    private boolean autism$fromPacket;

    @Unique
    private Object autism$cachedBlocked;

    @Override
    public void autism$setFromPacket() {
        this.autism$fromPacket = true;
    }

    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object autism$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!this.autism$fromPacket) return original.call(supplier);
        if (!AutismProtector.shouldProtectTranslationKeys()) return original.call(supplier);

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return original.call(supplier);
        }
        if (mc == null || mc.hasSingleplayerServer()) return original.call(supplier);

        if (!AutismProtectorTracker.shouldBlockKeybind(name)) return original.call(supplier);

        if (autism$cachedBlocked != null) return autism$cachedBlocked;
        Component replacement = Component.literal(name);
        autism$cachedBlocked = replacement;
        return replacement;
    }
}
