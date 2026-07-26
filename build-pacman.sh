#!/bin/bash
set -e

cd "$(dirname "$0")"

# Build the JAR first
echo "Building JAR..."
mvn package -q -DskipTests

VERSION="1.0.0"
NAME="convertor-tool"
JAR="target/${NAME}-${VERSION}.jar"

# Create a temp directory with the package layout
TMP_DIR=$(mktemp -d)
PKG_DIR="${TMP_DIR}/${NAME}-${VERSION}"

echo "Setting up pacman package tree..."

mkdir -p "$PKG_DIR/usr/bin"
mkdir -p "$PKG_DIR/usr/share/${NAME}"
mkdir -p "$PKG_DIR/usr/share/applications"
mkdir -p "$PKG_DIR/usr/share/icons/hicolor/scalable/apps"
mkdir -p "$PKG_DIR/usr/share/man/man1"
mkdir -p "$PKG_DIR/usr/share/doc/${NAME}"

# Copy JAR
install -m 644 "$JAR" "$PKG_DIR/usr/share/${NAME}/${NAME}.jar"

# Copy launcher script
install -m 755 "debian-pkg/usr/bin/convertor-tool" "$PKG_DIR/usr/bin/convertor-tool"

# Copy desktop entry
install -m 644 "debian-pkg/usr/share/applications/convertor-tool.desktop" \
    "$PKG_DIR/usr/share/applications/${NAME}.desktop"

# Copy icon
install -m 644 "debian-pkg/usr/share/icons/hicolor/scalable/apps/convertor-tool.svg" \
    "$PKG_DIR/usr/share/icons/hicolor/scalable/apps/${NAME}.svg"

# Copy copyright
install -m 644 "debian-pkg/usr/share/doc/convertor-tool/copyright" \
    "$PKG_DIR/usr/share/doc/${NAME}/copyright" 2>/dev/null || true

# Create man page
cat > "${PKG_DIR}/usr/share/man/man1/convertor-tool.1" << 'MANEOF'
.TH CONVERTOR-TOOL 1 "2026-07-22" "1.0.0" "User Commands"
.SH NAME
convertor-tool \- Convert between DOCX, PDF, and Markdown
.SH SYNOPSIS
.B convertor-tool
[\fImode\fR \fIinput\fR \fIoutput\fR]
.SH DESCRIPTION
Bi-directional document converter with GUI and CLI modes.
.PP
Modes:
.RS
docx2md  \- DOCX to Markdown
pdf2md   \- PDF to Markdown
md2docx  \- Markdown to DOCX
md2pdf   \- Markdown to PDF
.RE
.SH EXAMPLES
convertor-tool
.br
convertor-tool docx2md doc.docx doc.md
.br
convertor-tool pdf2md doc.pdf doc.md
MANEOF
gzip -9 "${PKG_DIR}/usr/share/man/man1/convertor-tool.1"

# Create .INSTALL script
cat > "${PKG_DIR}/.INSTALL" << 'INSTALLEOF'
post_install() {
  chmod +x /usr/bin/convertor-tool
  gtk-update-icon-cache -q -t /usr/share/icons/hicolor 2>/dev/null || true
  update-desktop-database -q 2>/dev/null || true
}

post_upgrade() {
  post_install
}

pre_remove() {
  true
}
INSTALLEOF

# Build the package
# Try makepkg first (native Arch Linux), fall back to bsdtar
if command -v makepkg &>/dev/null; then
  echo "Building with makepkg..."
  cd "$TMP_DIR"
  tar czf "${NAME}-${VERSION}.tar.gz" "${NAME}-${VERSION}"
  cp "${OLDPWD}/arch-pkg/PKGBUILD" "$TMP_DIR/PKGBUILD"
  mkdir -p "$TMP_DIR/src"
  mv "${NAME}-${VERSION}.tar.gz" "$TMP_DIR/"
  makepkg -f --clean --noextract 2>&1
  cp "${NAME}-${VERSION}-${PKGREL:-1}-any.pkg.tar.zst" "$OLDPWD/" 2>/dev/null || \
    cp *.pkg.tar.zst "$OLDPWD/" 2>/dev/null || true
else
  echo "Building with tar --zstd (no makepkg available)..."
  cd "$PKG_DIR"
  cat > .PKGINFO << PKGINFOEOF
pkgname = ${NAME}
pkgver = ${VERSION}-1
pkgdesc = Bi-directional document converter between DOCX, PDF, and Markdown formats
url = https://github.com/sajeethsaabir/file-convert-tool
builddate = $(date +%s)
packager = Convertor Tool Team
size = $(du -sb --apparent-size "$PKG_DIR" | cut -f1)
arch = any
license = Apache
depend = java-runtime>=21
PKGINFOEOF
  PKG_FILE="${OLDPWD}/${NAME}-${VERSION}-1-any.pkg.tar.zst"
  tar --zstd -cf "$PKG_FILE" \
    --exclude='.MTREE' \
    -C "$PKG_DIR" .
  echo "Created: $(ls -lh "$PKG_FILE" | awk '{print $5, $NF}')"
fi

# Clean up
rm -rf "$TMP_DIR"

echo "Done"
