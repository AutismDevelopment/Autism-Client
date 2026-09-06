package autismclient.mixin;

import autismclient.util.AutismFakeCoords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Mixin(DebugEntryPosition.class)
public abstract class AutismDebugScreenMixin {

    @ModifyArg(
        method = "display",
        index = 1,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addToGroup(Lnet/minecraft/resources/Identifier;Ljava/util/Collection;)V"))
    private Collection<String> autism$fakeF3Coords(Collection<String> lines) {
        if (!AutismFakeCoords.active()) return lines;
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (cam == null) return lines;
        double[] f = AutismFakeCoords.apply(cam.getX(), cam.getY(), cam.getZ());
        int bx = Mth.floor(f[0]), by = Mth.floor(f[1]), bz = Mth.floor(f[2]);
        int cx = bx >> 4, cz = bz >> 4, sy = by >> 4;
        String xyz = String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", f[0], f[1], f[2]);
        String block = String.format(Locale.ROOT, "Block: %d %d %d", bx, by, bz);
        String chunk = String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
            cx, sy, cz, cx & 31, cz & 31, cx >> 5, cz >> 5);
        List<String> out = new ArrayList<>(lines.size());
        for (String s : lines) {
            if (s.startsWith("XYZ:")) out.add(xyz);
            else if (s.startsWith("Block:")) out.add(block);
            else if (s.startsWith("Chunk:")) out.add(chunk);
            else out.add(s);
        }
        return out;
    }
}
