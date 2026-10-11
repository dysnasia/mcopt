package mcopt.metal.mixin.gen;

import java.util.concurrent.CompletableFuture;
import mcopt.metal.gen.GenBench;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dmcopt.gen.bench=R: hashes each chunk as its terrain (noise fill, surface, carvers) is done, for GenBench's exactness check. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class GenBenchTerrainMixin {
	@Inject(method = "buildTerrain", at = @At("RETURN"), cancellable = true)
	private void mcopt$hashTerrain(CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
		cir.setReturnValue(cir.getReturnValue().thenApply(GenBench::terrainDone));
	}

	/** The terrain task itself (noise fill, surface, carvers on one worker thread): timed for -Dmcopt.gen.bench.ab. */
	@Inject(method = "lambda$buildTerrain$0", at = @At("HEAD"))
	private void mcopt$terrainStart(CallbackInfoReturnable<ChunkAccess> cir) {
		GenBench.terrainStart();
	}

	@Inject(method = "lambda$buildTerrain$0", at = @At("RETURN"))
	private void mcopt$terrainEnd(CallbackInfoReturnable<ChunkAccess> cir) {
		GenBench.terrainEnd();
	}
}
