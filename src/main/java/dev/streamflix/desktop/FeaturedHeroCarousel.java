package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A fixed-height Home hero: only the visible slide changes, never the page geometry. */
final class FeaturedHeroCarousel extends JLayeredPane {
    static final int HEIGHT = 500;
    static final int ROTATION_MS = 12000;
    private static final int MAX_PER_TYPE = 5;

    private final List<Models.ShowItem> featured;
    private final CardLayout slideLayout = new CardLayout();
    private final JPanel slides = new JPanel(slideLayout);
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
    private final JLabel position = Theme.muted("");
    private final Timer timer;
    private int currentIndex;

    FeaturedHeroCarousel(List<Models.ShowItem> featured, int startIndex,
                         Consumer<Models.ShowItem> onPlay, Consumer<Models.ShowItem> onOpen) {
        if (featured == null || featured.isEmpty()) throw new IllegalArgumentException("No featured titles");
        this.featured = List.copyOf(featured);
        this.currentIndex = Math.floorMod(startIndex, featured.size());
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setPreferredSize(new Dimension(1080, HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
        setOpaque(true);
        setBackground(Theme.BG);

        slides.setOpaque(true);
        slides.setBackground(Theme.BG);
        for (int i = 0; i < featured.size(); i++) {
            // Mount the slides once so returning to an image never re-fetches it.
            slides.add(new HeroPanel(featured.get(i), onPlay, onOpen), String.valueOf(i));
        }
        add(slides, Integer.valueOf(0));

        controls.setOpaque(false);
        if (featured.size() > 1) {
            JButton previous = Theme.button("Anterior");
            previous.setToolTipText("Mostrar destacado anterior");
            previous.addActionListener(e -> step(-1));
            JButton next = Theme.button("Siguiente");
            next.setToolTipText("Mostrar siguiente destacado");
            next.addActionListener(e -> step(1));
            controls.add(previous);
            controls.add(position);
            controls.add(next);
            add(controls, Integer.valueOf(1));
        }

        timer = new Timer(ROTATION_MS, e -> {
            if (isShowing()) step(1);
        });
        timer.setCoalesce(true);
        timer.setRepeats(true);
        showCurrent();
    }

    static List<Models.ShowItem> select(List<Models.ShowItem> movies, List<Models.ShowItem> series) {
        List<Models.ShowItem> pickedMovies = usable(movies);
        List<Models.ShowItem> pickedSeries = usable(series);
        List<Models.ShowItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < MAX_PER_TYPE; i++) {
            addUnique(result, seen, pickedMovies, i);
            addUnique(result, seen, pickedSeries, i);
        }
        // A provider without backdrops can still supply a legitimate poster.
        if (result.isEmpty()) {
            for (int i = 0; i < MAX_PER_TYPE; i++) {
                addUnique(result, seen, movies, i);
                addUnique(result, seen, series, i);
            }
        }
        return List.copyOf(result);
    }

    private static List<Models.ShowItem> usable(List<Models.ShowItem> items) {
        return items.stream().filter(item -> item != null && item.banner() != null
                && !item.banner().isBlank()).limit(MAX_PER_TYPE + 5L).toList();
    }

    private static void addUnique(List<Models.ShowItem> result, Set<String> seen,
                                  List<Models.ShowItem> items, int index) {
        if (index < items.size()) addUnique(result, seen, items.get(index));
    }

    private static void addUnique(List<Models.ShowItem> result, Set<String> seen,
                                  Models.ShowItem item) {
        if (item != null && seen.add(item.type() + ":" + item.id())) result.add(item);
    }

    Models.ShowItem currentItem() { return featured.get(currentIndex); }

    void step(int delta) {
        if (featured.size() < 2) return;
        currentIndex = Math.floorMod(currentIndex + delta, featured.size());
        showCurrent();
        if (isShowing()) timer.restart();
    }

    private void showCurrent() {
        slideLayout.show(slides, String.valueOf(currentIndex));
        position.setText((currentIndex + 1) + " / " + featured.size());
    }

    @Override public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        slides.setBounds(0, 0, width, height);
        if (featured.size() > 1) {
            Dimension nav = controls.getPreferredSize();
            controls.setBounds(Math.max(0, width - nav.width - 24),
                    Math.max(0, height - nav.height - 20), nav.width, nav.height);
        }
    }

    @Override public void addNotify() {
        super.addNotify();
        if (timer != null && featured.size() > 1) timer.start();
    }

    @Override public void removeNotify() {
        if (timer != null) timer.stop();
        super.removeNotify();
    }
}
