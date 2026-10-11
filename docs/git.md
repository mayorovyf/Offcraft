# Git: branches, integration, and commits

[Русский](ru/git.md)

[Documentation](README.md) · [Project overview](../README.md)

Repository policy for Offcraft source changes, integration and publication.

See also [Agent instructions](../AGENTS.md).

## Branches and integration rules

- `main` is the stable branch. Changes enter it only through a pull request (PR) from `dev`, after verification and review. Direct commits and direct pushes to `main` are prohibited.
- `dev` is the integration branch for the next release and development builds. Do not develop or commit directly on it; merge changes from working branches.
- For each feature, create `feat/<short-name>` from an up-to-date `dev`.
- For fixes, create `fix/<short-name>` from `dev`. Use a separate branch for an independent substantial fix; fixes to an unfinished feature may remain on its `feat/*` branch.
- For documentation, maintenance, and refactoring, use `docs/*`, `chore/*`, and `refactor/*`.
- Use short English names in kebab-case: `feat/jsonl-control`, `fix/capture-release`, `docs/git-workflow`.
- Publishing a working branch is optional. A local merge into `dev` followed by pushing `dev` is allowed; publish working branches when collaboration requires it.
- `release/*` and `hotfix/*` are not currently part of this process. Explicitly introduce rules for them if needed.

Even small changes require a working branch. These rules apply to future work; do not rewrite existing history to make it conform. If `dev` or a remote repository is missing, agree on repository setup before integration rather than silently substituting `main`.

## Workflow

1. Inspect the current branch and working tree. Do not switch branches over someone else's unfinished changes or automatically stash, reset, or clean the tree.
2. Update `dev` and create a working branch. Implement the changes, create commits, and perform initial verification on that branch.
3. Before integration, fetch the current remote `dev`, merge it into the working branch, resolve conflicts, and repeat the affected checks. Do not postpone compatibility verification until the PR into `main`.
4. Merge the complete, verified working branch into `dev` with a merge commit (`--no-ff`). Preserve the feature boundary; do not replace the merge with file copying, cherry-pick, or squash.
5. Check the integrated `dev` for regressions, crashes, and interactions. Follow [verification.md](verification.md) and the time limit below.
6. Open a PR from `dev` into `main` for detailed verification and review. Describe the changes, checks performed, and known limitations. Address review feedback through working branches merged into `dev`.
7. Merge the verified and reviewed PR into `main`. Build releases from the accepted state of `main`.

Coordinate additional merges into `dev` while a release PR is open: subsequent `dev` commits are also included in the PR.

### Verification time limit

The target duration is 10–20 minutes, with a hard limit of **20 minutes for the entire verification cycle**, not for each command. Choose suitable module-level regression checks and a targeted runtime smoke test; do not combine short commands into an hours-long run. Longer calibrations or full platform/scaling runs require explicit user approval. Stop at the limit and report unverified behavior; an interrupted or partial run is not a PASS or full acceptance. Do not weaken assertions, quotas, or acceptance thresholds to obtain a successful run. Documentation-only changes require link/content checks, not launching Minecraft.

### Example of local feature integration

These commands assume a clean working tree, an `origin` remote, and existing local and remote `dev` branches. Perform each step deliberately; stop on errors or conflicts. Create commits, merge, and push only when those operations are authorized within the task.

```bash
git status --short --branch
git fetch origin
git switch dev
git merge --ff-only origin/dev
git switch -c feat/jsonl-control

# Implement and verify; stage only files relevant to the task.
# Commit title: feat(protocol): add JSONL control
# Commit body: list every change included in the commit.

# Update dev and check compatibility before integration.
git fetch origin
git merge origin/dev
# Resolve conflicts, complete the merge, and repeat affected checks.

git switch dev
git merge --ff-only origin/dev
# Set a Conventional Commit title and a complete list of branch changes.
git merge --no-ff feat/jsonl-control
# Verify the integrated dev before publishing.
git push origin dev
```

If `--ff-only` or a push is rejected, investigate the history divergence. Fetch updates, coordinate integration, and repeat affected checks; do not force-push. Do not rewrite published `dev`/`main` history or bypass branch protection. This example does not merge into `main`; that requires a PR.

## Commit format

Use Conventional Commits for ordinary commits and merge commits:

```text
<type>(<scope>)[!]: <description>

<body: complete list of changes>

[footer]
```

- **Type is required:** `feat` (new feature), `fix` (bug fix), `refactor` (structure without behavior changes), `perf` (performance), `docs` (documentation), `test` (tests), `build` (build/dependencies/packaging), `ci` (automation), `chore` (maintenance), or `style` (code formatting only, not interface changes).
- **Scope is required:** the affected subsystem, such as `client`, `protocol`, `ui`, `render`, `mcp`, `fabric`, `forge`, `neoforge`, `build`, or `git`. For cross-cutting changes, use `app` rather than listing every module in the title.
- Write a short English description in the imperative, without a final period: `add`, `fix`, `preserve`, `remove`. Aim for a title no longer than 72 characters; clarity is more important.
- One commit represents one logically related change. Do not mix a feature with unrelated formatting, dependency updates, or someone else's changes.
- **The body is required:** list every included addition, fix, change, and removal as separate items, including code, tests, documentation, build, and configuration. Describe changes, not just filenames or the main feature. Include rationale and limitations when needed; report only checks actually performed.
- For incompatible public contracts or user-facing formats, use `!` and a `BREAKING CHANGE: ...` footer explaining migration. Ordinary internal refactoring does not require this marker.
- Reference an issue in a footer (`Refs: #123`, `Closes: #123`) only with a real issue number.

