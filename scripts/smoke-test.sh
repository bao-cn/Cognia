#!/usr/bin/env bash
#
# Cognia API smoke test.
#
# Requires: curl, jq and a running backend (with PostgreSQL/Redis).
# Development mail mode is required so the script can read `devCode`
# (app.mail.expose-code=true, the default in application.yaml).
#
# Usage:
#   ./scripts/smoke-test.sh
#   BASE_URL=http://localhost:8080/api/v1 ADMIN_PASSWORD='...' ./scripts/smoke-test.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
ADMIN_ACCOUNT="${ADMIN_ACCOUNT:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
SUFFIX="$(date +%s)"
BODY_FILE="$(mktemp)"
HEADER_FILE="$(mktemp)"

STATUS=""
ADMIN_TOKEN=""
CREATED_USER_IDS=()
CREATED_ROLE_IDS=()

cleanup() {
  set +e
  if [[ -n "$ADMIN_TOKEN" ]]; then
    for id in "${CREATED_USER_IDS[@]:-}"; do
      [[ -n "$id" ]] && curl -s -o /dev/null -X DELETE "$BASE_URL/users/$id" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
    done
    for id in "${CREATED_ROLE_IDS[@]:-}"; do
      [[ -n "$id" ]] && curl -s -o /dev/null -X DELETE "$BASE_URL/roles/$id" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
    done
  fi
  rm -f "$BODY_FILE" "$HEADER_FILE"
}
trap cleanup EXIT

call() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-sS -o "$BODY_FILE" -D "$HEADER_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  STATUS="$(curl "${args[@]}")"
}

json() {
  jq -r "$1" "$BODY_FILE"
}

expect() {
  local label="$1" expected="$2"
  if [[ "$STATUS" != "$expected" ]]; then
    echo "FAIL: $label (expected HTTP $expected, got $STATUS)"
    cat "$BODY_FILE"
    echo
    exit 1
  fi
  echo "PASS: $label"
}

expect_code() {
  local label="$1" expected="$2"
  local actual
  actual="$(json '.code')"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $label (expected business code $expected, got $actual)"
    cat "$BODY_FILE"
    echo
    exit 1
  fi
  echo "PASS: $label"
}

require_jq() {
  command -v jq >/dev/null 2>&1 || {
    echo "jq is required: https://jqlang.github.io/jq/"
    exit 1
  }
}

require_jq
echo "Cognia smoke test -> $BASE_URL"

call GET "/settings/public"
if [[ "$STATUS" != "200" ]]; then
  echo "FAIL: cannot read public settings (HTTP $STATUS)"
  exit 1
fi
EMAIL_VERIFICATION="$(json '.data.registrationEmailVerification')"
REGISTER_CAPTCHA="$(json '.data.registerEmailCaptchaEnabled')"
LOGIN_CAPTCHA="$(json '.data.loginCaptchaEnabled')"

if [[ "$LOGIN_CAPTCHA" == "true" ]]; then
  echo "SKIP: login human verification is enabled; the smoke test cannot obtain a"
  echo "      single-use Turnstile token for every login. Disable it for this run."
  exit 2
fi
if [[ "$EMAIL_VERIFICATION" == "true" && "$REGISTER_CAPTCHA" == "true" && -z "${TURNSTILE_TOKEN:-}" ]]; then
  echo "SKIP: registration human verification is enabled and email verification is on."
  echo "      Provide TURNSTILE_TOKEN=<valid-widget-token> for this run, or disable"
  echo "      the register captcha / use the Cloudflare test keys."
  exit 2
fi

echo

# ---------------------------------------------------------------- auth basics
call GET "/auth/me"
expect "anonymous request is rejected" 401
expect_code "anonymous error code" 40100

call POST "/auth/login" "" "{\"account\":\"$ADMIN_ACCOUNT\",\"password\":\"$ADMIN_PASSWORD\"}"
expect "admin login" 200
ADMIN_TOKEN="$(json '.data.accessToken')"
ADMIN_ID="$(json '.data.user.id')"
[[ -n "$ADMIN_TOKEN" && "$ADMIN_TOKEN" != "null" ]] || { echo "FAIL: no admin access token"; exit 1; }
echo "PASS: admin token acquired (id=$ADMIN_ID)"

USERNAME="smoke$SUFFIX"
EMAIL="smoke-$SUFFIX@cognia.local"
PASSWORD="Smoke@12345"

