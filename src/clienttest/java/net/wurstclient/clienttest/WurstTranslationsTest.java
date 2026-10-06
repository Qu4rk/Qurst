/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clienttest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstTranslator;
import net.wurstclient.options.WurstOptionsScreen;
import net.wurstclient.util.ScreenWidgets;

/**
 * Slice 02a proof: bundled translations load on NeoForge, the real Wurst
 * Options control toggles them, and a second JVM restores the persisted
 * non-default state. Phase is selected with
 * {@code -Dwurst.test.phase=1|2}; both phases share one isolated game
 * directory. Not part of the production jar.
 */
@Mod(value = "wurst_clienttest", dist = Dist.CLIENT)
public final class WurstTranslationsTest
{
	private static final String PINNED_KEY =
		"description.wurst.hack.fullbright";
	private static final String PINNED_EN = "Allows you to see in the dark.";
	private static final String PINNED_DE =
		"Ermöglicht dir, im Dunkeln zu sehen.";
	
	private final String phase = System.getProperty("wurst.test.phase", "");
	private int readyTicks;
	private boolean finished;
	private boolean bootstrapped;
	private int stage;
	private int stageTicks;
	private Screen baseScreen;
	
	public WurstTranslationsTest()
	{
		if(!phase.equals("1") && !phase.equals("2"))
			throw new IllegalStateException("Missing -Dwurst.test.phase=1|2");
		
		NeoForge.EVENT_BUS.addListener(this::onTick);
		Thread.ofPlatform().daemon().name("Wurst translations timeout")
			.start(() -> {
				try
				{
					Thread.sleep(180_000);
					System.err.println("WURST_TRANSLATIONS_FAIL: timeout");
					Runtime.getRuntime().halt(2);
				}catch(InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			});
	}
	
	private void onTick(ClientTickEvent.Post event)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.gui.overlay() == null && mc.gui
			.screen() instanceof AccessibilityOnboardingScreen onboarding)
		{
			// Same first-run path as the bootstrap smoke.
			onboarding.onClose();
			return;
		}
		if(finished || mc.gui.overlay() != null)
			return;
		
		WurstClient wurst = WurstClient.INSTANCE;
		if(!bootstrapped)
		{
			if(!(mc.gui.screen() instanceof TitleScreen title))
				return;
			if(++readyTicks < 10)
				return;
			
			if(wurst.getHax().getAllHax().size() != 156
				|| wurst.getCmds().getAllCmds().size() != 52
				|| wurst.getOtfs().getAllOtfs().size() != 18)
				throw new IllegalStateException(
					"Incomplete native Wurst bootstrap");
			bootstrapped = true;
			baseScreen = title;
		}
		
