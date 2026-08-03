package autismclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autismclient.util.lan.LanPacket;
import autismclient.util.lan.LanPacketType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LanMacroSyncTest {

    @Test
    void keyIsCaseInsensitiveAndTrimmed() {
        assertEquals(MacroNames.key("AHH"), MacroNames.key("ahh"));
        assertEquals(MacroNames.key("AHH"), MacroNames.key("  Ahh  "));
        assertEquals("", MacroNames.key(null));
        assertEquals("", MacroNames.key("   "));
        assertTrue(MacroNames.equal("Farm Bot", "farm bot"));
        assertFalse(MacroNames.equal("Farm Bot", "farmbot"));
    }

    @Test
    void caseOnlyEditCountsAsARename() {

        assertTrue(MacroNames.renamed("ahh", "AHH"));
        assertTrue(MacroNames.renamed("ahh", "farm"));
        assertFalse(MacroNames.renamed("ahh", "ahh"));
        assertFalse(MacroNames.renamed(null, "ahh"));
    }

    @Test
    void rosterKeyedByCanonicalNameFindsAPeerHoldingOtherCasing() {

        Map<String, String> peerRoster = new LinkedHashMap<>();
        peerRoster.put(MacroNames.key("ahh"), "ahh");

        assertTrue(peerRoster.containsKey(MacroNames.key("AHH")));
        assertFalse(peerRoster.containsKey(MacroNames.key("AHH2")));
    }

    @Test
    void bucketingByCanonicalKeyDoesNotListOneMacroAsBothMineAndTheirs() {
        Set<String> mine = new HashSet<>(List.of(MacroNames.key("AHH")));
        Set<String> theirs = new HashSet<>(List.of(MacroNames.key("ahh")));

        List<String> peerOnly = new ArrayList<>();
        List<String> mineOnly = new ArrayList<>();
        for (String key : theirs) {
            if (!mine.contains(key)) peerOnly.add(key);
        }
        for (String key : mine) {
            if (!theirs.contains(key)) mineOnly.add(key);
        }

        assertTrue(peerOnly.isEmpty());
        assertTrue(mineOnly.isEmpty());
    }

    @Test
    void fullListPrunesAStaleNameFromAPeerRoster() {

        Map<String, String> roster = new LinkedHashMap<>();
        roster.put(MacroNames.key("ahh"), "ahh");
        roster.put(MacroNames.key("old name"), "old name");

        Set<String> live = new HashSet<>();
        for (String name : List.of("ahh", "new name")) live.add(MacroNames.key(name));
        roster.keySet().retainAll(live);

        assertTrue(roster.containsKey(MacroNames.key("AHH")));
        assertFalse(roster.containsKey(MacroNames.key("old name")));
    }

    @Test
    void macroListPacketRoundTrips() throws IOException {
        LanPacket.MacroListPacket sent = new LanPacket.MacroListPacket(
            "session-1", "Peer", new ArrayList<>(List.of("AHH", "Farm Bot", "ahh 2")));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        sent.write(new DataOutputStream(bytes));

        LanPacket.MacroListPacket received = new LanPacket.MacroListPacket();
        received.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(LanPacketType.MACRO_LIST, received.getType());
        assertEquals("Peer", received.senderUsername);
        assertEquals(List.of("AHH", "Farm Bot", "ahh 2"), received.macroNames);
    }

    @Test
    void macroListRejectsAnImpossibleLengthInsteadOfSizingByIt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("Peer");
        out.writeInt(Integer.MAX_VALUE);

        LanPacket.MacroListPacket received = new LanPacket.MacroListPacket();
        assertThrows(IOException.class,
            () -> received.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void macroChunkRejectsAnImpossibleLengthBeforeAllocating() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("Peer");
        out.writeUTF("AHH");
        out.writeByte(0);
        out.writeInt(0);
        out.writeInt(1);
        out.writeInt(Integer.MAX_VALUE);

        LanPacket.MacroDataChunkPacket received = new LanPacket.MacroDataChunkPacket();
        assertThrows(IOException.class,
            () -> received.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void chunkWireLimitCoversWhatTheSenderProduces() {

        assertTrue(LanPacket.MacroDataChunkPacket.MAX_CHUNK_BYTES >= 32_000);
    }

    @Test
    void macroChunkRoundTripsAtTheWireLimit() throws IOException {
        byte[] payload = new byte[32_000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
        LanPacket.MacroDataChunkPacket sent =
            new LanPacket.MacroDataChunkPacket("session-1", "Peer", "AHH", (byte) 0, 1, 3, payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        sent.write(new DataOutputStream(bytes));

        LanPacket.MacroDataChunkPacket received = new LanPacket.MacroDataChunkPacket();
        received.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals("AHH", received.macroName);
        assertEquals(1, received.chunkIndex);
        assertEquals(3, received.totalChunks);
        assertEquals(32_000, received.chunkData.length);
        assertEquals(payload[31_999], received.chunkData[31_999]);
    }

    @Test
    void clientListRejectsAnImpossibleLength() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(Integer.MAX_VALUE);

        LanPacket.ClientListPacket received = new LanPacket.ClientListPacket("session-1", new ArrayList<>());
        assertThrows(IOException.class,
            () -> received.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void clientListReconcileNeverLeavesTheMapEmpty() {

        Map<String, Boolean> connected = new LinkedHashMap<>();
        connected.put("Me", false);
        connected.put("Gone", false);

        Set<String> listed = new HashSet<>();
        for (String name : List.of("Me", "Fresh")) {
            connected.put(name, false);
            listed.add(name);
        }
        assertTrue(connected.containsKey("Me"));
        connected.keySet().retainAll(listed);

        assertEquals(Set.of("Me", "Fresh"), connected.keySet());
        assertFalse(connected.isEmpty());
    }

    @Test
    void legacyLibraryCollapsesTwoCaseVariantsToTheLaterOne() {

        List<String> onDisk = List.of("AHH", "Farm", "ahh");

        List<String> loaded = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String name : onDisk) {
            Integer previous = seen.get(MacroNames.key(name));
            if (previous != null) {
                loaded.set(previous, name);
            } else {
                seen.put(MacroNames.key(name), loaded.size());
                loaded.add(name);
            }
        }

        assertEquals(List.of("ahh", "Farm"), loaded);
    }

    @Test
    void everyPacketTypeIsConstructibleSoAReaderNeverDesyncs() {

        for (LanPacketType type : LanPacketType.values()) {
            assertNotEquals(null, LanPacket.create(type, "session-1"),
                "LanPacket.create returned null for " + type);
        }
    }
}
