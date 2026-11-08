package com.gildedledger.ui;

import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.SupplyLine;
import com.gildedledger.data.TaskRecord;
import com.gildedledger.data.TrackerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * An immutable snapshot of the ledger with prices already applied. Built on the
 * client thread (where item lookups are allowed) and handed to Swing to draw.
 */
public final class PanelModel
{
	/** Skills in the order RuneLite's skill panel lists combat stats, then everything else. */
	private static final List<String> SKILL_ORDER = List.of(
		"Attack", "Strength", "Defence", "Ranged", "Magic", "Hitpoints", "Prayer", "Slayer",
		"Farming", "Woodcutting");

	public static final class LootView
	{
		public final int itemId;
		public final String name;
		public final long quantity;
		public final long geEach;
		public final long haEach;
		public final boolean tradeable;
		/** GE total, or HA total when untradeable. */
		public final long value;
		/** The player excluded this item from profit; shown greyed out. */
		public final boolean excluded;

		LootView(int itemId, String name, long quantity, LootPrices prices, boolean excluded)
		{
			this.excluded = excluded;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.geEach = prices.ge(itemId);
			this.haEach = prices.highAlch(itemId);
			this.tradeable = prices.tradeable(itemId);
			this.value = prices.value(itemId) * quantity;
		}

		public long geTotal()
		{
			return geEach * quantity;
		}

		public long haTotal()
		{
			return haEach * quantity;
		}
	}

	public static final class SupplyView
	{
		public final String lineKey;
		public final boolean excluded;
		public final int itemId;
		public final String name;
		public final long quantity;
		public final long gp;
		public final String unit;

		SupplyView(String lineKey, SupplyLine line, boolean excluded)
		{
			this.lineKey = lineKey;
			this.excluded = excluded;
			this.itemId = line.itemId;
			this.name = line.name;
			this.quantity = line.quantity;
			this.gp = line.gp;
			this.unit = line.unit;
		}
	}

	public static final class SourceView
	{
		public final String key;
		public final String name;
		public final SourceCategory category;
		public final long count;
		public final long lootValue;
		public final long lootHa;
		public final long supplyGp;
		public final long xp;
		/** Skill name → XP, in skill-panel order. */
		public final Map<String, Long> xpBySkill;
		public final List<LootView> loot;
		public final List<SupplyView> supplies;
		public final long lastUpdatedMillis;

		SourceView(SourceRecord r, List<LootView> loot, List<SupplyView> supplies)
		{
			this.key = r.key;
			this.name = r.name;
			this.category = r.category;
			this.count = r.count;
			long value = 0;
			long ha = 0;
			for (LootView item : loot)
			{
				if (!item.excluded)
				{
					value += item.value;
					ha += item.haTotal();
				}
			}
			this.lootValue = value;
			this.lootHa = ha;
			this.supplyGp = r.supplyCost();
			this.xp = r.totalXp();
			this.xpBySkill = orderedXp(r.xp);
			this.loot = loot;
			this.supplies = supplies;
			this.lastUpdatedMillis = r.lastUpdatedMillis;
		}

		public long profit()
		{
			return lootValue - supplyGp;
		}
	}

	public static final class TaskView
	{
		public final String name;
		public final String location;
		public final int remaining;
		public final int initial;
		public final long kills;
		public final long lootGp;
		public final long supplyGp;
		public final long xp;
		public final long slayerXp;
		public final boolean finished;

		TaskView(TaskRecord t)
		{
			this.name = t.taskName;
			this.location = t.location;
			this.remaining = t.remainingAmount;
			this.initial = t.initialAmount;
			this.kills = t.kills;
			this.lootGp = t.lootGp;
			this.supplyGp = t.supplyGp;
			this.xp = t.xp;
			this.slayerXp = t.slayerXp;
			this.finished = t.isFinished();
		}

		public long profit()
		{
			return lootGp - supplyGp;
		}
	}

	public final boolean ready;
	public final TaskView task;
	public final List<SourceView> sources;

	private PanelModel(boolean ready, TaskView task, List<SourceView> sources)
	{
		this.ready = ready;
		this.task = task;
		this.sources = sources;
	}

	public static PanelModel build(TrackerData data, LootPrices prices, IntFunction<String> itemName, boolean ready)
	{
		return build(data, prices, itemName, ready, false);
	}

	/**
	 * @param combineSkills show each skill as one combined table instead of one entry
	 *                      per item; PvM always keeps one entry per monster
	 */
	public static PanelModel build(TrackerData data, LootPrices prices, IntFunction<String> itemName, boolean ready,
		boolean combineSkills)
	{
		return build(data, prices, itemName, ready, combineSkills, Collections.emptySet());
	}

	/**
	 * @param hidden categories left out of the panel and its totals (still tracked)
	 */
	public static PanelModel build(TrackerData data, LootPrices prices, IntFunction<String> itemName, boolean ready,
		boolean combineSkills, java.util.Set<SourceCategory> hidden)
	{
		return build(data, prices, itemName, ready, combineSkills, r -> hidden.contains(r.category));
	}

