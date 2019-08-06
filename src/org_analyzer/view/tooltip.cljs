(ns org-analyzer.view.tooltip
  (:require [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [org-analyzer.view.dom :as dom]))

(defn with-tooltip-following-mouse
  ([tooltip-content comp]
   (with-tooltip-following-mouse tooltip-content {} comp))
  ([tooltip-content {:keys [align offset] :as config} comp]
   (rg/with-let [bounds (atom [0 0 0 0])
                 el (atom nil)
                 follow (ratom false)]
     (let [react-el (rg/as-element comp)

           tooltip-content @tooltip-content
           stalker [:div.stalker
                    {:ref #(do (reset! el %)
                               (reset! bounds (if % (dom/screen-relative-bounds %) [0 0 0 0])))
                     :class (if (or (not (string? tooltip-content))
                                    (not-empty tooltip-content))
                              nil "hidden")}
                    tooltip-content]

           type (.-type react-el)
           ref (.-ref react-el)
           props (.-props react-el)
           children (.-children (.-props react-el))

           props (js->clj props)
           onMouseOver (get props "onMouseOver")
           onMouseOut (get props "onMouseOut")
           onMouseMove (get props "onMouseMove")

           offset (or offset [0 0])
           props (clj->js (merge props
                                 {"ref" ref
                                  "onMouseOver" (fn [evt]
                                                  (when (fn? onMouseOver) (onMouseOver evt))
                                                  (reset! follow true))
                                  "onMouseOut" (fn [evt]
                                                 (when (fn? onMouseOut) (onMouseOut evt))
                                                 (reset! follow false))
                                  "onMouseMove" (fn [evt]
                                                  (when (fn? onMouseMove)
                                                    (onMouseMove evt))
                                                  (when (and @el @follow)
                                                    (dom/align-element! :top @el (dom/mouse-position evt) 5)))}))]

       (rg/create-element
        type
        props
        children
        (when @follow (rg/as-element stalker)))))))
