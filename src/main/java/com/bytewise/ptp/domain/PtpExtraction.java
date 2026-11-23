package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the LLM extracts from a collection-call transcript.
 * Plain domain record: no Spring AI types here (hexagonal style).
 */
public record PtpExtraction(
        boolean ptpMade,
        BigDecimal amount,
        LocalDate date,
        String mode,
        String conditions) {
}
