package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;

public class DirectSpacer extends DirectUiNode {
    public DirectSpacer(float width, float height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        return context.theme().scale(width);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return context.theme().scale(height);
    }
}
