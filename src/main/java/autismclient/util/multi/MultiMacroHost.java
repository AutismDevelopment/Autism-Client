package autismclient.util.multi;

public interface MultiMacroHost {
    enum InventorySyncState {
        READY,
        WAITING,
        BLOCKED
    }

    boolean macroReady();
    default boolean customMenuPhaseActive() { return macroReady(); }
    boolean fullMode();

    default void macroNote(String note) {}

    default autismclient.api.custommenu.CustomMenuSnapshot customMenu() { return null; }
    default autismclient.api.custommenu.CustomMenuSubmitResult submitCustomMenu(
        autismclient.api.custommenu.CustomMenuSnapshot snapshot,
        autismclient.api.custommenu.CustomMenuSubmission submission
    ) { return autismclient.api.custommenu.CustomMenuSubmitResult.failure("Custom menus are unavailable"); }
    default String resolveCustomMenuValue(String template, java.util.Map<String, String> macroVariables) {
        return template == null ? "" : template;
    }

    default String botUsername() { return ""; }
    default String botUuid() { return ""; }
    default String serverAddress() { return ""; }

    default String macroPassword() { return ""; }

    float health();
    float maxHealth();
    int food();
    boolean hasPosition();
    double posX();
    double posY();
    double posZ();
    String dimension();
    String heldItemName();
    int selectedHotbar();
    String openScreenTitle();
    boolean containerOpen();
    long guiOpenSeq();

    int countItem(String query);
    int countItemTarget(autismclient.util.macro.ItemTarget target);
    int freeSlots();
    boolean slotFilled(int visibleSlot);
    boolean cursorEmpty();
    String cursorName();
    boolean cursorMatches(autismclient.util.macro.ItemTarget target);
    float currentPitch();

    int[] heldDurability();
    int[] durabilityAtInv(int inventoryIndex);
    int[] itemDurability(autismclient.util.macro.ItemTarget target);
    long teleportSeq();
    int gameMode();
    long chatSeq();
    java.util.List<String> chatSince(long baselineSeq);

    boolean entityWithin(java.util.List<String> typeRefs, boolean containerOnly, boolean centerOnPlayer,
                         double cx, double cy, double cz, double radius);

    void setPacketCapture(boolean on);
    long packetSeq();
    boolean packetSeen(long baselineSeq, java.util.List<String> targets);

    boolean editSign(autismclient.util.macro.SignEditAction action, String line1, String line2, String line3, String line4);

    void setSoundCapture(boolean on);
    long soundSeq();
    boolean soundMatched(long baselineSeq, java.util.List<String> ids, boolean checkDistance, double maxDistance);

    boolean packetMatched(long baselineSeq, autismclient.util.macro.WaitPacketMatchAction action);

    boolean itemOnCooldown(autismclient.util.macro.ItemTarget target, boolean mainHand);

    String captureItemText(autismclient.util.macro.CaptureValueAction action, autismclient.util.macro.ItemTarget filter);
    java.util.List<String> tablistNames(boolean excludeSelf);
    int requestCommandSuggestions(String command);
    java.util.List<String> commandSuggestions(int requestId);
    java.util.List<autismclient.util.macro.CaptureValueAction.ScoreboardLine> scoreboardLines();
    long containerRevision();

    default InventorySyncState inventorySyncState() { return InventorySyncState.READY; }

    void sendRawPayload(String channel, String rawData);

    boolean blockAt(int x, int y, int z, java.util.List<String> blockIds, boolean anyBlock, boolean wantDestroyed);

    String[] slotChangeBaseline(autismclient.util.macro.WaitForSlotChangeAction action);
    boolean slotChangeMet(autismclient.util.macro.WaitForSlotChangeAction action, String[] baseline);

    java.util.List<int[]> resolveItemClicks(autismclient.util.macro.ItemAction action);

    java.util.List<int[]> resolveStoreClicks(autismclient.util.macro.StoreItemAction action);

    java.util.List<int[]> resolveSwapClicks(autismclient.util.macro.SwapSlotsAction action);

    java.util.List<int[]> resolvePickupAllClicks(autismclient.util.macro.PickUpAllAction action);

    java.util.List<int[]> resolveSequenceClicks(autismclient.util.macro.ContainerClickSequenceAction action);

    void clickResolved(int handlerSlot, int button, int containerInputOrdinal);

    boolean sendPacketBurst(autismclient.util.macro.PacketBurstAction action);

    int writeBook(java.util.List<String> pages, String title, boolean sign, boolean requireHeld, int excludedHotbarMask);
    boolean macroStepMet(autismclient.util.macro.WaitForMacroStepAction action);
    boolean saveGui(boolean closeAfter, boolean sendClosePacket);
    boolean desyncGui();
    boolean restoreGui();

    int runXCarry(autismclient.util.macro.XCarryAction action, long now);
    void cancelXCarry();

    int nearestEntity(String type);
    double[] entityPos(int entityId);

    String runClient(String name, String args);
    void useItemPhase(autismclient.util.macro.UseItemPhaseAction.Phase phase, boolean offhand);
    String chat(String message);
    boolean startSelfMacro(String macroName);
    void stopSelfMacro();
    void disconnectBot(String reason);

    float currentYaw();
    void look(float yaw, float pitch);
    void move(double worldDx, double worldDz, long durationMs);
    void clip(double dx, double dy, double dz, int segments, boolean onGround);
    void setSneak(boolean on);
    void setSprint(boolean on);
    boolean sprinting();
    void jump();
    String interactEntity(int entityId, boolean attack);
    String useOnBlock(int x, int y, int z, String face);
    String breakBlock(int x, int y, int z, String face);
}
