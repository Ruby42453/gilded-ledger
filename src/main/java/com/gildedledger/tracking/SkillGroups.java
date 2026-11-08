package com.gildedledger.tracking;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How skilling actions are grouped in the panel:
 * <ul>
 *   <li>Runecrafting by altar ("Death altar"); anything that isn't an altar's rune is "Other".</li>
 *   <li>Fishing by the fish a spot gives ("Trout &amp; salmon", "Karambwanji").</li>
 *   <li>Mining by rock or vein ("Iron rocks", "Gem rocks").</li>
 *   <li>Hunter by the creature caught ("Red chinchompa", "Crimson swift").</li>
 *   <li>Herblore by potion, whatever the dose ("Prayer potion"), or by herb when cleaning.</li>
 * </ul>
 */
public final class SkillGroups
{
	public static final String OTHER = "Other";
	public static final String MOTHERLODE = "Motherlode Mine";
	public static final String VALE_TOTEMS = "Vale Totems";

	private static final Set<String> ALTAR_RUNES = Set.of(
		"air", "mind", "water", "earth", "fire", "body", "cosmic", "chaos", "astral", "nature", "law", "death",
		"blood", "soul", "wrath");

	/** Combination runes and the two altars they can be made at. */
	private static final Map<String, String[]> COMBINATION_RUNES = Map.of(
		"mist", new String[]{"air", "water"},
		"dust", new String[]{"air", "earth"},
		"mud", new String[]{"water", "earth"},
		"smoke", new String[]{"air", "fire"},
		"steam", new String[]{"water", "fire"},
		"lava", new String[]{"earth", "fire"});

	private static final Map<String, String> FISH_GROUPS = Map.ofEntries(
		Map.entry("shrimps", "Shrimps & anchovies"), Map.entry("anchovies", "Shrimps & anchovies"),
		Map.entry("sardine", "Sardine & herring"), Map.entry("herring", "Sardine & herring"),
		Map.entry("trout", "Trout & salmon"), Map.entry("salmon", "Trout & salmon"),
		Map.entry("tuna", "Tuna & swordfish"), Map.entry("swordfish", "Tuna & swordfish"),
		Map.entry("mackerel", "Mackerel, cod & bass"), Map.entry("cod", "Mackerel, cod & bass"),
		Map.entry("bass", "Mackerel, cod & bass"),
		Map.entry("leaping trout", "Barbarian fishing"), Map.entry("leaping salmon", "Barbarian fishing"),
		Map.entry("leaping sturgeon", "Barbarian fishing"));

	private static final Map<String, String> HUNTER_ITEMS = Map.ofEntries(
		Map.entry("red feather", "Crimson swift"), Map.entry("yellow feather", "Golden warbler"),
		Map.entry("orange feather", "Copper longtail"), Map.entry("blue feather", "Cerulean twitch"),
		Map.entry("stripy feather", "Tropical wagtail"),
		Map.entry("chinchompa", "Grey chinchompa"),
		Map.entry("polar kebbit fur", "Polar kebbit"), Map.entry("common kebbit fur", "Common kebbit"),
		Map.entry("feldip weasel fur", "Feldip weasel"), Map.entry("desert devil fur", "Desert devil"),
		Map.entry("spotted kebbit fur", "Spotted kebbit"), Map.entry("dark kebbit fur", "Dark kebbit"),
		Map.entry("dashing kebbit fur", "Dashing kebbit"), Map.entry("tatty larupia fur", "Spined larupia"),
		Map.entry("larupia fur", "Spined larupia"), Map.entry("tatty graahk fur", "Horned graahk"),
		Map.entry("graahk fur", "Horned graahk"), Map.entry("tatty kyatt fur", "Sabre-toothed kyatt"),
		Map.entry("kyatt fur", "Sabre-toothed kyatt"), Map.entry("swamp lizard", "Swamp lizard"),
		Map.entry("orange salamander", "Orange salamander"), Map.entry("red salamander", "Red salamander"),
		Map.entry("black salamander", "Black salamander"), Map.entry("tecu salamander", "Tecu salamander"));

	private SkillGroups()
	{
	}

