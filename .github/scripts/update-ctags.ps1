# ComicRack-Android Ctags Update Wrapper
# Use $PSScriptRoot to avoid hardcoded FQNs

$ErrorActionPreference = "Stop"

try {
    $WorkspaceRoot = (Resolve-Path "$PSScriptRoot\..\..\..").Path
    $BaseScript = Join-Path $WorkspaceRoot "scripts\update-ctags-base.ps1"
    $ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path

    # Define source paths - include main source and tests
    $ComicRackCERoot = Join-Path $ProjectRoot "ComicRackCE"
    $SourcePaths = @(
        (Join-Path $ComicRackCERoot "app\src\main\java"),
        (Join-Path $ComicRackCERoot "app\src\main\kotlin"),
        (Join-Path $ComicRackCERoot "app\src\test"),
        (Join-Path $ComicRackCERoot "app\src\androidTest")
    ) | Where-Object { Test-Path $_ }

    if ($SourcePaths.Count -eq 0) {
        Write-Error "No source paths found under $ComicRackCERoot"
        exit 1
    }

    $TagsDir = Join-Path $ProjectRoot ".github\ctags"

    if (-not (Test-Path $BaseScript)) {
        Write-Error "Base script not found at $BaseScript"
        exit 1
    }

    Write-Host "Updating ctags for $($SourcePaths.Count) source directories..." -ForegroundColor Cyan
    & $BaseScript -SourcePaths $SourcePaths -TagsDir $TagsDir -ProjectRoot $ProjectRoot

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Ctags update failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }

    Write-Host "Ctags updated successfully!" -ForegroundColor Green
}
catch {
    Write-Error "Failed to update ctags: $_"
    exit 1
}
