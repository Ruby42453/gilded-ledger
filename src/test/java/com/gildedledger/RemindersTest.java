package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gildedledger.tracking.Reminders;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RemindersTest
{
	private static final int WARN = 50; // 30 seconds

	@Test
	public void superCombatBoostIsRemindedOnceBeforeItRunsOut()
	{
		Reminders r = new Reminders();
		// Drink: +5 to each melee stat.
		Map<String, Integer> boosted = Map.of("Attack", 5, "Strength", 5, "Defence", 5);
		assertTrue(r.onBoosts(0, boosted, Collections.emptyMap(), false, WARN).isEmpty());
		// The shared clock ticks: one level drops at tick 30, then every 100 ticks.
		int boost = 5;
		List<String> warned = Collections.emptyList();
		int warnedAt = -1;
		for (int tick = 1; tick < 500 && warnedAt < 0; tick++)
		{
			if (tick == 30 || (tick > 30 && (tick - 30) % 100 == 0))
			{
				boost--;
			}
			warned = r.onBoosts(tick, Map.of("Attack", boost, "Strength", boost, "Defence", boost),
				Collections.emptyMap(), false, WARN);
			if (!warned.isEmpty())
			{
				warnedAt = tick;
			}
		}
		// Last level drops at tick 430, so the reminder comes 50 ticks before.
		assertEquals(380, warnedAt);
		assertEquals(3, warned.size());
	}

	@Test
	public void divinePotionsUseTheirTimer()
	{
		Reminders r = new Reminders();
		r.onBoosts(0, Map.of("Ranged", 13), Map.of("Ranged", 500), false, WARN);
		assertTrue(r.onBoosts(1, Map.of("Ranged", 13), Map.of("Ranged", 60), false, WARN).isEmpty());
		assertEquals(List.of("Ranged"), r.onBoosts(2, Map.of("Ranged", 13), Map.of("Ranged", 50), false, WARN));
		// Only once.
		assertTrue(r.onBoosts(3, Map.of("Ranged", 13), Map.of("Ranged", 40), false, WARN).isEmpty());
	}

	@Test
	public void prayerRemindsOnceWhenItDrainsLow()
	{
		Reminders r = new Reminders();
		assertFalse(r.onPrayer(5, 10)); // logged in with low prayer: no nag
		assertFalse(r.onPrayer(30, 10));
		assertFalse(r.onPrayer(11, 10));
		assertTrue(r.onPrayer(10, 10));
		assertFalse(r.onPrayer(9, 10));
		assertFalse(r.onPrayer(40, 10)); // drank a prayer potion: armed again
		assertTrue(r.onPrayer(8, 10));
	}

	@Test
	public void cannonRemindsWhenRunningLow()
	{
		Reminders r = new Reminders();
		assertFalse(r.onCannonballs(30, 5, true));
		assertFalse(r.onCannonballs(6, 5, true));
		assertTrue(r.onCannonballs(5, 5, true));
		assertFalse(r.onCannonballs(4, 5, true));
		assertFalse(r.onCannonballs(30, 5, true));
		assertTrue(r.onCannonballs(0, 5, true));
	}
}
