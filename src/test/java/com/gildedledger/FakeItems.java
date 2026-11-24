package com.gildedledger;

import com.gildedledger.tracking.ItemInfo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory item table for tests. */
public class FakeItems implements ItemInfo
{
	private final Map<Integer, String> names = new HashMap<>();
	private final Map<Integer, Long> prices = new HashMap<>();
	private final Set<Integer> stackable = new HashSet<>();
	private final Map<Integer, Integer> noted = new HashMap<>();
	private final java.util.Set<Integer> untradeable = new java.util.HashSet<>();

	public FakeItems untradeable(int id, String name)
	{
		names.put(id, name);
		untradeable.add(id);
		return this;
	}

	@Override
	public boolean tradeable(int itemId)
	{
		return !untradeable.contains(itemId);
	}

	public FakeItems add(int id, String name, long price)
	{
		names.put(id, name);
		prices.put(id, price);
		return this;
	}

	public FakeItems stackable(int id, String name, long price)
	{
		add(id, name, price);
		stackable.add(id);
		return this;
	}

	public FakeItems noted(int notedId, int baseId)
	{
		noted.put(notedId, baseId);
		stackable.add(notedId);
		names.put(notedId, names.get(baseId));
		return this;
	}

	@Override
	public int idByName(String name)
	{
		for (Map.Entry<Integer, String> e : names.entrySet())
		{
			if (e.getValue().equalsIgnoreCase(name) && !noted.containsKey(e.getKey()))
			{
				return e.getKey();
			}
		}
		return -1;
	}

	@Override
	public String name(int itemId)
	{
		return names.getOrDefault(itemId, "");
	}

	@Override
	public long marketPrice(int itemId)
	{
		return prices.getOrDefault(canonical(itemId), 0L);
	}

	@Override
	public boolean stackable(int itemId)
	{
		return stackable.contains(itemId);
	}

	@Override
	public int canonical(int itemId)
	{
		return noted.getOrDefault(itemId, itemId);
	}
}
