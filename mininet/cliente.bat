@echo off
set /p HOST="Host del servidor (Enter=localhost): "
if "%HOST%"=="" set HOST=localhost
set /p PORT="Puerto (Enter=9090): "
if "%PORT%"=="" set PORT=9090
set /p ID="Tu ID de cliente: "
echo Conectando a %HOST%:%PORT% como %ID%...
java Client %HOST% %PORT% %ID%
pause
