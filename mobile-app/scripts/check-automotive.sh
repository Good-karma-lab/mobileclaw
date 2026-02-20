#!/bin/bash

# Automotive Setup Checker for MobileClaw
# This script helps diagnose issues when running on Android Automotive

set -e

echo "🚗 MobileClaw Automotive Setup Checker"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_command() {
  if command -v $1 &> /dev/null; then
    echo -e "${GREEN}✓${NC} $1 is installed"
    return 0
  else
    echo -e "${RED}✗${NC} $1 is not installed"
    return 1
  fi
}

# Check basic requirements
echo "Checking requirements..."
check_command node || echo "  → Install Node.js from https://nodejs.org"
check_command npm || echo "  → npm should come with Node.js"
check_command npx || echo "  → npx should come with npm"

# Check for Android SDK
if [ -d "$HOME/Library/Android/sdk" ] || [ -n "$ANDROID_HOME" ]; then
  echo -e "${GREEN}✓${NC} Android SDK found"

  # Check for adb
  if check_command adb; then
    ADB_VERSION=$(adb version | head -1)
    echo "  $ADB_VERSION"
  else
    echo -e "${YELLOW}⚠${NC} adb not in PATH. Add to your shell profile:"
    echo "  export PATH=\"\$HOME/Library/Android/sdk/platform-tools:\$PATH\""
  fi
else
  echo -e "${RED}✗${NC} Android SDK not found"
  echo "  → Install Android Studio from https://developer.android.com/studio"
fi

echo ""
echo "Checking Android devices..."
if command -v adb &> /dev/null; then
  DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l | xargs)
  if [ "$DEVICES" -gt "0" ]; then
    echo -e "${GREEN}✓${NC} Found $DEVICES connected device(s):"
    adb devices | grep -v "List of devices" | grep -v "^$" | while read -r line; do
      echo "  → $line"
    done
  else
    echo -e "${YELLOW}⚠${NC} No connected devices"
    echo "  → Start an Android Automotive emulator in Android Studio"
    echo "  → Or run: emulator -avd <automotive-avd-name>"
  fi
else
  echo -e "${RED}✗${NC} Cannot check devices (adb not available)"
fi

echo ""
echo "Checking project setup..."

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
  echo -e "${RED}✗${NC} Not in mobile-app directory"
  echo "  → cd to mobile-app directory first"
  exit 1
fi

echo -e "${GREEN}✓${NC} In mobile-app directory"

# Check if node_modules exists
if [ -d "node_modules" ]; then
  echo -e "${GREEN}✓${NC} node_modules installed"
else
  echo -e "${YELLOW}⚠${NC} node_modules not found"
  echo "  → Run: npm install"
fi

# Check .env file
if [ -f ".env" ]; then
  echo -e "${GREEN}✓${NC} .env file exists"

  # Check if key vars are set
  if grep -q "EXPO_PUBLIC_PLATFORM_URL" .env; then
    PLATFORM_URL=$(grep "EXPO_PUBLIC_PLATFORM_URL" .env | cut -d '=' -f2 | tr -d '"' | tr -d ' ')
    echo "  Platform URL: $PLATFORM_URL"
  fi
else
  echo -e "${YELLOW}⚠${NC} .env file not found"
  echo "  → A default .env should have been created"
  echo "  → Check AUTOMOTIVE_SETUP.md for details"
fi

# Check for .gitignore
if [ -f ".gitignore" ]; then
  if grep -q "^\.env$" .gitignore; then
    echo -e "${GREEN}✓${NC} .env is properly ignored in git"
  else
    echo -e "${YELLOW}⚠${NC} .env should be in .gitignore"
  fi
else
  echo -e "${YELLOW}⚠${NC} .gitignore not found"
fi

echo ""
echo "Checking Metro bundler..."
if lsof -i :8081 &> /dev/null; then
  echo -e "${GREEN}✓${NC} Metro bundler is running on port 8081"
  lsof -i :8081 | tail -1
else
  echo -e "${YELLOW}⚠${NC} Metro bundler not running"
  echo "  → Start it with: npm start"
fi

echo ""
echo "Checking Android build..."
if [ -d "android/app/build" ]; then
  echo -e "${GREEN}✓${NC} Android build directory exists"
else
  echo -e "${YELLOW}⚠${NC} No previous build found"
  echo "  → Run: npm run android"
fi

echo ""
echo "========================================"
echo "Quick Commands:"
echo ""
echo "  Start Metro:          npm start"
echo "  Build & Run:          npm run android"
echo "  Clean Build:          npm run android -- --no-build-cache"
echo "  View Logs:            adb logcat | grep -E 'mobile|expo|ReactNative'"
echo "  Debug Menu (in app):  adb shell input keyevent 82"
echo "  Reload App:           adb shell input text \"RR\""
echo ""
echo "For more help, see AUTOMOTIVE_SETUP.md"
