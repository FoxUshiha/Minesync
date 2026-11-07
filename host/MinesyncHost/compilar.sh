#!/bin/sh
# MinesyncHost 2.1.2 - compila o MinesyncHost.jar (Java 21+). Uso: sh compilar.sh
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out/classes
javac --release 21 -encoding UTF-8 -d out/classes -cp lib/gson-2.10.1.jar $(find src -name '*.java')
(cd out/classes && jar xf ../../lib/gson-2.10.1.jar com && rm -rf META-INF)
cp -r resources/web out/classes/web
printf 'Main-Class: net.foxsrv.host.MinesyncHost\nImplementation-Title: MinesyncHost\nImplementation-Version: 2.1.2\n' > out/manifest.txt
jar cfm MinesyncHost.jar out/manifest.txt -C out/classes .
rm -rf out
echo "Pronto: MinesyncHost.jar"
