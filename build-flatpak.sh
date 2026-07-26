#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Building JAR..."
mvn package -q -DskipTests

VERSION="1.0.0"
NAME="convertor-tool"
JAR="target/${NAME}-${VERSION}.jar"
FLATPAK_DIR="flatpak-pkg"
FLATPAK_ID="com.convertor.ConvertorTool"

echo "Setting up Flatpak build..."

BUILD_DIR=$(mktemp -d)
FLATPAK_BUILD="$BUILD_DIR/flatpak-build"
FLATPAK_REPO="$BUILD_DIR/flatpak-repo"

mkdir -p "$FLATPAK_BUILD"

# Copy required sources into the flatpak build context
cp "$JAR" "$FLATPAK_BUILD/convertor-tool.jar"
cp "$FLATPAK_DIR/convertor-tool" "$FLATPAK_BUILD/"
cp "$FLATPAK_DIR/${FLATPAK_ID}.desktop" "$FLATPAK_BUILD/"
cp "$FLATPAK_DIR/${FLATPAK_ID}.svg" "$FLATPAK_BUILD/"
cp "$FLATPAK_DIR/copyright" "$FLATPAK_BUILD/"
cp "$FLATPAK_DIR/${FLATPAK_ID}.yml" "$FLATPAK_BUILD/"

cd "$FLATPAK_BUILD"

if command -v flatpak-builder &>/dev/null; then
  echo "Building Flatpak with flatpak-builder..."
  flatpak-builder --repo="$FLATPAK_REPO" --force-clean --install-deps-from=flathub \
    "$FLATPAK_BUILD/build" "${FLATPAK_ID}.yml" 2>&1

  # Create a .flatpak bundle
  flatpak build-bundle "$FLATPAK_REPO" "$OLDPWD/${NAME}-${VERSION}-1.x86_64.flatpak" \
    "${FLATPAK_ID}" 2>&1

  echo "Created: $(ls -lh "$OLDPWD/${NAME}-${VERSION}-1.x86_64.flatpak" | awk '{print $5, $NF}')"
else
  echo "flatpak-builder not available. Building Flatpak is only supported on systems with flatpak and flatpak-builder installed."
  echo "Skipping Flatpak build."
fi

# Clean up
rm -rf "$BUILD_DIR"

echo "Done"
