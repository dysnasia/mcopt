package mcopt.metal.mixin.gen;

import mcopt.metal.gen.GenBench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.gen.bench=R: starts GenBench when the first loading overlay goes away. */
@Mixin(Gui.class)
abstract class GenBenchMixin {
	@Shadow private Overlay overlay;

	@Inject(method = "setOverlay", at = @At("HEAD"))
	private void mcopt$genBench(Overlay next, CallbackInfo ci) {
		if (next == null && overlay instanceof LoadingOverlay) GenBench.titleReady(Minecraft.getInstance());
	}
}
