package com.gildedledger.tracking;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Name-based rules for classifying items. Names are used instead of hard-coded
 * IDs so new potions, seeds and saplings work without a plugin update.
 */
public final class ItemRules
{
	private static final Pattern DOSE_SUFFIX = Pattern.compile("^(.*)\\((\\d)\\)$");
	private static final Pattern COMPOST_MESSAGE = Pattern.compile(
		"^You treat the .+ with (compost|supercompost|ultracompost)\\.?$", Pattern.CASE_INSENSITIVE);

	private ItemRules()
	{
	}

	/** "Prayer potion(3)" → 3. Items without a dose suffix count as one use. */
	public static int doses(String name)
	{
		if (name == null)
		{
			return 1;
		}
		Matcher m = DOSE_SUFFIX.matcher(name.trim());
		if (!m.matches())
		{
			return 1;
		}
		int doses = m.group(2).charAt(0) - '0';
		return doses >= 1 && doses <= 6 ? doses : 1;
	}

	/** "Prayer potion(3)" → "Prayer potion". */
	public static String withoutDoses(String name)
	{
		if (name == null)
		{
			return "";
		}
		Matcher m = DOSE_SUFFIX.matcher(name.trim());
		return m.matches() ? m.group(1).trim() : name.trim();
	}

	/** Value of one sip, from the price of the potion actually drunk. */
	public static long perDoseValue(long itemPrice, String name)
	{
		if (itemPrice <= 0)
		{
			return 0;
		}
		return Math.max(1, itemPrice / doses(name));
	}

	public static boolean isRune(String name, boolean stackable)
	{
		return stackable && name != null && name.toLowerCase(Locale.ENGLISH).endsWith(" rune");
	}

	/**
	 * Teleport tablets (including house tabs with Inside/Outside), teleport
	 * scrolls and similar. Matched broadly: the cost is only charged once the
	 * item actually leaves the inventory, so options that don't consume it cost nothing.
	 */
	public static boolean isTeleportConsumable(String name, String option)
	{
		if (name == null || option == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH);
		String o = option.toLowerCase(Locale.ENGLISH).trim();
		boolean teleportItem = (n.contains("teleport") || n.contains("tablet") || n.endsWith(" tab")
			|| n.contains(" scroll")) && !n.contains("teleport crystal");
		boolean consumingOption = !o.isEmpty() && !o.equals("drop") && !o.equals("examine") && !o.equals("use")
			&& !o.equals("destroy") && !o.equals("check") && !o.equals("wield") && !o.equals("wear")
			&& !o.equals("remove") && !o.equals("cancel");
		return teleportItem && consumingOption;
	}

