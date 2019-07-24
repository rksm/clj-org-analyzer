(ns org-analyzer.scratch
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format pprint]]
            [clojure.string :as s]
            [clojure.zip :as zip]
            [java-time :as time :refer [adjust
                                        as
                                        day-of-week
                                        days
                                        duration
                                        format
                                        formatter
                                        instant
                                        interval
                                        iterate
                                        local-date
                                        local-date-time
                                        minus
                                        plus
                                        offset-date-time
                                        truncate-to
                                        weeks]
             :rename {as time-as
                      format time-format
                      formatter time-formatter
                      iterate time-iterate
                      minus time-
                      plust time+
                      truncate-to time-precision}]
            [organum.core :as org])
  (:import [java.time LocalDate LocalDateTime Duration]
           java.time.format.DateTimeFormatter
           java.util.Locale))

;; http://dm3.github.io/clojure.java-time/java-time.html

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn org-zipper [parsed]
  (zip/zipper (fn [node] (and (map? node) :content some?))
              (fn [node] (:content node))
              (fn [node children] (assoc node :content children))
              parsed))

(defn parse-and-zip [org-file]
  (org-zipper {:type :outer :content (org/parse-file org-file)}))

(defrecord Clock [start end duration clock-string sections])

(defmethod print-method Clock [{:keys [clock-string sections]} writer]
  (cl-format writer "~a for \"~a\"" clock-string (or (some-> sections last :name ) "???")))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; CLOCK parsing

