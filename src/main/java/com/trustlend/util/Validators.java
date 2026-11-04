package com.trustlend.util;

import com.trustlend.exception.ValidationException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/** Static input-validation helpers used by services (and therefore by the UI forms). */
public final class Validators {

    /** Indian mobile numbers: 10 digits starting with 6-9. */
    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L} .'-]{2,60}$");

    private Validators() { }   // utility class: no instances

    public static String requireText(String field, String value) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, capitalise(field) + " is required");
        }
        return value.trim();
    }

    public static String requireName(String field, String value) throws ValidationException {
        String v = requireText(field, value);
        if (!NAME.matcher(v).matches()) {
            throw new ValidationException(field, capitalise(field) + " should be 2-60 letters");
        }
        return v;
    }

    public static String requirePhone(String value) throws ValidationException {
        String v = requireText("phone", value).replaceAll("[\\s-]", "");
        if (v.startsWith("+91")) v = v.substring(3);
        if (!PHONE.matcher(v).matches()) {
            throw new ValidationException("phone", "Enter a valid 10-digit mobile number");
        }
        return v;
    }

    public static double requirePositive(String field, double value) throws ValidationException {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new ValidationException(field, capitalise(field) + " must be greater than zero");
        }
        return value;
    }

    public static double parseAmount(String field, String text) throws ValidationException {
        String v = requireText(field, text).replace(",", "").replace("₹", "").trim();
        try {
            return requirePositive(field, Double.parseDouble(v));
        } catch (NumberFormatException e) {
            throw new ValidationException(field, capitalise(field) + " must be a number");
        }
    }

    public static int requireRange(String field, int value, int min, int max) throws ValidationException {
        if (value < min || value > max) {
            throw new ValidationException(field, capitalise(field) + " must be between " + min + " and " + max);
        }
        return value;
    }

    public static LocalDate requireNotFuture(String field, LocalDate date) throws ValidationException {
        if (Objects.isNull(date)) {
            throw new ValidationException(field, capitalise(field) + " is required");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException(field, capitalise(field) + " cannot be in the future");
        }
        return date;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
