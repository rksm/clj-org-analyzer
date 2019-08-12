(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as r :refer [render]]))

(enable-console-print!)

(def known-org-files (cljs.reader/read-string (js/localStorage.getItem "org-analyzer-files")))
(defonce app-state (app/empty-app-state known-org-files))
(defonce dom-state (app/empty-dom-state))
(defonce event-handlers (app/event-handlers app-state dom-state))

(defn -main []
  (app/send-cancel-kill-server-request!)
  (render [app/app app-state dom-state event-handlers]
          (js/document.querySelector "#app"))
  (app/fetch-and-update! app-state)
  (app/fetch-org-files! (r/cursor app-state [:known-org-files])))

(-main)
