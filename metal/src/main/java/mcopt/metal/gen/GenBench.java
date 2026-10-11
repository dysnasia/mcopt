package mcopt.metal.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * -Dmcopt.gen.bench=R (a dev tool, off by default): times the integrated server's world generation and checks its output.
 * When the title screen first takes input, a fresh world (saves/mcopt-genbench, deleted first) is created with a fixed seed
 * (-Dmcopt.gen.bench.seed, default 1234); once the player is in, squares of (2R+1)^2 chunks far from spawn are loaded
 * to full status one after another (-Dmcopt.gen.bench.rounds, default 3), each timed. Every block state of every chunk in the
 * squares, as it was when its terrain was done, then goes into a 64-bit hash, so two runs with different settings can be compared for
 * identical terrain. With -Dmcopt.gen.bench.ab each chunk's terrain step gets the game's noise or the native one at random and
 * its thread CPU time is compared per arm (terrainStart). Prints
 * "mcopt-genbench:" lines and quits the game.
 */
public final class GenBench {
	public static final int RADIUS = Integer.getInteger("mcopt.gen.bench", -1);
	private static final long SEED = Long.getLong("mcopt.gen.bench.seed", 1234L);
	private static final int ROUNDS = Integer.getInteger("mcopt.gen.bench.rounds", 3);
	private static final String WORLD = "mcopt-genbench";
	private static boolean started;

	private GenBench() {
	}

