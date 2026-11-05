package com.gildedledger.tracking;

import java.util.Locale;
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
