(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-clocks]]
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
            [org-analyzer.view.expts-helper :as e :refer [expts defexpt]]))

(enable-console-print!)

(e/purge-all!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def expt-1-state
  (let [app-state (app/empty-app-state)
        dom-state (app/empty-dom-state)
        event-handlers (app/event-handlers app-state dom-state)]

    ;; fetch data
    (go (swap! app-state merge
               (<! (fetch-clocks :from (js/Date. "2019-07-01")
                                 :to (js/Date. "2019-08-06")))))

    {:app-state app-state
     :dom-state dom-state
     :event-handlers event-handlers }))

(defexpt calendar
  (let [{:keys [dom-state event-handlers app-state]} expt-1-state
        month-date-and-days (->> @app-state
                                 :calendar
                                 vals
                                 flatten
                                 (group-by #(s/replace (:date %) #"^([0-9]+-[0-9]+).*" "$1"))
                                 (into (sorted-map))
                                 first)
        clocks-by-day (cursor app-state [:clocks-by-day])
        max-weight (reaction (->> @clocks-by-day
                                  (map (comp util/sum-clocks-mins second))
                                  (reduce max)))
        calendar-state {:max-weight max-weight
                        :clocks-by-day clocks-by-day
                        :selected-days (reaction (union (:selected-days @app-state)
                                                        (:selected-days-preview @app-state)))}]
    [:div.expt
     [:h1 "expt 1"]
     (if (empty? (:calendar @app-state))
       [:span "Loading..."]
       [calendar/month-view
        dom-state
        event-handlers
        month-date-and-days
        calendar-state])]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defexpt activities-by-minute-view
  (let [app-state (:app-state expt-1-state)
        clocks-by-day (:clocks-by-day @app-state)
        clock-minute-intervals-by-day (:clock-minute-intervals-by-day @app-state)]
    (rg/with-let [tooltip (ratom "")]
      (tooltip/with-tooltip-following-mouse
        tooltip
        [:div [day-by-minute-view/activities-by-minute-view
               clock-minute-intervals-by-day
               tooltip]]))))

(defexpt by-minute-debug-1
  (let [app-state (:app-state expt-1-state)
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
  (let [app-state (:app-state expt-1-state)
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

;; (def el (first (js->clj (js/Array.from (js/document.getElementsByClassName "stalker")))))

;; (dom/screen-relative-bounds el)

(defexpt tooltip

  (rg/with-let [tooltip (ratom "foo")]
    (tooltip/with-tooltip-following-mouse
      tooltip
      [:div {:style {:background "red"
                     :height "300px"
                     :width "300px"}
             :on-mouse-move (fn [evt] (let [[x y] (dom/mouse-position evt)]
                                        (reset! tooltip [:div [:h1 (str x "/" y)]])))}
       [:div.foo "barrr"]
       ;; [:div.foo "zorrr"]
       ])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn start []
  (rg/render [e/expts]
             (. js/document (querySelector "#app"))
             #(println "rendered")))

(start)
