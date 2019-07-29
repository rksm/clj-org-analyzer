(ns org-analyzer.dom-helpers)

(def empty-drag-state {:mousedown? false
                       :start-pos nil
                       :current-pos nil})

(defn grab-position [target-element-id evt]
  (let [target (loop [target (.-target evt)]
                 (if (= target-element-id (.-id target))
                   target
                   (.-offsetParent target)))]
    (let [x (.-pageX evt)
          y (.-pageY evt)
          x (- x (.-offsetLeft target))
          y (- y (.-offsetTop target))]
      [x y])))


(defn selection-rectangle [{:keys [current-pos start-pos]}]
  (let [[x y] current-pos
        [x2 y2] start-pos
        left (min x x2)
        top (min y y2)
        right (max x x2)
        bottom (max y y2)
        w (- right left)
        h (- bottom top)]
    {:left left :top top :width w :height h}))



(defn on-mouse-down-track-drag [target-element-id drag-state evt]
  (let [pos (grab-position target-element-id evt)]
    (swap! drag-state assoc
           :mousedown? true
           :start-pos pos
           :current-pos pos)))

(defn on-mouse-up-track-drag [target-element-id drag-state evt]
  (swap! drag-state assoc :mousedown? false))

(defn on-mouse-move-track-drag [target-element-id drag-state evt]
  (let [{:keys [mousedown? on-selection-change]} @drag-state]
    (when mousedown?
      (swap! drag-state assoc
             :current-pos (grab-position target-element-id evt))
      (when (fn? on-selection-change)
        (on-selection-change @drag-state)))))

(defn drag-mouse-handlers [target-element-id drag-state]
  {:id "calendar"
   :on-mouse-down (partial on-mouse-down-track-drag target-element-id drag-state)
   :on-mouse-up (partial on-mouse-up-track-drag target-element-id drag-state)
   :on-mouse-move (partial on-mouse-move-track-drag target-element-id drag-state)})

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn el-bounds [el]
  (let [b-rect (.getBoundingClientRect el)]
    {:left   (.-left b-rect)
     :top    (.-top b-rect)
     :right  (.-right b-rect)
     :bottom (.-bottom b-rect)
     :width  (.-width b-rect)
     :height (.-height b-rect)}))

