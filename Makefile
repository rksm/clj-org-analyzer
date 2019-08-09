CLJ_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.clj" -o -iname "*.cljc" \) -print)

CLJS_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.cljs" -o -iname "*.cljc" \) -print)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

nrepl:
	clojure -R:deps:fig:nrepl -C:fig -C:nrepl -m org-analyzer.nrepl-server

chrome:
	chromium \
	  --remote-debugging-port=9222 \
	  --no-first-run \
	  --user-data-dir=chrome-user-profile

http-server:
	clojure -A:http-server

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

cljs: $(CLJS_FILES) deps.edn dev.cljs.edn
	clojure -A:fig

CLJS_PROD_FILES := resources/public/cljs-out/main.js resources/public/cljs-out/prod/

$(CLJS_PROD_FILES): $(CLJS_FILES) deps.edn prod.cljs.edn
	clojure -R:fig -A:fig-prod

cljs-prod: $(CLJS_PROD_FILES)

pom.xml: deps.edn
	clojure -Spom

AOT := classes

$(AOT): $(CLJ_FILES) $(CLJS_FILES)
	mkdir -p classes
	clojure -C:fig -A:aot

aot_uber.jar: cljs-prod aot pom.xml
	clojure -C:http-server:aot -A:depstar -m hf.depstar.uberjar aot_uber.jar -v

#	clojure -A:uberjar --aliases http-server:aot --target aot_uber.jar

uber.jar: cljs-prod pom.xml
	clojure -C:http-server -A:depstar -m hf.depstar.uberjar uber.jar -v -m org_analyzer.http_server

#	clojure -A:uberjar --aliases http-server --target uber.jar

run-uberjar: uber.jar
	java -jar uber.jar -m org-analyzer.http-server


# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

RESOURCE_CONFIG := target/graal-resource-config.json
$(RESOURCE_CONFIG): $(CLJS_FILES)
	clojure -A:graal-prep

BIN := bin/run

$(BIN): $(AOT) cljs-prod $(CLJS_FILES) $(CLJ_FILES)
	mkdir -p bin
	native-image \
		--report-unsupported-elements-at-runtime \
		--verbose \
		--no-server \
		--initialize-at-build-time \
		-cp $(shell clojure -C:aot:http-server -Spath) \
		--no-fallback \
		--enable-http --enable-https --allow-incomplete-classpath \
		-H:+ReportExceptionStackTraces \
		-H:ResourceConfigurationFiles=$(RESOURCE_CONFIG) \
		-H:Log=registerResource \
		org_analyzer.http_server \
		$(BIN)
	cp -r resources/public/ $(dir $(BIN))/public

bin: $(BIN)

run-bin: bin
	$(BIN)
# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

clean:
	rm -rf target .cpcache classes uber.jar aot_uber.jar

.PHONY: nrepl chrome clean aot run-uberjar cljs cljs-prod http-server


# clojure -A:pack mach.pack.alpha.capsule uberjar.jar \
# 	-C:http-server \
# 	-e classes \
# 	--application-id org.rksm.org-analyzer \
# 	--application-version "0.1.0" \
# 	-m org-analyzer.http-server
