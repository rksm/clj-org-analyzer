(ns org-analyzer.view.bar-chart
  (:require [org-analyzer.view.util :as util]
            [clojure.string :as s]
            sc.api))

(defn bar-chart
  [selected-days app-state]
  (let [clocks-by-day (:clocks-by-day @app-state)
        clocks-by-day (mapv #(find clocks-by-day (:date %)) selected-days)
        mins-by-day (->> clocks-by-day vals (map util/sum-clocks-mins))
        hours-shown (->> mins-by-day
                         (map #(or (first (drop-while (fn [hours] (> % (* 60 hours))) [2 5 10 16])) 24))
                         (apply max))
        label-height 50
        padding-left 50
        padding-right 50
        padding-top 20
        min-day-width 3
        max-day-width 100
        n-clocks (count clocks-by-day)
        min-width (+ padding-left padding-right (* n-clocks min-day-width))
        max-width (+ padding-left padding-right (* n-clocks max-day-width))
        w (min max-width (max min-width (- js/document.documentElement.clientWidth 50)))
        ;; w (- js/document.documentElement.clientWidth 50)
        h 300
        day-width (max min-day-width (/ (- w padding-right padding-left) n-clocks))
        ;; day-width (cond
        ;;             (< day-width min-day-width) min-day-width
        ;;             (> day-width max-day-width) max-day-width
        ;;             :else day-width)
        max-bar-h (- h padding-top label-height)
        hour-height (/ max-bar-h hours-shown)]

    [:div.bar-chart-container {:style {:display "flex" :flex-direction "column" :align-items "center" :overflow "auto" :width "100%"}}
     (when (> n-clocks 0)
       [:canvas.bar-chart {
                           :style {:width (str w "px")}
                           :width w
                           :height h
                           :ref (fn [canvas]
                                  (when canvas
                                    (let [ctx (. canvas (getContext "2d"))]
                                      (doto ctx (.clearRect 0 0 w h) (.save))

                                      (set! (.-lineWidth ctx) 1)
                                      (set! (.-textBaseline ctx) "middle")
                                      (set! (.-textAlign ctx) "center")
                                      (set! (.-font ctx) "10px sans-serif")

                                      (set! (.-fillStyle ctx) "black")
                                      (set! (.-strokeStyle ctx) "#EEE")
                                      (dotimes [h hours-shown]
                                        (when (or (< hours-shown 10) (= 0 (mod h 3)))
                                         (let [x padding-left
                                               y (+ padding-top (- max-bar-h (* hour-height h)))]
                                           (doto ctx
                                             (.fillText (str h "h") (- x 20) y)
                                             (.beginPath)
                                             (.moveTo x y)
                                             (.lineTo (- w padding-right) y)
                                             (.closePath)
                                             .stroke))))

                                      (set! (.-strokeStyle ctx) "#333")
                                      (doseq [[i [day clocks]] (map-indexed vector clocks-by-day)
                                              :let [mins (util/sum-clocks-mins clocks)
                                                    mins-shown (or (first (drop-while #(> mins %) [(* 60 2) (* 60 5) (* 60 10)]))
                                                                   (* 60 24))
                                                    clock-h (* (/ max-bar-h (* 60 hours-shown)) mins)
                                                    x (+ padding-left (* i day-width))
                                                    y (+ padding-top (- max-bar-h clock-h))]]
                                        (set! (.-fillStyle ctx) "white")
                                        (. ctx (strokeRect x y day-width clock-h))
                                        (. ctx (fillRect x y day-width clock-h))

                                        (set! (.-fillStyle ctx) "black")
                                        (when (> day-width 15)

                                          (let [rotated? (< day-width 50)]
                                            (set! (.-textBaseline ctx) "top")
                                            (set! (.-textAlign ctx) "center")

                                            (. ctx (translate (+ x (/ day-width 2))
                                                              (+ padding-top (+ 5 max-bar-h))))
                                            (when rotated?
                                              (. ctx (rotate (* 45 (/ Math.PI 180)))))
                                            (. ctx (fillText (-> day (subs 5) (s/replace "-" "/"))
                                                             (if rotated? 10 0)
                                                             0))
                                            (. ctx (setTransform 1, 0, 0, 1, 0, 0))

                                            (. ctx (fillText (util/print-duration-mins mins)
                                                             (+ x (/ day-width 2))
                                                             (- y 10)))
                                            mins-shown
                                            )))
                                      (. ctx restore)
                                      )))}])]))
