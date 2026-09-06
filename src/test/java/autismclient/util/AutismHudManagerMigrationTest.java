package autismclient.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutismHudManagerMigrationTest {

    private static AutismConfig.HudElementState element(String... keyValues) {
        AutismConfig.HudElementState state = new AutismConfig.HudElementState();
        state.x = 123;
        state.y = 456;
        state.anchor = "TOP_LEFT";
        state.settings = new LinkedHashMap<>();
        state.settings.put("vertical-padding", "1");
        for (int i = 0; i + 1 < keyValues.length; i += 2) state.settings.put(keyValues[i], keyValues[i + 1]);
        return state;
    }

    private static AutismConfig migratedConfig() {
        AutismConfig config = new AutismConfig();
        config.hudLayoutMigrated = true;
        config.hudLayoutNormalizedV2 = true;
        return config;
    }

    private static void normalize(AutismConfig config) throws Exception {
        Method method = AutismHudManager.class.getDeclaredMethod("normalizeDefaultHudStack", AutismConfig.class);
        method.setAccessible(true);
        method.invoke(null, config);
    }

    @Test
    void userLogoBackgroundAndPaddingSurviveNormalize() throws Exception {
        AutismConfig config = migratedConfig();
        config.hudElements.put("watermark", element(
            "background", "false",
            "padding", "7",
            "logo-style-migrated", "true",
            "legacy-style-migrated", "true"));

        normalize(config);

        assertEquals("false", config.hudElements.get("watermark").settings.get("background"));
        assertEquals("7", config.hudElements.get("watermark").settings.get("padding"));
    }

    @Test
    void deliberatelyLegacyValuedChoicesSurviveOnceMigrated() throws Exception {
        AutismConfig config = migratedConfig();

        config.hudElements.put("watermark", element(
            "background", "false",
            "padding", "1",
            "logo-style-migrated", "true",
            "legacy-style-migrated", "true"));
        config.hudElements.put("compass", element(
            "padding", "3",
            "compass-width", "86",
            "compass-style-migrated", "true",
            "legacy-style-migrated", "true"));
        config.hudElements.put("fps", element(
            "outline-color", "FF8F1F24",
            "stair-snap", "5",
            "legacy-style-migrated", "true"));

        normalize(config);

        assertEquals("false", config.hudElements.get("watermark").settings.get("background"));
        assertEquals("1", config.hudElements.get("watermark").settings.get("padding"));
        assertEquals("3", config.hudElements.get("compass").settings.get("padding"));
        assertEquals("86", config.hudElements.get("compass").settings.get("compass-width"));
        assertEquals("FF8F1F24", config.hudElements.get("fps").settings.get("outline-color"));
        assertEquals("5", config.hudElements.get("fps").settings.get("stair-snap"));
    }

    @Test
    void userArrangedPositionsSurviveNormalize() throws Exception {
        AutismConfig config = migratedConfig();

        for (String id : new String[] {"coordinates", "nether_coords", "rotation"}) {
            AutismConfig.HudElementState state = element("legacy-style-migrated", "true");
            state.anchor = "TOP_LEFT";
            state.x = 8;
            state.y = 100;
            config.hudElements.put(id, state);
        }

        normalize(config);

        for (String id : new String[] {"coordinates", "nether_coords", "rotation"}) {
            AutismConfig.HudElementState state = config.hudElements.get(id);
            assertEquals("TOP_LEFT", state.anchor, id + " anchor");
            assertEquals(8, state.x, id + " x");
            assertEquals(100, state.y, id + " y");
        }
    }
}
