(ns org-analyzer.main
  (:require [org-analyzer.http-server :refer [start-server default-host default-port org-files-and-dirs]]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]))


(def usage "Usage: org-analyzer [opt*] [org-file-or-dir*]

Interactive visualization of timetracking data (org clocks).

This command starts an HTTP server that serves a web page that visualizes the
time data found in org files. Org files can be specified individually or, when
passing a directory, a recursive search for .org files is done. If nothing is
specified, defaults to the current directory, recursively searching it for any
.org file.

opts:
     --host hostname	Sets hostname, default is localhost
 -p, --port portnumber	Sets port, default is 8090
     --dontopen		Don't automatically open a web browser window

For more info see https://github.com/rksm/cljs-org-analyzer.")

(defn parse-args [args]
  (loop [opts {:host default-host :port (str default-port) :openbrowser true :files []}
         [arg & rest] args]
    (case arg
      nil opts
      ("-p" "--port") (let [[port & rest] rest]
                        (recur
                         (if port (assoc opts :port port) opts)
                         rest))
      "--host" (let [[host & rest] rest]
                 (recur
                  (if host (assoc opts :host host) opts)
                  rest))
      "--dontopen" (recur (assoc opts :openbrowser false) rest)
      (recur (update opts :files conj arg) rest))))


(defn -main [& args]
  (let [args-set (set args)
        help? (or (args-set "--help") (args-set "-h"))]
    (when help?
      (println usage)
      (System/exit 0)))

  (let [{:keys [host port openbrowser files]} (parse-args args)]
    (start-server host (Integer/parseInt port))

    (when openbrowser
      (browse/browse-url (str "http://" host ":" port)))

    (when (seq files)
      (reset! org-files-and-dirs (map io/file files)))))
