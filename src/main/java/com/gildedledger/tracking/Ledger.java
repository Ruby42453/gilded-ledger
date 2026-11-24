package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The single place trackers write to. Holds the loaded account's data and
 * remembers whether anything changed since the last save.
 */
public class Ledger
{
	private static final int RECENT_LOOT_LIMIT = 2000;

	private static final class LootEntry
	{
		final String sourceKey;
		final int itemId;
		long quantity;

		LootEntry(String sourceKey, int itemId, long quantity)
		{
			this.sourceKey = sourceKey;
			this.itemId = itemId;
			this.quantity = quantity;
		}
	}

	/** Where recent loot came from, newest last, so a drop can be undone from the right source. */
	private final Deque<LootEntry> recentLoot = new ArrayDeque<>();
	private final LongSupplier clock;
	private TrackerData data = new TrackerData();
	private boolean dirty;
	private long revision;

	public Ledger(LongSupplier clock)
	{
		this.clock = clock;
	}

	public TrackerData data()
	{
		return data;
	}

	public void load(TrackerData loaded)
	{
		data = loaded == null ? new TrackerData() : loaded.normalize();
		recentLoot.clear();
		dirty = false;
		revision++;
	}

	public long now()
	{
		return clock.getAsLong();
	}

	public SourceRecord source(String key, String name, SourceCategory category)
	{
		return data.source(key, name, category, now());
	}

	public void addLoot(String key, String name, SourceCategory category, int itemId, long quantity)
	{
		source(key, name, category).addLoot(itemId, quantity, now());
		recentLoot.addLast(new LootEntry(key, itemId, quantity));
		while (recentLoot.size() > RECENT_LOOT_LIMIT)
		{
			recentLoot.removeFirst();
		}
		changed();
	}

	/**
	 * The player dropped some of an item: take it back out of the loot of the
	 * source that most recently gave it. Only loot the tracker logged this session
	 * is touched, so dropping something picked up off the floor changes nothing.
	 *
	 * @return how much was removed from each source, keyed by source key
	 */
	public Map<String, Long> removeDroppedLoot(int itemId, long quantity)
	{
		Map<String, Long> removedFrom = new LinkedHashMap<>();
		long remaining = quantity;
		Iterator<LootEntry> it = recentLoot.descendingIterator();
		while (it.hasNext() && remaining > 0)
		{
			LootEntry entry = it.next();
			if (entry.itemId != itemId)
			{
				continue;
			}
			SourceRecord record = data.sources.get(entry.sourceKey);
			long removed = record == null ? 0 : record.removeLoot(itemId, Math.min(remaining, entry.quantity), now());
			remaining -= removed;
			entry.quantity -= removed;
			if (removed > 0)
			{
				removedFrom.merge(entry.sourceKey, removed, Long::sum);
			}
			if (entry.quantity <= 0 || removed == 0)
			{
				it.remove();
			}
		}
		if (!removedFrom.isEmpty())
		{
			changed();
		}
		return removedFrom;
	}

	/** Carried items went into or out of storage; recent loot can no longer be matched to drops. */
	public void forgetLootHistory()
	{
		recentLoot.clear();
	}

	public void addSupply(String key, String name, SourceCategory category,
		String lineKey, int itemId, String lineName, String unit, long quantity, long gp)
	{
		source(key, name, category).addSupply(lineKey, itemId, lineName, unit, quantity, gp, now());
		changed();
	}

	/** Moves an already-charged supply from one source to another (compost following a new crop). */
	public void moveSupply(String fromKey, String toKey, String toName, SourceCategory category,
		String lineKey, int itemId, String lineName, long quantity, long gp)
	{
		SourceRecord from = data.sources.get(fromKey);
		if (from == null || !from.removeSupply(lineKey, quantity, gp))
		{
			return;
		}
		if (from.isEmpty())
		{
			data.sources.remove(fromKey);
		}
		source(toKey, toName, category).addSupply(lineKey, itemId, lineName, null, quantity, gp, now());
		changed();
	}

	/** Moves XP from one source to another (composting XP following a new crop). */
	public void moveXp(String fromKey, String toKey, String toName, SourceCategory category, String skill, long amount)
	{
		SourceRecord from = data.sources.get(fromKey);
		if (from == null || !from.removeXp(skill, amount))
		{
			return;
		}
		if (from.isEmpty())
		{
			data.sources.remove(fromKey);
		}
		source(toKey, toName, category).addXp(skill, amount, now());
		changed();
	}

	/** Folds a whole source into another one (renaming it when the other doesn't exist yet). */
	public void mergeInto(String fromKey, String toKey, String toName)
	{
		if (fromKey.equals(toKey))
		{
			return;
		}
		SourceRecord from = data.sources.remove(fromKey);
		if (from == null)
		{
			return;
		}
		SourceRecord to = data.sources.get(toKey);
		if (to == null)
		{
			from.key = toKey;
			from.name = toName;
			data.sources.put(toKey, from);
		}
		else
		{
			to.mergeFrom(from);
		}
		changed();
	}

	public void addXp(String key, String name, SourceCategory category, String skill, long amount)
	{
		source(key, name, category).addXp(skill, amount, now());
		changed();
	}

	public void incrementCount(String key, String name, SourceCategory category)
	{
		source(key, name, category).incrementCount(now());
		changed();
	}

	public void remove(String key)
	{
		if (data.sources.remove(key) != null)
		{
			changed();
		}
	}

	public void changed()
	{
		dirty = true;
		revision++;
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void markSaved()
	{
		dirty = false;
	}

	/** Increases on every change; the panel uses it to skip redundant redraws. */
	public long revision()
	{
		return revision;
	}
}
