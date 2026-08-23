#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
skills_root="$repo_root/agents/skills"

if [[ ! -f "$skills_root/INDEX.md" ]]; then
  echo "Could not find $skills_root/INDEX.md. Run this script from a repository with agents/skills/INDEX.md." >&2
  exit 1
fi

linked_count=0

for agent in claude gemini codex; do
  agent_dir="$repo_root/.$agent"
  link_path="$agent_dir/skills"

  if ! mkdir -p "$agent_dir"; then
    echo "Could not create $agent_dir" >&2
    continue
  fi

  if [[ -L "$link_path" ]]; then
    rm "$link_path"
  elif [[ -e "$link_path" ]]; then
    if [[ "$(cd "$link_path" && pwd -P)" == "$(cd "$skills_root" && pwd -P)" ]]; then
      echo "$link_path already points to $skills_root"
      continue
    fi

    echo "Refusing to replace existing non-link path: $link_path" >&2
    continue
  fi

  if ! ln -s "../agents/skills" "$link_path"; then
    echo "Could not link $link_path" >&2
    continue
  fi

  linked_count=$((linked_count + 1))
  echo "Linked $link_path -> $skills_root"
done

if [[ "$linked_count" -eq 0 ]]; then
  echo "No agent skill links were created." >&2
  exit 1
fi
