/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.graphics.GraphicMinMax;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.gui.components.modification.ModifyAlign;
import de.neemann.digital.gui.components.modification.ModifyMoveSelected;
import de.neemann.digital.lang.Lang;
import de.neemann.gui.ToolTipAction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;

import static de.neemann.digital.draw.shapes.GenericShape.SIZE;

/**
 * Actions working on the selected elements: moving them with the arrow keys and
 * aligning or evenly spacing them. The shortcuts follow Figma.
 */
public final class SelectionActions {
    private enum Mode {LEFT, RIGHT, TOP, BOTTOM, CENTER_H, CENTER_V, SPACE_H, SPACE_V}

    private final CircuitComponent circuitComponent;

    private SelectionActions(CircuitComponent circuitComponent) {
        this.circuitComponent = circuitComponent;
    }

    /**
     * Enables the shortcuts in the given circuit component and creates the align menu
     *
     * @param circuitComponent the circuit component
     * @return the align menu
     */
    public static JMenu install(CircuitComponent circuitComponent) {
        return new SelectionActions(circuitComponent).createActions();
    }

    private JMenu createActions() {
        int[][] nudges = {{KeyEvent.VK_LEFT, -1, 0}, {KeyEvent.VK_RIGHT, 1, 0}, {KeyEvent.VK_UP, 0, -1}, {KeyEvent.VK_DOWN, 0, 1}};
        for (int[] n : nudges)
            for (int steps : new int[]{1, 5}) // Shift moves further
                new ToolTipAction("nudge" + n[0] + "_" + steps) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        nudge(new Vector(n[1], n[2]).mul(SIZE * steps));
                    }
                }.setAccelerator(KeyStroke.getKeyStroke(n[0], steps == 1 ? 0 : InputEvent.SHIFT_DOWN_MASK))
                        .enableAcceleratorIn(circuitComponent);

        JMenu menu = new JMenu(Lang.get("menu_align"));
        menu.setToolTipText(Lang.get("menu_align_tt"));
        menu.add(action(Lang.get("menu_alignLeft"), Mode.LEFT, KeyEvent.VK_A, false));
        menu.add(action(Lang.get("menu_alignCenterH"), Mode.CENTER_H, KeyEvent.VK_H, false));
        menu.add(action(Lang.get("menu_alignRight"), Mode.RIGHT, KeyEvent.VK_D, false));
        menu.addSeparator();
        menu.add(action(Lang.get("menu_alignTop"), Mode.TOP, KeyEvent.VK_W, false));
        menu.add(action(Lang.get("menu_alignCenterV"), Mode.CENTER_V, KeyEvent.VK_V, false));
        menu.add(action(Lang.get("menu_alignBottom"), Mode.BOTTOM, KeyEvent.VK_S, false));
        menu.addSeparator();
        menu.add(action(Lang.get("menu_spaceH"), Mode.SPACE_H, KeyEvent.VK_H, true));
        menu.add(action(Lang.get("menu_spaceV"), Mode.SPACE_V, KeyEvent.VK_V, true));
        return menu;
    }

    private JMenuItem action(String name, Mode mode, int key, boolean shift) {
        int mask = InputEvent.ALT_DOWN_MASK | (shift ? InputEvent.SHIFT_DOWN_MASK : 0);
        return new ToolTipAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                align(mode);
            }
        }.setAccelerator(KeyStroke.getKeyStroke(key, mask)).enableAcceleratorIn(circuitComponent).createJMenuItem();
    }

    private void nudge(Vector delta) {
        Vector[] sel = circuitComponent.getSelection();
        if (sel != null && !circuitComponent.isLocked()) {
            circuitComponent.modify(new ModifyMoveSelected(sel[0], sel[1], delta, 0, sel[0]));
            circuitComponent.setSelection(sel[0].add(delta), sel[1].add(delta));
        }
    }

    private static final class Item {
        private final VisualElement ve;
        private final int lo;
        private final int hi;

        private Item(VisualElement ve, boolean horizontal) {
            this.ve = ve;
            GraphicMinMax mm = ve.getMinMax(false);
            lo = horizontal ? mm.getMin().x : mm.getMin().y;
            hi = horizontal ? mm.getMax().x : mm.getMax().y;
        }
    }

    private void align(Mode mode) {
        Vector[] sel = circuitComponent.getSelection();
        if (sel == null || circuitComponent.isLocked())
            return;

        boolean horizontal = mode == Mode.LEFT || mode == Mode.RIGHT || mode == Mode.CENTER_H || mode == Mode.SPACE_H;
        ArrayList<Item> items = new ArrayList<>();
        for (VisualElement ve : circuitComponent.getCircuit().getElements())
            if (ve.matches(sel[0], sel[1]))
                items.add(new Item(ve, horizontal));

        boolean space = mode == Mode.SPACE_H || mode == Mode.SPACE_V;
        if (items.size() < (space ? 3 : 2))
            return;

        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        int sizes = 0;
        for (Item i : items) {
            lo = Math.min(lo, i.lo);
            hi = Math.max(hi, i.hi);
            sizes += i.hi - i.lo;
        }

        double[] deltas = new double[items.size()];
        if (space) {
            // the outer elements stay where they are, the gaps between all elements become equal
            items.sort(Comparator.comparingInt(i -> i.lo + i.hi));
            double gap = (double) (items.get(items.size() - 1).hi - items.get(0).lo - sizes) / (items.size() - 1);
            double pos = items.get(0).hi + gap;
            for (int n = 1; n < items.size() - 1; n++) {
                Item i = items.get(n);
                deltas[n] = pos - i.lo;
                pos += i.hi - i.lo + gap;
            }
        } else
            for (int n = 0; n < items.size(); n++) {
                Item i = items.get(n);
                switch (mode) {
                    case LEFT:
                    case TOP:
                        deltas[n] = lo - i.lo;
                        break;
                    case RIGHT:
                    case BOTTOM:
                        deltas[n] = hi - i.hi;
                        break;
                    default:
                        deltas[n] = (lo + hi - i.lo - i.hi) / 2.0;
                }
            }

        ModifyAlign modification = new ModifyAlign();
        Vector min = sel[0];
        Vector max = sel[1];
        for (int n = 0; n < items.size(); n++) {
            // stay on the grid
            int d = (int) Math.round(deltas[n] / SIZE) * SIZE;
            Vector delta = horizontal ? new Vector(d, 0) : new Vector(0, d);
            VisualElement ve = items.get(n).ve;
            modification.add(ve, delta);
            GraphicMinMax mm = ve.getMinMax(false);
            min = Vector.min(min, mm.getMin().add(delta));
            max = Vector.max(max, mm.getMax().add(delta));
        }
        if (!modification.isEmpty()) {
            circuitComponent.modify(modification);
            circuitComponent.setSelection(min, max);
        }
    }
}
