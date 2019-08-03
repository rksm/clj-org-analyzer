(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-data]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]))

(enable-console-print!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce example-1-state
  (let [app-state (app/empty-app-state)
        dom-state (app/empty-dom-state)
        event-handlers (app/event-handlers app-state dom-state)]

    ;; fetch data
    (go (let [{cal-data :calendar clock-data :clocks-by-day}
              (<! (fetch-data :from (js/Date. "2019-07-01") :to (js/Date. "2019-07-05")))]
          (swap! app-state assoc
                 :clocks-by-day clock-data
                 :calendar cal-data)))

    {:app-state app-state
     :dom-state dom-state
     :event-handlers event-handlers }))

(defn example-1 []
  (let [{:keys [dom-state event-handlers app-state]} example-1-state
        {:keys [calendar clocks-by-day selected-days]} @app-state
        month-date-and-days (->> calendar
                                 (group-by #(s/replace (:date %) #"^([0-9]+-[0-9]+).*" "$1"))
                                 (into (sorted-map))
                                 first)
        max-weight (->> clocks-by-day
                        (map (comp app/sum-clocks-mins second))
                        (reduce max))
        calendar-state {:max-weight max-weight
                        :clocks-by-day clocks-by-day
                        :selected-days selected-days}]
    [:div.example
     [:h1 "example 1"]
     (if (empty? calendar)
       [:span "Loading..."]
       [app/month-view
        dom-state
        event-handlers
        month-date-and-days
        calendar-state])]))



;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (rg/render [example-1]
             (. js/document (querySelector "#app"))
             #(println "rendered")))

(start)
