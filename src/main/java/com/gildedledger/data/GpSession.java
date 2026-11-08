package com.gildedledger.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A GP/hour session: either the one running now, or a finished one in the session log.
 * Profit only counts while the session is running, never while it's paused.
 */
public class GpSession
{
	public String id;
	public String title;
	/** The player renamed it, so the automatic title is no longer used. */
	public boolean renamed;
	public long startedMillis;
	public long endedMillis;
	/** Time spent running, not counting pauses (and not the part still running, if any). */
	public long activeMillis;
	/** When the current running stretch began; 0 while paused or finished. */
	public long runningSinceMillis;
	/** Last time the client was seen running this session (to keep its time if the client crashes). */
	public long lastSeenMillis;
	public long profit;
	public long xp;
	/** What each source did during the session: used to name it after the main activity. */
	public Map<String, Activity> activity = new LinkedHashMap<>();
	/** Each source's totals when the current running stretch began. */
	public Map<String, Activity> stretchStart = new LinkedHashMap<>();
	/** Everything picked up or made during the session, all sources together. */
	public Map<Integer, Long> loot = new LinkedHashMap<>();
	/** Everything used up during the session, all sources together, by supply line. */
	public Map<String, SupplyLine> supplies = new LinkedHashMap<>();
	/** All loot and supplies when the current running stretch began. */
	public Map<Integer, Long> stretchLoot = new LinkedHashMap<>();
	public Map<String, SupplyLine> stretchSupplies = new LinkedHashMap<>();

	public boolean isRunning()
	{
		return runningSinceMillis > 0;
	}

	/** Profit per hour of running time. */
	public static long gpPerHour(long profit, long activeMillis)
	{
		return activeMillis < 1000 ? 0 : Math.round(profit * 3_600_000.0 / activeMillis);
	}

	public long gpPerHour()
	{
		return gpPerHour(profit, activeMillis);
	}

	/** A source's count, profit and XP: a snapshot, or what changed over a session. */
	public static class Activity
	{
		public String name;
		public SourceCategory category;
		public long count;
		public long profit;
		public long xp;

		public Activity()
		{
		}

		public Activity(String name, SourceCategory category, long count, long profit, long xp)
		{
			this.name = name;
			this.category = category;
			this.count = count;
			this.profit = profit;
			this.xp = xp;
		}
	}
}
