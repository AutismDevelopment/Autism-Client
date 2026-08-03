package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class AutismContainerTarget {
    public enum Kind { BLOCK, ENTITY_INTERACT, ENTITY_INTERACT_AT }

    private final Kind kind;
    private final BlockPos blockPos;
    private final String entityRef;
    private final InteractionHand hand;
    private final Vec3 hitPos;
    private final Direction blockSide;
    private final boolean insideBlock;

    private AutismContainerTarget(Kind kind, BlockPos blockPos, String entityRef, InteractionHand hand, Vec3 hitPos, Direction blockSide, boolean insideBlock) {
        this.kind = kind;
        this.blockPos = blockPos == null ? null : blockPos.immutable();
        this.entityRef = entityRef == null ? "" : entityRef;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.hitPos = hitPos;
        this.blockSide = blockSide;
        this.insideBlock = insideBlock;
    }

    public static AutismContainerTarget forBlock(BlockPos pos) {
        if (pos == null) return null;
        return new AutismContainerTarget(Kind.BLOCK, pos, "", InteractionHand.MAIN_HAND, null, null, false);
    }

    public static AutismContainerTarget forBlockHit(BlockHitResult hitResult, InteractionHand hand) {
        if (hitResult == null || hitResult.getBlockPos() == null) return null;
        return new AutismContainerTarget(
            Kind.BLOCK,
            hitResult.getBlockPos(),
            "",
            hand,
            hitResult.getLocation(),
            hitResult.getDirection(),
            hitResult.isInside()
        );
    }

    public static AutismContainerTarget forEntity(Entity entity, InteractionHand hand) {
        if (entity == null) return null;
        return new AutismContainerTarget(Kind.ENTITY_INTERACT, null, toSpecificEntityRef(entity), hand, null, null, false);
    }

    public static AutismContainerTarget forEntityAt(Entity entity, InteractionHand hand, Vec3 hitPos) {
        if (entity == null) return null;
        return new AutismContainerTarget(Kind.ENTITY_INTERACT_AT, null, toSpecificEntityRef(entity), hand, hitPos, null, false);
    }

    public static AutismContainerTarget forEntityRef(String entityRef) {
        if (entityRef == null || entityRef.isBlank()) return null;
        return new AutismContainerTarget(Kind.ENTITY_INTERACT, null, entityRef.trim(), InteractionHand.MAIN_HAND, null, null, false);
    }

    public static String toSpecificEntityRef(Entity entity) {
        if (entity == null) return "";
        String uuid = entity.getStringUUID();
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String displayName = entity.getDisplayName().getString().replaceAll("\u00a7.", "").trim();
        return "~" + uuid + "~" + typeId + "~" + displayName;
    }

    public Kind kind() {
        return kind;
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    public String entityRef() {
        return entityRef;
    }

    public boolean isBlock() {
        return kind == Kind.BLOCK;
    }

    public boolean interact(Minecraft mc) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.getConnection() == null) return false;

        if (kind == Kind.BLOCK) {
            if (blockPos == null) return false;
            if (mc.player == null || mc.gameMode == null) return false;
            BlockHitResult hitResult = resolveBlockHitResult(mc);
            if (hitResult == null) return false;
            mc.gameMode.useItemOn(mc.player, hand, hitResult);
            return true;
        }

        Entity entity = resolveEntity(mc);
        if (entity == null) return false;

        if (kind == Kind.ENTITY_INTERACT_AT && hitPos != null) {
            mc.getConnection().send(new ServerboundInteractPacket(entity.getId(), hand, hitPos, mc.player.isShiftKeyDown()));
            return true;
        }

        mc.getConnection().send(new ServerboundInteractPacket(entity.getId(), hand, Vec3.ZERO, mc.player.isShiftKeyDown()));
        return true;
    }

    public boolean canInteract(Minecraft mc) {
        if (mc == null || mc.player == null) return false;

        if (kind == Kind.BLOCK) {
            return blockPos != null && isWithinBlockReach(mc, blockPos) && resolveBlockHitResult(mc) != null;
        }

        Entity entity = resolveEntity(mc);
        return isWithinEntityReach(mc, entity);
    }

    private BlockHitResult resolveBlockHitResult(Minecraft mc) {
        if (blockSide != null && hitPos != null) {
            return new BlockHitResult(hitPos, blockSide, blockPos, insideBlock);
        }
        return resolveBlockHit(mc, blockPos);
    }

    public static BlockHitResult resolveBlockHit(Minecraft mc, BlockPos blockPos) {
        if (blockPos == null) return null;
        Vec3 center = Vec3.atCenterOf(blockPos);
        if (mc == null || mc.player == null) {
            return new BlockHitResult(center, Direction.UP, blockPos, false);
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 delta = center.subtract(eyePos);
        Direction side = Direction.getApproximateNearest(delta.x, delta.y, delta.z).getOpposite();
        return resolveBlockHit(blockPos, side);
    }

    public static BlockHitResult resolveBlockHit(BlockPos blockPos, Direction face) {
        if (blockPos == null || face == null) return null;
        Vec3 center = Vec3.atCenterOf(blockPos);
        Vec3 faceCenter = center.add(
            face.getStepX() * 0.5D,
            face.getStepY() * 0.5D,
            face.getStepZ() * 0.5D
        );
        return new BlockHitResult(faceCenter, face, blockPos, false);
    }

    public static boolean isWithinBlockReach(Minecraft mc, BlockPos blockPos) {
        if (mc == null || mc.player == null || blockPos == null) return false;
        double reach = Math.max(4.5D, mc.player.blockInteractionRange());
        return mc.player.distanceToSqr(Vec3.atCenterOf(blockPos)) <= reach * reach;
    }

    public static boolean isWithinEntityReach(Minecraft mc, Entity entity) {
        if (mc == null || mc.player == null || entity == null || entity == mc.player) return false;
        double reach = Math.max(4.5D, mc.player.blockInteractionRange());
        return entity.distanceToSqr(mc.player) <= reach * reach;
    }

    private Entity resolveEntity(Minecraft mc) {
        if (mc == null || mc.level == null) return null;

        String uuid = "";
        String typeId = entityRef;
        if (entityRef.startsWith("~")) {
            String[] parts = entityRef.split("~", 4);
            uuid = parts.length >= 2 ? parts[1] : "";
            typeId = parts.length >= 3 ? parts[2] : "";
        }

        Entity nearestTypeMatch = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            if (!uuid.isEmpty() && uuid.equals(entity.getStringUUID())) {
                return entity;
            }
            if (typeId.isBlank()) continue;
            String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!typeId.equalsIgnoreCase(entityTypeId)) continue;
            double distance = mc.player == null ? 0.0 : entity.distanceToSqr(mc.player);
            if (distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearestTypeMatch = entity;
        }

        return nearestTypeMatch;
    }
}
