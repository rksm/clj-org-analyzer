nrepl:
	clojure -R:deps:fig:dirac:nrepl -C:fig -C:nrepl -m org-analyzer.nrepl-server

chrome:
	chromium \
	  --remote-debugging-port=9222 \
	  --no-first-run \
	  --user-data-dir=chrome-user-profile

.PHONY: nrepl chrome
