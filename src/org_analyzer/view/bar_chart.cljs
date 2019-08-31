(ns org-analyzer.view.bar-chart
  (:require [org-analyzer.view.util :as util]
            [clojure.string :as s]
            sc.api
            [cljs.pprint :refer [pprint]]
            [sc.api]
            [org-analyzer.view.dom :as dom]
            [org-analyzer.view.geo :as geo]))

(defn bar-chart
  [app-state selected-days tooltip highlighted-entries]
  (let [clocks-by-day (:clocks-by-day-filtered @app-state)
        calendar (:calendar @app-state)
        clocks-by-day (sort-by first (map #(find clocks-by-day (:date %)) selected-days))
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
        hour-height (/ max-bar-h hours-shown)

        bounds-by-day (for [[i [day clocks]] (map-indexed vector clocks-by-day)
                            :let [mins (util/sum-clocks-mins clocks)
                                  mins-shown (or (first (drop-while #(> mins %) [(* 60 2) (* 60 5) (* 60 10)]))
                                                 (* 60 24))
                                  h (* (/ max-bar-h (* 60 hours-shown)) mins)
                                  x (+ padding-left (* i day-width))
                                  y (+ padding-top (- max-bar-h h))
                                  activities-by-effort (->> clocks
                                                            (group-by :location)
                                                            (map
                                                             (fn [[location clocks]] {:location location
                                                                                      :clocks clocks
                                                                                      :minutes (util/sum-clocks-mins clocks)}))
                                                            (sort-by :minutes)
                                                            reverse)
                                  activities-by-effort (loop [[{:keys [location clocks minutes]} & rest] activities-by-effort
                                                              i 0
                                                              bottom (+ padding-top max-bar-h)
                                                              result []]
                                                         (let [h (* (/ max-bar-h (* 60 hours-shown)) minutes)
                                                               top (- bottom h)
                                                               result (conj result {:i i
                                                                                    :location location
                                                                                    :clocks clocks
                                                                                    :minutes minutes
                                                                                    :bounds [x top day-width h]
                                                                                    :day day})]
                                                           (if rest
                                                             (recur rest (inc i) top result)
                                                             result)))]]
                        [day {:i i
                              :minutes mins
                              :minutes-shown mins-shown
                              :bounds [x y day-width h]
                              :activities-by-effort activities-by-effort
                              :clocks clocks}])

        clock-colors (cycle ["#E5FFEF" "#CCFFE0" "#B2FFD0" "#99FFC1" "#7FFFB1" "#66FFA2" "#33FF83"])

        selected-locations @highlighted-entries]

    [:div.bar-chart-container {:style {:display "flex" :flex-direction "column" :align-items "center" :overflow "auto" :width "100%"}}
     (when (> n-clocks 0)
       [:canvas.bar-chart {
                           :style {:width (str w "px")}
                           :width w
                           :height h

                           :on-mouse-move (fn [evt]
                                            (let [p (dom/mouse-position evt :relative? true)
                                                  activity (first
                                                            (for [[day {:keys [clocks activities-by-effort]}] bounds-by-day
                                                                  activity activities-by-effort
                                                                  :when (geo/contains-point? (:bounds activity) p)]
                                                              activity))]

                                              (when tooltip
                                                (reset! tooltip (when activity
                                                                  (let [{:keys [clocks minutes day]} activity
                                                                        [{:keys [path name]}] clocks]
                                                                    [:div
                                                                     [:div (str day " " (:dow-name (get calendar day)) ", " (str (util/print-duration-mins minutes) "h"))]
                                                                     [:div "[" (interpose " > " (map (comp util/parse-all-org-links s/trim) path)) "]"
                                                                      [:br]
                                                                      [:span.name (util/parse-all-org-links name)]]]))))
                                              (reset! highlighted-entries (if activity #{(:location activity)} #{}))))
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
                                      (doseq [[day {:keys [i minutes minutes-shown clocks activities-by-effort]
                                                    [x y day-width day-height] :bounds}] bounds-by-day]

                                        ;; show each groups of clocks belonging
                                        ;; to the same activity as a stacked
                                        ;; chart
                                        (do
                                          (set! (.-strokeStyle ctx) "#AAA")
                                          (set! (.-lineWidth ctx) .5)
                                          (. ctx beginPath)
                                          (let []
                                            (doseq [{:keys [i location] [x y w h] :bounds} activities-by-effort
                                                    :let [color (if (selected-locations location) "salmon" (nth clock-colors i))]]

                                              (set! (.-fillStyle ctx) color)
                                              (. ctx (fillRect x y w h))
                                              (doto ctx (.moveTo x y) (.lineTo (+ x w) y))
                                              ))
                                          (doto ctx (.closePath) (.stroke)))

                                        (set! (.-fillStyle ctx) "white")
                                        (. ctx (strokeRect x y day-width day-height))
                                        ;; (. ctx (fillRect x y day-width day-height))


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

                                            (when (and (> minutes 0) (> day-width 20))
                                              (. ctx (fillText (str (util/print-duration-mins minutes) "h")
                                                               (+ x (/ day-width 2))
                                                               (- y 10))))
                                            minutes-shown)))
                                      (. ctx restore)
                                      )))}])]))
