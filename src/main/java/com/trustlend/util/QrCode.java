package com.trustlend.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A small QR Code generator (ISO/IEC 18004), written for TrustLend's "open on your phone" page.
 * Byte mode, error-correction level M, versions 1–6 (up to 106 bytes — plenty for a URL).
 *
 * <p>Steps: encode the bytes into data codewords, add Reed–Solomon error correction (GF(256),
 * polynomial 0x11D), interleave the blocks, draw the fixed patterns (finders, timing,
 * alignment), place the bits in the zig-zag order, then try all 8 masks and keep the one with
 * the lowest penalty score.
 */
public final class QrCode {

    // per version 1..6, error-correction level M
    private static final int[] TOTAL_CODEWORDS = {0, 26, 44, 70, 100, 134, 172};
    private static final int[] EC_PER_BLOCK = {0, 10, 16, 26, 18, 24, 16};
    private static final int[] BLOCKS = {0, 1, 1, 1, 2, 2, 4};
    private static final int[] ALIGN = {0, 0, 18, 22, 26, 30, 34};   // second alignment coordinate (first is 6)
    private static final int FORMAT_M = 0;                          // ECC level bits: L=1, M=0, Q=3, H=2

    private final int version;
    private final int size;
    private final boolean[][] modules;      // [y][x], true = dark
    private final boolean[][] function;

    private QrCode(int version) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.function = new boolean[size][size];
    }

    /** Encodes text (UTF-8) into a QR code. */
    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= 6 && dataCapacity(version) < data.length + 2) version++;   // +2: mode and length header
        if (version > 6) throw new IllegalArgumentException("Text too long for this QR generator (max 106 bytes)");
        QrCode qr = new QrCode(version);
        qr.drawFunctionPatterns();
        qr.drawCodewords(qr.addErrorCorrection(qr.dataCodewords(data)));
        qr.applyBestMask();
        return qr;
    }

    public int size() {
        return size;
    }

    public int version() {
        return version;
    }

    public boolean isDark(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && modules[y][x];
    }

    static int dataCapacity(int version) {
        return TOTAL_CODEWORDS[version] - EC_PER_BLOCK[version] * BLOCKS[version];
    }

    // ------------------------------------------------------------------ data

    private byte[] dataCodewords(byte[] data) {
        int capacity = dataCapacity(version);
        List<Boolean> bits = new ArrayList<>();
        append(bits, 0b0100, 4);                 // byte mode
        append(bits, data.length, 8);            // character count (8 bits for versions 1-9)
        for (byte b : data) append(bits, b & 0xFF, 8);
        append(bits, 0, Math.min(4, capacity * 8 - bits.size()));   // terminator
        while (bits.size() % 8 != 0) bits.add(false);
        byte[] out = new byte[capacity];
        int n = bits.size() / 8;
        for (int i = 0; i < n; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) v = (v << 1) | (bits.get(i * 8 + j) ? 1 : 0);
            out[i] = (byte) v;
        }
        for (int i = n, pad = 0; i < capacity; i++, pad++) out[i] = (byte) (pad % 2 == 0 ? 0xEC : 0x11);
        return out;
    }

    private static void append(List<Boolean> bits, int value, int length) {
        for (int i = length - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
    }

    /** Splits the data into blocks, adds Reed-Solomon codewords to each, and interleaves them. */
    private byte[] addErrorCorrection(byte[] data) {
        int blocks = BLOCKS[version];
        int ecLen = EC_PER_BLOCK[version];
        int raw = TOTAL_CODEWORDS[version];
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;
        byte[] divisor = rsDivisor(ecLen);
        List<byte[]> all = new ArrayList<>();
        for (int i = 0, k = 0; i < blocks; i++) {
            int dataLen = shortLen - ecLen + (i < shortBlocks ? 0 : 1);
            byte[] dat = java.util.Arrays.copyOfRange(data, k, k + dataLen);
            k += dataLen;
            byte[] ec = rsRemainder(dat, divisor);
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, dat.length);
            if (i < shortBlocks) {
                System.arraycopy(ec, 0, block, dat.length + 1, ec.length);   // leave a gap so columns line up
            } else {
                System.arraycopy(ec, 0, block, dat.length, ec.length);
            }
            all.add(block);
        }
        byte[] result = new byte[raw];
        int n = 0;
        for (int i = 0; i < all.get(0).length; i++) {
            for (int j = 0; j < all.size(); j++) {
                if (i != shortLen - ecLen || j >= shortBlocks) result[n++] = all.get(j)[i];
            }
        }
        return result;
    }

    /** Generator polynomial for {@code degree} error-correction codewords (coefficients high to low, leading 1 omitted). */
    static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) gfMultiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) result[j] ^= result[j + 1];
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    /** Remainder of data(x)·x^n divided by the generator: the error-correction codewords. */
    static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMultiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1 (0x11D). */
    static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // ------------------------------------------------------------------ drawing

    private void set(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {       // timing patterns
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        if (version >= 2) {
            int a = ALIGN[version];
            alignment(a, a);                 // the only one that doesn't touch a finder for versions 2-6
        }
        drawFormatBits(0);                   // reserve the format areas (real bits are drawn after masking)
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void alignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
    }

    /** The 15 format bits for level M and a mask: 5 data bits + 10 BCH bits, XOR 0x5412. */
    static int formatBits(int mask) {
        int data = FORMAT_M << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        return (data << 10 | rem) ^ 0x5412;
    }

    private void drawFormatBits(int mask) {
        int bits = formatBits(mask);
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);              // the always-dark module
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    /** Places the codeword bits in two-module-wide columns, zig-zagging up and down from the right. */
    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;       // skip the vertical timing pattern
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private static boolean maskBit(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (x + y) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (x + y) % 3 == 0;
            case 4 -> (x / 3 + y / 2) % 2 == 0;
            case 5 -> x * y % 2 + x * y % 3 == 0;
            case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
            default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
        };
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y][x] && maskBit(mask, x, y)) modules[y][x] = !modules[y][x];
            }
        }
    }

    private void applyBestMask() {
        int best = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            long p = penalty();
            if (p < bestPenalty) {
                bestPenalty = p;
                best = m;
            }
            applyMask(m);                    // XOR again = undo
        }
        applyMask(best);
        drawFormatBits(best);
    }

    /** Simplified penalty (rules 1, 2 and 4 of the standard): long runs, 2×2 blocks, dark/light balance. */
    private long penalty() {
        long p = 0;
        for (int y = 0; y < size; y++) {
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == modules[y][x - 1]) {
                    run++;
                    if (run == 5) p += 3;
                    else if (run > 5) p++;
                } else run = 1;
            }
        }
        for (int x = 0; x < size; x++) {
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == modules[y - 1][x]) {
                    run++;
                    if (run == 5) p += 3;
                    else if (run > 5) p++;
                } else run = 1;
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) p += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean b : row) if (b) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        p += Math.max(0, k) * 10L;
        return p;
    }
}
