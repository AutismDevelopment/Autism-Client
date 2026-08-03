package autismclient.util;

public interface AutismSpecialGuiActions {
    void autism$closeWithPacket();

    void autism$closeWithoutPacket();

    void autism$desync();

    default void autism$closeWithPacket(boolean notify) {
        autism$closeWithPacket();
    }

    default void autism$closeWithoutPacket(boolean notify) {
        autism$closeWithoutPacket();
    }

    default void autism$desync(boolean notify) {
        autism$desync();
    }
}
