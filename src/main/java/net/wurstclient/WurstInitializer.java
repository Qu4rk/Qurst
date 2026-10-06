/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

// Adapted for native NeoForge 26.3 on 2026-10-06.

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "wurst", dist = Dist.CLIENT)
public final class WurstInitializer
{
	private static boolean initialized;
	
	public WurstInitializer()
	{
		NeoForge.EVENT_BUS.addListener(this::onClientStarted);
	}
	
	private void onClientStarted(ClientStartedEvent event)
	{
		if(initialized)
			throw new IllegalStateException("Wurst initialized twice!");
		WurstClient.INSTANCE.initialize();
		initialized = true;
	}
}
