package com.bytewise.ptp.adapters.in;

import com.bytewise.ptp.application.CollectionAgentService;
import com.bytewise.ptp.application.CollectionAgentService.TurnResult;
import com.bytewise.ptp.application.CollectionAgentService.UnknownAccountException;
import com.bytewise.ptp.application.LoanAccounts;
import com.bytewise.ptp.application.PromiseStore;
import com.bytewise.ptp.domain.Language;
import com.bytewise.ptp.domain.LoanAccount;
import com.bytewise.ptp.domain.PhoneNumbers;
import com.bytewise.ptp.domain.PromisePlan;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AgentController {

    /** @param language optional: ENGLISH (default), HINGLISH or MARATHI, for the opening line */
    public record StartRequest(String accountId, Language language) {
    }

    /** @param accountId optional: links a new call to a loan account if /start was not used */
    public record TurnRequest(String message, String accountId) {
    }

    private final CollectionAgentService agent;
    private final PromiseStore store;
    private final LoanAccounts accounts;

    public AgentController(CollectionAgentService agent, PromiseStore store, LoanAccounts accounts) {
        this.agent = agent;
        this.store = store;
        this.accounts = accounts;
    }

    /** An account as a test page sees it. The mobile number is masked, never returned in full. */
    public record AccountView(String accountId, String customerName, BigDecimal outstanding, String mobile,
                              boolean whatsappOptIn) {
        static AccountView of(LoanAccount a) {
            return new AccountView(a.accountId(), a.customerName(), a.outstanding(), PhoneNumbers.mask(a.mobile()),
                    a.whatsappOptIn());
        }
    }

    /** The loan accounts a call can be started on, so a test page can offer a picker. */
    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return accounts.all().stream().map(AccountView::of).toList();
    }

    /** Begin a call on a loan account. The reply is the agent's opening line, quoting the outstanding balance. */
    @PostMapping("/calls/{callId}/start")
    public TurnResult start(@PathVariable String callId, @RequestBody StartRequest request) {
        return agent.startCall(callId, request.accountId(), request.language());
    }

    /** One customer message in, the agent's reply and the call state out. */
    @PostMapping("/calls/{callId}/turn")
    public TurnResult turn(@PathVariable String callId, @RequestBody TurnRequest request) {
        return agent.handleTurn(callId, request.message(), request.accountId());
    }

    /** Promises that passed the gate and were saved. */
    @GetMapping("/promises")
    public Map<String, PromisePlan> promises() {
        return store.all();
    }

    @ExceptionHandler(UnknownAccountException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> unknownAccount(UnknownAccountException e) {
        return Map.of("error", e.getMessage());
    }
}