	/**
	 * The altar a craft happened at.
	 *
	 * @param madeNames    every item made in the action
	 * @param runesUsed    names of runes used up (a combination rune's altar is the element <i>not</i> used)
	 */
	public static String runecraftingAltar(Collection<String> madeNames, Collection<String> runesUsed)
	{
		long runeTypes = madeNames.stream().filter(n -> lower(n).endsWith(" rune")).count();
		if (runeTypes > 1)
		{
			// Several kinds of rune at once: the Ourania (ZMI) altar.
			return "Ourania altar";
		}
		for (String made : madeNames)
		{
			String altar = altarFor(made, runesUsed);
			if (!OTHER.equals(altar))
			{
				return altar;
			}
		}
		return OTHER;
	}

	/** The altar for one item made, or "Other". */
	public static String altarFor(String made, Collection<String> runesUsed)
	{
		String n = lower(made);
		if (n.startsWith("dark essence"))
		{
			return "Dark altar";
		}
		String element = null;
		if (n.endsWith(" rune"))
		{
			element = n.substring(0, n.length() - " rune".length()).trim();
		}
		else if (n.endsWith(" tiara"))
		{
			element = n.substring(0, n.length() - " tiara".length()).trim();
		}
		if (element == null)
		{
			return OTHER;
		}
		if (ALTAR_RUNES.contains(element))
		{
			return ItemRules.capitalize(element) + " altar";
		}
		String[] pair = COMBINATION_RUNES.get(element);
		if (pair != null && runesUsed != null)
		{
			boolean usedFirst = runesUsed.stream().anyMatch(r -> lower(r).equals(pair[0] + " rune"));
			boolean usedSecond = runesUsed.stream().anyMatch(r -> lower(r).equals(pair[1] + " rune"));
			if (usedFirst != usedSecond)
			{
				return ItemRules.capitalize(usedFirst ? pair[1] : pair[0]) + " altar";
			}
		}
		return OTHER;
	}

	/** Mist, dust, mud, smoke, steam and lava runes. */
	public static boolean isCombinationRune(String name)
	{
		String n = lower(name);
		return n.endsWith(" rune") && COMBINATION_RUNES.containsKey(n.substring(0, n.length() - " rune".length()));
	}

	/** Is this a fish (rather than a casket or seaweed from the same spot)? */
	public static boolean isFish(String name)
	{
		String n = lower(name);
		return n.startsWith("raw ") || n.startsWith("leaping ") || n.equals("karambwanji") || n.equals("minnow")
			|| n.endsWith(" eel") || n.equals("frog spawn");
	}

	/** "Raw trout" and "Raw salmon" → "Trout &amp; salmon"; "Raw karambwanji" → "Karambwanji". */
	public static String fishGroup(String fishName)
	{
		String n = lower(fishName);
		if (n.startsWith("raw "))
		{
			n = n.substring(4);
		}
		String group = FISH_GROUPS.get(n);
		return group != null ? group : ItemRules.capitalize(n);
	}

	/**
	 * The rock or vein mined: the object's name when it has a useful one ("Iron rocks"),
	 * otherwise worked out from the ore.
	 */
	public static String miningRock(String targetName, String oreName)
	{
		String t = lower(targetName);
		if (t.equals("ore vein") || t.equals("depleted vein"))
		{
			return MOTHERLODE;
		}
		if (!t.isEmpty() && !t.equals("rocks") && !t.equals("rock"))
		{
			return ItemRules.capitalize(t);
		}
		return rockFromOre(oreName);
	}

	/** An altar group name ("Death altar", "Ourania altar", "Other"), as opposed to an item. */
	public static boolean isAltarGroup(String name)
	{
		String n = lower(name);
		return n.endsWith(" altar") || n.equals(lower(OTHER));
	}

	/** An ore or gem name, as opposed to a rock group like "Iron rocks". */
	public static boolean isOre(String name)
	{
		String n = lower(name);
		return n.endsWith(" ore") || n.equals("coal") || n.equals("clay") || n.startsWith("uncut ")
			|| n.equals("pay-dirt") || n.equals("ore vein") || n.startsWith("granite (") || n.startsWith("sandstone (") || n.equals("amethyst");
	}

	/** "Iron ore" → "Iron rocks", "Uncut ruby" → "Gem rocks", "Pay-dirt" → "Ore vein". */
	public static String rockFromOre(String oreName)
	{
		String n = lower(oreName);
		if (n.isEmpty())
		{
			return OTHER;
		}
		if (n.startsWith("uncut "))
		{
			return "Gem rocks";
		}
		if (n.equals("pay-dirt") || n.equals("ore vein"))
		{
			return MOTHERLODE;
		}
		if (n.startsWith("granite ("))
		{
			return "Granite rocks";
		}
		if (n.startsWith("sandstone ("))
		{
			return "Sandstone rocks";
		}
		if (n.equals("amethyst"))
		{
			return "Amethyst crystals";
		}
		if (n.endsWith(" ore"))
		{
			n = n.substring(0, n.length() - " ore".length());
		}
		return ItemRules.capitalize(n) + " rocks";
	}

