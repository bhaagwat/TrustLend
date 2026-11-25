package com.trustlend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the QR generator against published reference values from the QR specification and tutorials. */
class QrCodeTest {

    @Test
    void reedSolomonMatchesTheHelloWorldExample() {
        // "HELLO WORLD", version 1-M: the 16 data codewords and their 10 error-correction codewords
        int[] data = {32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17};
        int[] expected = {196, 35, 39, 119, 235, 215, 231, 226, 93, 23};
        byte[] d = new byte[data.length];
        for (int i = 0; i < data.length; i++) d[i] = (byte) data[i];
        byte[] ec = QrCode.rsRemainder(d, QrCode.rsDivisor(10));
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], ec[i] & 0xFF, "codeword " + i);
    }

    @Test
    void formatInformationMatchesTheStandardTable() {
        // level M, masks 0..7 (ISO/IEC 18004 table C.1)
        String[] table = {"101010000010010", "101000100100101", "101111001111100", "101101101001011",
                "100010111111001", "100000011001110", "100111110010111", "100101010100000"};
        for (int m = 0; m < 8; m++) {
            assertEquals(table[m], String.format("%15s", Integer.toBinaryString(QrCode.formatBits(m))).replace(' ', '0'), "mask " + m);
        }
    }

    @Test
    void galoisFieldArithmetic() {
        assertEquals(0, QrCode.gfMultiply(0, 123));
        assertEquals(123, QrCode.gfMultiply(1, 123));
        assertEquals(0x1D, QrCode.gfMultiply(0x80, 2));     // x^8 = x^4 + x^3 + x^2 + 1
    }

    @Test
    void urlFitsAndHasFinderPatterns() {
        QrCode qr = QrCode.encode("http://192.168.100.200:8765/");
        assertEquals(3, qr.version());
        assertEquals(29, qr.size());
        for (int[] corner : new int[][]{{0, 0}, {qr.size() - 7, 0}, {0, qr.size() - 7}}) {
            assertTrue(qr.isDark(corner[0], corner[1]));                // outer ring
            assertFalse(qr.isDark(corner[0] + 1, corner[1] + 1));       // white ring
            assertTrue(qr.isDark(corner[0] + 3, corner[1] + 3));        // centre
        }
        assertTrue(qr.isDark(8, qr.size() - 8));                         // the always-dark module
        assertThrows(IllegalArgumentException.class, () -> QrCode.encode("x".repeat(200)));
    }
}
