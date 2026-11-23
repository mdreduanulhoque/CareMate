package com.carebridge.common;

import java.util.regex.Pattern;

/**
 * Normalizes Bangladeshi mobile numbers to the canonical 11-digit local format (01XXXXXXXXX)
 * so formatting variants like +88017... or 01712-345678 cannot create duplicate accounts.
 */
public final class PhoneNumberNormalizer {

    private static final Pattern BD_LOCAL_MOBILE_PATTERN = Pattern.compile("^01[3-9]\\d{8}$");

    private PhoneNumberNormalizer() {
    }

    public static String normalize(String rawMobileNumber) {
        if (rawMobileNumber == null || rawMobileNumber.isBlank()) {
            throw new BadRequestException("Mobile number is required.");
        }

        String compact = rawMobileNumber.replaceAll("[\\s\\-()]+", "");

        if (compact.startsWith("+880")) {
            compact = "0" + compact.substring(4);
        } else if (compact.startsWith("880")) {
            compact = "0" + compact.substring(3);
        }

        if (!BD_LOCAL_MOBILE_PATTERN.matcher(compact).matches()) {
            throw new BadRequestException(
                    "Please enter a valid Bangladeshi mobile number (e.g., 01712345678)."
            );
        }

        return compact;
    }
}
