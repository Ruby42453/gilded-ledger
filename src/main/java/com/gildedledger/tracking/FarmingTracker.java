package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks every farming patch the same way, filed by crop: all Toadflax together,
 * all Yew trees together, whichever patch they grew in.
 *
 * <p>A session opens for a patch when the player clicks it, uses an item on it,
 * or the game sends a farming message ("You plant a toadflax seed in the herb
 * patch.", "You treat the herb patch with ultracompost."). The messages make the
 * tracker work even when the patch object itself isn't recognised. While the
 * session lasts:
 * <ul>
 *   <li>Items gained are loot (herbs, fruit, logs from chopping a patch tree...).
 *       If the crop isn't known yet, it's worked out from the harvest
 *       ("Grimy huasca" → Huasca, "Yew logs" → Yew tree).</li>
 *   <li>Seeds, saplings and compost are charged the moment they're used. Compost
 *       goes on the crop in the patch; if a different seed is then planted on the
 *       same visit, that compost moves to the new crop.</li>
 *   <li>Paying a gardener to protect the patch is charged to that crop too.</li>
 * </ul>
 *
 * Clicking anything other than the patch (a leprechaun, a bank, an NPC) pauses
 * the session, so noting herbs or banking never looks like a harvest.
 */
public class FarmingTracker
{
	static final int MAX_DISTANCE = 12;
	static final int IDLE_TICKS = 100;
	static final int PAYMENT_WINDOW_TICKS = 500;
	static final int PAYMENT_DISTANCE = 20;

	/** "You plant a toadflax seed in the herb patch." / "You plant 3 potato seeds in the allotment." */
	private static final Pattern PLANT_MESSAGE = Pattern.compile(
		"^You plant (?:a |an |the |\\d+ )?(.+?) in the (.+?)\\.?$", Pattern.CASE_INSENSITIVE);
	/** "You treat the herb patch with ultracompost." */
	private static final Pattern TREAT_MESSAGE = Pattern.compile(
		"^You treat the (.+?) with (compost|supercompost|ultracompost)\\.?$", Pattern.CASE_INSENSITIVE);
	/** "You begin to harvest the herb patch." */
	private static final Pattern HARVEST_MESSAGE = Pattern.compile(
		"^You begin to harvest the (.+?)\\.?$", Pattern.CASE_INSENSITIVE);
	/** "You examine the tree for signs of disease and find that it is in perfect health." */
	private static final Pattern CHECK_HEALTH_MESSAGE = Pattern.compile(
		"^You examine the (.+?) for signs of disease.*$", Pattern.CASE_INSENSITIVE);

	private final Ledger ledger;
	private final ItemInfo items;
	private final Map<String, Integer> compostItemIds;

	private Session session;
	private Session lastSession;
	private int lastSessionEndTick;

	private final Map<Integer, Integer> tickChanges = new HashMap<>();
	private final Map<String, Long> tickXp = new HashMap<>();
	private final List<String> tickCompostMessages = new ArrayList<>();
	private String tickPlantedCrop;

	/** Compost already charged on this visit, so it can follow a newly planted crop. */
	private static final class BookedCompost
	{
		final String sourceKey;
		final int itemId;
		final long quantity;
		final long gp;
		final Map<String, Long> xp = new HashMap<>();

		BookedCompost(String sourceKey, int itemId, long quantity, long gp)
		{
			this.sourceKey = sourceKey;
			this.itemId = itemId;
			this.quantity = quantity;
			this.gp = gp;
		}
	}

	private static final class Session
	{
		final TileKey patch;
		String category;
		String crop;
		boolean armed = true;
		int lastActivityTick;
		final List<BookedCompost> compostThisVisit = new ArrayList<>();
		final Set<String> countedSources = new HashSet<>();

		Session(TileKey patch, String category, String crop, int tick)
		{
			this.patch = patch;
			this.category = category;
			this.crop = crop;
			this.lastActivityTick = tick;
		}
	}

	public FarmingTracker(Ledger ledger, ItemInfo items, Map<String, Integer> compostItemIds)
	{
		this.ledger = ledger;
		this.items = items;
		this.compostItemIds = compostItemIds;
	}

	/**
	 * The player clicked a farming patch or used an item on one.
	 *
	 * @param category       e.g. "Herb", "Tree", "Fruit tree"
	 * @param appearanceCrop crop guessed from the patch's current look; may be null
	 */
	public void onPatchClicked(TileKey patch, String category, String appearanceCrop, int tick)
	{
		if (session != null && session.patch != null && session.patch.equals(patch))
		{
			session.armed = true;
			session.lastActivityTick = tick;
			if (session.crop == null && appearanceCrop != null)
			{
				session.crop = appearanceCrop;
			}
			return;
		}
		closeSession(tick);
		String remembered = ledger.data().patchCrops.get(patchMemoryKey(patch, category));
		session = new Session(patch, category, remembered != null ? remembered : appearanceCrop, tick);
	}

	/** The player clicked something that isn't the patch; stop booking changes until they return. */
	public void onOtherAction()
	{
		if (session != null)
		{
			session.armed = false;
		}
	}

	/** True while a patch is being worked; the skilling tracker stays out of the way then. */
	public boolean isArmed()
	{
		return session != null && session.armed;
	}

	public void onInventoryChanged(InventoryDiff diff)
	{
		if (session == null || !session.armed)
		{
			return;
		}
		diff.changes().forEach((id, delta) -> tickChanges.merge(id, delta, Integer::sum));
	}

	public void onXp(String skill, long amount)
	{
		if (session != null && session.armed && amount > 0
			&& ("Farming".equals(skill) || "Woodcutting".equals(skill)))
		{
			tickXp.merge(skill, amount, Long::sum);
		}
	}

	/**
	 * A game message. Farming messages open (or re-arm) a session on their own, so
	 * planting and composting are tracked even if the patch click wasn't recognised.
	 */
	public void onChatMessage(String message, TileKey playerTile, int tick)
	{
		if (message == null)
		{
			return;
		}
		String text = message.trim();

		Matcher plant = PLANT_MESSAGE.matcher(text);
		if (plant.matches())
		{
			ensureSession(ItemRules.patchCategory(plant.group(2)), playerTile, tick);
			tickPlantedCrop = cropFromPlanted(plant.group(1));
			return;
		}
		Matcher treat = TREAT_MESSAGE.matcher(text);
		if (treat.matches())
		{
			ensureSession(ItemRules.patchCategory(treat.group(1)), playerTile, tick);
			tickCompostMessages.add(ItemRules.capitalize(treat.group(2).toLowerCase(Locale.ENGLISH)));
			return;
		}
		Matcher harvest = HARVEST_MESSAGE.matcher(text);
		if (harvest.matches())
		{
			ensureSession(ItemRules.patchCategory(harvest.group(1)), playerTile, tick);
			return;
		}
		Matcher check = CHECK_HEALTH_MESSAGE.matcher(text);
		if (check.matches() && session != null)
		{
			session.armed = true;
			session.lastActivityTick = tick;
		}
	}

	/**
	 * Something left the inventory after the player chose to pay a gardener.
	 * Charged to the patch most recently worked, if it was recent and nearby.
	 */
	public void onGardenerPayment(InventoryDiff diff, int tick, TileKey playerTile)
	{
		Session target = session != null ? session : lastSession;
		if (target == null || target.crop == null)
		{
			return;
		}
		if (session == null && tick - lastSessionEndTick > PAYMENT_WINDOW_TICKS)
		{
			return;
		}
		TileKey where = target.patch;
		if (where != null && playerTile != null && where.distanceTo(playerTile) > PAYMENT_DISTANCE)
		{
			return;
		}
		diff.changes().forEach((id, delta) ->
		{
			if (delta < 0)
			{
				bookCost(target, id, -delta, items.marketPrice(id) * -delta);
			}
		});
	}

	/** Called once per game tick: books this tick's changes and closes stale sessions. */
	public void onGameTick(int tick, TileKey playerTile)
	{
		if (session != null)
		{
			resolveTick(tick);
			boolean walkedAway = playerTile != null && session.patch != null
				&& session.patch.distanceTo(playerTile) > MAX_DISTANCE;
			if (walkedAway || tick - session.lastActivityTick > IDLE_TICKS)
			{
				closeSession(tick);
			}
		}
		tickChanges.clear();
		tickXp.clear();
		tickCompostMessages.clear();
		tickPlantedCrop = null;
	}

	/** Logging out or hopping: forget the session (everything is already charged). */
	public void reset()
	{
		session = null;
		lastSession = null;
		tickChanges.clear();
		tickXp.clear();
		tickCompostMessages.clear();
		tickPlantedCrop = null;
	}

	boolean hasSession()
	{
		return session != null;
	}

	/** Patches are remembered per region and patch type: one herb patch, one tree patch... */
	public static String patchMemoryKey(TileKey tile, String category)
	{
		if (tile == null)
		{
			return "unknown:" + category;
		}
		return (tile.x >> 6) + "," + (tile.y >> 6) + "," + tile.plane + ":" + category;
	}

	/**
	 * The name a crop is filed under: "Toadflax", "Yew tree", "Apple tree". Trees get
	 * "tree" added so a Yew sapling and a grown Yew tree land on the same entry.
	 */
	public static String cropName(String category, String crop)
	{
		if (crop == null)
		{
			return category == null || "Farming".equals(category)
				? "Unknown crop" : category + " patch (unknown crop)";
		}
		String name = canonicalCrop(crop);
		// The Farming Guild's anima plants don't get their own entries.
		return ANIMA_PLANTS.contains(name.toLowerCase(Locale.ENGLISH)) ? "Other" : name;
	}

	private static final Set<String> ANIMA_PLANTS = Set.of("attas", "kronos", "iasor");

	/** Crops that grow as trees, so "Yew", "Yew sapling" and "Yew tree" all become "Yew tree". */
	private static final Set<String> TREE_CROPS = Set.of(
		"oak", "willow", "maple", "yew", "magic", "teak", "mahogany", "camphor", "ironwood", "rosewood",
		"apple", "banana", "orange", "curry", "pineapple", "papaya", "palm", "dragonfruit",
		"calquat", "celastrus", "redwood", "spirit", "crystal");

	/**
	 * One name per crop whichever way it was seen: "Papaya", "Papaya tree" and a papaya
	 * sapling are all "Papaya tree"; "Ranarr" stays "Ranarr".
	 */
	public static String canonicalCrop(String crop)
	{
		if (crop == null)
		{
			return null;
		}
		String base = crop.trim();
		String lower = base.toLowerCase(Locale.ENGLISH);
		if (lower.endsWith(" tree"))
		{
			base = base.substring(0, base.length() - " tree".length()).trim();
			lower = base.toLowerCase(Locale.ENGLISH);
		}
		base = ItemRules.capitalize(lower);
		return TREE_CROPS.contains(lower) ? base + " tree" : base;
	}

	public static String sourceKey(String category, String crop)
	{
		return "farming:" + cropName(category, crop);
	}

	// ---------------------------------------------------------------- internals

	/** Opens a session from a farming message when no matching one is running. */
	private void ensureSession(String category, TileKey playerTile, int tick)
	{
		boolean near = session != null && (session.patch == null || playerTile == null
			|| session.patch.distanceTo(playerTile) <= MAX_DISTANCE);
		if (session != null && near)
		{
			session.armed = true;
			session.lastActivityTick = tick;
			if ("Farming".equals(session.category) && category != null && !"Farming".equals(category))
			{
				session.category = category;
			}
			return;
		}
		closeSession(tick);
		String remembered = ledger.data().patchCrops.get(patchMemoryKey(playerTile, category));
		session = new Session(playerTile, category, remembered, tick);
	}

	/** "toadflax seed" → "Toadflax"; "yew sapling" → "Yew tree". */
	private static String cropFromPlanted(String planted)
	{
		if (planted == null)
		{
			return null;
		}
		String crop = ItemRules.cropFromPlantable(planted.trim());
		return canonicalCrop(crop);
	}

	private void resolveTick(int tick)
	{
		Session s = session;
		boolean active = !tickChanges.isEmpty() || !tickXp.isEmpty() || !tickCompostMessages.isEmpty()
			|| tickPlantedCrop != null;
		if (active)
		{
			s.lastActivityTick = tick;
		}

		// Gains first, credited to whatever was growing before any replant this tick.
		boolean harvestGained = false;
		for (Map.Entry<Integer, Integer> change : tickChanges.entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			String name = items.name(id);
			if (delta > 0 && !ItemRules.isFarmingTool(name) && !ItemRules.isPlantable(name))
			{
				// The harvest tells us the crop when we didn't know it (or remembered it wrong).
				String harvested = cropFromHarvest(name, s.category);
				if (harvested != null && (s.crop == null || name.toLowerCase(Locale.ENGLISH).startsWith("grimy ")))
				{
					setCrop(s, harvested);
					if (!items.tradeable(id))
					{
						markUntradeable(s.crop);
					}
				}
				ledger.addLoot(sourceKey(s), sourceName(s), SourceCategory.FARMING, id, delta);
				countOnce(s);
				harvestGained = true;
			}
		}

		// Then planting, which changes the crop for the rest of the tick.
		boolean planted = false;
		for (Map.Entry<Integer, Integer> change : tickChanges.entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			String name = items.name(id);
			if (delta < 0 && ItemRules.isPlantable(name))
			{
				setCrop(s, cropFromPlanted(name));
				if (!items.tradeable(id))
				{
					// Quest plants (goutweed) and the like: hidden when the setting says so.
					markUntradeable(s.crop);
				}
				bookCost(s, id, -delta, items.marketPrice(id) * -delta);
				planted = true;
			}
		}
		if (!planted && tickPlantedCrop != null)
		{
			// Planted from somewhere we can't see (a seed box): the crop still changes.
			setCrop(s, tickPlantedCrop);
			planted = true;
		}
		if (planted)
		{
			moveCompostToCrop(s);
		}

		// Compost, charged straight away to the crop in the patch.
		boolean compostItemUsed = false;
		for (Map.Entry<Integer, Integer> change : tickChanges.entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			if (delta < 0 && ItemRules.isCompost(items.name(id)))
			{
				compostItemUsed = true;
				bookCompost(s, id, -delta, items.marketPrice(id) * -delta);
			}
		}
		// Compost from a bottomless bucket: the message arrives but no compost item leaves.
		if (!compostItemUsed)
		{
			for (String compost : tickCompostMessages)
			{
				Integer id = compostItemIds.get(compost);
				if (id != null)
				{
					bookCompost(s, id, 1, items.marketPrice(id));
				}
			}
		}

		// Picking a herb gives Farming XP; with an open herb sack the herb never reaches the
		// inventory, so the XP alone says one more of the patch's herb was picked.
		boolean compostThisTick = compostItemUsed || !tickCompostMessages.isEmpty();
		if (!harvestGained && !planted && !compostThisTick && tickXp.getOrDefault("Farming", 0L) > 0
			&& s.crop != null && s.category != null && s.category.toLowerCase(Locale.ENGLISH).contains("herb"))
		{
			int herb = grimyHerb(s.crop);
			if (herb > 0)
			{
				ledger.addLoot(sourceKey(s), sourceName(s), SourceCategory.FARMING, herb, 1);
				countOnce(s);
			}
		}

		boolean compostOnlyTick = (compostItemUsed || !tickCompostMessages.isEmpty()) && !planted;
		for (Map.Entry<String, Long> xp : tickXp.entrySet())
		{
			ledger.addXp(sourceKey(s), sourceName(s), SourceCategory.FARMING, xp.getKey(), xp.getValue());
			if (compostOnlyTick && !s.compostThisVisit.isEmpty())
			{
				// The XP for composting moves with the compost if a new seed goes in.
				s.compostThisVisit.get(s.compostThisVisit.size() - 1).xp.merge(xp.getKey(), xp.getValue(), Long::sum);
			}
			if ("Woodcutting".equals(xp.getKey()) || isCheckHealthOrHarvest())
			{
				countOnce(s);
			}
		}
	}

	/**
	 * Remembers a crop grown from an untradeable seed (or giving an untradeable harvest),
	 * so "Hide untradeable plants" can hide it.
	 */
	private void markUntradeable(String crop)
	{
		if (crop != null && !HESPORI.equals(crop))
		{
			ledger.data().untradeableCrops.add(crop);
		}
	}


	/** Hespori is a boss: its seed is a PvM supply, though its Farming XP stays in Farming. */
	public static final String HESPORI = "Hespori";


	private void setCrop(Session s, String crop)
	{
		crop = canonicalCrop(crop);
		if (crop == null)
		{
			return;
		}
		String unknownKey = s.crop == null ? sourceKey(s) : null;
		s.crop = crop;
		ledger.data().patchCrops.put(patchMemoryKey(s.patch, s.category), crop);
		if (unknownKey != null)
		{
			// Everything booked while the crop was unknown (compost, its XP) belongs to it.
			ledger.mergeInto(unknownKey, sourceKey(s), sourceName(s));
			s.compostThisVisit.removeIf(c -> c.sourceKey.equals(unknownKey));
		}
	}

	/** Planting XP comes with a seed or compost leaving; only XP with nothing used up is a harvest. */
	private boolean isCheckHealthOrHarvest()
	{
		if (tickPlantedCrop != null || !tickCompostMessages.isEmpty())
		{
			return false;
		}
		for (int delta : tickChanges.values())
		{
			if (delta < 0)
			{
				return false;
			}
		}
		return true;
	}

	private void countOnce(Session s)
	{
		String key = sourceKey(s);
		if (s.countedSources.add(key))
		{
			ledger.incrementCount(key, sourceName(s), SourceCategory.FARMING);
		}
	}

	private void bookCost(Session s, int itemId, long quantity, long gp)
	{
		String name = items.name(itemId);
		if ("hespori seed".equalsIgnoreCase(name))
		{
			// Planting Hespori is the start of a boss fight: the seed is the fight's cost.
			ledger.addSupply("npc:" + HESPORI, HESPORI, SourceCategory.COMBAT,
				String.valueOf(itemId), itemId, name, null, quantity, gp);
			return;
		}
		ledger.addSupply(sourceKey(s), sourceName(s), SourceCategory.FARMING,
			String.valueOf(itemId), itemId, name, null, quantity, gp);
	}

	/**
	 * Farming entries saved before the crop was known ("Herb patch (unknown crop)")
	 * are merged into the crop their harvest shows, e.g. grimy huasca → Huasca.
	 */
	public void tidyUnknownCrops()
	{
		normaliseCropNames();
		List<SourceRecord> unknown = new ArrayList<>();
		for (SourceRecord record : ledger.data().sources.values())
		{
			if (record.category == SourceCategory.FARMING && record.name != null
				&& (record.name.endsWith("(unknown crop)") || record.name.equals("Unknown crop")))
			{
				unknown.add(record);
			}
		}
		boolean changed = false;
		for (SourceRecord record : unknown)
		{
			String category = record.name.endsWith(" patch (unknown crop)")
				? record.name.substring(0, record.name.length() - " patch (unknown crop)".length()) : "Farming";
			String crop = null;
			long best = -1;
			for (Map.Entry<Integer, Long> loot : record.loot.entrySet())
			{
				String harvested = cropFromHarvest(items.name(loot.getKey()), category);
				if (harvested != null && loot.getValue() > best)
				{
					crop = harvested;
					best = loot.getValue();
				}
			}
			if (crop == null)
			{
				continue;
			}
			String name = cropName(category, crop);
			String key = "farming:" + name;
			ledger.data().sources.remove(record.key);
			SourceRecord target = ledger.data().sources.get(key);
			if (target == null)
			{
				record.key = key;
				record.name = name;
				ledger.data().sources.put(key, record);
			}
			else
			{
				target.mergeFrom(record);
			}
			changed = true;
		}
		if (changed)
		{
			ledger.changed();
		}
	}

	/** The grimy herb a herb crop gives: "Ranarr" → Grimy ranarr weed, "Guam" → Grimy guam leaf. */
	private int grimyHerb(String crop)
	{
		String base = "Grimy " + crop.toLowerCase(Locale.ENGLISH);
		for (String name : new String[]{base, base + " weed", base + " leaf"})
		{
			int id = items.idByName(name);
			if (id > 0)
			{
				return id;
			}
		}
		return -1;
	}

	/** "Papaya" and "Papaya tree", "Yew" and "Yew tree": one entry per crop. */
	private void normaliseCropNames()
	{
		List<SourceRecord> farming = new ArrayList<>();
		for (SourceRecord record : ledger.data().sources.values())
		{
			if (record.category == SourceCategory.FARMING && record.name != null
				&& !record.name.endsWith("(unknown crop)") && !record.name.equals("Unknown crop")
				&& record.key.startsWith("farming:"))
			{
				farming.add(record);
			}
		}
		for (SourceRecord record : farming)
		{
			String name = cropName(null, record.name);
			String key = "farming:" + name;
			if (key.equals(record.key))
			{
				continue;
			}
			ledger.mergeInto(record.key, key, name);
		}
	}

	private void bookCompost(Session s, int itemId, long quantity, long gp)
	{
		bookCost(s, itemId, quantity, gp);
		s.compostThisVisit.add(new BookedCompost(sourceKey(s), itemId, quantity, gp));
	}

	/** A seed was planted after composting: the compost belongs to the new crop. */
	private void moveCompostToCrop(Session s)
	{
		String key = sourceKey(s);
		for (BookedCompost compost : s.compostThisVisit)
		{
			if (!compost.sourceKey.equals(key))
			{
				ledger.moveSupply(compost.sourceKey, key, sourceName(s), SourceCategory.FARMING,
					String.valueOf(compost.itemId), compost.itemId, items.name(compost.itemId),
					compost.quantity, compost.gp);
				compost.xp.forEach((skill, amount) ->
					ledger.moveXp(compost.sourceKey, key, sourceName(s), SourceCategory.FARMING, skill, amount));
			}
		}
		s.compostThisVisit.clear();
	}

	/**
	 * The crop a harvested item came from: "Grimy huasca" → Huasca; logs from a tree
	 * patch → "Yew tree"; otherwise the item itself (Potato, Redberries...).
	 */
	static String cropFromHarvest(String itemName, String category)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return null;
		}
		String lower = itemName.toLowerCase(Locale.ENGLISH);
		if (lower.startsWith("grimy "))
		{
			// Named like the seed: "Grimy ranarr weed" → Ranarr, "Grimy guam leaf" → Guam.
			String herb = lower.substring("grimy ".length()).trim();
			if (herb.endsWith(" leaf"))
			{
				herb = herb.substring(0, herb.length() - " leaf".length());
			}
			else if (herb.endsWith(" weed") && !herb.equals("dwarf weed"))
			{
				herb = herb.substring(0, herb.length() - " weed".length());
			}
			return ItemRules.capitalize(herb);
		}
		if (lower.equals("logs"))
		{
			return "Tree";
		}
		if (lower.equals("cooking apple"))
		{
			return "Apple tree";
		}
		if (lower.equals("coconut"))
		{
			return "Palm tree";
		}
		for (String suffix : new String[]{" logs", " fruit", " bark", " leaf"})
		{
			if (lower.endsWith(suffix))
			{
				return canonicalCrop(itemName.substring(0, itemName.length() - suffix.length()));
			}
		}
		return canonicalCrop(itemName);
	}

	private void closeSession(int tick)
	{
		if (session == null)
		{
			return;
		}
		lastSession = session;
		lastSessionEndTick = tick;
		session = null;
	}

	private static String sourceKey(Session s)
	{
		return sourceKey(s.category, s.crop);
	}

	private static String sourceName(Session s)
	{
		return cropName(s.category, s.crop);
	}
}
