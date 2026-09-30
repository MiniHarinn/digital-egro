/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui;

import de.neemann.digital.lang.Lang;
import de.neemann.gui.Screen;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Shows the keyboard shortcuts of the editor, many of them are not shown in any menu.
 * Shortcuts which only exist in this version and not in the original Digital are marked,
 * so that one does not rely on them when working with the original.
 */
public final class ShortcutSheet {
    private static final String MARK = "★";

    private final StringBuilder html = new StringBuilder();
    private final String ctrl = Screen.isMac() ? "⌘" : "Ctrl";
    private final String alt = Screen.isMac() ? "⌥" : "Alt";

    private ShortcutSheet() {
    }

    /**
     * Shows the shortcut sheet
     *
     * @param owner the owning window
     */
    public static void show(Window owner) {
        new ShortcutSheet().createDialog(owner).setVisible(true);
    }

    private JDialog createDialog(Window owner) {
        createContent();

        JEditorPane pane = new JEditorPane("text/html", html.toString());
        pane.setEditable(false);
        pane.setCaretPosition(0);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.setFont(Screen.getInstance().getFont());

        JDialog dialog = new JDialog(owner, Lang.get("menu_shortcuts"), Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        dialog.getContentPane().add(scroll);

        AbstractAction close = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        };
        JRootPane root = dialog.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('?'), "close");
        root.getActionMap().put("close", close);

        dialog.pack();
        Rectangle screen = owner.getGraphicsConfiguration().getBounds();
        int fs = Screen.getInstance().getFontSize();
        dialog.setSize(Math.max(dialog.getWidth() + fs * 2, fs * 30), Math.min(dialog.getHeight(), screen.height * 4 / 5));
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }

    private void createContent() {
        int fs = Screen.getInstance().getFontSize();
        html.append("<html><body style='margin:").append(fs / 2).append("px'><table cellspacing='0' cellpadding='3'>");

        section(Lang.get("menu_elements"));
        row("Tab, " + ctrl + "+K", Lang.get("shortcut_palette"), true);
        row("L", Lang.get("shortcut_insertLast"), false);
        row("T", Lang.get("shortcut_tunnel"), false);
        row("Q", Lang.get("shortcut_pipetteCopy"), false);
        row(ctrl + "+Q", Lang.get("shortcut_pipette"), false);

        section(Lang.get("menu_edit"));
        row(ctrl + "+Z", Lang.get("menu_undo"), false);
        row(ctrl + "+Y", Lang.get("menu_redo"), false);
        row(ctrl + "+Shift+Z", Lang.get("menu_redo"), true);
        row(ctrl + "+X", Lang.get("menu_cut"), false);
        row(ctrl + "+C", Lang.get("menu_copy"), false);
        row(ctrl + "+V", Lang.get("menu_paste"), false);
        row(ctrl + "+D", Lang.get("shortcut_duplicate"), false);
        row(alt + "+" + Lang.get("shortcut_drag"), Lang.get("shortcut_altDrag"), true);
        row(ctrl + "+A", Lang.get("shortcut_selectAll"), false);
        row("← ↑ → ↓", Lang.get("shortcut_nudge"), true);
        row("R", Lang.get("menu_rotate"), false);
        row("Del", Lang.get("menu_delete"), false);
        row("+ / -", Lang.get("shortcut_plusMinus"), false);
        row("P", Lang.get("shortcut_program"), false);
        row("Esc", Lang.get("shortcut_escape"), false);
        row(ctrl + "+F", Lang.get("menu_find"), false);

        section(Lang.get("lib_wires"));
        row("S", Lang.get("shortcut_splitWire"), false);
        row("F", Lang.get("shortcut_flipWire"), false);
        row("D", Lang.get("shortcut_diagWire"), false);
        row(Lang.get("shortcut_dropOnWire"), Lang.get("shortcut_dropOnWire_tt"), true);

        section(Lang.get("menu_sim"));
        row(ctrl + "+R", Lang.get("shortcut_startStop"), true);
        row("F8", Lang.get("menu_runTests"), false);
        row(ctrl + "+T", Lang.get("menu_runTests"), true);
        row("F11", Lang.get("menu_runAllTests"), false);

        section(Lang.get("menu_view"));
        row("F1", Lang.get("menu_maximize"), false);
        row(ctrl + "+Plus", Lang.get("menu_zoomIn"), false);
        row(ctrl + "+Minus", Lang.get("menu_zoomOut"), false);
        row(ctrl + "+0 … 9", Lang.get("shortcut_saveView"), false);
        row("0 … 9", Lang.get("shortcut_restoreView"), false);
        row("F4", Lang.get("menu_presentationMode"), false);
        row("F5", Lang.get("menu_treeSelect"), false);
        row("?", Lang.get("menu_shortcuts"), true);

        html.append("</table><p style='color:gray'>").append(MARK).append(" ")
                .append(Lang.get("msg_shortcutsOnlyHere")).append("</p></body></html>");
    }

    private void section(String title) {
        int top = html.indexOf("<tr>") >= 0 ? Screen.getInstance().getFontSize() : 0;
        html.append("<tr><td colspan='2' style='padding-top:").append(top).append("px'><font size='+1'>")
                .append(escape(title)).append("</font></td></tr>");
    }

    private void row(String key, String description, boolean onlyHere) {
        html.append("<tr><td nowrap valign='top' style='padding-right:").append(Screen.getInstance().getFontSize())
                .append("px'><font color='#1a5fb4'>").append(escape(key)).append("</font></td><td>")
                .append(escape(description));
        if (onlyHere)
            html.append(" <span style='color:gray'>").append(MARK).append("</span>");
        html.append("</td></tr>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
