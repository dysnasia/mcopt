package mcopt.metal.mixin.startup;

import mcopt.metal.Startup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dmcopt.startup.report=true: the JVM uptime when the first loading overlay goes away (the title screen takes input), on stdout;
 * with -Dmcopt.startup.report=exit the game then quits (for timing launches).
 */
@Mixin(Gui.class)
abstract class StartupReportMixin {
	@Shadow private Overlay overlay;

	@Inject(method = "setOverlay", at = @At("HEAD"))
	private void mcopt$report(Overlay next, CallbackInfo ci) {
		if (next == null && overlay instanceof LoadingOverlay) Startup.titleReady(Minecraft.getInstance());
	}
}
