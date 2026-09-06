package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import autismclient.util.lan.*;
import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.protocol.Packet;

@SuppressWarnings("deprecation")
public class AutismLANSync {
    private static AutismLANSync instance;
    private static final int MACRO_INLINE_UTF_LIMIT = 60_000;
    private static final int MACRO_CHUNK_SIZE = 32_000;
    private static final long MACRO_CHUNK_TIMEOUT_MS = 120_000L;
    private static final int TICK_EXECUTION_DELAY_TICKS = 10;
    private static final String COMMAND_QUEUE_FLUSH = "__QUEUE_FLUSH__";
    private static final String COMMAND_DELAY_PACKETS = "__DELAY_PACKETS__\t";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean configurationOnly;

    private volatile String sessionId;
    private volatile boolean isHost = false;
    private volatile String myUsername = "";
    private final Map<String, ClientInfo> connectedClients = new ConcurrentHashMap<>();
    private final Map<String, String> discoveredSessions = new ConcurrentHashMap<>();

    private final java.util.concurrent.ExecutorService sendExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Autism-LAN-Send");
            t.setDaemon(true);
            return t;
        });

    private final Set<String> clientsReady = ConcurrentHashMap.newKeySet();

    private volatile boolean isSearching = false;
    private int searchTicksRemaining = 0;
    private static final int SEARCH_DURATION_TICKS = 400;
    private static final int SEARCH_BROADCAST_INTERVAL = 5;

    private volatile Runnable onClientJoined;
    private volatile Runnable onClientLeft;
    private volatile Runnable onCountdown;
    private volatile Runnable onSendNow;
    private volatile Runnable onSessionStateChanged;
    private volatile Runnable onSyncStateChanged;
    private volatile Runnable onSpreadCalculated;
    private volatile Runnable onPeerStatusChanged;

    private volatile ConcurrentHashMap<String, Map<String, AutismMacro>> clientMacroLists = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, Map<String, AutismMacro>> remoteMeteorMacros = new ConcurrentHashMap<>();
    private Map<String, String> lastBroadcastMeteorMacros = new ConcurrentHashMap<>();
    private final Map<String, PendingMacroTransfer> pendingMacroTransfers = new ConcurrentHashMap<>();

    private volatile Map<String, String> syncedAssignments = new ConcurrentHashMap<>();
    private volatile String assignmentSetBy = "";
    private volatile Runnable onAssignmentsChanged;

    private volatile SpreadResult lastSpreadResult = null;
    private final CopyOnWriteArrayList<SpreadResult> spreadHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_SPREAD_HISTORY = 10;

    public enum SyncState {
        IDLE, PREPARING, ALL_READY, DISPATCHING_GO, EXECUTING, REPORTING, DONE
    }

    public enum ExecutionMethod {
        INSTANT, TICK
    }

    public static class SyncContext {
        public final long executionId;
        public final boolean isMacro;
        public final String macroName;
        public final String command;
        public final AutismMacro macro;
        public final boolean isInitiator;
        public final ExecutionMethod executionMethod;
        public volatile long targetTick;

        public SyncContext(long executionId, boolean isMacro, String macroName,
                           String command, AutismMacro macro, boolean isInitiator) {
            this(executionId, isMacro, macroName, command, macro, isInitiator, ExecutionMethod.INSTANT, -1L);
        }

        public SyncContext(long executionId, boolean isMacro, String macroName,
                           String command, AutismMacro macro, boolean isInitiator,
                           ExecutionMethod executionMethod, long targetTick) {
            this.executionId = executionId;
            this.isMacro = isMacro;
            this.macroName = macroName;
            this.command = command;
            this.macro = macro;
            this.isInitiator = isInitiator;
            this.executionMethod = executionMethod != null ? executionMethod : ExecutionMethod.INSTANT;
            this.targetTick = targetTick;
        }
    }

    private static final class ScheduledTickExecution {
        final long executionId;
        final long targetTick;
        volatile long targetWallNanos = 0L;
        volatile Thread preciseScheduler = null;

        ScheduledTickExecution(long executionId, long targetTick) {
            this.executionId = executionId;
            this.targetTick = targetTick;
        }
    }

    private static final class TickExecutionReport {
        final long actualTick;
        final boolean late;

        TickExecutionReport(long actualTick, boolean late) {
            this.actualTick = actualTick;
            this.late = late;
        }
    }

    private static final class PendingMacroTransfer {
        private final long createdAtMs = System.currentTimeMillis();
        private final byte[][] chunks;
        private int receivedChunks;
        private int totalBytes;

        private PendingMacroTransfer(int totalChunks) {
            this.chunks = new byte[totalChunks][];
        }

        private synchronized byte[] addChunk(int index, byte[] data) {
            if (index < 0 || index >= chunks.length || data == null) return null;
            if (chunks[index] == null) {
                chunks[index] = data;
                receivedChunks++;
                totalBytes += data.length;
            }
            if (receivedChunks != chunks.length) return null;

            byte[] complete = new byte[totalBytes];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null) return null;
                System.arraycopy(chunk, 0, complete, offset, chunk.length);
                offset += chunk.length;
            }
            return complete;
        }
    }

    private volatile SyncContext activeSyncCtx = null;
    private volatile SyncState syncState = SyncState.IDLE;
    private volatile ExecutionMethod executionMethod = ExecutionMethod.INSTANT;
    private volatile ScheduledTickExecution scheduledTickExecution = null;
    private volatile long observedGameTick = -1L;

    private final Map<String, Long> executionTimestamps = new ConcurrentHashMap<>();

    private final Map<String, Long> ackReceiptTimes = new ConcurrentHashMap<>();
    private final Map<String, TickExecutionReport> tickExecutionReports = new ConcurrentHashMap<>();
    private final Set<Long> locallyExecutedIds = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> peerStepProgress = new ConcurrentHashMap<>();
    private volatile Runnable onStepProgressChanged;

    private static final int TCP_PORT = 25568;
    private ServerSocket tcpServer;
    private Thread tcpAcceptThread;
    private final Map<String, Socket> tcpClients = new ConcurrentHashMap<>();
    private Socket tcpHostSocket;
    private Thread tcpReadThread;

    private final Object tcpHostWriteLock = new Object();
    private final Map<String, Object> tcpClientWriteLocks = new ConcurrentHashMap<>();

    private final Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private long lastHeartbeatSentMs = 0;
    private static final long HEARTBEAT_INTERVAL_MS = 9000;
    private static final long HEARTBEAT_STALE_MS = 15000;
    private static final long HEARTBEAT_DEAD_MS = 30000;

    private volatile boolean reconnecting = false;
    private volatile String lastHostIp = null;
    private volatile String lastSessionId = null;

    private AutismLANSync() {}

    public static AutismLANSync getInstance() {
        if (instance == null) {
            instance = new AutismLANSync();
        }
        return instance;
    }

    public void start() {
        if (PackHideState.isHardLocked()) return;
        if (running.get()) return;
        running.set(true);
        AutismClientMessaging.sendPrefixed("§aLAN Sync started (TCP mode on port " + TCP_PORT + ").");
        AutismClientAddon.LOG.info("[Autism-LAN] Started in TCP-only mode on port {}", TCP_PORT);
    }

    public void stop() {
        if (!running.get()) return;
        if (sessionId != null) leaveSession();
        running.set(false);
        clearSession();
        discoveredSessions.clear();
        AutismClientMessaging.sendPrefixed("§eLAN Sync stopped.");
    }

    public void stopSilently() {
        if (sessionId != null) {
            reconnecting = false;
            lastHostIp = null;
            lastSessionId = null;
            sendTcpPacket(new LanPacket.LeavePacket(sessionId, getUsername()));
        }
        running.set(false);
        clearSession();
        discoveredSessions.clear();
    }

    public void createSession() {
        if (!running.get()) {
            AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
            return;
        }

        cancelSearch();
        discoveredSessions.clear();

        if (sessionId != null) leaveSession();
        stopTcp();

        sessionId = generateSessionId();
        isHost = true;
        myUsername = getUsername();
        if (myUsername.isEmpty()) myUsername = "Host";
        connectedClients.put(myUsername, new ClientInfo(myUsername, true));

        resetSyncState();
        startTcpServer();

        AutismClientMessaging.sendPrefixed("§aSession created: " + sessionId + " [TCP Sync]");
        fireCallback(onSessionStateChanged);
    }

    public void joinSession(String targetSessionId) {
        joinSession(targetSessionId, "127.0.0.1");
    }

    public void joinSession(String targetSessionId, String hostIp) {
        if (!running.get()) {
            AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
            return;
        }
        if (sessionId != null && sessionId.equals(targetSessionId)) {
            AutismClientMessaging.sendPrefixed("§cAlready in session " + sessionId);
            return;
        }

        cancelSearch();
        discoveredSessions.clear();

        if (sessionId != null) leaveSession();
        stopTcp();

        sessionId = targetSessionId;
        lastSessionId = targetSessionId;
        isHost = false;
        myUsername = getUsername();
        if (myUsername.isEmpty()) myUsername = "Player";
        connectedClients.put(myUsername, new ClientInfo(myUsername, false));
        resetSyncState();

        if (hostIp != null && !hostIp.isBlank() && !isLoopbackHost(hostIp)) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync is same-machine only; ignoring remote host " + hostIp + ".");
            AutismClientAddon.LOG.warn("[TCP] Refused non-loopback LAN Sync host {}", hostIp);
            return;
        }
        lastHostIp = "127.0.0.1";
        connectToTcpServer(lastHostIp);
        AutismClientAddon.LOG.info("[TCP] Connecting to {}:{}", lastHostIp, TCP_PORT);

        fireCallback(onClientJoined);
        fireCallback(onSessionStateChanged);
    }

    private static boolean isLoopbackHost(String host) {
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.equals("localhost") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1")) return true;
        try {
            for (java.net.InetAddress addr : java.net.InetAddress.getAllByName(h)) {
                if (!addr.isLoopbackAddress()) return false;
            }
            return true;
        } catch (java.net.UnknownHostException ex) {
            return false;
        }
    }

    public void leaveSession() {
        if (sessionId == null) return;

        reconnecting = false;
        lastHostIp = null;
        lastSessionId = null;
        sendTcpPacket(new LanPacket.LeavePacket(sessionId, getUsername()));
        clearSession();
        AutismClientMessaging.sendPrefixed("§eLeft session.");
        fireCallback(onSessionStateChanged);
    }

    private void clearSession() {
        sessionId = null;
        isHost = false;
        myUsername = "";
        reconnecting = false;
        lastHostIp = null;
        lastSessionId = null;
        connectedClients.clear();
        clientMacroLists.clear();
        remoteMeteorMacros.clear();
        lastBroadcastMeteorMacros.clear();
        syncedAssignments.clear();
        assignmentSetBy = "";
        isSearching = false;
        searchTicksRemaining = 0;
        lastSpreadResult = null;
        observedGameTick = -1L;
        resetSyncState();
        lastHeartbeatTime.clear();
        stopTcp();
    }

    public void onGameDisconnected() {
        if (sessionId == null) return;
        resetSyncState();
    }

    private void resetSyncState() {
        activeSyncCtx = null;
        setSyncState(SyncState.IDLE);
        executionTimestamps.clear();
        ackReceiptTimes.clear();
        tickExecutionReports.clear();
        scheduledTickExecution = null;
        locallyExecutedIds.clear();
        clientsReady.clear();
        peerStepProgress.clear();
    }

    private void setSyncState(SyncState state) {
        this.syncState = state;
        fireCallback(onSyncStateChanged);
    }

    private void initiateGoSync(boolean isMacro, String macroName, String command) {
        initiateGoSync(isMacro, macroName, command, null);
    }

    private void initiateGoSync(boolean isMacro, String macroName, String command, Map<String, String> macroAssignments) {
        if (!runtimeExecutionAvailable()) return;
        if (!syncActionIdle()) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync is busy with another synchronized action.");
            return;
        }
        long executionId = System.nanoTime();

        if (isMacro) {
            if (macroAssignments != null && !macroAssignments.isEmpty()) {
                Map<String, String> missingAssignments = getMissingAssignedMacros(macroAssignments);
                if (!missingAssignments.isEmpty()) {
                    AutismClientMessaging.sendPrefixed("§cMissing assigned macros on: " + summarizeAssignments(missingAssignments));
                    return;
                }
            } else if (macroName != null && !macroName.isBlank()) {
                List<String> missingPeers = getPeersMissingMacro(macroName);
                if (!missingPeers.isEmpty()) {
                    AutismClientMessaging.sendPrefixed("§cMissing '" + macroName + "' on: " + summarizeNames(missingPeers));
                    return;
                }
            }
        }

        String myMacroName = macroName;
        if (macroAssignments != null && macroAssignments.containsKey(myUsername)) {
            myMacroName = macroAssignments.get(myUsername);
        }

        AutismMacro macro = null;
        if (isMacro && myMacroName != null) {
            macro = AutismMacroManager.get().get(myMacroName);
            if (macro != null) {
                macro.regenerateAllPackets();
            } else {
                AutismClientMessaging.sendPrefixed("§cMacro not found: " + myMacroName);
                return;
            }
        }

        ExecutionMethod method = executionMethod;
        activeSyncCtx = new SyncContext(executionId, isMacro, myMacroName, command, macro, true, method, -1L);
        clientsReady.clear();
        executionTimestamps.clear();
        ackReceiptTimes.clear();
        tickExecutionReports.clear();
        locallyExecutedIds.clear();
        scheduledTickExecution = null;
        peerStepProgress.clear();
        clientsReady.add(myUsername);
        setSyncState(SyncState.PREPARING);

        AutismClientAddon.LOG.info("[Sync] Initiating GO-sync: execId={}", executionId);

        String assignmentStr = "";
        if (macroAssignments != null && !macroAssignments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : macroAssignments.entrySet()) {
                if (sb.length() > 0) sb.append('\t');
                sb.append(entry.getKey()).append('\t').append(entry.getValue());
            }
            assignmentStr = sb.toString();
        }

        LanPacket.PrepareExecutionPacket preparePacket = new LanPacket.PrepareExecutionPacket(
            sessionId, executionId, isMacro, macroName != null ? macroName : "", command, myUsername);
        preparePacket.macroAssignments = assignmentStr;
        preparePacket.executionMethod = method.ordinal();
        preparePacket.targetTick = -1L;
        sendTcpPacket(preparePacket);

        broadcastSyncState(SyncState.PREPARING, clientsReady.size() + "/" + connectedClients.size() + " ready");

        new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                long waitStart = System.currentTimeMillis();
                int expectedClients = connectedClients.size();

                while (clientsReady.size() < expectedClients) {
                    if (System.currentTimeMillis() - waitStart > 3000) {
                        AutismClientAddon.LOG.warn("[Sync] Timeout waiting for clients. Got {}/{}",
                            clientsReady.size(), expectedClients);
                        break;
                    }
                    java.util.concurrent.locks.LockSupport.parkNanos(500_000);
                }

                setSyncState(SyncState.ALL_READY);
                broadcastSyncState(SyncState.ALL_READY, clientsReady.size() + "/" + expectedClients + " ready");
                AutismClientAddon.LOG.info("[Sync] All {} clients ready!", clientsReady.size());

                setSyncState(SyncState.DISPATCHING_GO);
                long targetTick = -1L;
                if (method == ExecutionMethod.TICK) {
                    long currentTick = getCurrentGameTick();
                    if (currentTick < 0L) {
                        AutismClientMessaging.sendPrefixed("§cCannot tick-sync without a loaded world.");
                        setSyncState(SyncState.IDLE);
                        return;
                    }
                    targetTick = currentTick + TICK_EXECUTION_DELAY_TICKS;
                    SyncContext ctx = activeSyncCtx;
                    if (ctx != null && ctx.executionId == executionId) ctx.targetTick = targetTick;
                }
                byte[] goPayload = serializePacket(new LanPacket.GoPacket(sessionId, executionId, method.ordinal(), targetTick));

                setSyncState(SyncState.EXECUTING);
                for (Map.Entry<String, Socket> entry : tcpClients.entrySet()) {
                    Socket clientSocket = entry.getValue();
                    String clientName = entry.getKey();
                    try {
                        if (!clientSocket.isClosed()) {
                            Object lock = tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                            synchronized (lock) {
                                OutputStream out = clientSocket.getOutputStream();
                                out.write(goPayload);
                                out.flush();
                            }
                        }
                    } catch (IOException e) {
                        AutismClientAddon.LOG.warn("[Sync] Failed to send GO to {}", clientName);
                    }
                }

                if (method == ExecutionMethod.TICK) {
                    scheduleTickExecution(executionId, targetTick);
                } else {
                    int hostOffset = getPlayerDelayOffset(myUsername);
                    if (hostOffset > 0) {
                        try { Thread.sleep(hostOffset); } catch (InterruptedException ignored) {  }
                    }

                    executeAction();
                }

            } catch (Exception e) {
                AutismClientAddon.LOG.error("[Sync] Error in GO-sync: ", e);
                setSyncState(SyncState.IDLE);
            }
        }, "Sync-GO-Initiator") {{ setDaemon(true); }}.start();
    }

    private void initiateGoSyncForQueue() {
        if (!runtimeExecutionAvailable()) return;
        long executionId = System.nanoTime();

        ExecutionMethod method = executionMethod;
        activeSyncCtx = new SyncContext(executionId, false, null, COMMAND_QUEUE_FLUSH, null, true, method, -1L);
        clientsReady.clear();
        executionTimestamps.clear();
        ackReceiptTimes.clear();
        tickExecutionReports.clear();
        locallyExecutedIds.clear();
        scheduledTickExecution = null;
        clientsReady.add(myUsername);
        setSyncState(SyncState.PREPARING);

        AutismClientAddon.LOG.info("[Sync] Initiating queue flush GO-sync: execId={}", executionId);

        LanPacket.PrepareExecutionPacket preparePacket = new LanPacket.PrepareExecutionPacket(sessionId, executionId, false,
            "", COMMAND_QUEUE_FLUSH, myUsername);
        preparePacket.executionMethod = method.ordinal();
        preparePacket.targetTick = -1L;
        sendTcpPacket(preparePacket);

        broadcastSyncState(SyncState.PREPARING, clientsReady.size() + "/" + connectedClients.size() + " ready");

        new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                long waitStart = System.currentTimeMillis();
                int expectedClients = connectedClients.size();

                while (clientsReady.size() < expectedClients) {
                    if (System.currentTimeMillis() - waitStart > 3000) {
                        AutismClientAddon.LOG.warn("[Sync] Timeout waiting for clients. Got {}/{}",
                            clientsReady.size(), expectedClients);
                        break;
                    }
                    java.util.concurrent.locks.LockSupport.parkNanos(500_000);
                }

                setSyncState(SyncState.ALL_READY);
                broadcastSyncState(SyncState.ALL_READY, clientsReady.size() + "/" + expectedClients + " ready");

                setSyncState(SyncState.DISPATCHING_GO);
                long targetTick = -1L;
                if (method == ExecutionMethod.TICK) {
                    long currentTick = getCurrentGameTick();
                    if (currentTick < 0L) {
                        AutismClientMessaging.sendPrefixed("§cCannot tick-sync without a loaded world.");
                        setSyncState(SyncState.IDLE);
                        return;
                    }
                    targetTick = currentTick + TICK_EXECUTION_DELAY_TICKS;
                    SyncContext ctx = activeSyncCtx;
                    if (ctx != null && ctx.executionId == executionId) ctx.targetTick = targetTick;
                }
                byte[] goPayload = serializePacket(new LanPacket.GoPacket(sessionId, executionId, method.ordinal(), targetTick));

                setSyncState(SyncState.EXECUTING);
                for (Map.Entry<String, Socket> entry : tcpClients.entrySet()) {
                    Socket clientSocket = entry.getValue();
                    String clientName = entry.getKey();
                    try {
                        if (!clientSocket.isClosed()) {
                            Object lock = tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                            synchronized (lock) {
                                OutputStream out = clientSocket.getOutputStream();
                                out.write(goPayload);
                                out.flush();
                            }
                        }
                    } catch (IOException e) {
                        AutismClientAddon.LOG.warn("[Sync] Failed to send GO to {}", clientName);
                    }
                }

                if (method == ExecutionMethod.TICK) {
                    scheduleTickExecution(executionId, targetTick);
                } else {
                    int hostOffset = getPlayerDelayOffset(myUsername);
                    if (hostOffset > 0) {
                        try { Thread.sleep(hostOffset); } catch (InterruptedException ignored) {  }
                    }

                    executeAction();
                }

            } catch (Exception e) {
                AutismClientAddon.LOG.error("[Sync] Error in queue GO-sync: ", e);
                setSyncState(SyncState.IDLE);
            }
        }, "Sync-GO-QueueFlush") {{ setDaemon(true); }}.start();
    }

    private void handlePrepare(long executionId, boolean isMacro, String macroName, String command,
                               String senderUsername, String macroAssignments, int methodOrdinal, long targetTick) {
        if (!runtimeExecutionAvailable()) return;
        if (senderUsername != null && senderUsername.equals(myUsername)) return;

        AutismClientAddon.LOG.info("[Sync] Received PREPARE from {}", senderUsername);
        peerStepProgress.clear();
        tickExecutionReports.clear();
        locallyExecutedIds.clear();
        scheduledTickExecution = null;

        String myMacroName = macroName;
        if (macroAssignments != null && !macroAssignments.isEmpty()) {
            String[] parts = macroAssignments.split("\t");
            for (int pi = 0; pi + 1 < parts.length; pi += 2) {
                if (parts[pi].equals(myUsername)) {
                    myMacroName = parts[pi + 1];
                    break;
                }
            }
        }

        AutismMacro macro = null;
        if (isMacro && myMacroName != null && !myMacroName.isEmpty()) {
            macro = AutismMacroManager.get().get(myMacroName);
            if (macro != null) {
                macro.regenerateAllPackets();
            }
        } else if (COMMAND_QUEUE_FLUSH.equals(command)) {
            AutismSharedState.get().regenerateQueue();
        }

        ExecutionMethod method = executionMethodFromOrdinal(methodOrdinal);
        executionMethod = method;
        activeSyncCtx = new SyncContext(executionId, isMacro, myMacroName, command, macro, false, method, targetTick);
        setSyncState(SyncState.PREPARING);

        sendTcpPacket(new LanPacket.ClientReadyPacket(sessionId, executionId, myUsername));
        AutismClientAddon.LOG.info("[Sync] Sent READY signal");
    }

    private void handleClientReady(long executionId, String username) {
        SyncContext ctx = activeSyncCtx;
        if (ctx != null && executionId == ctx.executionId) {
            clientsReady.add(username);
            broadcastSyncState(SyncState.PREPARING, clientsReady.size() + "/" + connectedClients.size() + " ready");
            AutismClientAddon.LOG.info("[Sync] Client ready: {} (total: {})", username, clientsReady.size());
        }
    }

    private void handleGo(long executionId, int methodOrdinal, long targetTick) {
        if (!runtimeExecutionAvailable()) return;
        SyncContext ctx = activeSyncCtx;
        if (ctx == null || executionId != ctx.executionId) {
            AutismClientAddon.LOG.warn("[Sync] GO for wrong execId, ignoring");
            return;
        }
        if (ctx.isInitiator) return;

        ExecutionMethod method = executionMethodFromOrdinal(methodOrdinal);
        ctx.targetTick = targetTick;
        executionMethod = method;
        if (method == ExecutionMethod.TICK && targetTick >= 0L) {
            AutismClientAddon.LOG.info("[Sync] Received GO! Scheduling tick execution for {}", targetTick);
            setSyncState(SyncState.EXECUTING);
            scheduleTickExecution(executionId, targetTick);
            return;
        }

        int offset = getPlayerDelayOffset(myUsername);
        if (offset > 0) {
            AutismClientAddon.LOG.info("[Sync] Received GO! Applying {}ms offset before executing.", offset);
            setSyncState(SyncState.EXECUTING);
            try { Thread.sleep(offset); } catch (InterruptedException ignored) {  }
            executeAction();
        } else {
            AutismClientAddon.LOG.info("[Sync] Received GO! Executing immediately.");
            setSyncState(SyncState.EXECUTING);
            executeAction();
        }
    }

    private void executeAction() {
        if (!runtimeExecutionAvailable() || PackHideState.isHardLocked()) return;
        final SyncContext ctx = activeSyncCtx;
        if (ctx == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(this::executeAction);
            return;
        }
        if (!locallyExecutedIds.add(ctx.executionId)) return;

        final long executedAtMs = System.currentTimeMillis();
        final long actualTick = getCurrentGameTick();
        final boolean tickMode = ctx.executionMethod == ExecutionMethod.TICK;
        final boolean late = tickMode && (actualTick < 0L || (ctx.targetTick >= 0L && actualTick > ctx.targetTick));

        if (COMMAND_QUEUE_FLUSH.equals(ctx.command)) {
            ClientPacketListener nh = mc.getConnection();
            if (nh != null) {
                int count = AutismSharedState.get().flushDelayedPackets(nh);
                AutismClientAddon.LOG.info("[Sync] Queue flushed! {} packets sent", count);
            }
        } else if (ctx.command != null && ctx.command.startsWith(COMMAND_DELAY_PACKETS)) {
            boolean enabled = "1".equals(ctx.command.substring(COMMAND_DELAY_PACKETS.length()));
            AutismModule module = AutismModule.get();
            int flushed = module.applyDelayGuiPacketsUiBehavior(enabled);
            module.notifyDelayPacketsUiResult(enabled, flushed);
            AutismClientAddon.LOG.info("[Sync] Delay packets set to {} (flushed={})", enabled, flushed);
        } else if (ctx.command != null && ctx.command.startsWith("__CHAT__\t")) {
            String chatMsg = ctx.command.substring("__CHAT__\t".length());
            ClientPacketListener nh = mc.getConnection();
            if (nh != null) {
                if (chatMsg.startsWith("/")) {
                    nh.sendCommand(chatMsg.substring(1));
                } else {
                    nh.sendChat(chatMsg);
                }
                AutismClientAddon.LOG.info("[Sync] Chat sent: {}", chatMsg);
            }
        } else if (ctx.isMacro && ctx.macro != null) {
            ctx.macro.execute(false);
        }

        AutismClientAddon.LOG.info("[Sync] EXECUTED at {} tick {}", executedAtMs, actualTick);

        executionTimestamps.put(myUsername, executedAtMs);
        ackReceiptTimes.put(myUsername, System.nanoTime());
        if (tickMode) {
            tickExecutionReports.put(myUsername, new TickExecutionReport(actualTick, late));
        }
        sendTcpPacket(new LanPacket.TcpCommandAckPacket(
            sessionId,
            myUsername,
            executedAtMs,
            ctx.executionMethod.ordinal(),
            ctx.targetTick,
            actualTick,
            late
        ));

        setSyncState(SyncState.REPORTING);

        if (ctx.isInitiator) {
            new Thread(() -> {
                try {

                    int expected = connectedClients.size();
                    long start = System.currentTimeMillis();
                    while (getReportCount(ctx.executionMethod) < expected && System.currentTimeMillis() - start < 1500) {
                        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    }
                    if (ctx.executionMethod == ExecutionMethod.TICK) {
                        calculateAndDisplayTickSpread(ctx.targetTick);
                    } else {
                        calculateAndDisplaySpread();
                    }
                } catch (Exception e) {
                    AutismClientAddon.LOG.error("[Sync] Error calculating spread", e);
                }
            }, "Sync-SpreadCalc") {{ setDaemon(true); }}.start();
        }
    }

    private void handleExecutionTimestamp(String username, long timestampMs, int methodOrdinal, long targetTick, long actualTick, boolean late) {
        executionTimestamps.put(username, timestampMs);

        ackReceiptTimes.put(username, System.nanoTime());
        ExecutionMethod method = executionMethodFromOrdinal(methodOrdinal);
        if (method == ExecutionMethod.TICK) {
            tickExecutionReports.put(username, new TickExecutionReport(actualTick, late || (targetTick >= 0L && actualTick > targetTick)));
        }
        AutismClientAddon.LOG.info("[Sync] ACK from {}: remoteMs={}, hostReceiptNano={}, tick={}", username, timestampMs, ackReceiptTimes.get(username), actualTick);
    }

    private long getCurrentGameTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1L;
        if (mc.isSameThread()) {
            observedGameTick = mc.level.getGameTime();
        }
        return observedGameTick >= 0L ? observedGameTick : mc.level.getGameTime();
    }

    private void scheduleTickExecution(long executionId, long targetTick) {
        SyncContext ctx = activeSyncCtx;
        if (ctx == null || ctx.executionId != executionId) return;
        ctx.targetTick = targetTick;
        long currentTick = getCurrentGameTick();
        if (currentTick < 0L || currentTick >= targetTick) {
            scheduledTickExecution = null;
            executeAction();
            return;
        }

        ScheduledTickExecution sched = new ScheduledTickExecution(executionId, targetTick);

        if (autismclient.util.macro.ServerTickTracker.isReady()) {
            long ticksOut = targetTick - currentTick;
            long now = System.nanoTime();
            long nextTickNano = autismclient.util.macro.ServerTickTracker.getNextServerTickNanos();

            long targetBoundaryNano = nextTickNano + (Math.max(0, ticksOut - 1) * 50_000_000L);
            int pingMs = autismclient.util.macro.ServerTickTracker.getPingMs();
            long halfPingNano = (pingMs * 1_000_000L) / 2L;

            long fireAt = targetBoundaryNano - halfPingNano;

            if (fireAt <= now) fireAt = now;
            sched.targetWallNanos = fireAt;
            final long fireAtFinal = fireAt;
            final long execId = executionId;
            Thread t = new Thread(() -> {
                try { Thread.currentThread().setPriority(Thread.MAX_PRIORITY); } catch (Throwable ignored) {  }
                long parkUntil = fireAtFinal - 1_000_000L;
                long nowNano;
                while ((nowNano = System.nanoTime()) < parkUntil) {
                    java.util.concurrent.locks.LockSupport.parkNanos(parkUntil - nowNano);
                }
                while (System.nanoTime() < fireAtFinal) {  }
                SyncContext sc = activeSyncCtx;
                if (sc == null || sc.executionId != execId || sc.executionMethod != ExecutionMethod.TICK) return;
                ScheduledTickExecution s = scheduledTickExecution;
                if (s == null || s.executionId != execId) return;
                scheduledTickExecution = null;
                setSyncState(SyncState.EXECUTING);
                executeAction();
            }, "Sync-TickPrecise-" + executionId);
            sched.preciseScheduler = t;
            t.setDaemon(true);
            t.start();
        }

        scheduledTickExecution = sched;
    }

    public void onLevelTick(long currentTick) {
        observedGameTick = currentTick;
        ScheduledTickExecution scheduled = scheduledTickExecution;
        if (scheduled == null || currentTick < scheduled.targetTick) return;
        SyncContext ctx = activeSyncCtx;
        if (ctx == null || ctx.executionId != scheduled.executionId || ctx.executionMethod != ExecutionMethod.TICK) {
            scheduledTickExecution = null;
            return;
        }
        ctx.targetTick = scheduled.targetTick;
        scheduledTickExecution = null;
        setSyncState(SyncState.EXECUTING);
        executeAction();
    }

    private int getReportCount(ExecutionMethod method) {
        return method == ExecutionMethod.TICK ? tickExecutionReports.size() : ackReceiptTimes.size();
    }

    private ExecutionMethod executionMethodFromOrdinal(int ordinal) {
        ExecutionMethod[] values = ExecutionMethod.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ExecutionMethod.INSTANT;
    }

    private ExecutionMethod executionMethodFromName(String name) {
        if (name == null) return ExecutionMethod.INSTANT;
        try {
            return ExecutionMethod.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ExecutionMethod.INSTANT;
        }
    }

    private void calculateAndDisplaySpread() {

        Map<String, Long> timings = ackReceiptTimes.isEmpty() ? executionTimestamps : ackReceiptTimes;

        if (timings.size() < 2) {
            setSyncState(SyncState.DONE);
            return;
        }

        long earliest = Long.MAX_VALUE;
        long latest = Long.MIN_VALUE;
        String earliestClient = "";
        String latestClient = "";

        for (Map.Entry<String, Long> entry : timings.entrySet()) {
            long ts = entry.getValue();
            if (ts < earliest) { earliest = ts; earliestClient = entry.getKey(); }
            if (ts > latest) { latest = ts; latestClient = entry.getKey(); }
        }

        boolean isNanos = !ackReceiptTimes.isEmpty();
        long spreadNanos = latest - earliest;
        long totalSpreadMs = isNanos ? spreadNanos / 1_000_000 : (latest - earliest);

        lastSpreadResult = new SpreadResult(isNanos ? spreadNanos : totalSpreadMs * 1_000_000, latestClient, timings.size());

        spreadHistory.add(lastSpreadResult);
        while (spreadHistory.size() > MAX_SPREAD_HISTORY) {
            spreadHistory.remove(0);
        }

        String report;
        if (totalSpreadMs == 0) {
            report = "§aPerfect sync!";
        } else {
            report = "§e" + latestClient + " §7was §c+" + totalSpreadMs + "ms §7late";
        }

        AutismClientMessaging.sendPrefixed(report);

        sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, "__SYNC__", report));

        sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, "__SPREAD_DATA__",
            spreadNanos + "\t" + latestClient + "\t" + timings.size()));

        broadcastSyncState(SyncState.DONE, totalSpreadMs + "ms spread");
        setSyncState(SyncState.DONE);
        fireCallback(onSpreadCalculated);

        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {  }
            if (syncState == SyncState.DONE) setSyncState(SyncState.IDLE);
        }, "Sync-ResetIdle") {{ setDaemon(true); }}.start();
    }

    private void calculateAndDisplayTickSpread(long targetTick) {
        int expected = connectedClients.size();
        java.util.TreeSet<String> peers = new java.util.TreeSet<>(connectedClients.keySet());
        boolean perfect = targetTick >= 0L && tickExecutionReports.size() >= expected;
        int maxOffset = 0;
        List<String> offPeers = new ArrayList<>();

        for (String peer : peers) {
            TickExecutionReport report = tickExecutionReports.get(peer);
            if (report == null) {
                perfect = false;
                offPeers.add(peer + " no report");
                continue;
            }
            long deltaLong = targetTick >= 0L ? report.actualTick - targetTick : 0L;
            int delta = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, deltaLong));
            maxOffset = Math.max(maxOffset, Math.abs(delta));
            if (delta != 0 || report.late) {
                perfect = false;
                String direction = delta >= 0 ? "+" : "";
                String timing = delta >= 0 ? "late" : "early";
                offPeers.add(peer + " was " + direction + delta + " tick(s) " + timing + " on tick " + report.actualTick + ", target " + targetTick);
            }
        }

        String report;
        if (perfect) {
            report = "§aPerfect sync on server tick " + targetTick;
        } else {
            report = "§eTick sync target " + targetTick + ": §c" + String.join("; ", offPeers);
        }

        lastSpreadResult = new SpreadResult(maxOffset * 50_000_000L, perfect ? "" : "tick", Math.max(tickExecutionReports.size(), expected),
            ExecutionMethod.TICK, targetTick, maxOffset, perfect, report);
        spreadHistory.add(lastSpreadResult);
        while (spreadHistory.size() > MAX_SPREAD_HISTORY) {
            spreadHistory.remove(0);
        }

        AutismClientMessaging.sendPrefixed(report);

        sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, "__SYNC__", report));
        sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, "__TICK_SPREAD_DATA__",
            targetTick + "\t" + maxOffset + "\t" + Math.max(tickExecutionReports.size(), expected) + "\t" + perfect + "\t" + report));

        broadcastSyncState(SyncState.DONE, perfect ? "tick perfect" : maxOffset + " tick offset");
        setSyncState(SyncState.DONE);
        fireCallback(onSpreadCalculated);

        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {  }
            if (syncState == SyncState.DONE) setSyncState(SyncState.IDLE);
        }, "Sync-ResetIdle") {{ setDaemon(true); }}.start();
    }

    public void sendQueuedPackets() {
        if (!runtimeExecutionAvailable()) return;
        if (sessionId == null) {
            AutismClientMessaging.sendPrefixed("§cJoin a session first.");
            return;
        }
        AutismSharedState.get().regenerateQueue();

        if (isHost) {
            initiateGoSyncForQueue();
        } else {

            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(sessionId, myUsername, false, "", COMMAND_QUEUE_FLUSH);
            request.executionMethod = executionMethod.ordinal();
            sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync execution from host...");
        }
    }

    public void setDelayPacketsSynchronized(boolean enabled) {
        if (!runtimeExecutionAvailable()) return;
        if (sessionId == null) {
            AutismClientMessaging.sendPrefixed("§cJoin a session first.");
            return;
        }

        String command = COMMAND_DELAY_PACKETS + (enabled ? "1" : "0");
        if (isHost) {
            initiateGoSync(false, "", command);
        } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(sessionId, myUsername, false, "", command);
            request.executionMethod = executionMethod.ordinal();
            sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync delay toggle from host...");
        }
    }

    public void executeMacroSynchronized(String macroName) {
        if (!runtimeExecutionAvailable()) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync execution requires a connected world.");
            return;
        }
        if (sessionId == null) {
            AutismClientMessaging.sendPrefixed("§cJoin a session first.");
            return;
        }
        if (!syncActionIdle()) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync is busy with another synchronized action.");
            return;
        }

        if (isHost) {
            initiateGoSync(true, macroName, "");
        } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(sessionId, myUsername, true, macroName, "");
            request.executionMethod = executionMethod.ordinal();
            sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync execution from host...");
        }
    }

    public void executeMacrosSynchronized(Map<String, String> assignments) {
        if (!runtimeExecutionAvailable()) return;
        if (sessionId == null) {
            AutismClientMessaging.sendPrefixed("§cJoin a session first.");
            return;
        }
        if (assignments == null || assignments.isEmpty()) return;

        String defaultMacro = assignments.values().iterator().next();

        if (isHost) {
            initiateGoSync(true, defaultMacro, "", assignments);
        } else {

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : assignments.entrySet()) {
                if (sb.length() > 0) sb.append('\t');
                sb.append(entry.getKey()).append('\t').append(entry.getValue());
            }
            LanPacket.RequestSyncPacket rsp = new LanPacket.RequestSyncPacket(
                sessionId, myUsername, true, defaultMacro, "__PER_USER__\t" + sb);
            rsp.executionMethod = executionMethod.ordinal();
            sendTcpPacket(rsp);
            AutismClientMessaging.sendPrefixed("§eRequested per-user sync from host...");
        }
    }

    public static class SpreadResult {
        public final long spreadNanos;
        public final String lateClient;
        public final int clientCount;
        public final ExecutionMethod executionMethod;
        public final long targetTick;
        public final int maxTickOffset;
        public final boolean perfectTickSync;
        public final String tickReport;

        public SpreadResult(long spreadNanos, String lateClient, int clientCount) {
            this(spreadNanos, lateClient, clientCount, ExecutionMethod.INSTANT, -1L, 0, false, "");
        }

        public SpreadResult(long spreadNanos, String lateClient, int clientCount, ExecutionMethod executionMethod,
                            long targetTick, int maxTickOffset, boolean perfectTickSync, String tickReport) {
            this.spreadNanos = spreadNanos;
            this.lateClient = lateClient;
            this.clientCount = clientCount;
            this.executionMethod = executionMethod != null ? executionMethod : ExecutionMethod.INSTANT;
            this.targetTick = targetTick;
            this.maxTickOffset = maxTickOffset;
            this.perfectTickSync = perfectTickSync;
            this.tickReport = tickReport != null ? tickReport : "";
        }

        public double getSpreadMs() { return spreadNanos / 1_000_000.0; }
    }

    public void startSearching() {
        if (!running.get()) {
            AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
            return;
        }
        if (isInSession()) {
            AutismClientMessaging.sendPrefixed("§cAlready in session.");
            return;
        }
        if (myUsername.isEmpty()) myUsername = getUsername();

        discoveredSessions.clear();
        isSearching = true;
        searchTicksRemaining = SEARCH_DURATION_TICKS;

        new Thread(() -> {
            while (isSearching && searchTicksRemaining > 0) {
                try {
                    Socket searchSocket = new Socket();
                    searchSocket.connect(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), TCP_PORT), 500);
                    searchSocket.setTcpNoDelay(true);

                    DataOutputStream out = new DataOutputStream(searchSocket.getOutputStream());
                    DataInputStream in = new DataInputStream(searchSocket.getInputStream());

                    out.writeInt(LanPacketType.SEARCH_REQUEST.getId());
                    out.writeUTF("");
                    out.flush();

                    int typeId = in.readInt();
                    String foundSessionId = in.readUTF();

                    if (typeId == LanPacketType.SESSION.getId()) {
                        LanPacket.SessionPacket session = new LanPacket.SessionPacket();
                        session.setSessionId(foundSessionId);
                        session.read(in);
                        discoveredSessions.put(foundSessionId, session.hostName);
                    }
                    searchSocket.close();
                    Thread.sleep(1000);
                } catch (IOException e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {  }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TCP-Search-Loop") {{ setDaemon(true); }}.start();
    }

    public void cancelSearch() {
        isSearching = false;
        searchTicksRemaining = 0;
    }

    public void tick() {
        if (!running.get() && sessionId == null) return;

        if (sessionId != null) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastHeartbeatSentMs >= HEARTBEAT_INTERVAL_MS) {
                lastHeartbeatSentMs = nowMs;
                sendTcpPacket(new LanPacket.HeartbeatPacket(sessionId, myUsername, nowMs));
                checkPeerHealth();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long currentTick = mc.level.getGameTime();

        if (sessionId != null && currentTick % 40 == 10) {
            checkAndBroadcastMacroListChanges();
        }

        if (sessionId != null && !connectedClients.isEmpty() && currentTick % 600 == 37) {
            broadcastMacroList();
        }

        if (isSearching && searchTicksRemaining > 0) {
            searchTicksRemaining--;

            if (!discoveredSessions.isEmpty()) {
                String firstSessionId = discoveredSessions.keySet().iterator().next();
                String hostName = discoveredSessions.get(firstSessionId);
                isSearching = false;
                searchTicksRemaining = 0;
                joinSession(firstSessionId);
                AutismClientMessaging.sendPrefixed("§aJoined session " + firstSessionId + " (Host: " + hostName + ").");
                return;
            }

            if (searchTicksRemaining == 0) {
                isSearching = false;
                AutismClientMessaging.sendPrefixed("§cNo sessions found.");
            }
        }
    }

    private void checkPeerHealth() {
        long now = System.currentTimeMillis();
        List<String> deadPeers = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastHeartbeatTime.entrySet()) {
            String peer = entry.getKey();
            if (peer.equals(myUsername)) continue;
            long elapsed = now - entry.getValue();

            if (elapsed > HEARTBEAT_DEAD_MS) {
                deadPeers.add(peer);
            }
        }

        for (String dead : deadPeers) {
            connectedClients.remove(dead);
            lastHeartbeatTime.remove(dead);
            clientMacroLists.remove(dead);
            remoteMeteorMacros.remove(dead);
            peerStepProgress.remove(dead);

            if (isHost) {
                Socket s = tcpClients.remove(dead);
                if (s != null) try { s.close(); } catch (IOException ignored) {  }
                tcpClientWriteLocks.remove(dead);
            }

            AutismClientMessaging.sendPrefixed("§e" + dead + " timed out.");
            AutismClientAddon.LOG.info("[Heartbeat] Peer timed out: {}", dead);
            fireCallback(onClientLeft);
            fireCallback(onPeerStatusChanged);
            autismclient.util.macro.MacroConditionRegistry.onLanStepProgress();
        }

        if (isHost && !deadPeers.isEmpty()) {
            broadcastClientList();
        }
    }

    public PeerStatus getPeerStatus(String username) {
        if (username.equals(myUsername)) return PeerStatus.HEALTHY;
        Long lastBeat = lastHeartbeatTime.get(username);
        if (lastBeat == null) return PeerStatus.UNKNOWN;
        long elapsed = System.currentTimeMillis() - lastBeat;
        if (elapsed > HEARTBEAT_STALE_MS) return PeerStatus.STALE;
        return PeerStatus.HEALTHY;
    }

    public enum PeerStatus { HEALTHY, STALE, UNKNOWN }

    private void handlePacket(LanPacket packet) {
        try {
            switch (packet.getType()) {
                case SEARCH_REQUEST:
                    break;

                case SESSION:
                    if (packet instanceof LanPacket.SessionPacket) {
                        LanPacket.SessionPacket p = (LanPacket.SessionPacket) packet;
                        String sid = p.getSessionId();
                        String host = p.hostName;
                        if (!host.equals(myUsername) && !isInSession()) {
                            boolean isNew = !discoveredSessions.containsKey(sid);
                            discoveredSessions.put(sid, host);
                            if (isNew) {
                                AutismClientMessaging.sendPrefixed("§eFound session " + sid + " (Host: " + host + ").");
                            }
                        }
                    }
                    break;

                case JOIN:
                    if (packet instanceof LanPacket.JoinPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.JoinPacket p = (LanPacket.JoinPacket) packet;
                        String username = p.username;
                        if (username != null && !username.isEmpty()
                                && !username.equals(myUsername) && !connectedClients.containsKey(username)) {
                            connectedClients.put(username, new ClientInfo(username, false));
                            lastHeartbeatTime.put(username, System.currentTimeMillis());
                            AutismClientMessaging.sendPrefixed("§e" + username + " joined.");
                            executionTimestamps.clear();
                            if (isHost) {
                                broadcastClientList();
                                broadcastMacroList();
                            }
                            fireCallback(onClientJoined);
                            fireCallback(onPeerStatusChanged);
                        }
                    }
                    break;

                case CLIENT_LIST:
                    if (packet instanceof LanPacket.ClientListPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.ClientListPacket p = (LanPacket.ClientListPacket) packet;
                        if (!isHost) {
                            ExecutionMethod hostMethod = executionMethodFromOrdinal(p.executionMethod);
                            if (executionMethod != hostMethod) {
                                executionMethod = hostMethod;
                                fireCallback(onPeerStatusChanged);
                            }
                        }
                        if (!p.clients.isEmpty()) {

                            ClientInfo ourInfo = connectedClients.get(myUsername);
                            long now = System.currentTimeMillis();
                            java.util.Set<String> listedPeers = new java.util.HashSet<>();
                            java.util.Set<String> listed = new java.util.HashSet<>();
                            for (LanPacket.ClientListPacket.ClientEntry entry : p.clients) {
                                connectedClients.put(entry.name, new ClientInfo(entry.name, entry.isHost));
                                listed.add(entry.name);
                                if (!entry.name.equals(myUsername)) {

                                    lastHeartbeatTime.put(entry.name, now);
                                    listedPeers.add(entry.name);
                                }
                            }

                            if (!listed.contains(myUsername) && ourInfo != null) {
                                connectedClients.put(myUsername, ourInfo);
                                listed.add(myUsername);
                            }
                            connectedClients.keySet().retainAll(listed);
                            lastHeartbeatTime.keySet().retainAll(listedPeers);
                        }
                    }
                    break;

                case LEAVE:
                    if (packet instanceof LanPacket.LeavePacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.LeavePacket p = (LanPacket.LeavePacket) packet;
                        String username = p.username;
                        if (username != null && !username.isEmpty() && !username.equals(myUsername)) {
                            ClientInfo leavingClient = connectedClients.get(username);
                            boolean leavingWasHost = leavingClient != null && leavingClient.isHost;
                            connectedClients.remove(username);
                            lastHeartbeatTime.remove(username);
                            clientMacroLists.remove(username);
                            remoteMeteorMacros.remove(username);
                            peerStepProgress.remove(username);
                            AutismClientMessaging.sendPrefixed("§e" + username + " left.");

                            if (isHost) {

                                Socket s = tcpClients.remove(username);
                                if (s != null) try { s.close(); } catch (IOException ignored) {  }
                                tcpClientWriteLocks.remove(username);

                                relayTcpPacketToClients(packet, username);
                                broadcastClientList();
                            }

                            if (leavingWasHost && !connectedClients.isEmpty()) {
                                String newHostUsername = connectedClients.keySet().stream()
                                    .sorted().findFirst().orElse(null);
                                if (newHostUsername != null && newHostUsername.equals(myUsername)) {
                                    isHost = true;
                                    connectedClients.put(myUsername, new ClientInfo(myUsername, true));
                                    AutismClientMessaging.sendPrefixed("§aYou are now the host!");
                                    executionTimestamps.clear();
                                    broadcastClientList();
                                }
                            }
                            fireCallback(onClientLeft);
                            fireCallback(onPeerStatusChanged);
                            autismclient.util.macro.MacroConditionRegistry.onLanStepProgress();
                        }
                    }
                    break;

                case PLAYER_OFFSETS:

                    break;

                case CLIENT_OFFSET_UPDATE:

                    break;

                case REQUEST_CLIENT_LIST:
                    if (isHost) broadcastClientList();
                    break;

                case REQUEST_MACRO:
                    handleMacroRequest(packet);
                    break;

                case MACRO_DATA:
                    handleMacroData(packet);
                    break;

                case MACRO_DATA_CHUNK:
                    handleMacroDataChunk(packet);
                    break;

                case MACRO_DELETE:
                    handleMacroDelete(packet);
                    break;

                case MACRO_LIST:
                    handleMacroList(packet);
                    break;

                case PRESET_DATA:
                    handlePresetData(packet);
                    break;

                case QUEUE_SYNC:
                    if (packet instanceof LanPacket.QueueSyncPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.QueueSyncPacket qp = (LanPacket.QueueSyncPacket) packet;
                        handleQueueSync(qp.queueData, qp.senderUsername);
                    }
                    break;

                case CHAT_MESSAGE:
                    if (packet instanceof LanPacket.ChatMessagePacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.ChatMessagePacket cp = (LanPacket.ChatMessagePacket) packet;
                        handleChatMessage(cp.senderUsername, cp.message);
                    }
                    break;

                case PREPARE_EXECUTION:
                    if (packet instanceof LanPacket.PrepareExecutionPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.PrepareExecutionPacket p = (LanPacket.PrepareExecutionPacket) packet;
                        handlePrepare(p.executionId, p.isMacro, p.targetName, p.queueData, p.senderUsername, p.macroAssignments, p.executionMethod, p.targetTick);
                    }
                    break;

                case CLIENT_READY:
                    if (packet instanceof LanPacket.ClientReadyPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.ClientReadyPacket p = (LanPacket.ClientReadyPacket) packet;
                        handleClientReady(p.executionId, p.senderUsername);
                    }
                    break;

                case EXECUTE_NOW:

                    break;

                case GO:
                    if (packet instanceof LanPacket.GoPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.GoPacket p = (LanPacket.GoPacket) packet;
                        handleGo(p.executionId, p.executionMethod, p.targetTick);
                    }
                    break;

                case TCP_COMMAND_ACK:
                    if (packet instanceof LanPacket.TcpCommandAckPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.TcpCommandAckPacket p = (LanPacket.TcpCommandAckPacket) packet;
                        handleExecutionTimestamp(p.senderUsername, p.executeNanoTime, p.executionMethod, p.targetTick, p.actualTick, p.late);
                    }
                    break;

                case HEARTBEAT:
                    if (packet instanceof LanPacket.HeartbeatPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.HeartbeatPacket p = (LanPacket.HeartbeatPacket) packet;
                        if (!p.senderUsername.equals(myUsername)) {
                            lastHeartbeatTime.put(p.senderUsername, System.currentTimeMillis());

                            if (isHost) {
                                relayTcpPacketToClients(packet, p.senderUsername);
                            }
                        }
                    }
                    break;

                case REQUEST_SYNC:
                    if (packet instanceof LanPacket.RequestSyncPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.RequestSyncPacket p = (LanPacket.RequestSyncPacket) packet;
                        handleRequestSync(p.senderUsername, p.isMacro, p.macroName, p.command);
                    }
                    break;

                case OFFSET_SYNC:
                    if (packet instanceof LanPacket.OffsetSyncPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.OffsetSyncPacket p = (LanPacket.OffsetSyncPacket) packet;
                        handleOffsetSync(p.senderUsername, p.targetUsername, p.offsetMs);
                    }
                    break;

                case MACRO_STEP_PROGRESS:
                    if (packet instanceof LanPacket.MacroStepProgressPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.MacroStepProgressPacket p = (LanPacket.MacroStepProgressPacket) packet;
                        handleStepProgress(p.senderUsername, p.completedStep, p.totalSteps, p.macroName);
                    }
                    break;

                case SYNC_STATE_UPDATE:
                    if (packet instanceof LanPacket.SyncStateUpdatePacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.SyncStateUpdatePacket p = (LanPacket.SyncStateUpdatePacket) packet;
                        SyncState[] states = SyncState.values();
                        if (p.stateOrdinal >= 0 && p.stateOrdinal < states.length) {

                            SyncContext ctx = activeSyncCtx;
                            if (ctx == null || !ctx.isInitiator) {
                                syncState = states[p.stateOrdinal];
                                fireCallback(onSyncStateChanged);
                            }
                        }
                    }
                    break;

                case MACRO_ASSIGNMENT_SYNC:
                    if (packet instanceof LanPacket.MacroAssignmentSyncPacket && packet.getSessionId().equals(sessionId)) {
                        LanPacket.MacroAssignmentSyncPacket p = (LanPacket.MacroAssignmentSyncPacket) packet;
                        if (!p.senderUsername.equals(myUsername)) {
                            handleAssignmentSync(p.senderUsername, p.assignments);
                        }
                    }
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism-LAN] Error handling packet: {}", packet.getType(), e);
        }
    }

    private void startTcpServer() {
        try {

            tcpServer = new ServerSocket();
            tcpServer.setReuseAddress(true);

            tcpServer.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), TCP_PORT));

            tcpAcceptThread = new Thread(() -> {
                while (running.get() && isHost && tcpServer != null && !tcpServer.isClosed()) {
                    try {
                        Socket clientSocket = tcpServer.accept();
                        clientSocket.setTcpNoDelay(true);
                        handleNewTcpClient(clientSocket);
                    } catch (IOException e) {
                        if (running.get() && isHost) {
                            AutismClientAddon.LOG.warn("[TCP] Accept error: {}", e.getMessage());
                        }
                    }
                }
            }, "TCP-Accept");
            tcpAcceptThread.setDaemon(true);
            tcpAcceptThread.start();

            AutismClientAddon.LOG.info("[TCP] Server started on port {}", TCP_PORT);
        } catch (IOException e) {
            AutismClientAddon.LOG.error("[TCP] Failed to start server: {}", e.getMessage());
        }
    }

    private void handleNewTcpClient(Socket clientSocket) {
        new Thread(() -> {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                int packetType = in.readInt();
                String packetSession = in.readUTF();

                if (packetType == LanPacketType.SEARCH_REQUEST.getId()) {
                    AutismClientAddon.LOG.info("[TCP] Received search request, responding with session info");
                    out.writeInt(LanPacketType.SESSION.getId());
                    out.writeUTF(sessionId);
                    new LanPacket.SessionPacket(sessionId, 0, myUsername).write(out);
                    out.flush();
                    clientSocket.close();
                    return;
                }

                if (packetType == LanPacketType.JOIN.getId()) {
                    LanPacket.JoinPacket joinPacket = new LanPacket.JoinPacket();
                    joinPacket.setSessionId(packetSession);
                    joinPacket.read(in);

                    String clientUsername = joinPacket.username;

                    if (clientUsername == null || clientUsername.isBlank()) {
                        AutismClientAddon.LOG.warn("[TCP] Rejected client with empty username");
                        clientSocket.close();
                        return;
                    }

                    if (sessionId == null || !sessionId.equals(packetSession)) {
                        AutismClientAddon.LOG.warn("[TCP] Rejected client '{}' with wrong/missing session code", clientUsername);
                        clientSocket.close();
                        return;
                    }

                    Socket oldSocket = tcpClients.put(clientUsername, clientSocket);
                    if (oldSocket != null && oldSocket != clientSocket) {
                        try { oldSocket.close(); } catch (IOException ignored) {  }
                    }
                    tcpClientWriteLocks.put(clientUsername, new Object());
                    lastHeartbeatTime.put(clientUsername, System.currentTimeMillis());

                    if (!clientUsername.equals(myUsername) && !connectedClients.containsKey(clientUsername)) {
                        connectedClients.put(clientUsername, new ClientInfo(clientUsername, false));
                        AutismClientMessaging.sendPrefixed("§e" + clientUsername + " joined.");
                        executionTimestamps.clear();

                        broadcastClientList();

                        fireCallback(onClientJoined);
                        fireCallback(onPeerStatusChanged);
                    }

                    if (!clientUsername.equals(myUsername)) {
                        sendMacroListToClient(clientSocket, clientUsername);
                        sendKnownPeerRostersToClient(clientSocket, clientUsername);
                    }

                    sendClientListToClient(clientSocket, clientUsername);

                    AutismClientAddon.LOG.info("[TCP] Client joined: {}", clientUsername);

                    while (running.get() && !clientSocket.isClosed()) {
                        int nextPacketType = in.readInt();
                        String nextPacketSession = in.readUTF();

                        LanPacketType type = LanPacketType.fromId(nextPacketType);
                        LanPacket packet = type == null ? null : LanPacket.create(type, nextPacketSession);
                        if (packet == null) {

                            throw new IOException("Unknown LAN packet id " + nextPacketType);
                        }
                        packet.read(in);
                        handlePacket(packet);
                    }
                }
            } catch (Exception e) {  } finally {
                try { clientSocket.close(); } catch (IOException ignored) {  }

                if (running.get() && isHost) {
                    String disconnectedUser = null;
                    for (Map.Entry<String, Socket> entry : tcpClients.entrySet()) {
                        if (entry.getValue() == clientSocket) {
                            disconnectedUser = entry.getKey();
                            break;
                        }
                    }

                    if (disconnectedUser != null) {
                        tcpClients.remove(disconnectedUser);
                        tcpClientWriteLocks.remove(disconnectedUser);
                        connectedClients.remove(disconnectedUser);
                        clientMacroLists.remove(disconnectedUser);
                        remoteMeteorMacros.remove(disconnectedUser);
                        lastHeartbeatTime.remove(disconnectedUser);
                        peerStepProgress.remove(disconnectedUser);

                        AutismClientMessaging.sendPrefixed("§e" + disconnectedUser + " disconnected.");
                        AutismClientAddon.LOG.info("[TCP] Client disconnected: {}", disconnectedUser);

                        broadcastClientList();
                        fireCallback(onClientLeft);

                        autismclient.util.macro.MacroConditionRegistry.onLanStepProgress();
                    }
                }
            }
        }, "TCP-Client-Handler") {{ setDaemon(true); }}.start();
    }

    private void sendClientListToClient(Socket clientSocket, String clientUsername) {
        try {
            List<LanPacket.ClientListPacket.ClientEntry> entries = new ArrayList<>();
            for (Map.Entry<String, ClientInfo> entry : connectedClients.entrySet()) {
                entries.add(new LanPacket.ClientListPacket.ClientEntry(entry.getKey(), entry.getValue().isHost));
            }

            LanPacket.ClientListPacket packet = new LanPacket.ClientListPacket(sessionId, entries, executionMethod.ordinal());
            byte[] data = serializePacket(packet);

            Object lock = tcpClientWriteLocks.computeIfAbsent(clientUsername, k -> new Object());
            synchronized (lock) {
                clientSocket.getOutputStream().write(data);
                clientSocket.getOutputStream().flush();
            }
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to send client list: {}", e.getMessage());
        }
    }

    private void sendMacroListToClient(Socket clientSocket, String clientUsername) {
        try {
            List<AutismMacro> localMacros = AutismMacroManager.get().getAll();
            List<AutismMacro> meteorMacros = MeteorMacroAdapter.getMeteorMacros();
            Object lock = tcpClientWriteLocks.computeIfAbsent(clientUsername, k -> new Object());
            OutputStream out = clientSocket.getOutputStream();

            for (AutismMacro macro : localMacros) {
                writeMacroDataPacketsToStream(out, lock, myUsername, macro, (byte)0);
            }
            for (AutismMacro macro : meteorMacros) {
                writeMacroDataPacketsToStream(out, lock, myUsername, macro, (byte)1);
            }
            writePacketToStream(out, lock,
                new LanPacket.MacroListPacket(sessionId, myUsername, macroNamesFor(localMacros, meteorMacros)));
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to send macro list: {}", e.getMessage());
        }
    }

    private void sendKnownPeerRostersToClient(Socket clientSocket, String clientUsername) {
        String session = sessionId;
        if (session == null) return;
        try {
            Object lock = tcpClientWriteLocks.computeIfAbsent(clientUsername, k -> new Object());
            OutputStream out = clientSocket.getOutputStream();

            for (String peer : connectedClients.keySet()) {
                if (peer.equals(myUsername) || peer.equals(clientUsername)) continue;

                List<String> names = new ArrayList<>();
                Map<String, AutismMacro> peerMacros = clientMacroLists.get(peer);
                if (peerMacros != null) {
                    for (AutismMacro macro : peerMacros.values()) {
                        if (macro == null) continue;
                        writeMacroDataPacketsToStream(out, lock, peer, macro, (byte)0);
                        names.add(macro.name);
                    }
                }
                Map<String, AutismMacro> peerMeteor = remoteMeteorMacros.get(peer);
                if (peerMeteor != null) {
                    for (AutismMacro macro : peerMeteor.values()) {
                        if (macro == null) continue;
                        writeMacroDataPacketsToStream(out, lock, peer, macro, (byte)1);
                        names.add(macro.name);
                    }
                }
                if (!names.isEmpty()) {
                    writePacketToStream(out, lock, new LanPacket.MacroListPacket(session, peer, names));
                }
            }
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to forward peer rosters: {}", e.getMessage());
        }
    }

    private void connectToTcpServer(String hostIp) {
        new Thread(() -> {
            try {

                tcpHostSocket = new Socket();
                tcpHostSocket.connect(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), TCP_PORT), 5000);
                tcpHostSocket.setTcpNoDelay(true);
                DataOutputStream out = new DataOutputStream(tcpHostSocket.getOutputStream());

                LanPacket.JoinPacket joinPacket = new LanPacket.JoinPacket(sessionId, myUsername);
                synchronized (tcpHostWriteLock) {
                    out.writeInt(joinPacket.getType().getId());
                    out.writeUTF(joinPacket.getSessionId());
                    joinPacket.write(out);
                    out.flush();
                }

                AutismClientAddon.LOG.info("[TCP] Connected to host at {}:{}", hostIp, TCP_PORT);
                reconnecting = false;

                tcpReadThread = new Thread(() -> {
                    try {
                        DataInputStream in = new DataInputStream(tcpHostSocket.getInputStream());
                        while (running.get() && tcpHostSocket != null && !tcpHostSocket.isClosed()) {
                            int packetType = in.readInt();
                            String packetSession = in.readUTF();

                            LanPacketType type = LanPacketType.fromId(packetType);
                            LanPacket packet = type == null ? null : LanPacket.create(type, packetSession);
                            if (packet == null) {

                                throw new IOException("Unknown LAN packet id " + packetType);
                            }
                            packet.read(in);
                            handlePacket(packet);
                        }
                    } catch (Exception e) {
                        AutismClientAddon.LOG.warn("[TCP] Disconnected from host: {}", e.getMessage());
                        attemptReconnect();
                    }
                }, "TCP-Reader");
                tcpReadThread.setDaemon(true);
                tcpReadThread.start();

                sendTcpPacket(new LanPacket.RequestClientListPacket(sessionId));
                sendMacroListViaConnection(out);

            } catch (IOException e) {
                AutismClientAddon.LOG.warn("[TCP] Failed to connect to host: {}", e.getMessage());
                attemptReconnect();
            }
        }, "TCP-Connect") {{ setDaemon(true); }}.start();
    }

    private void attemptReconnect() {
        if (reconnecting || !running.get() || lastHostIp == null || lastSessionId == null) return;
        if (isHost) return;

        reconnecting = true;
        AutismClientMessaging.sendPrefixed("§eConnection lost. Reconnecting...");

        new Thread(() -> {
            long backoffMs = 1000;
            int attempt = 0;

            while (reconnecting && running.get() && lastHostIp != null) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    return;
                }

                if (!reconnecting || !running.get() || lastHostIp == null) return;

                attempt++;
                AutismClientAddon.LOG.info("[TCP] Reconnect attempt {}...", attempt);

                try {
                    Socket testSocket = new Socket();

                    testSocket.connect(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), TCP_PORT), 2000);
                    testSocket.setTcpNoDelay(true);
                    testSocket.close();

                    sessionId = lastSessionId;
                    if (myUsername.isEmpty()) myUsername = getUsername();
                    connectedClients.putIfAbsent(myUsername, new ClientInfo(myUsername, false));
                    connectToTcpServer(lastHostIp);
                    AutismClientMessaging.sendPrefixed("§aReconnected to session!");
                    return;
                } catch (IOException e) {
                    AutismClientAddon.LOG.warn("[TCP] Reconnect attempt {} failed", attempt);
                }

                backoffMs = Math.min(backoffMs * 2, 16_000);
            }

            reconnecting = false;
        }, "TCP-Reconnect") {{ setDaemon(true); }}.start();
    }

    private void sendMacroListViaConnection(DataOutputStream out) {
        try {
            List<AutismMacro> localMacros = AutismMacroManager.get().getAll();
            List<AutismMacro> meteorMacros = MeteorMacroAdapter.getMeteorMacros();
            synchronized (tcpHostWriteLock) {
                for (AutismMacro macro : localMacros) {
                    writeMacroDataPacketsToStream(out, null, myUsername, macro, (byte)0);
                }
                for (AutismMacro macro : meteorMacros) {
                    writeMacroDataPacketsToStream(out, null, myUsername, macro, (byte)1);
                }
                writePacketToStream(out, null,
                    new LanPacket.MacroListPacket(sessionId, myUsername, macroNamesFor(localMacros, meteorMacros)));
            }
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to send macro list: {}", e.getMessage());
        }
    }

    private byte[] serializePacket(LanPacket packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(packet.getType().getId());
        out.writeUTF(packet.getSessionId());
        packet.write(out);
        return baos.toByteArray();
    }

    private void writeMacroDataPacketsToStream(OutputStream out, Object lock, String senderUsername,
                                               AutismMacro macro, byte sourceType) throws IOException {
        if (macro == null) return;
        String nbtString = macro.toShareableTag().toString();
        List<LanPacket> packets = createMacroDataPackets(senderUsername, macro.name, nbtString, sourceType);
        for (LanPacket packet : packets) {
            writePacketToStream(out, lock, packet);
        }
    }

    private void writePacketToStream(OutputStream out, Object lock, LanPacket packet) throws IOException {
        byte[] data = serializePacket(packet);
        if (lock != null) {
            synchronized (lock) {
                out.write(data);
                out.flush();
            }
        } else {
            out.write(data);
            out.flush();
        }
    }

    private void sendTcpPacket(LanPacket packet) {
        if (PackHideState.isHardLocked()) return;
        try {
            byte[] data = serializePacket(packet);

            if (isHost) {
                for (Map.Entry<String, Socket> entry : tcpClients.entrySet()) {
                    Socket client = entry.getValue();
                    String clientName = entry.getKey();
                    try {
                        if (!client.isClosed()) {
                            Object lock = tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                            synchronized (lock) {
                                client.getOutputStream().write(data);
                                client.getOutputStream().flush();
                            }
                        }
                    } catch (IOException e) {  }
                }
            } else {
                synchronized (tcpHostWriteLock) {
                    if (tcpHostSocket != null && !tcpHostSocket.isClosed()) {
                        tcpHostSocket.getOutputStream().write(data);
                        tcpHostSocket.getOutputStream().flush();
                    }
                }
            }
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to send packet: {}", e.getMessage());
        }
    }

    private void relayTcpPacketToClients(LanPacket packet, String excludedUsername) {
        if (PackHideState.isHardLocked()) return;
        if (!isHost) return;
        try {
            byte[] data = serializePacket(packet);
            for (Map.Entry<String, Socket> entry : tcpClients.entrySet()) {
                String clientName = entry.getKey();
                if (excludedUsername != null && excludedUsername.equals(clientName)) continue;

                Socket client = entry.getValue();
                try {
                    if (!client.isClosed()) {
                        Object lock = tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                        synchronized (lock) {
                            client.getOutputStream().write(data);
                            client.getOutputStream().flush();
                        }
                    }
                } catch (IOException ignored) {  }
            }
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Failed to relay packet: {}", e.getMessage());
        }
    }

    private void stopTcp() {
        try {
            if (tcpServer != null) {
                tcpServer.close();
                tcpServer = null;
            }
            if (tcpHostSocket != null) {
                tcpHostSocket.close();
                tcpHostSocket = null;
            }
            for (Socket s : tcpClients.values()) {
                try { s.close(); } catch (IOException ignored) {  }
            }
            tcpClients.clear();
            tcpClientWriteLocks.clear();
            pendingMacroTransfers.clear();
        } catch (IOException e) {
            AutismClientAddon.LOG.warn("[TCP] Stop error: {}", e.getMessage());
        }
    }

    private void broadcastClientList() {
        if (!isHost) return;
        List<LanPacket.ClientListPacket.ClientEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : connectedClients.entrySet()) {
            entries.add(new LanPacket.ClientListPacket.ClientEntry(entry.getKey(), entry.getValue().isHost));
        }
        sendTcpPacket(new LanPacket.ClientListPacket(sessionId, entries, executionMethod.ordinal()));
    }

    private void broadcastSyncState(SyncState state, String detail) {
        if (isHost) {
            sendTcpPacket(new LanPacket.SyncStateUpdatePacket(sessionId, state.ordinal(), detail));
        }
    }

    private void checkAndBroadcastMacroListChanges() {
        try {
            Map<String, String> currentMeteorMacros = snapshotMeteorMacros();
            boolean changed = currentMeteorMacros.size() != lastBroadcastMeteorMacros.size();

            if (!changed) {
                for (Map.Entry<String, String> entry : currentMeteorMacros.entrySet()) {
                    String lastValue = lastBroadcastMeteorMacros.get(entry.getKey());
                    if (!entry.getValue().equals(lastValue)) {
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                for (String previousName : new java.util.ArrayList<>(lastBroadcastMeteorMacros.keySet())) {
                    if (!currentMeteorMacros.containsKey(previousName)) {
                        broadcastMacroDeletion(previousName);
                    }
                }
                lastBroadcastMeteorMacros = new ConcurrentHashMap<>(currentMeteorMacros);
                broadcastMacroList();
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism-LAN] Failed to check macro list changes", e);
        }
    }

    public void broadcastMacroList() {
        String session = sessionId;
        if (session == null) return;

        List<AutismMacro> localMacros = AutismMacroManager.get().getAll();
        List<AutismMacro> meteorMacros = MeteorMacroAdapter.getMeteorMacros();
        String sender = myUsername;

        List<LanPacket> packets = new ArrayList<>();
        for (AutismMacro macro : localMacros) {
            packets.addAll(macroDataPacketsFor(sender, macro, (byte)0));
        }
        for (AutismMacro macro : meteorMacros) {
            packets.addAll(macroDataPacketsFor(sender, macro, (byte)1));
        }

        packets.add(new LanPacket.MacroListPacket(session, sender, macroNamesFor(localMacros, meteorMacros)));

        sendTcpPacketsAsync(packets);

        lastBroadcastMeteorMacros = new ConcurrentHashMap<>(snapshotMeteorMacros());
    }

    private void sendTcpPacketsAsync(List<LanPacket> packets) {
        if (packets.isEmpty()) return;
        try {
            sendExecutor.execute(() -> {
                for (LanPacket packet : packets) sendTcpPacket(packet);
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {

        }
    }

    private static List<String> macroNamesFor(List<AutismMacro> local, List<AutismMacro> meteor) {
        List<String> names = new ArrayList<>(local.size() + meteor.size());
        for (AutismMacro macro : local) {
            if (macro != null && macro.name != null) names.add(macro.name);
        }
        for (AutismMacro macro : meteor) {
            if (macro != null && macro.name != null) names.add(macro.name);
        }
        return names;
    }

    private void sendMacroData(String senderUsername, AutismMacro macro, byte sourceType) {
        for (LanPacket packet : macroDataPacketsFor(senderUsername, macro, sourceType)) {
            sendTcpPacket(packet);
        }
    }

    private List<LanPacket> macroDataPacketsFor(String senderUsername, AutismMacro macro, byte sourceType) {
        if (macro == null || sessionId == null) return List.of();
        return createMacroDataPackets(senderUsername, macro.name, macro.toShareableTag().toString(), sourceType);
    }

    private List<LanPacket> createMacroDataPackets(String senderUsername, String macroName, String nbtData, byte sourceType) {
        String safeSender = senderUsername != null ? senderUsername : "";
        String safeName = macroName != null ? macroName : "";
        String safeNbt = nbtData != null ? nbtData : "";
        List<LanPacket> packets = new ArrayList<>();
        if (modifiedUtfLength(safeNbt) <= MACRO_INLINE_UTF_LIMIT) {
            packets.add(new LanPacket.MacroDataPacket(sessionId, safeSender, safeName, safeNbt, sourceType));
            return packets;
        }

        byte[] bytes = safeNbt.getBytes(StandardCharsets.UTF_8);
        int totalChunks = Math.max(1, (bytes.length + MACRO_CHUNK_SIZE - 1) / MACRO_CHUNK_SIZE);
        for (int i = 0; i < totalChunks; i++) {
            int start = i * MACRO_CHUNK_SIZE;
            int len = Math.min(MACRO_CHUNK_SIZE, bytes.length - start);
            byte[] chunk = new byte[len];
            System.arraycopy(bytes, start, chunk, 0, len);
            packets.add(new LanPacket.MacroDataChunkPacket(sessionId, safeSender, safeName, sourceType, i, totalChunks, chunk));
        }
        return packets;
    }

    private static int modifiedUtfLength(String value) {
        if (value == null) return 0;
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            int c = value.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                length += 1;
            } else if (c <= 0x07FF) {
                length += 2;
            } else {
                length += 3;
            }
        }
        return length;
    }

    private Map<String, String> snapshotMeteorMacros() {
        Map<String, String> snapshot = new java.util.LinkedHashMap<>();
        for (AutismMacro macro : MeteorMacroAdapter.getMeteorMacros()) {
            if (macro == null || macro.name == null || macro.name.isBlank()) continue;
            snapshot.put(macro.name, macro.toShareableTag().toString());
        }
        return snapshot;
    }

    private void handleMacroData(LanPacket packet) {
        handleMacroData(packet, true);
    }

    private void handleMacroData(LanPacket packet, boolean relayIfHost) {
        if (!(packet instanceof LanPacket.MacroDataPacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.MacroDataPacket p = (LanPacket.MacroDataPacket) packet;
        String sender = p.senderUsername;
        if (sender != null && sender.equals(myUsername)) return;

        try {
            net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(p.nbtData);
            AutismMacro macro = new AutismMacro();
            macro.fromTag(nbt);

            String relayNbt = macro.toTag().toString();

            if (p.sourceType == 1) {
                remoteMeteorMacros.computeIfAbsent(sender, k -> new ConcurrentHashMap<>())
                    .put(MacroNames.key(macro.name), macro);
            } else {
                clientMacroLists.computeIfAbsent(sender, k -> new ConcurrentHashMap<>())
                    .put(MacroNames.key(macro.name), macro);
            }

            if (p.sourceType == 2) {
                importSharedMacroIfMissing(macro, sender);
            }

            if (relayIfHost && isHost && sender != null && !sender.isBlank()) {

                relayTcpPacketToClients(new LanPacket.MacroDataPacket(sessionId, sender, macro.name, relayNbt, p.sourceType), sender);
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism-LAN] Failed to deserialize macro", e);
        }
    }

    private void handleMacroDataChunk(LanPacket packet) {
        if (!(packet instanceof LanPacket.MacroDataChunkPacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.MacroDataChunkPacket p = (LanPacket.MacroDataChunkPacket) packet;
        String sender = p.senderUsername;
        if (sender != null && sender.equals(myUsername)) return;
        if (p.totalChunks <= 0 || p.totalChunks > 4096 || p.chunkIndex < 0 || p.chunkIndex >= p.totalChunks) return;
        if (p.chunkData == null || p.chunkData.length == 0 || p.chunkData.length > MACRO_CHUNK_SIZE) return;

        if (isHost && sender != null && !sender.isBlank()) {
            relayTcpPacketToClients(new LanPacket.MacroDataChunkPacket(
                sessionId, sender, p.macroName, p.sourceType, p.chunkIndex, p.totalChunks, p.chunkData
            ), sender);
        }

        cleanupStaleMacroTransfers();
        String key = sender + "\u0000" + p.macroName + "\u0000" + p.sourceType + "\u0000" + p.totalChunks;
        PendingMacroTransfer transfer = pendingMacroTransfers.computeIfAbsent(key, ignored -> new PendingMacroTransfer(p.totalChunks));
        byte[] complete = transfer.addChunk(p.chunkIndex, p.chunkData);
        if (complete == null) return;

        pendingMacroTransfers.remove(key);
        String nbtData = new String(complete, StandardCharsets.UTF_8);
        handleMacroData(new LanPacket.MacroDataPacket(sessionId, sender, p.macroName, nbtData, p.sourceType), false);
    }

    private void cleanupStaleMacroTransfers() {
        long now = System.currentTimeMillis();
        pendingMacroTransfers.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs > MACRO_CHUNK_TIMEOUT_MS);
    }

    private void importSharedMacroIfMissing(AutismMacro macro, String sender) {
        if (macro == null || macro.name == null || macro.name.isBlank()) return;

        Minecraft.getInstance().execute(() -> applySharedMacro(macro, sender));
    }

    private void applySharedMacro(AutismMacro macro, String sender) {

        AutismMacroManager manager = AutismMacroManager.get();
        AutismMacro existing = manager.get(macro.name);
        if (existing != null) {

            AutismMacro fresh = macro.deepCopy(macro.name);
            existing.name = fresh.name;
            existing.description = fresh.description;
            existing.keyCode = fresh.keyCode;

            existing.actions = new java.util.ArrayList<>(fresh.actions);
            existing.loop = fresh.loop;
            existing.loopCount = fresh.loopCount;
            manager.save();
            AutismClientMessaging.sendPrefixed("§aUpdated macro from " + sender + ": " + existing.name);
            return;
        }

        AutismMacro installed = macro.deepCopy(macro.name);
        manager.add(installed);
        AutismClientMessaging.sendPrefixed("§aReceived macro from " + sender + ": " + installed.name);
    }

    private void handleMacroRequest(LanPacket packet) {
        if (!(packet instanceof LanPacket.RequestMacroPacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.RequestMacroPacket p = (LanPacket.RequestMacroPacket) packet;
        String requestedName = p.macroName == null ? "" : p.macroName.trim();
        if (requestedName.isEmpty()) return;

        AutismMacro macro = AutismMacroManager.get().get(requestedName);
        if (macro != null) {
            sendMacroData(myUsername, macro, (byte)2);
            return;
        }

        for (AutismMacro meteorMacro : MeteorMacroAdapter.getMeteorMacros()) {
            if (meteorMacro != null && meteorMacro.name != null && meteorMacro.name.equalsIgnoreCase(requestedName)) {
                sendMacroData(myUsername, meteorMacro, (byte)2);
                return;
            }
        }
    }

    private void handlePresetData(LanPacket packet) {
        if (!(packet instanceof LanPacket.PresetDataPacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.PresetDataPacket p = (LanPacket.PresetDataPacket) packet;
        if (p.senderUsername != null && p.senderUsername.equals(myUsername)) return;

        if (AutismPresetManager.get().importSharedPreset(p.presetName, p.presetJson, p.senderUsername)) {
            if (isHost && p.senderUsername != null && !p.senderUsername.isBlank()) {
                relayTcpPacketToClients(new LanPacket.PresetDataPacket(sessionId, p.senderUsername, p.presetName, p.presetJson), p.senderUsername);
            }
        }
    }

    private void handleMacroDelete(LanPacket packet) {
        if (!(packet instanceof LanPacket.MacroDeletePacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.MacroDeletePacket p = (LanPacket.MacroDeletePacket) packet;
        if (p.senderUsername != null && p.senderUsername.equals(myUsername)) return;

        Map<String, AutismMacro> senderMacros = clientMacroLists.get(p.senderUsername);
        if (senderMacros != null) {
            senderMacros.remove(MacroNames.key(p.macroName));
        }
        Map<String, AutismMacro> senderMeteor = remoteMeteorMacros.get(p.senderUsername);
        if (senderMeteor != null) {
            senderMeteor.remove(MacroNames.key(p.macroName));
        }

        if (isHost && p.senderUsername != null && !p.senderUsername.isBlank()) {
            relayTcpPacketToClients(new LanPacket.MacroDeletePacket(sessionId, p.senderUsername, p.macroName), p.senderUsername);
        }
    }

    private void handleMacroList(LanPacket packet) {
        if (!(packet instanceof LanPacket.MacroListPacket) || !packet.getSessionId().equals(sessionId)) return;
        LanPacket.MacroListPacket p = (LanPacket.MacroListPacket) packet;
        if (p.senderUsername == null || p.senderUsername.isBlank()) return;
        if (p.senderUsername.equals(myUsername)) return;

        java.util.Set<String> live = new java.util.HashSet<>();
        for (String name : p.macroNames) {
            live.add(MacroNames.key(name));
        }

        Map<String, AutismMacro> senderMacros = clientMacroLists.get(p.senderUsername);
        if (senderMacros != null) senderMacros.keySet().retainAll(live);
        Map<String, AutismMacro> senderMeteor = remoteMeteorMacros.get(p.senderUsername);
        if (senderMeteor != null) senderMeteor.keySet().retainAll(live);

        if (isHost) {
            relayTcpPacketToClients(
                new LanPacket.MacroListPacket(sessionId, p.senderUsername, new ArrayList<>(p.macroNames)),
                p.senderUsername);
        }
    }

    private void handleQueueSync(String queueData, String senderUsername) {
        try {
            if (senderUsername != null && senderUsername.equals(myUsername)) return;

            AutismSharedState.QueuedPacket.resetIdCounter();
            List<AutismSharedState.QueuedPacket> receivedQueue =
                AutismClipboardHelper.deserializeQueueFromBase64(queueData);

            if (receivedQueue == null || receivedQueue.isEmpty()) {
                AutismClientMessaging.sendPrefixed("§cReceived empty queue");
                return;
            }

            AutismSharedState.get().setDelayedPackets(receivedQueue);
            AutismClientMessaging.sendPrefixed("§aReceived queue: " + receivedQueue.size() + " packets");
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism-LAN] Failed to process queue sync", e);
            AutismClientMessaging.sendPrefixed("§cFailed to receive queue");
        }
    }

    private void handleChatMessage(String senderUsername, String message) {
        String safeSender = senderUsername == null || senderUsername.isBlank() ? "Peer" : senderUsername;
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) return;

        if ("__SYNC__".equals(safeSender)) {
            AutismClientMessaging.sendPrefixed(safeMessage);
            return;
        }

        if ("__STOP_ALL__".equals(safeSender)) {
            autismclient.util.macro.MacroExecutor.stop();
            AutismClientMessaging.sendPrefixed("§c[LAN] Macros stopped by " + safeMessage);
            return;
        }

        if ("__SPREAD_DATA__".equals(safeSender)) {
            try {
                String[] parts = safeMessage.split("\t");
                if (parts.length >= 3) {
                    long nanos = Long.parseLong(parts[0]);
                    String lateClient = parts[1];
                    int count = Integer.parseInt(parts[2]);
                    lastSpreadResult = new SpreadResult(nanos, lateClient, count);
                    spreadHistory.add(lastSpreadResult);
                    while (spreadHistory.size() > MAX_SPREAD_HISTORY) spreadHistory.remove(0);
                    fireCallback(onSpreadCalculated);
                }
            } catch (NumberFormatException ignored) {  }
            return;
        }

        if ("__TICK_SPREAD_DATA__".equals(safeSender)) {
            try {
                String[] parts = safeMessage.split("\t", 5);
                if (parts.length >= 5) {
                    long targetTick = Long.parseLong(parts[0]);
                    int maxOffset = Integer.parseInt(parts[1]);
                    int count = Integer.parseInt(parts[2]);
                    boolean perfect = Boolean.parseBoolean(parts[3]);
                    String tickReport = parts[4];
                    lastSpreadResult = new SpreadResult(maxOffset * 50_000_000L, perfect ? "" : "tick", count,
                        ExecutionMethod.TICK, targetTick, maxOffset, perfect, tickReport);
                    spreadHistory.add(lastSpreadResult);
                    while (spreadHistory.size() > MAX_SPREAD_HISTORY) spreadHistory.remove(0);
                    fireCallback(onSpreadCalculated);
                }
            } catch (NumberFormatException ignored) {  }
            return;
        }

        boolean isExecMethodControl = "__EXEC_METHOD__".equals(safeSender)
                || (safeMessage != null && safeMessage.startsWith("__EXEC_METHOD__:"));
        if (isExecMethodControl) {
            String originator;
            String modeName;
            if (safeMessage != null && safeMessage.startsWith("__EXEC_METHOD__:")) {
                originator = "__EXEC_METHOD__".equals(safeSender) ? "host" : safeSender;
                modeName = safeMessage.substring("__EXEC_METHOD__:".length());
            } else {

                originator = "host";
                modeName = safeMessage;
            }
            ExecutionMethod method = executionMethodFromName(modeName);
            if (isHost) {
                if (syncState != SyncState.IDLE && syncState != SyncState.DONE) {

                    sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, myUsername,
                            "__EXEC_METHOD__:" + executionMethod.name()));
                    return;
                }
                executionMethod = method;
                broadcastClientList();

                sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, originator,
                        "__EXEC_METHOD__:" + executionMethod.name()));
                if (!originator.equals(myUsername)) {
                    AutismClientMessaging.sendPrefixed("§eExecution method set to "
                            + executionMethod.name() + " by " + originator);
                }
                fireCallback(onPeerStatusChanged);
            } else {
                executionMethod = method;

                if (!originator.equals(myUsername)) {
                    AutismClientMessaging.sendPrefixed("§eExecution method set to "
                            + executionMethod.name() + " by " + originator);
                }
                fireCallback(onPeerStatusChanged);
            }
            return;
        }

        if (safeSender.equals(myUsername)) return;

        AutismClientMessaging.send("[LAN] <" + safeSender + "> " + safeMessage);
    }

    private void handleRequestSync(String senderUsername, boolean isMacro, String macroName, String command) {
        if (!runtimeExecutionAvailable()) return;
        if (senderUsername == null || senderUsername.equals(myUsername)) return;

        if (!isHost) {
            AutismClientAddon.LOG.warn("[Sync] Non-host received REQUEST_SYNC from {} - ignoring", senderUsername);
            return;
        }

        AutismClientAddon.LOG.info("[Sync] Received sync request from {}: isMacro={}, name={}, cmd={}",
            senderUsername, isMacro, macroName, command);

        if (COMMAND_QUEUE_FLUSH.equals(command)) {
            initiateGoSyncForQueue();
        } else if (command != null && command.startsWith(COMMAND_DELAY_PACKETS)) {
            initiateGoSync(false, "", command);
        } else if (command != null && command.startsWith("__PER_USER__\t")) {

            String assignStr = command.substring("__PER_USER__\t".length());
            String[] parts = assignStr.split("\t");
            Map<String, String> assignments = new java.util.LinkedHashMap<>();
            for (int pi = 0; pi + 1 < parts.length; pi += 2) {
                assignments.put(parts[pi], parts[pi + 1]);
            }
            initiateGoSync(isMacro, macroName, "", assignments);
        } else {
            initiateGoSync(isMacro, macroName, command);
        }
    }

    private void handleStepProgress(String senderUsername, int completedStep, int totalSteps, String macroName) {
        if (senderUsername == null || senderUsername.equals(myUsername)) return;

        if (isHost) {
            sendTcpPacket(new LanPacket.MacroStepProgressPacket(sessionId, senderUsername, completedStep, totalSteps, macroName));
        }

        if (completedStep < 0) {

            peerStepProgress.remove(senderUsername);
        } else {
            peerStepProgress.put(senderUsername, completedStep);
        }

        autismclient.util.macro.MacroConditionRegistry.onLanStepProgress();

        Runnable listener = onStepProgressChanged;
        if (listener != null) listener.run();
    }

    private void handleOffsetSync(String senderUsername, String targetUsername, int offsetMs) {
        if (senderUsername == null || senderUsername.equals(myUsername)) return;

        ClientInfo info = connectedClients.get(targetUsername);
        if (info != null) {
            info.delayOffsetMs = Math.max(0, offsetMs);
            AutismClientAddon.LOG.info("[Sync] Offset for {} set to {}ms by {}", targetUsername, offsetMs, senderUsername);
        }

        if (isHost) {
            sendTcpPacket(new LanPacket.OffsetSyncPacket(sessionId, senderUsername, targetUsername, offsetMs));
        }
    }

    public boolean isRunning() { return running.get(); }
    private boolean syncActionIdle() {
        return syncState == SyncState.IDLE || syncState == SyncState.DONE;
    }
    private boolean runtimeExecutionAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        return !configurationOnly
            && !PackHideState.isHardLocked()
            && minecraft != null
            && minecraft.level != null
            && minecraft.player != null
            && minecraft.getConnection() != null;
    }
    public void setConfigurationOnly(boolean configurationOnly) {
        this.configurationOnly = configurationOnly;
        if (configurationOnly) {
            activeSyncCtx = null;
            scheduledTickExecution = null;
            locallyExecutedIds.clear();
            if (syncState != SyncState.IDLE && syncState != SyncState.DONE) setSyncState(SyncState.IDLE);
        }
    }
    public boolean isConfigurationOnly() { return configurationOnly; }
    public boolean hasTickWork() { return running.get() && (sessionId != null || isSearching || reconnecting); }
    public boolean isInSession() { return sessionId != null; }
    public ExecutionMethod getExecutionMethod() { return executionMethod; }
    public int getTickExecutionDelayTicks() { return TICK_EXECUTION_DELAY_TICKS; }

    public void setExecutionMethod(ExecutionMethod method) {
        ExecutionMethod next = method != null ? method : ExecutionMethod.INSTANT;
        if (syncState != SyncState.IDLE && syncState != SyncState.DONE) {
            AutismClientMessaging.sendPrefixed("§cCannot change execution method while LAN Sync is active.");
            return;
        }
        if (sessionId == null || isHost) {
            ExecutionMethod previous = executionMethod;
            executionMethod = next;
            if (sessionId != null) {
                broadcastClientList();

                sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, myUsername,
                        "__EXEC_METHOD__:" + next.name()));
            }
            if (previous != next) {
                AutismClientMessaging.sendPrefixed("§eExecution method set to " + next.name());
            }
            fireCallback(onPeerStatusChanged);
        } else {
            sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, myUsername,
                    "__EXEC_METHOD__:" + next.name()));
            AutismClientMessaging.sendPrefixed("§eRequested execution method: " + next.name());
        }
    }

    public void cycleExecutionMethod() {
        setExecutionMethod(executionMethod == ExecutionMethod.INSTANT ? ExecutionMethod.TICK : ExecutionMethod.INSTANT);
    }

    public void onGameJoined() {
        if (sessionId == null) return;
        if (isHost) {

            broadcastClientList();
        } else {

            sendTcpPacket(new LanPacket.RequestClientListPacket(sessionId));
        }
    }
    public boolean isHost() { return isHost; }
    public String getSessionId() { return sessionId; }
    public int getConnectedCount() { return connectedClients.size(); }
    public boolean isSearching() { return isSearching; }
    public int getSearchSecondsRemaining() { return searchTicksRemaining / 20; }
    public boolean isReconnecting() { return reconnecting; }
    public SyncState getSyncState() { return syncState; }
    public SyncContext getActiveSyncContext() { return activeSyncCtx; }
    public SpreadResult getLastSpreadResult() { return lastSpreadResult; }
    public List<SpreadResult> getSpreadHistory() { return new ArrayList<>(spreadHistory); }
    public String getMyUsername() { return myUsername; }

    public Map<String, ClientInfo> getConnectedClients() { return new ConcurrentHashMap<>(connectedClients); }

    public void broadcastMacroDeletion(String macroName) {
        if (sessionId == null) return;
        sendTcpPacket(new LanPacket.MacroDeletePacket(sessionId, myUsername, macroName));
    }

    public void broadcastQueueSync(String queueData) {
        if (configurationOnly) return;
        if (!isInSession()) return;
        sendTcpPacket(new LanPacket.QueueSyncPacket(sessionId, queueData, myUsername));
    }

    public void broadcastStopAll() {
        if (configurationOnly) return;
        if (!isInSession()) return;

        autismclient.util.macro.MacroExecutor.stop();
        AutismClientMessaging.sendPrefixed("§c[LAN] Macros stopped by " + myUsername);

        sendTcpPacket(new LanPacket.ChatMessagePacket(sessionId, "__STOP_ALL__", myUsername));
    }

    public boolean sendChatMessage(String message) {
        if (!runtimeExecutionAvailable()) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync execution requires a connected world.");
            return false;
        }
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) return false;
        if (!isInSession()) {
            AutismClientMessaging.sendPrefixed("Join a LAN Sync session first.");
            return false;
        }
        if (!syncActionIdle()) {
            AutismClientMessaging.sendPrefixed("§cLAN Sync is busy with another synchronized action.");
            return false;
        }

        String command = "__CHAT__\t" + safeMessage;
        if (isHost) {
            initiateGoSync(false, null, command);
        } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(sessionId, myUsername, false, "", command);
            request.executionMethod = executionMethod.ordinal();
            sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync chat from host...");
        }
        return true;
    }

    public int getConnectedClientCount() { return connectedClients.size(); }

    public void broadcastStepProgress(int completedStep, int totalSteps, String macroName) {
        if (sessionId == null) return;
        if (completedStep < 0) {
            peerStepProgress.remove(myUsername);
        } else {
            peerStepProgress.put(myUsername, completedStep);
        }
        sendTcpPacket(new LanPacket.MacroStepProgressPacket(sessionId, myUsername, completedStep, totalSteps,
                macroName != null ? macroName : ""));

        autismclient.util.macro.MacroConditionRegistry.onLanStepProgress();
    }

    public int getPeerStep(String username) {
        return peerStepProgress.getOrDefault(username, 0);
    }

    public Map<String, Integer> getAllPeerSteps() {
        return new ConcurrentHashMap<>(peerStepProgress);
    }

    public void clearStepProgress() {
        peerStepProgress.clear();
    }

    public void setOnStepProgressChanged(Runnable callback) { this.onStepProgressChanged = callback; }

    public Map<String, Map<String, AutismMacro>> getAllRemoteMacros() { return new java.util.HashMap<>(clientMacroLists); }
    public Map<String, Map<String, AutismMacro>> getAllRemoteMeteorMacros() { return new java.util.HashMap<>(remoteMeteorMacros); }

    public List<String> getPeersMissingMacro(String macroName) {
        List<String> missing = new ArrayList<>();
        if (macroName == null || macroName.isBlank()) return missing;

        Map<String, Map<String, AutismMacro>> remote = getAllRemoteMacros();
        java.util.TreeSet<String> peers = new java.util.TreeSet<>(connectedClients.keySet());
        for (String peer : peers) {
            if (peer.equals(myUsername)) continue;
            Map<String, AutismMacro> peerMacros = remote.get(peer);
            if (peerMacros == null || !peerMacros.containsKey(MacroNames.key(macroName))) {
                missing.add(peer);
            }
        }
        return missing;
    }

    public Map<String, String> getMissingAssignedMacros(Map<String, String> assignments) {
        Map<String, String> missing = new java.util.LinkedHashMap<>();
        if (assignments == null || assignments.isEmpty()) return missing;

        Map<String, Map<String, AutismMacro>> remote = getAllRemoteMacros();
        for (Map.Entry<String, String> entry : new java.util.LinkedHashMap<>(assignments).entrySet()) {
            String username = entry.getKey();
            String macroName = entry.getValue();
            if (username == null || username.isBlank() || macroName == null || macroName.isBlank()) continue;

            if (username.equals(myUsername)) {
                if (AutismMacroManager.get().get(macroName) == null) {
                    missing.put(username, macroName);
                }
                continue;
            }

            Map<String, AutismMacro> peerMacros = remote.get(username);
            if (peerMacros == null || !peerMacros.containsKey(MacroNames.key(macroName))) {
                missing.put(username, macroName);
            }
        }
        return missing;
    }

    public List<String> getRemoteMacros() {

        Map<String, String> remoteMacros = new java.util.LinkedHashMap<>();
        Set<String> ownMacros = new java.util.HashSet<>();

        for (String meteorName : AutismCompatManager.getMeteorMacroNames()) ownMacros.add(MacroNames.key(meteorName));

        for (Map.Entry<String, Map<String, AutismMacro>> entry : clientMacroLists.entrySet()) {
            if (!entry.getKey().equals(myUsername)) {
                for (AutismMacro macro : entry.getValue().values()) {
                    if (macro == null) continue;
                    String key = MacroNames.key(macro.name);
                    if (!ownMacros.contains(key)) remoteMacros.putIfAbsent(key, macro.name);
                }
            }
        }

        return new ArrayList<>(remoteMacros.values());
    }

    public void importMacro(String clientUsername, String macroName) {
        Map<String, AutismMacro> clientMacros = clientMacroLists.get(clientUsername);
        AutismMacro remoteMacro = clientMacros == null ? null : clientMacros.get(MacroNames.key(macroName));
        if (remoteMacro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: " + macroName);
            return;
        }
        String newName = macroName;
        int suffix = 1;
        while (AutismMacroManager.get().get(newName) != null) {
            newName = macroName + " (" + suffix + ")";
            suffix++;
        }

        AutismMacro newMacro = new AutismMacro(newName);
        newMacro.actions.addAll(remoteMacro.actions);
        AutismMacroManager.get().add(newMacro);
        AutismMacroManager.get().save();

        AutismClientMessaging.sendPrefixed("§aImported macro: " + newName);
        AutismClientAddon.LOG.info("[Autism-LAN] Imported macro '{}' from {}", newName, clientUsername);
    }

    public void broadcastAssignments(Map<String, String> assignments) {
        if (sessionId == null || !isInSession()) return;
        syncedAssignments.clear();
        syncedAssignments.putAll(assignments);
        assignmentSetBy = myUsername;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            if (sb.length() > 0) sb.append('\t');
            sb.append(entry.getKey()).append('\t').append(entry.getValue());
        }
        sendTcpPacket(new LanPacket.MacroAssignmentSyncPacket(sessionId, myUsername, sb.toString()));
    }

    private void handleAssignmentSync(String sender, String encoded) {
        syncedAssignments.clear();
        assignmentSetBy = sender;
        if (encoded != null && !encoded.isEmpty()) {
            String[] parts = encoded.split("\t");
            for (int i = 0; i + 1 < parts.length; i += 2) {
                syncedAssignments.put(parts[i], parts[i + 1]);
            }
        }
        fireCallback(onAssignmentsChanged);
    }

    public Map<String, String> getSyncedAssignments() {
        return new java.util.LinkedHashMap<>(syncedAssignments);
    }

    public String getAssignmentSetBy() { return assignmentSetBy; }

    public void setOnAssignmentsChanged(Runnable callback) { this.onAssignmentsChanged = callback; }

    public List<String> getRemoteMacroNamesForPeer(String peerUsername) {
        Map<String, AutismMacro> peerMacros = clientMacroLists.get(peerUsername);
        if (peerMacros == null || peerMacros.isEmpty()) return java.util.Collections.emptyList();

        List<String> names = new ArrayList<>(peerMacros.size());
        for (AutismMacro macro : peerMacros.values()) if (macro != null) names.add(macro.name);
        return names;
    }

    public boolean shareMacroWithPeers(String macroName) {
        if (sessionId == null || !isInSession()) {
            AutismClientMessaging.sendPrefixed("§cJoin a session first.");
            return false;
        }
        if (macroName == null || macroName.isBlank()) {
            AutismClientMessaging.sendPrefixed("§cSelect a macro first.");
            return false;
        }

        AutismMacro macro = AutismMacroManager.get().get(macroName);
        if (macro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: " + macroName);
            return false;
        }

        sendMacroData(myUsername, macro, (byte)2);
        AutismClientMessaging.sendPrefixed("§aSent macro to peers: " + macro.name);
        return true;
    }

    public void sharePreset(String presetName) {
        if (sessionId == null) {
            AutismClientMessaging.sendPrefixed("§cNot in session.");
            return;
        }

        java.io.File file = new java.io.File(new java.io.File(AutismClientAddon.FOLDER, "presets"), presetName + ".json");
        if (!file.exists()) {
            AutismClientMessaging.sendPrefixed("§cPreset not found: " + presetName);
            return;
        }

        try {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            sendTcpPacket(new LanPacket.PresetDataPacket(sessionId, myUsername, presetName, json));
            AutismClientMessaging.sendPrefixed("§aShared preset '" + presetName + "' with LAN.");
        } catch (IOException e) {
            AutismClientMessaging.sendPrefixed("§cFailed to read preset: " + e.getMessage());
        }
    }

    public void requestMacro(String macroName) {
        if (sessionId == null || !isInSession()) {
            AutismClientMessaging.sendPrefixed("§cNot in a LAN Sync session!");
            return;
        }
        sendTcpPacket(new LanPacket.RequestMacroPacket(sessionId, macroName));
        AutismClientMessaging.sendPrefixed("§eRequesting macro: " + macroName);
    }

    public void startTwoPhaseExecution(String targetName, boolean isMacro) {
        if (!isInSession()) return;
        if (isMacro) executeMacroSynchronized(targetName);
        else sendQueuedPackets();
    }

    private String summarizeNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        if (names.size() <= 3) return String.join(", ", names);
        return String.join(", ", names.subList(0, 3)) + " +" + (names.size() - 3) + " more";
    }

    private String summarizeAssignments(Map<String, String> assignments) {
        if (assignments == null || assignments.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
            if (parts.size() == 3 && assignments.size() > 3) break;
        }
        if (assignments.size() > 3) {
            parts.add("+" + (assignments.size() - 3) + " more");
        }
        return String.join(", ", parts);
    }

    public void setOnClientJoined(Runnable callback) { this.onClientJoined = callback; }
    public void setOnClientLeft(Runnable callback) { this.onClientLeft = callback; }
    public void setOnCountdown(Runnable callback) { this.onCountdown = callback; }
    public void setOnSendNow(Runnable callback) { this.onSendNow = callback; }
    public void setOnSessionStateChanged(Runnable callback) { this.onSessionStateChanged = callback; }
    public void setOnSyncStateChanged(Runnable callback) { this.onSyncStateChanged = callback; }
    public void setOnSpreadCalculated(Runnable callback) { this.onSpreadCalculated = callback; }
    public void setOnPeerStatusChanged(Runnable callback) { this.onPeerStatusChanged = callback; }

    private void fireCallback(Runnable callback) {
        if (callback != null) {
            Minecraft.getInstance().execute(callback);
        }
    }

    private String generateSessionId() {
        return String.format("%04d", (int)(Math.random() * 10000));
    }

    private String getUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getName().getString();
        }
        return "Player";
    }

    public static class ClientInfo {
        public String username;
        public boolean isHost;
        public long lastSeen;
        public int delayOffsetMs;

        ClientInfo(String username, boolean isHost) {
            this.username = username;
            this.isHost = isHost;
            this.lastSeen = System.currentTimeMillis();
            this.delayOffsetMs = 0;
        }
    }

    public void setPlayerDelayOffset(String username, int offsetMs) {
        ClientInfo info = connectedClients.get(username);
        if (info != null) {
            int clamped = Math.max(0, offsetMs);
            info.delayOffsetMs = clamped;

            if (sessionId != null) {
                sendTcpPacket(new LanPacket.OffsetSyncPacket(sessionId, myUsername, username, clamped));
            }
        }
    }

    public int getPlayerDelayOffset(String username) {
        ClientInfo info = connectedClients.get(username);
        return info != null ? info.delayOffsetMs : 0;
    }

    public void sendChat(String message) {
        sendChatMessage(message);
    }
}
