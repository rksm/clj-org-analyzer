(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as rg]
            [cljs.core.async :refer [go <! chan]]
            [org-analyzer.view.selection :as sel]))

(enable-console-print!)

(defonce app-state (app/empty-app-state))
(defonce dom-state (app/empty-dom-state))

(defn -main []
  (app/setup-global-events dom-state)
  (rg/render [app/app app-state dom-state]
          (js/document.querySelector "#app"))
  (go (let [{:keys [calendar clocks-by-day]} (<! (app/fetch-data))]
        (swap! app-state assoc
               :calendar calendar
                :clocks-by-day clocks-by-day))))

(-main)

