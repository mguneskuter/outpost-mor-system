.PHONY: all clean hooks format format-check lint build test precommit setup up down status migrate ensure-static-data seed seed-test generate-fx-seed generate-fx-seed-test fetch-fx-fixture lifecycle

PRECOMMIT_SKIP ?= no-commit-to-branch

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
	SKIP=$(PRECOMMIT_SKIP) pre-commit run --all-files

setup:
	./local/setup.sh

up:
	docker compose --env-file .env -f local/docker-compose.yml up -d

down:
	docker compose --env-file .env -f local/docker-compose.yml down

status:
	docker compose --env-file .env -f local/docker-compose.yml ps
	@docker compose --env-file .env -f local/docker-compose.yml exec -T postgres pg_isready -U "$(OUTPOST_DB_USER)" -d outpost

migrate:
	@test -n "$(OUTPOST_DB_PASSWORD)" || { echo "OUTPOST_DB_PASSWORD must be set in .env (see .env.example)"; exit 1; }
	OUTPOST_DB_URL="jdbc:postgresql://localhost:$(OUTPOST_DB_PORT)/outpost" OUTPOST_DB_USER="$(OUTPOST_DB_USER)" OUTPOST_DB_PASSWORD="$(OUTPOST_DB_PASSWORD)" ./local/migrate.sh

ensure-static-data:
	@test -n "$(OUTPOST_DB_PASSWORD)" || { echo "OUTPOST_DB_PASSWORD must be set in .env (see .env.example)"; exit 1; }
	./outpost/gradlew -p outpost :static-data-job:bootBuildImage
	OUTPOST_DB_URL="jdbc:postgresql://host.docker.internal:$(OUTPOST_DB_PORT)/outpost" \
	OUTPOST_DB_USER="$(OUTPOST_DB_USER)" OUTPOST_DB_PASSWORD="$(OUTPOST_DB_PASSWORD)" \
		./local/run-static-data-job.sh

seed:
	@test -n "$(OUTPOST_DB_PASSWORD)$(OUTPOST_PSP_SIMULATOR_BASE_URL)$(OUTPOST_PSP_SIMULATOR_API_KEY)$(OUTPOST_PSP_SIMULATOR_HMAC_SECRET)" || { echo "Required seed variables must be set in .env (see .env.example)"; exit 1; }
	OUTPOST_DB_PASSWORD="$(OUTPOST_DB_PASSWORD)" OUTPOST_DB_USER="$(OUTPOST_DB_USER)" OUTPOST_DB_PORT="$(OUTPOST_DB_PORT)" \
		OUTPOST_PSP_SIMULATOR_BASE_URL="$(OUTPOST_PSP_SIMULATOR_BASE_URL)" \
		OUTPOST_PSP_SIMULATOR_API_KEY="$(OUTPOST_PSP_SIMULATOR_API_KEY)" \
		OUTPOST_PSP_SIMULATOR_HMAC_SECRET="$(OUTPOST_PSP_SIMULATOR_HMAC_SECRET)" ./local/seed.sh

seed-test:
	./local/seed_test.sh

lifecycle: migrate ensure-static-data seed
generate-fx-seed:
	java local/fx/GenerateFxSeed.java

generate-fx-seed-test:
	./local/fx/generate_fx_seed_test.sh

fetch-fx-fixture:
	mkdir -p local/fixtures/fx/cache
	curl --fail --location 'https://data-api.ecb.europa.eu/service/data/EXR/D.CZK+DKK+GBP+HUF+PLN+RON+SEK+USD.EUR.SP00.A?format=csvdata' -o local/fixtures/fx/cache/fx.csv
