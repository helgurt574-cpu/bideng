#!/bin/bash
# Paketlenmiş uygulamayı gerçek pencere ve gerçek seslerle uçtan uca dener (her ses + zorlama adımları).
# Kullanım: tools/selftest_package.sh <Bideng çalıştırılabilir dosyası> <belge>...
# Çıktı klasörü olarak geçici bir BIDENG_HOME kullanılır; kullanıcının kütüphanesine dokunulmaz.
set -u
app=$(realpath "$1"); shift
status=0
# macOS'ta timeout yok olabilir
limit() { if command -v timeout >/dev/null; then timeout 600 "$@"; else "$@"; fi; }
for doc in "$@"; do
    doc=$(realpath "$doc")
    home=$(mktemp -d)
    log="$home/selftest.log"
    (cd "$home" && BIDENG_HOME="$home" BIDENG_SELFTEST="$doc" limit "$app" > "$log" 2>&1)
    code=$?
    echo "== $(basename "$doc"): çıkış kodu $code"
    grep "^SELFTEST" "$log" | grep -v "duraklat=true devam=true"
    for crash in "$home"/hs_err*.log; do
        [ -f "$crash" ] || continue
        status=1
        echo "!! çökme kaydı: $crash"
        grep -A3 "Problematic frame" "$crash"
        sed -n '/^Current thread/p;/^Java frames/,/^$/p' "$crash" | head -20
        grep -m3 "VM state\|exiting" "$crash"
    done
    [ $code -ne 0 ] && status=1
done
exit $status
