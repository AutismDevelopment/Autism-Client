package autismclient.util;

import net.minecraft.client.KeyMapping;

public interface AutismKeyMappingBridge {
    static AutismKeyMappingBridge of(KeyMapping mapping) {
        return (AutismKeyMappingBridge) mapping;
    }

    boolean autism$isActuallyDown();

    void autism$resetPressedState();

    void autism$simulatePress(boolean pressed);
}
