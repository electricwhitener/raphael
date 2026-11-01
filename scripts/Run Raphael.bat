@echo off
title Raphael - keep this window open while you use it
echo Starting Raphael...
wsl.exe -d Ubuntu -- bash -lc "cd ~/raphael && java -cp 'dist/*' raphael.App"
if errorlevel 1 pause
