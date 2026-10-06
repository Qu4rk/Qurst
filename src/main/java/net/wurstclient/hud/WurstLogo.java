/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.WurstClient;
import net.wurstclient.other_features.WurstLogoOtf;
import net.wurstclient.util.QurstBranding;
import net.wurstclient.util.RenderUtils;

public final class WurstLogo
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	
	public void render(GuiGraphicsExtractor context)
	{
		WurstLogoOtf otf = WURST.getOtfs().wurstLogoOtf;
		if(!otf.isVisible())
			return;
		
		String version = getVersionString();
		Font tr = WurstClient.MC.font;
		
		// background
		int bgColor;
		if(WURST.getHax().rainbowUiHack.isEnabled())
			bgColor = RenderUtils.toIntColor(WURST.getGui().getAcColor(), 0.5F);
		else
			bgColor = otf.getBackgroundColor();
		context.fill(0, 6, tr.width(version) + 76, 17, bgColor);
		
		context.guiRenderState.up();
		
		// version string
		context.text(tr, version, 74, 8, otf.getTextColor(), false);
		
		// Qurst wordmark, using the supplied artwork without changing the
		// texture.
		QurstBranding.drawLogo(context, 0, 2, 72, 20);
	}
	
	private String getVersionString()
	{
		String version = "v" + WurstClient.VERSION;
		version += " MC" + WurstClient.MC_VERSION;
		
		if(WURST.getUpdater().isOutdated())
			version += " (outdated)";
		
		return version;
	}
}
