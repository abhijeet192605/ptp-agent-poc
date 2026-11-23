# PTP Agent POC (Spring AI + Ollama)

A collections agent for promise-to-pay (PTP) calls. A small local model (qwen2.5:3b) talks to the
customer and decides when to call tools; plain Java rules check everything before it has any effect.

## Prerequisites
- JDK 21
- Ollama installed, with `qwen2.5:3b` pulled and the Ollama server running (see the next section)

## Start the model (Ollama)

The app talks to a local model through Ollama at `http://localhost:11434`. Start Ollama **before** the app.
If it is not running, anything that needs the model waits 90 seconds and then falls back to fixed
questions, so the agent looks broken even though the app is up.

1. **Install Ollama once** from <https://ollama.com/download/windows>.
2. **Download the model once** (about 2 GB). The app is configured with `pull-model-strategy: never`, so it
   will not download the model for you:

   ```powershell
   ollama pull qwen2.5:3b
   ```

3. **Start the server.** Do either of these:
   - Open **Ollama** from the Start menu. It runs in the system tray.
   - Or run this in its own PowerShell window and leave that window open:

     ```powershell
     ollama serve
     ```

   If `ollama serve` says the address is already in use, Ollama is already running. That is fine.

4. **Check the server is up and the model is installed:**

   ```powershell
   Invoke-RestMethod http://localhost:11434/api/version
   ollama list
   ```

   `ollama list` should show `qwen2.5:3b`. An error such as "Unable to connect to the remote server" means the
   server is not running, so go back to step 3.

5. **Check the model actually answers.** The first call takes about 20 seconds because the model loads into
   memory:

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:11434/api/generate -ContentType "application/json" `
     -Body (@{ model = "qwen2.5:3b"; prompt = "Say ready"; stream = $false; options = @{ num_predict = 6 } } | ConvertTo-Json) |
     Select-Object -ExpandProperty response
   ```

   A short reply such as "ready" means the model is working.

**Troubleshooting**

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Connection refused on port 11434 | Ollama is not running | Start it (step 3) |
| First message after a pause is slow | The model was unloaded after being idle and is loading again | Wait about 20 seconds; later messages are faster |
| Requests hang or time out after the laptop slept | Ollama can stall after sleep | Quit Ollama from the tray (or run `Stop-Process -Name ollama -Force`), then start it again |
| App log shows `model not found` | The model was never pulled | Run `ollama pull qwen2.5:3b` |
| App fails to start with `NoClassDefFoundError` for a class without a package | The editor's own Java compiler also writes to `target/classes` and can leave a half-compiled class | Stop the app, run `.\run.ps1 clean compile` (or delete the `target` folder), and start again |
| Want a different model | | Change `spring.ai.ollama.chat.options.model` in `src/main/resources/application.yml`, pull that model, and restart the app |

The model name, temperature, context size and output cap are all in `application.yml`, never in code.

## Run

In PowerShell, from this folder (with Ollama running):

```powershell
.\run.ps1 test              # unit tests, no Ollama needed
.\run.ps1 spring-boot:run   # start the app on http://localhost:8080
```

If PowerShell blocks the script: `powershell -ExecutionPolicy Bypass -File .\run.ps1 test`

## Try it

**In the browser:** start the app and open <http://localhost:8080/>. Pick a loan account, press *Start call*
(or just type a first message), and chat as the customer. The page has one-click example messages, a call
status chip, a result card with the saved installments, a *Saved promises* tab, and a raw request/response
panel. It is a local test tool with no login; keep it on localhost.

**Voice, in the browser (English).** On the same page, the *Voice* card lets the agent speak and you answer out
loud, with no phone line or provider. Press *Start voice call*: the agent opens the call and speaks, the
microphone then opens, what you say is shown live and sent as your turn, the reply is spoken, and so on until the
call ends. *Pause* stops it for a moment and *Listen now* continues; *Let me check what it heard before sending*
lets you correct a misheard sentence first. *Wait for me to finish* sets how long a silence ends your turn (default
about 2.5 seconds): the microphone stays open while you pause to think, every new word restarts the wait, and the
turn is sent only after that silence. Choose *Patient* or *Very patient* if you pause a lot, *Quick* for a snappier
call. It uses the browser's own speech features, so you need **Edge or
Chrome**, a microphone with permission allowed, and an internet connection (the browser sends your audio to its
speech service). A headset avoids the agent's voice being picked up again. Typing still works at any time.
Limits for now: English only (Hindi and Marathi speech arrives in Devanagari, which the Roman-letter rules cannot
read yet), and number words such as "do hazaar" are not understood. Browsers often write spoken English numbers as
digits ("two thousand" arrives as 2000), so say English numbers, and watch the live text to see what was heard.

