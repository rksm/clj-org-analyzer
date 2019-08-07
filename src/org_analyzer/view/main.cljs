(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :refer [render]]))

(enable-console-print!)

(defonce app-state (app/empty-app-state))
(defonce dom-state (app/empty-dom-state))
(defonce event-handlers (app/event-handlers app-state dom-state))

(defn -main []
  (render [app/app app-state dom-state event-handlers]
          (js/document.querySelector "#app"))
  (app/fetch-and-update! app-state))

(-main)
