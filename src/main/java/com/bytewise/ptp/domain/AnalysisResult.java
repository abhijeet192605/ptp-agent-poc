package com.bytewise.ptp.domain;

import java.util.List;

/**
 * Extraction plus the validator's verdict. needsReview=true means a human should check it.
 * followUpQuestion is what the agent should ask next when no complete promise was made, else null.
 */
public record AnalysisResult(PtpExtraction extraction, List<String> issues, boolean needsReview,
                             String followUpQuestion) {
}
