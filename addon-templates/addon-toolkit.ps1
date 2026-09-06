param(
    [ValidateSet("Menu", "Setup", "Scan", "Validate", "PublishApi", "Build", "BuildAll", "Clean")]
    [string] $Action = "Menu",

    [ValidateSet("minimal", "advanced")]
    [string] $Template = "minimal",

    [string] $ProjectPath,
    [string] $OutputPath,
    [string] $AddonId,
    [string] $AddonName,
    [string] $AddonVersion,
    [string] $Package,
    [string] $Author,
    [string] $Description,
    [string] $MavenGroup,
    [string] $ArchiveName,
    [switch] $Advanced,
    [switch] $DebugStack,
    [switch] $NoPublish,
    [switch] $Yes,
    [switch] $NonInteractive
)

# Forwards to addon-toolkit.py, keeping the old -Action/-Param interface.

$ErrorActionPreference = "Stop"
$toolkit = Join-Path $PSScriptRoot "addon-toolkit.py"
if (-not (Test-Path -LiteralPath $toolkit)) {
    Write-Error "addon-toolkit.py not found next to this launcher."
    exit 1
}

function Resolve-PythonCommand {
    # Probe --version (not just existence) so we skip Windows' Store-alias stub that exits non-zero.
    foreach ($candidate in @(@('py', '-3'), @('python'), @('python3'))) {
        $exe = $candidate[0]
        if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
        $pre = if ($candidate.Count -gt 1) { $candidate[1..($candidate.Count - 1)] } else { @() }
        try { & $exe @pre '--version' *> $null } catch { continue }
        if ($LASTEXITCODE -eq 0) { return , $candidate }
    }
    return $null
}

$python = Resolve-PythonCommand
if (-not $python) {
    Write-Error "Python 3 was not found on PATH. Install Python 3, or use the Gradle tasks (gradlew newAddon, scanAddon, ...)."
    exit 1
}

$actionMap = @{
    Menu = 'menu'; Setup = 'setup'; Scan = 'scan'; Validate = 'validate'
    PublishApi = 'publish-api'; Build = 'build'; BuildAll = 'build-all'; Clean = 'clean'
}

$pyArgs = @($toolkit, $actionMap[$Action], '--template', $Template)
if ($ProjectPath)    { $pyArgs += @('--project', $ProjectPath) }
if ($OutputPath)     { $pyArgs += @('--output', $OutputPath) }
if ($AddonId)        { $pyArgs += @('--addon-id', $AddonId) }
if ($AddonName)      { $pyArgs += @('--name', $AddonName) }
if ($AddonVersion)   { $pyArgs += @('--addon-version', $AddonVersion) }
if ($Package)        { $pyArgs += @('--package', $Package) }
if ($Author)         { $pyArgs += @('--author', $Author) }
if ($Description)    { $pyArgs += @('--description', $Description) }
if ($MavenGroup)     { $pyArgs += @('--maven-group', $MavenGroup) }
if ($ArchiveName)    { $pyArgs += @('--archive-name', $ArchiveName) }
if ($Advanced)       { $pyArgs += '--advanced' }
if ($NoPublish)      { $pyArgs += '--no-publish' }
if ($Yes)            { $pyArgs += '--yes' }
if ($NonInteractive) { $pyArgs += '--non-interactive' }
if ($DebugStack)     { $pyArgs += '--debug-stack' }

$pyExe = $python[0]
$pyPre = if ($python.Count -gt 1) { $python[1..($python.Count - 1)] } else { @() }
& $pyExe @pyPre @pyArgs
exit $LASTEXITCODE
