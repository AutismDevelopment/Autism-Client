package autismclient.util.multi;

import autismclient.util.AutismAccount;
import autismclient.util.AutismProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProfileTest {
    @Test
    void heartbeatDefaultsOnIncludingLegacyTags() {
        assertTrue(new MultiPacketPolicy().autoPosition());
        assertTrue(MultiPacketPolicy.fromTag(new CompoundTag()).autoPosition());
    }

    @Test
    void chatDuplicatesMergeForTwoSecondsWithoutAVisibilityDelay() {
        assertTrue(MultiManager.withinChatMergeWindow(1_000L, 1_000L));
        assertTrue(MultiManager.withinChatMergeWindow(1_000L, 3_000L));
        assertFalse(MultiManager.withinChatMergeWindow(1_000L, 3_001L));
    }

    @Test
    void idleHeartbeatUsesOneSecondCadence() {
        assertFalse(MultiSession.shouldSendIdleHeartbeat(true, 1_000L, 1_999L));
        assertTrue(MultiSession.shouldSendIdleHeartbeat(true, 1_000L, 2_000L));
        assertFalse(MultiSession.shouldSendIdleHeartbeat(false, 0L, 10_000L));
    }

    @Test
    void sessionCapMatchesTheUiContract() {
        assertEquals(500, MultiProfile.MAX_SESSIONS);
    }

    @Test
    void retryOnlyAcceptsTerminalSessions() {
        assertTrue(MultiManager.isRetryable(MultiSession.Status.FAILED));
        assertTrue(MultiManager.isRetryable(MultiSession.Status.DISCONNECTED));
        assertFalse(MultiManager.isRetryable(MultiSession.Status.CONNECTING));
        assertFalse(MultiManager.isRetryable(MultiSession.Status.READY));
    }

    @Test
    void macroFinishNotesPreserveNamesWithSpaces() {
        MultiManager.MacroFinish finish = MultiManager.parseMacroFinish("My Macro" + Character.toString(0) + "done");
        assertEquals("My Macro", finish.macroName());
        assertEquals("done", finish.reason());
    }

    @Test
    void visibleChatRemovesControlCharactersAndLineBreaks() {
        Component safe = MultiManager.sanitizeComponent(Component.literal("A\n\t B\u0001C"), 64);
        assertEquals("A B C", safe.getString());
    }

    @Test
    void replacesMacroReferencesWithoutChangingOtherAssignments() {
        MultiProfile profile = new MultiProfile();
        profile.allMacroName = "Old";
        profile.sessions.add(new MultiProfile.SessionSpec("a", "", "Old"));
        profile.sessions.add(new MultiProfile.SessionSpec("b", "", "Other"));

        assertTrue(profile.replaceMacroReference("Old", "New"));
        assertEquals("New", profile.allMacroName);
        assertEquals("New", profile.sessions.get(0).macroName());
        assertEquals("Other", profile.sessions.get(1).macroName());
        assertFalse(profile.replaceMacroReference("Missing", "Nope"));
    }

    @Test
    void identityGuardRejectsRenderedAndDuplicateProfiles() {
        UUID rendered = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        HashMap<UUID, String> resolved = new HashMap<>();

        assertEquals("Account is already used by the rendered client",
            MultiManager.identityRejection(rendered, resolved, "current", rendered));
        assertTrue(resolved.isEmpty());
        assertEquals("", MultiManager.identityRejection(rendered, resolved, "first", other));
        assertEquals("Duplicate Minecraft identity in this batch",
            MultiManager.identityRejection(rendered, resolved, "second", other));
        assertEquals(other, MultiManager.parseKnownProfileId(other.toString().replace("-", "")));
    }

    @Test
    void normalizesDuplicateAccountsAndCapsSessions() {
        MultiProfile profile = new MultiProfile();
        for (int i = 0; i < MultiProfile.MAX_SESSIONS + 8; i++) {
            profile.sessions.add(new MultiProfile.SessionSpec("account-" + i, ""));
        }
        profile.sessions.add(new MultiProfile.SessionSpec("account-1", "proxy"));
        profile.normalize();
        assertEquals(MultiProfile.MAX_SESSIONS, profile.sessions.size());
        assertEquals(1, profile.sessions.stream().filter(s -> s.accountId().equals("account-1")).count());
    }

    @Test
    void packetPolicyRoundTripsAndHonorsPrecedence() {
        MultiPacketPolicy policy = new MultiPacketPolicy();
        policy.setSlot(0, new MultiPacketPolicy.Slot("example.Packet", false));
        policy.setBlocklist(java.util.List.of(new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C, "example.Inbound")));

        CompoundTag encoded = policy.toTag();
        MultiPacketPolicy decoded = MultiPacketPolicy.fromTag(encoded);

        assertTrue(decoded.allows(MultiPacketPolicy.Direction.C2S, "move.Packet", false, true));
        assertFalse(decoded.allows(MultiPacketPolicy.Direction.C2S, "example.Packet", false, false));
        assertFalse(decoded.allows(MultiPacketPolicy.Direction.S2C, "example.Inbound", false, false));
        assertTrue(decoded.allows(MultiPacketPolicy.Direction.S2C, "example.Inbound", true, false));
        assertTrue(decoded.allows(MultiPacketPolicy.Direction.C2S, "example.Packet", true, false));
    }

    @Test
    void protectedPacketsCannotBePersistedInBlocklist() {
        MultiPacketPolicy policy = new MultiPacketPolicy();
        policy.setBlocklist(List.of(
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C,
                "net.minecraft.network.protocol.common.ClientboundKeepAlivePacket"),
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.C2S,
                "net.minecraft.network.protocol.game.ServerboundChatAckPacket"),
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C,
                "net.minecraft.network.protocol.login.ClientboundCustomQueryPacket"),
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C,
                "net.minecraft.network.protocol.game.ClientboundAddEntityPacket"),
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C,
                "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$PosRot"),
            new MultiPacketPolicy.Rule(MultiPacketPolicy.Direction.S2C, "example.OptionalPacket")
        ));
        assertEquals(1, policy.blocklist().size());
        assertEquals("example.OptionalPacket", policy.blocklist().getFirst().packetClass());
        assertTrue(MultiPacketPolicy.isProtected(MultiPacketPolicy.Direction.S2C,
            "net.minecraft.network.protocol.common.ClientboundShowDialogPacket"));
        assertTrue(MultiPacketPolicy.isProtected(MultiPacketPolicy.Direction.S2C,
            "net.minecraft.network.protocol.common.ClientboundClearDialogPacket"));
        assertTrue(MultiPacketPolicy.isProtected(MultiPacketPolicy.Direction.C2S,
            "net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket"));
        assertTrue(MultiPacketPolicy.isProtected(MultiPacketPolicy.Direction.S2C,
            "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos"));
    }

    @Test
    void profileRoundTripsPacingAndPolicy() {
        MultiProfile profile = new MultiProfile();
        profile.name = "Farm";
        profile.serverAddress = "localhost:25565";
        profile.pacing = MultiProfile.Pacing.Custom;
        profile.customConcurrency = 12;
        profile.customDelayMs = 80;
        profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        profile.packetPolicy.setAutoSwing(true);
        profile.setQuickAction(0, new MultiQuickAction("Swing", ServerboundSwingPacket.class.getName(), ""));

        MultiProfile decoded = MultiProfile.fromTag(profile.toTag());
        assertEquals("Farm", decoded.name);
        assertEquals(12, decoded.concurrency());
        assertEquals(80, decoded.delayMs());
        assertTrue(decoded.packetPolicy.autoSwing());
        assertEquals("Swing", decoded.quickAction(0).name);
        assertEquals(ServerboundSwingPacket.class.getName(), decoded.quickAction(0).firstPacketClass());
    }

    @Test
    void proxyModeRoundTripsAndMigratesLegacyProfiles() {
        MultiProfile profile = new MultiProfile();
        profile.proxyMode = MultiProfile.ProxyMode.Auto;
        profile.autoMaxPingMs = 123;
        profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        MultiProfile decoded = MultiProfile.fromTag(profile.toTag());
        assertEquals(MultiProfile.ProxyMode.Auto, decoded.proxyMode);
        assertEquals(123, decoded.autoMaxPingMs);

        CompoundTag legacyManual = new CompoundTag();
        legacyManual.putString("name", "Legacy");
        legacyManual.putString("server", "localhost:25565");
        net.minecraft.nbt.ListTag manualSessions = new net.minecraft.nbt.ListTag();
        CompoundTag withProxy = new CompoundTag();
        withProxy.putString("accountId", "acc");
        withProxy.putString("proxyId", "proxy-1");
        manualSessions.add(withProxy);
        legacyManual.put("sessions", manualSessions);
        MultiProfile decodedManual = MultiProfile.fromTag(legacyManual);
        assertEquals(MultiProfile.ProxyMode.Manual, decodedManual.proxyMode);
        assertEquals(200, decodedManual.autoMaxPingMs);

        CompoundTag legacyOff = new CompoundTag();
        legacyOff.putString("name", "LegacyOff");
        legacyOff.putString("server", "localhost:25565");
        net.minecraft.nbt.ListTag directSessions = new net.minecraft.nbt.ListTag();
        CompoundTag direct = new CompoundTag();
        direct.putString("accountId", "acc");
        direct.putString("proxyId", "");
        directSessions.add(direct);
        legacyOff.put("sessions", directSessions);
        assertEquals(MultiProfile.ProxyMode.Off, MultiProfile.fromTag(legacyOff).proxyMode);
    }

    @Test
    void loginModeRoundTripsAndDefaultsToAutoForLegacyProfiles() {
        MultiProfile profile = new MultiProfile();
        profile.loginMode = MultiProfile.LoginMode.Custom;
        profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        assertEquals(MultiProfile.LoginMode.Custom, MultiProfile.fromTag(profile.toTag()).loginMode);

        profile.loginMode = MultiProfile.LoginMode.Off;
        assertEquals(MultiProfile.LoginMode.Off, MultiProfile.fromTag(profile.toTag()).loginMode);

        CompoundTag legacy = new CompoundTag();
        legacy.putString("name", "Legacy");
        legacy.putString("server", "localhost:25565");
        assertEquals(MultiProfile.LoginMode.Auto, MultiProfile.fromTag(legacy).loginMode);
    }

    @Test
    void migratesEnabledOldSlotsIntoQuickActions() {
        MultiPacketPolicy oldPolicy = new MultiPacketPolicy();
        oldPolicy.setSlot(0, new MultiPacketPolicy.Slot(ServerboundSwingPacket.class.getName(), true));
        oldPolicy.setSlot(1, new MultiPacketPolicy.Slot("example.Blocked", false));

        CompoundTag tag = new CompoundTag();
        tag.putString("name", "Legacy");
        tag.putString("server", "localhost:25565");
        tag.putString("pacing", MultiProfile.Pacing.Balanced.name());
        tag.put("packetPolicy", oldPolicy.toTag());

        MultiProfile decoded = MultiProfile.fromTag(tag);

        assertEquals(ServerboundSwingPacket.class.getName(), decoded.quickAction(0).firstPacketClass());
        assertTrue(decoded.quickAction(1).empty());
    }

    @Test
    void quickActionMultiStepRoundTripsAndMigratesLegacySinglePacket() {
        MultiQuickAction action = new MultiQuickAction();
        action.name = "Combo";
        action.steps.add(new MultiQuickAction.Step(ServerboundSwingPacket.class.getName(), ""));
        action.steps.add(new MultiQuickAction.Step(ServerboundSwingPacket.class.getName(), "1"));
        MultiProfile profile = new MultiProfile();
        profile.setQuickAction(0, action);
        MultiProfile decoded = MultiProfile.fromTag(profile.toTag());
        assertEquals("Combo", decoded.quickAction(0).name);
        assertEquals(2, decoded.quickAction(0).packetCount());
        assertEquals(ServerboundSwingPacket.class.getName(), decoded.quickAction(0).firstPacketClass());

        CompoundTag legacy = new CompoundTag();
        legacy.putString("name", "Old");
        legacy.putString("packet", ServerboundSwingPacket.class.getName());
        legacy.putString("args", "");
        net.minecraft.nbt.ListTag actions = new net.minecraft.nbt.ListTag();
        actions.add(legacy);
        CompoundTag profileTag = new CompoundTag();
        profileTag.putString("name", "P");
        profileTag.putString("server", "localhost:25565");
        profileTag.put("quickActions", actions);
        MultiProfile migrated = MultiProfile.fromTag(profileTag);
        assertEquals(1, migrated.quickAction(0).packetCount());
        assertEquals(ServerboundSwingPacket.class.getName(), migrated.quickAction(0).firstPacketClass());
    }

    @Test
    void quickActionsClearAndResetToDefaults() {
        MultiProfile profile = new MultiProfile();
        profile.setQuickAction(0, new MultiQuickAction("Swing", ServerboundSwingPacket.class.getName(), ""));
        assertFalse(profile.quickAction(0).empty());

        profile.setQuickAction(0, new MultiQuickAction());
        assertTrue(profile.quickAction(0).empty());

        profile.setQuickAction(1, new MultiQuickAction("Swing", ServerboundSwingPacket.class.getName(), ""));
        profile.resetQuickActions();
        for (int i = 0; i < MultiProfile.QUICK_ACTIONS; i++) assertTrue(profile.quickAction(i).empty());
    }

    @Test
    void movementPrecheckRequiresReadyAndPosition() {
        assertEquals("Session is not ready", MultiSession.movementPrecheck(MultiSession.Status.CONNECTING, true));
        assertEquals("Position is not ready", MultiSession.movementPrecheck(MultiSession.Status.READY, false));
        assertEquals("", MultiSession.movementPrecheck(MultiSession.Status.READY, true));
    }

    @Test
    void legacyAccountsAndProxiesReceiveStableIds() {
        AutismAccount account = new AutismAccount(new CompoundTag());
        AutismProxy proxy = new AutismProxy(new CompoundTag());
        assertFalse(account.stableId().isBlank());
        assertFalse(proxy.stableId().isBlank());
        assertEquals(account.stableId(), new AutismAccount(account.toTag()).stableId());
        assertEquals(proxy.stableId(), new AutismProxy(proxy.toTag()).stableId());
    }

    @Test
    void pacingPresetsMatchConfiguredLimits() {
        MultiProfile profile = new MultiProfile();
        profile.pacing = MultiProfile.Pacing.Gentle;
        assertEquals(1, profile.concurrency());
        assertEquals(1000, profile.delayMs());
        profile.pacing = MultiProfile.Pacing.Balanced;
        assertEquals(4, profile.concurrency());
        assertEquals(250, profile.delayMs());
        profile.pacing = MultiProfile.Pacing.Fast;
        assertEquals(8, profile.concurrency());
        assertEquals(100, profile.delayMs());
        profile.pacing = MultiProfile.Pacing.Immediate;
        assertEquals(500, profile.concurrency());
        assertEquals(0, profile.delayMs());
    }

    @Test
    void directConnectionContextStillOverridesGlobalProxy() {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        MultiConnectionContext.register(connection, null);
        assertTrue(MultiConnectionContext.isMulti(connection));
        assertEquals(null, MultiConnectionContext.proxy(connection));
        MultiConnectionContext.remove(connection);
        assertFalse(MultiConnectionContext.isMulti(connection));
    }

    @Test
    void manualPacketsRejectWorldAndInventoryDependentTypes() {
        assertTrue(MultiManualPackets.isSafe(ServerboundSwingPacket.class));
        assertFalse(MultiManualPackets.isSafe(ServerboundContainerClickPacket.class));
    }

    @Test
    void retryProxyAvoidsLastFailedWhenPossible() {
        AutismProxy failed = proxy("failed", AutismProxy.Status.ALIVE, 20);
        AutismProxy next = proxy("next", AutismProxy.Status.ALIVE, 40);

        AutismProxy selected = MultiManager.selectRetryProxy(List.of(failed, next), failed.stableId(), failed.stableId(), false);

        assertEquals(next.stableId(), selected.stableId());
    }

    @Test
    void retryProxyPrefersAliveLowestLatency() {
        AutismProxy slow = proxy("slow", AutismProxy.Status.ALIVE, 100);
        AutismProxy fast = proxy("fast", AutismProxy.Status.ALIVE, 10);
        AutismProxy unchecked = proxy("unchecked", AutismProxy.Status.UNCHECKED, 0);

        AutismProxy selected = MultiManager.selectRetryProxy(List.of(unchecked, slow, fast), "", "", false);

        assertEquals(fast.stableId(), selected.stableId());
    }

    @Test
    void retryProxyFallsBackToUncheckedValidProxy() {
        AutismProxy dead = proxy("dead", AutismProxy.Status.DEAD, 0);
        AutismProxy unchecked = proxy("unchecked", AutismProxy.Status.UNCHECKED, 0);

        AutismProxy selected = MultiManager.selectRetryProxy(List.of(dead, unchecked), "", "", false);

        assertEquals(unchecked.stableId(), selected.stableId());
    }

    @Test
    void retryProxyReturnsNullWhenNoProxyExists() {
        assertNull(MultiManager.selectRetryProxy(List.of(), "missing", "missing", false));
    }

    @Test
    void directRetryStaysDirectEvenWhenProxiesExist() {
        AutismProxy proxy = proxy("proxy", AutismProxy.Status.ALIVE, 10);

        assertNull(MultiManager.selectRetryProxy(List.of(proxy), "", "", true));
    }

    @Test
    void automaticProxyAssignmentNeverReusesAnEndpoint() {
        MultiProfile.SessionSpec first = new MultiProfile.SessionSpec("first", MultiProfile.BEST_PROXY_ID);
        MultiProfile.SessionSpec second = new MultiProfile.SessionSpec("second", MultiProfile.BEST_PROXY_ID);
        AutismProxy one = proxy("one", AutismProxy.Status.ALIVE, 10);
        AutismProxy two = proxy("two", AutismProxy.Status.ALIVE, 20);

        Map<String, AutismProxy> assigned = MultiManager.distributeProxies(
            List.of(first, second), List.of(one, two));

        assertEquals(2, assigned.size());
        assertEquals(one.stableId(), assigned.get("first").stableId());
        assertEquals(two.stableId(), assigned.get("second").stableId());
        assertFalse(MultiManager.proxyLeaseKey(assigned.get("first"))
            .equals(MultiManager.proxyLeaseKey(assigned.get("second"))));
    }

    @Test
    void proxyOffRowsGetNoAssignment() {
        MultiProfile.SessionSpec direct = new MultiProfile.SessionSpec("direct", "");
        MultiProfile.SessionSpec pooled = new MultiProfile.SessionSpec("pooled", MultiProfile.BEST_PROXY_ID);
        AutismProxy one = proxy("one", AutismProxy.Status.ALIVE, 10);

        Map<String, AutismProxy> assigned = MultiManager.distributeProxies(
            List.of(direct, pooled), List.of(one));

        assertEquals(1, assigned.size());
        assertEquals(one.stableId(), assigned.get("pooled").stableId());
        assertFalse(assigned.containsKey("direct"));
    }

    @Test
    void automaticProxyAssignmentReusesOnlyAfterEveryProxyWasDistributed() {
        AutismProxy only = proxy("only", AutismProxy.Status.ALIVE, 10);
        AutismProxy second = proxy("second", AutismProxy.Status.ALIVE, 20);
        Map<String, AutismProxy> assigned = MultiManager.distributeProxies(
            List.of(new MultiProfile.SessionSpec("first", MultiProfile.BEST_PROXY_ID),
                new MultiProfile.SessionSpec("second", MultiProfile.BEST_PROXY_ID),
                new MultiProfile.SessionSpec("third", MultiProfile.BEST_PROXY_ID),
                new MultiProfile.SessionSpec("fourth", MultiProfile.BEST_PROXY_ID)),
            List.of(only, second));

        assertEquals(only.stableId(), assigned.get("first").stableId());
        assertEquals(second.stableId(), assigned.get("second").stableId());
        assertEquals(only.stableId(), assigned.get("third").stableId());
        assertEquals(second.stableId(), assigned.get("fourth").stableId());
    }

    @Test
    void manualBestProxyRowsAvoidExplicitAndEachOther() {
        AutismProxy explicit = proxy("explicit", AutismProxy.Status.ALIVE, 5);
        AutismProxy bestOne = proxy("best-one", AutismProxy.Status.ALIVE, 10);
        AutismProxy bestTwo = proxy("best-two", AutismProxy.Status.ALIVE, 20);
        MultiProfile profile = new MultiProfile();
        profile.proxyMode = MultiProfile.ProxyMode.Manual;
        profile.sessions.add(new MultiProfile.SessionSpec("fixed", explicit.stableId()));
        profile.sessions.add(new MultiProfile.SessionSpec("auto-one", MultiProfile.BEST_PROXY_ID));
        profile.sessions.add(new MultiProfile.SessionSpec("auto-two", MultiProfile.BEST_PROXY_ID));

        Map<String, AutismProxy> assigned = MultiManager.assignManualProxies(
            profile, List.of(explicit, bestOne, bestTwo));

        assertEquals(explicit.stableId(), assigned.get("fixed").stableId());
        assertEquals(3, assigned.values().stream().map(MultiManager::proxyLeaseKey).distinct().count());
    }

    @Test
    void manualExplicitAssignmentsMayIntentionallyShareTheExactProxy() {
        AutismProxy shared = proxy("shared", AutismProxy.Status.ALIVE, 10);
        MultiProfile profile = new MultiProfile();
        profile.proxyMode = MultiProfile.ProxyMode.Manual;
        profile.sessions.add(new MultiProfile.SessionSpec("first", shared.stableId()));
        profile.sessions.add(new MultiProfile.SessionSpec("second", shared.stableId()));

        Map<String, AutismProxy> assigned = MultiManager.assignManualProxies(profile, List.of(shared));

        assertEquals(shared.stableId(), assigned.get("first").stableId());
        assertEquals(shared.stableId(), assigned.get("second").stableId());
    }

    @Test
    void manualExplicitAssignmentIsPreservedEvenAfterAFailedHealthCheck() {
        AutismProxy explicitlyChosen = proxy("chosen", AutismProxy.Status.DEAD, 0);
        AutismProxy alternate = proxy("alternate", AutismProxy.Status.ALIVE, 5);
        MultiProfile profile = new MultiProfile();
        profile.proxyMode = MultiProfile.ProxyMode.Manual;
        profile.sessions.add(new MultiProfile.SessionSpec("account", explicitlyChosen.stableId()));

        Map<String, AutismProxy> assigned = MultiManager.assignManualProxies(
            profile, List.of(alternate, explicitlyChosen));

        assertEquals(explicitlyChosen.stableId(), assigned.get("account").stableId());
    }

    @Test
    void endpointDuplicatesCountAsOneProxyLease() {
        AutismProxy first = proxy("first-id", AutismProxy.Status.ALIVE, 10);
        AutismProxy duplicate = proxy("second-id", AutismProxy.Status.ALIVE, 20);
        duplicate.address = first.address;
        duplicate.port = first.port;

        assertEquals(1, MultiManager.distinctUsableProxies(List.of(first, duplicate)).size());
    }

    @Test
    void formValuesRoundTripEncryptedAndAreRemovedWithAccount() {
        MultiProfile profile = new MultiProfile();
        profile.sessions.add(new MultiProfile.SessionSpec("account", ""));
        profile.runMacroWhileJoining = true;
        assertTrue(profile.setFormValue("account", "password", "plain-secret-value"));

        CompoundTag tag = profile.toTag();
        assertFalse(tag.toString().contains("plain-secret-value"));
        MultiProfile decoded = MultiProfile.fromTag(tag);
        assertTrue(decoded.runMacroWhileJoining);
        assertEquals("plain-secret-value", decoded.openFormValues("account").get("password"));

        decoded.sessions.clear();
        decoded.normalize();
        assertTrue(decoded.openFormValues("account").isEmpty());
    }

    private static AutismProxy proxy(String id, AutismProxy.Status status, long latency) {
        AutismProxy proxy = new AutismProxy();
        proxy.id = id;
        proxy.name = id;
        proxy.address = "127.0.0." + Math.max(1, Math.min(254, Math.abs(id.hashCode()) % 254));
        proxy.port = 1080;
        proxy.status = status;
        proxy.latency = latency;
        return proxy;
    }
}
