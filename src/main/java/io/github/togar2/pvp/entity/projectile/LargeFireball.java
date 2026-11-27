package io.github.togar2.pvp.entity.projectile;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla {@code LargeFireball} (ghast fireball): hits for 6 damage and explodes on impact.
 */
public class LargeFireball extends HurtingProjectile {
	public static final float DAMAGE = 6.0f;
	
	private final int explosionPower;
	
	public LargeFireball(@Nullable Entity shooter, int explosionPower) {
		super(shooter, EntityType.FIREBALL);
		this.explosionPower = explosionPower;
	}
	
	public LargeFireball(@Nullable Entity shooter) {
		this(shooter, 1);
	}
	
	public int getExplosionPower() {
		return explosionPower;
	}
	
	@Override
	public boolean onHit(Entity entity) {
		if (entity instanceof LivingEntity living) {
			living.damage(new Damage(DamageType.FIREBALL, this, getShooter(), null, DAMAGE));
		}
		explode();
		return true;
	}
	
	@Override
	public boolean onStuck() {
		explode();
		return true;
	}
	
	protected void explode() {
		Instance instance = getInstance();
		if (instance == null || instance.getExplosionSupplier() == null) return;
		
		Pos pos = getPosition();
		CompoundBinaryTag.Builder data = CompoundBinaryTag.builder().putBoolean("fire", true);
		if (getShooter() != null) data.putString("causingEntity", getShooter().getUuid().toString());
		instance.explode((float) pos.x(), (float) pos.y(), (float) pos.z(), explosionPower, data.build());
	}
}
