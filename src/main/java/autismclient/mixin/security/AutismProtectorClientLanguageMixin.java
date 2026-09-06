package autismclient.mixin.security;

import autismclient.security.AutismProtectorTracker;
import autismclient.security.AutismProtectorModResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public class AutismProtectorClientLanguageMixin {
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void autism$clearLanguageTracking(ResourceManager resourceManager, List<String> languageStack,
                                                     boolean defaultRightToLeft,
                                                     CallbackInfoReturnable<ClientLanguage> cir) {
        AutismProtectorTracker.resetTranslations();
    }

    @WrapOperation(
        method = "appendFrom",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"))
    private static void autism$trackTranslations(InputStream stream, BiConsumer<String, String> output,
                                                 Operation<Void> original, @Local Resource resource) {
        PackResources source = resource.source();
        if (source instanceof VanillaPackResources) {
            original.call(stream, trackingOutput(output, (key, value) -> AutismProtectorTracker.addVanillaTranslation(key)));
            return;
        }
        if (source instanceof FilePackResources || source instanceof CompositePackResources) {
            original.call(stream, trackingOutput(output, AutismProtectorTracker::addServerTranslation));
            return;
        }
        if (source instanceof PathPackResources) {
            original.call(stream, output);
            return;
        }
        String modId = source instanceof ModNioPackResources modPack
            ? modPack.getFabricModMetadata().getId()
            : AutismProtectorModResolver.modFromClass(source.getClass());
        if (modId == null) {
            original.call(stream, output);
            return;
        }
        original.call(stream, trackingOutput(output, (key, value) -> AutismProtectorTracker.addModTranslation(key, modId)));
    }

    private static BiConsumer<String, String> trackingOutput(BiConsumer<String, String> output, BiConsumer<String, String> tracker) {
        return (key, value) -> {
            tracker.accept(key, value);
            output.accept(key, value);
        };
    }
}
