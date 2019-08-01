(ns org-analyzer.view.app
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.selection :as sel]
            [clojure.set :refer [union]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(declare on-key-down-global)
(declare on-key-up-global)
(declare state)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

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

(defn fetch-data []
  (let [from (pr-str (js/Date. "2000-01-01"))
        to (pr-str (js/Date.))]
    (go (let [response (<! (http/get "/clocks" {:query-params {:from from :to to :by-day? true}}))
              clocks (cljs.reader/read-string {:readers {'inst #(js/Date. %)}} (:body response))]
          (println "got clocks")
          (reset! (:clocks-by-day state) (group-by (comp date-string :start) clocks))
          (let [from (pr-str (:start (first clocks)))]
            (go (let [response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                      body (cljs.reader/read-string (:body response))]
                  (println "got calendar")
                  (reset! (:calendar state) body))))))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; state

(defonce state {:calendar (ratom nil)
                :clocks-by-day (ratom {})
                :hovered-over-day (ratom nil)
                :selected-days (ratom #{})
                :selected-days-preview (ratom #{})
                :selecting? (ratom false)
                :sel-rect (atom sel/empty-rectangle-selection-state)
                :keys (atom {:shift-down? false
                             :alt-down? false})
                :dom-state (atom {:day-bounds {}})
                :global-event-handlers (let [down #(on-key-down-global %)
                                             up #(on-key-up-global %)]
                                         (.addEventListener js/document "keydown" down)
                                         (.addEventListener js/document "keyup" up)
                                         (atom {:key-down down :key-up up}))})

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; global events

(defn on-key-down-global [evt]
  (when (= "Alt" (.-key evt)) (swap! (:keys state) assoc :alt-down? true))
  (when (= "Shift" (.-key evt)) (swap! (:keys state) assoc :shift-down? true)))

(defn on-key-up-global [evt]
  (when (= "Alt" (.-key evt)) (swap! (:keys state) assoc :alt-down? false))
  (when (= "Shift" (.-key evt)) (swap! (:keys state) assoc :shift-down? false)))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; rectangle selection helpers
(defn- mark-days-as-potentially-selected [sel-state]
  (let [{:keys [left top width height]} (:global-bounds sel-state)
        contained (into #{} (for [[day {l :left t :top r :right b :bottom}]
                                  (-> state :dom-state deref :day-bounds)
                                  :when (and (<= left l)
                                             (<= top t)
                                             (>= (+ left width) r)
                                             (>= (+ top height) b))]
                              day))]
    (reset! (:selected-days-preview state) contained)))

(defn- commit-potentially-selected! []
  (let [selected (if (-> state :keys deref :shift-down?)
                   (union @(:selected-days-preview state)
                          @(:selected-days state))
                   @(:selected-days-preview state))]
    (reset! (:selected-days-preview state) #{})
    (reset! (:selected-days state) selected)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- emph-css-class [count max-count]
  (-> count
      (/ max-count)
      (* 10)
      js/Math.round
      ((partial str "emph-"))))

(defn on-mouse-over-day [date]
  (when (not @(:selecting? state))
    (reset! (:hovered-over-day state) date)))

(defn on-mouse-out-day []
  (reset! (:hovered-over-day state) nil))

(defn on-click-day [evt date]
  (let [add-selection? (-> state :keys deref :shift-down?)]
    (swap! (:selected-days state) (fn [selected-days]
                                    (cond
                                      (and add-selection? (selected-days date)) (disj selected-days date)
                                      add-selection? (conj selected-days date)
                                      :else #{date})))))

(defn day-view [{:keys [date]} {:keys [clocks-by-day selected-days max-weight] :as calendar-state}]
  (let [clocks (get clocks-by-day date)
        selected? (selected-days date)]
    [:div.day {:key date
               :id date
               :class [(emph-css-class
                        (sum-clocks-mins clocks)
                        max-weight) (if selected? "selected")]
               :ref (fn [el]
                      (if el
                        (swap! (:dom-state state) #(update % :day-bounds assoc date (dom/el-bounds el)))
                        (swap! (:dom-state state) #(update % :day-bounds dissoc date))))
               :on-mouse-over (partial on-mouse-over-day date)
               :on-mouse-out on-mouse-out-day
               :on-click #(on-click-day % date)}]))

(defn on-click-week [evt week-no days]
  (let [dates (into #{} (map :date days))
        add-selection? (-> state :keys deref :shift-down?)]
    (swap! (:selected-days state) (fn [selected-days]
                                    (cond
                                      add-selection? (union selected-days dates)
                                      :else dates)))))

(defn week-view [week calendar-state]
  (let [[{week-date :date week-no :week}] week]
    [:div.week {:key week-date}
     [:div.week-no {:on-click #(on-click-week % week-no week)} [:span week-no]]
     (map #(day-view % calendar-state) week)]))


(defn on-click-month [evt days]
  (let [dates (into #{} (map :date days))
        add-selection? (-> state :keys deref :shift-down?)]
    (swap! (:selected-days state) (fn [selected-days]
                                    (cond
                                      add-selection? (union selected-days dates)
                                      :else dates)))))

(defn month-view [[date days-in-month] calendar-state]
  [:div.month {:key date
               :class (lower-case (:month (first days-in-month)))}
   [:div.month-date {:on-click #(on-click-month % days-in-month)} [:span date]]
   [:div.weeks (map #(week-view % calendar-state) (weeks days-in-month))]])

(defn calendar-view
  [& {:keys [clocks-by-day
             calendar
             selected-days
             selected-days-preview
             selecting?
             sel-rect]}]
  (let [max-weight (reduce max (map (comp sum-clocks-mins second) clocks-by-day))
        calendar-state {:max-weight max-weight
                        :clocks-by-day clocks-by-day
                        :selected-days (union selected-days selected-days-preview)}]

    (let [by-month (into (sorted-map) (group-by
                                       (comp
                                        (partial join "-")
                                        (partial take 2)
                                        #(split % "-")
                                        :date)
                                       calendar))]

      [:div.calendar
       (sel/drag-mouse-handlers (:sel-rect state)
                                :on-selection-start #(reset! (:selecting? state) true)
                                :on-selection-end #(do
                                                     (reset! (:selecting? state) false)
                                                     (commit-potentially-selected!))
                                :on-selection-change mark-days-as-potentially-selected)
       (when selecting?
         [:div.selection {:style (:relative-bounds sel-rect)}])
       (map #(month-view % calendar-state) by-month)])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn selected-day [date clocks-by-day]
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



(defn selected-days [dates clocks-by-day calendar]
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


(defn app []
  [:div.app.noselect
   [controls]
   [:div [calendar-view
          :calendar @(:calendar state)
          :clocks-by-day @(:clocks-by-day state)
          :selected-days @(:selected-days state)
          :selected-days-preview @(:selected-days-preview state)
          :selecting? @(:selecting? state)
          :sel-rect @(:sel-rect state)]]

   [:div (let [hovered @(:hovered-over-day state)
               selected @(:selected-days state)
               clocks @(:clocks-by-day state)
               n-selected (count selected)
               calendar @(:calendar state)]
           (cond
             hovered [selected-day hovered clocks]
             (= n-selected 1) [selected-day (first selected) clocks]
             (> n-selected 1) [selected-days selected clocks calendar]
             :else nil))]])

