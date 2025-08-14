#!/bin/bash
PID=${1:-"13458325183"}
# Fetch fake idporten token
TOKEN=$(curl -s https://fakedings.intern.dev.nav.no/fake/idporten?pid=$PID)
# Prepare JSON body
JSON_BODY=$(jq -n \
  --arg token "$TOKEN" \
  '{identity_provider: "tokenx", target: "syfo-oppfolgingsplan-backend", user_token: $token}')

# exchange idporten token for tokenx token
curl -s -X POST http://localhost:3000/api/v1/token/exchange \
  -H "Content-Type: application/json" \
  -d "$JSON_BODY" | jq -r '.access_token' | tr -d '\n'
