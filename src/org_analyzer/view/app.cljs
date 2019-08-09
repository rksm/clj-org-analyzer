(ns org-analyzer.view.app
  (:require [reagent.core :as r :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! close! chan go]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.calendar :as calendar]
            [org-analyzer.view.util :as util]
            [org-analyzer.view.selected-day :as selected-day]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.selection :as sel]
            [clojure.set :refer [union difference]]
            [clojure.string :as s]
            [org-analyzer.view.tooltip :as tooltip]
            [org-analyzer.view.search-view :as search-view]
            [cljs.reader :as reader]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

(defn empty-app-state []
  (ratom {:calendar nil
          :clocks-by-day {}
          :clocks-by-day-filtered {}
          :clock-minute-intervals-by-day {}
          :hovered-over-date nil
          :selected-days #{}
          :selected-days-preview #{}
          :selecting? false
          :calendar-collapsed? false
          :clocks-collapsed? false
          :by-minute-collapsed? false
          :clock-details-collapsed? false
          :highlighted-calendar-dates #{}
          :highlighted-entries #{} ;; uses clock :locations for id'ing
          :search-input ""
          :search-focused? false
          :loading? true}))

(defn empty-dom-state []
  (atom {:sel-rect (atom sel/empty-rectangle-selection-state)
         :keys {:shift-down? false
                :alt-down? false}
         :day-bounding-boxes {}}))

(defn fetch-data
  [& {:keys [from to]
      :or {from (js/Date. "2000-01-01")
           to (js/Date.)}}]
  (let [result-chan (chan 1)
        from (util/format-date-time from)
        to (util/format-date-time to)]
    (go (let [response (<! (http/get "/clocks"
                                     {:query-params {:from from :to to :by-day? true}
                                      :headers {"Cache-Control" "no-cache"}}))
              clocks (reader/read-string (:body response))]
          (println "got clocks")

          (let [from (:start (first clocks))
                response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                calendar (into (sorted-map-by <) (map (juxt :date identity) (reader/read-string (:body response))))]
            (println "got calendar")

            (let [clocks-by-day (group-by #(-> % :start (s/split #" ") first) clocks)
                  clocks-by-day (merge
                                 (into (sorted-map-by <) (map #(vector % []) (difference
                                                                              (set (keys calendar))
                                                                              (set (keys clocks-by-day)))))
                                 clocks-by-day)]
              (>! result-chan {:calendar calendar
                               :clocks-by-day clocks-by-day
                               :clock-minute-intervals-by-day (util/clock-minute-intervals-by-day clocks-by-day)
                               :clocks-by-day-filtered clocks-by-day
                               :clock-minute-intervals-by-day-filtered (util/clock-minute-intervals-by-day clocks-by-day)}))
            (close! result-chan))))
    result-chan))

(defn fetch-and-update! [app-state]
  (go (swap! app-state merge (<! (fetch-data)) {:loading? false})))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn event-handlers [app-state dom-state]
  (letfn [(set-key-down! [evt key field down?]
            (when (= key (.-key evt)) (swap! dom-state assoc-in [:keys field] down?)))

          (on-key-down-global [evt]
            (cond
              ;; select all
              (and (= "a" (.-key evt)) (.-ctrlKey evt))
              (do (swap! app-state assoc :selected-days (set (keys (:calendar @app-state))))
                  (.preventDefault evt))

              ;; focus search
              (and (= "s" (.-key evt)) (.-ctrlKey evt))
              (do (swap! app-state assoc :search-focused? true)
                  (.preventDefault evt)))

            (set-key-down! evt "Alt" :alt-down? true)
            (set-key-down! evt "Shift" :shift-down? true))

          (on-key-up-global [evt]
            (set-key-down! evt "Alt" :alt-down? false)
            (set-key-down! evt "Shift" :shift-down? false))

          (on-window-resize [evt] nil)]

    (.addEventListener js/document "keydown" on-key-down-global)
    (.addEventListener js/document "keyup" on-key-up-global)
    (.addEventListener js/window "resize" on-window-resize)
    (println "registering global event handlers")

    (merge (calendar/event-handlers app-state dom-state)
           {:on-key-down-global on-key-down-global
            :on-key-up-global on-key-up-global
            :on-window-resize on-window-resize})))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn controls [app-state]
  [:div.controls
   [:input {:type "button"
            :value "reload"
            :on-click #(fetch-and-update! app-state)}]])


(defn collapsible* [title key collapsed-atom comp-fn]
  (let [collapsed? @collapsed-atom]
    [:div.panel.elev-2
     [:button.material-button
      {:on-click #(reset! collapsed-atom (not collapsed?))}
      title
      [:i.material-icons (if collapsed? "expand_less" "expand_more")]]
     (when-not collapsed?
       (comp-fn))]))


(defn app [app-state dom-state event-handlers]

  (let [{:keys [hovered-over-date
                selected-days
                clocks-by-day-filtered
                clock-minute-intervals-by-day-filtered
                calendar]} @app-state
        n-selected (count selected-days)
        selected-days (cond
                        (> n-selected 0) (vals (select-keys calendar selected-days))
                        hovered-over-date [(get calendar hovered-over-date)]
                        :else nil)

        highlighted-entries-cursor (r/cursor app-state [:highlighted-entries])]

    [:div.app
     (when (:loading? @app-state)
       [:div.loading-indicator
        [:div.loading-spinner
         [:div] [:div] [:div] [:div]
         [:div] [:div] [:div] [:div]
         [:div] [:div] [:div] [:div]]])

     #_[controls app-state]

     [search-view/search-bar app-state]

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; calendar
     (collapsible* "Calendar" :calendar-collapsed? (r/cursor app-state [:calendar-collapsed?])
                   (fn [] [calendar/calendar-view app-state dom-state event-handlers]))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; clocks
     (collapsible* "Clocks" :clocks-collapsed? (r/cursor app-state [:clocks-collapsed?])
                   (fn [] (when selected-days
                            [selected-day/selected-days-view
                             selected-days
                             clocks-by-day-filtered
                             calendar
                             highlighted-entries-cursor])))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; by-minute
     (collapsible* "Per Day" :by-minute-collapsed? (r/cursor app-state [:by-minute-collapsed?])
                   (fn [] (r/with-let [tooltip (ratom nil)]
                            (tooltip/with-tooltip-following-mouse tooltip
                              [:div.by-minute
                               (let [dates (map :date selected-days)
                                     clock-minute-intervals-by-day-filtered (into (sorted-map-by <) (select-keys clock-minute-intervals-by-day-filtered dates))]
                                 (when (> (count dates) 0)
                                   [org-analyzer.view.day-by-minute-view/activities-by-minute-view
                                    clock-minute-intervals-by-day-filtered
                                    highlighted-entries-cursor
                                    tooltip
                                    {:width (- js/document.documentElement.clientWidth 60)}]))]))))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; clock
     #_(collapsible* "Clock" :clock-details-collapsed? (r/cursor app-state [:clock-details-collapsed?])
                   (fn [] "details"))]))
