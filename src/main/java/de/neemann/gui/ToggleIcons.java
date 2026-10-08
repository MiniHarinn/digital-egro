/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.gui;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * Check box and radio button icons which are painted directly.
 * The icons of the Ocean theme are painted using cached volatile images, which are sometimes
 * lost under XWayland (e.g. Hyprland), so that the check boxes disappear while their text is still shown.
 */
public final class ToggleIcons {
    private static final int SIZE = 13;

    private ToggleIcons() {
    }

    /**
     * Replaces the check box and radio button icons of the current look and feel.
     * The icons are scaled like all other icons by {@link Screen}.
     */
    public static void install() {
        float scaling = Screen.getInstance().getScaling();
        UIManager.put("CheckBox.icon", new ToggleIcon(false, scaling));
        UIManager.put("RadioButton.icon", new ToggleIcon(true, scaling));
    }

    private static final class ToggleIcon implements Icon {
        private final boolean radio;
        private final float scaling;
        private final int size;

        private ToggleIcon(boolean radio, float scaling) {
            this.radio = radio;
            this.scaling = scaling;
            size = Math.round(SIZE * scaling);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel model = c instanceof AbstractButton ? ((AbstractButton) c).getModel() : null;
            boolean enabled = model == null || model.isEnabled();
            boolean pressed = model != null && model.isArmed() && model.isPressed();
            boolean rollover = model != null && model.isRollover();
            boolean selected = model != null && model.isSelected();

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.scale(scaling, scaling);

                Color fill = enabled ? (pressed ? MetalLookAndFeel.getControlShadow() : Color.WHITE) : MetalLookAndFeel.getControl();
                Color border = enabled
                        ? (rollover ? MetalLookAndFeel.getPrimaryControlShadow() : MetalLookAndFeel.getControlDarkShadow())
                        : MetalLookAndFeel.getControlShadow();
                Color mark = enabled ? MetalLookAndFeel.getControlInfo() : MetalLookAndFeel.getControlShadow();

                g2.setStroke(new BasicStroke(1f));
                if (radio) {
                    g2.setColor(fill);
                    g2.fillOval(0, 0, SIZE - 1, SIZE - 1);
                    g2.setColor(border);
                    g2.drawOval(0, 0, SIZE - 1, SIZE - 1);
                    if (selected) {
                        g2.setColor(mark);
                        g2.fillOval(3, 3, SIZE - 6, SIZE - 6);
                    }
                } else {
                    g2.setColor(fill);
                    g2.fillRect(0, 0, SIZE - 1, SIZE - 1);
                    g2.setColor(border);
                    g2.drawRect(0, 0, SIZE - 1, SIZE - 1);
                    if (selected) {
                        Path2D.Float check = new Path2D.Float();
                        check.moveTo(2.5f, 6.5f);
                        check.lineTo(5f, 9.5f);
                        check.lineTo(10f, 3f);
                        g2.setColor(mark);
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(check);
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
