#!/bin/bash

# Fetch fake idporten token
TOKEN=$(curl -s https://fakedings.intern.dev.nav.no/fake/idporten)

# Prepare JSON body
JSON_BODY=$(jq -n \
  --arg token "$TOKEN" \
  '{identity_provider: "tokenx", target: "followupplan-backend", user_token: $token}')

# exchange idporten token for tokenx token
curl -s -X POST http://localhost:3000/api/v1/token/exchange \
  -H "Content-Type: application/json" \
  -d "$JSON_BODY" | jq -r '.access_token' | tr -d '\n'
