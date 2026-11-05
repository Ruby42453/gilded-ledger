package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gildedledger.tracking.GroundDropLedger;
import com.gildedledger.tracking.TileKey;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GroundDropLedgerTest
{
	private static final int WHIP = 4151;
	private static final int BONES = 526;
	private static final TileKey NPC_TILE = new TileKey(3200, 3200, 0);
	private static final TileKey DROP_TILE = new TileKey(3200, 3200, 0);

	private GroundDropLedger ledger;

	@Before
	public void setUp()
	{
		ledger = new GroundDropLedger();
	}

	private void killDropping(int itemId, int quantity, TileKey where, int tick)
	{
		ledger.recordSpawn(where, itemId, quantity, tick);
		ledger.tagNpcLoot("npc:Abyssal demon", true, NPC_TILE, 1,
			Collections.singletonList(new int[]{itemId, quantity}), tick);
	}

	@Test
	public void takenByPlayerCountsAsLoot()
	{
		killDropping(WHIP, 1, DROP_TILE, 10);
		ledger.recordTake(DROP_TILE, WHIP, 12);

		List<GroundDropLedger.Pickup> pickups = ledger.recordRemoval(DROP_TILE, WHIP, 1, 14, DROP_TILE);

		assertEquals(1, pickups.size());
		assertEquals("npc:Abyssal demon", pickups.get(0).sourceKey);
		assertEquals(1, pickups.get(0).quantity);
		assertTrue(pickups.get(0).taskTarget);
	}

	@Test
	public void despawnWithoutTakeIsIgnored()
	{
		killDropping(WHIP, 1, DROP_TILE, 10);

		assertTrue(ledger.recordRemoval(DROP_TILE, WHIP, 1, 300, new TileKey(3210, 3210, 0)).isEmpty());
	}

	@Test
	public void takeFromFarAwayDoesNotCount()
	{
		killDropping(WHIP, 1, DROP_TILE, 10);
		ledger.recordTake(DROP_TILE, WHIP, 12);

		// Someone else grabbed it while we were still five tiles away.
		assertTrue(ledger.recordRemoval(DROP_TILE, WHIP, 1, 13, new TileKey(3205, 3200, 0)).isEmpty());
	}

	@Test
	public void itemsNotFromTheKillAreNeverLoot()
	{
		// The player drops their own item next to the corpse and picks it back up.
		ledger.recordSpawn(DROP_TILE, BONES, 1, 10);
		ledger.recordTake(DROP_TILE, BONES, 11);

		assertTrue(ledger.recordRemoval(DROP_TILE, BONES, 1, 12, DROP_TILE).isEmpty());
	}

	@Test
	public void partialStackPickupCountsOnlyWhatWasTaken()
	{
		killDropping(995, 5000, DROP_TILE, 10);
		ledger.recordTake(DROP_TILE, 995, 11);

		List<GroundDropLedger.Pickup> first = ledger.recordRemoval(DROP_TILE, 995, 3000, 12, DROP_TILE);
		assertEquals(3000, first.get(0).quantity);

		ledger.recordTake(DROP_TILE, 995, 13);
		List<GroundDropLedger.Pickup> second = ledger.recordRemoval(DROP_TILE, 995, 2000, 14, DROP_TILE);
		assertEquals(2000, second.get(0).quantity);

		ledger.recordTake(DROP_TILE, 995, 15);
		assertTrue(ledger.recordRemoval(DROP_TILE, 995, 1, 16, DROP_TILE).isEmpty());
	}

	@Test
	public void bossLootAwayFromTheBossStillMatches()
	{
		TileKey byPlayer = new TileKey(3208, 3204, 0);
		ledger.recordSpawn(byPlayer, WHIP, 1, 10);
		ledger.tagNpcLoot("npc:Zulrah", false, NPC_TILE, 5, Collections.singletonList(new int[]{WHIP, 1}), 10);
		ledger.recordTake(byPlayer, WHIP, 11);

		assertEquals(1, ledger.recordRemoval(byPlayer, WHIP, 1, 12, byPlayer).size());
	}

	@Test
	public void droppedLootPickedBackUpReturnsToItsSource()
	{
		ledger.recordPlayerDrop("gather:FISHING:Fly fishing", 335, 3, DROP_TILE, 10);
		ledger.recordTake(DROP_TILE, 335, 12);

		List<GroundDropLedger.Pickup> pickups = ledger.recordRemoval(DROP_TILE, 335, 3, 13, DROP_TILE);
		assertEquals(1, pickups.size());
		assertEquals("gather:FISHING:Fly fishing", pickups.get(0).sourceKey);
		assertEquals(3, pickups.get(0).quantity);
	}

	@Test
	public void oldSpawnsAreNotClaimedByALaterKill()
	{
		ledger.recordSpawn(DROP_TILE, WHIP, 1, 10);
		ledger.expire(20);
		ledger.tagNpcLoot("npc:Abyssal demon", false, NPC_TILE, 1, Collections.singletonList(new int[]{WHIP, 1}), 20);
		ledger.recordTake(DROP_TILE, WHIP, 21);

		assertTrue(ledger.recordRemoval(DROP_TILE, WHIP, 1, 22, DROP_TILE).isEmpty());
	}
}
