/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.PinException;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.graphics.GraphicMinMax;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.draw.model.Net;
import de.neemann.digital.draw.model.NetList;
import de.neemann.digital.gui.components.modification.ModifyAlign;
import de.neemann.digital.gui.components.modification.ModifyMoveSelected;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.undo.ModifyException;
import de.neemann.gui.ToolTipAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import static de.neemann.digital.draw.shapes.GenericShape.SIZE;

/**
 * Actions working on the selected elements: moving them with the arrow keys and
 * aligning or evenly spacing them. The shortcuts follow Figma.
 */
public final class SelectionActions {
    private enum Mode {LEFT, RIGHT, TOP, BOTTOM, CENTER_H, CENTER_V, SPACE_H, SPACE_V, TRANSPOSE, REVERSE}

    private final CircuitComponent circuitComponent;
    // the selection and the elements of the last arrangement, so that repeating it uses the same elements
    private Vector[] lastSelection;
    private ArrayList<VisualElement> lastElements;

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
        menu.addSeparator();
        menu.add(action(Lang.get("menu_transpose"), Mode.TRANSPOSE, KeyEvent.VK_T, false));
        menu.add(action(Lang.get("menu_reverse"), Mode.REVERSE, KeyEvent.VK_R, false));
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
        if (mode == Mode.TRANSPOSE) {
            transpose(sel);
            return;
        }
        if (mode == Mode.REVERSE) {
            reverse(sel);
            return;
        }

        boolean horizontal = mode == Mode.LEFT || mode == Mode.RIGHT || mode == Mode.CENTER_H || mode == Mode.SPACE_H;
        ArrayList<Item> items = new ArrayList<>();
        for (VisualElement ve : selected(sel))
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