	/** Seeds, saplings and other things you put into a patch. */
	public static boolean isPlantable(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH);
		return n.endsWith(" seed") || n.endsWith(" sapling") || n.endsWith(" acorn")
			|| n.endsWith(" spore") || n.endsWith(" seeds");
	}

	/** Money-like items: paying or receiving these is buying or selling, not processing. */
	public static boolean isCurrency(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH);
		return n.equals("coins") || n.equals("platinum token") || n.equals("tokkul") || n.equals("trading sticks")
			|| n.equals("numulite") || n.equals("mark of grace") || n.equals("ecto-token") || n.equals("warrior guild token");
	}

	/** Reward currencies: never worth anything in the log, whatever their alch price. */
	private static final Set<String> ZERO_VALUE_CURRENCIES = Set.of(
		"mark of grace", "abyssal pearls", "molch pearl", "tokkul", "numulite", "trading sticks", "ecto-token",
		"warrior guild token", "golden nugget", "unidentified minerals", "stardust", "hallowed mark",
		"mermaid's tear", "termites", "agility arena ticket", "castle wars ticket", "pieces of eight",
		"brimhaven voucher", "soul fragment", "frozen tear", "minnow", "barronite shards");

	/**
	 * Items valued at nothing: clue scrolls, caskets and their containers (their alch
	 * price says nothing about the reward), and reward currencies like marks of grace.
	 */
	public static boolean isZeroValue(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH).trim();
		return n.startsWith("clue scroll") || n.startsWith("clue bottle") || n.startsWith("clue geode")
			|| n.startsWith("clue nest") || n.startsWith("scroll box") || n.startsWith("reward casket")
			|| n.startsWith("challenge scroll") || n.startsWith("puzzle box") || n.startsWith("light box")
			|| ZERO_VALUE_CURRENCIES.contains(n);
	}

	/** Items that only exist inside a minigame and are gone when it ends. */
	private static final Set<String> MINIGAME_ITEMS = Set.of(
		// Guardians of the Rift
		"guardian essence", "guardian fragments", "elemental guardian stone", "catalytic guardian stone",
		"polyelemental guardian stone", "uncharged cell", "weak cell", "medium cell", "strong cell", "overcharged cell",
		"portal talisman (air)", "portal talisman (water)", "portal talisman (earth)", "portal talisman (fire)",
		"portal talisman (mind)", "portal talisman (chaos)", "portal talisman (death)", "portal talisman (blood)",
		"portal talisman (body)", "portal talisman (cosmic)", "portal talisman (nature)", "portal talisman (law)",
		// Wintertodt
		"bruma root", "bruma kindling", "bruma herb", "rejuvenation potion (unf)",
		// Tempoross
		"raw harpoonfish", "harpoonfish", "bucket of water (tempoross)",
		// Zalcano
		"tephra", "refined tephra", "imbued tephra",
		// Tithe Farm
		"golovanova seed", "bologano seed", "logavano seed", "golovanova fruit", "bologano fruit", "logavano fruit",
		"gricoller's can",
		// Soul Wars and Castle Wars
		"bandages", "barricade", "explosive potion", "castle wars bracelet",
		// Mage Training Arena
		"cube", "cylinder", "icosahedron", "pentamid", "animals' bones",
		// Volcanic Mine and Giants' Foundry
		"volcanic mine heat-proof vessel", "preform",
		// Tombs of Amascut
		"nectar", "silk dressing", "ambrosia", "tears of elidinis", "smelling salts", "liquid adrenaline",
		"blessed crystal scarab", "honey locust",
		// Chambers of Xeric
		"stinkhorn mushroom", "endarkened juice", "cicely", "golpar", "buchu leaf", "noxifer", "grimy golpar",
		"grimy buchu leaf", "grimy noxifer", "pysk fish (0)", "suphi fish (1)", "leckish fish (2)",
		"brawk fish (3)", "mycil fish (4)", "roqed fish (5)", "kyren fish (6)", "guanic bat (0)", "prael bat (1)",
		"giral bat (2)", "phluxia bat (3)", "kryket bat (4)", "murng bat (5)", "psykk bat (6)");

	/** Minigame-only items: never loot, never a cost. */
	public static boolean isMinigameItem(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = withoutDoses(name).toLowerCase(Locale.ENGLISH).trim();
		// Chambers of Xeric potions are marked (+), (-) or plain ("Overload (+)(4)", "Elder (-)(2)").
		return MINIGAME_ITEMS.contains(n) || n.endsWith("(+)") || n.endsWith("(-)")
			|| n.startsWith("rejuvenation potion") || n.startsWith("potion of power")
			|| n.startsWith("cooked pysk") || n.startsWith("cooked suphi") || n.startsWith("cooked leckish")
			|| n.startsWith("cooked brawk") || n.startsWith("cooked mycil") || n.startsWith("cooked roqed")
			|| n.startsWith("cooked kyren") || n.startsWith("raw pysk") || n.startsWith("raw suphi")
			|| n.startsWith("raw leckish") || n.startsWith("raw brawk") || n.startsWith("raw mycil")
			|| n.startsWith("raw roqed") || n.startsWith("raw kyren");
	}

	/** Tarnished items (a tarnished ring polishes into a real ring), valued at their alch price. */
	public static boolean isTarnished(String name)
	{
		return name != null && name.toLowerCase(Locale.ENGLISH).startsWith("tarnished");
	}

	/** Runecrafting essence pouches, degraded or not; they degrade and get repaired. */
	public static boolean isEssencePouch(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH).trim();
		return n.matches("(small|medium|large|giant|colossal) pouch.*");
	}

	public static boolean isCompost(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH);
		return n.equals("compost") || n.equals("supercompost") || n.equals("ultracompost");
	}

	/** Returns the compost name from "You treat the herb patch with ultracompost.", or null. */
	public static String compostFromMessage(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = COMPOST_MESSAGE.matcher(message.trim());
		return m.matches() ? capitalize(m.group(1).toLowerCase(Locale.ENGLISH)) : null;
	}

	/** Tools and containers that move in and out of the inventory during farming and are never loot. */
	public static boolean isFarmingTool(String name)
	{
		if (name == null)
		{
			return true;
		}
		String n = name.toLowerCase(Locale.ENGLISH);
		return n.startsWith("watering can") || n.contains("compost bucket") || n.equals("bucket")
			|| n.equals("empty plant pot") || n.equals("plant pot") || n.equals("spade") || n.equals("rake")
			|| n.equals("seed dibber") || n.contains("secateurs") || n.equals("gardening trowel")
			|| n.equals("weeds") || n.startsWith("plant cure");
	}

	/** Crop name from what was planted: "Ranarr seed" → "Ranarr", "Magic sapling" → "Magic". */
	public static String cropFromPlantable(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim();
		String[] suffixes = {" seeds", " seed", " sapling", " acorn", " spore"};
		for (String suffix : suffixes)
		{
			if (n.toLowerCase(Locale.ENGLISH).endsWith(suffix))
			{
				return capitalize(n.substring(0, n.length() - suffix.length()).trim());
			}
		}
		return capitalize(n);
	}

	/**
	 * Crop name from the patch object's current appearance, used when the plugin
	 * never saw the planting. Generic appearances return null.
	 */
	public static String cropFromPatchAppearance(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim();
		String lower = n.toLowerCase(Locale.ENGLISH);
		for (String prefix : new String[]{"diseased ", "dead "})
		{
			if (lower.startsWith(prefix))
			{
				n = n.substring(prefix.length());
				lower = lower.substring(prefix.length());
			}
		}
		if (lower.isEmpty() || lower.equals("null") || lower.equals("herbs") || lower.contains("patch")
			|| lower.contains("stump") || lower.equals("weeds") || lower.equals("tree") || lower.equals("bush"))
		{
			return null;
		}
		for (String suffix : new String[]{" tree", " bush", " plant"})
		{
			if (lower.endsWith(suffix))
			{
				n = n.substring(0, n.length() - suffix.length());
				break;
			}
		}
		return capitalize(n.trim());
	}

	/** "Fruit Tree Patch" → "Fruit tree"; "Herb patch" → "Herb". */
	public static String patchCategory(String baseObjectName)
	{
		if (baseObjectName == null)
		{
			return "Farming";
		}
		String n = baseObjectName.trim().toLowerCase(Locale.ENGLISH);
		if (n.endsWith(" patch"))
		{
			n = n.substring(0, n.length() - " patch".length());
		}
		n = n.trim();
		if (n.isEmpty() || n.equals("null"))
		{
			return "Farming";
		}
		return capitalize(n);
	}

	public static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
