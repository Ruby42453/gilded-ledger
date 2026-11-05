package com.gildedledger.tracking;

/** Item lookups the trackers need, abstracted so they can be tested without a game client. */
public interface ItemInfo
{
	String name(int itemId);

	/** Grand Exchange value of one item; used for every supply cost. */
	long marketPrice(int itemId);

	boolean stackable(int itemId);

	/** Turns a noted or placeholder id into the base item id. */
	int canonical(int itemId);
}
