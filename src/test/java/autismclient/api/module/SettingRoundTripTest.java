package autismclient.api.module;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingRoundTripTest {
    private enum Mode { VANILLA, STRAFE, FAST_PLACE }

    private static final class MapOwner implements SettingOwner {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String settingValue(String name) {
            return values.get(name);
        }

        @Override
        public void putSettingValue(String name, String value) {
            values.put(name, value);
        }
    }

    private static <T, S extends Setting<T, S>> S live(S setting) {
        setting.attach(new MapOwner());
        return setting;
    }

    @Test
    void boolRoundTrips() {
        BoolSetting setting = live(new BoolSetting("b", "B", true));
        assertTrue(setting.get());
        setting.set(false);
        assertFalse(setting.get());
        assertEquals("true", new BoolSetting("b", "B", true).defaultValue());
        assertEquals("false", setting.deserialize("definitely not a boolean"));
    }

    @Test
    void intClampsAndRoundTrips() {
        IntSetting setting = live(new IntSetting("i", "I", 5, 0, 10, 1));
        assertEquals(5, setting.get());
        setting.set(7);
        assertEquals(7, setting.get());
        setting.set(999);
        assertEquals(10, setting.get());
        setting.set(-50);
        assertEquals(0, setting.get());
        assertEquals("5", setting.deserialize("not-a-number"));
    }

    @Test
    void doubleClampsAndRoundTrips() {
        DoubleSetting setting = live(new DoubleSetting("d", "D", 1.5, 0.0, 5.0, 0.1));
        setting.set(2.5);
        assertEquals(2.5, setting.get(), 1e-9);
        setting.set(100.0);
        assertEquals(5.0, setting.get(), 1e-9);
        assertEquals(setting.defaultValue(), setting.deserialize("xyz"));
    }

    @Test
    void enumRoundTripsAndIsTolerant() {
        EnumSetting<Mode> setting = live(new EnumSetting<>("m", "M", Mode.VANILLA, Mode.values()));
        assertEquals(List.of("Vanilla", "Strafe", "Fast Place"), setting.choices());
        setting.set(Mode.STRAFE);
        assertEquals(Mode.STRAFE, setting.get());
        assertEquals("Vanilla", setting.deserialize("bogus token"));
        assertEquals("Strafe", setting.deserialize("strafe"));
        assertEquals("Fast Place", setting.deserialize("FAST_PLACE"));
    }

    @Test
    void choiceRoundTripsAndIsTolerant() {
        ChoiceSetting setting = live(new ChoiceSetting("c", "C", "A", "A", "B", "C"));
        setting.set("B");
        assertEquals("B", setting.get());
        assertEquals("A", setting.deserialize("zzz"));
        assertEquals("C", setting.deserialize("c"));
    }

    @Test
    void colorRoundTripsAndIsTolerant() {
        ColorSetting setting = live(new ColorSetting("col", "Col", 0xFF112233));
        setting.set(0x80AABBCC);
        assertEquals(0x80AABBCC, setting.get());
        assertEquals("FF112233", new ColorSetting("col", "Col", 0xFF112233).defaultValue());
        assertEquals("FF00FF00", setting.deserialize("#00FF00"));
        assertEquals(setting.defaultValue(), setting.deserialize("not hex"));
    }

    @Test
    void keybindRoundTrips() {
        KeybindSetting setting = live(new KeybindSetting("k", "K", -1));
        setting.set(65);
        assertEquals(65, setting.get());
        assertEquals("-1", setting.deserialize("garbage"));
    }

    @Test
    void stringRoundTrips() {
        StringSetting setting = live(new StringSetting("s", "S", "hi"));
        assertEquals("hi", setting.get());
        setting.set("there");
        assertEquals("there", setting.get());
    }

    @Test
    void listRoundTripsAndIsTolerant() {
        StringListSetting setting = live(new StringListSetting("l", "L"));
        assertEquals(List.of(), setting.get());
        setting.set(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), setting.get());
        assertEquals("a|b|c", setting.serialize());
        assertEquals("a|b", setting.deserialize(" a | | b "));
        assertEquals(Kind.STORAGE_LIST, RegistryListSetting.storages("st", "St").kind());
    }

    @Test
    void actionHasNoValueAndRequiresWorld() {
        boolean[] ran = {false};
        ActionSetting setting = new ActionSetting("a", "A", () -> ran[0] = true);
        assertFalse(setting.isAvailable(false, false));
        assertTrue(setting.isAvailable(true, false));
        assertTrue(new ActionSetting("a", "A", () -> {}).availableOffline().isAvailable(false, false));
        setting.action().run();
        assertTrue(ran[0]);
    }

    @Test
    void visibilityIsRobustWhenSupplierThrows() {
        BoolSetting setting = new BoolSetting("b", "B", false).visibleWhen(() -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(setting.isVisible());
    }
}
