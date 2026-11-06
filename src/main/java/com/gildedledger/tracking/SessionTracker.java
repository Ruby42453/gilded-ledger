package com.gildedledger.tracking;

import com.gildedledger.data.GpSession;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.SupplyLine;
import com.gildedledger.data.TrackerData;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntToLongFunction;
import java.util.function.LongSupplier;

/**
 * GP/hour sessions. Start, pause, resume and end; while running, whatever every source
 * gains or spends counts towards the session. Each running stretch is measured by
 * comparing every source's totals at its start and end, so a pause leaves out
 * everything that happens during it.
 */
public class SessionTracker
{
	private final Ledger ledger;
	/** The price used for loot (GE, or 0 for most untradeables). */
	private final IntToLongFunction lootPrice;
	private final LongSupplier clock;

	public SessionTracker(Ledger ledger, IntToLongFunction lootPrice, LongSupplier clock)
	{
		this.ledger = ledger;
		this.lootPrice = lootPrice;
		this.clock = clock;
	}

	public GpSession active()
	{
		return ledger.data().activeSession;
	}

	public void start()
	{
		if (active() != null)
		{
			return;
		}
		long now = clock.getAsLong();
		GpSession session = new GpSession();
		session.id = UUID.randomUUID().toString();
		session.startedMillis = now;
		session.runningSinceMillis = now;
		takeStretchStart(session);
		ledger.data().activeSession = session;
		ledger.changed();
	}

	public void pause()
	{
		GpSession session = active();
		if (session == null || !session.isRunning())
		{
			return;
		}
		closeStretch(session, true);
		ledger.changed();
	}

	public void resume()
	{
		GpSession session = active();
		if (session == null || session.isRunning())
		{
			return;
		}
		session.runningSinceMillis = clock.getAsLong();
		takeStretchStart(session);
		ledger.changed();
	}

	/** Ends the session and files it in the log. */
	public void end()
	{
		GpSession session = active();
		if (session == null)
		{
			return;
		}
		if (session.isRunning())
		{
			closeStretch(session, true);
		}
		session.endedMillis = clock.getAsLong();
		session.title = titleFor(session);
		session.stretchStart.clear();
		session.stretchLoot.clear();
		session.stretchSupplies.clear();
		ledger.data().sessionLog.add(0, session);
		ledger.data().activeSession = null;
		ledger.changed();
	}

	/**
	 * After loading a saved session that was running (the client closed mid-session),
	 * keep what it booked but not the time the client was closed.
	 */
	public void pauseAfterRestart()
	{
		GpSession session = active();
		if (session != null && session.isRunning())
		{
			closeStretch(session, false);
			ledger.changed();
		}
	}

	/** The session as it stands right now, the running stretch included. */
	public GpSession live()
	{
		GpSession session = active();
		if (session == null)
		{
			return null;
		}
		GpSession view = new GpSession();
		view.id = session.id;
		view.startedMillis = session.startedMillis;
		view.activeMillis = session.activeMillis;
		view.runningSinceMillis = session.runningSinceMillis;
		view.profit = session.profit;
		view.xp = session.xp;
		session.activity.forEach((key, a) ->
			view.activity.put(key, new GpSession.Activity(a.name, a.category, a.count, a.profit, a.xp)));
		view.loot.putAll(session.loot);
		session.supplies.forEach((key, line) -> view.supplies.put(key, copy(line)));
		if (session.isRunning())
		{
			addStretch(view, session.stretchStart, snapshot());
			addItems(view, session.stretchLoot, session.stretchSupplies);
		}
		view.title = titleFor(view);
		return view;
	}

	public void rename(String id, String title)
	{
		for (GpSession session : ledger.data().sessionLog)
		{
			if (session.id.equals(id) && title != null && !title.trim().isEmpty())
			{
				session.title = title.trim();
				session.renamed = true;
				ledger.changed();
				return;
			}
		}
	}

	public void delete(String id)
	{
		if (ledger.data().sessionLog.removeIf(s -> s.id.equals(id)))
		{
			ledger.changed();
		}
	}

	public void clearLog()
	{
		ledger.data().sessionLog.clear();
		ledger.changed();
	}

	// ---------------------------------------------------------------- measuring

	private void closeStretch(GpSession session, boolean countTime)
	{
		if (countTime)
		{
			session.activeMillis += Math.max(0, clock.getAsLong() - session.runningSinceMillis);
		}
		addStretch(session, session.stretchStart, snapshot());
		addItems(session, session.stretchLoot, session.stretchSupplies);
		session.runningSinceMillis = 0;
		session.stretchStart = new HashMap<>();
		session.stretchLoot = new HashMap<>();
		session.stretchSupplies = new HashMap<>();
	}

	private void takeStretchStart(GpSession session)
	{
		session.stretchStart = snapshot();
		session.stretchLoot = allLoot();
		session.stretchSupplies = allSupplies();
	}

	/** Adds the items gained and used since the stretch began (an item dropped again counts back off). */
	private void addItems(GpSession session, Map<Integer, Long> lootBefore, Map<String, SupplyLine> suppliesBefore)
	{
		allLoot().forEach((id, qty) ->
		{
			long gained = qty - lootBefore.getOrDefault(id, 0L);
			if (gained != 0)
			{
				session.loot.merge(id, gained, Long::sum);
			}
		});
		session.loot.values().removeIf(q -> q <= 0);
		allSupplies().forEach((key, line) ->
		{
			SupplyLine was = suppliesBefore.get(key);
			long qty = line.quantity - (was == null ? 0 : was.quantity);
			long gp = line.gp - (was == null ? 0 : was.gp);
			if (qty > 0)
			{
				SupplyLine total = session.supplies.computeIfAbsent(key, k -> new SupplyLine(line.itemId, line.name, line.unit));
				total.quantity += qty;
				total.gp += gp;
			}
		});
	}

