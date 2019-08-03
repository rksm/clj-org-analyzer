(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-data]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]))

(enable-console-print!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce example-1-app-state
  (let [app-state (app/empty-app-state)]
    (go (let [{cal-data :calendar clock-data :clocks-by-day}
              (<! (fetch-data :from (js/Date. "2019-07-01") :to (js/Date. "2019-07-05")))]
          (swap! app-state assoc
                 :clocks-by-day clock-data
                 :calendar cal-data)))
    app-state))

(defonce example-1-dom-state (app/empty-dom-state))

(defn example-1 []
  (let [calendar (:calendar @example-1-app-state)
        month-date-and-days (first (into (sorted-map) (group-by
                                                       #(s/replace (:date %) #"^([0-9]+-[0-9]+).*" "$1")
                                                       calendar)))
        clocks-by-day (:clocks-by-day @example-1-app-state)]
    [:div.example
     [:h1 "example 1"]
     (if (empty? calendar)
       [:span "Loading..."]
       [app/month-view
        example-1-app-state
        example-1-dom-state
        month-date-and-days
        (into {} (map
                  (juxt first second)
                  (select-keys @example-1-app-state
                               [:clocks-by-day :selected-days :max-weight])))])]))



;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (rg/render [example-1]
             (. js/document (querySelector "#app"))
             #(println "rendered"))
  )

(start)

;; (rg/force-update-all)


;; (sc.api/letsc [35 -4 ] [days-in-month])
