param(
    [ValidateSet("start", "stop", "restart", "status", "logs", "test")]
    [string]$Action = "start",
    [switch]$Open,
    [switch]$WithTests,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

# 让脚本无论从哪里执行，都能定位到 multi_agent_java 根目录。
$ScriptPath = if ($PSCommandPath) { $PSCommandPath } else { $MyInvocation.MyCommand.Definition }
$ScriptDir = if ($PSScriptRoot) {
    $PSScriptRoot
} elseif ($ScriptPath -and (Test-Path -LiteralPath $ScriptPath)) {
    Split-Path -Parent $ScriptPath
} else {
    Join-Path (Get-Location).Path "scripts"
}
$Root = (Resolve-Path (Join-Path $ScriptDir "..")).Path
Set-Location -LiteralPath $Root

function Assert-CommandExists {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command '$Name' was not found. Please install it or add it to PATH."
    }
}

function Assert-DockerRunning {
    Assert-CommandExists "docker"
    docker info *> $null
}

function Package-Backend {
    Assert-CommandExists "mvn"

    Write-Host "Packaging backend..."
    Push-Location -LiteralPath (Join-Path $Root "backend")
    if ($WithTests) {
        mvn clean package
    } else {
        mvn clean package -DskipTests
    }
    Pop-Location
}

function Wait-BackendHealth {
    $healthUrl = "http://localhost:18080/api/health"
    Write-Host "Waiting for backend health..."

    for ($i = 1; $i -le 60; $i++) {
        try {
            $result = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
            if ($result -eq "ok") {
                Write-Host "Backend is healthy."
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }

    throw "Backend did not become healthy in 60 seconds. Run '.\codeguard.bat logs' to inspect logs."
}

function Show-UsageInfo {
    Write-Host ""
    Write-Host "CodeGuard Agent is ready."
    Write-Host "  Frontend:        http://localhost:3000"
    Write-Host "  Backend health:  http://localhost:18080/api/health"
    Write-Host "  Actuator:        http://localhost:18080/actuator/health"
    Write-Host ""
    Write-Host "Demo accounts:"
    Write-Host "  admin / codeguard123"
    Write-Host "  developer / developer123"
    Write-Host "  auditor / auditor123"
    Write-Host ""
    Write-Host "Common commands:"
    Write-Host "  .\codeguard.bat start"
    Write-Host "  .\codeguard.bat stop"
    Write-Host "  .\codeguard.bat restart"
    Write-Host "  .\codeguard.bat status"
    Write-Host "  .\codeguard.bat logs"
    Write-Host "  .\codeguard.bat test"
    Write-Host ""
}

function Start-CodeGuard {
    Assert-DockerRunning

    if (-not $NoBuild) {
        Package-Backend
    }

    Write-Host "Starting Docker services..."
    docker compose up --build -d
    Wait-BackendHealth
    docker compose ps
    Show-UsageInfo

    if ($Open) {
        Start-Process "http://localhost:3000"
    }
}

function Stop-CodeGuard {
    Assert-DockerRunning
    Write-Host "Stopping Docker services..."
    docker compose down
}

function Test-CodeGuard {
    Assert-CommandExists "mvn"
    Assert-CommandExists "npm"

    Write-Host "Running backend tests..."
    Push-Location -LiteralPath (Join-Path $Root "backend")
    mvn test
    Pop-Location

    Write-Host "Running frontend build..."
    Push-Location -LiteralPath (Join-Path $Root "frontend")
    npm run build
    Pop-Location
}

switch ($Action) {
    "start" {
        Start-CodeGuard
    }
    "stop" {
        Stop-CodeGuard
    }
    "restart" {
        Stop-CodeGuard
        Start-CodeGuard
    }
    "status" {
        Assert-DockerRunning
        docker compose ps
    }
    "logs" {
        Assert-DockerRunning
        docker compose logs -f --tail=120
    }
    "test" {
        Test-CodeGuard
    }
}