	/** The first loading overlay is gone: create the bench world. */
	public static void titleReady(Minecraft mc) {
		if (started || RADIUS < 0) return;
		started = true;
		// a moment later: the world load waits for the overlay to be gone, and this runs while it is still being removed
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}
			mc.execute(() -> create(mc));
		}, "mcopt genbench");
		t.setDaemon(true);
		t.start();
	}

	private static void create(Minecraft mc) {
		deleteWorld(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD));
		Thread t = new Thread(() -> waitAndRun(mc), "mcopt genbench");
		t.setDaemon(true);
		t.start();
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true,
			WorldDataConfiguration.DEFAULT);
		// blocks (running the client's frames) until the player is in the world
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false), WorldPresets::createNormalWorldDimensions,
			new TitleScreen());
	}

	private static void waitAndRun(Minecraft mc) {
		try {
			long joined = System.nanoTime();
			IntegratedServer server;
			while ((server = mc.getSingleplayerServer()) == null || mc.player == null || !server.isReady()) {
				Thread.sleep(100);
				if (System.nanoTime() - joined > 300_000_000_000L) throw new IllegalStateException("no world after 300 s");
			}
			Thread.sleep(3000); // let the spawn area's own generation finish
			long total = 0, hash = 0, cpuTotal = 0;
			// the whole process's CPU time per round: less disturbed than wall time by other programs on the machine
			var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			for (int r = 0; r < ROUNDS; r++) {
				// centres 4096 blocks out along each axis in turn, far from spawn and from each other
				int[][] dirs = {{256, 0}, {0, 256}, {-256, 0}, {0, -256}};
				ChunkPos centre = new ChunkPos(dirs[r % 4][0] * (1 + r / 4), dirs[r % 4][1] * (1 + r / 4));
				IntegratedServer s = server;
				long cpu0 = os.getProcessCpuTime();
				// tickets only on the server thread: the whole chain is started by one task there
				long[] result = CompletableFuture.supplyAsync(() -> {
					ServerLevel level = s.overworld();
					long t0 = System.nanoTime();
					return level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, centre, RADIUS)
						.thenApplyAsync(x -> new long[] {System.nanoTime() - t0, hash(centre)}, s);
				}, s).get().get();
				long cpu = os.getProcessCpuTime() - cpu0;
				cpuTotal += cpu;
				total += result[0];
				hash = hash * 0x100000001B3L ^ result[1];
				System.out.printf(Locale.ROOT, "mcopt-genbench: round %d at chunk %d,%d: %d chunks in %.0f ms (process cpu %.0f ms), hash %016x%n", r, centre.x(),
					centre.z(), (2 * RADIUS + 1) * (2 * RADIUS + 1), result[0] / 1e6, cpu / 1e6, result[1]);
			}
			System.out.printf(Locale.ROOT, "mcopt-genbench: total %.0f ms, process cpu %.0f ms, hash %016x (seed %d, radius %d, %d rounds)%n", total / 1e6,
				cpuTotal / 1e6, hash, SEED, RADIUS, ROUNDS);
			printAb();
		} catch (Throwable e) {
			System.out.println("mcopt-genbench: failed: " + e);
			e.printStackTrace(System.out);
		}
		mc.execute(mc::stop);
	}

	private static final java.util.concurrent.ConcurrentHashMap<Long, Long> TERRAIN = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * A chunk's terrain is done (GenBenchTerrainMixin): FNV-1a over its block state ids, kept by position. Terrain (noise fill,
	 * surface, carvers) writes only its own chunk, so this hash depends on nothing but the seed and the position, unlike the
	 * finished chunk, whose features from neighbouring chunks land in whatever order the worker threads run them.
	 */
	public static net.minecraft.world.level.chunk.ChunkAccess terrainDone(net.minecraft.world.level.chunk.ChunkAccess chunk) {
		long h = 0xcbf29ce484222325L;
		for (LevelChunkSection section : chunk.getSections()) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) h = (h ^ Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x, y, z))) * 0x100000001B3L;
				}
			}
		}
		TERRAIN.put(chunk.getPos().pack(), h);
		return chunk;
	}

	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
	private static final ThreadLocal<long[]> TERRAIN_START = new ThreadLocal<>();
	/** -Dmcopt.gen.bench.ab, per arm (0: the game's noise, 1: ours): chunks, CPU ns, CPU ns squared. */
	private static final java.util.concurrent.atomic.LongAdder[] AB_N = {new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()},
		AB_NS = {new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()};
	private static final java.util.concurrent.atomic.DoubleAdder[] AB_SQ = {new java.util.concurrent.atomic.DoubleAdder(), new java.util.concurrent.atomic.DoubleAdder()};

	/**
	 * A chunk's terrain task starts on this thread: with -Dmcopt.gen.bench.ab it gets the game's noise or ours at random, and its
	 * thread CPU time is measured, so both arms run in the same process, on the same threads, under the same load.
	 */
	public static void terrainStart() {
		if (!mcopt.metal.lod.GenServerNoise.benchAb()) return;
		boolean plain = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
		mcopt.metal.lod.GenServerNoise.benchGameNoise(plain);
		TERRAIN_START.set(new long[] {plain ? 0 : 1, THREADS.getCurrentThreadCpuTime()});
	}

	public static void terrainEnd() {
		long[] st = TERRAIN_START.get();
		if (st == null) return;
		TERRAIN_START.remove();
		mcopt.metal.lod.GenServerNoise.benchGameNoise(false);
		long ns = THREADS.getCurrentThreadCpuTime() - st[1];
		int arm = (int) st[0];
		AB_N[arm].increment();
		AB_NS[arm].add(ns);
		AB_SQ[arm].add((double) ns * ns);
	}

	/** The per-chunk comparison's result: mean terrain CPU per chunk for each arm, with its standard error. */
	private static void printAb() {
		if (!mcopt.metal.lod.GenServerNoise.benchAb()) return;
		double[] mean = new double[2], se = new double[2];
		for (int a = 0; a < 2; a++) {
			long n = AB_N[a].sum();
			mean[a] = AB_NS[a].sum() / (double) n;
			se[a] = Math.sqrt((AB_SQ[a].sum() / n - mean[a] * mean[a]) / n);
		}
		System.out.printf(Locale.ROOT,
			"mcopt-genbench: terrain cpu per chunk: game's noise %.3f ms +- %.3f (%d chunks), native noise %.3f ms +- %.3f (%d chunks): %.1f%% less%n",
			mean[0] / 1e6, se[0] / 1e6, AB_N[0].sum(), mean[1] / 1e6, se[1] / 1e6, AB_N[1].sum(), 100 * (1 - mean[1] / mean[0]));
	}

	/** The square's terrain hashes folded in order (a chunk with none: a marker). */
	private static long hash(ChunkPos centre) {
		long h = 0xcbf29ce484222325L;
		for (int cz = centre.z() - RADIUS; cz <= centre.z() + RADIUS; cz++) {
			for (int cx = centre.x() - RADIUS; cx <= centre.x() + RADIUS; cx++) {
				Long t = TERRAIN.get(new ChunkPos(cx, cz).pack());
				h = (h ^ (t == null ? 0xdeadL : t)) * 0x100000001B3L;
			}
		}
		return h;
	}

	private static void deleteWorld(Path dir) {
		if (!Files.exists(dir)) return;
		try (Stream<Path> s = Files.walk(dir)) {
			s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			System.out.println("mcopt-genbench: can't delete " + dir + ": " + e);
		}
	}
}
