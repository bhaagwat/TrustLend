package com.trustlend.util;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.RiskBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilitiesTest {

    @Test
    @DisplayName("Indian mobile numbers are normalised and validated")
    void phoneValidation() throws Exception {
        assertEquals("9876543210", Validators.requirePhone("+91 98765-43210"));
        assertThrows(ValidationException.class, () -> Validators.requirePhone("12345"));
        assertThrows(ValidationException.class, () -> Validators.requirePhone("5876543210"));
    }

    @Test
    @DisplayName("Amounts accept commas and the rupee sign, reject junk")
    void amountParsing() throws Exception {
        assertEquals(12500.0, Validators.parseAmount("amount", "₹12,500"), 1e-9);
        assertThrows(ValidationException.class, () -> Validators.parseAmount("amount", "abc"));
        assertThrows(ValidationException.class, () -> Validators.parseAmount("amount", "-4"));
    }

    @Test
    @DisplayName("Risk bands map from score boundaries")
    void riskBands() {
        assertEquals(RiskBand.EXCELLENT, RiskBand.fromScore(750));
        assertEquals(RiskBand.GOOD, RiskBand.fromScore(749));
        assertEquals(RiskBand.HIGH_RISK, RiskBand.fromScore(300));
    }

    @Test
    @DisplayName("Passwords are salted and verified")
    void passwords() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("secret", salt);
        assertTrue(PasswordHasher.matches("secret", salt, hash));
        assertFalse(PasswordHasher.matches("Secret", salt, hash));
    }

    @Test
    @DisplayName("Indian number grouping for rupee amounts")
    void rupeeFormatting() {
        assertEquals("₹1,25,000", Formats.inr(125000));
    }
}
