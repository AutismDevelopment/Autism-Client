package autismclient.util;

import autismclient.AutismClientAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AutismWelcomeGate {
    private AutismWelcomeGate() {
    }

    public static boolean shouldShow(AutismConfig config) {
        if (config == null) return false;
        String current = currentInstallIdentity();
        String shown = config.welcomeInstallIdentity == null ? "" : config.welcomeInstallIdentity;
        return !current.equals(shown);
    }

    public static void markShown(AutismConfig config) {
        if (config == null) return;
        config.welcomeShown = true;
        config.welcomeInstallIdentity = currentInstallIdentity();
        config.save();
    }

    private static String currentInstallIdentity() {
        try {
            Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer(AutismClientAddon.MOD_ID);
            if (optional.isEmpty()) return AutismClientAddon.MOD_ID + ":unknown";
            ModContainer container = optional.get();
            StringBuilder id = new StringBuilder();
            id.append(container.getMetadata().getId())
                .append(':')
                .append(container.getMetadata().getVersion().getFriendlyString());
            for (Path path : container.getOrigin().getPaths()) {
                appendPathIdentity(id, path);
            }
            return id.toString();
        } catch (Throwable ignored) {
            return AutismClientAddon.MOD_ID + ":unknown";
        }
    }

    private static void appendPathIdentity(StringBuilder id, Path path) {
        if (path == null) return;
        try {
            Path normalized = path.toAbsolutePath().normalize();
            id.append('|').append(normalized.getFileName());
            if (Files.isRegularFile(normalized)) {
                id.append(':').append(Files.size(normalized))
                    .append(':').append(Files.getLastModifiedTime(normalized).toMillis());
            } else if (Files.isDirectory(normalized)) {
                id.append(":dir:").append(Files.getLastModifiedTime(normalized).toMillis());
            }
        } catch (Throwable ignored) {
            id.append('|').append(path);
        }
    }
}
