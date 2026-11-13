@echo off
rem Acceso de compatibilidad desde la raíz del repositorio.
rem La configuración y el wrapper Gradle del proyecto Android viven en Android\.
setlocal
set "REPO_ROOT=%~dp0"
call "%REPO_ROOT%Android\gradlew.bat" -p "%REPO_ROOT%Android" %*
exit /b %ERRORLEVEL%
