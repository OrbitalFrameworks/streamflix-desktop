package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

/** Local application settings. Secrets are never logged or echoed into status text. */
final class SettingsDialog extends JDialog {
    private static final String TMDB_API_URL = "https://www.themoviedb.org/settings/api";

    private final JPasswordField tmdbKey = new JPasswordField(40);
    private final JLabel status = Theme.muted(" ");
    private final JButton testButton = Theme.button("Probar credencial");
    private final JButton saveButton = Theme.primaryButton("Guardar");
    private final JComboBox<String> catalogLanguage = new JComboBox<>(new String[]{"Español", "English"});
    private final JCheckBox startMaximized = new JCheckBox("Iniciar Streamflix maximizado");
    private final JComboBox<String> audioLanguage = new JComboBox<>(
            new String[]{"Automático", "Español", "English"});
    private final JComboBox<String> subtitleLanguage = new JComboBox<>(
            new String[]{"Español", "English", "Desactivados"});
    private final JComboBox<String> qualityProfile = new JComboBox<>(
            new String[]{"Automática", "Ahorro de datos", "Equilibrada", "Alta", "Máxima"});

    SettingsDialog(Window owner) {
        super(owner, "Configuración · Streamflix", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        setResizable(true);
        setContentPane(build());
        pack();
        setMinimumSize(new Dimension(720, 560));
        setLocationRelativeTo(owner);
        loadCurrent();
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));
        root.setPreferredSize(new Dimension(780, 600));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Theme.heading("Configuración", 26f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted("Catálogos, servicios externos y reproducción.");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setFont(Theme.FONT.deriveFont(13f));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(18, 0, 12, 0));
        tabs.addTab("General", buildGeneralPanel());
        tabs.addTab("Reproductor", buildPlayerPanel());
        tabs.addTab("TMDb", buildTmdbPanel());
        tabs.addTab("Fuentes", buildSourcesPanel());
        tabs.addTab("Datos", buildDataPanel());
        tabs.addTab("Acerca de", buildAboutPanel());
        root.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);

        status.setFont(Theme.FONT.deriveFont(12f));
        footer.add(status, BorderLayout.CENTER);

        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setOpaque(false);
        JButton close = Theme.button("Cerrar");
        close.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());
        footerActions.add(close);
        footerActions.add(saveButton);
        footer.add(footerActions, BorderLayout.EAST);

        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildGeneralPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel general = Theme.surface();
        general.setLayout(new BoxLayout(general, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading("Experiencia", 18f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        general.add(heading);
        general.add(Box.createVerticalStrut(8));

        JLabel explanation = Theme.muted(
                "<html><body style='width:640px'>"
                + "Streamflix usa TMDb como catálogo principal. Las fuentes de reproducción se resuelven detrás de escena."
                + "</body></html>");
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        general.add(explanation);
        general.add(Box.createVerticalStrut(18));

        JLabel languageLabel = new JLabel("Idioma del catálogo");
        languageLabel.setForeground(Theme.TEXT);
        languageLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        languageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        general.add(languageLabel);
        general.add(Box.createVerticalStrut(7));

        catalogLanguage.setMaximumSize(new Dimension(220, 38));
        catalogLanguage.setAlignmentX(Component.LEFT_ALIGNMENT);
        catalogLanguage.setToolTipText("Cambia títulos, descripciones y metadata de TMDb.");
        general.add(catalogLanguage);
        general.add(Box.createVerticalStrut(20));

        startMaximized.setOpaque(false);
        startMaximized.setForeground(Theme.TEXT);
        startMaximized.setFont(Theme.FONT.deriveFont(13f));
        startMaximized.setAlignmentX(Component.LEFT_ALIGNMENT);
        general.add(startMaximized);

        wrapper.add(general, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent buildTmdbPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel tmdb = Theme.surface();
        tmdb.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        JLabel title = Theme.heading("The Movie Database (TMDb)", 18f);
        tmdb.add(title, c);

        c.gridy++;
        c.insets = new Insets(4, 0, 0, 0);
        JLabel note = new JLabel("Una credencial sirve para TMDb ES y TMDb EN");
        note.setForeground(new Color(104, 211, 145));
        note.setFont(Theme.FONT_BOLD.deriveFont(12f));
        tmdb.add(note, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        JLabel explanation = Theme.muted(
                "<html><body style='width:640px'>"
                + "TMDb es el catálogo principal de metadata: títulos, pósters, temporadas y episodios. "
                + "No es el servidor de video. En esta build pública cada usuario aporta su propia credencial; "
                + "la misma API key v3 o Read Access Token funciona para español e inglés."
                + "</body></html>");
        tmdb.add(explanation, c);

        c.gridy++;
        c.insets = new Insets(17, 0, 0, 0);
        JLabel keyLabel = new JLabel("API key v3 o Read Access Token");
        keyLabel.setForeground(Theme.TEXT);
        keyLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        tmdb.add(keyLabel, c);

        c.gridy++;
        c.insets = new Insets(7, 0, 0, 0);
        tmdbKey.putClientProperty("JTextField.placeholderText", "Pega aquí tu credencial de TMDb");
        tmdbKey.setToolTipText("Se guarda únicamente en %APPDATA%\\Streamflix\\settings.json");
        tmdb.add(tmdbKey, c);

        c.gridy++;
        c.insets = new Insets(7, 0, 0, 0);
        JPanel underField = new JPanel(new BorderLayout());
        underField.setOpaque(false);
        JCheckBox show = new JCheckBox("Mostrar credencial");
        show.setOpaque(false);
        show.setForeground(Theme.MUTED);
        show.setFont(Theme.FONT.deriveFont(12f));
        char echo = tmdbKey.getEchoChar();
        show.addActionListener(e -> tmdbKey.setEchoChar(show.isSelected() ? (char) 0 : echo));
        underField.add(show, BorderLayout.WEST);
        JLabel storage = Theme.muted("Guardado local · nunca se commitea al repositorio");
        storage.setFont(Theme.FONT.deriveFont(11.5f));
        underField.add(storage, BorderLayout.EAST);
        tmdb.add(underField, c);

        c.gridy++;
        c.insets = new Insets(18, 0, 0, 0);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton guide = Theme.primaryButton("Guía paso a paso");
        guide.addActionListener(e -> new TmdbGuideDialog(this).setVisible(true));
        JButton openTmdb = Theme.button("Abrir página de TMDb");
        openTmdb.addActionListener(e -> openTmdbSite());
        testButton.addActionListener(e -> testCredential());
        actions.add(guide);
        actions.add(openTmdb);
        actions.add(testButton);
        tmdb.add(actions, c);

        wrapper.add(tmdb, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent buildSourcesPanel() {
        JPanel root = new JPanel();
        root.setBackground(Theme.BG);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(12, 4, 16, 4));

        JLabel explanation = Theme.muted(
                "<html><body style='width:650px'>"
                + "<b>Modelo actual:</b> TMDb es el catálogo principal de Streamflix. "
                + "Las fuentes de reproducción se resuelven automáticamente detrás de escena. "
                + "Esta sección existe para diagnóstico y mantenimiento; no necesitas elegir una fuente para ver contenido."
                + "</body></html>");
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(explanation);
        root.add(Box.createVerticalStrut(18));

        root.add(sourceGroup("Catálogos principales", ProviderRegistry.all().stream()
                .filter(TmdbProvider.class::isInstance).toList()));
        root.add(Box.createVerticalStrut(14));
        root.add(sourceGroup("Catálogos alternativos", ProviderRegistry.all().stream()
                .filter(p -> !(p instanceof TmdbProvider) && !(p instanceof M3uLiveProvider)).toList()));
        root.add(Box.createVerticalStrut(14));
        root.add(sourceGroup("TV en vivo", ProviderRegistry.all().stream()
                .filter(M3uLiveProvider.class::isInstance).toList()));

        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setBackground(Theme.BG);
        viewport.add(root, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(viewport);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        return scroll;
    }

    private JComponent sourceGroup(String title, java.util.List<Provider> providers) {
        JPanel group = Theme.surface();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = Theme.heading(title, 16f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(heading);
        group.add(Box.createVerticalStrut(8));

        for (Provider provider : providers) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

            JLabel name = new JLabel(provider.name());
            name.setForeground(Theme.TEXT);
            name.setFont(Theme.FONT_BOLD.deriveFont(13f));
            row.add(name, BorderLayout.WEST);

            String capability = provider instanceof M3uLiveProvider
                    ? "Canales en vivo"
                    : provider.supportsMovies() && provider.supportsTvShows()
                        ? "Películas · Series"
                        : provider.supportsMovies() ? "Películas" : "Series";
            JLabel state = Theme.muted(capability + "   ·   Registrado");
            state.setFont(Theme.FONT.deriveFont(11.5f));
            row.add(state, BorderLayout.EAST);

            group.add(row);
            group.add(Box.createVerticalStrut(5));
        }
        return group;
    }

    private JComponent buildPlayerPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel player = Theme.surface();
        player.setLayout(new BoxLayout(player, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading("Reproductor integrado", 18f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(heading);
        player.add(Box.createVerticalStrut(8));

        JLabel engine = Theme.muted("Motor: mpv portable, renderizado dentro de Streamflix");
        engine.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(engine);
        player.add(Box.createVerticalStrut(6));

        JLabel available = new JLabel(MpvPlayer.isAvailable()
                ? "Reproductor detectado y disponible"
                : "mpv no detectado");
        available.setForeground(MpvPlayer.isAvailable() ? new Color(104, 211, 145) : Theme.DANGER);
        available.setFont(Theme.FONT_BOLD.deriveFont(12f));
        available.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(available);
        player.add(Box.createVerticalStrut(14));

        JLabel behavior = Theme.muted(
                "<html><body style='width:640px'>"
                + "Automático prioriza fuentes que históricamente arrancaron mejor en este equipo. "
                + "La calidad Automática evita forzar siempre el bitrate máximo y reduce la calidad si la red se queda corta."
                + "</body></html>");
        behavior.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(behavior);
        player.add(Box.createVerticalStrut(18));

        JLabel qualityLabel = new JLabel("Calidad de reproducción");
        qualityLabel.setForeground(Theme.TEXT);
        qualityLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        qualityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(qualityLabel);
        player.add(Box.createVerticalStrut(7));
        qualityProfile.setMaximumSize(new Dimension(240, 38));
        qualityProfile.setAlignmentX(Component.LEFT_ALIGNMENT);
        qualityProfile.setToolTipText("Automática equilibra arranque, estabilidad y calidad.");
        player.add(qualityProfile);
        player.add(Box.createVerticalStrut(16));

        JLabel audioLabel = new JLabel("Idioma de audio preferido");
        audioLabel.setForeground(Theme.TEXT);
        audioLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        audioLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(audioLabel);
        player.add(Box.createVerticalStrut(7));
        audioLanguage.setMaximumSize(new Dimension(220, 38));
        audioLanguage.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(audioLanguage);
        player.add(Box.createVerticalStrut(16));

        JLabel subtitleLabel = new JLabel("Subtítulos preferidos");
        subtitleLabel.setForeground(Theme.TEXT);
        subtitleLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(subtitleLabel);
        player.add(Box.createVerticalStrut(7));
        subtitleLanguage.setMaximumSize(new Dimension(220, 38));
        subtitleLanguage.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(subtitleLanguage);

        wrapper.add(player, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent buildDataPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel data = Theme.surface();
        data.setLayout(new BoxLayout(data, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading("Datos locales", 18f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        data.add(heading);
        data.add(Box.createVerticalStrut(8));

        JLabel explanation = Theme.muted(
                "<html><body style='width:640px'>"
                + "Favoritos, historial y progreso de reproducción se guardan localmente en %APPDATA%\\Streamflix."
                + "</body></html>");
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        data.add(explanation);
        data.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton clearHistory = Theme.button("Borrar historial y progreso");
        clearHistory.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Se eliminará el historial y todo el progreso de reproducción guardado.",
                    "Borrar historial",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.OK_OPTION) {
                UserData.clearHistory();
                status.setForeground(new Color(104, 211, 145));
                status.setText("Historial y progreso eliminados.");
            }
        });

        JButton clearFavorites = Theme.button("Borrar Mi lista");
        clearFavorites.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Se eliminarán todos los títulos guardados en Mi lista.",
                    "Borrar Mi lista",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.OK_OPTION) {
                UserData.clearFavorites();
                status.setForeground(new Color(104, 211, 145));
                status.setText("Mi lista fue vaciada.");
            }
        });

        actions.add(clearHistory);
        actions.add(clearFavorites);
        data.add(actions);

        wrapper.add(data, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent buildAboutPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel about = Theme.surface();
        about.setLayout(new BoxLayout(about, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading("Streamflix Desktop", 20f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        about.add(heading);
        about.add(Box.createVerticalStrut(6));

        JLabel version = Theme.muted("Versión " + AppVersion.current());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        about.add(version);
        about.add(Box.createVerticalStrut(14));

        JLabel description = Theme.muted(
                "<html><body style='width:640px'>"
                + "Cliente de streaming para Windows basado en Java/Swing y mpv, "
                + "con catálogo TMDb y fuentes de reproducción compatibles."
                + "</body></html>");
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        about.add(description);
        about.add(Box.createVerticalStrut(14));

        JLabel tmdbAttribution = Theme.muted(
                "<html><body style='width:640px'>"
                + "This product uses the TMDB API but is not endorsed or certified by TMDB."
                + "</body></html>");
        tmdbAttribution.setAlignmentX(Component.LEFT_ALIGNMENT);
        about.add(tmdbAttribution);
        about.add(Box.createVerticalStrut(18));

        JButton github = Theme.button("Abrir repositorio en GitHub");
        github.setAlignmentX(Component.LEFT_ALIGNMENT);
        github.addActionListener(e -> openUri(
                "https://github.com/Angel52781/streamflix-desktop",
                "No pude abrir el repositorio."));
        about.add(github);
        about.add(Box.createVerticalStrut(8));

        JButton updates = Theme.button("Buscar actualizaciones");
        updates.setAlignmentX(Component.LEFT_ALIGNMENT);
        updates.addActionListener(e -> UpdateService.checkAndPrompt(this, true));
        about.add(updates);
        about.add(Box.createVerticalStrut(8));

        JButton diagnostics = Theme.button("Exportar diagnóstico");
        diagnostics.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagnostics.addActionListener(e -> Diagnostics.chooseAndExport(this));
        about.add(diagnostics);

        wrapper.add(about, BorderLayout.NORTH);
        return wrapper;
    }

    private void loadCurrent() {
        catalogLanguage.setSelectedIndex("en".equals(TmdbSettings.catalogLanguage()) ? 1 : 0);
        startMaximized.setSelected(PlaybackSettings.startMaximized());

        audioLanguage.setSelectedIndex(switch (PlaybackSettings.audioLanguage()) {
            case "es" -> 1;
            case "en" -> 2;
            default -> 0;
        });
        subtitleLanguage.setSelectedIndex(switch (PlaybackSettings.subtitleLanguage()) {
            case "en" -> 1;
            case "off" -> 2;
            default -> 0;
        });
        qualityProfile.setSelectedIndex(switch (PlaybackSettings.qualityProfile()) {
            case "saver" -> 1;
            case "balanced" -> 2;
            case "high" -> 3;
            case "max" -> 4;
            default -> 0;
        });

        try {
            String key = TmdbSettings.localApiKey();
            tmdbKey.setText(key);
            if (TmdbSettings.environmentOverrideActive()) {
                status.setText("STREAMFLIX_TMDB_API_KEY está configurada y tiene prioridad sobre este campo.");
            } else if (!key.isBlank()) {
                status.setText("Hay una credencial local guardada. Puedes probarla o reemplazarla.");
            } else {
                status.setText("Aún no hay una credencial de TMDb configurada.");
            }
        } catch (TmdbException ex) {
            status.setForeground(Theme.DANGER);
            status.setText("No pudimos leer la configuración local de TMDb. Puedes volver a guardarla.");
        }
    }

    private void testCredential() {
        char[] chars = tmdbKey.getPassword();
        String credential = new String(chars).strip();
        Arrays.fill(chars, '\0');

        if (credential.isBlank()) {
            status.setForeground(Theme.DANGER);
            status.setText("Pega una API key o Read Access Token antes de probar.");
            return;
        }

        setTesting(true);
        status.setForeground(Theme.MUTED);
        status.setText("Comprobando conexión con TMDb…");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                TmdbClient client = new TmdbClient("en", () -> credential, new Http()::get);
                client.get("discover/movie", Map.of("page", "1"));
                return null;
            }

            @Override protected void done() {
                setTesting(false);
                try {
                    get();
                    status.setForeground(Theme.SUCCESS);
                    status.setText("Conexión correcta. Esta credencial puede usar TMDb ES y EN.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setForeground(Theme.DANGER);
                    status.setText("No pudimos validar la credencial. Revisa el valor e inténtalo de nuevo.");
                }
            }
        }.execute();
    }

    private void setTesting(boolean testing) {
        testButton.setEnabled(!testing);
        saveButton.setEnabled(!testing);
        tmdbKey.setEnabled(!testing);
    }

    private void save() {
        char[] chars = tmdbKey.getPassword();
        try {
            TmdbSettings.saveApiKey(new String(chars));
            TmdbSettings.saveCatalogLanguage(catalogLanguage.getSelectedIndex() == 1 ? "en" : "es");
            PlaybackSettings.save(
                    startMaximized.isSelected(),
                    switch (audioLanguage.getSelectedIndex()) {
                        case 1 -> "es";
                        case 2 -> "en";
                        default -> "auto";
                    },
                    switch (subtitleLanguage.getSelectedIndex()) {
                        case 1 -> "en";
                        case 2 -> "off";
                        default -> "es";
                    },
                    switch (qualityProfile.getSelectedIndex()) {
                        case 1 -> "saver";
                        case 2 -> "balanced";
                        case 3 -> "high";
                        case 4 -> "max";
                        default -> "auto";
                    }
            );
            status.setForeground(Theme.SUCCESS);
            status.setText(TmdbSettings.environmentOverrideActive()
                    ? "Guardada. La variable de entorno seguirá teniendo prioridad."
                    : "Configuración guardada.");
            dispose();
        } catch (TmdbException ex) {
            status.setForeground(Theme.DANGER);
            status.setText("No pudimos guardar la configuración. Revisa los campos e inténtalo de nuevo.");
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private void openTmdbSite() {
        openUri(TMDB_API_URL, "No pude abrir el navegador. Ve a themoviedb.org → Settings → API.");
    }

    private void openUri(String uri, String failureMessage) {
        try {
            if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException();
            Desktop.getDesktop().browse(URI.create(uri));
            status.setForeground(Theme.MUTED);
            status.setText("Abrí el enlace en tu navegador.");
        } catch (Exception ex) {
            status.setForeground(Theme.DANGER);
            status.setText(failureMessage);
        }
    }
}
