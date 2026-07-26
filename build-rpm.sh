#!/bin/bash
set -e

cd "$(dirname "$0")"

# Build the JAR first
echo "Building JAR..."
mvn package -q -DskipTests

VERSION="1.0.0"
NAME="convertor-tool"
JAR="target/${NAME}-${VERSION}.jar"
SPEC_FILE="rpm-pkg/${NAME}.spec"
RPM_BUILD_DIR="${HOME}/rpmbuild"
TMP_DIR=$(mktemp -d)
PKG_DIR="${TMP_DIR}/${NAME}-${VERSION}"

echo "Setting up RPM package tree..."

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
    "$PKG_DIR/usr/share/applications/convertor-tool.desktop"

# Copy icon
install -m 644 "debian-pkg/usr/share/icons/hicolor/scalable/apps/convertor-tool.svg" \
    "$PKG_DIR/usr/share/icons/hicolor/scalable/apps/convertor-tool.svg"

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

# Create source tarball
echo "Creating source tarball..."
cd "$TMP_DIR"
tar czf "${NAME}-${VERSION}.tar.gz" "${NAME}-${VERSION}"

# Set up RPM build tree
mkdir -p "$RPM_BUILD_DIR"/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

# Copy sources and spec
cp "${NAME}-${VERSION}.tar.gz" "$RPM_BUILD_DIR/SOURCES/"
cp "$OLDPWD/$SPEC_FILE" "$RPM_BUILD_DIR/SPECS/"

# Build RPM
echo "Building RPM..."
rpmbuild -bb "$RPM_BUILD_DIR/SPECS/${NAME}.spec" \
    --define "_sourcedir $RPM_BUILD_DIR/SOURCES" \
    --define "_specdir $RPM_BUILD_DIR/SPECS" \
    --define "_builddir $RPM_BUILD_DIR/BUILD" \
    --define "_buildrootdir $RPM_BUILD_DIR/BUILDROOT" \
    --define "_rpmdir $RPM_BUILD_DIR/RPMS" \
    --define "_srcrpmdir $RPM_BUILD_DIR/SRPMS" 2>&1

# Copy the resulting RPM to project root
RPM_FILE="$RPM_BUILD_DIR/RPMS/noarch/${NAME}-${VERSION}-1.*.noarch.rpm"
echo "Copying RPM to project root..."
cp $RPM_FILE "$OLDPWD/"

# Clean up
rm -rf "$TMP_DIR"

echo "Done: $(ls -lh "$OLDPWD"/${NAME}-${VERSION}-1.*.noarch.rpm 2>/dev/null | awk '{print $5, $NF}')"
