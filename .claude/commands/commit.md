---
name: commit
description: Create a git commit message following the Conventional Commits pattern, analyzing staged changes
argument-hint: [optional hint or context about the change]
allowed-tools:
  - Bash
  - AskUserQuestion
---

<objective>
Analyze the staged git changes and craft a precise, well-structured commit message following the Conventional Commits specification. Execute the commit after user confirmation.
</objective>

<conventional_commits_spec>
Format:
```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types:**
- `feat`: new feature
- `fix`: bug fix
- `refactor`: code change that neither fixes a bug nor adds a feature
- `style`: formatting, whitespace, missing semicolons — no logic change
- `test`: adding or updating tests
- `docs`: documentation only
- `build`: build system or dependency changes
- `ci`: CI/CD configuration changes
- `chore`: dev tooling, config files, project setup — no production code impact
- `perf`: performance improvements
- `revert`: reverts a previous commit

**Subject rules:**
- Imperative mood: "add feature" not "added feature" or "adds feature"
- No capital first letter
- No period at the end
- Max ~72 characters
- Think: "If applied, this commit will: <subject>"

**Scope** (optional):
- Lowercase, concise context (e.g., `auth`, `api`, `db`, `ui`)
- Multiple scopes separated by `/` (e.g., `web/mobile`)

**Breaking changes:**
- Add `!` after type/scope: `feat(auth)!: change token format`
- Add `BREAKING CHANGE: <description>` in footer

**Body** (optional):
- Explain *what* and *why*, not *how*
- Wrap at 72 characters

**Footer** (optional):
- `BREAKING CHANGE: <description>`
- `Closes #<issue>`, `Fixes #<issue>`, `Refs #<issue>`
</conventional_commits_spec>

<process>
1. **Inspect staged changes**
   Run `git diff --staged` (and `git status` for the file list). If nothing is staged, run `git status` and inform the user — suggest they stage changes with `git add` first.

2. **Analyze the diff**
   - Identify what changed: new files, modified logic, config updates, etc.
   - Determine the most appropriate type
   - Identify a scope if the change is clearly scoped to one area
   - Check for breaking changes

3. **Consider user hint** (if `$ARGUMENTS` was provided)
   Use it as additional context to refine the message. Do NOT use it verbatim as the subject.

4. **Draft the commit message**
   - Craft a clear, imperative subject
   - Add a body only if the change is non-trivial and the why isn't obvious from the subject
   - Add footer entries if applicable (breaking changes, issue references)

5. **Present and confirm**
   Show the full proposed commit message in a code block. Ask the user:
   - To confirm and run the commit
   - Or to provide feedback to refine it

6. **Execute on confirmation**
   Run `git commit -m "<subject>"` for single-line messages, or use a heredoc / `-m` chaining for multi-line:
   ```
   git commit -m "<subject>" -m "<body>" -m "<footer>"
   ```
   Report the resulting commit hash.
</process>

<important>
- Never commit without explicit user confirmation
- If the diff is very large, summarize the key changes rather than reading every line
- Prefer specificity over vagueness: "add JWT expiry validation to isAuth middleware" beats "update auth"
- If the changes span multiple unrelated concerns, warn the user and suggest splitting into separate commits
</important>
