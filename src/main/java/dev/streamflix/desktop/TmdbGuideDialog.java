package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

/** Non-technical onboarding for obtaining a personal TMDb developer credential. */
final class TmdbGuideDialog extends JDialog {
    private static final String SIGNUP_URL = "https://www.themoviedb.org/signup";
    private static final String API_SETTINGS_URL = "https://www.themoviedb.org/settings/api";
    private static final String FAQ_URL = "https://developer.themoviedb.org/docs/faq";

    TmdbGuideDialog(Window owner) {
        super(owner, "Guía TMDb · Streamflix", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        setContentPane(build());
        setMinimumSize(new Dimension(760, 640));
        setSize(820, 720);
        setLocationRelativeTo(owner);
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = Theme.heading("Conseguir una credencial de TMDb", 25f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted(
                "<html><body style='width:700px'>"
                + "Se hace una sola vez. Usa un navegador de escritorio y completa los datos de TMDb "
                + "de forma veraz según tu propio uso."
                + "</body></html>");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JPanel steps = new JPanel();
        steps.setBackground(Theme.BG);
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.setBorder(new EmptyBorder(18, 2, 24, 2));

        steps.add(step(
                "1", "Crea o inicia sesión en TMDb",
                "Abre TMDb en tu navegador y entra con tu cuenta. Si todavía no tienes una, crea una.",
                "Crear cuenta en TMDb", SIGNUP_URL));
        steps.add(Box.createVerticalStrut(10));

        steps.add(step(
                "2", "Abre Settings → API",
                "Dentro de la configuración de tu cuenta, entra a la sección API y solicita una credencial.",
                "Abrir Settings → API", API_SETTINGS_URL));
        steps.add(Box.createVerticalStrut(10));

        steps.add(step(
                "3", "Acepta los términos y elige el uso correcto",
                "Si TMDb te muestra un tipo de uso, elige la opción que corresponda a tu situación real. "
                + "Para un uso personal, educativo o no comercial, utiliza la opción de desarrollador/no comercial "
                + "si está disponible. Si tu uso es comercial o está orientado a generar ingresos, usa la vía comercial de TMDb.",
                "Ver FAQ oficial", FAQ_URL));
        steps.add(Box.createVerticalStrut(10));

        steps.add(step(
                "4", "Completa los datos de la aplicación",
                "TMDb puede pedir nombre de la aplicación, URL, descripción y datos de contacto. "
                + "Escribe información real. No necesitas describir técnicamente el reproductor: basta explicar "
                + "que utilizas TMDb para metadata, pósters, temporadas y episodios.",
                null, null));
        steps.add(Box.createVerticalStrut(10));

        steps.add(descriptionExamples());
        steps.add(Box.createVerticalStrut(10));

        steps.add(step(
                "6", "Copia una de las credenciales",
                "Cuando TMDb apruebe la solicitud, copia la API Key (v3 auth) o el API Read Access Token. "
                + "Streamflix acepta cualquiera de las dos.",
                "Abrir de nuevo Settings → API", API_SETTINGS_URL));
        steps.add(Box.createVerticalStrut(10));

        steps.add(step(
                "7", "Pégala en Streamflix",
                "Vuelve a Ajustes → TMDb, pega la credencial, pulsa “Probar credencial” y después “Guardar”. "
                + "La credencial queda almacenada únicamente en tu PC.",
                null, null));

        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setBackground(Theme.BG);
        viewport.add(steps, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(viewport);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton close = Theme.primaryButton("Entendido");
        close.addActionListener(e -> dispose());
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);

        return root;
    }

    private JComponent step(String number, String title, String body, String action, String uri) {
        JPanel card = Theme.surface();
        card.setLayout(new BorderLayout(14, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel badge = new JLabel(number, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS_CONTROL, Theme.RADIUS_CONTROL);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(Color.WHITE);
        badge.setFont(Theme.FONT_BOLD.deriveFont(15f));
        badge.setPreferredSize(new Dimension(36, 36));
        card.add(badge, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading(title, 15f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel description = Theme.muted(
                "<html><body style='width:590px'>" + escape(body) + "</body></html>");
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        copy.add(heading);
        copy.add(Box.createVerticalStrut(5));
        copy.add(description);

        if (action != null && uri != null) {
            copy.add(Box.createVerticalStrut(9));
            JButton button = Theme.button(action);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> openUri(uri));
            copy.add(button);
        }

        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    private JComponent descriptionExamples() {
        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = Theme.heading("5 · Ejemplo de descripción", 15f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(5));

        JLabel note = Theme.muted(
                "<html><body style='width:650px'>"
                + "Elige únicamente un ejemplo que describa tu uso real y personalízalo si hace falta. "
                + "No presentes como personal o no comercial un uso que en realidad sea distinto."
                + "</body></html>");
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(note);
        card.add(Box.createVerticalStrut(12));

        addExample(card,
                "Uso personal / no comercial",
                "I use the TMDB API in a personal, non-commercial desktop application to browse movie and TV metadata, posters, ratings, seasons and episode information. I do not sell access to TMDB data or images.");

        addExample(card,
                "Aprendizaje / proyecto open source",
                "I use the TMDB API in a non-commercial open-source learning project that displays movie and TV metadata and artwork in a desktop interface. The project is used for development and educational purposes and does not monetize TMDB content.");

        addExample(card,
                "Pruebas personales",
                "I am evaluating the TMDB API in a personal desktop project to enrich a media browsing interface with titles, artwork, ratings, seasons and episode metadata. This use is non-commercial.");

        return card;
    }

    private void addExample(JPanel parent, String label, String text) {
        JLabel title = new JLabel(label);
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(title);
        parent.add(Box.createVerticalStrut(4));

        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(true);
        area.setBackground(Theme.PANEL_ALT);
        area.setForeground(Theme.TEXT);
        area.setFont(Theme.FONT.deriveFont(12f));
        area.setBorder(new EmptyBorder(9, 10, 9, 10));
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(area);
        parent.add(Box.createVerticalStrut(6));

        JButton copy = Theme.button("Copiar ejemplo");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            copy.setText("Copiado");
            javax.swing.Timer timer = new javax.swing.Timer(1400, event -> copy.setText("Copiar ejemplo"));
            timer.setRepeats(false);
            timer.start();
        });
        parent.add(copy);
        parent.add(Box.createVerticalStrut(12));
    }

    private void openUri(String uri) {
        try {
            if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException();
            Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No pude abrir el navegador. Copia esta dirección:\n" + uri,
                    "Abrir enlace",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
