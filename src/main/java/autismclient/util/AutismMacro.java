package autismclient.util;

import autismclient.util.macro.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AutismMacro {
    public String name = "New Macro";
    public String description = "";
    public boolean loop = false;
    public int loopCount = -1;
    public int keyCode = -1;
    public List<MacroAction> actions = new ArrayList<>();

    public AutismMacro() {}

    public AutismMacro(String name) {
        this.name = name;
    }

    public AutismMacro deepCopy() {
        return new AutismMacro().fromTag(this.toTag());
    }

    public AutismMacro deepCopy(String newName) {
        AutismMacro copy = deepCopy();
        if (newName != null && !newName.isBlank()) {
            copy.name = newName;
        }
        return copy;
    }

    public AutismMacro sanitizeForSharing() {
        if (actions != null) {
            for (MacroAction action : actions) {
                if (action != null) action.sanitizeForSharing();
            }
        }
        return this;
    }

    public CompoundTag toShareableTag() {
        return deepCopy().sanitizeForSharing().toTag();
    }

    public void execute() {
        execute(true);
    }

    public void execute(boolean regenerate) {
        executeTracked(regenerate);
    }

    public long executeTracked() {
        return executeTracked(true);
    }

    public long executeTracked(boolean regenerate) {
        if (actions == null || actions.isEmpty()) {
            AutismClientMessaging.sendPrefixed("§cMacro has no actions!");
            return -1L;
        }

        if (regenerate) {
            regenerateAllPackets();
        }

        if (autismclient.util.multi.MultiTakeoverState.isActive()) {
            autismclient.util.multi.MultiManager multi = autismclient.util.multi.MultiManager.getIfInitialized();
            if (multi == null || !multi.isActive()) {
                AutismNotifications.error("The POV Multi session is no longer active.");
                return -1L;
            }
            autismclient.util.multi.MultiManager.BroadcastResult result =
                multi.runMacroDirectInteractive(this, java.util.Set.of());
            AutismNotifications.show("Run on POV: " + result.summary() + ".", 0xFF57F287);
            return -1L;
        }
        return MacroExecutor.executeTracked(this);
    }

    public void regenerateAllPackets() {
        if (actions == null) return;
        for (MacroAction action : actions) {
            if (action instanceof SendPacketAction) {
                ((SendPacketAction) action).regeneratePackets();
            }
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("description", description);
        tag.putBoolean("loop", loop);
        tag.putInt("loopCount", loopCount);
        tag.putInt("keyCode", keyCode);

        ListTag actionsList = new ListTag();
        if (actions != null) {
            for (MacroAction action : actions) {
                if (action != null) actionsList.add(serializeAction(action));
            }
        }
        tag.put("actions", actionsList);

        return tag;
    }

    public AutismMacro fromTag(CompoundTag tag) {
        if (tag.contains("name")) name = tag.getStringOr("name", "");
        if (tag.contains("description")) description = tag.getStringOr("description", "");
        if (tag.contains("loop")) loop = tag.getBooleanOr("loop", false);
        if (tag.contains("loopCount")) loopCount = tag.getIntOr("loopCount", -1);
        if (tag.contains("keyCode")) keyCode = tag.getIntOr("keyCode", -1);

        Tag rawActions = tag.get("actions");
        if (rawActions instanceof ListTag actionsList) {
            List<MacroAction> loadedActions = new ArrayList<>();
            for (Tag element : actionsList) {
                if (!(element instanceof CompoundTag actionTag)) continue;
                MacroAction action = createActionFromTag(actionTag);
                if (action != null) loadedActions.add(action);
            }
            this.actions = loadedActions;
            migrateLegacyRaceGroups();
        }
        return this;
    }

    public static MacroAction createActionFromTag(CompoundTag actionTag) {
        if (actionTag == null || !actionTag.contains("type")) return null;
        try {
            String typeName = actionTag.getStringOr("type", "");
            MacroAction migratedLegacy = createHardDebloatMigration(typeName, actionTag);
            if (migratedLegacy != null) return migratedLegacy;
            MacroActionType type = MacroActionType.valueOf(typeName);
            MacroAction action = createBuiltInAction(type);
            if (type == MacroActionType.WAIT_ITEM) {
                ((WaitForSlotChangeAction) action).fromTagLegacyItem(actionTag);
            } else {
                action.fromTag(actionTag);
            }
            MacroDynamicBindings.load(action, actionTag);
            return action;
        } catch (IllegalArgumentException e) {
            return recoverUnknownAction(actionTag);
        } catch (Throwable t) {
            String typeName = actionTag.getStringOr("type", "");
            autismclient.AutismClientAddon.LOG.warn("[Macros] Failed to deserialize action '{}'; preserving its data", typeName, t);
            return new MissingAddonAction(typeName.isBlank() ? "invalid:action" : typeName, actionTag.copy());
        }
    }

    public static CompoundTag serializeAction(MacroAction action) {
        CompoundTag tag = action == null ? new CompoundTag() : action.toTag();
        MacroDynamicBindings.write(action, tag);
        return tag;
    }

    private static final java.util.Set<String> BUILT_IN_ACTION_TYPES;
    static {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (MacroActionType t : MacroActionType.values()) s.add(t.name());
        BUILT_IN_ACTION_TYPES = java.util.Set.copyOf(s);
    }

    public static boolean isBuiltInActionType(String type) {
        return type != null && BUILT_IN_ACTION_TYPES.contains(type);
    }

    public static int stripToBuiltInActions(CompoundTag macroTag) {
        if (macroTag == null) return 0;
        if (!(macroTag.get("actions") instanceof ListTag actionsList)) return 0;
        ListTag kept = new ListTag();
        int dropped = 0;
        for (Tag element : actionsList) {
            if (element instanceof CompoundTag actionTag && isBuiltInActionType(actionTag.getStringOr("type", ""))) {
                kept.add(actionTag);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) macroTag.put("actions", kept);
        return dropped;
    }

    private static MacroAction createBuiltInAction(MacroActionType type) {
        return switch (type) {
            case DELAY -> new DelayAction();
            case PACKET -> new PacketAction();
            case PACKET_CLICK -> new PacketClickAction();
            case WAIT_PACKET -> new WaitForPacketAction();
            case WAIT_HEALTH -> new WaitForHealthAction();
            case WAIT_ITEM, WAIT_SLOT_CHANGE -> new WaitForSlotChangeAction();
            case WAIT_BLOCK -> new WaitForBlockAction();
            case WAIT_GUI -> new WaitForGuiAction();
            case CUSTOM_MENU -> new CustomMenuAction();
            case CLICK -> new ClickAction();
            case ROTATE -> new RotateAction();
            case USE_ITEM -> new UseItemAction();
            case INVENTORY -> new InventoryAction();
            case SEND_PACKET -> new SendPacketAction();
            case CRAFT -> new CraftAction();
            case SELECT_SLOT -> new SelectSlotAction();
            case XCARRY -> new XCarryAction();
            case DROP -> new DropAction();
            case ITEM -> new ItemAction();
            case PICK_UP_ALL -> new PickUpAllAction();
            case TICK_SYNC -> new TickSyncAction();
            case REVISION_SYNC -> new RevisionSyncAction();
            case SERVER_TICK_SYNC -> new ServerTickSyncAction();
            case CLOSE_GUI -> new CloseGuiAction();
            case SWAP_SLOTS -> new SwapSlotsAction();
            case WAIT_COOLDOWN -> new WaitForCooldownAction();
            case GO_TO -> new GoToAction();
            case WAIT_POS -> new WaitPosAction();
            case PAYLOAD -> new PayloadAction();
            case DISCONNECT -> new DisconnectAction();
            case TOGGLE_MODULE -> new ToggleModuleAction();
            case START_MACRO -> new StartMacroAction();
            case STOP_MACRO -> new StopMacroAction();
            case SNEAK -> new SneakAction();
            case JUMP -> new JumpAction();
            case SPRINT -> new SprintAction();
            case MOVE -> new MoveAction();
            case LOOK_AT_BLOCK -> new LookAtBlockAction();
            case REPEAT -> new RepeatAction();
            case WAIT_CHAT -> new WaitForChatAction();
            case WAIT_ENTITY -> new WaitForEntityAction();
            case OPEN_CONTAINER -> new OpenContainerAction();
            case INTERACT_ENTITY -> new InteractEntityAction();
            case DESYNC -> new DesyncAction();
            case RESTORE_GUI -> new RestoreGuiAction();
            case SAVE_GUI -> new SaveGuiAction();
            case SEND_TOGGLE -> new SendToggleAction();
            case DELAY_PACKETS -> new DelayPacketsAction();
            case INVENTORY_AUDIT -> new InventoryAuditAction();
            case STORE_ITEM -> new StoreItemAction();
            case WAIT_SOUND -> new WaitForSoundAction();
            case MINE -> new MineAction();
            case INSTA_BREAK -> new InstaBreakAction();
            case BREAK -> new BreakAction();
            case PAY -> new PayAction();
            case NBT_BOOK -> new NbtBookAction();
            case SEND_CHAT -> new SendChatAction();
            case WAIT_LAN_STEP -> new WaitForLanStepAction();
            case WAIT_MACRO_STEP -> new WaitForMacroStepAction();
            case WAIT_WORLD_CHANGE -> new WaitForWorldChangeAction();
            case WAIT_POSITION_DELTA -> new WaitForPositionDeltaAction();
            case WAIT_TELEPORT -> new WaitForTeleportAction();
            case WAIT_GAMEMODE_CHANGE -> new WaitGamemodeChangeAction();
            case WAIT_MOVEMENT -> new WaitMovementAction();
            case RACE -> new RaceAction();
            case REPORT -> new ReportAction();
            case VCLIP -> new VClipAction();
            case HCLIP -> new HClipAction();
            case TP -> new PacedTpAction();
            case PACKET_GATE -> new PacketGateAction();
            case END_PACKET_GATE -> new EndPacketGateAction();
            case PACKET_BURST -> new PacketBurstAction();
            case CONTAINER_CLICK_SEQUENCE -> new ContainerClickSequenceAction();
            case ASSERT -> new AssertAction();
            case USE_ITEM_PHASE -> new UseItemPhaseAction();
            case SEND_COMMAND_PACKET -> new SendCommandPacketAction();
            case WAIT_PACKET_MATCH -> new WaitPacketMatchAction();
            case WAIT_INVENTORY_PREDICATE -> new WaitInventoryPredicateAction();
            case WAIT_DURABILITY -> new WaitDurabilityAction();
            case WAIT_FREE_SLOTS -> new WaitFreeSlotsAction();
            case WAIT_ENTITY_TARGET -> new WaitEntityTargetAction();
            case WAIT_GUI_TYPE -> new WaitGuiTypeAction();
            case BRANCH -> new BranchAction();
            case FINALLY -> new FinallyAction();
            case MACRO_VARIABLES -> new MacroVariablesAction();
            case CAPTURE_VALUE -> new CaptureValueAction();
            case FAKE_GAMEMODE -> new FakeGamemodeAction();
            case BUNDLE_DUPE_V2 -> new BundleDupeV2Action();
            case ROLLBACK -> new RollbackAction();
            case PLACE -> new PlaceAction();
            case SIGN_EDIT -> new SignEditAction();
            case FPS -> new FpsAction();
            case SPING -> new SPingAction();
            case IF -> new IfAction();
            case FLOW -> new FlowAction();
            case LABEL -> new LabelAction();
            case MULTI -> new MultiAction();
        };
    }

    private static MacroAction recoverUnknownAction(CompoundTag actionTag) {
        if (actionTag == null) return null;
        String typeName = actionTag.getStringOr("type", "");
        if (typeName.isEmpty()) return null;
        java.util.function.Supplier<MacroAction> factory = autismclient.api.macro.MacroActionRegistry.factory(typeName);
        if (factory != null) {

            try {
                MacroAction action = factory.get();
                if (action != null) {
                    action.fromTag(actionTag);
                    MacroDynamicBindings.load(action, actionTag);
                    return action;
                }
            } catch (Throwable t) {
                autismclient.AutismClientAddon.LOG.warn("[MacroActions] Addon action '{}' failed to deserialize; keeping as placeholder", typeName, t);
            }
        }
        return new MissingAddonAction(typeName, actionTag.copy());
    }

    private static MacroAction createHardDebloatMigration(String typeName, CompoundTag actionTag) {
        if ("WAIT_ENTITY_TARGET".equals(typeName)) {
            WaitForEntityAction migrated = new WaitForEntityAction();
            migrated.fromLegacyTargetTag(actionTag);
            return migrated;
        }
        if ("WAIT_GUI_TYPE".equals(typeName)) {
            WaitForGuiAction migrated = new WaitForGuiAction();
            migrated.fromGuiTypeTag(actionTag);
            return migrated;
        }
        return null;
    }

    private void migrateLegacyRaceGroups() {
        if (actions == null || actions.isEmpty()) return;
        for (int i = 0; i < actions.size(); i++) {
            if (!(actions.get(i) instanceof RaceAction race) || !race.needsLegacyMigration()) continue;

            int inserted = 0;
            PacketClickAction packetClick = race.consumeLegacyPacketClickAction();
            if (packetClick != null) {
                actions.add(i + 1, packetClick);
                inserted++;
            }

            if (race.legacyUseNextAction()) {
                int j = i + 1 + inserted;
                while (j < actions.size()) {
                    MacroAction candidate = actions.get(j);
                    if (!RaceAction.isBodyAction(candidate)) break;
                    inserted++;
                    j++;
                }
            }

            race.bodyCount = inserted;
            race.clearLegacyMigration();
            i += inserted;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutismMacro that = (AutismMacro) o;
        return MacroNames.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(MacroNames.key(name));
    }
}
