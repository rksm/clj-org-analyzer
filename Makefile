.PHONY: nrepl chrome clean run-jar cljs cljs-prod http-server app-test test figwheel

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

VERSION := 0.3.3

CLJ_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.clj" -o -iname "*.cljc" \) -print)

CLJS_FILES := $(shell find . -type f \
		\( -path "./test/*" -o -path "./dev/*" -o -path "./src/*" \) \
		\( -iname "*.cljs" -o -iname "*.cljc" \) -print)


# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# repl / dev

nrepl:
	clojure -Srepro -R:deps:cljs:nrepl:test -C:cljs:nrepl:test -m org-analyzer.nrepl-server

figwheel:
	clj -R:cljs -C:dev -m figwheel.main -b dev -r

chrome:
	chromium \
	  --remote-debugging-port=9222 \
	  --no-first-run \
	  --user-data-dir=chrome-user-profile

http-server: cljs-prod
	clojure -A:http-server

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# cljs

JS_FILES := resources/public/cljs-out/dev/
JS_PROD_FILES := resources/public/cljs-out/prod/

$(JS_FILES): $(CLJS_FILES) deps.edn dev.cljs.edn
	clojure -A:dev:cljs -C:test

$(JS_PROD_FILES): $(CLJS_FILES) deps.edn prod.cljs.edn
	clojure -R:cljs -A:cljs-prod

cljs: $(JS_FILES)

cljs-prod: $(JS_PROD_FILES)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# packaging / jar

pom.xml: deps.edn
	clojure -Spom

AOT := target/classes

$(AOT): $(CLJ_FILES) $(CLJS_FILES)
	mkdir -p $(AOT)
	clojure -A:aot

JAR := target/org-analyzer-$(VERSION).jar
$(JAR): cljs $(AOT) pom.xml
	mkdir -p $(dir $(JAR))
	clojure -C:http-server:aot -A:depstar -m hf.depstar.uberjar $(JAR) -m org_analyzer.main
	chmod a+x $(JAR)
	cp $(JAR) org-analyzer-el/org-analyzer.jar

jar: $(JAR)

run-jar: jar
	cp $(JAR) org-analyzer-el/org-analyzer.jar
	java -jar $(JAR) -m org-analyzer.main

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# graal

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
		org_analyzer.main \
		$(BIN)
	cp -r resources/public $(dir $(BIN))/public

bin: $(BIN)

run-bin: bin
	$(BIN)


# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# emacs

EMACS_PACKAGE_NAME:=org-analyzer-for-emacs-$(VERSION)
EMACS_PACKAGE_DIR:=/tmp/$(EMACS_PACKAGE_NAME)

update-version:
	sed -e "s/[0-9].[0-9].[0-9]/$(VERSION)/" -i org-analyzer-el/org-analyzer-pkg.el

$(EMACS_PACKAGE_DIR): update-version $(JAR)
	@mkdir -p $@
	cp -r org-analyzer-el/*el org-analyzer-el/*jar $@

emacs-package: $(EMACS_PACKAGE_DIR)
	mkdir -p target
	tar czvf target/$(EMACS_PACKAGE_NAME).tar.gz \
		-C $(EMACS_PACKAGE_DIR)/.. $(EMACS_PACKAGE_NAME)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

# app-test:
# 	clojure -R:test:cljs -m figwheel.main -co test.cljs.edn -m org-analyzer.app-test

test:
	clojure -A:test

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

clean:
	rm -rf target/$(EMACS_PACKAGE_NAME).tar.gz \
		$(EMACS_PACKAGE_DIR) \
		target .cpcache $(AOT) \
		resources/public/cljs-out \
		$(JAR) bin
