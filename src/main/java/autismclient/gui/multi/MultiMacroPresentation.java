package autismclient.gui.multi;

import autismclient.util.multi.MultiMacroDelay;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiSession;

public final class MultiMacroPresentation {
    public static final int ASSIGNED_GRAY = 0xFF9AA6B2;
    public static final int PLAYING_GREEN = 0xFF57F287;

    public static final int QUEUED_AMBER = 0xFFE8B44A;

    private MultiMacroPresentation() {
    }

    public static String assignedName(MultiManager manager, MultiSession.Snapshot snapshot) {
        if (manager == null || snapshot == null) return "";
        String value = manager.assignedMacroName(snapshot.accountId());
        return value == null ? "" : value;
    }

    public static boolean playing(MultiSession.Snapshot snapshot) {
        if (snapshot == null || !snapshot.connected()) return false;
        MultiSession.MacroProgress progress = snapshot.macroProgress();
        return progress != null && progress.running() && progress.macroName() != null
            && !progress.macroName().isBlank();
    }

    public static String playingName(MultiSession.Snapshot snapshot) {
        if (!playing(snapshot)) return "";
        return snapshot.macroProgress().macroName();
    }

    public static String assignedLabel(String assignedName) {
        return "Assigned: " + (assignedName == null || assignedName.isBlank() ? "none" : assignedName);
    }

    public static String playingLabel(String playingName) {
        return playingName == null || playingName.isBlank() ? "" : "Playing: " + playingName;
    }

    public static boolean queued(MultiSession.Snapshot snapshot, long now) {
        return snapshot != null && snapshot.macroQueue() != null && snapshot.macroQueue().pending(now);
    }

    public static String queuedLabel(MultiSession.Snapshot snapshot, long now) {
        if (!queued(snapshot, now)) return "";
        return "Starts in " + MultiMacroDelay.countdownText(snapshot.macroQueue().remainingMs(now));
    }

    public static String tooltip(MultiManager manager, MultiSession.Snapshot snapshot) {
        String assigned = assignedName(manager, snapshot);
        StringBuilder text = new StringBuilder("Assigned: ").append(assigned.isBlank() ? "none" : assigned);
        String playing = playingName(snapshot);
        if (!playing.isBlank()) text.append(" | Playing: ").append(playing);
        String queued = queuedLabel(snapshot, System.currentTimeMillis());
        if (!queued.isBlank()) text.append(" | ").append(queued);
        return text.toString();
    }
}
