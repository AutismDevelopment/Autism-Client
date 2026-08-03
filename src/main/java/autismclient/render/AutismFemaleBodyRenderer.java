package autismclient.render;

import autismclient.modules.GoldenLeverModule;
import autismclient.modules.PackHideState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutismFemaleBodyRenderer {
    private static final float ARM_WIDTH_SCALE = 0.78F;
    private static final Set<PlayerModel> MAIN_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<PlayerModel> ARMOR_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final MeshSet FULL_MESHES = MeshSet.build(MeshBuilder.Detail.FULL);
    private static final MeshSet MEDIUM_MESHES = MeshSet.build(MeshBuilder.Detail.MEDIUM);
    private static final MeshSet FAR_MESHES = MeshSet.build(MeshBuilder.Detail.FAR);
    private static final MeshSet CROWD_MESHES = MeshSet.build(MeshBuilder.Detail.CROWD);
    private static final double FULL_DETAIL_DISTANCE_SQ = 6.0 * 6.0;
    private static final double MEDIUM_DETAIL_DISTANCE_SQ = 18.0 * 18.0;
    private static final double CROWD_SAMPLE_DISTANCE_SQ = 32.0 * 32.0;
    private static final int CROWD_DETAIL_PLAYER_COUNT = 24;
    private static final Map<Identifier, JacketAlpha> JACKET_ALPHA_CACHE = new HashMap<>();
    private static Object crowdSampleLevel;
    private static long crowdSampleTick = Long.MIN_VALUE;
    private static int nearbyPlayerCount;

    private static boolean initialized;
    private static volatile boolean layerReady;

    private AutismFemaleBodyRenderer() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (!(renderer instanceof AvatarRenderer<?> avatarRenderer)) return;
            PlayerModel playerModel = avatarRenderer.getModel();
            MAIN_MODELS.add(playerModel);
            helper.register(new FemaleBodyLayer(avatarRenderer, context.getEquipmentRenderer()));
        });
    }

    public static void markArmorModels(ArmorModelSet<?> modelSet) {
        if (modelSet == null) return;
        markArmorModel(modelSet.head());
        markArmorModel(modelSet.chest());
        markArmorModel(modelSet.legs());
        markArmorModel(modelSet.feet());
    }

    private static void markArmorModel(Object model) {
        if (model instanceof PlayerModel playerModel) ARMOR_MODELS.add(playerModel);
    }

    public static void applyModelVisibility(PlayerModel model, AvatarRenderState state) {
        if (model == null || state == null || !layerReady
            || !GoldenLeverModule.shouldApplyFemaleBody(state.id) || PackHideState.isHardLocked()) return;
        model.leftArm.xScale = ARM_WIDTH_SCALE;
        model.rightArm.xScale = ARM_WIDTH_SCALE;
        if (MAIN_MODELS.contains(model)) {
            model.body.visible = false;
            model.jacket.visible = false;
        } else if (ARMOR_MODELS.contains(model)) {
            model.body.visible = false;
        }
    }

    public static void compensateHeldItemArmScale(AvatarRenderState state, PoseStack poseStack) {
        if (state == null || poseStack == null || !GoldenLeverModule.shouldApplyFemaleBody(state.id)) return;
        poseStack.scale(1.0F / ARM_WIDTH_SCALE, 1.0F, 1.0F);
    }

    private static final class FemaleBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final EquipmentLayerRenderer equipmentRenderer;

        private FemaleBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EquipmentLayerRenderer equipmentRenderer) {
            super(renderer);
            this.equipmentRenderer = equipmentRenderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector output, int lightCoords,
                           AvatarRenderState state, float yRot, float xRot) {
            if (!GoldenLeverModule.shouldApplyFemaleBody(state.id) || PackHideState.isHardLocked() || state.isSpectator) return;
            layerReady = true;

            poseStack.pushPose();
            PlayerModel parent = getParentModel();
            parent.root().translateAndRotate(poseStack);
            parent.body.translateAndRotate(poseStack);

            MeshSet meshes = meshesFor(state);
            renderSkin(meshes, state, poseStack, output, lightCoords);

            renderArmorPiece(state.chestEquipment, EquipmentSlot.CHEST,
                net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID,
                meshes.chestArmor, state, poseStack, output, lightCoords, 3);
            renderArmorPiece(state.legsEquipment, EquipmentSlot.LEGS,
                net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                meshes.leggingsArmor, state, poseStack, output, lightCoords, 8);
            poseStack.popPose();
        }

        private static void renderSkin(MeshSet meshes, AvatarRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector output, int lightCoords) {
            Identifier texture = state.skin.body().texturePath();
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            JacketAlpha jacketAlpha = state.showJacket ? jacketAlpha(texture) : JacketAlpha.EMPTY;
            if (!state.isInvisible) {
                if (meshes == CROWD_MESHES) {
                    MeshModel crowdMesh = jacketAlpha == JacketAlpha.EMPTY ? meshes.body : meshes.bodyWithJacket;
                    output.order(0).submitModel(
                        crowdMesh, state, poseStack, RenderTypes.entityTranslucent(texture), lightCoords,
                        overlay, -1, null, state.outlineColor, null
                    );
                    return;
                }
                output.order(0).submitModel(
                    meshes.body, state, poseStack, RenderTypes.entitySolid(texture), lightCoords,
                    overlay, -1, null, state.outlineColor, null
                );
                if (jacketAlpha != JacketAlpha.EMPTY) {
                    RenderType jacketRenderType = switch (jacketAlpha) {
                        case OPAQUE -> RenderTypes.entitySolid(texture);
                        case CUTOUT -> RenderTypes.entityCutoutCull(texture);
                        case TRANSLUCENT, UNKNOWN -> AutismRenderTypes.femaleBodyTranslucentCull(texture);
                        case EMPTY -> throw new IllegalStateException("Empty jacket submitted");
                    };
                    output.order(0).submitModel(
                        meshes.jacket, state, poseStack, jacketRenderType, lightCoords,
                        overlay, -1, null, state.outlineColor, null
                    );
                }
                return;
            }

            MeshModel visibleMesh = jacketAlpha == JacketAlpha.EMPTY ? meshes.body : meshes.bodyWithJacket;
            if (!state.isInvisibleToPlayer) {
                output.order(0).submitModel(
                    visibleMesh, state, poseStack,
                    AutismRenderTypes.femaleBodyTranslucentCull(texture), lightCoords,
                    overlay, 0x26FFFFFF, null, state.outlineColor, null
                );
            } else if (state.appearsGlowing()) {
                output.order(0).submitModel(
                    visibleMesh, state, poseStack, RenderTypes.outline(texture), lightCoords,
                    overlay, -1, null, state.outlineColor, null
                );
            }
        }

        private static MeshSet meshesFor(AvatarRenderState state) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.getId() != state.id
                && sampleNearbyPlayerCount(minecraft) >= CROWD_DETAIL_PLAYER_COUNT) {
                return CROWD_MESHES;
            }
            double distanceToCameraSq = state.distanceToCameraSq;
            if (distanceToCameraSq <= FULL_DETAIL_DISTANCE_SQ) return FULL_MESHES;
            if (distanceToCameraSq <= MEDIUM_DETAIL_DISTANCE_SQ) return MEDIUM_MESHES;
            return FAR_MESHES;
        }

        private static int sampleNearbyPlayerCount(Minecraft minecraft) {
            if (minecraft.level == null || minecraft.player == null) return 0;
            long tick = minecraft.level.getGameTime();
            if (minecraft.level == crowdSampleLevel && tick == crowdSampleTick) return nearbyPlayerCount;
            crowdSampleLevel = minecraft.level;
            crowdSampleTick = tick;
            int count = 0;
            for (var player : minecraft.level.players()) {
                if (player.distanceToSqr(minecraft.player) <= CROWD_SAMPLE_DISTANCE_SQ) count++;
            }
            nearbyPlayerCount = count;
            return count;
        }

        private static JacketAlpha jacketAlpha(Identifier textureId) {
            JacketAlpha cached = JACKET_ALPHA_CACHE.get(textureId);
            if (cached != null) return cached;
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
            if (!(texture instanceof DynamicTexture dynamicTexture)) {
                JACKET_ALPHA_CACHE.put(textureId, JacketAlpha.UNKNOWN);
                return JacketAlpha.UNKNOWN;
            }
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels == null || pixels.isClosed() || pixels.getWidth() < 64 || pixels.getHeight() < 64) {
                return JacketAlpha.UNKNOWN;
            }

            boolean hasTransparent = false;
            boolean hasVisible = false;
            boolean hasPartial = false;
            for (int y = 32; y < 48; y++) {
                for (int x = 16; x < 40; x++) {
                    int alpha = ARGB.alpha(pixels.getPixel(x, y));
                    hasTransparent |= alpha == 0;
                    hasVisible |= alpha != 0;
                    hasPartial |= alpha > 0 && alpha < 255;
                }
            }

            JacketAlpha result;
            if (!hasVisible) result = JacketAlpha.EMPTY;
            else if (hasPartial) result = JacketAlpha.TRANSLUCENT;
            else if (hasTransparent) result = JacketAlpha.CUTOUT;
            else result = JacketAlpha.OPAQUE;
            JACKET_ALPHA_CACHE.put(textureId, result);
            return result;
        }

        private void renderArmorPiece(ItemStack stack, EquipmentSlot expectedSlot,
                                      net.minecraft.client.resources.model.EquipmentClientInfo.LayerType layerType,
                                      MeshModel model, AvatarRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector output, int lightCoords, int order) {
            if (stack == null || stack.isEmpty()) return;
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.slot() != expectedSlot || equippable.assetId().isEmpty()) return;
            equipmentRenderer.renderLayers(
                layerType,
                equippable.assetId().orElseThrow(),
                model,
                state,
                stack,
                poseStack,
                output,
                lightCoords,
                state.skin.body().texturePath(),
                state.outlineColor,
                order
            );
        }

    }

    private static final class MeshModel extends Model<AvatarRenderState> {
        private MeshModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {

        }
    }

    private record UvLayout(float textureWidth, float textureHeight, float frontV, float topV) {
        private static final UvLayout SKIN_BODY = new UvLayout(64.0F, 64.0F, 20.0F, 16.0F);
        private static final UvLayout SKIN_JACKET = new UvLayout(64.0F, 64.0F, 36.0F, 32.0F);
        private static final UvLayout ARMOR = new UvLayout(64.0F, 32.0F, 20.0F, 16.0F);
    }

    private enum JacketAlpha {
        UNKNOWN,
        EMPTY,
        OPAQUE,
        CUTOUT,
        TRANSLUCENT
    }

    private record MeshSet(MeshModel body, MeshModel jacket, MeshModel bodyWithJacket,
                           MeshModel chestArmor, MeshModel leggingsArmor) {
        private static MeshSet build(MeshBuilder.Detail detail) {
            return new MeshSet(
                MeshBuilder.build(UvLayout.SKIN_BODY, 0.0F, detail),
                MeshBuilder.build(UvLayout.SKIN_JACKET, 0.25F, detail),
                MeshBuilder.buildCombined(
                    UvLayout.SKIN_BODY, 0.0F, UvLayout.SKIN_JACKET, 0.25F, detail
                ),
                MeshBuilder.build(UvLayout.ARMOR, 0.58F, detail),
                MeshBuilder.build(UvLayout.ARMOR, 0.32F, detail)
            );
        }
    }

    private static final class MeshBuilder {
        private static final int FRONT_COLUMNS = 29;
        private static final int[] FLAT_COLUMNS = {0, 1, FRONT_COLUMNS - 2, FRONT_COLUMNS - 1};
        private static final float[] ROW_Y = {
            0.0F, 0.5F, 1.0F, 1.5F, 2.0F, 2.5F, 3.0F,
            3.5F, 4.0F, 4.5F, 5.1F, 5.5F, 5.8F, 6.15F,
            6.45F, 6.70F, 7.0F, 8.5F, 12.0F
        };
        private static final float[] SHAPE_Y = {0.0F, 1.0F, 2.5F, 4.8F, 6.15F, 8.5F, 12.0F};
        private static final float[] HALF_WIDTH = {4.40F, 4.66F, 4.78F, 4.55F, 4.20F, 3.35F, 4.20F};
        private static final float[] FRONT_DEPTH = {-2.02F, -2.05F, -2.08F, -2.03F, -2.00F, -1.94F, -2.05F};
        private static final float[] BACK_DEPTH = {2.00F, 2.02F, 2.04F, 2.02F, 2.00F, 1.94F, 2.05F};
        private static final float BREAST_CENTER_X = 1.55F;
        private static final float BREAST_CENTER_Y = 2.55F;
        private static final float BREAST_INNER_RADIUS_X = 2.65F;
        private static final float BREAST_OUTER_RADIUS_X = 3.40F;
        private static final float BREAST_UPPER_RADIUS_Y = 2.85F;
        private static final float BREAST_LOWER_RADIUS_Y = 4.05F;
        private static final float BREAST_DEPTH = 2.70F;
        private static final double BLEND_POWER = 6.0;

        private record Detail(int[] columns, int[] rows) {
            private static final Detail FULL = new Detail(sequence(FRONT_COLUMNS), sequence(ROW_Y.length));
            private static final Detail MEDIUM = new Detail(
                new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28},
                new int[]{0, 1, 3, 5, 7, 9, 10, 12, 14, 16, 17, 18}
            );
            private static final Detail FAR = new Detail(
                new int[]{0, 4, 8, 12, 16, 20, 24, 28},
                new int[]{0, 2, 5, 8, 10, 13, 16, 17, 18}
            );
            private static final Detail CROWD = new Detail(
                new int[]{0, 7, 10, 14, 18, 21, 28},
                new int[]{0, 5, 8, 10, 13, 16, 18}
            );
        }

        private MeshBuilder() {
        }

        private static MeshModel build(UvLayout uv, float shellOffset, Detail detail) {
            return model(List.of(new MeshCube(buildQuads(uv, shellOffset, detail))));
        }

        private static MeshModel buildCombined(UvLayout firstUv, float firstOffset,
                                               UvLayout secondUv, float secondOffset, Detail detail) {
            return model(List.of(
                new MeshCube(buildQuads(firstUv, firstOffset, detail)),
                new MeshCube(buildQuads(secondUv, secondOffset, detail))
            ));
        }

        private static MeshModel model(List<ModelPart.Cube> cubes) {
            return new MeshModel(new ModelPart(cubes, Map.of()));
        }

        private static List<Quad> buildQuads(UvLayout uv, float shellOffset, Detail detail) {
            SurfacePoint[][] front = buildSurface(true, shellOffset);
            SurfacePoint[][] back = buildSurface(false, shellOffset);
            List<Quad> quads = new ArrayList<>(640);

            for (int segment = 0; segment < detail.rows.length - 1; segment++) {
                int topRow = detail.rows[segment];
                int bottomRow = detail.rows[segment + 1];
                int[] frontColumns = intersectsChest(ROW_Y[topRow], ROW_Y[bottomRow])
                    ? detail.columns : FLAT_COLUMNS;
                addFrontRow(quads, front, topRow, bottomRow, frontColumns, uv);
                addBackRow(quads, back, topRow, bottomRow, uv);

                addSideQuad(quads, front, back, topRow, bottomRow, false, uv);
                addSideQuad(quads, front, back, topRow, bottomRow, true, uv);
            }

            addCapQuads(quads, front, back, detail.rows[0], false, detail.columns, uv);
            addCapQuads(quads, front, back, detail.rows[detail.rows.length - 1], true, FLAT_COLUMNS, uv);
            return List.copyOf(quads);
        }

        private static void addFrontRow(List<Quad> quads, SurfacePoint[][] surface,
                                        int topRow, int bottomRow, int[] columns, UvLayout uv) {
            for (int segment = 0; segment < columns.length - 1; segment++) {
                int left = columns[segment];
                int right = columns[segment + 1];
                quads.add(quad(
                    meshVertex(surface[topRow][right], frontU(right), frontV(topRow), uv),
                    meshVertex(surface[topRow][left], frontU(left), frontV(topRow), uv),
                    meshVertex(surface[bottomRow][left], frontU(left), frontV(bottomRow), uv),
                    meshVertex(surface[bottomRow][right], frontU(right), frontV(bottomRow), uv)
                ));
            }
        }

        private static void addBackRow(List<Quad> quads, SurfacePoint[][] surface,
                                       int topRow, int bottomRow, UvLayout uv) {
            for (int segment = 0; segment < FLAT_COLUMNS.length - 1; segment++) {
                int left = FLAT_COLUMNS[segment];
                int right = FLAT_COLUMNS[segment + 1];
                quads.add(quad(
                    meshVertex(surface[topRow][left], backU(left), frontV(topRow), uv),
                    meshVertex(surface[topRow][right], backU(right), frontV(topRow), uv),
                    meshVertex(surface[bottomRow][right], backU(right), frontV(bottomRow), uv),
                    meshVertex(surface[bottomRow][left], backU(left), frontV(bottomRow), uv)
                ));
            }
        }

        private static boolean intersectsChest(float topY, float bottomY) {
            return bottomY > BREAST_CENTER_Y - BREAST_UPPER_RADIUS_Y
                && topY < BREAST_CENTER_Y + BREAST_LOWER_RADIUS_Y;
        }

        private static SurfacePoint[][] buildSurface(boolean frontSurface, float shellOffset) {
            SurfacePoint[][] base = new SurfacePoint[ROW_Y.length][FRONT_COLUMNS];
            for (int row = 0; row < ROW_Y.length; row++) {
                float y = ROW_Y[row];
                float halfWidth = interpolate(y, HALF_WIDTH);
                float baseDepth = interpolate(y, frontSurface ? FRONT_DEPTH : BACK_DEPTH);
                for (int column = 0; column < FRONT_COLUMNS; column++) {
                    float factor = factor(column);
                    float x = factor * halfWidth;
                    float z = frontSurface ? baseDepth - breastProjection(x, y) : baseDepth;
                    base[row][column] = new SurfacePoint(x, y, z, new Vector3f());
                }
            }

            calculateSmoothNormals(base, frontSurface);
            if (shellOffset == 0.0F) return base;

            SurfacePoint[][] expanded = new SurfacePoint[ROW_Y.length][FRONT_COLUMNS];
            for (int row = 0; row < ROW_Y.length; row++) {
                for (int column = 0; column < FRONT_COLUMNS; column++) {
                    SurfacePoint point = base[row][column];
                    Vector3f normal = point.normal;
                    expanded[row][column] = new SurfacePoint(
                        point.x + normal.x() * shellOffset,
                        point.y + normal.y() * shellOffset,
                        point.z + normal.z() * shellOffset,
                        new Vector3f(normal)
                    );
                }
            }
            return expanded;
        }

        private static void calculateSmoothNormals(SurfacePoint[][] surface, boolean frontSurface) {
            int lastRow = surface.length - 1;
            int lastColumn = surface[0].length - 1;
            for (int row = 0; row <= lastRow; row++) {
                int previousRow = Math.max(0, row - 1);
                int nextRow = Math.min(lastRow, row + 1);
                for (int column = 0; column <= lastColumn; column++) {
                    int previousColumn = Math.max(0, column - 1);
                    int nextColumn = Math.min(lastColumn, column + 1);
                    Vector3f horizontal = difference(surface[row][nextColumn], surface[row][previousColumn]);
                    Vector3f vertical = difference(surface[nextRow][column], surface[previousRow][column]);
                    Vector3f normal = frontSurface ? vertical.cross(horizontal) : horizontal.cross(vertical);
                    if (normal.lengthSquared() < 1.0E-6F) normal.set(0.0F, 0.0F, frontSurface ? -1.0F : 1.0F);
                    else normal.normalize();
                    surface[row][column].normal.set(normal);
                }
            }
        }

        private static float breastProjection(float x, float y) {
            float leftDx = x + BREAST_CENTER_X;
            float rightDx = x - BREAST_CENTER_X;
            float left = dome(leftDx, y - BREAST_CENTER_Y,
                leftDx < 0.0F ? BREAST_OUTER_RADIUS_X : BREAST_INNER_RADIUS_X);
            float right = dome(rightDx, y - BREAST_CENTER_Y,
                rightDx > 0.0F ? BREAST_OUTER_RADIUS_X : BREAST_INNER_RADIUS_X);
            if (left <= 0.0F) return right * BREAST_DEPTH;
            if (right <= 0.0F) return left * BREAST_DEPTH;
            double blended = Math.pow(Math.pow(left, BLEND_POWER) + Math.pow(right, BLEND_POWER), 1.0 / BLEND_POWER);
            return (float) blended * BREAST_DEPTH;
        }

        private static float dome(float dx, float dy, float radiusX) {
            float nx = dx / radiusX;
            float ny = dy / (dy < 0.0F ? BREAST_UPPER_RADIUS_Y : BREAST_LOWER_RADIUS_Y);
            float radiusSquared = nx * nx + ny * ny;
            if (radiusSquared >= 1.0F) return 0.0F;
            float inside = 1.0F - radiusSquared;
            return inside * inside * (3.0F - 2.0F * inside);
        }

        private static void addSideQuad(List<Quad> quads, SurfacePoint[][] front, SurfacePoint[][] back,
                                        int topRow, int bottomRow, boolean east, UvLayout uv) {
            int column = east ? FRONT_COLUMNS - 1 : 0;
            SurfacePoint frontTop = front[topRow][column];
            SurfacePoint backTop = back[topRow][column];
            SurfacePoint frontBottom = front[bottomRow][column];
            SurfacePoint backBottom = back[bottomRow][column];
            float frontU = east ? 28.0F : 20.0F;
            float backU = east ? 32.0F : 16.0F;
            Vector3f normal = faceNormal(
                east ? backTop : frontTop,
                east ? frontTop : backTop,
                east ? frontBottom : backBottom
            );
            if (east) {
                quads.add(quad(
                    meshVertex(backTop, backU, frontV(topRow), normal, uv),
                    meshVertex(frontTop, frontU, frontV(topRow), normal, uv),
                    meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv),
                    meshVertex(backBottom, backU, frontV(bottomRow), normal, uv)
                ));
            } else {
                quads.add(quad(
                    meshVertex(frontTop, frontU, frontV(topRow), normal, uv),
                    meshVertex(backTop, backU, frontV(topRow), normal, uv),
                    meshVertex(backBottom, backU, frontV(bottomRow), normal, uv),
                    meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv)
                ));
            }
        }

        private static void addCapQuads(List<Quad> quads, SurfacePoint[][] front, SurfacePoint[][] back,
                                        int row, boolean bottom, int[] columns, UvLayout uv) {
            Vector3f normal = new Vector3f(0.0F, bottom ? 1.0F : -1.0F, 0.0F);
            for (int segment = 0; segment < columns.length - 1; segment++) {
                int left = columns[segment];
                int right = columns[segment + 1];
                SurfacePoint frontLeft = front[row][left];
                SurfacePoint frontRight = front[row][right];
                SurfacePoint backLeft = back[row][left];
                SurfacePoint backRight = back[row][right];
                float uLeft = (bottom ? 32.0F : 24.0F) + factor(left) * 4.0F;
                float uRight = (bottom ? 32.0F : 24.0F) + factor(right) * 4.0F;
                float frontCapV = uv.topV + 4.0F;
                float backCapV = uv.topV;
                if (bottom) {
                    quads.add(quad(
                        meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv),
                        meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv),
                        meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv),
                        meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv)
                    ));
                } else {
                    quads.add(quad(
                        meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv),
                        meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv),
                        meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv),
                        meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv)
                    ));
                }
            }
        }

        private static int[] sequence(int length) {
            int[] values = new int[length];
            for (int index = 0; index < length; index++) values[index] = index;
            return values;
        }

        private static float interpolate(float y, float[] values) {
            if (y <= SHAPE_Y[0]) return values[0];
            for (int i = 1; i < SHAPE_Y.length; i++) {
                if (y <= SHAPE_Y[i]) {
                    float progress = (y - SHAPE_Y[i - 1]) / (SHAPE_Y[i] - SHAPE_Y[i - 1]);
                    return values[i - 1] + (values[i] - values[i - 1]) * progress;
                }
            }
            return values[values.length - 1];
        }

        private static float factor(int column) {
            return -1.0F + 2.0F * column / (FRONT_COLUMNS - 1.0F);
        }

        private static float frontU(int column) {
            return 24.0F + factor(column) * 4.0F;
        }

        private static float backU(int column) {
            return 36.0F - factor(column) * 4.0F;
        }

        private static float frontV(int row) {
            return ROW_Y[row];
        }

        private static Vector3f difference(SurfacePoint a, SurfacePoint b) {
            return new Vector3f(a.x - b.x, a.y - b.y, a.z - b.z);
        }

        private static Vector3f faceNormal(SurfacePoint a, SurfacePoint b, SurfacePoint c) {
            Vector3f ab = difference(b, a);
            Vector3f ac = difference(c, a);
            Vector3f normal = ab.cross(ac);
            return normal.lengthSquared() < 1.0E-6F ? new Vector3f(0.0F, 0.0F, -1.0F) : normal.normalize();
        }

        private static MeshVertex meshVertex(SurfacePoint point, float u, float v, UvLayout uv) {
            return meshVertex(point, u, v, point.normal, uv);
        }

        private static MeshVertex meshVertex(SurfacePoint point, float u, float v, Vector3f normal, UvLayout uv) {
            return new MeshVertex(
                point.x, point.y, point.z,
                u / uv.textureWidth, (uv.frontV + v) / uv.textureHeight,
                normal.x(), normal.y(), normal.z()
            );
        }

        private static MeshVertex meshVertexAbsoluteUv(SurfacePoint point, float u, float v,
                                                       Vector3f normal, UvLayout uv) {
            return new MeshVertex(
                point.x, point.y, point.z,
                u / uv.textureWidth, v / uv.textureHeight,
                normal.x(), normal.y(), normal.z()
            );
        }

        private static Quad quad(MeshVertex a, MeshVertex b, MeshVertex c, MeshVertex d) {
            return new Quad(a, b, c, d);
        }

        private static final class MeshCube extends ModelPart.Cube {
            private final MeshVertex[] vertices;
            private final Vector3f transformedNormal = new Vector3f();
            private final Vector3f transformedPosition = new Vector3f();

            private MeshCube(List<Quad> quads) {
                super(
                0, 0, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F,
                    0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, java.util.Set.of()
                );
                this.vertices = new MeshVertex[quads.size() * 4];
                int index = 0;
                for (Quad quad : quads) {
                    for (MeshVertex point : quad.points) vertices[index++] = point;
                }
            }

            @Override
            public void compile(PoseStack.Pose pose, VertexConsumer output, int lightCoords, int overlayCoords, int color) {
                for (int index = 0; index < vertices.length; index++) {
                    MeshVertex point = vertices[index];
                    pose.transformNormal(point.nx, point.ny, point.nz, transformedNormal);
                    pose.pose().transformPosition(
                        point.x / ModelPart.Vertex.SCALE_FACTOR,
                        point.y / ModelPart.Vertex.SCALE_FACTOR,
                        point.z / ModelPart.Vertex.SCALE_FACTOR,
                        transformedPosition
                    );
                    output.addVertex(
                        transformedPosition.x(), transformedPosition.y(), transformedPosition.z(),
                        color, point.u, point.v, overlayCoords, lightCoords,
                        transformedNormal.x(), transformedNormal.y(), transformedNormal.z()
                    );
                }
            }
        }

        private static final class SurfacePoint {
            private final float x;
            private final float y;
            private final float z;
            private final Vector3f normal;

            private SurfacePoint(float x, float y, float z, Vector3f normal) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.normal = normal;
            }
        }

        private record MeshVertex(float x, float y, float z, float u, float v,
                                  float nx, float ny, float nz) {
        }

        private static final class Quad {
            private final MeshVertex[] points;

            private Quad(MeshVertex a, MeshVertex b, MeshVertex c, MeshVertex d) {
                this.points = new MeshVertex[]{a, b, c, d};
            }
        }
    }
}
