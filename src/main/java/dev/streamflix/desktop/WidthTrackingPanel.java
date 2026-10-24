package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;

/** Vertical-scroll content follows the actual viewport width, not a child's preferred width. */
final class WidthTrackingPanel extends JPanel implements Scrollable {
    WidthTrackingPanel(LayoutManager layout) {
        super(layout);
    }

    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 28;
    }

    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(32, visibleRect.height - 72);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
