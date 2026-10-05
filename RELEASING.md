# Releasing

Atomic Habit follows [Semantic Versioning](https://semver.org/). While the major version is `0`,
minor releases may contain breaking changes; they are called out in `CHANGELOG.md`.

Releases are cut from `master` and published by `.github/workflows/release.yml` when a tag is pushed.

## Checklist

1. **Start from a green `master`.** CI and CodeQL pass on the commit you want to release.
2. **Set the version** (example: `0.2.0`) in all three places; the release workflow refuses tags that
   don't match:
   ```bash
   cd backend && ./mvnw -q versions:set -DnewVersion=0.2.0 -DgenerateBackupPoms=false && cd ..
   npm --prefix frontend version 0.2.0 --no-git-tag-version
   ```
   Also set `project.build.outputTimestamp` in `backend/pom.xml` to the release date (reproducible
   builds).
3. **Finalize `CHANGELOG.md`**: rename `## [Unreleased]` to `## [0.2.0] - YYYY-MM-DD`, add a fresh
   empty `## [Unreleased]` above it, and update the compare links at the bottom. The section body
   becomes the GitHub Release notes verbatim.
4. **Open a release PR** (`chore(release): 0.2.0`), get it reviewed, and merge it.
5. **Tag the merge commit and push the tag**:
   ```bash
   git switch master && git pull
   git tag -a v0.2.0 -m "Atomic Habit 0.2.0"
   git push origin v0.2.0
   ```
6. **Watch the Release workflow.** It verifies the versions, runs the full test suite, builds the
   backend jar and frontend bundle, publishes multi-arch images
   `ghcr.io/inwardflow/atomic-habit-backend` / `-frontend` (`X.Y.Z`, `X.Y`, `latest`), attaches build
   provenance attestations, and creates the GitHub Release with assets and `SHA256SUMS`.
7. **Open the next development cycle**: bump to the next `-SNAPSHOT` on `master`
   (e.g. `0.3.0-SNAPSHOT`; npm uses `0.3.0-dev`) in a `chore: start 0.3.0 development` commit.

Pre-releases use a suffix tag such as `v0.2.0-rc.1`. They are marked as pre-release on GitHub and
only receive their exact image tag (not `latest`).

## Verifying an artifact

```bash
gh attestation verify atomic-habits-backend-0.1.0.jar --repo inwardflow/atomic-habit
gh attestation verify oci://ghcr.io/inwardflow/atomic-habit-backend:0.1.0 --repo inwardflow/atomic-habit
```

## If a release goes wrong

Never move or reuse a published tag. Delete the GitHub Release if needed, fix forward on `master`,
and release the next patch version.
