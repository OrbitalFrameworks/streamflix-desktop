package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class SportsHubPanel extends JPanel {
    private static final List<String> FILTERS = List.of(
            "Todos", "En vivo", "Favoritos", "Fútbol", "Básquet", "Tenis", "Automovilismo",
            "Combate", "Fútbol americano", "Béisbol", "Hockey", "Vóley", "Cricket", "Rugby", "Otros");
    private static final int REFRESH_MILLIS = 120_000;

    private final SportsDataProvider dataProvider;
    private final Consumer<SportsEvent> playEvent;
    private final Consumer<SportsEvent> chooseSignal;
    private final Runnable openLiveTv;
    private final JPanel body = new JPanel();
    private final JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JLabel sourceNote = Theme.muted(" ");
    private final JButton refreshButton = Theme.button("Actualizar");
    private SportsSnapshot snapshot;
    private String activeFilter = "Todos";
    private String query = "";
    private SwingWorker<SportsSnapshot, Void> worker;
    private final javax.swing.Timer refreshTimer = new javax.swing.Timer(REFRESH_MILLIS, e -> refresh(false));

    SportsHubPanel(SportsDataProvider dataProvider,
            Consumer<SportsEvent> playEvent,
            Consumer<SportsEvent> chooseSignal,
            Runnable openLiveTv) {
        this.dataProvider = dataProvider;
        this.playEvent = playEvent;
        this.chooseSignal = chooseSignal;
        this.openLiveTv = openLiveTv;
        refreshTimer.setRepeats(true);
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        controls.setBorder(new EmptyBorder(0, 34, 14, 34));

        filters.setOpaque(false);
        for (String filter : FILTERS) {
            JButton button = Theme.button(filter);
            Theme.setNavSelected(button, "Todos".equals(filter));
            button.addActionListener(e -> {
                activeFilter = filter;
                updateFilterButtons();
                render();
            });
            filters.add(button);
        }
        controls.add(filters, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        refreshButton.addActionListener(e -> refresh(true));
        sourceNote.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sourceNote.setToolTipText("Fuentes gratuitas de datos deportivos");
        sourceNote.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (snapshot != null && snapshot.sourceName().contains("SportScore")) {
                    try { Desktop.getDesktop().browse(URI.create("https://sportscore.com/")); }
                    catch (Exception ignored) {}
                }
            }
        });
        actions.add(sourceNote);
        actions.add(refreshButton);
        controls.add(actions, BorderLayout.EAST);
        add(controls, BorderLayout.NORTH);

        body.setBackground(Theme.BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(0, 34, 34, 34));

        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setBackground(Theme.BG);
        viewport.add(body, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(viewport);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);
    }

    void activate() {
        refreshTimer.start();
        if (snapshot == null) refresh(true);
    }

    void deactivate() { refreshTimer.stop(); }

    void search(String value) {
        query = value == null ? "" : value.trim();
        render();
    }

    private void refresh(boolean showLoading) {
        if (worker != null && !worker.isDone()) return;
        refreshButton.setEnabled(false);
        if (showLoading || snapshot == null) {
            body.removeAll();
            body.add(stateCard("Cargando deportes", "Consultando eventos en vivo y próximos partidos…"));
            body.revalidate();
            body.repaint();
        } else {
            sourceNote.setText("Actualizando marcadores…");
        }

        worker = new SwingWorker<>() {
            @Override protected SportsSnapshot doInBackground() throws Exception {
                return dataProvider.load();
            }

            @Override protected void done() {
                refreshButton.setEnabled(true);
                try {
                    snapshot = get();
                    sourceNote.setText(snapshot.limitedCoverage()
                            ? "Cobertura gratuita combinada · " + snapshot.sourceName()
                            : snapshot.sourceName());
                    render();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    AppLog.warn("sports", "No se pudo cargar Deportes", cause);
                    body.removeAll();
                    body.add(stateCard("No pudimos cargar Deportes",
                            "Comprueba tu conexión y vuelve a intentarlo. TV en vivo sigue disponible."));
                    body.revalidate();
                    body.repaint();
                }
            }
        };
        worker.execute();
    }

    private void render() {
        if (snapshot == null) return;
        body.removeAll();

        if (snapshot.limitedCoverage()) {
            JPanel notice = Theme.surface();
            notice.setLayout(new BorderLayout(12, 0));
            JLabel copy = Theme.muted("<html><body>Streamflix combina únicamente fuentes gratuitas. "
                    + "La cobertura puede variar por deporte o competición si una fuente externa está temporalmente limitada.</body></html>");
            notice.add(copy, BorderLayout.CENTER);
            body.add(notice);
            body.add(Box.createVerticalStrut(20));
        }

        List<SportsEvent> live = filtered(snapshot.live());
        List<SportsEvent> today = filtered(snapshot.today());
        List<SportsEvent> upcoming = filtered(snapshot.upcoming());

        if ("En vivo".equals(activeFilter)) {
            addSection("EN VIVO AHORA", "Eventos que están ocurriendo en este momento", live, true);
        } else {
            addSection("EN VIVO AHORA", "Eventos que están ocurriendo en este momento", live, true);
            addSection("HOY", "Programación deportiva para hoy", today, false);
            addSection("PRÓXIMOS", "Eventos de los próximos días", upcoming, false);
        }

        if (live.isEmpty() && today.isEmpty() && upcoming.isEmpty()) {
            body.add(stateCard("No hay eventos para este filtro",
                    query.isBlank() ? "Prueba otra disciplina o actualiza la agenda."
                            : "No encontramos coincidencias para “" + query + "”."));
        }

        body.revalidate();
        body.repaint();
    }

    private void addSection(String title, String subtitle, List<SportsEvent> events, boolean live) {
        if (events.isEmpty()) return;
        JLabel heading = Theme.heading(title, 21f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Theme.muted(subtitle);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(heading);
        body.add(Box.createVerticalStrut(3));
        body.add(sub);
        body.add(Box.createVerticalStrut(11));
        for (SportsEvent event : events) {
            JComponent card = eventCard(event, live);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(card);
            body.add(Box.createVerticalStrut(10));
        }
        body.add(Box.createVerticalStrut(16));
    }

    private JComponent eventCard(SportsEvent event, boolean live) {
        JPanel card = Theme.surface();
        card.setLayout(new BorderLayout(18, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 142));
        card.setPreferredSize(new Dimension(880, 136));

        JPanel identity = new JPanel(new BorderLayout(12, 0));
        identity.setOpaque(false);
        identity.setPreferredSize(new Dimension(540, 100));

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 9));
        badges.setOpaque(false);
        badges.add(badge(event.homeBadge(), event.homeTeam()));
        badges.add(badge(event.awayBadge(), event.awayTeam()));
        identity.add(badges, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel eyebrow = Theme.eyebrow(event.sport() + " · " + event.league());
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = Theme.heading(event.hasTeams()
                ? event.homeTeam() + "  vs  " + event.awayTeam() : event.name(), 17f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel timing = Theme.muted(timeLabel(event));
        timing.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(7));
        copy.add(title);
        copy.add(Box.createVerticalStrut(5));
        copy.add(timing);
        identity.add(copy, BorderLayout.CENTER);
        card.add(identity, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(210, 118));

        String score = scoreLabel(event);
        JLabel scoreLabel = Theme.heading(score, live ? 21f : 16f);
        scoreLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(scoreLabel);
        right.add(Box.createVerticalStrut(8));

        boolean hasBroadcasters = !event.broadcasters().isEmpty();
        JButton watch = Theme.primaryButton(hasBroadcasters ? "▶ Ver evento" : "Abrir TV en vivo");
        watch.setAlignmentX(Component.RIGHT_ALIGNMENT);
        watch.setToolTipText(!hasBroadcasters
                ? "Abrir TV en vivo y buscar señales deportivas"
                : "Resolver automáticamente la mejor señal disponible");
        watch.addActionListener(e -> {
            if (!hasBroadcasters) openLiveTv.run();
            else playEvent.accept(event);
        });
        right.add(watch);
        if (hasBroadcasters) {
            right.add(Box.createVerticalStrut(6));
            JButton signals = Theme.button("Señales");
            signals.setAlignmentX(Component.RIGHT_ALIGNMENT);
            signals.addActionListener(e -> chooseSignal.accept(event));
            right.add(signals);
        }
        right.add(Box.createVerticalStrut(6));
        JButton follow = Theme.button(SportsFavorites.matches(event) ? "★ Siguiendo" : "☆ Seguir");
        follow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        follow.addActionListener(e -> showFollowMenu(event, follow));
        right.add(follow);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JComponent badge(String url, String name) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(44, 44));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setToolTipText(name);
        if (url == null || url.isBlank()) {
            label.setText("•");
            label.setForeground(Theme.MUTED_2);
            label.setFont(Theme.FONT_DISPLAY.deriveFont(22f));
        } else {
            ImageLoader.load(url, label, 40, 40);
        }
        return label;
    }

    private List<SportsEvent> filtered(List<SportsEvent> events) {
        String needle = query.toLowerCase(Locale.ROOT);
        ArrayList<SportsEvent> out = new ArrayList<>();
        for (SportsEvent event : events) {
            if (!matchesFilter(event)) continue;
            if (!needle.isBlank() && !searchable(event).contains(needle)) continue;
            out.add(event);
        }
        out.sort(Comparator.comparing(SportsFavorites::matches).reversed()
                .thenComparing(SportsEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private boolean matchesFilter(SportsEvent event) {
        if ("Todos".equals(activeFilter)) return true;
        if ("En vivo".equals(activeFilter)) return true;
        if ("Favoritos".equals(activeFilter)) return SportsFavorites.matches(event);
        if ("Otros".equals(activeFilter)) return FILTERS.stream()
                .filter(filter -> !"Todos".equals(filter) && !"Otros".equals(filter))
                .noneMatch(filter -> filter.equalsIgnoreCase(event.sport()));
        return activeFilter.equalsIgnoreCase(event.sport());
    }

    private static String searchable(SportsEvent event) {
        String broadcasters = event.broadcasters().stream()
                .map(b -> b.channel() + " " + b.country())
                .reduce("", (a, b) -> a + " " + b);
        return String.join(" ", safe(event.sport()), safe(event.league()), safe(event.name()),
                safe(event.homeTeam()), safe(event.awayTeam()), broadcasters)
                .toLowerCase(Locale.ROOT);
    }

    private void updateFilterButtons() {
        for (Component component : filters.getComponents()) {
            if (component instanceof JButton button) {
                Theme.setNavSelected(button, activeFilter.equals(button.getText()));
            }
        }
    }

    private static String scoreLabel(SportsEvent event) {
        if (event.homeScore() != null && event.awayScore() != null) {
            return event.homeScore() + "  —  " + event.awayScore();
        }
        if (event.progress() != null && !event.progress().isBlank()) return event.progress();
        if (event.status() != null && !event.status().isBlank()) return displayStatus(event.status());
        return "Programado";
    }

    private static String displayStatus(String status) {
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "NS" -> "Programado";
            case "FT" -> "Final";
            case "AET", "AOT" -> "Final · prórroga";
            case "PEN" -> "Final · penales";
            case "HT" -> "Descanso";
            case "PST" -> "Postergado";
            case "CANC" -> "Cancelado";
            default -> status;
        };
    }

    private static String timeLabel(SportsEvent event) {
        ArrayList<String> parts = new ArrayList<>();
        if (event.startsAt() != null) {
            ZonedDateTime local = event.startsAt().atZone(ZoneId.systemDefault());
            parts.add(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", new Locale("es", "PE")).format(local));
        }
        if (!event.broadcasters().isEmpty()) parts.add(broadcasterLabel(event.broadcasters()));
        return parts.isEmpty() ? "Horario por confirmar" : String.join("  ·  ", parts);
    }

    private void showFollowMenu(SportsEvent event, Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        if (event.homeTeam() != null) addFollowItem(menu, "team", event.homeTeam(), event.sport());
        if (event.awayTeam() != null) addFollowItem(menu, "team", event.awayTeam(), event.sport());
        if (event.league() != null && !event.league().isBlank()) {
            addFollowItem(menu, "competition", event.league(), event.sport());
        }
        if (menu.getComponentCount() == 0) addFollowItem(menu, "team", event.name(), event.sport());
        menu.show(invoker, 0, invoker.getHeight());
    }

    private void addFollowItem(JPopupMenu menu, String kind, String name, String sport) {
        boolean following = SportsFavorites.contains(kind, name, sport);
        JMenuItem item = new JMenuItem((following ? "Dejar de seguir " : "Seguir ") + name);
        item.addActionListener(e -> {
            SportsFavorites.toggle(kind, name, sport);
            render();
        });
        menu.add(item);
    }

    private static String broadcasterLabel(List<SportsBroadcaster> broadcasters) {
        if (broadcasters == null || broadcasters.isEmpty()) return "sin emisora informada";
        List<String> labels = broadcasters.stream().limit(2).map(b -> b.country().isBlank()
                ? b.channel() : b.channel() + " (" + b.country() + ")").toList();
        String value = String.join(" · ", labels);
        int remaining = broadcasters.size() - labels.size();
        return remaining > 0 ? value + " · +" + remaining : value;
    }

    private static JPanel stateCard(String title, String message) {
        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JLabel heading = Theme.heading(title, 19f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = Theme.muted(message);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(8));
        card.add(body);
        return card;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
