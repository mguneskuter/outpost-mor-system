.PHONY: all clean hooks format format-check lint build test precommit setup \
	db-up db-down db-status migrate

-include .env

OUTPOST_DB_PORT ?= 5432
OUTPOST_DB_USER ?= outpost

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

db-up:
	docker compose --env-file .env -f local/docker-compose.yml up -d

db-down:
	docker compose --env-file .env -f local/docker-compose.yml down

db-status:
	docker compose --env-file .env -f local/docker-compose.yml ps
	@docker compose --env-file .env -f local/docker-compose.yml exec -T postgres pg_isready -U "$(OUTPOST_DB_USER)" -d outpost

migrate:
	@test -n "$(OUTPOST_DB_PASSWORD)" || { echo "OUTPOST_DB_PASSWORD must be set in .env (see .env.example)"; exit 1; }
	./outpost/gradlew -p outpost -PoutpostDbUrl="jdbc:postgresql://localhost:$(OUTPOST_DB_PORT)/outpost" -PoutpostDbUsername="$(OUTPOST_DB_USER)" -PoutpostDbPassword="$(OUTPOST_DB_PASSWORD)" :common-persistence:migrate
