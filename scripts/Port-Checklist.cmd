@echo off
setlocal
chcp 65001 >nul
set "PYTHONUTF8=1"
title Arcadium Port Checklist
where py >nul 2>nul
if not errorlevel 1 (
    py -3 "%~dp0port_checklist.py" %*
    goto finish
)
where python >nul 2>nul
if not errorlevel 1 (
    python "%~dp0port_checklist.py" %*
    goto finish
)
echo Python 3 is required to run this checklist.
pause
exit /b 1
:finish
if errorlevel 1 pause
endlocal
