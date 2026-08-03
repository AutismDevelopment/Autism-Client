package autismclient.gui.macro.editor;

import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;
import autismclient.util.macro.WaitForHealthAction;

import java.util.EnumMap;
import java.util.Map;

public final class ActionFieldRegistry {

    private static final ActionFieldSchema EMPTY = ActionFieldSchema.builder().build();
    private static final Map<MacroActionType, ActionFieldSchema> SCHEMAS =
            new EnumMap<>(MacroActionType.class);

    private static final String[] CONDITION_KINDS = {
            "ALWAYS", "HELD_ITEM", "INVENTORY_ITEM", "ITEM_COUNT", "SLOT_EMPTY", "SLOT_FILLED", "SELECTED_SLOT",
            "INVENTORY_FULL", "INVENTORY_EMPTY", "CURSOR_EMPTY", "CURSOR_FILLED", "CURSOR_MATCHES", "FREE_SLOTS",
            "DURABILITY", "HEALTH", "GUI_OPEN", "LOOKING_AT_ENTITY", "LOOKING_AT_CONTAINER_ENTITY", "MOUNTED_ENTITY",
            "ENTITY_NEARBY", "ENTITY_TARGET", "LOOKING_AT_BLOCK", "CONNECTION", "HAS_BUNDLE", "BUNDLE_V2_READY",
            "HAS_WRITABLE_BOOK", "VARIABLE"
    };

