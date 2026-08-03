package autismclient.util;

public interface AutismChamsHolder {
    void autism$setChams(boolean active, int visibleColor, int occludedColor);

    boolean autism$chamsActive();

    int autism$chamsVisible();

    int autism$chamsOccluded();
}
