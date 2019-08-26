(ns org-analyzer.parsing-test
  (:require [clojure.pprint :refer [cl-format]]
            [clojure.test :as t :refer [deftest is]]
            [clojure.zip :as zip]
            [org-analyzer.printing :as print]
            [org-analyzer.processing :as process]
            [org-analyzer.time :as org-time]
            [clojure.string :as s])
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

  #_(let [expected-date (LocalDateTime/of 2019 6 19 0 0 0)]
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
*bold text* here
* section 2 :tag2_1:tag2_2:
:LOGBOOK:
CREATED: [2018-12-15 Sa 12:05]
:END:
** TODO section 2.1 :section21tag:
   :LOGBOOK:
   CLOCK: [2018-02-20 Tue 14:36]--[2018-02-20 Tue 15:00] =>  0:24
   CLOCK: [2018-02-19 Mon 18:20]--[2018-02-19 Mon 18:58] =>  0:38
   :END:
" )

(def expected-org-data '({:type :file,
                          :name "pseudo.org"
                          :depth 0,
                          :index 0,
                          :props ({:line 1, :type :file-prop, :prop "FILETAGS", :value ":pseudo:"}),
                          :tags ("pseudo")}
                         {:line 3,
                          :type :section,
                          :depth 1,
                          :name "section 1",
                          :tags ("section1tag"),
                          :parent 0,
                          :index 1}
                         {:line 4, :type :section, :depth 2, :name "section 1.1" :parent 1, :index 2}
                         {:line 6, :type :section, :depth 2, :name "section 1.2" :parent 1, :index 3}
                         {:line 8,
                          :type :clock,
                          :text "CLOCK: [2018-02-02 Fri 19:55]--[2018-02-02 Fri 20:20] =>  0:25",
                          :parent 3,
                          :index 4}
                         {:line 9,
                          :type :metadata,
                          :text "CREATED: [2018-02-01 Thu 03:12]",
                          :parent 3,
                          :index 5}
                         {:line 13,
                          :type :section,
                          :depth 1,
                          :name "section 2",
                          :tags ("tag2_1" "tag2_2"),
                          :parent 0,
                          :index 6}
                         {:line 15,
                          :type :metadata,
                          :text "CREATED: [2018-12-15 Sa 12:05]",
                          :parent 6,
                          :index 7}
                         {:line 17,
                          :type :section,
                          :depth 2,
                          :keyword "TODO",
                          :name "section 2.1",
                          :tags ("section21tag"),
                          :parent 6,
                          :index 8}
                         {:line 19,
                          :type :clock,
                          :text "CLOCK: [2018-02-20 Tue 14:36]--[2018-02-20 Tue 15:00] =>  0:24",
                          :parent 8,
                          :index 9}
                         {:line 20,
                          :type :clock,
                          :text "CLOCK: [2018-02-19 Mon 18:20]--[2018-02-19 Mon 18:58] =>  0:38",
                          :parent 8,
                          :index 10}))


(defn read-pseudo-org []
  (process/parse-org-file
   "pseudo.org"
   (java.io.StringBufferInputStream. pseudo-org-content)))

(deftest find-clocks
  (let [clocks (process/find-clocks (read-pseudo-org))]
    (is (= '("CLOCK: [2018-02-02 Fri 19:55]--[2018-02-02 Fri 20:20] =>  0:25"
             "CLOCK: [2018-02-20 Tue 14:36]--[2018-02-20 Tue 15:00] =>  0:24"
             "CLOCK: [2018-02-19 Mon 18:20]--[2018-02-19 Mon 18:58] =>  0:38")
           (map print/print-clock clocks)))
    (is (= '(["0:25"
              "2018-02-02 Fri 19:55"
              "2018-02-02 Fri 20:20"
              "section 1.2"
              #{"pseudo" "section1tag"}]
             ["0:24"
              "2018-02-20 Tue 14:36"
              "2018-02-20 Tue 15:00"
              "section 2.1"
              #{"pseudo" "tag2_2" "section21tag" "tag2_1"}]
             ["0:38"
              "2018-02-19 Mon 18:20"
              "2018-02-19 Mon 18:58"
              "section 2.1"
              #{"pseudo" "tag2_2" "section21tag" "tag2_1"}])
           (map (juxt (comp print/print-duration :duration)
                      (comp print/print-timestamp :start)
                      (comp print/print-timestamp :end)
                      (comp :name first :sections)
                      :tags)
                clocks)))))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest parse-org-data
  (is (= expected-org-data (read-pseudo-org))))

(deftest find-parents
  (let [org-data (read-pseudo-org)]
    (is (= '("section 2.1" "section 2" "pseudo.org")
           (map #(or (:text %) (:name %))
                (process/parent-entries (last org-data) org-data))))))

(deftest all-tags
  (let [org-data (read-pseudo-org)]
    (is (= #{"pseudo" "tag2_2" "section21tag" "tag2_1"}
           (process/all-tags-for (last org-data) org-data)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-


(comment

  (require '[organum.core :as org])

  (org/parse-file
   (java.io.StringBufferInputStream. pseudo-org-content))

  (-> (read-pseudo-org) z/next z/next z/next z/next z/node)

  (cl-format true "~{~a~^~%~}~%" (take 10 (map zip/node (iterate zip/next (read-pseudo-org)))))

  (->> (read-pseudo-org)
       (iterate zip/next)
       (drop 10)
       first
       ((juxt (comp zip/up zip/up) zip/up identity))
       (map zip/node))

  (def n (->> (read-pseudo-org)
              (iterate zip/next)
              (drop 10)
              first))

  (-> n zip/up zip/node :name)
  (-> n zip/up zip/up zip/node :name)


  (require '[clojure.zip :as z])

  (def clocks (process/find-clocks (read-pseudo-org)))

  (let [clocks (process/find-clocks (read-pseudo-org))]
    (map :clock-string clocks)
    (->> clocks first :sections (map :name)))

  (println (first clocks))

  (clojure.pprint/cl-format true "Clocks (~a)~%~{~a~^~%~}~%"
                            (print/print-duration (org-time/sum-clock-duration clocks))
                            clocks))
