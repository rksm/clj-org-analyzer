(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :as rg]
            [cljs.core.async :refer [go <! chan]]
            [org-analyzer.view.selection :as sel]))

(enable-console-print!)

(defonce state {:calendar (rg/atom nil)
                :clocks-by-day (rg/atom {})
                :hovered-over-day (rg/atom nil)
                :selected-days (rg/atom #{})
                :selected-days-preview (rg/atom #{})
                :selecting? (rg/atom false)
                :sel-rect (atom sel/empty-rectangle-selection-state)
                :keys (atom {:shift-down? false
                             :alt-down? false})
                :dom-state (atom {:day-bounds {}})
                :global-event-handlers (atom {})})

(defn -main []
  (app/setup-global-events state)
  (rg/render [app/app state]
          (js/document.querySelector "#app"))
  (go (let [{:keys [calendar clocks-by-day]} (<! (app/fetch-data))]
        (reset! (:calendar state) calendar)
        (reset! (:clocks-by-day state) clocks-by-day))))

(-main)

