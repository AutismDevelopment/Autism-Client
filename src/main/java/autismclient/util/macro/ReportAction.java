package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

import java.util.function.Predicate;

public class ReportAction implements MacroAction {
    public enum StartEvent { ACTION_START, COMMAND_SENT, GUI_CLICK_SENT, MANUAL }
    public enum EndEvent { GUI_OPEN, GUI_CLOSE, WORLD_CHANGE, TELEPORT_PACKET, POSITION_JUMP }
    public enum EndCondition { GUI_OPEN, GUI_CLOSE, TELEPORT_PACKET, WORLD_CHANGE, POSITION_DELTA, PACKET }

    public StartEvent startEvent = StartEvent.ACTION_START;
    public EndEvent endEvent = EndEvent.GUI_OPEN;
    public EndCondition endCondition = EndCondition.GUI_OPEN;

    public String reportLabel = "Report";
    public String startConditionType = RaceAction.TriggerType.DELAY.name();
    public String startActionType = MacroActionType.ITEM.name();
    public String endConditionType = RaceAction.TriggerType.WAIT_GUI.name();
    public int conditionCount = 0;
    public String commandText = "";
    public String endGuiTitle = "";
    public String endPacketName = "";
    public String endTargetDimension = "";
    public double endDistance = 5.0;
    public boolean endHorizontalOnly = false;
    public int timeoutMs = 10_000;
    public boolean stashToSharedState = true;

