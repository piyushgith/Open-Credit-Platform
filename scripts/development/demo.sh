#!/usr/bin/env bash
# End-to-end demo walkthrough against a running instance (default http://localhost:8080).
#
# Requires: curl, jq.
#
# There are two routes to a credit decision, run here on two separate applications so each is
# clearly demonstrated on its own (see docs/architecture/overview.md's "Two decision tracks, one
# lifecycle owner"):
#
#   Track A — the simple loan lifecycle: apply -> submit (runs the deterministic, product-specific
#   LoanProcessingStrategy / FOIR check, which decides synchronously) -> offer -> sanction ->
#   disbursement.
#
#   Track B — financial analysis -> ratios -> risk indicators -> credit scoring -> the Week 7
#   rules-based CreditDecision -> Week 8 maker-checker approval -> (Week 12) the same
#   ApplicationStatus lifecycle Track A uses -> offer -> sanction -> disbursement. This is the
#   my_docs/plan.md §16 master demo scenario: a REFER outcome that needs a checker's sign-off
#   before an Offer becomes possible.
#
# The two tracks don't compose on one application (see the architecture doc), which is the other
# reason this script runs them on separate applications rather than pretending otherwise.
#
# Run from the repository root: ./scripts/development/demo.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

bold() { printf '\n\033[1m%s\033[0m\n' "$1"; }

login() {
  curl -s -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\": \"$1\", \"password\": \"$2\"}" | jq -r '.data.token'
}

auth() { echo "-H Authorization:\ Bearer\ $1"; }

bold "Logging in as the demo admin, maker and checker users (036-seed-demo-users.sql)"
ADMIN_TOKEN=$(login admin admin123)
MAKER_TOKEN=$(login alice.maker maker123)
CHECKER_TOKEN=$(login bob.checker checker123)
echo "admin token acquired: ${ADMIN_TOKEN:0:20}..."

# panNumber is unique per customer (DuplicateCustomerException) and must match Indian PAN format
# (5 letters, 4 digits, 1 letter) — randomize the digit block so re-running this script doesn't
# collide with a previous run's customers. Each track gets its own customer/application: the two
# tracks don't compose on a single application (see this script's header comment), so demonstrating
# both on one would misrepresent what actually happens.
register_customer() {
  local pan="DEMOA$(printf '%04d' $((RANDOM % 10000)))X"
  curl -s -X POST "$BASE_URL/api/customers" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
    -d '{
      "fullName": "'"$1"'",
      "email": "demo-'"$(date +%s)"'-'"$RANDOM"'@example.com",
      "phoneNumber": "9'"$(shuf -i 100000000-999999999 -n1)"'",
      "dateOfBirth": "1995-05-15",
      "panNumber": "'"$pan"'"
    }' | jq -r '.data.id'
}

bold "=== Track A: the simple loan lifecycle ==="

bold "Registering an SME customer"
CUSTOMER_ID=$(register_customer "Piyush Prasad")
echo "customerId: $CUSTOMER_ID"

bold "Applying for a personal loan"
APPLY=$(curl -s -X POST "$BASE_URL/api/loans/apply" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "productType": "PERSONAL",
    "customerId": "'"$CUSTOMER_ID"'",
    "requestedAmount": 500000.00,
    "tenureMonths": 60,
    "monthlyIncome": 120000.00,
    "existingEmi": 15000.00,
    "employmentType": "SALARIED"
  }')
REFERENCE=$(echo "$APPLY" | jq -r '.data.applicationReference')
echo "applicationReference: $REFERENCE (status: $(echo "$APPLY" | jq -r '.data.status'))"

bold "Submitting (runs underwriting: FOIR-based approval)"
SUBMIT=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/submit" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "status: $(echo "$SUBMIT" | jq -r '.data.status'), decision: $(echo "$SUBMIT" | jq -r '.data.decision'), FOIR: $(echo "$SUBMIT" | jq -r '.data.foir')"

bold "Creating and selecting an offer"
OFFER=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/offers" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"tenureMonths": 60}')
OFFER_ID=$(echo "$OFFER" | jq -r '.data.id')
curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/offers/$OFFER_ID/select" -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
echo "offer $OFFER_ID selected"

bold "Sanctioning"
curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/sanction" -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data'

bold "Disbursing in two tranches"
curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/disbursements" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount": 300000.00, "requestReference": "DEMO-TRANCHE-1"}' | jq -c '.data'
curl -s -X POST "$BASE_URL/api/loans/$REFERENCE/disbursements" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount": 200000.00, "requestReference": "DEMO-TRANCHE-2"}' | jq -c '.data'

