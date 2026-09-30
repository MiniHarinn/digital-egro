/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.core.element.Key;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.Tunnel;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.library.ElementNotFoundException;
import de.neemann.digital.gui.components.modification.ModifyAttribute;
import de.neemann.digital.lang.Lang;
import de.neemann.gui.Screen;
import de.neemann.gui.ToolTipAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Renames the component under the mouse with F2 in a small popup, without opening
 * the complete attribute dialog. For a tunnel the net name is edited.
 */
final class LabelEditor {
    private final CircuitComponent circuitComponent;
    private final ElementLibrary library;

    private LabelEditor(CircuitComponent circuitComponent, ElementLibrary library) {
        this.circuitComponent = circuitComponent;
        this.library = library;
    }

    /**
     * Enables the F2 shortcut in the given circuit component
     *
     * @param circuitComponent the circuit component
     * @param library          the library used to check if an element has a label
     */
    static void install(CircuitComponent circuitComponent, ElementLibrary library) {
        LabelEditor editor = new LabelEditor(circuitComponent, library);
        new ToolTipAction("renameLabel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                editor.edit();
            }
        }.setAccelerator("F2").enableAcceleratorIn(circuitComponent);
    }

    private void edit() {
        if (circuitComponent.isLocked())
            return;
        // null if the mouse is not above an element or if the simulation is running
        VisualElement ve = circuitComponent.getActualVisualElement();
        if (ve == null)
            return;

        boolean tunnel = ve.equalsDescription(Tunnel.DESCRIPTION);
        Key<String> key = tunnel ? Keys.NETNAME : Keys.LABEL;
        try {
            if (!library.getElementType(ve.getElementName()).hasAttribute(key))
                return;
        } catch (ElementNotFoundException e) {
            return;
        }

        String old = ve.getElementAttributes().get(key);
        showPopup(tunnel ? Lang.get("key_NetName") : Lang.get("key_Label"), old, newValue -> {
            if (!newValue.equals(old))
                circuitComponent.modify(new ModifyAttribute<>(ve, key, newValue));
        });
    }

    private interface Apply {
        void apply(String value);
    }

    private void showPopup(String caption, String value, Apply apply) {
        Window owner = SwingUtilities.getWindowAncestor(circuitComponent);
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);

        int fs = Screen.getInstance().getFontSize();
        JTextField field = new JTextField(value, 14);
        JPanel panel = new JPanel(new BorderLayout(fs / 2, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(fs / 3, fs / 2, fs / 3, fs / 2)));
        panel.add(new JLabel(caption), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        dialog.setContentPane(panel);

        boolean[] closed = {false};
        Runnable close = () -> {
            if (!closed[0]) {
                closed[0] = true;
                dialog.dispose();
                circuitComponent.requestFocusInWindow();
            }
        };
        field.addActionListener(e -> {
            String text = field.getText().trim();
            close.run();
            apply.apply(text);
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        field.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close.run();
            }
        });
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                close.run();
            }
        });

        dialog.pack();
        PointerInfo pi = MouseInfo.getPointerInfo();
        Point p = pi == null ? circuitComponent.getLocationOnScreen() : pi.getLocation();
        dialog.setLocation(p.x - dialog.getWidth() / 3, p.y - dialog.getHeight() / 2);
        dialog.setVisible(true);
        field.selectAll();
        field.requestFocusInWindow();
    }
}
