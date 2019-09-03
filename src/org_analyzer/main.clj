(ns org-analyzer.main
  (:gen-class)
  (:require [org-analyzer.http-server :refer [start-server!]]
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

(defn opts-from-args
  [default-opts args]
  (loop [opts default-opts
         [arg & rest] args]
    (case arg
      nil opts
      ("-p" "--port")        (let [[val & rest] rest] (recur (assoc opts :port (Integer/parseInt val)) rest))
      "--host"               (let [[val & rest] rest] (recur (assoc opts :host val) rest))
      "--dontopen"           (recur (assoc opts :openbrowser? false) rest)
      "--started-from-emacs" (recur (assoc opts :started-from-emacs? true) rest)
      (recur (update opts :files conj arg) rest))))

(defonce app-state (atom {:opts {:include-archives? true
                                 :openbrowser? true
                                 :files []
                                 :host "localhost"
                                 :port 8090
                                 :kill-when-client-disconnects? true
                                 :kill-remorse-period 5000
                                 :started-from-emacs? false}
                          :server nil
                          :org-files-and-dirs nil
                          :am-i-about-to-kill-myself? false}))

(defn -main [& args]
  (let [args-set (set args)
        help? (or (args-set "--help") (args-set "-h"))]
    (when help?
      (println usage)
      (System/exit 0)))

  (let [{:keys [openbrowser? host port files] :as opts}
        (opts-from-args (-> @app-state :opts) args)]
    (swap! app-state assoc
           :opts opts
           :org-files-and-dirs (->> files (map io/file) seq))

    (start-server! app-state)

    (when openbrowser?
      (browse/browse-url (str "http://" host ":" port)))))
