package autismclient.util.login;

import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;

public interface AutoLoginHost {

    String password();

    boolean spawnedInWorld();

    boolean canSendChat();

    CustomMenuSnapshot customMenu();

    boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission);

    boolean sendCommandLine(String line);

    default boolean screenOwnedElsewhere() {
        return false;
    }

    default void note(String message) {
    }

    default void needsPassword(String context) {
    }
}
