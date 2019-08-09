CLJ_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.clj" -o -iname "*.cljc" \) -print)

CLJS_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.cljs" -o -iname "*.cljc" \) -print)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

nrepl:
	clojure -R:deps:cljs:nrepl -C:cljs -C:nrepl -m org-analyzer.nrepl-server

chrome:
	chromium \
	  --remote-debugging-port=9222 \
	  --no-first-run \
	  --user-data-dir=chrome-user-profile

http-server:
	clojure -A:http-server

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

JS_FILES := resources/public/cljs-out/main.js resources/public/cljs-out/dev/
JS_PROD_FILES := resources/public/cljs-out/main.js resources/public/cljs-out/prod/

$(JS_FILES): $(CLJS_FILES) deps.edn dev.cljs.edn
	clojure -A:cljs

$(JS_PROD_FILES): $(CLJS_FILES) deps.edn prod.cljs.edn
	clojure -R:cljs -A:cljs-prod

cljs: $(JS_FILES)

cljs-prod: $(JS_PROD_FILES)

pom.xml: deps.edn
	clojure -Spom

AOT := target/classes

$(AOT): $(CLJ_FILES) $(CLJS_FILES)
	mkdir -p $(AOT)
	clojure -A:aot

org-analyzer.jar: cljs $(AOT) pom.xml
	clojure -C:http-server:aot -A:depstar -m hf.depstar.uberjar org-analyzer.jar  -m org_analyzer.http_server

run-uberjar: org-analyzer.jar
	java -jar org-analyzer.jar -m org-analyzer.http-server


# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

RESOURCE_CONFIG := target/graal-resource-config.json

$(RESOURCE_CONFIG): $(CLJS_FILES)
	clojure -A:graal-prep

BIN := bin/run

$(BIN): $(AOT) cljs $(CLJS_FILES) $(CLJ_FILES) $(RESOURCE_CONFIG)
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
		org_analyzer.http_server \
		$(BIN)
	cp -r resources/public $(dir $(BIN))/public

bin: $(BIN)

run-bin: bin
	$(BIN)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

clean:
	rm -rf target .cpcache $(AOT) \
		org-analyzer.jar bin

.PHONY: nrepl chrome clean run-uberjar cljs cljs-prod http-server
