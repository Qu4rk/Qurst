/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.keybindtest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui.screens.ClickGuiScreen;
import net.wurstclient.keybinds.Keybind;
import net.wurstclient.options.EnterProfileNameScreen;
import net.wurstclient.options.KeybindEditorScreen;
import net.wurstclient.options.KeybindManagerScreen;
import net.wurstclient.options.KeybindProfilesScreen;
import net.wurstclient.options.WurstOptionsScreen;
import net.wurstclient.util.ScreenWidgets;

/**
 * Slice 02b proof: keybind add/edit/remove through the real manager,
 * editor and capture screens, profile create/list/load, then a second
 * JVM restoring the bindings and proving native input dispatch. Phase
 * is selected with {@code -Dwurst.test.phase=1|2}; both phases share
 * one isolated game directory. Not part of the production jar.
 *
 * <p>
 * Stage discipline: every stage that navigates ends with
 * {@code stageTicks = 0}; every stage that needs settled widgets
 * waits first. All screen changes are synchronous, so assertions after
 * navigation hold immediately while button-active states settle over
 * ticks.
 */
@Mod(value = "wurst_keybindtest", dist = Dist.CLIENT)
public final class WurstKeybindsTest
{
	private static final String F8 = "key.keyboard.f8";
	private static final String F9 = "key.keyboard.f9";
	private static final String F10 = "key.keyboard.f10";
	private static final String MOUSE4 = "key.mouse.4";
	private static final String CMDS_DOUBLE = "fullbright;fullbright";
	private static final String CMDS_SINGLE = "fullbright";
	
	private final String phase = System.getProperty("wurst.test.phase", "");
	private int readyTicks;
	private boolean finished;
	private boolean bootstrapped;
	private int stage;
	private int stageTicks;
	private Screen baseScreen;
	
