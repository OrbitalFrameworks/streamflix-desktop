package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
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
        testRealizedComponentInterruptionCancellation();
        testDetailNavigationStack();
        testCardTargetMatching();
        testHeroCopyWordWrap();
        testPlayerTransitionSuppression();
        System.out.println("NavigationStateTest OK");
    }

    private static void testPlayerTransitionSuppression() {
        SmoothContentHost host = new SmoothContentHost();
        host.setSize(800, 600);

        JPanel detailCard = new JPanel();
        JPanel playerCard = new JPanel();
        host.addCard(detailCard, "DETAIL");
        host.addCard(playerCard, "PLAYER");

        // Initial landing on DETAIL without animation
        host.showCard("DETAIL");
        drainEdt();
        check(!host.isTransitioning(), "DETAIL initial card should not be transitioning");

        // Now enable forceAnimationForTesting to verify suppression logic even when testing animations
        host.setForceAnimationForTesting(true);

        // Transition from DETAIL to PLAYER must NEVER animate (immediate switch to prevent dark frame glitch)
        host.showCard("PLAYER");
        drainEdt();
        check(!host.isTransitioning(), "PLAYER transition must suppress crossfade animation");
        check("PLAYER".equals(host.currentCard()), "Card should immediately be PLAYER");

        // Transition from PLAYER to DETAIL must ALSO NEVER animate (prevent residual black rectangle overlay)
        host.showCard("DETAIL");
        drainEdt();
        check(!host.isTransitioning(), "Restoration from PLAYER must suppress crossfade animation");
        check("DETAIL".equals(host.currentCard()), "Card should immediately be DETAIL");
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

    private static void testHeroCopyWordWrap() {
        JLabel label = new JLabel();
        FontMetrics metrics = label.getFontMetrics(new Font("Dialog", Font.PLAIN, 14));
        String synopsis = "Walter White, un profesor de química con cáncer inoperable, se asocia con un ex alumno.";
        String wrapped = HeroPanel.wrappedHtml(synopsis, metrics, 230, 3);
        check(wrapped.contains("<br>"), "Compact synopsis must wrap at a measured word boundary");
        check(wrapped.contains("Walter White") && wrapped.contains("alumno"),
                "A short synopsis must preserve all meaningful text when wrapping");
        String clipped = HeroPanel.wrappedHtml(synopsis + " ".repeat(2) + synopsis, metrics, 150, 2);
        check(clipped.contains("…"), "Long synopsis truncation must be indicated visibly");
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

        AtomicBoolean bCompleted = new AtomicBoolean(false);
        AtomicBoolean cCompleted = new AtomicBoolean(false);

        // All Swing route transitions belong on the EDT. Keep B -> C in a
        // single event so an invokeLater completion for B cannot run between
        // two off-EDT calls and produce a scheduling-dependent false failure.
        try {
            SwingUtilities.invokeAndWait(() -> {
                host.showCard("A");
                host.showCard("B", () -> bCompleted.set(true));
                host.showCard("C", () -> cCompleted.set(true));
            });
        } catch (Exception e) {
            throw new AssertionError("Could not run navigation fixture on EDT", e);
        }
        drainEdt();
        check("C".equals(host.currentCard()), "Host must be on C");
        check(!bCompleted.get(), "Superseded route B callback must NOT be executed");
        check(cCompleted.get(), "Destination C callback must be executed");
    }

    private static void testRealizedComponentInterruptionCancellation() {
        if (!GraphicsEnvironment.isHeadless()) {
            JFrame frame = new JFrame("Test SmoothContentHost Realized");
            try {
                SmoothContentHost host = new SmoothContentHost();
                host.setSize(300, 200);
                host.setForceAnimationForTesting(true);

                JPanel cardA = new JPanel();
                JPanel cardB = new JPanel();
                JPanel cardC = new JPanel();
                JButton childBtnB = new JButton("Child in B");
                JButton childBtnC = new JButton("Child in C");
                cardB.add(childBtnB);
                cardC.add(childBtnC);

                host.addCard(cardA, "A");
                host.addCard(cardB, "B");
                host.addCard(cardC, "C");

                frame.getContentPane().add(host);
                frame.setSize(300, 200);
                frame.pack();
                frame.setSize(300, 200);
                frame.setVisible(true);
                drainEdt();

                host.showCard("A");
                drainEdt();

                AtomicBoolean bCallbackFired = new AtomicBoolean(false);
                AtomicBoolean cCallbackFired = new AtomicBoolean(false);

                // Sequential switch: A -> B -> C (B is superseded by C)
                host.showCard("B", () -> bCallbackFired.set(true));
                host.showCard("C", () -> cCallbackFired.set(true));

                check(!bCallbackFired.get(), "Superseded callback B must not execute");

                // Dispatch mouse press on descendant button in target card C during active transition
                childBtnC.dispatchEvent(new MouseEvent(
                        childBtnC, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), 0, 10, 10, 1, false));
                drainEdt();

                check(!host.isTransitioning(), "Active transition must be stopped by descendant click");
                check("C".equals(host.currentCard()), "Current card must be C");
                check(cCallbackFired.get(), "Target card C callback must complete upon interruption");
                check(!bCallbackFired.get(), "Superseded callback B must remain unexecuted");

                // Test dynamic child component added after card was added
                JButton dynamicChild = new JButton("Dynamic Child");
                cardC.add(dynamicChild);
                drainEdt();

                AtomicBoolean dCallbackFired = new AtomicBoolean(false);
                host.showCard("A");
                host.stopTransition();
                drainEdt();

                host.showCard("C", () -> dCallbackFired.set(true));
                check(host.isTransitioning(), "Host should be transitioning to C");

                dynamicChild.dispatchEvent(new MouseEvent(
                        dynamicChild, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), 0, 5, 5, 1, false));
                drainEdt();

                check(!host.isTransitioning(), "Active transition must be stopped by dynamic descendant click");
                check(dCallbackFired.get(), "Callback must fire on dynamic descendant interruption");
            } finally {
                frame.dispose();
            }
        }

        testSimulatedDescendantInterruptionCancellation();
    }

    private static void testSimulatedDescendantInterruptionCancellation() {
        SmoothContentHost host = new SmoothContentHost();
        host.setSize(300, 200);
        host.setForceAnimationForTesting(true);

        JPanel cardA = new JPanel();
        JPanel cardB = new JPanel();
        JPanel cardC = new JPanel();
        JPanel nestedContainer = new JPanel();
        JButton descendantButton = new JButton("Descendant Button");
        nestedContainer.add(descendantButton);
        cardC.add(nestedContainer);

        host.addCard(cardA, "A");
        host.addCard(cardB, "B");
        host.addCard(cardC, "C");

        host.showCard("A");
        host.stopTransition();
        drainEdt();
        check(!host.isTransitioning(), "Host must not be transitioning before test starts");

        AtomicBoolean bCompleted = new AtomicBoolean(false);
        AtomicBoolean cCompleted = new AtomicBoolean(false);

        // Rapid sequential navigation: A -> B -> C
        host.showCard("B", () -> bCompleted.set(true));
        check(host.isTransitioning(), "Host should be transitioning to B");

        host.showCard("C", () -> cCompleted.set(true));
        check(host.isTransitioning(), "Host should be transitioning to C");
        check(!bCompleted.get(), "Superseded route B callback must NOT have run");

        // Fire child click on deeply nested descendantButton in C
        descendantButton.dispatchEvent(new MouseEvent(
                descendantButton, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 5, 5, 1, false));
        drainEdt();

        check(!host.isTransitioning(), "Transition must be stopped by descendant mouse click");
        check("C".equals(host.currentCard()), "Host must land on target card C");
        check(cCompleted.get(), "Destination callback C must be executed upon interruption");
        check(!bCompleted.get(), "Superseded route B callback must remain unexecuted");

        // Verify keyboard interaction interruption on descendant
        host.showCard("A");
        host.stopTransition();
        drainEdt();

        AtomicBoolean b2Completed = new AtomicBoolean(false);
        host.showCard("B", () -> b2Completed.set(true));
        check(host.isTransitioning(), "Host should be transitioning to B");

        // Headless components have no keyboard focus. AWT may silently discard
        // dispatchEvent(KeyEvent) before it reaches registered listeners; invoke
        // those listeners explicitly to test the installed descendant hook.
        KeyEvent enter = new KeyEvent(
                cardB, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n');
        check(cardB.getKeyListeners().length > 0, "Keyboard interruption listener must be installed");
        for (KeyListener listener : cardB.getKeyListeners()) {
            listener.keyPressed(enter);
        }
        drainEdt();

        check(!host.isTransitioning(), "Transition must be stopped by key press interaction");
        check(b2Completed.get(), "Destination callback B must be executed upon interruption");

        // Verify descendant mouse release interruption
        host.showCard("A");
        host.stopTransition();
        drainEdt();

        AtomicBoolean b3Completed = new AtomicBoolean(false);
        host.showCard("B", () -> b3Completed.set(true));
        check(host.isTransitioning(), "Host should be transitioning to B");

        descendantButton.dispatchEvent(new MouseEvent(
                descendantButton, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, 5, 5, 1, false));
        drainEdt();

        check(!host.isTransitioning(), "Transition must be stopped by descendant mouse release");
        check(b3Completed.get(), "Destination callback B must execute upon interruption");

        // Verify dynamic child added after card/host was constructed
        JButton dynamicNestedButton = new JButton("Dynamic In Nested");
        nestedContainer.add(dynamicNestedButton);
        drainEdt();

        host.showCard("A");
        host.stopTransition();
        drainEdt();

        AtomicBoolean c2Completed = new AtomicBoolean(false);
        host.showCard("C", () -> c2Completed.set(true));
        check(host.isTransitioning(), "Host should be transitioning to C");

        dynamicNestedButton.dispatchEvent(new MouseEvent(
                dynamicNestedButton, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 2, 2, 1, false));
        drainEdt();

        check(!host.isTransitioning(), "Transition must be stopped by dynamic child in nested container");
        check(c2Completed.get(), "Destination callback C must execute upon interruption");
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
        drainEdt();
        check(completed.get(), "callback fired for CARD_1");
        check("CARD_1".equals(host.currentCard()), "current card is CARD_1");

        completed.set(false);
        host.showCard("CARD_2", () -> completed.set(true));
        drainEdt();
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

    private static void drainEdt() {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                return;
            }
            SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception ignored) {}
    }
}
