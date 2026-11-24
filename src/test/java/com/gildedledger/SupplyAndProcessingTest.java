package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TaskRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.SkillingTracker;
import com.gildedledger.tracking.SupplyTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SupplyAndProcessingTest
{
	private static final int PRAYER_POTION_4 = 2434;
	private static final int SHARK = 385;
	private static final int BLOOD_RUNE = 565;
	private static final int BATTLESTAFF = 1391;
	private static final int BATTLESTAFF_NOTED = 1392;
	private static final int COINS = 995;
	private static final int VARROCK_TAB = 8007;

	private final FakeItems items = new FakeItems()
		.add(PRAYER_POTION_4, "Prayer potion(4)", 10_000)
		.add(SHARK, "Shark", 900)
		.stackable(BLOOD_RUNE, "Blood rune", 300)
		.add(BATTLESTAFF, "Battlestaff", 8_400)
		.noted(BATTLESTAFF_NOTED, BATTLESTAFF)
		.stackable(COINS, "Coins", 1)
		.add(VARROCK_TAB, "Varrock teleport", 1_000);

	@Test
	public void potionIsChargedPerDoseOnlyWhenTheDoseIsGone()
	{
		List<SupplyTracker.Use> uses = new ArrayList<>();
		SupplyTracker supplies = new SupplyTracker(items, uses::add);

		supplies.onConsumeClicked(PRAYER_POTION_4, SupplyTracker.Kind.POTION, 2, 10);
		supplies.onInventoryChanged(id -> 2, 10);
		assertTrue(uses.isEmpty());

		supplies.onInventoryChanged(id -> id == PRAYER_POTION_4 ? 1 : 0, 11);
		assertEquals(1, uses.size());
		assertEquals("Prayer potion", uses.get(0).lineName);
		assertEquals(2_500, uses.get(0).gp);
		assertEquals("doses", uses.get(0).unit);
	}

	@Test
	public void eatClickThatNeverEatsCostsNothing()
	{
		List<SupplyTracker.Use> uses = new ArrayList<>();
		SupplyTracker supplies = new SupplyTracker(items, uses::add);

		supplies.onConsumeClicked(SHARK, SupplyTracker.Kind.FOOD, 5, 10);
		supplies.onGameTick(20);
		supplies.onInventoryChanged(id -> 4, 21);
		assertTrue(uses.isEmpty());
	}

	@Test
	public void runesCountOnlyWhenASpellWasCast()
	{
		List<SupplyTracker.Use> uses = new ArrayList<>();
		SupplyTracker supplies = new SupplyTracker(items, uses::add);

		supplies.onRunesLost(BLOOD_RUNE, 100);
		supplies.onGameTick(10);
		assertTrue("banking runes is not a cast", uses.isEmpty());

		supplies.onRunesLost(BLOOD_RUNE, 2);
		supplies.onMagicXp();
		supplies.onGameTick(11);
		assertEquals(1, uses.size());
		assertEquals(600, uses.get(0).gp);
	}

	@Test
	public void teleportSpellRunesAndTabletsAreMarkedAsTeleports()
	{
		List<SupplyTracker.Use> uses = new ArrayList<>();
		SupplyTracker supplies = new SupplyTracker(items, uses::add);

		supplies.onTeleportCast("Varrock Teleport", 10);
		supplies.onRunesLost(BLOOD_RUNE, 1);
		supplies.onGameTick(10);
		assertEquals("Varrock Teleport", uses.get(0).teleport);
		assertTrue(uses.get(0).countsAsUse);

		supplies.onConsumeClicked(VARROCK_TAB, SupplyTracker.Kind.TELEPORT, 3, 20);
		supplies.onInventoryChanged(id -> 2, 21);
		assertEquals("Varrock teleport", uses.get(1).teleport);

		// A combat spell afterwards is not a teleport.
		supplies.onCastClicked(30);
		supplies.onRunesLost(BLOOD_RUNE, 2);
		supplies.onGameTick(30);
		assertNull(uses.get(2).teleport);
	}

	@Test
	public void oldSavesMoveIntoTheNewCategories()
	{
		TrackerData data = new TrackerData();
		data.source("supplies:unassigned", "Unassigned supplies", SourceCategory.COMBAT, 1).addSupply("385", SHARK, "Shark", null, 1, 900, 1);
		data.source("process:Fletching:Yew longbow", "Fletching: Yew longbow", SourceCategory.PROCESSING, 1).addLoot(855, 3, 1);
		data.normalize();

		assertEquals(SourceCategory.GENERAL_SUPPLIES, data.sources.get(TrackerData.GENERAL_SUPPLIES_KEY).category);
		assertEquals(900, data.sources.get(TrackerData.GENERAL_SUPPLIES_KEY).supplyCost());
		data.sources.clear();
		data.source("process:PROCESSING:Oak logs", "Oak logs", SourceCategory.PROCESSING, 1).addXp("Firemaking", 60, 1);
		data.source("teleport:Varrock teleport", "Varrock teleport", SourceCategory.TELEPORTS, 1).addSupply("8007", VARROCK_TAB, "Varrock teleport", null, 1, 1_000, 1);
		data.source("teleport:Teleport to house", "Teleport to house", SourceCategory.TELEPORTS, 1).addSupply("8013", 8013, "Teleport to house", null, 1, 500, 1);
		data.source("process:Fletching:Yew longbow", "Fletching: Yew longbow", SourceCategory.PROCESSING, 1).addLoot(855, 3, 1);
		data.normalize();
		assertEquals(SourceCategory.FIREMAKING, data.sources.get("item:FIREMAKING:Oak logs").category);
		assertEquals(1_500, data.sources.get(TrackerData.TELEPORTS_KEY).supplyCost());

		SourceRecord bow = data.sources.get("item:FLETCHING:Yew longbow");
		assertEquals(SourceCategory.FLETCHING, bow.category);
		assertEquals("Yew longbow", bow.name);
	}

	@Test
	public void buyingFromAnNpcIsNotTracked()
	{
		Ledger ledger = new Ledger(() -> 1L);
		SkillingTracker skilling = new SkillingTracker(ledger, items);

		Map<Integer, Integer> before = new HashMap<>();
		before.put(COINS, 1_000_000);
		Map<Integer, Integer> after = new HashMap<>();
		after.put(COINS, 1_000_000 - 840_000);
		after.put(BATTLESTAFF_NOTED, 120);

		// Talk-to Zaff and buy battlestaves: coins in, items out. That's a purchase, not processing.
		skilling.onOtherTargetClicked("Zaff", null, 10);
		skilling.onInventoryChanged(InventoryDiff.between(before, after));
		skilling.onGameTick(11, null);
		skilling.onOtherAction();
		assertTrue(ledger.data().sources.isEmpty());

		// Selling for coins isn't processing either.
		skilling.onOtherTargetClicked("Shop keeper", null, 20);
		skilling.onInventoryChanged(InventoryDiff.between(after, before));
		skilling.onGameTick(21, null);
		skilling.onOtherAction();
		assertTrue(ledger.data().sources.isEmpty());
	}

	@Test
	public void oldPurchaseEntriesAreRemoved()
	{
		TrackerData data = new TrackerData();
		data.source("purchase:zaff-battlestaves", "Zaff: Battlestaves", SourceCategory.PURCHASE, 1).addLoot(BATTLESTAFF, 15, 1);
		data.source("process:PROCESSING:Battlestaff", "Battlestaff", SourceCategory.PROCESSING, 1).addLoot(BATTLESTAFF, 15, 1);
		data.normalize();
		assertTrue(data.sources.isEmpty());
	}

	@Test
	public void oldFarmingEntriesAreGroupedByCrop()
	{
		TrackerData data = new TrackerData();
		data.source("farming:Herb:Toadflax", "Herb: Toadflax", SourceCategory.FARMING, 1).addLoot(2998, 6, 1);
		data.source("item:FARMING:Grimy toadflax", "Grimy toadflax", SourceCategory.FARMING, 1).addLoot(2998, 4, 1);
		data.source("item:FARMING:Toadflax seed", "Toadflax seed", SourceCategory.FARMING, 1).addSupply("5296", 5296, "Toadflax seed", null, 1, 500, 1);
		data.source("farming:Tree:Yew", "Tree: Yew", SourceCategory.FARMING, 1).addXp("Farming", 7_000, 1);
		data.source("item:FARMING:Yew sapling", "Yew sapling", SourceCategory.FARMING, 1).addSupply("5315", 5315, "Yew sapling", null, 1, 20_000, 1);
		data.normalize();

		SourceRecord toadflax = data.sources.get("farming:Toadflax");
		assertEquals(10L, (long) toadflax.loot.get(2998));
		assertEquals(500, toadflax.supplyCost());
		SourceRecord yew = data.sources.get("farming:Yew tree");
		assertEquals("Yew tree", yew.name);
		assertEquals(20_000, yew.supplyCost());
		assertEquals(2, data.sources.size());
	}

	@Test
	public void ledgerSurvivesJsonRoundTrip()
	{
		Ledger ledger = new Ledger(() -> 42L);
		ledger.addLoot("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 4151, 1);
		ledger.addSupply("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT,
			"potion:Prayer potion", PRAYER_POTION_4, "Prayer potion", "doses", 3, 7_500);
		ledger.addXp("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, "Slayer", 150);
		ledger.data().currentTask = new TaskRecord("Abyssal demons", "Catacombs", 180, 120, 42L);

		Gson gson = new Gson();
		TrackerData copy = gson.fromJson(gson.toJson(ledger.data()), TrackerData.class).normalize();

		SourceRecord demon = copy.sources.get("npc:Abyssal demon");
		assertEquals(SourceCategory.COMBAT, demon.category);
		assertEquals(1L, (long) demon.loot.get(4151));
		assertEquals(7_500, demon.supplyCost());
		assertEquals(150, demon.totalXp());
		assertEquals("Abyssal demons", copy.currentTask.taskName);
	}

	@Test
	public void finishedTasksMoveToHistory()
	{
		TrackerData data = new TrackerData();
		data.currentTask = new TaskRecord("Gargoyles", null, 150, 150, 1L);
		data.currentTask.kills = 150;
		data.archiveCurrentTask(99L);

		assertNull(data.currentTask);
		assertEquals(1, data.taskHistory.size());
		assertEquals(99L, data.taskHistory.get(0).finishedMillis);
	}
}