Example titles:

```text
feat(protocol): add JSONL control
fix(render): preserve texture binding during capture
refactor(app): shorten Java package paths
docs(git): document branch and commit conventions
build(neoforge): update pinned loader version
ci(build): build development tags
```

### Merge commit descriptions

- Use the same `type(scope): description` format, for example `chore(git): merge JSONL control into dev`. Replace default `Merge branch ...` titles and generated PR merge titles.
- The required body lists every change that the incoming branch introduces across all its commits, not just the last one. Before merging, inspect its history and final difference against the target branch; exclude changes already present in the target branch.
- Include code, tests, documentation, build, configuration, and removals. Describe conflict-resolution changes separately. A branch/PR link or the phrase “merge branch” is not a change list.
- Prepare the final PR merge title and body under the same rules; do not rely on repository hosting service defaults.

## Tags and builds

- After merging a PR, tag the verified `main` commit for a release. For development builds, tag a verified `dev`, never an unfinished working branch.
- Release format: `v<major>.<minor>.<patch>`. Development format: `v<major>.<minor>.<patch>-dev.<N>` for the target version, for example `v0.2.0-dev.1`, `v0.2.0-dev.2`, then `v0.2.0`. These are examples, not the project's current version.
- Use annotated tags with a version/purpose description. Never move a published tag; use a new tag for a new build. Push only the explicitly agreed tag, not all local tags.
- The commit, tag, and artifact must correspond to one another. A tag does not update build or MCP versions. Before tagging, update every version declaration listed in [Versioning](development.md#versioning) through a working branch → `dev` → PR into `main` for releases. Final artifact names and the shared `dist/` directory are documented in the same guide.
- Creating a commit, creating a tag, publishing a tag, and publishing a release are separately authorized operations, not automatic task-completion steps.
- A tag or successful build does not establish that the game client, protocol, hidden window, or all loaders work. In release notes, distinguish verified behavior, historical observations, and unverified configurations.

### Current automation

At the time this guide was carried over, this working copy had no GitHub Actions files in `.github/workflows/`. The branch/tag rules above are team policy, not a claim that branch protection, automated development builds, or release publishing are configured. Local build and runtime commands are described in [verification.md](verification.md).

## Preparing the first GitHub publication

Preparation does not create a GitHub repository, upload files or make commits. The URL, visibility and publication are owner decisions. Offcraft uses the [MIT License](../LICENSE); include it in the first commit and retain its copyright/permission notice in distributions. Loader, Maven and MCP metadata must consistently declare MIT; third-party components retain their own licenses.

For a directory without Git history, initialize a working branch from the project root:

```powershell
git init -b chore/github-publication
git status --short --untracked-files=all
git add --dry-run .
```

The checked-in `.gitignore` excludes build outputs, `dist/`, verification images, loader run directories/worlds, Gradle caches, Node dependencies, IDE state, local environment files and credential formats. It explicitly retains all three Gradle wrapper JARs. `.gitattributes` normalizes source text to LF and checks Windows batch scripts out as CRLF. Ignore rules do not remove files already tracked by Git and are not a substitute for reviewing credentials or history.

Before the first commit:

1. Review every file listed by the dry run. Include source, docs, build scripts, wrapper scripts/JARs, metadata, `LICENSE`, `.gitignore` and `.gitattributes`; exclude worlds, profiles, account tokens, launch exports, logs and generated JARs.
2. Check for secrets and oversized files; if a credential has ever been committed, revoke it before publication and deliberately review history removal. Do not just add it to `.gitignore`.
3. Run the relevant checks in [verification.md](verification.md). Build JARs remain in `dist/` and are uploaded as GitHub Release assets, not committed to the source repository.
4. Create the initial commit on `chore/github-publication` only when authorized, using the required Conventional Commit title and full body. Establish `main` and `dev` at that reviewed initial commit as a one-time repository bootstrap; subsequent changes follow the normal working branch → `dev` → PR into `main` rules. Do not overwrite existing branches or history.
5. Create an empty GitHub repository with the chosen visibility; do not generate another README, `.gitignore` or license there. Add its actual URL as `origin`, review the remote, and push only the explicitly authorized branches. Authentication belongs in Git Credential Manager or SSH, never in source files or a token-bearing remote URL.
6. Set `main` as the default branch; configure its PR protection and review requirements in GitHub. Publish versions/tags/releases only under separate authorization. Branch protection and CI are not enabled merely by committing this guide.

Preparation checks only the local candidate file set. After a commit is approved, inspect the staged diff and history before pushing; local checks cannot prove that an existing remote has no private data.
