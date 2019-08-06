(ns org-analyzer.view.dom
  (:require [org-analyzer.view.geo :as geo]))

(defn scrolled-window-bounds []
  [(.-scrollLeft js/document.documentElement)
   (.-scrollTop js/document.documentElement)
   (.-scrollWidth js/document.documentElement)
   (.-scrollHeight js/document.documentElement)])


(defn screen-relative-bounds [el]
  (let [b-rect (.getBoundingClientRect el)]
    [(.-left b-rect)
     (.-top b-rect)
     (.-width b-rect)
     (.-height b-rect)]))

(defn global-bounds [el]
  (geo/translate
   (screen-relative-bounds el)
   [js/window.scrollX js/window.scrollY]))

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

(defn align-element!
  ([how el to-point]
   (align-element! how el to-point 0))
  ([how el to-point offset]
   (let [[rect-point-fn o-x o-y] (case how
                                   :top [geo/bottom-center 0 (- offset)]
                                   :bottom [geo/top-center 0 offset]
                                   :left [geo/right-center (- offset) 0]
                                   :right [geo/left-center offset 0])
         bounds (screen-relative-bounds el)
         [x y] (geo/align bounds
                          (rect-point-fn bounds)
                          to-point)
         x (+ o-x x)
         y (+ o-y y)]
     (set! (.. el -style -left) (str x "px"))
     (set! (.. el -style -top) (str y "px")))))

