# ComicRack-Android Automation Scripts

This directory contains automation and code quality scripts for the ComicRack-Android project.

## Scripts

### setup-hooks.sh

Installs Git hooks for code quality enforcement.

**Installs**:
- `pre-commit`: Runs ktlint, detekt, and Android Lint before commits
- `pre-push`: Runs unit tests before pushing
- `commit-msg`: Validates commit message format (conventional commits)

**Usage**:
```bash
./scripts/setup-hooks.sh
```

### setup-detekt.sh

Configures detekt static analysis tool.

**Creates**:
- `ComicRackCE/detekt.yml`: Detekt configuration file
- Instructions for adding detekt to build.gradle.kts

**Usage**:
```bash
./scripts/setup-detekt.sh
```

### quality-check.sh

Runs all code quality checks in sequence.

**Checks**:
1. ktlint format check
2. detekt static analysis
3. Android Lint
4. Unit tests
5. Debug build

**Usage**:
```bash
./scripts/quality-check.sh
```

**Use in CI/CD**: This script is designed to be used in continuous integration pipelines.

## First-Time Setup

Run these commands in order:

```bash
# 1. Make scripts executable
chmod +x scripts/*.sh

# 2. Install Git hooks
./scripts/setup-hooks.sh

# 3. Configure detekt
./scripts/setup-detekt.sh

# 4. Install ktlint
# macOS:
brew install ktlint
# Windows:
choco install ktlint
# Or download from https://github.com/pinterest/ktlint/releases

# 5. Verify everything works
./scripts/quality-check.sh
```

## Updating Hooks

If you modify the scripts, re-run the setup:

```bash
./scripts/setup-hooks.sh
```

## Customization

Edit the scripts directly to adjust which checks run:

- **setup-hooks.sh**: Modify hook behavior
- **setup-detekt.sh**: Adjust detekt configuration
- **quality-check.sh**: Add/remove quality checks

## Troubleshooting

### Scripts won't execute

```bash
chmod +x scripts/*.sh
```

### ktlint not found

```bash
# Verify installation
which ktlint

# Install if missing
brew install ktlint  # macOS
```

### Hooks not running

```bash
# Check hook files exist and are executable
ls -la .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Reinstall
./scripts/setup-hooks.sh
```

## See Also

- [AUTOMATION.md](../AUTOMATION.md) - Complete automation guide
- [.editorconfig](../.editorconfig) - Code style configuration
- [detekt.yml](../ComicRackCE/detekt.yml) - Detekt rules (after setup)