	/**
	 * @param hidden entries left out of the panel and its totals (still tracked): hidden
	 *               skills, and untradeable plants when that setting is on
	 */
	public static PanelModel build(TrackerData data, LootPrices prices, IntFunction<String> itemName, boolean ready,
		boolean combineSkills, java.util.function.Predicate<SourceRecord> hidden)
	{
		List<SourceView> views = new ArrayList<>();
		for (SourceRecord record : displayRecords(data, combineSkills, hidden))
		{
			if (hidden.test(record))
			{
				continue;
			}
			List<LootView> loot = new ArrayList<>();
			for (Map.Entry<Integer, Long> e : record.loot.entrySet())
			{
				loot.add(new LootView(e.getKey(), itemName.apply(e.getKey()), e.getValue(), prices,
					record.excludedLoot.contains(e.getKey())));
			}
			loot.sort(Comparator.comparingLong((LootView v) -> v.value).reversed());

			List<SupplyView> supplies = new ArrayList<>();
			for (Map.Entry<String, SupplyLine> line : record.supplies.entrySet())
			{
				supplies.add(new SupplyView(line.getKey(), line.getValue(), record.excludedSupplies.contains(line.getKey())));
			}
			supplies.sort(Comparator.comparingLong((SupplyView v) -> v.gp).reversed());

			views.add(new SourceView(record, Collections.unmodifiableList(loot), Collections.unmodifiableList(supplies)));
		}
		TaskView task = data.currentTask == null ? null : new TaskView(data.currentTask);
		return new PanelModel(ready, task, Collections.unmodifiableList(views));
	}

	/** The saved records, or with skills merged into one record per category for display. */
	private static List<SourceRecord> displayRecords(TrackerData data, boolean combineSkills,
		java.util.function.Predicate<SourceRecord> hidden)
	{
		List<SourceRecord> records = new ArrayList<>();
		Map<SourceCategory, SourceRecord> combined = new EnumMap<>(SourceCategory.class);
		for (SourceRecord record : data.sources.values())
		{
			if (record.isEmpty() || (combineSkills && record.category != SourceCategory.COMBAT && hidden.test(record)))
			{
				// Hidden entries (an untradeable plant) stay out of their category's table too.
				continue;
			}
			if (!combineSkills || record.category == SourceCategory.COMBAT)
			{
				records.add(record);
				continue;
			}
			SourceRecord table = combined.get(record.category);
			if (table == null)
			{
				table = new SourceRecord(COMBINED_PREFIX + record.category.name(), record.category.getLabel(),
					record.category, Long.MAX_VALUE);
				table.lastUpdatedMillis = 0;
				combined.put(record.category, table);
				records.add(table);
			}
			table.mergeFrom(copyOf(record));
		}
		return records;
	}

	/** A deep copy, so merging for display never changes the saved records. */
	private static SourceRecord copyOf(SourceRecord r)
	{
		SourceRecord copy = new SourceRecord(r.key, r.name, r.category, r.firstSeenMillis);
		copy.count = r.count;
		copy.lastUpdatedMillis = r.lastUpdatedMillis;
		copy.loot.putAll(r.loot);
		copy.xp.putAll(r.xp);
		copy.excludedLoot.addAll(r.excludedLoot);
		copy.excludedSupplies.addAll(r.excludedSupplies);
		r.supplies.forEach((key, line) ->
		{
			SupplyLine dup = new SupplyLine(line.itemId, line.name, line.unit);
			dup.quantity = line.quantity;
			dup.gp = line.gp;
			copy.supplies.put(key, dup);
		});
		return copy;
	}

	/** Key prefix of a skill shown as one combined table. */
	public static final String COMBINED_PREFIX = "category:";

	public static Map<String, Long> orderedXp(Map<String, Long> xp)
	{
		Map<String, Long> ordered = new LinkedHashMap<>();
		for (String skill : SKILL_ORDER)
		{
			Long amount = xp.get(skill);
			if (amount != null && amount > 0)
			{
				ordered.put(skill, amount);
			}
		}
		xp.forEach((skill, amount) ->
		{
			if (!ordered.containsKey(skill) && amount != null && amount > 0)
			{
				ordered.put(skill, amount);
			}
		});
		return Collections.unmodifiableMap(ordered);
	}

	/** One session's loot and supplies, all sources together, for the session log. */
	public static final class SessionDetail
	{
		public final List<LootView> loot;
		public final List<SupplyView> supplies;
		public final long lootValue;
		public final long supplyGp;

		SessionDetail(List<LootView> loot, List<SupplyView> supplies)
		{
			this.loot = loot;
			this.supplies = supplies;
			this.lootValue = loot.stream().mapToLong(l -> l.value).sum();
			this.supplyGp = supplies.stream().mapToLong(v -> v.gp).sum();
		}
	}

	public static SessionDetail sessionDetail(com.gildedledger.data.GpSession session, LootPrices prices,
		IntFunction<String> itemName)
	{
		List<LootView> loot = new ArrayList<>();
		session.loot.forEach((id, qty) -> loot.add(new LootView(id, itemName.apply(id), qty, prices, false)));
		loot.sort(Comparator.comparingLong((LootView v) -> v.value).reversed());
		List<SupplyView> supplies = new ArrayList<>();
		session.supplies.forEach((key, line) -> supplies.add(new SupplyView(key, line, false)));
		supplies.sort(Comparator.comparingLong((SupplyView v) -> v.gp).reversed());
		return new SessionDetail(Collections.unmodifiableList(loot), Collections.unmodifiableList(supplies));
	}
}
