# Agent Skills Index

This directory is the single, neutral source of agent skills adopted by
`puccomp-api`. Do not copy these skills into `.claude/`, `.codex/`, `.gemini/`,
or any other agent-specific directory. Agent-specific loaders should point back
to this directory.

## Adopted Skills

| Skill | Category | Local path | Origin |
| --- | --- | --- | --- |
| `improve-codebase-architecture` | Engineering | `agents/skills/improve-codebase-architecture/` | `mattpocock/skills:skills/engineering/improve-codebase-architecture` |
| `tdd` | Engineering | `agents/skills/tdd/` | `mattpocock/skills:skills/engineering/tdd` |
| `grill-me` | Productivity | `agents/skills/grill-me/` | `mattpocock/skills:skills/productivity/grill-me` |
| `teach` | Productivity | `agents/skills/teach/` | `mattpocock/skills:skills/productivity/teach` |

Upstream repository: <https://github.com/mattpocock/skills>

Current vendored commit: see `agents/skills/SOURCE_COMMIT`.

The upstream project documents two installation approaches: Claude Code can use
the managed `mattpocock-skills` plugin, while Codex and other agents can use
`npx skills@latest add mattpocock/skills`. For this repository we intentionally
use a vendored copy instead, so every agent reads the same reviewed files from
source control.

## Setup

Run one of these commands from the repository root to create agent-specific
links back to this directory:

```powershell
.\scripts\setup-agent-skills.ps1
```

```sh
bash scripts/setup-agent-skills.sh
```

The setup scripts create these loader paths without duplicating content:

| Agent | Loader path | Target |
| --- | --- | --- |
| Claude | `.claude/skills` | `agents/skills` |
| Codex | `.codex/skills` | `agents/skills` |
| Gemini | `.gemini/skills` | `agents/skills` |

`CLAUDE.md`, `GEMINI.md`, and `AGENTS.md` also point agents to this index.

## Update Strategy

Strategy: **vendored copy**. The skill files are committed in this repository so
reviews, CI, and every agent see the same skill version. Updates are explicit:

```powershell
.\scripts\sync-agent-skills.ps1
```

```sh
bash scripts/sync-agent-skills.sh
```

After syncing, review the diff and commit the changed files together with the
updated `agents/skills/SOURCE_COMMIT`.

## Stateless Policy

Only skill definitions and their static support files are versioned here. Do
not commit per-user or per-session state generated while using a skill. In
particular, `teach` may create lesson/workspace files when invoked; those are
runtime artifacts unless the team explicitly decides to version a course.
