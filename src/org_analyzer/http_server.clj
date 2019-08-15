(ns org-analyzer.http-server
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set :refer [rename-keys]]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET POST]]
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
           java.lang.Thread
           [java.time LocalDateTime ZoneId]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def org-files-and-dirs (atom nil))

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


(defn get-clocks []
  (let [org-files (apply concat
                         (for [^File f @org-files-and-dirs
                               :when (.exists f)]
                           (if (.isDirectory f)
                             (find-org-files-in f)
                             [f])))
        clocks (mapcat (comp find-clocks parse-org-file) org-files)
        clock-count (count clocks)
        clocks (mapcat clock->each-day-clocks clocks)]
    {:clocks clocks :org-files org-files :clock-count clock-count}))


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
  (let [{:keys [clocks org-files clock-count]} (get-clocks)
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


(def i-will-kill-myself! (atom false) )

(defn start-kill-countdown! []
  (println "Client requested kill. Will stop server in 5 seconds.")
  (reset! i-will-kill-myself! true)
  (future
    (Thread/sleep (* 5 1000))
    (if @i-will-kill-myself!
      (System/exit 0)
      (println "kill canceled")))
  "OK")

(defn stop-kill-countdown! []
  (reset! i-will-kill-myself! false) "OK")


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-


(defroutes main-routes
  (GET "/" [] (response/resource-response "public/index.html"))
  (GET "/index.html" [] (response/redirect "/"))
  (GET "/known-org-files" [] (map file-path (filter #(.exists %) @org-files-and-dirs)))
  (POST "/known-org-files" [files]
        (let [files (map (comp io/file replace-tilde-with-home-dir) (edn/read-string files))
              files (rename-keys (group-by #(.exists %) files) {false :non-existing true :existing})
              response (into {} (map (fn [[key files]] [key (seq (map #(.getCanonicalPath %) files))]) files))]
          (reset! org-files-and-dirs (:existing files))
          (prn response)
          (pr-str response)))

  (POST "/kill" [] (start-kill-countdown!))
  (POST "/cancel-kill" [] (stop-kill-countdown!))
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
