#!/usr/bin/env bash
# Run all code quality checks for ComicRack-Android
# Use this before committing or in CI/CD

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

FAILED=0

echo "=================================="
echo "ComicRack-Android Quality Checks"
echo "=================================="
echo ""

# Function to run check and track failures
run_check() {
    local name=$1
    local command=$2

    echo "Running $name..."
    if eval "$command"; then
        echo -e "${GREEN}✓ $name passed${NC}"
    else
        echo -e "${RED}✗ $name failed${NC}"
        FAILED=$((FAILED + 1))
    fi
    echo ""
}

# Check 1: ktlint
if command -v ktlint &> /dev/null; then
    run_check "ktlint format check" "ktlint --relative"
else
    echo -e "${YELLOW}⚠ ktlint not installed, skipping${NC}"
    echo ""
fi

# Check 2: detekt
if [ -f "ComicRackCE/detekt.yml" ]; then
    run_check "detekt static analysis" "./gradlew detekt"
else
    echo -e "${YELLOW}⚠ detekt not configured, skipping${NC}"
    echo ""
fi

# Check 3: Android Lint
run_check "Android Lint" "./gradlew lintDebug"

# Check 4: Unit tests
run_check "Unit tests" "./gradlew testDebugUnitTest"

# Check 5: Build
run_check "Debug build" "./gradlew assembleDebug"

echo "=================================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All quality checks passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ $FAILED check(s) failed${NC}"
    exit 1
fi
