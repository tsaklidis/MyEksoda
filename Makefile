# Builds run on the host with the Gradle wrapper and Android Studio's JBR, not in Docker
# (aapt2 and the emulator are unreliable in Linux arm64 containers).
JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME
GRADLE := ./gradlew
# detekt with type resolution: the debug variant of Android modules, main and test of JVM modules.
DETEKT := detektDebug detektDebugUnitTest :core:engine:detektMain :core:engine:detektTest :core:model:detektMain :core:model:detektTest

.PHONY: build test lint coverage no-telemetry check install install_rc qr_samples format clean \
	codemap release_key bump release_check publish

# Version part bump raises: patch (0.1.0 -> 0.1.1), minor (-> 0.2.0) or major (-> 1.0.0).
BUMP ?= patch
# The branch a pull request merges into, for release_check.
BASE ?= origin/master

build:  ## Debug APK
	$(GRADLE) assembleDebug

test:  ## JVM unit tests: engine and model (JUnit 5), Android modules on Robolectric (JUnit 4)
	$(GRADLE) :core:engine:test :core:model:test testDebugUnitTest

lint:  ## ktlint (Spotless), detekt with type resolution, Android Lint
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug

coverage:  ## Kover thresholds: core:engine >= 95%, core:data >= 92%
	$(GRADLE) :core:engine:koverVerify :core:data:koverVerifyDebug

no-telemetry:  ## Fails if a telemetry, analytics or crash SDK is on the app's classpath
	$(GRADLE) :app:checkNoTelemetry

check:  ## Everything above in one Gradle run. Must pass before a task counts as done.
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug \
		:core:engine:test :core:model:test testDebugUnitTest \
		:core:engine:koverVerify :core:data:koverVerifyDebug \
		:app:checkNoTelemetry assembleDebug

install:  ## Install the debug app on the running emulator or device
	$(GRADLE) installDebug

install_rc:  ## Build the release APK and install it on one device (DEVICE=<serial> if several)
	bash scripts/install_rc.sh $(DEVICE)

qr_samples:  ## On a running emulator or device: the hard QR samples through zxing-cpp, and the settings report
	$(GRADLE) :feature:scan:installDebugAndroidTest
	@out="$$("$${ANDROID_HOME:-$$HOME/Library/Android/sdk}/platform-tools/adb" shell am instrument -w \
		-e class io.eksoda.feature.scan.QrSampleSetTest \
		io.eksoda.feature.scan.test/androidx.test.runner.AndroidJUnitRunner)"; \
		echo "$$out"; echo "$$out" | grep -q '^OK '

format:  ## Apply ktlint formatting
	$(GRADLE) spotlessApply

codemap:  ## Regenerate docs/codemap/SYMBOLS.md (declaration index)
	python3 scripts/codemap.py

release_key:  ## Once: create the release signing key in ~/.eksoda (then back it up)
	bash scripts/release_key.sh

bump:  ## On a PR branch: bump the version (BUMP=patch|minor|major), add its CHANGELOG.md section
	bash scripts/bump_version.sh $(BUMP)

release_check:  ## On a PR branch, before pushing: version bumped from BASE and release notes written (CI runs it)
	bash scripts/check_release.sh $(BASE)

publish:  ## After merging to master: check, build the signed APK, tag and create the GitHub release
	bash scripts/publish_release.sh

clean:
	$(GRADLE) clean
