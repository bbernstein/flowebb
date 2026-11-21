# Adding a platform

Use this checklist for a new client such as `apps/ios/` or `apps/watchos/`.
Do every step in the same PR.

- [ ] **Directory.** Create `apps/<name>/`. Give it a `README.md` (setup and
      commands for humans) and a `.gitignore` covering its build output and any
      local secrets file.
- [ ] **`apps/<name>/CLAUDE.md`.** Include stack and versions, a directory map, exact
      commands (install, lint, test, coverage, build, run, deploy), the coverage
      threshold and whether CI enforces it, and platform rules. Include the exact
      heading `## Never commit secrets or private data`, naming the
      platform's sensitive files (signing certificates, provisioning profiles,
      API keys, `*.xcconfig` with secrets). Commit config as `*.example` / `*.template`.
- [ ] **Agent.** Add `.claude/agents/<name>.md`. Copy an existing one and use the same
      frontmatter (`name`, `description: Use for changes under \`apps/<name>/\` — <stack>;
      dispatch in parallel with other platform agents for cross-platform features`).
      Set its directory scope and list its lint and test commands.
- [ ] **Routing table.** Add a row to the table in the root `CLAUDE.md`, and replace the
      "Future" row if this platform was listed there. Add the platform to the
      commands quick reference.
- [ ] **API usage.** List the GraphQL fields the client reads in its `CLAUDE.md`,
      so backend changes can check them.
- [ ] **CI.** In `.github/workflows/verify.yml`:
  - add an output and a filter for `apps/<name>/**` to the `changes` job
  - add lint/test/build jobs gated on that output (and `workflow_dispatch` / `workflows`, like the others)
  - add each new job to `ci-gate`'s `needs`
- [ ] **Repo rules.** Add `apps/<name>` to `PLATFORMS` in `scripts/check-repo-rules.sh`,
      add any new sensitive-file patterns to `FORBIDDEN`, and run
      `bash scripts/check-repo-rules.test.sh`. Also add the platform to the
      test's `PLATFORMS` list.
- [ ] **Architecture.** Update `docs/architecture.md` (components table and data flow).
- [ ] **Workspace.** Add the folder to `flowebb.code-workspace`.
- [ ] **Decision record.** Add a record to `docs/decisions/` if you made non-obvious
      choices (framework, minimum OS, how it authenticates or caches).
