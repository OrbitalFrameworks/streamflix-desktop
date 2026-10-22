package dev.streamflix.desktop;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NavigationStateTest {
    public static void main(String[] args) {
        testReducedMotionProperty();
        testTransitionConstants();
        testSmoothContentHostInstantCardSwitch();
        testTransitionInterruption();
        testSupersededRouteCancellation();
        testDetailNavigationStack();
        testCardTargetMatching();
        System.out.println("NavigationStateTest OK");
    }

    private static void testCardTargetMatching() {
        Models.ShowItem item1 = new Models.ShowItem(
                "item-42", "prov-item", "Test Movie", "overview", "2024", 120, 8.0,
                "http://poster", "http://banner", Models.ShowType.MOVIE, "tmdb");
        LandscapeCard card = new LandscapeCard(item1, it -> {});
        check("item-42".equals(card.item().id()), "LandscapeCard exposes target item ID");

        HeroPanel hero = new HeroPanel(item1, it -> {});
        check("item-42".equals(hero.item().id()), "HeroPanel exposes target item ID");
    }

    private static void testTransitionConstants() {
        check(SmoothContentHost.TRANSITION_DURATION_MS == 140, "Snappy 140ms transition duration required");
        check(SmoothContentHost.FRAME_INTERVAL_MS == 16, "16ms frame interval (~60 fps) required");
    }

    private static void testSupersededRouteCancellation() {
        SmoothContentHost host = new SmoothContentHost();
        host.addCard(new JPanel(), "A");
        host.addCard(new JPanel(), "B");
        host.addCard(new JPanel(), "C");

        host.showCard("A");
        AtomicBoolean bCompleted = new AtomicBoolean(false);
        AtomicBoolean cCompleted = new AtomicBoolean(false);

        // Switch to B, then immediately switch to C before B finishes
        host.showCard("B", () -> bCompleted.set(true));
        host.showCard("C", () -> cCompleted.set(true));

        check("C".equals(host.currentCard()), "Host must be on C");
        check(!bCompleted.get(), "Superseded route B callback must NOT be executed");
        check(cCompleted.get(), "Destination C callback must be executed");
    }

    private static void testReducedMotionProperty() {
        String original = System.getProperty("streamflix.reducedMotion");
        try {
            System.setProperty("streamflix.reducedMotion", "true");
            check(SmoothContentHost.isReducedMotion(), "reduced motion true when property is true");

            System.setProperty("streamflix.reducedMotion", "1");
            check(SmoothContentHost.isReducedMotion(), "reduced motion true when property is 1");

            System.setProperty("streamflix.reducedMotion", "false");
            check(!SmoothContentHost.isReducedMotion(), "reduced motion false when property is false");
        } finally {
            if (original != null) {
                System.setProperty("streamflix.reducedMotion", original);
            } else {
                System.clearProperty("streamflix.reducedMotion");
            }
        }
    }

    private static void testSmoothContentHostInstantCardSwitch() {
        SmoothContentHost host = new SmoothContentHost();
        JPanel p1 = new JPanel();
        JPanel p2 = new JPanel();
        host.addCard(p1, "CARD_1");
        host.addCard(p2, "CARD_2");

        AtomicBoolean completed = new AtomicBoolean(false);
        host.showCard("CARD_1", () -> completed.set(true));
        check(completed.get(), "callback fired for CARD_1");
        check("CARD_1".equals(host.currentCard()), "current card is CARD_1");

        completed.set(false);
        host.showCard("CARD_2", () -> completed.set(true));
        check(completed.get(), "callback fired for CARD_2");
        check("CARD_2".equals(host.currentCard()), "current card is CARD_2");
    }

    private static void testTransitionInterruption() {
        SmoothContentHost host = new SmoothContentHost();
        host.addCard(new JPanel(), "A");
        host.addCard(new JPanel(), "B");
        host.addCard(new JPanel(), "C");

        host.showCard("A");
        host.showCard("B");
        // Interrupting B with C immediately
        host.showCard("C");

        check("C".equals(host.currentCard()), "host safely landed on C after interruption");
        host.stopTransition();
    }

    private static void testDetailNavigationStack() {
        record DetailRoute(String id, String title) {}
        Deque<DetailRoute> stack = new ArrayDeque<>();

        // Opening primary title A
        DetailRoute root = new DetailRoute("title-a", "Title A");

        // User clicks related title B from within Title A
        stack.push(root);

        // User clicks related title C from within Title B
        stack.push(new DetailRoute("title-b", "Title B"));

        check(stack.size() == 2, "2 items in backstack");

        // User clicks "Volver" from C -> returns to B
        DetailRoute back1 = stack.pop();
        check("title-b".equals(back1.id()), "popped to Title B");

        // User clicks "Volver" from B -> returns to A
        DetailRoute back2 = stack.pop();
        check("title-a".equals(back2.id()), "popped to Title A");

        check(stack.isEmpty(), "stack empty after reaching root detail item");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
