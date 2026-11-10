package com.gildedledger.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToLongFunction;

/**
 * Everything tracked for one source: a monster, a farming crop, a skilling action
 * or a processing product. Loot is stored as item quantities and valued at
 * current prices; supplies are stored with the gp they cost when used.
 */
public class SourceRecord
{
	public String key;
	public String name;
	public SourceCategory category;
	/** Kills, harvests, logs cut and so on, depending on the category. */
	public long count;
	public Map<Integer, Long> loot = new LinkedHashMap<>();
	public Map<String, SupplyLine> supplies = new LinkedHashMap<>();
	public Map<String, Long> xp = new LinkedHashMap<>();
	/** Loot items the player chose not to count towards profit (right-click, Exclude). */
	public Set<Integer> excludedLoot = new LinkedHashSet<>();
	/** Supply lines (by key) the player chose not to count towards profit. */
	public Set<String> excludedSupplies = new LinkedHashSet<>();
	public long firstSeenMillis;
	public long lastUpdatedMillis;

	public SourceRecord()
	{
	}

	public SourceRecord(String key, String name, SourceCategory category, long nowMillis)
	{
		this.key = key;
		this.name = name;
		this.category = category;
		this.firstSeenMillis = nowMillis;
		this.lastUpdatedMillis = nowMillis;
	}

	/** A separate copy of everything in this record. */
	public SourceRecord copy()
	{
		SourceRecord c = new SourceRecord(key, name, category, firstSeenMillis);
		c.lastUpdatedMillis = lastUpdatedMillis;
		c.count = count;
		c.loot.putAll(loot);
		supplies.forEach((k, line) ->
		{
			SupplyLine l = new SupplyLine(line.itemId, line.name, line.unit);
			l.quantity = line.quantity;
			l.gp = line.gp;
			c.supplies.put(k, l);
		});
		c.xp.putAll(xp);
		c.excludedLoot.addAll(excludedLoot);
		c.excludedSupplies.addAll(excludedSupplies);
		return c;
	}

	/**
	 * What this record gained since {@code start} (a copy of it taken earlier), or the
	 * whole record if it didn't exist then. Nothing goes below zero, so a reset since
	 * {@code start} just shows what came after it.
	 */
	public SourceRecord since(SourceRecord start)
	{
		SourceRecord d = new SourceRecord(key, name, category, firstSeenMillis);
		d.lastUpdatedMillis = lastUpdatedMillis;
		d.excludedLoot.addAll(excludedLoot);
		d.excludedSupplies.addAll(excludedSupplies);
		if (start == null)
		{
			SourceRecord all = copy();
			return all;
		}
		d.count = Math.max(0, count - start.count);
		loot.forEach((id, qty) ->
		{
			long gained = qty - start.loot.getOrDefault(id, 0L);
			if (gained > 0)
			{
				d.loot.put(id, gained);
			}
		});
		supplies.forEach((k, line) ->
		{
			SupplyLine was = start.supplies.get(k);
			long qty = line.quantity - (was == null ? 0 : was.quantity);
			if (qty > 0)
			{
				SupplyLine l = new SupplyLine(line.itemId, line.name, line.unit);
				l.quantity = qty;
				l.gp = Math.max(0, line.gp - (was == null ? 0 : was.gp));
				d.supplies.put(k, l);
			}
		});
		xp.forEach((skill, amount) ->
		{
			long gained = amount - start.xp.getOrDefault(skill, 0L);
			if (gained > 0)
			{
				d.xp.put(skill, gained);
			}
		});
		return d;
	}

	public void addLoot(int itemId, long quantity, long nowMillis)
	{
		if (itemId <= 0 || quantity <= 0)
		{
			return;
		}
		loot.merge(itemId, quantity, Long::sum);
		lastUpdatedMillis = nowMillis;
	}

	/** Takes up to {@code quantity} of an item back out of the loot. Returns how many were removed. */
	public long removeLoot(int itemId, long quantity, long nowMillis)
	{
		Long have = loot.get(itemId);
		if (have == null || quantity <= 0)
		{
			return 0;
		}
		long removed = Math.min(have, quantity);
		if (have - removed <= 0)
		{
			loot.remove(itemId);
		}
		else
		{
			loot.put(itemId, have - removed);
		}
		lastUpdatedMillis = nowMillis;
		return removed;
	}

