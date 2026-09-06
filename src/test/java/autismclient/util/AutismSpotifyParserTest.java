package autismclient.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismSpotifyParserTest {
    private static final String US = "\u001f";

    @TempDir
    static Path gameDir;

    @BeforeAll
    static void setUp() {
        ensureGameDir(gameDir);
    }

    private static synchronized void ensureGameDir(Path dir) {
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gd = impl.getDeclaredField("gameDir");
            gd.setAccessible(true);
            if (gd.get(loader) == null) {
                Method m = impl.getDeclaredMethod("setGameDir", Path.class);
                m.setAccessible(true);
                m.invoke(loader, dir);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize a test game dir", t);
        }
    }

    private static AutismSpotify.Snapshot line(String status, String artist, String title) {
        return AutismSpotify.parseLine(status + US + artist + US + title);
    }

    @Test
    void playingLineCarriesArtistAndTitle() {
        AutismSpotify.Snapshot s = line("PLAYING", "The Artist", "The Title");
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void pausedLineKeepsItsTrack() {

        AutismSpotify.Snapshot s = line("PAUSED", "The Artist", "The Title");
        assertEquals(AutismSpotify.Status.PAUSED, s.status());
        assertEquals("The Title", s.title());
    }

    @Test
    void statusWordsAreCaseInsensitive() {

        assertEquals(AutismSpotify.Status.PLAYING, line("Playing", "A", "T").status());
        assertEquals(AutismSpotify.Status.PAUSED, line("paused", "A", "T").status());
    }

    @Test
    void titleMayContainPipes() {

        AutismSpotify.Snapshot s = line("PLAYING", "Artist", "A | B | C");
        assertEquals("A | B | C", s.title());
    }

    @Test
    void cjkAndUnicodeSurviveIntact() {
        AutismSpotify.Snapshot s = line("PLAYING", "米津玄師", "打上花火 ✿");
        assertEquals("米津玄師", s.artist());
        assertEquals("打上花火 ✿", s.title());
    }

    @Test
    void missingArtistParsesAsEmptyString() {
        AutismSpotify.Snapshot s = line("PLAYING", "", "Solo Title");
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
        assertEquals("Solo Title", s.title());
    }

    @Test
    void bareStoppedLineIsStopped() {

        AutismSpotify.Snapshot s = AutismSpotify.parseLine("STOPPED");
        assertEquals(AutismSpotify.Status.STOPPED, s.status());
        assertEquals("", s.artist());
        assertEquals("", s.title());
    }

    @Test
    void bareUnavailableLineIsUnavailable() {
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine("UNAVAILABLE").status());
    }

    @Test
    void emptyTitleMeansStopped() {

        AutismSpotify.Snapshot s = line("PLAYING", "Artist", "");
        assertEquals(AutismSpotify.Status.STOPPED, s.status());
    }

    @Test
    void lineWithoutTitleFieldMeansStopped() {
        assertEquals(AutismSpotify.Status.STOPPED, AutismSpotify.parseLine("PAUSED" + US + "Artist").status());
    }

    @Test
    void unknownStatusWordIsUnavailable() {

        assertEquals(AutismSpotify.Status.UNAVAILABLE, line("CHANGING", "A", "T").status());
    }

    @Test
    void garbageBlankAndNullNeverThrowAndMeanUnavailable() {
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine("not a protocol line").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine("").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine("   ").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine(null).status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseLine(US + US).status());
    }

    @Test
    void snapshotsAreNeverNullAndAreStamped() {
        AutismSpotify.Snapshot s = AutismSpotify.parseLine("garbage");
        assertNotNull(s);
        assertNotNull(s.artist());
        assertNotNull(s.title());
        assertTrue(s.updatedAtMs() > 0, "a parsed snapshot must carry its observation time");
    }

    private static String getAll(String playbackStatus, String metadata) {
        return "({'PlaybackStatus': <'" + playbackStatus + "'>, 'LoopStatus': <'None'>, 'Rate': <1.0>, "
            + "'Shuffle': <false>, 'Metadata': <{" + metadata + "}>, 'Volume': <0.8>, "
            + "'Position': <int64 61516000>, 'CanGoNext': <true>, 'CanPlay': <true>, "
            + "'CanPause': <true>, 'CanSeek': <true>, 'CanControl': <true>},)";
    }

    private static String metadata(String artistArray, String titleValue) {
        return "'mpris:trackid': <objectpath '/org/mpris/MediaPlayer2/Track/4uLU6hMCjMI75M1A2tKUQC'>, "
            + "'mpris:length': <uint64 200066000>, "
            + "'mpris:artUrl': <'https://i.scdn.co/image/ab67616d0000b273'>, "
            + "'xesam:album': <'Some Album'>, "
            + (artistArray == null ? "" : "'xesam:artist': <[" + artistArray + "]>, ")
            + "'xesam:discNumber': <1>, "
            + (titleValue == null ? "" : "'xesam:title': <" + titleValue + ">, ")
            + "'xesam:url': <'https://open.spotify.com/track/xyz'>";
    }

    @Test
    void fullGetAllOutputParses() {
        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("'The Artist'", "'The Title'")));
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void firstArtistOfTheArrayWins() {

        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("'First', 'Second', 'Third'", "'Title'")));
        assertEquals("First", s.artist());
    }

    @Test
    void emptyArtistArrayMeansEmptyArtist() {
        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("", "'Title'")));
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
    }

    @Test
    void pausedAndStoppedVariants() {
        assertEquals(AutismSpotify.Status.PAUSED, AutismSpotify.parseGdbus(
            getAll("Paused", metadata("'A'", "'T'"))).status());
        assertEquals(AutismSpotify.Status.STOPPED, AutismSpotify.parseGdbus(
            getAll("Stopped", metadata("'A'", "'T'"))).status());
    }

    @Test
    void missingTitleMeansStopped() {

        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(getAll("Playing", metadata("'A'", null)));
        assertEquals(AutismSpotify.Status.STOPPED, s.status());
    }

    @Test
    void missingArtistKeyMeansEmptyArtist() {
        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(getAll("Playing", metadata(null, "'Title'")));
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
        assertEquals("Title", s.title());
    }

    @Test
    void missingPlaybackStatusIsUnavailable() {

        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus(
            "({'Metadata': <{" + metadata("'A'", "'T'") + "}>},)").status());
    }

    @Test
    void unknownPlaybackStatusIsUnavailable() {
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus(
            getAll("Buffering", metadata("'A'", "'T'"))).status());
    }

    @Test
    void doubleQuotedTitleWithApostropheParses() {

        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("'Queen'", "\"Don't Stop Me Now\"")));
        assertEquals("Don't Stop Me Now", s.title());
    }

    @Test
    void doubleQuotedArtistArrayElementWithApostropheParses() {

        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("\"Sinéad O'Connor\", 'Other'", "'Nothing Compares 2 U'")));
        assertEquals("Sinéad O'Connor", s.artist());
        assertEquals("Nothing Compares 2 U", s.title());
    }

    @Test
    void escapedBackslashBeforeTheClosingQuoteParses() {

        AutismSpotify.Snapshot s = AutismSpotify.parseGdbus(
            getAll("Playing", metadata("'A'", "'Trail\\\\'")));
        assertEquals("Trail\\", s.title());
    }

    @Test
    void gdbusGarbageBlankAndNullMeanUnavailable() {

        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus("").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus("  \n ").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus(null).status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseGdbus("gdbus: error").status());
    }

    private static AutismSpotify.Snapshot osa(String status, String artist, String title) {

        return AutismSpotify.parseOsascript(status + US + artist + US + title + "\n");
    }

    @Test
    void osascriptLowercaseStatusesParse() {

        assertEquals(AutismSpotify.Status.PLAYING, osa("playing", "A", "T").status());
        assertEquals(AutismSpotify.Status.PAUSED, osa("paused", "A", "T").status());
        assertEquals(AutismSpotify.Status.STOPPED, osa("stopped", "A", "T").status());
    }

    @Test
    void osascriptLineCarriesArtistAndTitleAndDropsTheTrailingNewline() {
        AutismSpotify.Snapshot s = osa("playing", "The Artist", "The Title");
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void osascriptUnicodeAndApostrophesSurviveIntact() {

        AutismSpotify.Snapshot s = osa("playing", "Sinéad O'Connor", "Nothing Compares 2 U ～ 打上花火");
        assertEquals("Sinéad O'Connor", s.artist());
        assertEquals("Nothing Compares 2 U ～ 打上花火", s.title());
    }

    @Test
    void osascriptSilenceMeansUnavailableNotStopped() {

        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseOsascript("").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseOsascript("\n").status());
        assertEquals(AutismSpotify.Status.UNAVAILABLE, AutismSpotify.parseOsascript(null).status());
    }

    @Test
    void osascriptEmptyTitleMeansStopped() {
        assertEquals(AutismSpotify.Status.STOPPED, osa("playing", "Artist", "").status());
    }

    @Test
    void positionOnlyPollsNeverMoveTheContentStamp() {

        AutismSpotify.Snapshot first = AutismSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "12.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + "");
        AutismSpotify.store(first);
        long stamp = first.updatedAtMs();
        AutismSpotify.Snapshot later = AutismSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "13.0" + US + "200.0" + US + "1" + US + "ALL" + US + "55" + US + "/tmp/art.png");
        AutismSpotify.store(later);
        assertEquals(stamp, AutismSpotify.snapshot().updatedAtMs(),
            "position/volume/shuffle/art movement must not re-stamp");
        assertEquals(13.0, AutismSpotify.snapshot().positionSec(), "but the new position still lands");
        assertEquals(55, AutismSpotify.snapshot().volume(), "and the new volume lands");
        assertEquals(AutismSpotify.Repeat.ALL, AutismSpotify.snapshot().repeat());
        assertEquals("/tmp/art.png", AutismSpotify.snapshot().artworkPath());
    }

    @Test
    void aRealContentChangeReplacesTheSnapshot() {
        AutismSpotify.store(line("PLAYING", "Artist", "Old"));
        AutismSpotify.Snapshot next = line("PLAYING", "Artist", "New");
        AutismSpotify.store(next);
        assertSame(next, AutismSpotify.snapshot());
        assertEquals("New", AutismSpotify.snapshot().title());
    }

    @Test
    void aStatusChangeCountsAsContentChange() {

        AutismSpotify.store(line("PLAYING", "Artist", "Title"));
        AutismSpotify.Snapshot paused = line("PAUSED", "Artist", "Title");
        AutismSpotify.store(paused);
        assertSame(paused, AutismSpotify.snapshot());
    }

    @Test
    void extendedLineParsesAllFields() {
        AutismSpotify.Snapshot s = AutismSpotify.parseLine(
            "PLAYING" + US + "The Artist" + US + "The Title" + US + "61.5" + US + "213.0" + US + "1"
                + US + "ALL" + US + "72" + US + "C:\\Temp\\art.png");
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-9);
        assertEquals(213.0, s.durationSec(), 1e-9);
        assertTrue(s.shuffle());
        assertEquals(AutismSpotify.Repeat.ALL, s.repeat());
        assertEquals(72, s.volume());
        assertEquals("C:\\Temp\\art.png", s.artworkPath());
    }

    @Test
    void missingTrailingFieldsGetDefaults() {

        AutismSpotify.Snapshot s = line("PAUSED", "A", "T");
        assertEquals(AutismSpotify.Status.PAUSED, s.status());
        assertEquals(0.0, s.positionSec());
        assertEquals(0.0, s.durationSec());
        assertTrue(!s.shuffle());
        assertEquals(AutismSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals(-1, s.volume());
        assertEquals("", s.artworkPath());
    }

    @Test
    void malformedTrailingFieldsGetDefaults() {
        AutismSpotify.Snapshot s = AutismSpotify.parseLine(
            "PLAYING" + US + "A" + US + "T" + US + "abc" + US + "" + US + "maybe" + US + "LOOPS"
                + US + "loud" + US + "");
        assertEquals(AutismSpotify.Status.PLAYING, s.status(), "garbage in trailing fields must not kill the line");
        assertEquals(0.0, s.positionSec());
        assertEquals(0.0, s.durationSec());
        assertTrue(!s.shuffle());
        assertEquals(AutismSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals(-1, s.volume());
        assertEquals("", s.artworkPath());
    }

    @Test
    void playerctlLineConvertsUnits() {
        AutismSpotify.Snapshot s = AutismSpotify.parsePlayerctl(
            "Playing" + US + "Artist" + US + "Title" + US + "61500000" + US + "213000000" + US + "On"
                + US + "Playlist" + US + "0.42" + US + "file:///home/u/art%20work.png");
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-6);
        assertEquals(213.0, s.durationSec(), 1e-6);
        assertTrue(s.shuffle());
        assertEquals(AutismSpotify.Repeat.ALL, s.repeat());
        assertEquals(42, s.volume());
        assertEquals("/home/u/art work.png", s.artworkPath(), "file:// passes through as a local path");
        AutismSpotify.Snapshot http = AutismSpotify.parsePlayerctl(
            "Playing" + US + "A" + US + "T" + US + "0" + US + "0" + US + "Off" + US + "None"
                + US + "1.0" + US + "https://i.scdn.co/image/abc");
        assertEquals("https://i.scdn.co/image/abc", http.artworkPath(),
            "http(s) urls pass through for the reader-side bounded download");
        assertEquals(100, http.volume());
        assertEquals(AutismSpotify.Repeat.OFF, http.repeat());
        assertTrue(!http.shuffle());
    }

    @Test
    void playerctlMissingFieldsGetDefaults() {
        AutismSpotify.Snapshot s = AutismSpotify.parsePlayerctl("Paused" + US + "A" + US + "T");
        assertEquals(AutismSpotify.Status.PAUSED, s.status());
        assertEquals(-1, s.volume());
        assertEquals(AutismSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals("", s.artworkPath());
    }

    @Test
    void osascriptExtendedLineConvertsMilliseconds() {
        AutismSpotify.Snapshot s = AutismSpotify.parseOsascript(
            "playing" + US + "Artist" + US + "Title" + US + "61500" + US + "213000" + US + "true"
                + US + "false" + US + "65" + US + "/var/tmp/art.png\n");
        assertEquals(AutismSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-6);
        assertEquals(213.0, s.durationSec(), 1e-6);
        assertTrue(s.shuffle());
        assertEquals(AutismSpotify.Repeat.OFF, s.repeat(), "macOS repeating=false maps to OFF");
        assertEquals(65, s.volume());
        assertEquals("/var/tmp/art.png", s.artworkPath());
        AutismSpotify.Snapshot rep = AutismSpotify.parseOsascript(
            "playing" + US + "A" + US + "T" + US + "0" + US + "0" + US + "false" + US + "true" + US + "50" + US + "");
        assertEquals(AutismSpotify.Repeat.ALL, rep.repeat(), "macOS repeating=true maps to ALL (no single-track repeat)");
    }

    @Test
    void repeatCycleOrderIsOffAllOneOff() {
        assertEquals(AutismSpotify.Repeat.ALL, AutismSpotify.nextRepeat(AutismSpotify.Repeat.OFF));
        assertEquals(AutismSpotify.Repeat.ONE, AutismSpotify.nextRepeat(AutismSpotify.Repeat.ALL));
        assertEquals(AutismSpotify.Repeat.OFF, AutismSpotify.nextRepeat(AutismSpotify.Repeat.ONE));
        assertEquals(AutismSpotify.Repeat.OFF, AutismSpotify.nextRepeat(AutismSpotify.Repeat.UNKNOWN));
        assertEquals(AutismSpotify.Repeat.OFF, AutismSpotify.nextRepeat(null));
    }

    @Test
    void volumeClampsBeforeTheWire() {
        assertEquals(0, AutismSpotify.clampVolume(-5));
        assertEquals(100, AutismSpotify.clampVolume(150));
        assertEquals(42, AutismSpotify.clampVolume(42));
    }

    @Test
    void sourceFlagRoundTrips() {
        try {
            AutismSpotify.setSourceAnywhere(true);
            assertTrue(AutismSpotify.sourceAnywhere());
            assertTrue(!String.join(" ", AutismSpotify.playerctlArgv()).contains("--player=spotify"),
                "ANY mode must drop --player=spotify from the follow command");
            AutismSpotify.setSourceAnywhere(false);
            assertTrue(!AutismSpotify.sourceAnywhere());
            assertTrue(String.join(" ", AutismSpotify.playerctlArgv()).contains("--player=spotify"),
                "SPOTIFY mode must keep --player=spotify");
        } finally {
            AutismSpotify.setSourceAnywhere(false);
        }
    }

    @Test
    void playerctlActionMapsCommands() {
        try {
            AutismSpotify.setSourceAnywhere(false);
            String[] vol = AutismSpotify.playerctlAction("VOLUME=42");
            assertEquals("volume", vol[vol.length - 2]);
            assertEquals("0.42", vol[vol.length - 1]);
            assertEquals("--player=spotify", vol[1]);
            String[] loop = AutismSpotify.playerctlAction("REPEAT=ONE");
            assertEquals("loop", loop[loop.length - 2]);
            assertEquals("Track", loop[loop.length - 1]);
            assertEquals("play-pause", AutismSpotify.playerctlAction("PLAY_PAUSE")[2]);
            AutismSpotify.setSourceAnywhere(true);
            assertEquals("playerctl", AutismSpotify.playerctlAction("NEXT")[0]);
            assertEquals("next", AutismSpotify.playerctlAction("NEXT")[1],
                "ANY mode commands drop --player too");
        } finally {
            AutismSpotify.setSourceAnywhere(false);
        }
    }

    @Test
    void osascriptRepeatOneDegradesToAll() {
        String[] one = AutismSpotify.osascriptAction("REPEAT=ONE");
        assertTrue(one[one.length - 1].contains("set repeating to true"),
            "AppleScript has no single-track repeat: ONE degrades to repeating on");
        String[] off = AutismSpotify.osascriptAction("REPEAT=OFF");
        assertTrue(off[off.length - 1].contains("set repeating to false"));
        assertTrue(AutismSpotify.osascriptAction("PLAY_PAUSE")[2].contains("playpause"));
        assertTrue(AutismSpotify.osascriptAction("VOLUME=65")[2].contains("set sound volume to 65"));
    }

    @Test
    void windowsScriptExposesCommandsAndSourceFilter() {
        String script = AutismSpotify.windowsScriptText();
        assertTrue(script.contains("AUTISM_SPOTIFY_SOURCE"), "script must read the source env var");
        assertTrue(script.contains("GetCurrentSession"), "ANY mode must use GSMTC's current session");
        assertTrue(script.contains("TryTogglePlayPauseAsync"), "play/pause command");
        assertTrue(script.contains("TrySkipNextAsync"), "next command");
        assertTrue(script.contains("TrySkipPreviousAsync"), "previous command");
        assertTrue(script.contains("TryChangeShuffleActiveAsync"), "shuffle commands");
        assertTrue(script.contains("TryChangeRepeatModeAsync"), "repeat commands");
        assertTrue(script.contains("ReadLineAsync"), "stdin commands must be polled non-blocking");
        assertTrue(script.contains("87CE5498-68D6-44E5-9215-6DA47EF883D8"), "ISimpleAudioVolume present for session volume");
    }

    @Test
    void volumeSentinelStaysUnsupported() {

        assertEquals(-1, AutismSpotify.parseVolume("-1"));
        assertEquals(0, AutismSpotify.parseVolume("0"));
        assertEquals(50, AutismSpotify.parseVolume("50"));
        assertEquals(100, AutismSpotify.parseVolume("150"), "out-of-range clamps to 100");
        assertEquals(-1, AutismSpotify.parseVolume(""));
        assertEquals(-1, AutismSpotify.parseVolume("loud"));
    }

    @Test
    void playerctlFormatUsesTheRealLoopVariable() {

        String argv = String.join(" ", AutismSpotify.playerctlArgv());
        assertTrue(argv.contains("{{loop}}"), "must use the real {{loop}} variable");
        assertTrue(!argv.contains("{{loopStatus}}"), "{{loopStatus}} must be gone");
        assertEquals(AutismSpotify.Repeat.ONE, AutismSpotify.parseRepeat("Track"));
        assertEquals(AutismSpotify.Repeat.ALL, AutismSpotify.parseRepeat("Playlist"));
        assertEquals(AutismSpotify.Repeat.OFF, AutismSpotify.parseRepeat("None"));
    }

    @Test
    void playerctlArtUsesOneStablePath() throws Exception {

        java.nio.file.Path a = java.nio.file.Files.createTempFile("art-src-a", ".bin");
        java.nio.file.Path b = java.nio.file.Files.createTempFile("art-src-b", ".bin");

        java.nio.file.Files.write(a, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        java.awt.image.BufferedImage realImage = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream jpegBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(realImage, "jpeg", jpegBytes);
        java.nio.file.Files.write(b, jpegBytes.toByteArray());
        String first = AutismSpotify.downloadArt(a.toUri().toString());
        String second = AutismSpotify.downloadArt(b.toUri().toString());
        assertTrue(!first.isEmpty(), "a file: download must succeed");
        assertEquals(first, second, "ONE stable temp path rewritten per track, not per-track churn");
        byte[] onDisk = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(second));
        assertTrue(AutismImageCodec.isPng(onDisk) && onDisk.length != 8,
            "the second download overwrote the first with the JPEG->PNG re-encode");
        java.nio.file.Files.deleteIfExists(a);
        java.nio.file.Files.deleteIfExists(b);
    }

    @Test
    void windowsScriptDeletesStaleArtAndHasNoBogusSlotComment() {
        String script = AutismSpotify.windowsScriptText();
        assertTrue(script.contains("Remove-Item $artPath"),
            "a failed save must delete the PREVIOUS track's art before the reuse branch");
        assertTrue(!script.contains("GetResults (slot 15)"),
            "the B6 comment claimed a GetResults call that does not exist");
    }

    @Test
    void itunesArtworkUrlExtraction() {
        String json = "{\"resultCount\":1,\"results\":[{\"wrapperType\":\"track\",\"artistName\":\"The Artist\","
            + "\"trackName\":\"The Title\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg\",\"trackId\":123}]}";
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg",
            AutismSpotify.extractArtworkUrl(json));
        assertEquals("", AutismSpotify.extractArtworkUrl("{\"resultCount\":0,\"results\":[]}"),
            "no results means empty, never a placeholder");
        assertEquals("", AutismSpotify.extractArtworkUrl(null));
        assertEquals("", AutismSpotify.extractArtworkUrl("garbage"));
    }

    @Test
    void artworkUrlUpgradeSwaps100For600() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/600x600bb.jpg",
            AutismSpotify.upgradeArtworkUrl("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg"));
        assertEquals("https://example.com/other.png",
            AutismSpotify.upgradeArtworkUrl("https://example.com/other.png"),
            "non-iTunes-shaped urls pass through untouched");
        assertEquals("", AutismSpotify.upgradeArtworkUrl(null));
    }

    @Test
    void storePreservesFetchedArtWhenLinesStayEmpty() {

        AutismSpotify.Snapshot withArt = AutismSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "1.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + "/tmp/fetched.png");
        AutismSpotify.store(withArt);
        long stamp = withArt.updatedAtMs();
        AutismSpotify.store(AutismSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "2.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + ""));
        assertEquals("/tmp/fetched.png", AutismSpotify.snapshot().artworkPath(),
            "an empty line art field must not erase the fetched path");
        assertEquals(stamp, AutismSpotify.snapshot().updatedAtMs());
        AutismSpotify.Snapshot newTrack = AutismSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Other Song" + US + "0.0" + US + "180.0" + US + "0" + US + "OFF" + US + "80" + US + "");
        AutismSpotify.store(newTrack);
        assertEquals("", AutismSpotify.snapshot().artworkPath(),
            "a new track starts art-empty again so the fallback re-fetches");
        assertEquals("Other Song", AutismSpotify.snapshot().title());
    }

    @Test
    void artContentSniffingRejectsNonImages() throws Exception {
        assertTrue(AutismSpotify.isImageBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x10}), "JPEG magic passes");
        assertTrue(AutismSpotify.isImageBytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}), "PNG magic passes");
        assertTrue(!AutismSpotify.isImageBytes("HTTP/1.1 200 OK".getBytes()), "a text body is not an image");
        assertTrue(!AutismSpotify.isImageBytes(new byte[]{1, 2}), "too short is not an image");
        assertTrue(!AutismSpotify.isImageBytes(null), "null is not an image");

        java.nio.file.Path png = java.nio.file.Files.createTempFile("art-png", ".bin");
        java.nio.file.Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        String good = AutismSpotify.downloadArt(png.toUri().toString());
        assertTrue(!good.isEmpty(), "a real PNG downloads");

        java.nio.file.Path text = java.nio.file.Files.createTempFile("art-txt", ".bin");
        java.nio.file.Files.write(text, "<html>error</html>".getBytes());
        assertEquals("", AutismSpotify.downloadArt(text.toUri().toString()),
            "a 200-OK text body must be a failure, not a served image");
        assertTrue(java.nio.file.Files.size(java.nio.file.Path.of(good)) > 0,
            "and the rejected download must NOT overwrite the previous good art");
        java.nio.file.Files.deleteIfExists(png);
        java.nio.file.Files.deleteIfExists(text);
    }

    @Test
    void transientArtFailuresAreNotCached() {

        String key = "no-such-artist-xyz|no-such-track-xyz";
        try {
            assertNull(AutismSpotify.queryArtFallback(key, (source, term) -> null),
                "a connection failure is transient, not a definitive no-result");
            assertTrue(!AutismSpotify.ART_FALLBACK_CACHE.containsKey(key),
                "transient misses must stay OUT of the cache so a replay re-tries");
        } finally {
            AutismSpotify.ART_FALLBACK_CACHE.remove(key);
        }
    }

    @Test
    void artFallbackConsultsBothSourcesEveryLookup() throws Exception {

        java.nio.file.Path itunesPng = java.nio.file.Files.createTempFile("art-mtx-i", ".png");
        java.nio.file.Path deezerPng = java.nio.file.Files.createTempFile("art-mtx-d", ".png");
        java.nio.file.Files.write(itunesPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        java.nio.file.Files.write(deezerPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 5, 6, 7, 8, 9});
        try {
            String iUrl = itunesPng.toUri().toString();
            String dUrl = deezerPng.toUri().toString();
            String iHit = "{\"results\":[{\"artistName\":\"A\",\"trackName\":\"B\",\"artworkUrl100\":\"" + iUrl + "\"}]}";
            String dHit = "{\"data\":[{\"title\":\"B\",\"artist\":{\"name\":\"A\"},\"album\":{\"cover_big\":\"" + dUrl + "\"}}]}";
            String iEmpty = "{\"results\":[]}";
            String dEmpty = "{\"data\":[]}";

            assertArrayEquals(java.nio.file.Files.readAllBytes(itunesPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iHit : dHit))),
                "an iTunes strict hit wins outright (iTunes first within the rung)");
            assertArrayEquals(java.nio.file.Files.readAllBytes(deezerPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : dHit))),
                "an iTunes definitive miss falls through to Deezer in the same cycle");
            assertArrayEquals(java.nio.file.Files.readAllBytes(deezerPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? null : dHit))),
                "an iTunes TRANSIENT failure must still consult Deezer in the same cycle");
            assertEquals("", AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : dEmpty),
                "both sources definitively empty at every rung is a cacheable no-result");
            assertNull(AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? null : dEmpty),
                "iTunes transient + Deezer definitive-empty stays retryable");
            assertNull(AutismSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : null),
                "Deezer transient + iTunes definitive-empty stays retryable");
            assertNull(AutismSpotify.queryArtFallback("A|B", (s, t) -> null),
                "both transient is a plain transient failure");
        } finally {
            java.nio.file.Files.deleteIfExists(itunesPng);
            java.nio.file.Files.deleteIfExists(deezerPng);
        }
    }

    @Test
    void positiveArtCacheServesOnlyTheLatestKey() {

        String a = "cache-test-a-" + System.nanoTime() + "|x";
        String b = "cache-test-b-" + System.nanoTime() + "|x";
        try {
            assertNull(AutismSpotify.artCacheLookup(a), "a cold cache has no opinion: run the query");
            AutismSpotify.artCacheStore(a, "/tmp/a.img");
            assertEquals("/tmp/a.img", AutismSpotify.artCacheLookup(a),
                "the file answers the key whose image it currently holds");
            AutismSpotify.artCacheStore(b, "/tmp/b.img");
            assertNull(AutismSpotify.artCacheLookup(a),
                "once B owns the file, A's positive entry is stale and must re-query");
            assertEquals("/tmp/b.img", AutismSpotify.artCacheLookup(b));
            AutismSpotify.artCacheStore(a, "");
            assertEquals("", AutismSpotify.artCacheLookup(a),
                "definitive negatives survive positive overwrites");
            assertNull(AutismSpotify.artCacheLookup(b + "-other"), "unknown keys stay cacheless");
            AutismSpotify.artCacheStore(b + "-other", null);
            assertNull(AutismSpotify.artCacheLookup(b + "-other"), "transient results are never stored");
        } finally {
            AutismSpotify.ART_FALLBACK_CACHE.remove(a);
            AutismSpotify.ART_FALLBACK_CACHE.remove(b);
        }
    }

    @Test
    void fallbackCacheEvictsEldestBeyondCap() {
        java.util.LinkedHashMap<String, String> seen = new java.util.LinkedHashMap<>();
        try {
            int n = AutismSpotify.ART_CACHE_MAX_ENTRIES + 5;
            for (int i = 0; i < n; i++) {
                String key = "evict-artist-" + i + "|track-" + i;
                AutismSpotify.ART_FALLBACK_CACHE.computeIfAbsent(key, k -> "cached-" + k);
                seen.put(key, "cached-" + key);
            }
            assertTrue(AutismSpotify.ART_FALLBACK_CACHE.size() <= AutismSpotify.ART_CACHE_MAX_ENTRIES,
                "the cache must stay bounded at " + AutismSpotify.ART_CACHE_MAX_ENTRIES);
            assertTrue(!AutismSpotify.ART_FALLBACK_CACHE.containsKey("evict-artist-0|track-0"),
                "the eldest entry must be the first evicted");
            assertTrue(AutismSpotify.ART_FALLBACK_CACHE.containsKey("evict-artist-" + (n - 1) + "|track-" + (n - 1)),
                "the newest entry must survive");
        } finally {
            for (String key : seen.keySet()) AutismSpotify.ART_FALLBACK_CACHE.remove(key);
        }
    }

    @Test
    void windowsScriptSubscribesByAumidAndPublishesArtAtomically() {
        String script = AutismSpotify.windowsScriptText();
        assertTrue(script.contains("$subscribedAumid"),
            "event re-subscription must key on the AUMID string, not RCW object identity");
        assertTrue(!script.contains("$subscribedSession"), "object-identity subscription must be gone");
        assertTrue(script.contains("$tmp = $path + '.part'"),
            "art must be published via a temp sibling, never a direct rewrite");
        assertTrue(script.contains("::Copy($tmp, $path, $true)"),
            "and copied over the stable path atomically-ish");
    }

    @Test
    void artStageLogSilentInProduction() {

        String stage = "test-stage-" + System.nanoTime();
        AutismSpotify.logArt(stage, "first");
        assertNull(AutismSpotify.ART_LOG_LAST.get(stage),
            "with DEBUG shipped false the stage logger must record nothing");
    }

    @Test
    void normalizeMusicTextStripsMarkers() {
        assertEquals("the perfect girl", AutismSpotify.normalizeMusicText("The Perfect Girl (The Motion Retrowave Remix)"),
            "parentheticals and the remix marker must both go");
        assertEquals("who is she x the perfect girl", AutismSpotify.normalizeMusicText("Who Is She x The Perfect Girl - Slowed & Reverb"),
            "the slowed/reverb markers must go");
        assertEquals("train ride", AutismSpotify.normalizeMusicText("Train Ride (Iris)"));
        assertEquals("1000 eyes", AutismSpotify.normalizeMusicText("1000 Eyes"));
        assertEquals("song title", AutismSpotify.normalizeMusicText("Song Title [Official Audio]"));
        assertEquals("alive", AutismSpotify.normalizeMusicText("Alive (feat. Someone)"));
        assertEquals("alive", AutismSpotify.normalizeMusicText("Alive (ft. Someone)"));
        assertEquals("alive", AutismSpotify.normalizeMusicText("Alive (featuring Someone)"));
        assertEquals("dash", AutismSpotify.normalizeMusicText("Dash (Sped Up)"));
        assertEquals("dash", AutismSpotify.normalizeMusicText("Dash (Nightcore)"));
        assertEquals("a b c d e f", AutismSpotify.normalizeMusicText("A-B/C_D'E\"F"),
            "every non-alphanumeric run collapses to one space");
        assertEquals("", AutismSpotify.normalizeMusicText(null));
    }

    @Test
    void artworkCandidateValidationRules() {

        assertTrue(AutismSpotify.validatesArtworkCandidate("1000 Eyes", "Train Ride", "1000 Eyes", "Train Ride (Iris)"),
            "exact artist + title-with-parenthetical must MATCH");
        assertTrue(!AutismSpotify.validatesArtworkCandidate("Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb",
                "Mareux", "The Perfect Girl"),
            "title match with artist mismatch must REJECT (no shared artist token)");
        assertTrue(!AutismSpotify.validatesArtworkCandidate("Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb",
                "I Monster", "Who Is She?"),
            "another artist mismatch must REJECT");
        assertTrue(AutismSpotify.validatesArtworkCandidate("Mareux", "The Perfect Girl - Slowed & Reverb",
                "Mareux", "The Perfect Girl (The Motion Retrowave Remix)"),
            "rip-decorated snapshot vs remix-decorated store entry must MATCH");
        assertTrue(!AutismSpotify.validatesArtworkCandidate("Mareux", "The Perfect Girl", "Mareux", "A Different Song"),
            "same artist, unrelated title must REJECT (no title containment)");
    }

    @Test
    void itunesCandidateSelectionPrefersFirstValidating() {
        String json = "{\"resultCount\":3,\"results\":["
            + "{\"artistName\":\"Mareux\",\"trackName\":\"The Perfect Girl\",\"artworkUrl100\":\"https://img/100x100bb-1.jpg\"},"
            + "{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She x The Perfect Girl\",\"artworkUrl100\":\"https://img/100x100bb-2.jpg\"},"
            + "{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She\",\"artworkUrl100\":\"https://img/100x100bb-3.jpg\"}]}";
        java.util.List<AutismSpotify.ArtCandidate> candidates = AutismSpotify.itunesCandidates(json);
        assertEquals(3, candidates.size(), "all three results must parse as candidates");
        assertEquals("Mareux", candidates.get(0).artist());
        assertEquals("The Perfect Girl", candidates.get(0).title());
        AutismSpotify.ArtCandidate picked = AutismSpotify.firstValidatingCandidate(candidates,
            "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb");
        assertNotNull(picked, "one of the three must validate");
        assertEquals("https://img/100x100bb-2.jpg", picked.imageUrl(),
            "the FIRST validating candidate wins, not the fuzzy first result");
    }

    @Test
    void deezerResponseParsingAndValidation() {
        String json = "{\"data\":["
            + "{\"title\":\"The Perfect Girl\",\"artist\":{\"name\":\"Mareux\"},\"album\":{\"cover_xl\":\"https://img/xl-1.jpg\"}},"
            + "{\"title\":\"Who Is She x The Perfect Girl\",\"artist\":{\"name\":\"Myongz\"},\"album\":{\"cover_xl\":\"https://img/xl-2.jpg\"}}],\"total\":2}";
        java.util.List<AutismSpotify.ArtCandidate> candidates = AutismSpotify.deezerCandidates(json);
        assertEquals(2, candidates.size(), "both data items must parse as candidates");
        assertEquals("The Perfect Girl", candidates.get(0).title());
        assertEquals("Mareux", candidates.get(0).artist());
        AutismSpotify.ArtCandidate picked = AutismSpotify.firstValidatingCandidate(candidates,
            "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb");
        assertNotNull(picked, "one of the two must validate");
        assertEquals("https://img/xl-2.jpg", picked.imageUrl(),
            "the validating Deezer candidate wins over the fuzzy first");
    }

    @Test
    void ladderDegradesButNeverCollapsesWhileCandidatesExist() throws Exception {

        java.util.List<AutismSpotify.ArtCandidate> junk = java.util.List.of(
            new AutismSpotify.ArtCandidate("Mareux", "The Perfect Girl", "https://img/1.jpg"),
            new AutismSpotify.ArtCandidate("I Monster", "Who Is She?", "https://img/2.jpg"));
        assertNull(AutismSpotify.firstValidatingCandidate(junk, "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb"),
            "the strict rung still rejects every wrong-artist candidate");

        String key = "Myongz|Who Is She x The Perfect Girl - Slowed & Reverb";
        java.util.List<AutismSpotify.ArtCandidate> titleOnly = java.util.List.of(
            new AutismSpotify.ArtCandidate("Mareux", "The Perfect Girl", "u1"));
        AutismSpotify.Selection byTitle = AutismSpotify.selectBest(key, titleOnly, java.util.List.of());
        assertEquals("u1", byTitle.candidate().imageUrl(), "a title-only match still serves");
        assertEquals(1, byTitle.rung(), "...at the title rung");

        java.util.List<AutismSpotify.ArtCandidate> unrelated = java.util.List.of(
            new AutismSpotify.ArtCandidate("Someone Else", "Completely Different", "u2"));
        AutismSpotify.Selection topHit = AutismSpotify.selectBest(key, unrelated, java.util.List.of());
        assertEquals("u2", topHit.candidate().imageUrl(), "even a fully unrelated candidate beats an empty frame");
        assertEquals(3, topHit.rung(), "...at the last-resort rung");

        assertEquals("", AutismSpotify.queryArtFallback("A|B",
                (s, t) -> s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}"),
            "\"\" only when zero candidates exist at every rung on both sources");
        assertEquals("", AutismSpotify.queryArtFallback("A|B",
                (s, t) -> s.equals("itunes") ? "{\"resultCount\":0,\"results\":[]}" : "{\"data\":[],\"total\":0}"),
            "same for explicit empty-result payloads");

        java.nio.file.Path good = java.nio.file.Files.createTempFile("art-valid", ".png");
        java.nio.file.Files.write(good, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        try {
            String goodItunes = "{\"results\":[{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She x The Perfect Girl\",\"artworkUrl100\":\"" + good.toUri().toString() + "\"}]}";
            assertTrue(!AutismSpotify.queryArtFallback(key, (s, t) -> s.equals("itunes") ? goodItunes : "{\"data\":[]}").isEmpty(),
                "a validating candidate's image must download");
        } finally {
            java.nio.file.Files.deleteIfExists(good);
        }
    }

    @Test
    void artCandidateLadderOrdersStrictThenTitleThenArtistThenAny() {
        java.util.List<AutismSpotify.ArtCandidate> candidates = java.util.List.of(
            new AutismSpotify.ArtCandidate("Totally Other", "Unrelated Song", "u1"),
            new AutismSpotify.ArtCandidate("DVRST", "Some Other Track", "u2"),
            new AutismSpotify.ArtCandidate("Someone Else", "Bloody Morning", "u3"),
            new AutismSpotify.ArtCandidate("DVRST", "Bloody Morning", "u4"));
        AutismSpotify.Selection strict = AutismSpotify.selectBest("DVRST|Bloody Morning", candidates, java.util.List.of());
        assertEquals("u4", strict.candidate().imageUrl(), "the strict match wins regardless of list position");
        assertEquals(0, strict.rung());
        AutismSpotify.Selection title = AutismSpotify.selectBest("Nobody|Bloody Morning (Slowed)", candidates, java.util.List.of());
        assertEquals("u3", title.candidate().imageUrl(), "no strict match: the title-only rung beats artist-only and any");
        assertEquals(1, title.rung());
        AutismSpotify.Selection artist = AutismSpotify.selectBest("DVRST|Unheard Track", candidates, java.util.List.of());
        assertEquals("u2", artist.candidate().imageUrl(), "no title match: the artist-only rung beats the top hit");
        assertEquals(2, artist.rung());
        AutismSpotify.Selection any = AutismSpotify.selectBest("Nobody|Nothing Here", candidates, java.util.List.of());
        assertEquals("u1", any.candidate().imageUrl(), "nothing matches at all: the search's top hit beats an empty frame");
        assertEquals(3, any.rung());
        assertNull(AutismSpotify.selectBest("DVRST|Bloody Morning", java.util.List.of(), java.util.List.of()).candidate(),
            "zero candidates is the ONLY no-pick (source has literally nothing)");
    }

    @Test
    void artFallbackWidensToArtistOnlyQueryAsLastRung() throws Exception {
        java.nio.file.Path png = java.nio.file.Files.createTempFile("art-widen", ".png");
        java.nio.file.Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        try {
            String url = png.toUri().toString();
            java.util.List<String> seen = new java.util.ArrayList<>();
            java.util.function.BiFunction<String, String, String> trackMissArtistHit = (s, t) -> {
                seen.add(s + ":" + t);
                if (t.equals("a")) {
                    return s.equals("itunes")
                        ? "{\"results\":[{\"artistName\":\"a\",\"trackName\":\"anything\",\"artworkUrl100\":\"" + url + "\"}]}"
                        : "{\"data\":[]}";
                }
                return s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}";
            };
            assertTrue(!AutismSpotify.queryArtFallback("a|b", trackMissArtistHit).isEmpty(),
                "track-level definitive-empty on both sources widens to the artist-only query");
            assertTrue(seen.contains("itunes:a"), "the widened rung queries the artist alone");
            java.util.function.BiFunction<String, String, String> empty = (s, t) -> s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}";
            assertEquals("", AutismSpotify.queryArtFallback("a|", empty),
                "an already artist-only key never re-widens (one rung deep, no loop)");
            assertEquals("", AutismSpotify.queryArtFallback("|b", empty),
                "no artist means there is nothing to widen to");
        } finally {
            java.nio.file.Files.deleteIfExists(png);
        }
    }

    @Test
    void coverJunkDemotionRules() {

        assertTrue(AutismSpotify.isJunkCover("21 Savage", "ball w/o you",
            new AutismSpotify.ArtCandidate("8-Bit Arcade", "Ball w/o You (8-Bit 21 Savage Emulation)", "u")),
            "chiptune emulation products are junk");
        assertTrue(AutismSpotify.isJunkCover("21 Savage", "ball w/o you",
            new AutismSpotify.ArtCandidate("Arcade Player", "Ball w/o You (16-Bit 21 Savage Emulation)", "u")));
        assertTrue(AutismSpotify.isJunkCover("21 Savage", "ball w/o you",
            new AutismSpotify.ArtCandidate("Sunday Without You", "Ball W/O You (Lofi Version)", "u")),
            "lofi-cover versions are junk");
        assertTrue(AutismSpotify.isJunkCover("Artist", "Song",
            new AutismSpotify.ArtCandidate("The Karaoke Crew", "Song (Karaoke Version)", "u")));
        assertTrue(AutismSpotify.isJunkCover("Artist", "Song",
            new AutismSpotify.ArtCandidate("Tribute Band", "Song - A Tribute", "u")));

        assertTrue(!AutismSpotify.isJunkCover("21 Savage", "ball w/o you",
            new AutismSpotify.ArtCandidate("21 Savage", "ball w/o You", "u")),
            "the exact track is never junk");

        assertTrue(!AutismSpotify.isJunkCover("Artist", "Song (Lofi Version)",
            new AutismSpotify.ArtCandidate("Artist", "Song (Lofi Version)", "u")),
            "a snapshot carrying the marker itself must not junk itself");

        java.util.List<AutismSpotify.ArtCandidate> onlyJunk = java.util.List.of(
            new AutismSpotify.ArtCandidate("8-Bit Arcade", "Real Song (8-Bit Emulation)", "u"));
        AutismSpotify.Selection lastResort = AutismSpotify.selectBest("Real Artist|Real Song", onlyJunk, java.util.List.of());
        assertEquals("u", lastResort.candidate().imageUrl(), "junk still serves as the last resort (never nothing)");
        assertEquals(3, lastResort.rung(), "...but only at rung 3, never earlier");
    }

    @Test
    void ballWithoutYouSelectsTheRealCoverOverJunkVersions() throws Exception {

        java.nio.file.Path realPng = java.nio.file.Files.createTempFile("art-real", ".png");
        java.nio.file.Path junkPng = java.nio.file.Files.createTempFile("art-junk", ".png");
        java.nio.file.Files.write(realPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 1, 1, 1});
        java.nio.file.Files.write(junkPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 2, 2, 2, 2, 2});
        try {
            String junk = junkPng.toUri().toString();
            String real = realPng.toUri().toString();
            String itunes = "{\"resultCount\":5,\"results\":["
                + "{\"artistName\":\"Sunday Without You\",\"trackName\":\"Ball W/O You (Lofi Version)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"21 Savage\",\"trackName\":\"ball w/o You\",\"artworkUrl100\":\"" + real + "\"},"
                + "{\"artistName\":\"8-Bit Arcade\",\"trackName\":\"Ball w/o You (8-Bit 21 Savage Emulation)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"Lloyd\",\"trackName\":\"You (Edited)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"Arcade Player\",\"trackName\":\"Ball w/o You (16-Bit 21 Savage Emulation)\",\"artworkUrl100\":\"" + junk + "\"}]}";
            String path = AutismSpotify.queryArtFallback("21 Savage|ball w/o you",
                (s, t) -> s.equals("itunes") ? itunes : "{\"data\":[]}");
            assertTrue(!path.isEmpty(), "the real track's art must download");
            assertArrayEquals(java.nio.file.Files.readAllBytes(realPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                "the strict rung must pick the REAL 21 Savage cover, never a junk version");
        } finally {
            java.nio.file.Files.deleteIfExists(realPng);
            java.nio.file.Files.deleteIfExists(junkPng);
        }
    }

    @Test
    void poundsAndShroomsPrefersDeezerStrictOverItunesArtistRung() throws Exception {

        java.nio.file.Path realPng = java.nio.file.Files.createTempFile("art-real2", ".png");
        java.nio.file.Path albumPng = java.nio.file.Files.createTempFile("art-album2", ".png");
        java.nio.file.Files.write(realPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 3, 3, 3, 3});
        java.nio.file.Files.write(albumPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 4, 4, 4, 4, 4});
        try {
            String album = albumPng.toUri().toString();
            String real = realPng.toUri().toString();
            String itunes = "{\"resultCount\":4,\"results\":["
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"Dirty Laundry\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"A Helping Hand\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"Make a Play (feat. J.R. Donato)\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Fetty Wap\",\"trackName\":\"Like A Taylor (feat. Wiz Khalifa)\",\"artworkUrl100\":\"" + album + "\"}]}";
            String deezer = "{\"data\":[{\"title\":\"Pounds and Shrooms\",\"artist\":{\"name\":\"Wiz Khalifa\"},"
                + "\"album\":{\"cover_big\":\"" + real + "\"}}],\"total\":1}";
            String path = AutismSpotify.queryArtFallback("Wiz Khalifa|Pounds And Shrooms",
                (s, t) -> s.equals("itunes") ? itunes : deezer);
            assertTrue(!path.isEmpty(), "the exact song's art must download");
            assertArrayEquals(java.nio.file.Files.readAllBytes(realPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                "Deezer's strict match must beat iTunes's artist-rung album cover");
        } finally {
            java.nio.file.Files.deleteIfExists(realPng);
            java.nio.file.Files.deleteIfExists(albumPng);
        }
    }

}
