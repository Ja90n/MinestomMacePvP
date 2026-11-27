package io.github.togar2.pvp.utils;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.tag.Tag;

/**
 * Tracks how far an entity moved during the last tick, equivalent to vanilla's {@code Entity#getKnownMovement}.
 * Minestom's velocity is not reliable for players (their movement is client authoritative) and the previous
 * position is not updated on ticks without a movement packet, so the position is sampled every tick instead.
 */
public final class KnownMovement {
	private static final Tag<Pos> LAST_POSITION = Tag.Transient("minestompvp:known_movement_pos");
	private static final Tag<Long> LAST_TICK = Tag.Transient("minestompvp:known_movement_tick");
	private static final Tag<Vec> MOVEMENT = Tag.Transient("minestompvp:known_movement");
	
	private KnownMovement() {}
	
	/**
	 * Samples the position of the entity, at most once per tick. Call this from an entity tick listener.
	 */
	public static void update(Entity entity) {
		long tick = entity.getAliveTicks();
		Long lastTick = entity.getTag(LAST_TICK);
		if (lastTick != null && lastTick == tick) return;
		
		Pos pos = entity.getPosition();
		Pos last = entity.getTag(LAST_POSITION);
		entity.setTag(MOVEMENT, last == null ? Vec.ZERO : new Vec(pos.x() - last.x(), pos.y() - last.y(), pos.z() - last.z()));
		entity.setTag(LAST_POSITION, pos);
		entity.setTag(LAST_TICK, tick);
	}
	
	/**
	 * Overrides the known movement of an entity for the current tick.
	 */
	public static void set(Entity entity, Vec movement) {
		entity.setTag(MOVEMENT, movement);
		entity.setTag(LAST_POSITION, entity.getPosition());
		entity.setTag(LAST_TICK, entity.getAliveTicks());
	}
	
	/**
	 * @return the movement of the entity during the last tick in blocks per tick
	 */
	public static Vec get(Entity entity) {
		Vec movement = entity.getTag(MOVEMENT);
		return movement == null ? Vec.ZERO : movement;
	}
}
