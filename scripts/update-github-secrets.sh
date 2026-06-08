#!/usr/bin/env bash
# Загрузка секретов подписи в GitHub Actions через gh CLI.
# Устанавливает: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS.
#
# Требования: установленный и авторизованный gh (`gh auth status`).
#
# Использование:
#   KEYSTORE_PASSWORD='пароль' ./scripts/update-github-secrets.sh
#
# Переменные окружения:
#   KEYSTORE_FILE=./ankiconnectandroid-release.jks
#   KEYSTORE_PASSWORD=<обязательно>
#   KEY_PASSWORD=<по умолчанию = KEYSTORE_PASSWORD>   (в PKCS12 совпадают)
#   KEY_ALIAS=ankiconnect
#   REPO=<owner/name>   # по умолчанию определяется из origin

set -euo pipefail

KEYSTORE_FILE="${KEYSTORE_FILE:-./ankiconnectandroid-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-ankiconnect}"
KEY_PASSWORD="${KEY_PASSWORD:-${KEYSTORE_PASSWORD:-}}"

command -v gh >/dev/null || { echo "ОШИБКА: gh не найден." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "ОШИБКА: gh не авторизован (gh auth login)." >&2; exit 1; }
[[ -e "$KEYSTORE_FILE" ]] || { echo "ОШИБКА: keystore не найден: $KEYSTORE_FILE" >&2; exit 1; }
[[ -r "$KEYSTORE_FILE" ]] || { echo "ОШИБКА: нет прав на чтение $KEYSTORE_FILE — проверьте владельца/режим (ls -l)." >&2; exit 1; }
[[ -n "${KEYSTORE_PASSWORD:-}" ]] || { echo "ОШИБКА: задайте KEYSTORE_PASSWORD." >&2; exit 1; }

# Определить репозиторий из origin, если REPO не задан.
if [[ -z "${REPO:-}" ]]; then
  url="$(git -C "$(dirname "$0")/.." remote get-url origin 2>/dev/null || true)"
  REPO="$(sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##' <<<"$url")"
fi
[[ -n "$REPO" ]] || { echo "ОШИБКА: не удалось определить REPO; задайте REPO=owner/name." >&2; exit 1; }

echo "Репозиторий: $REPO"

# Проверка, что пароль реально открывает keystore (различаем пароль/прочие ошибки).
if ! err="$(keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" 2>&1 >/dev/null)"; then
  if grep -qiE "password (was )?incorrect|password verification failed|mac (check|verification) failed|integrity" <<<"$err"; then
    echo "ОШИБКА: KEYSTORE_PASSWORD не подходит к $KEYSTORE_FILE." >&2
  else
    echo "ОШИБКА: не удалось открыть keystore: $err" >&2
  fi
  exit 1
fi

set_secret() {  # имя; значение читается из stdin (не светится в ps/логах)
  gh secret set "$1" --repo "$REPO"
}

base64 -w0 "$KEYSTORE_FILE" | set_secret KEYSTORE_BASE64
printf '%s' "$KEYSTORE_PASSWORD"  | set_secret KEYSTORE_PASSWORD
printf '%s' "$KEY_PASSWORD"       | set_secret KEY_PASSWORD
printf '%s' "$KEY_ALIAS"          | set_secret KEY_ALIAS

echo "Готово. Установлены секреты: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS"
gh secret list --repo "$REPO"
