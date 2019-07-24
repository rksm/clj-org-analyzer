(ns org-analyzer.test
  (:require [clojure.test :as t :refer [deftest is]]
            [org-analyzer.scratch :as sut])
  (:import [java.time LocalDateTime LocalDate Duration]))

(deftest clock-re
  (doseq [[clock-string {:keys [start end duration]}] [["CLOCK: [2019-06-19 Wed 13:44]--[2019-06-19 Wed 14:11] =>  0:27"
                                                        {:start "[2019-06-19 Wed 13:44]" :end "[2019-06-19 Wed 14:11]" :duration "0:27"}]
                                                       ["CLOCK: [2019-06-19 Wed 13:44]"
                                                        {:start "[2019-06-19 Wed 13:44]" :end nil :duration nil}]
                                                       ["CLOCK: [2019-06-19 Wed 13:44]--[2019-06-19 Wed 14:11]"
                                                        {:start "[2019-06-19 Wed 13:44]" :end "[2019-06-19 Wed 14:11]" :duration nil}]]]
    (let [[_ expected-start expected-end expected-duration] (re-find sut/clock-re clock-string)]
      (is (= [start end duration] [expected-start expected-end expected-duration])))))

(deftest parse-timestamp
  (let [expected-date-time (LocalDateTime/of 2019 6 19 13 44 00)]
    (is (= expected-date-time (sut/parse-timestamp "2019-06-19 Wed 13:44")))
    (is (= expected-date-time (sut/parse-timestamp "2019-06-19 Mi 13:44")))
    (is (= expected-date-time (sut/parse-timestamp "2019-06-19 Mittwoch 13:44")))
    (is (= expected-date-time (sut/parse-timestamp "2019-06-19 13:44"))))

  (let [expected-date (LocalDate/of 2019 6 19)]
   (is (= expected-date (sut/parse-timestamp "2019-06-19 Wednesday")))
   (is (= expected-date (sut/parse-timestamp "2019-06-19")))
   (is (= expected-date (sut/parse-timestamp "2019-06-19 Mi")))))

(deftest parse-duration
  (is (= (Duration/ofMinutes 65) (sut/parse-duration "1:05"))))
