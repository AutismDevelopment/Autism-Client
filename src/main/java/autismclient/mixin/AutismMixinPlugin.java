package autismclient.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AutismMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> SODIUM_MIXINS = Set.of(
        "AutismSodiumBlockRendererMixin",
        "AutismSodiumBlockContextMixin",
        "AutismSodiumLightDataAccessMixin",
        "AutismSodiumDefaultFluidRendererMixin",
        "AutismSodiumFluidRendererImplMixin",
        "AutismSodiumRenderSectionManagerMixin",
        "AutismSodiumDefaultChunkRendererMixin"
    );

    private boolean sodiumLoaded;
    private boolean indigoLoaded;
    private boolean opsecLoaded;
    private boolean exploitPreventerLoaded;
    private boolean replayModLoaded;
    private boolean flashbackLoaded;
    private boolean essentialLoaded;
    private boolean lithiumLoaded;

    private static final Set<String> EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS = Set.of(
        "AutismProtectorTranslatableContentsMixin",
        "AutismProtectorKeybindContentsMixin",
        "AutismProtectorComponentSerializationMixin",
        "AutismProtectorPacketProcessorMixin",
        "AutismProtectorPacketDecoderMixin",
        "AutismProtectorHttpUtilMixin",
        "AutismProtectorConnectionTrackingMixin",
        "AutismProtectorDownloadQueueMixin",
        "AutismProtectorClientLanguageMixin",
        "AutismProtectorDeprecatedTranslationsInfoMixin",
        "AutismProtectorOptionsMixin",
        "AutismProtectorKeyMappingRegistryImplMixin",
        "AutismProtectorPayloadTypeRegistryImplMixin",
        "AutismProtectorResourceLoaderImplMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        sodiumLoaded = loader.isModLoaded("sodium");
        indigoLoaded = loader.isModLoaded("fabric-renderer-indigo");
        opsecLoaded = loader.isModLoaded("opsec");
        exploitPreventerLoaded = loader.isModLoaded("exploitpreventer");
        replayModLoaded = loader.isModLoaded("replaymod");
        flashbackLoaded = loader.isModLoaded("flashback");
        essentialLoaded = loader.isModLoaded("essential")
            || loader.isModLoaded("essential-container")
            || loader.isModLoaded("essential-loader");
        lithiumLoaded = loader.isModLoaded("lithium");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (SODIUM_MIXINS.contains(simpleName)) return sodiumLoaded;
        if ("AutismFluidRendererMixin".equals(simpleName) && sodiumLoaded) return false;
        if ("AutismReplayModGuiHandlerMixin".equals(simpleName) || "AutismReplayStudioTeamMixin".equals(simpleName)) return replayModLoaded;
        if ("AutismFlashbackConnectionMixin".equals(simpleName)) return flashbackLoaded;
        if (simpleName.startsWith("AutismEssential")) return essentialLoaded;

        if (mixinClassName.startsWith("autismclient.mixin.indigo.")) return indigoLoaded && !sodiumLoaded;
        if (mixinClassName.startsWith("autismclient.mixin.lithium.")) return lithiumLoaded;

        if (mixinClassName.startsWith("autismclient.mixin.security.")) {
            if (opsecLoaded) return false;
            return !exploitPreventerLoaded || !EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS.contains(simpleName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
