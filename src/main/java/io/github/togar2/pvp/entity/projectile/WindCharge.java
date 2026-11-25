package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.ServerFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a thrown Wind Charge projectile (Minecraft 1.21).
 */
public class WindCharge extends CustomEntityProjectile {
	public static final double EXPLOSION_RADIUS = 3.5;
	public static final double LAUNCH_UPWARD_POWER = 1.3 * ServerFlag.SERVER_TICKS_PER_SECOND; // ~26 blocks/s
	
	private final FallFeature fallFeature;
	private boolean exploded = false;
	
	public WindCharge(@Nullable Entity shooter, @Nullable FallFeature fallFeature) {
		super(shooter, EntityType.WIND_CHARGE);
		this.fallFeature = fallFeature;
		setNoGravity(true);
		setAerodynamics(getAerodynamics().withGravity(0.0).withHorizontalAirResistance(1.0).withVerticalAirResistance(1.0));
	}
	
	@Override
	public boolean hasEntityCollision() {
		return true;
	}

	private Pos prevPos = null;

	@Override
	public void tick(long time) {
		if (isRemoved() || exploded) return;
		Pos startPos = prevPos != null ? prevPos : getPosition();
		prevPos = getPosition();

		super.tick(time);

		if (!isRemoved() && !exploded) {
			Pos endPos = getPosition();
			checkPearlInterception(startPos, endPos);
		}
	}

	private void checkPearlInterception(Pos start, Pos end) {
		if (exploded || isRemoved()) return;
		Instance instance = getInstance();
		if (instance == null) return;

		Vec moveVec = end.asVec().sub(start.asVec());
		double searchRadius = Math.max(EXPLOSION_RADIUS, moveVec.length() + 2.0);
		for (Entity entity : instance.getNearbyEntities(end, searchRadius)) {
			if (entity instanceof ThrownEnderpearl pearl && !pearl.isRemoved()) {
				// Don't intercept if the pearl just spawned right inside the player in the same tick
				if (pearl.getAliveTicks() < 1 && pearl.getPosition().distanceSquared(start) < 1.0) continue;

				Pos pearlPos = pearl.getPosition();
				double dist = distanceToSegment(pearlPos.asVec(), start.asVec(), end.asVec());
				if (dist <= 1.5) { // generous hit tolerance for mid-air pearl-catching
					Pos catchPos = new Pos(pearlPos.x(), pearlPos.y(), pearlPos.z());
					explode(catchPos, pearl);
					scheduler().scheduleNextProcess(this::remove);
					return;
				}
			}
		}
	}

	public static double distanceToSegment(Vec p, Vec a, Vec b) {
		Vec ab = b.sub(a);
		Vec ap = p.sub(a);
		double lenSq = ab.lengthSquared();
		if (lenSq < 1e-6) {
			return ap.length();
		}
		double t = ap.dot(ab) / lenSq;
		t = Math.clamp(t, 0.0, 1.0);
		Vec projection = a.add(ab.mul(t));
		return p.sub(projection).length();
	}

	@Override
	public boolean onHit(Entity entity) {
		explode(getPosition(), entity);
		return true;
	}
	
	@Override
	public boolean onStuck() {
		explode(getPosition(), null);
		return true;
	}
	
	public void explode(Pos explosionPos, @Nullable Entity directHitEntity) {
		if (exploded) return;
		exploded = true;
		
		Instance instance = getInstance();
		if (instance == null) return;
		
		// Instant Pearl Catch: trigger any in-flight Ender Pearl directly hit or caught in the blast radius FIRST
		// so the player teleports to the explosion site BEFORE radial forces are applied!
		Pos pearlTeleportPos = new Pos(explosionPos.x(), explosionPos.y(), explosionPos.z());
		if (directHitEntity instanceof ThrownEnderpearl directPearl && !directPearl.isRemoved()) {
			directPearl.teleportOwner(pearlTeleportPos);
			directPearl.remove();
		}
		for (Entity entity : instance.getNearbyEntities(explosionPos, EXPLOSION_RADIUS)) {
			if (entity instanceof ThrownEnderpearl enderpearl && !enderpearl.isRemoved()) {
				enderpearl.teleportOwner(pearlTeleportPos);
				enderpearl.remove();
			}
		}

		// Play sound
		ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST,
				Sound.Source.BLOCK,
				1.0f, 1.0f
		), explosionPos.x(), explosionPos.y(), explosionPos.z());
		
		// Spawn gust particles
		sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_SMALL,
				explosionPos.x(), explosionPos.y(), explosionPos.z(),
				0.2f, 0.2f, 0.2f,
				0.0f, 1
		));
		
		// Direct impact damage
		if (directHitEntity instanceof LivingEntity living && living != getShooter()) {
			living.damage(new Damage(DamageType.WIND_CHARGE, this, getShooter(), explosionPos, 1.0f));
		}
		
		// Radial Wind Burst effect: launch shooter upwards (Wind Charge jump) and knockback nearby entities
		for (Entity entity : instance.getNearbyEntities(explosionPos, EXPLOSION_RADIUS)) {
			if (!(entity instanceof LivingEntity living)) continue;
			
			Point entityPos = living.getPosition().add(0, living.getEyeHeight() / 2.0, 0);
			Vec diff = entityPos.sub(explosionPos).asVec();
			double dist = diff.length();
			if (dist > EXPLOSION_RADIUS) continue;
			
			double factor = Math.max(0.1, 1.0 - (dist / EXPLOSION_RADIUS));
			
			if (living == getShooter()) {
				// Wind Charge Jump: launch upward and cancel fall damage
				Vec currentVel = living.getVelocity();
				double upwardVel = LAUNCH_UPWARD_POWER * Math.max(factor, 0.75);
				living.setVelocity(new Vec(currentVel.x(), upwardVel, currentVel.z()));
				
				if (fallFeature != null) {
					fallFeature.resetFallDistance(living);
				}
				if (living instanceof CombatPlayer custom) {
					custom.sendImmediateVelocityUpdate();
				}
			} else {
				// Knock back target away from blast center (accounting for knockback resistance)
				Vec dir = dist > 0.001 ? diff.normalize() : new Vec(0, 1, 0);
				double kbRes = living.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
				double horizSpeed = factor * 0.9 * Math.max(0.0, 1.0 - kbRes) * ServerFlag.SERVER_TICKS_PER_SECOND;
				double vertSpeed = Math.max(0.35, dir.y()) * factor * 0.7 * ServerFlag.SERVER_TICKS_PER_SECOND;
				
				Vec currentVel = living.getVelocity();
				living.setVelocity(currentVel.add(dir.x() * horizSpeed, vertSpeed, dir.z() * horizSpeed));
				if (living instanceof CombatPlayer custom) {
					custom.sendImmediateVelocityUpdate();
				}
			}
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		if (entity instanceof ThrownEnderpearl) return true;
		return super.canHit(entity);
	}
}
