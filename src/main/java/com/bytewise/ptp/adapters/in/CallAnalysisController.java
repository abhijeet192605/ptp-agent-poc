package com.bytewise.ptp.adapters.in;

import com.bytewise.ptp.application.PtpExtractionService;
import com.bytewise.ptp.domain.AnalysisResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calls")
public class CallAnalysisController {

    public record AnalyzeRequest(String transcript) {
    }

    private final PtpExtractionService service;

    public CallAnalysisController(PtpExtractionService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public AnalysisResult analyze(@RequestBody AnalyzeRequest request) {
        return service.analyze(request.transcript());
    }
}
