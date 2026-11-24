package com.gildedledger.tracking;

import com.gildedledger.data.PeriodStart;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TrackerData;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * Day, week and month views. At the start of each day (00:00), week (Monday 00:00) and
 * month (the 1st, 00:00) in the chosen time zone, every source's totals are noted; the
 * view for that period is everything gained since. Nothing changes while logged out,
 * so noting them at the first login after the boundary gives the same figures.
 */
public class PeriodTracker
{
	public enum Period
	{
		DAY("Day"), WEEK("Week"), MONTH("Month"), ALL("All time");

		private final String label;

		Period(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private final Ledger ledger;

	public PeriodTracker(Ledger ledger)
	{
		this.ledger = ledger;
	}

	/** Which day, week or month {@code now} falls in. */
	public static String periodId(Period period, Instant now, ZoneId zone)
	{
		ZonedDateTime t = now.atZone(zone);
		if (period == Period.DAY)
		{
			return t.format(DateTimeFormatter.ISO_LOCAL_DATE);
		}
		if (period == Period.WEEK)
		{
			return t.get(IsoFields.WEEK_BASED_YEAR) + "-W" + t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		}
		if (period == Period.MONTH)
		{
			return t.getYear() + "-" + t.getMonthValue();
		}
		return "all";
	}

	/** When the period containing {@code now} began, for showing it. */
	public static ZonedDateTime periodStart(Period period, Instant now, ZoneId zone)
	{
		ZonedDateTime day = now.atZone(zone).toLocalDate().atStartOfDay(zone);
		if (period == Period.WEEK)
		{
			return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		}
		if (period == Period.MONTH)
		{
			return day.withDayOfMonth(1);
		}
		return day;
	}

	/** Called every tick: notes where a new day, week or month began. */
	public void tick(Instant now, ZoneId zone)
	{
		TrackerData data = ledger.data();
		for (Period period : new Period[]{Period.DAY, Period.WEEK, Period.MONTH})
		{
			String id = periodId(period, now, zone);
			PeriodStart start = data.periodStarts.get(period.name());
			if (start == null || !id.equals(start.id))
			{
				PeriodStart fresh = new PeriodStart();
				fresh.id = id;
				for (SourceRecord record : data.sources.values())
				{
					fresh.sources.put(record.key, record.copy());
				}
				data.periodStarts.put(period.name(), fresh);
				ledger.changed();
			}
		}
	}

	/**
	 * The data to show for a period: the same task and sessions, with each source cut down
	 * to what it gained since the period began.
	 */
	public TrackerData view(Period period)
	{
		TrackerData data = ledger.data();
		PeriodStart start = period == Period.ALL ? null : data.periodStarts.get(period.name());
		if (start == null)
		{
			return data;
		}
		TrackerData view = new TrackerData();
		view.currentTask = data.currentTask;
		for (SourceRecord record : data.sources.values())
		{
			SourceRecord since = record.since(start.sources.get(record.key));
			if (!since.isEmpty())
			{
				view.sources.put(record.key, since);
			}
		}
		return view;
	}

	/**
	 * Resets what a day, week or month shows without touching the totals underneath: the
	 * period (and any shorter one inside it, so a week never shows less than its day) starts
	 * over from what each matching source holds now.
	 */
	public void resetPeriod(Period period, java.util.function.Predicate<SourceRecord> matching)
	{
		if (period == Period.ALL)
		{
			return;
		}
		TrackerData data = ledger.data();
		for (Period p : new Period[]{Period.DAY, Period.WEEK, Period.MONTH})
		{
			if (p.ordinal() > period.ordinal())
			{
				continue;
			}
			PeriodStart start = data.periodStarts.get(p.name());
			if (start == null)
			{
				continue;
			}
			for (SourceRecord record : data.sources.values())
			{
				if (matching.test(record))
				{
					start.sources.put(record.key, record.copy());
				}
			}
		}
		ledger.changed();
	}

	/**
	 * Zeroes XP only, keeping loot, supplies and counts. In all time the XP is cleared (and
	 * every period's starting XP with it, so later gains still show); in a day, week or month
	 * just that period (and shorter ones inside it) starts its XP over from now.
	 */
	public void resetXp(Period period, java.util.function.Predicate<SourceRecord> matching)
	{
		TrackerData data = ledger.data();
		for (Period p : new Period[]{Period.DAY, Period.WEEK, Period.MONTH})
		{
			if (period != Period.ALL && p.ordinal() > period.ordinal())
			{
				continue;
			}
			PeriodStart start = data.periodStarts.get(p.name());
			if (start == null)
			{
				continue;
			}
			for (SourceRecord record : data.sources.values())
			{
				if (!matching.test(record))
				{
					continue;
				}
				SourceRecord was = start.sources.get(record.key);
				if (was == null)
				{
					was = new SourceRecord(record.key, record.name, record.category, record.firstSeenMillis);
					start.sources.put(record.key, was);
				}
				was.xp.clear();
				if (period != Period.ALL)
				{
					was.xp.putAll(record.xp);
				}
			}
		}
		if (period == Period.ALL)
		{
			data.sources.values().forEach(r ->
			{
				if (matching.test(r))
				{
					r.xp.clear();
				}
			});
		}
		ledger.changed();
	}

	/** A source was reset: its period starts no longer apply. */
	public void forget(String key)
	{
		ledger.data().periodStarts.values().forEach(start -> start.sources.remove(key));
	}
}
