(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as rg]
            [cljs.core.async :refer [go <! chan]]
            [org-analyzer.view.selection :as sel]))

(enable-console-print!)

(defonce app-state (app/empty-app-state))
(defonce dom-state (app/empty-dom-state))
(defonce event-handlers (app/event-handlers app-state dom-state))

(defn -main []
  (rg/render [app/app app-state dom-state event-handlers]
             (js/document.querySelector "#app"))
  (go (let [{:keys [calendar clocks-by-day]} (<! (app/fetch-data))]
        (swap! app-state assoc
               :calendar calendar
               :clocks-by-day clocks-by-day))))

(-main)

