package com.gildedledger.tracking;

import java.util.HashMap;
import java.util.Map;

/**
 * Recognizes items the player drops from their inventory, so the trackers can
 * take them back out of the loot instead of treating them as used up.
 */
public class DropTracker
{
	static final int DROP_WINDOW_TICKS = 3;

	private final Map<Integer, Integer> lastDropClick = new HashMap<>();

	public void onDropClicked(int itemId, int tick)
	{
		if (itemId > 0)
		{
			lastDropClick.put(itemId, tick);
		}
	}

	/**
	 * Returns the decreases in this change that are drops, as negative amounts
	 * keyed by item id, ready for {@link InventoryDiff#without}.
	 */
	public Map<Integer, Integer> dropsIn(InventoryDiff diff, int tick)
	{
		Map<Integer, Integer> drops = new HashMap<>();
		lastDropClick.values().removeIf(clickTick -> tick - clickTick > DROP_WINDOW_TICKS);
		for (Map.Entry<Integer, Integer> change : diff.changes().entrySet())
		{
			if (change.getValue() < 0 && lastDropClick.containsKey(change.getKey()))
			{
				drops.put(change.getKey(), change.getValue());
			}
		}
		return drops;
	}

	public void reset()
	{
		lastDropClick.clear();
	}
}
