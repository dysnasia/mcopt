package mcopt.metal;

import com.mojang.text2speech.Narrator;

/**
 * The game's narrator, created by {@link Narrator#getNarrator()} on first use (same object, same calls, later).
 * {@link #active()} alone does not create it on macOS: every screen asks it at init, and NarratorMac's answer is
 * the interface default, true (only a narrator that failed to load, Narrator.EMPTY, says false). So with narration
 * off, the NSSpeechSynthesizer (about 0.3 s on the main thread, before the first resource load) is never made.
 */
public final class LazyNarrator implements Narrator {
	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
	private Narrator real;

	private synchronized Narrator real() {
		if (real == null) real = Narrator.getNarrator();
		return real;
	}

	@Override public void say(String msg, boolean interrupt, float volume) { real().say(msg, interrupt, volume); }
	@Override public void clear() { real().clear(); }
	@Override public synchronized boolean active() { return real == null && MAC || real().active(); }
	@Override public synchronized void destroy() { if (real != null) real.destroy(); }
}
