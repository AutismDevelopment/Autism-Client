package autismclient.mixin.security;

import autismclient.security.AutismProtectorModResolver;
import autismclient.security.AutismProtectorTracker;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;

@Mixin(targets = "net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl")
public class AutismProtectorKeyMappingRegistryImplMixin {
    @Inject(method = "registerKeyMapping", at = @At("RETURN"))
    private static void autism$trackModKeyMapping(KeyMapping keyMapping, CallbackInfoReturnable<KeyMapping> cir) {
        LinkedHashSet<String> mods = AutismProtectorModResolver.modsFromStacktrace();
        if (!mods.isEmpty()) AutismProtectorTracker.addModKeybind(keyMapping.getName(), mods.getLast());
    }
}
