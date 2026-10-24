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
    private final String displayTitle;
    private final String displayDescription;
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
                            0, 0, new Color(10, 12, 17, 245),
                            Math.max(1, (int) (w * 0.72)), 0, new Color(10, 12, 17, 18)));
                    g2.fillRect(0, 0, w, h);

                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.40)), new Color(10, 12, 17, 0),
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

        displayTitle = item.title() == null ? "" : item.title();
        JLabel title = Theme.heading(
                "<html>" + escape(displayTitle) + "</html>", 40f);
        titleLabel = title;
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel metadata = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        metadata.setOpaque(false);
        metadata.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (item.released() != null && !item.released().isBlank()) {
            String year = item.released().length() >= 4 ? item.released().substring(0, 4) : item.released();
            metadata.add(Theme.badge(year));
        }
        if (item.rating() != null && item.rating() > 0) {
            metadata.add(Theme.starRatingBadge(item.rating()));
        }
        metadata.add(Theme.badge(item.type() == Models.ShowType.MOVIE ? "Película" : "Serie"));
        metadata.add(Theme.badge("Full HD"));

        String synopsis = item.overview() == null || item.overview().isBlank()
                ? "Descubre este título en Streamflix."
                : item.overview();

        displayDescription = shorten(synopsis, 290);
        JLabel description = Theme.muted(
                "<html>" + escape(displayDescription) + "</html>");
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

        String sourceId = item.sourceProviderId() != null ? item.sourceProviderId() : "tmdb";
        boolean initiallyFavorite = UserData.isFavorite(sourceId, item.id());
        JButton favorite = Theme.button(initiallyFavorite ? "En mi lista" : "Mi lista",
                initiallyFavorite ? StreamflixIcons.Glyph.CHECK : StreamflixIcons.Glyph.PLUS);
        favorite.setToolTipText(initiallyFavorite ? "Quitar de Mi lista" : "Guardar en Mi lista");
        favorite.getAccessibleContext().setAccessibleName(initiallyFavorite ? "Quitar de Mi lista" : "Guardar en Mi lista");
        favorite.addActionListener(e -> {
            UserData.toggleFavorite(sourceId, item);
            boolean selected = UserData.isFavorite(sourceId, item.id());
            favorite.setText(selected ? "En mi lista" : "Mi lista");
            favorite.setToolTipText(selected ? "Quitar de Mi lista" : "Guardar en Mi lista");
            favorite.getAccessibleContext().setAccessibleName(selected ? "Quitar de Mi lista" : "Guardar en Mi lista");
            Theme.setButtonIcon(favorite,
                    selected ? StreamflixIcons.Glyph.CHECK : StreamflixIcons.Glyph.PLUS);
        });
        actions.add(favorite);

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
            // Swing's HTML body width can be ignored by JLabel's preferred
            // size calculation, leaving a visibly clipped sentence. Explicit
            // font-measured word boundaries make compact wrapping predictable.
            titleLabel.setText(wrappedHtml(displayTitle, titleLabel.getFontMetrics(titleLabel.getFont()), textWidth - 14, 2));
            descriptionLabel.setText(wrappedHtml(displayDescription, descriptionLabel.getFontMetrics(descriptionLabel.getFont()), textWidth - 14, 3));
        }
        copy.setBounds(horizontalInset, Math.max(24, h - ch - 30), cw, ch);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    static String wrappedHtml(String value, FontMetrics metrics, int width, int maxLines) {
        StringBuilder current = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();
        int limit = Math.max(60, width);
        boolean truncated = false;
        for (String word : (value == null ? "" : value.strip()).split("\\s+")) {
            if (word.isBlank()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && metrics.stringWidth(candidate) > limit) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() >= maxLines) {
                    truncated = true;
                    break;
                }
            }
            current.append(current.isEmpty() ? word : " " + word);
        }
        if (!current.isEmpty() && lines.size() < maxLines) lines.add(current.toString());
        if (lines.isEmpty()) lines.add("");
        if (truncated) {
            int last = lines.size() - 1;
            String tail = lines.get(last);
            while (tail.length() > 1 && metrics.stringWidth(tail + "…") > limit) {
                tail = tail.substring(0, tail.length() - 1);
            }
            lines.set(last, tail.stripTrailing() + "…");
        }
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) html.append("<br>");
            html.append(escape(lines.get(i)));
        }
        return html.append("</html>").toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
