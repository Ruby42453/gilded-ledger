package com.gildedledger.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Remembers which items on the floor came from which monster, and decides when
 * one of them has really been picked up by the player.
 *
 * <p>A drop only becomes loot when all three happen: the player clicks "Take" on
 * that item on that tile, the item then leaves the tile, and the player is
 * standing on or next to the tile when it does. Items that despawn, are picked
 * up by someone else, or vanish because the player walked away never count.
 */
public class GroundDropLedger
{
	/** Spawns this old can no longer be matched to a monster's death. */
	static final int SPAWN_MATCH_TICKS = 2;
	/** A Take click stays valid long enough to walk to an item across a boss room. */
	static final int TAKE_WINDOW_TICKS = 30;
	/** Floor items despawn for everyone after a few minutes; forget them after 5. */
	static final int DROP_LIFETIME_TICKS = 500;
	static final int PICKUP_REACH = 1;
	static final int FAR_LOOT_DISTANCE = 16;

	public static final class Pickup
	{
		public final String sourceKey;
		public final boolean taskTarget;
		public final int itemId;
		public final int quantity;

		Pickup(String sourceKey, boolean taskTarget, int itemId, int quantity)
		{
			this.sourceKey = sourceKey;
			this.taskTarget = taskTarget;
			this.itemId = itemId;
			this.quantity = quantity;
		}
	}

	private static final class Spawn
	{
		final TileKey tile;
		final int itemId;
		int quantity;
		final int tick;

		Spawn(TileKey tile, int itemId, int quantity, int tick)
		{
			this.tile = tile;
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
		}
	}

	private static final class Drop
	{
		final String sourceKey;
		final boolean taskTarget;
		final int itemId;
		int quantity;
		final int tick;

		Drop(String sourceKey, boolean taskTarget, int itemId, int quantity, int tick)
		{
			this.sourceKey = sourceKey;
			this.taskTarget = taskTarget;
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
		}
	}

	private static final class Take
	{
		final TileKey tile;
		final int itemId;
		final int tick;

		Take(TileKey tile, int itemId, int tick)
		{
			this.tile = tile;
			this.itemId = itemId;
			this.tick = tick;
		}
	}

	private final List<Spawn> unclaimedSpawns = new ArrayList<>();
	private final Map<TileKey, List<Drop>> drops = new HashMap<>();
	private final List<Take> takes = new ArrayList<>();

	/** An item appeared on the floor (or an existing pile grew). */
	public void recordSpawn(TileKey tile, int itemId, int quantity, int tick)
	{
		if (tile == null || itemId <= 0 || quantity <= 0)
		{
			return;
		}
		unclaimedSpawns.add(new Spawn(tile, itemId, quantity, tick));
	}

	/**
	 * A monster's loot was reported. Claims matching floor spawns from the last
	 * couple of ticks around the monster and attributes them to the source.
	 *
	 * @param stacks pairs of {itemId, quantity}
	 */
	public void tagNpcLoot(String sourceKey, boolean taskTarget, TileKey npcTile, int npcSize, List<int[]> stacks, int tick)
	{
		if (sourceKey == null || npcTile == null || stacks == null)
		{
			return;
		}
		int size = Math.max(1, npcSize);
		for (int[] stack : stacks)
		{
			// First under or beside the monster; then anywhere close, for bosses
			// such as Kraken and Zulrah whose loot appears by the player instead.
			int remaining = claim(sourceKey, taskTarget, stack[0], stack[1], tick, spawn -> isUnderOrBeside(spawn, npcTile, size));
			claim(sourceKey, taskTarget, stack[0], remaining, tick, spawn -> spawn.distanceTo(npcTile) <= FAR_LOOT_DISTANCE);
		}
		unclaimedSpawns.removeIf(s -> s.quantity <= 0);
	}

