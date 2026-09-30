/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui;

import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.library.LibraryNode;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.components.CircuitComponent;
import de.neemann.digital.lang.Lang;
import de.neemann.gui.Screen;
import de.neemann.gui.ToolTipAction;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

/**
 * A floating quick search popup (Ctrl+K) which allows to pick a component by typing
 * a part of its name, instead of navigating the components menu or the tree.
 * The popup opens at the mouse position if the mouse is above the circuit, otherwise
 * it is centered at the top of the window. The selected component is then attached
 * to the mouse, exactly as if it was selected in the components menu.
 */
public final class ComponentPalette {
    private static final int MAX_RECENT = 8;
    private static final LinkedList<String> RECENT = new LinkedList<>();

    private final Window owner;
    private final ElementLibrary library;
    private final ShapeFactory shapeFactory;
    private final InsertHistory insertHistory;
    private final CircuitComponent circuitComponent;

    /**
     * Creates a new instance
     *
     * @param owner            the owning window
     * @param library          the library to search in
     * @param shapeFactory     the shape factory used to create the icons
     * @param insertHistory    the insert history the selected component is added to
     * @param circuitComponent the circuit component the selected component is inserted to
     */
    public ComponentPalette(Window owner, ElementLibrary library, ShapeFactory shapeFactory, InsertHistory insertHistory, CircuitComponent circuitComponent) {
        this.owner = owner;
        this.library = library;
        this.shapeFactory = shapeFactory;
        this.insertHistory = insertHistory;
        this.circuitComponent = circuitComponent;
    }

    /**
     * Shows the popup
     */
    public void show() {
        new PaletteDialog().open();
    }

    private static void addToRecent(String name) {
        RECENT.remove(name);
        RECENT.addFirst(name);
        while (RECENT.size() > MAX_RECENT)
            RECENT.removeLast();
    }

    private ArrayList<Entry> collectEntries() {
        ArrayList<Entry> entries = new ArrayList<>();
        library.getRoot().traverse(node -> {
            if (node.isLeaf() && !node.isHidden() && node.isUnique())
                entries.add(new Entry(node));
        });
        return entries;
    }

    private final class PaletteDialog extends JDialog {
        private final ArrayList<Entry> entries;
        private final JTextField search;
        private final DefaultListModel<Entry> listModel;
        private final JList<Entry> list;
        private final JLabel emptyLabel;
        private final JScrollPane scrollPane;
        private boolean closed;

        private PaletteDialog() {
            super(owner, ModalityType.MODELESS);
            setUndecorated(true);
            entries = collectEntries();

            int fs = Screen.getInstance().getFontSize();

            search = new HintTextField(Lang.get("msg_componentPaletteSearch"));
            search.setFont(Screen.getInstance().getFont(1.3f));
            search.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                    BorderFactory.createEmptyBorder(fs / 2, fs * 2 / 3, fs / 2, fs * 2 / 3)));

