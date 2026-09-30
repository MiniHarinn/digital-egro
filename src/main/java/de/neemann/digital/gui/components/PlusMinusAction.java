/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.core.Bits;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.core.io.Const;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.library.ElementNotFoundException;
import de.neemann.digital.gui.components.modification.ModifyAttribute;
import de.neemann.gui.ToolTipAction;

import java.awt.event.ActionEvent;

/**
 * Increases or decreases the number of inputs of the element under the mouse,
 * or the value of a constant.
 */
final class PlusMinusAction extends ToolTipAction {
    private final CircuitComponent circuitComponent;
    private final ElementLibrary library;
    private final int delta;

    PlusMinusAction(CircuitComponent circuitComponent, ElementLibrary library, int delta) {
        super("plusMinus");
        this.circuitComponent = circuitComponent;
        this.library = library;
        this.delta = delta;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!circuitComponent.isLocked()) {
            VisualElement ve = circuitComponent.getActualVisualElement();
            if (ve != null) {
                try {
                    if (library.getElementType(ve.getElementName()).hasAttribute(Keys.INPUT_COUNT)) {
                        int number = ve.getElementAttributes().get(Keys.INPUT_COUNT) + delta;
                        if (number >= Keys.INPUT_COUNT.getMin() && number <= Keys.INPUT_COUNT.getMax())
                            circuitComponent.modify(new ModifyAttribute<>(ve, Keys.INPUT_COUNT, number));
                    } else if (ve.equalsDescription(Const.DESCRIPTION)) {
                        long v = ve.getElementAttributes().get(Keys.VALUE) + delta;
                        v &= Bits.mask(ve.getElementAttributes().getBits());
                        circuitComponent.modify(new ModifyAttribute<>(ve, Keys.VALUE, v));
                    }
                } catch (ElementNotFoundException e1) {
                    // do nothing on error
                }
            }
        }
    }
}
