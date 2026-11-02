.PHONY: build
build:
	rm -rf build/libs bin
	gradle build
	gradle shadowJar
	mkdir -p bin
	cp build/libs/DynLight*.jar bin/

.PHONY: clean
clean:
	gradle clean
	rm -rf bin/
	rm -rf .gradle/
	rm -rf build/
	rm -rf server/plugins/DynLight*.jar
	rm -rf server/plugins/DynLight/


.PHONY: server-clear-plugin-data
server-clear-plugin-data:
	rm -rf server/plugins/DynLight/

.PHONY: server-plugin-copy
server-plugin-copy: server-clear-plugin-data
	rm -rf server/plugins/DynLight*.jar
	cp bin/DynLight*.jar server/plugins/

.PHONY: server-start
server-start:
	cd server && java -Xmx2G -jar paper-1.21.11-55.jar --nogui

.PHONY: server
server: build server-plugin-copy server-start

.PHONY: all
all: clean build server

# ---------------------------------------------------------------------------
# Multi-version CI matrix (see .github/workflows/server-test.yml).
# Startup smoke test: boots the plugin alone on a chosen server flavour + Minecraft
# version, checks it enabled, stops the server, and fails on any error that names
# the plugin. Uses the jar already in bin/ (no gradle), so 26.x cells can run on Java 25.
# ---------------------------------------------------------------------------
PLUGIN := DynLight
PKG := anon.def9a2a4.dynlight
TEST_PORT := 25581
SERVER_VARIANT ?= paper
MINECRAFT_VERSION ?= 1.21.11
DOWNLOAD_CACHE := .temp/download-cache
SERVER_JAR := $(DOWNLOAD_CACHE)/$(SERVER_VARIANT)-$(MINECRAFT_VERSION).jar
TEST_SERVER_DIR := test-server
# Anchored to Paper's log formats so a file path containing the repo name can't match
ERROR_PATTERN := (ERROR|FATAL)\]: \[$(PLUGIN)\]|Could not load plugin '$(PLUGIN)|Error occurred while (enabling|disabling) $(PLUGIN) v|Could not pass event .* to $(PLUGIN) v|[[:space:]]at $(PKG)\.

# Paper: Fill API (v2 was retired). builds/latest returns the download URL directly.
$(DOWNLOAD_CACHE)/paper-%.jar:
	mkdir -p $(DOWNLOAD_CACHE)
	url=$$(curl -fsSL -H "User-Agent: $(PLUGIN)-ci (github actions)" \
		"https://fill.papermc.io/v3/projects/paper/versions/$*/builds/latest" \
		| jq -r '.downloads."server:default".url'); \
	curl -fsSL -o $@.tmp "$$url" && mv $@.tmp $@

# Purpur: resolve the latest build for the version, then download it.
$(DOWNLOAD_CACHE)/purpur-%.jar:
	mkdir -p $(DOWNLOAD_CACHE)
	build=$$(curl -fsSL "https://api.purpurmc.org/v2/purpur/$*" | jq -r '.builds.latest'); \
	curl -fsSL -o $@.tmp "https://api.purpurmc.org/v2/purpur/$*/$$build/download" && mv $@.tmp $@

.PHONY: test-server-download
test-server-download: $(SERVER_JAR)

.PHONY: test-server-ci
test-server-ci: test-server-download
	@ls bin/$(PLUGIN)*.jar >/dev/null 2>&1 || { echo "ERROR: no bin/$(PLUGIN)*.jar (run make build)"; exit 1; }
	rm -rf $(TEST_SERVER_DIR)
	mkdir -p $(TEST_SERVER_DIR)/plugins/bStats
	cp bin/$(PLUGIN)*.jar $(TEST_SERVER_DIR)/plugins/
	cp $(SERVER_JAR) $(TEST_SERVER_DIR)/server.jar
	echo 'eula=true' > $(TEST_SERVER_DIR)/eula.txt
	printf '%s\n' 'online-mode=false' 'enforce-secure-profile=false' 'level-type=flat' \
		'generate-structures=false' 'spawn-protection=0' 'max-tick-time=-1' \
		'server-port=$(TEST_PORT)' 'motd=$(PLUGIN) startup test' > $(TEST_SERVER_DIR)/server.properties
	printf 'enabled: false\nserverUuid: "00000000-0000-0000-0000-000000000000"\nlogFailedRequests: false\n' \
		> $(TEST_SERVER_DIR)/plugins/bStats/config.yml
	@cd $(TEST_SERVER_DIR) && \
	mkfifo server_input; \
	java -Xmx1G -jar server.jar nogui < server_input > server.log 2>&1 & \
	SERVER_PID=$$!; \
	exec 3> server_input; \
	stop_server() { \
		( echo stop >&3 ) 2>/dev/null || true; \
		for i in $$(seq 1 30); do kill -0 $$SERVER_PID 2>/dev/null || break; sleep 1; done; \
		exec 3>&-; \
		kill $$SERVER_PID 2>/dev/null || true; \
		rm -f server_input; \
	}; \
	echo "Waiting for $(SERVER_VARIANT) $(MINECRAFT_VERSION) to start..."; \
	for i in $$(seq 1 300); do \
		grep -q "Done.*For help" server.log 2>/dev/null && break; \
		if ! kill -0 $$SERVER_PID 2>/dev/null; then echo "✗ Server process died"; cat server.log; stop_server; exit 1; fi; \
		sleep 1; \
	done; \
	if ! grep -q "Done.*For help" server.log; then echo "✗ Server startup timed out"; cat server.log; stop_server; exit 1; fi; \
	echo "✓ Server started"; \
	stop_server; \
	cat server.log; \
	rm -f errors.log; \
	if ! grep -qE "\[$(PLUGIN)\] Enabling $(PLUGIN)" server.log; then \
		echo "✗ $(PLUGIN) did not enable" | tee -a errors.log; exit 1; \
	fi; \
	if grep -qE "$(ERROR_PATTERN)" server.log; then \
		grep -E "$(ERROR_PATTERN)" server.log > errors.log; \
		echo "✗ $(PLUGIN) logged errors:"; cat errors.log; exit 1; \
	fi; \
	echo "✓ $(PLUGIN) startup test passed on $(SERVER_VARIANT) $(MINECRAFT_VERSION)"
