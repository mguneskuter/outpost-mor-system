.PHONY: all clean hooks format format-check lint build test precommit setup

all: build

clean:
	./outpost/gradlew -p outpost clean

hooks:
	pre-commit install --install-hooks

format:
	./outpost/gradlew -p outpost spotlessApply

format-check:
	./outpost/gradlew -p outpost spotlessCheck

lint:
	./outpost/gradlew -p outpost checkstyleMain checkstyleTest

build:
	./outpost/gradlew -p outpost build

test:
	./outpost/gradlew -p outpost test

precommit:
	SKIP=no-commit-to-branch pre-commit run --all-files

setup:
	./local/setup.sh
