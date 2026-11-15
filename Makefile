# Builds run on the host with the Gradle wrapper and Android Studio's JBR, not in Docker
# (docs/android-standalone.md §12.2).
JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME
GRADLE := ./gradlew
SERVER ?= $(HOME)/eksoda
# detekt with type resolution: the debug variant of Android modules, main and test of JVM modules.
DETEKT := detektDebug detektDebugUnitTest :core:engine:detektMain :core:engine:detektTest :core:model:detektMain :core:model:detektTest

.PHONY: build test lint coverage no-telemetry check install sync-testdata format clean

build:  ## Debug APK
	$(GRADLE) assembleDebug

test:  ## JVM unit tests: engine and model (JUnit 5), Android modules on Robolectric (JUnit 4)
	$(GRADLE) :core:engine:test :core:model:test testDebugUnitTest

lint:  ## ktlint (Spotless), detekt with type resolution, Android Lint
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug

# core:data (>= 80%) joins in L3, when it has code to cover. Kover reads 0 of 0 lines as 0%.
coverage:  ## Kover thresholds: core:engine >= 90% (§10.3)
	$(GRADLE) :core:engine:koverVerify

no-telemetry:  ## Fails if a telemetry, analytics or crash SDK is on the app's classpath (§8.1)
	$(GRADLE) :app:checkNoTelemetry

check:  ## Everything above in one Gradle run. Must pass before a task counts as done.
	$(GRADLE) spotlessCheck $(DETEKT) lintDebug \
		:core:engine:test :core:model:test testDebugUnitTest \
		:core:engine:koverVerify \
		:app:checkNoTelemetry assembleDebug

install:  ## Install the debug app on the running emulator or device
	$(GRADLE) installDebug

sync-testdata:  ## Copy fixtures and vectors from the server checkout (§10.1)
	scripts/sync-server-testdata.sh $(SERVER)

format:  ## Apply ktlint formatting
	$(GRADLE) spotlessApply

clean:
	$(GRADLE) clean
