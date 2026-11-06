package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.MinigameRewards;
import com.gildedledger.ui.LootPrices;
import com.gildedledger.ui.PanelModel;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MinigameRewardsTest
{
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
	public void rewardPullsAreLootForTheMinigamesSkill()
	{
		assertEquals(SourceCategory.RUNECRAFTING, MinigameRewards.forTarget("Rewards Guardian", "Search").category);
		assertEquals(SourceCategory.FISHING, MinigameRewards.forTarget("Reward pool", "Fish").category);
		assertEquals(SourceCategory.FIREMAKING, MinigameRewards.forItem("Supply crate", "Open").category);
		assertEquals(SourceCategory.OTHER, MinigameRewards.forItem("Spoils of war", "Open").category);
		assertNull(MinigameRewards.forTarget("Rewards Guardian", "Examine"));
		assertNull(MinigameRewards.forTarget("Bank booth", "Bank"));

		Ledger ledger = new Ledger(() -> 1L);
		MinigameRewards rewards = new MinigameRewards();
		rewards.onRewardClicked(MinigameRewards.forTarget("Rewards Guardian", "Search"), 10);
		assertTrue(rewards.onCarriedChanges(change(565, 120), 11, ledger));
		assertTrue(rewards.onCarriedChanges(change(26813, 1), 14, ledger));
		SourceRecord gotr = ledger.data().sources.get("reward:Guardians of the Rift");
		assertEquals(SourceCategory.RUNECRAFTING, gotr.category);
		assertEquals(2, gotr.loot.size());
		assertEquals(2, gotr.count);

		// Opening a supply crate: the crate isn't a cost.
		rewards.onRewardClicked(MinigameRewards.forItem("Supply crate", "Open"), 30);
		assertTrue(rewards.onCarriedChanges(change(20703, -1, 1515, 12), 31, ledger));
		SourceRecord wintertodt = ledger.data().sources.get("reward:Wintertodt");
		assertEquals(12L, (long) wintertodt.loot.get(1515));
		assertTrue(wintertodt.supplies.isEmpty());

		// Long after, or after clicking something else, items are nothing to do with it.
		assertFalse(rewards.onCarriedChanges(change(1515, 1), 60, ledger));
		rewards.onRewardClicked(MinigameRewards.forTarget("Reward pool", "Fish"), 70);
		rewards.onOtherClick();
		assertFalse(rewards.onCarriedChanges(change(1515, 1), 71, ledger));
	}

	@Test
	public void motherlodeSackJoinsTheMotherlodeEntry()
	{
		MinigameRewards.Minigame mlm = MinigameRewards.forTarget("Sack", "Search");
		assertEquals(SourceCategory.MINING, mlm.category);
		assertEquals("item:MINING:Motherlode Mine", mlm.key());
		assertEquals("Motherlode Mine", com.gildedledger.tracking.SkillGroups.miningRock("Ore vein", "Pay-dirt"));
		assertNull(MinigameRewards.forTarget("Sack", "Examine"));

		Ledger ledger = new Ledger(() -> 1L);
		ledger.addXp("item:MINING:Motherlode Mine", "Motherlode Mine", SourceCategory.MINING, "Mining", 600);
		MinigameRewards rewards = new MinigameRewards();
		rewards.onRewardClicked(mlm, 5);
		rewards.onCarriedChanges(change(447, 10, 453, 6), 6, ledger);
		SourceRecord entry = ledger.data().sources.get("item:MINING:Motherlode Mine");
		assertEquals(10L, (long) entry.loot.get(447));
		assertEquals(600L, (long) entry.xp.get("Mining"));
	}

	@Test
	public void hiddenSkillsLeaveThePanelButStayTracked()
	{
		TrackerData data = new TrackerData();
		data.source("npc:Goblin", "Goblin", SourceCategory.COMBAT, 1).addLoot(1, 1, 1);
		data.source("item:FISHING:Karambwanji", "Karambwanji", SourceCategory.FISHING, 1).addLoot(1, 1, 1);
		data.source("item:MINING:Iron rocks", "Iron rocks", SourceCategory.MINING, 1).addLoot(1, 1, 1);
		LootPrices prices = new LootPrices()
		{
			public long ge(int id)
			{
				return 10;
			}

			public long highAlch(int id)
			{
				return 5;
			}

			public boolean tradeable(int id)
			{
				return true;
			}
		};
		PanelModel model = PanelModel.build(data, prices, id -> "", true, false, EnumSet.of(SourceCategory.FISHING));
		assertEquals(2, model.sources.size());
		assertTrue(model.sources.stream().noneMatch(v -> v.category == SourceCategory.FISHING));
		assertEquals(3, data.sources.size());
		assertTrue(SourceCategory.FISHING.isSkill());
		assertFalse(SourceCategory.FARMING.isSkill());
		assertFalse(SourceCategory.COMBAT.isSkill());
	}
}
