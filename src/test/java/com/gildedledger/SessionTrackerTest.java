package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.GpSession;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.SessionTracker;
import com.gildedledger.tracking.ItemRules;
import org.junit.Before;
import org.junit.Test;

public class SessionTrackerTest
{
	private static final int BLOOD_RUNE = 565;
	private static final int KARAMBWAN = 3142;

	private long now = 1_000_000;
	private Ledger ledger;
	private SessionTracker sessions;

	@Before
	public void setUp()
	{
		ledger = new Ledger(() -> now);
		// Blood runes 400 gp, karambwan 180 gp.
		sessions = new SessionTracker(ledger, id -> id == BLOOD_RUNE ? 400 : id == KARAMBWAN ? 180 : 0, () -> now);
	}

	private void craftBloods(int runes)
	{
		ledger.addLoot("item:RUNECRAFTING:Blood altar", "Blood altar", SourceCategory.RUNECRAFTING, BLOOD_RUNE, runes);
		ledger.incrementCount("item:RUNECRAFTING:Blood altar", "Blood altar", SourceCategory.RUNECRAFTING);
	}

	@Test
	public void profitBeforeTheSessionAndWhilePausedDoesNotCount()
	{
		craftBloods(100); // before the session

		sessions.start();
		craftBloods(10);
		now += 30 * 60_000; // 30 minutes
		sessions.pause();
		assertFalse(sessions.active().isRunning());

		craftBloods(50); // while paused: doesn't count
		now += 60 * 60_000;
		sessions.resume();
		craftBloods(5);
		now += 30 * 60_000;

		GpSession live = sessions.live();
		assertEquals(15 * 400, live.profit);
		sessions.end();

		assertNull(sessions.active());
		GpSession done = ledger.data().sessionLog.get(0);
		assertEquals(15 * 400, done.profit);
		assertEquals(60 * 60_000, done.activeMillis);
		assertEquals(6_000, done.gpPerHour());
		assertEquals("Blood rune crafting", done.title);
	}

	@Test
	public void sessionsAreNamedAfterWhatWasDoneMost()
	{
		sessions.start();
		craftBloods(1);
		for (int i = 0; i < 3; i++)
		{
			ledger.addLoot("item:FISHING:Karambwan", "Karambwan", SourceCategory.FISHING, KARAMBWAN, 1);
			ledger.incrementCount("item:FISHING:Karambwan", "Karambwan", SourceCategory.FISHING);
		}
		now += 60_000;
		sessions.end();
		assertEquals("Karambwan fishing", ledger.data().sessionLog.get(0).title);

		assertEquals("Iron mining", SessionTracker.activityTitle("Iron rocks", SourceCategory.MINING));
		assertEquals("Yew woodcutting", SessionTracker.activityTitle("Yew logs", SourceCategory.WOODCUTTING));
		assertEquals("Abyssal demon", SessionTracker.activityTitle("Abyssal demon", SourceCategory.COMBAT));
		assertEquals("Ourania altar runecrafting", SessionTracker.activityTitle("Ourania altar", SourceCategory.RUNECRAFTING));
	}

	@Test
	public void aSessionKeepsItsOwnLootAndSuppliesAsOneTable()
	{
		craftBloods(100); // before
		ledger.addSupply("item:RUNECRAFTING:Blood altar", "Blood altar", SourceCategory.RUNECRAFTING,
			"7936", 7936, "Pure essence", null, 100, 200);

		sessions.start();
		craftBloods(20);
		ledger.addSupply("item:RUNECRAFTING:Blood altar", "Blood altar", SourceCategory.RUNECRAFTING,
			"7936", 7936, "Pure essence", null, 20, 40);
		ledger.addLoot("item:FISHING:Karambwan", "Karambwan", SourceCategory.FISHING, KARAMBWAN, 3);
		sessions.pause();
		craftBloods(50); // paused
		sessions.resume();
		craftBloods(5);
		now += 60_000;
		sessions.end();

		GpSession done = ledger.data().sessionLog.get(0);
		assertEquals(25L, (long) done.loot.get(BLOOD_RUNE));
		assertEquals(3L, (long) done.loot.get(KARAMBWAN));
		assertEquals(20L, done.supplies.get("7936").quantity);
		assertEquals(40L, done.supplies.get("7936").gp);
	}

	@Test
	public void renameDeleteAndClear()
	{
		sessions.start();
		now += 1000;
		sessions.end();
		sessions.start();
		now += 1000;
		sessions.end();
		String id = ledger.data().sessionLog.get(0).id;
		sessions.rename(id, "My trip");
		assertEquals("My trip", ledger.data().sessionLog.get(0).title);
		assertTrue(ledger.data().sessionLog.get(0).renamed);
		sessions.delete(id);
		assertEquals(1, ledger.data().sessionLog.size());
		sessions.clearLog();
		assertTrue(ledger.data().sessionLog.isEmpty());
	}

	@Test
	public void restartingTheClientKeepsTheTimePlayedButNotTheTimeAway()
	{
		sessions.start();
		craftBloods(2);
		now += 5 * 60_000;
		sessions.heartbeat(); // last tick before the client closed
		now += 60 * 60_000; // closed for an hour
		sessions.pauseAfterRestart();
		assertEquals(5 * 60_000, sessions.active().activeMillis);
		assertEquals(800, sessions.active().profit);
	}

	@Test
	public void restartingWithNoTicksSeenCountsNoTime()
	{
		sessions.start();
		craftBloods(2);
		now += 10 * 60_000;
		sessions.pauseAfterRestart();
		assertFalse(sessions.active().isRunning());
		assertEquals(0, sessions.active().activeMillis);
		assertEquals(800, sessions.active().profit);
	}

	@Test
	public void bossPartsAreFiledUnderTheBoss()
	{
		assertEquals("Hespori", ItemRules.bossGroup("Flower"));
		assertEquals("Zulrah", ItemRules.bossGroup("Snakeling"));
		assertEquals("Grotesque Guardians", ItemRules.bossGroup("Dusk"));
		assertEquals("Goblin", ItemRules.bossGroup("Goblin"));
	}
}
