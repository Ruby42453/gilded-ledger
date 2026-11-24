package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.PeriodStart;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.XpJumpRepair;
import java.util.Map;
import org.junit.Test;

public class XpJumpRepairTest
{
	private static SourceRecord record(TrackerData data, String key, String skill, long xp)
	{
		SourceRecord r = data.source(key, "Other", SourceCategory.SMITHING, 0);
		r.xp.put(skill, xp);
		return r;
	}

	@Test
	public void wholeSkillJumpIsRemoved()
	{
		TrackerData data = new TrackerData();
		// 2,000 real XP before today, then the 3.43M jump, then 16,680 more.
		record(data, "other:SMITHING", "Smithing", 2_000 + 3_430_000 + 16_680);
		PeriodStart day = new PeriodStart();
		day.id = "2026-11-13";
		SourceRecord before = new SourceRecord("other:SMITHING", "Other", SourceCategory.SMITHING, 0);
		before.xp.put("Smithing", 2_000L);
		day.sources.put("other:SMITHING", before);
		data.periodStarts.put("DAY", day);

		assertEquals(1, XpJumpRepair.repair(data, Map.of("Smithing", 3_446_680L)).size());
		long left = data.sources.get("other:SMITHING").xp.get("Smithing");
		assertTrue("left " + left, left >= 2_000 && left <= 2_000 + 16_680);
	}

	@Test
	public void realXpIsLeftAlone()
	{
		TrackerData data = new TrackerData();
		record(data, "item:WOODCUTTING:Yew", "Woodcutting", 400_000);
		assertTrue(XpJumpRepair.repair(data, Map.of("Woodcutting", 13_034_431L)).isEmpty());
		assertEquals(400_000L, (long) data.sources.get("item:WOODCUTTING:Yew").xp.get("Woodcutting"));
	}
}
