
config ?= compileClasspath

ifdef module
mm = :${module}:
else
mm =
endif

NOMAD_ADDR ?= http://localhost:4646
NF_NOMAD_LOCAL_NOMAD_ADDR ?= http://localhost:4646
NF_NOMAD_LOCAL_STACK_DIR ?= ${CURDIR}/../../infrastructure/03_automation/035_terraform/local-nomad-minio
NF_NOMAD_LOCAL_WORK_DIR ?= ${NF_NOMAD_LOCAL_STACK_DIR}/work
INTEGRATION_ARGS ?=
NF_TEST_DIR ?= src/e2e
NF_TEST_ARGS ?= tests/hello-remote.nf.test

#
# Clean, compile, package and install targets
#
clean:
	./gradlew clean
	@echo "✓ Project cleaned"

compile:
	./gradlew compileGroovy
	@echo "✓ Compilation complete `date`"

build: clean compile
	./gradlew build
	@echo "✓ Build complete `date`"

#
# Package the plugin as a JAR file
#
package: build
	./gradlew assemble
	@echo "✓ Plugin packaged as JAR"

#
# Install plugin locally to $HOME/.nextflow/plugins
# This allows Nextflow to use the plugin in development
#
install-local: clean compile
	@echo "Installing nf-nomad plugin locally to ~/.nextflow/plugins..."
	@mkdir -p ${HOME}/.nextflow/plugins
	./gradlew installPlugin --rerun-tasks
	@echo "✓ Plugin installed to ${HOME}/.nextflow/plugins"

#
# Install a development version (99.99.99) for end-to-end testing
#
install-dev: clean compile
	@echo "Installing nf-nomad development version (99.99.99) to ~/.nextflow/plugins..."
	@mkdir -p ${HOME}/.nextflow/plugins
	./gradlew clean installPlugin -Pversion=99.99.99 --rerun-tasks
	@echo "✓ Development version installed to ${HOME}/.nextflow/plugins"

#
# Uninstall the plugin from local installation
#
uninstall-local:
	@echo "Uninstalling nf-nomad plugin from ~/.nextflow/plugins..."
	@rm -rf ${HOME}/.nextflow/plugins/nf-nomad*
	@echo "✓ Plugin uninstalled"

check:
	./gradlew check


#
# Show dependencies try `make deps config=runtime`, `make deps config=google`
#
deps:
	./gradlew -q ${mm}dependencies --configuration ${config}

deps-all:
	./gradlew -q dependencyInsight --configuration ${config} --dependency ${module}

#
# Refresh SNAPSHOTs dependencies
#
refresh:
	./gradlew --refresh-dependencies

#
# Run all tests or selected ones
#
test:
ifndef class
	./gradlew ${mm}test
else
	./gradlew ${mm}test --tests ${class}
endif

#
# Integration test targets — source the cluster env first, then run:
#   make test-mock   (unit + mock tests with MockWebServer)
#   make test-local  (unit + integration against local-nomad-minio)
#   make test-oci    (unit + integration against oci-vm-nomad)
#
test-mock:
	./gradlew ${mm}test -PtestEnv=mock

test-local:
	./gradlew ${mm}test -PtestEnv=local

test-oci:
	./gradlew ${mm}test -PtestEnv=oci
test-integration:
	NOMAD_ADDR=${NF_NOMAD_LOCAL_NOMAD_ADDR} \
	NF_NOMAD_LOCAL_NOMAD_ADDR=${NF_NOMAD_LOCAL_NOMAD_ADDR} \
	NF_NOMAD_LOCAL_STACK_DIR=${NF_NOMAD_LOCAL_STACK_DIR} \
	NF_NOMAD_LOCAL_WORK_DIR=${NF_NOMAD_LOCAL_WORK_DIR} \
	./validation/run-integration.sh --build ${INTEGRATION_ARGS}
# nf-test E2E targets (local Nomad cluster)
nf-test-update:
	cd ${NF_TEST_DIR} && ./nf-test update

