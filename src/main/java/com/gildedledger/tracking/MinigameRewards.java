package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import java.util.Locale;
import java.util.Map;

/**
 * Minigame reward pulls: searching the Rewards Guardian (Guardians of the Rift), fishing
 * the Tempoross reward pool, the Wintertodt reward cart or supply crates, and so on.
 * Everything gained from a pull is loot for that minigame, filed under its skill, or
 * under Other when it belongs to no skill.
 */
public class MinigameRewards
{
	/** How long after the click (or the last item received) a pull keeps paying out. */
	static final int WINDOW_TICKS = 8;

	/** A minigame and the category its rewards go to. */
	public static final class Minigame
	{
		public final String name;
		public final SourceCategory category;
		private final String key;

		Minigame(String name, SourceCategory category)
		{
			this(name, category, "reward:" + name);
		}

		Minigame(String name, SourceCategory category, String key)
		{
			this.name = name;
			this.category = category;
			this.key = key;
		}

		public String key()
		{
			return key;
		}
	}

	private static final Minigame GOTR = new Minigame("Guardians of the Rift", SourceCategory.RUNECRAFTING);
	private static final Minigame TEMPOROSS = new Minigame("Tempoross", SourceCategory.FISHING);
	private static final Minigame WINTERTODT = new Minigame("Wintertodt", SourceCategory.FIREMAKING);
	private static final Minigame TRAWLER = new Minigame("Fishing Trawler", SourceCategory.FISHING);
	private static final Minigame SEPULCHRE = new Minigame("Hallowed Sepulchre", SourceCategory.AGILITY);
	private static final Minigame PLUNDER = new Minigame("Pyramid Plunder", SourceCategory.THIEVING);
	private static final Minigame RUMOURS = new Minigame("Hunter rumours", SourceCategory.HUNTER);
	private static final Minigame SOUL_WARS = new Minigame("Soul Wars", SourceCategory.OTHER);
	/** Motherlode Mine: the sack's ores join the same entry as the pay-dirt mined. */
	public static final Minigame MOTHERLODE = new Minigame(SkillGroups.MOTHERLODE, SourceCategory.MINING,
		"item:MINING:" + SkillGroups.MOTHERLODE);

	/** Reward objects and NPCs, by name. */
	private static final Map<String, Minigame> TARGETS = Map.ofEntries(
		Map.entry("rewards guardian", GOTR),
		Map.entry("reward pool", TEMPOROSS),
		Map.entry("reward cart", WINTERTODT),
		Map.entry("reward crate", WINTERTODT),
		Map.entry("trawler net", TRAWLER),
		Map.entry("reward net", TRAWLER),
		Map.entry("grand hallowed coffin", SEPULCHRE),
		Map.entry("grand gold chest", PLUNDER));

	/** Reward items you open, by the start of their name. */
	private static final Map<String, Minigame> ITEMS = Map.ofEntries(
		Map.entry("intricate pouch", GOTR),
		Map.entry("supply crate", WINTERTODT),
		Map.entry("hunters' loot sack", RUMOURS),
		Map.entry("spoils of war", SOUL_WARS));

	private Minigame active;
	private int untilTick;

	/** The minigame a click on this object or NPC pulls rewards from, or null. */
	public static Minigame forTarget(String target, String option)
	{
		if (isLookingOnly(option))
		{
			return null;
		}
		if (lower(target).equals("sack") && lower(option).equals("search"))
		{
			// The Motherlode Mine sack, where pay-dirt from the hopper comes out as ore.
			return MOTHERLODE;
		}
		return TARGETS.get(lower(target));
	}

	/** The minigame a reward item belongs to, or null. */
	public static Minigame forItem(String itemName, String option)
	{
		if (isLookingOnly(option))
		{
			return null;
		}
		String n = lower(itemName);
		for (Map.Entry<String, Minigame> e : ITEMS.entrySet())
		{
			if (n.startsWith(e.getKey()))
			{
				return e.getValue();
			}
		}
		return null;
	}

	private static boolean isLookingOnly(String option)
	{
		String o = lower(option);
		return o.isEmpty() || o.equals("examine") || o.equals("check") || o.equals("talk-to") || o.equals("inspect")
			|| o.equals("drop") || o.equals("use") || o.equals("walk here") || o.equals("cancel");
	}

	public void onRewardClicked(Minigame minigame, int tick)
	{
		active = minigame;
		untilTick = tick + WINDOW_TICKS;
	}

	/** Any other click ends the pull. */
	public void onOtherClick()
	{
		active = null;
	}

	public boolean isActive(int tick)
	{
		return active != null && tick <= untilTick;
	}

	/**
	 * Items received during a pull are that minigame's loot (the crate or pouch opened is
	 * not a cost). Returns true when the change was a reward and has been booked.
	 */
	public boolean onCarriedChanges(InventoryDiff diff, int tick, Ledger ledger)
	{
		if (!isActive(tick))
		{
			active = null;
			return false;
		}
		boolean gained = false;
		for (Map.Entry<Integer, Integer> change : diff.changes().entrySet())
		{
			if (change.getValue() > 0)
			{
				ledger.addLoot(active.key(), active.name, active.category, change.getKey(), change.getValue());
				gained = true;
			}
		}
		if (gained)
		{
			ledger.incrementCount(active.key(), active.name, active.category);
			untilTick = tick + WINDOW_TICKS;
		}
		return true;
	}

	public void reset()
	{
		active = null;
	}

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH).trim();
	}
}
