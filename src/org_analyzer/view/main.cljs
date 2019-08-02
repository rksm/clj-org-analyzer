(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :as app]
            [reagent.core :refer [render]]
            [cljs.core.async :refer [go <! chan]]))

(enable-console-print!)

;; (defonce state {:calendar (ratom nil)
;;                 :clocks-by-day (ratom {})
;;                 :hovered-over-day (ratom nil)
;;                 :selected-days (ratom #{})
;;                 :selected-days-preview (ratom #{})
;;                 :selecting? (ratom false)
;;                 :sel-rect (atom sel/empty-rectangle-selection-state)
;;                 :keys (atom {:shift-down? false
;;                              :alt-down? false})
;;                 :dom-state (atom {:day-bounds {}})
;;                 :global-event-handlers (atom {})})

(defn -main []
  (app/setup-global-events app/state)
  (render [app/app]
          (js/document.querySelector "#app"))
  (go (let [{:keys [calendar clocks-by-day]} (<! (app/fetch-data))]
        (reset! (:calendar app/state) calendar)
        (reset! (:clocks-by-day app/state) clocks-by-day))))

(-main)

