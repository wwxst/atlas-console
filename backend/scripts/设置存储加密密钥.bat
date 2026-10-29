@echo off
setlocal

echo Generating STORAGE_ENCRYPTION_KEY...
for /f "usebackq delims=" %%K in (`powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$bytes = New-Object byte[] 32; $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose(); [Convert]::ToBase64String($bytes)"`) do set "STORAGE_KEY=%%K"

if not defined STORAGE_KEY (
    echo Failed to generate the encryption key.
    exit /b 1
)

setx STORAGE_ENCRYPTION_KEY "%STORAGE_KEY%" >nul
if errorlevel 1 (
    echo Failed to save STORAGE_ENCRYPTION_KEY.
    exit /b 1
)

echo STORAGE_ENCRYPTION_KEY has been saved for the current Windows user.
echo Restart the backend before saving Alibaba Cloud credentials.
echo Keep this key safe. Changing it makes existing encrypted credentials unreadable.
endlocal
