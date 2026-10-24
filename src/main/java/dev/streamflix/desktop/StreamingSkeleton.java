package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Lightweight animated placeholder used while the streaming home is loading. */
final class StreamingSkeleton extends JComponent {
    private final javax.swing.Timer timer;
    private float phase;

    StreamingSkeleton() {
        setPreferredSize(new Dimension(1200, 650));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 650));
        setOpaque(false);

        timer = new javax.swing.Timer(36, e -> {
            phase += 0.018f;
            if (phase > 1f) phase -= 1f;
            repaint();
        });
        timer.setCoalesce(true);
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!SmoothContentHost.isReducedMotion()) {
            timer.start();
        }
    }

    @Override public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = Math.max(1, getWidth());
            int heroH = Math.min(410, Math.max(300, getHeight() - 240));

            paintBlock(g2, 0, 0, w, heroH, 0);

            int contentX = 34;
            int contentW = Math.max(1, w - 68);
            paintBlock(g2, contentX, heroH + 28, 190, 18, 8);

            int gap = 14;
            int cards = Math.max(3, Math.min(5, contentW / 250));
            int cardW = Math.max(190, (contentW - gap * (cards - 1)) / cards);
            int cardH = Math.max(108, (int) Math.round(cardW * 9.0 / 16.0));

            int y = heroH + 62;
            for (int i = 0; i < cards; i++) {
                paintBlock(g2, contentX + i * (cardW + gap), y, cardW, cardH, 12);
            }

            y += cardH + 34;
            paintBlock(g2, contentX, y, 150, 16, 8);
        } finally {
            g2.dispose();
        }
    }

    private void paintBlock(Graphics2D g2, int x, int y, int w, int h, int arc) {
        Color base = new Color(24, 28, 37);
        Color shine = new Color(45, 51, 64);

        g2.setColor(base);
        g2.fillRoundRect(x, y, w, h, arc, arc);

        if (!SmoothContentHost.isReducedMotion()) {
            int shimmerWidth = Math.max(90, w / 4);
            int shimmerX = x - shimmerWidth + Math.round((w + shimmerWidth * 2) * phase);
            GradientPaint shimmer = new GradientPaint(
                    shimmerX, y, new Color(45, 51, 64, 0),
                    shimmerX + shimmerWidth, y, shine
            );
            g2.setPaint(shimmer);
            Shape oldClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
            g2.fillRect(shimmerX, y, shimmerWidth, h);
            g2.setClip(oldClip);
        }
    }
}
