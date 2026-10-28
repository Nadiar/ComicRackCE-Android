# ComicRack-Android Automation Guide

This document describes the automation and code quality setup for ComicRack-Android.

## Overview

The project uses Git hooks and Gradle tasks to enforce code quality standards before commits and pushes.

## Quick Start

### Initial Setup

```bash
# 1. Install ktlint (choose one)
brew install ktlint                    # macOS
choco install ktlint                   # Windows
# Or download from https://github.com/pinterest/ktlint/releases

# 2. Run setup scripts
chmod +x scripts/*.sh
./scripts/setup-hooks.sh               # Install Git hooks
./scripts/setup-detekt.sh              # Configure detekt

# 3. Verify setup
./scripts/quality-check.sh             # Run all checks
```

## Git Hooks

### Pre-Commit Hook

Runs before each commit to ensure code quality:

- **Warp lock check**: Prevents commits during warp operations
- **ktlint**: Kotlin code formatting
- **detekt**: Static code analysis (if configured)
- **Android Lint**: Android-specific issues

**Skip with**: `git commit --no-verify` (use sparingly!)

### Pre-Push Hook

Runs before pushing to remote:

- **Unit tests**: All tests must pass
- **Lint**: Full lint check

**Skip with**: `git push --no-verify` (use sparingly!)

### Commit-Msg Hook

Validates commit message format:

**Required format**: `<type>(<scope>): <subject>`

**Valid types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Code style (formatting, no logic change)
- `refactor`: Code refactor
- `perf`: Performance improvement
- `test`: Test changes
- `build`: Build system changes
- `ci`: CI/CD changes
- `chore`: Maintenance

**Examples**:
```
feat(reader): add zoom gesture support
fix(db): resolve migration crash on upgrade
docs: update README with build instructions
```

## Code Quality Tools

### ktlint

Kotlin linter and formatter.

```bash
# Check formatting
ktlint

# Auto-fix formatting
ktlint -F

# Check specific files
ktlint "app/src/**/*.kt"
```

**Configuration**: `.editorconfig` file in project root

### detekt

Static code analysis for Kotlin.

```bash
# Run detekt
./gradlew detekt

# View report
open ComicRackCE/app/build/reports/detekt/detekt.html
```

**Configuration**: `ComicRackCE/detekt.yml`

### Android Lint

Android-specific code quality checks.

```bash
# Run lint
./gradlew lintDebug

# View report
open ComicRackCE/app/build/reports/lint-results-debug.html
```

**Configuration**: `ComicRackCE/app/build.gradle.kts` (lint block)

## Gradle Tasks

### Quality Check

Run all code quality checks at once:

```bash
./scripts/quality-check.sh
```

This runs:
1. ktlint format check
2. detekt static analysis
3. Android Lint
4. Unit tests
5. Debug build

### Individual Checks

```bash
# Format check
ktlint

# Static analysis
./gradlew detekt

# Lint
./gradlew lintDebug

# Unit tests
./gradlew testDebugUnitTest

# Build
./gradlew assembleDebug
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Quality Check

on: [push, pull_request]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Install ktlint
        run: |
          curl -sSLO https://github.com/pinterest/ktlint/releases/download/1.0.1/ktlint
          chmod a+x ktlint
          sudo mv ktlint /usr/local/bin/

      - name: Run quality checks
        run: ./scripts/quality-check.sh
```

## Customization

### Disable Specific Checks

Edit the hooks in `.git/hooks/`:

```bash
# Edit pre-commit hook
nano .git/hooks/pre-commit

# Comment out checks you don't want:
# if ! ./gradlew detekt; then
#     exit 1
# fi
```

### Adjust ktlint Rules

Edit `.editorconfig`:

```ini
[*.kt]
max_line_length = 150  # Change from 120
```

### Adjust detekt Rules

Edit `ComicRackCE/detekt.yml`:

```yaml
complexity:
  LongMethod:
    threshold: 80  # Change from 60
```

### Adjust Android Lint

Edit `ComicRackCE/app/build.gradle.kts`:

```kotlin
lint {
    abortOnError = true  // Fail build on lint errors
    warningsAsErrors = true  // Treat warnings as errors
    disable += setOf("MissingTranslation")
}
```

## Troubleshooting

### Hook Not Running

```bash
# Verify hook is executable
ls -la .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Reinstall hooks
./scripts/setup-hooks.sh
```

### ktlint Not Found

```bash
# Verify installation
which ktlint

# Add to PATH (add to ~/.bashrc or ~/.zshrc)
export PATH="/usr/local/bin:$PATH"
```

### Gradle Permission Denied

```bash
chmod +x gradlew
```

### Tests Failing

```bash
# Run tests with verbose output
./gradlew testDebugUnitTest --info

# Run specific test
./gradlew testDebugUnitTest --tests "com.comicrack.reader.SpecificTest"
```

## Best Practices

1. **Run quality checks before committing**:
   ```bash
   ./scripts/quality-check.sh
   ```

2. **Fix formatting automatically**:
   ```bash
   ktlint -F
   ```

3. **Review lint/detekt reports** before dismissing issues

4. **Don't skip hooks** unless absolutely necessary

5. **Keep hooks fast**: Remove slow checks or make them optional

6. **Update tools regularly**:
   ```bash
   brew upgrade ktlint  # macOS
   # Update detekt version in build.gradle.kts
   ```

## Additional Resources

- [ktlint documentation](https://pinterest.github.io/ktlint/)
- [detekt documentation](https://detekt.dev/)
- [Android Lint documentation](https://developer.android.com/studio/write/lint)
- [EditorConfig documentation](https://editorconfig.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
