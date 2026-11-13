param(
    [string]$DbUrl = 'jdbc:mysql://127.0.0.1:13307/linkstamp?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
    [string]$DbUser = 'linkstamp',
    [Parameter(Mandatory)][securestring]$DbPassword,
    [securestring]$EncryptKey
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root '.runtime'
$file = Join-Path $runtime 'sharding.yaml'
if (Test-Path -LiteralPath $file) {
    throw 'The local sharding configuration already exists. Keep its encryption key when changing the database connection.'
}
$password = [System.Net.NetworkCredential]::new('', $DbPassword).Password
if (-not $password) { throw 'Provide a password for the project database user.' }
$key = if ($EncryptKey) { [System.Net.NetworkCredential]::new('', $EncryptKey).Password } else { [guid]::NewGuid().ToString('N') }
if ([Text.Encoding]::UTF8.GetByteCount($key) -notin 16,24,32) { throw 'Use a 16, 24, or 32 byte encryption key.' }
$values = @{ DB_URL = $DbUrl; DB_USER = $DbUser; DB_PASSWORD = $password; ENCRYPT_KEY = $key }
foreach ($value in $values.Values) {
    if ($value -match '[\r\n]') { throw 'Database settings must be single-line values.' }
}
$template = [IO.File]::ReadAllText((Join-Path $root 'resources\sharding-local.example.yaml'))
$rendered = [regex]::Replace($template, '@(DB_URL|DB_USER|DB_PASSWORD|ENCRYPT_KEY)@', {
    param($match)
    $values[$match.Groups[1].Value].Replace("'", "''")
})
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
[IO.File]::WriteAllText($file, $rendered)
Write-Output "Created $file"
