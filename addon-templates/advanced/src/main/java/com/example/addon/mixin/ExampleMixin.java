package com.example.addon.mixin;

import com.example.addon.ExampleAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Example mixin. List your mixin classes in autism-advanced-addon-template.mixins.json (referenced from
// fabric.mod.json). An @Inject handler matches the target method's params, then CallbackInfo.
@Mixin(Minecraft.class)
public abstract class ExampleMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onClientInit(GameConfig gameConfig, CallbackInfo ci) {
        org.slf4j.LoggerFactory.getLogger(ExampleAddon.ID).info("Hello from ExampleMixin!");
    }
}
