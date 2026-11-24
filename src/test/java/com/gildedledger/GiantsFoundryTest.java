package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.tracking.GiantsFoundry;
import com.gildedledger.tracking.Ledger;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class GiantsFoundryTest
{
	private static final int MITHRIL_BAR = 2359;
	private static final int ADAMANT_PLATEBODY = 1123;
	private static final int LOBSTER = 379;

	private Ledger ledger;
	private GiantsFoundry foundry;

	@Before
	public void setUp()
	{
		ledger = new Ledger(() -> 0L);
		foundry = new GiantsFoundry(ledger);
	}

	private static String name(int id)
	{
		switch (id)
		{
			case MITHRIL_BAR:
				return "Mithril bar";
			case ADAMANT_PLATEBODY:
				return "Adamant platebody";
			default:
				return "Lobster";
		}
	}

	private SourceRecord record()
	{
		return ledger.data().sources.get(GiantsFoundry.KEY);
	}

	@Test
	public void metalsTheCrucibleTakes()
	{
		assertTrue(GiantsFoundry.isFoundryMetal("Mithril bar"));
		assertTrue(GiantsFoundry.isFoundryMetal("Runite bar"));
		assertTrue(GiantsFoundry.isFoundryMetal("Adamant platebody"));
		assertTrue(GiantsFoundry.isFoundryMetal("Rune 2h sword"));
		assertFalse(GiantsFoundry.isFoundryMetal("Gold bar"));
		assertFalse(GiantsFoundry.isFoundryMetal("Rune essence"));
		assertFalse(GiantsFoundry.isFoundryMetal("Lobster"));
	}

	@Test
	public void crucibleMetalIsSupplies()
	{
		foundry.onClick("Fill", "Crucible", 10);
		Map<Integer, Integer> changes = new HashMap<>();
		changes.put(MITHRIL_BAR, -14);
		changes.put(ADAMANT_PLATEBODY, -2);
		changes.put(LOBSTER, -1);
		Map<Integer, Integer> taken = foundry.takeCrucibleMetal(changes, 15, GiantsFoundryTest::name, id -> 100);

		assertEquals(2, taken.size());
		assertEquals(14L, record().supplies.get(String.valueOf(MITHRIL_BAR)).quantity);
		assertEquals(1400L, record().supplies.get(String.valueOf(MITHRIL_BAR)).gp);
		assertEquals(2L, record().supplies.get(String.valueOf(ADAMANT_PLATEBODY)).quantity);
	}

	@Test
	public void metalLostWithoutTheCrucibleIsNotTaken()
	{
		Map<Integer, Integer> changes = new HashMap<>();
		changes.put(MITHRIL_BAR, -14);
		assertTrue(foundry.takeCrucibleMetal(changes, 15, GiantsFoundryTest::name, id -> 100).isEmpty());
		foundry.onClick("Check", "Crucible", 20);
		assertTrue(foundry.takeCrucibleMetal(changes, 21, GiantsFoundryTest::name, id -> 100).isEmpty());
	}

	@Test
	public void kovacsPaymentIsLootAndASword()
	{
		foundry.onClick("Hand-in", "Kovac", 100);
		assertTrue(foundry.onChatMessage("Kovac deposits 12,345 coins into your bank as payment.", 102));
		assertTrue(foundry.onXp("Smithing", 6172, 102));
		assertFalse(foundry.onXp("Mining", 50, 102));

		assertEquals(12345L, (long) record().loot.get(995));
		assertEquals(1L, record().count);
		assertEquals(6172L, (long) record().xp.get("Smithing"));
	}

	@Test
	public void refiningXpIsTheFoundrys()
	{
		foundry.onClick("Use", "Trip hammer", 75);
		assertTrue(foundry.onXp("Smithing", 5, 83));
		foundry.onClick("Use", "Polishing wheel", 149);
		assertTrue(foundry.onXp("Smithing", 3, 157));
		assertEquals(8L, (long) record().xp.get("Smithing"));
		// Long after leaving the foundry, Smithing XP is someone else's.
		assertFalse(foundry.onXp("Smithing", 50, 2000));
	}

	@Test
	public void otherCoinMessagesAreIgnored()
	{
		assertFalse(foundry.onChatMessage("You sell the item for 500 coins.", 5));
		assertFalse(foundry.onXp("Smithing", 100, 5));
	}

	@Test
	public void ironmanCoinsInHandAreNotCountedTwice()
	{
		foundry.onClick("Hand-in", "Kovac", 100);
		foundry.onChatMessage("Kovac gives you 1,000 coins as payment.", 101);
		Map<Integer, Integer> changes = new HashMap<>();
		changes.put(995, 1000);
		assertEquals(1000, (int) foundry.paymentCoins(changes, 102).get(995));
	}
}
