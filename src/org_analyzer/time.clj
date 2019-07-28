(ns org-analyzer.time
  (:require [java-time
             :as
             time
             :refer
             [adjust
              as
              before?
              day-of-week
              days
              duration
              local-date
              local-date-time
              minus
              plus
              truncate-to
              weeks]])
  (:import java.time.Duration
           org_analyzer.processing.Clock))

;; http://dm3.github.io/clojure.java-time/java-time.html

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn now []
  (local-date-time))

(defn days-ago [days]
  (-> (local-date-time)
      (minus (days days))
      (truncate-to :days)))

(defn beginning-of-week [date-time]
  (-> date-time
      (adjust (day-of-week :monday))
      (truncate-to :days)))

(defn week-seq-from [date-time]
  "Creates a lazy seq with start-of-week-dates, backwards in time.
Example:
(map (partial time/format \"mm-dd eee\") (take 3 (week-seq-from (now))))
;; => (\"07-24 wed\" \"07-22 mon\" \"07-15 mon\")"
  (let [bow (beginning-of-week date-time)
        first-days-of-weeks (conj (time/iterate minus bow (weeks 1)) date-time)]
    first-days-of-weeks))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn max-week-of-year [the-year]
  "Returns the max week number of `the-year`. Note: There might be a partial
  week after that!"
  (->> the-year
       (time/year)
       (time/local-date)
       (.rangeRefinedBy java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR)
       .getMaximum))

(defn week-dates-between [start end & {:keys [with-time] :or {with-time false}}]
  "Including start (as first) and end (as last) dates, returns a lazy seq for
  each week inbetween. Example:
  (map (partial time/format \"MM-dd eee\") (week-dates-between (local-date 2018 12 15) (local-date 2019 1 15)))
   ;; => (\"12-15 Sat\"
   ;;     \"12-17 Mon\"
   ;;     \"12-24 Mon\"
   ;;     \"12-31 Mon\"
   ;;     \"01-07 Mon\"
   ;;     \"01-14 Mon\"
   ;;     \"01-15 Tue\") "
  (let [iter-start (-> start
                       (adjust (day-of-week :monday))
                       (plus (weeks 1)))
        iter-end (-> end
                     (adjust (day-of-week :monday))
                     (plus (days 1)))
        iter-start (if with-time (local-date-time iter-start 0 0) iter-start)
        iter-end (if with-time (local-date-time iter-end 0 0) iter-end)]
    (->> (lazy-cat
          (cons start (take-while #(before? % iter-end) (time/iterate plus iter-start (weeks 1))))
          [end]))))

(defn days-between [start end]
  (lazy-cat
   (take-while #(time/before? % end)
               (time/iterate (comp #(local-date-time % 0 0) plus)
                             start (days 1)))
   [end]))


(defn weeks-of-year [year]
  "Returns a non-lazy collection of week start dates of the given year."
  (let [year (time/year year)
        max-week (max-week-of-year year)
        first-day (-> year time/local-date (adjust :first-day-of-year))
        last-day (-> year time/local-date (adjust :last-day-of-year) (plus (days 1)))]
    (loop [week-start first-day
           week-end (-> first-day
                        (adjust (time/day-of-week :sunday))
                        (plus (days 1)))
           result []]

      (let [days-of-week (->> (time/iterate plus week-start (days 1))
                              (take (time/time-between week-start week-end :days)))
            week-no (as week-start :week-of-week-based-year)
            new-week-start week-end
            new-week-end (if (< week-no max-week)
                           (plus week-end (weeks 1))
                           last-day)]
        (if (before? new-week-start (plus last-day (days 1)))
          (recur new-week-start new-week-end (conj result {:days days-of-week :week week-no}))
          result)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn compute-clock-duration [clock]
  (let [{:keys [start end]} clock]
    (when end (duration start end))))

(defn sum-clock-duration [clocks]
  (->> (filter :duration clocks)
       (reduce (fn [^Duration sum ^Clock clock] (.plus sum (:duration clock)))
               (duration 0))))

(defn clocks-since [start-day clocks]
  (->> clocks
       (filter #(before? start-day (:start %)))
       (sort-by :start)))

(defn clocks-between [from-date to-date clocks]
  (->> clocks
       (filter #(let [{:keys [start]} %]
                  (and (time/after? start from-date)
                       (not= from-date start)
                       (time/before? start to-date))))
       (sort-by :start)))

(defn clock->each-day-clocks [{:keys [start end] :as clock}]
  "Given a single clock, splits it into multiple clocks, all with the same
  section but with :start :end times adapted so that each clock fits into a
  single day."
  (if (= (local-date start) (local-date end))
    [clock] ; start / end on same day
    (let [days (days-between start end)
          paired (map vector days (drop 1 days))]
      (map (fn [[start end]] (assoc clock :start start :end end)) paired))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
