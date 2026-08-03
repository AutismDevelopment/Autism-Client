package autismclient.nbt;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface NbtTagParser {
    void parseTagToList(List<Component> tooltip, @Nullable Tag tag, boolean splitLines);
}
