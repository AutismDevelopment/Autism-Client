package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.KeybindSetting;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismFakeGamemode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class GhostBlockModule extends Module {
    static final String MODULE_DESCRIPTION =
        "Places blocks that only exist for you. Creative mode fakes a creative gamemode so anything you "
            + "place becomes a ghost; right-click places, left-click removes, sneak keeps it real.";
    static final String MODE_TIP = "Creative fakes your gamemode";
    static final String DELAY_TIP = "Ticks between placements";
    static final String BREAK_TIP = "Click ghosts to remove";
    static final String SNEAK_TIP = "Sneak places real blocks";
    static final String CLEAR_DISABLE_TIP = "Clear ghosts on disable";
    static final String CLEAR_KEY_TIP = "Key clears all ghosts";
    static final String HIGHLIGHT_TIP = "Outline ghost blocks";
    static final String HIGHLIGHT_FILL_TIP = "Translucent ghost fill";
    static final String HIGHLIGHT_COLOR_TIP = "Ghost highlight color";

    private static final int MAX_GHOSTS = 4096;
    private static final int BLOCK_BREAK_EFFECT = 2001;
    private static final int GAMEMODE_SYNC_INTERVAL_TICKS = 20;
    private static final int DEFAULT_HIGHLIGHT_COLOR = 0xFF9E9E9E;

    private final Map<BlockPos, BlockState> ghosts = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private Block armedBlock;
    private ClientLevel lastLevel;
    private boolean clearKeyWasDown;
    private boolean fakeGamemodeApplied;
    private int gamemodeSyncTicks;

    private volatile List<AABB> cachedHighlightBoxes = List.of();
    private volatile boolean highlightBoxesDirty;

    GhostBlockModule() {
        super("ghostblock", "GhostBlock", ModuleCategory.PLAYER, MODULE_DESCRIPTION);
        add(new ChoiceSetting("mode", "Mode", "Creative", "Creative", "Survival").description(MODE_TIP).build());
        add(new IntSetting("place-delay", "Place Delay", 4, 1, 20, 1).description(DELAY_TIP).build());
        add(new BoolSetting("break-ghosts", "Click Removes", true).description(BREAK_TIP).build());
        add(new BoolSetting("sneak-bypass", "Sneak Bypass", true).description(SNEAK_TIP).build());
        add(new BoolSetting("clear-on-disable", "Clear On Disable", false).description(CLEAR_DISABLE_TIP).build());
        add(new KeybindSetting("clear-key", "Clear Key", -1).description(CLEAR_KEY_TIP).build());
        add(new BoolSetting("highlight", "Highlight", true).description(HIGHLIGHT_TIP).build());
        add(new BoolSetting("highlight-fill", "Highlight Fill", true).description(HIGHLIGHT_FILL_TIP).build());
        add(new ColorSetting("highlight-color", "Highlight Color", DEFAULT_HIGHLIGHT_COLOR)
            .description(HIGHLIGHT_COLOR_TIP).build());
    }

    @Override
    public void onEnable() {
        syncFakeGamemode();
    }

    @Override
    public void onDisable() {
        if (bool("clear-on-disable")) restoreAll();
        releaseFakeGamemode();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return ghostCount() > 0;
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("mode".equals(settingId)) syncFakeGamemode();
    }

    private void syncFakeGamemode() {
        if (!isEnabled() || MC == null || MC.player == null || MC.gameMode == null) return;
        if (!isCreative()) {
            releaseFakeGamemode();
            return;
        }
        if (fakeGamemodeApplied && AutismFakeGamemode.snapshot().displayedMode() == GameType.CREATIVE) return;
        fakeGamemodeApplied = AutismFakeGamemode.apply(GameType.CREATIVE).success();
    }

    private void releaseFakeGamemode() {
        if (!fakeGamemodeApplied) return;
        fakeGamemodeApplied = false;
        AutismFakeGamemode.reset();
    }

    @Override
    public void onGameJoin() {
        syncFakeGamemode();
    }

    @Override
    public void onGameLeft() {
        clearGhosts();
        releaseFakeGamemode();
        lastLevel = null;
        clearKeyWasDown = false;
    }

    @Override
    public void tick() {
        if (MC == null || MC.level == null || MC.player == null) return;
        if (MC.level != lastLevel) {
            clearGhosts();
            lastLevel = MC.level;
        }
        handleClearKey();

        if (!isEnabled()) {
            pruneLostGhosts();
            return;
        }
        sweepGhosts();

        if (++gamemodeSyncTicks >= GAMEMODE_SYNC_INTERVAL_TICKS) {
            gamemodeSyncTicks = 0;
            syncFakeGamemode();
        }
    }

    private void handleClearKey() {
        int bind = clearKeyCode();
        boolean down = bind != -1 && AutismBindUtil.isBindPressed(MC, bind);
        if (down && !clearKeyWasDown && MC.gui.screen() == null) {
            int removed = restoreAll();
            if (removed > 0) {
                AutismClientMessaging.sendPrefixed("§aCleared §f" + removed + "§a ghost block"
                    + (removed == 1 ? "" : "s") + ".");
            }
        }
        clearKeyWasDown = down;
    }

    private int clearKeyCode() {
        try {
            return Integer.parseInt(value("clear-key"));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void sweepGhosts() {
        List<Map.Entry<BlockPos, BlockState>> snapshot;
        synchronized (ghosts) {
            if (ghosts.isEmpty()) return;
            snapshot = new ArrayList<>(ghosts.entrySet());
        }
        for (Map.Entry<BlockPos, BlockState> entry : snapshot) {
            BlockPos pos = entry.getKey();
            if (!hasChunk(pos)) continue;
            if (MC.level.getBlockState(pos) != entry.getValue()) {
                MC.level.setBlock(pos, entry.getValue(), 3);
            }
        }
    }

    private void pruneLostGhosts() {
        List<Map.Entry<BlockPos, BlockState>> snapshot;
        synchronized (ghosts) {
            if (ghosts.isEmpty()) return;
            snapshot = new ArrayList<>(ghosts.entrySet());
        }
        List<BlockPos> lost = null;
        for (Map.Entry<BlockPos, BlockState> entry : snapshot) {
            BlockPos pos = entry.getKey();

            if (!hasChunk(pos)) continue;
            if (MC.level.getBlockState(pos) == entry.getValue()) continue;
            if (lost == null) lost = new ArrayList<>();
            lost.add(pos);
        }
        if (lost == null) return;
        synchronized (ghosts) {
            for (BlockPos pos : lost) {
                ghosts.remove(pos);
                originals.remove(pos);
            }
            highlightBoxesDirty = true;
        }
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand ignoredHand) {
        if (MC == null || MC.level == null || MC.player == null || autismclient.util.multi.MultiPilot.isActive()) {
            return false;
        }

        if (AutoTotemModule.operationActive()) return false;
        if (!(hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        boolean sneaking = MC.player.isShiftKeyDown();
        if (sneaking && bool("sneak-bypass")) return false;

        if (!sneaking && hasUseAction(MC.level.getBlockState(hit.getBlockPos()), hit.getBlockPos())) return false;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = MC.player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            stack = MC.player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        boolean fromHand = stack.getItem() instanceof BlockItem;
        if (!fromHand) {

            if (!isCreative() || armedBlock == null) return false;
            hand = InteractionHand.MAIN_HAND;
            stack = new ItemStack(armedBlock);
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

        ((AutismMinecraftAccessor) MC).autism$setRightClickDelay(Math.max(1, integer("place-delay")));
        placeGhost(blockItem, hand, stack, hit, fromHand);
        return true;
    }

    private void placeGhost(BlockItem blockItem, InteractionHand hand, ItemStack stack, BlockHitResult hit,
                            boolean fromHand) {
        BlockPlaceContext context = new BlockPlaceContext(MC.player, hand, stack, hit);
        if (!context.canPlace()) return;
        BlockPos clicked = context.getClickedPos();
        if (MC.level.isOutsideBuildHeight(clicked)) return;

        Map<BlockPos, BlockState> before = snapshotAround(clicked);
        ItemStack held = fromHand ? stack.copy() : null;
        boolean placed = blockItem.place(context).consumesAction();
        if (held != null) MC.player.setItemInHand(hand, held);
        if (!placed) return;

        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockState current = MC.level.getBlockState(entry.getKey());
            if (current == entry.getValue()) continue;
            rememberOriginal(entry.getKey(), entry.getValue());
            markGhost(entry.getKey(), current);
        }
        MC.player.swing(hand);
    }

    private Map<BlockPos, BlockState> snapshotAround(BlockPos pos) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        states.put(pos.immutable(), MC.level.getBlockState(pos));
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction).immutable();
            states.put(neighbour, MC.level.getBlockState(neighbour));
        }
        return states;
    }

    @Override
    public boolean shouldCancelAttack(HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        return attackBlock(hit.getBlockPos());
    }

    @Override
    public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
        return attackBlock(pos);
    }

    @Override
    public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
        return attackBlock(pos);
    }

    private boolean attackBlock(BlockPos pos) {
        if (MC == null || MC.level == null || MC.player == null) return false;

        if (AutoTotemModule.operationActive()) return false;
        if (isGhost(pos)) {
            if (bool("break-ghosts")) restoreGhost(pos, true);
            return true;
        }
        if (!isCreative()) return false;
        BlockState state = MC.level.getBlockState(pos);
        if (state.isAir() || MC.level.isOutsideBuildHeight(pos)) return true;
        rememberOriginal(pos, state);
        MC.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        markGhost(pos, MC.level.getBlockState(pos));
        MC.level.levelEvent(null, BLOCK_BREAK_EFFECT, pos, Block.getId(state));
        return true;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!isCreative()) return false;
        if (packet instanceof ServerboundSetCreativeModeSlotPacket) return true;
        if (packet instanceof ServerboundPickItemFromBlockPacket pick) {

            if (!AutoTotemModule.operationActive()) pickBlockLocally(pick.pos(), pick.includeData());
            return true;
        }
        if (packet instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action kind = action.getAction();
            return kind == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || kind == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
        }
        return false;
    }

    private void pickBlockLocally(BlockPos pos, boolean includeData) {
        if (MC == null || MC.level == null || MC.player == null) return;
        BlockState state = MC.level.getBlockState(pos);
        if (state.isAir()) return;
        ItemStack picked = state.getCloneItemStack(MC.level, pos, includeData);
        if (picked.isEmpty()) return;
        Inventory inventory = MC.player.getInventory();
        int existing = inventory.findSlotMatchingItem(picked);
        if (Inventory.isHotbarSlot(existing)) inventory.setSelectedSlot(existing);
        else inventory.setSelectedItem(picked);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {

        if (!isEnabled()) return false;
        if (!(packet instanceof ClientboundBlockUpdatePacket update)) return false;
        synchronized (ghosts) {
            if (!ghosts.containsKey(update.getPos())) return false;
            originals.put(update.getPos(), update.getBlockState());
        }
        return true;
    }

    private boolean hasChunk(BlockPos pos) {
        return MC.level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private void rememberOriginal(BlockPos pos, BlockState original) {
        synchronized (ghosts) {
            originals.putIfAbsent(pos.immutable(), original);
        }
    }

    private void markGhost(BlockPos pos, BlockState state) {
        synchronized (ghosts) {
            ghosts.put(pos.immutable(), state);
            while (ghosts.size() > MAX_GHOSTS) {
                BlockPos eldest = ghosts.keySet().iterator().next();
                ghosts.remove(eldest);
                BlockState original = originals.remove(eldest);
                if (original != null && hasChunk(eldest)) MC.level.setBlock(eldest, original, 3);
            }
            highlightBoxesDirty = true;
        }
    }

    private void clearGhosts() {
        synchronized (ghosts) {
            ghosts.clear();
            originals.clear();
            highlightBoxesDirty = true;
        }
    }

    private void restoreGhost(BlockPos pos, boolean effects) {
        BlockState ghost;
        BlockState original;
        synchronized (ghosts) {
            ghost = ghosts.remove(pos);
            original = originals.remove(pos);
            if (ghost != null) highlightBoxesDirty = true;
        }
        if (ghost == null || MC == null || MC.level == null) return;
        if (!hasChunk(pos)) return;
        if (original != null) MC.level.setBlock(pos, original, 3);
        if (effects) MC.level.levelEvent(null, BLOCK_BREAK_EFFECT, pos, Block.getId(ghost));
    }

    public int restoreAll() {
        List<BlockPos> positions;
        synchronized (ghosts) {
            positions = new ArrayList<>(ghosts.keySet());
        }
        for (BlockPos pos : positions) restoreGhost(pos, false);
        return positions.size();
    }

    public boolean isGhost(BlockPos pos) {
        synchronized (ghosts) {
            return ghosts.containsKey(pos);
        }
    }

    public int ghostCount() {
        synchronized (ghosts) {
            return ghosts.size();
        }
    }

    public boolean isCreative() {
        return "Creative".equals(choice("mode"));
    }

    public void setCreative(boolean creative) {
        setValue("mode", creative ? "Creative" : "Survival");
    }

    public Block armedBlock() {
        return armedBlock;
    }

    public boolean highlightEnabled() {
        return bool("highlight");
    }

    public boolean highlightFill() {
        return bool("highlight-fill");
    }

    public int highlightColor() {
        try {
            String value = value("highlight-color").replace("#", "");
            if (value.length() == 6) value = "FF" + value;
            return (int) Long.parseLong(value, 16);
        } catch (RuntimeException ignored) {
            return DEFAULT_HIGHLIGHT_COLOR;
        }
    }

    public List<AABB> highlightBoxes() {
        if (highlightBoxesDirty) {
            synchronized (ghosts) {
                List<AABB> boxes = new ArrayList<>(ghosts.size());
                for (BlockPos pos : ghosts.keySet()) {
                    boxes.add(new AABB(pos).inflate(0.002D));
                }
                cachedHighlightBoxes = Collections.unmodifiableList(boxes);
                highlightBoxesDirty = false;
            }
        }
        return cachedHighlightBoxes;
    }

    public void setArmedBlock(Block block, boolean announce) {
        armedBlock = block;
        if (announce) {
            AutismClientMessaging.sendPrefixed(block == null
                ? "§7Ghost block disarmed."
                : "§aGhost block: §f" + BuiltInRegistries.BLOCK.getKey(block));
        }
    }

    private boolean hasUseAction(BlockState state, BlockPos pos) {
        if (state.getMenuProvider(MC.level, pos) != null) return true;
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
            || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof NoteBlock
            || block instanceof BedBlock || block instanceof BellBlock || block instanceof CakeBlock
            || block instanceof RespawnAnchorBlock || block instanceof DiodeBlock
            || block instanceof DaylightDetectorBlock || block instanceof DragonEggBlock
            || block instanceof RedStoneWireBlock || block instanceof JukeboxBlock
            || block instanceof FlowerPotBlock || block instanceof BeehiveBlock
            || block instanceof ChiseledBookShelfBlock || block instanceof CandleCakeBlock;
    }

    @Override
    public String info() {
        int count = ghostCount();
        if (count > 0) return Integer.toString(count);
        return armedBlock == null ? "" : BuiltInRegistries.BLOCK.getKey(armedBlock).getPath();
    }
}
