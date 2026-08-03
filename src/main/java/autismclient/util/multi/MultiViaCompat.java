package autismclient.util.multi;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.Locale;

final class MultiViaCompat {
    record Target(boolean present, Object version, String label) {
    }

    private MultiViaCompat() {
    }

    static Target captureSelectedTarget() {
        if (!FabricLoader.getInstance().isModLoaded("viafabricplus")) return new Target(false, null, "Native");
        try {
            Class<?> apiClass = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object api = apiClass.getMethod("getImpl").invoke(null);
            Method targetMethod = api.getClass().getMethod("getTargetVersion");
            Object target = targetMethod.invoke(api);
            return new Target(true, target, String.valueOf(target));
        } catch (ReflectiveOperationException ignored) {
            return new Target(true, null, "ViaFabricPlus");
        }
    }

    static Target captureServerTarget(net.minecraft.client.multiplayer.ServerData serverData) {
        if (serverData == null || !FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            return new Target(false, null, "Native");
        }
        try {
            Class<?> apiClass = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object api = apiClass.getMethod("getImpl").invoke(null);
            Object target = api.getClass().getMethod("getServerVersion", net.minecraft.client.multiplayer.ServerData.class)
                .invoke(api, serverData);
            return new Target(true, target, String.valueOf(target));
        } catch (ReflectiveOperationException ignored) {
            return new Target(true, null, "ViaFabricPlus Auto Detect");
        }
    }

    static boolean isAutoDetect(Target target) {
        if (target == null) return false;
        String label = target.label().toLowerCase(Locale.ROOT);
        return label.contains("auto") && label.contains("detect");
    }

    static String validateSelectedTarget(Target target) {
        if (target == null || !target.present()) return "";
        String label = target.label().toLowerCase(Locale.ROOT);
        if (label.contains("bedrock")) return "Multi does not support ViaFabricPlus Bedrock targets";
        if (label.contains("classicube")) return "Multi supports offline Classic, not authenticated ClassiCube";
        return "";
    }

    static void applyTarget(net.minecraft.network.Connection connection, Target target) {
        if (connection == null || target == null || !target.present() || target.version() == null) return;
        if (isAutoDetect(target)) return;
        try {
            Class<?> access = Class.forName("com.viaversion.viafabricplus.injection.access.core.IConnection");
            Class<?> protocol = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            access.getMethod("viaFabricPlus$setTargetVersion", protocol).invoke(connection, target.version());
        } catch (ReflectiveOperationException ignored) {

        }
    }
}
