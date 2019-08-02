(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-data]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]))

(enable-console-print!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce example-1-state
  (let [state (rg/atom {:clocks-by-day {} :calendar []})]
    (go (let [{cal-data :calendar clock-data :clocks-by-day}
              (<! (fetch-data :from (js/Date. "2019-07-01") :to (js/Date. "2019-07-05")))]
          (reset! state {:clocks-by-day clock-data :calendar cal-data})))
    state))

(defn example-1 []
  (let [{:keys [calendar clocks-by-day]} @example-1-state]
    [:div.example
     [:h1 "example 1"]
     (if (empty? calendar)
       [:span "Loading..."]
       (app/month-view ["2019-07" calendar]
                       {:clocks-by-day clocks-by-day
                        :selected-days #{}}))]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (rg/render [example-1]
             (. js/document (querySelector "#app"))
             #(println "rendered"))
  )

(start)

;; (rg/force-update-all)


;; (sc.api/letsc [35 -4 ] [days-in-month])
