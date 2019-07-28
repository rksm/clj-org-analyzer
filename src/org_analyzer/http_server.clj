(ns org-analyzer.http-server
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response]))

;; (defn handler [request]
;;   {:status 200
;;    :headers {"Content-Type" "text/plain"}
;;    :body "Hello World 123"})

;; (def app
;;   (-> handler
;;       (wrap-resource "public")
;;       (wrap-content-type)
;;       (wrap-not-modified)
;;       (wrap-stacktrace)))

(defroutes main-routes
  (GET "/" [] (response/resource-response "public/index.html"))
  (GET "/index.html" [] (response/redirect "/"))
  (GET "/bar" [] (/ 1 0))
  (GET "/baz" [] (pr-str {:hello "world"}))
  (route/resources "/" {:root "public"})
  (route/not-found "NOTFOUND "))

(def app (-> (handler/site main-routes)
             (wrap-stacktrace)))

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
