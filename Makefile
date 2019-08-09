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
# PACKR := bin-build/packr.jar

# $(PACKR):
# 	mkdir -p $(dir $(PACKR))
# 	wget https://libgdx.badlogicgames.com/ci/packr/packr.jar -O $(PACKR)

# LINUX64_JRE_URL := $(shell wget -qq "https://www.java.com/en/download/linux_manual.jsp" -O - | grep "Linux x64" | head -n 1 | cut -d '"' -f 4 | xargs)
# LINUX64_JDK_URL := "https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz"
# LINUX64_JRE := bin-build/linux64-jre
# LINUX64_JDK := bin-build/linux64-jdk
# LINUX64_BIN := bin-build/linux64/org-analyzer-server

# MACOSX_JRE_URL := $(shell wget -qq "https://www.java.com/en/download/manual.jsp" -O - | grep "Download Java for Mac OS X" | head -n 1 | cut -d '"' -f 4 | xargs)
# MACOSX_JDK_URL := "https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz"
# MACOSX_JRE := bin-build/macosx-jre
# MACOSX_JDK := bin-build/macosx-jdk
# MACOSX_BIN := bin-build/macosx/org-analyzer-server

# WIN64_JRE_URL := "https://javadl.oracle.com/webapps/download/AutoDL?BundleId=239856_230deb18db3e4014bb8e3e8324f81b43"
# WIN64_JDK_URL := "https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_windows-x64_bin.zip"
# WIN64_JRE := bin-build/win32-jre
# # WIN64_JDK := bin-build/win32-jdk
# WIN64_JDK := bin-build/win32-jre/java-11-openjdk-11.0.4.11-1.windows.ojdkbuild.x86_64/
# WIN64_BIN := bin-build/win32/org-analyzer-server

# $(LINUX64_JRE):
# 	mkdir -p $(LINUX64_JRE)
# 	wget $(LINUX64_JRE_URL) -O - | tar xfvz - -C $(LINUX64_JRE) --strip-components=1

# $(LINUX64_JDK): $(LINUX64_JRE)
# 	mkdir -p $(LINUX64_JDK)
# 	wget $(LINUX64_JDK_URL) -O - | tar xfvz - -C $(LINUX64_JDK) --strip-components=1
# 	cp -r $(LINUX64_JRE)/ $(LINUX64_JDK)/jre/

# # $(LINUX64_BIN): uber.jar $(PACKR) $(LINUX64_JDK)
# $(LINUX64_BIN): $(PACKR) $(LINUX64_JDK)
# 	java -jar $(PACKR) \
# 	     --platform linux64 \
# 	     --jdk $(LINUX64_JDK) \
# 	     --executable $(notdir $(LINUX64_BIN)) \
# 	     --classpath uber.jar \
# 	     --mainclass org_analyzer.http_server \
# 	     --vmargs Xmx1G \
# 	     --minimizejre hard \
# 	     --removelibs uber.jar \
# 	     --output $(dir $(LINUX64_BIN))

# $(MACOSX_JRE):
# 	mkdir -p $(MACOSX_JRE)
# 	wget $(MACOSX_JRE_URL) -O - | tar xfvz - -C $(MACOSX_JRE) --strip-components=1

# $(MACOSX_JDK):
# 	mkdir -p $(MACOSX_JDK)
# 	wget $(MACOSX_JDK_URL) -O - | tar xfvz - -C $(MACOSX_JDK) --strip-components=2

# #	cp -r $(MACOSX_JRE)/ $(MACOSX_JDK)/jre/

# # $(MACOSX_BIN): uber.jar $(PACKR) $(MACOSX_JDK)
# $(MACOSX_BIN): $(PACKR) $(MACOSX_JDK)
# 	java -jar $(PACKR) \
# 	     --platform mac \
# 	     --jdk $(MACOSX_JDK) \
# 	     --executable $(notdir $(MACOSX_BIN)) \
# 	     --classpath uber.jar \
# 	     --mainclass org_analyzer.http_server \
# 	     --vmargs Xmx1G \
# 	     --minimizejre hard \
# 	     --removelibs uber.jar \
# 	     --output $(dir $(MACOSX_BIN))

# $(WIN64_JRE):
# 	mkdir -p $(WIN64_JRE)
# 	unzip $(WIN64_JRE)/win32_jre_temp.zip -d $(WIN64_JRE)

# # unzip $(WIN64_JRE)/win32_jre_temp.zip -d $(WIN64_JRE)
# #	wget $(WIN64_JRE_URL) -O $(WIN64_JRE)/win32_jre_temp.zip
# #	rm $(WIN64_JRE)/win32_jre_temp.zip

# $(WIN64_JDK):

# #	mkdir -p $(WIN64_JDK)
# #	wget $(WIN64_JDK_URL) -O - | tar xfvz - -C $(WIN64_JDK) --strip-components=2

# #	cp -r $(WIN64_JRE)/ $(WIN64_JDK)/jre/

# # $(WIN64_BIN): uber.jar $(PACKR) $(WIN64_JDK)
# $(WIN64_BIN): $(PACKR) $(WIN64_JDK)
# 	java -jar $(PACKR) \
# 	     --platform windows64 \
# 	     --jdk $(WIN64_JDK) \
# 	     --executable $(notdir $(WIN64_BIN)) \
# 	     --classpath uber.jar \
# 	     --mainclass org_analyzer.http_server \
# 	     --vmargs Xmx1G \
# 	     --minimizejre hard \
# 	     --removelibs uber.jar \
# 	     --output $(dir $(WIN64_BIN))


# bin: $(WIN64_BIN)

# # bin: $(MACOSX_BIN)
# # bin: $(MACOSX_BIN) $(LINUX64_BIN)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

clean:
	rm -rf target .cpcache classes \
		uber.jar bin

.PHONY: nrepl chrome clean run-uberjar cljs cljs-prod http-server


# rsync -aizP --delete target/linux-64/ krahn:org-analyzer-server/

# clojure -A:pack mach.pack.alpha.capsule uberjar.jar \
# 	-C:http-server \
# 	-e classes \
# 	--application-id org.rksm.org-analyzer \
# 	--application-version "0.1.0" \
# 	-m org-analyzer.http-server
