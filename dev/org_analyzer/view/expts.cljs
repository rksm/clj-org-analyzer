(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.util :as util]
            [org-analyzer.view.calendar :as calendar]
            [org-analyzer.view.tooltip :as tooltip]
            [org-analyzer.view.timeline :as timeline]
            [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]
            [clojure.set :refer [union]]
            [cljs.pprint :refer [cl-format]]
            [org-analyzer.view.expts-helper :as e :refer [expts defexpt]]
            [org-analyzer.view.expt-test-data :refer [test-data]]
            [org-analyzer.view.clock-list :as clock-list]
            [org-analyzer.view.timeline :as timeline]))

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
                                  [clock-list/clock-list-view
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
                                         [timeline/activities-by-minute-view
                                          clock-minute-intervals-by-day-filtered
                                          highlighted-entries-cursor
                                          tooltip
                                          {:width (- js/document.documentElement.clientWidth 60)}]))]))))])))


(defexpt links-in-heading
  [clock-list/clock-list-view
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
        [:div [timeline/activities-by-minute-view
               clock-minute-intervals-by-day
               (atom nil)
               tooltip
               {:width (- js/document.documentElement.clientWidth 20)}]]))))

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


(comment
 (def app-state (:app-state (test-data)))
 (def days (-> @app-state :clocks-by-day keys))
 (def clocks (-> @app-state :clocks-by-day keys))
 (def mins (map util/sum-clocks-mins (-> @app-state :clocks-by-day vals)))
 (def mins (first (map util/sum-clocks-mins (-> @app-state :clocks-by-day vals))))
 (def mins (* 60 6))

 ;; upper ceiling for mins needed on y-axis
 (def mins-shown (or (first (drop-while #(> mins %) [(* 60 2) (* 60 5) (* 60 10)])) (* 60 24)))
 )


(defexpt bar-chart

  (let [app-state (:app-state (test-data))
        clocks-by-day (:clocks-by-day @app-state)
        mins-by-day (->> clocks-by-day vals (map util/sum-clocks-mins))
        hours-shown (->> mins-by-day
                         (map #(or (first (drop-while (fn [hours] (> % (* 60 hours))) [2 5 10])) 24))
                         (apply max))
        label-height 50
        padding-left 50
        padding-right 50
        padding-top 20
        min-day-width 10
        max-day-width 100
        n-clocks (count clocks-by-day)
        min-width (+ padding-left padding-right (* n-clocks min-day-width))
        max-width (+ padding-left padding-right (* n-clocks max-day-width))
        w (min max-width (max min-width (- js/document.documentElement.clientWidth 50)))
        h 300
        day-width (min 100 (/ w n-clocks))
        max-bar-h (- h label-height padding-top)]

    [:div.bar-chart-container {:style {:display "flex" :flex-direction "column" :align-items "center"}}
     [:canvas.bar-chart {:width w
                         :height h
                         :ref (fn [canvas]
                                (when canvas
                                  (let [ctx (. canvas (getContext "2d"))]

                                    (dotimes [h hours-shown]
                                      (let [h (inc h)
                                            x (- padding-left 20)
                                            y (+ padding-top (- max-bar-h (* (/ max-bar-h hours-shown) h)))]
                                        (. ctx (fillText (str h "h") x y))))

                                    (doseq [[i [day clocks]] (map-indexed vector clocks-by-day)
                                            :let [mins (util/sum-clocks-mins clocks)
                                                  mins-shown (or (first (drop-while #(> mins %) [(* 60 2) (* 60 5) (* 60 10)]))
                                                                 (* 60 24))
                                                  clock-h (* (/ max-bar-h (* 60 hours-shown)) mins)
                                                  x (+ padding-left (* i day-width))
                                                  y (+ padding-top (- max-bar-h clock-h))
                                                  ]]
                                      (println clock-h h mins x)
                                      (. ctx (strokeRect x y day-width clock-h))

                                      (set! (.-fillStyle ctx) "black")
                                      (set! (.-strokeStyle ctx) "rgba(60,160,120,.4)")
                                      (set! (.-lineWidth ctx) 1)
                                      (set! (.-textBaseline ctx) "top")
                                      (set! (.-textAlign ctx) "center")
                                      (set! (.-font ctx) "10px sans-serif")
                                      (set! (.-strokeStyle ctx) "black")
                                      (. ctx (fillText (-> day (subs 5) (s/replace "-" "/")) (+ x (/ day-width 2)) (+ padding-top (+ 10 max-bar-h))))))))}]]))



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
