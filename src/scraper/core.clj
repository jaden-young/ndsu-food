(ns scraper.core
  (:require [clojure.java.io]
            [net.cgrand.enlive-html :as html :refer [select attr= attr-contains]]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def pages-dir "resources/scraped-pages/")
(def test-page-path (str pages-dir "2017-07-20-WDC.html"))

;; Private functions are either small utilites, or only operate on the ugly
;; intermediate gobley gook on our road to extracted data.

(defn- gluten-free?
  [row]
  (not (empty? (select row [(attr-contains :src "gluten")]))))

(defn- vegetarian?
  [row]
  (not (empty? (select row [(attr-contains :src "veggie")]))))

;; TODO: remove " - Vegetarian" from end of item names
(defn- food-item
  "Selects a food item from a row"
  [row]
  {:name (->> (select row [:div.shortmenurecipes :span])
              (map html/text)
              (apply str)  ;; The last character is some kind of space, but
              (butlast)    ;; clojure doesn't recognize it as such, so trim
              (apply str)) ;; doesn't work. TODO: Fix unnecessary conversions
   :gluten-free (gluten-free? row)
   :vegetarian (vegetarian? row)})

(defn- category?
  "Tells whether a given row contains a category (true) or a food item (false)"
  [row]
  (not (empty? (select row [:div.shortmenucats]))))

(defn- ->kebob-case
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
  [meal-info]
  (let [cats-and-items (partition 2 (partition-by category? meal-info))]
    (into {} (for [[[cat] foods] cats-and-items]
               [(keyword (category-name cat)) (into [] (map food-item foods))]))))

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

(defn html->meal
  "Extracts a menu from an html document. html can be one of:
    * a string (denoting a resource on the classpath)
    * a java.io.File
    * a java.io.Reader,
    * a java.io.InputStream,
    * a java.net.URI
    * a java.net.URL"
  [html]
  (let [page (html/html-resource html)
        ms (meals page)]
    (into {} (map meal ms))))

;; dev func
(defn- fetch-page
  [file-path]
  (with-open [rdr (io/reader file-path)]
    (html/html-resource rdr)))

(defn -main
  [& args]
  (with-open [rdr (io/reader test-page-path)]
    (html->meal rdr)))
