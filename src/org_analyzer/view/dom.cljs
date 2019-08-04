(ns org-analyzer.view.dom)

(defn el-bounds [el]
  (let [b-rect (.getBoundingClientRect el)]
    [(.-left b-rect)
     (.-top b-rect)
     (.-width b-rect)
     (.-height b-rect)]))

(defn mouse-position [event & {:keys [relative?]}]
  (let [client-x (.-clientX event) client-y (.-clientY event)]
    (if client-x
      (let [target (.-target event)
            doc (or (and target (.-ownerDocument target)) js/document)
            doc-el (and doc (.-documentElement doc))
            body (.-body doc)
            scroll-left (or (.-scrollLeft doc-el) (.-scrollLeft body) 0)
            scroll-top (or (.-scrollTop doc-el) (.-scrollTop body) 0)
            client-left (or (.-clientLeft doc-el) (.-clientLeft body) 0)
            client-top (or (.-clientTop doc-el) (.-clientTop body) 0)
            [offset-x offset-y] (if-not relative?
                                  [0 0]
                                  (loop [target target offset-x 0 offset-y 0]
                                    (if-not target
                                      [(- offset-x) (- offset-y)]
                                      (recur (.-offsetParent target)
                                             (+ offset-x (.-offsetLeft target))
                                             (+ offset-y (.-offsetTop target))))))]
        [(+ client-x (- scroll-left client-left) offset-x)
         (+ client-y (- scroll-top client-top) offset-y)])
      [0 0])))
