package mcopt.metal.mixin.startup;

import mcopt.metal.Startup;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.startup.report=...: the JVM uptime when the first resource load has finished (the overlay's fade-out starts). */
@Mixin(Minecraft.class)
abstract class LoadFinishedReportMixin {
	@Inject(method = "onGameLoadFinished", at = @At("HEAD"))
	private void mcopt$report(CallbackInfo ci) {
		Startup.loadFinished();
	}
}
