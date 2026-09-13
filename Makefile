.PHONY: all clean hooks format format-check lint build test psp-simulator precommit setup up down status migrate ensure-static-data seed seed-test generate-fx-seed generate-fx-seed-test fetch-fx-fixture lifecycle images verify

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

psp-simulator:
	./outpost/gradlew -p psp-simulator bootRun

images:
	./outpost/gradlew -p outpost :gateway-api:bootBuildImage :ledger-api:bootBuildImage \
		:outpost-worker:bootBuildImage :static-data-job:bootBuildImage
	./outpost/gradlew -p psp-simulator bootBuildImage

precommit:
	SKIP=$(PRECOMMIT_SKIP) pre-commit run --all-files --hook-stage manual

# The one command that builds and tests every Gradle root; CI runs this as its sole gate so
# nothing is skipped by a path filter and no suite runs twice in the same CI run.
verify:
	./outpost/gradlew -p outpost spotlessCheck checkstyleMain checkstyleTest build
	./outpost/gradlew -p psp-simulator spotlessCheck checkstyleMain checkstyleTest build
	./outpost/gradlew -p merchant-simulator spotlessCheck checkstyleMain checkstyleTest build
	$(MAKE) seed-test

setup:
	./local/setup.sh

up: images
	docker compose --env-file .env -f local/docker-compose.yml up -d --wait postgres
	$(MAKE) lifecycle
	docker compose --env-file .env -f local/docker-compose.yml up -d --wait

down:
	docker compose --env-file .env -f local/docker-compose.yml down --volumes

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
