$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$python = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } else { 'python' }
& $python -m venv .venv
& .\.venv\Scripts\python.exe -m pip install --upgrade pip
& .\.venv\Scripts\python.exe -m pip install -r requirements.txt pyinstaller==6.13.0
& .\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onefile --noconsole `
  --name DShalev `
  --add-data 'dshalev/templates;dshalev/templates' `
  --add-data 'dshalev/static;dshalev/static' `
  --collect-all jinja2 `
  --collect-all uvicorn `
  --collect-all fastapi `
  --collect-all cryptography `
  DShalev.py
Write-Host 'Ejecutable portable creado en dist\DShalev.exe' -ForegroundColor Green
