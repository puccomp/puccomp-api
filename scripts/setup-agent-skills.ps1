[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string[]] $Agents = @("claude", "gemini", "codex")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$skillsRoot = (Resolve-Path (Join-Path $RepositoryRoot "agents/skills")).Path
$indexPath = Join-Path $skillsRoot "INDEX.md"

if (-not (Test-Path -LiteralPath $indexPath)) {
    throw "Could not find $indexPath. Run this script from a repository with agents/skills/INDEX.md."
}

function New-AgentSkillsLink {
    param([string] $AgentName)

    $agentDir = Join-Path $RepositoryRoot ".$AgentName"
    $linkPath = Join-Path $agentDir "skills"

    New-Item -ItemType Directory -Force -Path $agentDir | Out-Null

    if (Test-Path -LiteralPath $linkPath) {
        $item = Get-Item -LiteralPath $linkPath -Force
        $isReparsePoint = ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0

        if ($isReparsePoint) {
            Remove-Item -LiteralPath $linkPath -Force
        }
        else {
            $resolved = (Resolve-Path -LiteralPath $linkPath).Path
            if ($resolved -eq $skillsRoot) {
                Write-Host "$linkPath already points to $skillsRoot"
                return
            }

            throw "Refusing to replace existing non-link path: $linkPath"
        }
    }

    try {
        New-Item -ItemType SymbolicLink -Path $linkPath -Target $skillsRoot | Out-Null
    }
    catch {
        New-Item -ItemType Junction -Path $linkPath -Target $skillsRoot | Out-Null
    }

    Write-Host "Linked $linkPath -> $skillsRoot"
}

$linkedCount = 0

foreach ($agent in $Agents) {
    try {
        New-AgentSkillsLink -AgentName $agent
        $linkedCount += 1
    }
    catch {
        Write-Warning "Could not link .$agent/skills: $($_.Exception.Message)"
    }
}

if ($linkedCount -eq 0) {
    throw "No agent skill links were created."
}