Run the end-to-end scenarios (app and Ollama must be running):

```powershell
powershell -ExecutionPolicy Bypass -File .\test-agent.ps1
```

Or talk to the agent yourself, one customer message per request. `callId` is any string; reuse it to
continue the same call, use a new one for a new call:

```powershell
function Turn($id, $m) {
  $b = @{ message = $m } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/calls/$id/turn" `
    -ContentType "application/json; charset=utf-8" -Body $b | ConvertTo-Json -Depth 4
}
Turn "demo1" "Salary aane ke baad dunga."
Turn "demo1" "20 tarikh ko 3000 UPI se dunga."
Invoke-RestMethod http://localhost:8080/promises | ConvertTo-Json -Depth 4
```

More requests are in `requests.http`.

## API

| Endpoint | Purpose |
|---|---|
| `POST /calls/{callId}/start` `{"accountId": "ACC1001", "language": "ENGLISH"}` | Begin a call; the opening line quotes the outstanding balance. `language` is optional (ENGLISH, HINGLISH, MARATHI) |
| `POST /calls/{callId}/turn` `{"message": "...", "accountId": "ACC1001"}` | One customer message in; reply, status and saved promise out. `accountId` is optional and only needed if `/start` was not called |
| `GET /promises` | Promises that passed the gate and were saved |
| `GET /accounts` | The loan accounts a call can be started on (used by the test page) |
| `POST /calls/analyze` `{"transcript": "..."}` | One-shot extraction, no conversation (steps 1-3) |

`status` is `IN_PROGRESS`, `PTP_SAVED`, `ESCALATED` or `KEPT_EXISTING` (the customer confirmed the promise
already on record). Once a call leaves `IN_PROGRESS` it is closed.

A saved promise has one or more installments:

```json
"savedPromise": {
  "installments": [
    {"amount": 5000.00, "date": "2026-10-20", "mode": "CHEQUE"},
    {"amount": 5000.00, "date": "2026-10-22", "mode": "CASH"}
  ],
  "conditions": null
}
```

## How it works

The model proposes, Java decides.

```
customer message
  -> EscalationRules   "manager", "court", "police", ...        -> ESCALATED (model not called)
  -> RefusalRules      2nd refusal / "nahi pata kab dunga"      -> ESCALATED (model not called)
  -> model + tools     recordPromise / escalateToHuman
        recordPromise -> PromiseGate: PromiseGuard, DateResolver, PtpValidator, missing fields
  -> backstop          model did not act: re-read the whole call with the extraction pipeline
  -> ReplyGuard        model free text with digits, dates, waivers or instructions is replaced
  -> fixed replies     confirmation and call endings are worded by code (English/Hinglish/Marathi)
