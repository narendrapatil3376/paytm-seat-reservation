#!/usr/bin/env bash

# Paytm Seat Reservation - concurrent hot-seat burst test
#
# Usage:
#   chmod +x burst-test.sh
#   ./burst-test.sh
#
# Railway:
#   BASE_URL="https://paytm-seat-reservation-production-a9e2.up.railway.app" ./burst-test.sh
#
# Optional:
#   SHOW_ID=1 SEAT_ID=A12 REQUESTS=20 ./burst-test.sh
#
# IMPORTANT:
# This script follows the assignment API contract:
#   POST /shows/{id}/reserve
#
# If your final controller uses a different authentication or request
# contract, update AUTH_HEADER and the JSON body below.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
SHOW_ID="${SHOW_ID:-1}"
SEAT_ID="${SEAT_ID:-A12}"
REQUESTS="${REQUESTS:-20}"

# Put a valid JWT here if the deployed API requires authentication.
# Example:
# AUTH_TOKEN="eyJ..."
AUTH_TOKEN="${AUTH_TOKEN:-}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "=========================================="
echo "Paytm Seat Reservation Burst Test"
echo "=========================================="
echo "BASE_URL : ${BASE_URL}"
echo "SHOW_ID  : ${SHOW_ID}"
echo "SEAT_ID  : ${SEAT_ID}"
echo "REQUESTS : ${REQUESTS}"
echo "=========================================="

for i in $(seq 1 "$REQUESTS"); do
  (
    KEY="burst-${SHOW_ID}-${SEAT_ID}-${i}"

    if [ -n "$AUTH_TOKEN" ]; then
      curl -sS \
        -o "${TMP_DIR}/${i}.body" \
        -w "%{http_code}" \
        -X POST \
        "${BASE_URL}/shows/${SHOW_ID}/reserve" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${AUTH_TOKEN}" \
        -H "Idempotency-Key: ${KEY}" \
        -d "{\"seats\":[\"${SEAT_ID}\"]}" \
        > "${TMP_DIR}/${i}.status" 2>"${TMP_DIR}/${i}.error"
    else
      curl -sS \
        -o "${TMP_DIR}/${i}.body" \
        -w "%{http_code}" \
        -X POST \
        "${BASE_URL}/shows/${SHOW_ID}/reserve" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: ${KEY}" \
        -d "{\"seats\":[\"${SEAT_ID}\"]}" \
        > "${TMP_DIR}/${i}.status" 2>"${TMP_DIR}/${i}.error"
    fi
  ) &
done

wait

echo
echo "========== OUTCOME DISTRIBUTION =========="

count_status() {
  code="$1"
  count=0

  for file in "${TMP_DIR}"/*.status; do
    [ -f "$file" ] || continue
    if grep -qx "$code" "$file"; then
      count=$((count + 1))
    fi
  done

  echo "HTTP ${code}: ${count}"
}

count_status 201
count_status 200
count_status 409

FIVE_XX=0
FOUR_XX=0
OTHER=0
TOTAL=0

for file in "${TMP_DIR}"/*.status; do
  [ -f "$file" ] || continue

  code="$(cat "$file")"
  TOTAL=$((TOTAL + 1))

  case "$code" in
    4??) FOUR_XX=$((FOUR_XX + 1)) ;;
    5??) FIVE_XX=$((FIVE_XX + 1)) ;;
    201|200) ;;
    *) OTHER=$((OTHER + 1)) ;;
  esac
done

echo "------------------------------------------"
echo "Total responses : ${TOTAL}"
echo "4xx responses   : ${FOUR_XX}"
echo "5xx responses   : ${FIVE_XX}"
echo "Other responses: ${OTHER}"
echo "=========================================="

echo
echo "Individual response bodies are available only"
echo "during this execution in: ${TMP_DIR}"

if [ "$FIVE_XX" -gt 0 ]; then
  echo
  echo "FAIL: one or more 5xx responses were returned."
  exit 1
fi

echo
echo "Burst completed without 5xx responses."
echo
echo "Final reconciliation:"
echo "Call:"
echo "  ${BASE_URL}/shows/${SHOW_ID}"
echo
echo "Verify:"
echo "  available + held + confirmed == total_seats"
echo
echo "For a hot-seat storm, the expected result is exactly"
echo "one successful reservation for the target seat and"
echo "domain-level 409 responses for competing users."
