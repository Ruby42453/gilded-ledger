package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ChargedWeaponsTest
{
	@Test
	public void poweredStavesCostTheirRechargeRunes()
	{
		List<int[]> swamp = ChargedWeapons.costPerAttack("Trident of the swamp (e)", ChargedWeapons.POWERED_STAFF_ATTACK);
		assertEquals(4, swamp.size());
		assertTrue(swamp.stream().anyMatch(c -> c[0] == ItemID.SNAKEBOSS_SCALE && c[1] == 1));

		// The older high-level magic animation counts too.
		assertEquals(4, ChargedWeapons.costPerAttack("Trident of the swamp", ChargedWeapons.HIGH_LEVEL_MAGIC_ATTACK).size());

		List<int[]> scythe = ChargedWeapons.costPerAttack("Holy scythe of vitur", ChargedWeapons.SCYTHE_ATTACK);
		assertTrue(scythe.stream().anyMatch(c -> c[0] == ItemID.VIAL_BLOOD && c[2] == 100));
		assertEquals(1, ChargedWeapons.costPerAttack("Craw's bow", ChargedWeapons.BOW_ATTACK).size());
		assertTrue(ChargedWeapons.costPerAttack("Craw's bow (u)", ChargedWeapons.BOW_ATTACK).isEmpty());

		List<int[]> seas = ChargedWeapons.costPerAttack("Trident of the seas", ChargedWeapons.POWERED_STAFF_ATTACK);
		assertTrue(seas.stream().anyMatch(c -> c[0] == ItemID.COINS && c[1] == 10));

		List<int[]> sang = ChargedWeapons.costPerAttack("Holy sanguinesti staff", ChargedWeapons.POWERED_STAFF_ATTACK);
		assertEquals(ItemID.BLOODRUNE, sang.get(0)[0]);
		assertEquals(3, sang.get(0)[1]);

		List<int[]> shadow = ChargedWeapons.costPerAttack("Tumeken's shadow", ChargedWeapons.SHADOW_ATTACK);
		assertTrue(shadow.stream().anyMatch(c -> c[0] == ItemID.SOULRUNE && c[1] == 2));
		assertTrue(shadow.stream().anyMatch(c -> c[0] == ItemID.CHAOSRUNE && c[1] == 5));
	}

	@Test
	public void otherAnimationsAndUnchargedWeaponsCostNothing()
	{
		assertTrue(ChargedWeapons.costPerAttack("Trident of the seas", 829).isEmpty());
		assertTrue(ChargedWeapons.costPerAttack("Tumeken's shadow (uncharged)", ChargedWeapons.SHADOW_ATTACK).isEmpty());
		assertTrue(ChargedWeapons.costPerAttack("Abyssal whip", ChargedWeapons.POWERED_STAFF_ATTACK).isEmpty());
	}

	@Test
	public void blowpipeHelmsAndDarts()
	{
		assertTrue(ChargedWeapons.isBlowpipe("Toxic blowpipe"));
		assertTrue(ChargedWeapons.isBlowpipe("Blazing blowpipe"));
		assertFalse(ChargedWeapons.isBlowpipe("Toxic blowpipe (empty)"));
		assertEquals(0.20, ChargedWeapons.dartLossRate("Ava's assembler"), 1e-9);
		assertEquals(0.28, ChargedWeapons.dartLossRate("Max cape"), 1e-9);
		assertEquals(0.28, ChargedWeapons.dartLossRate("Ranging cape(t)"), 1e-9);
		assertEquals(1.0, ChargedWeapons.dartLossRate(null), 1e-9);
		assertTrue(ChargedWeapons.isScaleHelm("Serpentine helm"));
		assertFalse(ChargedWeapons.isScaleHelm("Serpentine helm (uncharged)"));
		assertEquals("Adamant dart", ChargedWeapons.dartFromCheckMessage("Darts: Adamant dart x 1,234. Scales: 5,678 (35.0%)."));
		assertNull(ChargedWeapons.dartFromCheckMessage("Darts: None. Scales: 0 (0.0%)."));
		long[] counts = ChargedWeapons.blowpipeCheckCounts("Darts: Amethyst dart x 1,234. Scales: 5,678 (35.0%).");
		assertEquals(1234, counts[0]);
		assertEquals(5678, counts[1]);
		assertEquals(0, ChargedWeapons.blowpipeCheckCounts("Darts: None. Scales: 12 (0.1%).")[0]);
		assertNull(ChargedWeapons.blowpipeCheckCounts("You eat the shark."));
	}
}
