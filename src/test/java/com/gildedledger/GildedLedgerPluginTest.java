package com.gildedledger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with Gilded Ledger loaded. Run with -ea (the Gradle "run" task does this). */
public class GildedLedgerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GildedLedgerPlugin.class);
		RuneLite.main(args);
	}
}
