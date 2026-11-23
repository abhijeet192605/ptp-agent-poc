package com.bytewise.ptp.domain;

/** The customer's number is never read out or shown in full; only the last four digits are. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** "9876500001" becomes "****0001". */
    public static String mask(String number) {
        if (number == null) {
            return "";
        }
        String digits = number.replaceAll("\\D", "");
        String last4 = digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
        return "****" + last4;
    }

    /** The last four digits only, for a spoken or written reply. */
    public static String last4(String number) {
        String masked = mask(number);
        return masked.length() <= 4 ? "" : masked.substring(4); // drop the four asterisks
    }
}
