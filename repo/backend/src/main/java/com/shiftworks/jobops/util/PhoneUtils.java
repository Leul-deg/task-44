package com.shiftworks.jobops.util;

import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final Pattern US_PHONE = Pattern.compile("^(?:\\+1[\\s-]?)?(?:\\(?\\d{3}\\)?)[\\s.-]?\\d{3}[\\s.-]?\\d{4}$");

    private PhoneUtils() {}

    public static boolean isValidUsPhone(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return US_PHONE.matcher(value.trim()).matches();
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "***-***-" + digits;
        }
        String last4 = digits.substring(digits.length() - 4);
        return "***-***-" + last4;
    }
}
