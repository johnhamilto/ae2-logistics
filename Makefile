# All targets funnel into the Gradle wrapper; this file makes the common
# commands memorable and finds Homebrew's JDK 21 without any shell config.

BREW_JDK := $(shell brew --prefix openjdk@21 2>/dev/null)
ifneq ($(BREW_JDK),)
export JAVA_HOME := $(BREW_JDK)/libexec/openjdk.jdk/Contents/Home
endif

GRADLE := ./gradlew

.DEFAULT_GOAL := help

.PHONY: help build check client server data textures clean refresh

help: ## List available targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-10s %s\n", $$1, $$2}'

build: ## Compile, run checks, and produce the mod jar (build/libs/)
	$(GRADLE) build

check: ## Compile and run verification tasks without assembling jars
	$(GRADLE) check

client: ## Launch a Minecraft client with the mod + AE2 loaded
	$(GRADLE) runClient

server: ## Launch a dedicated dev server with the mod + AE2 loaded
	$(GRADLE) runServer

data: ## Run datagen; output lands in src/generated/resources
	$(GRADLE) runData

textures: ## Regenerate all sprite PNGs from scripts/gen_textures.py
	python3 scripts/gen_textures.py src/main/resources/assets/ae2logistics/textures

clean: ## Delete build outputs
	$(GRADLE) clean

refresh: ## Re-resolve dependencies after changing versions in gradle.properties
	$(GRADLE) --refresh-dependencies build
