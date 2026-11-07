@echo off
rem MineBot Android 2.9.0 - compila o APK do Android (o projeto fica em android\MineBot-Android)
rem   - horizon.zip ao lado deste arquivo (opcional): as tabelas do jogo vem dele; sem ele o build prepara sozinho
cd /d "%~dp0"
if not exist "%~dp0android\MineBot-Android\COMPILAR.bat" (echo [ERRO] Nao achei android\MineBot-Android. Extraia o zip inteiro numa pasta nova. & pause & exit /b 1)
call "%~dp0android\MineBot-Android\COMPILAR.bat" %*