	private int claim(String sourceKey, boolean taskTarget, int itemId, int quantity, int tick,
		java.util.function.Predicate<TileKey> where)
	{
		int remaining = quantity;
		for (Spawn spawn : unclaimedSpawns)
		{
			if (remaining <= 0)
			{
				break;
			}
			if (spawn.itemId != itemId || spawn.quantity <= 0
				|| tick - spawn.tick > SPAWN_MATCH_TICKS
				|| !where.test(spawn.tile))
			{
				continue;
			}
			int claimed = Math.min(remaining, spawn.quantity);
			spawn.quantity -= claimed;
			remaining -= claimed;
			drops.computeIfAbsent(spawn.tile, t -> new ArrayList<>())
				.add(new Drop(sourceKey, taskTarget, itemId, claimed, tick));
		}
		return remaining;
	}

	/**
	 * The player dropped tracked loot at their feet. It stays attributed to its
	 * source, so taking it back puts it back where it came from.
	 */
	public void recordPlayerDrop(String sourceKey, int itemId, int quantity, TileKey tile, int tick)
	{
		if (sourceKey == null || tile == null || itemId <= 0 || quantity <= 0)
		{
			return;
		}
		drops.computeIfAbsent(tile, t -> new ArrayList<>()).add(new Drop(sourceKey, false, itemId, quantity, tick));
	}

	/** The player chose "Take" on a floor item. */
	public void recordTake(TileKey tile, int itemId, int tick)
	{
		if (tile != null && itemId > 0)
		{
			takes.add(new Take(tile, itemId, tick));
		}
	}

	/**
	 * Some quantity of an item left a tile. Returns what counts as picked-up loot,
	 * or an empty list when the item was not a tracked drop or not taken by us.
	 */
	public List<Pickup> recordRemoval(TileKey tile, int itemId, int quantity, int tick, TileKey playerTile)
	{
		List<Pickup> result = new ArrayList<>();
		List<Drop> onTile = drops.get(tile);
		if (onTile == null || quantity <= 0)
		{
			return result;
		}

		boolean takenByPlayer = consumeTake(tile, itemId, tick)
			&& playerTile != null && tile.distanceTo(playerTile) <= PICKUP_REACH;

		int remaining = quantity;
		Iterator<Drop> it = onTile.iterator();
		while (it.hasNext() && remaining > 0)
		{
			Drop drop = it.next();
			if (drop.itemId != itemId)
			{
				continue;
			}
			int counted = Math.min(remaining, drop.quantity);
			remaining -= counted;
			drop.quantity -= counted;
			if (takenByPlayer)
			{
				result.add(new Pickup(drop.sourceKey, drop.taskTarget, itemId, counted));
			}
			if (drop.quantity <= 0)
			{
				it.remove();
			}
		}
		if (onTile.isEmpty())
		{
			drops.remove(tile);
		}
		return result;
	}

	public void expire(int tick)
	{
		unclaimedSpawns.removeIf(s -> tick - s.tick > SPAWN_MATCH_TICKS);
		takes.removeIf(t -> tick - t.tick > TAKE_WINDOW_TICKS);
		drops.values().forEach(list -> list.removeIf(d -> tick - d.tick > DROP_LIFETIME_TICKS));
		drops.values().removeIf(List::isEmpty);
	}

	public void clear()
	{
		unclaimedSpawns.clear();
		drops.clear();
		takes.clear();
	}

	int trackedDropCount()
	{
		int count = 0;
		for (List<Drop> list : drops.values())
		{
			count += list.size();
		}
		return count;
	}

	private boolean consumeTake(TileKey tile, int itemId, int tick)
	{
		Iterator<Take> it = takes.iterator();
		while (it.hasNext())
		{
			Take take = it.next();
			if (tick - take.tick > TAKE_WINDOW_TICKS)
			{
				it.remove();
				continue;
			}
			if (take.itemId == itemId && take.tile.equals(tile))
			{
				it.remove();
				return true;
			}
		}
		return false;
	}

	/** Loot lands under a monster's footprint or one tile around it. */
	private static boolean isUnderOrBeside(TileKey spawn, TileKey npcSouthWest, int size)
	{
		return spawn.plane == npcSouthWest.plane
			&& spawn.x >= npcSouthWest.x - 1 && spawn.x <= npcSouthWest.x + size
			&& spawn.y >= npcSouthWest.y - 1 && spawn.y <= npcSouthWest.y + size;
	}
}
