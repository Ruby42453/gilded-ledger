package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.tracking.FarmingTracker;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.TileKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class FarmingTrackerTest
{
	private static final int RANARR_SEED = 5295;
	private static final int SNAPDRAGON_SEED = 5300;
	private static final int GRIMY_RANARR = 207;
	private static final int GRIMY_RANARR_NOTED = 208;
	private static final int ULTRACOMPOST = 21483;
	private static final int BUCKET = 1925;
	private static final int MAGIC_SAPLING = 5316;
	private static final int COCONUT = 5974;
	private static final int MAGIC_LOGS = 1513;

	private static final TileKey HERB_PATCH = new TileKey(2813, 3463, 0);
	private static final TileKey TREE_PATCH = new TileKey(3228, 3459, 0);
	private static final TileKey OTHER_HERB_PATCH = new TileKey(3058, 3311, 0);

	private Ledger ledger;
	private FarmingTracker farming;

	@Before
	public void setUp()
	{
		FakeItems items = new FakeItems()
			.add(RANARR_SEED, "Ranarr seed", 40_000)
			.add(SNAPDRAGON_SEED, "Snapdragon seed", 60_000)
			.add(GRIMY_RANARR, "Grimy ranarr weed", 7_000)
			.noted(GRIMY_RANARR_NOTED, GRIMY_RANARR)
			.add(ULTRACOMPOST, "Ultracompost", 1_000)
			.add(BUCKET, "Bucket", 5)
			.add(MAGIC_SAPLING, "Magic sapling", 100_000)
			.add(COCONUT, "Coconut", 900)
			.add(MAGIC_LOGS, "Magic logs", 1_100);
		ledger = new Ledger(() -> 1_000L);
		Map<String, Integer> compost = new HashMap<>();
		compost.put("Ultracompost", ULTRACOMPOST);
		farming = new FarmingTracker(ledger, items, compost);
	}

	private static InventoryDiff change(int... idAndDelta)
	{
		Map<Integer, Integer> before = new HashMap<>();
		Map<Integer, Integer> after = new HashMap<>();
		for (int i = 0; i < idAndDelta.length; i += 2)
		{
			before.put(idAndDelta[i], 100);
			after.put(idAndDelta[i], 100 + idAndDelta[i + 1]);
		}
		return InventoryDiff.between(before, after);
	}

	private SourceRecord source(String key)
	{
		return ledger.data().sources.get(key);
	}

	@Test
	public void herbRunBooksHarvestToOldCropAndCostsToNewCrop()
	{
		ledger.data().patchCrops.put(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb"), "Ranarr");

		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(GRIMY_RANARR, 3));
		farming.onXp("Farming", 82);
		farming.onGameTick(2, HERB_PATCH);
		farming.onInventoryChanged(change(GRIMY_RANARR, 2));
		farming.onXp("Farming", 55);
		farming.onGameTick(3, HERB_PATCH);

		// Compost first, then plant something new.
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 5);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onChatMessage("You treat the herb patch with ultracompost.", HERB_PATCH, 1);
		farming.onXp("Farming", 36);
		farming.onGameTick(5, HERB_PATCH);
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 6);
		farming.onInventoryChanged(change(SNAPDRAGON_SEED, -1));
		farming.onXp("Farming", 87);
		farming.onGameTick(6, HERB_PATCH);

		SourceRecord ranarr = source("farming:Ranarr");
		assertEquals(5L, (long) ranarr.loot.get(GRIMY_RANARR));
		assertFalse(ranarr.loot.containsKey(BUCKET));
		assertEquals(1, ranarr.count);
		assertEquals(0, ranarr.supplyCost());

		SourceRecord snapdragon = source("farming:Snapdragon");
		assertEquals("Snapdragon", snapdragon.name);
		assertEquals(61_000, snapdragon.supplyCost());
		assertEquals(0, snapdragon.count);
		// Composting XP goes with the compost to the new crop.
		assertEquals(87L + 36, (long) snapdragon.xp.get("Farming"));
		assertEquals("Snapdragon", ledger.data().patchCrops.get(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb")));
	}

	@Test
	public void notingHerbsAtTheLeprechaunIsNotLoot()
	{
		ledger.data().patchCrops.put(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb"), "Ranarr");
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(GRIMY_RANARR, 6));
		farming.onGameTick(2, HERB_PATCH);

		farming.onOtherAction();
		farming.onInventoryChanged(change(GRIMY_RANARR, -6, GRIMY_RANARR_NOTED, 6));
		farming.onGameTick(3, HERB_PATCH);

		SourceRecord ranarr = source("farming:Ranarr");
		assertEquals(1, ranarr.loot.size());
		assertEquals(6L, (long) ranarr.loot.get(GRIMY_RANARR));
	}

	@Test
	public void noteConversionsAreNotChanges()
	{
		FakeItems items = new FakeItems().add(GRIMY_RANARR, "Grimy ranarr weed", 7_000).noted(GRIMY_RANARR_NOTED, GRIMY_RANARR);
		InventoryDiff noting = change(GRIMY_RANARR, -6, GRIMY_RANARR_NOTED, 6).withoutNoteConversions(items);
		assertTrue(noting.isEmpty());

		InventoryDiff partly = change(GRIMY_RANARR, -6, GRIMY_RANARR_NOTED, 4).withoutNoteConversions(items);
		assertEquals(-2, partly.change(GRIMY_RANARR));
		assertEquals(0, partly.change(GRIMY_RANARR_NOTED));
		assertTrue(partly.hasNoGains());
	}

	@Test
	public void seedAndCompostAreBothChargedInEitherOrder()
	{
		// Compost first, then the seed.
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onGameTick(2, HERB_PATCH);
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 3);
		farming.onInventoryChanged(change(RANARR_SEED, -1));
		farming.onGameTick(4, HERB_PATCH);
		assertEquals(41_000, source("farming:Ranarr").supplyCost());

		// Next patch: the seed first, then the compost.
		farming.onPatchClicked(OTHER_HERB_PATCH, "Herb", null, 10);
		farming.onInventoryChanged(change(SNAPDRAGON_SEED, -1));
		farming.onGameTick(11, OTHER_HERB_PATCH);
		farming.onPatchClicked(OTHER_HERB_PATCH, "Herb", null, 12);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onGameTick(13, OTHER_HERB_PATCH);
		farming.onGameTick(14, new TileKey(3000, 3000, 0));
		assertEquals(61_000, source("farming:Snapdragon").supplyCost());
	}

	@Test
	public void gameMessagesTrackPlantingEvenWithoutAPatchClick()
	{
		// The patch click wasn't recognised; the game's messages still open the session.
		farming.onChatMessage("You treat the herb patch with ultracompost.", HERB_PATCH, 1);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onGameTick(1, HERB_PATCH);
		farming.onChatMessage("You plant a ranarr seed in the herb patch.", HERB_PATCH, 2);
		farming.onInventoryChanged(change(RANARR_SEED, -1));
		farming.onGameTick(2, HERB_PATCH);

		SourceRecord ranarr = source("farming:Ranarr");
		assertEquals(41_000, ranarr.supplyCost());
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void cropsAreGroupedAcrossPatches()
	{
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(RANARR_SEED, -1));
		farming.onGameTick(2, HERB_PATCH);
		farming.onPatchClicked(OTHER_HERB_PATCH, "Herb", null, 3);
		farming.onInventoryChanged(change(RANARR_SEED, -1));
		farming.onGameTick(4, OTHER_HERB_PATCH);

		assertEquals(80_000, source("farming:Ranarr").supplyCost());
		assertEquals(1, ledger.data().sources.size());
	}

	@Test
	public void compostIsChargedStraightAwayAndFollowsANewSeed()
	{
		ledger.data().patchCrops.put(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb"), "Ranarr");
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onGameTick(2, HERB_PATCH);
		// Charged at once, to the crop currently in the patch.
		assertEquals(1_000, source("farming:Ranarr").supplyCost());

		farming.onPatchClicked(HERB_PATCH, "Herb", null, 3);
		farming.onInventoryChanged(change(SNAPDRAGON_SEED, -1));
		farming.onGameTick(4, HERB_PATCH);
		// Planting something else moves that compost to the new crop.
		assertNull(source("farming:Ranarr"));
		assertEquals(61_000, source("farming:Snapdragon").supplyCost());
	}

	@Test
	public void unknownCropIsWorkedOutFromTheHarvest()
	{
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(GRIMY_RANARR, 5));
		farming.onXp("Farming", 135);
		farming.onGameTick(2, HERB_PATCH);

		assertNull(source("farming:Herb patch (unknown crop)"));
		assertEquals(5L, (long) source("farming:Ranarr").loot.get(GRIMY_RANARR));
		assertEquals("Ranarr", ledger.data().patchCrops.get(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb")));
	}

	@Test
	public void oldUnknownCropEntriesMergeIntoTheirCrop()
	{
		ledger.data().source("farming:Herb patch (unknown crop)", "Herb patch (unknown crop)", SourceCategory.FARMING, 1)
			.addLoot(GRIMY_RANARR, 5, 1);
		ledger.data().source("farming:Ranarr", "Ranarr", SourceCategory.FARMING, 1).addLoot(GRIMY_RANARR, 9, 1);
		farming.tidyUnknownCrops();

		assertNull(source("farming:Herb patch (unknown crop)"));
		assertEquals(14L, (long) source("farming:Ranarr").loot.get(GRIMY_RANARR));
	}

	@Test
	public void bottomlessBucketCompostIsCharged()
	{
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onChatMessage("You treat the herb patch with ultracompost.", HERB_PATCH, 1);
		farming.onGameTick(1, HERB_PATCH);
		farming.onInventoryChanged(change(RANARR_SEED, -1));
		farming.onGameTick(2, HERB_PATCH);

		assertEquals(41_000, source("farming:Ranarr").supplyCost());
	}

	@Test
	public void treeRunTracksSaplingPaymentCheckHealthAndLogs()
	{
		// Plant and pay the gardener.
		farming.onPatchClicked(TREE_PATCH, "Tree", null, 1);
		farming.onInventoryChanged(change(MAGIC_SAPLING, -1));
		farming.onXp("Farming", 145);
		farming.onGameTick(2, TREE_PATCH);
		farming.onOtherAction();
		farming.onGardenerPayment(change(COCONUT, -25), 10, new TileKey(3230, 3460, 0));
		farming.onGameTick(10, TREE_PATCH);

		SourceRecord magic = source("farming:Magic tree");
		assertEquals("Magic tree", magic.name);
		assertEquals(100_000 + 25 * 900, magic.supplyCost());
		assertEquals(0, magic.count);

		// Days later: check health and chop it down.
		farming.onPatchClicked(TREE_PATCH, "Tree", "Magic tree", 5000);
		farming.onXp("Farming", 13_913);
		farming.onGameTick(5001, TREE_PATCH);
		farming.onPatchClicked(TREE_PATCH, "Tree", "Magic tree", 5002);
		farming.onInventoryChanged(change(MAGIC_LOGS, 1));
		farming.onXp("Woodcutting", 250);
		farming.onGameTick(5005, TREE_PATCH);

		assertEquals(1, magic.count);
		assertEquals(13_913L + 145, (long) magic.xp.get("Farming"));
		assertEquals(250L, (long) magic.xp.get("Woodcutting"));
		assertEquals(1L, (long) magic.loot.get(MAGIC_LOGS));
	}

	@Test
	public void unknownCropFallsBackToPatchAppearanceOrUnknown()
	{
		farming.onPatchClicked(TREE_PATCH, "Tree", "Yew", 1);
		farming.onXp("Farming", 7_000);
		farming.onGameTick(2, TREE_PATCH);
		assertEquals(1, source("farming:Yew tree").count);

		farming.onPatchClicked(HERB_PATCH, "Herb", null, 3);
		farming.onInventoryChanged(change(GRIMY_RANARR, 4));
		farming.onGameTick(4, HERB_PATCH);
		// The harvest itself shows it was Ranarr.
		assertNull(source("farming:Herb patch (unknown crop)"));
		assertEquals(4L, (long) source("farming:Ranarr").loot.get(GRIMY_RANARR));
	}

	@Test
	public void compostBeforeASaplingFollowsTheTreeWithItsXp()
	{
		// Empty tree patch: compost first, so the crop isn't known yet.
		farming.onPatchClicked(TREE_PATCH, "Tree", null, 1);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onXp("Farming", 36);
		farming.onGameTick(2, TREE_PATCH);
		assertEquals(1_000, source("farming:Tree patch (unknown crop)").supplyCost());

		farming.onPatchClicked(TREE_PATCH, "Tree", null, 3);
		farming.onInventoryChanged(change(MAGIC_SAPLING, -1));
		farming.onXp("Farming", 145);
		farming.onGameTick(4, TREE_PATCH);

		assertNull(source("farming:Tree patch (unknown crop)"));
		SourceRecord magic = source("farming:Magic tree");
		assertEquals(101_000, magic.supplyCost());
		assertEquals(181L, (long) magic.xp.get("Farming"));
	}

	@Test
	public void compostXpMovesWithTheCompostToANewSeed()
	{
		ledger.data().patchCrops.put(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb"), "Ranarr");
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onInventoryChanged(change(ULTRACOMPOST, -1, BUCKET, 1));
		farming.onXp("Farming", 36);
		farming.onGameTick(2, HERB_PATCH);
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 3);
		farming.onInventoryChanged(change(SNAPDRAGON_SEED, -1));
		farming.onXp("Farming", 98);
		farming.onGameTick(4, HERB_PATCH);

		assertNull(source("farming:Ranarr"));
		assertEquals(134L, (long) source("farming:Snapdragon").xp.get("Farming"));
	}

	@Test
	public void cropNamesAreTheSameWhicheverWayTheyWereSeen()
	{
		assertEquals("Papaya tree", FarmingTracker.canonicalCrop("Papaya"));
		assertEquals("Papaya tree", FarmingTracker.canonicalCrop("Papaya tree"));
		assertEquals("Yew tree", FarmingTracker.canonicalCrop("yew"));
		assertEquals("Ranarr", FarmingTracker.canonicalCrop("Ranarr"));
		assertEquals("Papaya tree", FarmingTracker.cropName("Fruit Tree", "Papaya"));
		assertEquals("Papaya tree", FarmingTracker.cropName("Farming", "Papaya"));

		ledger.data().source("farming:Papaya", "Papaya", SourceCategory.FARMING, 1).addLoot(COCONUT, 3, 1);
		ledger.data().source("farming:Papaya tree", "Papaya tree", SourceCategory.FARMING, 1).addLoot(COCONUT, 2, 1);
		farming.tidyUnknownCrops();
		assertNull(source("farming:Papaya"));
		assertEquals(5L, (long) source("farming:Papaya tree").loot.get(COCONUT));
	}

	@Test
	public void walkingAwayEndsTheSession()
	{
		ledger.data().patchCrops.put(FarmingTracker.patchMemoryKey(HERB_PATCH, "Herb"), "Ranarr");
		farming.onPatchClicked(HERB_PATCH, "Herb", null, 1);
		farming.onGameTick(2, new TileKey(2900, 3500, 0));

		farming.onInventoryChanged(change(GRIMY_RANARR, 4));
		farming.onGameTick(3, new TileKey(2900, 3500, 0));
		assertNull(source("farming:Ranarr"));
	}
}
