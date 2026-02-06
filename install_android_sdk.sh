#!/bin/bash
# Install Android SDK Command-Line Tools

set -e

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║           Installing Android SDK Command-Line Tools             ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

SDK_DIR="$HOME/Library/Android/sdk"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-${CMDLINE_TOOLS_VERSION}_latest.zip"

# Create SDK directory
mkdir -p "$SDK_DIR/cmdline-tools"

# Download command-line tools
echo "📥 Downloading Android SDK command-line tools..."
cd /tmp
curl -L -o commandlinetools.zip "$CMDLINE_TOOLS_URL"

# Extract
echo "📦 Extracting..."
unzip -q commandlinetools.zip
mv cmdline-tools "$SDK_DIR/cmdline-tools/latest"
rm commandlinetools.zip

# Set up environment
export ANDROID_HOME="$SDK_DIR"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# Accept licenses
echo "📜 Accepting Android SDK licenses..."
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses

# Install required SDK packages
echo "📦 Installing Android SDK packages..."
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "cmdline-tools;latest"

# Add to shell profile
if ! grep -q "ANDROID_HOME" ~/.zshrc; then
    echo "" >> ~/.zshrc
    echo "# Android SDK" >> ~/.zshrc
    echo "export ANDROID_HOME=\"$HOME/Library/Android/sdk\"" >> ~/.zshrc
    echo "export PATH=\"\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools\"" >> ~/.zshrc
    echo "✅ Added ANDROID_HOME to ~/.zshrc"
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║              ✅ Android SDK Installation Complete               ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "📍 SDK Location: $SDK_DIR"
echo ""
echo "🔄 Next step: Create local.properties file"
echo ""
