nrepl:
	clojure -R:deps:fig:nrepl -C:fig -C:nrepl -m org-analyzer.nrepl-server

chrome:
	chromium \
	  --remote-debugging-port=9222 \
	  --no-first-run \
	  --user-data-dir=chrome-user-profile

http-server:
	clojure -A:http-server

cljs:
	clojure -A:fig-prod

clean:
	rm -rf target .cpcache

.PHONY: nrepl chrome clean
