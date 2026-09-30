/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components.modification;

import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.undo.Modification;
import de.neemann.digital.undo.ModifyException;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Moves several elements, each by its own distance.
 * Wire ends connected to a pin of a moved element follow the pin, so no connection gets lost.
 */
public class ModifyAlign implements Modification<Circuit> {
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Vector> positions = new ArrayList<>();
    private final ArrayList<Vector> deltas = new ArrayList<>();

    /**
     * Adds an element to move
     *
     * @param ve    the element
     * @param delta the distance to move the element
     */
    public void add(VisualElement ve, Vector delta) {
        if (delta.x != 0 || delta.y != 0) {
            names.add(ve.getElementName());
            positions.add(ve.getPos());
            deltas.add(delta);
        }
    }

    /**
     * @return true if no element is moved
     */
    public boolean isEmpty() {
        return names.isEmpty();
    }

    @Override
    public void modify(Circuit circuit) throws ModifyException {
        // find all elements first, a moved element could take the place of another one
        ArrayList<VisualElement> found = new ArrayList<>();
        for (int i = 0; i < names.size(); i++)
            found.add(find(circuit, names.get(i), positions.get(i), found));

        HashMap<Vector, Vector> pinDelta = new HashMap<>();
        for (int i = 0; i < found.size(); i++)
            for (Pin p : found.get(i).getPins())
                pinDelta.putIfAbsent(p.getPos(), deltas.get(i));

        for (int i = 0; i < found.size(); i++)
            found.get(i).move(deltas.get(i));

        for (Wire w : circuit.getWires()) {
            Vector d1 = pinDelta.get(w.p1);
            Vector d2 = pinDelta.get(w.p2);
            if (d1 != null) w.getMovableP1().move(d1);
            if (d2 != null) w.getMovableP2().move(d2);
        }
        circuit.elementsMoved();
    }

    private static VisualElement find(Circuit circuit, String name, Vector pos, ArrayList<VisualElement> found) throws ModifyException {
        for (VisualElement ve : circuit.getElements())
            if (ve.getPos().equals(pos) && ve.getElementName().equals(name) && !found.contains(ve))
                return ve;
        throw new ModifyException("internal error: Element not found!");
    }

    @Override
    public String toString() {
        return Lang.get("mod_aligned");
    }
}
