.PHONY: build
build:
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
