(ns org-analyzer.view.selected-day
  (:require [org-analyzer.view.util :as util]
            [cljs.pprint :refer [cl-format]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn selected-day-view [date clocks-by-day]
  (if (nil? date)
    nil
    (let [clocks (get clocks-by-day date)
          location-durations (reverse
                              (sort-by second
                                       (map (fn [[a b]] [a (util/sum-clocks-mins b)])
                                            (group-by :location clocks))))]
      [:div.day-detail
       [:div.date date]
       [:div.hours (util/print-duration-mins (apply + (map second location-durations)))]
       [:div.clock-list
        (for [[location duration] location-durations]
          [:div.activity {:key location}
           [:span.duration (util/print-duration-mins duration)]
           (util/parse-all-org-links location)])]])))

(defn analyze-clocks [days clocks-by-day]
  (letfn [(clocks-avg [clocks-by-sth]
            (util/print-duration-mins
             (quot (->> clocks-by-sth
                        (map (comp util/sum-clocks-mins second))
                        (apply +))
                   (count clocks-by-sth))))]
    (let [weeks (group-by #(str (:year %) "-" (:week %)) days)
          clocks-by-week (into {} (for [[week-id days] weeks
                                        :let [clocks (apply concat (map
                                                                    #(get clocks-by-day (:date %))
                                                                    days))]]
                                    [week-id clocks]))]
      {:average-day-duration (clocks-avg clocks-by-day)
       :average-week-duration (clocks-avg clocks-by-week)
       :n-weeks (count weeks)})))


(defn selected-days-view [dates clocks-by-day calendar]
  (let [clocks-by-day (select-keys clocks-by-day dates)
        clocks (apply concat (vals clocks-by-day))
        location-durations (reverse
                            (sort-by second
                                     (map (fn [[a b]] [a (util/sum-clocks-mins b)])
                                          (group-by :location clocks))))
        duration (util/sum-clocks-mins clocks)
        days (filter #(-> % :date dates) calendar)
        {:keys [average-day-duration
                average-week-duration
                n-weeks]} (analyze-clocks days clocks-by-day)]
    [:div.day-detail
     [:div.date
      (cl-format nil "~d days over ~d week~:*~P selected" (count dates) n-weeks)]
     [:div (str "Average time per week: " average-week-duration)]
     [:div (str "Average time per day: " average-day-duration)]
     [:div.hours (str "Entire time: " (util/print-duration-mins duration))]
     [:div (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count location-durations))]
     [:div.clock-list
      (for [[location duration] location-durations]
        [:div.activity {:key location}
         [:span.duration (util/print-duration-mins duration)]
         (util/parse-all-org-links location)])]]))
