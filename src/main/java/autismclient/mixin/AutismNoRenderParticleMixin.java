package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class AutismNoRenderParticleMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noParticle(ParticleOptions options, double x, double y, double z,
                                   double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        if (options != null && NoRenderState.noParticle(options.getType())) cir.setReturnValue(null);
    }
}