if [[ "$EMAIL_VERIFICATION" == "true" ]]; then
  if [[ "$REGISTER_CAPTCHA" == "true" ]]; then
    call POST "/auth/register/code" "" "{\"email\":\"$EMAIL\",\"turnstileToken\":\"$TURNSTILE_TOKEN\"}"
  else
    call POST "/auth/register/code" "" "{\"email\":\"$EMAIL\"}"
  fi
  expect "send registration code" 200
  CODE="$(json '.data.devCode')"
  if [[ -z "$CODE" || "$CODE" == "null" ]]; then
    echo "FAIL: devCode missing. Set app.mail.expose-code=true for this test."
    exit 1
  fi
  echo "PASS: registration code received ($CODE)"

  call POST "/auth/register" "" "{\"username\":\"$USERNAME\",\"nickname\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"code\":\"$CODE\"}"
  expect "register user" 201
  expect_code "register success code" 0
  USER1_ID="$(json '.data.id')"
  CREATED_USER_IDS+=("$USER1_ID")
else
  echo "INFO: registration email verification is disabled; creating the test user via admin API"
  call POST "/users" "$ADMIN_TOKEN" "{\"username\":\"$USERNAME\",\"nickname\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
  expect "create smoke user via admin API" 201
  USER1_ID="$(json '.data.id')"
  CREATED_USER_IDS+=("$USER1_ID")
fi

call POST "/auth/login" "" "{\"account\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect "login with email" 200
USER1_TOKEN="$(json '.data.accessToken')"

call GET "/users" "$USER1_TOKEN"
expect "USER cannot list users" 403
expect_code "USER forbidden code" 40300

call GET "/users/me" "$USER1_TOKEN"
expect "USER can read own profile" 200

call GET "/legal/consent" "$USER1_TOKEN"
expect "new user can read legal consent status" 200
if [[ "$EMAIL_VERIFICATION" == "true" ]]; then
  if [[ "$(json '.data.requiresConsent')" != "false" ]]; then
    echo "FAIL: registration should record initial legal consent"
    exit 1
  fi
  echo "PASS: registration recorded initial legal consent"
else
  echo "PASS: admin-created user has a legal consent status (requiresConsent=$(json '.data.requiresConsent'))"
fi

call GET "/settings" "$USER1_TOKEN"
expect "USER cannot read system settings" 403

call GET "/users?page=1&size=10" "$ADMIN_TOKEN"
expect "ADMIN can list users" 200

call GET "/settings" "$ADMIN_TOKEN"
expect "ADMIN can read system settings" 200

call GET "/settings/mail-templates" "$ADMIN_TOKEN"
expect "ADMIN can list mail templates" 200
[[ "$(json '.data | length')" -ge 2 ]] || { echo "FAIL: default mail templates missing"; exit 1; }
[[ "$(json '[.data[].code] | index("REGISTER_CODE")')" != "null" ]] || { echo "FAIL: REGISTER_CODE template missing"; exit 1; }
echo "PASS: mail templates available"

call GET "/settings/mail-templates" "$USER1_TOKEN"
expect "USER cannot list mail templates" 403

call GET "/settings/public"
expect "public settings endpoint" 200
[[ "$(json '.data.siteName')" != "null" ]] || { echo "FAIL: public site name missing"; exit 1; }

call GET "/settings/downloads/public"
expect "public download assets endpoint" 200
[[ "$(json '.data | length')" -ge 1 ]] || { echo "FAIL: download assets missing"; exit 1; }
echo "PASS: public download assets available"

call GET "/legal/documents"
expect "public legal documents endpoint" 200

# --------------------------------------------------------------- user CRUD
call GET "/roles" "$ADMIN_TOKEN"
expect "ADMIN can list roles" 200
USER_ROLE_ID="$(json '.data[] | select(.code == "USER") | .id')"
[[ -n "$USER_ROLE_ID" ]] || { echo "FAIL: USER role not found"; exit 1; }

USERNAME2="smoke2$SUFFIX"
EMAIL2="smoke2-$SUFFIX@cognia.local"
PASSWORD2="Smoke@12345"

call POST "/users" "$ADMIN_TOKEN" "{\"username\":\"$USERNAME2\",\"nickname\":\"Smoke Two\",\"email\":\"$EMAIL2\",\"password\":\"$PASSWORD2\",\"roleIds\":[\"$USER_ROLE_ID\"]}"
expect "create user" 201
grep -qi '^Location:' "$HEADER_FILE" || { echo "FAIL: create user missing Location header"; exit 1; }
echo "PASS: create user returns Location"
USER2_ID="$(json '.data.id')"
CREATED_USER_IDS+=("$USER2_ID")

