package com.gildedledger;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Supplies used by weapons and armour that hold their own charges, so nothing
 * leaves the inventory when they're used. Each attack is priced at what a charge
 * costs to put back in (per-charge costs from the OSRS Wiki).
 *
 * <p>Attack detection follows the Supplies Tracker plugin: staves and other
 * charged weapons are counted when their attack animation starts, and the
 * blowpipe, whose attacks are too quick for that, on the first frame each tick.
 * Weapons are matched by name, so ornament, (e) and holy variants all count.
 */
final class ChargedWeapons
{
	/** Attack animations, from the Supplies Tracker plugin. */
	static final int HIGH_LEVEL_MAGIC_ATTACK = 1167;
	static final int POWERED_STAFF_ATTACK = 11430;
	static final int SHADOW_ATTACK = 9493;
	static final int SCYTHE_ATTACK = 8056;
	static final int BOW_ATTACK = 426;
	static final int WEBWEAVER_SPECIAL = 9964;
	static final int ACCURSED_SPECIAL = 9961;
	static final int CHAINMACE_ATTACK = 245;
	static final int CHAINMACE_SPECIAL = 9963;
	static final int BLOWPIPE_ATTACK = 5061;
	static final int BLOWPIPE_ORNAMENT_ATTACK = 10656;

	private static final Set<Integer> STAFF_ATTACKS = Set.of(HIGH_LEVEL_MAGIC_ATTACK, POWERED_STAFF_ATTACK);

	/** Blowpipe dart projectiles → the dart loaded, from the Supplies Tracker plugin. */
	static final Map<Integer, Integer> BLOWPIPE_DART_PROJECTILES = Map.of(
		226, ItemID.BRONZE_DART,
		227, ItemID.IRON_DART,
		228, ItemID.STEEL_DART,
		32, ItemID.BLACK_DART,
		229, ItemID.MITHRIL_DART,
		230, ItemID.ADAMANT_DART,
		231, ItemID.RUNE_DART,
		1936, ItemID.AMETHYST_DART,
		1122, ItemID.DRAGON_DART);

	/** Revenant ether (its game name is "wild cave shard"). */
	private static final int REVENANT_ETHER = ItemID.WILD_CAVE_SHARD;

	/** Cost parts are {itemId, quantity, per}: "quantity" of the item every "per" attacks. */
	private static final List<int[]> TRIDENT_OF_THE_SEAS = List.of(
		part(ItemID.DEATHRUNE, 1), part(ItemID.CHAOSRUNE, 1), part(ItemID.FIRERUNE, 5), part(ItemID.COINS, 10));
	private static final List<int[]> TRIDENT_OF_THE_SWAMP = List.of(
		part(ItemID.DEATHRUNE, 1), part(ItemID.CHAOSRUNE, 1), part(ItemID.FIRERUNE, 5), part(ItemID.SNAKEBOSS_SCALE, 1));
	private static final List<int[]> SANGUINESTI = List.of(part(ItemID.BLOODRUNE, 3));
	private static final List<int[]> TUMEKENS_SHADOW = List.of(part(ItemID.SOULRUNE, 2), part(ItemID.CHAOSRUNE, 5));
	private static final List<int[]> WARPED_SCEPTRE = List.of(part(ItemID.CHAOSRUNE, 2), part(ItemID.EARTHRUNE, 5));
	/** 300 blood runes and a vial of blood refill 100 charges. */
	private static final List<int[]> SCYTHE = List.of(part(ItemID.BLOODRUNE, 3), new int[]{ItemID.VIAL_BLOOD, 1, 100});
	private static final List<int[]> ETHER = List.of(part(REVENANT_ETHER, 1));

	private ChargedWeapons()
	{
	}

	private static int[] part(int itemId, int quantity)
	{
		return new int[]{itemId, quantity, 1};
	}

