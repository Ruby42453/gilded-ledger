package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.FarmingTracker;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.ItemRules;
import com.gildedledger.tracking.LegacyTidy;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.RecentSupplies;
import com.gildedledger.tracking.SkillingTracker;
import com.gildedledger.tracking.TileKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class LatestChangesTest
{
	private static final int MAGIC_LOGS = 1513;
	private static final int MAGIC_LONGBOW_U = 70;
	private static final int STONE_BOWL = 9000;
	private static final int SLAYER_RING_8 = 11866;
	private static final int HESPORI_SEED = 22875;
	private static final int GOUT_TUBER = 6311;
	private static final int GOUTWEED = 3261;
	private static final int DRAGONFRUIT_PIE = 22795;
	private static final TileKey HERE = new TileKey(1300, 3300, 0);

	private FakeItems items;
	private Ledger ledger;
	private SkillingTracker skilling;

	@Before
	public void setUp()
	{
		items = new FakeItems().add(MAGIC_LOGS, "Magic logs", 1_000).add(MAGIC_LONGBOW_U, "Magic longbow (u)", 1_200)
			.add(STONE_BOWL, "Stone bowl", 0).add(SLAYER_RING_8, "Slayer ring (8)", 1_000)
			.untradeable(HESPORI_SEED, "Hespori seed").untradeable(GOUT_TUBER, "Gout tuber")
			.untradeable(GOUTWEED, "Goutweed").add(DRAGONFRUIT_PIE, "Dragonfruit pie", 3_000);
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
	public void valeTotemBowsAreMadeAndOfferedInOneEntry()
	{
		skilling.onValeTotemClicked(1);
		// Fletch a bow from a log...
		skilling.onProcessClicked(2);
		skilling.onInventoryChanged(change(MAGIC_LOGS, -1, MAGIC_LONGBOW_U, 1));
		skilling.onXp("Fletching", 91);
		skilling.onGameTick(3, null);
		// ...then decorate the totem with it.
		skilling.onOtherTargetClicked("Totem", HERE, 4);
		skilling.onInventoryChanged(change(MAGIC_LONGBOW_U, -1));
		skilling.onXp("Fletching", 300);
		skilling.onGameTick(5, HERE);

		SourceRecord vale = ledger.data().sources.get("item:FLETCHING:Vale Totems");
		assertEquals(1L, vale.supplies.get(String.valueOf(MAGIC_LOGS)).quantity);
		assertFalse(vale.loot.containsKey(MAGIC_LONGBOW_U));
		assertNull(vale.supplies.get(String.valueOf(MAGIC_LONGBOW_U)));
		assertEquals(391L, (long) vale.xp.get("Fletching"));
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void leftoversAndUntrackedItems()
	{
		assertTrue(ItemRules.isLeftover("Dragonfruit pie", "Half a dragonfruit pie"));
		assertTrue(ItemRules.isLeftover("Half a dragonfruit pie", "Pie dish"));
		assertTrue(ItemRules.isLeftover("Anchovy pizza", "1/2 anchovy pizza"));
		assertTrue(ItemRules.isLeftover("Cake", "2/3 cake"));
		assertTrue(ItemRules.isLeftover("Prayer potion", "Vial"));
		assertFalse(ItemRules.isLeftover("Shark", "Raw shark"));
		assertTrue(ItemRules.isMinigameItem("Stone bowl"));
		assertTrue(ItemRules.isDegrading("Slayer ring (8)"));
		assertTrue(ItemRules.isBoss("Vorkath"));
		assertFalse(ItemRules.isBoss("Goblin"));
		assertTrue(ItemRules.isAshSanctifierXp(55));
		assertTrue(ItemRules.isAshSanctifierXp(42));
		assertFalse(ItemRules.isAshSanctifierXp(36));
	}

	@Test
	public void animaPlantsGoToOther()
	{
		assertEquals("Other", FarmingTracker.cropName("Anima", "Kronos"));
		assertEquals("Other", FarmingTracker.cropName(null, "Attas"));
		assertEquals("Ranarr", FarmingTracker.cropName("Herb", "Ranarr"));
	}

	@Test
	public void oldDataIsBroughtInLine()
	{
		TrackerData data = ledger.data();
		SourceRecord gotr = data.source("item:FISHING:Karambwan", "Karambwan", SourceCategory.FISHING, 1);
		gotr.addLoot(STONE_BOWL, 1, 1);
		gotr.addLoot(SLAYER_RING_8, 1, 1);
		gotr.addLoot(MAGIC_LOGS, 3, 1);
		data.source("farming:Hespori", "Hespori", SourceCategory.FARMING, 1)
			.addSupply(String.valueOf(HESPORI_SEED), HESPORI_SEED, "Hespori seed", null, 1, 0, 1);
		SourceRecord gout = data.source("farming:Goutweed", "Goutweed", SourceCategory.FARMING, 1);
		gout.addSupply(String.valueOf(GOUT_TUBER), GOUT_TUBER, "Gout tuber", null, 1, 0, 1);
		gout.addLoot(GOUTWEED, 3, 1);

		data.source("npc:Flower", "Flower", SourceCategory.COMBAT, 1).addXp("Magic", 500, 1);
		data.source("npc:Hespori", "Hespori", SourceCategory.COMBAT, 1).addXp("Magic", 1000, 1);

		LegacyTidy.tidy(ledger, items);
		LegacyTidy.tidy(ledger, items);

		assertNull(data.sources.get("npc:Flower"));
		assertEquals(1500L, (long) data.sources.get("npc:Hespori").xp.get("Magic"));

		assertEquals(1, data.sources.get("item:FISHING:Karambwan").loot.size());
		assertEquals(1L, data.sources.get("npc:Hespori").supplies.get(String.valueOf(HESPORI_SEED)).quantity);
		assertNull(data.sources.get("farming:Hespori"));
		assertTrue(data.untradeableCrops.contains("Goutweed"));
		assertFalse(data.untradeableCrops.contains("Karambwan"));
	}

	@Test
	public void boostEatenBeforeASkillJoinsIt()
	{
		RecentSupplies recent = new RecentSupplies(ledger);
		ledger.addSupply(TrackerData.GENERAL_SUPPLIES_KEY, TrackerData.GENERAL_SUPPLIES_NAME,
			SourceCategory.GENERAL_SUPPLIES, "22795", DRAGONFRUIT_PIE, "Dragonfruit pie", null, 1, 1_500);
		recent.addGeneral(new RecentSupplies.Booked(TrackerData.GENERAL_SUPPLIES_KEY,
			TrackerData.GENERAL_SUPPLIES_NAME, SourceCategory.GENERAL_SUPPLIES, false, "22795", DRAGONFRUIT_PIE,
			"Dragonfruit pie", null, 1, 1_500, 100, true));
		recent.moveGeneralTo("item:FLETCHING:Vale Totems", "Vale Totems", SourceCategory.FLETCHING, false, 50);
		assertEquals(1_500, ledger.data().sources.get("item:FLETCHING:Vale Totems").supplyCost());
		assertNull(ledger.data().sources.get(TrackerData.GENERAL_SUPPLIES_KEY));
	}

	@Test
	public void jewelleryCharges()
	{
		Object[] games = ItemRules.teleportJewellery("Games necklace(8)");
		assertEquals("Games necklace", games[0]);
		assertEquals(8, games[1]);
		assertEquals("Amulet of glory", ItemRules.teleportJewellery("Amulet of glory(4)")[0]);
		assertEquals(0, ItemRules.teleportJewellery("Amulet of glory")[1]);
		assertEquals("Ring of wealth", ItemRules.teleportJewellery("Ring of wealth (5)")[0]);
		assertEquals("Digsite pendant", ItemRules.teleportJewellery("Digsite pendant (3)")[0]);
		assertNull(ItemRules.teleportJewellery("Slayer ring (8)"));
		assertNull(ItemRules.teleportJewellery("Prayer potion(4)"));

		assertEquals("Sailors' amulet", ItemRules.rechargeableTeleportJewellery("Sailors' amulet"));
		assertNull(ItemRules.rechargeableTeleportJewellery("Sailors' amulet (inert)"));
		assertEquals("Ring of the elements", ItemRules.rechargeableTeleportJewellery("Ring of the elements"));
		// Destinations picked straight from the menu (worn options, submenus, Menu Entry Swapper).
		assertEquals("Ring of the elements", ItemRules.rechargeableJewelForDestination("Water Altar"));
		assertEquals("Ring of the elements", ItemRules.rechargeableJewelForDestination("Last Destination"));
		assertEquals("Sailors' amulet", ItemRules.rechargeableJewelForDestination("Port Roberts"));
		assertEquals("Sailors' amulet", ItemRules.rechargeableJewelForDestination("The Pandemonium"));
		assertNull(ItemRules.rechargeableJewelForDestination("Rub"));
		assertNull(ItemRules.rechargeableJewelForDestination("Wear"));
		assertTrue(ItemRules.isNonTeleportOption("Wear"));
		assertFalse(ItemRules.isNonTeleportOption("Last Destination"));

		assertEquals(1, ItemRules.enduranceChargesPerDose("Stamina potion(3)"));
		assertEquals(2, ItemRules.enduranceChargesPerDose("Extended stamina potion(1)"));
		assertEquals(1, ItemRules.enduranceChargesPerDose("Stamina mix(2)"));
		assertEquals(0, ItemRules.enduranceChargesPerDose("Super restore(4)"));

		assertTrue(ItemRules.isConsumableJewellery("Ring of recoil"));
		assertTrue(ItemRules.isConsumableJewellery("Bracelet of slaughter"));
		assertFalse(ItemRules.isConsumableJewellery("Ring of suffering"));
		assertTrue(com.gildedledger.tracking.SkillRules.isPouchShuffle("Rune pouch", "Open"));

		java.util.Map<Integer, String> names = java.util.Map.of(1, "Games necklace(8)", 2, "Games necklace(7)",
			3, "Ring of dueling(1)", 4, "Amulet of glory(1)", 5, "Amulet of glory", 6, "Ring of wealth (5)");
		java.util.Map<Integer, Integer> changes = new HashMap<>();
		changes.put(1, -1);
		changes.put(2, 1);
		changes.put(3, -1); // crumbled
		changes.put(4, -1);
		changes.put(5, 1);
		changes.put(6, -1); // just put away, not a charge
		java.util.List<int[]> used = ItemRules.jewelleryChargesUsed(changes, names::get);
		assertEquals(3, used.size());
		assertTrue(used.stream().anyMatch(u -> u[0] == 1 && u[1] == 2));
		assertTrue(used.stream().anyMatch(u -> u[0] == 3 && u[1] == -1));
		assertTrue(used.stream().anyMatch(u -> u[0] == 4 && u[1] == 5));
	}
}