```

A promise is remembered per loan account, not only per call. When a new call starts on an account that
already has a promise still to be kept, the opening line quotes it ("Our records show your earlier promise:
Rs 12500 on 16 Oct 2026. Is that still correct?"). If the customer says they already gave it ("maine already
bata diya in last call") the agent answers from the record instead of asking again; a plain "yes" ends the call
with status `KEPT_EXISTING` and nothing new saved; a new plan replaces the old one, and the newest promise is
the one quoted on the next call. To start clean, stop the app and delete the `data` folder.

**Pay now and payment links.** A customer who says "I will pay now, send me the payment link" (or "abhi pay
karunga, link bhejo") gets today's promise recorded and a payment link sent to the mobile number **on record**
(never a number given on the call; only the last four digits are ever spoken). The link is always for the full
outstanding balance and accepts part payments, so a customer on a 10,000 balance who pays 5,000 is fine: the link
becomes `PARTIALLY_PAID` and the balance drops to 5,000. The promise defaults to the full outstanding; "pay 5000
now" or "aadha abhi" sets a smaller promise. WhatsApp is used where the customer agreed to it (`whatsapp_opt_in`),
otherwise SMS. If the link cannot be made or sent, the promise still stands and the agent says a colleague will
share it. The gateway and the sender are **mocks** (`MockPaymentGateway`, `MockMessageSender`): nothing is really
sent. Swap in real adapters for the `PaymentGateway` and `MessageSender` ports, and see "Before this goes live".

| Endpoint | Purpose |
|---|---|
| `GET /payments` | Every payment link with its status, paid amount, and whether the promise is met |
| `GET /messages` | Audit trail of every message the agent sent (number masked) |
| `POST /payments/{linkId}/mock-payment` `{"amount": 5000}` | Test stand-in for the provider's payment webhook: records a payment and lowers the balance |

Before this goes live: a payment gateway account and webhook signature checks; SMS sender and template
registration (DLT in India); an approved WhatsApp Business template and recorded customer consent; sending only
inside permitted hours; and an idempotency key on link creation so a retry never sends two links.

**Moving a payment on record.** A customer on a new call can change the date of what they already promised, in
their own words, and the agent applies it to the record without asking for the amount again: "my next installment
will be delayed by 2 days, that is on the 23rd" (21 plus 2 days picks the right installment), "I need one more day",
"I will pay next day rather than the 14th", "push the second payment by a week", or simply "I will pay on the 15th"
once the record has been read out. If it is unclear which installment is meant, the agent asks which one; "it is
partially correct" gets "which part needs to change?". The amounts and modes on record never change.

Each call is linked to a loan account, so the agent knows the outstanding balance. Sample accounts
(seeded from `data.sql`): `ACC1001` owes 10000, `ACC1002` owes 25000, `ACC1003` owes 48500.50 (SMS only, no
WhatsApp consent), `ACC1004` owes 8000. Each has a fictional mobile number ending 0001 to 0004. Payments reduce
the balance and are kept across restarts; to reset the balances, stop the app and delete the `data` folder.

Rules that run in code, because the 3B model is unreliable at them:
- Amounts relative to the balance are calculated: "50%", "25 percent", "30 takke", "half / aadha",
  "full / poora / purna", rounded to the paisa. Ignored if the customer also names a rupee figure,
  if the percentage is over 100, or if the call has no account.
- Payment mode (UPI, cash, cheque, bank transfer) is read from the customer's words.
- Split payments are read from one message: "50% on the 20th by cheque and 50% on the 22nd by cash",
  "Friday 1000, next week 2000 and month end 3000". Parts are separated by and / ani / aur / then / commas.
  Each installment gets its own amount, date and mode. A plan is rejected (and the agent asks again) if an
  installment lacks an amount or date, there are more than 6, or the total is above the outstanding balance.
  All installments must be given in one message.
- A model call that stalls or fails never breaks the call: it times out after 90 seconds and the code paths
  take over. A tool call from a timed-out model can no longer change anything.
- Dates the customer states are computed, not asked of the model: weekdays, `5 tarikh`, kal, parso,
  next week (+7 days), month end (the 28th). Delinquent customer, so `kal` is always tomorrow.
- `conditions` is read from the customer's words against a fixed list (salary, wedding, harvest, bonus,
  festival), never from the model.
- A promise needs an amount and a date the customer actually said. Injection text ("loan is waived")
  has neither, so nothing is saved.

## Data

Calls and promises are stored in a file-based H2 database in `./data` and survive a restart.
To reset: stop the app and delete the `data` folder.

## Layout

```
domain/        PtpValidator, DateResolver, PromiseGate, PromiseGuard, ReplyGuard, EscalationRules,
               RefusalRules, ConditionFilter, Language, Replies, CallSession   (no Spring types)
application/   CollectionAgentService, PtpTools (@Tool), PtpExtractionService, ports (PromiseStore, CallRepository)
adapters/in/   AgentController, CallAnalysisController
adapters/out/  JdbcPromiseStore, JdbcCallRepository
```

## Known limits
- The 3B model is weak at tool calling; the backstop covers missed saves. A bigger model (step 6) would
  need less of it.
- `kal` is read as tomorrow, never yesterday. Amounts spoken in words ("teen hazaar") are not matched.
- Language detection is word-list based and works on Roman-script text only.

## Next steps
- Step 5: audit-logging advisor and a larger Hinglish test set
- Step 6: Anthropic profile for model comparison
