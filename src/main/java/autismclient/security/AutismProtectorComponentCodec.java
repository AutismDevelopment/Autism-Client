package autismclient.security;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public record AutismProtectorComponentCodec(Codec<Component> wrapped) implements Codec<Component> {

    @Override
    public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
        DataResult<Pair<Component, T>> result = wrapped.decode(ops, input);
        if (!AutismProtector.shouldTagPacketComponents()) return result;
        if (!AutismProtectorPacketContext.isProcessingPacket()) return result;
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return result;
        }
        if (mc != null && mc.hasSingleplayerServer()) return result;
        return result.map(pair -> pair.mapFirst(c -> {
            markTree(c);
            return c;
        }));
    }

    @Override
    public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
        return wrapped.encode(input, ops, prefix);
    }

    private static void markTree(Component component) {
        if (component == null) return;
        ComponentContents contents = component.getContents();
        if (contents instanceof AutismFromPacketAccess access) access.autism$setFromPacket();
        if (contents instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component argComp) markTree(argComp);
            }
        }
        for (Component sibling : component.getSiblings()) markTree(sibling);
    }
}
