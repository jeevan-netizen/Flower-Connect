# generate-dev-certs.ps1 - Generates self-signed dev certificates for local HTTPS
$ErrorActionPreference = "Stop"

$certsDir = Join-Path $PSScriptRoot "..\certs"
if (-not (Test-Path $certsDir)) {
    New-Item -ItemType Directory -Path $certsDir -Force | Out-Null
}

$certFile = Join-Path $certsDir "localhost.pem"
$keyFile = Join-Path $certsDir "localhost-key.pem"

if ((Test-Path $certFile) -and (Test-Path $keyFile)) {
    Write-Host "Certificates already exist in $certsDir"
    exit 0
}

# Try Git's openssl first, then system openssl
$opensslPath = "C:\Program Files\Git\usr\bin\openssl.exe"
if (-not (Test-Path $opensslPath)) {
    $opensslCmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($opensslCmd) {
        $opensslPath = $opensslCmd.Source
    }
}

if (Test-Path $opensslPath) {
    & $opensslPath req -x509 -newkey rsa:2048 -nodes -keyout $keyFile -out $certFile -days 365 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
    Write-Host "Generated self-signed certificates in $certsDir using OpenSSL"
} else {
    # Fallback to docker alpine openssl
    docker run --rm -v "${certsDir}:/certs" alpine sh -c "apk add --no-cache openssl && openssl req -x509 -newkey rsa:2048 -nodes -keyout /certs/localhost-key.pem -out /certs/localhost.pem -days 365 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'"
    Write-Host "Generated self-signed certificates in $certsDir using Docker Alpine OpenSSL"
}

