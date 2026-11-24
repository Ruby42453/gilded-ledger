package com.gildedledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.tracking.BirdHouses;
import com.gildedledger.tracking.Ledger;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class BirdHousesTest
{
	private static final int MAGIC_BIRD_HOUSE = 22204;
	private static final int HOPS_SEED = 5305;
	private static final int BIRD_NEST = 5070;
	private static final int FEATHER = 314;
	private static final int CLOCKWORK = 8792;

	private Ledger ledger;
	private BirdHouses houses;

	@Before
	public void setUp()
	{
		ledger = new Ledger(() -> 0L);
		houses = new BirdHouses(ledger);
	}

	private static String name(int id)
	{
		switch (id)
		{
			case MAGIC_BIRD_HOUSE:
				return "Magic bird house";
			case HOPS_SEED:
				return "Barley seed";
			case BIRD_NEST:
				return "Bird nest";
			case FEATHER:
				return "Feather";
			default:
				return "Clockwork";
		}
	}

	private SourceRecord record()
	{
		return ledger.data().sources.get(BirdHouses.KEY);
	}

	@Test
	public void targets()
	{
		assertTrue(BirdHouses.isBirdHouseTarget("Empty", "Magic birdhouse"));
		assertTrue(BirdHouses.isBirdHouseTarget("Use", "Magic bird house -> Space"));
		assertTrue(BirdHouses.isBirdHouseTarget("Build", "Space"));
		assertFalse(BirdHouses.isBirdHouseTarget("Examine", "Magic birdhouse"));
		assertFalse(BirdHouses.isBirdHouseTarget("Chop down", "Magic tree"));
	}

	@Test
	public void aRunBooksHousesAndSeedsAsSuppliesAndTheEmptyingAsLoot()
	{
		houses.onClick("Empty", "Magic birdhouse", 1);
		Map<Integer, Integer> emptied = new HashMap<>();
		emptied.put(BIRD_NEST, 2);
		emptied.put(FEATHER, 50);
		emptied.put(CLOCKWORK, 1);
		assertEquals(3, houses.onCarriedChanges(emptied, 4, BirdHousesTest::name, id -> 10).size());
		assertTrue(houses.onXp("Hunter", 1000, 4));

		Map<Integer, Integer> built = new HashMap<>();
		built.put(MAGIC_BIRD_HOUSE, -1);
		houses.onCarriedChanges(built, 7, BirdHousesTest::name, id -> 1500);
		Map<Integer, Integer> seeded = new HashMap<>();
		seeded.put(HOPS_SEED, -10);
		houses.onClick("Use", "Barley seed -> Magic birdhouse", 9);
		houses.onCarriedChanges(seeded, 10, BirdHousesTest::name, id -> 5);

		assertEquals(1L, record().count);
		assertEquals(2L, (long) record().loot.get(BIRD_NEST));
		assertEquals(1L, record().supplies.get(String.valueOf(MAGIC_BIRD_HOUSE)).quantity);
		assertEquals(1500L, record().supplies.get(String.valueOf(MAGIC_BIRD_HOUSE)).gp);
		assertEquals(10L, record().supplies.get(String.valueOf(HOPS_SEED)).quantity);
		assertEquals(1000L, (long) record().xp.get("Hunter"));
	}

	@Test
	public void nothingWithoutABirdHouseClick()
	{
		Map<Integer, Integer> nest = new HashMap<>();
		nest.put(BIRD_NEST, 1);
		assertTrue(houses.onCarriedChanges(nest, 50, BirdHousesTest::name, id -> 10).isEmpty());
		assertFalse(houses.onXp("Hunter", 100, 50));
	}
}
