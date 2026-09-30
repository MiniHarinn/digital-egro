/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui;

import de.neemann.digital.gui.components.CircuitComponent;
import de.neemann.digital.lang.Lang;
import de.neemann.gui.Screen;

import javax.swing.*;
import java.awt.*;

/**
 * Shows the keys which can be used in the current editing mode, e.g. while drawing a wire.
 * This way also the shortcuts of the original Digital are learned, which are not shown in any menu.
 */
final class KeyHints extends JLabel {

    /**
     * Creates the hint label and keeps it up to date
     *
     * @param circuitComponent the circuit component to follow
     */
    KeyHints(CircuitComponent circuitComponent) {
        super(" ");
        setForeground(Color.GRAY);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Screen.getInstance().getFontSize() * 2 / 3));
        circuitComponent.addPropertyChangeListener(CircuitComponent.MOUSE_MODE,
                e -> setText(hintFor((String) e.getNewValue())));
    }

    private static String hintFor(String mode) {
        String hint;
        switch (mode) {
            case "MouseControllerNormal":
                hint = Lang.get("hint_normal");
                break;
            case "MouseControllerWireRect":
                hint = Lang.get("hint_wire");
                break;
            case "MouseControllerWireDiag":
                hint = Lang.get("hint_wireDiag");
                break;
            case "MouseControllerInsertElement":
                hint = Lang.get("hint_insert");
                break;
            case "MouseControllerMoveElement":
                hint = Lang.get("hint_move");
                break;
            case "MouseControllerSelect":
                hint = Lang.get("hint_select");
                break;
            case "MouseControllerMoveSelected":
            case "MouseControllerInsertCopied":
                hint = Lang.get("hint_moveSelected");
                break;
            case "MouseControllerMoveWire":
                hint = Lang.get("hint_moveWire");
                break;
            case "MouseControllerRun":
                hint = Lang.get("hint_run");
                break;
            default:
                return " ";
        }
        if (Screen.isMac())
            hint = hint.replace("Ctrl+", "⌘").replace("Strg+", "⌘").replace("Alt+", "⌥");
        return hint;
    }
}
