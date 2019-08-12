(ns org-analyzer.view.info
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs.pprint :as pp]))

(defn info [app-state]
  (r/with-let [expand-info? (ratom false)]
    (let [{:keys [known-org-files]
           {:keys [clock-count org-files]} :info} @app-state]
      [:div.info
       [:div.clock-count
        (pp/cl-format nil "~a clock~:*~P" clock-count)
        [:button.material-button
         {:on-click #(swap! expand-info? not)}
         [:i.material-icons "info"]]
        ]

       (when @expand-info?
         (let [{:keys [known-org-files non-existing-org-files] {:keys [clock-count org-files]} :info} @app-state]
           [:div.file-statistics
            [:ul [:h4 "file arguments passed to clj-analyzer"]
             (doall (for [f known-org-files]
                      ^{:key f} [:li [:span f]]))]

            (when (not-empty non-existing-org-files)
              [:ul [:h4 "files passed to clj-analyzer that are non-existing"]
               (doall (for [f non-existing-org-files]
                        ^{:key f} [:li [:span f]]))])

            [:ul [:h4 "org files found"]
             (doall (for [f org-files]
                      ^{:key f} [:li [:span f]]))]])
         )])))
