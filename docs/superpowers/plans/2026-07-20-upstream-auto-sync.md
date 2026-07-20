# Upstream Auto Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Periodically merge `Mutx163/mikcb:main` into `stareyeXT/mikcb:master`, then build and publish the rolling release APK when updates were merged.

**Architecture:** A scheduled workflow checks out `master`, fetches the parent repository, performs a normal Git merge, and pushes only when upstream is ahead. Because pushes made with `GITHUB_TOKEN` do not trigger another workflow, the sync workflow explicitly calls the existing APK workflow through `workflow_call`; the APK workflow checks out the latest `master` before building.

**Tech Stack:** GitHub Actions, Git, reusable workflows, Flutter Android release build

## Global Constraints

- Poll upstream every six hours and support manual execution.
- Never force-push or discard local custom commits.
- Stop safely on merge conflicts.
- Build and update `master-latest` only when upstream changes were merged.
- Use `master` as the repository default branch so scheduled workflows run from the customized branch.

---

### Task 1: Make APK Workflow Reusable

**Files:**
- Modify: `.github/workflows/android-push-apk.yml`

- [ ] Add `workflow_call` under `on`.
- [ ] Configure `actions/checkout` with `ref: master` so a called workflow builds the newly pushed merge commit.
- [ ] Parse the YAML and run `git diff --check`.

### Task 2: Add Upstream Sync Workflow

**Files:**
- Create: `.github/workflows/upstream-sync.yml`

- [ ] Trigger at minute 17 every six hours and through `workflow_dispatch`.
- [ ] Check out `master` with full history and configure the Actions bot identity.
- [ ] Fetch `https://github.com/Mutx163/mikcb.git` as `upstream`.
- [ ] Skip when `upstream/main` is already contained in `master`.
- [ ] Merge with `git merge --no-edit upstream/main` and push `HEAD:master` when updated.
- [ ] Call `./.github/workflows/android-push-apk.yml` with inherited signing secrets when the merge step reports an update.

### Task 3: Publish and Verify

**Files:**
- Commit: `.github/workflows/android-push-apk.yml`
- Commit: `.github/workflows/upstream-sync.yml`
- Commit: `docs/superpowers/plans/2026-07-20-upstream-auto-sync.md`

- [ ] Push the commit to `stareyeXT/mikcb:master`.
- [ ] Change the repository default branch to `master`.
- [ ] Manually run `Sync Upstream` and verify it completes without changing the branch when upstream is already synchronized.
