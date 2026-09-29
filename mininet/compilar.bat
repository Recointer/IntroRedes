@echo off
echo Compilando MiniNet...
javac Server.java Client.java
if %ERRORLEVEL% == 0 (
    echo [OK] Compilacion exitosa.
) else (
    echo [ERROR] Fallo la compilacion.
)
pause
