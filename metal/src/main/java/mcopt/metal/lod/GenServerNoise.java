package mcopt.metal.lod;

import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;

/**
 * The server-side door of LodNativeNoise (-Dmcopt.gen.nativeNoise=neon|java): GenNoiseChunkMixin passes the sampler set a
 * NoiseChunk takes from its RandomState through here, so the integrated server's chunk generation evaluates the same copied
 * noise leaves as the far terrain. Off: the same set is returned.
 */
public final class GenServerNoise {
	private GenServerNoise() {
	}

	public static DensitySamplerSet wrap(DensitySamplerSet set, Object randomState) {
		return LodNativeNoise.wrapServer(set, randomState);
	}

	/** GenBench's per-chunk comparison (-Dmcopt.gen.bench.ab): the calling thread's server noise is the game's own while true. */
	public static void benchGameNoise(boolean plain) {
		LodNativeNoise.BENCH_PLAIN.set(plain);
	}

	public static boolean benchAb() {
		return LodNativeNoise.BENCH_AB;
	}
}
