package com.gildedledger.tracking;

import java.util.Objects;

/** A world tile, kept free of RuneLite types so the drop logic is unit-testable. */
public final class TileKey
{
	public final int x;
	public final int y;
	public final int plane;

	public TileKey(int x, int y, int plane)
	{
		this.x = x;
		this.y = y;
		this.plane = plane;
	}

	/** Chebyshev distance, matching how OSRS measures "adjacent"; different planes are infinitely far. */
	public int distanceTo(TileKey other)
	{
		if (other == null || other.plane != plane)
		{
			return Integer.MAX_VALUE;
		}
		return Math.max(Math.abs(other.x - x), Math.abs(other.y - y));
	}

	public String pack()
	{
		return x + "," + y + "," + plane;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof TileKey))
		{
			return false;
		}
		TileKey other = (TileKey) o;
		return x == other.x && y == other.y && plane == other.plane;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, plane);
	}

	@Override
	public String toString()
	{
		return "(" + pack() + ")";
	}
}
