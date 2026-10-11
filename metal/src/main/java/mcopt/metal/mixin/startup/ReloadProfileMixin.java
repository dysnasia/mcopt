package mcopt.metal.mixin.startup;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * -Dmcopt.startup.reloadProfile=true: every resource reload runs as vanilla's ProfiledReloadInstance (normally only with
 * debug logging), which logs each listener's prepare and apply time when the reload finishes. A measuring aid only.
 */
@Mixin(ReloadableResourceManager.class)
abstract class ReloadProfileMixin {
	@ModifyArg(method = "createReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"), index = 5)
	private boolean mcopt$profiled(boolean profiled) {
		return true;
	}
}
