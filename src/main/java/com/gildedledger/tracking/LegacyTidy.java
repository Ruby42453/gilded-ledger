package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.SupplyLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Brings data saved by older versions in line with what's tracked now, every time a
 * profile loads (it changes nothing the second time):
 * <ul>
 *   <li>Items that are no longer tracked (minigame-only items like the Tears of Guthix
 *   stone bowl, a slayer ring losing charges, essence pouches degrading) are removed.</li>
 *   <li>Hespori seeds planted move from Farming to the Hespori kill in PvM.</li>
 *   <li>Farming entries for untradeable plants are remembered, so "Hide untradeable
 *   plants" can hide them.</li>
 *   <li>Monsters that are part of a boss fight (Hespori's flowers) merge into the boss.</li>
 * </ul>
 */
public final class LegacyTidy
{
	private LegacyTidy()
	{
	}

	public static void tidy(Ledger ledger, ItemInfo items)
	{
		List<SourceRecord> records = new ArrayList<>(ledger.data().sources.values());
		boolean changed = false;
		for (SourceRecord record : records)
		{
			changed |= dropUntrackedItems(record, items);
			if (record.category == SourceCategory.FARMING)
			{
				changed |= moveHesporiSeeds(ledger, record, items);
				noteUntradeablePlant(ledger, record, items);
			}
			if (record.isEmpty())
			{
				ledger.data().sources.remove(record.key);
				changed = true;
				continue;
			}
			if (record.category == SourceCategory.COMBAT && record.name != null)
			{
				// Boss parts (Hespori's flowers) join the boss.
				String boss = ItemRules.bossGroup(record.name);
				if (!boss.equals(record.name))
				{
					ledger.mergeInto(record.key, "npc:" + boss, boss);
					changed = true;
				}
			}
		}
		if (changed)
		{
			ledger.changed();
		}
	}

	private static boolean untracked(String name)
	{
		return ItemRules.isMinigameItem(name) || ItemRules.isDegrading(name);
	}

	private static boolean dropUntrackedItems(SourceRecord record, ItemInfo items)
	{
		boolean changed = record.loot.keySet().removeIf(id -> untracked(items.name(id)));
		changed |= record.supplies.values().removeIf(line -> untracked(line.name != null ? line.name : items.name(line.itemId)));
		return changed;
	}

	private static boolean moveHesporiSeeds(Ledger ledger, SourceRecord record, ItemInfo items)
	{
		boolean moved = false;
		for (Map.Entry<String, SupplyLine> e : new ArrayList<>(record.supplies.entrySet()))
		{
			SupplyLine line = e.getValue();
			if ("hespori seed".equalsIgnoreCase(items.name(line.itemId)))
			{
				ledger.moveSupply(record.key, "npc:" + FarmingTracker.HESPORI, FarmingTracker.HESPORI,
					SourceCategory.COMBAT, e.getKey(), line.itemId, line.name, line.quantity, line.gp);
				moved = true;
			}
		}
		return moved;
	}

	private static void noteUntradeablePlant(Ledger ledger, SourceRecord record, ItemInfo items)
	{
		if (record.name == null || FarmingTracker.HESPORI.equals(record.name) || "Other".equals(record.name))
		{
			return;
		}
		boolean untradeableSeed = record.supplies.values().stream()
			.anyMatch(line -> ItemRules.isPlantable(items.name(line.itemId)) && !items.tradeable(line.itemId));
		boolean untradeableHarvest = !record.loot.isEmpty()
			&& record.loot.keySet().stream().allMatch(id -> !items.tradeable(id));
		if (untradeableSeed || untradeableHarvest)
		{
			ledger.data().untradeableCrops.add(record.name);
		}
	}
}
