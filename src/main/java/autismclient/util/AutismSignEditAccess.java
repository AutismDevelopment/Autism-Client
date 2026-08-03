package autismclient.util;

import net.minecraft.core.BlockPos;

public interface AutismSignEditAccess {
    BlockPos autism$getSignPos();
    boolean autism$isFrontText();
    String[] autism$getSignLines();
}
