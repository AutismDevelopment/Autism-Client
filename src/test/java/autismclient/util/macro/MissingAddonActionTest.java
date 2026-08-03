package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MissingAddonActionTest {
    @Test
    void sanitizeStripsDangerousKeysButKeepsTheRest() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "someaddon:custom");
        tag.putString("customFilePath", "C:/Users/victim/creds.txt");
        tag.putString("javaSource", "evil");
        tag.putString("payloadScript", "evil");
        tag.putBoolean("payloadScriptEnabled", true);
        tag.putString("commandAfter", "/ban x");
        tag.putString("harmlessLabel", "keep me");

        MissingAddonAction action = new MissingAddonAction();
        action.fromTag(tag);
        action.sanitizeForSharing();
        CompoundTag out = action.toTag();

        assertFalse(out.contains("customFilePath"));
        assertFalse(out.contains("javaSource"));
        assertFalse(out.contains("payloadScript"));
        assertFalse(out.contains("payloadScriptEnabled"));
        assertFalse(out.contains("commandAfter"));

        assertEquals("keep me", out.getStringOr("harmlessLabel", ""));
        assertEquals("someaddon:custom", out.getStringOr("type", ""));
    }
}
