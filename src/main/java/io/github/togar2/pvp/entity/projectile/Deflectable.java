package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.entity.Entity;

/**
 * A projectile which can be redirected by hitting it or by hitting it with another projectile
 * (the vanilla {@code minecraft:redirectable_projectile} entity type tag: fireballs and wind charges).
 */
public interface Deflectable {
	/**
	 * Vanilla {@code ProjectileDeflection#AIM_DEFLECT}: the projectile flies in the look direction of the
	 * deflecting entity, which becomes its new owner.
	 *
	 * @return whether the projectile was deflected
	 */
	boolean deflect(Entity deflector);
}
