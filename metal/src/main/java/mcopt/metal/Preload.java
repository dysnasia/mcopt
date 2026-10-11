package mcopt.metal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Loads, on background threads, the classes Minecraft is about to load one by one on its main thread during boot and
 * world load, in the order it loads them, so reading, Mixin-transforming and defining them overlaps the main thread's
 * own work instead of stalling it. Loading only (no static initializers run), so nothing about the game changes: the
 * same classes end up loaded, only sooner and off the main thread. The list (resources/mcopt/metal/preload.txt.gz)
 * comes from a -Xlog:class+load run of the shipping stack (tools/preloadlist.py); names that don't exist in some
 * other mod set are skipped. With Metal on, one more thread runs the static setup of the native bridges (see onPreLaunch).
 * -Dmcopt.preload=false turns it off, -Dmcopt.preload.threads=N sets the thread count.
 */
public final class Preload implements PreLaunchEntrypoint {
	static {
		mcopt.metal.Profile.apply(); // before any flag is read
	}

	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("mcopt.preload", "true"));
	private static final int THREADS = Integer.getInteger("mcopt.preload.threads", 2);

	@Override
	public void onPreLaunch() {
		PlatformCheck.run();
		if (!ENABLED) return;
		List<String> names = new ArrayList<>();
		try (InputStream in = Preload.class.getResourceAsStream("/mcopt/metal/preload.txt.gz")) {
			if (in == null) return;
			BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
			for (String line; (line = r.readLine()) != null; ) if (!line.isEmpty()) names.add(line);
		} catch (IOException e) {
			return;
		}
		ClassLoader loader = Preload.class.getClassLoader();
		if (Boolean.parseBoolean(System.getProperty("mcopt.metal", "true"))) {
			// Static setup the main thread otherwise runs in Minecraft.<init>, before the first resource load: Native
			// extracts libmcmetal.dylib and links its downcalls (about 0.2 s), RuntimeUtils extracts and loads the
			// Objective-C bridge the window uses. Neither touches AppKit or Metal, and the JVM makes the main thread
			// wait for an initializer still running here, so nothing changes but where the time is spent.
			Thread init = new Thread(() -> {
				for (String name : new String[] {"mcopt.metal.Native", "ca.weblite.objc.RuntimeUtils"}) {
					try {
						Class.forName(name, true, loader);
					} catch (Throwable t) {
						// The main thread's first use then fails with NoClassDefFoundError, caused by this same error.
					}
				}
			}, "mcopt native init");
			init.setDaemon(true);
			init.start();
		}
		AtomicInteger next = new AtomicInteger();
		for (int t = 0; t < THREADS; t++) {
			Thread thread = new Thread(() -> {
				for (int i; (i = next.getAndIncrement()) < names.size(); ) {
					try {
						Class.forName(names.get(i), false, loader);
					} catch (Throwable absent) {
						// Not in this mod set or environment: the game wouldn't load it either.
					}
				}
			}, "mcopt class preload #" + t);
			thread.setDaemon(true);
			thread.start();
		}
	}
}
