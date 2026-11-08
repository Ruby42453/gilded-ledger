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
		// Tears of Guthix
		"stone bowl",
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

	/** What's left in the inventory after eating or drinking: empty containers, and what's left of a pie or pizza. */
	private static final Set<String> CONTAINERS = Set.of(
		"vial", "pie dish", "bowl", "jug", "empty cup", "beer glass", "cocktail glass", "empty jug", "bucket",
		"cake tin", "empty bowl");

	/**
	 * Is {@code gained} what's left of {@code eaten} (doses already removed)? "Half a dragonfruit
	 * pie" after a dragonfruit pie, "1/2 anchovy pizza", "Slice of cake", a vial or a pie dish.
	 */
	public static boolean isLeftover(String eaten, String gained)
	{
		if (eaten == null || gained == null)
		{
			return false;
		}
		String e = eaten.toLowerCase(Locale.ENGLISH).replace("half a ", "").replace("1/2 ", "").trim();
		String g = withoutDoses(gained).toLowerCase(Locale.ENGLISH).trim();
		if (CONTAINERS.contains(g) || g.equals(e))
		{
			return true;
		}
		if (e.endsWith("cake") && (g.endsWith("cake") || g.startsWith("slice of")))
		{
			return true;
		}
		return g.contains(e) && (g.startsWith("half a ") || g.startsWith("1/2 ") || g.startsWith("2/3 "));
	}

	/**
	 * Prayer XP an ash sanctifier gives (half of scattering fiery, vile, malicious, abyssal
	 * or infernal ashes), allowing for XP rounding.
	 */
	public static boolean isAshSanctifierXp(long xp)
	{
		for (double half : new double[]{5, 12.5, 32.5, 42.5, 55})
		{
			if (Math.abs(xp - half) <= 0.5)
			{
				return true;
			}
		}
		return false;
	}

	/** Bosses, whose fights can outlast the normal before-the-fight window. */
	private static final Set<String> BOSSES = Set.of(
		"abyssal sire", "alchemical hydra", "amoxliatl", "araxxor", "artio", "barrows", "bryophyta", "callisto",
		"calvar'ion", "cerberus", "chaos elemental", "chaos fanatic", "commander zilyana", "corporeal beast",
		"crazy archaeologist", "dagannoth prime", "dagannoth rex", "dagannoth supreme", "deranged archaeologist",
		"duke sucellus", "general graardor", "giant mole", "grotesque guardians", "dusk", "dawn", "hespori",
		"kalphite queen", "king black dragon", "kraken", "kree'arra", "k'ril tsutsaroth", "the leviathan",
		"leviathan", "the whisperer", "whisperer", "vardorvis", "nex", "the nightmare", "phosani's nightmare",
		"obor", "phantom muspah", "sarachnis", "scorpia", "scurrius", "skotizo", "spindel", "tempoross",
		"thermonuclear smoke devil", "vene natis", "venenatis", "vet'ion", "vorkath", "zalcano", "zulrah",
		"tztok-jad", "tzkal-zuk", "sol heredit", "the hueycoatl", "hueycoatl", "lunar moons", "blood moon",
		"blue moon", "eclipse moon", "the royal titans", "royal titans", "branda the fire queen", "eldric the ice king",
		"yama", "doom of mokhaiotl", "kalphite king", "galvek", "verzik vitur", "great olm",
		"tumeken's warden", "elidinis' warden", "kephri", "zebak", "akkha", "ba-ba");

	/** Monsters that are part of a boss fight, filed under the boss itself. */
	private static final java.util.Map<String, String> BOSS_PARTS = java.util.Map.ofEntries(
		java.util.Map.entry("flower", "Hespori"),
		java.util.Map.entry("zombified spawn", "Vorkath"),
		java.util.Map.entry("snakeling", "Zulrah"),
		java.util.Map.entry("enormous tentacle", "Kraken"),
		java.util.Map.entry("summoned soul", "Cerberus"),
		java.util.Map.entry("respiratory system", "Abyssal Sire"),
		java.util.Map.entry("scion", "Abyssal Sire"),
		java.util.Map.entry("dusk", "Grotesque Guardians"),
		java.util.Map.entry("dawn", "Grotesque Guardians"),
		java.util.Map.entry("husk", "The Nightmare"),
		java.util.Map.entry("parasite", "The Nightmare"),
		java.util.Map.entry("dark energy core", "Corporeal Beast"),
		java.util.Map.entry("spawn of sarachnis", "Sarachnis"),
		java.util.Map.entry("skeleton hellhound", "Vet'ion"),
		java.util.Map.entry("greater skeleton hellhound", "Vet'ion"),
		java.util.Map.entry("scorpia's offspring", "Scorpia"),
		java.util.Map.entry("scorpia's guardian", "Scorpia"),
		java.util.Map.entry("vardorvis' head", "Vardorvis"),
		java.util.Map.entry("yt-hurkot", "TzTok-Jad"),
		java.util.Map.entry("sergeant strongstack", "General Graardor"),
		java.util.Map.entry("sergeant steelwill", "General Graardor"),
		java.util.Map.entry("sergeant grimspike", "General Graardor"),
		java.util.Map.entry("starlight", "Commander Zilyana"),
		java.util.Map.entry("growler", "Commander Zilyana"),
		java.util.Map.entry("bree", "Commander Zilyana"),
		java.util.Map.entry("flight kilisa", "Kree'arra"),
		java.util.Map.entry("flockleader geerin", "Kree'arra"),
		java.util.Map.entry("wingman skree", "Kree'arra"),
		java.util.Map.entry("tstanon karlak", "K'ril Tsutsaroth"),
		java.util.Map.entry("zakl'n gritch", "K'ril Tsutsaroth"),
		java.util.Map.entry("balfrug kreeyath", "K'ril Tsutsaroth"),
		java.util.Map.entry("fumus", "Nex"),
		java.util.Map.entry("umbra", "Nex"),
		java.util.Map.entry("cruor", "Nex"),
		java.util.Map.entry("glacies", "Nex"),
		java.util.Map.entry("branda the fire queen", "Royal Titans"),
		java.util.Map.entry("eldric the ice king", "Royal Titans"),
		java.util.Map.entry("hueycoatl tail", "The Hueycoatl"),
		java.util.Map.entry("hueycoatl body", "The Hueycoatl"),
		java.util.Map.entry("ahrim the blighted", "Barrows"),
		java.util.Map.entry("dharok the wretched", "Barrows"),
		java.util.Map.entry("guthan the infested", "Barrows"),
		java.util.Map.entry("karil the tainted", "Barrows"),
		java.util.Map.entry("torag the corrupted", "Barrows"),
		java.util.Map.entry("verac the defiled", "Barrows"),
		java.util.Map.entry("great olm (left claw)", "Great Olm"),
		java.util.Map.entry("great olm (right claw)", "Great Olm"));

	/** The boss a monster belongs to (Hespori's flowers → Hespori), or the name unchanged. */
	public static String bossGroup(String npcName)
	{
		if (npcName == null)
		{
			return null;
		}
		String boss = BOSS_PARTS.get(npcName.toLowerCase(Locale.ENGLISH).trim());
		return boss != null ? boss : npcName;
	}

	/** Is this NPC a boss? */
	public static boolean isBoss(String npcName)
	{
		return npcName != null && BOSSES.contains(npcName.toLowerCase(Locale.ENGLISH).trim());
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

	/**
	 * Items that change into another version of themselves as they're used (a slayer
	 * ring losing a charge, an essence pouch degrading): neither loot nor a cost.
	 */
	public static boolean isDegrading(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ENGLISH).trim();
		return isEssencePouch(name) || n.startsWith("slayer ring");
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
