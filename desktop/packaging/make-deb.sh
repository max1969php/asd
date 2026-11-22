#!/usr/bin/env bash
# Costruisce asd-desktop_<versione>_amd64.deb dall'immagine dell'app di Compose (Java incluso).
# Uso: make-deb.sh <cartella app-image> <versione> <icona png> <deb di uscita>
# Con dpkg-deb --root-owner-group non serve fakeroot; il control dichiara GStreamer, che ASD usa per suonare.
set -euo pipefail
umask 022

APP="$1" VERSION="$2" ICON="$3" OUT="$4"
NAME=asd-desktop
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
PKG="$WORK/pkg"

mkdir -p "$PKG/opt" "$PKG/usr/bin" "$PKG/usr/share/applications" "$PKG/usr/share/icons/hicolor/256x256/apps" "$PKG/DEBIAN"
cp -a "$APP" "$PKG/opt/$NAME"
chmod -R u+rwX,go+rX,go-w "$PKG/opt/$NAME"
ln -s "/opt/$NAME/bin/$NAME" "$PKG/usr/bin/$NAME"
install -m 644 "$ICON" "$PKG/usr/share/icons/hicolor/256x256/apps/$NAME.png"

cat > "$PKG/usr/share/applications/$NAME.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=ASD Desktop
GenericName=Lettore musicale
Comment=La tua musica, senza abbonamenti
Exec=/opt/$NAME/bin/$NAME
Icon=$NAME
Terminal=false
Categories=AudioVideo;Audio;Player;
Keywords=musica;music;player;mp3;flac;
StartupWMClass=com-asd-desktop-MainKt
EOF

SIZE_KB=$(du -sk "$PKG" | cut -f1)
cat > "$PKG/DEBIAN/control" <<EOF
Package: $NAME
Version: $VERSION
Section: sound
Priority: optional
Architecture: amd64
Installed-Size: $SIZE_KB
Depends: gstreamer1.0-plugins-base, gstreamer1.0-plugins-good, gstreamer1.0-libav
Recommends: zenity
Suggests: yt-dlp
Maintainer: ASD <noreply@users.noreply.github.com>
Homepage: https://asd-eight-rust.vercel.app
Description: ASD Desktop, lettore musicale per la tua musica
 La cartella della musica sfogliata per cartelle, artisti e album, con ricerca,
 mixtape, riproduzione gapless, volume uniforme e ripresa dell'ultimo ascolto.
 Niente account, niente tracciamento: ASD legge i file e non li cancella mai.
 Va in rete solo il TUBO, tramite yt-dlp, quando incolli un link.
EOF

mkdir -p "$(dirname "$OUT")"
dpkg-deb --root-owner-group -Zxz --build "$PKG" "$OUT" >/dev/null
echo "$OUT"
