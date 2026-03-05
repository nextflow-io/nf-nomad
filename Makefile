
config ?= compileClasspath

ifdef module 
mm = :${module}:
else 
mm = 
endif 

clean:
	./gradlew clean

compile:
	./gradlew compileGroovy
	@echo "DONE `date`"


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



#
# Upload JAR artifacts to Maven Central
#
upload:
	./gradlew upload


upload-plugins:
	./gradlew plugins:upload

publish-index:
	./gradlew plugins:publishIndex