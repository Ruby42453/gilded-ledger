package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gildedledger.tracking.ItemRules;
import org.junit.Test;

public class ItemRulesTest
{
	@Test
	public void potionDoses()
	{
		assertEquals(4, ItemRules.doses("Prayer potion(4)"));
		assertEquals(2, ItemRules.doses("Divine super combat potion(2)"));
		assertEquals(1, ItemRules.doses("Shark"));
		assertEquals("Super restore", ItemRules.withoutDoses("Super restore(3)"));
		assertEquals(3000, ItemRules.perDoseValue(12000, "Prayer potion(4)"));
		assertEquals(0, ItemRules.perDoseValue(0, "Prayer potion(4)"));
	}

	@Test
	public void runesAreStackableItemsEndingInRune()
	{
		assertTrue(ItemRules.isRune("Blood rune", true));
		assertTrue(ItemRules.isRune("Wrath rune", true));
		assertFalse(ItemRules.isRune("Rune scimitar", false));
		assertFalse(ItemRules.isRune("Rune arrow", true));
	}

	@Test
	public void teleportConsumables()
	{
		assertTrue(ItemRules.isTeleportConsumable("Varrock teleport", "Break"));
		assertTrue(ItemRules.isTeleportConsumable("Lumberyard teleport", "Read"));
		assertFalse(ItemRules.isTeleportConsumable("Teleport crystal (3)", "Teleport"));
		assertFalse(ItemRules.isTeleportConsumable("Varrock teleport", "Drop"));
	}

	@Test
	public void farmingNames()
	{
		assertTrue(ItemRules.isPlantable("Ranarr seed"));
		assertTrue(ItemRules.isPlantable("Magic sapling"));
		assertTrue(ItemRules.isPlantable("Crystal acorn"));
		assertFalse(ItemRules.isPlantable("Grimy ranarr weed"));
		assertEquals("Ranarr", ItemRules.cropFromPlantable("Ranarr seed"));
		assertEquals("Magic", ItemRules.cropFromPlantable("Magic sapling"));
		assertEquals("Palm", ItemRules.cropFromPlantable("Palm sapling"));
		assertEquals("Magic", ItemRules.cropFromPatchAppearance("Magic tree"));
		assertEquals("Yew", ItemRules.cropFromPatchAppearance("Diseased yew tree"));
		assertNull(ItemRules.cropFromPatchAppearance("Herbs"));
		assertNull(ItemRules.cropFromPatchAppearance("Tree stump"));
		assertEquals("Herb", ItemRules.patchCategory("Herb patch"));
		assertEquals("Fruit tree", ItemRules.patchCategory("Fruit Tree Patch"));
		assertEquals("Farming", ItemRules.patchCategory(null));
		assertTrue(ItemRules.isFarmingTool("Watering can(7)"));
		assertTrue(ItemRules.isFarmingTool("Bucket"));
		assertFalse(ItemRules.isFarmingTool("Grimy ranarr weed"));
	}

	@Test
	public void compostMessages()
	{
		assertEquals("Ultracompost", ItemRules.compostFromMessage("You treat the herb patch with ultracompost."));
		assertEquals("Supercompost", ItemRules.compostFromMessage("You treat the tree patch with supercompost."));
		assertNull(ItemRules.compostFromMessage("You plant a ranarr seed in the herb patch."));
	}
}
