package com.gildedledger.data;

import com.gildedledger.tracking.FarmingTracker;
import com.gildedledger.tracking.ItemRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything saved for one RuneScape account. Serialized to JSON and stored in
 * the account's RuneLite profile, so each character keeps its own ledger.
 */
public class TrackerData
{
	public static final int MAX_TASK_HISTORY = 25;
	public static final String GENERAL_SUPPLIES_KEY = "supplies:general";
	public static final String GENERAL_SUPPLIES_NAME = "General supplies";
	public static final String TELEPORTS_KEY = "teleport:all";
	public static final String TELEPORTS_NAME = "Teleports";

	public int version = 1;
	public Map<String, SourceRecord> sources = new LinkedHashMap<>();
	public TaskRecord currentTask;
	public List<TaskRecord> taskHistory = new ArrayList<>();
	/** The dart last seen loaded in the blowpipe (from a "Check" or a shot); 0 if unknown. */
	public int blowpipeDart;
	/** Which crop was last planted in each farming patch, keyed by packed patch location. */
	public Map<String, String> patchCrops = new LinkedHashMap<>();
	/** Crops grown from untradeable seeds (quest plants like goutweed), hidden by a setting. */
	public java.util.Set<String> untradeableCrops = new java.util.HashSet<>();
	/** The GP/hour session in progress (running or paused), or null. */
	public GpSession activeSession;
	/** Finished GP/hour sessions, newest first. */
	public List<GpSession> sessionLog = new ArrayList<>();

	public SourceRecord source(String key, String name, SourceCategory category, long nowMillis)
	{
		SourceRecord record = sources.get(key);
		if (record == null)
		{
			record = new SourceRecord(key, name, category, nowMillis);
			sources.put(key, record);
		}
		return record;
	}

	public void archiveCurrentTask(long nowMillis)
	{
		if (currentTask == null)
		{
			return;
		}
		if (!currentTask.isFinished())
		{
			currentTask.finishedMillis = nowMillis;
		}
		if (currentTask.hasActivity())
		{
			taskHistory.add(0, currentTask);
			while (taskHistory.size() > MAX_TASK_HISTORY)
			{
				taskHistory.remove(taskHistory.size() - 1);
			}
		}
		currentTask = null;
	}

	/**
	 * Earlier versions kept unassigned supplies under PvM and filed all processing
	 * as "Processing: Skill: Product". Move those into the current categories.
	 */
	private void migrateOldCategories()
	{
		Map<String, SourceRecord> migrated = new LinkedHashMap<>();
		for (SourceRecord record : sources.values())
		{
			if (record.category == SourceCategory.FARMING)
			{
				migrateFarming(record);
			}
			if ("supplies:unassigned".equals(record.key)
				|| record.category == SourceCategory.GENERAL_SUPPLIES
				|| (record.category == SourceCategory.COMBAT && record.key.startsWith("supplies:")))
			{
				// General supplies are one table, not one entry per item.
				record.category = SourceCategory.GENERAL_SUPPLIES;
				record.key = GENERAL_SUPPLIES_KEY;
				record.name = GENERAL_SUPPLIES_NAME;
			}
			else if (record.category == SourceCategory.PURCHASE || "process:PROCESSING:Battlestaff".equals(record.key)
				|| "item:PROCESSING:Battlestaff".equals(record.key))
			{
				// Buying from NPCs (Zaff's battlestaves) is no longer tracked at all.
				continue;
			}
			else if (record.category == SourceCategory.TELEPORTS)
			{
				// Teleports are one table, not one entry per teleport.
				record.key = TELEPORTS_KEY;
				record.name = TELEPORTS_NAME;
			}
			else if (record.category == SourceCategory.PROCESSING && !record.xp.isEmpty())
			{
				// Something filed as Processing that did give XP (lighting a log before the XP
				// arrived, in older versions): move it to that skill.
				SourceCategory skill = SourceCategory.forSkill(topSkill(record));
				if (skill != null)
				{
					String product = record.key.startsWith("process:PROCESSING:")
						? record.key.substring("process:PROCESSING:".length()) : record.name;
					record.category = skill;
					record.name = product;
					record.key = "process:" + skill.name() + ":" + product;
				}
			}
			else if (record.category == SourceCategory.PROCESSING && record.key.startsWith("process:"))
			{
				String[] parts = record.key.split(":", 3);
				SourceCategory skill = parts.length == 3 ? SourceCategory.forSkill(parts[1]) : null;
				if (skill != null)
				{
					record.category = skill;
					record.name = parts[2];
					record.key = "process:" + skill.name() + ":" + parts[2];
				}
			}
			// Skills are now split by item ("item:...") or, for Thieving, by NPC ("target:...").
			if (record.key.startsWith("process:"))
			{
				record.key = "item:" + record.key.substring("process:".length());
			}
			else if (record.key.startsWith("gather:THIEVING:"))
			{
				record.key = "target:" + record.key.substring("gather:".length());
			}
			SourceRecord existing = migrated.get(record.key);
			if (existing == null)
			{
				migrated.put(record.key, record);
			}
			else
			{
				existing.mergeFrom(record);
			}
		}
		sources = migrated;
	}

