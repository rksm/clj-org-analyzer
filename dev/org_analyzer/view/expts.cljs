(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.util :as util]
            [org-analyzer.view.calendar :as calendar]
            [org-analyzer.view.tooltip :as tooltip]
            [org-analyzer.view.day-by-minute-view :as day-by-minute-view]
            [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]
            [clojure.set :refer [union]]
            [cljs.pprint :refer [cl-format]]
            [org-analyzer.view.expts-helper :as e :refer [expts defexpt]]
            [org-analyzer.view.expt-test-data :refer [test-data]]
            [org-analyzer.view.selected-day :as selected-day]
            [org-analyzer.view.day-by-minute-view :as day-by-minute-view]))

(enable-console-print!)

(e/purge-all!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(let [{:keys [app-state dom-state event-handlers]} (test-data)]
  (defexpt calendar
    (println "render" (-> @app-state :selected-days))
    [:div
     [calendar/calendar-view app-state dom-state event-handlers]
     [:div.state
      [:span "selected days" (-> @app-state :selected-days)]]]))


(let [{:keys [app-state dom-state event-handlers]} (test-data)]
  (defexpt selectable-calendar
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

          highlighted-entries-cursor (rg/cursor app-state [:highlighted-entries])]

      [:div.app
       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; calendar
       (app/collapsible* "Calendar" :calendar-collapsed? (rg/cursor app-state [:calendar-collapsed?])
                         (fn [] [calendar/calendar-view app-state dom-state event-handlers]))

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; clocks
       (app/collapsible* "Clocks" :clocks-collapsed? (rg/cursor app-state [:clocks-collapsed?])
                         (fn [] (when selected-days
                                  [selected-day/selected-days-view
                                   selected-days
                                   clocks-by-day-filtered
                                   calendar
                                   highlighted-entries-cursor])))

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; by-minute
       (app/collapsible* "Per Day" :by-minute-collapsed? (rg/cursor app-state [:by-minute-collapsed?])
                         (fn [] (rg/with-let [tooltip (ratom nil)]
                                  (tooltip/with-tooltip-following-mouse tooltip
                                    [:div.by-minute
                                     (let [dates (map :date selected-days)
                                           clock-minute-intervals-by-day-filtered (into (sorted-map-by <) (select-keys clock-minute-intervals-by-day-filtered dates))]
                                       (when (> (count dates) 0)
                                         [day-by-minute-view/activities-by-minute-view
                                          clock-minute-intervals-by-day-filtered
                                          highlighted-entries-cursor
                                          tooltip
                                          {:width (- js/document.documentElement.clientWidth 60)}]))]))))])))


(defexpt links-in-heading
  [selected-day/selected-days-view
   [{:date "2019-08-27" :dow 2 :dow-name "Tuesday"  :week 35 :month "August" :year 2019}]
   {"2019-08-27" [{:start "2019-08-27 19:17"
                   :end "2019-08-27 19:27"
                   :duration "0:10"
                   :path ["baz.org"
                          "[[https://github.com/rksm/clj-org-analyzer/issues/8][Org-links in the heading #8]]"]
                   :name "activity 2"
                   :location "baz.org/zork/activity 2"
                   :tags #{"org" "tag 3"}}]}
   {"2019-08-27" {:date "2019-08-27", :dow 2, :dow-name "Tuesday", :week 35, :month "August", :year 2019}}
   (atom #{})])

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defexpt activities-by-minute-view
  (let [app-state (:app-state (test-data))
        clocks-by-day (:clocks-by-day @app-state)
        clock-minute-intervals-by-day (:clock-minute-intervals-by-day @app-state)]
    (rg/with-let [tooltip (ratom "")]
      (tooltip/with-tooltip-following-mouse
        tooltip
        [:div [day-by-minute-view/activities-by-minute-view
               clock-minute-intervals-by-day
               (atom nil)
               tooltip
               {:width (- js/document.documentElement.clientWidth 20) :height 500}]]))))

(defexpt by-minute-debug-1
  (let [app-state (:app-state (test-data))
        clocks-by-day (:clocks-by-day @app-state)]

    [:div.fix-clocks-by-minute.verbatim
     (doall (for [[i [day clocks]] (map-indexed vector clocks-by-day)]
              ^{:key (str day "-" i)}
              [:div
               [:div day]
               (doall
                (for [{:keys [location start end]} clocks]
                  ^{:key start} [:div.clock "  [" start "] - [" end "]: " location]))]))]))

(defexpt by-minute-debug-2
  (let [app-state (:app-state (test-data))
        clock-intervals (:clock-minute-intervals-by-day @app-state)]

    [:div.fix-clocks-by-minute.verbatim
     (doall (for [[day intervals] clock-intervals]
              [:div
               [:div day]
               (doall (for [[from to clocks] intervals]
                        [:div (str "  " from " " to)
                         (doall (for [{:keys [location start end]} clocks]
                                  ^{:key (str day location)}
                                  [:div.clock "    [" start "] - [" end "]: " location]))]))
               ]))]))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defexpt tooltip
  (rg/with-let [tooltip (ratom "foo")]
    (tooltip/with-tooltip-following-mouse
      tooltip
      [:div {:style {:background "red"
                     :height "300px"
                     :width "300px"}
             :on-mouse-move (fn [evt] (let [[x y] (dom/mouse-position evt)]
                                        (reset! tooltip [:div [:h1 (str x "/" y)]])))}
       [:div.foo]])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (app/send-cancel-kill-server-request!)
  (rg/render [e/expts]
             (. js/document (querySelector "#app"))
             #(println "rendered")))

(start)
