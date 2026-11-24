package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TaskRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.RecentSupplies;
import org.junit.Before;
import org.junit.Test;

public class RecentSuppliesTest
{
	private static final int SUPER_COMBAT = 12695;
	private static final int SCALES = 12934;
	private static final int AMETHYST_DART = 25849;

	private Ledger ledger;
	private RecentSupplies recent;

	@Before
	public void setUp()
	{
		ledger = new Ledger(() -> 1_000L);
		recent = new RecentSupplies(ledger);
	}

	private RecentSupplies.Booked general(String lineKey, int itemId, String name, long qty, long gp, int tick)
	{
		ledger.addSupply(TrackerData.GENERAL_SUPPLIES_KEY, TrackerData.GENERAL_SUPPLIES_NAME,
			SourceCategory.GENERAL_SUPPLIES, lineKey, itemId, name, "doses", qty, gp);
		ledger.incrementCount(TrackerData.GENERAL_SUPPLIES_KEY, TrackerData.GENERAL_SUPPLIES_NAME,
			SourceCategory.GENERAL_SUPPLIES);
		RecentSupplies.Booked b = new RecentSupplies.Booked(TrackerData.GENERAL_SUPPLIES_KEY,
			TrackerData.GENERAL_SUPPLIES_NAME, SourceCategory.GENERAL_SUPPLIES, false, lineKey, itemId, name, "doses",
			qty, gp, tick, true);
		recent.addGeneral(b);
		return b;
	}

	@Test
	public void potionBeforeTheFightMovesToTheKill()
	{
		general("12695", SUPER_COMBAT, "Super combat potion", 1, 4_000, 100);
		ledger.data().currentTask = new TaskRecord("Goblins", null, 50, 50, 1);

		// The fight starts at tick 150, the goblin dies at 160.
		recent.moveGeneralToKill("npc:Goblin", "Goblin", true, 150 - RecentSupplies.PRE_FIGHT_TICKS);

		assertNull(ledger.data().sources.get(TrackerData.GENERAL_SUPPLIES_KEY));
		SourceRecord goblin = ledger.data().sources.get("npc:Goblin");
		assertEquals(4_000, goblin.supplyCost());
		assertEquals("doses", goblin.supplies.get("12695").unit);
		assertEquals(4_000, ledger.data().currentTask.supplyGp);
	}

	@Test
	public void suppliesLongBeforeTheFightStayInGeneral()
	{
		general("12695", SUPER_COMBAT, "Super combat potion", 1, 4_000, 100);
		recent.moveGeneralToKill("npc:Goblin", "Goblin", false, 1_000 - RecentSupplies.PRE_FIGHT_TICKS);
		assertEquals(4_000, ledger.data().sources.get(TrackerData.GENERAL_SUPPLIES_KEY).supplyCost());
		assertNull(ledger.data().sources.get("npc:Goblin"));
	}

	private void blowpipe(String key, String name, int itemId, long qty, long each, int tick)
	{
		ledger.addSupply(key, name, SourceCategory.COMBAT, String.valueOf(itemId), itemId, "x", null, qty, qty * each);
		recent.addBlowpipe(new RecentSupplies.Booked(key, name, SourceCategory.COMBAT, false, String.valueOf(itemId),
			itemId, "x", null, qty, qty * each, tick, false));
	}

	@Test
	public void blowpipeCheckCorrectsTheEstimate()
	{
		recent.onBlowpipeCheck(AMETHYST_DART, 1_000, 5_000, SCALES, id -> 0);
		// Estimated 10 scales and 3 darts...
		blowpipe("npc:Zulrah", "Zulrah", SCALES, 10, 150, 1);
		blowpipe("npc:Zulrah", "Zulrah", AMETHYST_DART, 3, 1_000, 1);
		// ...but the Check shows 12 scales and 2 darts were used.
		recent.onBlowpipeCheck(AMETHYST_DART, 998, 4_988, SCALES, id -> id == SCALES ? 150 : 1_000);

		SourceRecord zulrah = ledger.data().sources.get("npc:Zulrah");
		assertEquals(12, zulrah.supplies.get(String.valueOf(SCALES)).quantity);
		assertEquals(1_800, zulrah.supplies.get(String.valueOf(SCALES)).gp);
		assertEquals(2, zulrah.supplies.get(String.valueOf(AMETHYST_DART)).quantity);
		assertEquals(2_000, zulrah.supplies.get(String.valueOf(AMETHYST_DART)).gp);
	}

	@Test
	public void reloadingBetweenChecksSkipsTheCorrection()
	{
		recent.onBlowpipeCheck(AMETHYST_DART, 10, 100, SCALES, id -> 0);
		blowpipe("npc:Zulrah", "Zulrah", SCALES, 4, 150, 1);
		recent.forgetBlowpipeCheck();
		recent.onBlowpipeCheck(AMETHYST_DART, 500, 9_000, SCALES, id -> 150);
		assertEquals(4, ledger.data().sources.get("npc:Zulrah").supplies.get(String.valueOf(SCALES)).quantity);
	}
}