	/** Every source's loot added together, by item. */
	private Map<Integer, Long> allLoot()
	{
		Map<Integer, Long> all = new HashMap<>();
		for (SourceRecord record : ledger.data().sources.values())
		{
			record.loot.forEach((id, qty) -> all.merge(id, qty, Long::sum));
		}
		return all;
	}

	/** Every source's supplies added together, by supply line. */
	private Map<String, SupplyLine> allSupplies()
	{
		Map<String, SupplyLine> all = new HashMap<>();
		for (SourceRecord record : ledger.data().sources.values())
		{
			record.supplies.forEach((key, line) ->
			{
				SupplyLine total = all.computeIfAbsent(key, k -> new SupplyLine(line.itemId, line.name, line.unit));
				total.quantity += line.quantity;
				total.gp += line.gp;
			});
		}
		return all;
	}

	private static SupplyLine copy(SupplyLine line)
	{
		SupplyLine c = new SupplyLine(line.itemId, line.name, line.unit);
		c.quantity = line.quantity;
		c.gp = line.gp;
		return c;
	}

	/** Adds what changed between two snapshots to the session. Sources reset meanwhile are skipped. */
	private static void addStretch(GpSession session, Map<String, GpSession.Activity> before,
		Map<String, GpSession.Activity> after)
	{
		for (Map.Entry<String, GpSession.Activity> e : after.entrySet())
		{
			GpSession.Activity now = e.getValue();
			GpSession.Activity was = before.get(e.getKey());
			long count = now.count - (was == null ? 0 : was.count);
			long profit = now.profit - (was == null ? 0 : was.profit);
			long xp = now.xp - (was == null ? 0 : was.xp);
			if (count < 0 || xp < 0)
			{
				// The source was reset during the stretch: nothing sensible to add.
				continue;
			}
			if (count == 0 && profit == 0 && xp == 0)
			{
				continue;
			}
			session.profit += profit;
			session.xp += xp;
			GpSession.Activity total = session.activity.computeIfAbsent(e.getKey(),
				k -> new GpSession.Activity(now.name, now.category, 0, 0, 0));
			total.count += count;
			total.profit += profit;
			total.xp += xp;
		}
	}

	private Map<String, GpSession.Activity> snapshot()
	{
		Map<String, GpSession.Activity> snapshot = new HashMap<>();
		for (SourceRecord record : ledger.data().sources.values())
		{
			long xp = record.xp.values().stream().mapToLong(Long::longValue).sum();
			snapshot.put(record.key, new GpSession.Activity(record.name, record.category, record.count,
				record.lootValue(lootPrice) - record.supplyCost(), xp));
		}
		return snapshot;
	}

	// ---------------------------------------------------------------- naming

	/** The session's name: what was done most, e.g. "Blood rune crafting" or "Karambwan fishing". */
	static String titleFor(GpSession session)
	{
		if (session.renamed && session.title != null)
		{
			return session.title;
		}
		return session.activity.entrySet().stream()
			.filter(e -> e.getValue().category != SourceCategory.TELEPORTS
				&& e.getValue().category != SourceCategory.GENERAL_SUPPLIES)
			.max(Comparator.comparingLong((Map.Entry<String, GpSession.Activity> e) -> e.getValue().count)
				.thenComparingLong(e -> Math.abs(e.getValue().profit))
				.thenComparingLong(e -> e.getValue().xp))
			.map(e ->
			{
				GpSession.Activity a = e.getValue();
				if (e.getKey().startsWith("reward:"))
				{
					return a.name;
				}
				if (e.getKey().startsWith("other:"))
				{
					return a.category.getLabel();
				}
				return activityTitle(a.name, a.category);
			})
			.orElse("Session");
	}

	/** "Blood altar" + Runecrafting → "Blood rune crafting"; "Karambwan" + Fishing → "Karambwan fishing". */
	public static String activityTitle(String name, SourceCategory category)
	{
		if (name == null || category == null)
		{
			return "Session";
		}
		String n = name.trim();
		String lower = n.toLowerCase(Locale.ENGLISH);
		switch (category)
		{
			case COMBAT:
				return n;
			case RUNECRAFTING:
				if (lower.endsWith(" altar") && !lower.startsWith("ourania") && !lower.startsWith("dark"))
				{
					return n.substring(0, n.length() - " altar".length()) + " rune crafting";
				}
				return n + " runecrafting";
			case WOODCUTTING:
				return strip(n, " logs") + " woodcutting";
			case MINING:
				return lower.contains("motherlode") ? n : strip(strip(n, " rocks"), " ore") + " mining";
			case HUNTER:
				return n + " hunting";
			case MAGIC:
				return n;
			case OTHER:
				return n;
			default:
				return n + " " + category.getLabel().toLowerCase(Locale.ENGLISH);
		}
	}

	private static String strip(String s, String suffix)
	{
		return s.toLowerCase(Locale.ENGLISH).endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
	}

	/** For tests and the plugin: the data the sessions live in. */
	TrackerData data()
	{
		return ledger.data();
	}
}
