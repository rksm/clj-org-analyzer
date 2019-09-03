(ns org-analyzer.view.clock-list
  (:require [org-analyzer.view.util :as util]
            [org-analyzer.view.timeline :as timeline]
            [org-analyzer.view.dom :as dom]
            [cljs.pprint :refer [cl-format]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [clojure.set :refer [intersection]]
            [clojure.string :as s]
            [cljs-http.client :as http]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; FIXME: side-effecty, move to proper place / inject...!
(defn open-org-file [org-file heading]
  (http/post "/open-org-file" {:form-params {:file (pr-str org-file)
                                             :heading (pr-str heading)}}))

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
    [:table.clock-list
     [:tbody
      (doall
       (for [{:keys [location duration] [{:keys [name tags path org-file] :as clock}] :clocks} clocks-with-id-and-duration
             :let [highlighted? (current-highlighted-entries location)
                   on-mouse-over #(reset! highlighted-entries #{location})
                   on-mouse-out #(reset! highlighted-entries #{})
                   attrs {:on-mouse-over on-mouse-over
                          :on-mouse-out on-mouse-out
                          :class (if highlighted? "highlighted" "")}]]
         ^{:key (str location "-name")} [:tr
                                         [:td.name
                                          (merge attrs {:on-click #(open-org-file org-file name)})
                                          (util/parse-all-org-links name)]
                                         [:td.duration      attrs (util/print-duration-mins duration)]
                                         [:td.path          attrs (->> path
                                                                       (map (comp util/parse-all-org-links s/trim))
                                                                       (interpose " > "))]
                                         [:td.tags.md-chips attrs (for [tag tags]
                                                                    ^{:key (str location "-" tag)}
                                                                    [:span.tag.md-chip.md-chip-raised
                                                                     {:on-click #(dom/select-element (.-target %))}
                                                                     tag])]]))]]))

(defn group-clocks-same-activity-and-sort-by-duration [clocks]
  (reverse
   (sort-by :duration
            (map (fn [[location clocks]] {:location location
                                          :clocks clocks
                                          :duration (util/sum-clocks-mins clocks)})
                 (group-by :location clocks)))))


(defn clock-list-view
  [days clocks-by-day calendar highlighted-entries clock-list-group]

  (let [n (count days)
        dates (map :date days)
        clocks-by-day (into (sorted-map-by <) (select-keys clocks-by-day dates))
        clocks (apply concat (vals clocks-by-day))
        clocks-with-id-and-duration (group-clocks-same-activity-and-sort-by-duration clocks)
        duration (util/sum-clocks-mins clocks)
        days (vals (select-keys calendar dates))
        {:keys [average-day-duration average-week-duration n-weeks]} (analyze-clocks days clocks-by-day)]

    [:div.day-detail

     [:div.select-wrapper.clock-list-grouper
      [:select {:on-change (fn [evt] (reset! clock-list-group
                                             (let [val (-> evt .-target .-value)]
                                               (when-not (empty? val) (keyword val)))))
                :value (or @clock-list-group "")}
       [:option {:value ""} "not grouped"]
       [:option {:value "day"} "group by day"]
       [:option {:value "week"} "group by week"]
       [:option {:value "month"} "group by month"]]]


     [:div.date
      (if (= 1 n)
        (str (first dates) " " (:dow-name (first days)))
        (cl-format nil "~d days over ~d week~:*~P selected" (count dates) n-weeks))]

     [:div.hours (str "Clocked time: " (util/print-duration-mins duration))]
     (when (> n 1) [:div.avg-per-day (str "Average time per day: " average-day-duration)])
     (when (> n 1) [:div.avg-per-week (str "Average time per week: " average-week-duration)])
     [:div.activity-count (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count clocks-with-id-and-duration))]

     (case @clock-list-group

       :day (doall (for [[day clocks] clocks-by-day
                         :when (not-empty clocks)
                         :let [clocks-with-id-and-duration (group-clocks-same-activity-and-sort-by-duration clocks)]]
                     ^{:key day} [:div day
                                  (clock-list clocks-with-id-and-duration highlighted-entries)]))

       :week (let [clocks-by-week (->> clocks-by-day
                                       (reduce (fn [by-week [day clocks]]
                                                 (update by-week
                                                         (str "Week " (:week (get calendar day)))
                                                         concat clocks))
                                               {})
                                       (sort-by <))]
               (doall (for [[week clocks] clocks-by-week
                            :when (not-empty clocks)
                            :let [clocks-with-id-and-duration (group-clocks-same-activity-and-sort-by-duration clocks)]]
                        ^{:key week} [:div week
                                      (clock-list clocks-with-id-and-duration highlighted-entries)])))

       :month (let [clocks-by-month (->> clocks-by-day
                                         (reduce (fn [by-week [day clocks]]
                                                   (update by-week
                                                           (:month (get calendar day))
                                                           concat clocks))
                                                 {})
                                         (sort-by <))]
                (doall (for [[month clocks] clocks-by-month
                             :when (not-empty clocks)
                             :let [clocks-with-id-and-duration (group-clocks-same-activity-and-sort-by-duration clocks)]]
                         ^{:key month} [:div month
                                        (clock-list clocks-with-id-and-duration highlighted-entries)])))

       (clock-list clocks-with-id-and-duration highlighted-entries))]))
