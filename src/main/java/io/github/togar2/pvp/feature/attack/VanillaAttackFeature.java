package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.entity.projectile.Deflectable;
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
import io.github.togar2.pvp.utils.ExplosionKnockback;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.KnownMovement;
import io.github.togar2.pvp.utils.TeamUtil;
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
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.player.PlayerStabEvent;
import net.minestom.server.event.player.PlayerTickEvent;
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

	/**
	 * Server side tolerance on top of the entity interaction range
	 * (vanilla: Player#isWithinEntityInteractionRange(aabb, 1.0)).
	 */
	private static final double ATTACK_RANGE_MARGIN = 1.0;

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
	private VanillaSpearAttacks spearAttacks;

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
		this.spearAttacks = new VanillaSpearAttacks(cooldownFeature, exhaustionFeature, itemDamageFeature,
				enchantmentFeature, knockbackFeature, fallFeature);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityAttackEvent.class, event -> {
			if (event.getEntity() instanceof Player player && player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
				Entity target = event.getTarget();
				// Vanilla ignores normal attacks with piercing weapons (spears), they jab using the stab action
				if (VanillaSpearAttacks.isPiercing(player.getItemInMainHand())) return;
				if (isWithinInteractionRange(player, target)) performAttack(player, target);
			}
		});
		
		// Spear jab (left click)
		node.addListener(PlayerStabEvent.class, event -> spearAttacks.jab(event.getPlayer()));
		
		// Spear charge (hold right click)
		node.addListener(PlayerBeginItemUseEvent.class, event -> {
			if (event.getHand() == PlayerHand.MAIN) spearAttacks.startKinetic(event.getPlayer(), event.getItemStack());
		});
		node.addListener(PlayerTickEvent.class, event -> spearAttacks.tickKinetic(event.getPlayer()));
		
		node.addListener(EntityTickEvent.class, event -> KnownMovement.update(event.getEntity()));
	}
	
	/**
	 * Vanilla range check: the distance between the eyes of the player and the closest point
	 * of the bounding box of the target has to be smaller than interaction range + margin.
	 */
	protected boolean isWithinInteractionRange(Player player, Entity target) {
		double range = player.getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + ATTACK_RANGE_MARGIN;
		Pos eye = player.getPosition().add(0, player.getEyeHeight(), 0);
		Pos pos = target.getPosition();
		var box = target.getBoundingBox();
		double halfWidth = box.width() / 2, halfDepth = box.depth() / 2;
		double dx = Math.max(Math.max(pos.x() - halfWidth - eye.x(), 0), eye.x() - (pos.x() + halfWidth));
		double dy = Math.max(Math.max(pos.y() - eye.y(), 0), eye.y() - (pos.y() + box.height()));
		double dz = Math.max(Math.max(pos.z() - halfDepth - eye.z(), 0), eye.z() - (pos.z() + halfDepth));
		return dx * dx + dy * dy + dz * dz < range * range;
	}
	
	@Override
	public boolean performAttack(LivingEntity attacker, Entity target) {
		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());

		// Redirectable projectiles (wind charges) are deflected by hitting them
		if (target instanceof Deflectable deflectable && deflectable.deflect(attacker)) {
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
				SoundEvent.ENTITY_PLAYER_ATTACK_NODAMAGE, Sound.Source.PLAYER, 1.0f, 1.0f), attacker);
			return false;
		}
		
		PrepareAttackEvent prepareAttackEvent = new PrepareAttackEvent(attacker, target);
		EventDispatcher.call(prepareAttackEvent);
		if (prepareAttackEvent.isCancelled()) return false;
		AttackValues.Final attack = prepareAttack(attacker, target);
		if (attack == null) return false; // Event cancelled

		RegistryKey<DamageType> damageType;
		if (attack.maceSmash()) {
			damageType = DamageType.MACE_SMASH;
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

			// Vanilla only damages the item and adds exhaustion when the target was actually hurt
			
			// The sprint knockback sound is played before the damage is dealt
			if (attack.sounds() && attack.sprint()) {
				ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, Sound.Source.PLAYER,
					1.0f, 1.0f
				), attacker);
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
			affectedEntities = sweepingFeature.applySweeping(attacker, living, attack.sweepDamage(), attack.cooldownProgress());
			affectedEntities.add(living);
		}

		if (target instanceof CombatPlayer custom)
			custom.sendImmediateVelocityUpdate();

		if (attack.maceSmash()) {
			// 1. Negate fall damage
			fallFeature.resetFallDistance(attacker);

			// 2. AoE Smash Knockback (vanilla: MaceItem#knockback)
			// The attacker also loses its vertical momentum (y velocity is set to 0.01 blocks/tick)
			attacker.setVelocity(attacker.getVelocity().withY(0.01 * ServerFlag.SERVER_TICKS_PER_SECOND));
			if (attacker instanceof CombatPlayer customAttacker) {
				customAttacker.sendImmediateVelocityUpdate();
			}
			if (target.getInstance() != null) {
				Pos targetPos = target.getPosition();
				double fallMultiplier = attack.fallDistance() > 5.0 ? 2.0 : 1.0;
				for (Entity nearby : target.getInstance().getNearbyEntities(targetPos, 3.5)) {
					if (nearby == target || nearby == attacker) continue;
					if (!(nearby instanceof LivingEntity livingNearby)) continue;
					if (TeamUtil.isAllied(attacker, nearby)) continue;
					if (nearby instanceof Player p && (p.getGameMode() == GameMode.SPECTATOR
							|| (p.getGameMode() == GameMode.CREATIVE && p.isFlying()))) continue;
					if (nearby.getEntityMeta() instanceof net.minestom.server.entity.metadata.other.ArmorStandMeta stand
							&& stand.isMarker()) continue;
					
					Pos nearbyPos = nearby.getPosition();
					if (targetPos.distanceSquared(nearbyPos) > 3.5 * 3.5) continue;
					
					// delta is normalized in 3D, only its horizontal components are used
					Vec delta = new Vec(nearbyPos.x() - targetPos.x(), nearbyPos.y() - targetPos.y(), nearbyPos.z() - targetPos.z());
					double power = (3.5 - delta.length()) * 0.7 * fallMultiplier
							* (1.0 - livingNearby.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE));
					if (power <= 0) continue;
					Vec push = delta.length() < 1.0E-5 ? Vec.ZERO : delta.normalize().mul(power);
					
					double tps = ServerFlag.SERVER_TICKS_PER_SECOND;
					livingNearby.setVelocity(livingNearby.getVelocity().add(push.x() * tps, 0.7 * tps, push.z() * tps));
					if (livingNearby instanceof CombatPlayer customNearby) {
						customNearby.sendImmediateVelocityUpdate();
					}
				}
			}

			// 3. Wind Burst (vanilla: explode effect of the enchantment, radius 3.5 around the attacker, no damage)
			int windBurst = enchantmentFeature.getWindBurst(attacker);
			boolean attackerFlying = attacker instanceof Player p && p.isFlying();
			if (windBurst > 0 && attack.fallDistance() >= 1.5 && !attackerFlying && attacker.getInstance() != null) {
				float multiplier = windBurst <= 3
						? new float[]{1.2f, 1.75f, 2.2f}[windBurst - 1]
						: 1.5f + 0.35f * (windBurst - 1);
				Pos burstPos = attacker.getPosition();
				ExplosionKnockback.apply(attacker.getInstance(), burstPos, 3.5f, multiplier, null, fallFeature, null);
				ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, Sound.Source.PLAYER,
					1.0f, 1.0f
				), attacker);
				attacker.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.GUST_EMITTER_LARGE, false, false,
					burstPos.x(), burstPos.y(), burstPos.z(),
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
			SoundEvent smashSound = !target.isOnGround() ? SoundEvent.ITEM_MACE_SMASH_AIR
					: attack.fallDistance() > 5.0 ? SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvent.ITEM_MACE_SMASH_GROUND;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(smashSound, Sound.Source.PLAYER, 1.0f, 1.0f), target);
		}

		// Play attack sounds
		if (attack.sounds()) {
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
		double knockback = enchantmentFeature.getKnockback(attacker) + attacker.getAttributeValue(Attribute.ATTACK_KNOCKBACK);
		int fireAspect = enchantmentFeature.getFireAspect(attacker);

		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		boolean isMace = tool != null && tool.isMace();
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
		// Vanilla bases the sweeping damage on the damage after the cooldown and the mace bonus,
		// but before critical hits and enchantment damage
		float sweepDamage = damage;

		// Apply critical damage and knockback
		if (critical) damage = criticalFeature.applyToDamage(damage);
		damage += magicalDamage;

		if (sprintAttack) knockback++;

		return new AttackValues.Final(
			damage, strongAttack, sprintAttack, knockback, critical,
			magicalDamage > 0, fireAspect, sweeping,
			finalAttackEvent.hasAttackSounds(),
			finalAttackEvent.playSoundsOnFail(),
			maceSmash, false, fallDistance,
			0.0f, false,
			sweepDamage, cooldownProgress
		);
	}

	@Override
	public boolean performLunge(LivingEntity attacker, int lunge) {
		return spearAttacks.performLunge(attacker, lunge);
	}
}
