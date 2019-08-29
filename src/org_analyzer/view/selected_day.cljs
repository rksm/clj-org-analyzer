(ns org-analyzer.view.selected-day
  (:require [org-analyzer.view.util :as util]
            [org-analyzer.view.timeline :as timeline]
            [org-analyzer.view.dom :as dom]
            [cljs.pprint :refer [cl-format]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [clojure.set :refer [intersection]]
            [clojure.string :as s]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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

(defn clock-list [clocks-with-id-and-duration highlighted-entries]
  (let [current-highlighted-entries @highlighted-entries]
   [:div.clock-list
    (apply concat
           (doall
            (for [{:keys [location duration] [{:keys [name tags path] :as clock}] :clocks} clocks-with-id-and-duration
                  :let [highlighted? (current-highlighted-entries location)
                        on-mouse-over #(reset! highlighted-entries #{location})
                        on-mouse-out #(reset! highlighted-entries #{})
                        attrs {:on-mouse-over on-mouse-over
                               :on-mouse-out on-mouse-out
                               :class (if highlighted? "highlighted" "")}]]
              [^{:key (str location "-name")}     [:span.name          attrs (util/parse-all-org-links name)]
               ^{:key (str location "-duration")} [:span.duration      attrs (util/print-duration-mins duration)]
               ^{:key (str location "-path")}     [:span.path          attrs (->> path
                                                                                  (map (comp util/parse-all-org-links s/trim))
                                                                                  (interpose " > "))]
               ^{:key (str location "-tags")}     [:span.tags.md-chips attrs (for [tag tags]
                                                                               ^{:key (str location "-" tag)}
                                                                               [:span.tag.md-chip.md-chip-raised
                                                                                {:on-click #(dom/select-element (.-target %))}
                                                                                tag])]])))]))

(defn selected-days-view
  [days clocks-by-day calendar highlighted-entries]

  (let [n (count days)
        dates (map :date days)
        clocks-by-day (into (sorted-map-by <) (select-keys clocks-by-day dates))
        clocks (apply concat (vals clocks-by-day))
        clocks-with-id-and-duration (reverse
                                     (sort-by :duration
                                              (map (fn [[location clocks]] {:location location
                                                                            :clocks clocks
                                                                            :duration (util/sum-clocks-mins clocks)})
                                                   (group-by :location clocks))))
        duration (util/sum-clocks-mins clocks)
        days (vals (select-keys calendar dates))
        {:keys [average-day-duration average-week-duration n-weeks]} (analyze-clocks days clocks-by-day)]

    [:div.day-detail
     [:div.date
      (if (= 1 n)
        (str (first dates) " " (:dow-name (first days)))
        (cl-format nil "~d days over ~d week~:*~P selected" (count dates) n-weeks))]

     [:div.hours (str "Clocked time: " (util/print-duration-mins duration))]
     (when (> n 1) [:div.avg-per-day (str "Average time per day: " average-day-duration)])
     (when (> n 1) [:div.avg-per-week (str "Average time per week: " average-week-duration)])
     [:div.activity-count (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count clocks-with-id-and-duration))]

     (clock-list clocks-with-id-and-duration highlighted-entries)]))
