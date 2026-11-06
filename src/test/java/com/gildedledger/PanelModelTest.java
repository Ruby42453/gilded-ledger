package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.TrackerData;
import com.gildedledger.ui.LootPrices;
import com.gildedledger.ui.PanelModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PanelModelTest
{
	private static final int WHIP = 4151;
	private static final int DEFENDER = 12954;
	private static final int CLUE = 2722;
	private static final int TARNISHED_RING = 31000;

	/** Whip trades for 1.5M; the defender and the clue can't be traded. */
	private static final LootPrices PRICES = new LootPrices()
	{
		@Override
		public long ge(int itemId)
		{
			return itemId == WHIP ? 1_500_000 : 0;
		}

		@Override
		public long highAlch(int itemId)
		{
			return itemId == WHIP ? 72_000 : itemId == DEFENDER ? 30_000 : itemId == TARNISHED_RING ? 1_800 : 0;
		}

		@Override
		public boolean valuedAtAlch(int itemId)
		{
			return itemId == TARNISHED_RING;
		}

		@Override
		public boolean tradeable(int itemId)
		{
			return itemId == WHIP;
		}
	};

	@Test
	public void tradeableLootUsesGeAndUntradeablesAreWorthNothingExceptTarnished()
	{
		TrackerData data = new TrackerData();
		data.source("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 1).addLoot(WHIP, 2, 1);
		data.source("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 1).addLoot(DEFENDER, 1, 1);
		data.source("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 1).addLoot(CLUE, 1, 1);
		data.source("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 1).addLoot(TARNISHED_RING, 1, 1);

		PanelModel.SourceView demon = PanelModel.build(data, PRICES, id -> "item" + id, true).sources.get(0);

		// Whips at GE, the tarnished ring at its alch price, the defender and clue at nothing.
		assertEquals(3_000_000 + 1_800, demon.lootValue);
		assertEquals(WHIP, demon.loot.get(0).itemId);
		assertTrue(demon.loot.stream().anyMatch(l -> l.itemId == DEFENDER && !l.tradeable && l.value == 0));
	}

	@Test
	public void excludedItemsDoNotCountTowardsProfit()
	{
		TrackerData data = new TrackerData();
		com.gildedledger.data.SourceRecord demon = data.source("npc:Abyssal demon", "Abyssal demon", SourceCategory.COMBAT, 1);
		demon.addLoot(WHIP, 1, 1);
		demon.addLoot(DEFENDER, 1, 1);
		demon.addSupply("2434", 2434, "Prayer potion", "doses", 4, 10_000, 1);
		demon.addSupply("385", 385, "Shark", null, 2, 1_800, 1);

		demon.toggleLootExcluded(DEFENDER);
		demon.toggleSupplyExcluded("385");
		PanelModel.SourceView view = PanelModel.build(data, PRICES, id -> "", true).sources.get(0);
		assertEquals(1_500_000, view.lootValue);
		assertEquals(10_000, view.supplyGp);
		assertTrue(view.loot.stream().anyMatch(l -> l.itemId == DEFENDER && l.excluded));

		// Toggling again counts it once more.
		demon.toggleLootExcluded(DEFENDER);
		assertEquals(1_500_000, PanelModel.build(data, PRICES, id -> "", true).sources.get(0).lootValue);
	}

	@Test
	public void skillsCanBeShownAsOneTable()
	{
		TrackerData data = new TrackerData();
		data.source("item:WOODCUTTING:Yew logs", "Yew logs", SourceCategory.WOODCUTTING, 1).addLoot(WHIP, 3, 1);
		data.source("item:WOODCUTTING:Magic logs", "Magic logs", SourceCategory.WOODCUTTING, 1).addLoot(WHIP, 2, 1);
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addLoot(WHIP, 1, 1);
		data.source("npc:Cow", "Cow", SourceCategory.COMBAT, 1).addLoot(WHIP, 1, 1);

		assertEquals(4, PanelModel.build(data, PRICES, id -> "", true, false).sources.size());
		List<PanelModel.SourceView> combined = PanelModel.build(data, PRICES, id -> "", true, true).sources;
		assertEquals(3, combined.size());
		PanelModel.SourceView woodcutting = combined.stream()
			.filter(v -> v.category == SourceCategory.WOODCUTTING).findFirst().get();
		assertEquals(5L, woodcutting.loot.get(0).quantity);
		assertEquals(3L, (long) data.sources.get("item:WOODCUTTING:Yew logs").loot.get(WHIP));
	}

	@Test
	public void xpIsSplitBySkillInSkillPanelOrderWithATotal()
	{
		TrackerData data = new TrackerData();
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addXp("Hitpoints", 40, 1);
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addXp("Slayer", 20, 1);
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addXp("Strength", 120, 1);
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addXp("Attack", 10, 1);

		PanelModel.SourceView goblin = PanelModel.build(data, PRICES, id -> "", true).sources.get(0);

		List<String> order = new ArrayList<>(goblin.xpBySkill.keySet());
		assertEquals(List.of("Attack", "Strength", "Hitpoints", "Slayer"), order);
		assertEquals(190, goblin.xp);
	}
}
