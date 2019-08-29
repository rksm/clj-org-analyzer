(ns org-analyzer.view.info
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.pprint :as pp]))

(defn info-view [app-state close-fn]
  (let [{:keys [known-org-files non-existing-org-files] {:keys [clock-count org-files]} :info} @app-state]
    [:div.info
     [:div [:h4 (pp/cl-format nil "Found ~a clock~:*~P in ~a org file~:*~P" clock-count (count org-files))]]
     [:ul [:h4 "file arguments passed to clj-analyzer"]
      (doall (for [f known-org-files]
               ^{:key f} [:li [:span f]]))]

     (when (not-empty non-existing-org-files)
       [:ul [:h4 "files passed to clj-analyzer that are non-existing"]
        (doall (for [f non-existing-org-files]
                 ^{:key f} [:li [:span f]]))])

     [:ul [:h4 "org files found"]
      (doall (for [f org-files]
               ^{:key f} [:li [:span f]]))]

     [:button.close.material-button
      {:on-click close-fn}
      [:i.material-icons "close"]]]))
