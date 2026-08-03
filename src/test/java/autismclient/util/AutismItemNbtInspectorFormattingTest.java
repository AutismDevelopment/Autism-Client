package autismclient.util;

import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismItemNbtInspectorFormattingTest {
    @Test
    void prettyFormattingPreservesCompleteSnbt() throws Exception {
        String raw = "{id:\"minecraft:paper\",count:1,components:{\"minecraft:custom_data\":{text:\"a,b{c}\"}}}";
        String formatted = AutismItemNbtInspector.prettySnbt(raw);

        assertTrue(formatted.contains("\n"));
        assertTrue(formatted.contains("\"a,b{c}\""));
        assertEquals(TagParser.parseCompoundFully(raw), TagParser.parseCompoundFully(formatted));
    }

    @Test
    void prettyFormattingDoesNotSplitSingleQuotedValues() throws Exception {
        String raw = "{id:'minecraft:paper',components:{'minecraft:custom_data':{text:'a,b[c]'}}}";
        String formatted = AutismItemNbtInspector.prettySnbt(raw);

        assertTrue(formatted.contains("'a,b[c]'"));
        assertEquals(TagParser.parseCompoundFully(raw), TagParser.parseCompoundFully(formatted));
    }
}
