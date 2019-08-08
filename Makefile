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

cljs:
	clojure -A:fig

cljs-prod:
	clojure -R:fig -A:fig-prod

pom.xml: deps.edn
	clojure -Spom

aot:
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

clean:
	rm -rf target .cpcache classes uber.jar aot_uber.jar

.PHONY: nrepl chrome clean aot run-uberjar cljs cljs-prod http-server


# clojure -A:pack mach.pack.alpha.capsule uberjar.jar \
# 	-C:http-server \
# 	-e classes \
# 	--application-id org.rksm.org-analyzer \
# 	--application-version "0.1.0" \
# 	-m org-analyzer.http-server
