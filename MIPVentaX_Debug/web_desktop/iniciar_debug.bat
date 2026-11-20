@echo off
REM Inicia MIPVentaX Web-Desktop con los datos de depuracion pre-sembrados en Windows
chcp 65001 > nul
cd /d "%~dp0"

echo =======================================================
echo   Iniciando MIPVentaX Web-Desktop (Modo Debug)...
echo =======================================================
echo.

python ejecutar_debug.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Probando con el comando 'py'...
    py ejecutar_debug.py
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Probando con 'python3'...
    python3 ejecutar_debug.py
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] No se pudo iniciar el servidor.
    echo Si falta instalar dependencias, ejecuta en tu terminal:
    echo   pip install -r ..\..\Web-Desktop\requirements.txt
    echo.
)
pause
