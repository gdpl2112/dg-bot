@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
git pull
java -Dfile.encoding=UTF-8 -classpath ".\src\main.jar;.\target\dependency\*" io.github.gdpl2112.dg_bot.DgMain
if errorlevel 1 pause
