package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Responsive cover artwork that caches the expensive cover-scale operation. */
final class ArtworkPanel extends JPanel {
    private volatile BufferedImage image;
    private volatile BufferedImage scaled;
    private volatile int scaledWidth = -1;
    private volatile int scaledHeight = -1;
    private String fallbackText = "";
    private boolean editorialFallback;

    ArtworkPanel(String url) {
        setOpaque(true);
        setBackground(Theme.PANEL_ALT);
        ImageLoader.loadRaw(url, loaded -> {
            image = loaded;
            scaled = null;
            scaledWidth = -1;
            scaledHeight = -1;
            repaint();
        });
    }

    void setFallbackText(String text) {
        fallbackText = text == null ? "" : text;
        editorialFallback = false;
        repaint();
    }

    /** Draw abstract, title-derived visual texture without repeating the overlay title. */
    void setEditorialFallback(String title) {
        fallbackText = title == null ? "" : title;
        editorialFallback = true;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage current = image;
        if (current == null) {
            paintFallback(g);
            return;
        }

        int targetWidth = Math.max(1, getWidth());
        int targetHeight = Math.max(1, getHeight());
        BufferedImage ready = scaled;

        if (ready == null || scaledWidth != targetWidth || scaledHeight != targetHeight) {
            ready = scaleCover(current, targetWidth, targetHeight);
            scaled = ready;
            scaledWidth = targetWidth;
            scaledHeight = targetHeight;
        }

        g.drawImage(ready, 0, 0, null);
    }

