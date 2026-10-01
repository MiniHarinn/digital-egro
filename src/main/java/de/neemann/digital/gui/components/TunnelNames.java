/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Tunnel;
import de.neemann.digital.draw.elements.VisualElement;

import javax.swing.*;
import java.util.TreeSet;

/**
 * Offers the net names already used by the tunnels of a circuit, so that
 * they don't have to be remembered and typos are avoided.
 */
final class TunnelNames {

    private TunnelNames() {
    }

    /**
     * Returns the net names used by the tunnels of the circuit, sorted
     *
     * @param circuit the circuit, maybe null
     * @return the net names
     */
    static String[] of(Circuit circuit) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (circuit != null)
            for (VisualElement ve : circuit.getElements())
                if (ve.equalsDescription(Tunnel.DESCRIPTION)) {
                    String name = ve.getElementAttributes().get(Keys.NETNAME).trim();
                    if (!name.isEmpty())
                        names.add(name);
                }
        return names.toArray(new String[0]);
    }

    /**
     * Creates an editable combo box offering the net names used in the circuit
     *
     * @param circuit the circuit, maybe null
     * @param value   the current net name
     * @return the combo box, its editor is a text field
     */
    static JComboBox<String> createComboBox(Circuit circuit, String value) {
        JComboBox<String> comboBox = new JComboBox<>(of(circuit));
        comboBox.setEditable(true);
        comboBox.setMaximumRowCount(15);
        comboBox.setSelectedItem(value);
        return comboBox;
    }
}
