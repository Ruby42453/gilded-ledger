package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giants' Foundry, under Smithing. The bars and metal items put into the crucible are its
 * supplies; Kovac's payment for a finished sword (sent to the bank, so read from his chat
 * message) is its loot, along with the Smithing XP he gives. Each sword handed in is one.
 */
public class GiantsFoundry
{
	public static final String NAME = "Giants' Foundry";
	public static final String KEY = "item:SMITHING:" + NAME;
	static final int COINS = 995;

	/** After clicking the crucible, the metal menu can take a while to pick from. */
	static final int FILL_TICKS = 30;
	/** Refining keeps giving small Smithing XP drops for a while after clicking a station. */
	static final int WORKING_TICKS = 100;
	/** Objects of the foundry: Smithing XP after using one is the foundry's. */
	private static final Set<String> STATIONS = Set.of(
		"trip hammer", "grindstone", "polishing wheel", "lava pool", "waterfall", "mould jig", "preform storage");
	/** After clicking Kovac, the hand-in dialogue. */
	static final int HAND_IN_TICKS = 30;
	/** The payment, its XP and (for Ultimate Ironmen) the coins land within a few ticks. */
	static final int PAYMENT_TICKS = 3;

	/** "Kovac deposits 12,345 coins into your bank as payment." and similar. */
	private static final Pattern PAYMENT = Pattern.compile("([\\d,]+) coins");

	private static final Set<String> METALS = Set.of("bronze", "iron", "steel", "mithril", "adamant", "rune");
	private static final Set<String> BARS = Set.of(
		"bronze bar", "iron bar", "steel bar", "mithril bar", "adamantite bar", "runite bar");
	/** Smithed items the crucible melts down (worth their bars less one). */
	private static final Set<String> ITEM_TYPES = Set.of(
		"scimitar", "longsword", "full helm", "sq shield", "claws", "warhammer", "battleaxe", "chainbody",
		"kiteshield", "2h sword", "platelegs", "plateskirt", "platebody");

	private final Ledger ledger;
	private int fillUntil = Integer.MIN_VALUE / 2;
	private int handInUntil = Integer.MIN_VALUE / 2;
	private int paidTick = Integer.MIN_VALUE / 2;
	/** The last click on a foundry station (trip hammer, grindstone, polishing wheel...). */
	private int workingTick = Integer.MIN_VALUE / 2;
	private long paidCoins;

	public GiantsFoundry(Ledger ledger)
	{
		this.ledger = ledger;
	}

	/** Bars and smithed metal items the crucible accepts. */
	public static boolean isFoundryMetal(String itemName)
	{
		String n = lower(itemName);
		if (BARS.contains(n))
		{
			return true;
		}
		int space = n.indexOf(' ');
		return space > 0 && METALS.contains(n.substring(0, space)) && ITEM_TYPES.contains(n.substring(space + 1));
	}

	/** A click on an object or NPC: the crucible (to fill it) or Kovac (to hand in). */
	public void onClick(String option, String target, int tick)
	{
		String o = lower(option);
		if (o.equals("examine") || o.equals("check") || o.equals("walk here") || o.equals("cancel"))
		{
			return;
		}
		String t = lower(target);
		int bracket = t.indexOf(" (");
		if (STATIONS.contains(bracket > 0 ? t.substring(0, bracket) : t) || t.startsWith("crucible"))
		{
			workingTick = tick;
		}
		if (t.startsWith("crucible") && !o.equals("empty"))
		{
			fillUntil = tick + FILL_TICKS;
		}
		else if (t.equals("kovac"))
		{
			handInUntil = tick + HAND_IN_TICKS;
		}
	}

	public boolean isFilling(int tick)
	{
		return tick <= fillUntil;
	}

	/**
	 * Metal that left the inventory while filling the crucible: booked as the foundry's
	 * supplies. Returns what was booked, to take out of the tick's changes.
	 */
	public Map<Integer, Integer> takeCrucibleMetal(Map<Integer, Integer> changes, int tick,
		IntFunction<String> names, IntToLongFunction price)
	{
		Map<Integer, Integer> used = new HashMap<>();
		if (!isFilling(tick))
		{
			return used;
		}
		changes.forEach((id, delta) ->
		{
			if (delta < 0 && isFoundryMetal(names.apply(id)))
			{
				long quantity = -delta;
				ledger.addSupply(KEY, NAME, SourceCategory.SMITHING, String.valueOf(id), id, null, null,
					quantity, price.applyAsLong(id) * quantity);
				used.put(id, delta);
			}
		});
		if (!used.isEmpty())
		{
			// Picking the next metal keeps the crucible open.
			fillUntil = tick + FILL_TICKS;
			ledger.changed();
		}
		return used;
	}

	/** Kovac's payment message: the coins are the foundry's loot, and one more sword handed in. */
	public boolean onChatMessage(String message, int tick)
	{
		String m = lower(message);
		boolean fromKovac = m.contains("kovac") || tick <= handInUntil;
		if (!fromKovac || !m.contains("coins") || !(m.contains("bank") || m.contains("payment") || m.contains("kovac")))
		{
			return false;
		}
		Matcher matcher = PAYMENT.matcher(m);
		if (!matcher.find())
		{
			return false;
		}
		long coins;
		try
		{
			coins = Long.parseLong(matcher.group(1).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		if (coins <= 0)
		{
			return false;
		}
		ledger.addLoot(KEY, NAME, SourceCategory.SMITHING, COINS, coins);
		ledger.incrementCount(KEY, NAME, SourceCategory.SMITHING);
		ledger.changed();
		paidTick = tick;
		paidCoins = coins;
		return true;
	}

	/** Smithing XP from refining a sword or handing it in is the foundry's. Returns true when it was booked here. */
	public boolean onXp(String skill, long amount, int tick)
	{
		boolean working = tick - workingTick <= WORKING_TICKS;
		if (!"Smithing".equalsIgnoreCase(skill) || (tick > handInUntil && tick - paidTick > PAYMENT_TICKS && !working))
		{
			return false;
		}
		ledger.addXp(KEY, NAME, SourceCategory.SMITHING, "Smithing", amount);
		ledger.changed();
		return true;
	}

	/**
	 * Ultimate Ironmen get the payment in their inventory as well as the message: those
	 * coins were already booked from the message. Returns the coins to ignore.
	 */
	public Map<Integer, Integer> paymentCoins(Map<Integer, Integer> changes, int tick)
	{
		Map<Integer, Integer> paid = new HashMap<>();
		Integer gained = changes.get(COINS);
		if (gained != null && gained > 0 && tick - paidTick <= PAYMENT_TICKS && paidCoins > 0)
		{
			int take = (int) Math.min(gained, paidCoins);
			paid.put(COINS, take);
			paidCoins -= take;
		}
		return paid;
	}

	public void reset()
	{
		fillUntil = Integer.MIN_VALUE / 2;
		handInUntil = Integer.MIN_VALUE / 2;
		paidTick = Integer.MIN_VALUE / 2;
		paidCoins = 0;
		workingTick = Integer.MIN_VALUE / 2;
	}

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH).trim();
	}
}
