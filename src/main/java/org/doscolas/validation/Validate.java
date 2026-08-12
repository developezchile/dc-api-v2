package org.doscolas.validation;

import org.doscolas.exception.ValidationException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Tiny bean-validation stand-in: request DTOs call these while building themselves from JSON. */
public final class Validate {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validate() {
    }

    public static Map<String, String> newErrors() {
        return new LinkedHashMap<>();
    }

    public static void notBlank(Map<String, String> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.put(field, "no debe estar vacío");
        }
    }

    public static void email(Map<String, String> errors, String field, String value) {
        if (value != null && !value.isBlank() && !EMAIL_PATTERN.matcher(value).matches()) {
            errors.put(field, "debe ser una dirección de correo válida");
        }
    }

    public static void minLength(Map<String, String> errors, String field, String value, int min) {
        if (value != null && value.length() < min) {
            errors.put(field, "debe tener al menos " + min + " caracteres");
        }
    }

    public static void maxLength(Map<String, String> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.put(field, "no debe superar los " + max + " caracteres");
        }
    }

    public static void notNull(Map<String, String> errors, String field, Object value) {
        if (value == null) {
            errors.put(field, "es requerido");
        }
    }

    public static void positive(Map<String, String> errors, String field, BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            errors.put(field, "debe ser mayor que 0");
        }
    }

    public static void matches(Map<String, String> errors, String field, String value, Pattern pattern, String message) {
        if (value != null && !value.isBlank() && !pattern.matcher(value).matches()) {
            errors.put(field, message);
        }
    }

    public static void check(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public static String str(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long longVal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through to null
            }
        }
        return null;
    }

    public static Integer intVal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through to null
            }
        }
        return null;
    }

    public static Double doubleVal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through to null
            }
        }
        return null;
    }

    public static Boolean boolVal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value instanceof Boolean b ? b : null;
    }

    public static BigDecimal decimalVal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through to null
            }
        }
        return null;
    }

    public static java.time.LocalDate dateVal(Map<String, Object> json, String key) {
        String value = str(json, key);
        return value == null || value.isBlank() ? null : java.time.LocalDate.parse(value);
    }
}
