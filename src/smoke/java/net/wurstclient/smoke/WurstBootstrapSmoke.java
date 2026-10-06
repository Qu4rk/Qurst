/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.smoke;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ScreenWidgets;

@Mod(value = "wurst_smoke", dist = Dist.CLIENT)
public final class WurstBootstrapSmoke
{
	private int readyTicks;
	private boolean finished;
	
	public WurstBootstrapSmoke()
	{
		NeoForge.EVENT_BUS.addListener(this::onTick);
		Thread.ofPlatform().daemon().name("Wurst smoke timeout").start(() -> {
			try
			{
				Thread.sleep(180_000);
				System.err.println("WURST_SMOKE_FAIL: title screen timeout");
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
			// Follow the same close/fade path as the first-run Continue button.
			onboarding.onClose();
			return;
		}
		if(finished || !(mc.gui.screen() instanceof TitleScreen title)
			|| mc.gui.overlay() != null)
			return;
		if(++readyTicks < 10)
			return;
		
		WurstClient wurst = WurstClient.INSTANCE;
		if(wurst.getHax().getAllHax().size() != 156
			|| wurst.getCmds().getAllCmds().size() != 52
			|| wurst.getOtfs().getAllOtfs().size() != 18
			|| ScreenWidgets.getWidgets(title).stream()
				.noneMatch(widget -> widget.getMessage().getString()
					.equals("Alt Manager")))
			throw new IllegalStateException(
				"Incomplete native Wurst bootstrap");
			
		// REV-01 regression: execute the actual transformed production
		// handler on a real initialized PauseScreen and inspect the
		// button it creates. Mixin merges @Inject handlers into the
		// target under a remapped name, so the handler is found by its
		// single-CallbackInfo signature plus original-name fragment
		// (vanilla declares no CallbackInfo methods). PauseScreen(false)
		// skips createPauseMenu in init, which needs a world (player
		// connection NPE without one); invoking the handler itself is
		// the bounded fixture. Old buttons.add code throws UOE here;
		// the fix registers children/renderables/narratables
		// (input/rendering/narration).
		if(!WurstClient.INSTANCE.getOtfs().wurstOptionsOtf
			.isVisibleInGameMenu())
			throw new IllegalStateException(
				"Wurst Options not visible in game menu");
		PauseScreen pause = new PauseScreen(false);
		pause.init(title.width, title.height);
		StringBuilder candidates = new StringBuilder();
		Method handler = null;
		for(Method m : PauseScreen.class.getDeclaredMethods())
		{
			Class<?>[] params = m.getParameterTypes();
			if(params.length == 1 && params[0] == CallbackInfo.class)
			{
				candidates.append(m.getName()).append(';');
				if(m.getName().contains("addWurstOptionsButton"))
					handler = m;
			}
		}
		System.out.println("WURST_PAUSE_HANDLER_CANDIDATES: " + candidates);
		if(handler == null)
			throw new IllegalStateException(
				"Remapped pause handler not found: " + candidates);
		handler.setAccessible(true);
		try
		{
			handler.invoke(pause,
				new CallbackInfo("addWurstOptionsButton", false));
		}catch(InvocationTargetException e)
		{
			throw new IllegalStateException("PauseScreenMixin handler failed",
				e.getCause());
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot invoke pause handler", e);
		}
		Button wurstButton = null;
		for(AbstractWidget widget : ScreenWidgets.getWidgets(pause))
			if(widget instanceof Button button && "            Options"
				.equals(button.getMessage().getString()))
				wurstButton = button;
		boolean placedAsProduced =
			wurstButton != null && wurstButton.getX() == pause.width / 2 - 102
				&& wurstButton.getY() == 60 && wurstButton.getWidth() == 204
				&& wurstButton.getHeight() == 20;
		boolean inChildren =
			wurstButton != null && pause.children().contains(wurstButton);
		boolean inRenderables = false;
		if(wurstButton != null)
			for(Renderable r : pause.renderables)
				if(r == wurstButton)
					inRenderables = true;
		boolean inNarratables = false;
		if(wurstButton != null)
			try
			{
				Field narrField = Screen.class.getDeclaredField("narratables");
				narrField.setAccessible(true);
				List<?> narratables = (List<?>)narrField.get(pause);
				inNarratables = narratables.contains(wurstButton)
					&& wurstButton instanceof NarratableEntry;
			}catch(ReflectiveOperationException e)
			{
				throw new IllegalStateException("Cannot inspect narratables",
					e);
			}
		if(wurstButton == null || !placedAsProduced || !inChildren
			|| !inRenderables || !inNarratables || !wurstButton.visible)
			throw new IllegalStateException(
				"Pause registration contract broken");
		
		try
		{
			Files.writeString(Path.of("wurst-bootstrap-pass.txt"),
				"156 hacks; 52 commands; 18 other features; title screen\n");
		}catch(IOException e)
		{
			throw new IllegalStateException("Cannot record bootstrap evidence",
				e);
		}
		finished = true;
		System.out.println(
			"WURST_SMOKE_PASS: all feature registries and title screen");
		System.out.println(
			"WURST_SMOKE_PAUSE_OK: transformed pause handler registered in children/renderables/narratables");
		mc.stop();
	}
}
