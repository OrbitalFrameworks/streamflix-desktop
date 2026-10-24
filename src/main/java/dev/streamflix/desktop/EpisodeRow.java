package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Streaming-style episode row with still image, synopsis, progress and explicit actions. */
final class EpisodeRow extends JPanel {
    private final Models.Episode episode;
    private final double progress;
    private boolean hovered;
    private boolean focused;

    EpisodeRow(Models.Episode episode,
               Consumer<Models.Episode> onPlay,
               BiConsumer<Models.Episode, Component> onServer) {
        this(episode, onPlay, onServer, 0.0);
    }

    EpisodeRow(Models.Episode episode,
               Consumer<Models.Episode> onPlay,
               BiConsumer<Models.Episode, Component> onServer,
               double progress) {
        this.episode = episode;
        this.progress = Math.max(0.0, Math.min(1.0, progress));

        setLayout(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 12, 10, 14));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        setPreferredSize(new Dimension(760, 126));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);

        ArtworkPanel still = new ArtworkPanel(episode.poster());
        still.setPreferredSize(new Dimension(176, 99));
        still.setFallbackText("");
        add(still, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        String rawTitle = episode.title() != null ? episode.title().strip() : "";
        String displayTitle;
        String epPrefix = "E" + episode.episodeNumber();
        if (rawTitle.isEmpty()
                || rawTitle.equalsIgnoreCase("Episodio " + episode.episodeNumber())
                || rawTitle.equalsIgnoreCase("Episode " + episode.episodeNumber())
                || rawTitle.equalsIgnoreCase(epPrefix)) {
            displayTitle = "Episodio " + episode.episodeNumber();
        } else {
            String cleaned = rawTitle.replaceFirst("^(?i)(E" + episode.episodeNumber() + "|Episodio\\s*" + episode.episodeNumber() + "|Episode\\s*" + episode.episodeNumber() + ")\\s*[:·\\-]\\s*", "");
            displayTitle = epPrefix + " · " + cleaned;
        }

        JLabel title = Theme.heading(displayTitle, 14f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String overviewText = episode.overview() == null || episode.overview().isBlank()
                ? "Sin descripción disponible."
                : episode.overview();
        JLabel overview = Theme.muted("<html><body style='width:430px'>"
                + escape(shorten(overviewText, 180)) + "</body></html>");
        overview.setFont(Theme.FONT.deriveFont(12f));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);

        copy.add(title);
        copy.add(Box.createVerticalStrut(7));
        copy.add(overview);
        add(copy, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        JButton play = Theme.primaryButton(this.progress > 0.0001 && this.progress < 0.995
                ? "Continuar"
                : "Reproducir");
        play.setAlignmentX(Component.CENTER_ALIGNMENT);
        play.addActionListener(e -> onPlay.accept(episode));

        JButton server = Theme.button("Opciones");
        server.setAlignmentX(Component.CENTER_ALIGNMENT);
        server.addActionListener(e -> onServer.accept(episode, server));

        actions.add(Box.createVerticalGlue());
        actions.add(play);
        actions.add(Box.createVerticalStrut(7));
        actions.add(server);
        actions.add(Box.createVerticalGlue());

        add(actions, BorderLayout.EAST);

        play.setToolTipText("Reproducir " + displayTitle);
        play.getAccessibleContext().setAccessibleName("Reproducir " + displayTitle);
        server.setToolTipText("Opciones de reproducción para " + displayTitle);
        server.getAccessibleContext().setAccessibleName("Opciones de reproducción para " + displayTitle);

        getAccessibleContext().setAccessibleName(displayTitle + " · " + overviewText);
        setToolTipText(displayTitle);

        MouseAdapter interaction = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 || SwingUtilities.isLeftMouseButton(e)) onPlay.accept(episode);
            }

            @Override public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        };
        addMouseListener(interaction);
        still.addMouseListener(interaction);
        copy.addMouseListener(interaction);

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                focused = true;
                repaint();
            }

            @Override public void focusLost(FocusEvent e) {
                focused = false;
                repaint();
            }
        });

        // A focused Swing component must dispatch each key once. A KeyListener
        // alongside registerKeyboardAction would invoke playback twice.
        registerKeyboardAction(e -> onPlay.accept(episode),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        registerKeyboardAction(e -> onPlay.accept(episode),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
    }

    Models.Episode episode() {
        return episode;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(hovered ? new Color(24, 29, 40) : new Color(16, 19, 26));
            g2.fillRoundRect(0, 0, w, h, Theme.RADIUS_CONTROL, Theme.RADIUS_CONTROL);
            g2.setColor(hovered ? new Color(255, 255, 255, 36) : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS_CONTROL, Theme.RADIUS_CONTROL);

            if (focused) {
                g2.setColor(Theme.FOCUS);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, Math.max(0, w - 3), Math.max(0, h - 3),
                        Theme.RADIUS_CONTROL - 2, Theme.RADIUS_CONTROL - 2);
            }

            if (progress > 0.0001 && progress < 0.995) {
                int barHeight = 4;
                int y = h - barHeight - 2;
                g2.setColor(new Color(255, 255, 255, 55));
                g2.fillRoundRect(12, y, w - 24, barHeight, 2, 2);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(12, y, Math.max(2, (int) Math.round((w - 24) * progress)), barHeight, 2, 2);
            }
        } finally {
            g2.dispose();
        }
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
