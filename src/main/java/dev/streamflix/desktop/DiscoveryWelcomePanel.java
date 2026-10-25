package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Honest, actionable editorial start when the catalogue has not populated. */
final class DiscoveryWelcomePanel extends JPanel {
    private final JPanel content = new JPanel();
    private final JLabel title = Theme.heading("Tu cine, a tu manera.", 30f);
    private final JLabel message = Theme.muted("");
    private int lastWidth = -1;

    DiscoveryWelcomePanel(Runnable configureCatalog, Runnable browseMovies, Runnable browseLive) {
        super(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 385));
        setPreferredSize(new Dimension(1040, 385));
        setBorder(new EmptyBorder(16, 34, 14, 34));

        JPanel stage = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth(), h = getHeight();
                    g2.setPaint(new GradientPaint(0, 0, new Color(31, 36, 49),
                            w, h, Theme.BG));
                    g2.fillRoundRect(0, 0, w, h, 22, 22);
                    g2.setColor(new Color(201, 42, 54, 85));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, Math.max(1, w - 3), Math.max(1, h - 3), 22, 22);
                    g2.setColor(new Color(255, 255, 255, 16));
                    for (int i = 0; i < 5; i++) {
                        int x = (int) (w * .57) + i * 26;
                        g2.drawLine(x, 16, Math.min(w - 24, x + 110), h - 16);
                    }
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        stage.setOpaque(false);
        stage.setBorder(new EmptyBorder(30, 32, 30, 32));

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel eyebrow = Theme.eyebrow("BIENVENIDO A STREAMFLIX");
        eyebrow.setForeground(new Color(234, 184, 189));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setForeground(new Color(210, 216, 229));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton configure = Theme.primaryButton("Configurar catálogo", StreamflixIcons.Glyph.INFO);
        configure.setToolTipText("Agregar una clave personal de TMDb en Ajustes");
        configure.addActionListener(e -> configureCatalog.run());
        JButton browse = Theme.button("Explorar películas", StreamflixIcons.Glyph.MORE);
        browse.addActionListener(e -> browseMovies.run());
        JButton live = Theme.button("Ver TV", StreamflixIcons.Glyph.PLAY);
        live.addActionListener(e -> browseLive.run());
        actions.add(configure);
        actions.add(browse);
        actions.add(live);

        content.add(eyebrow);
        content.add(Box.createVerticalStrut(13));
        content.add(title);
        content.add(Box.createVerticalStrut(12));
        content.add(message);
        content.add(Box.createVerticalStrut(22));
        content.add(actions);
        stage.add(content, BorderLayout.CENTER);

        JLabel mark = new JLabel(BrandMark.markIcon(92));
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setPreferredSize(new Dimension(130, 130));
        stage.add(mark, BorderLayout.EAST);
        add(stage, BorderLayout.CENTER);
    }

    @Override public void doLayout() {
        int inner = Math.max(200, getWidth() - 34 * 2 - 32 * 2 - 150);
        int width = Math.min(660, inner);
        if (lastWidth != width) {
            lastWidth = width;
            title.setFont(Theme.FONT_DISPLAY.deriveFont(width < 400 ? 23f : 30f));
            message.setText("<html><body style='width:" + width + "px'>"
                    + "Añade tu acceso personal a TMDb para descubrir portadas y recomendaciones. "
                    + "También puedes explorar películas o TV sin salir de esta pantalla."
                    + "</body></html>");
        }
        super.doLayout();
    }
}
