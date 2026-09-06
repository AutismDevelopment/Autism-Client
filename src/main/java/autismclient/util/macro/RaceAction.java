package autismclient.util.macro;

import autismclient.util.AutismPacketClick;
import autismclient.util.AutismMacro;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RaceAction implements MacroAction {
    public enum TriggerType {
        DELAY,
        WAIT_GUI,
        WAIT_CHAT,
        WAIT_PACKET,
        WAIT_HEALTH,
        WAIT_SLOT_CHANGE,
        WAIT_BLOCK,
        WAIT_ENTITY,
        WAIT_COOLDOWN,
        WAIT_POS,
        WAIT_SOUND,
        WAIT_PACKET_MATCH,
        WAIT_INVENTORY_PREDICATE,
        WAIT_DURABILITY,
        WAIT_FREE_SLOTS,
        WAIT_MOVEMENT,
        WAIT_LAN_STEP,
        WAIT_MACRO_STEP,
        TICK_SYNC,
        REVISION_SYNC,
        SERVER_TICK_SYNC,
        WAIT_WORLD_CHANGE,
        WAIT_POSITION_DELTA,
        WAIT_TELEPORT,
        WAIT_GAMEMODE_CHANGE
    }

    public String label = "Race";
    public MacroAction triggerAction = new DelayAction(0);
    public int bodyCount = 0;
    public int timeoutMs = 10_000;
    public String conditionType = TriggerType.WAIT_PACKET.name();
    public String actionType = MacroActionType.PACKET_CLICK.name();
    public ArrayList<String> raceSteps = new ArrayList<>();
    public ArrayList<String> conditionTypes = new ArrayList<>();
    public ArrayList<String> actionTypes = new ArrayList<>();

    private transient boolean legacyNeedsMigration = false;
    private transient boolean legacyUseNextAction = false;
    private transient AutismPacketClick.Target legacyTarget = null;
    private transient int legacyTimes = 1;

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("label", label == null ? "" : label);
        tag.putInt("bodyCount", Math.max(0, bodyCount));
        tag.putInt("timeoutMs", Math.max(0, timeoutMs));
        tag.putString("conditionType", conditionType == null ? TriggerType.WAIT_PACKET.name() : conditionType);
        tag.putString("actionType", actionType == null ? MacroActionType.PACKET_CLICK.name() : actionType);
        tag.put("raceSteps", MacroStringList.toTag(effectiveRaceSteps()));
        tag.put("conditionTypes", MacroStringList.toTag(effectiveConditionTypes()));
        tag.put("actionTypes", MacroStringList.toTag(effectiveActionTypes()));
        MacroAction trigger = triggerAction == null ? new DelayAction(0) : triggerAction;
        if (!effectiveConditionTypes().isEmpty()) {
            tag.put("triggerAction", trigger.toTag());
            writeFlatTriggerFields(tag, trigger);
        }
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        label = tag.getStringOr("label", "Race");
        bodyCount = Math.max(0, tag.getIntOr("bodyCount", 0));
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 10_000));
        conditionType = canonicalTriggerName(tag.getStringOr("conditionType", tag.getStringOr("triggerType", TriggerType.WAIT_PACKET.name())));
        actionType = tag.getStringOr("actionType", MacroActionType.PACKET_CLICK.name());
        raceSteps = MacroStringList.fromTag(tag.getList("raceSteps").orElse(new ListTag()));
        raceSteps.removeIf(s -> s == null || s.isBlank());
        boolean hasConditionTypes = tag.contains("conditionTypes");
        conditionTypes = MacroStringList.fromTag(tag.getList("conditionTypes").orElse(new ListTag()));
        conditionTypes.removeIf(s -> s == null || s.isBlank());
        if (!hasConditionTypes && conditionType != null && !conditionType.isBlank()) conditionTypes.add(conditionType);
        actionTypes = MacroStringList.fromTag(tag.getList("actionTypes").orElse(new ListTag()));
        if (actionTypes.isEmpty() && actionType != null && !actionType.isBlank()) actionTypes.add(actionType);
        actionTypes.removeIf(s -> s == null || s.isBlank());
        if (actionTypes.isEmpty()) actionTypes.add(MacroActionType.PACKET_CLICK.name());
        conditionTypes.replaceAll(RaceAction::canonicalTriggerName);
        if (raceSteps.isEmpty()) {
            for (String type : conditionTypes) raceSteps.add("CONDITION:" + canonicalTriggerName(type));
            for (String type : actionTypes) raceSteps.add("ACTION:" + canonicalActionName(type));
        } else {
            raceSteps.replaceAll(RaceAction::canonicalRaceStep);
            raceSteps.removeIf(s -> s == null || s.isBlank());
            syncLegacyListsFromRaceSteps();
            if (actionTypes.isEmpty()) actionTypes.add(MacroActionType.PACKET_CLICK.name());
        }
        conditionType = conditionTypes.isEmpty() ? "" : conditionTypes.get(0);
        actionType = actionTypes.get(0);

        MacroAction nested = tag.getCompound("triggerAction")
            .map(autismclient.util.AutismMacro::createActionFromTag)
            .orElse(null);
        if (isConditionAction(nested)) {
            triggerAction = nested;
        }

        if (tag.contains("triggerType")) {
            triggerAction = triggerFromFlatFields(tag);
        } else if (tag.contains("trigger")) {
            triggerAction = migrateLegacyTrigger(tag);
            legacyNeedsMigration = true;
            legacyUseNextAction = tag.getBooleanOr("useNextAction", false);
            legacyTimes = Math.max(1, tag.getIntOr("times", 1));
            legacyTarget = tag.getCompound("target").map(AutismPacketClick.Target::fromTag).orElse(null);
        }

        if (!isConditionAction(triggerAction)) {
            triggerAction = new DelayAction(0);
        }
    }

    public boolean needsLegacyMigration() {
        return legacyNeedsMigration;
    }

    public boolean legacyUseNextAction() {
        return legacyUseNextAction;
    }

    public PacketClickAction consumeLegacyPacketClickAction() {
        if (legacyTarget == null) return null;
        PacketClickAction action = new PacketClickAction(legacyTarget, legacyTimes, false);
        legacyTarget = null;
        return action;
    }

    public void clearLegacyMigration() {
        legacyNeedsMigration = false;
        legacyUseNextAction = false;
        legacyTarget = null;
    }

    public static boolean isConditionAction(MacroAction action) {
        return action instanceof DelayAction
            || action instanceof WaitForPacketAction
            || action instanceof WaitForHealthAction
            || action instanceof WaitForBlockAction
            || action instanceof WaitForGuiAction
            || action instanceof WaitForCooldownAction
            || action instanceof WaitPosAction
            || action instanceof WaitForChatAction
            || action instanceof WaitForEntityAction
            || action instanceof WaitForSlotChangeAction
            || action instanceof WaitForSoundAction
            || action instanceof WaitPacketMatchAction
            || action instanceof WaitInventoryPredicateAction
            || action instanceof WaitDurabilityAction
            || action instanceof WaitFreeSlotsAction
            || action instanceof WaitMovementAction
            || action instanceof WaitForLanStepAction
            || action instanceof WaitForMacroStepAction
            || action instanceof TickSyncAction
            || action instanceof RevisionSyncAction
            || action instanceof ServerTickSyncAction
            || action instanceof WaitForWorldChangeAction
            || action instanceof WaitForPositionDeltaAction
            || action instanceof WaitForTeleportAction
            || action instanceof WaitGamemodeChangeAction;
    }

    public static boolean isBodyAction(MacroAction action) {
        if (action == null) return false;
        if (isConditionAction(action)) return false;
        return !(action instanceof RaceAction)
            && !(action instanceof ReportAction)
            && !(action instanceof MultiAction)
            && !(action instanceof RepeatAction)
            && !(action instanceof DisconnectAction)
            && !(action instanceof GoToAction)
            && !(action instanceof MoveAction);
    }

    public MacroAction createSelectedConditionAction() {
        return defaultTrigger(parseTriggerType(conditionType));
    }

    public List<String> effectiveConditionTypes() {
        ArrayList<String> out = new ArrayList<>();
        if (conditionTypes != null) {
            for (String type : conditionTypes) {
                String canonical = canonicalTriggerName(type);
                if (canonical != null && !canonical.isBlank()) out.add(canonical);
            }
        }
        return out;
    }

    public List<MacroAction> createSelectedConditionActions() {
        ArrayList<MacroAction> out = new ArrayList<>();
        for (String typeName : effectiveConditionTypes()) out.add(createConditionAction(typeName));
        return out;
    }

    public MacroAction createSelectedBodyAction() {
        return createBodyAction(actionType);
    }

    public List<String> effectiveRaceSteps() {
        ArrayList<String> out = new ArrayList<>();
        if (raceSteps != null) {
            for (String step : raceSteps) {
                String canonical = canonicalRaceStep(step);
                if (!canonical.isBlank()) out.add(canonical);
            }
        }
        if (out.isEmpty()) {
            for (String type : effectiveConditionTypes()) out.add("CONDITION:" + type);
            for (String type : effectiveActionTypes()) out.add("ACTION:" + type);
        }
        return out;
    }

    public List<String> effectiveActionTypes() {
        ArrayList<String> out = new ArrayList<>();
        if (actionTypes != null) {
            for (String type : actionTypes) {
                if (type != null && !type.isBlank()) out.add(type.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (out.isEmpty()) out.add(actionType == null || actionType.isBlank() ? MacroActionType.PACKET_CLICK.name() : actionType.trim().toUpperCase(Locale.ROOT));
        return out;
    }

    public List<MacroAction> createSelectedBodyActions() {
        ArrayList<MacroAction> out = new ArrayList<>();
        for (String typeName : effectiveActionTypes()) out.add(createBodyAction(typeName));
        return out;
    }

    public static boolean isConditionStep(String step) {
        return step != null && step.trim().toUpperCase(Locale.ROOT).startsWith("CONDITION:");
    }

    public static boolean isActionStep(String step) {
        return step != null && step.trim().toUpperCase(Locale.ROOT).startsWith("ACTION:");
    }

    public static String stepTypeName(String step) {
        if (step == null) return "";
        int idx = step.indexOf(':');
        return (idx >= 0 ? step.substring(idx + 1) : step).trim().toUpperCase(Locale.ROOT);
    }

    public static MacroAction createStepAction(String step) {
        return isConditionStep(step) ? createConditionAction(stepTypeName(step)) : createBodyAction(stepTypeName(step));
    }

    public static MacroAction createBodyAction(String typeName) {
        MacroActionType type;
        try { type = MacroActionType.valueOf(typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { type = MacroActionType.PACKET_CLICK; }
        MacroAction action = switch (type) {
            case PACKET_CLICK -> new PacketClickAction();
            case SEND_CHAT -> new SendChatAction();
            case PAYLOAD -> new PayloadAction();
            case CLOSE_GUI -> new CloseGuiAction();
            case CLICK -> new ClickAction();
            case USE_ITEM -> new UseItemAction();
            case ITEM -> new ItemAction();
            case PICK_UP_ALL -> new PickUpAllAction();
            case XCARRY -> new XCarryAction();
            case DROP -> new DropAction();
            case SELECT_SLOT -> new SelectSlotAction();
            case SWAP_SLOTS -> new SwapSlotsAction();
            case SEND_PACKET -> new SendPacketAction();
            case INVENTORY -> new InventoryAction();
            case RESTORE_GUI -> new RestoreGuiAction();
            case SAVE_GUI -> new SaveGuiAction();
            case DESYNC -> new DesyncAction();
            case NBT_BOOK -> new NbtBookAction();
            case PAY -> new PayAction();
            case INSTA_BREAK -> new InstaBreakAction();
            case BREAK -> new BreakAction();
            case TOGGLE_MODULE -> new ToggleModuleAction();
            case START_MACRO -> new StartMacroAction();
            case STOP_MACRO -> new StopMacroAction();
            case SNEAK -> new SneakAction();
            case JUMP -> new JumpAction();
            case SPRINT -> new SprintAction();
            case PACKET_GATE -> new PacketGateAction();
            case PACKET_BURST -> new PacketBurstAction();
            case CONTAINER_CLICK_SEQUENCE -> new ContainerClickSequenceAction();
            case ASSERT -> new AssertAction();
            case USE_ITEM_PHASE -> new UseItemPhaseAction();
            case SEND_COMMAND_PACKET -> new SendCommandPacketAction();
            case SIGN_EDIT -> new SignEditAction();
            case MACRO_VARIABLES -> new MacroVariablesAction();
            case FAKE_GAMEMODE -> new FakeGamemodeAction();
            case BUNDLE_DUPE_V2 -> new BundleDupeV2Action();
            case VCLIP -> new VClipAction();
            case HCLIP -> new HClipAction();
            case TP -> new PacedTpAction();
            case MINE -> new MineAction();
            default -> new PacketClickAction();
        };
        return isBodyAction(action) ? action : new PacketClickAction();
    }

    public static TriggerType triggerTypeFor(MacroAction action) {
        if (action instanceof WaitForGuiAction) return TriggerType.WAIT_GUI;
        if (action instanceof WaitForChatAction) return TriggerType.WAIT_CHAT;
        if (action instanceof WaitForPacketAction) return TriggerType.WAIT_PACKET;
        if (action instanceof WaitForHealthAction) return TriggerType.WAIT_HEALTH;
        if (action instanceof WaitForSlotChangeAction) return TriggerType.WAIT_SLOT_CHANGE;
        if (action instanceof WaitForBlockAction) return TriggerType.WAIT_BLOCK;
        if (action instanceof WaitForEntityAction) return TriggerType.WAIT_ENTITY;
        if (action instanceof WaitForCooldownAction) return TriggerType.WAIT_COOLDOWN;
        if (action instanceof WaitForSoundAction) return TriggerType.WAIT_SOUND;
        if (action instanceof WaitPacketMatchAction) return TriggerType.WAIT_PACKET_MATCH;
        if (action instanceof WaitInventoryPredicateAction) return TriggerType.WAIT_INVENTORY_PREDICATE;
        if (action instanceof WaitDurabilityAction) return TriggerType.WAIT_DURABILITY;
        if (action instanceof WaitFreeSlotsAction) return TriggerType.WAIT_FREE_SLOTS;
        if (action instanceof WaitMovementAction) return TriggerType.WAIT_MOVEMENT;
        if (action instanceof WaitForLanStepAction) return TriggerType.WAIT_LAN_STEP;
        if (action instanceof WaitForMacroStepAction) return TriggerType.WAIT_MACRO_STEP;
        if (action instanceof WaitGamemodeChangeAction) return TriggerType.WAIT_GAMEMODE_CHANGE;
        if (action instanceof WaitPosAction
            || action instanceof WaitForWorldChangeAction
            || action instanceof WaitForPositionDeltaAction
            || action instanceof WaitForTeleportAction) return TriggerType.WAIT_MOVEMENT;
        if (action instanceof TickSyncAction) return TriggerType.TICK_SYNC;
        if (action instanceof RevisionSyncAction) return TriggerType.REVISION_SYNC;
        if (action instanceof ServerTickSyncAction) return TriggerType.SERVER_TICK_SYNC;
        return TriggerType.DELAY;
    }

    public static MacroAction createConditionAction(String typeName) {
        return defaultTrigger(parseTriggerType(typeName));
    }

    private static MacroAction defaultTrigger(TriggerType type) {
        return switch (type) {
            case WAIT_GUI -> new WaitForGuiAction();
            case WAIT_CHAT -> new WaitForChatAction();
            case WAIT_PACKET -> new WaitForPacketAction();
            case WAIT_HEALTH -> new WaitForHealthAction();
            case WAIT_SLOT_CHANGE -> new WaitForSlotChangeAction();
            case WAIT_BLOCK -> new WaitForBlockAction();
            case WAIT_ENTITY -> new WaitForEntityAction();
            case WAIT_COOLDOWN -> new WaitForCooldownAction();
            case WAIT_POS -> new WaitPosAction();
            case WAIT_SOUND -> new WaitForSoundAction();
            case WAIT_PACKET_MATCH -> new WaitPacketMatchAction();
            case WAIT_INVENTORY_PREDICATE -> new WaitInventoryPredicateAction();
            case WAIT_DURABILITY -> new WaitDurabilityAction();
            case WAIT_FREE_SLOTS -> new WaitFreeSlotsAction();
            case WAIT_MOVEMENT -> new WaitMovementAction();
            case WAIT_LAN_STEP -> new WaitForLanStepAction();
            case WAIT_MACRO_STEP -> new WaitForMacroStepAction();
            case TICK_SYNC -> new TickSyncAction();
            case REVISION_SYNC -> new RevisionSyncAction();
            case SERVER_TICK_SYNC -> new ServerTickSyncAction();
            case WAIT_WORLD_CHANGE -> new WaitForWorldChangeAction();
            case WAIT_POSITION_DELTA -> new WaitForPositionDeltaAction();
            case WAIT_TELEPORT -> new WaitForTeleportAction();
            case WAIT_GAMEMODE_CHANGE -> new WaitGamemodeChangeAction();
            case DELAY -> new DelayAction(0);
        };
    }

    private static MacroAction triggerFromFlatFields(CompoundTag tag) {
        TriggerType type = parseTriggerType(tag.getStringOr("triggerType", TriggerType.DELAY.name()));
        MacroAction action = defaultTrigger(type);
        CompoundTag actionTag = AutismMacro.serializeAction(action);
        switch (type) {
            case DELAY -> {
                actionTag.putBoolean("useTicks", tag.getBooleanOr("useTicks", false));
                actionTag.putInt("delayMs", tag.getIntOr("delayMs", 0));
                actionTag.putInt("delayTicks", tag.getIntOr("delayTicks", 1));
            }
            case WAIT_GUI -> {
                actionTag.putString("waitMode", tag.getStringOr("waitMode", "OPEN"));
                actionTag.putString("guiTitle", tag.getStringOr("guiTitle", ""));
            }
            case WAIT_CHAT -> {
                actionTag.putString("pattern", tag.getStringOr("pattern", ""));
                actionTag.putBoolean("useRegex", tag.getBooleanOr("useRegex", false));
                actionTag.putInt("fuzzyPercent", tag.getIntOr("fuzzyPercent", 100));
            }
            case WAIT_PACKET -> {
                actionTag.putString("packetName", tag.getStringOr("packetName", ""));
                copyStringList(tag, actionTag, "packetNames");
            }
            case WAIT_HEALTH -> {
                actionTag.putFloat("healthThreshold", (float) tag.getDoubleOr("healthThreshold", 20.0));
                actionTag.putString("comparison", tag.getStringOr("comparison", WaitForHealthAction.COMPARISON_DROPS_BELOW));
            }
            case WAIT_BLOCK -> {
                actionTag.putString("checkMode", tag.getStringOr("checkMode", "AT_POSITION"));
                actionTag.putString("waitBehavior", tag.getStringOr("waitBehavior", "PRESENT"));
                actionTag.putBoolean("anyBlock", tag.getBooleanOr("anyBlock", false));
                copyStringList(tag, actionTag, "blockIds");
                actionTag.putInt("x", tag.getIntOr("blockX", tag.getIntOr("x", 0)));
                actionTag.putInt("y", tag.getIntOr("blockY", tag.getIntOr("y", 0)));
                actionTag.putInt("z", tag.getIntOr("blockZ", tag.getIntOr("z", 0)));
                actionTag.putDouble("searchRadius", tag.getDoubleOr("searchRadius", 6.0));
            }
            case WAIT_ENTITY -> {
                actionTag.putString("checkMode", tag.getStringOr("entityCheckMode", "RADIUS"));
                copyStringList(tag, actionTag, "entityIds");
                actionTag.putBoolean("centerOnPlayer", tag.getBooleanOr("centerOnPlayer", true));
                actionTag.putDouble("radius", tag.getDoubleOr("radius", 8.0));
                actionTag.putDouble("x", tag.getDoubleOr("entityX", 0.0));
                actionTag.putDouble("y", tag.getDoubleOr("entityY", 0.0));
                actionTag.putDouble("z", tag.getDoubleOr("entityZ", 0.0));
            }
            case WAIT_COOLDOWN -> {
                actionTag.putString("itemName", tag.getStringOr("itemName", ""));
                actionTag.putBoolean("checkMainHand", tag.getBooleanOr("checkMainHand", true));
            }
            case WAIT_POS -> {
                actionTag.putDouble("x", tag.getDoubleOr("x", 0.0));
                actionTag.putDouble("y", tag.getDoubleOr("y", 0.0));
                actionTag.putDouble("z", tag.getDoubleOr("z", 0.0));
                actionTag.putDouble("leeway", tag.getDoubleOr("leeway", 1.0));
                actionTag.putBoolean("checkRotation", tag.getBooleanOr("checkRotation", false));
                actionTag.putFloat("yaw", (float) tag.getDoubleOr("yaw", 0.0));
                actionTag.putFloat("pitch", (float) tag.getDoubleOr("pitch", 0.0));
                actionTag.putFloat("rotLeeway", (float) tag.getDoubleOr("rotLeeway", 5.0));
            }
            case WAIT_SOUND -> {
                copyStringList(tag, actionTag, "soundIds");
                actionTag.putBoolean("checkDistance", tag.getBooleanOr("checkDistance", false));
                actionTag.putDouble("maxDistance", tag.getDoubleOr("maxDistance", 16.0));
            }
            case WAIT_POSITION_DELTA -> {
                actionTag.putDouble("distance", tag.getDoubleOr("distance", 5.0));
                actionTag.putBoolean("horizontalOnly", tag.getBooleanOr("horizontalOnly", false));
            }
            default -> {
            }
        }
        action.fromTag(actionTag);
        return action;
    }

    private static MacroAction migrateLegacyTrigger(CompoundTag tag) {
        String legacy = tag.getStringOr("trigger", "MANUAL_MS").toUpperCase(Locale.ROOT);
        CompoundTag flat = new CompoundTag();
        switch (legacy) {
            case "POSITION_JUMP" -> {
                flat.putString("triggerType", TriggerType.WAIT_POSITION_DELTA.name());
                flat.putDouble("distance", tag.getDoubleOr("positionTolerance", 5.0));
            }
            case "WORLD_CHANGE" -> flat.putString("triggerType", TriggerType.WAIT_WORLD_CHANGE.name());
            case "GUI_OPEN_BY_TITLE" -> {
                flat.putString("triggerType", TriggerType.WAIT_GUI.name());
                flat.putString("waitMode", "OPEN");
                flat.putString("guiTitle", tag.getStringOr("guiTitleSubstr", ""));
            }
            case "GUI_CLOSE_BY_TITLE" -> {
                flat.putString("triggerType", TriggerType.WAIT_GUI.name());
                flat.putString("waitMode", "CLOSE");
                flat.putString("guiTitle", tag.getStringOr("guiTitleSubstr", ""));
            }
            case "TELEPORT_PACKET" -> {
                flat.putString("triggerType", TriggerType.WAIT_PACKET.name());
                flat.putString("packetName", "S2C:ClientboundPlayerPositionPacket");
            }
            case "CHAT_MATCH" -> {
                flat.putString("triggerType", TriggerType.WAIT_CHAT.name());
                flat.putString("pattern", tag.getStringOr("chatPattern", ""));
                flat.putBoolean("useRegex", tag.getBooleanOr("chatUseRegex", false));
            }
            case "S2C_PACKET_CLASS" -> {
                flat.putString("triggerType", TriggerType.WAIT_PACKET.name());
                flat.putString("packetName", "S2C:" + tag.getStringOr("s2cPacketClassName", ""));
            }
            case "HEALTH_BELOW" -> {
                flat.putString("triggerType", TriggerType.WAIT_HEALTH.name());
                flat.putDouble("healthThreshold", tag.getDoubleOr("healthBelowThreshold", 10.0));
                flat.putString("comparison", WaitForHealthAction.COMPARISON_DROPS_BELOW);
            }
            case "SLOT_CHANGE", "ITEM_FOUND" -> flat.putString("triggerType", TriggerType.WAIT_SLOT_CHANGE.name());
            case "BLOCK_STATE" -> {
                flat.putString("triggerType", TriggerType.WAIT_BLOCK.name());
                ListTag blockIds = new ListTag();
                String blockId = tag.getStringOr("blockId", "");
                if (!blockId.isBlank()) blockIds.add(StringTag.valueOf(blockId));
                flat.put("blockIds", blockIds);
                flat.putInt("blockX", tag.getIntOr("blockX", 0));
                flat.putInt("blockY", tag.getIntOr("blockY", 0));
                flat.putInt("blockZ", tag.getIntOr("blockZ", 0));
            }
            case "ENTITY_NEAR" -> {
                flat.putString("triggerType", TriggerType.WAIT_ENTITY.name());
                copyStringList(tag, flat, "entityNearIds", "entityIds");
                flat.putDouble("radius", tag.getDoubleOr("entityNearRadius", 8.0));
            }
            case "COOLDOWN_READY" -> {
                flat.putString("triggerType", TriggerType.WAIT_COOLDOWN.name());
                flat.putString("itemName", tag.getStringOr("cooldownItemName", ""));
            }
            default -> {
                flat.putString("triggerType", TriggerType.DELAY.name());
                flat.putInt("delayMs", tag.getIntOr("manualDelayMs", 0));
            }
        }
        return triggerFromFlatFields(flat);
    }

    private static void writeFlatTriggerFields(CompoundTag tag, MacroAction action) {
        TriggerType type = triggerTypeFor(action);
        tag.putString("triggerType", type.name());
        CompoundTag src = AutismMacro.serializeAction(action);
        for (String key : src.keySet()) {
            if ("type".equals(key) || tag.contains(key)) continue;
            tag.put(key, src.get(key));
        }
        if (action instanceof WaitForBlockAction block) {
            tag.putInt("blockX", block.blockPos == null ? 0 : block.blockPos.getX());
            tag.putInt("blockY", block.blockPos == null ? 0 : block.blockPos.getY());
            tag.putInt("blockZ", block.blockPos == null ? 0 : block.blockPos.getZ());
        }
        if (action instanceof WaitForEntityAction entity) {
            tag.putString("entityCheckMode", entity.checkMode == null ? "NEAR_PLAYER" : entity.checkMode.name());
            tag.putDouble("entityX", entity.x);
            tag.putDouble("entityY", entity.y);
            tag.putDouble("entityZ", entity.z);
        }
    }

    private static TriggerType parseTriggerType(String raw) {
        try { return TriggerType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return TriggerType.DELAY; }
    }

    private static String canonicalTriggerName(String raw) {
        String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "WAIT_POS", "WAIT_WORLD_CHANGE", "WAIT_POSITION_DELTA", "WAIT_TELEPORT" -> TriggerType.WAIT_MOVEMENT.name();
            default -> key;
        };
    }

    private static String canonicalActionName(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    public static String canonicalRaceStep(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.isBlank()) return "";
        int idx = text.indexOf(':');
        String role = idx >= 0 ? text.substring(0, idx).trim().toUpperCase(Locale.ROOT) : "";
        String type = idx >= 0 ? text.substring(idx + 1).trim() : text;
        if ("CONDITION".equals(role) || "COND".equals(role)) return "CONDITION:" + canonicalTriggerName(type);
        if ("ACTION".equals(role) || "ACT".equals(role)) return "ACTION:" + canonicalActionName(type);
        String condition = canonicalTriggerName(type);
        try {
            TriggerType.valueOf(condition);
            return "CONDITION:" + condition;
        } catch (IllegalArgumentException ignored) {
            return "ACTION:" + canonicalActionName(type);
        }
    }

    public void syncLegacyListsFromRaceSteps() {
        conditionTypes = new ArrayList<>();
        actionTypes = new ArrayList<>();
        for (String step : effectiveRaceSteps()) {
            String type = stepTypeName(step);
            if (isConditionStep(step)) conditionTypes.add(canonicalTriggerName(type));
            else if (isActionStep(step)) actionTypes.add(canonicalActionName(type));
        }
    }

    private static void copyStringList(CompoundTag from, CompoundTag to, String key) {
        copyStringList(from, to, key, key);
    }

    private static void copyStringList(CompoundTag from, CompoundTag to, String fromKey, String toKey) {
        ListTag out = new ListTag();
        from.getList(fromKey).ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                list.getString(i).ifPresent(s -> {
                    if (!s.isBlank()) out.add(StringTag.valueOf(s));
                });
            }
        });
        to.put(toKey, out);
    }

    public int normalizedBodyCount(List<MacroAction> actions, int headerIndex) {
        if (actions == null || headerIndex < 0 || headerIndex >= actions.size()) return 0;
        int max = Math.max(0, Math.min(bodyCount, actions.size() - headerIndex - 1));
        int count = 0;
        for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction action = actions.get(i);
            if (action instanceof RaceAction || action instanceof ReportAction) break;
            count++;
        }
        return count;
    }

    public int normalizedConditionCount(List<MacroAction> actions, int headerIndex) {
        int body = normalizedBodyCount(actions, headerIndex);
        int count = 0;
        for (int offset = 1; offset <= body && headerIndex + offset < actions.size(); offset++) {
            if (!isConditionAction(actions.get(headerIndex + offset))) continue;
            count++;
        }
        return count;
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.RACE;
    }

    @Override
    public String getDisplayName() {
        String name = label == null || label.isBlank() ? "Race" : label.trim();
        List<String> conditions = effectiveConditionTypes();
        String trigger = conditions.isEmpty() ? "instant" : (conditions.size() == 1 ? conditions.get(0) : conditions.size() + " conditions");
        List<String> actions = effectiveActionTypes();
        String action = actions.size() == 1 ? actions.get(0) : actions.size() + " actions";
        return name + " [" + trigger + " -> " + action + "]";
    }

    @Override
    public String getIcon() {
        return "Rce";
    }
}
