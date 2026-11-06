package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.ItemRules;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.SkillGroups;
import com.gildedledger.tracking.SkillRules;
import com.gildedledger.tracking.SkillingTracker;
import com.gildedledger.tracking.TileKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SkillGroupsTest
{
	private static final int RAW_TROUT = 335;
	private static final int RAW_SALMON = 331;
	private static final int CASKET = 405;
	private static final int IRON_ORE = 440;
	private static final int UNCUT_RUBY = 1619;
	private static final int RED_FEATHER = 10088;
	private static final int BONES = 526;
	private static final int PRAYER_POT_3 = 139;
	private static final int PRAYER_POT_4 = 2434;
	private static final int ESSENCE = 7936;
	private static final int EARTH_RUNE = 557;
	private static final int LAVA_RUNE = 4699;
	private static final int RAW_KARAMBWAN = 3142;
	private static final int KARAMBWANJI = 3150;
	private static final TileKey HERE = new TileKey(3200, 3200, 0);

	private Ledger ledger;
	private SkillingTracker skilling;

	@Before
	public void setUp()
	{
		FakeItems items = new FakeItems()
			.add(RAW_TROUT, "Raw trout", 60).add(RAW_SALMON, "Raw salmon", 90).add(CASKET, "Casket", 500)
			.add(IRON_ORE, "Iron ore", 100).add(UNCUT_RUBY, "Uncut ruby", 900)
			.stackable(RED_FEATHER, "Red feather", 30).add(BONES, "Bones", 100)
			.add(PRAYER_POT_3, "Prayer potion(3)", 8_000).add(PRAYER_POT_4, "Prayer potion(4)", 10_000)
			.add(ESSENCE, "Pure essence", 2).stackable(EARTH_RUNE, "Earth rune", 4)
			.stackable(LAVA_RUNE, "Lava rune", 6)
			.add(RAW_KARAMBWAN, "Raw karambwan", 180).stackable(KARAMBWANJI, "Raw karambwanji", 5);
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
	public void runesAreGroupedByAltar()
	{
		assertEquals("Death altar", SkillGroups.altarFor("Death rune", null));
		assertEquals("Dark altar", SkillGroups.altarFor("Dark essence block", null));
		assertEquals("Fire altar", SkillGroups.altarFor("Lava rune", List.of("Earth rune")));
		assertEquals("Earth altar", SkillGroups.altarFor("Lava rune", List.of("Fire rune")));
		assertEquals(SkillGroups.OTHER, SkillGroups.altarFor("Lava rune", null));
		assertEquals(SkillGroups.OTHER, SkillGroups.altarFor("Sunfire rune", null));
		assertEquals("Ourania altar", SkillGroups.runecraftingAltar(List.of("Nature rune", "Law rune"), List.of()));

		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(ESSENCE, -20, EARTH_RUNE, -20, LAVA_RUNE, 20));
		skilling.onXp("Runecraft", 200);
		skilling.onGameTick(2, null);
		SourceRecord fire = ledger.data().sources.get("item:RUNECRAFTING:Fire altar");
		assertEquals(20L, (long) fire.loot.get(LAVA_RUNE));
	}

	@Test
	public void fishingIsGroupedByWhatTheSpotGives()
	{
		skilling.onGatherClicked(SourceCategory.FISHING, "Fly fishing", HERE, 1);
		skilling.onInventoryChanged(change(RAW_TROUT, 1));
		skilling.onXp("Fishing", 50);
		skilling.onGameTick(2, HERE);
		skilling.onInventoryChanged(change(RAW_SALMON, 1));
		skilling.onXp("Fishing", 70);
		skilling.onGameTick(3, HERE);
		skilling.onInventoryChanged(change(CASKET, 1));
		skilling.onXp("Fishing", 10);
		skilling.onGameTick(4, HERE);

		SourceRecord group = ledger.data().sources.get("item:FISHING:Trout & salmon");
		assertEquals(3, group.loot.size());
		assertEquals(130L, (long) group.xp.get("Fishing"));
		assertEquals("Karambwanji", SkillGroups.fishGroup("Raw karambwanji"));
	}

	@Test
	public void baitUsedBeforeTheFirstCatchJoinsTheFish()
	{
		skilling.onGatherClicked(SourceCategory.FISHING, "Fishing", HERE, 1);
		skilling.onInventoryChanged(change(KARAMBWANJI, -1));
		skilling.onGameTick(2, HERE);
		skilling.onInventoryChanged(change(KARAMBWANJI, -1, RAW_KARAMBWAN, 1));
		skilling.onXp("Fishing", 50);
		skilling.onGameTick(4, HERE);

		SourceRecord karambwan = ledger.data().sources.get("item:FISHING:Karambwan");
		assertEquals(2L, karambwan.supplies.get(String.valueOf(KARAMBWANJI)).quantity);
		assertEquals(1, ledger.data().sources.size());

		// A session that never catches anything still books its bait somewhere.
		skilling.onGatherClicked(SourceCategory.FISHING, "Fishing", HERE, 10);
		skilling.onInventoryChanged(change(KARAMBWANJI, -1));
		skilling.onGameTick(11, HERE);
		skilling.onOtherAction();
		assertEquals(2, ledger.data().sources.size());
	}

	@Test
	public void miningIsGroupedByRock()
	{
		skilling.onGatherClicked(SourceCategory.MINING, "Iron rocks", HERE, 1);
		skilling.onInventoryChanged(change(IRON_ORE, 1));
		skilling.onXp("Mining", 35);
		skilling.onGameTick(2, HERE);
		skilling.onInventoryChanged(change(UNCUT_RUBY, 1));
		skilling.onXp("Mining", 35);
		skilling.onGameTick(3, HERE);

		SourceRecord iron = ledger.data().sources.get("item:MINING:Iron rocks");
		assertEquals(2, iron.loot.size());
		assertEquals("Gem rocks", SkillGroups.rockFromOre("Uncut sapphire"));
		assertEquals("Iron rocks", SkillGroups.miningRock("Rocks", "Iron ore"));
	}

	@Test
	public void hunterIsGroupedByCreature()
	{
		skilling.onOtherTargetClicked("Bird snare", HERE, 1);
		skilling.onChatMessage("You've caught a crimson swift.", 2);
		skilling.onInventoryChanged(change(RED_FEATHER, 5, BONES, 1));
		skilling.onXp("Hunter", 34);
		skilling.onGameTick(2, HERE);
		assertEquals(5L, (long) ledger.data().sources.get("item:HUNTER:Crimson swift").loot.get(RED_FEATHER));

		assertEquals("Crimson swift", SkillGroups.creatureFromItem("Red feather"));
		assertEquals("Baby impling", SkillGroups.creatureFromItem("Baby impling jar"));
		assertTrue(SkillGroups.isTrap("Shaking box"));
		assertFalse(SkillGroups.isTrap("Ruby harvest"));
	}

	@Test
	public void herbloreIsByPotionWhateverTheDose()
	{
		assertEquals("Prayer potion", SkillGroups.herblore("Prayer potion(3)"));
		assertEquals("Ranarr weed", SkillGroups.herblore("Ranarr weed"));

		ledger.data().source("item:HERBLORE:Prayer potion(3)", "Prayer potion(3)", SourceCategory.HERBLORE, 1)
			.addLoot(PRAYER_POT_3, 2, 1);
		ledger.data().source("item:HERBLORE:Prayer potion(4)", "Prayer potion(4)", SourceCategory.HERBLORE, 1)
			.addLoot(PRAYER_POT_4, 1, 1);
		ledger.data().source("item:RUNECRAFTING:Death rune", "Death rune", SourceCategory.RUNECRAFTING, 1)
			.addXp("Runecraft", 100, 1);
		skilling.tidyGroups();
		assertEquals(2, ledger.data().sources.get("item:HERBLORE:Prayer potion").loot.size());
		assertNull(ledger.data().sources.get("item:HERBLORE:Prayer potion(3)"));
		assertEquals(100L, (long) ledger.data().sources.get("item:RUNECRAFTING:Death altar").xp.get("Runecraft"));
	}

	@Test
	public void tidyingTwiceLeavesGroupedEntriesAlone()
	{
		ledger.data().source("item:RUNECRAFTING:Death altar", "Death altar", SourceCategory.RUNECRAFTING, 1)
			.addXp("Runecraft", 100, 1);
		ledger.data().source("item:RUNECRAFTING:Blood altar", "Blood altar", SourceCategory.RUNECRAFTING, 1)
			.addXp("Runecraft", 200, 1);
		ledger.data().source("item:MINING:Iron rocks", "Iron rocks", SourceCategory.MINING, 1)
			.addXp("Mining", 35, 1);
		ledger.data().source("item:MINING:Coal", "Coal", SourceCategory.MINING, 1).addXp("Mining", 50, 1);
		skilling.tidyGroups();
		skilling.tidyGroups();

		assertEquals(100L, (long) ledger.data().sources.get("item:RUNECRAFTING:Death altar").xp.get("Runecraft"));
		assertEquals(200L, (long) ledger.data().sources.get("item:RUNECRAFTING:Blood altar").xp.get("Runecraft"));
		assertNull(ledger.data().sources.get("other:RUNECRAFTING"));
		assertEquals(35L, (long) ledger.data().sources.get("item:MINING:Iron rocks").xp.get("Mining"));
		assertEquals(50L, (long) ledger.data().sources.get("item:MINING:Coal rocks").xp.get("Mining"));
		assertNull(ledger.data().sources.get("item:MINING:Iron rocks rocks"));
	}

	@Test
	public void minigameRewardXpGoesToOther()
	{
		// Nothing being done: straight to Other.
		skilling.bookOtherXp("Runecraft", 30_000);
		assertEquals(30_000L, (long) ledger.data().sources.get("other:RUNECRAFTING").xp.get("Runecraft"));

		// Mid-session, a lump of XP with no items long after the last craft is Other too.
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(ESSENCE, -20, EARTH_RUNE, 20));
		skilling.onXp("Runecraft", 130);
		skilling.onGameTick(2, null);
		skilling.onXp("Runecraft", 12_000);
		skilling.onGameTick(20, null);
		skilling.onGameTick(21, null);
		assertEquals(42_000L, (long) ledger.data().sources.get("other:RUNECRAFTING").xp.get("Runecraft"));
		assertEquals(130L, (long) ledger.data().sources.get("item:RUNECRAFTING:Earth altar").xp.get("Runecraft"));
	}

	@Test
	public void catchesThatGoIntoABagStillCount()
	{
		assertEquals(List.of("Raw trout", "Trout", "Raw trout", "Trout"), SkillGroups.caughtItemNames("You catch a trout."));
		assertEquals("Raw shrimps", SkillGroups.caughtItemNames("You catch some shrimps.").get(0));
		assertEquals(List.of("Yew logs"), SkillGroups.caughtItemNames("You get some yew logs."));
		assertEquals(List.of("Uncut ruby"), SkillGroups.caughtItemNames("You just found a Ruby!"));
		assertTrue(SkillGroups.caughtItemNames("You eat the shark.").isEmpty());

		// Fishing into an open fish barrel: the trout never reaches the inventory.
		skilling.onGatherClicked(SourceCategory.FISHING, "Fly fishing", HERE, 1);
		skilling.onChatMessage("You catch a trout.", 2);
		skilling.onXp("Fishing", 50);
		skilling.onGameTick(2, HERE);
		// A normal catch into the inventory isn't counted twice.
		skilling.onChatMessage("You catch a salmon.", 3);
		skilling.onInventoryChanged(change(RAW_SALMON, 1));
		skilling.onXp("Fishing", 70);
		skilling.onGameTick(3, HERE);
		// An infernal harpoon burns the catch: nothing kept.
		skilling.onChatMessage("You catch a trout.", 4);
		skilling.onChatMessage("The infernal harpoon incinerates the trout.", 4);
		skilling.onXp("Fishing", 50);
		skilling.onGameTick(4, HERE);

		SourceRecord group = ledger.data().sources.get("item:FISHING:Trout & salmon");
		assertEquals(1L, (long) group.loot.get(RAW_TROUT));
		assertEquals(1L, (long) group.loot.get(RAW_SALMON));
	}

	@Test
	public void bonusRunesAfterACraftJoinIt()
	{
		skilling.onProcessClicked(1);
		skilling.onInventoryChanged(change(ESSENCE, -20, EARTH_RUNE, 20));
		skilling.onXp("Runecraft", 130);
		skilling.onGameTick(2, null);
		// Raiments of the Eye bonus runes, a tick later with no XP of their own.
		skilling.onInventoryChanged(change(EARTH_RUNE, 6));
		skilling.onGameTick(3, null);
		skilling.onOtherAction();
		assertEquals(26L, (long) ledger.data().sources.get("item:RUNECRAFTING:Earth altar").loot.get(EARTH_RUNE));
		assertNull(ledger.data().sources.get("item:PROCESSING:Earth rune"));
	}

	@Test
	public void bagsOnlyIgnoreWhatTheyHold()
	{
		assertTrue(SkillRules.bagContents("Colossal pouch").test("Pure essence"));
		assertFalse(SkillRules.bagContents("Colossal pouch").test("Lava rune"));
		assertTrue(SkillRules.bagContents("Fish barrel").test("Raw shark"));
		assertTrue(SkillRules.bagContents("Open log basket").test("Magic logs"));
		assertTrue(SkillRules.bagContents("Open gem bag").test("Uncut diamond"));
		assertTrue(SkillRules.bagContents("Open herb sack").test("Grimy torstol"));
		assertTrue(SkillRules.bagContents("Open seed box").test("Ranarr seed"));
		assertTrue(SkillRules.isPouchShuffle("Giant pouch", "Empty"));
		assertFalse(SkillRules.isPouchShuffle("Giant pouch", "Check"));
		assertTrue(SkillGroups.isCombinationRune("Lava rune"));
		assertFalse(SkillGroups.isCombinationRune("Fire rune"));
		assertTrue(ItemRules.isTarnished("Tarnished ring"));
	}

	@Test
	public void cluesAndCurrenciesAreWorthNothingAndMinigameItemsAreIgnored()
	{
		assertTrue(ItemRules.isZeroValue("Clue scroll (elite)"));
		assertTrue(ItemRules.isZeroValue("Reward casket (hard)"));
		assertTrue(ItemRules.isZeroValue("Mark of grace"));
		assertTrue(ItemRules.isZeroValue("Abyssal pearls"));
		assertTrue(ItemRules.isZeroValue("Molch pearl"));
		assertFalse(ItemRules.isZeroValue("Coins"));
		assertFalse(ItemRules.isZeroValue("Casket"));
		assertTrue(ItemRules.isMinigameItem("Guardian fragments"));
		assertTrue(ItemRules.isMinigameItem("Guardian essence"));
		assertTrue(ItemRules.isMinigameItem("Overload (+)(4)"));
		assertTrue(ItemRules.isMinigameItem("Bruma root"));
		assertFalse(ItemRules.isMinigameItem("Pure essence"));
		assertTrue(ItemRules.isEssencePouch("Colossal pouch"));
		assertTrue(ItemRules.isEssencePouch("Giant pouch (degraded)"));
		assertFalse(ItemRules.isEssencePouch("Rune pouch"));
	}
}