	/**
	 * Farming is filed by crop now ("Toadflax", "Yew tree"). Older saves used
	 * "farming:Herb:Toadflax", and some farming was misfiled by item
	 * ("item:FARMING:Toadflax seed", "item:FARMING:Grimy toadflax").
	 */
	private static void migrateFarming(SourceRecord record)
	{
		String key = record.key;
		String crop = null;
		if (key.startsWith("farming:") && key.split(":", -1).length == 3)
		{
			String[] parts = key.split(":", 3);
			String name = FarmingTracker.cropName(parts[1], "Unknown".equals(parts[2]) ? null : parts[2]);
			record.key = "farming:" + name;
			record.name = name;
			return;
		}
		if (key.startsWith("item:FARMING:"))
		{
			String item = key.substring("item:FARMING:".length());
			String lower = item.toLowerCase(java.util.Locale.ENGLISH);
			if (lower.startsWith("grimy "))
			{
				crop = ItemRules.capitalize(item.substring("grimy ".length()));
			}
			else if (ItemRules.isPlantable(item))
			{
				crop = ItemRules.cropFromPlantable(item);
				if (lower.endsWith("sapling") || lower.endsWith("acorn"))
				{
					crop = crop + " tree";
				}
			}
			String name = crop != null ? crop : "Unsorted farming";
			record.key = "farming:" + name;
			record.name = name;
		}
	}

	private static String topSkill(SourceRecord record)
	{
		String best = null;
		long bestXp = 0;
		for (Map.Entry<String, Long> entry : record.xp.entrySet())
		{
			if (entry.getValue() != null && entry.getValue() > bestXp)
			{
				best = entry.getKey();
				bestXp = entry.getValue();
			}
		}
		return best;
	}

	/** Gson leaves fields null when an older save lacks them; repair that after loading. */
	public TrackerData normalize()
	{
		if (sources == null)
		{
			sources = new LinkedHashMap<>();
		}
		if (taskHistory == null)
		{
			taskHistory = new ArrayList<>();
		}
		if (patchCrops == null)
		{
			patchCrops = new LinkedHashMap<>();
		}
		sources.values().removeIf(r -> r == null || r.key == null || r.category == null);
		for (SourceRecord record : sources.values())
		{
			if (record.loot == null)
			{
				record.loot = new LinkedHashMap<>();
			}
			if (record.supplies == null)
			{
				record.supplies = new LinkedHashMap<>();
			}
			if (record.xp == null)
			{
				record.xp = new LinkedHashMap<>();
			}
			if (record.excludedLoot == null)
			{
				record.excludedLoot = new java.util.LinkedHashSet<>();
			}
			if (record.excludedSupplies == null)
			{
				record.excludedSupplies = new java.util.LinkedHashSet<>();
			}
		}
		migrateOldCategories();
		return this;
	}
}
