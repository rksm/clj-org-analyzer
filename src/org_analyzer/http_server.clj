(ns org-analyzer.http-server
  (:gen-class)
  (:require [clojure.edn :as edn]
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
            [ring.logger :as logger]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response])
  (:import java.io.File
           [java.time LocalDateTime ZoneId]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn get-clocks []
  (let [org-files (let [^File dir (io/file "/home/robert/org/")]
                    (->> dir
                         file-seq
                         (filter (fn [^File f] (and
                                                (not (.isDirectory f))
                                                (s/ends-with? (.getPath f) ".org"))))))
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
             logger/wrap-with-logger
             wrap-stacktrace))

(defonce server (atom nil))

(defn stop-server []
  (when @server (@server) (reset! server nil)))

(defn start-server []
  (when @server (stop-server))
  (reset! server (run-server app {:port 8080 :join? false})))

(defn -main [& args]
  (start-server))


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
