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
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.gui.components.modification.ModifyAttribute;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.undo.Modifications;
import de.neemann.gui.Screen;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

/**
 * A window listing all net names used by the tunnels of the circuit.
 * Selecting a name highlights its tunnels and wires, editing a name renames all its tunnels at once.
 */
public final class TunnelManager extends JDialog {
    private final CircuitComponent circuitComponent;
    private final Model model = new Model();
    private final JTable table;

    /**
     * Shows the tunnel manager
     *
     * @param owner            the owning window
     * @param circuitComponent the circuit component
     */
    public static void show(Window owner, CircuitComponent circuitComponent) {
        new TunnelManager(owner, circuitComponent).setVisible(true);
    }

    private TunnelManager(Window owner, CircuitComponent circuitComponent) {
        super(owner, Lang.get("menu_tunnels"), ModalityType.MODELESS);
        this.circuitComponent = circuitComponent;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        int fs = Screen.getInstance().getFontSize();
        table.setRowHeight(fs * 3 / 2);
        table.getColumnModel().getColumn(1).setMaxWidth(fs * 8);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                highlightSelected();
        });

        // html lets the hint wrap instead of being cut off
        JLabel hint = new JLabel("<html><body style='width:" + fs * 21 + "px'>"
                + Lang.get("msg_tunnelsHint").replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(fs / 3, fs / 2, fs / 3, fs / 2));

        getContentPane().add(new JScrollPane(table));
        getContentPane().add(hint, BorderLayout.SOUTH);

        // the circuit may have been edited in the meantime
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                refresh();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                circuitComponent.removeHighLighted();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!table.isEditing())
                    dispose();
            }
        });

        refresh();
        setSize(fs * 30, fs * 22);
        // top right corner of the main window, so that the circuit stays visible
        setLocation(owner.getX() + owner.getWidth() - getWidth() - fs, owner.getY() + fs * 6);
    }

    private void refresh() {
        String selected = getSelectedName();
        model.update();
        for (int i = 0; i < model.names.size(); i++)
            if (model.names.get(i).equals(selected))
                table.getSelectionModel().setSelectionInterval(i, i);
    }

    private String getSelectedName() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.names.size() ? null : model.names.get(row);
    }

    private void highlightSelected() {
        circuitComponent.removeHighLighted();
        String name = getSelectedName();
        if (name == null)
            return;
        Circuit circuit = circuitComponent.getCircuit();
        ArrayList<VisualElement> tunnels = tunnelsNamed(circuit, name);
        circuitComponent.addHighLighted(tunnels);

        // follow the wires starting at the tunnels; the net list is not used, it fails if any tunnel is unconnected
        HashSet<Vector> points = new HashSet<>();
        for (VisualElement t : tunnels)
            points.add(t.getPos());
        HashSet<Wire> wires = new HashSet<>();
        boolean added = true;
        while (added) {
            added = false;
            for (Wire w : circuit.getWires())
                if (!wires.contains(w) && (points.contains(w.p1) || points.contains(w.p2))) {
                    wires.add(w);
                    points.add(w.p1);
                    points.add(w.p2);
                    added = true;
                }
        }
        circuitComponent.addHighLighted(wires);
    }

    private static ArrayList<VisualElement> tunnelsNamed(Circuit circuit, String name) {
        ArrayList<VisualElement> list = new ArrayList<>();
        for (VisualElement ve : circuit.getElements())
            if (ve.equalsDescription(Tunnel.DESCRIPTION) && ve.getElementAttributes().get(Keys.NETNAME).trim().equals(name))
                list.add(ve);
        return list;
    }

    private void rename(String oldName, String newName) {
        newName = newName.trim();
        if (newName.isEmpty() || newName.equals(oldName) || circuitComponent.isLocked())
            return;
        if (model.names.contains(newName)) {
            int answer = JOptionPane.showConfirmDialog(this,
                    Lang.get("msg_tunnelsMerge_N", newName), Lang.get("menu_tunnels"), JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION)
                return;
        }
        Modifications.Builder<Circuit> builder =
                new Modifications.Builder<>(Lang.get("mod_tunnelsRenamed_N_N", oldName, newName));
        for (VisualElement t : tunnelsNamed(circuitComponent.getCircuit(), oldName))
            builder.add(new ModifyAttribute<>(t, Keys.NETNAME, newName));
        circuitComponent.modify(builder.build());

        model.update();
        int row = model.names.indexOf(newName);
        if (row >= 0)
            table.getSelectionModel().setSelectionInterval(row, row);
    }

    private final class Model extends AbstractTableModel {
        private final ArrayList<String> names = new ArrayList<>();
        private final ArrayList<Integer> counts = new ArrayList<>();

        private void update() {
            TreeMap<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (VisualElement ve : circuitComponent.getCircuit().getElements())
                if (ve.equalsDescription(Tunnel.DESCRIPTION)) {
                    String name = ve.getElementAttributes().get(Keys.NETNAME).trim();
                    if (!name.isEmpty())
                        map.merge(name, 1, Integer::sum);
                }
            names.clear();
            counts.clear();
            names.addAll(map.keySet());
            counts.addAll(map.values());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return names.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? Lang.get("key_NetName") : Lang.get("msg_tunnelsCount");
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0)
                return names.get(row);
            int count = counts.get(row);
            // a single tunnel connects nothing, often a typo
            return count == 1 ? "1 ⚠" : Integer.toString(count);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !circuitComponent.isLocked();
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && row < names.size()) {
                String oldName = names.get(row);
                SwingUtilities.invokeLater(() -> rename(oldName, value.toString()));
            }
        }
    }
}
