#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
skills_root="$repo_root/agents/skills"
source_repository="${SOURCE_REPOSITORY:-https://github.com/mattpocock/skills.git}"
source_ref="${SOURCE_REF:-main}"
temp_root="$(mktemp -d)"

cleanup() {
  rm -rf "$temp_root"
}
trap cleanup EXIT

git clone --depth 1 --filter=blob:none --sparse --branch "$source_ref" "$source_repository" "$temp_root"

git -C "$temp_root" sparse-checkout set \
  skills/engineering/improve-codebase-architecture \
  skills/engineering/tdd \
  skills/productivity/grill-me \
  skills/productivity/teach

copy_skill() {
  local source_path="$1"
  local target_name="$2"
  local target_path="$skills_root/$target_name"

  case "$target_path" in
    "$skills_root"/*) ;;
    *)
      echo "Refusing to update path outside $skills_root: $target_path" >&2
      exit 1
      ;;
  esac

  rm -rf "$target_path"
  cp -R "$temp_root/$source_path" "$target_path"
}

copy_skill skills/engineering/improve-codebase-architecture improve-codebase-architecture
copy_skill skills/engineering/tdd tdd
copy_skill skills/productivity/grill-me grill-me
copy_skill skills/productivity/teach teach

cp "$temp_root/LICENSE" "$skills_root/LICENSE"
git -C "$temp_root" rev-parse HEAD > "$skills_root/SOURCE_COMMIT"

echo "Synced mattpocock/skills at $(cat "$skills_root/SOURCE_COMMIT")"
