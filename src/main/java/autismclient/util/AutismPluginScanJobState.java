package autismclient.util;

final class AutismPluginScanJobState {
    private static final int MAX_SEND_ATTEMPTS = 4;

    private final long generation;
    private final String serverAddress;
    private boolean finalized;

    AutismPluginScanJobState(long generation, String serverAddress) {
        this.generation = generation;
        this.serverAddress = serverAddress == null ? "" : serverAddress;
    }

    boolean matches(long expectedGeneration, String currentAddress) {
        return !finalized
            && generation == expectedGeneration
            && serverAddress.equals(currentAddress == null ? "" : currentAddress);
    }

    boolean canRetry(int completedAttempts) {
        return !finalized && completedAttempts + 1 < MAX_SEND_ATTEMPTS;
    }

    boolean beginFinalize() {
        if (finalized) return false;
        finalized = true;
        return true;
    }

    boolean requiresPartialResult(int runtimeErrors, int failedProbes, int pendingProbes) {
        return runtimeErrors > 0 || failedProbes > 0 || pendingProbes > 0;
    }

    boolean isFinalized() {
        return finalized;
    }
}
