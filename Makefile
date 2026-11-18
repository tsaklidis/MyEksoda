# Builds run on the host with the Gradle wrapper and Android Studio's JBR, not in Docker
# (aapt2 and the emulator are unreliable in Linux arm64 containers).
JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME
GRADLE := ./gradlew
# detekt with type resolution: the debug variant of Android modules, main and test of JVM modules.
DETEKT := detektDebug detektDebugUnitTest :core:engine:detektMain :core:engine:detektTest :core:model:detektMain :core:model:detektTest

.PHONY: build test lint coverage no-telemetry check install install_rc format clean codemap \
	release_key new_release

# Version part new_release bumps: patch (0.1.0 -> 0.1.1), minor (-> 0.2.0) or major (-> 1.0.0).
BUMP ?= patch

build:  ## Debug APK
	$(GRADLE) assembleDebug

test:  ## JVM unit tests: engine and model (JUnit 5), Android modules on Robolectric (JUnit 4)
	$(GRADLE) :core:engine:test :core:model:test testDebugUnitTest

lint:  ## ktlint (Spotless), detekt with type resolution, Android Lint
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug

# core:data (>= 80%) joins in L3, when it has code to cover. Kover reads 0 of 0 lines as 0%.
coverage:  ## Kover thresholds: core:engine >= 90%
	$(GRADLE) :core:engine:koverVerify

no-telemetry:  ## Fails if a telemetry, analytics or crash SDK is on the app's classpath
	$(GRADLE) :app:checkNoTelemetry

check:  ## Everything above in one Gradle run. Must pass before a task counts as done.
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug \
		:core:engine:test :core:model:test testDebugUnitTest \
		:core:engine:koverVerify \
		:app:checkNoTelemetry assembleDebug

install:  ## Install the debug app on the running emulator or device
	$(GRADLE) installDebug

install_rc:
	$(GRADLE) installRelease

format:  ## Apply ktlint formatting
	$(GRADLE) spotlessApply

codemap:  ## Regenerate docs/codemap/SYMBOLS.md (declaration index)
	python3 scripts/codemap.py

release_key:  ## Once: create the release signing key in ~/.eksoda (then back it up)
	bash scripts/release_key.sh

new_release:  ## Check, bump the version (BUMP=patch|minor|major), build eksoda-<version>.apk in the root
	bash scripts/new_release.sh $(BUMP)

clean:
	$(GRADLE) clean