	/**
	 * What one attack costs with this weapon when the given animation starts, as
	 * {itemId, quantity, per} parts. Empty when it isn't a charged weapon's attack.
	 */
	static List<int[]> costPerAttack(String weaponName, int animation)
	{
		String n = lower(weaponName);
		if (n.isEmpty() || n.contains("uncharged") || n.endsWith("(u)"))
		{
			return Collections.emptyList();
		}
		if (STAFF_ATTACKS.contains(animation))
		{
			if (n.contains("trident of the swamp"))
			{
				return TRIDENT_OF_THE_SWAMP;
			}
			if (n.contains("trident of the seas"))
			{
				return TRIDENT_OF_THE_SEAS;
			}
			if (n.contains("sanguinesti staff"))
			{
				return SANGUINESTI;
			}
			if (n.contains("warped sceptre"))
			{
				return WARPED_SCEPTRE;
			}
		}
		if ((STAFF_ATTACKS.contains(animation) || animation == ACCURSED_SPECIAL)
			&& (n.contains("thammaron's sceptre") || n.contains("accursed sceptre")))
		{
			return ETHER;
		}
		if (animation == SHADOW_ATTACK && n.contains("tumeken's shadow"))
		{
			return TUMEKENS_SHADOW;
		}
		if (animation == SCYTHE_ATTACK && n.contains("scythe of vitur"))
		{
			return SCYTHE;
		}
		if ((animation == BOW_ATTACK || animation == WEBWEAVER_SPECIAL)
			&& (n.contains("craw's bow") || n.contains("webweaver bow")))
		{
			return ETHER;
		}
		if ((animation == CHAINMACE_ATTACK || animation == CHAINMACE_SPECIAL)
			&& (n.contains("viggora's chainmace") || n.contains("ursine chainmace")))
		{
			return ETHER;
		}
		return Collections.emptyList();
	}

	static boolean isBlowpipe(String weaponName)
	{
		String n = lower(weaponName);
		return n.endsWith("blowpipe") && !n.contains("empty");
	}

	static boolean isBlowpipeAttack(int animation)
	{
		return animation == BLOWPIPE_ATTACK || animation == BLOWPIPE_ORNAMENT_ATTACK;
	}

	/**
	 * Share of blowpipe darts lost per shot, by the Ava's device worn (Supplies
	 * Tracker's rates). A Max or Ranging cape works like an accumulator.
	 */
	static double dartLossRate(String capeName)
	{
		String n = lower(capeName);
		if (n.contains("assembler") || n.contains("dizana"))
		{
			return 0.20;
		}
		if (n.contains("accumulator") || n.contains("max cape") || n.contains("ranging cape"))
		{
			return 0.28;
		}
		if (n.contains("attractor"))
		{
			return 0.40;
		}
		return 1.0;
	}

	/** Serpentine, tanzanite and magma helms: their charges are Zulrah's scales. */
	static boolean isScaleHelm(String headName)
	{
		String n = lower(headName);
		return (n.contains("serpentine helm") || n.contains("tanzanite helm") || n.contains("magma helm"))
			&& !n.contains("uncharged");
	}

	/** "Darts: Adamant dart x 1,234. Scales: 5,678 (35.0%)." → "Adamant dart". */
	static String dartFromCheckMessage(String message)
	{
		if (message == null)
		{
			return null;
		}
		String m = message.trim();
		int start = m.indexOf("Darts: ");
		int end = m.indexOf(" x ", start);
		if (start < 0 || end < 0)
		{
			return null;
		}
		String dart = m.substring(start + "Darts: ".length(), end).trim();
		return dart.isEmpty() || dart.equalsIgnoreCase("none") ? null : dart;
	}

	/**
	 * "Darts: Adamant dart x 1,234. Scales: 5,678 (35.0%)." → {1234, 5678}; darts are 0
	 * when none are loaded. Null if this isn't a blowpipe Check message.
	 */
	static long[] blowpipeCheckCounts(String message)
	{
		if (message == null)
		{
			return null;
		}
		java.util.regex.Matcher m = CHECK.matcher(message.trim());
		if (!m.find())
		{
			return null;
		}
		long darts = m.group(1) == null ? 0 : Long.parseLong(m.group(1).replace(",", ""));
		long scales = Long.parseLong(m.group(2).replace(",", ""));
		return new long[]{darts, scales};
	}

	private static final java.util.regex.Pattern CHECK = java.util.regex.Pattern.compile(
		"Darts: (?:.+? x ([\\d,]+)|None)\\. Scales: ([\\d,]+)", java.util.regex.Pattern.CASE_INSENSITIVE);

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH);
	}
}
