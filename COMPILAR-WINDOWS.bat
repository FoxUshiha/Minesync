@echo off
rem MineBot 2.16.2 - compila o launcher do Windows (o projeto fica em launcher\MineBot-Launcher)
rem   - horizon.zip ao lado deste arquivo: o launcher ja vem com o Horizon (sem baixar/compilar de novo)
rem   - mods\out\minebot-*.jar: o launcher poe o mod MineBot sozinho no Fabric/Forge 26.3
cd /d "%~dp0"
if not exist "%~dp0launcher\MineBot-Launcher\COMPILAR.bat" (echo [ERRO] Nao achei launcher\MineBot-Launcher. Extraia o zip inteiro numa pasta nova. & pause & exit /b 1)
if exist "%~dp0horizon.zip" (echo horizon.zip encontrado: vai junto com o launcher.) else (echo [info] sem horizon.zip ao lado deste arquivo: o launcher baixa/compila o Horizon na 1a vez.)
dir /b "%~dp0mods\out\minebot-*.jar" >nul 2>&1
if errorlevel 1 if exist "%~dp0mods\COMPILAR-MODS.bat" (
  echo.
  echo O mod MineBot ^(Fabric/Forge 26.3^) ainda nao foi compilado.
  choice /C SN /T 15 /D S /M "Compilar os mods agora para o launcher ja vir com eles? (S/N, 15 s = Sim)"
  if not errorlevel 2 call "%~dp0mods\COMPILAR-MODS.bat" nopause
)
call "%~dp0launcher\MineBot-Launcher\COMPILAR.bat"
