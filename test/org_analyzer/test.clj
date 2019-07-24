(ns org-analyzer.test
  (:require [clojure.test :as t :refer [deftest is]]
            [org-analyzer.printing :as print]
            [org-analyzer.time :as org-time]
            [org-analyzer.processing :as process])
  (:import [java.time Duration LocalDateTime]))

(deftest clock-re
  (doseq [[clock-string {:keys [start end duration]}] [["CLOCK: [2019-06-19 Wed 13:44]--[2019-06-19 Wed 14:11] =>  0:27"
                                                        {:start "[2019-06-19 Wed 13:44]" :end "[2019-06-19 Wed 14:11]" :duration "0:27"}]
                                                       ["CLOCK: [2019-06-19 Wed 13:44]"
                                                        {:start "[2019-06-19 Wed 13:44]" :end nil :duration nil}]
                                                       ["CLOCK: [2019-06-19 Wed 13:44]--[2019-06-19 Wed 14:11]"
                                                        {:start "[2019-06-19 Wed 13:44]" :end "[2019-06-19 Wed 14:11]" :duration nil}]]]
    (let [[_ expected-start expected-end expected-duration] (re-find process/clock-re clock-string)]
      (is (= [start end duration] [expected-start expected-end expected-duration])))))

(deftest parse-timestamp
  (let [expected-date-time (LocalDateTime/of 2019 6 19 13 44 00)]
    (is (= expected-date-time (process/parse-timestamp "2019-06-19 Wed 13:44")))
    (is (= expected-date-time (process/parse-timestamp "2019-06-19 Mi 13:44")))
    (is (= expected-date-time (process/parse-timestamp "2019-06-19 Mittwoch 13:44")))
    (is (= expected-date-time (process/parse-timestamp "2019-06-19 13:44"))))

  (let [expected-date (LocalDateTime/of 2019 6 19 0 0 0)]
   (is (= expected-date (process/parse-timestamp "2019-06-19 Wednesday")))
   (is (= expected-date (process/parse-timestamp "2019-06-19")))
   (is (= expected-date (process/parse-timestamp "2019-06-19 Mi")))))

(deftest parse-duration
  (is (= (Duration/ofMinutes 65) (process/parse-duration "1:05"))))

(deftest pretty-print-duration
  (is (= "1 day"                  (print/pretty-print-duration (Duration/ofDays 1))))
  (is (= "1 hour"                 (print/pretty-print-duration (Duration/ofHours 1))))
  (is (= "1 day 4 hours"          (print/pretty-print-duration (Duration/ofHours 28))))
  (is (= "10 minutes"             (print/pretty-print-duration (Duration/ofMinutes 10))))
  (is (= "1 day 3 minutes"        (print/pretty-print-duration (Duration/ofMinutes (+ (* 24 60) 3)))))
  (is (= "1 day 1 hour 5 minutes" (print/pretty-print-duration (Duration/ofMinutes (+ (* 24 60) 65))))))

(def pseudo-org-content "#+FILETAGS: :pseudo:

* section 1 :section1tag:
** section 1.1
Some text
** section 1.2
:LOGBOOK:
CLOCK: [2018-02-02 Fri 19:55]--[2018-02-02 Fri 20:20] =>  0:25
CREATED: [2018-02-01 Thu 03:12]
:END:
Some more
text here
* section 2
:LOGBOOK:
CREATED: [2018-12-15 Sa 12:05]
:END:
** TODO section 2.1 :section21tag:
:LOGBOOK:
CLOCK: [2018-02-20 Tue 14:36]--[2018-02-20 Tue 15:00] =>  0:24
CLOCK: [2018-02-19 Mon 18:20]--[2018-02-19 Mon 18:58] =>  0:38
:END:
" )

(defn read-pseudo-org []
  (process/parse-and-zip
   "pseudo.org"
   (java.io.StringBufferInputStream. pseudo-org-content)))


(deftest find-clocks
  (let [clocks (process/find-clocks (read-pseudo-org))]
    (is (= ["CLOCK: [2018-02-02 Fri 19:55]--[2018-02-02 Fri 20:20] =>  0:25"
            "CLOCK: [2018-02-20 Tue 14:36]--[2018-02-20 Tue 15:00] =>  0:24"
            "CLOCK: [2018-02-19 Mon 18:20]--[2018-02-19 Mon 18:58] =>  0:38"]
           (map print/print-clock clocks)))
    (is (= (->> clocks first :sections (map :name))
           ["pseudo.org" "section 1" "section 1.1" "section 1.2"]))))


(comment

  (def clocks (process/find-clocks (read-pseudo-org)))

  (let [clocks (process/find-clocks (read-pseudo-org))]
    (map :clock-string clocks)
    (->> clocks first :sections (map :name)))

  (println (first clocks))

  (clojure.pprint/cl-format true "Clocks (~a)~%~{~a~^~%~}~%"
                            (print/print-duration (org-time/sum-clock-duration clocks))
                            clocks))
