package com.gildedledger.tracking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Item-count changes between two snapshots of a container. */
public final class InventoryDiff
{
	private final Map<Integer, Integer> changes;

	private InventoryDiff(Map<Integer, Integer> changes)
	{
		this.changes = changes;
	}

	public static InventoryDiff between(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Map<Integer, Integer> changes = new HashMap<>();
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		for (int id : ids)
		{
			int delta = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (delta != 0)
			{
				changes.put(id, delta);
			}
		}
		return new InventoryDiff(changes);
	}

	/** A diff from changes already worked out (id → signed change). */
	public static InventoryDiff of(Map<Integer, Integer> changes)
	{
		Map<Integer, Integer> copy = new HashMap<>(changes);
		copy.values().removeIf(v -> v == 0);
		return new InventoryDiff(copy);
	}

	/** Builds an id → total quantity map from parallel id/quantity arrays. */
	public static Map<Integer, Integer> counts(int[] ids, int[] quantities)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (int i = 0; i < ids.length; i++)
		{
			if (ids[i] > 0 && quantities[i] > 0)
			{
				counts.merge(ids[i], quantities[i], Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * Cancels out items that only changed between noted and unnoted form, such as
	 * noting herbs at a tool leprechaun or a bank. Those are never loot or costs.
	 */
	public InventoryDiff withoutNoteConversions(ItemInfo items)
	{
		Map<Integer, Integer> result = new HashMap<>(changes);
		Map<Integer, Map<Integer, Integer>> byBase = new HashMap<>();
		for (Map.Entry<Integer, Integer> change : changes.entrySet())
		{
			byBase.computeIfAbsent(items.canonical(change.getKey()), k -> new HashMap<>())
				.put(change.getKey(), change.getValue());
		}
		for (Map<Integer, Integer> forms : byBase.values())
		{
			if (forms.size() < 2)
			{
				continue;
			}
			for (Map.Entry<Integer, Integer> lost : forms.entrySet())
			{
				for (Map.Entry<Integer, Integer> gained : forms.entrySet())
				{
					int out = -result.getOrDefault(lost.getKey(), 0);
					int in = result.getOrDefault(gained.getKey(), 0);
					if (out <= 0 || in <= 0)
					{
						continue;
					}
					int converted = Math.min(out, in);
					result.merge(lost.getKey(), converted, Integer::sum);
					result.merge(gained.getKey(), -converted, Integer::sum);
				}
			}
		}
		result.values().removeIf(v -> v == 0);
		return new InventoryDiff(result);
	}

	/**
	 * Removes changes that something else has already explained, such as a
	 * dropped item or a potion dose that was drunk. Each amount has the same sign
	 * as the change it explains; a change never flips sign.
	 */
	public InventoryDiff without(Map<Integer, Integer> explained)
	{
		Map<Integer, Integer> result = new HashMap<>(changes);
		explained.forEach((id, amount) ->
		{
			int current = result.getOrDefault(id, 0);
			if (current == 0 || amount == 0 || Integer.signum(current) != Integer.signum(amount))
			{
				return;
			}
			int left = Math.abs(amount) >= Math.abs(current) ? 0 : current - amount;
			if (left == 0)
			{
				result.remove(id);
			}
			else
			{
				result.put(id, left);
			}
		});
		return new InventoryDiff(result);
	}

	/** True when nothing was gained, e.g. a pure payment. */
	public boolean hasNoGains()
	{
		for (int delta : changes.values())
		{
			if (delta > 0)
			{
				return false;
			}
		}
		return true;
	}

	public Map<Integer, Integer> changes()
	{
		return changes;
	}

	public int change(int itemId)
	{
		return changes.getOrDefault(itemId, 0);
	}

	public boolean isEmpty()
	{
		return changes.isEmpty();
	}
}
