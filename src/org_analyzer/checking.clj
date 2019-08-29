(ns org-analyzer.checking
  (:require [clojure.pprint :refer [cl-format]]
            [org-analyzer.time :refer [compute-clock-duration clock->each-day-clocks]]
            [org-analyzer.printing
             :refer
             [print-clock print-clock-location]]))

(defn check-clocks [clocks]
  #_(doseq [{:keys [start end duration clock-string] :as clock} clocks
          :let [loc (print-clock-location clock)]]
    #_(when-not (= clock-string (print-clock clock))
        (cl-format true "CLOCK string not canonical in \"~a\":~%  ~a~%  vs~%  ~a~%"
                   loc clock-string (print-clock clock)))
    (when-not duration (println loc "CLOCK has no duration"))
    (when (and end duration (not= duration (compute-clock-duration clock)))
      (cl-format true "CLOCK computed duration differs from stated duration in \"~a\"~%  ~a~%  vs~%  ~a~%"
                 loc duration (compute-clock-duration clock)))
    (when-not end (println loc "CLOCK has no end")))

  (let [sorted (sort-by :start clocks)]
    (doseq [[a b] (map vector sorted (drop 1 sorted))
            :when (time/after? (:end a) (:start b))]
      (cl-format true
                 "Overlapping CLOCKs:~%  ~a (~a)~%  ~a (~a)~%~%"
                 (print-clock-location a) (print-clock a)
                 (print-clock-location b) (print-clock b)))))
