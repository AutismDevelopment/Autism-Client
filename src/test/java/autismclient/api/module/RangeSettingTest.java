package autismclient.api.module;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.RangeSlider;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeSettingTest {

    @Test
    void aValueRangeIsAlwaysOrdered() {

        assertEquals(new ValueRange(100, 200), new ValueRange(200, 100));
        assertEquals(100.0, new ValueRange(200, 100).min());
        assertEquals(200.0, new ValueRange(200, 100).max());
        assertEquals("100,200", new ValueRange(200, 100).toString());
    }

    @Test
    void aValueRangeClampsBothEnds() {
        assertEquals(new ValueRange(0, 500), new ValueRange(-40, 900).clamp(0, 500));
        assertEquals(new ValueRange(500, 500), new ValueRange(600, 900).clamp(0, 500));
        assertEquals(new ValueRange(0, 0), new ValueRange(-900, -600).clamp(0, 500));
    }

    @Test
    void parsingNeverThrowsAndFallsBackCleanly() {
        ValueRange fallback = new ValueRange(100, 200);
        assertEquals(new ValueRange(5, 45), ValueRange.parse("5,45", fallback));
        assertEquals(new ValueRange(5, 45), ValueRange.parse("  5 , 45  ", fallback), "whitespace is tolerated");
        assertEquals(fallback, ValueRange.parse(null, fallback));
        assertEquals(fallback, ValueRange.parse("", fallback));
        assertEquals(fallback, ValueRange.parse("120", fallback), "a single number is not a range");
        assertEquals(fallback, ValueRange.parse("abc,def", fallback));
        assertEquals(fallback, ValueRange.parse("100,", fallback));
    }

    @Test
    void aRandomPickAlwaysLandsInsideTheBand() {
        Random random = new Random(1234L);
        ValueRange band = new ValueRange(100, 200);
        boolean sawLow = false;
        boolean sawHigh = false;
        for (int i = 0; i < 20000; i++) {
            double picked = band.random(random);
            assertTrue(picked >= 100.0 && picked <= 200.0, "picked " + picked + " outside the band");
            sawLow |= picked < 101.0;
            sawHigh |= picked > 199.0;
        }
        assertTrue(sawLow && sawHigh, "both ends must be reachable - the band is inclusive");

        assertEquals(140.0, new ValueRange(140, 140).random(random));
    }

    @Test
    void aMinimumSeparationKeepsTheThumbsApart() {

        assertEquals(new ValueRange(19.5, 20), new ValueRange(20, 20).withMinSeparation(0.5, 5, 20, false));
        assertEquals(new ValueRange(12, 12.5), new ValueRange(12, 12).withMinSeparation(0.5, 5, 20, true));

        assertEquals(new ValueRange(19.5, 20), new ValueRange(20, 20).withMinSeparation(0.5, 5, 20, true));
        assertEquals(new ValueRange(5, 5.5), new ValueRange(5, 5).withMinSeparation(0.5, 5, 20, false));

        assertEquals(new ValueRange(8, 12), new ValueRange(8, 12).withMinSeparation(0.5, 5, 20, false));
        assertEquals(new ValueRange(9, 9), new ValueRange(9, 9).withMinSeparation(0, 5, 20, false));
    }

    @Test
    void theSettingEnforcesItsSeparationOnAnythingStored() {
        RangeSetting cps = new RangeSetting("blocks-cps", "Blocks CPS", new ValueRange(8, 12), 5, 20, 0.5)
            .minSeparation(0.5);
        assertEquals("8,12", cps.defaultValue());
        assertEquals("19.5,20", cps.sanitizeUiString("20,20"), "a collapsed band must be opened up");
        assertEquals("5,5.5", cps.sanitizeUiString("5,5"));
        assertEquals("8,12", cps.sanitizeUiString("garbage"));

        assertEquals("9.5,15.5", cps.sanitizeUiString("9.5,15.5"));
    }

    @Test
    void wholeNumberBandsNeverShowADecimalPoint() {

        assertEquals("100 - 200", new ValueRange(100, 200).format(5));
        assertEquals("8 - 12", new ValueRange(8, 12).format(0.5));
        assertEquals("19.5 - 20", new ValueRange(19.5, 20).format(0.5));
    }

    @Test
    void theSettingRoundTripsAndClampsToItsRange() {

        RangeSetting setting = new RangeSetting("hold-time", "Hold Time", new ValueRange(100, 200), 0, 500, 5);
        assertEquals("100,200", setting.defaultValue());
        assertEquals(DisplayMode.RANGE_SLIDER, setting.displayMode(), "must draw as the dual-thumb control");
        assertEquals(0.0, setting.min());
        assertEquals(500.0, setting.max());
        assertEquals(5.0, setting.step());

        assertEquals("100,200", setting.sanitizeUiString("garbage"));
        assertEquals("0,500", setting.sanitizeUiString("-30,900"));
        assertEquals("40,90", setting.sanitizeUiString("90,40"));
    }

    @Test
    void draggingAThumbStepsInFivesAcrossTheWholeTrack() {

        UiBounds bounds = UiBounds.of(20, 0, 120, 12);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int mx = bounds.x() - 30; mx <= bounds.right() + 30; mx++) {
            double value = RangeSlider.valueFromMouse(mx, bounds, 0, 500, 5);
            assertTrue(value >= 0.0 && value <= 500.0, "mouse " + mx + " produced " + value);
            assertEquals(0.0, value % 5.0, 1.0E-9, "mouse " + mx + " produced off-step " + value);
            sawMin |= value == 0.0;
            sawMax |= value == 500.0;
        }
        assertTrue(sawMin, "the low end must be reachable");
        assertTrue(sawMax, "the high end must be reachable");
    }

    @Test
    void aClickGrabsTheNearerThumb() {
        UiBounds bounds = UiBounds.of(0, 0, 100, 12);
        double lowRatio = RangeSlider.ratio(100, 0, 500);
        double highRatio = RangeSlider.ratio(200, 0, 500);
        var track = RangeSlider.trackOf(bounds);
        int lowX = track.x() + (int) Math.round(track.width() * lowRatio);
        int highX = track.x() + (int) Math.round(track.width() * highRatio);

        assertEquals(RangeSlider.THUMB_MIN, RangeSlider.nearestThumb(lowX, bounds, lowRatio, highRatio));
        assertEquals(RangeSlider.THUMB_MAX, RangeSlider.nearestThumb(highX, bounds, lowRatio, highRatio));
        assertEquals(RangeSlider.THUMB_MIN, RangeSlider.nearestThumb(track.x() - 20, bounds, lowRatio, highRatio),
            "dragging off the left end grabs the min");
        assertEquals(RangeSlider.THUMB_MAX, RangeSlider.nearestThumb(track.right() + 20, bounds, lowRatio, highRatio),
            "dragging off the right end grabs the max");
    }

    @Test
    void stackedThumbsStayDraggableApart() {

        UiBounds bounds = UiBounds.of(0, 0, 100, 12);
        double ratio = RangeSlider.ratio(250, 0, 500);
        var track = RangeSlider.trackOf(bounds);
        int centre = track.x() + (int) Math.round(track.width() * ratio);
        assertEquals(RangeSlider.THUMB_MIN, RangeSlider.nearestThumb(centre - 12, bounds, ratio, ratio),
            "pulling left must move the min");
        assertEquals(RangeSlider.THUMB_MAX, RangeSlider.nearestThumb(centre + 12, bounds, ratio, ratio),
            "pulling right must move the max");
    }
}
