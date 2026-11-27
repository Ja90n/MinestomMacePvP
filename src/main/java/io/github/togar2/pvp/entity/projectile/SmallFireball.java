package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla {@code SmallFireball} (blaze fireball, dispenser): sets the target on fire and deals 5 damage,
 * or places fire when it hits a block.
 */
public class SmallFireball extends HurtingProjectile {
	public static final float DAMAGE = 5.0f;
	public static final float FIRE_SECONDS = 5.0f;
	
	public SmallFireball(@Nullable Entity shooter) {
		super(shooter, EntityType.SMALL_FIREBALL);
	}
	
	@Override
	public boolean onHit(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return true;
		int remainingFireTicks = living.getFireTicks();
		living.setFireTicks((int) (FIRE_SECONDS * ServerFlag.SERVER_TICKS_PER_SECOND));
		if (!living.damage(new Damage(DamageType.FIREBALL, this, getShooter(), null, DAMAGE))) {
			living.setFireTicks(remainingFireTicks);
		}
		return true;
	}
	
	@Override
	public boolean onStuck() {
		Instance instance = getInstance();
		if (instance != null && collisionDirection != null) {
			// The block the fireball is in front of: one step back from the surface it hit
			Pos pos = getPosition().sub(collisionDirection.mul(0.1));
			Block current = instance.getBlock(pos);
			Block below = instance.getBlock(pos.sub(0, 1, 0));
			if (current.air() && below.registry().isSolid()) {
				boolean soul = below.compare(Block.SOUL_SAND) || below.compare(Block.SOUL_SOIL);
				instance.setBlock(pos, soul ? Block.SOUL_FIRE : Block.FIRE);
			}
		}
		return true;
	}
}
