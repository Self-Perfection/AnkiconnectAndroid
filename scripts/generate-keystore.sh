#!/usr/bin/env bash
# Генерация release-keystore для подписи APK (PKCS12, RSA-2048).
# Создаёт keystore "с нуля" — НОВЫЙ ключ подписи.
#
# ВНИМАНИЕ: новый ключ = новая подпись. Обновление уже установленного APK
# "поверх" сломается (INSTALL_FAILED_UPDATE_INCOMPATIBLE) — потребуется
# удалить и поставить заново. Если нужно лишь СМЕНИТЬ ПАРОЛЬ, сохранив ключ,
# используйте rotate-keystore-password.sh — это не ломает обновления.
#
# Файл *.jks игнорируется .gitignore и НЕ должен попадать в репозиторий.
#
# Использование:
#   ./scripts/generate-keystore.sh                 # пароль сгенерируется случайно
#   KEYSTORE_PASSWORD='мой-пароль' ./scripts/generate-keystore.sh
#   FORCE=1 ./scripts/generate-keystore.sh         # перезаписать существующий файл
#
# Переменные окружения (все опциональны, показаны значения по умолчанию):
#   KEYSTORE_FILE=./ankiconnectandroid-release.jks
#   KEY_ALIAS=ankiconnect
#   VALIDITY_DAYS=10000
#   KEYSTORE_PASSWORD=<случайный, если не задан>   # в PKCS12 пароль ключа == паролю стора
#   DNAME="CN=AnkiConnectAndroid, OU=AnkiConnectAndroid, O=AnkiConnectAndroid, C=US"

set -euo pipefail

KEYSTORE_FILE="${KEYSTORE_FILE:-./ankiconnectandroid-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-ankiconnect}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"
DNAME="${DNAME:-CN=AnkiConnectAndroid, OU=AnkiConnectAndroid, O=AnkiConnectAndroid, C=US}"

command -v keytool >/dev/null || { echo "ОШИБКА: keytool не найден (нужен JDK)." >&2; exit 1; }

if [[ -e "$KEYSTORE_FILE" && "${FORCE:-0}" != "1" ]]; then
  echo "ОШИБКА: $KEYSTORE_FILE уже существует. Запустите с FORCE=1, чтобы перезаписать." >&2
  exit 1
fi

# Пароль: из окружения либо случайный.
if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
  command -v openssl >/dev/null || { echo "ОШИБКА: нет openssl для генерации пароля; задайте KEYSTORE_PASSWORD." >&2; exit 1; }
  KEYSTORE_PASSWORD="$(openssl rand -base64 24)"
  GENERATED=1
fi

rm -f "$KEYSTORE_FILE"
keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storetype PKCS12 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEYSTORE_PASSWORD" \
  -dname "$DNAME" >&2

chmod 600 "$KEYSTORE_FILE"

echo "================ ЗНАЧЕНИЯ ДЛЯ GITHUB SECRETS ================"
echo "KEY_ALIAS         = $KEY_ALIAS"
echo "KEYSTORE_PASSWORD = $KEYSTORE_PASSWORD"
echo "KEY_PASSWORD      = $KEYSTORE_PASSWORD   (то же — PKCS12)"
echo "Файл keystore     : $KEYSTORE_FILE  (НЕ коммитить!)"
[[ "${GENERATED:-0}" == "1" ]] && echo "(пароль сгенерирован случайно — сохраните его!)"
echo "============================================================"
echo
echo "SHA-256 ключа подписи:"
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" 2>/dev/null | grep "SHA256:" | head -1
echo
echo "Дальше: загрузить секреты в репозиторий —"
echo "  KEYSTORE_FILE='$KEYSTORE_FILE' KEYSTORE_PASSWORD='<пароль>' KEY_ALIAS='$KEY_ALIAS' ./scripts/update-github-secrets.sh"
