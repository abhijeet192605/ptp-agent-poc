package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DateResolverTest {

    private final LocalDate today = LocalDate.of(2026, 10, 13); // Tuesday

    @Test
    void nextFriday() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 16)),
                DateResolver.resolve("Agle Friday tak 3000 de dunga.", today));
    }

    @Test
    void sameWeekdayAsTodayMeansNextWeek() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 20)),
                DateResolver.resolve("Tuesday ko dunga", today));
    }

    @Test
    void dayAfterTomorrowIsOneDateNotTwo() {
        // Real test message: "tomorrow" inside "day after tomorrow" made it ambiguous, so the model's guess was used
        assertEquals(Optional.of(today.plusDays(2)),
                DateResolver.resolve("I will pay 50% amount by day after tomorrow.", today));
        assertEquals(Optional.of(today.plusDays(1)), DateResolver.resolve("I will pay tomorrow", today));
        // genuinely two different dates are still ambiguous
        assertEquals(Optional.empty(), DateResolver.resolve("tomorrow or the day after tomorrow", today));
    }

    @Test
    void aDayFollowedByKoLaOrTakIsTheDayOfTheMonth() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 25)), DateResolver.resolve("5000 rupaye 25 ko dunga", today));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 25)), DateResolver.resolve("25 la denar", today));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 25)), DateResolver.resolve("25 tak de dunga", today));
        // a four-digit amount followed by "ko" is not a day
        assertEquals(Optional.empty(), DateResolver.resolve("4500 ko dunga", today));
    }

    @Test
    void marathiDayAlreadyPassedRollsToNextMonth() {
        assertEquals(Optional.of(LocalDate.of(2026, 11, 5)),
                DateResolver.resolve("Saheb, pagar 5 tarkhela honar, tevha 2500 rupaye cash deto.", today));
    }

    @Test
    void dayLaterThisMonth() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 20)),
                DateResolver.resolve("20 tarikh ko 4500 dunga", today));
    }

    @Test
    void dayMissingInNextMonthSkipsAhead() {
        // 31 Oct passed in a 30-day context: from 1 Nov, 31 Nov doesn't exist -> 31 Dec
        assertEquals(Optional.of(LocalDate.of(2026, 12, 31)),
                DateResolver.resolve("31 tarikh ko dunga", LocalDate.of(2026, 11, 1)));
    }

    @Test
    void todayAndTomorrow() {
        assertEquals(Optional.of(today), DateResolver.resolve("Aaj 1000 UPI kar dunga", today));
        assertEquals(Optional.of(today.plusDays(1)), DateResolver.resolve("Tomorrow I will pay", today));
    }

    @Test
    void kalIsTomorrowAndParsoIsDayAfter() {
        assertEquals(Optional.of(today.plusDays(1)), DateResolver.resolve("Kal 2000 de dunga", today));
        assertEquals(Optional.of(today.plusDays(2)), DateResolver.resolve("Parso 2000 de dunga", today));
    }

    @Test
    void nextWeekIsSevenDaysOrNextMondayWhicheverIsLater() {
        // Tue 13 Oct: +7 = Tue 20 Oct, next Monday = 19 Oct -> 20 Oct
        assertEquals(Optional.of(LocalDate.of(2026, 10, 20)), DateResolver.resolve("Agle hafte dunga 2000", today));
        // Sunday 11 Oct: +7 = 18 Oct, next Monday = 12 Oct -> 18 Oct
        assertEquals(Optional.of(LocalDate.of(2026, 10, 18)),
                DateResolver.resolve("next week pay karunga", LocalDate.of(2026, 10, 11)));
    }

    @Test
    void monthEndIsThe28th() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 28)), DateResolver.resolve("Month end tak 5000 dunga", today));
        // already past the 28th -> next month
        assertEquals(Optional.of(LocalDate.of(2026, 11, 28)),
                DateResolver.resolve("Month end tak 5000 dunga", LocalDate.of(2026, 10, 30)));
    }

    @Test
    void noClueOrAmbiguousReturnsEmpty() {
        assertEquals(Optional.empty(), DateResolver.resolve("Dekhta hoon, try karunga.", today));
        assertEquals(Optional.empty(), DateResolver.resolve("Friday ya 20 tarikh ko dunga", today));
    }

    @Test
    void applyToOverridesWrongModelDate() {
        var wrong = new PtpExtraction(true, new BigDecimal("3000"), LocalDate.of(2026, 10, 15), null, null);
        var fixed = DateResolver.applyTo(wrong, "Agle Friday tak 3000 de dunga.", today);
        assertEquals(LocalDate.of(2026, 10, 16), fixed.date());
        assertEquals(new BigDecimal("3000"), fixed.amount());
    }

    @Test
    void applyToLeavesNonPromiseAlone() {
        var none = new PtpExtraction(false, null, null, null, null);
        assertSame(none, DateResolver.applyTo(none, "Friday dekhta hoon", today));
    }
}
