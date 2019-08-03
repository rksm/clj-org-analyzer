(ns org-analyzer.view.app
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! close! chan go]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.selection :as sel]
            [clojure.set :refer [union]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

(defn empty-app-state []
  (ratom {:calendar nil
          :clocks-by-day {}
          :hovered-over-day nil
          :selected-days #{}
          :selected-days-preview #{}
          :selecting? false}))

(defn empty-dom-state []
  (atom {:sel-rect (atom sel/empty-rectangle-selection-state)
         :keys {:shift-down? false
                :alt-down? false}
         :day-bounding-boxes {}}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn event-handlers [app-state dom-state]
  (letfn [(set-key-down! [evt key field down?]
            (when (= key (.-key evt)) (swap! dom-state assoc-in [:keys field] down?)))

          (shift-down? [] (-> @dom-state :keys :shift-down?))

          (on-key-down-global [evt]
            (set-key-down! evt "Alt" :alt-down? true)
            (set-key-down! evt "Shift" :shift-down? true))

          (on-key-up-global [evt]
            (set-key-down! evt "Alt" :alt-down? false)
            (set-key-down! evt "Shift" :shift-down? false))

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
              (swap! app-state assoc :hovered-over-day date)))

          (on-mouse-out-day []
            (swap! app-state assoc :hovered-over-day nil))

          (on-click-day [evt date]
            (let [add-selection? (shift-down?)]
              (swap! app-state update :selected-days #(cond
                                                       (and add-selection? (% date)) (disj % date)
                                                       add-selection? (conj % date)
                                                       :else #{date}))))]

    (println "registering global event handlers")
    (.addEventListener js/document "keydown" on-key-down-global)
    (.addEventListener js/document "keyup" on-key-up-global)

    {:on-key-down-global on-key-down-global
     :on-key-up-global on-key-up-global
     :on-click-month on-click-month
     :on-click-week on-click-week
     :on-mouse-over-day on-mouse-over-day
     :on-mouse-out-day on-mouse-out-day
     :on-click-day on-click-day}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn date-string [^js/Date date]
  (first (split (.toISOString date) \T)))

(defn- weeks [days]
  (loop [week [] weeks [] days days]
    (if (empty? days)
      (if (empty? week) weeks (conj weeks week))
      (let [[day & days] days
            week (conj week day)
            sunday? (= 7 (:dow day))
            weeks (if sunday? (conj weeks week) weeks)]
        (recur (if sunday? [] week) weeks days)))))

(defn sum-clocks-mins [clocks]
  (reduce + (for [{:keys [duration]} clocks]
              (let [[hours mins] (map #(js/Number. %) (split duration ":"))
                    result (+ (* 60 hours) mins)]
                (if (js/isNaN result) 0 result)))))

(defn sum-clocks-count [clocks]
  (count clocks))

(def org-link-re #"(.*)\[\[([^\]]+)\]\[([^\]]+)\]\](.*)")

(defn parse-org-link [string i]
  (if-let [[_ before link link-title after]
           (re-find org-link-re string)]
    [[:span {:key i} before]
     [:a {:key (+ 1 i) :href link :target "_blank"} link-title]
     [:span {:key (+ 2 i)} after]]
    nil))

(defn parse-all-org-links [string]
  (loop [[[_ attrs string] & rest] [[:span {:key 0} string]]
         i 1]
    (if-let [[& parsed] (parse-org-link string i)]
      (recur (concat parsed rest) (+ i (count parsed)))
      (concat [[:span attrs string]] rest))))

(defn print-duration-mins [mins]
  (let [hours (quot mins 60)
        mins (- mins (* hours 60))]
    (cl-format nil "~d:~2,'0d" hours mins)))

(defn fetch-data
  [& {:keys [from to]
      :or {from (js/Date. "2000-01-01")
           to (js/Date.)}}]
  (let [result-chan (chan 1)
        from (pr-str from)
        to (pr-str to)]
    (go (let [response (<! (http/get "/clocks" {:query-params {:from from :to to :by-day? true}}))
              clocks (cljs.reader/read-string {:readers {'inst #(js/Date. %)}} (:body response))]
          (println "got clocks")
          (let [from (pr-str (:start (first clocks)))
                response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                body (cljs.reader/read-string (:body response))]
            (println "got calendar")
            (>! result-chan {:calendar body :clocks-by-day
                             (group-by (comp date-string :start) clocks)})
            (close! result-chan))))
    result-chan))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; rectangle selection helpers
(defn- mark-days-as-potentially-selected [app-state dom-state sel-state]
  (let [{:keys [left top width height]} (:global-bounds sel-state)
        contained (into #{} (for [[day {l :left t :top r :right b :bottom}]
                                  (:day-bounding-boxes @dom-state)
                                  :when (and (<= left l)
                                             (<= top t)
                                             (>= (+ left width) r)
                                             (>= (+ top height) b))]
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
   {:keys [clocks-by-day selected-days max-weight] :as calendar-state}]
  (let [clocks (get clocks-by-day date)
        selected? (selected-days date)]
    [:div.day {:key date
               :id date
               :class [(emph-css-class
                        (sum-clocks-mins clocks)
                        max-weight)
                       (if selected? "selected")]
               :ref (fn [el]
                      (if el
                        (swap! dom-state assoc-in [:day-bounding-boxes date] (dom/el-bounds el))
                        (swap! dom-state update :day-bounding-boxes #(dissoc % date))))
               :on-mouse-over #((:on-mouse-over-day event-handlers) date)
               :on-mouse-out #((:on-mouse-out-day event-handlers))
               :on-click #((:on-click-day event-handlers) % date)}]))

(defn week-view [dom-state event-handlers week calendar-state]
  (let [[{week-date :date week-no :week}] week]
    [:div.week {:key week-date}
     [:div.week-no {:on-click #((:on-click-week event-handlers) % week-no week)} [:span week-no]]
     (map #(day-view dom-state event-handlers % calendar-state) week)]))

(defn month-view [dom-state event-handlers [date days-in-month] calendar-state]
  [:div.month {:key date
               :class (lower-case (:month (first days-in-month)))}
   [:div.month-date {:on-click #((:on-click-month event-handlers) % days-in-month)} [:span date]]
   [:div.weeks (map #(week-view dom-state event-handlers % calendar-state) (weeks days-in-month))]])

(defn calendar-view
  [app-state dom-state event-handlers]
  (let [max-weight (->> @app-state
                        :clocks-by-day
                        (map (comp sum-clocks-mins second))
                        (reduce max))
        calendar-state {:max-weight max-weight
                        :clocks-by-day (:clocks-by-day @app-state)
                        :selected-days (union (:selected-days @app-state)
                                              (:selected-days-preview @app-state))}]

    (let [selecting? (r/cursor app-state [:selecting?])
          selected-days (r/cursor app-state [:selected-days])
          selected-days-preview (r/cursor app-state [:selected-days-preview])
          by-month (into (sorted-map) (->> @app-state
                                           :calendar
                                           (group-by
                                            #(replace (:date %) #"^([0-9]+-[0-9]+).*" "$1"))))]

      [:div.calendar
       (sel/drag-mouse-handlers (:sel-rect @dom-state)
                                :on-selection-start #(reset! selecting? true)
                                :on-selection-end #(do
                                                     (reset! selecting? false)
                                                     (commit-potentially-selected! selected-days selected-days-preview dom-state))
                                :on-selection-change #(mark-days-as-potentially-selected app-state dom-state %))
       (when @selecting?
         [:div.selection {:style (:relative-bounds @(:sel-rect @dom-state))}])
       (map #(month-view dom-state event-handlers % calendar-state) by-month)])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn selected-day-view [date clocks-by-day]
  (if (nil? date)
    nil
    (let [clocks (get clocks-by-day date)
          location-durations (reverse
                              (sort-by second
                                       (map (fn [[a b]] [a (sum-clocks-mins b)])
                                            (group-by :location clocks))))]
      [:div.day-detail
       [:div.date date]
       [:div.hours (print-duration-mins (apply + (map second location-durations)))]
       [:div.clock-list
        (for [[location duration] location-durations]
          [:div.activity {:key location}
           [:span.duration (print-duration-mins duration)]
           (parse-all-org-links location)])]])))

(defn analyze-clocks [days clocks-by-day]
  (letfn [(clocks-avg [clocks-by-sth]
            (print-duration-mins
             (quot (->> clocks-by-sth
                        (map (comp sum-clocks-mins second))
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
                                     (map (fn [[a b]] [a (sum-clocks-mins b)])
                                          (group-by :location clocks))))
        duration (sum-clocks-mins clocks)
        days (filter #(-> % :date dates) calendar)
        {:keys [average-day-duration
                average-week-duration
                n-weeks]} (analyze-clocks days clocks-by-day)]
    [:div.day-detail
     [:div.date
      (cl-format nil "~d days over ~d week~:*~P selected" (count dates) n-weeks)]
     [:div (str "Average time per week: " average-week-duration)]
     [:div (str "Average time per day: " average-day-duration)]
     [:div.hours (str "Entire time: " (print-duration-mins duration))]
     [:div (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count location-durations))]
     [:div.clock-list
      (for [[location duration] location-durations]
        [:div.activity {:key location}
         [:span.duration (print-duration-mins duration)]
         (parse-all-org-links location)])]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn controls []
  [:div.controls
   [:input {:type "button" :value "reload" :on-click fetch-data}]])


(defn app [app-state dom-state event-handlers]
  [:div.app.noselect
   [controls]
   [:div [calendar-view app-state dom-state event-handlers]]
   [:div (let [{:keys [hovered-over-day
                       selected-days
                       clocks-by-day
                       calendar]} @app-state
               n-selected (count selected-days)]
           (cond
             hovered-over-day [selected-day-view hovered-over-day clocks-by-day]
             (= n-selected 1) [selected-day-view (first selected-days) clocks-by-day]
             (> n-selected 1) [selected-days-view selected-days clocks-by-day calendar]
             :else nil))]])
