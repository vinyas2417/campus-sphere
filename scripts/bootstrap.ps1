$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolDirectory = Join-Path $projectRoot '.tools'
$archivePath = Join-Path $toolDirectory 'apache-maven-3.9.11-bin.zip'
$distributionUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
New-Item -ItemType Directory -Force -Path $toolDirectory | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $toolDirectory 'apache-maven-3.9.11/bin/mvn.cmd'))) {
    Write-Host 'Downloading Apache Maven 3.9.11 into the project...'
    Invoke-WebRequest -Uri $distributionUrl -OutFile $archivePath -UseBasicParsing
    $expectedHash = (Invoke-WebRequest -Uri "$distributionUrl.sha512" -UseBasicParsing).Content.Trim().Split(' ')[0]
    $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA512).Hash
    if ($actualHash -ne $expectedHash) { throw 'Maven download checksum verification failed.' }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolDirectory -Force
}
