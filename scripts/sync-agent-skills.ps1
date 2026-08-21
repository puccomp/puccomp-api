[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string] $SourceRepository = "https://github.com/mattpocock/skills.git",
    [string] $SourceRef = "main"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$skillsRoot = Join-Path $RepositoryRoot "agents/skills"
$resolvedSkillsRoot = (Resolve-Path -LiteralPath $skillsRoot).Path
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("mattpocock-skills-" + [guid]::NewGuid().ToString("N"))

$skills = @(
    @{ Source = "skills/engineering/improve-codebase-architecture"; Target = "improve-codebase-architecture" },
    @{ Source = "skills/engineering/tdd"; Target = "tdd" },
    @{ Source = "skills/productivity/grill-me"; Target = "grill-me" },
    @{ Source = "skills/productivity/teach"; Target = "teach" }
)

try {
    git clone --depth 1 --filter=blob:none --sparse --branch $SourceRef $SourceRepository $tempRoot
    git -C $tempRoot sparse-checkout set @($skills | ForEach-Object { $_.Source })

    foreach ($skill in $skills) {
        $targetPath = Join-Path $skillsRoot $skill.Target
        $parent = (Resolve-Path -LiteralPath (Split-Path -Parent $targetPath)).Path

        if ($parent -ne $resolvedSkillsRoot) {
            throw "Refusing to update path outside ${resolvedSkillsRoot}: $targetPath"
        }

        if (Test-Path -LiteralPath $targetPath) {
            Remove-Item -Recurse -Force -LiteralPath $targetPath
        }

        Copy-Item -Recurse -Force -Path (Join-Path $tempRoot $skill.Source) -Destination $targetPath
    }

    Copy-Item -Force -Path (Join-Path $tempRoot "LICENSE") -Destination (Join-Path $skillsRoot "LICENSE")

    $commit = (git -C $tempRoot rev-parse HEAD).Trim()
    Set-Content -NoNewline -Path (Join-Path $skillsRoot "SOURCE_COMMIT") -Value $commit

    Write-Host "Synced mattpocock/skills at $commit"
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -Recurse -Force -LiteralPath $tempRoot
    }
}
