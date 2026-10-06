/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class QurstBranding
{
	public static final int BACKGROUND = 0x21152F;
	public static final int ACCENT = 0xA73BD4;
	public static final int TEXT = 0xEEE8FA;
	private static final Identifier LOGO_TEXTURE =
		Identifier.fromNamespaceAndPath("wurst", "qurst-logo.png");
	
	private QurstBranding()
	{}
	
	public static void drawLogo(GuiGraphicsExtractor context, int x, int y,
		int width, int height)
	{
		// Sample the wordmark inside the unchanged 2000 x 2000 supplied logo.
		context.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 345, 750,
			width, height, 1300, 365, 2000, 2000);
	}
}
