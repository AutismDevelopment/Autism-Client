package autismclient.util;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class AutismBufferSource implements AutoCloseable {
    private final StagedVertexBuffer stagedBuffer;
    private final List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
    private final List<RenderType> drawTypes = new ArrayList<>();

    public AutismBufferSource() {
        this(RenderType.BIG_BUFFER_SIZE);
    }

    public AutismBufferSource(int bufferSize) {
        stagedBuffer = new StagedVertexBuffer(() -> "AutismBufferSource", bufferSize);
    }

    public VertexConsumer getBuffer(RenderType renderType) {
        if (!drawTypes.isEmpty() && drawTypes.getLast() == renderType
            && renderType.canConsolidateConsecutiveGeometry()) {
            return stagedBuffer.getVertexBuilder(draws.getLast());
        }

        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(renderType.format(),
            renderType.primitiveTopology(), renderType.sortOnUpload()
                ? RenderSystem.getProjectionType().vertexSorting() : null);

        draws.add(draw);
        drawTypes.add(renderType);
        return stagedBuffer.getVertexBuilder(draw);
    }

    public void uploadAndDraw() {
        try {
            if (draws.isEmpty()) return;

            stagedBuffer.upload();

            for (int i = 0; i < draws.size(); i++) {
                draw(drawTypes.get(i), draws.get(i));
            }
        } finally {
            draws.clear();
            drawTypes.clear();

            stagedBuffer.endFrame();
        }
    }

    @Override
    public void close() {
        stagedBuffer.close();
    }

    public static final class Holder {
        private final int bufferSize;
        private AutismBufferSource source;

        public Holder(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public AutismBufferSource get() {
            AutismBufferSource current = source;
            if (current == null) {
                current = new AutismBufferSource(bufferSize);
                source = current;
            }
            return current;
        }
    }

    private void draw(RenderType type, StagedVertexBuffer.Draw draw) {
        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
        if (info != null) {
            type.prepare().drawFromBuffer(info);
        }
    }
}
