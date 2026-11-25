package io.github.togar2.pvp.feature.armor;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;

/**
 * Combat feature used for determining the resulting damage after armor usage.
 */
public interface ArmorFeature extends CombatFeature {
	ArmorFeature NO_OP = (entity, type, amount) -> amount;
	
	float getDamageWithProtection(LivingEntity entity, DamageType type, float amount);
	
	default float getDamageWithProtection(LivingEntity entity, Damage damage) {
		DamageType damageType = net.minestom.server.MinecraftServer.getDamageTypeRegistry().get(damage.getType());
		return getDamageWithProtection(entity, damageType, damage.getAmount());
	}
}
