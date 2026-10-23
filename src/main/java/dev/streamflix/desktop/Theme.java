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

    // Shared geometry keeps independently assembled Swing screens visually related.
    static final int SPACE_1 = 4;
    static final int SPACE_2 = 8;
    static final int SPACE_3 = 12;
    static final int SPACE_4 = 16;
    static final int RADIUS_COMPACT = 6;
    static final int RADIUS_CONTROL = 12;

    static final Font FONT = new Font("Segoe UI Variable Text", Font.PLAIN, 14);
    static final Font FONT_BOLD = new Font("Segoe UI Variable Text", Font.BOLD, 14);
    static final Font FONT_DISPLAY = new Font("Segoe UI Variable Display", Font.BOLD, 14);

    private Theme() {}

    static void install() {
        FlatDarkLaf.setup();
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        UIManager.put("defaultFont", FONT);
        UIManager.put("Component.arc", RADIUS_CONTROL);
        UIManager.put("Button.arc", RADIUS_CONTROL);
        UIManager.put("TextComponent.arc", RADIUS_CONTROL);
        UIManager.put("CheckBox.arc", RADIUS_COMPACT);
        UIManager.put("ProgressBar.arc", RADIUS_CONTROL);
        UIManager.put("Component.focusWidth", 2);
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
        UIManager.put("PasswordField.background", PANEL_ALT);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("PasswordField.caretForeground", TEXT);
        UIManager.put("PasswordField.selectionBackground", ACCENT);
        UIManager.put("PasswordField.margin", new Insets(7, 10, 7, 10));
        UIManager.put("ComboBox.background", PANEL_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", HOVER);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", HOVER);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("TabbedPane.background", BG);
        UIManager.put("TabbedPane.foreground", MUTED);
        UIManager.put("TabbedPane.selectedBackground", PANEL_ALT);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.inactiveUnderlineColor", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 12, 8, 12));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(0, 0, 2, 0));
        UIManager.put("TabbedPane.tabSelectionHeight", 3);
        UIManager.put("TabbedPane.focusColor", FOCUS);
        UIManager.put("CheckBox.icon.focusColor", FOCUS);
        UIManager.put("CheckBox.foreground", TEXT);
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

    static JButton button(String text, StreamflixIcons.Glyph glyph) {
        JButton button = new FlatButton(text, false, false);
        setButtonIcon(button, glyph);
        return button;
    }

    static JButton primaryButton(String text) {
        return new FlatButton(text, true, false);
    }

    static JButton primaryButton(String text, StreamflixIcons.Glyph glyph) {
        JButton button = new FlatButton(text, true, false);
        setButtonIcon(button, glyph);
        return button;
    }

    static JButton iconButton(StreamflixIcons.Glyph glyph, String accessibleName) {
        FlatButton button = new FlatButton("", false, false);
        button.setBorder(new EmptyBorder(8, 8, 8, 8));
        button.setIcon(StreamflixIcons.icon(glyph, 18));
        button.setToolTipText(accessibleName);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        return button;
    }

    static void setButtonIcon(JButton button, StreamflixIcons.Glyph glyph) {
        button.setIcon(StreamflixIcons.icon(glyph, 18));
        button.setIconTextGap(8);
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
        return cardPanel(PANEL, BORDER, RADIUS_CONTROL, SPACE_4);
    }

    static JPanel cardPanel() {
        return cardPanel(PANEL, BORDER, RADIUS_CONTROL, SPACE_4 + SPACE_2);
    }

    static JPanel cardPanel(Color bg, Color border, int radius, int padding) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                    if (border != null) {
                        g2.setColor(border);
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                    }
                } finally {
                    g2.dispose();
                }
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return p;
    }

    static JPanel stateCard(String title, String message, String actionText, Runnable action) {
        JPanel card = cardPanel(PANEL, BORDER, RADIUS_CONTROL, SPACE_4 + SPACE_2);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 180));
        card.setMaximumSize(new Dimension(560, 180));

        JLabel heading = heading(title, 20f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = muted("<html><body style='width:455px'>" + (message != null ? message : "") + "</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(heading);
        card.add(Box.createVerticalStrut(SPACE_2 + 1));
        card.add(body);

        if (actionText != null && action != null) {
            card.add(Box.createVerticalStrut(SPACE_4 + 2));
            JButton actionButton = primaryButton(actionText);
            actionButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            actionButton.addActionListener(e -> action.run());
            card.add(actionButton);
        }
        return card;
    }

    private static final class FlatButton extends JButton {
        private final boolean primary;
        private final boolean navigation;
        private final boolean topNavigation;
        private boolean hovered;
        private boolean pressed;
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
                    pressed = false;
                    repaint();
                }
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
                        pressed = true;
                        repaint();
                    }
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (pressed) {
                        pressed = false;
                        repaint();
                    }
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
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_CONTROL, RADIUS_CONTROL);
                }
                if (pressed) {
                    g2.setColor(new Color(0, 0, 0, primary ? 32 : 22));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_CONTROL, RADIUS_CONTROL);
                }
                if (!primary && !navigation && !topNavigation) {
                    g2.setColor(selectedState ? ACCENT : BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_CONTROL, RADIUS_CONTROL);
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
                            Math.max(0, getHeight() - 3), RADIUS_CONTROL - 2, RADIUS_CONTROL - 2);
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
