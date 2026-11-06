package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Which menu options start which kind of skilling, matched on the option text. */
public final class SkillRules
{
	private static final Map<String, String> FISHING_METHODS = Map.ofEntries(
		Map.entry("lure", "Fly fishing"),
		Map.entry("bait", "Bait fishing"),
		Map.entry("net", "Net fishing"),
		Map.entry("small net", "Net fishing"),
		Map.entry("big net", "Big net fishing"),
		Map.entry("cage", "Cage fishing"),
		Map.entry("harpoon", "Harpoon fishing"),
		Map.entry("use-rod", "Barbarian fishing"),
		Map.entry("fish", "Fishing"));

	/** Clicking a station with one of these options processes items. */
	private static final Set<String> PROCESS_OBJECT_OPTIONS = Set.of(
		"cook", "smelt", "smith", "spin", "weave", "craft", "form", "fire", "bake", "tan", "make", "fletch",
		"craft-rune", "bind", "offer", "pray-at", "build");

	/** Inventory item options that process the item directly. */
	private static final Set<String> PROCESS_ITEM_OPTIONS = Set.of("clean", "light", "crush", "grind", "bury", "scatter");

	private SkillRules()
	{
	}

	/** Gathering category for an option on a game object, or null. */
	public static SourceCategory gatherFromObject(String option)
	{
		String o = normalize(option);
		if (o.equals("chop down") || o.equals("chop") || o.equals("cut"))
		{
			return SourceCategory.WOODCUTTING;
		}
		if (o.equals("mine"))
		{
			return SourceCategory.MINING;
		}
		if (o.equals("steal-from") || o.equals("steal from"))
		{
			return SourceCategory.THIEVING;
		}
		if (FISHING_METHODS.containsKey(o))
		{
			return SourceCategory.FISHING;
		}
		return null;
	}

	/** Gathering category for an option on an NPC, or null. */
	public static SourceCategory gatherFromNpc(String option)
	{
		String o = normalize(option);
		if (FISHING_METHODS.containsKey(o))
		{
			return SourceCategory.FISHING;
		}
		if (o.equals("pickpocket"))
		{
			return SourceCategory.THIEVING;
		}
		if (o.equals("catch"))
		{
			return SourceCategory.HUNTER;
		}
		return null;
	}

	/** Fishing spots share a name, so fishing sources are named by method. */
	public static String fishingMethod(String option)
	{
		return FISHING_METHODS.getOrDefault(normalize(option), ItemRules.capitalize(normalize(option)));
	}

	public static boolean isProcessingObjectOption(String option)
	{
		return PROCESS_OBJECT_OPTIONS.contains(normalize(option));
	}

	public static boolean isProcessingItemOption(String option)
	{
		return PROCESS_ITEM_OPTIONS.contains(normalize(option));
	}

	/**
	 * A teleport spell in the spellbook, including diary-unlocked alternatives:
	 * "Cast" on Varrock Teleport, but also its "Grand Exchange" option, Camelot's
	 * "Seers'", Watchtower's "Yanille" and so on.
	 */
	public static boolean isTeleportSpell(String option, String target)
	{
		String o = normalize(option);
		return normalize(target).contains("teleport") && !o.isEmpty() && !o.equals("examine")
			&& !o.equals("autocast") && !o.equals("use");
	}

	/** Display name for a teleport spell: "Varrock Teleport" or "Varrock Teleport (Grand Exchange)". */
	public static String teleportSpellName(String option, String target)
	{
		String spell = target == null ? "Teleport" : target.trim();
		String o = normalize(option);
		return o.equals("cast") ? spell : spell + " (" + option.trim() + ")";
	}

	/**
	 * Options on objects and NPCs that are never skilling. Anything else gets a
	 * watch: if it gives XP (e.g. "Tend-to" on a Forester's campfire) it is tracked,
	 * and so is an item conversion done through an NPC or its dialogue (Talk-to the
	 * sawmill operator). Plain buying and selling are filtered out later.
	 */
	private static final Set<String> NOT_SKILLING = Set.of(
		"attack", "bank", "collect", "open", "close", "shut",
		"climb", "climb-up", "climb-down", "enter", "exit", "leave", "pass", "pass-through",
		"go-through", "cross", "jump", "jump-over", "jump-up", "jump-on", "balance", "leap",
		"vault", "hurdle", "squeeze-through", "walk-across", "walk-on", "swing-on", "grab",
		"teleport", "travel", "examine", "follow", "walk here", "exchange", "pay", "deposit",
		"use", "dismiss", "pick-up", "take", "search", "read", "look-at", "check",
		"history", "rewards", "loot", "claim", "board", "disembark", "quick-travel", "configure");

	public static boolean couldGiveSkillingXp(String option)
	{
		String o = normalize(option);
		return !o.isEmpty() && !NOT_SKILLING.contains(o);
	}

	/** Filling or emptying an essence or rune pouch only moves items around. */
	public static boolean isPouchShuffle(String itemName, String option)
	{
		return bagContents(itemName) != null && isBagOption(option);
	}

	private static boolean isBagOption(String option)
	{
		String o = normalize(option);
		return o.equals("fill") || o.equals("empty") || o.equals("deposit") || o.startsWith("empty ")
			|| o.equals("withdraw") || o.equals("take");
	}

	/**
	 * What a carried bag holds, as a test on item names, or null if this isn't a bag.
	 * Moving items between the bag and the inventory is never loot or a cost.
	 */
	public static java.util.function.Predicate<String> bagContents(String bagName)
	{
		String n = bagName == null ? "" : bagName.toLowerCase(Locale.ENGLISH);
		if (n.equals("rune pouch") || n.startsWith("rune pouch") || n.startsWith("divine rune pouch"))
		{
			return name -> ItemRules.isRune(name, true);
		}
		if (n.matches("(small|medium|large|giant|colossal) pouch.*"))
		{
			return name -> name.toLowerCase(Locale.ENGLISH).contains("essence");
		}
		if (n.contains("fish barrel") || n.contains("fish sack barrel"))
		{
			return SkillGroups::isFish;
		}
		if (n.contains("log basket") || n.contains("forestry basket"))
		{
			return name -> name.toLowerCase(Locale.ENGLISH).endsWith("logs");
		}
		if (n.contains("gem bag"))
		{
			return name -> name.toLowerCase(Locale.ENGLISH).startsWith("uncut ");
		}
		if (n.contains("herb sack"))
		{
			return name -> name.toLowerCase(Locale.ENGLISH).startsWith("grimy ");
		}
		if (n.contains("seed box"))
		{
			String[] seedy = {" seed", " seeds", " sapling", " spore", "acorn"};
			return name ->
			{
				String l = name.toLowerCase(Locale.ENGLISH);
				for (String suffix : seedy)
				{
					if (l.endsWith(suffix))
					{
						return true;
					}
				}
				return false;
			};
		}
		if (n.contains("coal bag"))
		{
			return name -> name.equalsIgnoreCase("coal") || name.toLowerCase(Locale.ENGLISH).endsWith(" ore");
		}
		return null;
	}

	private static String normalize(String option)
	{
		return option == null ? "" : option.trim().toLowerCase(Locale.ENGLISH);
	}
}
