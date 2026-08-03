package autismclient.mixin.security;

import autismclient.security.AutismFromPacketAccess;
import autismclient.security.AutismProtector;
import autismclient.security.AutismProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public abstract class AutismProtectorTranslatableContentsMixin implements AutismFromPacketAccess {

    @Unique
    private boolean autism$fromPacket;

    @Unique
    private boolean autism$silent;

    @Override
    public void autism$setFromPacket() {
        this.autism$fromPacket = true;
    }

    @Override
    public void autism$setSilent() {
        this.autism$silent = true;
    }

    @Unique
    private static final String AUTISM_ALLOW = "\0__autism_allow__";

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String autism$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
        String result = autism$handle(id, id);
        if (result == AUTISM_ALLOW) return original.call(instance, id);
        return result;
    }

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    )
    private String autism$wrapGetOrDefaultFallback(Language instance, String idArg, String defaultValue,
                                                   Operation<String> original) {
        String result = autism$handle(idArg, defaultValue);
        if (result == AUTISM_ALLOW) return original.call(instance, idArg, defaultValue);
        return result;
    }

    @Unique
    private String autism$handle(String translationKey, String defaultValue) {

        if (autism$silent) return AUTISM_ALLOW;
        if (!this.autism$fromPacket) return AUTISM_ALLOW;
        if (!AutismProtector.shouldProtectTranslationKeys()) return AUTISM_ALLOW;

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return AUTISM_ALLOW;
        }
        if (mc == null || mc.hasSingleplayerServer()) return AUTISM_ALLOW;

        String replacement = AutismProtectorTracker.translationReplacement(translationKey, defaultValue);
        return replacement == null ? AUTISM_ALLOW : replacement;
    }
}
