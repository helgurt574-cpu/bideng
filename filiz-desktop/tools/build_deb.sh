#!/usr/bin/env bash
# jpackage'in .deb paketi kurulum sonunda xdg-desktop-menu çalıştırıyor; bu bazı sistemlerde
# "No writable system menu directory found" ile bitip paketi yarım bırakıyor. Bu betik, Compose'un
# ürettiği uygulama klasöründen kurulum betiği gerektirmeyen bir .deb oluşturur:
# menü kaydı, simge ve "bideng" komutu doğrudan pakette; menüyü dpkg tetikleyicileri günceller.
#
# Kullanım: tools/build_deb.sh <app-klasörü (…/app/Bideng)> <sürüm> <çıktı.deb>
set -euo pipefail
umask 022

APP_DIR="$1"
VERSION="$2"
OUT="$3"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ICON="$HERE/src/desktopMain/resources/icons/bideng.png"

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
chmod 755 "$ROOT"

mkdir -p "$ROOT/opt/bideng" "$ROOT/usr/bin" "$ROOT/usr/share/applications" \
  "$ROOT/usr/share/icons/hicolor/512x512/apps" "$ROOT/DEBIAN"
cp -a "$APP_DIR/." "$ROOT/opt/bideng/"
chmod 755 "$ROOT/opt/bideng/bin/Bideng"
ln -s /opt/bideng/bin/Bideng "$ROOT/usr/bin/bideng"
cp "$ICON" "$ROOT/usr/share/icons/hicolor/512x512/apps/bideng.png"

cat > "$ROOT/usr/share/applications/bideng.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Bideng
GenericName=Sesli Belge Okuyucu
Comment=Çevrimdışı sesli belge okuyucu
Exec=/opt/bideng/bin/Bideng %F
Icon=bideng
Terminal=false
Categories=Office;Education;
MimeType=application/pdf;application/epub+zip;application/vnd.openxmlformats-officedocument.wordprocessingml.document;application/msword;application/vnd.oasis.opendocument.text;application/rtf;text/plain;text/html;
StartupWMClass=com-example-filiz-desktop-MainKt
EOF

chmod -R go-w "$ROOT"
SIZE_KB="$(du -sk "$ROOT" | cut -f1)"
cat > "$ROOT/DEBIAN/control" <<EOF
Package: bideng
Version: $VERSION
Section: text
Priority: optional
Architecture: amd64
Maintainer: Bideng <bideng@users.noreply.github.com>
Installed-Size: $SIZE_KB
Depends: libc6, libasound2 | libasound2t64, libx11-6, libxext6, libxi6, libxrender1, libxtst6, libfreetype6, libfontconfig1, zlib1g
Description: Bideng - çevrimdışı sesli belge okuyucu
 PDF, EPUB, Word ve diğer belgeleri Türkçe, Arapça ve Farsça seslerle
 internet bağlantısı olmadan sesli okur. Sesler paketin içindedir.
EOF

dpkg-deb --root-owner-group -Zxz --build "$ROOT" "$OUT" >/dev/null
echo "oluşturuldu: $OUT ($(du -h "$OUT" | cut -f1))"
