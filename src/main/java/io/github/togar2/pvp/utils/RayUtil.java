package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;

/**
 * Ray helpers for blocks and bounding boxes.
 */
public final class RayUtil {
	private RayUtil() {}
	
	/**
	 * @return the fraction (0 to 1) of the segment from origin to target that can be travelled before a solid block is hit
	 */
	public static double unobstructedFraction(Instance instance, Vec origin, Vec target) {
		if (VanillaExplosionSupplier.noBlocking(instance, origin, target)) return 1.0;
		Vec delta = target.sub(origin);
		double lo = 0, hi = 1;
		for (int i = 0; i < 12; i++) {
			double mid = (lo + hi) / 2;
			if (VanillaExplosionSupplier.noBlocking(instance, origin, origin.add(delta.mul(mid)))) lo = mid;
			else hi = mid;
		}
		return lo;
	}
	
	/**
	 * Clips the segment from-to against a box.
	 *
	 * @return the point where the segment enters the box (or the start if it is inside), or null if it does not touch the box
	 */
	public static Vec clipSegment(Vec min, Vec max, Vec from, Vec to) {
		double tMin = 0, tMax = 1;
		double[] f = {from.x(), from.y(), from.z()};
		double[] d = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1.0E-9) {
				if (f[i] < lo[i] || f[i] > hi[i]) return null;
			} else {
				double t1 = (lo[i] - f[i]) / d[i], t2 = (hi[i] - f[i]) / d[i];
				if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) return null;
			}
		}
		return new Vec(f[0] + d[0] * tMin, f[1] + d[1] * tMin, f[2] + d[2] * tMin);
	}
}
