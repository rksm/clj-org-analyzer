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
            [clojure.set :refer [union]]
            [sc.api]
            [clojure.string :as s]
            [org-analyzer.view.tooltip :as tooltip]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

(defn empty-app-state []
  (ratom {:calendar nil
          :clocks-by-day {}
          :clock-minute-intervals-by-day {}
          :hovered-over-date nil
          :selected-days #{}
          :selected-days-preview #{}
          :selecting? false
          :calendar-collapsed? false
          :clocks-collapsed? false
          :by-minute-collapsed? false
          :clock-details-collapsed? false}))

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
    (go (let [response (<! (http/get "/clocks" {:query-params {:from from :to to :by-day? true}}))
              clocks (cljs.reader/read-string (:body response))]
          (println "got clocks")

          (let [from (:start (first clocks))
                response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                calendar (into (sorted-map-by <) (map (juxt :date identity) (cljs.reader/read-string (:body response))))]
            (println "got calendar")

            (let [clocks-by-day
                  (into (sorted-map-by <) (group-by #(-> % :start (s/split #" ") first) clocks))
                  clock-minute-intervals-by-day
                  (into (sorted-map-by <) (map
                                           (fn [[key clocks]] [key (util/clock-minute-intervals clocks)])
                                           clocks-by-day))]
              (>! result-chan {:calendar calendar
                               :clocks-by-day clocks-by-day
                               :clock-minute-intervals-by-day clock-minute-intervals-by-day}))
            (close! result-chan))))
    result-chan))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn event-handlers [app-state dom-state]
  (letfn [(set-key-down! [evt key field down?]
            (when (= key (.-key evt)) (swap! dom-state assoc-in [:keys field] down?)))

          (on-key-down-global [evt]
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

(defn controls []
  [:div.controls
   [:input {:type "button"
            :value "reload"
            :on-click fetch-data
            :class ["mdl-button" "mdl-js-button" "mdl-button--raised" "mdl-js-ripple-effect"]}]])

(defn collapsible [app-state key title comp-fn]
  (let [collapsed? (key @app-state)]
    [:div.panel.elev-2
     [:button.material-button
      {:on-click #(swap! app-state update key not)}
      title
      [:i.material-icons (if collapsed? "expand_less" "expand_more")]]
     (when-not collapsed?
       (comp-fn))]))


(defn app [app-state dom-state event-handlers]

  (let [{:keys [hovered-over-date
                selected-days
                clocks-by-day
                clock-minute-intervals-by-day
                calendar]} @app-state
        n-selected (count selected-days)
        selected-days (cond
                        (> n-selected 0) (vals (select-keys calendar selected-days))
                        hovered-over-date [(get calendar hovered-over-date)]
                        :else nil)]

    [:div.app.noselect
     [controls]

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; calendar
     (collapsible app-state :calendar-collapsed? "Calendar"
                  (fn [] [calendar/calendar-view app-state dom-state event-handlers]))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; clocks
     (collapsible app-state :clocks-collapsed? "Clocks"
                  (fn [] (when selected-days
                           [selected-day/selected-days-view
                            selected-days
                            clocks-by-day
                            calendar])))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; by-minute
     (collapsible app-state :by-minute-collapsed? "Per Day"
                  (fn [] (r/with-let [tooltip (ratom nil)
                                      highlighted-clocks (ratom #{})]
                           (tooltip/with-tooltip-following-mouse tooltip
                             [:div.by-minute
                              (let [dates (map :date selected-days)
                                    clock-minute-intervals-by-day (into (sorted-map-by <) (select-keys clock-minute-intervals-by-day dates))]
                                (when (> (count dates) 0)
                                  [org-analyzer.view.day-by-minute-view/activities-by-minute-view
                                   clock-minute-intervals-by-day
                                   highlighted-clocks
                                   tooltip
                                   {:width (- js/document.documentElement.clientWidth 60)}]))]))))

     ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
     ;; clock
     (collapsible app-state :clock-details-collapsed? "Clock"
                  (fn [] "details"))]))
