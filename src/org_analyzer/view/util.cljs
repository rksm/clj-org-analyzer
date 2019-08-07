(ns org-analyzer.view.util
  (:require [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]]
            [cljs.reader :as reader])
  (:import goog.async.Debouncer))

(defn date-string [^js/Date date]
  (first (split (.toISOString date) \T)))

(defn weeks [days]
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

(defn format-date-time [d]
  (apply cl-format nil "~d-~2,'0d-~2,'0d ~2,'0d:~2,'0d"
         ((juxt #(.getFullYear %)
                #(inc (.getMonth %))
                #(.getDate %)
                #(.getHours %)
                #(.getMinutes %)) d)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn debounce [f interval]
  (let [dbnc (goog.async.Debouncer. f interval)]
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- minutes-since-midnight
  [time-string]
  (when time-string
    (let [[hours mins]
          (map reader/read-string
               (drop 1 (re-find #"0?([0-9]{1,2}):0?([0-9]{1,2})" time-string)))]
      (+ (* 60 hours) mins))))

(defn clocks-each-minute [clocks]
  (let [mins (mapv transient (repeat (* 24 60) []))]
    (doseq [clock clocks
            :let [start (minutes-since-midnight (:start clock))
                  end (minutes-since-midnight (:end clock))
                  end (cond
                        (zero? end) (if (zero? start) 0 1440)
                        (< end start) nil
                        :else end)]
            :when end
            i (range start end)]
      (conj! (nth mins i) clock))
    (mapv persistent! mins)))

(defn clock-minute-intervals [clocks]
  (loop [i 0
         current-clocks []
         each-minute (clocks-each-minute clocks)
         result []]
    (if (empty? each-minute)
      result
      (let [n (count (take-while #(= current-clocks %) each-minute))
            next-i (+ i n)
            next (drop n each-minute)]
        (recur next-i
               (first next)
               next
               (if (zero? n) result (conj result [i (+ i n) current-clocks])))))))

(defn clock-minute-intervals-by-day [clocks-by-day]
  (into (sorted-map-by <) (map
                           (fn [[key clocks]] [key (clock-minute-intervals clocks)])
                           clocks-by-day)))

