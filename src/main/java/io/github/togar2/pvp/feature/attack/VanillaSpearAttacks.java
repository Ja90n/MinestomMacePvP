package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.cooldown.AttackCooldownFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.KnownMovement;
import io.github.togar2.pvp.utils.RayUtil;
import io.github.togar2.pvp.utils.TeamUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttackRange;
import net.minestom.server.item.component.KineticWeapon;
import net.minestom.server.item.component.PiercingWeapon;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla (26.2) spear mechanics: the jab ({@code PiercingWeapon#attack}), the charge while using the item
 * ({@code KineticWeapon#damageEntities}), {@code LivingEntity#stabAttack} and the lunge enchantment.
 */
final class VanillaSpearAttacks {
	private static final Tag<Map<Integer, Long>> RECENT_ENEMIES = Tag.Transient("minestompvp:recent_kinetic_enemies");
	private static final float MINIMUM_ATTACK_CHARGE_TOLERANCE_TICKS = 5;
	
	private final AttackCooldownFeature cooldownFeature;
	private final ExhaustionFeature exhaustionFeature;
	private final ItemDamageFeature itemDamageFeature;
	private final EnchantmentFeature enchantmentFeature;
	private final KnockbackFeature knockbackFeature;
	private final FallFeature fallFeature;
	
	VanillaSpearAttacks(AttackCooldownFeature cooldownFeature, ExhaustionFeature exhaustionFeature,
	                    ItemDamageFeature itemDamageFeature, EnchantmentFeature enchantmentFeature,
	                    KnockbackFeature knockbackFeature, FallFeature fallFeature) {
		this.cooldownFeature = cooldownFeature;
		this.exhaustionFeature = exhaustionFeature;
		this.itemDamageFeature = itemDamageFeature;
		this.enchantmentFeature = enchantmentFeature;
		this.knockbackFeature = knockbackFeature;
		this.fallFeature = fallFeature;
	}
	
	static boolean isPiercing(ItemStack stack) {
		return stack.get(DataComponents.PIERCING_WEAPON) != null;
	}
	
	/** Vanilla {@code Player#cannotAttackWithItem}: items can require a minimum attack charge. */
	boolean cannotAttackWithItem(Player player, ItemStack stack, int toleranceTicks) {
		Float required = stack.get(DataComponents.MINIMUM_ATTACK_CHARGE);
		if (required == null || required <= 0) return false;
		double delay = 1.0 / player.getAttributeValue(Attribute.ATTACK_SPEED) * 20.0;
		double progress = cooldownFeature.getAttackCooldownProgress(player);
		double optimistic = progress >= 1.0 ? 1.0 : progress + (toleranceTicks - 0.5) / delay;
		return optimistic < required;
	}
	
	// ---- Jab (left click) ----
	
	void jab(Player player) {
		if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return;
		ItemStack stack = player.getItemInMainHand();
		PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
		if (piercing == null) return;
		if (cannotAttackWithItem(player, stack, (int) MINIMUM_ATTACK_CHARGE_TOLERANCE_TICKS)) return;
		
		float damage = (float) player.getAttributeValue(Attribute.ATTACK_DAMAGE);
		boolean hitSomething = false;
		for (Entity target : hitEntities(player, getAttackRange(player, stack))) {
			hitSomething |= stab(player, target, damage, true, piercing.dealsKnockback(), piercing.dismounts(), false);
		}
		
		// Player#onAttack resets the attack strength, then the enchantments run their post piercing attack effects
		cooldownFeature.resetCooldownProgress(player);
		int lunge = enchantmentFeature.getLunge(player);
		if (lunge > 0) performLunge(player, lunge);
		
		if (hitSomething && piercing.hitSound() != null) {
			ViewUtil.viewersAndSelf(player).playSound(Sound.sound(piercing.hitSound(), Sound.Source.PLAYER, 1f, 1f), player);
		}
		if (piercing.sound() != null) {
			ViewUtil.viewersAndSelf(player).playSound(Sound.sound(piercing.sound(), Sound.Source.PLAYER, 1f, 1f), player);
		}
		player.swingMainHand(false);
	}
	
	// ---- Charge (right click, hold) ----
	
	void startKinetic(Player player, ItemStack stack) {
		KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
		if (kinetic == null) return;
		player.setTag(RECENT_ENEMIES, new HashMap<>());
		if (kinetic.sound() != null) {
			ViewUtil.viewersAndSelf(player).playSound(Sound.sound(kinetic.sound(), Sound.Source.PLAYER, 1f, 1f), player);
		}
	}
	
	void tickKinetic(Player player) {
		if (!player.isUsingItem() || player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return;
		PlayerHand hand = player.getItemUseHand();
		ItemStack stack = hand == PlayerHand.MAIN ? player.getItemInMainHand() : player.getItemInOffHand();
		KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
		if (kinetic == null) return;
		
		long ticksUsed = player.getCurrentItemUseTime();
		if (ticksUsed < kinetic.delayTicks()) return;
		ticksUsed -= kinetic.delayTicks();
		
		Vec look = player.getPosition().direction();
		double attackerSpeed = look.dot(motion(player));
		double actionFactor = 1.0; // 0.2 for mobs
		double baseMobDamage = player.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
		
		Map<Integer, Long> recent = player.getTag(RECENT_ENEMIES);
		if (recent == null) {
			recent = new HashMap<>();
			player.setTag(RECENT_ENEMIES, recent);
		}
		
		for (Entity target : hitEntities(player, getAttackRange(player, stack))) {
			Long last = recent.get(target.getEntityId());
			if (last != null && player.getAliveTicks() - last < kinetic.contactCooldownTicks()) continue;
			recent.put(target.getEntityId(), player.getAliveTicks());
			
			double targetSpeed = look.dot(motion(target));
			double relativeSpeed = Math.max(0.0, attackerSpeed - targetSpeed);
			boolean dismount = test(kinetic.dismountConditions(), ticksUsed, attackerSpeed, relativeSpeed, actionFactor);
			boolean knockback = test(kinetic.knockbackConditions(), ticksUsed, attackerSpeed, relativeSpeed, actionFactor);
			boolean damage = test(kinetic.damageConditions(), ticksUsed, attackerSpeed, relativeSpeed, actionFactor);
			if (dismount || knockback || damage) {
				float damageDealt = (float) baseMobDamage + (float) Math.floor(relativeSpeed * kinetic.damageMultiplier());
				boolean affected = stab(player, target, damageDealt, damage, knockback, dismount, true);
				if (affected && kinetic.hitSound() != null) {
					player.playSound(Sound.sound(kinetic.hitSound(), Sound.Source.PLAYER, 1f, 1f), Sound.Emitter.self());
				}
			}
		}
	}
	
	private static boolean test(KineticWeapon.Condition condition, long ticksUsed, double attackerSpeed,
	                            double relativeSpeed, double entityFactor) {
		return condition != null && ticksUsed <= condition.maxDurationTicks()
				&& attackerSpeed >= condition.minSpeed() * entityFactor
				&& relativeSpeed >= condition.minRelativeSpeed() * entityFactor;
	}
	
	/** Vanilla {@code KineticWeapon#getMotion}: blocks per second of the entity (or its vehicle for non-players). */
	private static Vec motion(Entity entity) {
		if (!(entity instanceof Player) && entity.getVehicle() != null) {
			Entity root = entity.getVehicle();
			while (root.getVehicle() != null) root = root.getVehicle();
			entity = root;
		}
		return KnownMovement.get(entity).mul(ServerFlag.SERVER_TICKS_PER_SECOND);
	}
	
	// ---- Stabbing ----
	
	/**
	 * Vanilla {@code Player#stabAttack}.
	 *
	 * @param usingItem whether the stab is done by charging (right click), which is not scaled by the attack cooldown
	 * @return whether the stab affected the target
	 */
	boolean stab(LivingEntity attacker, Entity target, float baseDamage, boolean dealsDamage,
	             boolean dealsKnockback, boolean dismounts, boolean usingItem) {
		if (target == attacker || target.isRemoved()) return false;
		ItemStack weapon = attacker.getItemInMainHand();
		
		float magicBoost = enchantmentFeature.getAttackDamage(weapon,
				target instanceof LivingEntity living ? EntityGroup.ofEntity(living) : EntityGroup.DEFAULT);
		if (!usingItem && attacker instanceof Player player) {
			double strength = cooldownFeature.getAttackCooldownProgress(player);
			magicBoost *= (float) strength;
			baseDamage *= (float) (0.2 + strength * strength * 0.8);
		}
		
		float totalDamage = dealsDamage ? baseDamage + magicBoost : 0.0f;
		float oldHealth = target instanceof LivingEntity living ? living.getHealth() : 0;
		
		boolean wasHurt = dealsDamage && target instanceof LivingEntity livingTarget
				&& livingTarget.damage(new Damage(DamageType.SPEAR, attacker, attacker, null, totalDamage));
		
		if (dealsKnockback && target instanceof LivingEntity livingTarget) {
			// causeExtraKnockback(0.4) followed by causeExtraKnockback(enchantment knockback)
			knockbackFeature.applySweepingKnockback(attacker, livingTarget);
			slowAttacker(attacker);
			double extra = enchantmentFeature.getKnockback(attacker) + attacker.getAttributeValue(Attribute.ATTACK_KNOCKBACK);
			if (extra > 0) knockbackFeature.applyAttackKnockback(attacker, livingTarget, extra);
			if (target instanceof CombatPlayer custom) custom.sendImmediateVelocityUpdate();
		}
		
		boolean dismounted = false;
		if (dismounts && target.getVehicle() != null) {
			dismounted = true;
			target.getVehicle().removePassenger(target);
		}
		
		if (!wasHurt && !dealsKnockback && !dismounted) return false;
		
		if (magicBoost > 0 && dealsDamage) {
			attacker.sendPacketToViewersAndSelf(new EntityAnimationPacket(
					target.getEntityId(), EntityAnimationPacket.Animation.MAGICAL_CRITICAL_EFFECT));
		}
		
		// itemAttackInteraction: the weapon is damaged for every living target, enchantment effects need a hurt target
		if (target instanceof LivingEntity livingTarget) {
			itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, 1);
			if (wasHurt) {
				enchantmentFeature.onUserDamaged(livingTarget, attacker);
				enchantmentFeature.onTargetDamaged(attacker, livingTarget);
				int fireAspect = enchantmentFeature.getFireAspect(attacker);
				if (fireAspect > 0) livingTarget.setFireTicks(fireAspect * 4 * ServerFlag.SERVER_TICKS_PER_SECOND);
			}
			float damageDone = oldHealth - livingTarget.getHealth();
			if (damageDone > 2) {
				Pos pos = target.getPosition();
				target.sendPacketToViewersAndSelf(new net.minestom.server.network.packet.server.play.ParticlePacket(
						net.minestom.server.particle.Particle.DAMAGE_INDICATOR, false, false,
						pos.x(), pos.y() + target.getBoundingBox().height() * 0.5, pos.z(),
						0.1f, 0, 0.1f, 0.2f, (int) (damageDone * 0.5)));
			}
		}
		if (attacker instanceof Player player) exhaustionFeature.addAttackExhaustion(player);
		return true;
	}
	
	/** The attacker is slowed down and stops sprinting whenever knockback is applied (vanilla causeExtraKnockback). */
	private static void slowAttacker(LivingEntity attacker) {
		if (attacker instanceof CombatPlayer custom) custom.afterSprintAttack();
		else attacker.setVelocity(attacker.getVelocity().mul(0.6, 1, 0.6));
		attacker.setSprinting(false);
	}
	
	// ---- Hit detection ----
	
	private AttackRange getAttackRange(LivingEntity attacker, ItemStack stack) {
		AttackRange range = stack.get(DataComponents.ATTACK_RANGE);
		if (range != null) return range;
		float reach = (float) attacker.getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE);
		return new AttackRange(0, reach, 0, reach, 0, 1);
	}
	
	/** Vanilla {@code ProjectileUtil#getHitEntitiesAlong}: every entity along the look direction within the range. */
	List<Entity> hitEntities(LivingEntity attacker, AttackRange range) {
		Instance instance = attacker.getInstance();
		if (instance == null) return List.of();
		
		Vec look = attacker.getPosition().direction();
		Pos eyePos = attacker.getPosition().add(0, attacker.getEyeHeight(), 0);
		Vec eye = eyePos.asVec();
		double minReach = range.effectiveMinReach(attacker);
		double maxReach = range.effectiveMaxReach(attacker);
		double movement = Math.max(0.0, KnownMovement.get(attacker).dot(look));
		Vec from = eye.add(look.mul(minReach));
		Vec to = eye.add(look.mul(maxReach + movement));
		
		double fraction = RayUtil.unobstructedFraction(instance, eye, to);
		if (fraction < 1.0) {
			Vec blockHit = eye.add(to.sub(eye).mul(fraction));
			if (eye.distanceSquared(blockHit) < eye.distanceSquared(from)) return List.of();
			to = blockHit;
		}
		
		double margin = range.hitboxMargin();
		List<Entity> result = new ArrayList<>();
		for (Entity entity : instance.getNearbyEntities(eyePos, maxReach + movement + 3)) {
			if (entity == attacker || !canHitEntity(attacker, entity)) continue;
			
			var box = entity.getBoundingBox();
			Pos pos = entity.getPosition();
			Vec min = new Vec(pos.x() + box.minX(), pos.y() + box.minY(), pos.z() + box.minZ());
			Vec max = new Vec(pos.x() + box.maxX(), pos.y() + box.maxY(), pos.z() + box.maxZ());
			
			boolean inside = from.x() >= min.x() && from.x() <= max.x() && from.y() >= min.y() && from.y() <= max.y()
					&& from.z() >= min.z() && from.z() <= max.z();
			if (inside || RayUtil.clipSegment(min, max, from, to) != null) {
				result.add(entity);
			} else if (margin > 0) {
				Vec outside = RayUtil.clipSegment(min.sub(margin, margin, margin), max.add(margin, margin, margin), from, to);
				if (outside != null) {
					// The margin only counts if no block is in between the hit and the entity
					Vec center = min.add(max).mul(0.5);
					if (io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier.noBlocking(instance, outside, center)) {
						result.add(entity);
					}
				}
			}
		}
		return result;
	}
	
	/** Vanilla {@code PiercingWeapon#canHitEntity}. */
	private static boolean canHitEntity(LivingEntity jabber, Entity target) {
		if (target.isRemoved() || (target instanceof LivingEntity living && living.isDead())) return false;
		if (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
		if (target.getEntityType() == EntityType.ITEM || target.getEntityType() == EntityType.EXPERIENCE_ORB) return false;
		Entity jabberRoot = jabber;
		while (jabberRoot.getVehicle() != null) jabberRoot = jabberRoot.getVehicle();
		Entity targetRoot = target;
		while (targetRoot.getVehicle() != null) targetRoot = targetRoot.getVehicle();
		if (jabber instanceof Player jabbingPlayer && target instanceof Player targetPlayer
				&& !TeamUtil.canHarmPlayer(jabbingPlayer, targetPlayer)) return false;
		return jabberRoot != targetRoot;
	}
	
	// ---- Lunge ----
	
	/** Vanilla lunge: the post piercing attack effects of the lunge enchantment. */
	boolean performLunge(LivingEntity attacker, int lunge) {
		if (lunge <= 0) return false;
		if (attacker.getVehicle() != null || attacker.isFlyingWithElytra()) return false;
		if (attacker instanceof Player player) {
			if (FluidUtil.isTouchingWater(player)) return false;
			if (player.getGameMode() != GameMode.CREATIVE && player.getFood() < 7) return false;
		}
		
		itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, 1);
		if (attacker instanceof Player player) exhaustionFeature.addExhaustion(player, 4.0f * lunge);
		
		// apply_impulse: the look direction, scaled by (1, 0, 1), with magnitude 0.458 per level
		Vec look = attacker.getPosition().direction();
		Vec impulse = new Vec(look.x(), 0, look.z()).mul(0.458 * lunge * ServerFlag.SERVER_TICKS_PER_SECOND);
		attacker.setVelocity(attacker.getVelocity().add(impulse));
		if (attacker instanceof CombatPlayer custom) custom.sendImmediateVelocityUpdate();
		
		SoundEvent sound = lunge >= 3 ? SoundEvent.ITEM_SPEAR_LUNGE_3 : (lunge == 2 ? SoundEvent.ITEM_SPEAR_LUNGE_2 : SoundEvent.ITEM_SPEAR_LUNGE_1);
		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(sound, Sound.Source.PLAYER, 1f, 1f), attacker);
		return true;
	}
}