    private static BufferedImage scaleCover(BufferedImage source, int width, int height) {
        double scale = Math.max(
                width / (double) source.getWidth(),
                height / (double) source.getHeight()
        );
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            // Bilinear is visually sufficient for card/downscale work and materially
            // cheaper than re-running bicubic scaling on every scroll repaint.
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        } finally {
            g2.dispose();
        }
        return canvas;
    }

    private void paintFallback(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // A missing cached image must not turn a populated catalog into a wall of
            // empty rectangles. This is intentionally an honest typographic fallback:
            // it uses only the title already supplied by the provider, never invented art.
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            int hash = fallbackText != null && !fallbackText.isBlank() ? Math.abs(fallbackText.hashCode()) : 0;
            Color base = switch (hash % 4) {
                case 0 -> new Color(28, 31, 43);
                case 1 -> new Color(34, 29, 38);
                case 2 -> new Color(25, 35, 43);
                default -> new Color(39, 31, 30);
            };
            Color edge = new Color(Math.min(255, base.getRed() + 20),
                    Math.min(255, base.getGreen() + 16), Math.min(255, base.getBlue() + 20));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, edge, w, h, base));
            g2.fillRect(0, 0, w, h);

            int stripeX = Math.max(10, w / 11);
            g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 205));
            g2.fillRoundRect(stripeX, Math.max(10, h / 8), Math.max(3, w / 72), Math.max(28, h * 3 / 4), 5, 5);
            g2.setColor(new Color(255, 255, 255, 24));
            g2.fillRoundRect(stripeX + Math.max(12, w / 15), Math.max(10, h / 8),
                    Math.max(18, w / 4), Math.max(3, h / 42), 4, 4);

            if (editorialFallback) {
                paintEditorialFallback(g2, w, h, hash);
                return;
            }

            if (fallbackText == null || fallbackText.isBlank()) return;

            int padding = Math.max(14, w / 12);
            boolean stage = h > 260;
            float size = stage ? Math.min(42f, Math.max(20f, w / 22f))
                    : Math.min(18f, Math.max(11f, w / 17f));
            g2.setColor(new Color(246, 247, 249, 235));
            g2.setFont(Theme.FONT_DISPLAY.deriveFont(size));
            FontMetrics metrics = g2.getFontMetrics();
            int maxWidth = Math.max(40, w - padding * 2);
            java.util.List<String> lines = wrap(fallbackText, metrics, maxWidth, stage ? 3 : 2);
            int lineHeight = metrics.getHeight();
            int y = stage ? Math.max(h / 2 - (lines.size() * lineHeight) / 2, h / 4)
                    : Math.max(h - padding - (lines.size() - 1) * lineHeight, metrics.getAscent() + padding);
            for (String line : lines) {
                g2.drawString(line, padding, y);
                y += lineHeight;
            }

            if (stage) {
                g2.setColor(new Color(246, 247, 249, 150));
                g2.setFont(Theme.FONT_BOLD.deriveFont(11f));
                g2.drawString("CATÁLOGO STREAMFLIX", padding, Math.min(h - padding, y + 20));
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintEditorialFallback(Graphics2D g2, int w, int h, int hash) {
        // A self-contained vector treatment: no invented cover photos, external
        // downloads, repeated title label, or persistent image allocations.
        int artW = Math.max(55, (int) (w * (w > 700 ? 0.37 : 0.44)));
        int artH = Math.max(45, (int) (h * 0.77));
        int x = Math.max(4, (int) (w * (w > 700 ? 0.57 : 0.48)));
        int y = Math.max(6, (h - artH) / 2);
        Color[] palette = {
                new Color(127, 46, 52), new Color(61, 102, 123),
                new Color(116, 79, 57), new Color(94, 73, 119)
        };
        Color hue = palette[Math.floorMod(hash, palette.length)];
        g2.setPaint(new GradientPaint(x, y, new Color(hue.getRed(), hue.getGreen(), hue.getBlue(), 210),
                x + artW, y + artH, new Color(15, 19, 27, 20)));
        g2.fillRoundRect(x, y, artW, artH, Math.max(12, artW / 10), Math.max(12, artW / 10));

        // Recurring film-frame linework adds structure at all viewport sizes.
        g2.setStroke(new BasicStroke(Math.max(1f, w / 780f)));
        g2.setColor(new Color(244, 233, 219, 60));
        g2.drawRoundRect(x + 9, y + 9, Math.max(1, artW - 18), Math.max(1, artH - 18),
                Math.max(8, artW / 13), Math.max(8, artW / 13));
        int dot = Math.max(2, Math.min(8, artW / 45));
        for (int k = 0; k < 6; k++) {
            int px = x + 18 + k * Math.max(6, (artW - 40) / 6);
            g2.fillRoundRect(px, y + 15, dot, Math.max(2, dot / 2), 2, 2);
            g2.fillRoundRect(px, y + artH - 19, dot, Math.max(2, dot / 2), 2, 2);
        }

        String key = fallbackText.strip();
        String[] words = key.split("\\s+");
        StringBuilder mark = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank() && Character.isLetterOrDigit(word.charAt(0))) {
                mark.append(Character.toUpperCase(word.charAt(0)));
                if (mark.length() == 2) break;
            }
        }
        if (mark.isEmpty()) mark.append("S");
        g2.setColor(new Color(250, 240, 225, 145));
        g2.setFont(Theme.FONT_DISPLAY.deriveFont(Font.BOLD,
                Math.max(20f, Math.min(205f, Math.min(artH * 0.46f, artW * 0.48f)))));
        FontMetrics fm = g2.getFontMetrics();
        String lettering = mark.toString();
        g2.drawString(lettering,
                x + Math.max(0, (artW - fm.stringWidth(lettering)) / 2),
                y + (artH - fm.getHeight()) / 2 + fm.getAscent());
    }

    private static java.util.List<String> wrap(String value, FontMetrics metrics, int maxWidth, int maxLines) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() == maxLines) break;
            }
            if (lines.size() < maxLines) line.append(line.isEmpty() ? word : " " + word);
        }
        if (!line.isEmpty() && lines.size() < maxLines) lines.add(line.toString());
        if (lines.isEmpty()) lines.add(value);
        if (lines.size() == maxLines && !lines.get(lines.size() - 1).equals(value)) {
            int last = lines.size() - 1;
            String clipped = lines.get(last);
            while (clipped.length() > 1 && metrics.stringWidth(clipped + "…") > maxWidth) {
                clipped = clipped.substring(0, clipped.length() - 1);
            }
            lines.set(last, clipped + "…");
        }
        return lines;
    }
}
