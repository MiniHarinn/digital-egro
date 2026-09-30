/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.core.element.PinDescription;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.gui.components.modification.ModifyDeleteWire;
import de.neemann.digital.gui.components.modification.ModifyInsertWires;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.undo.Modification;
import de.neemann.digital.undo.Modifications;

import java.util.Arrays;

/**
 * Inserts a component into a wire: If an input and an output of a newly placed component
 * both lie inside the same wire, the part of the wire between them is removed, so that
 * the component is connected in series. Without this, the pins would not be connected at
 * all, because wires only connect at their end points.
 */
final class WireInserter {

    private WireInserter() {
    }

    /**
     * Creates the modification which inserts the element into a wire.
     *
     * @param circuit the circuit the element was placed in
     * @param element the placed element
     * @return the modification or null if the element was not placed on a wire
     */
    static Modification<Circuit> create(Circuit circuit, VisualElement element) {
        for (Wire w : circuit.getWires()) {
            Vector in = null;
            Vector out = null;
            for (Pin p : element.getPins())
                if (w.contains(p.getPos())) {
                    if (p.getDirection() == PinDescription.Direction.input && in == null)
                        in = p.getPos();
                    else if (p.getDirection() == PinDescription.Direction.output && out == null)
                        out = p.getPos();
                }

            if (in != null && out != null) {
                Vector near = in;
                Vector far = out;
                if (distance(w.p1, in) > distance(w.p1, out)) {
                    near = out;
                    far = in;
                }
                return new Modifications.Builder<Circuit>(Lang.get("mod_insertedIntoWire"))
                        .add(new ModifyDeleteWire(w))
                        .add(ModifyInsertWires.create(Arrays.asList(new Wire(w.p1, near), new Wire(far, w.p2))))
                        .build();
            }
        }
        return null;
    }

    private static int distance(Vector a, Vector b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
