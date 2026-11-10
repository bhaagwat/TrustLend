package com.trustlend.io;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The hand-written PNG encoder must produce files that standard decoders (ImageIO) read back exactly. */
class PngWriterTest {

    @Test
    void roundTripsThroughImageIo() throws Exception {
        int[] pixels = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0x80FFFFFF, 0xFF123456, 0x00000000};
        byte[] png = PngWriter.encode(3, 2, pixels);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(3, img.getWidth());
        assertEquals(2, img.getHeight());
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                int expected = pixels[y * 3 + x];
                int actual = img.getRGB(x, y);
                if ((expected >>> 24) == 0) {
                    assertEquals(0, actual >>> 24);          // fully transparent: colour irrelevant
                } else {
                    assertEquals(expected, actual);
                }
            }
        }
    }
}
