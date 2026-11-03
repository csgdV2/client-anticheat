package combat_tracker.mixin;

import combat_tracker.detection.ExternalDetection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class ExternalRaycastMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void clientAnticheat$sample(float partialTick, CallbackInfo ci) {
        ExternalDetection.get().frame(Minecraft.getInstance());
    }
}
