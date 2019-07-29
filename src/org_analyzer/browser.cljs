(ns org-analyzer.browser
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(enable-console-print!)

(println "running")

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

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn sum-clocks-mins [clocks]
  (reduce + (for [{:keys [duration]} clocks]
              (let [[hours mins] (map #(js/Number. %) (split duration ":"))
                    result (+ (* 60 hours) mins)]
                (if (js/isNaN result) 0 result)))))

(defn sum-clocks-count [clocks]
  (count clocks))

#_(reset! (:sum-clocks-fn state) sum-clocks-mins)
#_(reset! (:sum-clocks-fn state) sum-clocks-count)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def event (atom nil))

(defonce state {:sum-clocks-fn (ratom sum-clocks-mins)
                :calendar (ratom nil)
                :clocks (ratom [])
                :hovered-over-day (ratom nil)
                :selected-day (ratom nil)})

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn fetch-data []
  (let [from (pr-str (js/Date. "2000-01-01"))
        to (pr-str (js/Date.))]

    (go (let [response (<! (http/get "/clocks" {:query-params {:from from :to to :by-day? true}}))
              body (cljs.reader/read-string {:readers {'inst #(js/Date. %)}} (:body response))]
          (println "got clocks")
          (reset! (:clocks state) body)
          ;; (js/console.log body)

          (let [from (pr-str (:start (first @(:clocks state))))]
            (go (let [response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                      body (cljs.reader/read-string (:body response))]
                  (println "got calendar")
                  ;; (prn response)
                  (reset! (:calendar state) body)
                  ;; (js/console.log body)
                  ;; (println (cljs.tools.reader.edn/read-string (:body response)))
                  )))

          ))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- emph-css-class [count max-count]
  (-> count
      (/ max-count)
      (* 10)
      js/Math.round
      ((partial str "emph-"))))


(defn on-mouse-over-day [{:keys [day clocks] :as evt}]
  (reset! (:hovered-over-day state) evt)
  (reset! event evt))


(defn on-mouse-out-day [{:keys [day clocks] :as evt}]
  (reset! (:hovered-over-day state) nil)
  ;(reset! event evt)
  )

(defn on-click-day [day]
  (println day)
  (let [selected-day (some-> state :selected-day deref)]
    (println selected-day)
    (reset! (:selected-day state)
            (if (and selected-day (= day selected-day)) nil day))))


(defn day-view [{:keys [date] :as day} {:keys [clocks-by-day selected-day sum-clocks-fn max-weight] :as calendar-state}]
  (let [clocks (get clocks-by-day date)
        selected? (= selected-day day)]
    [:div.day {:key date
               :class [(emph-css-class
                        (sum-clocks-fn clocks)
                        max-weight) (if selected? "selected")]
               :on-mouse-over #(on-mouse-over-day {:day day :clocks clocks})
               :on-mouse-out #(on-mouse-out-day {:day day :clocks clocks})
               :on-click #(on-click-day day)}]))

(defn week-view [week calendar-state]
  (let [week-date (:date (first week))]
    [:div.week {:key week-date}
     (map #(day-view % calendar-state) week)]))


(defn month-view [[date days-in-month] calendar-state]
  [:div.month {:key date
               :class (lower-case (:month (first days-in-month)))}
   date
   [:div.weeks (map #(week-view % calendar-state) (weeks days-in-month))]])


(defn calendar-view [clocks calendar]
  (let [clocks-by-day (group-by (comp date-string :start) clocks)
        sum-clocks-fn @(:sum-clocks-fn state)
        max-weight (reduce max (map (comp sum-clocks-fn second) clocks-by-day))
        calendar-state {:max-weight max-weight
                        :sum-clocks-fn sum-clocks-fn
                        :clocks-by-day clocks-by-day
                        :selected-day @(:selected-day state)}]

    (let [by-month (into (sorted-map) (group-by
                                       (comp
                                        (partial join "-")
                                        (partial take 2)
                                        #(split % "-")
                                        :date)
                                       calendar))]
      [:div.calendar
       (map #(month-view % calendar-state) by-month)])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn current-day [{:keys [day clocks]}]
  (if (empty? clocks)
    nil
    (let [location-durations (reverse
                              (sort-by second
                                       (map (fn [[a b]] [a (sum-clocks-mins b)])
                                            (group-by :location clocks))))]
      [:div.day-detail
       [:div.date (str (:date day))]
       [:div.hours (print-duration-mins (apply + (map second location-durations)))]
       [:div.clock-list
        (for [[location duration] location-durations]
          [:div.activity {:key location}
           [:span.duration (print-duration-mins duration)]
           (parse-all-org-links location)])]])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn reload-button []
  [:input {:type "button" :value "reload" :on-click fetch-data}])


(defn app []
  [:div.app
   [reload-button]
   [:div [calendar-view @(:clocks state) @(:calendar state)]]
   [:div [current-day (or @(:hovered-over-day state) @(:selected-day state))]]
   ])

(defn start []
  (r/render [app]
            (js/document.querySelector "#app"))
  (fetch-data))

(start)
