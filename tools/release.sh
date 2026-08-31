#!/usr/bin/env bash
#
# Build, verify and tag a release. Does NOT publish.
#
# The last step of the documented checklist -- the tag -- has been skipped on
# four of nine releases and fixed by hand afterwards each time. Ordering
# discipline did not survive contact (BUILD_NOTES 8.8), so this checks the
# invariants instead of asking anyone to remember them.
#
# It stops before `gh release create` and prints the command. That boundary is
# deliberate and matches SESSION-HANDOFF section 4: building, tagging and
# verifying are fine unprompted; publishing is not. Do not add a --publish flag.
#
#   bash tools/release.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

RELEASES_REPO="AR13X3/Jarvis_2.0"
# Every release since 0.1.1 carries this. A change here is the one failure that
# cannot be repaired after the fact: Obtainium would have to uninstall to
# update, and uninstalling takes the paired token and the alarm mirror with it.
EXPECTED_CERT="54e25abc199e05db4227452c8417fb37fb4d2db837ee97a9d94d414dc550cd7d"

GH="${GH:-/c/Users/ahmed/Tools/bin/gh.exe}"
SDK="${ANDROID_HOME:-/c/Users/ahmed/AppData/Local/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"

die() { printf '\n  STOP: %s\n\n' "$1" >&2; exit 1; }
step() { printf '\n== %s\n' "$1"; }

# Newest build-tools wins; apksigner and aapt2 both live there.
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || die "no build-tools under $SDK"
APKSIGNER="$BT/apksigner.bat"
AAPT2="$BT/aapt2.exe"

# --- what are we releasing -----------------------------------------------------

VERSION="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' app/build.gradle.kts)"
[ -n "$VERSION" ] || die "could not read appVersionName from app/build.gradle.kts"
TAG="v$VERSION"

IFS=. read -r MAJ MIN PATCH <<<"$VERSION"
CODE=$(( MAJ * 10000 + MIN * 100 + PATCH ))

step "Releasing $TAG (versionCode $CODE)"

# --- refuse before building, not after -----------------------------------------

# A dirty tree is the 0.1.1-0.1.3 bug: GIT_SHA names a commit that does not
# contain the build. The -dirty suffix announces it, but a release should never
# get that far.
[ -z "$(git status --porcelain)" ] || die "working tree is dirty -- commit the bump first (BUILD_NOTES 8.8)"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
  && die "$TAG already exists locally -- bump appVersionName, or delete the tag if it was wrong"

if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
  die "$TAG already exists on origin"
fi

step "Checking $CODE beats what is published"
LAST="$("$GH" api "repos/$RELEASES_REPO/releases" --jq '.[0].tag_name' 2>/dev/null || echo "")"
if [ -n "$LAST" ]; then
  IFS=. read -r LMAJ LMIN LPATCH <<<"${LAST#v}"
  LCODE=$(( LMAJ * 10000 + LMIN * 100 + LPATCH ))
  printf '   published: %s (%s)   this: %s (%s)\n' "$LAST" "$LCODE" "$TAG" "$CODE"
  # Arithmetic, not string order -- 0.1.10 beats 0.1.9 only when compared this
  # way, and 0.1.10 was the first release where the two disagreed.
  [ "$CODE" -gt "$LCODE" ] || die "versionCode $CODE does not beat the published $LCODE"
else
  printf '   no published release found -- skipping the comparison\n'
fi

# --- build ---------------------------------------------------------------------

step "Building"
./gradlew :app:assembleRelease --console=plain -q

APK="app/build/outputs/release/jarvis-$TAG.apk"
[ -f "$APK" ] || die "expected $APK -- did renameReleaseApk run?"
printf '   %s (%s bytes)\n' "$APK" "$(wc -c <"$APK")"

# --- verify the artifact, not the intention ------------------------------------

step "Signature"
CERT="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null \
  | sed -n 's/.*certificate SHA-256 digest: *//p' | head -1)"
[ "$CERT" = "$EXPECTED_CERT" ] || die "cert is $CERT, expected $EXPECTED_CERT -- this would not install in place"
printf '   %s  matches every prior release\n' "$CERT"

step "Version inside the APK"
BADGING="$("$AAPT2" dump badging "$APK" 2>/dev/null | head -1)"
APK_CODE="$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$BADGING")"
APK_NAME="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")"
[ "$APK_CODE" = "$CODE" ] || die "APK says versionCode $APK_CODE, expected $CODE"
[ "$APK_NAME" = "$VERSION" ] || die "APK says versionName $APK_NAME, expected $VERSION"
printf '   versionName=%s versionCode=%s\n' "$APK_NAME" "$APK_CODE"

# The check that would have caught 0.1.1-0.1.3 directly. Ordering is a proxy for
# this; this is the thing itself, read back out of the artifact.
step "Embedded commit"
HEAD_SHA="$(git rev-parse --short HEAD)"
if unzip -p "$APK" 'classes*.dex' | grep -aqF "$HEAD_SHA"; then
  printf '   %s -- the APK contains the commit it names\n' "$HEAD_SHA"
else
  die "the APK does not embed HEAD ($HEAD_SHA) -- it was built from something else"
fi

# --- tag -----------------------------------------------------------------------

step "Tagging"
git tag -a "$TAG" -m "$TAG — versionCode $CODE, built from $HEAD_SHA."
git push -q origin "$TAG"
printf '   %s -> %s, pushed\n' "$TAG" "$HEAD_SHA"

# --- stop ----------------------------------------------------------------------

cat <<EOF

== Built, verified and tagged. Nothing has been published.

Write the notes first: the release body is an app surface, rendered inline-only
at six lines (BUILD_NOTES 8.9). Summary first, no bullets, no headings.

  "$GH" release create $TAG --repo $RELEASES_REPO -F notes.md "$APK"

Then confirm Obtainium installs it in place. Being asked to uninstall first
means the signature changed -- stop, do not accept the uninstall.
EOF
