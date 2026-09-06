package autismclient.mixin.accessor;

import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BaseSpawner.class)
public interface AutismBaseSpawnerAccessor {
    @Accessor("nextSpawnData")
    SpawnData autism$getNextSpawnData();
}
