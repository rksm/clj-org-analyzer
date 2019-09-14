(ns ^:figwheel-hooks org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as r]
            [cljs.reader :as reader]))

(enable-console-print!)

(def known-org-files (reader/read-string (js/localStorage.getItem "org-analyzer-files")))
(defonce app-state (app/empty-app-state known-org-files))
(defonce dom-state (app/empty-dom-state))
(defonce event-handlers (app/event-handlers app-state dom-state))

(defn render []
  (r/render [app/app app-state dom-state event-handlers]
          (js/document.querySelector "#app")))

(defn -main []
  (app/send-cancel-kill-server-request!)
  (render)
  (app/fetch-and-update! app-state)
  (app/fetch-org-files! (r/cursor app-state [:known-org-files])))

(defonce started (do (-main) true))

(defn ^:after-load hot-rerender []
  (println "hot rerender")
  (render))
