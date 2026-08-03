package autismclient.mixin.security;

import autismclient.AutismClientAddon;
import autismclient.security.AutismRegistryComponentCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(RegistryDataCollector.class)
public abstract class AutismRegistryDataCollectorMixin {
    private static final Set<String> AUTISM$REPORTED_WORLD_CLOCK_FIXES = ConcurrentHashMap.newKeySet();
    private static final Identifier AUTISM$OVERWORLD_CLOCK = Identifier.withDefaultNamespace("overworld");
    private static final Identifier AUTISM$THE_END_CLOCK = Identifier.withDefaultNamespace("the_end");

    @ModifyArg(
        method = "loadNewElementsAndTags",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryDataLoader;load(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        ),
        index = 0,
        require = 0
    )
    private Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> autism$ensureVanillaWorldClocks(
        Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries
    ) {
        if (entries == null) return null;
        RegistryDataLoader.NetworkedRegistryData previous = entries.get(Registries.WORLD_CLOCK);
        List<RegistrySynchronization.PackedRegistryEntry> elements = new ArrayList<>(previous == null ? List.of() : previous.elements());
        boolean changed = autism$addWorldClockIfMissing(elements, AUTISM$OVERWORLD_CLOCK);
        changed |= autism$addWorldClockIfMissing(elements, AUTISM$THE_END_CLOCK);
        if (!changed) return entries;
        entries.put(
            Registries.WORLD_CLOCK,
            new RegistryDataLoader.NetworkedRegistryData(
                List.copyOf(elements),
                previous == null ? net.minecraft.tags.TagNetworkSerialization.NetworkPayload.EMPTY : previous.tags()
            )
        );
        if (AUTISM$REPORTED_WORLD_CLOCK_FIXES.add("vanilla-world-clocks")) {
            AutismClientAddon.LOG.warn(
                "[Autism] Server registry payload was missing vanilla world clocks; added minecraft:overworld/minecraft:the_end so configuration can continue."
            );
        }
        return entries;
    }

    private static boolean autism$addWorldClockIfMissing(List<RegistrySynchronization.PackedRegistryEntry> elements, Identifier id) {
        for (RegistrySynchronization.PackedRegistryEntry element : elements) {
            if (id.equals(element.id())) return false;
        }
        elements.add(new RegistrySynchronization.PackedRegistryEntry(id, Optional.of(new CompoundTag())));
        return true;
    }

    @WrapOperation(
        method = "updateComponents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/component/DataComponentInitializers;build(Lnet/minecraft/core/HolderLookup$Provider;)Ljava/util/List;"
        ),
        require = 0
    )
    private static List<DataComponentInitializers.PendingComponents<?>> autism$buildRemoteComponentsCompat(
        DataComponentInitializers initializers,
        HolderLookup.Provider context,
        Operation<List<DataComponentInitializers.PendingComponents<?>>> original
    ) {
        AutismRegistryComponentCompat.beginRemoteComponentBake();
        try {
            return original.call(initializers, context);
        } finally {
            AutismRegistryComponentCompat.endRemoteComponentBake();
        }
    }
}
