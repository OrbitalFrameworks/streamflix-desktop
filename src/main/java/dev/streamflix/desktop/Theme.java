package dev.streamflix.desktop;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class Theme {
    static final Color BG = new Color(10, 12, 17);
    static final Color SIDEBAR = new Color(14, 17, 24);
    static final Color PANEL = new Color(19, 23, 32);
    static final Color PANEL_ALT = new Color(25, 30, 42);
    static final Color HOVER = new Color(31, 37, 51);
    static final Color BORDER = new Color(42, 49, 64);
    static final Color TEXT = new Color(246, 247, 249);
    static final Color MUTED = new Color(151, 160, 178);
    static final Color MUTED_2 = new Color(110, 119, 137);
    static final Color ACCENT = new Color(201, 42, 54);
    static final Color ACCENT_HOVER = new Color(228, 61, 73);
    static final Color DANGER = new Color(255, 118, 118);
    static final Color SUCCESS = new Color(104, 211, 145);
    static final Color FOCUS = new Color(255, 221, 104);

    static final Font FONT = new Font("Segoe UI Variable Text", Font.PLAIN, 14);
    static final Font FONT_BOLD = new Font("Segoe UI Variable Text", Font.BOLD, 14);
    static final Font FONT_DISPLAY = new Font("Segoe UI Variable Display", Font.BOLD, 14);

    private Theme() {}

    static void install() {
        FlatDarkLaf.setup();
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        UIManager.put("defaultFont", FONT);
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 12);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", FOCUS);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.menuBarEmbedded", true);
        UIManager.put("List.selectionArc", 10);
        UIManager.put("List.cellMargins", new Insets(5, 7, 5, 7));
        UIManager.put("Panel.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.font", FONT);
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("TextField.font", FONT);
        UIManager.put("List.font", FONT);
        UIManager.put("Button.background", PANEL_ALT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("TextField.background", PANEL_ALT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.selectionBackground", ACCENT);
        UIManager.put("TextField.margin", new Insets(7, 10, 7, 10));
        UIManager.put("ComboBox.background", PANEL_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", HOVER);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", HOVER);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ToolTip.background", PANEL_ALT);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", BG);
    }

    static JButton button(String text) {
        return new FlatButton(text, false, false);
    }

    static JButton primaryButton(String text) {
        return new FlatButton(text, true, false);
    }

    static JButton navButton(String text) {
        FlatButton b = new FlatButton(text, false, true);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        b.setPreferredSize(new Dimension(188, 42));
        return b;
    }

    static JButton topNavButton(String text) {
        FlatButton b = new FlatButton(text, false, false, true);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setPreferredSize(new Dimension(Math.max(70, text.length() * 8 + 22), 34));
        b.setMaximumSize(new Dimension(145, 34));
        return b;
    }

    static void setNavSelected(JButton button, boolean selected) {
        if (button instanceof FlatButton flat) {
            flat.setSelectedState(selected);
        } else {
            button.setBackground(selected ? PANEL_ALT : SIDEBAR);
        }
    }

    static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(FONT);
        return l;
    }

    static JLabel eyebrow(String text) {
        JLabel l = new JLabel(text == null ? "" : text.toUpperCase());
        l.setForeground(MUTED_2);
        l.setFont(FONT_BOLD.deriveFont(11f));
        return l;
    }

    static JLabel heading(String text, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(FONT_DISPLAY.deriveFont(size));
        return l;
    }

    static JPanel surface() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 16, 16, 16)));
        return p;
    }

    private static final class FlatButton extends JButton {
        private final boolean primary;
        private final boolean navigation;
        private final boolean topNavigation;
        private boolean hovered;
        private boolean selectedState;
        private boolean focused;

        FlatButton(String text, boolean primary, boolean navigation) {
            this(text, primary, navigation, false);
        }

        FlatButton(String text, boolean primary, boolean navigation, boolean topNavigation) {
            super(text);
            this.primary = primary;
            this.navigation = navigation;
            this.topNavigation = topNavigation;
            setFont(FONT_BOLD);
            setForeground(TEXT);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(
                    topNavigation ? 8 : navigation ? 11 : 9,
                    topNavigation ? 10 : navigation ? 13 : 15,
                    topNavigation ? 8 : navigation ? 11 : 9,
                    topNavigation ? 10 : navigation ? 13 : 15));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    hovered = true;
                    repaint();
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    focused = true;
                    repaint();
                }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        void setSelectedState(boolean selected) {
            this.selectedState = selected;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg;
                if (!isEnabled()) {
                    bg = new Color(PANEL_ALT.getRed(), PANEL_ALT.getGreen(), PANEL_ALT.getBlue(), 110);
                } else if (primary) {
                    bg = hovered ? ACCENT_HOVER : ACCENT;
                } else if (selectedState && navigation) {
                    bg = PANEL_ALT;
                } else if (selectedState && !topNavigation) {
                    bg = hovered
                            ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 84)
                            : new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 58);
                } else if (hovered) {
                    bg = topNavigation ? new Color(255, 255, 255, 18) : HOVER;
                } else {
                    bg = topNavigation ? new Color(0, 0, 0, 0) : navigation ? SIDEBAR : PANEL_ALT;
                }
                if (bg.getAlpha() > 0) {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                if (!primary && !navigation && !topNavigation) {
                    g2.setColor(selectedState ? ACCENT : BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                if (selectedState && navigation) {
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(0, 7, 3, Math.max(0, getHeight() - 14), 3, 3);
                } else if (selectedState && topNavigation) {
                    g2.setColor(ACCENT);
                    int width = Math.min(30, Math.max(14, getWidth() / 3));
                    g2.fillRoundRect((getWidth() - width) / 2, Math.max(0, getHeight() - 2), width, 2, 2, 2);
                }
                if (focused) {
                    g2.setColor(FOCUS);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, Math.max(0, getWidth() - 3),
                            Math.max(0, getHeight() - 3), 10, 10);
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
