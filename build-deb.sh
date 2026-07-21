#!/bin/bash
set -e

cd "$(dirname "$0")"

# Build the JAR first
echo "Building JAR..."
mvn package -q -DskipTests

VERSION="1.0.0"
PKG_DIR="debian-pkg"
JAR="target/convertor-tool-${VERSION}.jar"

# Copy JAR
echo "Copying JAR..."
install -D -m 644 "$JAR" "$PKG_DIR/usr/share/convertor-tool/convertor-tool.jar"

# Create man page
echo "Creating man page..."
mkdir -p "$PKG_DIR/usr/share/man/man1"
cat > /tmp/convertor-tool.1 << 'MANEOF'
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
gzip -9 -c /tmp/convertor-tool.1 > "$PKG_DIR/usr/share/man/man1/convertor-tool.1.gz"
rm -f /tmp/convertor-tool.1

# Remove the placeholder changelog and use a real one
rm -f "$PKG_DIR/usr/share/doc/convertor-tool/changelog.Debian.gz"

# Set permissions
echo "Setting permissions..."
chmod 755 "$PKG_DIR/DEBIAN/postinst" "$PKG_DIR/DEBIAN/prerm" 2>/dev/null || true
chmod 755 "$PKG_DIR/usr/bin/convertor-tool"
find "$PKG_DIR/usr/share" -type d -exec chmod 755 {} \;
find "$PKG_DIR/usr/share" -type f -exec chmod 644 {} \;

# Build .deb
DEB_FILE="convertor-tool_${VERSION}_all.deb"
echo "Building ${DEB_FILE}..."
fakeroot dpkg-deb --build "$PKG_DIR" "$DEB_FILE" 2>/dev/null || dpkg-deb --build "$PKG_DIR" "$DEB_FILE"

echo "Done: $(ls -lh $DEB_FILE | awk '{print $5, $NF}')"
