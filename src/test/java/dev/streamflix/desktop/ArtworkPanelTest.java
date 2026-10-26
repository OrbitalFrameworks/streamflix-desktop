package dev.streamflix.desktop;

import java.awt.Graphics2D;
import java.awt.Color;
import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Headless visual regression guards for honest, title-derived missing-artwork states. */
public final class ArtworkPanelTest {
    public static void main(String[] args) {
        testPhysicalPixelArtworkVariants();
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

    /** Simulates Windows display scaling without a Windows monitor or networking. */
    private static void testPhysicalPixelArtworkVariants() {
        BufferedImage source = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D sourceGraphics = source.createGraphics();
        try {
            sourceGraphics.setColor(Color.BLACK);
            sourceGraphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            sourceGraphics.setColor(Color.WHITE);
            for (int i = 0; i < source.getWidth(); i += 2) {
                sourceGraphics.fillRect(i, 0, 1, source.getHeight());
            }
        } finally {
            sourceGraphics.dispose();
        }

        ArtworkPanel panel = new ArtworkPanel(null);
        panel.acceptImage(source);
        panel.setSize(350, 198);
        BufferedImage previous = null;
        for (double scale : new double[] {1.0, 1.25, 1.5, 2.0, 1.0}) {
            int physicalWidth = (int) Math.ceil(350 * scale);
            int physicalHeight = (int) Math.ceil(198 * scale);
            BufferedImage display = new BufferedImage(physicalWidth, physicalHeight,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = display.createGraphics();
            try {
                g.scale(scale, scale);
                panel.paint(g);
            } finally {
                g.dispose();
            }
            BufferedImage variant = cachedArtwork(panel);
            check(variant != null, "Loaded image must render");
            check(variant.getWidth() == physicalWidth && variant.getHeight() == physicalHeight,
                    "HiDPI artwork must be cached at physical pixels for scale " + scale);
            check((display.getRGB(physicalWidth / 2, physicalHeight / 2) & 0xffffff) != 0,
                    "HiDPI artwork must be visible at scale " + scale);

            // Multiple paints with the same viewport should reuse the image,
            // rather than recalculating bicubic scaling during every scroll.
            BufferedImage repeat = new BufferedImage(physicalWidth, physicalHeight,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D repeatGraphics = repeat.createGraphics();
            try {
                repeatGraphics.scale(scale, scale);
                panel.paint(repeatGraphics);
            } finally {
                repeatGraphics.dispose();
            }
            check(cachedArtwork(panel) == variant,
                    "Image variant must be reused at scale " + scale);
            if (previous != null && previous.getWidth() != physicalWidth) {
                check(previous != variant, "Changing display scale must invalidate the previous variant");
            }
            previous = variant;
        }

        panel.acceptImage(new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB));
        check(cachedArtwork(panel) == null, "New source must invalidate the scaled image");
    }

    private static BufferedImage cachedArtwork(ArtworkPanel panel) {
        try {
            Field field = ArtworkPanel.class.getDeclaredField("scaled");
            field.setAccessible(true);
            return (BufferedImage) field.get(panel);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot examine the artwork cache", exception);
        }
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
