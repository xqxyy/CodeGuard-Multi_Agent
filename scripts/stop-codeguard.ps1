$ScriptPath = if ($PSCommandPath) { $PSCommandPath } else { $MyInvocation.MyCommand.Definition }
$ScriptDir = if ($PSScriptRoot) {
    $PSScriptRoot
} elseif ($ScriptPath -and (Test-Path -LiteralPath $ScriptPath)) {
    Split-Path -Parent $ScriptPath
} else {
    Join-Path (Get-Location).Path "scripts"
}

& (Join-Path $ScriptDir "codeguard.ps1") stop
