package com.trustlend.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * A tiny PNG encoder (RGBA, 8 bits per channel) written with plain java.io and java.util.zip,
 * so the app can save certificates as images without extra libraries.
 * Format: 8-byte signature, then IHDR, IDAT (zlib-compressed scanlines) and IEND chunks,
 * each followed by a CRC-32.
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngWriter() { }

    /** @param argb pixels row by row, each as 0xAARRGGBB (as JavaFX's PixelReader returns them) */
    public static void write(Path file, int width, int height, int[] argb) throws IOException {
        if (argb.length != width * height) throw new IllegalArgumentException("pixel count does not match size");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(encode(width, height, argb));
        }
    }

    public static byte[] encode(int width, int height, int[] argb) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(SIGNATURE);

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(width);
        h.writeInt(height);
        h.writeByte(8);      // bit depth
        h.writeByte(6);      // colour type: RGBA
        h.writeByte(0);      // compression
        h.writeByte(0);      // filter
        h.writeByte(0);      // no interlace
        chunk(png, "IHDR", ihdr.toByteArray());

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
            byte[] row = new byte[1 + width * 4];
            for (int y = 0; y < height; y++) {
                row[0] = 0;   // filter type "None"
                for (int x = 0; x < width; x++) {
                    int p = argb[y * width + x];
                    int i = 1 + x * 4;
                    row[i] = (byte) (p >> 16);
                    row[i + 1] = (byte) (p >> 8);
                    row[i + 2] = (byte) p;
                    row[i + 3] = (byte) (p >>> 24);
                }
                z.write(row);
            }
        }
        chunk(png, "IDAT", raw.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
