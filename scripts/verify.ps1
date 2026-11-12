# scripts/verify.ps1 — FlowerConnect Phase 14 end-to-end verification
#
# Runs the documented verification suite in order, stopping on the first
# failure:
#   1. Backend unit tests        ./mvnw test
#   2. Full backend integration  ./mvnw verify -Pintegration
#   3. Frontend lint             npm run lint
#   4. Frontend typecheck        npm run typecheck
#   5. Frontend tests            npm run test
#   6. Frontend production build npm run build
#
# Usage (from repository root):
#   .\scripts\verify.ps1
#
# Requires:
#   - Java 17 + Maven wrapper (backend/mvnw)
#   - Node.js + npm (frontend)
#   - Docker Desktop running (integration suite uses Testcontainers MySQL)
#
# Exit code is non-zero if any step fails.

$ErrorActionPreference = "Stop"
$verbosity = "Detailed"

Write-Host "=== FlowerConnect Phase 14 verification ===" -ForegroundColor Cyan
Write-Host "Working directory: $(Get-Location)"
$start = Get-Date

function RunStep([string]$name, [string]$cmd, [string[]]$args) {
    Write-Host "--- $name ---" -ForegroundColor Yellow
    try {
        & $cmd @args
    }
    catch {
        Write-Host "FAILED: $name" -ForegroundColor Red
        exit 1
    }
    Write-Host "PASSED: $name" -ForegroundColor Green
}

# Resolve paths relative to repo root
$backend = Join-Path (Get-Location) "backend"
$frontend = Join-Path (Get-Location) "frontend"

# 1. Backend unit tests
Push-Location $backend
RunStep "Backend unit tests (./mvnw test)" ./mvnw @("test")
Pop-Location

# 2. Full backend integration suite (Testcontainers MySQL)
Push-Location $backend
RunStep "Backend full integration suite (./mvnw verify -Pintegration)" ./mvnw @("verify","-Pintegration")
Pop-Location

# 3. Frontend lint
Push-Location $frontend
RunStep "Frontend lint (npm run lint)" npm @("run","lint")
Pop-Location

# 4. Frontend typecheck
Push-Location $frontend
RunStep "Frontend typecheck (npm run typecheck)" npm @("run","typecheck")
Pop-Location

# 5. Frontend tests
Push-Location $frontend
RunStep "Frontend tests (npm run test)" npm @("run","test")
Pop-Location

# 6. Frontend production build
Push-Location $frontend
RunStep "Frontend production build (npm run build)" npm @("run","build")
Pop-Location

$end = Get-Date
$elapsed = New-Timespan -Start $start -End $end
Write-Host "" -ForegroundColor Cyan
Write-Host "=== All verification steps PASSED ===" -ForegroundColor Green
Write-Host ("Elapsed: {0:hh\:mm\:ss}" -f $elapsed) -ForegroundColor Cyan
exit 0
