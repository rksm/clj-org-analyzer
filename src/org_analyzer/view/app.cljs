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
            [clojure.string :as s]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

(defn empty-app-state []
  (ratom {:calendar nil
          :clocks-by-day {}
          :clock-minute-intervals-by-day {}
          :hovered-over-day nil
          :selected-days #{}
          :selected-days-preview #{}
          :selecting? false}))

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
                calendar (cljs.reader/read-string (:body response))]
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
            (set-key-down! evt "Shift" :shift-down? false))]

    (println "registering global event handlers")
    (.addEventListener js/document "keydown" on-key-down-global)
    (.addEventListener js/document "keyup" on-key-up-global)

    (merge (calendar/event-handlers app-state dom-state)
           {:on-key-down-global on-key-down-global
            :on-key-up-global on-key-up-global})))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn controls []
  [:div.controls
   [:input {:type "button" :value "reload" :on-click fetch-data}]])


(defn app [app-state dom-state event-handlers]
  [:div.app.noselect
   [controls]
   [:div [calendar/calendar-view app-state dom-state event-handlers]]
   [:div (let [{:keys [hovered-over-day
                       selected-days
                       clocks-by-day
                       calendar]} @app-state
               n-selected (count selected-days)]
           (cond
             hovered-over-day [selected-day/selected-day-view hovered-over-day clocks-by-day]
             (= n-selected 1) [selected-day/selected-day-view (first selected-days) clocks-by-day]
             (> n-selected 1) [selected-day/selected-days-view selected-days clocks-by-day calendar]
             :else nil))]])
