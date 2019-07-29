(ns org-analyzer.view.dom)

(defn el-bounds [el]
  (let [b-rect (.getBoundingClientRect el)]
    {:left   (.-left b-rect)
     :top    (.-top b-rect)
     :right  (.-right b-rect)
     :bottom (.-bottom b-rect)
     :width  (.-width b-rect)
     :height (.-height b-rect)}))

(defn mouse-position [event]
  (let [client-x (.-clientX event) client-y (.-clientY event)]
    (if client-x
      (let [doc (or (some-> event .-target .-ownerDocument) js/document)
            body (.-body doc)
            scroll-left (or (.-scrollLeft doc) (.-scrollLeft body) 0)
            scroll-top (or (.-scrollTop doc) (.-scrollTop body) 0)
            client-left (or (.-clientLeft doc) (.-clientLeft body) 0)
            client-top (or (.-clientTop doc) (.-clientTop body) 0)]
        [(+ client-x (- scroll-left client-left))
         (+ client-y (- scroll-top client-top))])
      [0 0])))
