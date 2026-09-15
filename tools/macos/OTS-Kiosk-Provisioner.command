#!/bin/bash

set -u

PACKAGE_NAME="com.ontimestack.webkiosk"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd -P)"
BUNDLED_APK="${SCRIPT_DIR}/OTS-Kiosk.apk"
ADB_BIN=""
TV_TARGET=""
APK_PATH=""

print_header() {
    clear 2>/dev/null || true
    printf '%s\n' \
        "============================================================" \
        "               OTS Kiosk Provisioner for macOS" \
        "============================================================" \
        "" \
        "Este assistente instala o OTS Kiosk, concede a permissao" \
        "de rotacao integral e abre o aplicativo na TV." \
        ""
}

pause_before_exit() {
    if [ "${OTS_SKIP_PAUSE:-0}" != "1" ] && [ -t 0 ]; then
        printf '\nPressione Enter para fechar...'
        read -r _unused
    fi
}

fail() {
    printf '\nERRO: %s\n' "$1" >&2
    pause_before_exit
    exit 1
}

find_adb() {
    if [ -n "${OTS_ADB_PATH:-}" ] && [ -x "${OTS_ADB_PATH}" ]; then
        ADB_BIN="${OTS_ADB_PATH}"
        return
    fi

    if command -v adb >/dev/null 2>&1; then
        ADB_BIN="$(command -v adb)"
        return
    fi

    for candidate in \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
        "${HOME:-}/Library/Android/sdk/platform-tools/adb"; do
        if [ -x "$candidate" ]; then
            ADB_BIN="$candidate"
            return
        fi
    done

    fail "ADB nao encontrado. Instale o Android SDK Platform-Tools ou Android Studio."
}

validate_target() {
    case "$1" in
        ""|*[$'\t\r\n ']* ) fail "Endereco do dispositivo invalido: $1" ;;
    esac
}

is_connected() {
    [ "$("$ADB_BIN" -s "$1" get-state 2>/dev/null || true)" = "device" ]
}

pair_device_if_requested() {
    local pair_target="${OTS_PAIR_TARGET:-}"
    local should_pair=""

    if [ -z "$pair_target" ] && [ -z "${OTS_TV_TARGET:-}" ]; then
        printf '%s\n' \
            "Se a TV mostra 'Parear dispositivo com codigo', responda S." \
            "Se ela ja foi pareada ou usa a porta 5555, pressione Enter."
        printf '\nParear agora? [s/N]: '
        read -r should_pair
        case "$should_pair" in
            s|S|sim|SIM|Sim)
                printf 'Endereco de pareamento mostrado na TV (IP:porta): '
                read -r pair_target
                ;;
        esac
    fi

    if [ -n "$pair_target" ]; then
        validate_target "$pair_target"
        printf '\nPareando com %s...\n' "$pair_target"
        "$ADB_BIN" pair "$pair_target" ||
            fail "Nao foi possivel parear. Confira o endereco, a porta e o codigo."
    fi
}

choose_target() {
    local target="${OTS_TV_TARGET:-}"

    if [ -z "$target" ]; then
        printf '\nDispositivos ADB visiveis:\n'
        "$ADB_BIN" devices -l
        printf '%s\n' \
            "" \
            "Na TV, abra Depuracao sem fio e use o endereco IP e porta" \
            "de conexao. Em firmwares antigos, normalmente e IP:5555."
        printf '\nEndereco da Xiaomi TV Box (IP:porta): '
        read -r target
    fi

    validate_target "$target"

    if ! is_connected "$target"; then
        printf '\nConectando com %s...\n' "$target"
        "$ADB_BIN" connect "$target" ||
            fail "Falha ao conectar. Confirme a mesma rede e autorize o Mac na TV."
    fi

    is_connected "$target" ||
        fail "A TV nao ficou disponivel no ADB. Aceite a autorizacao exibida nela."

    TV_TARGET="$target"
}