    static {

        SCHEMAS.put(MacroActionType.DELAY, ActionFieldSchema.builder()
                .toggle("useTicks",   "Use Ticks")
                .number("delayMs",    "Delay (ms)")   .dynamic().range(0, 300_000) .hideWhen("useTicks")
                .number("delayTicks", "Delay (ticks)").dynamic().range(0, 20_000)  .showWhen("useTicks")
                .build());

        SCHEMAS.put(MacroActionType.FPS, ActionFieldSchema.builder()
                .number   ("maxFps",        "Max FPS (0 = freeze)").dynamic().range(0, 260)
                .enumField("endMode",       "End When", "TIMEOUT", "STEPS", "END")
                .number   ("stepCount",     "Steps").range(1, 999)                       .showWhenEnum("endMode", "STEPS")
                .toggle   ("useTicks",      "Use Ticks")                                  .showWhenEnum("endMode", "TIMEOUT")
                .number   ("durationMs",    "Duration (ms)")   .dynamic().range(0, 300_000).hideWhen("useTicks")
                .number   ("durationTicks", "Duration (ticks)").dynamic().range(0, 20_000) .showWhen("useTicks")
                .toggle   ("continueNextActions", "Continue Next Actions")                .showWhenEnum("endMode", "TIMEOUT")
                .build());

        SCHEMAS.put(MacroActionType.SPING, ActionFieldSchema.builder()
                .number   ("pingDelayMs",   "Add Ping (ms)").dynamic().range(0, 500)
                .toggle   ("realIncoming",  "Real: Incoming")
                .toggle   ("realOutgoing",  "Real: Outgoing")
                .enumField("endMode",       "End When", "TIMEOUT", "STEPS", "END")
                .number   ("stepCount",     "Steps").range(1, 999)                       .showWhenEnum("endMode", "STEPS")
                .toggle   ("useTicks",      "Use Ticks")                                  .showWhenEnum("endMode", "TIMEOUT")
                .number   ("durationMs",    "Duration (ms)")   .dynamic().range(0, 300_000).hideWhen("useTicks")
                .number   ("durationTicks", "Duration (ticks)").dynamic().range(0, 20_000) .showWhen("useTicks")
                .toggle   ("continueNextActions", "Continue Next Actions")                .showWhenEnum("endMode", "TIMEOUT")
                .build());

        SCHEMAS.put(MacroActionType.PACKET, ActionFieldSchema.builder()
                .text  ("description", "Description")
                .toggle("regenerate",  "Regenerate")
                .build());

        SCHEMAS.put(MacroActionType.PACKET_CLICK, ActionFieldSchema.builder()
                .enumField    ("mode",   "Click Mode", "LEFT_CLICK", "RIGHT_CLICK", "QUICK_MOVE")
                .number       ("times",  "Repeats").range(1, 100)
                .toggle       ("queue",  "Queue Exact")
                .targetSummary("target", "Captured")
                .capturePacketClick("target", "Re-capture")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_PACKET, ActionFieldSchema.builder()
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_HEALTH, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .decimal("healthThreshold", "Target Health").dynamic().decRange(0, 20)
                .enumField("comparison", "Condition",
                        WaitForHealthAction.COMPARISON_DROPS_BELOW,
                        WaitForHealthAction.COMPARISON_RISES_ABOVE)
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_ITEM, ActionFieldSchema.builder()
                .stringList("itemNames", "Items").dynamic().addLabel("Add Item").captureItemSlot()
                .toggle    ("waitForGuiBefore", "Before")
                .toggle    ("waitForGuiAfter",  "After")
                .text      ("guiName",     "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.WAIT_BLOCK, ActionFieldSchema.builder()
                .toggle     ("listenDuringPreviousAction", "Listen During Previous")
                .enumField  ("checkMode",    "Check Mode",    "AT_POSITION", "IN_REACH", "LOOKING_AT")
                .enumField  ("waitBehavior", "Wait For",      "PLACED", "DESTROYED")
                .toggle     ("anyBlock",     "Any Block")
                .stringList ("blockIds",     "Block IDs")    .addLabel("Add Block").captureBlock().hideWhen("anyBlock")
                .blockPos   ("pos",          "Position").dynamic().captureBlock().showWhenEnum("checkMode", "AT_POSITION")
                .toggle     ("mustBeInReach","Must Be In Reach").showWhenEnum("checkMode", "AT_POSITION")
                .decimal    ("searchRadius", "Search Radius").decRange(0, 32).showWhenEnum("checkMode", "IN_REACH")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_GUI, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("waitMode", "Wait Mode", "OPEN", "CLOSE")
                .enumField("guiType", "GUI", "ANY", "CONTAINER", "INVENTORY", "SIGN", "HANGING_SIGN", "BOOK", "BOOK_EDIT", "BOOK_SIGN", "BOOK_VIEW", "CHAT", "CUSTOM_MENU")
                .enumField("matchMode", "Match", "MATCH", "CAPTURE", "REGEX")
                .text     ("guiTitle", "Title").dynamic()
                .text     ("saveAs", "Save As")
                .number("timeoutMs", "Timeout ms").dynamic().range(0, 300000)
                .build());

        SCHEMAS.put(MacroActionType.CUSTOM_MENU, ActionFieldSchema.builder()
                .stringList("fieldValues", "Text Field Values (in order)").dynamic().addLabel("Add Value")
                .text("clickButton", "Button To Press").captureMenuButton()
                .number("timeoutMs", "Timeout ms").dynamic().range(100, 120_000)
                .build());

        SCHEMAS.put(MacroActionType.CLICK, ActionFieldSchema.builder()
                .enumField("clickType",  "Click Type", "LEFT", "RIGHT")
                .number   ("clickCount", "Click Count").dynamic().range(1, 100)
                .toggle   ("waitForGuiBefore", "Before")
                .toggle   ("waitForGuiAfter",  "After")
                .text     ("guiName",    "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.ROTATE, ActionFieldSchema.builder()
                .decimal("yaw",               "Yaw")              .dynamic().decRange(-180, 180)
                .decimal("pitch",             "Pitch")            .dynamic().decRange(-90, 90)
                .toggle ("smooth",            "Smooth")
                .number ("smoothness",        "Smoothness")       .range(1, 10).showWhen("smooth")
                .toggle ("waitForCompletion", "Wait for Completion")
                .build());

        SCHEMAS.put(MacroActionType.USE_ITEM, ActionFieldSchema.builder()
                .text     ("itemName", "Item Name").dynamic().captureItemSlot()
                .number   ("slot",     "Slot").dynamic().range(-1, 500)
                .enumField("useMode",  "Use Mode", "AUTOMATIC", "CUSTOM_HOLD")
                .toggle   ("waitForFinish", "Wait Finish")
                .toggle   ("sneak", "Sneak")
                .number   ("holdTicks","Hold Ticks").dynamic().range(1, 1000).showWhenEnum("useMode", "CUSTOM_HOLD")
                .number   ("useCount", "Use Count") .dynamic().range(1, 1000).showWhenEnum("useMode", "AUTOMATIC")
                .toggle   ("waitForGui", "Wait GUI")
                .text     ("guiName", "GUI Name").showWhen("waitForGui")
                .build());

        SCHEMAS.put(MacroActionType.INVENTORY, ActionFieldSchema.builder()
                .enumField("mode",       "Mode", "OPEN", "CLOSE")
                .toggle   ("waitForGuiBefore", "Before").showWhenEnum("mode", "OPEN")
                .toggle   ("waitForGuiAfter",  "After").showWhenEnum("mode", "OPEN")
                .toggle   ("sendPacket", "Close without pkt").showWhenEnum("mode", "CLOSE")
                .build());

        SCHEMAS.put(MacroActionType.SEND_PACKET, ActionFieldSchema.builder()
                .text  ("customName", "Custom Name")
                .toggle("waitForGuiBefore", "Before")
                .toggle("waitForGuiAfter",  "After")
                .text  ("guiName",    "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.PAYLOAD, EMPTY);

        SCHEMAS.put(MacroActionType.CRAFT, EMPTY);

        SCHEMAS.put(MacroActionType.SELECT_SLOT, ActionFieldSchema.builder()
                .slot("slot",     "Slot").dynamic()
                .text("itemName", "Item Name").dynamic().captureItemSlot()
                .enumField("strategy", "Strategy", "FIRST_MATCH", "BEST_DURABILITY", "WORST_DURABILITY", "LARGEST_STACK", "EMPTY_SLOT").dynamic()
                .text("outputVariable", "Save Slot As")
                .build());

        SCHEMAS.put(MacroActionType.XCARRY, ActionFieldSchema.builder()
                .enumField  ("mode",         "Mode",  "PUT_IN", "TAKE_OUT", "DROP")
                .enumField  ("transferMode", "Transfer", "FAST", "CLICK", "SAFE_CLICK").showWhenEnum("mode", "PUT_IN")
                .number     ("safeClickDelayTicks", "Safe Delay").range(0, 10).showWhenEnum("transferMode", "SAFE_CLICK")
                .toggle     ("safeClickDelayAfterPickup", "After Pickup").showWhenEnum("transferMode", "SAFE_CLICK")
                .toggle     ("safeClickDelayBeforeReturn", "Return Delay").showWhenEnum("transferMode", "SAFE_CLICK")
                .stringList ("entries",      "Items").dynamic().addLabel("Add Item").captureItemSlot()
                .build());

        SCHEMAS.put(MacroActionType.DROP, EMPTY);

        SCHEMAS.put(MacroActionType.ITEM, EMPTY);

        SCHEMAS.put(MacroActionType.PICK_UP_ALL, ActionFieldSchema.builder()
                .number("times", "Clicks").range(1, 100)
                .build());

        SCHEMAS.put(MacroActionType.TICK_SYNC, ActionFieldSchema.builder()
                .number("tickOffset",  "Tick Offset")    .range(0, 20)
                .number("preGenCount", "Pre-gen Count")  .range(0, 100)
                .build());

        SCHEMAS.put(MacroActionType.REVISION_SYNC, ActionFieldSchema.builder()
                .number("revisionOffset", "Revision Offset").range(0, 100)
                .number("preGenCount",    "Pre-gen Count")  .range(0, 100)
                .build());

        SCHEMAS.put(MacroActionType.SERVER_TICK_SYNC, ActionFieldSchema.builder()
                .number("bufferMs",    "Buffer (ms)")    .range(0, 5_000)
                .number("maxWaitMs",   "Max Wait (ms)")  .range(100, 60_000)
                .toggle("ignorePing",  "Ignore Ping")
                .number("preGenCount", "Pre-gen Count")  .range(0, 100)
                .build());

        SCHEMAS.put(MacroActionType.CLOSE_GUI, ActionFieldSchema.builder()
                .text  ("guiName",      "GUI Name").dynamic()
                .toggle("useItemFilter","Filter by Item")
                .text  ("itemName",     "Item Name") .dynamic().captureItemSlot().showWhen("useItemFilter")
                .slot  ("targetSlot",   "Target Slot").showWhen("useItemFilter")
                .toggle("sendPacket",   "Close without pkt")
                .build());

        SCHEMAS.put(MacroActionType.SWAP_SLOTS, ActionFieldSchema.builder()
                .toggle("fromUseItemName", "From: Use Item Name")
                .text  ("fromItemName",    "From: Item Name").dynamic().captureItemSlot().showWhen("fromUseItemName")
                .slot  ("fromSlot",        "From: Slot")     .captureItemSlot().hideWhen("fromUseItemName")
                .toggle("toUseItemName",   "To: Use Item Name")
                .text  ("toItemName",      "To: Item Name")  .dynamic().captureItemSlot().showWhen("toUseItemName")
                .slot  ("toSlot",          "To: Slot")       .captureItemSlot().hideWhen("toUseItemName")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_COOLDOWN, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .text  ("itemName",     "Item Name").dynamic().captureItemSlot()
                .toggle("checkMainHand","Check Main Hand")
                .build());

        SCHEMAS.put(MacroActionType.GO_TO, ActionFieldSchema.builder()
                .blockPos("pos",            "Target Position").dynamic().xyzDouble(true)
                .toggle  ("waitForArrival", "Wait for Arrival")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_POS, ActionFieldSchema.builder()
                .toggle  ("listenDuringPreviousAction", "Listen During Previous")
                .blockPos("pos",        "Position")      .dynamic().xyzDouble(true).captureBlock()
                .decimal ("leeway",     "Leeway")        .decRange(0, 100)
                .toggle  ("checkRotation","Check Rotation")
                .decimal ("yaw",        "Yaw")           .decRange(-180, 180).showWhen("checkRotation")
                .decimal ("pitch",      "Pitch")         .decRange(-90, 90)  .showWhen("checkRotation")
                .decimal ("rotLeeway",  "Rotation Leeway").decRange(0, 180)  .showWhen("checkRotation")
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.DISCONNECT, ActionFieldSchema.builder()
                .enumField("mode",          "Mode",         "DISCONNECT", "KICK", "KICK_DUPE", "AUTO_DISCONNECT")
                .number   ("delayMs",       "Delay (ms)")   .range(0, 10_000).showWhenEnum("mode", "DISCONNECT")
                .enumField("lagMethod",     "Lag Method",   "CLICK_SLOT", "BOAT_NBT", "ENTITY_NBT")
                                                           .hideWhenEnum("mode", "DISCONNECT")
                                                           .hideWhenEnum("mode", "AUTO_DISCONNECT")
                .enumField("kickMethod",    "Kick Method",  "HURT", "CLIENT_SETTINGS", "INVALID_SLOT")
                                                           .hideWhenEnum("mode", "DISCONNECT")
                                                           .hideWhenEnum("mode", "AUTO_DISCONNECT")
                .number   ("packetCount",   "Packet Count") .range(1, 1000).hideWhenEnum("mode", "DISCONNECT")
                                                                .hideWhenEnum("mode", "AUTO_DISCONNECT")
                .toggle   ("useNextAction", "Use Next Action").showWhenEnum("mode", "KICK_DUPE")

                .enumField("trigger",       "Trigger",      "TELEPORT", "POSITION", "WORLD_CHANGE", "GUI_CLOSE", "INVENTORY_CLEAR")
                                                           .showWhenEnum("mode", "AUTO_DISCONNECT")
                .decimal  ("tolerance",     "Tolerance")    .decRange(0, 100).showWhenEnum("trigger", "POSITION")
                .number   ("bufferMs",      "Buffer (ms)")  .range(0, 1000).showWhenEnum("mode", "AUTO_DISCONNECT")
                .number   ("timeoutSec",     "Timeout (sec)") .range(1, 300).showWhenEnum("mode", "AUTO_DISCONNECT")
                .build());

        SCHEMAS.put(MacroActionType.TOGGLE_MODULE, ActionFieldSchema.builder()
                .text     ("moduleName",  "Module Name")
                .enumField("toggleMode",  "Toggle Mode", "TOGGLE", "ENABLE", "DISABLE")
                .build());

        SCHEMAS.put(MacroActionType.START_MACRO, ActionFieldSchema.builder()
                .macroSelect("macroName",        "Macro")
                .toggle("restartIfRunning", "Restart If Running")
                .build());

        SCHEMAS.put(MacroActionType.STOP_MACRO, ActionFieldSchema.builder()
                .enumField("target",    "Target", "SELF", "SELECTED", "ALL")
                .macroSelect("macroName", "Macro").showWhenEnum("target", "SELECTED")
                .build());

        SCHEMAS.put(MacroActionType.SNEAK, ActionFieldSchema.builder()
                .toggle("sneak",      "Sneak")
                .toggle("persistent", "Persistent")
                .build());

        SCHEMAS.put(MacroActionType.JUMP, ActionFieldSchema.builder()
                .toggle("tap",           "Tap (single tick)")
                .number("durationTicks", "Duration (ticks)").range(1, 200).hideWhen("tap")
                .build());

        SCHEMAS.put(MacroActionType.SPRINT, ActionFieldSchema.builder()
                .toggle("sprint",     "Sprint")
                .toggle("persistent", "Persistent")
                .build());

        SCHEMAS.put(MacroActionType.MOVE, ActionFieldSchema.builder()
                .enumField("direction",     "Direction",      "FORWARD", "BACKWARD", "LEFT", "RIGHT")
                .number   ("durationTicks", "Duration (ticks)").range(1, 10_000)
                .toggle   ("nonBlocking",   "Non-blocking")
                .build());

        SCHEMAS.put(MacroActionType.LOOK_AT_BLOCK, ActionFieldSchema.builder()
                .enumField("targetMode", "Target Mode", "SPECIFIC", "BLOCK", "ENTITY")
                .blockPos("blockPos", "Block Position").dynamic().xyzKeys("blockX", "blockY", "blockZ").captureBlock().showWhenEnum("targetMode", "SPECIFIC")
                .decimal("searchRadius", "Search Radius").decRange(1, 64).showWhenEnum("targetMode", "BLOCK").showWhenEnum("targetMode", "ENTITY")
                .stringList("blockIds", "Blocks").captureCatalog().showWhenEnum("targetMode", "BLOCK")
                .stringList("entityIds", "Entities").captureEntity().addLabel("Entity").showWhenEnum("targetMode", "ENTITY")
                .toggle("smooth", "Smooth")
                .number("smoothness", "Smoothness").range(1, 10).showWhen("smooth")
                .toggle("waitForCompletion", "Wait for Completion")
                .build());

        SCHEMAS.put(MacroActionType.REPEAT, ActionFieldSchema.builder()
                .number("stepCount",   "Steps to Repeat").range(1, 1000)
                .number("repeatCount", "Repeat Count")   .range(1, 10_000)
                .build());

        SCHEMAS.put(MacroActionType.WAIT_CHAT, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .text  ("pattern",     "Pattern").dynamic()
                .enumField("matchMode", "Match", "MATCH", "CAPTURE", "REGEX")
                .number("fuzzyPercent","Match Strength").range(40, 100).showWhenEnum("matchMode", "MATCH")
                .text  ("saveAs",      "Save As")
                .number("timeoutMs",   "Timeout (ms)")  .range(0, 300_000)
                .toggle("waitForGuiBefore", "Before")
                .toggle("waitForGuiAfter",  "After")
                .text  ("waitGuiName", "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.WAIT_ENTITY, ActionFieldSchema.builder()
                .toggle    ("listenDuringPreviousAction", "Listen During Previous")
                .enumField ("checkMode",       "Check Mode",       "RADIUS", "LOOKING_AT", "WITHIN_REACH", "MOUNTED_IN", "NEARBY")
                .stringList("entityIds",       "Entity IDs")       .addLabel("Add Entity").captureEntity()
                .toggle    ("containerEntitiesOnly", "Containers Only")
                .toggle    ("centerOnPlayer",  "Center on Player").showWhenEnum("checkMode", "RADIUS").showWhenEnum("checkMode", "NEARBY")
                .blockPos  ("pos",             "Position")         .dynamic().xyzDouble(true).captureBlock()
                .decimal   ("radius",          "Radius")           .decRange(0, 100).showWhenEnum("checkMode", "RADIUS").showWhenEnum("checkMode", "NEARBY")
                .toggle    ("mustBeLookingAt", "Must Be Looking At").showWhenEnum("checkMode", "RADIUS")
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_ENTITY_TARGET, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("condition", "Condition", "LOOKING_AT", "WITHIN_REACH", "MOUNTED_IN", "NEARBY")
                .text("entityId", "Entity").dynamic().captureEntity()
                .decimal("range", "Range").decRange(0.0, 256.0)
                .toggle("containerEntitiesOnly", "Containers Only")
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .build());

        SCHEMAS.put(MacroActionType.WAIT_SLOT_CHANGE, ActionFieldSchema.builder()
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.OPEN_CONTAINER, ActionFieldSchema.builder()
                .enumField("targetMode", "Target", "BLOCK", "ENTITY", "LAST_TARGET")
                .blockPos("pos",       "Container Position").dynamic().captureBlock().showWhenEnum("targetMode", "BLOCK")
                .stringList("entityTargets", "Entity").addLabel("Pick Entity").captureEntity().showWhenEnum("targetMode", "ENTITY")
                .toggle  ("waitForTarget", "Wait Target")
                .toggle  ("waitForGuiBefore", "Before")
                .toggle  ("waitForGuiAfter",  "After")
                .text    ("guiName",          "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.INTERACT_ENTITY, ActionFieldSchema.builder()
                .enumField("targetMode", "Target", "ENTITY", "LAST_TARGET")
                .stringList("entityTargets", "Entity").addLabel("Pick Entity").captureEntity().showWhenEnum("targetMode", "ENTITY")
                .toggle  ("waitForTarget", "Wait Target")
                .toggle  ("waitForGuiBefore", "Before")
                .toggle  ("waitForGuiAfter",  "After")
                .text    ("guiName",          "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.DESYNC, EMPTY);

        SCHEMAS.put(MacroActionType.RESTORE_GUI, ActionFieldSchema.builder()
                .toggle("waitForGuiBefore", "Before")
                .toggle("waitForGuiAfter",  "After")
                .build());

        SCHEMAS.put(MacroActionType.SAVE_GUI, ActionFieldSchema.builder()
                .toggle("closeAfter", "Close After Saving")
                .toggle("sendPacket", "Close without pkt").showWhen("closeAfter")
                .build());

        SCHEMAS.put(MacroActionType.SEND_TOGGLE, ActionFieldSchema.builder()
                .enumField("mode", "Mode", "ENABLE", "DISABLE")
                .build());

        SCHEMAS.put(MacroActionType.DELAY_PACKETS, ActionFieldSchema.builder()
                .enumField ("mode",           "Mode",        "ENABLE", "DISABLE")
                .toggle    ("flushOnDisable",  "Flush on Disable").showWhenEnum("mode", "DISABLE")
                .stringList("c2sPackets",      "C2S Packets") .addLabel("Add C2S Packet").showWhenEnum("mode", "ENABLE")
                .stringList("s2cPackets",      "S2C Packets") .addLabel("Add S2C Packet").showWhenEnum("mode", "ENABLE")
                .build());

        SCHEMAS.put(MacroActionType.INVENTORY_AUDIT, ActionFieldSchema.builder()
                .enumField ("mode",              "Mode",                "DUPE", "DUPE_SPAM")
                .stringList("targetItems",       "Targets")            .dynamic().addLabel("Add Item").captureItemSlot()

                .enumField ("openMode",          "Open Method",       "COMMAND", "CONTAINER")
                .text      ("openCommand",       "Open Command").dynamic()
                .blockPos  ("containerPos",      "Container")         .dynamic().xyzKeys("containerX", "containerY", "containerZ").captureBlock()

                .enumField ("dupeVector",        "Dupe Vector", "DESYNC_REOPEN", "CLOSE_NO_PACKET",
                                                        "SHIFT_CLICK_REOPEN", "DELAYED_PACKETS", "SWAP_HOTBAR", "DROP_EXPLOIT",
                                                        "DELAYED_DESYNC_REOPEN", "SWAP_DESYNC_REOPEN", "DROP_DELAYED_PACKETS")
                .number    ("delayBeforeReopen", "Delay Before (ms)") .range(0, 10_000)
                .number    ("delayAfterReopen",  "Delay After (ms)")  .range(0, 10_000)
                .number    ("iterations",        "Iterations")         .range(1, 100)
                .number    ("maxTransferAttempts", "Max Transfers")   .range(1, 20)
                .number    ("transferRetryDelayMs", "Retry Delay (ms)").range(10, 500)
                .toggle    ("multipleStacks",    "Multiple Stacks")

                .number    ("spamCount",         "Spam Count")        .range(1, 20).showWhenEnum("mode", "DUPE_SPAM")
                .number    ("spamDelayMs",         "Spam Delay (ms)")  .range(10, 1000).showWhenEnum("mode", "DUPE_SPAM")
                .build());

        SCHEMAS.put(MacroActionType.STORE_ITEM, ActionFieldSchema.builder()
                .enumField ("mode",        "Mode",               "LOOT", "STORE")
                .toggle    ("allItems",    "All Items")
                .stringList("targetItems", "Target Items")       .dynamic().addLabel("Add Item").captureItemSlot().hideWhen("allItems")
                .toggle    ("persistent",  "Loop Forever")
                .number    ("delayTicks",  "Item Delay").range(0, 200)
                .toggle    ("closeAfter",  "Close After")        .hideWhen("persistent")
                .toggle    ("closeSendPkt","Close without pkt")  .showWhen("closeAfter")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_SOUND, ActionFieldSchema.builder()
                .toggle    ("listenDuringPreviousAction", "Listen During Previous")
                .stringList("soundIds",      "Sound IDs")    .addLabel("Add Sound ID")
                .toggle    ("waitForGuiBefore", "Before")
                .toggle    ("waitForGuiAfter",  "After")
                .text      ("waitGuiName",   "GUI Name").dynamic()
                .toggle    ("checkDistance", "Check Distance")
                .decimal   ("maxDistance",   "Max Distance") .decRange(0, 256).showWhen("checkDistance")
                .text      ("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.MINE, ActionFieldSchema.builder()
                .stringList("targetBlocks",       "Target Blocks")       .addLabel("Add Block").captureCatalog()
                .toggle    ("stopInventoryFull",  "Stop: Inventory Full").exclusiveWith("stopSlotsUsed")
                .toggle    ("stopSlotsUsed",      "Stop: Slots Used")    .exclusiveWith("stopInventoryFull")
                .number    ("slotsUsedThreshold", "Slots Used Threshold").range(1, 36)   .showWhen("stopSlotsUsed")
                .toggle    ("stopMinedCount",     "Stop: Mined Count")
                .number    ("minedCountTarget",   "Mined Count Target")  .range(1, 10_000).showWhen("stopMinedCount")
                .toggle    ("stopAfterTime",      "Stop: After Time")
                .number    ("timeoutSeconds",     "Timeout (seconds)")   .range(1, 86400) .showWhen("stopAfterTime")
                .build());

        SCHEMAS.put(MacroActionType.INSTA_BREAK, ActionFieldSchema.builder()
                .blockPos  ("blockPos",            "Target Block").dynamic().xyzKeys("x", "y", "z").captureBlock()
                .number    ("delayTicks",          "Delay").range(0, 20)
                .number    ("times",               "Times (0 = Infinite)").range(0, 10_000)
                .toggle    ("autoPickaxe",         "Auto Pickaxe")
                .toggle    ("interact",            "Interact")
                .enumField ("interactTiming",      "Interact Timing", "WITH", "BEFORE", "AFTER", "AFTER_PLUS", "CUSTOM").showWhen("interact")
                .number    ("interactCustomMs",    "Custom ms (±)").range(-5000, 5000).showWhenEnum("interactTiming", "CUSTOM")
                .toggle    ("sneak",               "Sneak While Mining")
                .enumField ("sneakMode",           "Sneak Mode", "Packet", "Vanilla").showWhen("sneak")
                .toggle    ("manualDirection",     "Manual Direction")
                .enumField ("direction",           "Direction", "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST").showWhen("manualDirection")
                .toggle    ("waitForGuiBefore",    "Before")
                .toggle    ("waitForGuiAfter",     "After")
                .text      ("guiName",             "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.BREAK, ActionFieldSchema.builder()
                .blockPos  ("blockPos",            "Target Block").dynamic().xyzKeys("x", "y", "z").captureBlock()
                .number    ("delayTicks",          "Start Delay").range(0, 100)
                .number    ("times",               "Times (0 = Infinite)").range(0, 10_000)
                .toggle    ("autoTool",            "Auto Tool")
                .toggle    ("considerInventory",   "Tool From Inventory").showWhen("autoTool")
                .toggle    ("interact",            "Interact (GUI Race)")
                .toggle    ("runNextSteps",        "Run Next Steps").showWhen("interact")
                .enumField ("interactTiming",      "Interact Timing", "WITH", "BEFORE", "AFTER", "AFTER_PLUS", "CUSTOM").showWhen("interact")
                .number    ("interactCustomMs",    "Custom ms (±)").range(-5000, 5000).showWhenEnum("interactTiming", "CUSTOM")
                .toggle    ("sneak",               "Sneak While Mining")
                .enumField ("sneakMode",           "Sneak Mode", "Packet", "Vanilla").showWhen("sneak")
                .toggle    ("manualDirection",     "Manual Direction")
                .enumField ("direction",           "Direction", "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST").showWhen("manualDirection")
                .toggle    ("waitForGuiBefore",    "Before")
                .toggle    ("waitForGuiAfter",     "After")
                .text      ("guiName",             "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.PAY, ActionFieldSchema.builder()
                .text      ("commandTemplate", "Command Template").dynamic()
                .text      ("amountInput",     "Amount").dynamic()
                .toggle    ("divideEnabled",   "Split Among Players")
                .toggle    ("probeHidden",     "Probe Hidden")
                .toggle    ("delayEnabled",    "Use Delay")
                .number    ("delayMs",         "Delay (ms)").range(0, 60_000).showWhen("delayEnabled")
                .stringList("players",         "Players")  .dynamic().addLabel("Add Player")
                .build());

        SCHEMAS.put(MacroActionType.SEND_CHAT, ActionFieldSchema.builder()
                .text  ("message",   "Message").dynamic()
                .toggle("waitForGuiBefore", "Before")
                .toggle("waitForGuiAfter",  "After")
                .text  ("guiName",   "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.NBT_BOOK, ActionFieldSchema.builder()
                .number("pages",      "Pages")      .range(1, 100)
                .number("characters", "Chars/Page").range(1, 1024)
                .text  ("title",      "Title")
                .toggle("sign", "Sign")
                .toggle("appendCount", "Append Count")
                .enumField("dataSource", "Data Source", "Random", "File", "Pasted")
                .enumField("randomType", "Random Type", "Utf8", "Ascii", "PaperMC").showWhenEnum("dataSource", "Random")
                .text  ("customText", "Pasted Text").showWhenEnum("dataSource", "Pasted")
                .text  ("customFilePath", "Text File").showWhenEnum("dataSource", "File")
                .toggle("wordWrap", "Word Wrap").showWhenEnum("dataSource", "File").showWhenEnum("dataSource", "Pasted")
                .number("delayTicks", "Delay (ticks)").range(0, 200)
                .number("bookCount",  "Book Count")   .range(1, 64)
                .toggle("requireHeldWritableBook", "Require Held Book")
                .toggle("dropInventoryBefore",     "Drop Inventory First")
                .toggle("disconnectAfter",         "Disconnect After")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_LAN_STEP, EMPTY);

        SCHEMAS.put(MacroActionType.WAIT_MACRO_STEP, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .macroSelect("macroName", "Macro")
                .enumField("mode",      "Wait For", "COMPLETED_STEP", "STARTED_STEP", "FINISHED")
                .number   ("step",      "Step").range(1, 1000).hideWhenEnum("mode", "FINISHED")
                .number   ("timeoutMs", "Timeout (ms)").range(0, 300_000)
                .build());

        SCHEMAS.put(MacroActionType.WAIT_WORLD_CHANGE, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .text("targetDimension", "Target Dimension")
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_POSITION_DELTA, ActionFieldSchema.builder()
                .toggle ("listenDuringPreviousAction", "Listen During Previous")
                .decimal("distance",       "Distance").decRange(0, 1000)
                .toggle ("horizontalOnly", "Horizontal Only")
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_TELEPORT, ActionFieldSchema.builder()
                .toggle ("listenDuringPreviousAction", "Listen During Previous")
                .decimal("minDistance",    "Min Distance").decRange(0, 1000)
                .toggle ("horizontalOnly", "Horizontal Only")
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_GAMEMODE_CHANGE, ActionFieldSchema.builder()
                .toggle   ("listenDuringPreviousAction", "Listen During Previous")
                .enumField("match", "Match", "ANY_CHANGE", "TO_MODE")
                .enumField("gameMode", "Game Mode", "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").showWhenEnum("match", "TO_MODE")
                .toggle   ("detectFake", "Detect Fake")
                .number   ("timeoutMs", "Timeout ms").range(0, 300_000)
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_MOVEMENT, ActionFieldSchema.builder()
                .toggle   ("listenDuringPreviousAction", "Listen During Previous")
                .enumField("mode", "Wait For", "POSITION", "POSITION_DELTA", "WORLD_CHANGE", "TELEPORT")
                .blockPos ("pos",           "Position").dynamic().xyzDouble(true).captureBlock().showWhenEnum("mode", "POSITION")
                .decimal  ("leeway",        "Leeway").decRange(0, 100).showWhenEnum("mode", "POSITION")
                .toggle   ("checkRotation", "Check Rotation").showWhenEnum("mode", "POSITION")
                .decimal  ("yaw",           "Yaw").decRange(-180, 180).showWhen("checkRotation")
                .decimal  ("pitch",         "Pitch").decRange(-90, 90).showWhen("checkRotation")
                .decimal  ("rotLeeway",     "Rotation Leeway").decRange(0, 180).showWhen("checkRotation")
                .decimal  ("distance",      "Distance").decRange(0, 1000).showWhenEnum("mode", "POSITION_DELTA")
                .decimal  ("minDistance",   "Min Distance").decRange(0, 1000).showWhenEnum("mode", "TELEPORT")
                .toggle   ("horizontalOnly","Horizontal Only").showWhenEnum("mode", "POSITION_DELTA").showWhenEnum("mode", "TELEPORT")
                .text     ("targetDimension","Target Dimension").showWhenEnum("mode", "WORLD_CHANGE")
                .build());

        SCHEMAS.put(MacroActionType.RACE, ActionFieldSchema.builder()
                .text      ("label",       "Label")
                .stringList("raceSteps", "Race Steps").addLabel("Step")
                .number    ("timeoutMs",   "Timeout (ms)").range(0, 600_000)
                .build());

        SCHEMAS.put(MacroActionType.REPORT, ActionFieldSchema.builder()
                .text     ("reportLabel",        "Label")
                .enumField("startActionType",    "Start Action",
                            "ITEM", "PACKET_CLICK", "SEND_CHAT", "PAYLOAD",
                            "CLOSE_GUI", "CLICK", "USE_ITEM", "XCARRY", "DROP",
                            "SELECT_SLOT", "SWAP_SLOTS", "SEND_PACKET",
                            "INVENTORY", "RESTORE_GUI", "SAVE_GUI", "DESYNC",
                            "NBT_BOOK", "PAY", "INSTA_BREAK", "TOGGLE_MODULE",
                            "START_MACRO", "STOP_MACRO", "SNEAK", "JUMP", "SPRINT")
                .enumField("endConditionType",   "End Condition",
                            "WAIT_GUI", "WAIT_CHAT", "WAIT_PACKET",
                            "WAIT_HEALTH", "WAIT_SLOT_CHANGE", "WAIT_BLOCK",
                            "WAIT_ENTITY", "WAIT_COOLDOWN", "WAIT_POS",
                            "WAIT_SOUND", "TICK_SYNC", "REVISION_SYNC",
                            "SERVER_TICK_SYNC", "WAIT_WORLD_CHANGE",
                            "WAIT_POSITION_DELTA", "WAIT_TELEPORT",
                            "WAIT_GAMEMODE_CHANGE")
                .number   ("timeoutMs",          "Timeout (ms)").range(100, 600_000)
                .toggle   ("stashToSharedState", "Stash for later")
                .build());

        SCHEMAS.put(MacroActionType.MULTI, ActionFieldSchema.builder()
                .stringList("accounts",        "Accounts")
                .number    ("stepCount",       "Steps on accounts").range(1, 50)
                .toggle    ("connectIfDown",   "Connect if down")
                .toggle    ("disconnectAfter", "Disconnect when done")
                .enumField ("waitMode",        "Wait trigger", "Until ready", "Fixed delay", "No wait")
                .number    ("waitMs",          "Wait (ms)").range(0, 600_000).hideWhenEnum("waitMode", "No wait")
                .build());

        SCHEMAS.put(MacroActionType.VCLIP, ActionFieldSchema.builder()
                .enumField("mode",              "Mode", "MANUAL", "TOP", "BOTTOM")
                .decimal("deltaY",              "Y Blocks").decRange(-10000, 10000).showWhenEnum("mode", "MANUAL")
                .toggle ("useSegmented",        "Segmented (Paper)")
                .number ("segmentBlocks",       "Segment Size").range(1, 50).showWhen("useSegmented")
                .number ("maxPackets",          "Max Packets").range(1, 100).showWhen("useSegmented")
                .toggle ("forceGrounded",       "Force Grounded")
                .toggle ("updateLocalPosition", "Update Local Pos")
                .toggle ("tryVehicleFirst",     "Use Vehicle If Riding")
                .build());

        SCHEMAS.put(MacroActionType.HCLIP, ActionFieldSchema.builder()
                .enumField("mode",              "Mode", "MANUAL", "FORWARD", "BACK")
                .decimal("blocks",              "Blocks Forward").decRange(-10000, 10000).showWhenEnum("mode", "MANUAL")
                .toggle ("useSegmented",        "Packet Padding")
                .number ("segmentBlocks",       "Padding Size").range(1, 50).showWhen("useSegmented")
                .number ("maxPackets",          "Max Packets").range(1, 100).showWhen("useSegmented")
                .number ("searchRadius",        "Search Radius").range(1, 128)
                .number ("verticalRange",       "Vertical Range").range(0, 32)
                .number ("maxRoutePackets",     "Max Route Packets").range(1, 200)
                .toggle ("forceGrounded",       "On Ground")
                .toggle ("updateLocalPosition", "Update Local Pos")
                .toggle ("tryVehicleFirst",     "Use Vehicle If Riding")
                .build());

        SCHEMAS.put(MacroActionType.TP, ActionFieldSchema.builder()
                .decimal("x",          "X").decRange(-30_000_000, 30_000_000)
                .toggle ("relativeX",  "Relative X")
                .decimal("y",          "Y").decRange(-20_000, 20_000)
                .toggle ("relativeY",  "Relative Y")
                .decimal("z",          "Z").decRange(-30_000_000, 30_000_000)
                .toggle ("relativeZ",  "Relative Z")
                .number ("maxPackets", "Window Packets").range(1, 100)
                .number ("pauseMs",    "Pause (ms)").range(50, 10_000)
                .build());

        SCHEMAS.put(MacroActionType.PACKET_GATE, ActionFieldSchema.builder()
                .enumField("mode", "Mode", "CANCEL", "DELAY", "ALLOW_ONLY")
                .enumField("scope", "Ends", "END_MARKER", "MACRO_PASS")
                .stringList("packetNames", "Packets").addLabel("Add Packet").capturePacketName()
                .toggle("flushOnDisable", "Flush").showWhenEnum("mode", "DELAY")
                .build());

        SCHEMAS.put(MacroActionType.END_PACKET_GATE, ActionFieldSchema.builder()
                .text("gateId", "Gate ID")
                .toggle("flushOnDisable", "Flush")
                .build());

        SCHEMAS.put(MacroActionType.PACKET_BURST, ActionFieldSchema.builder()
                .enumField("mode", "Mode", "CONTAINER_CLICK", "ENTITY_INTERACT", "CLIENT_COMMAND", "BUNDLE_SELECT", "USE_ITEM", "RELEASE_ITEM", "SET_CARRIED_ITEM", "CLIENT_INFORMATION", "CLOSE_CONTAINER")
                .number("count", "Count").range(1, 10000)
                .number("delayTicks", "Delay Ticks").range(0, 2000)
                .slot("slot", "Slot").showWhenEnum("mode", "CONTAINER_CLICK").showWhenEnum("mode", "BUNDLE_SELECT")
                .number("button", "Button").range(0, 10).showWhenEnum("mode", "CONTAINER_CLICK")
                .enumField("containerInput", "Input", "PICKUP", "QUICK_MOVE", "SWAP", "CLONE", "THROW", "QUICK_CRAFT", "PICKUP_ALL").showWhenEnum("mode", "CONTAINER_CLICK")
                .number("entityId", "Entity ID").range(-1, 999999).showWhenEnum("mode", "ENTITY_INTERACT")
                .enumField("hand", "Hand", "MAIN_HAND", "OFF_HAND").showWhenEnum("mode", "ENTITY_INTERACT").showWhenEnum("mode", "USE_ITEM")
                .enumField("playerCommand", "Player Cmd", "OPEN_INVENTORY", "START_FALL_FLYING", "START_SPRINTING", "STOP_SPRINTING").showWhenEnum("mode", "CLIENT_COMMAND")
                .number("bundleIndex", "Bundle Index").range(-2000, 2000).showWhenEnum("mode", "BUNDLE_SELECT")
                .number("carriedSlot", "Carried Slot").range(0, 8).showWhenEnum("mode", "SET_CARRIED_ITEM")
                .number("containerId", "Container ID").range(0, 255).showWhenEnum("mode", "CLOSE_CONTAINER")
                .toggle("flushBefore", "Flush First")
                .build());

        SCHEMAS.put(MacroActionType.BUNDLE_DUPE_V2, ActionFieldSchema.builder()
                .number("hotbarSlot", "Hotbar Slot").range(0, 8)
                .number("bundlePacketCount", "Bundle Packets").range(1, 10000)
                .number("delayAfterPickingUpMs", "Pickup Delay").range(0, 5000)
                .number("delayAfterPuttingBackMs", "Putback Delay").range(0, 5000)
                .number("dropDelayMs", "Drop Delay").range(0, 5000)
                .number("bundleIndex", "Bundle Index").range(-2000, 2000)
                .number("maxCycles", "Max Cycles").range(0, 100000)
                .build());

        SCHEMAS.put(MacroActionType.ROLLBACK, ActionFieldSchema.builder()
                .enumField("scope", "Scope", "ALL_CONTAINER", "CAPTURED_SLOTS", "SLOT_RANGE")
                 .stringList("slots", "Slots").addLabel("Add Slot").showWhenEnum("scope", "CAPTURED_SLOTS")
                 .slot("startSlot", "Start Slot").range(0, 500).showWhenEnum("scope", "SLOT_RANGE")
                 .slot("endSlot", "End Slot").range(0, 500).showWhenEnum("scope", "SLOT_RANGE")
                 .build());

        SCHEMAS.put(MacroActionType.CONTAINER_CLICK_SEQUENCE, ActionFieldSchema.builder()
                .enumField("slotSource", "Slots", "SINGLE", "RANGE", "LIST", "CAPTURED_SEQUENCE")
                .enumField("containerSource", "Container", "CURRENT", "SAVED_GUI", "PLAYER_INVENTORY", "MANUAL")
                .slot("slot", "Slot").showWhenEnum("slotSource", "SINGLE")
                .slot("startSlot", "Start").range(0, 500).showWhenEnum("slotSource", "RANGE")
                .slot("endSlot", "End").range(0, 500).showWhenEnum("slotSource", "RANGE")
                .stringList("slots", "Slot List").addLabel("Add Slot").showWhenEnum("slotSource", "LIST").showWhenEnum("slotSource", "CAPTURED_SEQUENCE")
                .number("manualContainerId", "Manual ID").range(0, 255).showWhenEnum("containerSource", "MANUAL")
                .number("savedContainerId", "Saved ID").range(-1, 255).showWhenEnum("containerSource", "SAVED_GUI")
                .enumField("button", "Button", "0 (Left/Primary)", "1 (Right/Secondary)", "2 (Middle)", "3", "4", "5", "6", "7", "8", "9")
                .enumField("containerInput", "Input", "PICKUP", "QUICK_MOVE", "SWAP", "CLONE", "THROW", "QUICK_CRAFT", "PICKUP_ALL")
                .number("repeatCount", "Repeats").range(1, 1000)
                .number("delayTicks", "Delay Ticks").range(0, 2000)
                .build());

        SCHEMAS.put(MacroActionType.ASSERT, ActionFieldSchema.builder()
                .enumField("check", "Check", "HELD_ITEM", "INVENTORY_ITEM", "GUI_TYPE", "LOOKING_AT_ENTITY", "LOOKING_AT_CONTAINER_ENTITY", "MOUNTED_ENTITY", "HAS_BUNDLE", "BUNDLE_V2_READY", "HAS_WRITABLE_BOOK", "CONNECTION", "LOOKING_AT_BLOCK")
                .enumField("failureBehavior", "On Fail", "STOP_MACRO", "WARN_ONLY")
                .text("itemName", "Item").dynamic().captureItemSlot().showWhenEnum("check", "HELD_ITEM").showWhenEnum("check", "INVENTORY_ITEM")
                .enumField("guiType", "GUI", "ANY", "CONTAINER", "INVENTORY", "SIGN", "HANGING_SIGN", "BOOK", "BOOK_EDIT", "BOOK_SIGN", "BOOK_VIEW", "CHAT").dynamic().showWhenEnum("check", "GUI_TYPE")
                .text("entityId", "Entity").dynamic().captureEntity().showWhenEnum("check", "LOOKING_AT_ENTITY").showWhenEnum("check", "LOOKING_AT_CONTAINER_ENTITY").showWhenEnum("check", "MOUNTED_ENTITY")
                .text("message", "Message").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.USE_ITEM_PHASE, ActionFieldSchema.builder()
                .enumField("phase", "Phase", "USE_ONCE", "START_USE", "RELEASE_USE", "USE_BLOCK", "SWING")
                .text("itemName", "Item").captureItemSlot().hideWhenEnum("phase", "SWING")
                .enumField("hand", "Hand", "MAIN_HAND", "OFF_HAND").hideWhenEnum("phase", "RELEASE_USE")
                .number("repeat", "Repeats").range(1, 1000)

                .number("holdTicks", "Hold Ticks").range(0, 2000).showWhenEnum("phase", "START_USE")
                .toggle("gateDuringHold", "Block Packets While Holding").showWhenEnum("phase", "START_USE")
                .toggle("gatePlayerActions", "Block Player Actions").showWhen("gateDuringHold")
                .toggle("gateContainerClicks", "Block Container Clicks").showWhen("gateDuringHold")
                .toggle("releaseAfterHold", "Release After Hold").showWhenEnum("phase", "START_USE")
                .toggle("useCustomSlotMapping", "Use UI Slots").showWhenEnum("phase", "START_USE")
                .slot("swapSlotBeforeRelease", "Swap Slot (-1 = off)").range(-1, 500).showWhenEnum("phase", "START_USE")
                .number("swapButton", "Swap Hotbar Btn").range(0, 8).showWhenEnum("phase", "START_USE")
                .toggle("dropSlotAfterRelease", "Drop Slot After Release").showWhenEnum("phase", "START_USE")
                .slot("dropSlot", "Drop Slot #").range(0, 500).showWhen("dropSlotAfterRelease")
                .build());

        SCHEMAS.put(MacroActionType.SEND_COMMAND_PACKET, ActionFieldSchema.builder()
                .text("command", "Command").dynamic()
                .toggle("stripLeadingSlash", "Strip Slash")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_PACKET_MATCH, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("direction", "Direction", "C2S", "S2C", "ANY")
                .text("packetName", "Packet")
                .text("fieldName", "Field")
                .enumField("operator", "Operator", "EXISTS", "EQUALS", "CONTAINS", "NOT_EQUALS")
                .text("value", "Value").dynamic()
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_INVENTORY_PREDICATE, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("condition", "Condition", "ITEM_EXISTS", "COUNT_AT_LEAST", "COUNT_CHANGED", "COUNT_INCREASED", "COUNT_DECREASED", "SLOT_EMPTY", "SLOT_FILLED", "SLOT_CHANGED", "INVENTORY_FULL", "INVENTORY_EMPTY", "CURSOR_MATCHES", "CURSOR_EMPTY", "CURSOR_FILLED", "SELECTED_SLOT")
                .text("itemName", "Item").dynamic().captureItemSlot().showWhenEnum("condition", "ITEM_EXISTS").showWhenEnum("condition", "COUNT_AT_LEAST").showWhenEnum("condition", "COUNT_CHANGED").showWhenEnum("condition", "COUNT_INCREASED").showWhenEnum("condition", "COUNT_DECREASED").showWhenEnum("condition", "CURSOR_MATCHES")
                .number("count", "Count").dynamic().range(1, 100000).showWhenEnum("condition", "COUNT_AT_LEAST")
                .slot("slot", "Slot").dynamic().showWhenEnum("condition", "SLOT_EMPTY").showWhenEnum("condition", "SLOT_FILLED").showWhenEnum("condition", "SLOT_CHANGED").showWhenEnum("condition", "SELECTED_SLOT")
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .text("saveAs", "Save As")
                .build());

        SCHEMAS.put(MacroActionType.WAIT_DURABILITY, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("targetMode", "Target", "HELD", "ITEM", "SLOT")
                .text("itemName", "Item").dynamic().captureItemSlot().showWhenEnum("targetMode", "ITEM")
                .slot("slot", "Slot").range(0, 40).showWhenEnum("targetMode", "SLOT")
                .enumField("measurement", "Measure", "REMAINING", "DAMAGE_USED", "PERCENT_REMAINING")
                .enumField("comparison", "Compare", "BELOW", "AT_MOST", "EXACT", "AT_LEAST", "ABOVE")
                .number("value", "Value").range(0, 4096)
                .toggle("useNext", "Use Next")
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .build());

        SCHEMAS.put(MacroActionType.WAIT_FREE_SLOTS, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("countMode", "Count", "FREE_SLOTS", "FILLED_SLOTS")
                .enumField("comparison", "Compare", "BELOW", "AT_MOST", "EXACT", "AT_LEAST", "ABOVE")
                .number("slots", "Slots").range(0, 36)
                .number("timeoutMs", "Timeout ms").range(0, 300000)
                .build());

        SCHEMAS.put(MacroActionType.WAIT_GUI_TYPE, ActionFieldSchema.builder()
                .toggle("listenDuringPreviousAction", "Listen During Previous")
                .enumField("waitMode", "Mode", "OPEN", "CLOSE")
                .enumField("guiType", "GUI", "ANY", "CONTAINER", "INVENTORY", "SIGN", "HANGING_SIGN", "BOOK", "BOOK_EDIT", "BOOK_SIGN", "BOOK_VIEW", "CHAT", "CUSTOM_MENU")
                .enumField("matchMode", "Match", "MATCH", "CAPTURE", "REGEX")
                .text("title", "Title").dynamic()
                .text("saveAs", "Save As")
                .number("timeoutMs", "Timeout ms").dynamic().range(0, 300000)
                .build());

        SCHEMAS.put(MacroActionType.IF, ActionFieldSchema.builder()
                .condition("condition", "Condition")
                .enumField("cond_kind", "Check", CONDITION_KINDS)
                .toggle("cond_negate", "Not")
                .text("cond_item", "Value").dynamic().captureItemSlot()
                        .showWhenEnum("cond_kind", "HELD_ITEM").showWhenEnum("cond_kind", "INVENTORY_ITEM")
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "CURSOR_MATCHES")
                        .showWhenEnum("cond_kind", "GUI_OPEN").showWhenEnum("cond_kind", "LOOKING_AT_ENTITY")
                        .showWhenEnum("cond_kind", "LOOKING_AT_CONTAINER_ENTITY").showWhenEnum("cond_kind", "MOUNTED_ENTITY")
                        .showWhenEnum("cond_kind", "ENTITY_NEARBY").showWhenEnum("cond_kind", "VARIABLE")
                .enumField("cond_op", "Op", "EQ", "NEQ", "LT", "LE", "GT", "GE", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "REGEX", "IS_EMPTY", "IS_TRUE")
                        .showWhenEnum("cond_kind", "VARIABLE")
                .text("cond_value", "Compare To").dynamic().showWhenEnum("cond_kind", "VARIABLE")
                .enumField("cond_cmp", "Compare", "BELOW", "AT_MOST", "EXACT", "AT_LEAST", "ABOVE")
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "HEALTH")
                        .showWhenEnum("cond_kind", "FREE_SLOTS").showWhenEnum("cond_kind", "DURABILITY")
                .number("cond_amount", "Amount").range(0, 1_000_000)
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "HEALTH")
                        .showWhenEnum("cond_kind", "FREE_SLOTS").showWhenEnum("cond_kind", "DURABILITY")
                .slot("cond_slot", "Slot")
                        .showWhenEnum("cond_kind", "SLOT_EMPTY").showWhenEnum("cond_kind", "SLOT_FILLED").showWhenEnum("cond_kind", "SELECTED_SLOT")
                .number("thenSteps", "Then Steps").range(0, 200)
                .number("elseSteps", "Else Steps").range(0, 200)
                .build());

        SCHEMAS.put(MacroActionType.FLOW, ActionFieldSchema.builder()
                .enumField("target", "Go To", "FORWARD", "BACK", "STEP", "LABEL", "TOP", "END", "STOP")
                .number("amount", "Steps").range(0, 100_000)
                        .showWhenEnum("target", "FORWARD").showWhenEnum("target", "BACK").showWhenEnum("target", "STEP")
                .text("labelName", "Label").showWhenEnum("target", "LABEL")
                .enumField("onMissingLabel", "If Missing", "CONTINUE", "STOP").showWhenEnum("target", "LABEL")
                .toggle("conditional", "Only If…")
                .condition("condition", "Condition").showWhen("conditional")
                .enumField("cond_kind", "Check", CONDITION_KINDS).showWhen("conditional")
                .toggle("cond_negate", "Not").showWhen("conditional")
                .text("cond_item", "Value").dynamic().captureItemSlot()
                        .showWhenEnum("cond_kind", "HELD_ITEM").showWhenEnum("cond_kind", "INVENTORY_ITEM")
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "CURSOR_MATCHES")
                        .showWhenEnum("cond_kind", "GUI_OPEN").showWhenEnum("cond_kind", "LOOKING_AT_ENTITY")
                        .showWhenEnum("cond_kind", "LOOKING_AT_CONTAINER_ENTITY").showWhenEnum("cond_kind", "MOUNTED_ENTITY")
                        .showWhenEnum("cond_kind", "ENTITY_NEARBY").showWhenEnum("cond_kind", "VARIABLE")
                .enumField("cond_op", "Op", "EQ", "NEQ", "LT", "LE", "GT", "GE", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "REGEX", "IS_EMPTY", "IS_TRUE")
                        .showWhenEnum("cond_kind", "VARIABLE")
                .text("cond_value", "Compare To").dynamic().showWhenEnum("cond_kind", "VARIABLE")
                .enumField("cond_cmp", "Compare", "BELOW", "AT_MOST", "EXACT", "AT_LEAST", "ABOVE")
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "HEALTH")
                        .showWhenEnum("cond_kind", "FREE_SLOTS").showWhenEnum("cond_kind", "DURABILITY")
                .number("cond_amount", "Amount").range(0, 1_000_000)
                        .showWhenEnum("cond_kind", "ITEM_COUNT").showWhenEnum("cond_kind", "HEALTH")
                        .showWhenEnum("cond_kind", "FREE_SLOTS").showWhenEnum("cond_kind", "DURABILITY")
                .slot("cond_slot", "Slot")
                        .showWhenEnum("cond_kind", "SLOT_EMPTY").showWhenEnum("cond_kind", "SLOT_FILLED").showWhenEnum("cond_kind", "SELECTED_SLOT")
                .build());

        SCHEMAS.put(MacroActionType.LABEL, ActionFieldSchema.builder()
                .text("name", "Label Name")
                .build());

        SCHEMAS.put(MacroActionType.BRANCH, ActionFieldSchema.builder()
                .enumField("conditionKind", "Condition", "ALWAYS", "GUI_TYPE", "INVENTORY_ITEM", "ENTITY_TARGET", "HELD_ITEM")
                .text("value", "Value").dynamic()
                .number("thenSteps", "Then Steps").range(0, 100)
                .number("elseSteps", "Else Steps").range(0, 100)
                .build());

        SCHEMAS.put(MacroActionType.FINALLY, ActionFieldSchema.builder()
                .number("bodyCount", "Cleanup Rows").range(0, 100)
                .build());

        SCHEMAS.put(MacroActionType.MACRO_VARIABLES, ActionFieldSchema.builder()
                .stringList("names", "Names").addLabel("Add Name")
                .stringList("values", "Values").dynamic().addLabel("Add Value")
                .build());

        SCHEMAS.put(MacroActionType.CAPTURE_VALUE, ActionFieldSchema.builder()
                .enumField("source", "Read From", "GUI_TITLE", "RECENT_CHAT", "SCOREBOARD", "GUI_ITEM", "PLAYER_ITEM", "CURSOR_ITEM", "HELD_ITEM", "COMMAND_AUTOFILL", "TABLIST", "NAMESCRAPE")
                .text("saveAs", "Variable Name")
                .text("autofillCommand", "Command").dynamic().showWhenEnum("source", "COMMAND_AUTOFILL")
                .number("autofillTimeoutMs", "Timeout ms").range(0, 60_000).showWhenEnum("source", "COMMAND_AUTOFILL")
                .toggle("autofillCacheList", "Cache List").showWhenEnum("source", "COMMAND_AUTOFILL")
                .enumField("listSelection", "Pick", "RANDOM", "SEQUENTIAL", "RANDOM_NO_REPEAT", "FIRST", "LAST", "POSITION").showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .number("listPickPosition", "Position").range(1, 500).showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .enumField("listFilter", "Filter", "NONE", "PREFIX", "SUFFIX", "CONTAINS", "NOT_CONTAINS", "REGEX").showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .text("listFilterText", "Filter Text").showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .text("listExcludeText", "Exclude").showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .toggle("listStripPrefix", "Strip Matched Part").showWhenEnum("source", "COMMAND_AUTOFILL").showWhenEnum("source", "TABLIST").showWhenEnum("source", "NAMESCRAPE")
                .toggle("excludeSelf", "Exclude Self").showWhenEnum("source", "TABLIST")
                .text("scoreboardRow", "Scoreboard Row").showWhenEnum("source", "SCOREBOARD")
                .number("scoreboardRowIndex", "Scoreboard Position").range(-1, 15).showWhenEnum("source", "SCOREBOARD")
                .text("scoreboardObjective", "Scoreboard Objective").showWhenEnum("source", "SCOREBOARD")
                .text("exampleText", "Example Message").showWhenEnum("source", "GUI_TITLE").showWhenEnum("source", "RECENT_CHAT").showWhenEnum("source", "SCOREBOARD")
                .text("selectedText", "Capture This").showWhenEnum("source", "GUI_TITLE").showWhenEnum("source", "RECENT_CHAT").showWhenEnum("source", "SCOREBOARD")
                .enumField("matchMode", "Read Part", "MATCH", "CAPTURE", "REGEX")
                .text("pattern", "Find / Pattern")
                .text("itemFilter", "Item").dynamic().captureItemSlot().showWhenEnum("source", "GUI_ITEM").showWhenEnum("source", "PLAYER_ITEM")
                .slot("slot", "Slot").dynamic().range(-1, 500).showWhenEnum("source", "GUI_ITEM").showWhenEnum("source", "PLAYER_ITEM")
                .enumField("itemText", "Read", "NAME", "ID", "LORE").showWhenEnum("source", "GUI_ITEM").showWhenEnum("source", "PLAYER_ITEM").showWhenEnum("source", "CURSOR_ITEM").showWhenEnum("source", "HELD_ITEM")
                .toggle("normalizeNumbers", "Normalize Number").hideWhenEnum("source", "COMMAND_AUTOFILL").hideWhenEnum("source", "TABLIST").hideWhenEnum("source", "NAMESCRAPE")
                .enumField("numberModifier", "Modifier", "NONE", "PLUS", "MINUS", "MULTIPLY", "DIVIDE").hideWhenEnum("source", "COMMAND_AUTOFILL").hideWhenEnum("source", "TABLIST").hideWhenEnum("source", "NAMESCRAPE")
                .decimal("numberModifierAmount", "By").decRange(-1_000_000_000, 1_000_000_000)
                    .showWhenEnum("numberModifier", "PLUS").showWhenEnum("numberModifier", "MINUS")
                    .showWhenEnum("numberModifier", "MULTIPLY").showWhenEnum("numberModifier", "DIVIDE")
                .toggle("waitForTrigger", "Wait for Trigger").hideWhenEnum("source", "COMMAND_AUTOFILL").hideWhenEnum("source", "NAMESCRAPE")
                .build());

        SCHEMAS.put(MacroActionType.FAKE_GAMEMODE, ActionFieldSchema.builder()
                .enumField("mode", "Gamemode", "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR", "RESET")
                .enumField("method", "Method", "FAKE", "REAL").hideWhenEnum("mode", "RESET")
                .build());

        SCHEMAS.put(MacroActionType.PLACE, ActionFieldSchema.builder()
                .text("itemName", "Item").dynamic().captureItemSlot()
                .blockPos("blockPos", "Place Against").dynamic().xyzKeys("blockX", "blockY", "blockZ").captureBlock()
                .toggle("manualDirection", "Manual Face")
                .enumField("direction", "Face", "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST").showWhen("manualDirection")
                .toggle("sneak", "Sneak While Placing")
                .enumField("sneakMode", "Sneak Mode", "Packet", "Vanilla").showWhen("sneak")
                .toggle("interact", "Interact Placed")
                .enumField("interactTiming", "Interact Timing", "AFTER", "AFTER_PLUS", "WITH", "BEFORE", "CUSTOM").showWhen("interact")
                .number("interactCustomMs", "Custom ms (±)").range(-5000, 5000).showWhenEnum("interactTiming", "CUSTOM")
                .toggle("waitForGuiBefore", "Before")
                .toggle("waitForGuiAfter", "After")
                .text("guiName", "GUI Name").dynamic()
                .build());

        SCHEMAS.put(MacroActionType.SIGN_EDIT, ActionFieldSchema.builder()
                .enumField("targetMode", "Target", "CURRENT_SIGN_GUI", "LAST_INTERACTED_BLOCK", "MANUAL_POS")
                .text("line1", "Line 1").dynamic()
                .text("line2", "Line 2").dynamic()
                .text("line3", "Line 3").dynamic()
                .text("line4", "Line 4").dynamic()
                .toggle("frontText", "Front")
                .number("x", "X").range(-30000000, 30000000).showWhenEnum("targetMode", "MANUAL_POS")
                .number("y", "Y").range(-2048, 2048).showWhenEnum("targetMode", "MANUAL_POS")
                .number("z", "Z").range(-30000000, 30000000).showWhenEnum("targetMode", "MANUAL_POS")
                .toggle("waitForGuiBefore", "Wait Before")
                .toggle("waitForGuiAfter", "Wait After")
                .enumField("guiName", "GUI", "SIGN", "HANGING_SIGN", "ANY")
                .enumField("closeMode", "Close", "STAY_OPEN", "CLOSE_LOCAL", "CLOSE_WITH_PACKET", "SEND_CLOSE_PACKET_ONLY")
                .toggle("sendCommandAfter", "Run Command After")
                .text("commandAfter", "Command").dynamic().showWhen("sendCommandAfter")
                .number("closePacketContainerId", "Close Container ID").range(0, 255).showWhenEnum("closeMode", "SEND_CLOSE_PACKET_ONLY")
                .build());

    }

    public static ActionFieldSchema get(MacroActionType type) {
        return SCHEMAS.getOrDefault(type, EMPTY);
    }

    public static ActionFieldSchema get(MacroAction action) {
        if (action == null) return EMPTY;
        MacroActionType type = action.getType();
        if (type != null) return get(type);
        ActionFieldSchema addonSchema = autismclient.api.macro.MacroActionRegistry.schema(action.getTypeId());
        return addonSchema != null ? addonSchema : EMPTY;
    }

    private ActionFieldRegistry() {}
}