(def clock-re #"(?i)^\s*CLOCK:\s*((?:\[|<)[0-9a-z :-]+(?:\]|>))(?:--((?:\[|<)[0-9a-z :-]+(?:\]|>)))?(?:\s*=>\s*([0-9:]+))?\s*$")


(def date-time-patterns [{:parse #(LocalDateTime/parse %1 (DateTimeFormatter/ofPattern %2 %3))
                          :pattern "y-M-d[ ][cccc][ccc][ ]H:m"}
                         {:parse #(. (LocalDate/parse %1 (DateTimeFormatter/ofPattern %2 %3)) (atTime 0 0))
                          :pattern "y-M-d[ ][cccc][ccc]"}])

(def locales [Locale/ENGLISH Locale/GERMAN])

(defn parse-timestamp
  "`string` like \"[2019-06-19 Wed 14:11]\". Returns LocalDateTime."
  [string]
  (let [sanitized (s/replace string #"^(\[|<)|(\]|>)$" "")]
    (first
     (filter some?
             (for [locale locales
                   {:keys [parse pattern]} date-time-patterns]
               (try (parse sanitized pattern locale) (catch Exception e nil)))))))

(defn parse-duration
  "`duration-string` like \"3:22\"."
  [duration-string]
  (as-> duration-string it
    (s/replace it #":" "H")
    (str "PT" it "M")
    (duration it)))

(defn parse-clock [clock-string]
  (let [[_ start end duration] (re-find clock-re clock-string)]
    {:start (parse-timestamp start)
     :end (when end (parse-timestamp end))
     :duration (when duration (parse-duration duration))}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn find-clocks [zipped-org]
  (loop [cursor zipped-org
         parents []
         clocks []]
    (let [val (zip/node cursor)
          section? (= :section (:type val))
          metadata? (= :metadata (:line-type val))
          with-parents (if section? (conj parents val) parents)
          text-content (:text val)
          clock (when (and metadata? (s/starts-with? text-content "CLOCK:"))
                  (let [{:keys [start end duration]} (parse-clock text-content)]
                    (->Clock start end duration text-content with-parents)))
          clocks (if clock (conj clocks clock) clocks)
          n (zip/next cursor)]
      (if (zip/end? n)
        clocks
        (recur n with-parents clocks)))))

(defn clocks-by-section [clocks]
  (group-by (comp last :sections) clocks))

(defn print-timestamp [date-time]
  (. date-time
     (format
      (time-formatter "yyyy-MM-dd ccc HH:mm" Locale/ENGLISH))))

(comment
  (print-timestamp (local-date-time)))

(defn print-duration [duration]
  (let [mins (-> duration (time-as :seconds) (quot 60))
        h (quot mins 60)
        m (- mins (* 60 h))]
    (cl-format nil "~d:~2,'0d" h m)))

(comment
  (print-duration (duration (beginning-of-week (local-date-time)) (local-date-time))))

(defn now []
  (local-date-time))

(defn days-ago [days]
  (-> (local-date-time)
      (time- (days days))
      (time-precision :days)))

(defn beginning-of-week [date]
  (-> date
      (adjust (day-of-week :monday))
      (time-precision :days)))

(defn week-seq-from [date-time]
  (let [bow (beginning-of-week date-time)
        first-days-of-weeks (conj (time-iterate time- bow (weeks 1)) date-time)]
    first-days-of-weeks))


(def secs-in-one-min 60)
(def secs-in-one-hour (* secs-in-one-min 60))
(def secs-in-one-day (* secs-in-one-hour 24))

(defn pretty-print-duration [duration]
  (let [secs-left (time-as duration :seconds)
        days (quot secs-left secs-in-one-day)
        days-printed (if (zero? days) "" (cl-format nil "~d day~:*~P" days))
        secs-left (- secs-left (* secs-in-one-day days))
        hours (quot secs-left secs-in-one-hour)
        hours-printed (if (zero? hours) "" (cl-format nil "~d hour~:*~P" hours))
        secs-left (- secs-left (* secs-in-one-hour hours))
        mins (quot secs-left secs-in-one-min)
        mins-printed (if (and (zero? mins) (not (zero? (+ hours days)))) "" (cl-format nil "~d minute~:*~P" mins))]
    (s/join " " (keep not-empty [days-printed hours-printed mins-printed]))))



(comment
  (print-duration (sum-clock-duration (take 5 clocks)))

  (doseq [week-ago (range 5000)]
    (let [[end start] (->> (local-date-time)
                           week-seq-from
                           (drop week-ago)
                           (take 2))
          clocks (clocks-between start end clocks)]
      ;; (cl-format true "Clocks from ~a to ~a (~a)~%~{~a~^~%~}~%"
      ;;            (time-format "yyyy-MM-dd eee hh:mm" start)
      ;;            (time-format "yyyy-MM-dd eee hh:mm" end)
      ;;            (print-duration (sum-clock-duration clocks))
      ;;            clocks)
      (when (not-empty clocks)
          (cl-format true "Clocks from ~a to ~a (~a)~%"
                     (time-format "yyyy-MM-dd eee hh:mm" start)
                     (time-format "yyyy-MM-dd eee hh:mm" end)
                     (print-duration (sum-clock-duration clocks))))))


  (require 'java-time.repl)
  (java-time.repl/show-units)
  (java-time.repl/show-adjusters)

  (time-as (parse-duration "5:33") :hours)

  (time-iterate (interval (time- (instant) (days 3)) (instant)))
  (instant (days-ago 3))
  (java.time.temporal.TemporalAdjusters/dayOfWeekInMonth )
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
  )



(defn print-clock [{:keys [start end duration] :as clock}]
  (cl-format nil "CLOCK: [~a]~:[~;--~:*[~a]~]~:[~; =>  ~:*~a~]"
             (print-timestamp start)
             (some-> end print-timestamp)
             (some-> duration print-duration)))

(defn print-clock-location [{:keys [parents i] :as clock}]
  ;; (->> parents (map :name) (interpose "|") (apply str))
  (->> parents first :name))

(defn compute-clock-duration [clock]
  (let [{:keys [start end]} clock]
    (java.time.Duration/between start end) ))

(defn check-clocks [clocks]
  (doseq [{:keys [start end duration clock-string] :as clock} clocks
          :let [loc (print-clock-location clock)]]
    (when-not (= clock-string (print-clock clock))
      (cl-format true "CLOCK string not canonical in \"~a\":~%  ~a~%  vs~%  ~a~%"
                 loc clock-string (print-clock clock)))
    (when-not duration (println loc "CLOCK has no duration"))
    (when (and end duration (not= duration (compute-clock-duration clock)))
      (cl-format true "CLOCK computed duration differs from stated duration in \"~a\"~%  ~a~%  vs~%  ~a~%"
                 loc duration (compute-clock-duration clock)))
    (when-not end (println loc "CLOCK has no end"))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn clocks-since [start-day clocks]
  (->> clocks
       (filter #(.isBefore start-day (:start %)))
       (sort-by :start)))



(defn clocks-between [from-date to-date clocks]
  (->> clocks
       (filter #(let [{:keys [start]} %]
                  (and (time/after? start from-date)
                       (not= from-date start)
                       (time/before? start to-date))))
       (sort-by :start)))

(defn sum-clock-duration [clocks]
  (->> (filter :duration clocks)
       (reduce (fn [^Duration sum ^Clock clock] (.plus sum (:duration clock)))
               (java.time.Duration/ZERO))))

;; java.time.temporal.TemporalAdjusters/next
;; (.dayOfWeek (java.time.temporal.WeekFields/of Locale/ENGLISH))

;; (.datesUntil (.getDayOfWeek (days-ago 5)) (.toLocalDate (days-ago 2)))
;; (.getDayOfWeek (days-ago 5))
;; (.with (days-ago 5) (java.time.temporal.TemporalAdjusters/firstDayOfMonth))
;; (java.util.Calendar/getAvailableCalendarTypes)
;; (java.util.Calendar/DAY_OF_WEEK)
;; (.before (java.util.Calendar/getInstance (Locale/GERMAN)) (days-ago 20))

(comment

  (= (.atStartOfDay (local-date))
     (.atTime (local-date) 0 0))

  (def clocks (find-clocks (parse-and-zip "/home/robert/org/lively.org")))
  (def clocks (find-clocks (parse-and-zip "/home/robert/org/codium.org")))
  (def clocks (find-clocks (parse-and-zip "/home/robert/org/clockin.org")))
  (def clocks (find-clocks (org-zipper {:type :outer :content parsed-2})))
  (def clocks (find-clocks (org-zipper {:type :outer :content parsed-3})))

  (def start-day (-> (java.time.LocalDate/now) (.minusDays 7) (.atTime 0 0)))

  (cl-format true "~{~a~^~%~}" (clocks-since (days-ago 5) clocks))
  (cl-format true "~{~a~^~%~}" (clocks-since (days-ago 90) clocks))

  (print-duration (sum-clock-duration (clocks-since start-day clocks)))

  (map #(-> % :start) (take 2 clocks))


  (println (first clocks))

  (def org-files (let [dir (io/file "/home/robert/org/")]
                   (->> dir file-seq (filter #(and (= dir (.getParentFile %)) (s/ends-with? (.getName %) ".org"))))))

  (def clocks (mapcat (comp find-clocks parse-and-zip) org-files))
  (count clocks)

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




  (sc.api/defsc 57)
  (first clocks)

  (map (comp last :sections) (take 3 clocks))
  (-> clocks first :sections first)

  (count (clocks-by-section clocks))

  (-> clocks :start)
  (-> clocks :duration))
