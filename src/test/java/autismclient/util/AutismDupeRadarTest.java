package autismclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutismDupeRadarTest {
    @Test
    void versionedProviderNamesMatchScannerPluginNames() {
        assertEquals(
            AutismPluginNameMatcher.normalizeVersionless("quickshop-hikari"),
            AutismPluginNameMatcher.normalizeVersionless("QuickShop-Hikari 6.2.0.10")
        );
        assertEquals(
            AutismPluginNameMatcher.normalizeVersionless("topminions"),
            AutismPluginNameMatcher.normalizeVersionless("TOPMINIONS-v2.6.1")
        );
        assertEquals(
            AutismPluginNameMatcher.normalizeVersionless("protocollib"),
            AutismPluginNameMatcher.normalizeVersionless("ProtocolLib_5.4.0-SNAPSHOT")
        );
    }

    @Test
    void officialPluginReferenceShapePreservesNameAndVersion() {
        var card = JsonParser.parseString("""
            {
              "name": "QuickShop-Hikari Balance Transfer Exploit",
              "status": "verified",
              "plugin_name": "QuickShop-Hikari",
              "plugin_version": "6.2.0.10",
              "plugins": [{"name": "QuickShop-Hikari", "version": "6.2.0.10"}]
            }
            """).getAsJsonObject();

        List<String> references = AutismPluginNameMatcher.extractProviderReferences(card);
        assertTrue(references.contains("QuickShop-Hikari 6.2.0.10"));
        assertEquals(
            "QuickShop-Hikari 6.2.0.10",
            AutismPluginNameMatcher.bestMatchingReference("quickshop-hikari", references, 0.90D)
        );
    }

    @Test
    void fuzzyReferenceValidationRejectsUnrelatedPlugins() {
        assertEquals(
            null,
            AutismPluginNameMatcher.bestMatchingReference(
                "quickshop-hikari",
                List.of("ExcellentCrates 6.0.0", "WorldGuard 7.0.14"),
                0.90D
            )
        );
    }
}
