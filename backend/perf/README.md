# Perf Test Guide

This directory contains all 1st-phase backend load-test assets.

The comparison baseline should keep these fixed:

- same scenario
- same VU count
- same duration
- same payment path
- same account pool

For pure load comparison, the perf product stock should be large enough that the test does not become a sold-out test.

Default stock is now `1000000`.
Default user count is now `5000`.

## Structure

- `k6/order-flow.js`
  Full scenario: product lookup -> order create -> payment confirm -> order verify
- `k6/stock-check.js`
  Verifies that confirmed payments and remaining stock match
- `seed/apply-perf-seed.ps1`
  Creates perf users and the perf product, then writes `k6/users.csv`
- `seed/generate-access-tokens.ps1`
  Creates `k6/tokens.csv` for pre-authenticated runs
- `seed/reset-perf-state.ps1`
  Deletes old orders and payments for the perf product and resets stock
- `run-k6.ps1`
  Wrapper script that prepares the product when needed and runs k6
- `show-summary.ps1`
  Prints the key numbers from the latest or specified summary JSON

## Default Values

- perf product name: `PERF_TEST_PRODUCT`
- default stock: `1000000`
- default user count: `5000`
- default price: `10000`
- default payment type: `PAYPAL`
- default duration: `60s`

## One-Time Setup

Run this once from `backend`:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\seed\apply-perf-seed.ps1 -UserCount 5000
powershell -ExecutionPolicy Bypass -File .\perf\seed\generate-access-tokens.ps1 -ValidateBaseUrl http://localhost:8080
```

Artifacts:

- `perf/k6/users.csv`
- `perf/k6/tokens.csv`

## Normal Run

From `backend`, run one command per measurement.

Recommended comparison points:

- `100`
- `500`
- `1000`
- `1500`
- `2000`

Recommended stress points after that:

- `3000`
- `5000`

Scenario modes:

- `constant`
  Keeps the requested VU level for the whole duration. Good for baseline comparison.
- `burst`
  Ramps from `0 -> target VUs`, holds, then ramps down. Good for KTX reservation or open-run sale traffic.

100 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 100 -UseTokens -ResetState
```

500 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 500 -UseTokens -ResetState
```

1000 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1000 -UseTokens -ResetState
```

1500 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1500 -UseTokens -ResetState
```

2000 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 2000 -UseTokens -ResetState
```

3000 VU:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 3000 -UseTokens -ResetState
```

Burst example for open-time traffic:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1000 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s
```

`-ResetState` does three things before the run:

- creates the perf product if it does not exist
- deletes old orders and payments for that product
- restores stock to the requested value

`-UseTokens` is strongly recommended. The login API has rate limits, so load tests should not include login requests.
If `Vus` is larger than the rows in `tokens.csv`, `run-k6.ps1` stops immediately and tells you to reseed users and tokens.

`run-k6.ps1` validates the first token against `http://localhost:8080/api/members/me` before starting k6.
If it fails with `401`, your running backend is using a different `JWT_SECRET` than `backend/.env`.

## Stock Quantity

Default stock is `1000000`, but you can override it.

Example:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 100 -UseTokens -ResetState -StockQuantity 2000
```

You can also seed or reset directly with a custom stock:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\seed\apply-perf-seed.ps1 -StockQuantity 1000000 -UserCount 5000
powershell -ExecutionPolicy Bypass -File .\perf\seed\reset-perf-state.ps1 -StockQuantity 1000000
```

Recommended usage:

- pure load comparison: `1000000`
- sold-out behavior check: `2000` or another small value

## Burst Scenario

Use burst mode when you want to simulate many users rushing in at almost the same time.

Example:

- `5s -> 1000 VUs`
- `30s hold at 1000 VUs`
- `5s -> 0 VUs`

Command:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1000 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1500 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 2000 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 3000 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 5000 -UseTokens -ResetState -ScenarioMode burst -BurstRampUp 5s -BurstHold 30s -BurstRampDown 5s -StockQuantity 1000000
```

This is closer to:

- KTX reservation opening time
- limited product drop opening time
- ticketing burst traffic

Keep using the existing constant scenario for 1st-vs-2nd architecture comparison, and use burst as an additional domain-fit scenario.

## See Results

After each run:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1
```

Or point at a specific summary:

```powershell
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1 -SummaryFile .\perf\results\order-flow-100vu-20260311-173000.json
```

Important numbers:

- `httpReqsPerSec`
- `httpErrorRate`
- `completedFlows`
- `completedFlowsPerSec`
- `paymentsConfirmed`
- `orderFlowSuccessRate`
- `httpP95`
- `httpP99`
- `flowP95`
- `flowP99`
- stage failure counters

## Stock Check

After a run, verify stock consistency with the same initial stock value used for that run.

Example for the default stock:

```powershell
docker compose --profile loadtest run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e PRODUCT_ID=1 `
  -e INITIAL_STOCK=1000000 `
  -e ORDER_QUANTITY=1 `
  -e SUMMARY_FILE=/scripts/results/order-flow-2000vu-20260312-090727.json `
  k6 run /scripts/k6/stock-check.js
```

## Recommended Sequence

```powershell
cd backend

powershell -ExecutionPolicy Bypass -File .\perf\seed\apply-perf-seed.ps1 -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\seed\generate-access-tokens.ps1 -ValidateBaseUrl http://localhost:8080

powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 100 -UseTokens -ResetState -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1

powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 500 -UseTokens -ResetState -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1

powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1000 -UseTokens -ResetState -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1

powershell -ExecutionPolicy Bypass -File .\perf\run-k6.ps1 -Vus 1500 -UseTokens -ResetState -StockQuantity 1000000
powershell -ExecutionPolicy Bypass -File .\perf\show-summary.ps1

```
