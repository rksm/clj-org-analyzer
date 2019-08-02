(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-data]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]))

(enable-console-print!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce example-1-state
  (let [state (app/empty-state)]
    (go (let [{cal-data :calendar clock-data :clocks-by-day}
              (<! (fetch-data :from (js/Date. "2019-07-01") :to (js/Date. "2019-07-05")))]
          (reset! (:clocks-by-day state) clock-data)
          (reset! (:calendar state) cal-data)))
    state))

(defn example-1 []
  (let [calendar @(:calendar example-1-state)
        month-date-and-days (first (into (sorted-map) (group-by
                                      #(s/replace (:date %) #"^([0-9]+-[0-9]+).*" "$1")
                                      @(:calendar example-1-state))))
        clocks-by-day @(:clocks-by-day example-1-state)]
    [:div.example
     [:h1 "example 1"]
     (if (empty? calendar)
       [:span "Loading..."]
       (app/month-view example-1-state
                       month-date-and-days
                       (into {} (map
                                 (juxt first (comp deref second))
                                 (select-keys example-1-state
                                              [:clocks-by-day :selected-days :max-weight])))))]))



;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (rg/render [example-1]
             (. js/document (querySelector "#app"))
             #(println "rendered"))
  )

(start)

;; (rg/force-update-all)


;; (sc.api/letsc [35 -4 ] [days-in-month])
