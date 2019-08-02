(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as rg]
            [cljs.core.async :refer [go <! chan]]
            [org-analyzer.view.selection :as sel]))

(enable-console-print!)

(defonce state (app/empty-state))

(defn -main []
  (app/setup-global-events state)
  (rg/render [app/app state]
          (js/document.querySelector "#app"))
  (go (let [{:keys [calendar clocks-by-day]} (<! (app/fetch-data))]
        (reset! (:calendar state) calendar)
        (reset! (:clocks-by-day state) clocks-by-day))))

(-main)

