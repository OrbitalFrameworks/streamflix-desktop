package dev.streamflix.desktop;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Headless visual regression guards for honest, title-derived missing-artwork states. */
public final class ArtworkPanelTest {
    public static void main(String[] args) {
        for (int[] size : new int[][] {{1480, 500}, {300, 172}, {150, 100}}) {
            BufferedImage first = render("Breaking Bad", size[0], size[1]);
            BufferedImage repeat = render("Breaking Bad", size[0], size[1]);
            BufferedImage other = render("Inception", size[0], size[1]);
            check(Arrays.equals(pixels(first), pixels(repeat)), "fallback must be deterministic");
            check(!Arrays.equals(pixels(first), pixels(other)), "fallback must derive from the title");

            int brightRight = 0;
            int darkLeft = 0;
            for (int y = size[1] / 6; y < size[1] * 5 / 6; y++) {
                for (int x = 0; x < size[0]; x++) {
                    int rgb = first.getRGB(x, y);
                    int red = (rgb >>> 16) & 255;
                    int green = (rgb >>> 8) & 255;
                    int blue = rgb & 255;
                    if (x >= size[0] * 3 / 5 && Math.max(red, Math.max(green, blue)) > 85)
                        brightRight++;
                    if (x <= size[0] / 5 && Math.max(red, Math.max(green, blue)) < 90)
                        darkLeft++;
                }
            }
            check(brightRight > size[0] * size[1] / 90,
                    "editorial fallback must provide visible image composition at " + size[0]);
            check(darkLeft > size[0] * size[1] / 20,
                    "left text area must remain quiet at " + size[0]);
        }
        System.out.println("ArtworkPanelTest OK");
    }

    private static BufferedImage render(String title, int w, int h) {
        ArtworkPanel panel = new ArtworkPanel(null);
        panel.setEditorialFallback(title);
        panel.setSize(w, h);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