	/** Trap objects, which aren't the creature. */
	public static boolean isTrap(String targetName)
	{
		String t = lower(targetName);
		return t.contains("trap") || t.contains("snare") || t.contains("box") || t.contains("net")
			|| t.contains("deadfall") || t.contains("pitfall") || t.contains("boulder") || t.contains("birdhouse")
			|| t.contains("young tree") || t.contains("bird house");
	}

	/** The creature behind a Hunter item: "Red feather" → "Crimson swift", "Baby impling jar" → "Baby impling". */
	public static String creatureFromItem(String itemName)
	{
		String n = lower(itemName);
		if (n.isEmpty())
		{
			return OTHER;
		}
		String creature = HUNTER_ITEMS.get(n);
		if (creature != null)
		{
			return creature;
		}
		if (n.endsWith(" jar"))
		{
			return ItemRules.capitalize(n.substring(0, n.length() - " jar".length()));
		}
		return ItemRules.capitalize(n);
	}

	/** "You've caught a crimson swift." → "Crimson swift". */
	public static String creatureFromMessage(String message)
	{
		if (message == null)
		{
			return null;
		}
		java.util.regex.Matcher m = CAUGHT.matcher(message.trim());
		return m.matches() ? ItemRules.capitalize(m.group(1).trim().toLowerCase(Locale.ENGLISH)) : null;
	}

	private static final java.util.regex.Pattern CAUGHT = java.util.regex.Pattern.compile(
		"You(?:'ve| have)? (?:caught|catch) (?:an? |the |some )?(.+?)[.!]?", java.util.regex.Pattern.CASE_INSENSITIVE);

	/**
	 * The item a gathering message says you got, as candidate item names (best first):
	 * "You catch some shrimps." → [Raw shrimps, Shrimps, Raw shrimp, Shrimp];
	 * "You get some yew logs." → [Yew logs]; "You just found a Ruby!" → [Uncut ruby].
	 * Empty if it isn't one. Used for catches that go straight into a fish barrel,
	 * log basket or gem bag, which never show up in the inventory.
	 */
	public static List<String> caughtItemNames(String message)
	{
		if (message == null)
		{
			return Collections.emptyList();
		}
		String m = message.trim();
		java.util.regex.Matcher fish = CATCH_FISH.matcher(m);
		if (fish.matches())
		{
			String x = fish.group(1).trim().toLowerCase(Locale.ENGLISH);
			String one = x.endsWith("s") ? x.substring(0, x.length() - 1) : x;
			return List.of(ItemRules.capitalize("raw " + x), ItemRules.capitalize(x),
				ItemRules.capitalize("raw " + one), ItemRules.capitalize(one));
		}
		java.util.regex.Matcher logs = GET_LOGS.matcher(m);
		if (logs.matches())
		{
			return List.of(ItemRules.capitalize(logs.group(1).trim().toLowerCase(Locale.ENGLISH)));
		}
		java.util.regex.Matcher gem = FOUND_GEM.matcher(m);
		if (gem.matches())
		{
			return List.of("Uncut " + gem.group(1).trim().toLowerCase(Locale.ENGLISH));
		}
		return Collections.emptyList();
	}

	private static final java.util.regex.Pattern CATCH_FISH = java.util.regex.Pattern.compile(
		"You catch (?:a |an |some )(.+?)[.!]", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern GET_LOGS = java.util.regex.Pattern.compile(
		"You get some (.*?logs)\\b.*", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern FOUND_GEM = java.util.regex.Pattern.compile(
		"You just (?:found|mined) (?:a |an )(.+?)!", java.util.regex.Pattern.CASE_INSENSITIVE);

	/** Messages saying a catch was burnt or smelted straight away (infernal tools), so it wasn't kept. */
	public static boolean isDestroyedCatch(String message)
	{
		String m = lower(message);
		return m.contains("incinerat") || m.contains("burn") || m.contains("smelt") || m.contains("infernal");
	}

	/** "Prayer potion(3)" → "Prayer potion"; a cleaned herb keeps its name. */
	public static String herblore(String itemName)
	{
		return ItemRules.withoutDoses(itemName);
	}

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH).trim();
	}
}
