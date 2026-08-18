@echo off
REM Double-click this file to start CodeGuard Agent and open the browser.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\codeguard.ps1" start -Open
