(ns org-analyzer.processing
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [java-time :as time]
            [taoensso.tufte :refer [defnp p]])
  (:import java.io.File
           java.lang.String
           [java.time Duration LocalDate LocalDateTime]
           java.time.format.DateTimeFormatter
           java.util.Locale))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; CLOCK parsing

(defrecord Clock [start end duration clock-string sections])

(def clock-re #"(?i)^\s*CLOCK:\s*((?:\[|<)[^\]>]+(?:\]|>))(?:--((?:\[|<)[^\]>]++(?:\]|>)))?(?:\s*=>\s*([0-9:]+))?\s*$")
(def brackets-re #"^(\[|<)|(\]|>)$")
(def colon-re #":")

(def date-time-patterns [{:parse #(p ::localdatetime-parse-timestamp (LocalDateTime/parse %1 (DateTimeFormatter/ofPattern %2 %3)))
                          :pattern "y-M-d[ ][cccc][ccc][ ]H:m"}
                         {:parse #(p ::localdate-parse-timestamp (. (LocalDate/parse %1 (DateTimeFormatter/ofPattern %2 %3)) (atTime 0 0)))
                          :pattern "y-M-d[ ][cccc][ccc]"}])

(def locales [Locale/ENGLISH Locale/GERMAN])

(def timestamp-re #"([0-9]{4})-([0-9]{2})-([0-9]{2})\s+(?:\S+\s+)?([0-9]{1,2}):([0-9]{1,2})")

(defn parse-timestamp-manually [string]
  (let [[_ & year-month-day-hour-min] (re-find timestamp-re string)
      year-month-day-hour-min (map #(Integer/parseInt %) year-month-day-hour-min)]
  (apply time/local-date-time year-month-day-hour-min)))

#_(defnp parse-timestamp
  "`string` like \"[2019-06-19 Wed 14:11]\". Returns LocalDateTime."
  [string]
  (when string
    (let [sanitized (s/replace string brackets-re "")]
      (first
       (filter some?
               (for [locale locales
                     {:keys [parse pattern]} date-time-patterns]
                 (or (try (parse sanitized pattern locale) (catch Exception e nil))
                     (parse-timestamp-manually sanitized))))))))

(defnp parse-timestamp
  "`string` like \"[2019-06-19 Wed 14:11]\". Returns LocalDateTime."
  [string]
  (when string
    (parse-timestamp-manually string)))

(defnp parse-duration
  "`duration-string` like \"3:22\"."
  [duration-string]
  ^Duration (as-> duration-string it
              (s/replace it colon-re "H")
              (str "PT" it "M")
              (Duration/parse it)))

(defnp parse-clock [clock-string]
  (let [[_ start end duration] (re-find clock-re clock-string)]
    {:start (parse-timestamp start)
     :end (when end (parse-timestamp end))
     :duration (when duration (parse-duration duration))}))

(declare parent-entries all-tags-for)

(defnp find-clocks [org-data]
  (for [{:keys [type text] :as entry} org-data
        :when (= type :clock)]
    (merge {:clock-string text
            :sections (p ::parent-entries (parent-entries entry org-data))
            :tags (p ::all-tags-for (all-tags-for entry org-data))}
           (parse-clock text))))

(defn clocks-by-section [clocks]
  (group-by (comp last :sections) clocks))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; org-parsing

(def headline-re #"^(\*+)\s+(.*)$")
(def file-props-re #"^\s*#\+([0-9A-Za-z_\-]+):\s*(.*)")
(def metadata-re #"^\s*(CLOCK|DEADLINE|START|CLOSED|SCHEDULED|CREATED):.*")
(def simple-clock-re #"^\s*(CLOCK):.*")
(def section-keyword-re #"^(TODO|NEXT|MAYBE|WAIT|CANCELLED|DONE|REVIEW)\s*(.*)")

(defn parse-section-text-and-tags [text]
  (let [[_ keyword text-no-kw] (re-find section-keyword-re text)
        text (or text-no-kw text)
        [_ text-no-tags raw-tags] (re-find #"(.*)\s+:([^ ]+):$" text)
        text (s/trim (or text-no-tags text))]
    (merge {:name text}
           (when keyword {:keyword keyword})
           (when raw-tags {:tags (keep not-empty (s/split raw-tags #":"))}))))

(defn read-org-lines [lines]
  (for [[i line] (map-indexed vector lines)
        :let [[_ stars text] (re-find headline-re line)
              [metadata] (re-find metadata-re line)
              metadata (and metadata (s/trim metadata))
              clock (when (and metadata (s/starts-with? metadata "CLOCK:")) metadata)
              [_ file-prop-name prop-value] (re-find file-props-re line)
              line-no (inc i)]
        :when (or stars metadata clock file-prop-name)]
    (cond
      stars (merge {:line line-no
                    :type :section
                    :depth (count stars)}
                   (parse-section-text-and-tags text))
      clock {:line line-no :type :clock :text clock}
      metadata {:line line-no :type :metadata :text metadata}
      file-prop-name {:line line-no
                      :type :file-prop
                      :prop file-prop-name
                      :value prop-value})))

(defn read-file-props [file file-name parsed]
  (let [path (when (and file (instance? java.io.File file))
               (.getCanonicalPath file))
        props (->> parsed
                   (take-while #(not (= :section (:type %))))
                   (filter #(= (:type %) :file-prop)))
        tags (->> props
                  (keep #(when (= (:prop %) "FILETAGS")
                           (->> #":"
                                (s/split (:value %))
                                (keep not-empty))))
                  (apply concat))]
    {:type :file :path path :name file-name :depth 0 :index 0 :props props :tags tags}))


(defn read-sections
  "Takes the parsed lines filters out sections and clocks and adds parent
  information to them. Parent relationship is definend through an index to a
  previous item."
  [file file-name parsed]
  (loop [[current & rest] parsed
         index 0
         result (list (read-file-props file file-name parsed))
         parent-cache [0]]
    (if-not current
      (reverse result)
      (let [{:keys [type depth]} current
            depth (or depth ##Inf)]
        (if-not (#{:clock :section :metadata} type)
          (recur rest index result parent-cache)
          (let [
                index (inc index)
                [parent parent-cache] (let [size (count parent-cache)
                                            section? (= type :section)
                                            parent-at-or-before (dec (if section? (min size depth) size))
                                            parent (first (drop-while nil? (map (partial parent-cache) (range parent-at-or-before -1 -1))))
                                            cache (if section?
                                                    (conj (case (compare size depth)
                                                            1 (into [] (take depth parent-cache))
                                                            0 parent-cache
                                                            -1 (into [] (concat parent-cache (map (constantly nil) (range (- depth (dec size)))))))
                                                          index)
                                                    parent-cache)]
                                        [parent cache])
                entry (case type
                        (:clock :metadata) (assoc current :parent parent :index index)
                        :section (assoc current :parent parent :index index))]
            (recur rest index (conj result entry) parent-cache)))))))


(defn parent-entries [entry org-data]
  (loop [index (:parent entry) result []]
    (if-let [next (and index (nth org-data index))]
      (recur (:parent next) (conj result next))
      result)))

(defn all-tags-for [entry org-data]
  (let [with-parents (conj (parent-entries entry org-data) entry)]
    (set (apply concat (keep :tags with-parents)))))

(defnp parse-org-file
  "The main function to turn org-content into a flat sequence of entry maps, each having at least :type, :line, and :index fields."
  ([^File org-file]
   (let [org-file (if (string? org-file) (io/file org-file) org-file)]
     (parse-org-file (.getName org-file) org-file)))
  ([name org-file]
   (let [lines (p ::parse-org-file-lines (line-seq (io/reader org-file)))
         parsed (p ::parse-org-file-read-org-lines (read-org-lines lines))
         org-data (p ::parse-org-file-read-sections (read-sections org-file name parsed))]
     org-data)))
