package com.gildedledger.tracking;

import com.gildedledger.data.PeriodStart;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-time clean-up for a bug in earlier versions: the game sometimes reported a skill as
 * 0 XP while stats were loading, and the next real update was booked as a gain of the whole
 * skill's XP. An entry holding most of a big skill's total XP can only come from that, since
 * the plugin can't have watched it all being earned.
 */
public final class XpJumpRepair
{
	/** Only skills with at least this much XP can have been hit visibly. */
	static final long MIN_JUMP = 200_000;
	/** An entry holding this share of the skill's total XP. */
	static final double SHARE = 0.9;

	private XpJumpRepair()
	{
	}

	/**
	 * Takes the bogus jump back out of every entry it landed in (and out of the period
	 * starts that were taken after it). Returns a description of each fix, empty if none.
	 *
	 * @param skillXp the player's current XP in each skill, by skill name
	 */
	public static List<String> repair(TrackerData data, Map<String, Long> skillXp)
	{
		List<String> fixed = new ArrayList<>();
		for (SourceRecord record : data.sources.values())
		{
			for (Map.Entry<String, Long> e : new ArrayList<>(record.xp.entrySet()))
			{
				String skill = e.getKey();
				long now = e.getValue();
				long total = skillXp.getOrDefault(skill, 0L);
				long before = earliestStart(data, record.key, skill);
				long gained = now - before;
				if (total < MIN_JUMP || gained < MIN_JUMP || gained < SHARE * total)
				{
					continue;
				}
				// gained = jump + real XP; total = jump + XP earned anywhere since. What's left
				// is (about) the real XP this entry earned before the jump.
				long fixedXp = before + Math.max(0, gained - total);
				long removed = now - fixedXp;
				record.xp.put(skill, fixedXp);
				for (PeriodStart start : data.periodStarts.values())
				{
					SourceRecord snap = start.sources.get(record.key);
					Long snapXp = snap == null ? null : snap.xp.get(skill);
					if (snapXp != null && snapXp - before >= removed)
					{
						snap.xp.put(skill, Math.max(0, snapXp - removed));
					}
				}
				fixed.add(record.name + " " + skill + " -" + removed);
			}
		}
		return fixed;
	}

	/** The entry's XP at the earliest period start that has it (0 if none does). */
	private static long earliestStart(TrackerData data, String key, String skill)
	{
		long earliest = Long.MAX_VALUE;
		for (PeriodStart start : data.periodStarts.values())
		{
			SourceRecord snap = start.sources.get(key);
			if (snap != null)
			{
				earliest = Math.min(earliest, snap.xp.getOrDefault(skill, 0L));
			}
		}
		return earliest == Long.MAX_VALUE ? 0 : earliest;
	}
}
