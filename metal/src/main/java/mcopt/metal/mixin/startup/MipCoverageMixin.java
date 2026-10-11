package mcopt.metal.mixin.startup;

import com.mojang.blaze3d.platform.NativeImage;
import mcopt.metal.MipCoverage;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dmcopt.startup.mipCoverage=fast: alphaTestCoverage computed by {@link MipCoverage}; =check: both, compared. */
@Mixin(MipmapGenerator.class)
abstract class MipCoverageMixin {
	@Unique private static final boolean CHECK = "check".equals(System.getProperty("mcopt.startup.mipCoverage"));
	@Unique private static final ThreadLocal<float[]> FAST = ThreadLocal.withInitial(() -> new float[1]);

	@Inject(method = "alphaTestCoverage", at = @At("HEAD"), cancellable = true)
	private static void mcopt$fast(NativeImage image, float cutoff, float alphaScale, CallbackInfoReturnable<Float> cir) {
		float fast = MipCoverage.alphaTestCoverage(image, cutoff, alphaScale);
		if (CHECK) FAST.get()[0] = fast;
		else cir.setReturnValue(fast);
	}

	@Inject(method = "alphaTestCoverage", at = @At("RETURN"))
	private static void mcopt$check(NativeImage image, float cutoff, float alphaScale, CallbackInfoReturnable<Float> cir) {
		if (CHECK) MipCoverage.check(cir.getReturnValueF(), FAST.get()[0], image, cutoff, alphaScale);
	}
}