	public void addSupply(String lineKey, int itemId, String lineName, String unit, long quantity, long gp, long nowMillis)
	{
		if (quantity <= 0 || gp < 0)
		{
			return;
		}
		SupplyLine line = supplies.computeIfAbsent(lineKey, k -> new SupplyLine(itemId, lineName, unit));
		line.quantity += quantity;
		line.gp += gp;
		lastUpdatedMillis = nowMillis;
	}

	/** Takes back part of a supply line. Returns false if there isn't that much on it. */
	public boolean removeSupply(String lineKey, long quantity, long gp)
	{
		SupplyLine line = supplies.get(lineKey);
		if (line == null || line.quantity < quantity)
		{
			return false;
		}
		line.quantity -= quantity;
		line.gp = Math.max(0, line.gp - gp);
		if (line.quantity <= 0)
		{
			supplies.remove(lineKey);
			excludedSupplies.remove(lineKey);
		}
		return true;
	}

	/** Takes back some XP. Returns false if there isn't that much. */
	public boolean removeXp(String skill, long amount)
	{
		Long have = xp.get(skill);
		if (have == null || have < amount)
		{
			return false;
		}
		if (have == amount)
		{
			xp.remove(skill);
		}
		else
		{
			xp.put(skill, have - amount);
		}
		return true;
	}

	public void addXp(String skill, long amount, long nowMillis)
	{
		if (amount <= 0)
		{
			return;
		}
		xp.merge(skill, amount, Long::sum);
		lastUpdatedMillis = nowMillis;
	}

	public void incrementCount(long nowMillis)
	{
		count++;
		lastUpdatedMillis = nowMillis;
	}

	/** Folds another record for the same source into this one. */
	public void mergeFrom(SourceRecord other)
	{
		count += other.count;
		other.loot.forEach((id, qty) -> loot.merge(id, qty, Long::sum));
		other.xp.forEach((skill, amount) -> xp.merge(skill, amount, Long::sum));
		other.supplies.forEach((key, line) ->
		{
			SupplyLine mine = supplies.get(key);
			if (mine == null)
			{
				supplies.put(key, line);
			}
			else
			{
				mine.quantity += line.quantity;
				mine.gp += line.gp;
			}
		});
		excludedLoot.addAll(other.excludedLoot);
		excludedSupplies.addAll(other.excludedSupplies);
		firstSeenMillis = Math.min(firstSeenMillis, other.firstSeenMillis);
		lastUpdatedMillis = Math.max(lastUpdatedMillis, other.lastUpdatedMillis);
	}

	public long lootValue(IntToLongFunction unitPrice)
	{
		long total = 0;
		for (Map.Entry<Integer, Long> entry : loot.entrySet())
		{
			if (!excludedLoot.contains(entry.getKey()))
			{
				total += unitPrice.applyAsLong(entry.getKey()) * entry.getValue();
			}
		}
		return total;
	}

	public long supplyCost()
	{
		long total = 0;
		for (Map.Entry<String, SupplyLine> entry : supplies.entrySet())
		{
			if (!excludedSupplies.contains(entry.getKey()))
			{
				total += entry.getValue().gp;
			}
		}
		return total;
	}

	/** Right-click "Exclude from profit" / "Include in profit" on a loot item. */
	public void toggleLootExcluded(int itemId)
	{
		if (!excludedLoot.remove(itemId))
		{
			excludedLoot.add(itemId);
		}
	}

	/** Right-click "Exclude from profit" / "Include in profit" on a supply. */
	public void toggleSupplyExcluded(String lineKey)
	{
		if (!excludedSupplies.remove(lineKey))
		{
			excludedSupplies.add(lineKey);
		}
	}

	public long totalXp()
	{
		long total = 0;
		for (long amount : xp.values())
		{
			total += amount;
		}
		return total;
	}

	public boolean isEmpty()
	{
		return count == 0 && loot.isEmpty() && supplies.isEmpty() && xp.isEmpty();
	}
}