choose_apk() {
    local apk_path="${OTS_APK_PATH:-}"

    if [ -z "$apk_path" ] && [ -f "$BUNDLED_APK" ]; then
        apk_path="$BUNDLED_APK"
    fi

    if [ -z "$apk_path" ] && [ -z "${OTS_TV_TARGET:-}" ]; then
        printf '\nAPK nao encontrado ao lado do provisionador.\n'
        printf 'Informe o caminho do APK ou pressione Enter para usar o app instalado: '
        read -r apk_path
    fi

    APK_PATH="$apk_path"
}

install_apk_if_available() {
    local target="$1"
    local apk_path="$2"
    local install_choice="s"

    if [ -z "$apk_path" ]; then
        return
    fi

    [ -f "$apk_path" ] || fail "APK nao encontrado em: $apk_path"

    if [ -z "${OTS_TV_TARGET:-}" ]; then
        printf '\nAPK encontrado: %s\n' "$(basename -- "$apk_path")"
        printf 'Instalar ou atualizar o OTS Kiosk? [S/n]: '
        read -r install_choice
    fi

    case "$install_choice" in
        n|N|nao|NAO|Nao) return ;;
    esac

    printf '\nInstalando o OTS Kiosk sem apagar configuracoes...\n'
    if ! "$ADB_BIN" -s "$target" install -r "$apk_path"; then
        fail "A instalacao falhou. O provisionador nao desinstala o app nem apaga dados automaticamente."
    fi
}

provision_device() {
    local target="$1"
    local installed_path=""
    local appops_status=""

    installed_path="$("$ADB_BIN" -s "$target" shell pm path "$PACKAGE_NAME" 2>/dev/null || true)"
    [ -n "$installed_path" ] ||
        fail "OTS Kiosk nao esta instalado. Coloque OTS-Kiosk.apk ao lado deste provisionador."

    printf '\nConcedendo acesso para a rotacao integral...\n'
    "$ADB_BIN" -s "$target" shell appops set "$PACKAGE_NAME" WRITE_SETTINGS allow ||
        fail "A Xiaomi TV Box recusou a permissao WRITE_SETTINGS."

    appops_status="$(
        "$ADB_BIN" -s "$target" shell appops get "$PACKAGE_NAME" WRITE_SETTINGS 2>/dev/null || true
    )"
    case "$appops_status" in
        *allow*) ;;
        *) fail "A permissao nao foi confirmada. Resposta: ${appops_status:-vazia}" ;;
    esac

    printf '\nReiniciando o OTS Kiosk...\n'
    "$ADB_BIN" -s "$target" shell am force-stop "$PACKAGE_NAME" ||
        fail "Nao foi possivel fechar a instancia anterior do aplicativo."
    "$ADB_BIN" -s "$target" shell am start -n "$MAIN_ACTIVITY" >/dev/null ||
        fail "A permissao foi concedida, mas o aplicativo nao abriu."

    printf '%s\n' \
        "" \
        "============================================================" \
        "                    PROVISIONAMENTO CONCLUIDO" \
        "============================================================" \
        "" \
        "Dispositivo: $target" \
        "Permissao:   WRITE_SETTINGS allow" \
        "" \
        "Agora abra Settings no OTS Kiosk e selecione novamente" \
        "a orientacao desejada: 0, 90, 180 ou 270 graus." \
        "" \
        "Voce pode desativar a Depuracao sem fio na TV."
}

main() {
    print_header
    find_adb
    printf 'ADB encontrado: %s\n\n' "$ADB_BIN"
    "$ADB_BIN" start-server >/dev/null || fail "Nao foi possivel iniciar o ADB."

    pair_device_if_requested
    choose_target
    choose_apk
    install_apk_if_available "$TV_TARGET" "$APK_PATH"
    provision_device "$TV_TARGET"
    pause_before_exit
}

main "$@"
