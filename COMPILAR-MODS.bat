@echo off
rem MineBot 2.16.2 - compila os mods Forge e Fabric (Minecraft 26.3) - o projeto fica em mods\
cd /d "%~dp0"
if not exist "%~dp0mods\COMPILAR-MODS.bat" (echo [ERRO] Nao achei a pasta mods. Extraia o zip inteiro numa pasta nova. & pause & exit /b 1)
call "%~dp0mods\COMPILAR-MODS.bat"
