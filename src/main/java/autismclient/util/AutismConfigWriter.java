package autismclient.util;

import autismclient.AutismClientAddon;

final class AutismConfigWriter {
    private static final String CONFIG_KEY = "config:" + AutismConfig.configFile().getAbsolutePath();

    private AutismConfigWriter() {
    }

    static void request(AutismConfig config) {
        SaveCoordinator.requestConfigSave(config);
    }

    static void captureAndEnqueue(AutismConfig source) {
        final AutismConfig snapshot;
        try {
            snapshot = AutismConfigSnapshot.copyForPersistence(source);
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to capture Autism config", t);
            return;
        }
        enqueueSnapshot(snapshot);
        AutismConfig.onPersistenceSnapshot(snapshot);
    }

    static void enqueueSnapshot(AutismConfig snapshot) {
        SaveCoordinator.enqueueLatest(CONFIG_KEY, () -> {
            try {
                AutismConfig.writeToDisk(AutismConfig.toJson(snapshot));
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Failed to serialize Autism config", t);
            }
        });
    }

    static void capturePendingNow() {
        SaveCoordinator.capturePendingConfigNow();
    }

    static void flushBlocking(long timeoutMs) {
        SaveCoordinator.flushBlocking(timeoutMs);
    }
}
