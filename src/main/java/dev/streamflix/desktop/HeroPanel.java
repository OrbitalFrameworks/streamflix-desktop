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
    private final JLabel titleLabel;
    private final JLabel descriptionLabel;
    private final String escapedTitle;
    private final String escapedDescription;
    private int lastTextWidth = -1;

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
        backdrop.setEditorialFallback(item.title());
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
        eyebrow.setForeground(new Color(220, 220, 224));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        escapedTitle = escape(item.title());
        JLabel title = Theme.heading(
                "<html><body style='width:500px'>" + escapedTitle + "</body></html>", 40f);
        titleLabel = title;
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel metadata = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        metadata.setOpaque(false);
        metadata.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (item.released() != null && !item.released().isBlank()) {
            JLabel released = Theme.muted(item.released().length() >= 4 ? item.released().substring(0, 4) : item.released());
            released.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            metadata.add(released);
        }
        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f / 10", item.rating()));
            rating.setForeground(new Color(232, 211, 96));
            rating.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            metadata.add(rating);
        }
        JLabel type = Theme.muted(item.type() == Models.ShowType.MOVIE ? "Película" : "Serie");
        type.setFont(Theme.FONT.deriveFont(12.5f));
        metadata.add(type);

        String synopsis = item.overview() == null || item.overview().isBlank()
                ? "Descubre este título en Streamflix."
                : item.overview();

        escapedDescription = escape(shorten(synopsis, 290));
        JLabel description = Theme.muted(
                "<html><body style='width:490px'>" + escapedDescription + "</body></html>");
        descriptionLabel = description;
        description.setForeground(new Color(218, 220, 225));
        description.setFont(Theme.FONT.deriveFont(14.5f));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton play = Theme.primaryButton("Reproducir", StreamflixIcons.Glyph.PLAY);
        play.addActionListener(e -> onPlay.accept(item));
        this.playButton = play;
        actions.add(play);

        JButton open = Theme.button("Más información", StreamflixIcons.Glyph.INFO);
        open.addActionListener(e -> onOpen.accept(item));
        actions.add(open);

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(10));
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(metadata);
        copy.add(Box.createVerticalStrut(15));
        copy.add(description);
        copy.add(Box.createVerticalStrut(20));
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
        // Both authentic backdrops and the offline title-derived composition
        // reserve the right-hand editorial zone. At compact desktop sizes this
        // also prevents long synopsis text from crossing the visual subject.
        int safeCopyWidth = (int) (w * 0.52) - horizontalInset - 8;
        int cw = Math.min(620, Math.max(0, Math.min(w - horizontalInset * 2, safeCopyWidth)));
        int ch = Math.min(350, Math.max(0, h - 70));
        int textWidth = Math.max(100, cw - 8);
        if (lastTextWidth != textWidth) {
            lastTextWidth = textWidth;
            titleLabel.setText("<html><body style='width:" + textWidth + "px'>" + escapedTitle + "</body></html>");
            descriptionLabel.setText("<html><body style='width:" + textWidth + "px'>" + escapedDescription + "</body></html>");
        }
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
