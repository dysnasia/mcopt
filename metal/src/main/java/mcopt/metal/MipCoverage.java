package mcopt.metal;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.ARGB;

/**
 * -Dmcopt.startup.mipCoverage=fast|check: a faster MipmapGenerator.alphaTestCoverage with the same result, bit for bit.
 * That method (about a fifth of the worker threads' time in the first resource load) runs up to six times per mip level
 * of every cutout sprite: for each 2x2 block of pixels it interpolates the four alphas at 16 points and counts the
 * points above the cutoff. Here each pixel's alpha is read once instead of four times, and a block whose four alphas
 * are all clearly below the cutoff counts 0, all clearly above counts 16, without the 16 interpolations: a bilinear
 * value lies between its corners, and the 1e-4 margin is far wider than the float rounding of one interpolation (about
 * 1e-6), so those blocks count exactly what vanilla counts. Every other block runs vanilla's arithmetic in vanilla's
 * order, and the per-block sums add up in vanilla's order too. "check" runs both and logs any difference.
 */
public final class MipCoverage {
	private static final float MARGIN = 1e-4f;
	private static final AtomicLong CHECKED = new AtomicLong(), DIFFERENT = new AtomicLong();

	private MipCoverage() {
	}

	public static float alphaTestCoverage(NativeImage image, float cutoff, float alphaScale) {
		int w = image.getWidth(), h = image.getHeight();
		float[] a = new float[w * h];
		for (int y = 0, i = 0; y < h; y++) {
			for (int x = 0; x < w; x++, i++) a[i] = Math.clamp(ARGB.alphaFloat(image.getPixel(x, y)) * alphaScale, 0.0F, 1.0F);
		}
		float coverage = 0.0F;
		for (int y = 0; y < h - 1; y++) {
			for (int x = 0; x < w - 1; x++) {
				int i = y * w + x;
				float a00 = a[i], a10 = a[i + 1], a01 = a[i + w], a11 = a[i + w + 1];
				float lo = Math.min(Math.min(a00, a10), Math.min(a01, a11));
				float hi = Math.max(Math.max(a00, a10), Math.max(a01, a11));
				float count;
				if (hi < cutoff - MARGIN) {
					count = 0.0F;
				} else if (lo > cutoff + MARGIN) {
					count = 16.0F;
				} else {
					count = 0.0F;
					for (int sy = 0; sy < 4; sy++) {
						float t = (sy + 0.5F) / 4.0F;
						for (int sx = 0; sx < 4; sx++) {
							float s = (sx + 0.5F) / 4.0F;
							float v = a00 * (1.0F - s) * (1.0F - t) + a10 * s * (1.0F - t) + a01 * (1.0F - s) * t + a11 * s * t;
							if (v > cutoff) count++;
						}
					}
				}
				coverage += count / 16.0F;
			}
		}
		return coverage / ((w - 1) * (h - 1));
	}

	/** "check": vanilla's result next to ours; logs each difference and, every 1000 calls, the totals. */
	public static void check(float vanilla, float fast, NativeImage image, float cutoff, float alphaScale) {
		long n = CHECKED.incrementAndGet();
		if (Float.floatToRawIntBits(vanilla) != Float.floatToRawIntBits(fast)) {
			DIFFERENT.incrementAndGet();
			System.out.println("mcopt-mip-coverage: DIFFERENT " + image.getWidth() + "x" + image.getHeight() + " cutoff " + cutoff
				+ " scale " + alphaScale + ": vanilla " + vanilla + ", fast " + fast);
		}
		if (n % 1000 == 0) System.out.println("mcopt-mip-coverage: " + n + " checked, " + DIFFERENT.get() + " different");
	}
}
