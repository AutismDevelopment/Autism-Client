package autismclient.util.lan;

import autismclient.modules.AutismModule;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;

public abstract class LanPacket {
    protected final LanPacketType type;
    protected String sessionId;

    public LanPacket(LanPacketType type, String sessionId) {
        this.type = type;
        this.sessionId = sessionId != null ? sessionId : "";
    }

    public LanPacketType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public abstract void write(DataOutputStream out) throws IOException;
    public abstract void read(DataInputStream in) throws IOException;

    protected static int readCount(DataInputStream in, int limit) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > limit) {
            throw new IOException("Collection length out of range: " + count);
        }
        return count;
    }

    public static LanPacket create(LanPacketType type, String sessionId) {
        switch (type) {
            case SEARCH_REQUEST: return new SearchRequestPacket(sessionId);
            case SESSION: return new SessionPacket(sessionId, 0, "");
            case JOIN: return new JoinPacket(sessionId, "");
            case REQUEST_CLIENT_LIST: return new RequestClientListPacket(sessionId);
            case CLIENT_LIST: return new ClientListPacket(sessionId, new ArrayList<>());
            case LEAVE: return new LeavePacket(sessionId, "");

            case REQUEST_MACRO: return new RequestMacroPacket(sessionId, "");
            case MACRO_DATA: return new MacroDataPacket(sessionId, "", "", "", (byte)0);
            case MACRO_DELETE: return new MacroDeletePacket(sessionId, "", "");
            case MACRO_LIST: return new MacroListPacket(sessionId, "", new ArrayList<>());
            case QUEUE_SYNC: return new QueueSyncPacket(sessionId, "", "");
            case PRESET_DATA: return new PresetDataPacket(sessionId, "", "", "");
            case CHAT_MESSAGE: return new ChatMessagePacket(sessionId, "", "");

            case TCP_COMMAND_ACK: return new TcpCommandAckPacket(sessionId, "", 0);
            case PLAYER_OFFSETS: return new PlayerOffsetsPacket(sessionId, new java.util.HashMap<>());
            case CLIENT_OFFSET_UPDATE: return new ClientOffsetUpdatePacket(sessionId, "", "", 0);

            case PREPARE_EXECUTION: return new PrepareExecutionPacket(sessionId, 0, false, "", "", "");
            case CLIENT_READY: return new ClientReadyPacket(sessionId, 0, "");
            case EXECUTE_NOW: return new ExecuteNowPacket(sessionId, 0, 0);

            case GO: return new GoPacket(sessionId, 0);
            case HEARTBEAT: return new HeartbeatPacket(sessionId, "", 0);
            case SYNC_STATE_UPDATE: return new SyncStateUpdatePacket(sessionId, 0, "");
            case REQUEST_SYNC: return new RequestSyncPacket(sessionId, "", false, "", "");
            case OFFSET_SYNC: return new OffsetSyncPacket(sessionId, "", "", 0);
            case MACRO_STEP_PROGRESS: return new MacroStepProgressPacket(sessionId, "", 0, 0, "");
            case MACRO_DATA_CHUNK: return new MacroDataChunkPacket(sessionId, "", "", (byte)0, 0, 0, new byte[0]);
            case MACRO_ASSIGNMENT_SYNC: return new MacroAssignmentSyncPacket(sessionId, "", "");
            default: return null;
        }
    }

    public static class SearchRequestPacket extends LanPacket {
        public SearchRequestPacket(String sessionId) { super(LanPacketType.SEARCH_REQUEST, sessionId); }
        public SearchRequestPacket() { super(LanPacketType.SEARCH_REQUEST, ""); }
        @Override public void write(DataOutputStream out) throws IOException {}
        @Override public void read(DataInputStream in) throws IOException {}
    }

    public static class SessionPacket extends LanPacket {
        public long startTime;
        public String hostName;
        public byte transportType;
        public SessionPacket(String sessionId, long startTime, String hostName, byte transportType) {
            super(LanPacketType.SESSION, sessionId);
            this.startTime = startTime;
            this.hostName = hostName;
            this.transportType = transportType;
        }
        public SessionPacket(String sessionId, long startTime, String hostName) {
            this(sessionId, startTime, hostName, (byte) 1);
        }
        public SessionPacket() { super(LanPacketType.SESSION, ""); }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeLong(startTime);
            out.writeUTF(hostName);
            out.writeByte(transportType);
        }
        @Override public void read(DataInputStream in) throws IOException {
            startTime = in.readLong();
            hostName = in.readUTF();
            try {
                transportType = in.readByte();
            } catch (EOFException e) {
                transportType = 1;
            }
        }
    }

    public static class JoinPacket extends LanPacket {
        public String username;
        public JoinPacket(String sessionId, String username) {
            super(LanPacketType.JOIN, sessionId);
            this.username = username;
        }
        public JoinPacket() { super(LanPacketType.JOIN, ""); }
        @Override public void write(DataOutputStream out) throws IOException { out.writeUTF(username); }
        @Override public void read(DataInputStream in) throws IOException { username = in.readUTF(); }
    }

    public static class RequestClientListPacket extends LanPacket {
        public RequestClientListPacket(String sessionId) { super(LanPacketType.REQUEST_CLIENT_LIST, sessionId); }
        public RequestClientListPacket() { super(LanPacketType.REQUEST_CLIENT_LIST, ""); }
        @Override public void write(DataOutputStream out) throws IOException {}
        @Override public void read(DataInputStream in) throws IOException {}
    }

    public static class ClientListPacket extends LanPacket {
        public static class ClientEntry {
            public String name;
            public boolean isHost;
            public ClientEntry(String name, boolean isHost) { this.name = name; this.isHost = isHost; }
        }
        public List<ClientEntry> clients = new ArrayList<>();
        public int executionMethod = 0;
        public ClientListPacket(String sessionId, List<ClientEntry> clients) {
            super(LanPacketType.CLIENT_LIST, sessionId);
            this.clients = clients;
        }
        public ClientListPacket(String sessionId, List<ClientEntry> clients, int executionMethod) {
            this(sessionId, clients);
            this.executionMethod = executionMethod;
        }
        public ClientListPacket() { super(LanPacketType.CLIENT_LIST, ""); }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeInt(clients.size());
            for (ClientEntry client : clients) {
                out.writeUTF(client.name);
                out.writeBoolean(client.isHost);
            }
            out.writeInt(executionMethod);
        }
        @Override public void read(DataInputStream in) throws IOException {
            int size = readCount(in, 1024);
            clients.clear();
            for (int i = 0; i < size; i++) {
                clients.add(new ClientEntry(in.readUTF(), in.readBoolean()));
            }
            try {
                executionMethod = in.readInt();
            } catch (EOFException e) {
                executionMethod = 0;
            }
        }
    }

    public static class LeavePacket extends LanPacket {
        public String username;
        public LeavePacket(String sessionId, String username) {
            super(LanPacketType.LEAVE, sessionId);
            this.username = username;
        }
        public LeavePacket() { super(LanPacketType.LEAVE, ""); }
        @Override public void write(DataOutputStream out) throws IOException { out.writeUTF(username); }
        @Override public void read(DataInputStream in) throws IOException { username = in.readUTF(); }
    }

    public static class RequestMacroPacket extends LanPacket {
        public String macroName;
        public RequestMacroPacket(String sessionId, String macroName) {
            super(LanPacketType.REQUEST_MACRO, sessionId);
            this.macroName = macroName;
        }
        public RequestMacroPacket() { super(LanPacketType.REQUEST_MACRO, ""); }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(macroName);
        }
        @Override public void read(DataInputStream in) throws IOException {
            macroName = in.readUTF();
        }
    }

    public static class MacroDataPacket extends LanPacket {
        public String senderUsername;
        public String macroName;
        public String nbtData;
        public byte sourceType;

        public MacroDataPacket(String sessionId, String senderUsername, String macroName, String nbtData, byte sourceType) {
            super(LanPacketType.MACRO_DATA, sessionId);
            this.senderUsername = senderUsername;
            this.macroName = macroName;
            this.nbtData = nbtData != null ? nbtData : "";
            this.sourceType = sourceType;
        }
        public MacroDataPacket() { super(LanPacketType.MACRO_DATA, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(macroName);
            out.writeUTF(nbtData);
            out.writeByte(sourceType);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            macroName = in.readUTF();
            nbtData = in.readUTF();
            try {
                sourceType = in.readByte();
            } catch (EOFException e) {

                sourceType = 0;
            }
        }
    }

    public static class MacroDeletePacket extends LanPacket {
        public String senderUsername;
        public String macroName;

        public MacroDeletePacket(String sessionId, String senderUsername, String macroName) {
            super(LanPacketType.MACRO_DELETE, sessionId);
            this.senderUsername = senderUsername;
            this.macroName = macroName;
        }
        public MacroDeletePacket() { super(LanPacketType.MACRO_DELETE, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(macroName);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            macroName = in.readUTF();
        }
    }

    public static class MacroListPacket extends LanPacket {
        public String senderUsername;
        public List<String> macroNames = new ArrayList<>();
        public MacroListPacket(String sessionId, String senderUsername, List<String> macroNames) {
            super(LanPacketType.MACRO_LIST, sessionId);
            this.senderUsername = senderUsername;
            this.macroNames = macroNames;
        }
        public MacroListPacket() { super(LanPacketType.MACRO_LIST, ""); }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeInt(macroNames.size());
            for (String name : macroNames) {
                out.writeUTF(name);
            }
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            int size = readCount(in, 100_000);
            macroNames.clear();
            for (int i = 0; i < size; i++) {
                macroNames.add(in.readUTF());
            }
        }
    }

    public static class QueueSyncPacket extends LanPacket {
        public String queueData;
        public String senderUsername;

        public QueueSyncPacket(String sessionId, String queueData, String senderUsername) {
            super(LanPacketType.QUEUE_SYNC, sessionId);
            this.queueData = queueData;
            this.senderUsername = senderUsername;
        }
        public QueueSyncPacket() { super(LanPacketType.QUEUE_SYNC, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(queueData);
            out.writeUTF(senderUsername);
        }
        @Override public void read(DataInputStream in) throws IOException {
            queueData = in.readUTF();
            senderUsername = in.readUTF();
        }
    }

    public static class PresetDataPacket extends LanPacket {
        public String senderUsername;
        public String presetName;
        public String presetJson;

        public PresetDataPacket(String sessionId, String senderUsername, String presetName, String presetJson) {
            super(LanPacketType.PRESET_DATA, sessionId);
            this.senderUsername = senderUsername;
            this.presetName = presetName;
            this.presetJson = presetJson;
        }
        public PresetDataPacket() { super(LanPacketType.PRESET_DATA, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(presetName);
            out.writeUTF(presetJson);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            presetName = in.readUTF();
            presetJson = in.readUTF();
        }
    }

    public static class ChatMessagePacket extends LanPacket {
        public String senderUsername;
        public String message;

        public ChatMessagePacket(String sessionId, String senderUsername, String message) {
            super(LanPacketType.CHAT_MESSAGE, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.message = message != null ? message : "";
        }

        public ChatMessagePacket() { super(LanPacketType.CHAT_MESSAGE, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(message);
        }

        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            message = in.readUTF();
        }
    }

    public static class TcpCommandAckPacket extends LanPacket {
        public String senderUsername;
        public long executeNanoTime;
        public int executionMethod = 0;
        public long targetTick = -1L;
        public long actualTick = -1L;
        public boolean late = false;

        public TcpCommandAckPacket(String sessionId, String senderUsername, long executeNanoTime) {
            super(LanPacketType.TCP_COMMAND_ACK, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.executeNanoTime = executeNanoTime;
        }
        public TcpCommandAckPacket(String sessionId, String senderUsername, long executeNanoTime,
                                   int executionMethod, long targetTick, long actualTick, boolean late) {
            this(sessionId, senderUsername, executeNanoTime);
            this.executionMethod = executionMethod;
            this.targetTick = targetTick;
            this.actualTick = actualTick;
            this.late = late;
        }
        public TcpCommandAckPacket() { super(LanPacketType.TCP_COMMAND_ACK, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeLong(executeNanoTime);
            out.writeInt(executionMethod);
            out.writeLong(targetTick);
            out.writeLong(actualTick);
            out.writeBoolean(late);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            executeNanoTime = in.readLong();
            try {
                executionMethod = in.readInt();
                targetTick = in.readLong();
                actualTick = in.readLong();
                late = in.readBoolean();
            } catch (EOFException e) {
                executionMethod = 0;
                targetTick = -1L;
                actualTick = -1L;
                late = false;
            }
        }
    }

    public static class PlayerOffsetsPacket extends LanPacket {
        public java.util.Map<String, Integer> offsets = new java.util.HashMap<>();

        public PlayerOffsetsPacket(String sessionId, java.util.Map<String, Integer> offsets) {
            super(LanPacketType.PLAYER_OFFSETS, sessionId);
            this.offsets = offsets != null ? new java.util.HashMap<>(offsets) : new java.util.HashMap<>();
        }
        public PlayerOffsetsPacket() { super(LanPacketType.PLAYER_OFFSETS, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeInt(offsets.size());
            for (java.util.Map.Entry<String, Integer> entry : offsets.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue());
            }
        }
        @Override public void read(DataInputStream in) throws IOException {
            int count = readCount(in, 1024);
            offsets = new java.util.HashMap<>();
            for (int i = 0; i < count; i++) {
                String player = in.readUTF();
                int offset = in.readInt();
                offsets.put(player, offset);
            }
        }
    }

    public static class ClientOffsetUpdatePacket extends LanPacket {
        public String senderUsername;
        public String targetUsername;
        public int offsetMs;

        public ClientOffsetUpdatePacket(String sessionId, String senderUsername, String targetUsername, int offsetMs) {
            super(LanPacketType.CLIENT_OFFSET_UPDATE, sessionId);
            this.senderUsername = senderUsername;
            this.targetUsername = targetUsername;
            this.offsetMs = offsetMs;
        }
        public ClientOffsetUpdatePacket() { super(LanPacketType.CLIENT_OFFSET_UPDATE, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(targetUsername);
            out.writeInt(offsetMs);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            targetUsername = in.readUTF();
            offsetMs = in.readInt();
        }
    }

    public static class PrepareExecutionPacket extends LanPacket {
        public long executionId;
        public boolean isMacro;
        public String targetName;
        public String queueData;
        public String senderUsername;

        public String macroAssignments = "";
        public int executionMethod = 0;
        public long targetTick = -1L;

        public PrepareExecutionPacket(String sessionId, long executionId, boolean isMacro, String targetName, String queueData, String senderUsername) {
            super(LanPacketType.PREPARE_EXECUTION, sessionId);
            this.executionId = executionId;
            this.isMacro = isMacro;
            this.targetName = targetName;
            this.queueData = queueData != null ? queueData : "";
            this.senderUsername = senderUsername;
        }
        public PrepareExecutionPacket(String sessionId, long executionId, boolean isMacro, String targetName, String queueData, String senderUsername,
                                   int executionMethod, long targetTick) {
            this(sessionId, executionId, isMacro, targetName, queueData, senderUsername);
            this.executionMethod = executionMethod;
            this.targetTick = targetTick;
        }
        public PrepareExecutionPacket() { super(LanPacketType.PREPARE_EXECUTION, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeLong(executionId);
            out.writeBoolean(isMacro);
            out.writeUTF(targetName);
            out.writeUTF(queueData);
            out.writeUTF(senderUsername);
            out.writeUTF(macroAssignments);
            out.writeInt(executionMethod);
            out.writeLong(targetTick);
        }
        @Override public void read(DataInputStream in) throws IOException {
            executionId = in.readLong();
            isMacro = in.readBoolean();
            targetName = in.readUTF();
            queueData = in.readUTF();
            try {
                senderUsername = in.readUTF();
            } catch (EOFException e) {
                senderUsername = "";
                return;
            }
            try {
                macroAssignments = in.readUTF();
            } catch (EOFException e) {
                macroAssignments = "";
                return;
            }
            try {
                executionMethod = in.readInt();
                targetTick = in.readLong();
            } catch (EOFException e) {
                executionMethod = 0;
                targetTick = -1L;
            }
        }
    }

    public static class ClientReadyPacket extends LanPacket {
        public long executionId;
        public String senderUsername;

        public ClientReadyPacket(String sessionId, long executionId, String senderUsername) {
            super(LanPacketType.CLIENT_READY, sessionId);
            this.executionId = executionId;
            this.senderUsername = senderUsername;
        }
        public ClientReadyPacket() { super(LanPacketType.CLIENT_READY, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeLong(executionId);
            out.writeUTF(senderUsername);
        }
        @Override public void read(DataInputStream in) throws IOException {
            executionId = in.readLong();
            senderUsername = in.readUTF();
        }
    }

    public static class ExecuteNowPacket extends LanPacket {
        public long executionId;
        public long targetTime;

        public ExecuteNowPacket(String sessionId, long executionId, long targetTime) {
            super(LanPacketType.EXECUTE_NOW, sessionId);
            this.executionId = executionId;
            this.targetTime = targetTime;
        }
        public ExecuteNowPacket() { super(LanPacketType.EXECUTE_NOW, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeLong(executionId);
            out.writeLong(targetTime);
        }
        @Override public void read(DataInputStream in) throws IOException {
            executionId = in.readLong();
            targetTime = in.readLong();
        }
    }

    public static class GoPacket extends LanPacket {
        public long executionId;
        public int executionMethod = 0;
        public long targetTick = -1L;

        public GoPacket(String sessionId, long executionId) {
            super(LanPacketType.GO, sessionId);
            this.executionId = executionId;
        }
        public GoPacket(String sessionId, long executionId, int executionMethod, long targetTick) {
            this(sessionId, executionId);
            this.executionMethod = executionMethod;
            this.targetTick = targetTick;
        }
        public GoPacket() { super(LanPacketType.GO, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeLong(executionId);
            out.writeInt(executionMethod);
            out.writeLong(targetTick);
        }
        @Override public void read(DataInputStream in) throws IOException {
            executionId = in.readLong();
            try {
                executionMethod = in.readInt();
                targetTick = in.readLong();
            } catch (EOFException e) {
                executionMethod = 0;
                targetTick = -1L;
            }
        }
    }

    public static class HeartbeatPacket extends LanPacket {
        public String senderUsername;
        public long timestamp;

        public HeartbeatPacket(String sessionId, String senderUsername, long timestamp) {
            super(LanPacketType.HEARTBEAT, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.timestamp = timestamp;
        }
        public HeartbeatPacket() { super(LanPacketType.HEARTBEAT, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeLong(timestamp);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            timestamp = in.readLong();
        }
    }

    public static class SyncStateUpdatePacket extends LanPacket {
        public int stateOrdinal;
        public String detail;

        public SyncStateUpdatePacket(String sessionId, int stateOrdinal, String detail) {
            super(LanPacketType.SYNC_STATE_UPDATE, sessionId);
            this.stateOrdinal = stateOrdinal;
            this.detail = detail != null ? detail : "";
        }
        public SyncStateUpdatePacket() { super(LanPacketType.SYNC_STATE_UPDATE, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeInt(stateOrdinal);
            out.writeUTF(detail);
        }
        @Override public void read(DataInputStream in) throws IOException {
            stateOrdinal = in.readInt();
            detail = in.readUTF();
        }
    }

    public static class RequestSyncPacket extends LanPacket {
        public String senderUsername;
        public boolean isMacro;
        public String macroName;
        public String command;
        public int executionMethod = 0;

        public RequestSyncPacket(String sessionId, String senderUsername, boolean isMacro, String macroName, String command) {
            super(LanPacketType.REQUEST_SYNC, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.isMacro = isMacro;
            this.macroName = macroName != null ? macroName : "";
            this.command = command != null ? command : "";
        }
        public RequestSyncPacket() { super(LanPacketType.REQUEST_SYNC, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeBoolean(isMacro);
            out.writeUTF(macroName);
            out.writeUTF(command);
            out.writeInt(executionMethod);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            isMacro = in.readBoolean();
            macroName = in.readUTF();
            command = in.readUTF();
            try {
                executionMethod = in.readInt();
            } catch (EOFException e) {
                executionMethod = 0;
            }
        }
    }

    public static class OffsetSyncPacket extends LanPacket {
        public String senderUsername;
        public String targetUsername;
        public int offsetMs;

        public OffsetSyncPacket(String sessionId, String senderUsername, String targetUsername, int offsetMs) {
            super(LanPacketType.OFFSET_SYNC, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.targetUsername = targetUsername != null ? targetUsername : "";
            this.offsetMs = offsetMs;
        }
        public OffsetSyncPacket() { super(LanPacketType.OFFSET_SYNC, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(targetUsername);
            out.writeInt(offsetMs);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            targetUsername = in.readUTF();
            offsetMs = in.readInt();
        }
    }

    public static class MacroStepProgressPacket extends LanPacket {
        public String senderUsername;
        public int completedStep;
        public int totalSteps;
        public String macroName;

        public MacroStepProgressPacket(String sessionId, String senderUsername, int completedStep, int totalSteps, String macroName) {
            super(LanPacketType.MACRO_STEP_PROGRESS, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.completedStep = completedStep;
            this.totalSteps = totalSteps;
            this.macroName = macroName != null ? macroName : "";
        }
        public MacroStepProgressPacket() { super(LanPacketType.MACRO_STEP_PROGRESS, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeInt(completedStep);
            out.writeInt(totalSteps);
            out.writeUTF(macroName);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            completedStep = in.readInt();
            totalSteps = in.readInt();
            macroName = in.readUTF();
        }
    }

    public static class MacroDataChunkPacket extends LanPacket {
        public String senderUsername;

        public static final int MAX_CHUNK_BYTES = 64_000;

        public String macroName;
        public byte sourceType;
        public int chunkIndex;
        public int totalChunks;
        public byte[] chunkData;

        public MacroDataChunkPacket(String sessionId, String senderUsername, String macroName,
                                     byte sourceType, int chunkIndex, int totalChunks, byte[] chunkData) {
            super(LanPacketType.MACRO_DATA_CHUNK, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.macroName = macroName != null ? macroName : "";
            this.sourceType = sourceType;
            this.chunkIndex = chunkIndex;
            this.totalChunks = totalChunks;
            this.chunkData = chunkData != null ? chunkData : new byte[0];
        }
        public MacroDataChunkPacket() { super(LanPacketType.MACRO_DATA_CHUNK, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(macroName);
            out.writeByte(sourceType);
            out.writeInt(chunkIndex);
            out.writeInt(totalChunks);
            out.writeInt(chunkData.length);
            out.write(chunkData);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            macroName = in.readUTF();
            sourceType = in.readByte();
            chunkIndex = in.readInt();
            totalChunks = in.readInt();
            int len = in.readInt();

            if (len < 0 || len > MAX_CHUNK_BYTES) {
                throw new IOException("Macro chunk length out of range: " + len);
            }
            chunkData = new byte[len];
            in.readFully(chunkData);
        }
    }

    public static class MacroAssignmentSyncPacket extends LanPacket {
        public String senderUsername = "";
        public String assignments = "";

        public MacroAssignmentSyncPacket(String sessionId, String senderUsername, String assignments) {
            super(LanPacketType.MACRO_ASSIGNMENT_SYNC, sessionId);
            this.senderUsername = senderUsername != null ? senderUsername : "";
            this.assignments = assignments != null ? assignments : "";
        }
        public MacroAssignmentSyncPacket() { super(LanPacketType.MACRO_ASSIGNMENT_SYNC, ""); }

        @Override public void write(DataOutputStream out) throws IOException {
            out.writeUTF(senderUsername);
            out.writeUTF(assignments);
        }
        @Override public void read(DataInputStream in) throws IOException {
            senderUsername = in.readUTF();
            assignments = in.readUTF();
        }
    }

}
