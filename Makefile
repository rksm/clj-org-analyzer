start-nrepl-server:
	clojure -R:deps:fig:nrepl -C:nrepl -m org-analyzer.nrepl-server
