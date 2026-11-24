package com.gildedledger.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides when to remind the player to re-pot, drink prayer or reload the cannon.
 * Each reminder fires once, and is armed again when the thing is topped back up.
 *
 * <p>Boosts: a divine potion's boost lasts as long as its timer. Any other boost
 * drops by one level every minute (90 seconds with Preserve), on a clock shared by
 * all stats, so a boost of 5 runs out four minutes after the next drop.
 */
public class Reminders
{
	public static final int DECAY_TICKS = 100;
	public static final int PRESERVE_DECAY_TICKS = 150;

	private final Map<String, Integer> lastBoost = new HashMap<>();
	private final Map<String, Integer> lastDivine = new HashMap<>();
	private final Map<String, Boolean> boostArmed = new HashMap<>();
	private int lastDecayTick = Integer.MIN_VALUE;

	private boolean prayerArmed = true;
	private int lastPrayer = -1;
	private int lastCannonballs = -1;
	private boolean cannonArmed = true;

	/**
	 * Called every tick with the current boost (boosted minus real level) of each
	 * combat skill, and the divine potion ticks left for it (0 if none).
	 * Returns the skills whose boost runs out within {@code warnTicks}.
	 */
	public List<String> onBoosts(int tick, Map<String, Integer> boosts, Map<String, Integer> divineTicks,
		boolean preserve, int warnTicks)
	{
		// A stat dropping by exactly one with no divine timer is the shared decay clock.
		for (Map.Entry<String, Integer> e : boosts.entrySet())
		{
			Integer before = lastBoost.get(e.getKey());
			int divine = divineTicks.getOrDefault(e.getKey(), 0);
			if (before != null && before > 0 && e.getValue() == before - 1 && divine <= 0)
			{
				lastDecayTick = tick;
			}
		}

		int interval = preserve ? PRESERVE_DECAY_TICKS : DECAY_TICKS;
		List<String> warn = new ArrayList<>();
		for (Map.Entry<String, Integer> e : boosts.entrySet())
		{
			String skill = e.getKey();
			int boost = e.getValue();
			int divine = divineTicks.getOrDefault(skill, 0);
			int before = lastBoost.getOrDefault(skill, 0);
			int divineBefore = lastDivine.getOrDefault(skill, 0);
			lastBoost.put(skill, boost);
			lastDivine.put(skill, divine);

			if (boost <= 0)
			{
				boostArmed.put(skill, false);
				continue;
			}
			long remaining = remainingTicks(tick, boost, divine, interval);
			boolean topUp = boost > before || divine > divineBefore;
			if (topUp)
			{
				// Freshly drunk: remind once it's nearly gone (unless it's already that short).
				boostArmed.put(skill, remaining > warnTicks);
				continue;
			}
			if (boostArmed.getOrDefault(skill, false) && remaining <= warnTicks)
			{
				boostArmed.put(skill, false);
				warn.add(skill);
			}
		}
		return warn;
	}

	/** Ticks until the boost is gone (or, for a divine potion, until its timer ends). */
	long remainingTicks(int tick, int boost, int divineTicks, int interval)
	{
		if (divineTicks > 0)
		{
			return divineTicks;
		}
		// Unknown clock: assume the next drop is due now, so the reminder is early rather than late.
		long sinceDrop = lastDecayTick == Integer.MIN_VALUE ? interval : Math.min(interval, tick - (long) lastDecayTick);
		long untilNextDrop = Math.max(0, interval - sinceDrop);
		return untilNextDrop + (long) (boost - 1) * interval;
	}

	/**
	 * True once when prayer drains to the threshold or below (not just for logging in
	 * with little prayer, or never using it).
	 */
	public boolean onPrayer(int points, int threshold)
	{
		int before = lastPrayer;
		lastPrayer = points;
		if (points > threshold)
		{
			prayerArmed = true;
			return false;
		}
		if (prayerArmed && threshold > 0 && before > points)
		{
			prayerArmed = false;
			return true;
		}
		return false;
	}

	/** True once when the cannon's balls run down to the threshold or below. */
	public boolean onCannonballs(int balls, int threshold, boolean placed)
	{
		int before = lastCannonballs;
		lastCannonballs = balls;
		if (!placed || balls > threshold)
		{
			cannonArmed = balls > threshold || !placed;
			return false;
		}
		if (cannonArmed && before > balls)
		{
			cannonArmed = false;
			return true;
		}
		return false;
	}

	public void reset()
	{
		lastBoost.clear();
		lastDivine.clear();
		boostArmed.clear();
		lastDecayTick = Integer.MIN_VALUE;
		prayerArmed = true;
		lastPrayer = -1;
		lastCannonballs = -1;
		cannonArmed = true;
	}
}
