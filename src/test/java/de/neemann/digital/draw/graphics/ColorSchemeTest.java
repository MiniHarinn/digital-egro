/*
 * Copyright (c) 2020 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.draw.graphics;

import junit.framework.TestCase;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Arrays;

public class ColorSchemeTest extends TestCase {

    public void testCompleteness() {
        ColorScheme map = ColorScheme.COLOR_SCHEME.getDefault().getScheme();
        for (ColorKey ck : ColorKey.values())
            assertNotNull(map.getColor(ck));
    }

    /**
     * A custom scheme stored by an older version lacks the colors of newer keys.
     * It must still be usable and editable.
     */
    public void testSchemeWithoutNewerKeys() throws Exception {
        ColorScheme old = new ColorScheme.Builder(ColorScheme.COLOR_SCHEME.getDefault().getScheme())
                .set(ColorKey.HIGHLIGHT, Color.ORANGE)
                .build();
        Field f = ColorScheme.class.getDeclaredField("colors");
        f.setAccessible(true);
        Color[] colors = (Color[]) f.get(old);
        f.set(old, Arrays.copyOf(colors, ColorKey.NET_HIGHLIGHT.ordinal()));

        assertEquals(Color.ORANGE, old.getColor(ColorKey.HIGHLIGHT));
        assertEquals(Color.MAGENTA, old.getColor(ColorKey.NET_HIGHLIGHT));

        ColorScheme edited = new ColorScheme.Builder(old).build();
        for (ColorKey ck : ColorKey.values())
            assertNotNull(edited.getColor(ck));
        assertEquals(Color.ORANGE, edited.getColor(ColorKey.HIGHLIGHT));
    }

}