    public String guiTitleSubstr = "";
    public double positionTolerance = 5.0;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("\u00A7cReport: no network connection");
            return;
        }

        long executeStartNanos = System.nanoTime();
        long startNanos = resolveStartAnchor(mc);
        if (startNanos <= 0L) return;

        long spentMs = (System.nanoTime() - executeStartNanos) / 1_000_000L;
        int remainingTimeout = (int) Math.max(1L, (long) Math.max(100, timeoutMs) - spentMs);
        if (remainingTimeout <= 0) {
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + safeLabel() + "\": no time left after start event");
            return;
        }

        MacroAction condition = buildEndConditionAction();
        boolean fired;
        try {
            fired = MacroExecutor.waitForMacroCondition(mc, condition, remainingTimeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (!fired) {
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + safeLabel() + "\": timeout waiting for " + describeEnd());
            return;
        }

        long deltaMs = (System.nanoTime() - startNanos) / 1_000_000L;
        AutismClientMessaging.sendPrefixed("\u00A7b" + safeLabel() + ": " + deltaMs + " ms ("
            + describeStart() + " -> " + describeEnd() + ")");

        if (stashToSharedState) {
            AutismSharedState.get().pushReport(new AutismSharedState.ReportSample(
                safeLabel(), deltaMs, describeStart(), describeEnd(), System.currentTimeMillis()));
        }
    }

    private long resolveStartAnchor(Minecraft mc) {
        if (startEvent == StartEvent.ACTION_START || startEvent == StartEvent.MANUAL) {
            return System.nanoTime();
        }

        Predicate<Packet<?>> startMatch = startEvent == StartEvent.COMMAND_SENT
            ? p -> p instanceof ServerboundChatCommandPacket
                || p instanceof ServerboundChatCommandSignedPacket
                || (p instanceof ServerboundChatPacket spc && spc.message() != null && spc.message().startsWith("/"))
            : p -> p instanceof ServerboundContainerClickPacket;

        MacroExecutor.OneShotPacketListener sendListener = MacroExecutor.awaitSend(startMatch);
        try {
            if (startEvent == StartEvent.COMMAND_SENT) {
                String command = normalizeCommand(commandText);
                if (command.isBlank()) {
                    sendListener.cancel();
                    AutismClientMessaging.sendPrefixed("Report \"" + safeLabel() + "\": set a command for COMMAND_SENT.");
                    return 0L;
                }
                mc.execute(() -> {
                    if (!PackHideState.isHardLocked() && mc.getConnection() != null) mc.getConnection().sendCommand(command);
                });
            }
            sendListener.future.get(Math.max(100, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS);
            return System.nanoTime();
        } catch (Exception e) {
            sendListener.cancel();
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + safeLabel() + "\": timeout waiting for start event "
                + startEvent.name());
            return 0L;
        }
    }

    private MacroAction buildEndConditionAction() {
        return switch (endCondition) {
            case GUI_CLOSE -> {
                WaitForGuiAction action = new WaitForGuiAction();
                action.waitMode = WaitForGuiAction.WaitMode.CLOSE;
                action.guiTitle = endGuiTitle == null ? "" : endGuiTitle;
                yield action;
            }
            case TELEPORT_PACKET -> {
                WaitForPacketAction action = new WaitForPacketAction("S2C:" + ClientboundPlayerPositionPacket.class.getSimpleName());
                yield action;
            }
            case WORLD_CHANGE -> {
                WaitForWorldChangeAction action = new WaitForWorldChangeAction();
                action.targetDimension = endTargetDimension == null ? "" : endTargetDimension;
                yield action;
            }
            case POSITION_DELTA -> {
                WaitForPositionDeltaAction action = new WaitForPositionDeltaAction();
                action.distance = endDistance;
                action.horizontalOnly = endHorizontalOnly;
                yield action;
            }
            case PACKET -> new WaitForPacketAction(endPacketName == null ? "" : endPacketName);
            case GUI_OPEN -> {
                WaitForGuiAction action = new WaitForGuiAction();
                action.waitMode = WaitForGuiAction.WaitMode.OPEN;
                action.guiTitle = endGuiTitle == null ? "" : endGuiTitle;
                yield action;
            }
        };
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("startEvent", startEvent.name());
        tag.putString("endEvent", legacyEndEvent().name());
        tag.putString("endCondition", endCondition.name());
        tag.putString("startConditionType", startConditionType == null ? RaceAction.TriggerType.DELAY.name() : startConditionType);
        tag.putString("startActionType", startActionType == null ? MacroActionType.ITEM.name() : startActionType);
        tag.putString("endConditionType", endConditionType == null ? RaceAction.TriggerType.WAIT_GUI.name() : endConditionType);
        tag.putInt("conditionCount", Math.max(0, conditionCount));
        tag.putString("reportLabel", reportLabel == null ? "" : reportLabel);
        tag.putString("commandText", commandText == null ? "" : commandText);
        tag.putString("endGuiTitle", endGuiTitle == null ? "" : endGuiTitle);
        tag.putString("endPacketName", endPacketName == null ? "" : endPacketName);
        tag.putString("endTargetDimension", endTargetDimension == null ? "" : endTargetDimension);
        tag.putDouble("endDistance", endDistance);
        tag.putBoolean("endHorizontalOnly", endHorizontalOnly);
        tag.putInt("timeoutMs", Math.max(100, timeoutMs));
        tag.putBoolean("stashToSharedState", stashToSharedState);
        tag.putString("guiTitleSubstr", endGuiTitle == null ? "" : endGuiTitle);
        tag.putDouble("positionTolerance", endDistance);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        try { startEvent = StartEvent.valueOf(tag.getStringOr("startEvent", StartEvent.ACTION_START.name())); }
        catch (IllegalArgumentException e) { startEvent = StartEvent.ACTION_START; }

        reportLabel = tag.getStringOr("reportLabel", "Report");
        startConditionType = tag.getStringOr("startConditionType", RaceAction.TriggerType.DELAY.name());
        startActionType = tag.getStringOr("startActionType", MacroActionType.ITEM.name());
        endConditionType = tag.getStringOr("endConditionType", "");
        conditionCount = Math.max(0, tag.getIntOr("conditionCount", 0));
        commandText = tag.getStringOr("commandText", "");
        timeoutMs = Math.max(100, tag.getIntOr("timeoutMs", 10_000));
        stashToSharedState = tag.getBooleanOr("stashToSharedState", true);

        guiTitleSubstr = tag.getStringOr("guiTitleSubstr", "");
        positionTolerance = tag.getDoubleOr("positionTolerance", 5.0);

        if (tag.contains("endCondition")) {
            try { endCondition = EndCondition.valueOf(tag.getStringOr("endCondition", EndCondition.GUI_OPEN.name())); }
            catch (IllegalArgumentException e) { endCondition = EndCondition.GUI_OPEN; }
            endGuiTitle = tag.getStringOr("endGuiTitle", guiTitleSubstr);
            endPacketName = tag.getStringOr("endPacketName", "");
            endTargetDimension = tag.getStringOr("endTargetDimension", "");
            endDistance = tag.getDoubleOr("endDistance", positionTolerance);
            endHorizontalOnly = tag.getBooleanOr("endHorizontalOnly", false);
            if (endConditionType == null || endConditionType.isBlank()) {
                endConditionType = conditionTypeForLegacyEnd();
            }
        } else {
            try { endEvent = EndEvent.valueOf(tag.getStringOr("endEvent", EndEvent.GUI_OPEN.name())); }
            catch (IllegalArgumentException e) { endEvent = EndEvent.GUI_OPEN; }
            migrateLegacyEndEvent();
        }
    }

    private void migrateLegacyEndEvent() {
        switch (endEvent) {
            case GUI_CLOSE -> {
                endCondition = EndCondition.GUI_CLOSE;
                endGuiTitle = guiTitleSubstr;
            }
            case WORLD_CHANGE -> {
                endCondition = EndCondition.WORLD_CHANGE;
                endTargetDimension = "";
            }
            case TELEPORT_PACKET -> endCondition = EndCondition.TELEPORT_PACKET;
            case POSITION_JUMP -> {
                endCondition = EndCondition.POSITION_DELTA;
                endDistance = positionTolerance;
                endHorizontalOnly = false;
            }
            case GUI_OPEN -> {
                endCondition = EndCondition.GUI_OPEN;
                endGuiTitle = guiTitleSubstr;
            }
        }
        endConditionType = conditionTypeForLegacyEnd();
    }

    private EndEvent legacyEndEvent() {
        return switch (endCondition) {
            case GUI_CLOSE -> EndEvent.GUI_CLOSE;
            case WORLD_CHANGE -> EndEvent.WORLD_CHANGE;
            case TELEPORT_PACKET, PACKET -> EndEvent.TELEPORT_PACKET;
            case POSITION_DELTA -> EndEvent.POSITION_JUMP;
            case GUI_OPEN -> EndEvent.GUI_OPEN;
        };
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.REPORT;
    }

    @Override
    public String getDisplayName() {
        return "Report \"" + safeLabel() + "\" [" + describeStart() + " -> " + describeEnd() + "]";
    }

    @Override
    public String getIcon() {
        return "Rep";
    }

    private String safeLabel() {
        return reportLabel == null || reportLabel.isBlank() ? "Report" : reportLabel.trim();
    }

    private String describeStart() {
        return startActionType != null && !startActionType.isBlank()
            ? startActionType
            : startConditionType == null || startConditionType.isBlank()
            ? (startEvent == null ? StartEvent.ACTION_START.name() : startEvent.name())
            : startConditionType;
    }

    private String describeEnd() {
        return endConditionType == null || endConditionType.isBlank()
            ? (endCondition == null ? EndCondition.GUI_OPEN.name() : endCondition.name())
            : endConditionType;
    }

    public MacroAction createSelectedStartConditionAction() {
        return RaceAction.createConditionAction(startConditionType);
    }

    public MacroAction createSelectedStartAction() {
        return RaceAction.createBodyAction(startActionType);
    }

    public MacroAction createSelectedEndConditionAction() {
        return RaceAction.createConditionAction(endConditionType);
    }

    public int normalizedConditionCount(java.util.List<MacroAction> actions, int headerIndex) {
        if (actions == null || headerIndex < 0 || headerIndex >= actions.size()) return 0;
        int max = Math.max(0, Math.min(conditionCount, actions.size() - headerIndex - 1));
        int count = 0;
        for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction action = actions.get(i);
            if (action instanceof RaceAction || action instanceof ReportAction) break;
            count++;
        }
        return count;
    }

    private String conditionTypeForLegacyEnd() {
        return switch (endCondition) {
            case GUI_CLOSE, GUI_OPEN -> RaceAction.TriggerType.WAIT_GUI.name();
            case WORLD_CHANGE -> RaceAction.TriggerType.WAIT_WORLD_CHANGE.name();
            case TELEPORT_PACKET -> RaceAction.TriggerType.WAIT_TELEPORT.name();
            case POSITION_DELTA -> RaceAction.TriggerType.WAIT_POSITION_DELTA.name();
            case PACKET -> RaceAction.TriggerType.WAIT_PACKET.name();
        };
    }

    private static String normalizeCommand(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }
}
