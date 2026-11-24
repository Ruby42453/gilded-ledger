package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TaskRecord;
import com.gildedledger.data.TrackerData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Supplies that may still need to move once we know more:
 * <ul>
 *   <li>General supplies used just before a fight (a super combat potion before the
 *   goblin, charges spent before the first kill) move to the monster when it dies.</li>
 *   <li>Blowpipe darts and scales are booked as an estimate per shot. When the
 *   blowpipe is checked, the estimate since the last check is corrected to the
 *   exact amount used.</li>
 * </ul>
 */
public class RecentSupplies
{
	/** Supplies used up to this long (2 minutes) before a fight count towards it. */
	public static final int PRE_FIGHT_TICKS = 200;

	/** One booking, kept so it can be moved or corrected later. */
	public static final class Booked
	{
		String key;
		String name;
		SourceCategory category;
		boolean task;
		final String lineKey;
		final int itemId;
		final String lineName;
		final String unit;
		long quantity;
		long gp;
		final int tick;
		final boolean countedUse;

		public Booked(String key, String name, SourceCategory category, boolean task, String lineKey, int itemId,
			String lineName, String unit, long quantity, long gp, int tick, boolean countedUse)
		{
			this.key = key;
			this.name = name;
			this.category = category;
			this.task = task;
			this.lineKey = lineKey;
			this.itemId = itemId;
			this.lineName = lineName;
			this.unit = unit;
			this.quantity = quantity;
			this.gp = gp;
			this.tick = tick;
			this.countedUse = countedUse;
		}
	}

	private final Ledger ledger;
	private final List<Booked> general = new ArrayList<>();
	private final List<Booked> blowpipe = new ArrayList<>();

	private int checkedDart = -1;
	private long checkedDarts = -1;
	private long checkedScales = -1;

	public RecentSupplies(Ledger ledger)
	{
		this.ledger = ledger;
	}

	/** A supply just booked to General supplies. */
	public void addGeneral(Booked booked)
	{
		general.add(booked);
	}

	/** Blowpipe darts or scales just booked (wherever they went). */
	public void addBlowpipe(Booked booked)
	{
		blowpipe.add(booked);
	}

	/** Boss fights can be long: supplies are kept this long (15 minutes) in case a boss dies. */
	public static final int BOSS_TICKS = 1500;

	/** Forgets general supplies too old to belong to any fight starting now. */
	public void expire(int tick)
	{
		general.removeIf(b -> tick - b.tick > BOSS_TICKS);
	}

	/**
	 * A monster died: general supplies used since {@code sinceTick} were for this fight,
	 * so they move from General supplies to the monster (and the Slayer task if it's one).
	 */
	public void moveGeneralToKill(String key, String name, boolean task, int sinceTick)
	{
		moveGeneralTo(key, name, SourceCategory.COMBAT, task, sinceTick);
	}

	/**
	 * Moves general supplies used since {@code sinceTick} to a source: the monster killed, or
	 * a skilling action boosted by the potion or food (a dragonfruit pie before fletching).
	 */
	public void moveGeneralTo(String key, String name, SourceCategory category, boolean task, int sinceTick)
	{
		TrackerData data = ledger.data();
		for (Iterator<Booked> it = general.iterator(); it.hasNext(); )
		{
			Booked b = it.next();
			if (b.tick < sinceTick)
			{
				continue;
			}
			it.remove();
			if (!TrackerData.GENERAL_SUPPLIES_KEY.equals(b.key))
			{
				continue;
			}
			SourceRecord from = data.sources.get(b.key);
			if (from == null || !from.removeSupply(b.lineKey, b.quantity, b.gp))
			{
				continue;
			}
			if (b.countedUse && from.count > 0)
			{
				from.count--;
			}
			if (from.isEmpty())
			{
				data.sources.remove(b.key);
			}
			ledger.source(key, name, category)
				.addSupply(b.lineKey, b.itemId, b.lineName, b.unit, b.quantity, b.gp, ledger.now());
			b.key = key;
			b.name = name;
			b.category = category;
			b.task = task;
			if (task)
			{
				adjustTask(b.gp);
			}
		}
		ledger.changed();
	}

	/** Darts or scales were loaded or taken out: the last check no longer tells us anything. */
	public void forgetBlowpipeCheck()
	{
		checkedDart = -1;
		checkedDarts = -1;
		checkedScales = -1;
		blowpipe.clear();
	}

	/**
	 * The blowpipe was checked. Corrects the per-shot estimate since the last check to
	 * what was really used. Returns true when a correction could be made.
	 *
	 * @param dartId the dart loaded, or -1 for none
	 * @param scaleId the item id of Zulrah's scales
	 */
	public boolean onBlowpipeCheck(int dartId, long darts, long scales, int scaleId, LongPrice price)
	{
		boolean corrected = false;
		if (checkedScales >= 0 && scales <= checkedScales)
		{
			correct(scaleId, checkedScales - scales, price);
			corrected = true;
		}
		if (checkedDarts >= 0 && dartId > 0 && dartId == checkedDart && darts <= checkedDarts)
		{
			correct(dartId, checkedDarts - darts, price);
			corrected = true;
		}
		checkedDart = dartId;
		checkedDarts = dartId > 0 ? darts : 0;
		checkedScales = scales;
		blowpipe.clear();
		if (corrected)
		{
			ledger.changed();
		}
		return corrected;
	}

	/** Prices an item. */
	public interface LongPrice
	{
		long of(int itemId);
	}

	private void correct(int itemId, long actual, LongPrice price)
	{
		long estimated = 0;
		Booked last = null;
		for (Booked b : blowpipe)
		{
			if (b.itemId == itemId)
			{
				estimated += b.quantity;
				last = b;
			}
		}
		long diff = actual - estimated;
		if (diff > 0)
		{
			// Used more than we guessed: the extra goes where the last shot went.
			String key = last != null ? last.key : TrackerData.GENERAL_SUPPLIES_KEY;
			String name = last != null ? last.name : TrackerData.GENERAL_SUPPLIES_NAME;
			SourceCategory category = last != null ? last.category : SourceCategory.GENERAL_SUPPLIES;
			long gp = price.of(itemId) * diff;
			String lineName = last != null ? last.lineName : null;
			ledger.source(key, name, category)
				.addSupply(String.valueOf(itemId), itemId, lineName, null, diff, gp, ledger.now());
			if (last != null && last.task)
			{
				adjustTask(gp);
			}
			return;
		}
		// Used fewer than we guessed: take the difference back, newest first.
		long refund = -diff;
		for (int i = blowpipe.size() - 1; i >= 0 && refund > 0; i--)
		{
			Booked b = blowpipe.get(i);
			if (b.itemId != itemId || b.quantity <= 0)
			{
				continue;
			}
			long take = Math.min(refund, b.quantity);
			long gp = b.quantity == 0 ? 0 : b.gp * take / b.quantity;
			SourceRecord from = ledger.data().sources.get(b.key);
			if (from != null && from.removeSupply(b.lineKey, take, gp))
			{
				if (from.isEmpty())
				{
					ledger.data().sources.remove(b.key);
				}
				b.quantity -= take;
				b.gp -= gp;
				refund -= take;
				if (b.task)
				{
					adjustTask(-gp);
				}
			}
		}
	}

	private void adjustTask(long gp)
	{
		TaskRecord task = ledger.data().currentTask;
		if (task != null && !task.isFinished())
		{
			task.supplyGp = Math.max(0, task.supplyGp + gp);
		}
	}

	public void reset()
	{
		general.clear();
		forgetBlowpipeCheck();
	}
}
