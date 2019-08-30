(ns org-analyzer.http-server
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format]]
            [clojure.set :as set :refer [rename-keys]]
            [clojure.string :as s]
            [compojure.core :refer [GET POST routes]]
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
           java.lang.Thread))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; (def org-files-and-dirs (atom nil))

(defn find-org-files-in
  ([^File dir]
   (find-org-files-in dir {}))
  ([^File dir
    {:keys [include-archives?] :or {include-archives? true} :as opts}]
   (for [^File
         file (file-seq dir)
         :let [name (.getName file)]
         :when (and
                (not (.isDirectory file))
                (or (s/ends-with? name ".org")
                    (and include-archives?
                         (s/ends-with? name ".org_archive"))))]
     file)))

(defn file-path
  [^File f]
  ^String (let [p (.getCanonicalPath f)
                dir? (.isDirectory f)]
            (if dir? (str p "/") p)))

(defn replace-tilde-with-home-dir [path]
  (s/replace path #"~" (System/getProperty "user.home")))


(defn get-clocks
  [org-files-and-dirs & {:keys [by-day?] :or {by-day? true}}]
  (let [org-files (apply concat
                         (for [^File f org-files-and-dirs
                               :when (.exists f)]
                           (if (.isDirectory f)
                             (find-org-files-in f)
                             [f])))
        clocks (mapcat (comp find-clocks parse-org-file) org-files)
        clock-count (count clocks)
        clocks (if by-day? (mapcat clock->each-day-clocks clocks) clocks)]
    {:clocks clocks :org-files org-files :clock-count clock-count}))

(defn- as-instant [time]
  (-> time
      (time/offset-date-time (java.util.TimeZone/getDefault))
      time/instant->sql-timestamp))

(defn clock-data [{:keys [start end duration sections tags] :as clock}]
  (let [{org-file :path} (last sections)
        [name & path] (mapv :name sections)
        path (vec (reverse path))]
    {:start (time/format "yyyy-MM-dd HH:mm" start)
     :end (and (not (nil? end)) (time/format "yyyy-MM-dd HH:mm" end))
     :duration (and (not (nil? duration)) (print-duration duration))
     :path path
     :name name
     :org-file org-file
     :location (->> name (conj path) (s/join "/"))
     :tags tags}))

(defn time-string-to-local-date [time-string]
  (time/local-date-time (time/zoned-date-time time-string)))

(defn read-with-inst [string]
  (let [readers {'inst time-string-to-local-date}]
    (edn/read-string {:readers readers} string)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn send-clocks-between
  [org-files-and-dirs start end]
  (let [{:keys [clocks org-files clock-count]} (get-clocks org-files-and-dirs)
        clocks (clocks-between start end clocks)]
    {:info {:clock-count clock-count
            :org-files (map file-path org-files)}
     :clocks (map clock-data clocks)}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; FIXME 2019-08-09
;; when running as native-image resources get the protocol "resources:" which
;; isn't handled by the ring middleware. This hack here will allow us to get
;; resource file infos + send the contents
(def bin-path "bin/")
(defn registered-resource-to-file [url]
  (io/file bin-path (s/replace (str url) #"^[^:]+:" "")))

(defmethod ring.util.response/resource-data :resource
  [url]
  (response/resource-data (io/as-url (registered-resource-to-file url))))
;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start-kill-countdown!
  "(:opts @app-state) has two fields kill-when-client-disconnects? and
  kill-remorse-period. If kill-when-client-disconnects? option is truthy, will
  set (:am-i-about-to-kill-myself? @app-state) to true and start a countdown for
  kill-remorse-period milliseconds. If after the
  timeout (:am-i-about-to-kill-myself? @app-state) is still true, it will exit
  the program.

  `start-kill-countdown!` will be triggered when a client exists. If a new
  client is started, it will cancel the kill countdown. This allows us to stop
  servers that were started via double click and not via a terminal or other
  means that would control the java process."
  [app-state]
  (let [{{:keys [kill-when-client-disconnects? kill-remorse-period]} :opts} @app-state]
    (if-not kill-when-client-disconnects?
      "Server kill is disabled"
      (let [msg (cl-format nil
                           "Client requested kill. Will stop server in ~d second~:*~P"
                           (quot kill-remorse-period 1000))]
        (println msg)
        (swap! app-state assoc :am-i-about-to-kill-myself? true)
        (future
          (Thread/sleep kill-remorse-period)
          (if (-> @app-state  :am-i-about-to-kill-myself?)
            (System/exit 0)
            (println "kill canceled")))
        msg))))

(defn stop-kill-countdown!
  [app-state]
  (swap! app-state assoc :am-i-about-to-kill-myself? false)
  "OK")


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn http-get-known-org-files
  [app-state]
  (->> @app-state
       :org-files-and-dirs
       (filter #(.exists %))
       (map file-path)
       (pr-str)))

(defn http-set-known-org-files!
  [app-state files]
  (let [files (map (comp io/file replace-tilde-with-home-dir) (edn/read-string files))
        files (rename-keys (group-by #(.exists %) files) {false :non-existing true :existing})
        response (into {} (map (fn [[key files]] [key (seq (map #(.getCanonicalPath %) files))]) files))]
    (swap! app-state assoc :org-files-and-dirs (:existing files))
    (pr-str response)))

(defn http-get-clocks [app-state from to]
  (pr-str (send-clocks-between
           (-> @app-state :org-files-and-dirs)
           (parse-timestamp from)
           (parse-timestamp to))))

(defn http-get-calender
  [app-state from to]
  (pr-str (into [] (calendar
                    (parse-timestamp from)
                    (parse-timestamp to)))))

(defn open-org-file-system-default [file heading]
  (->> file
       io/file
       (.open (java.awt.Desktop/getDesktop))))

(defn open-org-file-in-emacs [file heading]
  ;; NOTE: emacs expects an output string starting with "open-org-file"
  (println "open-org-file:" (pr-str file) (pr-str heading)))

(defn open-org-file [app-state file heading]
  (println file heading)
  (if (-> @app-state :opts :started-from-emacs?)
    (open-org-file-in-emacs file heading)
    (open-org-file-system-default file heading)))


(defn make-http-app [app-state]
  (let [main-routes
        (routes
         (GET "/" [] (response/resource-response "public/index.html"))
         (GET "/index.html" [] (response/redirect "/"))
         (GET "/known-org-files" [] (http-get-known-org-files app-state))
         (POST "/known-org-files" [files] (http-set-known-org-files! app-state files))
         (POST "/kill" [] (start-kill-countdown! app-state))
         (POST "/cancel-kill" [] (stop-kill-countdown! app-state))
         (GET "/clocks" [from to] (http-get-clocks app-state from to))
         (GET "/calendar" [from to] (http-get-calender app-state from to))
         (POST "/open-org-file" [file heading] (open-org-file app-state (read-string file) (read-string heading)) "OK")
         (route/resources "/" {:root "public"})
         (route/not-found "NOTFOUND "))]
    (-> (handler/api main-routes)
        wrap-stacktrace)))

(defn stop-server!
  [app-state]
  (let [{:keys [server]} @app-state]
    (when server (server))
    (swap! app-state assoc :server nil)))

(defn start-server!
  ([app-state]
   (let [{:keys [server] {:keys [host port]} :opts} @app-state]
     (when server (server))
     (swap! app-state assoc
            :server (run-server (make-http-app app-state)
                                {:ip host :port port :join? false})))))

;; (stop-server! org-analyzer.main/app-state)
;; (start-server! org-analyzer.main/app-state)
