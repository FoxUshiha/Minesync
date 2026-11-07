@echo off
rem MinesyncHost 2.1.2 - compila o MinesyncHost.jar (precisa do JDK 21+ no PATH)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out\classes
dir /s /b src\*.java > out\sources.txt
javac --release 21 -encoding UTF-8 -d out\classes -cp lib\gson-2.10.1.jar @out\sources.txt || goto :err
pushd out\classes
jar xf ..\..\lib\gson-2.10.1.jar com
popd
xcopy /e /i /q resources\web out\classes\web >nul
(echo Main-Class: net.foxsrv.host.MinesyncHost& echo Implementation-Title: MinesyncHost& echo Implementation-Version: 2.1.2)> out\manifest.txt
jar cfm MinesyncHost.jar out\manifest.txt -C out\classes . || goto :err
rmdir /s /q out
echo Pronto: MinesyncHost.jar
pause
exit /b 0
:err
echo ERRO na compilacao
pause
exit /b 1
