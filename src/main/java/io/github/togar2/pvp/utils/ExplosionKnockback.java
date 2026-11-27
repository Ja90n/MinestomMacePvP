package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Vanilla explosion knockback without damage or block interaction (as used by wind charges and the wind burst enchantment),
 * see {@code ServerExplosion#hurtEntities}.
 */
public final class ExplosionKnockback {
	private ExplosionKnockback() {}
	
	/**
	 * Pushes all entities around the center.
	 *
	 * @param source the entity causing the explosion, which is not affected itself
	 * @param knockbackMultiplier the knockback multiplier of the explosion (1.22 for wind charges)
	 * @param fallFeature when not null, the fall distance of affected living entities is reset (wind jumps)
	 * @param onPush called for every entity that was pushed
	 */
	public static void apply(Instance instance, Point center, float radius, float knockbackMultiplier,
	                         @Nullable Entity source, @Nullable FallFeature fallFeature,
	                         @Nullable Consumer<Entity> onPush) {
		if (radius < 1.0E-5f) return;
		double doubleRadius = radius * 2.0;
		Vec centerVec = new Vec(center.x(), center.y(), center.z());
		
		for (Entity entity : List.copyOf(instance.getNearbyEntities(center, doubleRadius + 1.0))) {
			if (entity == source) continue;
			if (entity.getEntityType() == EntityType.WIND_CHARGE || entity.getEntityType() == EntityType.BREEZE_WIND_CHARGE) continue;
			if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) continue;
			
			double dist = entity.getPosition().distance(center) / doubleRadius;
			if (dist > 1.0) continue;
			
			Vec origin = entity.getPosition().add(0, entity.getEyeHeight(), 0).asVec();
			Vec direction = origin.sub(centerVec);
			direction = direction.length() < 1.0E-5 ? Vec.ZERO : direction.normalize();
			
			double exposure = VanillaExplosionSupplier.getExposure(center, entity);
			// Vanilla uses a separate attribute for explosions, regular knockback resistance has no effect
			double resistance = entity instanceof LivingEntity living
					? living.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE) : 0.0;
			double power = (1.0 - dist) * exposure * knockbackMultiplier * (1.0 - resistance);
			if (power <= 0) continue;
			
			Vec push = direction.mul(power * ServerFlag.SERVER_TICKS_PER_SECOND);
			entity.setVelocity(entity.getVelocity().add(push));
			
			if (fallFeature != null && entity instanceof LivingEntity living) fallFeature.resetFallDistance(living);
			if (entity instanceof CombatPlayer custom) custom.sendImmediateVelocityUpdate();
			if (onPush != null) onPush.accept(entity);
		}
	}
}
