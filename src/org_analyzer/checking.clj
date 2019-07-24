(ns org-analyzer.checking
  (:require [clojure.pprint :refer [cl-format]]
            [org-analyzer.time :refer [compute-clock-duration]]
            [org-analyzer.printing
             :refer
             [print-clock print-clock-location]]))

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
