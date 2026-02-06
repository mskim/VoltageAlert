#!/bin/bash
# VoltageAlert - Quick Start Build Script
# Run this script to install JDK and build the app

set -e  # Exit on error

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║        VoltageAlert - Quick Start Installation & Build          ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

# Check if Homebrew is installed
if ! command -v brew &> /dev/null; then
    echo "❌ Homebrew not found. Installing Homebrew..."
    echo ""
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

    # Add Homebrew to PATH for Apple Silicon Macs
    if [[ $(uname -m) == 'arm64' ]]; then
        echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
else
    echo "✅ Homebrew is already installed"
fi

# Install JDK 17
echo ""
echo "📦 Installing OpenJDK 17..."
brew install openjdk@17

# Set up JAVA_HOME
echo ""
echo "🔧 Setting up JAVA_HOME..."
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)

if [ -z "$JAVA_HOME" ]; then
    echo "⚠️  Could not find Java 17. Trying to link it..."
    sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi

# Add JAVA_HOME to shell profile
if ! grep -q "JAVA_HOME" ~/.zshrc; then
    echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
    echo "✅ Added JAVA_HOME to ~/.zshrc"
fi

# Verify Java installation
echo ""
echo "✅ Java installed successfully!"
java -version
echo ""

# Navigate to project directory
cd "$(dirname "$0")"

# Build the app
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                     Building VoltageAlert                        ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

echo "🧹 Cleaning previous builds..."
./gradlew clean

echo ""
echo "🔨 Building debug APK..."
./gradlew assembleDebug

# Check if build was successful
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════════════╗"
    echo "║                    ✅ BUILD SUCCESSFUL!                          ║"
    echo "╚══════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "📦 APK Location:"
    ls -lh app/build/outputs/apk/debug/app-debug.apk
    echo ""
    echo "📍 Full path:"
    echo "   $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📲 To install on device:"
    echo "   ~/Library/Android/sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo ""
    echo "❌ Build failed. Check the output above for errors."
    exit 1
fi
