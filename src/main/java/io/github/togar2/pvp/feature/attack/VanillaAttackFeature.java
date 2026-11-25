package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.events.FinalAttackEvent;
import io.github.togar2.pvp.events.PrepareAttackEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.AttackCooldownFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.KineticWeapon;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Vanilla implementation of {@link AttackFeature}
 * <p>
 * Listens on {@link EntityAttackEvent}
 */
public class VanillaAttackFeature implements AttackFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaAttackFeature> DEFINED = new DefinedFeature<>(
		FeatureType.ATTACK, VanillaAttackFeature::new,
		FeatureType.ATTACK_COOLDOWN, FeatureType.EXHAUSTION, FeatureType.ITEM_DAMAGE,
		FeatureType.ENCHANTMENT, FeatureType.CRITICAL, FeatureType.SWEEPING, FeatureType.KNOCKBACK,
		FeatureType.FALL, FeatureType.VERSION
	);

	private static final double ATTACK_RANGE_MARGIN = 3.0;

	private final FeatureConfiguration configuration;

	private AttackCooldownFeature cooldownFeature;
	private ExhaustionFeature exhaustionFeature;
	private ItemDamageFeature itemDamageFeature;
	private EnchantmentFeature enchantmentFeature;

	private CriticalFeature criticalFeature;
	private SweepingFeature sweepingFeature;
	private KnockbackFeature knockbackFeature;
	private FallFeature fallFeature;

	private CombatVersion version;

	public VanillaAttackFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.cooldownFeature = configuration.get(FeatureType.ATTACK_COOLDOWN);
		this.exhaustionFeature = configuration.get(FeatureType.EXHAUSTION);
		this.itemDamageFeature = configuration.get(FeatureType.ITEM_DAMAGE);
		this.enchantmentFeature = configuration.get(FeatureType.ENCHANTMENT);
		this.criticalFeature = configuration.get(FeatureType.CRITICAL);
		this.sweepingFeature = configuration.get(FeatureType.SWEEPING);
		this.knockbackFeature = configuration.get(FeatureType.KNOCKBACK);
		this.fallFeature = configuration.get(FeatureType.FALL);
		this.version = configuration.get(FeatureType.VERSION);
	}

	public static final Tag<Long> LAST_LUNGE_TICK = Tag.Long("lastLungeTick");

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityAttackEvent.class, event -> {
			if (event.getEntity() instanceof Player player && player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
				Entity target = event.getTarget();
				double maxDistanceSquared = Math.pow(player.getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + ATTACK_RANGE_MARGIN, 2);
				if (player.getPosition().distanceSquared(target.getPosition().add(0, target.getEyeHeight(), 0)) < maxDistanceSquared)
					performAttack(player, target);
			}
		});

		node.addListener(PlayerHandAnimationEvent.class, event -> {
			Player player = event.getPlayer();
			if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return;
			if (event.getHand() != PlayerHand.MAIN) return;

			handleSpearLungeAction(player);
		});

		node.addListener(PlayerUseItemEvent.class, event -> {
			Player player = event.getPlayer();
			if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return;
			if (event.getHand() != PlayerHand.MAIN) return;

			handleSpearLungeAction(player);
		});
	}

	private void handleSpearLungeAction(Player player) {
		ItemStack stack = player.getItemInMainHand();
		Tool tool = Tool.fromMaterial(stack.material());
		if (tool != null && tool.isSpear()) {
			int lunge = enchantmentFeature.getLunge(player);
			if (lunge > 0) {
				if (cooldownFeature.getAttackCooldownProgress(player) >= 0.95) {
					if (performLunge(player, lunge)) {
						cooldownFeature.resetCooldownProgress(player);
					}
				}
			}
		}
	}

	@Override
	public boolean performAttack(LivingEntity attacker, Entity target) {
		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		if (tool != null && tool.isSpear()) {
			if (attacker instanceof Player player) {
				if (cooldownFeature.getAttackCooldownProgress(player) < 0.95) return false;
			}
		}

		PrepareAttackEvent prepareAttackEvent = new PrepareAttackEvent(attacker, target);
		EventDispatcher.call(prepareAttackEvent);
		if (prepareAttackEvent.isCancelled()) return false;
		AttackValues.Final attack = prepareAttack(attacker, target);
		if (attack == null) return false; // Event cancelled

		RegistryKey<DamageType> damageType;
		if (attack.maceSmash()) {
			damageType = DamageType.MACE_SMASH;
		} else if (attack.spear()) {
			damageType = DamageType.SPEAR;
		} else {
			damageType = attacker instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK;
		}

		float originalHealth = 0;
		boolean damageSucceeded = false;
		if (target instanceof LivingEntity livingTarget) {
			originalHealth = livingTarget.getHealth();
			damageSucceeded = livingTarget.damage(new Damage(
				damageType,
				attacker, attacker,
				null, attack.damage()
			));
		}

		if (!damageSucceeded) {
			// Vanilla parity: Mace smash fall damage negation applies even when blocked by a shield
			if (attack.maceSmash()) {
				fallFeature.resetFallDistance(attacker);
			}

			if (tool != null) {
				itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND,
					(tool.isSword() || tool == Tool.TRIDENT || tool.isMace() || tool.isSpear()) ? 1 : 2);
			}
			if (attacker instanceof Player player) {
				exhaustionFeature.addAttackExhaustion(player);
			}

			// No damage sound
			if (attack.sounds() && attack.playSoundsOnFail()) {
				ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ENTITY_PLAYER_ATTACK_NODAMAGE, Sound.Source.PLAYER,
					1.0f, 1.0f
				), attacker);
			}
			return false;
		}

		// Target is always living now, because the damage would not have succeeded if it wasn't
		LivingEntity living = (LivingEntity) target;
		Collection<LivingEntity> affectedEntities = List.of(living);

		// Knockback and sweeping
		knockbackFeature.applyAttackKnockback(attacker, living, attack.knockback());
		if (attack.sweeping()) {
			affectedEntities = sweepingFeature.applySweeping(attacker, living, attack.damage());
			affectedEntities.add(living);
		}

		if (target instanceof CombatPlayer custom)
			custom.sendImmediateVelocityUpdate();

		if (attack.maceSmash()) {
			// 1. Negate fall damage
			fallFeature.resetFallDistance(attacker);

			// 2. AoE Smash Knockback
			if (target.getInstance() != null) {
				Pos targetPos = target.getPosition();
				for (Entity nearby : target.getInstance().getNearbyEntities(targetPos, 3.5)) {
					if (nearby == target || nearby == attacker) continue;
					if (!(nearby instanceof LivingEntity livingNearby)) continue;
					if (nearby.getEntityType() == EntityType.ARMOR_STAND) continue;

					double dist = targetPos.distance(nearby.getPosition());
					if (dist <= 3.5) {
						double strength = (3.5 - dist) / 3.5 * 0.7;
						double kbResistance = livingNearby.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
						double horizontalImpulse = strength * Math.max(0.0, 1.0 - kbResistance) * ServerFlag.SERVER_TICKS_PER_SECOND;
						double verticalImpulse = Math.min(0.5, 0.35 * (3.5 - dist) / 3.5) * ServerFlag.SERVER_TICKS_PER_SECOND;

						Pos nearbyPos = nearby.getPosition();
						Vec dir = new Vec(nearbyPos.x() - targetPos.x(), 0, nearbyPos.z() - targetPos.z());
						if (dir.isZero()) {
							dir = new Vec(1, 0, 0);
						} else {
							dir = dir.normalize();
						}

						livingNearby.setVelocity(livingNearby.getVelocity().add(
							dir.x() * horizontalImpulse,
							verticalImpulse,
							dir.z() * horizontalImpulse
						));
						if (livingNearby instanceof CombatPlayer customNearby) {
							customNearby.sendImmediateVelocityUpdate();
						}
					}
				}
			}

			// 3. Wind Burst
			int windBurst = enchantmentFeature.getWindBurst(attacker);
			if (windBurst > 0) {
				double upwardBlocksPerTick = windBurst == 1 ? 1.2 : (windBurst == 2 ? 1.75 : 2.2);
				double upwardVel = upwardBlocksPerTick * ServerFlag.SERVER_TICKS_PER_SECOND;
				attacker.setVelocity(new Vec(attacker.getVelocity().x(), upwardVel, attacker.getVelocity().z()));
				if (attacker instanceof CombatPlayer customAttacker) {
					customAttacker.sendImmediateVelocityUpdate();
				}
				ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, Sound.Source.PLAYER,
					1.0f, 1.0f
				), attacker);
				attacker.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.GUST_EMITTER_SMALL, false, false,
					attacker.getPosition().x(), attacker.getPosition().y(), attacker.getPosition().z(),
					0, 0, 0, 0, 1
				));
			}

			// 4. Smash particles
			if (target.getInstance() != null) {
				Block groundBlock = target.getInstance().getBlock(target.getPosition().sub(0, 0.5, 0));
				if (groundBlock.air()) groundBlock = target.getInstance().getBlock(target.getPosition().sub(0, 1.0, 0));
				if (groundBlock.air()) groundBlock = Block.STONE;
				target.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.DUST_PILLAR.withBlock(groundBlock), false, false,
					target.getPosition().x(), target.getPosition().y(), target.getPosition().z(),
					0.2f, 0.2f, 0.2f, 0.15f, 20
				));
				target.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.DUST_PLUME, false, false,
					target.getPosition().x(), target.getPosition().y(), target.getPosition().z(),
					0.5f, 0.5f, 0.5f, 0.1f, 10
				));
			}

			// 5. Smash sounds
			SoundEvent smashSound = attack.fallDistance() >= 5.0 ? SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvent.ITEM_MACE_SMASH_GROUND;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(smashSound, Sound.Source.PLAYER, 1.0f, 1.0f), target);
		}

		if (attack.spear()) {
			if (attack.dismountTarget() && living.getVehicle() != null) {
				living.getVehicle().removePassenger(living);
			}

			if (attack.kineticDamage() > 0) {
				KineticWeapon kw = attacker.getItemInMainHand().get(DataComponents.KINETIC_WEAPON);
				SoundEvent hitSound = kw != null && kw.hitSound() != null ? kw.hitSound() : SoundEvent.ITEM_SPEAR_HIT;
				ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(hitSound, Sound.Source.PLAYER, 1.0f, 1.0f), target);
			}

			int lunge = enchantmentFeature.getLunge(attacker);
			if (lunge > 0) {
				performLunge(attacker, lunge);
			}
		}

		// Play attack sounds
		if (attack.sounds() && !attack.maceSmash()) {
			Audience audience = attacker.getViewersAsAudience();
			if (attacker instanceof Player player)
				audience = Audience.audience(audience, player);

			if (attack.sprint()) audience.playSound(Sound.sound(
				SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, Sound.Source.PLAYER,
				1.0f, 1.0f
			), attacker);

			if (attack.sweeping()) audience.playSound(Sound.sound(
				SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, Sound.Source.PLAYER,
				1.0f, 1.0f
			), attacker);

			if (attack.critical()) audience.playSound(Sound.sound(
				SoundEvent.ENTITY_PLAYER_ATTACK_CRIT, Sound.Source.PLAYER,
				1.0f, 1.0f
			), attacker);

			if (!attack.critical() && !attack.sweeping()) audience.playSound(Sound.sound(
				attack.strong() ?
					SoundEvent.ENTITY_PLAYER_ATTACK_STRONG :
					SoundEvent.ENTITY_PLAYER_ATTACK_WEAK,
				Sound.Source.PLAYER, 1.0f, 1.0f
			), attacker);
		}

		// Play attack effects
		if (attack.critical()) attacker.sendPacketToViewersAndSelf(new EntityAnimationPacket(
			target.getEntityId(),
			EntityAnimationPacket.Animation.CRITICAL_EFFECT
		));
		if (attack.magical()) attacker.sendPacketToViewersAndSelf(new EntityAnimationPacket(
			target.getEntityId(),
			EntityAnimationPacket.Animation.MAGICAL_CRITICAL_EFFECT
		));

		for (LivingEntity affectedEntity : affectedEntities) {
			// Thorns
			enchantmentFeature.onUserDamaged(affectedEntity, attacker);
			enchantmentFeature.onTargetDamaged(attacker, affectedEntity);

			if (attack.fireAspect() > 0) {
				for (LivingEntity entity : affectedEntities) {
					entity.setFireTicks(attack.fireAspect() * 4 * ServerFlag.SERVER_TICKS_PER_SECOND);
				}
			}
		}

		// Damage item
		if (tool != null) itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND,
			(tool.isSword() || tool == Tool.TRIDENT || tool.isMace() || tool.isSpear()) ? 1 : 2);

		// Damage indicator particles
		float damageDone = originalHealth - living.getHealth();
		if (damageDone > 2) {
			int particleCount = (int) (damageDone * 0.5);
			Pos targetPosition = target.getPosition();
			target.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.DAMAGE_INDICATOR, false, false,
				targetPosition.x(), targetPosition.y() + target.getBoundingBox().height() * 0.5, targetPosition.z(),
				0.1f, 0, 0.1f,
				0.2F, particleCount
			));
		}

		if (attacker instanceof Player player)
			exhaustionFeature.addAttackExhaustion(player);

		return true;
	}

	protected @Nullable AttackValues.Final prepareAttack(LivingEntity attacker, Entity target) {
		float damage = (float) attacker.getAttributeValue(Attribute.ATTACK_DAMAGE);
		float magicalDamage = enchantmentFeature.getAttackDamage(
			attacker.getItemInMainHand(),
			target instanceof LivingEntity living ? EntityGroup.ofEntity(living) : EntityGroup.DEFAULT
		);

		double cooldownProgress = 1;
		if (attacker instanceof Player player) {
			cooldownProgress = cooldownFeature.getAttackCooldownProgress(player);
			cooldownFeature.resetCooldownProgress(player);
		}

		// Apply cooldownProgress to damage
		damage *= (float) (0.2 + cooldownProgress * cooldownProgress * 0.8);
		magicalDamage *= (float) cooldownProgress;

		// Calculate attacks
		boolean strongAttack = cooldownProgress > 0.9;
		boolean sprintAttack = attacker.isSprinting() && strongAttack;
		int knockback = enchantmentFeature.getKnockback(attacker) + (int) attacker.getAttributeValue(Attribute.ATTACK_KNOCKBACK);
		int fireAspect = enchantmentFeature.getFireAspect(attacker);

		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		boolean isMace = tool != null && tool.isMace();
		boolean isSpear = tool != null && tool.isSpear();
		double fallDistance = fallFeature.getFallDistance(attacker);
		boolean inCobweb = attacker.getInstance() != null && attacker.getInstance().getBlock(attacker.getPosition()).compare(Block.COBWEB);
		boolean inWater = attacker instanceof Player p && FluidUtil.isTouchingWater(p);
		boolean isFallFlying = attacker.isFlyingWithElytra() || (attacker instanceof Player p && p.isFlying());
		boolean maceSmash = isMace && fallDistance > 1.5 && attacker.getVehicle() == null && !isFallFlying && !inCobweb && !inWater;

		// Use features to determine critical and sweeping
		AttackValues.PreCritical preCritical = new AttackValues.PreCritical(
			damage, magicalDamage, cooldownProgress,
			strongAttack, sprintAttack, knockback, fireAspect
		);
		AttackValues.PreSweeping preSweeping = preCritical.withCritical(criticalFeature.shouldCrit(attacker, preCritical));
		AttackValues.PreSounds preSounds = preSweeping.withSweeping(sweepingFeature.shouldSweep(attacker, preSweeping));

		boolean critical = preSounds.critical();
		boolean sweeping = preSounds.sweeping();
		if (maceSmash) critical = false;

		boolean sounds = version.modern();

		// Call event which can modify attack values
		FinalAttackEvent finalAttackEvent = new FinalAttackEvent(
			attacker, target, sprintAttack, critical, sweeping, damage,
			magicalDamage, sounds, sounds
		);
		EventDispatcher.call(finalAttackEvent);
		if (finalAttackEvent.isCancelled()) return null;

		sprintAttack = finalAttackEvent.isSprint();
		critical = finalAttackEvent.isCritical();
		sweeping = finalAttackEvent.isSweeping();
		damage = finalAttackEvent.getBaseDamage();
		magicalDamage = finalAttackEvent.getEnchantsExtraDamage();

		if (maceSmash) {
			critical = false;
			double remaining = fallDistance;
			float smashBonus = 0.0f;
			double stage1 = Math.min(remaining, 3.0);
			smashBonus += (float) (stage1 * 4.0);
			remaining -= stage1;
			if (remaining > 0) {
				double stage2 = Math.min(remaining, 5.0);
				smashBonus += (float) (stage2 * 2.0);
				remaining -= stage2;
				if (remaining > 0) {
					smashBonus += (float) (remaining * 1.0);
				}
			}
			int density = enchantmentFeature.getDensity(attacker);
			if (density > 0) {
				smashBonus += (float) (density * 0.5 * fallDistance);
			}
			damage += smashBonus;
		}

		float kineticDamage = 0.0f;
		boolean dismountTarget = false;
		if (isSpear) {
			Entity attackerRoot = attacker.getVehicle() != null ? attacker.getVehicle() : attacker;
			Entity targetRoot = target.getVehicle() != null ? target.getVehicle() : target;
			Vec v = attackerRoot.getVelocity();
			if (v.isZero()) {
				Vec disp = attackerRoot.getPosition().asVec().sub(attackerRoot.getPreviousPosition().asVec());
				if (disp.lengthSquared() < 100) v = disp;
			}
			Vec vAttacker = v.mul(20.0);

			Vec tv = targetRoot.getVelocity();
			if (tv.isZero()) {
				Vec disp = targetRoot.getPosition().asVec().sub(targetRoot.getPreviousPosition().asVec());
				if (disp.lengthSquared() < 100) tv = disp;
			}
			Vec vTarget = tv.mul(20.0);

			Vec vRel = vAttacker.sub(vTarget);
			Vec lookDir = attacker.getPosition().direction().normalize();
			double speed = vAttacker.dot(lookDir);
			double relativeSpeed = vRel.dot(lookDir);

			KineticWeapon kw = attacker.getItemInMainHand().get(DataComponents.KINETIC_WEAPON);
			float damageMultiplier;
			float minRelativeSpeed = 4.6f;
			float minKnockbackSpeed = 5.1f;
			float minDismountSpeed = 11.0f;

			if (kw != null) {
				damageMultiplier = kw.damageMultiplier();
				if (kw.damageConditions() != null) minRelativeSpeed = kw.damageConditions().minRelativeSpeed();
				if (kw.knockbackConditions() != null) minKnockbackSpeed = kw.knockbackConditions().minSpeed();
				if (kw.dismountConditions() != null) minDismountSpeed = kw.dismountConditions().minSpeed();
			} else {
				damageMultiplier = switch (attacker.getItemInMainHand().material().name().toLowerCase()) {
					case "wooden_spear" -> { minDismountSpeed = 14.0f; yield 0.7f; }
					case "golden_spear" -> { minDismountSpeed = 13.0f; yield 0.7f; }
					case "stone_spear" -> { minDismountSpeed = 13.0f; yield 0.82f; }
					case "copper_spear" -> { minDismountSpeed = 12.0f; yield 0.82f; }
					case "iron_spear" -> { minDismountSpeed = 11.0f; yield 0.95f; }
					case "diamond_spear" -> { minDismountSpeed = 10.0f; yield 1.075f; }
					case "netherite_spear" -> { minDismountSpeed = 9.0f; yield 1.2f; }
					default -> 1.0f;
				};
			}

			if (relativeSpeed >= minRelativeSpeed) {
				kineticDamage = (float) Math.floor(relativeSpeed * damageMultiplier);
				damage += kineticDamage;
			}

			if (speed >= minKnockbackSpeed) {
				knockback += Math.max(1, (int) Math.floor((speed - minKnockbackSpeed) / 2.0) + 1);
			}

			dismountTarget = speed >= minDismountSpeed && target.getVehicle() != null;
		}

		// Apply critical damage and knockback
		if (critical) damage = criticalFeature.applyToDamage(damage);
		damage += magicalDamage;

		if (sprintAttack) knockback++;

		return new AttackValues.Final(
			damage, strongAttack, sprintAttack, knockback, critical,
			magicalDamage > 0, fireAspect, sweeping,
			finalAttackEvent.hasAttackSounds(),
			finalAttackEvent.playSoundsOnFail(),
			maceSmash, isSpear, fallDistance,
			kineticDamage, dismountTarget
		);
	}

	@Override
	public boolean performLunge(LivingEntity attacker, int lunge) {
		if (lunge <= 0) return false;
		if (attacker.getVehicle() != null) return false;
		if (attacker instanceof Player p) {
			if (p.isFlying() || p.isFlyingWithElytra() || FluidUtil.isTouchingWater(p)) return false;
			if (p.getFood() < 7 && p.getGameMode() != GameMode.CREATIVE) return false;
			Long lastLunge = p.getTag(LAST_LUNGE_TICK);
			if (lastLunge != null && lastLunge == p.getAliveTicks()) {
				return false; // Already lunged this tick
			}
		}

		Vec lookDir = attacker.getPosition().direction();
		double yImpulse = lookDir.y();
		// If player is on ground, give a slight upward component (0.15) so ground friction does not halt forward dash
		if (attacker.isOnGround() && yImpulse < 0.15) {
			yImpulse = 0.15;
		}
		Vec lungeVec = new Vec(lookDir.x(), yImpulse, lookDir.z()).normalize()
				.mul(0.458 * lunge * ServerFlag.SERVER_TICKS_PER_SECOND);
		attacker.setVelocity(attacker.getVelocity().add(lungeVec));
		if (attacker instanceof CombatPlayer customAttacker) {
			customAttacker.sendImmediateVelocityUpdate();
		}

		fallFeature.resetFallDistance(attacker);

		SoundEvent sound = lunge >= 3 ? SoundEvent.ITEM_SPEAR_LUNGE_3 : (lunge == 2 ? SoundEvent.ITEM_SPEAR_LUNGE_2 : SoundEvent.ITEM_SPEAR_LUNGE_1);
		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(sound, Sound.Source.PLAYER, 1.0f, 1.0f), attacker);

		if (attacker instanceof Player player) {
			player.setTag(LAST_LUNGE_TICK, player.getAliveTicks());
			exhaustionFeature.addExhaustion(player, (float) (4.0 * lunge));
		}
		itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, 1);
		return true;
	}
}
