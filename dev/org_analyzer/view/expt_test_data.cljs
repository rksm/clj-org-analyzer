(ns org-analyzer.view.expt-test-data
  (:require
   [org-analyzer.view.app :as app]
   [cljs.pprint :refer [pprint]]))

(def info {:clock-count 5, :org-files ["/foo/bar.org" "/foo/baz.org"]})

(def clocks [{:start "2019-08-25 14:12" :end "2019-08-26 15:10" :duration "0:58" :path ["bar.org"]        :name "activity 1" :location "bar.org/activity 1"      :tags #{"org" "tag1" "tag2"}}
             {:start "2019-08-26 15:26" :end "2019-08-26 17:12" :duration "1:46" :path ["bar.org"]        :name "activity 1" :location "bar.org/activity 1"      :tags #{"org" "tag1" "tag2"}}
             {:start "2019-08-23 17:15" :end "2019-08-26 17:50" :duration "0:35" :path ["baz.org" "zork"] :name "activity 2" :location "baz.org/zork/activity 2" :tags #{"org" "tag 3"}}
             {:start "2019-08-26 19:17" :end "2019-08-26 19:27" :duration "0:10" :path ["baz.org" "zork"] :name "activity 2" :location "baz.org/zork/activity 2" :tags #{"org" "tag 3"}}
             {:start "2019-08-27 19:38" :end false              :duration false  :path ["baz.org" "zork"] :name "activity 2" :location "baz.org/zork/activity 2" :tags #{"org" "tag 3"}}])

(def calendar (into (sorted-map-by <) {"2019-08-23" {:date "2019-08-23" :dow 5 :dow-name "Friday"   :week 34 :month "August" :year 2019}
                                       "2019-08-24" {:date "2019-08-24" :dow 6 :dow-name "Saturday" :week 34 :month "August" :year 2019}
                                       "2019-08-25" {:date "2019-08-25" :dow 7 :dow-name "Sunday"   :week 34 :month "August" :year 2019}
                                       "2019-08-26" {:date "2019-08-26" :dow 1 :dow-name "Monday"   :week 35 :month "August" :year 2019}
                                       "2019-08-27" {:date "2019-08-27" :dow 2 :dow-name "Tuesday"  :week 35 :month "August" :year 2019}}))

(defn test-data []
  (let [app-state (app/empty-app-state)
        dom-state (app/empty-dom-state)
        event-handlers (app/event-handlers app-state dom-state)]
    (swap! app-state merge (app/prepare-fetched-clocks info clocks calendar))
    {:app-state app-state
     :dom-state dom-state
     :event-handlers event-handlers}))
