#!/usr/bin/env bash
# Ротация ПАРОЛЯ существующего keystore с СОХРАНЕНИЕМ ключа подписи.
# В отличие от generate-keystore.sh, подпись APK не меняется —
# обновление установленных приложений "поверх" продолжит работать.
#
# Для PKCS12 пароль один (store == key), меняется через -storepasswd.
#
# Использование:
#   OLD_PASSWORD='старый' ./scripts/rotate-keystore-password.sh           # новый пароль случайный
#   OLD_PASSWORD='старый' NEW_PASSWORD='новый' ./scripts/rotate-keystore-password.sh
#
# Переменные окружения:
#   KEYSTORE_FILE=./ankiconnectandroid-release.jks
#   OLD_PASSWORD=<обязательно>
#   NEW_PASSWORD=<по умолчанию случайный>

set -euo pipefail

KEYSTORE_FILE="${KEYSTORE_FILE:-./ankiconnectandroid-release.jks}"

command -v keytool >/dev/null || { echo "ОШИБКА: keytool не найден (нужен JDK)." >&2; exit 1; }
[[ -e "$KEYSTORE_FILE" ]] || { echo "ОШИБКА: keystore не найден: $KEYSTORE_FILE" >&2; exit 1; }
[[ -r "$KEYSTORE_FILE" && -w "$KEYSTORE_FILE" ]] || { echo "ОШИБКА: нет прав на чтение/запись $KEYSTORE_FILE — проверьте владельца/режим (ls -l)." >&2; exit 1; }
[[ -n "${OLD_PASSWORD:-}" ]] || { echo "ОШИБКА: задайте OLD_PASSWORD." >&2; exit 1; }

# Проверка старого пароля (различаем пароль/прочие ошибки).
if ! err="$(keytool -list -keystore "$KEYSTORE_FILE" -storepass "$OLD_PASSWORD" 2>&1 >/dev/null)"; then
  if grep -qiE "password (was )?incorrect|password verification failed|mac (check|verification) failed|integrity" <<<"$err"; then
    echo "ОШИБКА: OLD_PASSWORD не подходит к $KEYSTORE_FILE." >&2
  else
    echo "ОШИБКА: не удалось открыть keystore: $err" >&2
  fi
  exit 1
fi

if [[ -z "${NEW_PASSWORD:-}" ]]; then
  command -v openssl >/dev/null || { echo "ОШИБКА: нет openssl; задайте NEW_PASSWORD." >&2; exit 1; }
  NEW_PASSWORD="$(openssl rand -base64 24)"
  GENERATED=1
fi

# Бэкап на случай сбоя.
cp -p "$KEYSTORE_FILE" "$KEYSTORE_FILE.bak"

keytool -storepasswd \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$OLD_PASSWORD" \
  -new "$NEW_PASSWORD" >&2

# Проверка нового пароля; при успехе убрать бэкап.
if keytool -list -keystore "$KEYSTORE_FILE" -storepass "$NEW_PASSWORD" >/dev/null 2>&1; then
  rm -f "$KEYSTORE_FILE.bak"
else
  mv -f "$KEYSTORE_FILE.bak" "$KEYSTORE_FILE"
  echo "ОШИБКА: смена пароля не подтвердилась, keystore восстановлен из бэкапа." >&2
  exit 1
fi

echo "================ НОВЫЙ ПАРОЛЬ (ключ тот же) ================"
echo "KEYSTORE_PASSWORD = $NEW_PASSWORD"
echo "KEY_PASSWORD      = $NEW_PASSWORD   (то же — PKCS12)"
[[ "${GENERATED:-0}" == "1" ]] && echo "(сгенерирован случайно — сохраните!)"
echo "==========================================================="
echo
echo "Обновить секреты в репозитории:"
echo "  KEYSTORE_PASSWORD='$NEW_PASSWORD' ./scripts/update-github-secrets.sh"
