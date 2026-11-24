package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;

/**
 * Bird house runs, under Hunter in one "Bird houses" entry: the bird houses built on the
 * spaces and the seeds put in them are its supplies; the nests, feathers and anything else
 * from emptying them is its loot, along with the Hunter XP. Each house emptied is one.
 */
public class BirdHouses
{
	public static final String NAME = "Bird houses";
	public static final String KEY = "item:HUNTER:" + NAME;
	/** The entry earlier versions filed bird house loot under. */
	public static final String OLD_KEY = "item:HUNTER:Bird nest";

	/** After clicking a bird house (or its space), its items and XP come within this long. */
	static final int WINDOW_TICKS = 10;

	private final Ledger ledger;
	private int untilTick = Integer.MIN_VALUE / 2;

	public BirdHouses(Ledger ledger)
	{
		this.ledger = ledger;
	}

	/** A bird house object, its empty space, or a bird house or seed used on one. */
	public static boolean isBirdHouseTarget(String option, String target)
	{
		String t = lower(target);
		String o = lower(option);
		if (o.equals("examine") || o.equals("walk here") || o.equals("cancel"))
		{
			return false;
		}
		return t.contains("birdhouse") || t.contains("bird house")
			|| (o.equals("build") && (t.equals("space") || t.endsWith("-> space")));
	}

	/** Items a bird house run uses: the houses and the seeds that fill them. */
	static boolean isSupply(String itemName)
	{
		String n = lower(itemName);
		return n.endsWith("bird house") || n.endsWith("birdhouse") || n.endsWith(" seed") || n.endsWith(" seeds");
	}

	public void onClick(String option, String target, int tick)
	{
		if (isBirdHouseTarget(option, target))
		{
			untilTick = tick + WINDOW_TICKS;
		}
	}

	public boolean isActive(int tick)
	{
		return tick <= untilTick;
	}

	/**
	 * Books what a bird house did this tick: houses and seeds put in are supplies, anything
	 * gained from emptying one is loot. Returns what was booked, to take out of the changes.
	 */
	public Map<Integer, Integer> onCarriedChanges(Map<Integer, Integer> changes, int tick,
		IntFunction<String> names, IntToLongFunction price)
	{
		Map<Integer, Integer> booked = new HashMap<>();
		if (!isActive(tick))
		{
			return booked;
		}
		boolean emptied = false;
		for (Map.Entry<Integer, Integer> change : changes.entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			String name = names.apply(id);
			if (delta < 0 && isSupply(name))
			{
				long quantity = -delta;
				ledger.addSupply(KEY, NAME, SourceCategory.HUNTER, String.valueOf(id), id, null, null,
					quantity, price.applyAsLong(id) * quantity);
				booked.put(id, delta);
			}
			else if (delta > 0 && (lower(name).endsWith("bird house") || lower(name).endsWith("birdhouse")))
			{
				// A bird house made (crafting books it as that skill's loot), not something emptied.
				continue;
			}
			else if (delta > 0)
			{
				ledger.addLoot(KEY, NAME, SourceCategory.HUNTER, id, delta);
				booked.put(id, delta);
				emptied |= lower(name).startsWith("bird nest") || lower(name).endsWith("feather");
			}
		}
		if (emptied)
		{
			ledger.incrementCount(KEY, NAME, SourceCategory.HUNTER);
		}
		if (!booked.isEmpty())
		{
			untilTick = tick + WINDOW_TICKS;
			ledger.changed();
		}
		return booked;
	}

	/** Hunter XP from building or emptying a bird house. Returns true when booked here. */
	public boolean onXp(String skill, long amount, int tick)
	{
		if (!"Hunter".equalsIgnoreCase(skill) || !isActive(tick))
		{
			return false;
		}
		ledger.addXp(KEY, NAME, SourceCategory.HUNTER, "Hunter", amount);
		ledger.changed();
		return true;
	}

	public void reset()
	{
		untilTick = Integer.MIN_VALUE / 2;
	}

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH).trim();
	}
}
