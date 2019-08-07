(ns org-analyzer.view.calendar
  (:require [org-analyzer.view.util :as util]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.selection :as sel]
            [org-analyzer.view.geo :as geo]
            [clojure.string :refer [split lower-case join replace]]
            [reagent.core :as r :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [clojure.set :refer [union]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn event-handlers [app-state dom-state]
  (letfn [(shift-down? [] (-> @dom-state :keys :shift-down?))

          (on-click-month [evt days]
            (let [dates (into #{} (map :date days))]
              (swap! app-state update :selected-days #(cond
                                                        (shift-down?) (union % dates)
                                                        :else dates))))

          (on-click-week [evt week-no days]
            (let [dates (into #{} (map :date days))]
              (swap! app-state update :selected-days #(cond
                                                        (shift-down?) (union % dates)
                                                        :else dates))))

          (on-mouse-over-day [date]
            (when (not (:selecting? @app-state))
              (swap! app-state assoc :hovered-over-date date)))

          (on-mouse-out-day []
            (swap! app-state assoc :hovered-over-date nil))

          (on-click-day [evt date]
            (let [add-selection? (shift-down?)]
              (swap! app-state update :selected-days #(cond
                                                        (and add-selection? (% date)) (disj % date)
                                                        add-selection? (conj % date)
                                                        :else #{date}))))]
    {:on-click-month on-click-month
     :on-click-week on-click-week
     :on-mouse-over-day on-mouse-over-day
     :on-mouse-out-day on-mouse-out-day
     :on-click-day on-click-day}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; rectangle selection helpers
(defn- mark-days-as-potentially-selected [app-state dom-state sel-state]
  (let [sel-bounds (:global-bounds sel-state)
        contained (into #{} (for [[day day-bounds]
                                  (:day-bounding-boxes @dom-state)
                                  :when (geo/contains-rect? sel-bounds day-bounds)]
                              day))]
    (swap! app-state assoc :selected-days-preview contained)))

(defn- commit-potentially-selected!
  [selected-days selected-days-preview dom-state]
  (let [selected (if (-> @dom-state :keys :shift-down?)
                   (union @selected-days-preview
                          @selected-days)
                   @selected-days-preview)]
    (reset! selected-days-preview #{})
    (reset! selected-days selected)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- emph-css-class [count max-count]
  (-> count
      (/ max-count)
      (* 10)
      js/Math.round
      ((partial str "emph-"))))

(defn day-view
  [dom-state event-handlers
   {:keys [date] :as day}
   {:keys [clocks-by-day selected-days max-weight] :as calendar-state}
   highlighted-days]
  (let [clocks (get @clocks-by-day date)
        selected? (@selected-days date)
        highlighted? (@highlighted-days date)]
    [:div.day {:key date
               :id date
               :class [(emph-css-class
                        (util/sum-clocks-mins clocks)
                        @max-weight)
                       (when selected? "selected")
                       (when highlighted? "highlighted")]
               :ref (fn [el]
                      (if el
                        (swap! dom-state assoc-in [:day-bounding-boxes date] (dom/screen-relative-bounds el))
                        (swap! dom-state update :day-bounding-boxes #(dissoc % date))))
               :on-mouse-over #((:on-mouse-over-day event-handlers) date)
               :on-mouse-out #((:on-mouse-out-day event-handlers))
               :on-click #((:on-click-day event-handlers) % date)}]))

(defn week-view
  [dom-state event-handlers week calendar-state highlighted-days]
  (let [[{week-date :date week-no :week}] week]
    [:div.week {:key week-date}
     [:div.week-no {:on-click #((:on-click-week event-handlers) % week-no week)} [:span week-no]]
     (doall (map #(day-view dom-state event-handlers % calendar-state highlighted-days)
                 week))]))

(defn month-view
  [dom-state event-handlers [date days-in-month] calendar-state highlighted-days]
  [:div.month {:key date
               :class (lower-case (:month (first days-in-month)))}
   [:div.month-date {:on-click #((:on-click-month event-handlers) % days-in-month)} [:span date]]
   [:div.weeks (doall (map
                       #(week-view dom-state event-handlers % calendar-state highlighted-days)
                       (util/weeks days-in-month)))]])

(defn calendar-view
  [app-state dom-state event-handlers]
  (let [clocks-by-day (cursor app-state [:clocks-by-day-filtered])
        calendar-state {:max-weight (reaction (->> @clocks-by-day
                                                   (map (comp util/sum-clocks-mins second))
                                                   (reduce max)))
                        :clocks-by-day clocks-by-day
                        :selected-days (reaction (union (:selected-days @app-state)
                                                        (:selected-days-preview @app-state)))}

        ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
        selecting? (cursor app-state [:selecting?])
        selected-days (cursor app-state [:selected-days])
        selected-days-preview (cursor app-state [:selected-days-preview])
        by-month (into (sorted-map) (->> @app-state
                                         :calendar
                                         vals
                                         flatten
                                         (group-by
                                          #(replace (:date %) #"^([0-9]+-[0-9]+).*" "$1"))))

        ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
        highlighted-days (reaction (set
                                    (let [highlighted-locations (-> @app-state :highlighted-entries)]
                                      (for [[date clocks] (-> @app-state :clocks-by-day)
                                            :when (first (filter #(highlighted-locations (:location %)) clocks))]
                                        date))))]

    [:div.calendar.noselect
     (sel/drag-mouse-handlers (:sel-rect @dom-state)
                              :on-selection-start #(reset! selecting? true)
                              :on-selection-end #(do
                                                   (reset! selecting? false)
                                                   (commit-potentially-selected! selected-days selected-days-preview dom-state))
                              :on-selection-change #(when @selecting?
                                                      (mark-days-as-potentially-selected app-state dom-state %)))
     (when @selecting?
       [:div.selection {:style
                        (let [[x y w h] (:relative-bounds @(:sel-rect @dom-state))]
                          {:left x :top y :width w :height h})}])
     (doall (map #(month-view dom-state event-handlers % calendar-state highlighted-days) by-month))]))
