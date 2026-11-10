package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.PeriodTracker;
import com.gildedledger.tracking.PeriodTracker.Period;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

public class PeriodTrackerTest
{
	private static final String ALTAR = "item:RUNECRAFTING:Blood altar";
	private static final int BLOOD_RUNE = 565;

	private Ledger ledger;
	private PeriodTracker periods;

	@Before
	public void setUp()
	{
		ledger = new Ledger(() -> 0L);
		periods = new PeriodTracker(ledger);
	}

	private void craft(int runes, long xp)
	{
		ledger.addLoot(ALTAR, "Blood altar", SourceCategory.RUNECRAFTING, BLOOD_RUNE, runes);
		ledger.incrementCount(ALTAR, "Blood altar", SourceCategory.RUNECRAFTING);
		ledger.addXp(ALTAR, "Blood altar", SourceCategory.RUNECRAFTING, "Runecraft", xp);
	}

	@Test
	public void periodsStartAtMidnightMondayAndTheFirst()
	{
		// Wednesday 2026-11-04 15:30 UTC.
		Instant now = Instant.parse("2026-11-04T15:30:00Z");
		assertEquals(Instant.parse("2026-11-04T00:00:00Z"), PeriodTracker.periodStart(Period.DAY, now, ZoneOffset.UTC).toInstant());
		assertEquals(Instant.parse("2026-11-02T00:00:00Z"), PeriodTracker.periodStart(Period.WEEK, now, ZoneOffset.UTC).toInstant());
		assertEquals(Instant.parse("2026-11-01T00:00:00Z"), PeriodTracker.periodStart(Period.MONTH, now, ZoneOffset.UTC).toInstant());
		// A Monday is its own week start.
		Instant monday = Instant.parse("2026-11-02T00:00:00Z");
		assertEquals(monday, PeriodTracker.periodStart(Period.WEEK, monday, ZoneOffset.UTC).toInstant());
	}

	@Test
	public void timeZoneMovesTheStart()
	{
		// 03:00 UTC on Nov 4 is still Nov 3 in Chicago (UTC-6 in November).
		Instant now = Instant.parse("2026-11-04T03:00:00Z");
		ZoneId chicago = ZoneId.of("America/Chicago");
		assertEquals(Instant.parse("2026-11-03T06:00:00Z"), PeriodTracker.periodStart(Period.DAY, now, chicago).toInstant());
		assertEquals(PeriodTracker.periodId(Period.DAY, Instant.parse("2026-11-03T12:00:00Z"), chicago),
			PeriodTracker.periodId(Period.DAY, now, chicago));
	}

	@Test
	public void dayShowsOnlyWhatWasGainedToday()
	{
		craft(100, 1000);
		Instant day1 = Instant.parse("2026-11-04T22:00:00Z");
		periods.tick(day1, ZoneOffset.UTC);
		craft(50, 500);

		SourceRecord today = periods.view(Period.DAY).sources.get(ALTAR);
		assertEquals(50L, (long) today.loot.get(BLOOD_RUNE));
		assertEquals(1L, today.count);
		assertEquals(500L, (long) today.xp.get("Runecraft"));

		// Midnight passes: the new day starts empty, the week keeps both.
		Instant day2 = Instant.parse("2026-11-05T00:00:01Z");
		periods.tick(day2, ZoneOffset.UTC);
		assertFalse(periods.view(Period.DAY).sources.containsKey(ALTAR));
		// The week began when tracking periods began (after the first 100), so it has both days since.
		assertEquals(50L, (long) periods.view(Period.WEEK).sources.get(ALTAR).loot.get(BLOOD_RUNE));
		craft(10, 100);
		assertEquals(10L, (long) periods.view(Period.DAY).sources.get(ALTAR).loot.get(BLOOD_RUNE));
	}

	@Test
	public void allTimeIsTheRealData()
	{
		craft(10, 100);
		periods.tick(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
		TrackerData all = periods.view(Period.ALL);
		assertSame(ledger.data(), all);
	}

	@Test
	public void resetSourceStartsFreshInEveryPeriod()
	{
		craft(100, 1000);
		periods.tick(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
		ledger.remove(ALTAR);
		periods.forget(ALTAR);
		craft(20, 200);
		assertEquals(20L, (long) periods.view(Period.DAY).sources.get(ALTAR).loot.get(BLOOD_RUNE));
		assertTrue(ledger.data().periodStarts.get("DAY").sources.isEmpty());
	}

	@Test
	public void sourceStartedTodayShowsInFull()
	{
		periods.tick(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
		craft(30, 300);
		assertEquals(30L, (long) periods.view(Period.MONTH).sources.get(ALTAR).loot.get(BLOOD_RUNE));
	}
}