        ArrayList<VisualElement> elements = new ArrayList<>();
        ArrayList<Vector> moves = new ArrayList<>();
        for (int n = 0; n < items.size(); n++) {
            int d = toGrid(deltas[n]);
            elements.add(items.get(n).ve);
            moves.add(horizontal ? new Vector(d, 0) : new Vector(0, d));
        }
        apply(sel, elements, moves);
    }

    private static int toGrid(double d) {
        return (int) Math.round(d / SIZE) * SIZE;
    }

    private ArrayList<VisualElement> selected(Vector[] sel) {
        // after an arrangement the selection may cover further elements, which must not be included
        if (Arrays.equals(sel, lastSelection) && circuitComponent.getCircuit().getElements().containsAll(lastElements))
            return new ArrayList<>(lastElements);
        ArrayList<VisualElement> elements = new ArrayList<>();
        for (VisualElement ve : circuitComponent.getCircuit().getElements())
            if (ve.matches(sel[0], sel[1]))
                elements.add(ve);
        return elements;
    }

    // true if the elements are spread more horizontally than vertically
    private static boolean isRow(ArrayList<VisualElement> elements) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (VisualElement ve : elements) {
            GraphicMinMax mm = ve.getMinMax(false);
            int cx = mm.getMin().x + mm.getMax().x;
            int cy = mm.getMin().y + mm.getMax().y;
            minX = Math.min(minX, cx);
            maxX = Math.max(maxX, cx);
            minY = Math.min(minY, cy);
            maxY = Math.max(maxY, cy);
        }
        return maxX - minX >= maxY - minY;
    }

    // reverses the order of a row or a column, the gaps are reversed as well
    private void reverse(Vector[] sel) {
        ArrayList<VisualElement> elements = selected(sel);
        if (elements.size() < 2)
            return;
        boolean isRow = isRow(elements);

        ArrayList<Item> items = new ArrayList<>();
        for (VisualElement ve : elements)
            items.add(new Item(ve, isRow));
        items.sort(Comparator.comparingInt(i -> i.lo + i.hi));

        int n = items.size();
        int pos = items.get(0).lo;
        ArrayList<Vector> moves = new ArrayList<>();
        elements.clear();
        for (int k = 0; k < n; k++) {
            Item i = items.get(n - 1 - k);
            int d = toGrid(pos - i.lo);
            elements.add(i.ve);
            moves.add(isRow ? new Vector(d, 0) : new Vector(0, d));
            if (k < n - 1)
                pos += i.hi - i.lo + items.get(n - 1 - k).lo - items.get(n - 2 - k).hi;
        }
        apply(sel, elements, moves);
    }

    // turns a row into a column and vice versa, the order and the gaps are kept
    private void transpose(Vector[] sel) {
        ArrayList<VisualElement> elements = selected(sel);
        if (elements.size() < 2)
            return;
        boolean isRow = isRow(elements);

        // old main axis: x for a row, new main axis: y for a row
        ArrayList<Item> oldMain = new ArrayList<>();
        for (VisualElement ve : elements)
            oldMain.add(new Item(ve, isRow));
        oldMain.sort(Comparator.comparingInt(i -> i.lo + i.hi));

        int cross = oldMain.get(0).lo;
        int pos = new Item(oldMain.get(0).ve, !isRow).hi;
        ArrayList<Vector> moves = new ArrayList<>();
        elements.clear();
        for (int n = 0; n < oldMain.size(); n++) {
            Item o = oldMain.get(n);
            Item t = new Item(o.ve, !isRow);
            int dMain = 0;
            if (n > 0) {
                int gap = o.lo - oldMain.get(n - 1).hi;
                dMain = toGrid(pos + gap - t.lo);
                pos = t.hi + dMain;
            }
            int dCross = toGrid(cross - o.lo);
            elements.add(o.ve);
            moves.add(isRow ? new Vector(dCross, dMain) : new Vector(dMain, dCross));
        }
        apply(sel, elements, moves);
    }

    private void apply(Vector[] sel, ArrayList<VisualElement> elements, ArrayList<Vector> moves) {
        ModifyAlign modification = new ModifyAlign();
        Vector min = null;
        Vector max = null;
        for (int n = 0; n < elements.size(); n++) {
            VisualElement ve = elements.get(n);
            Vector delta = moves.get(n);
            modification.add(ve, delta);
            GraphicMinMax mm = ve.getMinMax(false);
            // the new selection fits exactly around the arranged elements
            min = min == null ? mm.getMin().add(delta) : Vector.min(min, mm.getMin().add(delta));
            max = max == null ? mm.getMax().add(delta) : Vector.max(max, mm.getMax().add(delta));
        }
        if (modification.isEmpty())
            return;
        if (connectsSomething(modification)) {
            Toolkit.getDefaultToolkit().beep();
            circuitComponent.getMain().setStatus(Lang.get("msg_alignWouldConnect"));
            return;
        }
        circuitComponent.modify(modification);
        circuitComponent.setSelection(min, max);
        lastSelection = circuitComponent.getSelection();
        lastElements = new ArrayList<>(elements);
    }

    // True if a moved pin or wire end would touch another wire or pin, which would connect them.
    private boolean connectsSomething(ModifyAlign modification) {
        Circuit circuit = circuitComponent.getCircuit();
        try {
            int[] before = connections(circuit);
            Circuit moved = circuit.createDeepCopy();
            modification.modify(moved);
            try {
                return !Arrays.equals(before, connections(moved));
            } catch (PinException e) {
                return true;
            }
        } catch (PinException | ModifyException e) {
            return false; // the circuit is broken anyway, don't block the user
        }
    }

    // numbers the net of every pin, pins connected with each other get the same number
    private static int[] connections(Circuit circuit) throws PinException {
        NetList netList = new NetList(circuit);
        HashMap<Net, Integer> ids = new HashMap<>();
        ArrayList<Integer> list = new ArrayList<>();
        for (VisualElement ve : circuit.getElements())
            for (Pin p : ve.getPins()) {
                Net net = netList.getNetOfPos(p.getPos());
                list.add(net == null ? -1 - list.size() : ids.computeIfAbsent(net, n -> ids.size()));
            }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = list.get(i);
        return result;
    }
}