bold "=== Track B: financial analysis -> scoring -> decision -> maker-checker approval -> offer ==="
echo "A separate application/customer — see this script's header comment for why."

bold "Registering a second SME customer"
CUSTOMER_ID_B=$(register_customer "Anita Verma")
echo "customerId: $CUSTOMER_ID_B"

bold "Applying for a larger personal loan (needs CREDIT_OFFICER authority, not AUTO)"
APPLY_B=$(curl -s -X POST "$BASE_URL/api/loans/apply" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "productType": "PERSONAL",
    "customerId": "'"$CUSTOMER_ID_B"'",
    "requestedAmount": 1500000.00,
    "tenureMonths": 60,
    "monthlyIncome": 500000.00,
    "existingEmi": 0.00,
    "employmentType": "SALARIED"
  }')
REFERENCE_B=$(echo "$APPLY_B" | jq -r '.data.applicationReference')
echo "applicationReference: $REFERENCE_B (status: $(echo "$APPLY_B" | jq -r '.data.status'))"

bold "Submitting a financial statement with weak liquidity (REFER, not DECLINE or auto-APPROVE)"
# Current assets 100,000 against 200,000 current liabilities -> CURRENT_RATIO=0.5, under the SOFT
# 1.0 floor, while every HARD rule (score, DSCR, DEBT_TO_EBITDA) still passes -> REFER, matching
# my_docs/plan.md §16's scenario (a decision that genuinely needs a checker's sign-off).
STATEMENT=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/financial-statements" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "period": { "periodLabel": "FY2023", "periodType": "AUDITED", "startDate": "2022-04-01", "endDate": "2023-03-31" },
    "lineItems": [
      { "lineItemCode": "CURRENT_ASSETS", "value": 100000.00 },
      { "lineItemCode": "NON_CURRENT_ASSETS", "value": 300000.00 },
      { "lineItemCode": "CURRENT_LIABILITIES", "value": 200000.00 },
      { "lineItemCode": "NON_CURRENT_LIABILITIES", "value": 150000.00 },
      { "lineItemCode": "EQUITY", "value": 400000.00 },
      { "lineItemCode": "LONG_TERM_DEBT", "value": 100000.00 },
      { "lineItemCode": "SHORT_TERM_DEBT", "value": 50000.00 },
      { "lineItemCode": "INVENTORY", "value": 100000.00 },
      { "lineItemCode": "REVENUE", "value": 1000000.00 },
      { "lineItemCode": "COST_OF_GOODS_SOLD", "value": 600000.00 },
      { "lineItemCode": "OPERATING_EXPENSES", "value": 150000.00 },
      { "lineItemCode": "DEPRECIATION_AMORTIZATION", "value": 40000.00 },
      { "lineItemCode": "INTEREST_EXPENSE", "value": 20000.00 },
      { "lineItemCode": "TAX_EXPENSE", "value": 30000.00 }
    ]
  }')
STATEMENT_ID=$(echo "$STATEMENT" | jq -r '.data.id')
curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/financial-statements/$STATEMENT_ID/analyze" -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null

