(ns org-analyzer.http-server
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [java-time :as time]
            [org-analyzer.printing :refer [print-duration]]
            [org-analyzer.processing :refer [find-clocks parse-org-file parse-timestamp]]
            [org-analyzer.time
             :refer
             [calendar clock->each-day-clocks clocks-between]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response])
  (:import [java.time LocalDateTime ZoneId]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn get-clocks []
  (let [org-files (let [dir (io/file "/home/robert/org/")]
                    (->> dir
                         file-seq
                         (filter #(and
                                   ;; (= dir (.getPaarentFile %))
                                   (s/ends-with? (.getName %) ".org")))))

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
(last (sort-by :start (get-clocks)))
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

(def app (-> (handler/site main-routes)
             (wrap-stacktrace)))

;; ring-logger {:mvn/version "1.0.1"}
;; (require 'ring.logger)

(defonce server (atom nil))

(defn stop-server []
  (when @server (.stop @server) (reset! server nil)))

(defn start-server []
  (when @server (stop-server))
  (reset! server (run-jetty app {:port 8080 :join? false})))

(start-server)
;; (stop-server)

(defn -main [& args]
  (start-server))

;; (sc.api.logging/register-cs-logger :sc.api.logging/log-spy-cs (fn [cs] nil))

