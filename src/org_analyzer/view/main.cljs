(ns org-analyzer.view.main
  (:require [org-analyzer.view.app :refer [app fetch-data]]
            [reagent.core :refer [render]]))

(enable-console-print!)

(defn -main []
  (render [app]
          (js/document.querySelector "#app"))
  (fetch-data))

(-main)

