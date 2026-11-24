package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.tracking.DropTracker;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.SkillRules;
import com.gildedledger.tracking.SkillingTracker;
import com.gildedledger.tracking.TileKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SkillingTrackerTest
{
	private static final int YEW_LOGS = 1515;
	private static final int RAW_TROUT = 335;
	private static final int FEATHER = 314;
	private static final int GRIMY_RANARR = 207;
	private static final int RANARR_WEED = 257;
	private static final int RUNE_PLATEBODY = 1127;
	private static final int COINS = 995;
	private static final int ESSENCE = 7936;
	private static final int NATURE_RUNE = 561;
	private static final int TOOTH_HALF = 985;
	private static final int LOOP_HALF = 987;
	private static final int CRYSTAL_KEY = 989;

	private static final TileKey YEW = new TileKey(3086, 3481, 0);

	private FakeItems items;
	private Ledger ledger;
	private SkillingTracker skilling;

	@Before
	public void setUp()
	{
		items = new FakeItems()
			.add(YEW_LOGS, "Yew logs", 250)
			.add(RAW_TROUT, "Raw trout", 60)
			.stackable(FEATHER, "Feather", 3)
			.add(GRIMY_RANARR, "Grimy ranarr weed", 7_000)
			.add(RANARR_WEED, "Ranarr weed", 7_600)
			.add(RUNE_PLATEBODY, "Rune platebody", 38_000)
			.stackable(COINS, "Coins", 1)
			.add(ESSENCE, "Pure essence", 2)
			.stackable(NATURE_RUNE, "Nature rune", 90)
			.add(TOOTH_HALF, "Tooth half of key", 1_000)
			.add(LOOP_HALF, "Loop half of key", 1_000)
			.add(CRYSTAL_KEY, "Crystal key", 2_200);
		ledger = new Ledger(() -> 1L);
		skilling = new SkillingTracker(ledger, items);
	}

	private static InventoryDiff change(int... idAndDelta)
	{
		Map<Integer, Integer> before = new HashMap<>();
		Map<Integer, Integer> after = new HashMap<>();
		for (int i = 0; i < idAndDelta.length; i += 2)
		{
			before.put(idAndDelta[i], 1000);
			after.put(idAndDelta[i], 1000 + idAndDelta[i + 1]);
		}
		return InventoryDiff.between(before, after);
	}

	@Test
	public void woodcuttingIsTrackedPerTree()
	{
		skilling.onGatherClicked(SourceCategory.WOODCUTTING, "Yew tree", YEW, 1);
		for (int tick = 2; tick <= 4; tick++)
		{
			skilling.onInventoryChanged(change(YEW_LOGS, 1));
			skilling.onXp("Woodcutting", 175);
			skilling.onGameTick(tick, YEW);
		}

		SourceRecord yew = ledger.data().sources.get("item:WOODCUTTING:Yew logs");
		assertEquals(SourceCategory.WOODCUTTING, yew.category);
		assertEquals("Yew logs", yew.name);
		assertEquals(3L, (long) yew.loot.get(YEW_LOGS));
		assertEquals(3, yew.count);
		assertEquals(525L, (long) yew.xp.get("Woodcutting"));
	}

	@Test
	public void flyFishingChargesFeathers()
	{
		skilling.onGatherClicked(SourceCategory.FISHING, SkillRules.fishingMethod("Lure"), YEW, 1);
		skilling.onInventoryChanged(change(RAW_TROUT, 1, FEATHER, -1));
		skilling.onGameTick(2, YEW);

		SourceRecord fly = ledger.data().sources.get("item:FISHING:Trout & salmon");
		assertEquals(1L, (long) fly.loot.get(RAW_TROUT));
		assertEquals(3, fly.supplyCost());
	}

	@Test
	public void cleaningHerbsCostsTheGrimyHerbAndOutputsTheCleanOne()
	{
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(GRIMY_RANARR, -1, RANARR_WEED, 1));
		skilling.onXp("Herblore", 7);
		skilling.onGameTick(2, null);

		SourceRecord clean = ledger.data().sources.get("item:HERBLORE:Ranarr weed");
		assertEquals(SourceCategory.HERBLORE, clean.category);
		assertEquals("Ranarr weed", clean.name);
		assertEquals(1L, (long) clean.loot.get(RANARR_WEED));
		assertEquals(7_000, clean.supplyCost());
		assertEquals(1, clean.count);
	}

	@Test
	public void alchingIsNamedAfterTheItemAlched()
	{
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(RUNE_PLATEBODY, -1, COINS, 39_000));
		skilling.onXp("Magic", 65);
		skilling.onGameTick(2, null);

		SourceRecord alch = ledger.data().sources.get("item:MAGIC:Rune platebody");
		// (No spell name given here, so it falls back to the item.)
		assertEquals(SourceCategory.MAGIC, alch.category);
		assertEquals(39_000L, (long) alch.loot.get(COINS));
		assertEquals(38_000, alch.supplyCost());
	}

	@Test
	public void actionsGoToTheSkillThatGaveTheMostXp()
	{
		// Drift net fishing gives Fishing and Hunter XP; Hunter gave more here.
		skilling.onGatherClicked(SourceCategory.FISHING, "Drift net", YEW, 1);
		skilling.onInventoryChanged(change(RAW_TROUT, 1));
		skilling.onXp("Fishing", 77);
		skilling.onXp("Hunter", 99);
		skilling.onGameTick(2, YEW);
		SourceRecord drift = ledger.data().sources.get("item:HUNTER:Raw trout");
		assertEquals(SourceCategory.HUNTER, drift.category);
		assertEquals(77L, (long) drift.xp.get("Fishing"));

		// Runecrafting: essence in, runes out, filed by altar.
		skilling.onProcessClicked(3);
		skilling.onInventoryChanged(change(ESSENCE, -28, NATURE_RUNE, 28));
		skilling.onXp("Runecraft", 252);
		skilling.onGameTick(4, null);
		SourceRecord nature = ledger.data().sources.get("item:RUNECRAFTING:Nature altar");
		assertEquals(SourceCategory.RUNECRAFTING, nature.category);
		assertEquals(28L, (long) nature.loot.get(NATURE_RUNE));
		assertEquals(28 * 2, nature.supplyCost());
	}

	@Test
	public void mudRunesWithPouchEarthRunesAreOneWaterAltarCraft()
	{
		int earth = 557;
		int mud = 4698;
		items.stackable(earth, "Earth rune", 5).stackable(mud, "Mud rune", 30);
		skilling.onProcessClicked(1);
		// The craft: essence from the inventory, earth runes from the rune pouch (a second
		// diff the same tick), mud runes into the pouch or inventory, a binding charge.
		skilling.onInventoryChanged(change(ESSENCE, -27, mud, 77));
		skilling.onInventoryChanged(change(earth, -27));
		skilling.onSupplyUsed(com.gildedledger.tracking.SupplyTracker.charge(5521, "binding-charge",
			"Binding necklace", "charges", 1, 60));
		skilling.onXp("Runecraft", 256);
		skilling.onGameTick(2, null);

		SourceRecord water = ledger.data().sources.get("item:RUNECRAFTING:Water altar");
		assertEquals(77L, (long) water.loot.get(mud));
		assertEquals(27L, water.supplies.get(String.valueOf(earth)).quantity);
		assertEquals(27L, water.supplies.get(String.valueOf(ESSENCE)).quantity);
		assertEquals(1L, water.supplies.get("binding-charge").quantity);
		assertEquals(null, ledger.data().sources.get(SkillingTracker.otherKey(SourceCategory.RUNECRAFTING)));
	}

	@Test
	public void unclearCraftGoesToTheAltarJustUsed()
	{
		int earth = 557;
		int mud = 4698;
		items.stackable(earth, "Earth rune", 5).stackable(mud, "Mud rune", 30);
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(ESSENCE, -27, earth, -27, mud, 27));
		skilling.onXp("Runecraft", 256);
		skilling.onGameTick(2, null);
		// The next craft's elemental runes weren't seen: still the Water altar, not Other.
		skilling.onInventoryChanged(change(ESSENCE, -20, mud, 20));
		skilling.onXp("Runecraft", 190);
		skilling.onGameTick(20, null);

		SourceRecord water = ledger.data().sources.get("item:RUNECRAFTING:Water altar");
		assertEquals(47L, (long) water.loot.get(mud));
		assertEquals(null, ledger.data().sources.get(SkillingTracker.otherKey(SourceCategory.RUNECRAFTING)));
	}

	@Test
	public void actionsWithoutXpAreProcessing()
	{
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(TOOTH_HALF, -1, LOOP_HALF, -1, CRYSTAL_KEY, 1));
		for (int tick = 2; tick <= 14; tick++)
		{
			skilling.onGameTick(tick, null);
		}

		SourceRecord key = ledger.data().sources.get("item:PROCESSING:Crystal key");
		assertEquals(SourceCategory.PROCESSING, key.category);
		assertEquals(1L, (long) key.loot.get(CRYSTAL_KEY));
	}

	@Test
	public void xpLandingATickLaterStillPicksTheSkill()
	{
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(GRIMY_RANARR, -1, RANARR_WEED, 1));
		skilling.onGameTick(2, null);
		skilling.onXp("Herblore", 7);
		skilling.onGameTick(3, null);

		assertNull(ledger.data().sources.get("item:PROCESSING:Ranarr weed"));
		assertEquals(1L, (long) ledger.data().sources.get("item:HERBLORE:Ranarr weed").loot.get(RANARR_WEED));
	}

	@Test
	public void lightingALogWaitsForTheFiremakingXp()
	{
		// The log leaves on tick 2; the Firemaking XP only lands on tick 5.
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(YEW_LOGS, -1));
		skilling.onGameTick(2, null);
		skilling.onGameTick(3, null);
		skilling.onGameTick(4, null);
		skilling.onXp("Firemaking", 60);
		skilling.onGameTick(5, null);

		assertNull(ledger.data().sources.get("item:PROCESSING:Yew logs"));
		SourceRecord fire = ledger.data().sources.get("item:FIREMAKING:Yew logs");
		assertEquals(SourceCategory.FIREMAKING, fire.category);
		assertEquals(250, fire.supplyCost());
		assertEquals(60L, (long) fire.xp.get("Firemaking"));
		assertEquals(1, fire.count);
	}

	@Test
	public void anyOtherActionThatGivesXpIsTracked()
	{
		// Tending a Forester's campfire: logs go in, Firemaking XP comes out.
		skilling.onOtherTargetClicked("Forester's campfire", YEW, 1);
		skilling.onInventoryChanged(change(YEW_LOGS, -1));
		skilling.onXp("Firemaking", 300);
		skilling.onGameTick(2, YEW);

		SourceRecord fire = ledger.data().sources.get("item:FIREMAKING:Yew logs");
		assertEquals(SourceCategory.FIREMAKING, fire.category);
		assertEquals(250, fire.supplyCost());
		assertEquals(300L, (long) fire.xp.get("Firemaking"));

		// Clicking something that never gives XP (a door, a shop) records nothing.
		skilling.onOtherTargetClicked("Large door", YEW, 3);
		skilling.onInventoryChanged(change(YEW_LOGS, -5));
		skilling.onGameTick(4, YEW);
		skilling.onGameTick(5, YEW);
		assertNull(ledger.data().sources.get("item:FIREMAKING:Large door"));
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void sawmillPlanksAreProcessingEvenWithoutXp()
	{
		// "Buy-plank" on the sawmill operator: logs and coins in, planks out, no XP.
		skilling.onOtherTargetClicked("Sawmill operator", YEW, 1);
		skilling.onInventoryChanged(change(YEW_LOGS, -10, COINS, -15_000, RANARR_WEED, 10));
		for (int tick = 2; tick <= 14; tick++)
		{
			skilling.onGameTick(tick, YEW);
		}
		SourceRecord planks = ledger.data().sources.get("item:PROCESSING:Ranarr weed");
		assertEquals(SourceCategory.PROCESSING, planks.category);
		assertEquals(10L, (long) planks.loot.get(RANARR_WEED));
		assertEquals(10 * 250 + 15_000, planks.supplyCost());
	}

	@Test
	public void buryingBonesIsPrayer()
	{
		assertTrue(SkillRules.isProcessingItemOption("Bury"));
		assertTrue(SkillRules.isProcessingItemOption("Scatter"));
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(YEW_LOGS, -1));
		skilling.onXp("Prayer", 15);
		skilling.onGameTick(2, null);
		assertEquals(SourceCategory.PRAYER, ledger.data().sources.get("item:PRAYER:Yew logs").category);
	}

	@Test
	public void firemakingIsByLogHoweverItWasBurned()
	{
		// Tinderbox on a log.
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(YEW_LOGS, -1));
		skilling.onXp("Firemaking", 300);
		skilling.onGameTick(2, null);
		// Tending a Forester's campfire with the same logs.
		skilling.onOtherTargetClicked("Forester's campfire", YEW, 3);
		skilling.onInventoryChanged(change(YEW_LOGS, -1));
		skilling.onXp("Firemaking", 300);
		skilling.onGameTick(4, YEW);

		SourceRecord yew = ledger.data().sources.get("item:FIREMAKING:Yew logs");
		assertEquals(2, yew.count);
		assertEquals(600L, (long) yew.xp.get("Firemaking"));
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void thievingIsByNpc()
	{
		skilling.onGatherClicked(SourceCategory.THIEVING, "Master Farmer", YEW, 1);
		skilling.onInventoryChanged(change(GRIMY_RANARR, 1));
		skilling.onXp("Thieving", 43);
		skilling.onGameTick(2, YEW);
		skilling.onInventoryChanged(change(RAW_TROUT, 1));
		skilling.onXp("Thieving", 43);
		skilling.onGameTick(3, YEW);

		SourceRecord farmer = ledger.data().sources.get("target:THIEVING:Master Farmer");
		assertEquals(2, farmer.count);
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void magicIsBySpell()
	{
		skilling.onProcessClicked(1, "High Level Alchemy");
		skilling.onInventoryChanged(change(RUNE_PLATEBODY, -1, COINS, 39_000));
		skilling.onXp("Magic", 65);
		skilling.onGameTick(2, null);
		skilling.onProcessClicked(3, "High Level Alchemy");
		skilling.onInventoryChanged(change(GRIMY_RANARR, -1, COINS, 25));
		skilling.onXp("Magic", 65);
		skilling.onGameTick(4, null);

		SourceRecord alching = ledger.data().sources.get("spell:MAGIC:High Level Alchemy");
		assertEquals(2, alching.count);
		assertEquals(39_025L, (long) alching.loot.get(COINS));

		// Superglass Make gives Crafting XP too, but it's a spell, so it's Magic.
		skilling.onProcessClicked(5, "Superglass Make");
		skilling.onInventoryChanged(change(ESSENCE, -18, RANARR_WEED, 18));
		skilling.onXp("Magic", 78);
		skilling.onXp("Crafting", 180);
		skilling.onGameTick(6, null);
		assertEquals(SourceCategory.MAGIC, ledger.data().sources.get("spell:MAGIC:Superglass Make").category);
	}

	@Test
	public void teleportRules()
	{
		assertTrue(SkillRules.isTeleportSpell("Grand Exchange", "Varrock Teleport"));
		assertEquals("Varrock Teleport (Grand Exchange)", SkillRules.teleportSpellName("Grand Exchange", "Varrock Teleport"));
		assertEquals("Watchtower Teleport", SkillRules.teleportSpellName("Cast", "Watchtower Teleport"));
		assertFalse(SkillRules.isTeleportSpell("Cast", "High Level Alchemy"));
		assertTrue(com.gildedledger.tracking.ItemRules.isTeleportConsumable("Teleport to house", "Outside"));
		assertTrue(com.gildedledger.tracking.ItemRules.isTeleportConsumable("Lumberyard teleport", "Teleport"));
		assertFalse(com.gildedledger.tracking.ItemRules.isTeleportConsumable("Teleport to house", "Drop"));
		assertTrue(SkillRules.couldGiveSkillingXp("Tend-to"));
		assertFalse(SkillRules.couldGiveSkillingXp("Open"));
	}

	@Test
	public void otherClicksAndWalkingAwayStopTheSession()
	{
		skilling.onGatherClicked(SourceCategory.WOODCUTTING, "Yew tree", YEW, 1);
		skilling.onOtherAction();
		skilling.onInventoryChanged(change(YEW_LOGS, 28));
		skilling.onGameTick(2, YEW);
		assertNull(ledger.data().sources.get("item:WOODCUTTING:Yew logs"));

		skilling.onGatherClicked(SourceCategory.WOODCUTTING, "Yew tree", YEW, 3);
		skilling.onGameTick(4, new TileKey(3200, 3200, 0));
		assertFalse(skilling.isActive());
	}

	@Test
	public void droppedItemsLeaveTheLootOfTheSourceThatGaveThem()
	{
		skilling.onGatherClicked(SourceCategory.FISHING, "Fly fishing", YEW, 1);
		skilling.onInventoryChanged(change(RAW_TROUT, 5));
		skilling.onGameTick(2, YEW);

		DropTracker drops = new DropTracker();
		drops.onDropClicked(RAW_TROUT, 3);
		InventoryDiff diff = change(RAW_TROUT, -3);
		Map<Integer, Integer> dropped = drops.dropsIn(diff, 4);
		dropped.forEach((id, amount) ->
			assertEquals(3L, (long) ledger.removeDroppedLoot(id, -amount).get("item:FISHING:Trout & salmon")));

		assertTrue(diff.without(dropped).isEmpty());
		assertEquals(2L, (long) ledger.data().sources.get("item:FISHING:Trout & salmon").loot.get(RAW_TROUT));

		// Dropping items the tracker never logged (picked up off the floor) changes nothing.
		assertTrue(ledger.removeDroppedLoot(YEW_LOGS, 5).isEmpty());

		// Dropping more than is left just empties the loot.
		ledger.removeDroppedLoot(RAW_TROUT, 10);
		assertFalse(ledger.data().sources.get("item:FISHING:Trout & salmon").loot.containsKey(RAW_TROUT));
	}

	@Test
	public void skillRulesRecogniseActions()
	{
		assertEquals(SourceCategory.WOODCUTTING, SkillRules.gatherFromObject("Chop down"));
		assertEquals(SourceCategory.MINING, SkillRules.gatherFromObject("Mine"));
		assertEquals(SourceCategory.THIEVING, SkillRules.gatherFromObject("Steal-from"));
		assertEquals(SourceCategory.FISHING, SkillRules.gatherFromNpc("Harpoon"));
		assertEquals(SourceCategory.THIEVING, SkillRules.gatherFromNpc("Pickpocket"));
		assertEquals(SourceCategory.HUNTER, SkillRules.gatherFromNpc("Catch"));
		assertNull(SkillRules.gatherFromNpc("Talk-to"));
		assertTrue(SkillRules.isProcessingObjectOption("Cook"));
		assertTrue(SkillRules.isProcessingItemOption("Clean"));
	}
}
