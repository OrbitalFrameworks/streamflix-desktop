package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Image-first 16:9 catalog card with streaming-style overlay metadata. */
final class LandscapeCard extends JLayeredPane {
    private final Models.ShowItem item;
    private final ArtworkPanel artwork;
    private final JPanel overlay;
    private final double progress;
    private Timer hoverTimer;
    private boolean hovered;
    private boolean focused;
    private float hoverAmount;
    private float hoverTarget;

    LandscapeCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        this(item, onOpen, 0.0);
    }

    LandscapeCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen, double progress) {
        this.item = item;
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        setOpaque(false);
        setPreferredSize(new Dimension(300, 172));
        setMinimumSize(new Dimension(220, 126));
        setMaximumSize(new Dimension(380, 214));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);

        hoverTimer = new Timer(16, e -> {
            float delta = hoverTarget - hoverAmount;
            if (Math.abs(delta) < 0.02f) {
                hoverAmount = hoverTarget;
                hoverTimer.stop();
            } else {
                hoverAmount += Math.signum(delta) * Math.min(Math.abs(delta), 0.16f);
            }
            repaintAll();
        });
        hoverTimer.setCoalesce(true);

        String image = item.banner() != null && !item.banner().isBlank()
                ? cardArtworkUrl(item.banner()) : cardArtworkUrl(item.poster());
        artwork = new ArtworkPanel(image);
        artwork.setFallbackText(item.title());
        add(artwork, Integer.valueOf(0));

        overlay = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int overlayAlpha = 188 + Math.round(47f * hoverAmount);
                    GradientPaint gradient = new GradientPaint(
                            0, Math.max(0, h / 3), new Color(0, 0, 0, 0),
                            0, h, new Color(0, 0, 0, overlayAlpha));
                    g2.setPaint(gradient);
                    g2.fillRoundRect(0, 0, w, h, 12, 12);

                    if (hoverAmount > 0.02f) {
                        if (focused) {
                            g2.setColor(Theme.ACCENT);
                        } else {
                            g2.setColor(new Color(255, 255, 255,
                                    Math.max(1, Math.round(55f * hoverAmount))));
                        }
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                    }

                    if (progress > 0.01 && progress < 0.995) {
                        int barHeight = 4;
                        int y = h - barHeight;
                        g2.setColor(new Color(255, 255, 255, 70));
                        g2.fillRect(0, y, w, barHeight);
                        g2.setColor(Theme.ACCENT);
                        g2.fillRect(0, y, Math.max(2, (int) Math.round(w * progress)), barHeight);
                    }
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        overlay.setOpaque(false);
        overlay.setLayout(new BorderLayout());
        add(overlay, Integer.valueOf(1));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));

        JLabel title = new JLabel(item.title());
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.FONT_BOLD.deriveFont(13.5f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setToolTipText(item.title());
        copy.add(title);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (item.released() != null && !item.released().isBlank()) {
            String year = item.released().length() >= 4 ? item.released().substring(0, 4) : item.released();
            JLabel yearLabel = new JLabel(year);
            yearLabel.setForeground(new Color(205, 209, 216));
            yearLabel.setFont(Theme.FONT.deriveFont(11f));
            meta.add(yearLabel);
        }

        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f", item.rating()));
            rating.setForeground(new Color(232, 211, 96));
            rating.setFont(Theme.FONT_BOLD.deriveFont(11f));
            meta.add(rating);
        }

        JLabel kind = new JLabel(item.type() == Models.ShowType.MOVIE ? "Película" : "Serie");
        kind.setForeground(new Color(178, 185, 198));
        kind.setFont(Theme.FONT.deriveFont(11f));
        meta.add(kind);

        copy.add(Box.createVerticalStrut(4));
        copy.add(meta);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(copy, BorderLayout.SOUTH);
        overlay.add(south, BorderLayout.SOUTH);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) onOpen.accept(item);
            }

            @Override public void mouseEntered(MouseEvent e) {
                hovered = true;
                animateHover(1f);
            }

            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                if (!focused) animateHover(0f);
            }
        };

        addMouseListener(mouse);
        artwork.addMouseListener(mouse);
        overlay.addMouseListener(mouse);
        copy.addMouseListener(mouse);
        title.addMouseListener(mouse);

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                focused = true;
                animateHover(1f);
            }

            @Override public void focusLost(FocusEvent e) {
                focused = false;
                if (!hovered) animateHover(0f);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    onOpen.accept(item);
                }
            }
        });
    }

    @Override public void doLayout() {
        artwork.setBounds(0, 0, getWidth(), getHeight());
        overlay.setBounds(0, 0, getWidth(), getHeight());
    }

    private static String cardArtworkUrl(String url) {
        if (url == null || url.isBlank()) return url;
        return url.replace("/t/p/w1280/", "/t/p/w780/");
    }

    private void animateHover(float target) {
        hoverTarget = target;
        if (!hoverTimer.isRunning()) hoverTimer.start();
    }

    Models.ShowItem item() {
        return item;
    }

    private void repaintAll() {
        overlay.repaint();
    }
}
