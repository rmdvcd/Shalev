$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# El intérprete debe ser el que usa el proyecto (Python 3.11 o 3.12). El lanzador
# `py` puede apuntar a una versión no soportada —en el CI resolvía 3.15— y entonces
# PyInstaller falla sin que el script se enterara. Se comprueba la versión y se
# detiene con un mensaje claro. En el workflow, `python` es el de setup-python.
$python = if (Get-Command python -ErrorAction SilentlyContinue) { 'python' } else { 'py' }
$version = (& $python -c "import sys; print('%d.%d' % sys.version_info[:2])").Trim()
if ($LASTEXITCODE -ne 0 -or -not $version) {
  throw "No se pudo ejecutar '$python'. Instala Python 3.11 o 3.12 y vuelve a intentarlo."
}
if ($version -notin @('3.11', '3.12')) {
  throw "Se requiere Python 3.11 o 3.12; '$python' es $version. Instala esa versión o ponla antes en el PATH."
}

& $python -m venv .venv
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el entorno virtual (código $LASTEXITCODE)." }

$venvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$venvVersion = (& $venvPython -c "import sys; print('%d.%d' % sys.version_info[:2])").Trim()
if ($LASTEXITCODE -ne 0) { throw "No se pudo ejecutar el Python del entorno virtual." }
if ($venvVersion -notin @('3.11', '3.12')) {
  throw "El entorno DShalev\.venv usa Python $venvVersion. Bórralo y repite el script con Python 3.11 o 3.12."
}

& $venvPython -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "No se pudo actualizar pip (código $LASTEXITCODE)." }

& $venvPython -m pip install -r requirements.txt pyinstaller==6.13.0
if ($LASTEXITCODE -ne 0) { throw "No se pudieron instalar las dependencias (código $LASTEXITCODE)." }

& $venvPython -m PyInstaller --noconfirm --clean --onefile --noconsole `
  --name DShalev `
  --add-data 'dshalev/templates;dshalev/templates' `
  --add-data 'dshalev/static;dshalev/static' `
  --collect-all jinja2 `
  --collect-all uvicorn `
  --collect-all fastapi `
  --collect-all cryptography `
  DShalev.py
if ($LASTEXITCODE -ne 0) { throw "PyInstaller falló con código $LASTEXITCODE." }
if (-not (Test-Path 'dist\DShalev.exe')) { throw "PyInstaller terminó sin crear dist\DShalev.exe." }

Write-Host 'Ejecutable portable creado en dist\DShalev.exe' -ForegroundColor Green
