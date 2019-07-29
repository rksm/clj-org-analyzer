(ns org-analyzer.printing
  (:require [clojure.pprint :refer [cl-format]]
            [clojure.string :as s]
            [java-time :as time :refer [local-date-time]]
            org-analyzer.processing
            [org-analyzer.time :refer [compute-clock-duration]])
  (:import java.util.Locale
           org_analyzer.processing.Clock))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; various parts of org content

(defn print-timestamp [date-time]
  (. date-time
     (format
      (time/formatter "yyyy-MM-dd ccc HH:mm" Locale/ENGLISH))))

(comment
  (print-timestamp (local-date-time)))

(defn print-duration [duration]
  (let [mins (-> duration (time/as :seconds) (quot 60))
        h (quot mins 60)
        m (- mins (* 60 h))]
    (cl-format nil "~d:~2,'0d" h m)))

(comment
  (print-duration (duration (beginning-of-week (local-date-time)) (local-date-time))))

(defn print-clock [{:keys [start end duration] :as clock}]
  (cl-format nil "CLOCK: [~a]~:[~;--~:*[~a]~]~:[~; =>  ~:*~a~]"
             (print-timestamp start)
             (some-> end print-timestamp)
             (some-> duration print-duration)))

(defn print-clock-location [{:keys [parents i] :as clock}]
  ;; (->> parents (map :name) (interpose "|") (apply str))
  (->> parents first :name))

(def secs-in-one-min 60)
(def secs-in-one-hour (* secs-in-one-min 60))
(def secs-in-one-day (* secs-in-one-hour 24))

(defn pretty-print-duration [duration]
  (let [secs-left (time/as duration :seconds)
        days (quot secs-left secs-in-one-day)
        days-printed (if (zero? days) "" (cl-format nil "~d day~:*~P" days))
        secs-left (- secs-left (* secs-in-one-day days))
        hours (quot secs-left secs-in-one-hour)
        hours-printed (if (zero? hours) "" (cl-format nil "~d hour~:*~P" hours))
        secs-left (- secs-left (* secs-in-one-hour hours))
        mins (quot secs-left secs-in-one-min)
        mins-printed (if (and (zero? mins) (not (zero? (+ hours days)))) "" (cl-format nil "~d minute~:*~P" mins))]
    (s/join " " (keep not-empty [days-printed hours-printed mins-printed]))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; Clock

(defmethod print-method Clock [{:keys [sections] :as clock} writer]
  (cl-format writer "~a for \"~a\""
             (print-clock clock)
             (->> sections ((juxt first last)) (map :name) (apply format "(%s > %s)"))))
