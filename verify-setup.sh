#!/bin/bash

# V-Trainer Setup Verification Script
# This script checks if all required files and configurations are in place

echo "🔍 V-Trainer Setup Verification"
echo "================================"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASSED=0
FAILED=0
WARNINGS=0

# Function to check file existence
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $1"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $1 (missing)"
        ((FAILED++))
        return 1
    fi
}

# Function to check directory existence
check_dir() {
    if [ -d "$1" ]; then
        echo -e "${GREEN}✓${NC} $1/"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $1/ (missing)"
        ((FAILED++))
        return 1
    fi
}

# Function to check if file contains placeholder
check_placeholder() {
    if grep -q "YOUR_" "$1" 2>/dev/null; then
        echo -e "${YELLOW}⚠${NC} $1 (contains placeholders - needs configuration)"
        ((WARNINGS++))
        return 1
    fi
    return 0
}

echo "📁 Checking Firebase Configuration..."
check_file "firebase.json"
check_file ".firebaserc"
check_file "firestore.rules"
check_file "firestore.indexes.json"
check_file "storage.rules"
echo ""

echo "📱 Checking Android Mobile App..."
check_dir "app"
check_dir "app/src/main"
check_file "app/build.gradle.kts"
check_file "app/src/main/AndroidManifest.xml"
check_file "app/google-services.json"
check_placeholder "app/google-services.json"
echo ""

echo "⌚ Checking Wear OS App..."
check_dir "wear"
check_dir "wear/src/main"
check_file "wear/build.gradle.kts"
check_file "wear/src/main/AndroidManifest.xml"
check_file "wear/google-services.json"
check_placeholder "wear/google-services.json"
echo ""

echo "☁️  Checking Cloud Functions..."
check_dir "functions"
check_dir "functions/src"
check_file "functions/package.json"
check_file "functions/tsconfig.json"
check_file "functions/src/index.ts"
check_file "functions/src/syncWorkout.ts"
check_file "functions/src/calculateProgress.ts"
check_file "functions/src/sendWorkoutReminder.ts"
echo ""

echo "🔧 Checking Gradle Configuration..."
check_file "build.gradle.kts"
check_file "settings.gradle.kts"
check_file "gradle.properties"
echo ""

echo "📚 Checking Documentation..."
check_file "README.md"
check_file "SETUP.md"
check_file "PROJECT_STRUCTURE.md"
echo ""

echo "================================"
echo "Summary:"
echo -e "${GREEN}✓ Passed: $PASSED${NC}"
echo -e "${RED}✗ Failed: $FAILED${NC}"
echo -e "${YELLOW}⚠ Warnings: $WARNINGS${NC}"
echo ""

if [ $FAILED -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}🎉 All checks passed! Your setup is complete.${NC}"
    exit 0
elif [ $FAILED -eq 0 ]; then
    echo -e "${YELLOW}⚠️  Setup is mostly complete, but you need to configure google-services.json files.${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Create a Firebase project at https://console.firebase.google.com/"
    echo "2. Download google-services.json for both mobile and wear apps"
    echo "3. Replace the placeholder files in app/ and wear/ directories"
    echo "4. Run 'firebase init' to connect to your Firebase project"
    exit 1
else
    echo -e "${RED}❌ Setup is incomplete. Please check the missing files above.${NC}"
    exit 1
fi
