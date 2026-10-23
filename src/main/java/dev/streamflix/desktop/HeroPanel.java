package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Large cinematic home hero using responsive TMDb backdrop artwork. */
final class HeroPanel extends JLayeredPane {
    private final Models.ShowItem item;
    private final ArtworkPanel backdrop;
    private final JPanel shade;
    private final JPanel copy;
    private final JButton playButton;

    HeroPanel(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        this(item, onOpen, onOpen);
    }

    HeroPanel(Models.ShowItem item,
              Consumer<Models.ShowItem> onPlay,
              Consumer<Models.ShowItem> onOpen) {
        this.item = item;
        setPreferredSize(new Dimension(1080, 500));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        setOpaque(true);
        setBackground(Color.BLACK);

        String image = item.banner() != null && !item.banner().isBlank() ? item.banner() : item.poster();
        backdrop = new ArtworkPanel(image);
        backdrop.setFallbackText("");
        add(backdrop, Integer.valueOf(0));

        shade = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();

                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(0, 0, 0, 225),
                            Math.max(1, (int) (w * 0.68)), 0, new Color(0, 0, 0, 12)));
                    g2.fillRect(0, 0, w, h);

                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.45)), new Color(0, 0, 0, 0),
                            0, h, Theme.BG));
                    g2.fillRect(0, 0, w, h);
                } finally {
                    g2.dispose();
                }
            }
        };
        shade.setOpaque(false);
        add(shade, Integer.valueOf(1));

        copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel eyebrow = Theme.eyebrow(
                item.type() == Models.ShowType.MOVIE ? "PELÍCULA DESTACADA" : "SERIE DESTACADA");
        eyebrow.setForeground(Theme.TEXT.darker());
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = Theme.heading(
                "<html><body style='width:580px'>" + escape(item.title()) + "</body></html>",
                38f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel metadata = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_2 + 1, 0));
        metadata.setOpaque(false);
        metadata.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (item.released() != null && !item.released().isBlank()) {
            JLabel released = Theme.muted(item.released().length() >= 4 ? item.released().substring(0, 4) : item.released());
            released.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            metadata.add(released);
        }
        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f / 10", item.rating()));
            rating.setForeground(Theme.FOCUS);
            rating.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            metadata.add(rating);
        }
        JLabel type = Theme.muted(item.type() == Models.ShowType.MOVIE ? "Película" : "Serie");
        type.setFont(Theme.FONT.deriveFont(12.5f));
        metadata.add(type);

        String synopsis = item.overview() == null || item.overview().isBlank()
                ? "Descubre este título en Streamflix."
                : item.overview();

        JLabel description = Theme.muted(
                "<html><body style='width:565px'>" + escape(shorten(synopsis, 280)) + "</body></html>");
        description.setForeground(Theme.TEXT);
        description.setFont(Theme.FONT.deriveFont(14f));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_2 + 1, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton play = Theme.primaryButton("Reproducir", StreamflixIcons.Glyph.PLAY);
        play.setToolTipText("Iniciar reproducción de " + item.title());
        play.getAccessibleContext().setAccessibleName("Reproducir " + item.title());
        play.addActionListener(e -> onPlay.accept(item));
        this.playButton = play;
        actions.add(play);

        JButton open = Theme.button("Más información", StreamflixIcons.Glyph.INFO);
        open.setToolTipText("Ver detalles y episodios de " + item.title());
        open.getAccessibleContext().setAccessibleName("Más información sobre " + item.title());
        open.addActionListener(e -> onOpen.accept(item));
        actions.add(open);

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(Theme.SPACE_2));
        copy.add(title);
        copy.add(Box.createVerticalStrut(Theme.SPACE_2));
        copy.add(metadata);
        copy.add(Box.createVerticalStrut(Theme.SPACE_4));
        copy.add(description);
        copy.add(Box.createVerticalStrut(Theme.SPACE_4 + Theme.SPACE_1));
        copy.add(actions);

        add(copy, Integer.valueOf(2));
    }

    Models.ShowItem item() {
        return item;
    }

    @Override
    public boolean requestFocusInWindow() {
        if (playButton != null && playButton.isDisplayable()) {
            return playButton.requestFocusInWindow();
        }
        return super.requestFocusInWindow();
    }

    @Override public void doLayout() {
        int w = getWidth();
        int h = getHeight();

        backdrop.setBounds(0, 0, w, h);
        shade.setBounds(0, 0, w, h);

        int horizontalInset = Math.min(38, Math.max(18, w / 18));
        int cw = Math.min(620, Math.max(0, Math.min(w - horizontalInset * 2, w * 3 / 5)));
        int ch = Math.min(350, Math.max(0, h - 70));
        copy.setBounds(horizontalInset, Math.max(24, h - ch - 30), cw, ch);
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
