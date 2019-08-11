(ns org-analyzer.http-server
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [java-time :as time]
            [org-analyzer.printing :refer [print-duration]]
            [org-analyzer.processing
             :refer
             [find-clocks parse-org-file parse-timestamp]]
            [org-analyzer.time
             :refer
             [calendar clock->each-day-clocks clocks-between]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response])
  (:import java.io.File
           [java.time LocalDateTime ZoneId]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; (def app-state (atom {:files [(io/file (System/getProperty "user.home") "org")]}))

(def app-state (atom {:files [(io/file ".")]}))

(defn find-org-files-in [^File dir]
  (for [^File
        file (file-seq dir)
        :when (and
               (not (.isDirectory file))
               (s/ends-with? (.getPath file) ".org"))]
    file))

(defn get-clocks []
  (let [org-files (apply concat
                         (for [^File f (:files @app-state)
                               :when (.exists f)]
                           (if (.isDirectory f)
                             (find-org-files-in f)
                             [f])))
        clocks (mapcat (comp find-clocks parse-org-file) org-files)
        clocks (mapcat clock->each-day-clocks clocks)]
    clocks))


(defn- as-instant [time]
  (-> time
      (time/offset-date-time (java.util.TimeZone/getDefault))
      time/instant->sql-timestamp))

(defn clock-data [{:keys [start end duration sections tags] :as clock}]
  (let [[name & path] (mapv :name sections)
        path (vec (reverse path))]
    {:start (time/format "yyyy-MM-dd HH:mm" start)
     :end (and (not (nil? end)) (time/format "yyyy-MM-dd HH:mm" end))
     :duration (and (not (nil? duration)) (print-duration duration))
     :path path
     :name name
     :location (->> name (conj path) (s/join "/"))
     :tags tags}))

(defn time-string-to-local-date [time-string]
  (time/local-date-time (time/zoned-date-time time-string)))

(defn read-with-inst [string]
  (let [readers {'inst time-string-to-local-date}]
    (edn/read-string {:readers readers} string)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn send-clocks-between [start end & {:keys [by-day?] :or {by-day? false}}]
  (let [clocks (clocks-between start end (get-clocks))
        ;; clocks (if by-day?
        ;;          (apply concat (map clock->each-day-clocks clocks))
        ;;          clocks)
        ]
    (map clock-data clocks)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defroutes main-routes
  (GET "/" [] (response/resource-response "public/index.html"))
  (GET "/index.html" [] (response/redirect "/"))
  (GET "/bar" [] (/ 1 0))
  (GET "/clocks" [from to by-day?] (pr-str (send-clocks-between
                                            (parse-timestamp from)
                                            (parse-timestamp to)
                                            :by-day? (edn/read-string by-day?))))
  (GET "/calendar" [from to] (pr-str (into [] (calendar
                                               (parse-timestamp from)
                                               (parse-timestamp to)))))
  (route/resources "/" {:root "public"})
  (route/not-found "NOTFOUND "))

(def app (-> (handler/api main-routes)
             wrap-stacktrace))

(defonce server (atom nil))

(def default-host "localhost")
(def default-port 8090)

(defn stop-server []
  (when @server (@server) (reset! server nil)))

(defn start-server
  ([]
   (start-server default-host default-port))
  ([^String host ^Integer port]
   (when @server (stop-server))
   (reset! server (run-server app {:ip host :port port :join? false}))))

;; (@server)
;; (start-server)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def args ["--port" "8090" "--host" "localhost" ""])
(def args ["--port" "8090" "foo.org"])


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


(def usage "Usage: org-analyzer [opt*] [org-file-or-dir*]

Interactive visualization of timetracking data (org clocks).

This command starts an HTTP server that serves a web page that visualizes the
time data found in org files. Org files can be specified individually or, when
passing a directory, a recursive search for .org files is done. If nothing is
specified, defaults to ~/org/ and doing a recursive search in that directory.

opts:
     --host hostname	Sets hostname, default is 0.0.0.0
 -p, --port portnumber	Sets port, default is 8090
     --dontopen		Don't automatically open a web browser window

For more info see https://github.com/rksm/cljs-org-analyzer.")


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
      (swap! app-state assoc :files (map io/file files)))))
