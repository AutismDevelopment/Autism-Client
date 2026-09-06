package autismclient.util.oresim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class AutismOreGhostModels {

    public static final int VERTEX_STRIDE = 5;

    public record Face(Direction cull, Direction facing, float[] data) {
    }

    public record Template(List<Face> faces) {
        static final Template EMPTY = new Template(List.of());
    }

    private static final RandomSource RANDOM = RandomSource.create(42L);
    private static final Map<BlockState, Template> CACHE = new IdentityHashMap<>();
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();
    private static Object modelSetToken;

    private AutismOreGhostModels() {
    }

    public static void clear() {
        CACHE.clear();
        modelSetToken = null;
    }

    public static Template of(BlockState state) {
        if (state == null) return Template.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return Template.EMPTY;
        BlockStateModelSet set = mc.getModelManager().getBlockStateModelSet();
        if (set == null) return Template.EMPTY;
        if (set != modelSetToken) {
            CACHE.clear();
            modelSetToken = set;
        }
        Template cached = CACHE.get(state);
        if (cached != null) return cached;

        Template built;
        try {
            built = build(set, state);
        } catch (Throwable error) {

            autismclient.AutismClientAddon.LOG.debug("OreSim ghost model build failed", error);
            built = Template.EMPTY;
        }
        CACHE.put(state, built);
        return built;
    }

    private static Template build(BlockStateModelSet set, BlockState state) {
        PARTS.clear();
        RANDOM.setSeed(42L);
        set.get(state).collectParts(RANDOM, PARTS);
        List<Face> faces = new ArrayList<>();
        for (BlockStateModelPart part : PARTS) {
            for (Direction direction : Direction.values()) collect(part.getQuads(direction), direction, faces);
            collect(part.getQuads(null), null, faces);
        }
        PARTS.clear();
        return faces.isEmpty() ? Template.EMPTY : new Template(List.copyOf(faces));
    }

    private static void collect(List<BakedQuad> quads, Direction cull, List<Face> out) {
        if (quads == null || quads.isEmpty()) return;
        for (BakedQuad quad : quads) {
            float[] data = new float[BakedQuad.VERTEX_COUNT * VERTEX_STRIDE];
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                var position = quad.position(i);
                long uv = quad.packedUV(i);
                int base = i * VERTEX_STRIDE;
                data[base] = position.x();
                data[base + 1] = position.y();
                data[base + 2] = position.z();

                data[base + 3] = UVPair.unpackU(uv);
                data[base + 4] = UVPair.unpackV(uv);
            }
            Direction facing = quad.materialInfo().shade() ? quad.direction() : null;
            out.add(new Face(cull, facing, data));
        }
    }
}