	public WurstKeybindsTest()
	{
		if(!phase.equals("1") && !phase.equals("2"))
			throw new IllegalStateException("Missing -Dwurst.test.phase=1|2");
		
		NeoForge.EVENT_BUS.addListener(this::onTick);
		Thread.ofPlatform().daemon().name("Wurst keybinds timeout")
			.start(() -> {
				try
				{
					Thread.sleep(180_000);
					System.err.println("WURST_KEYBINDS_FAIL: timeout");
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
				// Enter through the real Options entry point.
				mc.gui.setScreen(new WurstOptionsScreen(baseScreen));
				stageTicks = 0;
				stage = 1;
			}
			case 1 ->
			{
				if(++stageTicks < 3)
					return;
				WurstOptionsScreen opts =
					requireScreen(mc, WurstOptionsScreen.class,
						"Wurst Options screen never opened");
				clickButton(findButton(opts, "Keybinds"));
				stageTicks = 0;
				stage = 2;
			}
			case 2 ->
			{
				// Disabled no-selection states plus defaults baseline.
				if(++stageTicks < 3)
					return;
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "Keybind Manager never opened");
				assertCondition(
					!findButton(manager, "Edit").active
						&& !findButton(manager, "Remove").active,
					"Edit/Remove enabled without selection");
				assertModel(wurst, expectedDefaults(),
					"default bindings changed before journey");
				assertFileJson(wurstFile(mc, "keybinds.json"),
					expectedDefaults(), "fresh keybinds.json mismatch");
				manager
					.keyPressed(new KeyEvent(InputConstants.KEY_DELETE, 0, 0));
				assertModel(wurst, expectedDefaults(),
					"Delete without selection changed bindings");
				stageTicks = 0;
				stage = 3;
			}
			case 3 ->
			{
				// Sentinel repro: saving an unselected key must not
				// persist NONE. Fails on the unfixed product.
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Add"));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "editor never opened");
				clickButton(findButton(editor, "Change Key"));
				KeybindEditorScreen back = requirePressEscape(mc, editor);
				typeInto(back, CMDS_SINGLE);
				clickButton(findButton(back, "Save"));
				if(wurst.getKeybinds().getCommands("NONE") != null)
					throw new IllegalStateException(
						"Unselected NONE key was persisted");
				if(!(mc.gui.screen() instanceof KeybindEditorScreen))
					throw new IllegalStateException(
						"Guarded Save left the editor");
				clickButton(
					findButton((KeybindEditorScreen)mc.gui.screen(), "Cancel"));
				assertModel(wurst, expectedDefaults(),
					"Cancelled editor polluted bindings");
				stageTicks = 0;
				stage = 4;
			}
			case 4 ->
			{
				// Add F8 through capture, typed commands, real Save.
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Add"));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "editor never opened");
				clickButton(findButton(editor, "Change Key"));
				requirePressKey(mc, editor, InputConstants.KEY_F8, F8);
				KeybindEditorScreen back = requireScreen(mc,
					KeybindEditorScreen.class, "capture did not return");
				typeInto(back, CMDS_DOUBLE);
				clickButton(findButton(back, "Save"));
				assertCommands(wurst, F8, CMDS_DOUBLE);
				assertFileCommands(wurstFile(mc, "keybinds.json"), F8,
					CMDS_DOUBLE);
				stageTicks = 0;
				stage = 5;
			}
			case 5 ->
			{
				// Select the F8 row through real list input.
				selectManagerRow(mc, wurst, F8);
				stageTicks = 0;
				stage = 6;
			}
			case 6 ->
			{
				// Edit F8 -> F9 through the keyboard Enter path.
				if(++stageTicks < 3)
					return;
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				assertCondition(findButton(manager, "Edit").active,
					"Edit not enabled with selection");
				manager
					.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "Enter did not open the editor");
				assertFieldValue(editor, CMDS_DOUBLE);
				clickButton(findButton(editor, "Change Key"));
				requirePressKey(mc, editor, InputConstants.KEY_F9, F9);
				KeybindEditorScreen back =
					requireScreen(mc, KeybindEditorScreen.class, "editor lost");
				// Type after the last capture: returning from capture
				// re-runs init, which restores the old commands.
				typeInto(back, CMDS_SINGLE);
				clickButton(findButton(back, "Save"));
				assertCommands(wurst, F9, CMDS_SINGLE);
				assertAbsent(wurst, F8);
				stageTicks = 0;
				stage = 7;
			}
			case 7 ->
			{
				// Add mouse button 4 through the real capture screen.
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Add"));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "editor never opened");
				clickButton(findButton(editor, "Change Key"));
				Screen capture = mc.gui.screen();
				if(capture == null || capture instanceof KeybindEditorScreen)
					throw new IllegalStateException(
						"capture screen never opened");
				capture.mouseClicked(
					new MouseButtonEvent(capture.width / 2.0,
						capture.height / 2.0, new MouseButtonInfo(4, 0)),
					false);
				KeybindEditorScreen back = requireScreen(mc,
					KeybindEditorScreen.class, "mouse capture did not return");
				typeInto(back, CMDS_SINGLE);
				clickButton(findButton(back, "Save"));
				assertCommands(wurst, MOUSE4, CMDS_SINGLE);
				stageTicks = 0;
				stage = 8;
			}
			case 8 ->
			{
				// Temporary F10 added, then selected for removal.
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Add"));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "editor never opened");
				clickButton(findButton(editor, "Change Key"));
				requirePressKey(mc, editor, InputConstants.KEY_F10, F10);
				KeybindEditorScreen back = requireScreen(mc,
					KeybindEditorScreen.class, "capture did not return");
				typeInto(back, CMDS_SINGLE);
				clickButton(findButton(back, "Save"));
				assertCommands(wurst, F10, CMDS_SINGLE);
				selectManagerRow(mc, wurst, F10);
				stageTicks = 0;
				stage = 9;
			}
			case 9 ->
			{
				// Remove button, then manager Escape through the key path.
				if(++stageTicks < 3)
					return;
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Remove"));
				assertAbsent(wurst, F10);
				assertFileAbsent(wurstFile(mc, "keybinds.json"), F10);
				requireScreen(mc, KeybindManagerScreen.class, "manager lost")
					.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
				if(!(mc.gui.screen() instanceof WurstOptionsScreen))
					throw new IllegalStateException(
						"Manager Escape missed Wurst Options");
				stageTicks = 0;
				stage = 10;
			}
			case 10 ->
			{
				// Profiles: empty state first.
				WurstOptionsScreen opts =
					requireScreen(mc, WurstOptionsScreen.class, "options lost");
				clickButton(findButton(opts, "Keybinds"));
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Profiles..."));
				stageTicks = 0;
				stage = 11;
			}
			case 11 ->
			{
				if(++stageTicks < 3)
					return;
				KeybindProfilesScreen profiles = requireScreen(mc,
					KeybindProfilesScreen.class, "profiles never opened");
				assertCondition(wurst.getKeybinds().listProfiles().isEmpty(),
					"profiles directory not fresh");
				assertCondition(!findButton(profiles, "Load").active,
					"Load enabled without selection");
				clickButton(findButton(profiles, "New Profile"));
				stageTicks = 0;
				stage = 12;
			}
			case 12 ->
			{
				if(++stageTicks < 3)
					return;
				EnterProfileNameScreen nameScreen = requireScreen(mc,
					EnterProfileNameScreen.class, "name prompt never opened");
				typeInto(nameScreen, "slice02b");
				clickButton(findButton(nameScreen, "Done"));
				KeybindProfilesScreen profiles =
					requireScreen(mc, KeybindProfilesScreen.class,
						"Done missed the profiles screen");
				Path snapshot = wurst.getKeybinds().getProfilesFolder()
					.resolve("slice02b.json");
				if(!Files.isRegularFile(snapshot))
					throw new IllegalStateException(
						"slice02b.json was not created");
				assertFileJson(snapshot, expectedSnapshot(),
					"profile snapshot mismatch");
				assertProfileRow(profiles, "slice02b.json");
				stageTicks = 0;
				stage = 13;
			}
			case 13 ->
			{
				// Malformed + invalid-key fixtures live only in the
				// dedicated profiles directory.
				writeString(wurst.getKeybinds().getProfilesFolder()
					.resolve("broken.json"), "{broken json");
				writeString(
					wurst.getKeybinds().getProfilesFolder()
						.resolve("invalidkeys.json"),
					"{\"" + F8 + "\":\"fullbright\","
						+ "\"key.keyboard.notakey\":\"fullbright\"}");
				KeybindProfilesScreen profiles = requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost");
				clickButton(findButton(profiles, "Cancel"));
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Profiles..."));
				stageTicks = 0;
				stage = 14;
			}
			case 14 ->
			{
				if(++stageTicks < 3)
					return;
				KeybindProfilesScreen profiles = requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost");
				assertProfileRow(profiles, "broken.json");
				assertProfileRow(profiles, "invalidkeys.json");
				selectProfileRow(mc, profiles, "broken.json");
				stageTicks = 0;
				stage = 15;
			}
			case 15 ->
			{
				// Malformed load must keep screen, bindings and file.
				if(++stageTicks < 3)
					return;
				Map<String, String> before = modelSnapshot(wurst);
				byte[] fileBefore = readBytes(wurstFile(mc, "keybinds.json"));
				clickButton(findButton(requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost"), "Load"));
				if(!(mc.gui.screen() instanceof KeybindProfilesScreen))
					throw new IllegalStateException(
						"Malformed profile load left the screen");
				assertModel(wurst, before, "malformed load changed bindings");
				assertFileBytes(wurstFile(mc, "keybinds.json"), fileBefore,
					"malformed load changed keybinds.json");
				stageTicks = 0;
				stage = 16;
			}
			case 16 ->
			{
				// Narrow public-seam check: invalid keys are skipped while
				// the valid entry is preserved. This replaces the active
				// list, so the next stage restores it through the GUI.
				try
				{
					wurst.getKeybinds().loadProfile("invalidkeys.json");
				}catch(IOException | RuntimeException e)
				{
					throw new IllegalStateException(
						"Invalid-key profile load crashed", e);
				}catch(Exception e)
				{
					throw new IllegalStateException(
						"Invalid-key profile load crashed", e);
				}
				Map<String, String> single = new LinkedHashMap<>();
				single.put(F8, CMDS_SINGLE);
				assertModel(wurst, single,
					"invalid key was not skipped cleanly");
				stageTicks = 0;
				stage = 17;
			}
			case 17 ->
			{
				// GUI load restores the profile snapshot after clobbering.
				selectProfileRow(mc, requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost"),
					"slice02b.json");
				stageTicks = 0;
				stage = 18;
			}
			case 18 ->
			{
				if(++stageTicks < 3)
					return;
				clickButton(findButton(requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost"), "Load"));
				if(!(mc.gui.screen() instanceof KeybindManagerScreen))
					throw new IllegalStateException(
						"Profile load missed the manager");
				assertModel(wurst, expectedSnapshot(),
					"GUI profile load did not restore snapshot");
				stageTicks = 0;
				stage = 19;
			}
			case 19 ->
			{
				// Mutate through the editor.
				selectManagerRow(mc, wurst, F9);
				stageTicks = 0;
				stage = 20;
			}
			case 20 ->
			{
				if(++stageTicks < 3)
					return;
				KeybindManagerScreen manager = requireScreen(mc,
					KeybindManagerScreen.class, "manager lost");
				clickButton(findButton(manager, "Edit"));
				KeybindEditorScreen editor = requireScreen(mc,
					KeybindEditorScreen.class, "editor never opened");
				clickButton(findButton(editor, "Change Key"));
				requirePressKey(mc, editor, InputConstants.KEY_F9, F9);
				KeybindEditorScreen back =
					requireScreen(mc, KeybindEditorScreen.class, "editor lost");
				typeInto(back, CMDS_DOUBLE);
				clickButton(findButton(back, "Save"));
				assertCommands(wurst, F9, CMDS_DOUBLE);
				clickButton(
					findButton(requireScreen(mc, KeybindManagerScreen.class,
						"manager lost"), "Profiles..."));
				stageTicks = 0;
				stage = 21;
			}
			case 21 ->
			{
				if(++stageTicks < 3)
					return;
				selectProfileRow(mc, requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost"),
					"slice02b.json");
				stageTicks = 0;
				stage = 22;
			}
			case 22 ->
			{
				// Final GUI load restores the snapshot before exit.
				if(++stageTicks < 3)
					return;
				clickButton(findButton(requireScreen(mc,
					KeybindProfilesScreen.class, "profiles lost"), "Load"));
				if(!(mc.gui.screen() instanceof KeybindManagerScreen))
					throw new IllegalStateException(
						"Final profile load missed the manager");
				assertModel(wurst, expectedSnapshot(),
					"final restore mismatch");
				assertFileJson(wurstFile(mc, "keybinds.json"),
					expectedSnapshot(), "final keybinds.json mismatch");
				try
				{
					Files.writeString(gameFile(mc, "phase1-pass.txt"),
						"phase=1 ok keybinds-profile slice02b\n");
				}catch(IOException e)
				{
					throw new IllegalStateException(
						"Cannot record phase 1 evidence", e);
				}
				finished = true;
				System.out.println(
					"WURST_KEYBINDS_PHASE1_OK: profile slice02b journey done");
				mc.stop();
			}
			default -> throw new IllegalStateException("Bad phase 1 stage");
		}
	}
	
	private void onTickPhase2(Minecraft mc, WurstClient wurst)
	{
		switch(stage)
		{
			case 0 ->
			{
				// AC-04: fresh JVM asserts restored state before any
				// mutation in this process. Poll briefly for the boot
				// reload to settle, then assert exactly once.
				if(!expectedSnapshot().equals(modelSnapshot(wurst)))
				{
					if(++stageTicks > 300)
						throw new IllegalStateException(
							"bindings were not restored after restart");
					return;
				}
				assertFileJson(wurstFile(mc, "keybinds.json"),
					expectedSnapshot(), "keybinds.json not restored");
				assertFileJson(
					wurst.getKeybinds().getProfilesFolder()
						.resolve("slice02b.json"),
					expectedSnapshot(), "profile file not intact");
				assertCondition(
					wurst.getKeybinds().listProfiles().stream()
						.anyMatch(path -> path.getFileName().toString()
							.equals("slice02b.json")),
					"profile not discoverable after restart");
				mc.gui.setScreen(new ClickGuiScreen(wurst.getGui()));
				stageTicks = 0;
				stage = 1;
			}
			case 1 ->
			{
				// AC-05: real transformed native handlers on the client
				// thread drive the real processor; Fullbright enabled
				// state is the independent oracle.
				if(++stageTicks < 3)
					return;
				if(!(mc.gui.screen() instanceof ClickGuiScreen))
					throw new IllegalStateException(
						"ClickGui screen never opened");
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"Fullbright started enabled");
				pressKey(mc, InputConstants.KEY_F9);
				assertCondition(wurst.getHax().fullbrightHack.isEnabled(),
					"F9 PRESS did not toggle Fullbright");
				pressKey(mc, InputConstants.KEY_F9);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"F9 PRESS did not toggle back");
				releaseKey(mc, InputConstants.KEY_F9);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"RELEASE toggled Fullbright");
				repeatKey(mc, InputConstants.KEY_F9);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"REPEAT toggled Fullbright");
				pressMouse(mc, 4);
				assertCondition(wurst.getHax().fullbrightHack.isEnabled(),
					"mouse.4 PRESS did not toggle Fullbright");
				pressMouse(mc, 4);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"mouse.4 PRESS did not toggle back");
				releaseMouse(mc, 4);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"mouse RELEASE toggled Fullbright");
				stageTicks = 0;
				stage = 2;
			}
			case 2 ->
			{
				// Input while an editor is open must not dispatch.
				mc.gui.setScreen(new KeybindEditorScreen(baseScreen));
				if(++stageTicks < 3)
					return;
				if(!(mc.gui.screen() instanceof KeybindEditorScreen))
					throw new IllegalStateException(
						"editor never opened for suppression check");
				pressKey(mc, InputConstants.KEY_F9);
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"editor-open input dispatched a binding");
				mc.gui.screen().onClose();
				if(mc.gui.screen() != baseScreen)
					throw new IllegalStateException(
						"editor close missed base screen");
				assertCondition(!wurst.getHax().fullbrightHack.isEnabled(),
					"Fullbright left enabled");
				try
				{
					Files.writeString(gameFile(mc, "wurst-keybinds-pass.txt"),
						"156 hacks; 52 commands; 18 other features; keybinds restored\n");
				}catch(IOException e)
				{
					throw new IllegalStateException(
						"Cannot record keybind evidence", e);
				}
				finished = true;
				System.out.println(
					"WURST_KEYBINDS_PASS: keybinds restored and dispatching");
				mc.stop();
			}
			default -> throw new IllegalStateException("Bad phase 2 stage");
		}
	}
	
	private static <T extends Screen> T requireScreen(Minecraft mc,
		Class<T> type, String message)
	{
		if(!type.isInstance(mc.gui.screen()))
			throw new IllegalStateException(
				message + " (at " + mc.gui.screen() + ")");
		return type.cast(mc.gui.screen());
	}
	
	private static void assertCondition(boolean condition, String message)
	{
		if(!condition)
			throw new IllegalStateException(message);
	}
	
	private static Map<String, String> expectedDefaults()
	{
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("key.keyboard.b", "fastplace;fastbreak");
		defaults.put("key.keyboard.c", "fullbright");
		defaults.put("key.keyboard.g", "flight");
		defaults.put("key.keyboard.semicolon", "speednuker");
		defaults.put("key.keyboard.h", "say /home");
		defaults.put("key.keyboard.j", "jesus");
		defaults.put("key.keyboard.k", "multiaura");
		defaults.put("key.keyboard.n", "nuker");
		defaults.put("key.keyboard.r", "killaura");
		defaults.put("key.keyboard.right.shift", "navigator");
		defaults.put("key.keyboard.right.control", "clickgui");
		defaults.put("key.keyboard.u", "freecam");
		defaults.put("key.keyboard.x", "x-ray");
		defaults.put("key.keyboard.y", "sneak");
		return defaults;
	}
	
	private static Map<String, String> expectedSnapshot()
	{
		Map<String, String> snapshot = expectedDefaults();
		snapshot.put(F9, CMDS_SINGLE);
		snapshot.put(MOUSE4, CMDS_SINGLE);
		return snapshot;
	}
	
	private static Map<String, String> modelSnapshot(WurstClient wurst)
	{
		Map<String, String> snapshot = new LinkedHashMap<>();
		for(Keybind keybind : wurst.getKeybinds().getAllKeybinds())
			snapshot.put(keybind.getKey(), keybind.getCommands());
		return snapshot;
	}
	
	private static void assertModel(WurstClient wurst,
		Map<String, String> expected, String message)
	{
		Map<String, String> actual = modelSnapshot(wurst);
		if(!expected.equals(actual))
			throw new IllegalStateException(
				message + ": expected " + expected + " but was " + actual);
	}
	
	private static void assertCommands(WurstClient wurst, String key,
		String commands)
	{
		String actual = wurst.getKeybinds().getCommands(key);
		if(!commands.equals(actual))
			throw new IllegalStateException("Binding <" + key + "> is <"
				+ actual + ">, expected <" + commands + ">");
	}
	
	private static void assertAbsent(WurstClient wurst, String key)
	{
		if(wurst.getKeybinds().getCommands(key) != null)
			throw new IllegalStateException(
				"Binding <" + key + "> should be absent");
	}
	
	private static JsonObject readJson(Path path)
	{
		try
		{
			return JsonParser.parseString(Files.readString(path))
				.getAsJsonObject();
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read " + path, e);
		}
	}
	
	private static void assertFileJson(Path path, Map<String, String> expected,
		String message)
	{
		JsonObject json = readJson(path);
		if(json.size() != expected.size())
			throw new IllegalStateException(message + ": size " + json.size());
		for(Map.Entry<String, String> entry : expected.entrySet())
			if(!json.has(entry.getKey())
				|| !json.get(entry.getKey()).isJsonPrimitive() || !entry
					.getValue().equals(json.get(entry.getKey()).getAsString()))
				throw new IllegalStateException(
					message + ": entry <" + entry.getKey() + "> mismatch");
	}
	
	private static void assertFileCommands(Path path, String key,
		String commands)
	{
		JsonObject json = readJson(path);
		if(!json.has(key) || !commands.equals(json.get(key).getAsString()))
			throw new IllegalStateException(
				"File binding <" + key + "> mismatch in " + path);
	}
	
	private static void assertFileAbsent(Path path, String key)
	{
		if(readJson(path).has(key))
			throw new IllegalStateException(
				"File binding <" + key + "> should be absent");
	}
	
	private static void assertFileBytes(Path path, byte[] expected,
		String message)
	{
		byte[] actual;
		try
		{
			actual = Files.readAllBytes(path);
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read " + path, e);
		}
		if(!java.util.Arrays.equals(expected, actual))
			throw new IllegalStateException(message);
	}
	
	private static byte[] readBytes(Path path)
	{
		try
		{
			return Files.readAllBytes(path);
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot read " + path, e);
		}
	}
	
	private static void writeString(Path path, String text)
	{
		try
		{
			Files.createDirectories(path.getParent());
			Files.writeString(path, text, StandardCharsets.UTF_8);
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot write " + path, e);
		}
	}
	
	private static Path gameFile(Minecraft mc, String name)
	{
		return mc.gameDirectory.toPath().resolve(name);
	}
	
	private static Path wurstFile(Minecraft mc, String name)
	{
		return mc.gameDirectory.toPath().resolve("wurst").resolve(name);
	}
	
	private static Button findButton(Screen screen, String label)
	{
		for(AbstractWidget widget : ScreenWidgets.getWidgets(screen))
			if(widget instanceof Button button
				&& button.getMessage().getString().equals(label))
				return button;
		throw new IllegalStateException(
			"Button <" + label + "> not found on " + screen.getClass());
	}
	
	private static EditBox findEditBox(Screen screen)
	{
		for(AbstractWidget widget : ScreenWidgets.getWidgets(screen))
			if(widget instanceof EditBox field)
				return field;
		throw new IllegalStateException(
			"EditBox not found on " + screen.getClass());
	}
	
	private static void clickButton(Button button)
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
	
	private static void typeInto(Screen screen, String text)
	{
		EditBox field = findEditBox(screen);
		double x = field.getX() + field.getWidth() / 2.0;
		double y = field.getY() + field.getHeight() / 2.0;
		screen.mouseClicked(
			new MouseButtonEvent(x, y,
				new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
			false);
		if(screen.getFocused() != field)
			throw new IllegalStateException("EditBox did not take focus");
		field.setValue("");
		for(int i = 0; i < text.length(); i++)
			if(!field.charTyped(new CharacterEvent(text.charAt(i))))
				throw new IllegalStateException(
					"Character input rejected at index " + i);
	}
	
	private static KeybindEditorScreen requirePressEscape(Minecraft mc,
		KeybindEditorScreen editor)
	{
		clickButton(findButton(editor, "Change Key"));
		if(mc.gui.screen() == null
			|| mc.gui.screen() instanceof KeybindEditorScreen)
			throw new IllegalStateException("capture screen never opened");
		mc.gui.screen()
			.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
		KeybindEditorScreen back = requireScreen(mc, KeybindEditorScreen.class,
			"capture Escape missed the editor");
		if(back != editor)
			throw new IllegalStateException(
				"capture Escape returned a different editor");
		return back;
	}
	
	private static void requirePressKey(Minecraft mc,
		KeybindEditorScreen editor, int keyCode, String expectedName)
	{
		clickButton(findButton(editor, "Change Key"));
		if(mc.gui.screen() == null
			|| mc.gui.screen() instanceof KeybindEditorScreen)
			throw new IllegalStateException("capture screen never opened");
		mc.gui.screen().keyPressed(new KeyEvent(keyCode, 0, 0));
		KeybindEditorScreen back = requireScreen(mc, KeybindEditorScreen.class,
			"capture missed the editor");
		if(back != editor)
			throw new IllegalStateException(
				"capture returned a different editor");
		String resolved =
			InputConstants.getKey(new KeyEvent(keyCode, 0, 0)).getName();
		if(!expectedName.equals(resolved))
			throw new IllegalStateException("Capture resolved <" + resolved
				+ ">, expected <" + expectedName + ">");
	}
	
	private static void assertFieldValue(KeybindEditorScreen editor,
		String expected)
	{
		String actual = findEditBox(editor).getValue();
		if(!expected.equals(actual))
			throw new IllegalStateException(
				"Editor field <" + actual + ">, expected <" + expected + ">");
	}
	
	private static Object managerList(KeybindManagerScreen manager)
	{
		try
		{
			Field field =
				KeybindManagerScreen.class.getDeclaredField("listGui");
			field.setAccessible(true);
			return field.get(manager);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot reach manager list", e);
		}
	}
	
	private static Object profilesList(KeybindProfilesScreen profiles)
	{
		try
		{
			Field field =
				KeybindProfilesScreen.class.getDeclaredField("listGui");
			field.setAccessible(true);
			return field.get(profiles);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot reach profiles list", e);
		}
	}
	
	private static void selectManagerRow(Minecraft mc, WurstClient wurst,
		String key)
	{
		KeybindManagerScreen manager =
			requireScreen(mc, KeybindManagerScreen.class, "manager lost");
		Object list = managerList(manager);
		clickListRow(list, rowIndexForKey(list, key), 30);
	}
	
	private static int rowIndexForKey(Object list, String key)
	{
		List<?> children = ((ObjectSelectionList<?>)list).children();
		for(int i = 0; i < children.size(); i++)
			if(key.equals(entryKeybind(children.get(i))))
				return i;
		throw new IllegalStateException("No manager row for <" + key + ">");
	}
	
	private static String entryKeybind(Object entry)
	{
		try
		{
			Field field = entry.getClass().getDeclaredField("keybind");
			field.setAccessible(true);
			return ((Keybind)field.get(entry)).getKey();
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot read entry keybind", e);
		}
	}
	
	private static void selectProfileRow(Minecraft mc,
		KeybindProfilesScreen profiles, String fileName)
	{
		Object list = profilesList(profiles);
		List<?> children = ((ObjectSelectionList<?>)list).children();
		for(int i = 0; i < children.size(); i++)
			if(fileName.equals(entryPath(children.get(i))))
			{
				clickListRow(list, i, 20);
				return;
			}
		throw new IllegalStateException(
			"No profiles row for <" + fileName + ">");
	}
	
	private static String entryPath(Object entry)
	{
		try
		{
			Field field = entry.getClass().getDeclaredField("path");
			field.setAccessible(true);
			return "" + ((Path)field.get(entry)).getFileName();
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot read entry path", e);
		}
	}
	
	private static void assertProfileRow(KeybindProfilesScreen profiles,
		String fileName)
	{
		Object list = profilesList(profiles);
		for(Object child : ((ObjectSelectionList<?>)list).children())
			if(fileName.equals(entryPath(child)))
				return;
		throw new IllegalStateException(
			"No profiles row for <" + fileName + ">");
	}
	
	private static void clickListRow(Object list, int index, int rowHeight)
	{
		ObjectSelectionList<?> rows = (ObjectSelectionList<?>)list;
		if(index < 0 || index >= rows.children().size())
			throw new IllegalStateException("Row index out of range");
		scrollIntoView(rows, rows.children().get(index));
		double x = rows.getRowLeft() + rows.getRowWidth() / 2.0;
		double y = rows.getRowTop(index) + rowHeight / 2.0;
		boolean handled = rows.mouseClicked(
			new MouseButtonEvent(x, y,
				new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
			false);
		if(!handled)
			throw new IllegalStateException("Row click not handled");
	}
	
	private static void scrollIntoView(ObjectSelectionList<?> rows,
		Object entry)
	{
		try
		{
			Method scroll = findScrollMethod(rows);
			scroll.setAccessible(true);
			scroll.invoke(rows, entry);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot scroll list", e);
		}
	}
	
	private static Method findScrollMethod(ObjectSelectionList<?> rows)
		throws NoSuchMethodException
	{
		// scrollToEntry lives on the vanilla superclass, not the
		// production ListGui subclass, so walk the hierarchy.
		for(Class<?> type = rows.getClass(); type != null; type =
			type.getSuperclass())
			for(Method method : type.getDeclaredMethods())
				if(method.getName().equals("scrollToEntry")
					&& method.getParameterCount() == 1)
					return method;
		throw new NoSuchMethodException("scrollToEntry");
	}
	
	private static void pressKey(Minecraft mc, int keyCode)
	{
		mc.keyboardHandler.keyPress(mc.getWindow().handle(),
			InputConstants.PRESS, new KeyEvent(keyCode, 0, 0));
	}
	
	private static void releaseKey(Minecraft mc, int keyCode)
	{
		mc.keyboardHandler.keyPress(mc.getWindow().handle(),
			InputConstants.RELEASE, new KeyEvent(keyCode, 0, 0));
	}
	
	private static void repeatKey(Minecraft mc, int keyCode)
	{
		mc.keyboardHandler.keyPress(mc.getWindow().handle(),
			InputConstants.REPEAT, new KeyEvent(keyCode, 0, 0));
	}
	
	private static void pressMouse(Minecraft mc, int button)
	{
		mc.mouseHandler.onButton(mc.getWindow().handle(),
			new MouseButtonInfo(button, 0), InputConstants.PRESS);
	}
	
	private static void releaseMouse(Minecraft mc, int button)
	{
		mc.mouseHandler.onButton(mc.getWindow().handle(),
			new MouseButtonInfo(button, 0), InputConstants.RELEASE);
	}
}