call PUT "/users/$USER2_ID" "$ADMIN_TOKEN" '{"nickname":"Smoke Updated","phone":"13800138000"}'
expect "update user" 200

call POST "/auth/login" "" "{\"account\":\"$USERNAME2\",\"password\":\"$PASSWORD2\"}"
expect "user2 login" 200
USER2_TOKEN="$(json '.data.accessToken')"

call PATCH "/users/$USER2_ID/status" "$ADMIN_TOKEN" '{"status":0}'
expect "disable user2" 204

call GET "/users/me" "$USER2_TOKEN"
expect "disabled user token revoked" 401

call PATCH "/users/$USER2_ID/status" "$ADMIN_TOKEN" '{"status":1}'
expect "enable user2" 204

# --------------------------------------------------- role / permission system
call POST "/roles" "$ADMIN_TOKEN" "{\"code\":\"SMOKE_$SUFFIX\",\"name\":\"Smoke Role $SUFFIX\",\"description\":\"smoke test\",\"sort\":99,\"status\":1}"
expect "create role" 201
ROLE_ID="$(json '.data.id')"
CREATED_ROLE_IDS+=("$ROLE_ID")

call GET "/permissions" "$ADMIN_TOKEN"
expect "list permissions" 200
ROLE_READ_ID="$(json '.data[] | select(.code == "role:read") | .id')"
[[ -n "$ROLE_READ_ID" ]] || { echo "FAIL: role:read permission not found"; exit 1; }

call PUT "/roles/$ROLE_ID/permissions" "$ADMIN_TOKEN" "{\"permissionIds\":[\"$ROLE_READ_ID\"]}"
expect "assign permission to role" 200

call PUT "/users/$USER2_ID/roles" "$ADMIN_TOKEN" "{\"roleIds\":[\"$ROLE_ID\"]}"
expect "assign role to user2" 200

call POST "/auth/login" "" "{\"account\":\"$USERNAME2\",\"password\":\"$PASSWORD2\"}"
expect "user2 login after role assignment" 200
USER2_TOKEN="$(json '.data.accessToken')"

call GET "/roles" "$USER2_TOKEN"
expect "user2 can list roles (has role:read)" 200

call GET "/users" "$USER2_TOKEN"
expect "user2 still cannot list users" 403

call PUT "/roles/$ROLE_ID/permissions" "$ADMIN_TOKEN" '{"permissionIds":[]}'
expect "revoke role permissions" 200

call GET "/roles" "$USER2_TOKEN"
expect "permission change revokes user2 session" 401

# ---------------------------------------------------------- refresh / logout
call POST "/auth/login" "" "{\"account\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"rememberMe\":true}"
expect "user1 login remember me" 200
REFRESH_TOKEN="$(json '.data.refreshToken')"
REFRESH_EXPIRES="$(json '.data.refreshExpiresIn')"
[[ "$REFRESH_EXPIRES" -gt 604800 ]] || { echo "FAIL: rememberMe refresh TTL not extended"; exit 1; }

call POST "/auth/refresh" "" "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
expect "refresh token" 200
NEW_ACCESS="$(json '.data.accessToken')"
NEW_REFRESH="$(json '.data.refreshToken')"

call POST "/auth/refresh" "" "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
expect "refresh token reuse rejected" 401
expect_code "refresh reuse code" 40102

call GET "/users/me" "$NEW_ACCESS"
expect "new access token works" 200

call POST "/auth/logout" "$NEW_ACCESS"
expect "logout" 204

call GET "/users/me" "$NEW_ACCESS"
expect "access token invalid after logout" 401

call POST "/auth/refresh" "" "{\"refreshToken\":\"$NEW_REFRESH\"}"
expect "refresh token invalid after logout" 401

# -------------------------------------------------------------------- CORS
CORS_HEADERS="$(curl -sS -o /dev/null -D - -X OPTIONS "$BASE_URL/users" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' | tr -d '\r')"
if grep -qi '^HTTP/.* 200' <<< "$CORS_HEADERS" \
  && grep -qi 'Access-Control-Allow-Origin: http://localhost:5173' <<< "$CORS_HEADERS"; then
  echo "PASS: CORS preflight"
else
  echo "FAIL: CORS preflight"
  echo "$CORS_HEADERS"
  exit 1
fi

# ---------------------------------------------------------------- validation
call POST "/users" "$ADMIN_TOKEN" '{"username":"1","email":"bad","password":"short"}'
expect "invalid payload rejected" 400
expect_code "validation error code" 40000

call GET "/does-not-exist" "$ADMIN_TOKEN"
expect "unknown endpoint returns 404" 404

echo
echo "All smoke tests passed."