# .data is a list of analysis runs for this statement (there's only one here, from the single
# /analyze call above) — not a single object.
ANALYSIS_RUN_ID=$(curl -s "$BASE_URL/api/loans/$REFERENCE_B/financial-statements/$STATEMENT_ID/analysis" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.data[0].id')
echo "analysisRunId: $ANALYSIS_RUN_ID"

bold "Scoring"
SCORE=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/financial-statements/$STATEMENT_ID/analysis-runs/$ANALYSIS_RUN_ID/score" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
SCORE_ID=$(echo "$SCORE" | jq -r '.data.id')
echo "score: $(echo "$SCORE" | jq -r '.data.totalScore'), riskGrade: $(echo "$SCORE" | jq -r '.data.riskGrade')"

bold "Deciding against the active credit policy"
DECISION=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/financial-statements/$STATEMENT_ID/analysis-runs/$ANALYSIS_RUN_ID/score/$SCORE_ID/decision" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
DECISION_ID=$(echo "$DECISION" | jq -r '.data.id')
OUTCOME=$(echo "$DECISION" | jq -r '.data.outcome')
echo "decisionId: $DECISION_ID, outcome: $OUTCOME"

if [ "$OUTCOME" = "DECLINE" ]; then
  # A DECLINE never needs a case (ApprovalService.openCase's rule) — it finalizes the application
  # to DECLINED immediately instead. Financial-data variance across environments could in principle
  # still land here even with the line items above; the branch is handled for that reason.
  bold "Opening an approval case (expected to be rejected — no case needed for a decline)"
  curl -s -X POST "$BASE_URL/api/credit-decisions/$DECISION_ID/approval-case" -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.errors'
  echo "Application finalized straight to DECLINED: $(curl -s "$BASE_URL/api/loans/$REFERENCE_B" -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.data.status')"
else
  bold "Opening an approval case"
  CASE=$(curl -s -X POST "$BASE_URL/api/credit-decisions/$DECISION_ID/approval-case" -H "Authorization: Bearer $ADMIN_TOKEN")
  CASE_ID=$(echo "$CASE" | jq -r '.data.id // empty')

  if [ -z "$CASE_ID" ] || [ "$CASE_ID" = "null" ]; then
    # Resolved authority was AUTO — ApprovalService.openCase already finalized the application to
    # OFFERED as a side effect of this same call (Week 12's wiring); nothing left to approve.
    echo "No case opened — resolved authority was AUTO, application already finalized to OFFERED: $(echo "$CASE" | jq -c '.errors // .data')"
  else
    echo "caseId: $CASE_ID, requiredLevel: $(echo "$CASE" | jq -r '.data.requiredLevel')"

    bold "Maker recommends"
    curl -s -X POST "$BASE_URL/api/approval-cases/$CASE_ID/maker-decision" \
      -H "Authorization: Bearer $MAKER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"outcome": "APPROVE", "comment": "Weak liquidity but everything else clears, demo run"}' | jq -c '.data | {status, decisions}'

    bold "Checker approves — this is what finalizes the application to OFFERED (Week 12's wiring)"
    curl -s -X POST "$BASE_URL/api/approval-cases/$CASE_ID/checker-decision" \
      -H "Authorization: Bearer $CHECKER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"outcome": "APPROVE", "comment": "Concur, demo run"}' | jq -c '.data | {status, decisions}'
  fi

  bold "Confirming the application reached OFFERED"
  curl -s "$BASE_URL/api/loans/$REFERENCE_B" -H "Authorization: Bearer $ADMIN_TOKEN" \
    | jq -c '.data | {status, decision, approvedAmount, interestRate}'

  bold "Creating and selecting an offer (only reachable now that Track B advanced the status)"
  OFFER_B=$(curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/offers" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"tenureMonths": 60}')
  OFFER_ID_B=$(echo "$OFFER_B" | jq -r '.data.id')
  curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/offers/$OFFER_ID_B/select" -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
  echo "offer $OFFER_ID_B selected"

  bold "Sanctioning and disbursing in full"
  curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/sanction" -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data'
  curl -s -X POST "$BASE_URL/api/loans/$REFERENCE_B/disbursements" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
    -d '{"amount": 1500000.00, "requestReference": "DEMO-B-TRANCHE-1"}' | jq -c '.data'
fi

bold "=== Audit history ==="
# GET /api/loans/{reference} never returns LoanApplication's own internal UUID (only its
# human-readable reference number), so a data-change/business-event lookup by
# entityType=LoanApplication isn't reachable from outside the service layer. Offer/CreditDecision/
# ApprovalCase ids are returned to the client, so those are what a real caller can actually look up.
bold "Business events on Track A's offer"
curl -s "$BASE_URL/api/audit/business-events?entityType=Offer&entityId=$OFFER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data[] | {eventType, actorUsername, occurredAt}'

bold "Business events on Track B's credit decision"
curl -s "$BASE_URL/api/audit/business-events?entityType=CreditDecision&entityId=$DECISION_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data[] | {eventType, actorUsername, occurredAt}'

if [ -n "${CASE_ID:-}" ] && [ "$CASE_ID" != "null" ]; then
  bold "Data changes on Track B's approval case"
  curl -s "$BASE_URL/api/audit/data-changes?entityType=ApprovalCase&entityId=$CASE_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data[] | {field, oldValue, newValue, changedBy}'
fi

if [ -n "${OFFER_ID_B:-}" ]; then
  bold "Business events on Track B's offer (only reachable because the checker's approval finalized the application)"
  curl -s "$BASE_URL/api/audit/business-events?entityType=Offer&entityId=$OFFER_ID_B" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data[] | {eventType, actorUsername, occurredAt}'
fi

bold "Done. Track A reference: $REFERENCE, Track B reference: $REFERENCE_B"