            listModel = new DefaultListModel<>();
            list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new EntryRenderer());
            list.setFocusable(false);
            list.setVisibleRowCount(10);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
                        list.setSelectedIndex(i);
                        insertSelected();
                    }
                }
            });

            emptyLabel = new JLabel(Lang.get("msg_componentPaletteNoMatch"), SwingConstants.CENTER);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(fs, fs, fs, fs));

            scrollPane = new JScrollPane(list);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));
            panel.add(search, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);
            setContentPane(panel);

            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateList();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateList();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateList();
                }
            });

            bind(KeyEvent.VK_ESCAPE, 0, this::close);
            bind(KeyEvent.VK_ENTER, 0, this::insertSelected);
            bind(KeyEvent.VK_DOWN, 0, () -> moveSelection(1));
            bind(KeyEvent.VK_UP, 0, () -> moveSelection(-1));
            bind(KeyEvent.VK_TAB, 0, () -> moveSelection(1));
            bind(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, () -> moveSelection(-1));
            bind(KeyEvent.VK_PAGE_DOWN, 0, () -> moveSelection(list.getVisibleRowCount()));
            bind(KeyEvent.VK_PAGE_UP, 0, () -> moveSelection(-list.getVisibleRowCount()));
            // pressing the shortcut again closes the popup, like in most command palettes
            bind(KeyEvent.VK_K, ToolTipAction.getCTRLMask(), this::close);
            search.setFocusTraversalKeysEnabled(false);

            addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowLostFocus(WindowEvent e) {
                    close();
                }
            });

            updateList();
        }

        private void bind(int keyCode, int modifiers, Runnable r) {
            String name = "palette_" + keyCode + "_" + modifiers;
            search.getInputMap().put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
            search.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    r.run();
                }
            });
        }

        private void open() {
            pack();
            int fs = Screen.getInstance().getFontSize();
            int width = Math.max(getWidth(), fs * 32);
            int height = getHeight();
            setSize(width, height);
            setLocation(computeLocation(width, height));
            setVisible(true);
            search.requestFocusInWindow();
        }

        private Point computeLocation(int width, int height) {
            Rectangle bounds = owner.getGraphicsConfiguration().getBounds();
            Point mouse = null;
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null && circuitComponent.isShowing()) {
                Point p = pi.getLocation();
                Point cc = circuitComponent.getLocationOnScreen();
                Rectangle ccRect = new Rectangle(cc, circuitComponent.getSize());
                if (ccRect.contains(p))
                    mouse = p;
            }

            int x;
            int y;
            if (mouse != null) {
                // open slightly up and left of the mouse, so that the search field is directly under it
                x = mouse.x - width / 2;
                y = mouse.y - search.getPreferredSize().height / 2;
            } else {
                Rectangle o = owner.getBounds();
                x = o.x + (o.width - width) / 2;
                y = o.y + o.height / 5;
            }
            x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - width));
            y = Math.max(bounds.y, Math.min(y, bounds.y + bounds.height - height));
            return new Point(x, y);
        }

        private void updateList() {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            ArrayList<Entry> result = new ArrayList<>();
            if (query.isEmpty()) {
                HashMap<String, Entry> byName = new HashMap<>();
                for (Entry e : entries)
                    byName.put(e.node.getName(), e);
                for (String r : RECENT) {
                    Entry e = byName.get(r);
                    if (e != null)
                        result.add(e);
                }
                for (Entry e : entries)
                    if (!RECENT.contains(e.node.getName()))
                        result.add(e);
            } else {
                String[] tokens = query.split("\\s+");
                for (Entry e : entries) {
                    e.score = e.score(tokens);
                    if (e.score > 0)
                        result.add(e);
                }
                Collections.sort(result, Comparator.comparingInt((Entry e) -> -e.score)
                        .thenComparing(e -> e.name));
            }

            listModel.clear();
            for (Entry e : result)
                listModel.addElement(e);

            if (result.isEmpty()) {
                scrollPane.setViewportView(emptyLabel);
            } else {
                scrollPane.setViewportView(list);
                list.setSelectedIndex(0);
                list.ensureIndexIsVisible(0);
            }
        }

        private void moveSelection(int delta) {
            int size = listModel.getSize();
            if (size == 0)
                return;
            int i = list.getSelectedIndex() + delta;
            if (Math.abs(delta) == 1)
                i = (i + size) % size;
            else
                i = Math.max(0, Math.min(size - 1, i));
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        }

        private void insertSelected() {
            Entry e = list.getSelectedValue();
            if (e == null || !scrollPane.getViewport().getView().equals(list))
                return;
            close();
            addToRecent(e.node.getName());
            circuitComponent.requestFocusInWindow();
            new InsertAction(e.node, insertHistory, circuitComponent, shapeFactory).actionPerformed(null);
        }

        private void close() {
            if (closed)
                return;
            closed = true;
            dispose();
            circuitComponent.requestFocusInWindow();
        }
    }

    /**
     * Text field which shows a hint while it is empty, also if it has the focus.
     */
    private static final class HintTextField extends JTextField {
        private final String hint;

        private HintTextField(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Insets in = getInsets();
                g.setColor(Color.GRAY);
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                int y = in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(hint, in.left, y);
            }
        }
    }

    private final class Entry {
        private final LibraryNode node;
        private final String name;
        private final String nameLower;
        private final String internalLower;
        private final String category;
        private final String categoryLower;
        private int score;

        private Entry(LibraryNode node) {
            this.node = node;
            name = node.getTranslatedName();
            nameLower = name.toLowerCase(Locale.ROOT);
            internalLower = node.getName().toLowerCase(Locale.ROOT);

            StringBuilder sb = new StringBuilder();
            Object[] path = node.getPath();
            // skip the root node and the node itself
            for (int i = 1; i < path.length - 1; i++) {
                if (sb.length() > 0)
                    sb.append(" › ");
                sb.append(((LibraryNode) path[i]).getName());
            }
            category = sb.toString();
            categoryLower = category.toLowerCase(Locale.ROOT);
        }

        /**
         * Every token has to match somewhere, otherwise the entry is rejected.
         *
         * @return the score, zero if the entry does not match
         */
        private int score(String[] tokens) {
            int total = 0;
            for (String t : tokens) {
                int s = Math.max(scoreText(nameLower, t), scoreText(internalLower, t));
                if (s == 0 && categoryLower.contains(t))
                    s = 10;
                if (s == 0)
                    return 0;
                total += s;
            }
            if (RECENT.contains(node.getName()))
                total += 5;
            return total;
        }

        private int scoreText(String text, String t) {
            if (text.equals(t))
                return 1000;
            if (text.startsWith(t))
                return 500 - Math.min(text.length(), 100);
            int i = text.indexOf(t);
            if (i > 0) {
                char before = text.charAt(i - 1);
                if (!Character.isLetterOrDigit(before))
                    return 300 - i;
                return 200 - i;
            }
            if (isSubsequence(text, t))
                return 50;
            return 0;
        }

        private boolean isSubsequence(String text, String t) {
            int j = 0;
            for (int i = 0; i < text.length() && j < t.length(); i++)
                if (text.charAt(i) == t.charAt(j))
                    j++;
            return j == t.length();
        }

        private Icon getIcon(int maxSize) {
            Icon icon = node.getIconOrNull(shapeFactory);
            if (icon == null)
                return null;
            int w = icon.getIconWidth();
            int h = icon.getIconHeight();
            if (w <= maxSize && h <= maxSize)
                return icon;
            return new FitIcon(icon, maxSize / (double) Math.max(w, h));
        }
    }

    private final class EntryRenderer extends JPanel implements ListCellRenderer<Entry> {
        private final JLabel nameLabel;
        private final JLabel categoryLabel;
        private final int iconSize;

        private EntryRenderer() {
            super(new BorderLayout());
            int fs = Screen.getInstance().getFontSize();
            iconSize = fs * 2;
            setBorder(BorderFactory.createEmptyBorder(fs / 4, fs / 2, fs / 4, fs / 2));
            nameLabel = new JLabel();
            nameLabel.setIconTextGap(fs / 2);
            categoryLabel = new JLabel();
            categoryLabel.setFont(Screen.getInstance().getFont(0.85f));
            add(nameLabel, BorderLayout.CENTER);
            add(categoryLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry entry, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(entry.name);
            nameLabel.setIcon(new SlotIcon(entry.getIcon(iconSize), iconSize));
            categoryLabel.setText(entry.category);

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(bg);
            nameLabel.setForeground(fg);
            categoryLabel.setForeground(isSelected ? fg : Color.GRAY);
            return this;
        }
    }

    private static final class FitIcon implements Icon {
        private final Icon icon;
        private final double scale;

        private FitIcon(Icon icon, double scale) {
            this.icon = icon;
            this.scale = scale;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform tr = g2.getTransform();
            Object hint = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.translate(x, y);
            g2.scale(scale, scale);
            icon.paintIcon(c, g2, 0, 0);
            g2.setTransform(tr);
            if (hint != null)
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        }

        @Override
        public int getIconWidth() {
            return (int) Math.ceil(icon.getIconWidth() * scale);
        }

        @Override
        public int getIconHeight() {
            return (int) Math.ceil(icon.getIconHeight() * scale);
        }
    }

    /**
     * Centers an icon in a square of fixed size, so that all rows have the same height
     * and all names are aligned.
     */
    private static final class SlotIcon implements Icon {
        private final Icon icon;
        private final int size;

        private SlotIcon(Icon icon, int size) {
            this.icon = icon;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (icon != null)
                icon.paintIcon(c, g, x + (size - icon.getIconWidth()) / 2, y + (size - icon.getIconHeight()) / 2);
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
