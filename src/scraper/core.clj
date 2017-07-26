(ns scraper.core
  (:require [clj-time.core :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cemerick.url :as url]
            [net.cgrand.enlive-html :as html :refer [select attr= attr-contains]]
            [clj-time.format :as t-fmt]
            [clojure.string :as str])
  (:gen-class))

;; *****************************************************************************
;; Grab pages
;; *****************************************************************************
(def ^:const base-url "https://www.ndsu.edu/dining/menu/shortmenu.asp")
(def website-date-formatter (t-fmt/formatter "M/d/yyyy"))

;; Key to identify restaurant with website
(def ^:const loc-nums {:wdc "02"
                       :rdc "04"
                       :udc "10"
                       :pe "25"
                       :mg "11"})
(def ^:const all-locs (vec (keys loc-nums)))

(def cache-dir
  ;; One would assume that a directory name ends in '/', but I guess Java
  ;; has it's reasons.
  (let [home (System/getProperty "user.home")
        folders [home ".cache" "ndsu-food" "html"]
        path (interpose (java.io.File/separatorChar) folders)
        s (apply str path)
        dir (str s (java.io.File/separatorChar))]
    dir))

(defn- loc-abbrev
  [loc]
  (-> loc
      (str)
      (subs 1)
      (string/upper-case)))

(defn iso-date-fmt
  [date]
  (t-fmt/unparse (t-fmt/formatters :date) date))

(defn- menu-date-fmt
  [date]
  (t-fmt/unparse website-date-formatter date))

(defn- build-filename
  [date loc]
  (let [loc-str (-> loc
                    (str)
                    (subs 1)
                    (str/upper-case))]
  (str (iso-date-fmt date) "-" loc-str ".html")))

(defn- build-url
  [date loc]
  (let [u (url/url base-url)
        q {:dtdate (menu-date-fmt date)
           :locationNum (get loc-nums loc)}]
    (str (assoc u :query q))))

(defn- persist!
  [date loc]
  (let [fname (build-filename date loc)
        file (io/file (str cache-dir fname))]
    (when-not (.exists file)
      (io/make-parents file)
      (io/copy (build-url date loc) file))
    file))

(defn- locs-resources
  [date locs resource-fn]
  (into {} (map #(vector % (resource-fn date %)) locs)))

(defn grab
  "Creates a mapping from location keywords to urls of
  menus for those dates/locs on the web. If locations are not
  provided, defaults to all."
  ([date]
   (apply grab date all-locs))
  ([date & locs]
   (locs-resources date locs build-url)))

(defn grab!
  "Creates a mapping from location keywords to java.io.File objects with html
  data for the menus at that loc/date. Grabs the menu data from the web and
  stores it at ~/.cache/ndsu-food/html/. Cache is checked each time, so web
  is only hit once."
  ([date]
   (apply grab! date all-locs))
  ([date & locs]
   (locs-resources date locs persist!)))
;; *****************************************************************************
;; Scrape pages
;; *****************************************************************************
(defn- gluten-free?
  [row]
  (not (empty? (select row [(attr-contains :src "gluten")]))))

(defn- vegetarian?
  [row]
  (not (empty? (select row [(attr-contains :src "veggie")]))))

(defn- food-item
  "Selects a food item from a row"
  [row]
  ;; The last character is some kind of space, but
  ;; clojure doesn't recognize it as such, so trim
  ;; doesn't work. There has to be a more efficient way of doing this.
  ;; HACK: Fix unnecessary conversions
  (let [name (->> (select row [:div.shortmenurecipes :span])
                  (map html/text)
                  (apply str)
                  (butlast)
                  (apply str))]
    {:name (string/replace name #" - Vegetarian" "")
     :gluten-free (gluten-free? row)
     :vegetarian (vegetarian? row)}))

(defn- category?
  "Tells whether a given row contains a category (true) or a food item (false)"
  [row]
  (not (empty? (select row [:div.shortmenucats]))))

(defn- ->kebob-case
  "Not rigorous but handles the case here"
  [s]
  (-> s
      (string/lower-case)
      (string/replace #" " "-")))

(defn- category-name
  [row]
  (let [name (->> (select row [:div.shortmenucats :span])
                  (map #(->> %
                             (html/text)
                             (string/trim)
                             (drop 3)
                             (drop-last 3)
                             (apply str)
                             (->kebob-case))))]
    ;; I don't want to deal with escaping that slash everywhere in the
    ;; application. "soups" carries just as much meaning as "soups/chowders"
    (string/replace (first name) #"/chowders" "")))

(defn- categorize
  "Creates a map of {:category [food-item]}"
  [meal-info]
  (let [cats-and-items (partition 2 (partition-by category? meal-info))]
    (into {} (for [[[cat] foods] cats-and-items]
               (let [k (keyword (category-name cat))
                     v (vec (map food-item foods))]
                 [k v])))))

(defn- meals
  "Selects a map of nodes where each top-level entry is a meal"
  [page]
  (select page
          [[:table (attr= :cellspacing "0") (attr= :cellpadding "0")
            (attr= :border "0") (attr= :width "100%") (attr= :height "100%")]]))

(defn- meal-name
  "Selects the meal name as a string"
  [meal]
  (->> (select meal [:div.shortmenumeals])
       (map html/text)
       (first)
       (string/lower-case)))

(defn- meal-data
  "Selects the categories and menu items from a meal"
  [meal]
  (select meal [:table (attr= :cellspacing "1") :> :tr]))

(defn- meal
  [m]
  (let [name (meal-name m)
        data (meal-data m)]
    {(keyword name) (categorize data)}))

(defn- fetch-page
  [file]
  (with-open [rdr (io/reader file)]
    (html/html-resource rdr)))

(defn html->menu
  "Extracts a menu map from an html document. Argument must be able to
  be coerced to a java.io.Reader"
  [html]
  (let [page (fetch-page html)
        ms (meals page)]
    (into {} (map meal ms))))

(defn- fmap
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn- do-scrape
  [date locs grabber]
  (let [srcs (apply grabber date locs)
        menus (fmap html->menu srcs)]
    {:date (iso-date-fmt date)
     :menus menus}))

(defn scrape
  "Scrapes menu data from the web for the given date/locs, returning a map.
  If no locations are provided, all are scraped. Results are not cached."
  ([date]
   (apply scrape date all-locs))
  ([date & locs]
   (do-scrape date locs grab)))

(defn scrape!
  "Same as scrape, but persists html files to ~/.cache/ndsu-food/html/.
  Cache is checked on each run, so web is only hit once."
  ([date]
   (apply scrape! date all-locs))
  ([date & locs]
   (do-scrape date locs grab!)))

(defn -main
  [& args]
  ())
