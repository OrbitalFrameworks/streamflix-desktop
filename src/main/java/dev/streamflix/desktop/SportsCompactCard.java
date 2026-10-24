package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

final class SportsCompactCard extends JPanel {
    private final SportsEvent event;
    private boolean hovered;
    private boolean focused;

    SportsCompactCard(SportsEvent event, Consumer<SportsEvent> onPlay) {
        this.event = event;
        setLayout(new BorderLayout(10, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        setPreferredSize(new Dimension(300, 146));
        setMaximumSize(new Dimension(300, 146));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel eyebrow = Theme.eyebrow(event.sport() + " · " + event.league());
        JLabel title = Theme.heading(event.hasTeams()
                ? event.homeTeam() + " vs " + event.awayTeam() : event.name(), 15f);
        JLabel status = Theme.muted(status(event));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(eyebrow);
        text.add(Box.createVerticalStrut(6));
        text.add(title);
        text.add(Box.createVerticalStrut(5));
        text.add(status);
        add(text, BorderLayout.CENTER);

        JButton play = Theme.primaryButton("▶ Ver");
        play.setEnabled(!event.broadcasters().isEmpty());
        play.setToolTipText(play.isEnabled()
                ? "Buscar la mejor señal disponible"
                : "Todavía no hay broadcaster asociado a este evento");
        play.addActionListener(e -> onPlay.accept(event));
        add(play, BorderLayout.SOUTH);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !event.broadcasters().isEmpty()) {
                    onPlay.accept(event);
                }
            }
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
        };
        addMouseListener(ma);
        text.addMouseListener(ma);

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
            @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE)
                        && !event.broadcasters().isEmpty()) {
                    onPlay.accept(event);
                }
            }
        });
        registerKeyboardAction(e -> {
            if (!event.broadcasters().isEmpty()) onPlay.accept(event);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        registerKeyboardAction(e -> {
            if (!event.broadcasters().isEmpty()) onPlay.accept(event);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

        String accessible = (event.hasTeams() ? event.homeTeam() + " vs " + event.awayTeam() : event.name())
                + " · " + event.sport() + " · " + status(event);
        getAccessibleContext().setAccessibleName(accessible);
        setToolTipText(accessible);
    }

    SportsEvent event() {
        return event;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(hovered ? Theme.HOVER : Theme.PANEL);
            g2.fillRoundRect(0, 0, w, h, Theme.RADIUS_CONTROL, Theme.RADIUS_CONTROL);
            g2.setColor(hovered ? new Color(255, 255, 255, 45) : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS_CONTROL, Theme.RADIUS_CONTROL);

            if (focused) {
                g2.setColor(Theme.FOCUS);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, Math.max(0, w - 3), Math.max(0, h - 3),
                        Theme.RADIUS_CONTROL - 2, Theme.RADIUS_CONTROL - 2);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private static String status(SportsEvent event) {
        if (event.homeScore() != null && event.awayScore() != null) {
            String progress = event.progress() == null || event.progress().isBlank() ? "" : " · " + event.progress();
            return event.homeScore() + " — " + event.awayScore() + progress;
        }
        if (event.startsAt() != null) {
            return DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", new Locale("es", "PE"))
                    .format(event.startsAt().atZone(ZoneId.systemDefault()));
        }
        return event.status() == null || event.status().isBlank() ? "En vivo" : event.status();
    }
}
