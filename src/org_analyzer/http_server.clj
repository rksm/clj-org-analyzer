(ns org-analyzer.http-server
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [java-time :as time]
            [org-analyzer.printing :refer [print-duration]]
            [org-analyzer.processing :refer [find-clocks parse-and-zip]]
            [org-analyzer.time
             :refer
             [calendar clock->each-day-clocks clocks-between]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce org-files (let [dir (io/file "/home/robert/org/")]
                     (->> dir
                          file-seq
                          (filter #(and
                                    ;; (= dir (.getParentFile %))
                                    (s/ends-with? (.getName %) ".org"))))))

(defonce clocks (mapcat (comp find-clocks parse-and-zip) org-files))

(defn- as-instant [time]
  (-> time
      (time/offset-date-time (java.util.TimeZone/getDefault))
      time/instant->sql-timestamp))

(defn clock-data [{:keys [start end duration sections] :as clock}]
  {:start (as-instant start)
   :end (and (not (nil? end)) (as-instant end))
   :duration (and (not (nil? duration)) (print-duration duration))
   :location (->> sections ((juxt first last)) (map :name) (apply format "%s > %s"))})

(defn read-with-inst [string]
  (let [readers {'inst (comp time/local-date-time time/offset-date-time)}]
    (edn/read-string {:readers readers} string)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn send-clocks-between [start end & {:keys [by-day?] :or {by-day? false}}]
  (let [clocks (clocks-between start end clocks)
        clocks (if by-day?
                 (apply concat (map clock->each-day-clocks clocks))
                 clocks)]
    (map clock-data clocks)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defroutes main-routes
  (GET "/" [] (response/resource-response "public/index.html"))
  (GET "/index.html" [] (response/redirect "/"))
  (GET "/bar" [] (/ 1 0))
  (GET "/clocks" [from to by-day?] (pr-str (send-clocks-between
                                            (read-with-inst from)
                                            (read-with-inst to)
                                            :by-day? (edn/read-string by-day?))))
  (GET "/calendar" [from to] (pr-str (into [] (calendar
                                               (read-with-inst from)
                                               (read-with-inst to)))))
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
