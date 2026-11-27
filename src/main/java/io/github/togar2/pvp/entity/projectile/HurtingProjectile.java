package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla {@code AbstractHurtingProjectile}: a projectile without gravity which accelerates
 * in its direction of movement every tick and is slowed down by inertia.
 */
public abstract class HurtingProjectile extends CustomEntityProjectile implements Deflectable {
	public static final double INITIAL_ACCELERATION_POWER = 0.1;
	
	protected double accelerationPower = INITIAL_ACCELERATION_POWER;
	
	protected HurtingProjectile(@Nullable Entity shooter, @NotNull EntityType entityType) {
		super(shooter, entityType);
		setNoGravity(true);
		// Inertia is applied manually in tick()
		setAerodynamics(getAerodynamics().withGravity(0.0).withHorizontalAirResistance(1.0).withVerticalAirResistance(1.0));
	}
	
	protected float getInertia() {
		return 0.95f;
	}
	
	protected float getLiquidInertia() {
		return 0.8f;
	}
	
	/**
	 * Spawns the projectile moving in the given direction (vanilla: direction * accelerationPower).
	 */
	public void launch(Instance instance, Pos position, Vec direction) {
		setInstance(instance, position).join();
		setVelocity(direction.normalize().mul(accelerationPower * ServerFlag.SERVER_TICKS_PER_SECOND));
	}
	
	@Override
	public void tick(long time) {
		if (!isRemoved() && !isStuck() && getInstance() != null && !velocity.isZero()) {
			// Vanilla applyInertia: movement = (movement + normalize(movement) * acceleration) * inertia
			boolean inWater = getInstance().getBlock(getPosition()).compare(Block.WATER);
			Vec accelerated = velocity.add(velocity.normalize().mul(accelerationPower * ServerFlag.SERVER_TICKS_PER_SECOND));
			velocity = accelerated.mul(inWater ? getLiquidInertia() : getInertia());
		}
		super.tick(time);
	}
	
	@Override
	public boolean deflect(Entity deflector) {
		if (isRemoved()) return false;
		setShooter(deflector);
		accelerationPower = INITIAL_ACCELERATION_POWER;
		setVelocity(deflector.getPosition().direction().mul(ServerFlag.SERVER_TICKS_PER_SECOND));
		return true;
	}
	
	@Override
	protected boolean canHit(Entity entity) {
		return !(entity instanceof Deflectable) && super.canHit(entity);
	}
}
