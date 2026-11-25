package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.item.ThrownEnderPearlMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class ThrownEnderpearl extends CustomEntityProjectile implements ItemHoldingProjectile {
	private Pos prevPos = Pos.ZERO;
	
	private final FallFeature fallFeature;
	
	public ThrownEnderpearl(@Nullable Entity shooter, FallFeature fallFeature) {
		super(shooter, EntityType.ENDER_PEARL);
		this.fallFeature = fallFeature;
	}
	
	private boolean teleported = false;
	
	public void teleportOwner(@Nullable Pos targetPosition) {
		if (teleported) return;
		teleported = true;
		
		Pos position = (targetPosition != null && !targetPosition.isZero()) ? targetPosition
				: ((prevPos != null && !prevPos.isZero()) ? prevPos : getPosition());
		ThreadLocalRandom random = ThreadLocalRandom.current();
		
		for (int i = 0; i < 32; i++) {
			sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.PORTAL, false, false,
					position.x(), position.y() + random.nextDouble() * 2, position.z(),
					(float) random.nextGaussian(), 0.0F, (float) random.nextGaussian(),
					0, 1
			));
		}
		
		Entity shooter = getShooter();
		if (shooter != null) {
			Pos shooterPos = shooter.getPosition();
			position = position.withPitch(shooterPos.pitch()).withYaw(shooterPos.yaw());
		}
		
		if (shooter instanceof Player player) {
			if (player.isOnline() && player.getInstance() == getInstance()
					&& player.getPlayerMeta().getBedInWhichSleepingPosition() == null) {
				if (player.getVehicle() != null) {
					player.getVehicle().removePassenger(player);
				}
				
				player.teleport(position);
				fallFeature.resetFallDistance(player);
				
				ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
						SoundEvent.ENTITY_PLAYER_TELEPORT, Sound.Source.PLAYER, 1.0f, 1.0f
				), position.x(), position.y(), position.z());
				
				if (player instanceof CombatPlayer combatPlayer) {
					combatPlayer.sendImmediateVelocityUpdate();
				}
				
				player.damage(DamageType.FALL, 5.0F);
			}
		} else if (shooter != null) {
			shooter.teleport(position);
		}
	}
	
	@Override
	public boolean onHit(Entity entity) {
		if (entity instanceof LivingEntity living) {
			living.damage(new Damage(DamageType.THROWN, this, getShooter(), null, 0));
		}
		
		teleportOwner(getPosition());
		return true;
	}
	
	@Override
	public boolean hasEntityCollision() {
		return true;
	}

	@Override
	public boolean onStuck() {
		teleportOwner(prevPos != null && !prevPos.isZero() ? prevPos : getPosition());
		return true;
	}
	
	@Override
	protected boolean canHit(Entity entity) {
		if (entity instanceof WindCharge) return true;
		return super.canHit(entity);
	}
	
	@Override
	public void tick(long time) {
		Entity shooter = getShooter();
		if (shooter instanceof Player && ((Player) shooter).isDead()) {
			remove();
			return;
		}

		Pos startPos = prevPos != null && !prevPos.isZero() ? prevPos : getPosition();
		prevPos = getPosition();
		super.tick(time);

		if (!isRemoved() && !teleported) {
			Pos endPos = getPosition();
			checkWindChargeInterception(startPos, endPos);
		}
	}

	private void checkWindChargeInterception(Pos start, Pos end) {
		if (teleported || isRemoved()) return;
		Instance instance = getInstance();
		if (instance == null) return;

		Vec moveVec = end.asVec().sub(start.asVec());
		double searchRadius = Math.max(3.5, moveVec.length() + 2.0);
		for (Entity entity : instance.getNearbyEntities(end, searchRadius)) {
			if (entity instanceof WindCharge windCharge && !windCharge.isRemoved()) {
				Pos chargePos = windCharge.getPosition();
				double dist = WindCharge.distanceToSegment(chargePos.asVec(), start.asVec(), end.asVec());
				if (dist <= 1.5) {
					Pos catchPos = new Pos(end.x(), end.y(), end.z());
					windCharge.explode(catchPos, this);
					scheduler().scheduleNextProcess(this::remove);
					return;
				}
			}
		}
	}
	
	@Override
	public void setItem(@NotNull ItemStack item) {
		((ThrownEnderPearlMeta) getEntityMeta()).setItem(item);
	}
}