		if(phase.equals("1"))
			onTickPhase1(mc, wurst);
		else
			onTickPhase2(mc, wurst);
	}
	
	private void onTickPhase1(Minecraft mc, WurstClient wurst)
	{
		switch(stage)
		{
			case 0 ->
			{
				// AC-01, English half: observe the real built-in pack
				// identity, then resolve the pinned key with the default
				// Force English=true.
				Identifier langId = Identifier.fromNamespaceAndPath("wurst",
					"translations/en_us.json");
				boolean nativePackSeen = false;
				for(Resource resource : mc.getResourceManager()
					.getResourceStack(langId))
				{
					String known = resource.knownPackInfo()
						.map(Object::toString).orElse("<none>");
					System.out.println("WURST_PACK: " + resource.sourcePackId()
						+ " known=" + known);
					if(resource.knownPackInfo()
						.map(pack -> pack.namespace().equals("neoforge")
							&& pack.id().equals("mod/wurst"))
						.orElse(false))
						nativePackSeen = true;
				}
				if(!nativePackSeen)
					throw new IllegalStateException(
						"Native built-in Wurst pack not observed");
				assertTranslation(wurst, PINNED_EN,
					"default English did not resolve");
				stage = 1;
				stageTicks = 0;
			}
			case 1 ->
			{
				// AC-01, German half at translator level: select de_de
				// through the real manager/options path and reload.
				wurst.getOtfs().translationsOtf.getForceEnglish()
					.setChecked(false);
				mc.getLanguageManager().setSelected("de_de");
				mc.options.languageCode = "de_de";
				mc.options.save();
				mc.reloadResourcePacks();
				stage = 2;
				stageTicks = 0;
			}
			case 2 ->
			{
				// Bounded reload wait, then German + fallback + stub
				// trust/malformed checks (AC-01 remainder, AC-04).
				if(!wurst.translate(PINNED_KEY).equals(PINNED_DE))
				{
					if(++stageTicks > 400)
						throw new IllegalStateException(
							"German translation did not load after reload");
					return;
				}
				assertMissingKeyFallback(wurst);
				assertUnknownKey(wurst);
				runLoaderTrustChecks(wurst);
				mc.gui.setScreen(new WurstOptionsScreen(baseScreen));
				stage = 3;
				stageTicks = 0;
			}
			case 3 ->
			{
				// AC-02: real Options control through the real input path.
				// Stage 1 left Force English=false, so the first UI press
				// flips false->true and the second flips back, ending in
				// the non-default persisted state.
				if(++stageTicks < 3)
					return;
				if(!(mc.gui.screen() instanceof WurstOptionsScreen opts))
				{
					if(stageTicks > 200)
						throw new IllegalStateException(
							"Wurst Options screen never opened");
					return;
				}
				Button toggle = findTranslationsButton(opts);
				clickCenter(toggle);
				assertButtonLabel(toggle, "Translations: OFF");
				assertCondition(wurst.getOtfs().translationsOtf
					.getForceEnglish().isChecked(),
					"Force English not set by UI toggle");
				assertTranslation(wurst, PINNED_EN,
					"English lost after UI toggle off");
				assertMcLanguage(mc);
				clickCenter(toggle);
				assertButtonLabel(toggle, "Translations: ON");
				assertCondition(!wurst.getOtfs().translationsOtf
					.getForceEnglish().isChecked(),
					"Force English not cleared by UI toggle");
				assertTranslation(wurst, PINNED_DE,
					"German lost after UI toggle on");
				assertMcLanguage(mc);
				stage = 4;
			}
			case 4 ->
			{
				// AC-02 back/escape: Back returns to the previous screen,
				// Escape (onClose) does the same on a fresh instance.
				if(!(mc.gui.screen() instanceof WurstOptionsScreen opts))
					throw new IllegalStateException(
						"Options screen lost before Back check");
				clickCenter(findButton(opts, "Back"));
				if(mc.gui.screen() != baseScreen)
					throw new IllegalStateException(
						"Back did not return to previous screen");
				WurstOptionsScreen reopened =
					new WurstOptionsScreen(baseScreen);
				mc.gui.setScreen(reopened);
				reopened.onClose();
				if(mc.gui.screen() != baseScreen)
					throw new IllegalStateException(
						"Escape did not return to previous screen");
				assertMcLanguage(mc);
				// Prove the persistence vehicles before relying on them.
				assertOptionsLang(mc);
				assertSettingsJson(mc, false);
				try
				{
					Files.writeString(gameFile(mc, "phase1-pass.txt"),
						"phase=1 ok translations-toggle de_de\n");
				}catch(IOException e)
				{
					throw new IllegalStateException(
						"Cannot record phase 1 evidence", e);
				}
				finished = true;
				System.out.println(
					"WURST_TRANSLATIONS_PHASE1_OK: toggle persisted de_de");
				mc.stop();
			}
			default -> throw new IllegalStateException("Bad phase 1 stage");
		}
	}
	
	private void onTickPhase2(Minecraft mc, WurstClient wurst)
	{
		// AC-03: a fresh JVM restores the non-default state before any
		// mutation in this process.
		if(++stageTicks > 600)
			throw new IllegalStateException(
				"Restored German translation never appeared");
		if(!wurst.translate(PINNED_KEY).equals(PINNED_DE))
			return;
		if(wurst.getOtfs().translationsOtf.getForceEnglish().isChecked())
			throw new IllegalStateException(
				"Force English was not restored as false");
		assertMcLanguage(mc);
		assertSettingsJson(mc, false);
		try
		{
			Files.writeString(gameFile(mc, "wurst-translations-pass.txt"),
				"156 hacks; 52 commands; 18 other features; de_de restored\n");
		}catch(IOException e)
		{
			throw new IllegalStateException(
				"Cannot record translations evidence", e);
		}
		finished = true;
		System.out
			.println("WURST_TRANSLATIONS_PASS: de_de restored after restart");
		mc.stop();
	}
	
	private static void assertTranslation(WurstClient wurst, String expected,
		String message)
	{
		String actual = wurst.translate(PINNED_KEY);
		if(!expected.equals(actual))
			throw new IllegalStateException(message + ": expected <" + expected
				+ "> but was <" + actual + ">");
	}
	
	private static void assertMcLanguage(Minecraft mc)
	{
		if(!"de_de".equals(mc.getLanguageManager().getSelected()))
			throw new IllegalStateException("Minecraft language left de_de");
		if(!"de_de".equals(mc.options.languageCode))
			throw new IllegalStateException("Options language left de_de");
	}
	
	private static void assertCondition(boolean condition, String message)
	{
		if(!condition)
			throw new IllegalStateException(message);
	}
	
	private static void assertButtonLabel(Button button, String expected)
	{
		String actual = button.getMessage().getString();
		if(!expected.equals(actual))
			throw new IllegalStateException(
				"Button label <" + actual + ">, expected <" + expected + ">");
	}
	
	private static Button findTranslationsButton(Screen screen)
	{
		for(AbstractWidget widget : ScreenWidgets.getWidgets(screen))
			if(widget instanceof Button button
				&& button.getMessage().getString().startsWith("Translations: "))
				return button;
		throw new IllegalStateException("Translations button not found");
	}
	
	private static Button findButton(Screen screen, String label)
	{
		for(AbstractWidget widget : ScreenWidgets.getWidgets(screen))
			if(widget instanceof Button button
				&& button.getMessage().getString().equals(label))
				return button;
		throw new IllegalStateException("Button <" + label + "> not found");
	}
	
	private static void clickCenter(Button button)
	{
		double x = button.getX() + button.getWidth() / 2.0;
		double y = button.getY() + button.getHeight() / 2.0;
		boolean handled = button.mouseClicked(
			new MouseButtonEvent(x, y,
				new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
			false);
		if(!handled)
			throw new IllegalStateException("Click not handled by <"
				+ button.getMessage().getString() + ">");
	}
	
	private static Path gameFile(Minecraft mc, String name)
	{
		return mc.gameDirectory.toPath().resolve(name);
	}
	
	private static void assertOptionsLang(Minecraft mc)
	{
		Path options = mc.gameDirectory.toPath().resolve("options.txt");
		String text;
		try
		{
			text = Files.readString(options);
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read options.txt", e);
		}
		if(!text.contains("lang:de_de"))
			throw new IllegalStateException(
				"options.txt does not persist de_de");
	}
	
	private static void assertSettingsJson(Minecraft mc, boolean expected)
	{
		Path settings =
			mc.gameDirectory.toPath().resolve("wurst/settings.json");
		JsonObject root;
		try
		{
			root = JsonParser.parseString(Files.readString(settings))
				.getAsJsonObject();
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read settings.json", e);
		}
		if(!root.has("Translations")
			|| !root.getAsJsonObject("Translations").has("Force English")
			|| !root.getAsJsonObject("Translations").get("Force English")
				.isJsonPrimitive()
			|| !root.getAsJsonObject("Translations").get("Force English")
				.getAsJsonPrimitive().isBoolean()
			|| root.getAsJsonObject("Translations").get("Force English")
				.getAsBoolean() != expected)
			throw new IllegalStateException(
				"settings.json Translations/Force English != " + expected);
	}
	
	private static void assertMissingKeyFallback(WurstClient wurst)
	{
		String enJson = readAsset("/assets/wurst/translations/en_us.json");
		String deJson = readAsset("/assets/wurst/translations/de_de.json");
		JsonObject enTexts = JsonParser.parseString(enJson).getAsJsonObject();
		JsonObject de = JsonParser.parseString(deJson).getAsJsonObject();
		String missing = null;
		for(String key : enTexts.keySet())
			if(!de.has(key))
			{
				missing = key;
				break;
			}
		if(missing == null)
			throw new IllegalStateException(
				"No English-only key found in assets");
		String actual = wurst.translate(missing);
		String wanted = enTexts.get(missing).getAsString();
		if(!wanted.equals(actual))
			throw new IllegalStateException(
				"Missing-key fallback broken for <" + missing + ">");
	}
	
	private static void assertUnknownKey(WurstClient wurst)
	{
		String key = "wurst.test.unknown.key.zzz";
		if(!key.equals(wurst.translate(key)))
			throw new IllegalStateException(
				"Unknown key did not return upstream fallback");
	}
	
	private static String readAsset(String path)
	{
		// Assets live in the production module; resolve them through a
		// production class so the lookup uses that module's resources.
		try(InputStream in = ScreenWidgets.class.getResourceAsStream(path))
		{
			if(in == null)
				throw new IllegalStateException("Asset missing: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read " + path, e);
		}
	}
	
	/**
	 * AC-04: exercises the real production loader with controlled pack
	 * metadata and streams. Only the ResourceManager/source side is a
	 * fixture; parsing, trust filtering and exception containment are
	 * production code.
	 */
	private static void runLoaderTrustChecks(WurstClient wurst)
	{
		WurstTranslator translator = wurst.getTranslator();
		Method load;
		try
		{
			load = WurstTranslator.class.getDeclaredMethod("loadTranslations",
				ResourceManager.class, Iterable.class, BiConsumer.class);
			load.setAccessible(true);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot reach production loader",
				e);
		}
		
		String goodJson = "{\"description.wurst.test.probe\":\"Probe OK\"}";
		String probe = "description.wurst.test.probe";
		
		// Narrow trust: the native built-in identity is accepted.
		HashMap<String, String> map = new HashMap<>();
		invokeLoader(load, translator,
			stubManager("neoforge", "mod/wurst", goodJson), List.of("probe"),
			map);
		if(!"Probe OK".equals(map.get(probe)))
			throw new IllegalStateException(
				"Native built-in resource was not accepted");
		
		// Missing KnownPack cannot override the key.
		map.clear();
		invokeLoader(load, translator, stubManager(null, null, goodJson),
			List.of("probe"), map);
		if(map.containsKey(probe))
			throw new IllegalStateException(
				"Resource without KnownPack was accepted");
		
		// Wrong id cannot override the key despite the same path.
		for(String[] identity : new String[][]{{"neoforge", "mod/other"},
			{"fabric", "other"}})
		{
			map.clear();
			invokeLoader(load, translator,
				stubManager(identity[0], identity[1], goodJson),
				List.of("probe"), map);
			if(map.containsKey(probe))
				throw new IllegalStateException("Untrusted " + identity[0] + ":"
					+ identity[1] + " was accepted");
		}
		
		// Malformed JSON in an accepted resource cannot crash the reload,
		// and a later valid reload still resolves.
		map.clear();
		invokeLoader(load, translator,
			stubManager("neoforge", "mod/wurst", "{broken json"),
			List.of("probe"), map);
		if(map.containsKey(probe))
			throw new IllegalStateException("Malformed JSON produced entries");
		invokeLoader(load, translator,
			stubManager("neoforge", "mod/wurst", goodJson), List.of("probe"),
			map);
		if(!"Probe OK".equals(map.get(probe)))
			throw new IllegalStateException(
				"Valid reload after malformed input did not resolve");
			
		// Merge order: English first, selected second; a key missing from
		// the selected language keeps its bundled English text.
		map.clear();
		ResourceManager both = proxyManager(code -> {
			if(code.endsWith("en_us.json"))
				return List.of(stubResource("neoforge", "mod/wurst",
					"{\"" + probe + "\":\"Probe EN\"}"));
			return List
				.of(stubResource("neoforge", "mod/wurst", "{\"other\":\"x\"}"));
		});
		invokeLoaderRaw(load, translator, both, List.of("en_us", "de_de"),
			map::put);
		if(!"Probe EN".equals(map.get(probe)))
			throw new IllegalStateException(
				"English fallback for missing key broken");
	}
	
	private static void invokeLoader(Method load, WurstTranslator translator,
		ResourceManager manager, List<String> codes, Map<String, String> map)
	{
		invokeLoaderRaw(load, translator, manager, codes, map::put);
	}
	
	private static void invokeLoaderRaw(Method load, WurstTranslator translator,
		ResourceManager manager, List<String> codes,
		BiConsumer<String, String> consumer)
	{
		try
		{
			load.invoke(translator, manager, codes, consumer);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Production loader failed", e);
		}
	}
	
	private static ResourceManager stubManager(String namespace, String id,
		String json)
	{
		return proxyManager(code -> List.of(stubResource(namespace, id, json)));
	}
	
	private interface StackSelector
	{
		List<Resource> stackFor(String code);
	}
	
	private static ResourceManager proxyManager(StackSelector selector)
	{
		InvocationHandler handler = (proxy, method, args) -> {
			if(method.getName().equals("getResourceStack") && args.length == 1)
				return selector.stackFor(args[0].toString());
			return defaultValue(method.getReturnType());
		};
		return (ResourceManager)Proxy.newProxyInstance(
			ResourceManager.class.getClassLoader(),
			new Class<?>[]{ResourceManager.class}, handler);
	}
	
	private static Resource stubResource(String namespace, String id,
		String json)
	{
		InvocationHandler handler = (proxy, method, args) -> {
			return switch(method.getName())
			{
				case "knownPackInfo" -> namespace == null ? Optional.empty()
					: Optional.of(new KnownPack(namespace, id, "9.9.9-test"));
				case "packId" -> "test-pack";
				case "toString" -> "test-pack";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> defaultValue(method.getReturnType());
			};
		};
		PackResources pack = (PackResources)Proxy.newProxyInstance(
			PackResources.class.getClassLoader(),
			new Class<?>[]{PackResources.class}, handler);
		return new Resource(pack, () -> new ByteArrayInputStream(
			json.getBytes(StandardCharsets.UTF_8)));
	}
	
	private static Object defaultValue(Class<?> type)
	{
		if(!type.isPrimitive())
		{
			if(type == Optional.class)
				return Optional.empty();
			if(type == List.class)
				return List.of();
			if(type == Map.class)
				return Map.of();
			if(type == Stream.class)
				return Stream.empty();
			return null;
		}
		if(type == boolean.class)
			return false;
		if(type == void.class)
			return null;
		return 0;
	}
}
