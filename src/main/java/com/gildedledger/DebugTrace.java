package com.gildedledger;

import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.SupplyLine;
import com.gildedledger.data.TrackerData;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step-by-step trace of what the plugin saw and booked, for development only. It's off
 * unless RuneLite was started with {@code -Dgildedledger.trace=true}, which the developer
 * launcher ({@code GildedLedgerPluginTest}) does; it then writes to the "com.gildedledger"
 * log, which the launcher sends to its own file.
 *
 * <p>Each tick that changed anything gets one line per entry: what it did, the XP, the loot
 * and supplies with their value, and the profit. Clicks, XP drops, chat messages and game
 * state changes are logged as they happen.</p>
 */
final class DebugTrace
{
	private static final Logger log = LoggerFactory.getLogger("com.gildedledger.trace");
	static final boolean ENABLED = Boolean.getBoolean("gildedledger.trace");

	private final IntFunction<String> names;
	private final IntToLongFunction prices;
	private final NumberFormat number = NumberFormat.getIntegerInstance(Locale.ENGLISH);
	private Map<String, SourceRecord> snapshot;

	DebugTrace(IntFunction<String> names, IntToLongFunction prices)
	{
		this.names = names;
		this.prices = prices;
	}

	void click(int tick, String option, String target, int itemId, Object action)
	{
		if (ENABLED)
		{
			log.debug("tick {} CLICK {} | {}{} ({})", tick, option, target,
				itemId > 0 ? " [item " + itemId + " " + names.apply(itemId) + "]" : "", action);
		}
	}

	void xp(int tick, String skill, int gained)
	{
		if (ENABLED)
		{
			log.debug("tick {} XP {} +{}", tick, skill, number.format(gained));
		}
	}

	void chat(int tick, Object type, String message)
	{
		if (ENABLED)
		{
			log.debug("tick {} CHAT [{}] {}", tick, type, message);
		}
	}

	void note(int tick, String what)
	{
		if (ENABLED)
		{
			log.debug("tick {} {}", tick, what);
		}
	}

	/** The baseline to compare against (after loading a profile or resetting). */
	void rebase(TrackerData data)
	{
		if (ENABLED)
		{
			snapshot = copy(data);
		}
	}

	/** End of a tick: one line for every entry that changed, saying what it gained and spent. */
	void endOfTick(int tick, TrackerData data, Object location)
	{
		if (!ENABLED || data == null)
		{
			return;
		}
		Map<String, SourceRecord> now = copy(data);
		Map<String, SourceRecord> before = snapshot == null ? new HashMap<>() : snapshot;
		snapshot = now;
		Set<String> keys = new HashSet<>(before.keySet());
		keys.addAll(now.keySet());
		for (String key : keys)
		{
			String line = describe(before.get(key), now.get(key));
			if (line != null)
			{
				SourceRecord r = now.containsKey(key) ? now.get(key) : before.get(key);
				log.debug("tick {} BOOKED {} > {} [{}] at {} | {}", tick,
					r.category == null ? "?" : r.category.getLabel(), r.name, key, location, line);
			}
		}
	}

	private String describe(SourceRecord was, SourceRecord now)
	{
		if (now == null)
		{
			return "removed";
		}
		List<String> parts = new ArrayList<>();
		long count = now.count - (was == null ? 0 : was.count);
		if (count != 0)
		{
			parts.add((count > 0 ? "+" : "") + count + " " + (now.category == null ? "actions" : now.category.getCountLabel().toLowerCase(Locale.ENGLISH)));
		}
		List<String> xp = new ArrayList<>();
		now.xp.forEach((skill, amount) ->
		{
			long d = amount - (was == null ? 0 : was.xp.getOrDefault(skill, 0L));
			if (d != 0)
			{
				xp.add(skill + " " + signed(d));
			}
		});
		if (!xp.isEmpty())
		{
			parts.add("XP " + String.join(", ", xp));
		}
		long lootGp = 0;
		List<String> loot = new ArrayList<>();
		Set<Integer> lootIds = new HashSet<>(now.loot.keySet());
		if (was != null)
		{
			lootIds.addAll(was.loot.keySet());
		}
		for (int id : lootIds)
		{
			long d = now.loot.getOrDefault(id, 0L) - (was == null ? 0 : was.loot.getOrDefault(id, 0L));
			if (d != 0)
			{
				long gp = prices.applyAsLong(id) * d;
				lootGp += gp;
				loot.add(signed(d) + " " + names.apply(id) + " (" + number.format(gp) + " gp)");
			}
		}
		if (!loot.isEmpty())
		{
			parts.add("loot " + String.join(", ", loot));
		}
		long supplyGp = 0;
		List<String> supplies = new ArrayList<>();
		Set<String> lines = new HashSet<>(now.supplies.keySet());
		if (was != null)
		{
			lines.addAll(was.supplies.keySet());
		}
		for (String lineKey : lines)
		{
			SupplyLine n = now.supplies.get(lineKey);
			SupplyLine w = was == null ? null : was.supplies.get(lineKey);
			long dq = (n == null ? 0 : n.quantity) - (w == null ? 0 : w.quantity);
			long dg = (n == null ? 0 : n.gp) - (w == null ? 0 : w.gp);
			if (dq != 0 || dg != 0)
			{
				SupplyLine any = n != null ? n : w;
				String name = any.name != null ? any.name : names.apply(any.itemId);
				supplyGp += dg;
				supplies.add(signed(dq) + " " + name + (any.unit != null ? " " + any.unit : "") + " (" + number.format(dg) + " gp)");
			}
		}
		if (!supplies.isEmpty())
		{
			parts.add("supplies " + String.join(", ", supplies));
		}
		if (parts.isEmpty())
		{
			return null;
		}
		if (lootGp != 0 || supplyGp != 0)
		{
			parts.add("profit " + signed(lootGp - supplyGp) + " gp");
		}
		return String.join(" | ", parts);
	}

	private String signed(long n)
	{
		return (n > 0 ? "+" : "") + number.format(n);
	}

	private static Map<String, SourceRecord> copy(TrackerData data)
	{
		Map<String, SourceRecord> copy = new LinkedHashMap<>();
		data.sources.forEach((key, record) -> copy.put(key, record.copy()));
		return copy;
	}
}
