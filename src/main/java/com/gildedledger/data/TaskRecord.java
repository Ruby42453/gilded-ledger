package com.gildedledger.data;

/**
 * Running totals for one Slayer task. Values are captured in gp at the time
 * each event happened, so a finished task keeps the numbers it ended with.
 */
public class TaskRecord
{
	public String taskName;
	public String location;
	public int initialAmount;
	public int remainingAmount;
	public long startedMillis;
	public long finishedMillis;
	public long kills;
	public long lootGp;
	public long supplyGp;
	public long xp;
	public long slayerXp;

	public TaskRecord()
	{
	}

	public TaskRecord(String taskName, String location, int initialAmount, int remainingAmount, long nowMillis)
	{
		this.taskName = taskName;
		this.location = location;
		this.initialAmount = initialAmount;
		this.remainingAmount = remainingAmount;
		this.startedMillis = nowMillis;
	}

	public long profit()
	{
		return lootGp - supplyGp;
	}

	public boolean isFinished()
	{
		return finishedMillis > 0;
	}

	public boolean hasActivity()
	{
		return kills > 0 || lootGp > 0 || supplyGp > 0 || xp > 0 || slayerXp > 0;
	}
}
