#!/usr/bin/env bash
# Setup script for installing Git hooks and code quality tools

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"

echo "=================================="
echo "ComicRack-Android Automation Setup"
echo "=================================="

# Check if we're in a git repository
if [ ! -d "$PROJECT_ROOT/.git" ]; then
    echo "ERROR: Not a git repository. Run this from the project root."
    exit 1
fi

# Install pre-commit hook
echo "Installing pre-commit hook..."
cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/usr/bin/env bash
# Pre-commit hook for ComicRack-Android
# Runs code quality checks before allowing commit

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

echo "=================================="
echo "Pre-commit Quality Checks"
echo "=================================="

# Check for warp lock (preserve existing check)
LOCKFILE="$PROJECT_ROOT/.git/warp.lock"
if [ -f "$LOCKFILE" ]; then
    LOCK_TIMESTAMP=$(git config warp.lock.timestamp 2>/dev/null || echo "")
    if [ -n "$LOCK_TIMESTAMP" ]; then
        echo -e "${RED}ERROR: Cannot commit - repository is locked${NC}"
        echo "A warp operation is in progress."
        echo "If you're sure no operation is running:"
        echo "  1. Delete: $LOCKFILE"
        echo "  2. Run: git config --unset warp.lock.timestamp"
        exit 1
    fi
fi

# Get list of staged Kotlin files
KOTLIN_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$' || true)

if [ -z "$KOTLIN_FILES" ]; then
    echo -e "${GREEN}✓ No Kotlin files to check${NC}"
    exit 0
fi

echo -e "${YELLOW}Checking ${#KOTLIN_FILES[@]} Kotlin files...${NC}"

# Run ktlint format check
echo "Running ktlint..."
if command -v ktlint &> /dev/null; then
    if ! echo "$KOTLIN_FILES" | xargs ktlint --relative; then
        echo -e "${RED}✗ ktlint failed${NC}"
        echo "Run 'ktlint -F' to auto-fix formatting issues"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠ ktlint not installed, skipping format check${NC}"
    echo "Install with: brew install ktlint (Mac) or download from https://github.com/pinterest/ktlint"
fi

# Run detekt if configured
if [ -f "ComicRackCE/detekt.yml" ]; then
    echo "Running detekt..."
    if ! ./gradlew detekt; then
        echo -e "${RED}✗ detekt found issues${NC}"
        exit 1
    fi
fi

# Run Android Lint on staged files
echo "Running Android Lint..."
if ! ./gradlew lintDebug; then
    echo -e "${YELLOW}⚠ Lint found issues (check reports/lint-results-debug.html)${NC}"
    # Don't fail on lint warnings, just warn
fi

echo -e "${GREEN}✓ All pre-commit checks passed!${NC}"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-commit"

# Install pre-push hook
echo "Installing pre-push hook..."
cat > "$HOOKS_DIR/pre-push" << 'EOF'
#!/usr/bin/env bash
# Pre-push hook for ComicRack-Android
# Runs tests before allowing push

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

echo "=================================="
echo "Pre-push Tests"
echo "=================================="

# Run unit tests
echo "Running unit tests..."
if ! ./gradlew testDebugUnitTest; then
    echo -e "${RED}✗ Unit tests failed${NC}"
    echo "Fix tests before pushing or use --no-verify to skip"
    exit 1
fi

# Optional: Run lint
echo "Running full lint check..."
if ! ./gradlew lintDebug; then
    echo -e "${YELLOW}⚠ Lint found issues${NC}"
    # Don't block push on lint warnings
fi

echo -e "${GREEN}✓ All pre-push checks passed!${NC}"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-push"

# Install commit-msg hook for conventional commits
echo "Installing commit-msg hook..."
cat > "$HOOKS_DIR/commit-msg" << 'EOF'
#!/usr/bin/env bash
# Commit message validation hook
# Enforces conventional commit format

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

COMMIT_MSG_FILE=$1
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")

# Pattern for conventional commits
PATTERN="^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\(.+\))?: .{1,80}"

if ! echo "$COMMIT_MSG" | head -n1 | grep -qE "$PATTERN"; then
    echo -e "${RED}✗ Invalid commit message format${NC}"
    echo ""
    echo "Commit message must follow conventional commits:"
    echo "  <type>(<scope>): <subject>"
    echo ""
    echo "Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert"
    echo ""
    echo "Examples:"
    echo "  feat(reader): add zoom gesture support"
    echo "  fix(db): resolve migration crash on upgrade"
    echo "  docs: update README with build instructions"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ Commit message format valid${NC}"
exit 0
EOF

chmod +x "$HOOKS_DIR/commit-msg"

echo ""
echo -e "${GREEN}✓ Git hooks installed successfully!${NC}"
echo ""
echo "Next steps:"
echo "  1. Install ktlint: brew install ktlint (or download manually)"
echo "  2. Run: ./gradlew addKtlintFormatGitPreCommitHook (optional)"
echo "  3. Configure detekt: ./scripts/setup-detekt.sh"
echo ""
echo "Installed hooks:"
echo "  - pre-commit: ktlint, detekt, Android Lint"
echo "  - pre-push: unit tests, lint"
echo "  - commit-msg: conventional commit format validation"
echo ""
