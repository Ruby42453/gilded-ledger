package com.gildedledger;

import java.io.File;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.slf4j.LoggerFactory;

/**
 * Launches RuneLite with Gilded Ledger loaded. Run with -ea (the Gradle "run" task does this).
 *
 * <p>Development only: also turns on the plugin's debug trace and writes it, and nothing from
 * other plugins, to {@code logs/gilded-ledger-<date>_<time>.log} in the project folder.</p>
 */
public class GildedLedgerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		System.setProperty("gildedledger.trace", "true");
		startDebugLog();
		ExternalPluginManager.loadBuiltin(GildedLedgerPlugin.class);
		RuneLite.main(args);
	}

	/**
	 * Sends the "com.gildedledger" logs to their own file. RuneLite logs with Logback; it's
	 * set up through reflection so this file builds without depending on Logback directly.
	 */
	private static void startDebugLog()
	{
		try
		{
			File dir = new File("logs");
			dir.mkdirs();
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			File file = new File(dir, "gilded-ledger-" + stamp + ".log");

			Object context = LoggerFactory.getILoggerFactory();
			Class<?> contextType = Class.forName("ch.qos.logback.core.Context");

			Object encoder = Class.forName("ch.qos.logback.classic.encoder.PatternLayoutEncoder")
				.getDeclaredConstructor().newInstance();
			call(encoder, "setContext", contextType, context);
			call(encoder, "setPattern", String.class, "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{0} - %msg%n");
			call(encoder, "start");

			Object appender = Class.forName("ch.qos.logback.core.FileAppender").getDeclaredConstructor().newInstance();
			call(appender, "setContext", contextType, context);
			call(appender, "setName", String.class, "gilded-ledger-debug");
			call(appender, "setFile", String.class, file.getAbsolutePath());
			call(appender, "setAppend", boolean.class, false);
			call(appender, "setEncoder", Class.forName("ch.qos.logback.core.encoder.Encoder"), encoder);
			call(appender, "start");

			Object logger = LoggerFactory.getLogger("com.gildedledger");
			Class<?> levelType = Class.forName("ch.qos.logback.classic.Level");
			call(logger, "setLevel", levelType, levelType.getField("DEBUG").get(null));
			call(logger, "addAppender", Class.forName("ch.qos.logback.core.Appender"), appender);

			System.out.println("Gilded Ledger debug log: " + file.getAbsolutePath());
		}
		catch (Exception e)
		{
			System.err.println("Gilded Ledger debug log could not be started: " + e);
		}
	}

	private static void call(Object target, String method, Class<?> type, Object value) throws Exception
	{
		Method m = target.getClass().getMethod(method, type);
		m.invoke(target, value);
	}

	private static void call(Object target, String method) throws Exception
	{
		target.getClass().getMethod(method).invoke(target);
	}
}
