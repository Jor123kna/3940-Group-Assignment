@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Back-End\Test-Runner\run-tests.ps1" %*
exit /b %ERRORLEVEL%
