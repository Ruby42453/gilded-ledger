package com.gildedledger.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every source as it stood when a day, week or month began, so the panel can show just that period. */
public class PeriodStart
{
	/** Which day, week or month this is ("2026-11-09", "2026-W46", "2026-11"), in the chosen time zone. */
	public String id;
	public Map<String, SourceRecord> sources = new LinkedHashMap<>();
}
