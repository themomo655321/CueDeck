@echo off
title CueDeck - Push to GitHub
cd /d "%~dp0"

echo =======================================================
echo          CueDeck - Push to GitHub for APK Build
echo =======================================================
echo.

git remote remove origin 2>nul
git remote add origin https://github.com/themomo655321/CueDeck.git
git branch -M main

echo Pushing code to https://github.com/themomo655321/CueDeck.git ...
echo (A browser window may open to authorize GitHub)
echo.

git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =======================================================
    echo SUCCESS! Code pushed to GitHub.
    echo GitHub Actions is now building your Android APK!
    echo.
    echo Opening GitHub Actions in your browser...
    echo =======================================================
    start https://github.com/themomo655321/CueDeck/actions
) else (
    echo.
    echo =======================================================
    echo ERROR: Could not push to GitHub.
    echo Please make sure you created the repo "CueDeck" at:
    echo https://github.com/new
    echo =======================================================
    start https://github.com/new
)

echo.
pause