test-nf-test:
	@mkdir -p ${NF_NOMAD_LOCAL_WORK_DIR}/assets ${NF_NOMAD_LOCAL_WORK_DIR}/cache
	NOMAD_ADDR=${NF_NOMAD_LOCAL_NOMAD_ADDR} \
	NF_NOMAD_LOCAL_NOMAD_ADDR=${NF_NOMAD_LOCAL_NOMAD_ADDR} \
	NF_NOMAD_LOCAL_STACK_DIR=${NF_NOMAD_LOCAL_STACK_DIR} \
	NF_NOMAD_LOCAL_WORK_DIR=${NF_NOMAD_LOCAL_WORK_DIR} \
	NXF_ASSETS=${NF_NOMAD_LOCAL_WORK_DIR}/assets \
	NXF_CACHE_DIR=${NF_NOMAD_LOCAL_WORK_DIR}/cache \
	cd ${NF_TEST_DIR} && ./nf-test test ${NF_TEST_ARGS} --verbose



#
# Upload JAR artifacts to Maven Central
#
upload:
	./gradlew upload


upload-plugins:
	./gradlew plugins:upload

publish-index:
	./gradlew plugins:publishIndex


#
# Development workflow targets
#
# Use: make install-dev followed by ./launch.sh to test with the plugin
#
dev-setup: install-dev
	@echo ""
	@echo "✓ Development environment ready!"
	@echo ""
	@echo "To test the plugin, run:"
	@echo "  ./launch.sh run main.nf -plugins nf-nomad@99.99.99"
	@echo ""

#
# List installed plugins
#
list-plugins:
	@echo "Installed plugins in ~/.nextflow/plugins:"
	@ls -la ${HOME}/.nextflow/plugins/ 2>/dev/null || echo "No plugins installed yet"

#
# Display help for Makefile targets
#
help:
	@echo "nf-nomad Plugin Build Targets:"
	@echo ""
	@echo "Build & Package Targets:"
	@echo "  make clean              - Clean build artifacts"
	@echo "  make compile            - Compile Groovy sources"
	@echo "  make build              - Clean and build the project"
	@echo "  make package            - Build and package plugin as JAR"
	@echo ""
	@echo "Installation Targets:"
	@echo "  make install-local      - Install plugin to ~/.nextflow/plugins (uses current version)"
	@echo "  make install-dev        - Install development version (99.99.99) for testing"
	@echo "  make uninstall-local    - Remove plugin from ~/.nextflow/plugins"
	@echo "  make dev-setup          - Install dev version and show how to test"
	@echo "  make list-plugins       - List installed plugins"
	@echo ""
	@echo "Test Targets:"
	@echo "  make test               - Run unit tests only"
	@echo "  make test-mock          - Run unit + mock tests"
	@echo "  make test-local         - Run unit + local integration tests"
	@echo "  make test-oci           - Run unit + OCI integration tests"
	@echo "  make test-integration   - Run local real-pipeline integration suite (hello + demo + rnaseq)"
	@echo "  make nf-test-update     - Install/update nf-test runtime under ~/.nf-test"
	@echo "  make test-nf-test       - Run nf-test E2E tests (default: tests/hello-remote.nf.test)"
	@echo ""
	@echo "Other Targets:"
	@echo "  make check              - Run all checks (compile + tests)"
	@echo "  make compile            - Compile sources"
	@echo "  make deps               - Show project dependencies"
	@echo "  make deps-all           - Show detailed dependency information"
	@echo "  make refresh            - Refresh SNAPSHOT dependencies"
	@echo "  make upload             - Upload JAR to Maven Central"
	@echo "  make upload-plugins     - Upload plugins"
	@echo "  make publish-index      - Publish plugin index"
	@echo ""
	@echo "Examples:"
	@echo "  make install-dev                  # Install development version"
	@echo "  make test-local                   # Run local integration tests"
	@echo "  make test-integration             # Run real pipeline integration suite"
	@echo "  make test-integration INTEGRATION_ARGS='--skip-rnaseq'   # Faster local smoke run"
	@echo "  make test-nf-test                                   # Run default nf-test hello E2E"
	@echo "  make test-nf-test NF_TEST_ARGS='tests/*.nf.test'   # Run all nf-test specs"
	@echo "  make dev-setup                    # Setup dev environment with instructions"
	@echo "  make test class=MyTestClass       # Run specific test class"
	@echo ""
