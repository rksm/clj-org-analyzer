(ns org-analyzer.scratch
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format pprint]]
            [clojure.string :as s]
            [java-time
             :as
             time
             :refer
             [adjust
              as
              day-of-week
              days
              duration
              local-date
              local-date-time
              minus
              plus
              truncate-to
              weeks]]
            [org-analyzer.http-server :as http]
            [org-analyzer.printing :refer [print-duration]]
            [org-analyzer.processing :refer [find-clocks]]
            [org-analyzer.time :as org-time :refer :all]
            [clojure.string :refer [starts-with?]])
  (:import [java.time LocalDateTime ZoneId]))

(comment

  (def clocks (:clocks (http/get-clocks (:org-files-and-dirs @org-analyzer.main/app-state))))

  (def clock-data (map http/clock-data clocks))

  (->> clock-data (filter #(starts-with? (:start %) "2019-08-19")) (sort-by :start))

  (def clocks (http/get-clocks))
  (def clocks (http/send-clocks-between
               (LocalDateTime/ofInstant (time/instant #inst "2019-07-08T00:00:00.000-00:00") (ZoneId/of "UTC"))
               (LocalDateTime/ofInstant (time/instant #inst "2019-07-10T00:00:00.000-00:00") (ZoneId/of "UTC"))))

  (for [{:keys [sections] :as clock} clocks
        {:keys [name]} sections
        :when (s/includes? name "org-mode")]
    clock)

  (count (mapcat org-time/clock->each-day-clocks clocks))
  (count clocks)

  (def clocks-by-day (group-by #(time/format "yyyy-MM-dd" (local-date (:start %))) clocks))

  (def clock (last (sort-by :start (get clocks-by-day "2019-07-04"))))

  (time/before? (local-date (:start clock))
                (local-date (:end clock)))

  (time/format "yyyy-MM-dd" (local-date (:start (first clocks))))

  (def org-files (let [dir (io/file "/home/robert/org/")]
                   (->> dir file-seq (filter #(and (= dir (.getParentFile %)) (s/ends-with? (.getName %) ".org"))))))

  (def clocks (mapcat (comp find-clocks parse-and-zip) org-files))
  (def clocks (-> (first org-files)  parse-and-zip find-clocks))

  (-> (first org-files) parse-and-zip)
  (count clocks)

  (count (clocks-between (minus (local-date-time) (days 3)) (local-date-time) clocks))
  (def c (first (clocks-between (minus (local-date-time) (days 3)) (local-date-time) clocks)))

  (->> c :sections (map :name) (s/join " - "))
  (-> clocks first)

  (print-duration (sum-clock-duration (take 5 clocks)))

  ;; report / list past weeks
  (doseq [week-ago (range 10)]
    (let [[end start] (->> (now)
                           week-seq-from
                           (drop week-ago)
                           (take 2))
          clocks (clocks-between start end clocks)]
      (when (not-empty clocks)
        (cl-format true "Clocks from ~a to ~a (~a)~%~{~a~^~%~}~%"
                   (time/format "yyyy-MM-dd eee hh:mm" start)
                   (time/format "yyyy-MM-dd eee hh:mm" end)
                   (print-duration (sum-clock-duration clocks))
                   clocks)
        #_(cl-format true "Clocks from ~a to ~a (~a)~%"
                     (time-format "yyyy-MM-dd eee hh:mm" start)
                     (time-format "yyyy-MM-dd eee hh:mm" end)
                     (print-duration (sum-clock-duration clocks))))))

  (def some-clocks (take 5 clocks))

  (cl-format true "~{~a~^~%~}~%" some-clocks)

  (def start (-> some-clocks second :start))
  (def end (-> some-clocks second :end))

  (time/time-between start end :days)
  (time/period (time/local-date start) (time/local-date end))

  (loop [current start per-day []]
    (if (= (local-date end) (local-date current))
      (conj per-day [current end])
      (let [midnight])))

  start
  (time/adjust start plus (days 1))
  (time/adjust start)

  ;; (map (partial time/format "MM-dd eee") (take 3 (week-seq-from (now))))
  ;; pprint


  (max-week-of-year (as (now) :year))

  (def first-day (adjust (time/local-date (time/year)) :first-day-of-year))
  (def last-day (adjust (time/local-date (time/year)) :last-day-of-year))

  (def week-start first-day)
  (def week-end (adjust first-day (time/day-of-week :sunday)))

  (as (org-time/now) :week-of-week-based-year)
  (truncate-to (org-time/now) :days)
  (adjust (truncate-to (org-time/now) :days) :first-day-of-year)
  (adjust (truncate-to (org-time/now) :days) :last-day-of-year)

  (-> (org-time/now)
      ;; (truncate-to :days)
      (adjust :last-day-of-year)
      (minus (days 1))
      (as :week-of-week-based-year))
  (java.time.Year/now)
  (.getMaximum (.rangeRefinedBy java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR (now)))
  (.getMaximum (.rangeRefinedBy java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR (time/local-date (time/year 2018))))
  (.range java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR)

  (as (org-time/beginning-of-week (org-time/now)) :week-of-week-based-year)
  (as (minus (org-time/beginning-of-week (org-time/now)) (weeks 1)) :week-of-week-based-year)
  (as (org-time/now) :week-based-year)
  (time/local-date (time/weeks 30))

  java.time.Month/APRIL

  ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-


  (require 'java-time.repl)
  (java-time.repl/show-units)
  (java-time.repl/show-fields)
  (java-time.repl/show-fields)
  (java-time.repl/show-adjusters)

  (time-as (parse-duration "5:33") :hours)

  (instant (days-ago 3))
  (java.time.temporal.TemporalAdjusters/dayOfWeekInMonth)
  (adjust (local-date) :day-of-week-in-month 1 :monday)
  (adjust (local-date) time- (weeks 1))
  (adjust (local-date) time- :day-of-week)
  (time-as (local-date) :day-of-week)
  (time-as (time- (local-date) (days (time-as (local-date) :day-of-week))) :day-of-week)
  (pprint (take 5 (time-iterate adjust (local-date) :next-working-day)))

  (-> (local-date) beginning-of-week)
  (def bow (-> (offset-date-time) beginning-of-week))

  (let [weeks (week-seq-from (local-date-time))]
    (pprint
     (take 3
           (map (juxt
                 (comp (partial time-format "yyyy-MM-dd eee") first vector)
                 (comp (partial time-format "yyyy-MM-dd eee") second vector)
                 (comp print-duration duration))
                (drop 1 weeks) weeks))))

  (let [weeks (time-iterate time- bow (weeks 1))]
    (pprint
     (take 50
           (map (juxt
                 (comp (partial time-format "yyyy-MM-dd eee") first vector)
                 (comp (partial time-format "yyyy-MM-dd eee") second vector)
                 (comp print-duration duration))
                (drop 1 weeks) weeks))))

  (map #(duration % (weeks 1)) (take 20 (time-iterate time- bow (weeks 1))))

  (adjust (local-date) java.time.DayOfWeek/MONDAY)
  (adjust (local-date-time) (day-of-week :monday))

  (.toInstant (days-ago 3) (java.time.ZoneOffset/systemDefault))

  ;; (pretty-print-duration (Duration/ofMinutes (+ (* 24 60) 65)))


  (= (.atStartOfDay (local-date))
     (.atTime (local-date) 0 0))

  (def clocks (find-clocks (parse-and-zip "/home/robert/org/lively.org")))
  (def clocks (find-clocks (parse-and-zip "/home/robert/org/codium.org")))
  (def clocks (find-clocks (parse-and-zip "/home/robert/org/clockin.org")))
  (def clocks (find-clocks (org-zipper {:type :outer :content parsed-2})))
  (def clocks (find-clocks (org-zipper {:type :outer :content parsed-3})))

  (cl-format true "~{~a~^~%~}" (clocks-since (days-ago 5) clocks))
  (cl-format true "~{~a~^~%~}" (clocks-since (days-ago 90) clocks))

  (print-duration (sum-clock-duration (clocks-since start-day clocks)))

  (map #(-> % :start) (take 2 clocks))

  (println (first clocks))

  (doseq [file org-files
          :let [clocks (-> file parse-and-zip find-clocks)]]
    (println (.getPath file))
    (check-clocks clocks))

  (check-clocks clocks)
  (doseq [c (take 1 (drop 10 (reverse clocks)))] (compute-clock-duration c))

  (compute-clock-duration (second clocks))
  (print-clock (second clocks))

  (def clock-string "CLOCK: [2019-06-19 Wed 13:44]--[2019-06-19 Wed 14:11] =>  0:27")
  (parse-clock clock-string)

  (-> (io/file "/home/robert/org/") file-seq (nth 3) .getParent)
  (->> (io/file "/home/robert/org/") file-seq (filter #(s/ends-with? (.getName %) ".org")))

  (first clocks)

  (map (comp last :sections) (take 3 clocks))
  (-> clocks first :sections first)

  (count (clocks-by-section clocks))

  (-> clocks :start)
  (-> clocks :duration))

(comment

  (require '[taoensso.tufte :as tufte :refer (defnp p profiled profile)])

  ;; We'll request to send `profile` stats to `println`:
  (tufte/add-basic-println-handler! {})

  (do (profile {} (count (http/get-clocks))))

  (sc.api.logging/register-cs-logger :sc.api.logging/log-spy-cs (fn [cs] nil)))
