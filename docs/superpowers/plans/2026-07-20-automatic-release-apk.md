# Automatic Release APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a signed Android release APK automatically after every push to `master` and expose it as a GitHub Actions artifact.

**Architecture:** Add a dedicated push-build workflow so the existing tag-based GitHub Release workflow remains unchanged. The workflow reuses the repository's Flutter version, Android release flavor, and signing secret names.

**Tech Stack:** GitHub Actions, Flutter 3.44.4, Java 17, Gradle, Android APK signing

## Global Constraints

- Trigger on every push to `master` and allow manual dispatch when available.
- Build `prod` release APK for `android-arm64`.
- Require repository secrets for the signing keystore and passwords.
- Upload the APK as an Actions artifact without creating a GitHub Release.

---

### Task 1: Add Push APK Workflow

**Files:**
- Create: `.github/workflows/android-push-apk.yml`

**Interfaces:**
- Consumes: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` repository secrets.
- Produces: `android-release-apk-<short-sha>` Actions artifact containing one signed arm64 APK.

- [ ] **Step 1: Add the workflow trigger and toolchain setup**

Create a workflow triggered by pushes to `master`, using Java 17 and Flutter 3.44.4.

- [ ] **Step 2: Restore signing material securely**

Decode `ANDROID_KEYSTORE_BASE64` into `android/app/upload-keystore.jks` and fail when any signing secret is missing.

- [ ] **Step 3: Build and validate the APK**

Run `flutter build apk --release --flavor prod --target-platform android-arm64`, select the explicit prod release output, and reject files smaller than 5 MiB.

- [ ] **Step 4: Upload the artifact**

Upload `dist/mikcb-*-arm64-v8a.apk` with `actions/upload-artifact`, no compression, and 14-day retention.

- [ ] **Step 5: Validate locally**

Run a YAML parse check and inspect the Git diff.

### Task 2: Configure Repository Signing Secrets

**Files:**
- Read only: `android/key.properties`
- Read only: configured local keystore

**Interfaces:**
- Consumes: the existing local signing configuration.
- Produces: four encrypted GitHub Actions repository secrets.

- [ ] **Step 1: Resolve and verify the configured keystore**

Confirm the keystore exists without printing secret values.

- [ ] **Step 2: Upload encrypted secrets**

Use `gh secret set` to configure the four signing secrets in `stareyeXT/mikcb`.

- [ ] **Step 3: Verify secret names**

Run `gh secret list -R stareyeXT/mikcb` and confirm all required names exist.

### Task 3: Publish and Verify

**Files:**
- Commit: `.github/workflows/android-push-apk.yml`
- Commit: `docs/superpowers/plans/2026-07-20-automatic-release-apk.md`

**Interfaces:**
- Consumes: the completed workflow and configured secrets.
- Produces: a pushed commit and a GitHub Actions run for `master`.

- [ ] **Step 1: Commit the workflow**

Commit with `ci: build release APK on master pushes`.

- [ ] **Step 2: Push to the user repository**

Run `git push fork master`.

- [ ] **Step 3: Verify the Actions run**

Use GitHub CLI to confirm the workflow is recognized and the push-triggered run starts.
