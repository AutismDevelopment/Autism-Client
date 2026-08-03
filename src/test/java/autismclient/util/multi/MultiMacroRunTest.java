package autismclient.util.multi;

import autismclient.util.AutismMacro;
import autismclient.util.AutismPacketClick;
import autismclient.util.macro.BranchAction;
import autismclient.util.macro.CustomMenuAction;
import autismclient.util.macro.DelayAction;
import autismclient.util.macro.DropAction;
import autismclient.util.macro.FlowAction;
import autismclient.util.macro.IfAction;
import autismclient.util.macro.ItemAction;
import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.LabelAction;
import autismclient.util.macro.MacroCondition;
import autismclient.util.macro.PacketClickAction;
import autismclient.util.macro.RaceAction;
import autismclient.util.macro.RepeatAction;
import autismclient.util.macro.SendChatAction;
import autismclient.util.macro.SendCommandPacketAction;
import autismclient.util.macro.StartMacroAction;
import autismclient.util.macro.WaitForHealthAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiMacroRunTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.ITEM
            .get(net.minecraft.resources.Identifier.withDefaultNamespace("diamond"))
            .orElseThrow()
            .bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
    }

    @Test
    void publishesStructuredProgressWithoutExposingInterpreterState() {
        AutismMacro macro = macro(false, -1, cmd("one"), cmd("two"));
        macro.name = "Progress";
        MultiMacroRun run = new MultiMacroRun(macro);

        MultiSession.MacroProgress progress = run.progress();
        assertEquals("Progress", progress.macroName());
        assertTrue(progress.running());
        assertEquals(0, progress.step());
        assertEquals(2, progress.totalSteps());
        assertEquals(1, progress.loop());
    }

    private static class FakeHost implements MultiMacroHost {
        final List<String> commands = new ArrayList<>();
        float health = 20f;
        String openScreen = "";
        int itemCount = 0;
        boolean full = false;
        long guiSeq = 0;
        boolean itemResolvable = false;
        double px = 0, py = 0, pz = 0;
        float pitch = 0;
        int[] heldDur = null;
        long tpSeq = 0;
        String dim = "minecraft:overworld";
        String username = "";
        String uuid = "";
        String server = "";
        String macroPassword = "";
        boolean slotChangeSatisfied = false;
        int gm = 0;
        final List<String> chatLines = new ArrayList<>();
        boolean entityPresent = false;
        boolean packetArmed = false;
        long pktSeq = 0;
        final List<String> seenPackets = new ArrayList<>();
        boolean soundArmed = false;
        long sndSeq = 0;
        final List<String> heardSounds = new ArrayList<>();
        final List<String> signEdits = new ArrayList<>();
        final List<String> tabNames = new ArrayList<>();
        final List<autismclient.util.macro.CaptureValueAction.ScoreboardLine> scoreboard = new ArrayList<>();
        String requestedSuggestion = "";
        int suggestionId;
        List<String> suggestionReply;
        boolean burstAccepted = true;
        int burstsSent;
        final List<List<String>> writtenBooks = new ArrayList<>();
        final List<String> writtenTitles = new ArrayList<>();
        boolean macroStepSatisfied;
        int xCarryCalls;
        boolean ready = true;
        InventorySyncState inventorySync = InventorySyncState.READY;
        boolean customMenuPhase = true;
        autismclient.api.custommenu.CustomMenuSnapshot customMenu;
        int customMenuSubmissions;
        boolean customMenuValueAvailable = true;

        void openGui(String title) {
            openScreen = title;
            guiSeq++;
        }

        @Override public boolean macroReady() { return ready; }
        @Override public InventorySyncState inventorySyncState() { return inventorySync; }
        @Override public boolean customMenuPhaseActive() { return customMenuPhase; }
        @Override public autismclient.api.custommenu.CustomMenuSnapshot customMenu() { return customMenu; }
        @Override public autismclient.api.custommenu.CustomMenuSubmitResult submitCustomMenu(
            autismclient.api.custommenu.CustomMenuSnapshot snapshot,
            autismclient.api.custommenu.CustomMenuSubmission submission
        ) {
            customMenuSubmissions++;
            customMenu = null;
            return autismclient.api.custommenu.CustomMenuSubmitResult.packets(List.of());
        }
        @Override public String resolveCustomMenuValue(String template, java.util.Map<String, String> vars) {
            if (!customMenuValueAvailable) return null;
            return template == null ? "" : template.replace("{secret.password}", "password");
        }
        @Override public boolean fullMode() { return full; }
        @Override public String botUsername() { return username; }
        @Override public String botUuid() { return uuid; }
        @Override public String serverAddress() { return server; }
        @Override public String macroPassword() { return macroPassword; }
        @Override public float health() { return health; }
        @Override public float maxHealth() { return 20f; }
        @Override public int food() { return 20; }
        @Override public boolean hasPosition() { return true; }
        @Override public double posX() { return px; }
        @Override public double posY() { return py; }
        @Override public double posZ() { return pz; }
        @Override public String dimension() { return dim; }
        @Override public String heldItemName() { return ""; }
        @Override public int selectedHotbar() { return 0; }
        @Override public String openScreenTitle() { return openScreen; }
        @Override public boolean containerOpen() { return !openScreen.isEmpty(); }
        @Override public long guiOpenSeq() { return guiSeq; }
        @Override public int countItem(String query) { return itemCount; }
        @Override public int countItemTarget(ItemTarget target) { return itemCount; }
        @Override public int freeSlots() { return 36; }
        @Override public boolean slotFilled(int visibleSlot) { return false; }
        @Override public boolean cursorEmpty() { return true; }
        @Override public String cursorName() { return ""; }
        @Override public boolean cursorMatches(ItemTarget target) { return false; }
        @Override public float currentPitch() { return pitch; }
        @Override public int[] heldDurability() { return heldDur; }
        @Override public int[] durabilityAtInv(int inventoryIndex) { return heldDur; }
        @Override public int[] itemDurability(ItemTarget target) { return heldDur; }
        @Override public long teleportSeq() { return tpSeq; }
        @Override public int gameMode() { return gm; }
        @Override public long chatSeq() { return chatLines.size(); }
        @Override public List<String> chatSince(long baselineSeq) {
            int from = (int) Math.max(0, Math.min(chatLines.size(), baselineSeq));
            return new ArrayList<>(chatLines.subList(from, chatLines.size()));
        }
        @Override public boolean entityWithin(List<String> typeRefs, boolean containerOnly, boolean centerOnPlayer,
                                              double cx, double cy, double cz, double radius) {
            return entityPresent;
        }
        @Override public void setPacketCapture(boolean on) { packetArmed = on; if (!on) seenPackets.clear(); }
        @Override public long packetSeq() { return pktSeq; }
        boolean cooldownActive = false;
        boolean packetMatchHit = false;
        @Override public boolean packetMatched(long baselineSeq, autismclient.util.macro.WaitPacketMatchAction action) {
            return packetMatchHit;
        }
        @Override public boolean itemOnCooldown(ItemTarget target, boolean mainHand) { return cooldownActive; }
        String capturedItem = null;
        @Override public String captureItemText(autismclient.util.macro.CaptureValueAction action, ItemTarget filter) {
            return capturedItem;
        }
        @Override public List<String> tablistNames(boolean excludeSelf) { return List.copyOf(tabNames); }
        @Override public int requestCommandSuggestions(String command) {
            requestedSuggestion = command;
            suggestionReply = null;
            return ++suggestionId;
        }
        @Override public List<String> commandSuggestions(int requestId) {
            return requestId == suggestionId ? suggestionReply : null;
        }
        @Override public List<autismclient.util.macro.CaptureValueAction.ScoreboardLine> scoreboardLines() {
            return List.copyOf(scoreboard);
        }
        @Override public boolean packetSeen(long baselineSeq, List<String> targets) {
            for (String seen : seenPackets) {
                String seenDir = autismclient.util.macro.WaitForPacketAction.getDirection(seen);
                String seenName = autismclient.util.macro.WaitForPacketAction.getPacketName(seen);
                if (targets == null || targets.isEmpty()) return true;
                for (String t : targets) {
                    String dir = autismclient.util.macro.WaitForPacketAction.getDirection(t);
                    if (!dir.isEmpty() && !dir.equalsIgnoreCase(seenDir)) continue;
                    if (autismclient.util.macro.WaitForPacketAction.getPacketName(t).equalsIgnoreCase(seenName)) return true;
                }
            }
            return false;
        }
        @Override public boolean editSign(autismclient.util.macro.SignEditAction action, String l1, String l2, String l3, String l4) {
            signEdits.add("sign:" + l1 + "|" + l2 + "|" + l3 + "|" + l4);
            return true;
        }
        @Override public void setSoundCapture(boolean on) { soundArmed = on; if (!on) heardSounds.clear(); }
        @Override public long soundSeq() { return sndSeq; }
        @Override public boolean soundMatched(long baselineSeq, List<String> ids, boolean checkDistance, double maxDistance) {
            for (String s : heardSounds) {
                if (ids == null || ids.isEmpty()) return true;
                for (String want : ids) if (want != null && want.equalsIgnoreCase(s)) return true;
            }
            return false;
        }
        @Override public String[] slotChangeBaseline(autismclient.util.macro.WaitForSlotChangeAction action) {
            return new String[action.entries.size()];
        }
        @Override public boolean slotChangeMet(autismclient.util.macro.WaitForSlotChangeAction action, String[] baseline) {
            return slotChangeSatisfied;
        }
        @Override public int nearestEntity(String type) { return -1; }
        @Override public double[] entityPos(int entityId) { return null; }
        @Override public List<int[]> resolveItemClicks(ItemAction action) {
            if (!itemResolvable || openScreen.isEmpty()) return List.of();
            int times = Math.max(1, action.itemTimes.isEmpty() ? 1 : action.itemTimes.get(0));
            List<int[]> plan = new ArrayList<>();
            for (int i = 0; i < times; i++) plan.add(new int[]{0, 0, 0});
            return plan;
        }
        @Override public List<int[]> resolveStoreClicks(autismclient.util.macro.StoreItemAction action) {
            if (!itemResolvable || openScreen.isEmpty()) return List.of();
            return new ArrayList<>(List.of(new int[]{0, 0, 1}, new int[]{1, 0, 1}));
        }
        @Override public List<int[]> resolveSwapClicks(autismclient.util.macro.SwapSlotsAction action) {
            return new ArrayList<>(List.of(new int[]{0, 0, 0}, new int[]{1, 0, 0}, new int[]{0, 0, 0}));
        }
        @Override public List<int[]> resolvePickupAllClicks(autismclient.util.macro.PickUpAllAction action) {
            List<int[]> plan = new ArrayList<>();
            for (int i = 0; i < Math.max(1, action.times); i++) plan.add(new int[]{0, 0, 6});
            return plan;
        }
        @Override public List<int[]> resolveSequenceClicks(autismclient.util.macro.ContainerClickSequenceAction action) {
            List<int[]> plan = new ArrayList<>();
            for (int r = 0; r < Math.max(1, action.repeatCount); r++) {
                for (int ignored : action.resolvedSlots()) plan.add(new int[]{0, 0, 0});
            }
            return plan;
        }
        @Override public void clickResolved(int handlerSlot, int button, int containerInputOrdinal) {
            commands.add("click " + handlerSlot);
        }
        @Override public boolean sendPacketBurst(autismclient.util.macro.PacketBurstAction action) {
            burstsSent++;
            return burstAccepted;
        }
        @Override public int writeBook(List<String> pages, String title, boolean sign, boolean requireHeld, int excludedHotbarMask) {
            int slot = writtenBooks.size();
            if (slot >= 9 || (excludedHotbarMask & (1 << slot)) != 0) return -1;
            writtenBooks.add(List.copyOf(pages));
            writtenTitles.add(title);
            return slot;
        }
        @Override public boolean macroStepMet(autismclient.util.macro.WaitForMacroStepAction action) {
            return macroStepSatisfied;
        }
        @Override public boolean saveGui(boolean closeAfter, boolean sendClosePacket) {
            commands.add("save-gui " + closeAfter + " " + sendClosePacket);
            return true;
        }
        @Override public boolean desyncGui() { commands.add("desync-gui"); return true; }
        @Override public boolean restoreGui() { commands.add("restore-gui"); return true; }
        @Override public int runXCarry(autismclient.util.macro.XCarryAction action, long now) {
            xCarryCalls++;
            commands.add("xcarry " + action.mode + " " + action.transferMode);
            return 1;
        }
        @Override public void cancelXCarry() { }
        @Override public String runClient(String name, String args) { commands.add(name + " " + args); return "Sent"; }
        @Override public void useItemPhase(autismclient.util.macro.UseItemPhaseAction.Phase phase, boolean offhand) {
            commands.add("use-phase " + phase + " " + (offhand ? "offhand" : "mainhand"));
        }
        @Override public String chat(String message) { commands.add(message); return "Sent"; }
        @Override public boolean startSelfMacro(String macroName) { commands.add("start:" + macroName); return true; }
        @Override public void stopSelfMacro() { commands.add("stop"); }
        @Override public void disconnectBot(String reason) { }
        @Override public float currentYaw() { return 0f; }
        @Override public void look(float yaw, float pitch) { commands.add("look " + yaw + " " + pitch); }
        @Override public void move(double worldDx, double worldDz, long durationMs) { commands.add("move"); }
        @Override public void clip(double dx, double dy, double dz, int segments, boolean onGround) {
            commands.add("clip " + dx + "," + dy + "," + dz + " x" + segments + " " + onGround);
        }
        @Override public void setSneak(boolean on) { commands.add("sneak " + on); }
        boolean sprint = true;
        @Override public void setSprint(boolean on) { sprint = on; commands.add("sprint " + on); }
        @Override public boolean sprinting() { return sprint; }
        @Override public void jump() { commands.add("jump"); }
        @Override public String interactEntity(int entityId, boolean attack) { return "Sent"; }
        @Override public String useOnBlock(int x, int y, int z, String face) { commands.add("useon " + x + "," + y + "," + z); return "Sent"; }
        @Override public String breakBlock(int x, int y, int z, String face) { commands.add("break " + x + "," + y + "," + z); return "Sent"; }
        long rev = 0;
        @Override public long containerRevision() { return rev; }
        @Override public void sendRawPayload(String channel, String rawData) { commands.add("payload " + channel + " " + rawData); }
        boolean blockPresent = false;
        @Override public boolean blockAt(int x, int y, int z, List<String> blockIds, boolean anyBlock, boolean wantDestroyed) {
            return blockPresent;
        }
    }

    private static SendCommandPacketAction cmd(String c) {
        SendCommandPacketAction a = new SendCommandPacketAction();
        a.command = c;
        return a;
    }

    private static AutismMacro macro(boolean loop, int loopCount, autismclient.util.macro.MacroAction... actions) {
        AutismMacro m = new AutismMacro();
        m.loop = loop;
        m.loopCount = loopCount;
        for (autismclient.util.macro.MacroAction a : actions) m.actions.add(a);
        return m;
    }

    private static autismclient.api.custommenu.CustomMenuSnapshot menu(long generation, String title) {
        return new autismclient.api.custommenu.CustomMenuSnapshot("fake", "CONFIGURATION", generation, title,
            List.of(new autismclient.api.custommenu.CustomMenuInput(1, "password", "Password",
                autismclient.api.custommenu.CustomMenuInput.Kind.TEXT, "", 128, 0, 0, 0, List.of())),
            List.of(new autismclient.api.custommenu.CustomMenuButton(1, "Login", "login_submit",
                autismclient.api.custommenu.CustomMenuButton.Kind.CUSTOM)), null);
    }

    private static List<String> runToEnd(AutismMacro m, FakeHost host) {
        MultiMacroRun run = new MultiMacroRun(m);
        long now = 0;
        for (int i = 0; i < 10_000 && !run.done(); i++) {
            run.step(now, host);
            now += 100;
        }
        assertTrue(run.done(), "run should finish");
        return host.commands;
    }

    @Test
    void runsActionsOnceThenFinishes() {
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("a"), cmd("b")), host);
        assertEquals(List.of("/a", "/b"), out);
    }

    @Test
    void customMenuRunsBeforeReadyAndGameplayWaits() {
        FakeHost host = new FakeHost();
        host.ready = false;
        host.customMenu = menu(1, "Login");
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, new CustomMenuAction(), cmd("after")));

        run.step(1_000L, host);

        assertEquals(1, host.customMenuSubmissions);
        assertTrue(host.commands.isEmpty());
        assertFalse(run.done());

        host.ready = true;
        run.step(1_050L, host);
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void waitGuiThenCustomMenuLoginRunsDuringConfiguration() {

        FakeHost host = new FakeHost();
        host.ready = false;
        autismclient.util.macro.WaitForGuiAction wait = new autismclient.util.macro.WaitForGuiAction();
        wait.guiType = "CUSTOM_MENU";
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, wait, new CustomMenuAction(), cmd("after")));

        run.step(1_000L, host);
        assertEquals(0, host.customMenuSubmissions, "no dialog yet -> waits, does not submit");

        host.customMenu = menu(1, "SparkLogin - Verification Required");
        host.openScreen = "SparkLogin - Verification Required";
        run.step(1_050L, host);
        assertEquals(1, host.customMenuSubmissions, "dialog appeared -> login submitted during config");
        assertTrue(host.commands.isEmpty(), "gameplay action still waits for READY");

        host.ready = true;
        run.step(1_100L, host);
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void customMenuWaitsForPasswordInsteadOfFailing() {

        FakeHost host = new FakeHost();
        host.ready = false;
        host.customMenu = menu(1, "Login");
        host.customMenuValueAvailable = false;
        CustomMenuAction login = new CustomMenuAction();
        login.fieldValues.add("{secret.password}");
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, login, cmd("after")));

        run.step(1_000L, host);
        assertEquals(0, host.customMenuSubmissions, "no password -> parked, not submitted");
        assertFalse(run.done());

        run.step(1_000_000L, host);
        assertEquals(0, host.customMenuSubmissions);
        assertFalse(run.done());

        host.customMenuValueAvailable = true;
        run.step(1_000_050L, host);
        assertEquals(1, host.customMenuSubmissions, "password available -> submits");
    }

    @Test
    void consecutiveCustomMenusWaitForNextGeneration() {
        FakeHost host = new FakeHost();
        host.ready = false;
        host.customMenu = menu(1, "Login");
        MultiMacroRun run = new MultiMacroRun(macro(false, -1,
            new CustomMenuAction(), new CustomMenuAction(), cmd("after")));

        run.step(1_000L, host);
        assertEquals(1, host.customMenuSubmissions);
        assertFalse(run.done());

        host.customMenu = menu(2, "Register");
        run.step(1_050L, host);
        assertEquals(2, host.customMenuSubmissions);
        assertTrue(host.commands.isEmpty());
    }

    @Test
    void loopCountRepeatsWholeMacro() {
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(true, 3, cmd("x")), host);
        assertEquals(List.of("/x", "/x", "/x"), out);
    }

    @Test
    void delayParksUntilTimeElapses() {
        DelayAction delay = new DelayAction();
        delay.useTicks = false;
        delay.delayMs = 500;
        AutismMacro m = macro(false, -1, cmd("a"), delay, cmd("b"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);

        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(200, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(600, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void flowLabelBuildsALoop() {

        LabelAction label = new LabelAction();
        label.name = "top";
        FlowAction back = new FlowAction();
        back.target = FlowAction.Target.LABEL;
        back.labelName = "top";
        back.conditional = true;
        back.condition = healthBelow(5);
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, label, cmd("tick"), back), host);
        assertEquals(List.of("/tick"), out);
    }

    @Test
    void ifSkipsThenBlockWhenFalse() {

        IfAction ifA = new IfAction();
        ifA.condition = healthBelow(5);
        ifA.thenSteps = 1;
        ifA.elseSteps = 0;
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("before"), ifA, cmd("then"), cmd("after")), host);
        assertEquals(List.of("/before", "/after"), out);
    }

    @Test
    void ifRunsThenBlockWhenTrue() {
        IfAction ifA = new IfAction();
        ifA.condition = healthBelow(5);
        ifA.thenSteps = 1;
        ifA.elseSteps = 0;
        FakeHost host = new FakeHost();
        host.health = 2f;
        List<String> out = runToEnd(macro(false, -1, cmd("before"), ifA, cmd("then"), cmd("after")), host);
        assertEquals(List.of("/before", "/then", "/after"), out);
    }

    @Test
    void repeatRunsBodyMultipleTimes() {
        RepeatAction rep = new RepeatAction(1, 3);
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, rep, cmd("r")), host);
        assertEquals(List.of("/r", "/r", "/r"), out);
    }

    @Test
    void notReadyPausesTheRun() {
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a")));
        MultiMacroHost notReady = new FakeHost() {
            @Override public boolean macroReady() { return false; }
        };
        run.step(0, notReady);
        assertFalse(run.done());
    }

    @Test
    void emitBudgetPacesOutputPerTick() {

        AutismMacro m = new AutismMacro();
        for (int i = 0; i < 20; i++) m.actions.add(cmd("c" + i));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);
        run.step(0, host);
        assertEquals(8, host.commands.size(), "one tick should emit at most EMIT_BUDGET actions");
        run.step(50, host);
        assertEquals(16, host.commands.size());
        run.step(100, host);
        assertEquals(20, host.commands.size());
        assertTrue(run.done());
    }

    @Test
    void guiWaitParksUntilNamedGuiOpens() {
        SendChatAction buy = new SendChatAction("/buy");
        buy.waitForGuiAfter = true;
        buy.guiName = "Shop";
        AutismMacro m = macro(false, -1, buy, cmd("confirm"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);

        run.step(0, host);
        assertEquals(List.of("/buy"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/buy"), host.commands);
        host.openGui("Other Menu");
        run.step(200, host);
        assertEquals(List.of("/buy"), host.commands);
        host.openGui("Shop Menu");
        run.step(300, host);
        assertEquals(List.of("/buy", "/confirm"), host.commands);
    }

    @Test
    void namelessGuiWaitParksUntilAnyGuiOpens() {

        SendChatAction buy = new SendChatAction("/open");
        buy.waitForGuiAfter = true;
        buy.guiName = "";
        AutismMacro m = macro(false, -1, buy, cmd("click"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);

        run.step(0, host);
        assertEquals(List.of("/open"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/open"), host.commands);
        host.openGui("Anything");
        run.step(200, host);
        assertEquals(List.of("/open", "/click"), host.commands);
    }

    @Test
    void namelessGuiWaitTimesOutIfNoGuiEverOpens() {

        SendChatAction a = new SendChatAction("hi");
        a.waitForGuiAfter = true;
        a.guiName = "";
        AutismMacro m = macro(false, -1, a, cmd("next"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);
        run.step(0, host);
        assertEquals(List.of("hi"), host.commands);
        run.step(10_000, host);
        assertEquals(List.of("hi", "/next"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void dropByItemSendsDropCommand() {
        DropAction d = new DropAction();
        d.mode = DropAction.DropMode.ALL;
        ItemTarget t = new ItemTarget();
        t.registryId = "minecraft:cobblestone";
        d.itemTargets.add(t);
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, d), host);
        assertEquals(List.of("drop minecraft:cobblestone"), host.commands);
    }

    @Test
    void untargetedDropAllDropsTheWholeInventory() {
        DropAction drop = new DropAction();
        drop.mode = DropAction.DropMode.ALL;
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, drop), host);
        assertEquals(List.of("drop fullinventory"), host.commands);
        assertEquals("Drop Entire Inventory", drop.getDisplayName());
    }

    @Test
    void untargetedDropTimesUsesGlobalCountOnHeldItem() {
        DropAction drop = new DropAction();
        drop.mode = DropAction.DropMode.TIMES;
        drop.dropCount = 4;
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, drop), host);
        assertEquals(List.of("drop 4"), host.commands);
        assertEquals("Drop Held Item x4", drop.getDisplayName());
    }

    @Test
    void targetedDropUsesEachRowsOwnCountInsteadOfGlobalMode() {
        ItemTarget cobble = new ItemTarget();
        cobble.registryId = "minecraft:cobblestone";

        DropAction counted = new DropAction();
        counted.mode = DropAction.DropMode.ALL;
        counted.itemTargets.add(cobble);
        counted.itemCounts.add(3);
        FakeHost countedHost = new FakeHost();
        runToEnd(macro(false, -1, counted), countedHost);
        assertEquals(List.of("drop 3 minecraft:cobblestone"), countedHost.commands);

        DropAction wholeStack = new DropAction();
        wholeStack.mode = DropAction.DropMode.TIMES;
        wholeStack.itemTargets.add(cobble.copy());
        wholeStack.itemCounts.add(0);
        FakeHost wholeStackHost = new FakeHost();
        runToEnd(macro(false, -1, wholeStack), wholeStackHost);
        assertEquals(List.of("drop minecraft:cobblestone"), wholeStackHost.commands);
    }

    @Test
    void itemCountConditionRunsThenWhenSatisfied() {
        IfAction ifA = new IfAction();
        MacroCondition leaf = MacroCondition.leaf(MacroCondition.Kind.ITEM_COUNT);
        leaf.item = "diamond";
        leaf.cmp = MacroCondition.Cmp.AT_LEAST;
        leaf.amount = 5;
        MacroCondition root = MacroCondition.group(MacroCondition.Combine.ALL);
        root.children.add(leaf);
        ifA.condition = root;
        ifA.thenSteps = 1;
        FakeHost host = new FakeHost();
        host.itemCount = 10;
        List<String> out = runToEnd(macro(false, -1, cmd("a"), ifA, cmd("then"), cmd("b")), host);
        assertEquals(List.of("/a", "/then", "/b"), out);
    }

    @Test
    void branchRunsThenWhenMatchedAndSkipsWhenNot() {
        BranchAction always = new BranchAction();
        always.conditionKind = BranchAction.ConditionKind.ALWAYS;
        always.thenSteps = 1;
        FakeHost host = new FakeHost();
        assertEquals(List.of("/a", "/then", "/b"),
            runToEnd(macro(false, -1, cmd("a"), always, cmd("then"), cmd("b")), host));

        BranchAction gui = new BranchAction();
        gui.conditionKind = BranchAction.ConditionKind.GUI_TYPE;
        gui.value = "Shop";
        gui.thenSteps = 1;
        FakeHost host2 = new FakeHost();
        assertEquals(List.of("/a", "/b"),
            runToEnd(macro(false, -1, cmd("a"), gui, cmd("then"), cmd("b")), host2));
    }

    @Test
    void waitHealthParksUntilConditionHolds() {
        WaitForHealthAction w = new WaitForHealthAction();
        w.healthThreshold = 5f;
        w.below = true;
        AutismMacro m = macro(false, -1, cmd("a"), w, cmd("b"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.health = 2f;
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void packetClickBurstIsPacedAcrossTicks() {
        PacketClickAction pc = new PacketClickAction();
        pc.target = new AutismPacketClick.Target(0, 0, 9, 9, "", "", "", AutismPacketClick.Mode.LEFT_CLICK, 0L);
        pc.times = 20;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, pc));
        run.step(0, host);
        assertEquals(8, host.commands.size(), "one tick emits at most EMIT_BUDGET clicks");
        run.step(50, host);
        run.step(100, host);
        assertEquals(20, host.commands.size(), "all clicks eventually sent");
        assertTrue(run.done());
    }

    @Test
    void startMacroHandsOffAndFinishes() {
        StartMacroAction s = new StartMacroAction();
        s.macroName = "other";
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), s, cmd("never")));
        for (int i = 0; i < 100 && !run.done(); i++) run.step(i * 50L, host);
        assertTrue(run.done());
        assertEquals(List.of("/a", "start:other"), host.commands);
    }

    @Test
    void missingStartMacroTargetEndsAsVisibleError() {
        StartMacroAction s = new StartMacroAction();
        s.macroName = "missing";
        FakeHost host = new FakeHost() {
            @Override public boolean startSelfMacro(String macroName) { return false; }
        };
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, s, cmd("never")));
        for (int i = 0; i < 10 && !run.done(); i++) run.step(i * 50L, host);
        assertTrue(run.done());
        assertEquals("error", run.status());
        assertTrue(host.commands.isEmpty());
    }

    private static ItemAction item(String registryId, boolean waitForGuiAfter) {
        ItemAction a = new ItemAction();
        ItemTarget t = new ItemTarget();
        t.registryId = registryId;
        a.itemTargets.add(t);
        a.itemTimes.add(1);
        a.itemActionIdx.add(0);
        a.itemButtons.add(0);
        a.waitForGuiAfter = waitForGuiAfter;
        a.guiName = "";
        return a;
    }

    @Test
    void itemClicksAfterGuiOpensAndContentLoads() {

        SendChatAction shop = new SendChatAction("/shop");
        shop.waitForGuiAfter = true;
        shop.guiName = "";
        AutismMacro m = macro(false, -1, shop, item("minecraft:wheat", false), cmd("done"));
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(m);

        run.step(0, host);
        assertEquals(List.of("/shop"), host.commands);
        host.openGui("Shop");
        run.step(100, host);
        assertEquals(List.of("/shop"), host.commands);
        host.itemResolvable = true;
        run.step(200, host);
        assertEquals(List.of("/shop", "click 0"), host.commands);
        run.step(250, host);
        assertEquals(List.of("/shop", "click 0", "/done"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void itemWaitForGuiAfterChainsPages() {

        AutismMacro m = macro(false, -1, item("minecraft:wheat", true), item("minecraft:paper", false));
        FakeHost host = new FakeHost();
        host.openGui("Shop");
        host.itemResolvable = true;
        MultiMacroRun run = new MultiMacroRun(m);

        run.step(0, host);
        assertEquals(List.of("click 0"), host.commands);
        run.step(100, host);
        assertEquals(List.of("click 0"), host.commands);
        host.openGui("Category");
        run.step(200, host);
        assertEquals(List.of("click 0", "click 0"), host.commands);
        run.step(250, host);
        assertTrue(run.done());
    }

    @Test
    void dependentContainerStepsWaitForAuthoritativeTransactionCompletion() {
        autismclient.util.macro.StoreItemAction store = new autismclient.util.macro.StoreItemAction();
        store.mode = autismclient.util.macro.StoreItemAction.Mode.STORE;
        store.allItems = true;
        store.closeAfter = true;
        store.closeSendPkt = true;
        FakeHost host = new FakeHost() {
            @Override public void clickResolved(int handlerSlot, int button, int containerInputOrdinal) {
                super.clickResolved(handlerSlot, button, containerInputOrdinal);
                inventorySync = InventorySyncState.WAITING;
            }
        };
        host.openGui("Chest");
        host.itemResolvable = true;
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, store, cmd("after")));

        run.step(0, host);
        assertEquals(List.of("click 0"), host.commands);
        run.step(50, host);
        assertEquals(List.of("click 0"), host.commands, "second click cannot race the first reply");

        host.inventorySync = MultiMacroHost.InventorySyncState.READY;
        run.step(100, host);
        assertEquals(List.of("click 0", "click 1"), host.commands);
        run.step(150, host);
        assertEquals(List.of("click 0", "click 1"), host.commands, "close remains dependent on click two");

        host.inventorySync = MultiMacroHost.InventorySyncState.READY;
        run.step(200, host);
        assertEquals(List.of("click 0", "click 1", "close ", "/after"), host.commands);
    }

    @Test
    void timedOutInventoryStateParksMacroWithVisibleReason() {
        FakeHost host = new FakeHost();
        host.inventorySync = MultiMacroHost.InventorySyncState.BLOCKED;
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("never")));
        run.step(0, host);
        assertTrue(host.commands.isEmpty());
        assertEquals("Waiting for authoritative inventory update", run.status());
    }

    @Test
    void itemSkipsAfterGraceWhenNeverResolvable() {

        AutismMacro m = macro(false, -1, item("minecraft:diamond", false), cmd("after"));
        FakeHost host = new FakeHost();
        host.openGui("Shop");
        MultiMacroRun run = new MultiMacroRun(m);
        run.step(0, host);
        assertEquals(List.of(), host.commands);
        run.step(50_000, host);
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void storeItemQuickMovesThenCloses() {
        autismclient.util.macro.StoreItemAction s = new autismclient.util.macro.StoreItemAction();
        s.mode = autismclient.util.macro.StoreItemAction.Mode.STORE;
        s.allItems = true;
        s.closeAfter = true;
        s.closeSendPkt = true;
        FakeHost host = new FakeHost();
        host.openGui("Chest");
        host.itemResolvable = true;
        List<String> out = runToEnd(macro(false, -1, s), host);
        assertEquals(List.of("click 0", "click 1", "close "), out);
    }

    @Test
    void storeItemWaitsForContainerThenSkipsIfEmpty() {
        autismclient.util.macro.StoreItemAction s = new autismclient.util.macro.StoreItemAction();
        s.allItems = true;
        FakeHost host = new FakeHost();
        host.openGui("Chest");
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, s, cmd("after")));
        run.step(0, host);
        assertEquals(List.of(), host.commands);
        run.step(50_000, host);
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void swapSlotsEmitsThreeClicks() {
        autismclient.util.macro.SwapSlotsAction sw = new autismclient.util.macro.SwapSlotsAction();
        sw.fromSlot = 0;
        sw.toSlot = 1;
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, sw), host);
        assertEquals(List.of("click 0", "click 1", "click 0"), out);
    }

    @Test
    void waitInventoryPredicateProceedsWhenCountReached() {
        autismclient.util.macro.WaitInventoryPredicateAction w = new autismclient.util.macro.WaitInventoryPredicateAction();
        w.condition = autismclient.util.macro.WaitInventoryPredicateAction.InventoryCondition.COUNT_AT_LEAST;
        w.itemName = "minecraft:diamond";
        w.count = 5;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.itemCount = 8;
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitPosProceedsWhenWithinLeeway() {
        autismclient.util.macro.WaitPosAction w = new autismclient.util.macro.WaitPosAction(10, 64, -20, 1.0);
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.px = 10; host.py = 64; host.pz = -20;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitDurabilityProceedsWhenLow() {
        autismclient.util.macro.WaitDurabilityAction w = new autismclient.util.macro.WaitDurabilityAction();
        w.targetMode = autismclient.util.macro.WaitDurabilityAction.TargetMode.HELD;
        w.measurement = autismclient.util.macro.WaitDurabilityAction.Measurement.REMAINING;
        w.comparison = autismclient.util.macro.WaitDurabilityAction.Comparison.AT_MOST;
        w.value = 2;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.heldDur = new int[]{1557, 1561};
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.heldDur = new int[]{1560, 1561};
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitPositionDeltaProceedsAfterMoving() {
        autismclient.util.macro.WaitForPositionDeltaAction w = new autismclient.util.macro.WaitForPositionDeltaAction();
        w.distance = 5.0;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.px = 3; host.pz = 3;
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.px = 10;
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitTeleportProceedsOnTeleportPacket() {
        autismclient.util.macro.WaitForTeleportAction w = new autismclient.util.macro.WaitForTeleportAction();
        w.minDistance = 0.0;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.tpSeq = 1;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitWorldChangeProceedsOnDimensionChange() {
        autismclient.util.macro.WaitForWorldChangeAction w = new autismclient.util.macro.WaitForWorldChangeAction();
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.dim = "minecraft:the_nether";
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitSlotChangeProceedsWhenSatisfied() {
        autismclient.util.macro.WaitForSlotChangeAction w = new autismclient.util.macro.WaitForSlotChangeAction(3);
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.slotChangeSatisfied = true;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitMovementDelegatesToPositionDelta() {
        autismclient.util.macro.WaitMovementAction w = new autismclient.util.macro.WaitMovementAction();
        w.mode = autismclient.util.macro.WaitMovementAction.Mode.POSITION_DELTA;
        w.distance = 5.0;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.px = 10;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void paySendsPerPlayerCommands() {
        autismclient.util.macro.PayAction p = new autismclient.util.macro.PayAction();
        p.players.add("Alice");
        p.players.add("Bob");
        p.amountInput = "100";
        p.commandTemplate = "/pay <player> <amount>";
        p.delayEnabled = false;
        FakeHost host = new FakeHost();
        assertEquals(List.of("/pay Alice 100", "/pay Bob 100"), runToEnd(macro(false, -1, p), host));
    }

    @Test
    void payDivideSplitsTotal() {
        autismclient.util.macro.PayAction p = new autismclient.util.macro.PayAction();
        p.players.add("Alice");
        p.players.add("Bob");
        p.amountInput = "100";
        p.divideEnabled = true;
        p.delayEnabled = false;
        FakeHost host = new FakeHost();
        assertEquals(List.of("/pay Alice 50", "/pay Bob 50"), runToEnd(macro(false, -1, p), host));
    }

    @Test
    void assertStopsMacroWhenCheckFails() {
        autismclient.util.macro.AssertAction a = new autismclient.util.macro.AssertAction();
        a.check = autismclient.util.macro.AssertAction.CheckType.INVENTORY_ITEM;
        a.itemName = "minecraft:diamond";
        a.failureBehavior = autismclient.util.macro.AssertAction.FailureBehavior.STOP_MACRO;
        FakeHost host = new FakeHost();
        assertEquals(List.of("/a"), runToEnd(macro(false, -1, cmd("a"), a, cmd("b")), host));
    }

    @Test
    void assertPassesWhenConditionHolds() {
        autismclient.util.macro.AssertAction a = new autismclient.util.macro.AssertAction();
        a.check = autismclient.util.macro.AssertAction.CheckType.CONNECTION;
        FakeHost host = new FakeHost();
        assertEquals(List.of("/a", "/b"), runToEnd(macro(false, -1, cmd("a"), a, cmd("b")), host));
    }

    @Test
    void waitChatProceedsOnMatchingLine() {
        autismclient.util.macro.WaitForChatAction w = new autismclient.util.macro.WaitForChatAction("hello");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.chatLines.add("random other line");
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.chatLines.add("hello");
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitGuiCustomMenuProceedsWhenDialogAppears() {
        autismclient.util.macro.WaitForGuiAction w = new autismclient.util.macro.WaitForGuiAction();
        w.guiType = "CUSTOM_MENU";
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.openGui("Shop");
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.customMenu = new autismclient.api.custommenu.CustomMenuSnapshot(
            "minecraft:dialog", "CONFIGURATION", 1L, "SparkLogin - Verification Required", List.of(), List.of(), null);
        host.openScreen = "SparkLogin - Verification Required";
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void waitGuiAnyTypeAcceptsCustomScreens() {
        autismclient.util.macro.WaitForGuiAction w = new autismclient.util.macro.WaitForGuiAction();
        w.guiType = "ANY";
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.customMenu = new autismclient.api.custommenu.CustomMenuSnapshot(
            "minecraft:dialog", "PLAY", 1L, "Register", List.of(), List.of(), null);
        host.openScreen = "Register";
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitGuiTitleFilterAppliesToCustomScreens() {
        autismclient.util.macro.WaitForGuiAction w = new autismclient.util.macro.WaitForGuiAction();
        w.guiType = "CUSTOM_MENU";
        w.guiTitle = "register";
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        host.customMenu = new autismclient.api.custommenu.CustomMenuSnapshot(
            "minecraft:dialog", "PLAY", 1L, "Login", List.of(), List.of(), null);
        host.openScreen = "Login";
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.customMenu = new autismclient.api.custommenu.CustomMenuSnapshot(
            "minecraft:dialog", "PLAY", 2L, "Register Account", List.of(), List.of(), null);
        host.openScreen = "Register Account";
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitGamemodeChangeProceedsOnChange() {
        autismclient.util.macro.WaitGamemodeChangeAction w = new autismclient.util.macro.WaitGamemodeChangeAction();
        w.match = autismclient.util.macro.WaitGamemodeChangeAction.Match.ANY_CHANGE;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.gm = 1;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitEntityProceedsWhenEntityAppears() {
        autismclient.util.macro.WaitForEntityAction w = new autismclient.util.macro.WaitForEntityAction();
        w.entityIds.add("minecraft:zombie");
        w.checkMode = autismclient.util.macro.WaitForEntityAction.CheckMode.RADIUS;
        w.radius = 6.0;
        FakeHost host = new FakeHost();
        host.full = true;
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.entityPresent = true;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitPacketArmsAndProceedsOnMatch() {
        autismclient.util.macro.WaitForPacketAction w =
            new autismclient.util.macro.WaitForPacketAction("S2C:ClientboundOpenScreenPacket");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertTrue(host.packetArmed, "capture armed while parked on WAIT_PACKET");
        assertEquals(List.of("/a"), host.commands);
        host.seenPackets.add("S2C:ClientboundContainerSetContentPacket");
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.seenPackets.add("S2C:ClientboundOpenScreenPacket");
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertFalse(host.packetArmed, "capture disarmed once the wait resolved");
    }

    @Test
    void signEditSendsResolvedLines() {
        autismclient.util.macro.SignEditAction s = new autismclient.util.macro.SignEditAction();
        s.targetMode = autismclient.util.macro.SignEditAction.TargetMode.MANUAL_POS;
        s.line1 = "line one";
        s.line2 = "line two";
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, s), host);
        assertEquals(List.of("sign:line one|line two||"), host.signEdits);
    }

    @Test
    void waitSoundArmsAndProceedsOnMatch() {
        autismclient.util.macro.WaitForSoundAction w = new autismclient.util.macro.WaitForSoundAction();
        w.soundIds.add("minecraft:entity.player.levelup");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertTrue(host.soundArmed, "sound capture armed while parked");
        assertEquals(List.of("/a"), host.commands);
        host.heardSounds.add("minecraft:block.note_block.harp");
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.heardSounds.add("minecraft:entity.player.levelup");
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertFalse(host.soundArmed, "sound capture disarmed after the wait resolved");
    }

    @Test
    void waitCooldownProceedsWhenOffCooldown() {
        autismclient.util.macro.WaitForCooldownAction w = new autismclient.util.macro.WaitForCooldownAction();
        w.checkMainInteractionHand = true;
        FakeHost host = new FakeHost();
        host.cooldownActive = true;
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.cooldownActive = false;
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitPacketMatchArmsAndProceedsOnMatch() {
        autismclient.util.macro.WaitPacketMatchAction w = new autismclient.util.macro.WaitPacketMatchAction();
        w.addRule(autismclient.util.macro.WaitPacketMatchAction.Direction.S2C, "ClientboundSetHealthPacket");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertTrue(host.packetArmed, "packet capture armed for WAIT_PACKET_MATCH");
        assertEquals(List.of("/a"), host.commands);
        host.packetMatchHit = true;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertFalse(host.packetArmed, "disarmed after match");
    }

    @Test
    void finallyBlockRunsAtEndAndIsSkippedDuringNormalFlow() {
        autismclient.util.macro.FinallyAction f = new autismclient.util.macro.FinallyAction();
        f.bodyCount = 1;

        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("a"), f, cmd("cleanup"), cmd("b")), host);
        assertEquals(List.of("/a", "/b", "/cleanup"), out);
    }

    @Test
    void finallyRunsOnFlowStop() {
        autismclient.util.macro.FinallyAction f = new autismclient.util.macro.FinallyAction();
        f.bodyCount = 1;
        FlowAction stop = new FlowAction();
        stop.target = FlowAction.Target.STOP;

        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("a"), f, cmd("cleanup"), stop, cmd("never")), host);
        assertEquals(List.of("/a", "/cleanup"), out);
    }

    @Test
    void finallyRunsOnStopMacroAction() {

        autismclient.util.macro.FinallyAction f = new autismclient.util.macro.FinallyAction();
        f.bodyCount = 1;
        autismclient.util.macro.StopMacroAction stop = new autismclient.util.macro.StopMacroAction();

        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("a"), f, cmd("cleanup"), stop, cmd("never")), host);
        assertEquals(List.of("/a", "stop", "/cleanup"), out);
    }

    @Test
    void finallyRunsOnAssertStop() {

        autismclient.util.macro.FinallyAction f = new autismclient.util.macro.FinallyAction();
        f.bodyCount = 1;
        autismclient.util.macro.AssertAction a = new autismclient.util.macro.AssertAction();
        a.check = autismclient.util.macro.AssertAction.CheckType.HELD_ITEM;
        a.itemName = "diamond_sword";
        a.failureBehavior = autismclient.util.macro.AssertAction.FailureBehavior.STOP_MACRO;
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, cmd("a"), f, cmd("cleanup"), a, cmd("never")), host);
        assertEquals(List.of("/a", "/cleanup"), out);
    }

    @Test
    void raceRunsBodyWhenConditionFires() {

        RaceAction race = new RaceAction();
        race.bodyCount = 2;
        race.timeoutMs = 10_000;
        autismclient.util.macro.WaitForChatAction cond = new autismclient.util.macro.WaitForChatAction("go");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, race, cond, cmd("body"), cmd("after")));
        run.step(0, host);
        assertEquals(List.of(), host.commands);
        host.chatLines.add("go");
        run.step(100, host);
        assertEquals(List.of("/body", "/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void raceSkipsBodyOnTimeout() {
        RaceAction race = new RaceAction();
        race.bodyCount = 2;
        race.timeoutMs = 500;
        autismclient.util.macro.WaitForChatAction cond = new autismclient.util.macro.WaitForChatAction("never");
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, race, cond, cmd("body"), cmd("after")));
        run.step(0, host);
        assertEquals(List.of(), host.commands);
        run.step(1000, host);
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void durabilityConditionEvaluatesHeldItem() {

        IfAction ifA = new IfAction();
        MacroCondition leaf = MacroCondition.leaf(MacroCondition.Kind.DURABILITY);
        leaf.durMode = autismclient.util.macro.WaitDurabilityAction.TargetMode.HELD;
        leaf.durMeasure = autismclient.util.macro.WaitDurabilityAction.Measurement.REMAINING;
        leaf.cmp = MacroCondition.Cmp.AT_MOST;
        leaf.amount = 2;
        MacroCondition root = MacroCondition.group(MacroCondition.Combine.ALL);
        root.children.add(leaf);
        ifA.condition = root;
        ifA.thenSteps = 1;
        FakeHost host = new FakeHost();
        host.heldDur = new int[]{1560, 1561};
        assertEquals(List.of("/before", "/then", "/after"),
            runToEnd(macro(false, -1, cmd("before"), ifA, cmd("then"), cmd("after")), host));
    }

    @Test
    void captureValueFromChatPatternFeedsCommand() {

        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT;
        cap.matchMode = autismclient.util.macro.MacroCapturePattern.Mode.CAPTURE;
        cap.pattern = "Balance: {bal}";
        cap.saveAs = "line";
        SendCommandPacketAction useIt = cmd("pay Bob {bal}");
        FakeHost host = new FakeHost();
        host.chatLines.add("Balance: 4200");
        List<String> out = runToEnd(macro(false, -1, cap, useIt), host);
        assertEquals(List.of("/pay Bob 4200"), out);
    }

    @Test
    void captureValueWholeGuiTitleIntoVar() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.GUI_TITLE;
        cap.saveAs = "shop";
        FakeHost host = new FakeHost();
        host.openGui("Blackmarket");
        List<String> out = runToEnd(macro(false, -1, cap, cmd("warp {shop}")), host);
        assertEquals(List.of("/warp Blackmarket"), out);
    }

    @Test
    void macroVariablesSetsAndSubstitutes() {
        autismclient.util.macro.MacroVariablesAction mv = new autismclient.util.macro.MacroVariablesAction();
        mv.names.add("who");
        mv.values.add("Steve");
        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, mv, cmd("msg {who} hi")), host);
        assertEquals(List.of("/msg Steve hi"), out);
    }

    @Test
    void captureValueNumberModifierAdjustsValue() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT;
        cap.saveAs = "n";
        cap.numberModifier = autismclient.util.macro.CaptureValueAction.NumberModifier.MULTIPLY;
        cap.numberModifierAmount = 2.0;
        FakeHost host = new FakeHost();
        host.chatLines.add("1,000");
        List<String> out = runToEnd(macro(false, -1, cap, cmd("give {n}")), host);
        assertEquals(List.of("/give 2000"), out);
    }

    @Test
    void captureValuePicksFromTrackedTablist() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.TABLIST;
        cap.saveAs = "player";
        cap.listSelection = autismclient.util.macro.CaptureListSelector.Selection.FIRST;
        FakeHost host = new FakeHost();
        host.tabNames.addAll(List.of("Alice", "Bob"));

        assertEquals(List.of("/msg Alice hi"),
            runToEnd(macro(false, -1, cap, cmd("msg {player} hi")), host));
    }

    @Test
    void commandAutofillUsesNormalSlashQueryAndWaitsForMatchingReply() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.COMMAND_AUTOFILL;
        cap.autofillCommand = "msg ";
        cap.saveAs = "player";
        cap.listSelection = autismclient.util.macro.CaptureListSelector.Selection.FIRST;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cap, cmd("msg {player} ready")));

        run.step(0, host);
        assertEquals("/msg ", host.requestedSuggestion);
        assertTrue(host.commands.isEmpty());
        host.suggestionReply = List.of("Alice", "Bob");
        run.step(100, host);

        assertEquals(List.of("/msg Alice ready"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void waitingScoreboardCaptureRequiresTrackedRowToChange() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.SCOREBOARD;
        cap.matchMode = autismclient.util.macro.MacroCapturePattern.Mode.CAPTURE;
        cap.pattern = "Coins: {coins}";
        cap.waitForTrigger = true;
        FakeHost host = new FakeHost();
        host.scoreboard.add(scoreLine("wallet", "Coins", "10"));
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cap, cmd("pay Bob {coins}")));

        run.step(0, host);
        run.step(100, host);
        assertTrue(host.commands.isEmpty(), "the existing scoreboard value is only the baseline");
        host.scoreboard.set(0, scoreLine("wallet", "Coins", "25"));
        run.step(200, host);

        assertEquals(List.of("/pay Bob 25"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void packetBurstPacesDelayedEntriesAndStopsAfterRejectedSend() {
        autismclient.util.macro.PacketBurstAction burst = new autismclient.util.macro.PacketBurstAction();
        burst.mode = autismclient.util.macro.PacketBurstAction.BurstMode.USE_ITEM;
        burst.count = 3;
        burst.delayTicks = 2;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, burst, cmd("after")));

        run.step(0, host);
        assertEquals(1, host.burstsSent);
        run.step(99, host);
        assertEquals(1, host.burstsSent);
        run.step(100, host);
        assertEquals(2, host.burstsSent);
        host.burstAccepted = false;
        run.step(200, host);

        assertEquals(3, host.burstsSent, "a failed delayed send cancels the remaining burst");
        assertEquals(List.of("/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void rejectedPacketBurstDoesNotAttemptRemainingEntries() {
        autismclient.util.macro.PacketBurstAction burst = new autismclient.util.macro.PacketBurstAction();
        burst.count = 20;
        FakeHost host = new FakeHost();
        host.burstAccepted = false;

        assertEquals(List.of("/after"), runToEnd(macro(false, -1, burst, cmd("after")), host));
        assertEquals(1, host.burstsSent);
    }

    @Test
    void nbtBookWritesResolvedPastedPagesThroughTrackedHotbarSlots() {
        autismclient.util.macro.MacroVariablesAction vars = new autismclient.util.macro.MacroVariablesAction();
        vars.names.add("name");
        vars.values.add("Alice");
        autismclient.util.macro.NbtBookAction book = new autismclient.util.macro.NbtBookAction();
        book.dataSource = autismclient.util.macro.NbtBookAction.SOURCE_PASTED;
        book.customComponent = "Hello {name}";
        book.characters = 100;
        book.title = "Ledger";
        book.bookCount = 2;
        book.delayTicks = 1;
        FakeHost host = new FakeHost();

        runToEnd(macro(false, -1, vars, book), host);

        assertEquals(List.of(List.of("Hello Alice"), List.of("Hello Alice")), host.writtenBooks);
        assertEquals(List.of("Ledger", "Ledger #2"), host.writtenTitles);
    }

    @Test
    void randomNbtBookBuildsRequestedPageCount() {
        autismclient.util.macro.NbtBookAction book = new autismclient.util.macro.NbtBookAction();
        book.dataSource = autismclient.util.macro.NbtBookAction.SOURCE_RANDOM;
        book.randomType = autismclient.util.AutismBookPayloadBuilder.RANDOM_ASCII;
        book.pages = 3;
        book.characters = 8;
        FakeHost host = new FakeHost();

        runToEnd(macro(false, -1, book), host);

        assertEquals(1, host.writtenBooks.size());
        assertEquals(3, host.writtenBooks.getFirst().size());
        assertTrue(host.writtenBooks.getFirst().stream().allMatch(page -> page.length() == 8));
    }

    @Test
    void waitMacroStepParksUntilPeerProgressMatches() {
        autismclient.util.macro.WaitForMacroStepAction wait = new autismclient.util.macro.WaitForMacroStepAction();
        wait.macroName = "Worker";
        wait.step = 2;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, wait, cmd("continue")));

        run.step(0, host);
        run.step(100, host);
        assertTrue(host.commands.isEmpty());
        host.macroStepSatisfied = true;
        run.step(200, host);

        assertEquals(List.of("/continue"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void publishedMacroProgressCoordinatesStartedCompletedAndFinishedModes() {
        autismclient.util.macro.WaitForMacroStepAction wait = new autismclient.util.macro.WaitForMacroStepAction();
        wait.macroName = "Worker";
        wait.step = 2;
        MultiSession.MacroProgress before = new MultiSession.MacroProgress("Worker", true, 1, 3, 1, "running");
        MultiSession.MacroProgress after = new MultiSession.MacroProgress("Worker", true, 2, 3, 1, "running");
        MultiSession.MacroProgress finished = new MultiSession.MacroProgress("Worker", false, 3, 3, 1, "done");

        wait.mode = autismclient.util.macro.WaitForMacroStepAction.WaitMode.STARTED_STEP;
        assertFalse(MultiManager.publishedMacroStepMet(wait, List.of(before)));
        assertTrue(MultiManager.publishedMacroStepMet(wait, List.of(after)));
        wait.mode = autismclient.util.macro.WaitForMacroStepAction.WaitMode.COMPLETED_STEP;
        assertTrue(MultiManager.publishedMacroStepMet(wait, List.of(after)));
        wait.mode = autismclient.util.macro.WaitForMacroStepAction.WaitMode.FINISHED;
        assertFalse(MultiManager.publishedMacroStepMet(wait, List.of(after)));
        assertTrue(MultiManager.publishedMacroStepMet(wait, List.of(finished)));
        assertTrue(MultiManager.publishedMacroStepMet(wait, List.of()), "an absent target is already finished");
    }

    @Test
    void saveDesyncAndRestoreGuiExecutePerBotInOrder() {
        autismclient.util.macro.SaveGuiAction save = new autismclient.util.macro.SaveGuiAction();
        save.closeAfter = true;
        save.sendPacket = false;
        autismclient.util.macro.DesyncAction desync = new autismclient.util.macro.DesyncAction();
        autismclient.util.macro.RestoreGuiAction restore = new autismclient.util.macro.RestoreGuiAction();
        FakeHost host = new FakeHost();

        assertEquals(List.of("save-gui true false", "desync-gui", "restore-gui", "/after"),
            runToEnd(macro(false, -1, save, desync, restore, cmd("after")), host));
    }

    @Test
    void xCarryRunsInHeadlessAndFullModesWithoutCompatibilityWarnings() {
        autismclient.util.macro.XCarryAction xcarry = new autismclient.util.macro.XCarryAction();
        xcarry.mode = autismclient.util.macro.XCarryAction.Mode.PUT_IN;
        xcarry.transferMode = autismclient.util.macro.XCarryAction.TransferMode.SAFE_CLICK;
        FakeHost host = new FakeHost();

        assertEquals(List.of("xcarry PUT_IN SAFE_CLICK", "/after"),
            runToEnd(macro(false, -1, xcarry, cmd("after")), host));
        AutismMacro compatibility = macro(false, -1, xcarry,
            new autismclient.util.macro.SaveGuiAction(),
            new autismclient.util.macro.DesyncAction(),
            new autismclient.util.macro.RestoreGuiAction());
        assertTrue(MultiMacroSupport.analyze(compatibility).isEmpty());
    }

    @Test
    void xCarryPlannerMovesConfiguredStackIntoStorageSlot() {
        List<net.minecraft.world.item.ItemStack> slots = emptyInventorySlots();
        slots.set(36, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        autismclient.util.macro.XCarryAction xcarry = new autismclient.util.macro.XCarryAction();
        xcarry.entryTargets.add(autismclient.util.macro.ItemTarget.registry("minecraft:diamond"));
        xcarry.entryDestinations.add(1);
        xcarry.entryAmountModes.add(autismclient.util.macro.XCarryAction.AmountMode.CUSTOM);
        xcarry.entryAmounts.add(3);

        List<MultiXCarryPlanner.Click> clicks = MultiXCarryPlanner.plan(
            xcarry, slots, net.minecraft.world.item.ItemStack.EMPTY);

        assertEquals(2, clicks.size());
        assertEquals(36, clicks.get(0).slot());
        assertEquals(1, clicks.get(1).slot());
        assertTrue(clicks.stream().allMatch(click -> click.input() == net.minecraft.world.inventory.ContainerInput.PICKUP));
    }

    @Test
    void xCarryPlannerSupportsCursorTakeOutAndDropModes() {
        List<net.minecraft.world.item.ItemStack> slots = emptyInventorySlots();
        slots.set(36, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        autismclient.util.macro.XCarryAction cursor = new autismclient.util.macro.XCarryAction();
        cursor.entryTargets.add(autismclient.util.macro.ItemTarget.registry("minecraft:diamond"));
        cursor.entryDestinations.add(autismclient.util.macro.XCarryAction.DEST_CURSOR);
        assertEquals(3, MultiXCarryPlanner.plan(cursor, slots, net.minecraft.world.item.ItemStack.EMPTY).size());

        slots = emptyInventorySlots();
        slots.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
        autismclient.util.macro.XCarryAction take = new autismclient.util.macro.XCarryAction();
        take.mode = autismclient.util.macro.XCarryAction.Mode.TAKE_OUT;
        List<MultiXCarryPlanner.Click> takeClicks = MultiXCarryPlanner.plan(take, slots, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, takeClicks.getFirst().input());

        autismclient.util.macro.XCarryAction drop = new autismclient.util.macro.XCarryAction();
        drop.mode = autismclient.util.macro.XCarryAction.Mode.DROP;
        List<MultiXCarryPlanner.Click> dropClicks = MultiXCarryPlanner.plan(drop, slots, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(net.minecraft.world.inventory.ContainerInput.THROW, dropClicks.getFirst().input());
        assertEquals(1, dropClicks.getFirst().button());
    }

    @Test
    void xCarryPlannerTrimsAnExistingCursorBeforeRunningTheConfiguredMode() {
        List<net.minecraft.world.item.ItemStack> slots = emptyInventorySlots();
        slots.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
        net.minecraft.world.item.ItemStack cursor =
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3);
        autismclient.util.macro.XCarryAction drop = new autismclient.util.macro.XCarryAction();
        drop.mode = autismclient.util.macro.XCarryAction.Mode.DROP;

        List<MultiXCarryPlanner.Click> clicks = MultiXCarryPlanner.plan(drop, slots, cursor);

        assertEquals(2, clicks.size());
        assertTrue(clicks.stream().allMatch(click -> click.slot() == 9));
        assertTrue(clicks.stream().allMatch(click -> click.button() == 1));
        assertTrue(clicks.stream().allMatch(click -> click.input() == net.minecraft.world.inventory.ContainerInput.PICKUP));
    }

    @Test
    void xCarryPlannerHonorsSafeClickPickupDelaySettings() {
        List<net.minecraft.world.item.ItemStack> slots = emptyInventorySlots();
        slots.set(36, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 2));
        autismclient.util.macro.XCarryAction xcarry = new autismclient.util.macro.XCarryAction();
        xcarry.transferMode = autismclient.util.macro.XCarryAction.TransferMode.SAFE_CLICK;
        xcarry.safeClickDelayTicks = 4;
        xcarry.entryTargets.add(autismclient.util.macro.ItemTarget.registry("minecraft:diamond"));
        xcarry.entryDestinations.add(1);

        List<MultiXCarryPlanner.Click> delayed = MultiXCarryPlanner.plan(
            xcarry, slots, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(200L, delayed.getFirst().delayAfterMs());

        xcarry.safeClickDelayAfterPickup = false;
        List<MultiXCarryPlanner.Click> immediate = MultiXCarryPlanner.plan(
            xcarry, slots, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(0L, immediate.getFirst().delayAfterMs());
    }

    @Test
    void clickLeftSwingsRightUses() {
        autismclient.util.macro.ClickAction left = new autismclient.util.macro.ClickAction(autismclient.util.macro.ClickAction.ContainerInput.LEFT);
        left.clickCount = 2;
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, left), host);
        assertEquals(List.of("swing ", "swing "), host.commands);

        autismclient.util.macro.ClickAction right = new autismclient.util.macro.ClickAction(autismclient.util.macro.ClickAction.ContainerInput.RIGHT);
        FakeHost host2 = new FakeHost();
        runToEnd(macro(false, -1, right), host2);
        assertEquals(List.of("use "), host2.commands);
    }

    @Test
    void useItemPhasePreservesPhaseHandRepeatAndRelease() {
        autismclient.util.macro.UseItemPhaseAction phase = new autismclient.util.macro.UseItemPhaseAction();
        phase.phase = autismclient.util.macro.UseItemPhaseAction.Phase.SWING;
        phase.hand = "OFF_HAND";
        phase.repeat = 2;
        phase.releaseAfterHold = true;
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, phase), host);
        assertEquals(List.of(
            "use-phase SWING offhand", "use-phase RELEASE_USE offhand",
            "use-phase SWING offhand", "use-phase RELEASE_USE offhand"
        ), host.commands);
    }

    @Test
    void useItemPhaseReleasesAfterConfiguredHold() {
        autismclient.util.macro.UseItemPhaseAction phase = new autismclient.util.macro.UseItemPhaseAction();
        phase.phase = autismclient.util.macro.UseItemPhaseAction.Phase.START_USE;
        phase.holdTicks = 2;
        phase.releaseAfterHold = true;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, phase, cmd("after")));
        run.step(0, host);
        assertEquals(List.of("use-phase START_USE mainhand"), host.commands);
        run.step(99, host);
        assertEquals(List.of("use-phase START_USE mainhand"), host.commands);
        run.step(100, host);
        assertEquals(List.of("use-phase START_USE mainhand", "use-phase RELEASE_USE mainhand", "/after"), host.commands);
    }

    @Test
    void openContainerBlockWorksInHeadless() {

        autismclient.util.macro.OpenContainerAction oc = new autismclient.util.macro.OpenContainerAction();
        oc.targetMode = autismclient.util.macro.OpenContainerAction.TargetMode.BLOCK;
        oc.blockPos = new net.minecraft.core.BlockPos(5, 63, 9);
        FakeHost host = new FakeHost();
        host.full = false;
        runToEnd(macro(false, -1, oc), host);
        assertEquals(List.of("useon 5,63,9"), host.commands);

        List<String> warnings = MultiMacroSupport.analyze(macro(false, -1, oc));
        assertTrue(warnings.isEmpty() || !warnings.getFirst().contains("SKIPPED"),
            "block-target open container is not skipped headless");
    }

    @Test
    void breakAndPlaceWorkInHeadless() {
        autismclient.util.macro.BreakAction b = new autismclient.util.macro.BreakAction();
        b.blockPos = new net.minecraft.core.BlockPos(1, 2, 3);
        b.direction = net.minecraft.core.Direction.UP;
        autismclient.util.macro.PlaceAction p = new autismclient.util.macro.PlaceAction();
        p.blockPos = new net.minecraft.core.BlockPos(4, 5, 6);
        p.direction = net.minecraft.core.Direction.UP;
        FakeHost host = new FakeHost();
        host.full = false;
        runToEnd(macro(false, -1, b, p), host);
        assertEquals(List.of("break 1,2,3", "useon 4,5,6"), host.commands);
    }

    @Test
    void instaBreakBreaksCoordsInFullMode() {
        autismclient.util.macro.InstaBreakAction ib = new autismclient.util.macro.InstaBreakAction();
        ib.blockPos = new net.minecraft.core.BlockPos(10, 64, -3);
        ib.times = 2;
        FakeHost host = new FakeHost();
        host.full = true;
        runToEnd(macro(false, -1, ib), host);
        assertEquals(List.of("break 10,64,-3", "break 10,64,-3"), host.commands);
    }

    @Test
    void manualVclipRunsInFullModeWithBoundedSegments() {
        autismclient.util.macro.VClipAction clip = new autismclient.util.macro.VClipAction();
        clip.mode = autismclient.util.macro.VClipAction.Mode.MANUAL;
        clip.deltaY = 25.0;
        clip.segmentBlocks = 10;
        clip.maxPackets = 20;
        clip.forceGrounded = true;
        FakeHost host = new FakeHost();
        host.full = true;
        runToEnd(macro(false, -1, clip), host);
        assertEquals(List.of("clip 0.0,25.0,0.0 x3 true"), host.commands);
    }

    @Test
    void manualHclipUsesTrackedYawInFullMode() {
        autismclient.util.macro.HClipAction clip = new autismclient.util.macro.HClipAction();
        clip.mode = autismclient.util.macro.HClipAction.Mode.MANUAL;
        clip.blocks = 12.0;
        clip.segmentBlocks = 10;
        FakeHost host = new FakeHost();
        host.full = true;
        runToEnd(macro(false, -1, clip), host);
        assertEquals(List.of("clip -0.0,0.0,12.0 x2 false"), host.commands);
    }

    @Test
    void fastTpActionBurstsHeadlessBotOnGround() {
        autismclient.util.macro.PacedTpAction tp = new autismclient.util.macro.PacedTpAction();
        tp.x = 5.0D;
        tp.y = 2.0D;
        tp.z = -1.0D;
        tp.relativeX = true;
        tp.relativeY = true;
        tp.relativeZ = true;
        tp.maxPackets = 2;
        tp.pauseMs = 500;
        FakeHost host = new FakeHost() {
            @Override public void clip(double dx, double dy, double dz, int segments, boolean onGround) {
                super.clip(dx, dy, dz, segments, onGround);
                px += dx;
                py += dy;
                pz += dz;
            }
        };
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, tp, cmd("after")));
        run.step(0L, host);
        assertTrue(host.commands.isEmpty());
        run.step(50L, host);
        assertEquals(1, host.commands.size());
        assertTrue(host.commands.getFirst().endsWith("true"), "fast TP must stay grounded");
        for (long now = 100L; now <= 1_000L && !run.done(); now += 50L) run.step(now, host);
        assertTrue(run.done());
        assertEquals(5.0D, host.px, 1.0E-9D);
        assertEquals(2.0D, host.py, 1.0E-9D);
        assertEquals(-1.0D, host.pz, 1.0E-9D);
        assertEquals("/after", host.commands.getLast());
    }

    @Test
    void automaticClipModesWarnAndSkipEvenInFullMode() {
        autismclient.util.macro.VClipAction clip = new autismclient.util.macro.VClipAction();
        clip.mode = autismclient.util.macro.VClipAction.Mode.TOP;
        AutismMacro m = macro(false, -1, clip);
        List<String> warnings = MultiMacroSupport.analyze(m);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("only Manual"));
        FakeHost host = new FakeHost();
        host.full = true;
        assertTrue(runToEnd(m, host).isEmpty());
    }

    @Test
    void clientLastTargetActionsWarnAndSkipInFullMode() {
        autismclient.util.macro.InteractEntityAction interact = new autismclient.util.macro.InteractEntityAction();
        interact.targetMode = autismclient.util.macro.InteractEntityAction.TargetMode.LAST_TARGET;
        AutismMacro m = macro(false, -1, interact);
        List<String> warnings = MultiMacroSupport.analyze(m);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("last crosshair target"));
        FakeHost host = new FakeHost();
        host.full = true;
        assertTrue(runToEnd(m, host).isEmpty());
    }

    @Test
    void nearestBlockLookModeWarnsBecauseBotsDoNotLoadChunks() {
        autismclient.util.macro.LookAtBlockAction look = new autismclient.util.macro.LookAtBlockAction();
        look.targetMode = autismclient.util.macro.LookAtBlockAction.TargetMode.BLOCK;
        List<String> warnings = MultiMacroSupport.analyze(macro(false, -1, look));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("chunk data"));
    }

    @Test
    void variableConditionIsReportedAsSupported() {
        autismclient.util.macro.MacroVariablesAction variables = new autismclient.util.macro.MacroVariablesAction();
        variables.names.add("ready");
        variables.values.add("true");
        IfAction conditional = new IfAction();
        MacroCondition leaf = MacroCondition.leaf(MacroCondition.Kind.VARIABLE);
        leaf.item = "ready";
        leaf.op = MacroCondition.Op.IS_TRUE;
        conditional.condition = leaf;
        conditional.thenSteps = 1;
        AutismMacro m = macro(false, -1, variables, conditional, cmd("go"));
        assertTrue(MultiMacroSupport.analyze(m).isEmpty());
        assertEquals(List.of("/go"), runToEnd(m, new FakeHost()));
    }

    @Test
    void tickSyncWaitsOneTick() {
        autismclient.util.macro.TickSyncAction ts = new autismclient.util.macro.TickSyncAction();
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), ts, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(60, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void revisionSyncWaitsForContainerStateChange() {
        autismclient.util.macro.RevisionSyncAction rs = new autismclient.util.macro.RevisionSyncAction();
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), rs, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        run.step(100, host);
        assertEquals(List.of("/a"), host.commands);
        host.rev = 5;
        run.step(200, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void payloadSendsRawBytesOnChannel() {
        autismclient.util.macro.PayloadAction p = new autismclient.util.macro.PayloadAction();
        p.channel = "myserver:hello";
        p.payloadData = "01 02 03";
        FakeHost host = new FakeHost();
        runToEnd(macro(false, -1, p), host);
        assertEquals(List.of("payload myserver:hello 01 02 03"), host.commands);
    }

    @Test
    void waitBlockAtPositionProceedsWhenBlockUpdates() {
        autismclient.util.macro.WaitForBlockAction w = new autismclient.util.macro.WaitForBlockAction();
        w.checkMode = autismclient.util.macro.WaitForBlockAction.CheckMode.AT_POSITION;
        w.waitBehavior = autismclient.util.macro.WaitForBlockAction.WaitBehavior.PLACED;
        w.anyBlock = true;
        w.blockPos = new net.minecraft.core.BlockPos(1, 2, 3);
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a"), host.commands);
        host.blockPresent = true;
        run.step(100, host);
        assertEquals(List.of("/a", "/b"), host.commands);
    }

    @Test
    void waitEntitySkipsInHeadlessInsteadOfHanging() {
        autismclient.util.macro.WaitForEntityAction w = new autismclient.util.macro.WaitForEntityAction();
        w.entityIds.add("minecraft:zombie");
        w.checkMode = autismclient.util.macro.WaitForEntityAction.CheckMode.RADIUS;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);

        assertEquals(List.of("/a", "/b"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void waitBlockNonAtPositionSkips() {
        autismclient.util.macro.WaitForBlockAction w = new autismclient.util.macro.WaitForBlockAction();
        w.checkMode = autismclient.util.macro.WaitForBlockAction.CheckMode.IN_REACH;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("a"), w, cmd("b")));
        run.step(0, host);
        assertEquals(List.of("/a", "/b"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void analyzeMoveIsLimitedButRuns() {
        AutismMacro m = macro(false, -1, new autismclient.util.macro.MoveAction());
        List<String> warnings = MultiMacroSupport.analyze(m);
        assertEquals(1, warnings.size(), "MOVE carries a limited note");
        assertFalse(warnings.get(0).contains("SKIPPED"), "MOVE is never skipped");
    }

    @Test
    void moveAndJumpRunHeadless() {
        autismclient.util.macro.MoveAction move = new autismclient.util.macro.MoveAction();
        move.durationTicks = 1;
        move.nonBlocking = true;
        autismclient.util.macro.JumpAction jump = new autismclient.util.macro.JumpAction();
        jump.durationTicks = 1;
        FakeHost host = new FakeHost();
        host.full = false;
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, move, jump));
        run.step(0, host);
        run.step(10_000, host);
        assertTrue(run.done());
        assertEquals(List.of("move", "jump"), host.commands);
    }

    @Test
    void unavailableDeadSessionDoesNotAdvancePendingMacroWork() {
        DelayAction delay = new DelayAction(10);
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, cmd("before"), delay, cmd("after")));
        run.step(0, host);
        assertEquals(List.of("/before"), host.commands);

        host.ready = false;
        host.customMenuPhase = false;
        run.step(10_000, host);
        assertEquals(List.of("/before"), host.commands);
        assertFalse(run.done());

        host.ready = true;
        host.customMenuPhase = true;
        run.step(10_001, host);
        assertEquals(List.of("/before", "/after"), host.commands);
        assertTrue(run.done());
    }

    @Test
    void sendChatResolvesUsernameBuiltin() {
        FakeHost host = new FakeHost();
        host.username = "Steve";
        List<String> out = runToEnd(macro(false, -1, new SendChatAction("hi {username}")), host);
        assertEquals(List.of("hi Steve"), out);
    }

    @Test
    void sendChatSuppressedWhenVariableMissing() {

        FakeHost host = new FakeHost();
        List<String> out = runToEnd(macro(false, -1, new SendChatAction("hi {ghost}"), new SendChatAction("ok")), host);
        assertEquals(List.of("ok"), out);
    }

    @Test
    void sendChatResolvesPositionBuiltins() {
        FakeHost host = new FakeHost();
        host.px = 10.0; host.py = 64.0; host.pz = -20.0;
        List<String> out = runToEnd(macro(false, -1, new SendChatAction("at {bx},{by},{bz}")), host);
        assertEquals(List.of("at 10,64,-20"), out);
    }

    @Test
    void commandResolvesServerBuiltin() {
        FakeHost host = new FakeHost();
        host.server = "play.example.com";
        assertEquals(List.of("/connect play.example.com"),
            runToEnd(macro(false, -1, cmd("connect {server}")), host));
    }

    @Test
    void passwordTemplateResolvesInsideLoginMacro() {
        FakeHost host = new FakeHost();
        host.macroPassword = "s3cret";
        assertEquals(List.of("/login s3cret"), runToEnd(macro(false, -1, cmd("login {password}")), host));
    }

    @Test
    void passwordTemplateMissingOutsideLoginMacroSuppressesCommand() {
        FakeHost host = new FakeHost();
        assertEquals(List.of(), runToEnd(macro(false, -1, cmd("login {password}")), host));
    }

    @Test
    void templateFormatterAndDefaultApply() {
        FakeHost host = new FakeHost();
        host.username = "steve";

        assertEquals(List.of("hi STEVE and friend"),
            runToEnd(macro(false, -1, new SendChatAction("hi {username|upper} and {ghost|default:friend}")), host));
    }

    @Test
    void templateBarePickResolves() {

        FakeHost host = new FakeHost();
        assertEquals(List.of("alpha"),
            runToEnd(macro(false, -1, new SendChatAction("{alpha,alpha,alpha}")), host));
    }

    @Test
    void macroVariablesAbortsWholeSetWhenOneValueMissing() {

        autismclient.util.macro.MacroVariablesAction mv = new autismclient.util.macro.MacroVariablesAction();
        mv.names.add("a");
        mv.values.add("ok");
        mv.names.add("b");
        mv.values.add("{ghost}");
        FakeHost host = new FakeHost();

        assertEquals(List.of(), runToEnd(macro(false, -1, mv, cmd("say {a}")), host));
    }

    @Test
    void useItemPhaseRepeatPacedAcrossTicks() {
        autismclient.util.macro.UseItemPhaseAction phase = new autismclient.util.macro.UseItemPhaseAction();
        phase.phase = autismclient.util.macro.UseItemPhaseAction.Phase.USE_ONCE;
        phase.repeat = 20;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, phase));
        run.step(0, host);
        assertEquals(8, host.commands.size(), "one tick emits at most EMIT_BUDGET uses");
        run.step(50, host);
        run.step(100, host);
        assertEquals(20, host.commands.size(), "all repeats eventually sent");
        assertTrue(host.commands.stream().allMatch(c -> c.equals("use-phase USE_ONCE mainhand")));
        assertTrue(run.done());
    }

    @Test
    void clickBurstPacedAcrossTicks() {
        autismclient.util.macro.ClickAction click =
            new autismclient.util.macro.ClickAction(autismclient.util.macro.ClickAction.ContainerInput.RIGHT);
        click.clickCount = 20;
        FakeHost host = new FakeHost();
        MultiMacroRun run = new MultiMacroRun(macro(false, -1, click));
        run.step(0, host);
        assertEquals(8, host.commands.size(), "one tick emits at most EMIT_BUDGET clicks");
        run.step(50, host);
        run.step(100, host);
        assertEquals(20, host.commands.size());
        assertTrue(host.commands.stream().allMatch(c -> c.equals("use ")));
        assertTrue(run.done());
    }

    @Test
    void captureNumberModifierAppliesToPatternGroups() {

        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT;
        cap.matchMode = autismclient.util.macro.MacroCapturePattern.Mode.CAPTURE;
        cap.pattern = "Bal: {amt}";
        cap.saveAs = "line";
        cap.numberModifier = autismclient.util.macro.CaptureValueAction.NumberModifier.PLUS;
        cap.numberModifierAmount = 5.0;
        FakeHost host = new FakeHost();
        host.chatLines.add("Bal: 100");
        assertEquals(List.of("/give 105"),
            runToEnd(macro(false, -1, cap, cmd("give {amt}")), host));
    }

    @Test
    void captureDivideByZeroAbortsAndSetsNothing() {
        autismclient.util.macro.CaptureValueAction cap = new autismclient.util.macro.CaptureValueAction();
        cap.source = autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT;
        cap.saveAs = "n";
        cap.waitForTrigger = false;
        cap.numberModifier = autismclient.util.macro.CaptureValueAction.NumberModifier.DIVIDE;
        cap.numberModifierAmount = 0.0;
        FakeHost host = new FakeHost();
        host.chatLines.add("100");

        assertEquals(List.of(), runToEnd(macro(false, -1, cap, cmd("give {n}")), host));
    }

    private static autismclient.util.macro.CaptureValueAction.ScoreboardLine scoreLine(
        String owner, String name, String score
    ) {
        return new autismclient.util.macro.CaptureValueAction.ScoreboardLine(
            "sidebar\u001F" + owner, 0, "sidebar", "Stats", owner, name, score, name + ": " + score);
    }

    private static List<net.minecraft.world.item.ItemStack> emptyInventorySlots() {
        return new ArrayList<>(java.util.Collections.nCopies(46, net.minecraft.world.item.ItemStack.EMPTY));
    }

    private static MacroCondition healthBelow(int amount) {
        MacroCondition leaf = MacroCondition.leaf(MacroCondition.Kind.HEALTH);
        leaf.cmp = MacroCondition.Cmp.BELOW;
        leaf.amount = amount;
        MacroCondition root = MacroCondition.group(MacroCondition.Combine.ALL);
        root.children.add(leaf);
        return root;
    }
}
