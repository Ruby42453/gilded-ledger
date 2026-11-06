package com.gildedledger.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Confirms supply use before charging for it, so clicking "Eat" on a full
 * health bar or banking runes never counts.
 *
 * <ul>
 *   <li>Potions, food and teleports: an Eat/Drink/Break/Read click, then that
 *       item's inventory count dropping within a few ticks.</li>
 *   <li>Runes: runes leaving the inventory or rune pouch in the same tick as
 *       Magic XP or right after a Cast click.</li>
 * </ul>
 * Cannonballs and ammunition are confirmed by the plugin from varps and the
 * equipment container and passed straight to {@link #recordDirect}.
 */
public class SupplyTracker
{
	static final int CONSUME_WINDOW_TICKS = 3;
	static final int CAST_WINDOW_TICKS = 2;

	public enum Kind
	{
		POTION, FOOD, TELEPORT, RUNE, CANNONBALL, AMMO,
		/** Charges used by weapons and armour that hold their own (tridents, blowpipe, serpentine helm). */
		CHARGES
	}

	/** One confirmed use, priced and ready to be booked to a source. */
	public static final class Use
	{
		public final Kind kind;
		public final int itemId;
		public final String lineKey;
		public final String lineName;
		public final String unit;
		public final long quantity;
		public final long gp;
		/** The teleport this was spent on (tablet name or spell name), or null. */
		public final String teleport;
		/** True for the one line that should count as a single use (one cast, one tablet). */
		public final boolean countsAsUse;

		Use(Kind kind, int itemId, String lineKey, String lineName, String unit, long quantity, long gp)
		{
			this(kind, itemId, lineKey, lineName, unit, quantity, gp, null, true);
		}

		Use(Kind kind, int itemId, String lineKey, String lineName, String unit, long quantity, long gp,
			String teleport, boolean countsAsUse)
		{
			this.teleport = teleport;
			this.countsAsUse = countsAsUse;
			this.kind = kind;
			this.itemId = itemId;
			this.lineKey = lineKey;
			this.lineName = lineName;
			this.unit = unit;
			this.quantity = quantity;
			this.gp = gp;
		}
	}

	@FunctionalInterface
	public interface Sink
	{
		void accept(Use use);
	}

	private static final class Pending
	{
		final int countBefore;
		final int tick;
		final Kind kind;

		Pending(int countBefore, int tick, Kind kind)
		{
			this.countBefore = countBefore;
			this.tick = tick;
			this.kind = kind;
		}
	}

	private final ItemInfo items;
	private final Sink sink;
	private final Map<Integer, Pending> pending = new HashMap<>();
	private final Map<Integer, Integer> tickRuneLoss = new HashMap<>();
	/** Runes that left the rune pouch this tick, and those no spell used (for Runecrafting). */
	private final Map<Integer, Integer> tickPouchLoss = new HashMap<>();
	private final Map<Integer, Integer> unusedPouchRunes = new HashMap<>();
	private int lastCastTick = Integer.MIN_VALUE / 2;
	private int lastTeleportCastTick = Integer.MIN_VALUE / 2;
	private String lastTeleportSpell;
	private boolean magicXpThisTick;

	public SupplyTracker(ItemInfo items, Sink sink)
	{
		this.items = items;
		this.sink = sink;
	}

	public void onConsumeClicked(int itemId, Kind kind, int countBefore, int tick)
	{
		if (itemId > 0 && countBefore > 0)
		{
			pending.put(itemId, new Pending(countBefore, tick, kind));
		}
	}

	/**
	 * Checks pending clicks against the new inventory.
	 *
	 * @return ids of the items confirmed as eaten, drunk or broken this change
	 */
	public List<Integer> onInventoryChanged(IntUnaryOperator countOf, int tick)
	{
		List<Integer> confirmed = new ArrayList<>();
		Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Integer, Pending> entry = it.next();
			int itemId = entry.getKey();
			Pending p = entry.getValue();
			if (tick - p.tick > CONSUME_WINDOW_TICKS)
			{
				it.remove();
				continue;
			}
			if (countOf.applyAsInt(itemId) < p.countBefore)
			{
				it.remove();
				sink.accept(priceConsumable(itemId, p.kind));
				confirmed.add(itemId);
			}
		}
		return confirmed;
	}

	public void onCastClicked(int tick)
	{
		lastCastTick = tick;
	}

	/** A teleport spell was cast from the spellbook; its runes belong to Teleports. */
	public void onTeleportCast(String spellName, int tick)
	{
		lastCastTick = tick;
		lastTeleportCastTick = tick;
		lastTeleportSpell = spellName;
	}

	public void onMagicXp()
	{
		magicXpThisTick = true;
	}

	public void onRunesLost(int runeItemId, int quantity)
	{
		if (quantity > 0)
		{
			tickRuneLoss.merge(runeItemId, quantity, Integer::sum);
		}
	}

	/**
	 * Runes taken from the rune pouch. A spell's runes are booked as usual; runes no spell
	 * used (combination runecrafting draws its elemental runes from the pouch) are left for
	 * {@link #takeUnusedPouchRunes()}.
	 */
	public void onPouchRunesLost(int runeItemId, int quantity)
	{
		onRunesLost(runeItemId, quantity);
		if (quantity > 0)
		{
			tickPouchLoss.merge(runeItemId, quantity, Integer::sum);
		}
	}

	/** Rune pouch runes used this tick by something other than a spell. */
	public Map<Integer, Integer> takeUnusedPouchRunes()
	{
		Map<Integer, Integer> runes = new HashMap<>(unusedPouchRunes);
		unusedPouchRunes.clear();
		return runes;
	}

	/** A charge of something worn, priced per use (a binding necklace's 16 charges). */
	public static Use charge(int itemId, String lineKey, String lineName, String unit, long quantity, long gp)
	{
		return new Use(Kind.CHARGES, itemId, lineKey, lineName, unit, quantity, gp);
	}

	/** Books this tick's rune losses if a spell was cast, then resets per-tick state. */
	public void onGameTick(int tick)
	{
		boolean cast = magicXpThisTick || tick - lastCastTick <= CAST_WINDOW_TICKS;
		boolean teleport = tick - lastTeleportCastTick <= CAST_WINDOW_TICKS && lastTeleportSpell != null;
		if (cast)
		{
			boolean first = true;
			for (Map.Entry<Integer, Integer> loss : tickRuneLoss.entrySet())
			{
				int id = loss.getKey();
				int qty = loss.getValue();
				sink.accept(new Use(teleport ? Kind.TELEPORT : Kind.RUNE, id, String.valueOf(id), items.name(id), null, qty,
					items.marketPrice(id) * qty, teleport ? lastTeleportSpell : null, first));
				first = false;
			}
			if (teleport && !tickRuneLoss.isEmpty())
			{
				lastTeleportSpell = null;
			}
		}
		if (!cast)
		{
			tickPouchLoss.forEach((id, qty) -> unusedPouchRunes.merge(id, qty, Integer::sum));
		}
		tickPouchLoss.clear();
		tickRuneLoss.clear();
		magicXpThisTick = false;
		pending.values().removeIf(p -> tick - p.tick > CONSUME_WINDOW_TICKS);
	}

	/** For supplies confirmed elsewhere (cannonballs, ammo). */
	public void recordDirect(Kind kind, int itemId, long quantity)
	{
		if (quantity > 0)
		{
			sink.accept(new Use(kind, itemId, String.valueOf(itemId), items.name(itemId), null, quantity,
				items.marketPrice(itemId) * quantity));
		}
	}

	public void reset()
	{
		pending.clear();
		tickRuneLoss.clear();
		magicXpThisTick = false;
	}

	Use priceConsumable(int itemId, Kind kind)
	{
		String name = items.name(itemId);
		long price = items.marketPrice(itemId);
		if (kind == Kind.POTION)
		{
			String base = ItemRules.withoutDoses(name);
			return new Use(kind, itemId, "potion:" + base, base, "doses", 1, ItemRules.perDoseValue(price, name));
		}
		if (kind == Kind.TELEPORT)
		{
			return new Use(kind, itemId, String.valueOf(itemId), name, null, 1, price, name, true);
		}
		return new Use(kind, itemId, String.valueOf(itemId), name, null, 1, price);
	}
}
