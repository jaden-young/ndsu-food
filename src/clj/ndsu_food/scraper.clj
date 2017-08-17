(ns ndsu-food.scraper
  (:require [clj-time.core :as t]
            [clojure.java.io :as io]
            [cemerick.url :as url]
            [net.cgrand.enlive-html :as html :refer [select attr= attr-contains]]
            [clj-time.format :as t-fmt]
            [clojure.string :as str]
            [ndsu-food.db.core :as db]
            [ndsu-food.util :as util])
  (:gen-class))

(def ^:const loc-info {:wdc {:name "West Dining Center"
                             :abbreviation "WDC"
                             :num "02"}
                       :rdc {:name "Residence Dining Center"
                             :abbreviation "RDC"
                             :num "04"}
                       :udc {:name "Union Dining Center"
                             :abbreviation "UDC"
                             :num "10"}
                       :pe {:name "Pizza Express"
                            :abbreviation "PE"
                            :num "25"}
                       :mg {:name "Marketplace Grille"
                            :abbreviation "MG"
                            :num "11"}})

;; *****************************************************************************
;; Grab pages
;; *****************************************************************************

(def ^:const base-url "https://www.ndsu.edu/dining/menu/shortmenu.asp")
(def website-date-formatter (t-fmt/formatter "M/d/yyyy"))

(def cache-dir
  ;; One would assume that a directory name ends in '/', but I guess Java
  ;; has its reasons.
  (let [home (System/getProperty "user.home")
        folders [home ".cache" "ndsu-food" "html"]
        path (interpose (java.io.File/separatorChar) folders)
        s (apply str path)
        dir (str s (java.io.File/separatorChar))]
    dir))

(defn- menu-date-fmt
  [date]
  (t-fmt/unparse website-date-formatter date))

(defn- build-filename
  [date loc]
  (let [loc-str (-> loc
                    (str)
                    (subs 1)
                    (str/upper-case))]
    (str (util/iso-date-fmt date) "-" loc-str ".html")))

(defn- build-url
  [date loc]
  (let [u (url/url base-url)
        q {:dtdate (menu-date-fmt date)
           :locationNum (get-in loc-info [loc :num])}]
    (str (assoc u :query q))))

(defn- persist!
  [date loc]
  (let [fname (build-filename date loc)
        file (io/file (str cache-dir fname))]
    (when-not (.exists file)
      (io/make-parents file)
      (with-open [r (io/input-stream (build-url date loc))]
        (io/copy r file)))
    file))

(defn- grab
  [no-cache]
  (if no-cache
    build-url
    persist!))

;; *****************************************************************************
;; Scrape pages
;; *****************************************************************************

(defn- title-case-word
  [w]
  (if (zero? (count w))
    w
    (str (Character/toTitleCase (first w))
         (subs w 1))))

;;; Selectors. These functions take html data processed by enlive.

(defn- gluten-free?
  [row]
  (not (empty? (select row [(attr-contains :src "gluten")]))))

(defn- vegetarian?
  [row]
  (not (empty? (select row [(attr-contains :src "veggie")]))))

(defn- nuts?
  [row]
  (not (empty? (select row [(attr-contains :src "nuts")]))))

(defn- category?
  "Tells whether a given row contains a category (true) or a food item (false)"
  [row]
  (not (empty? (select row [:div.shortmenucats]))))

(defn- food-item
  "Selects a food item from a row"
  [row]
  (let [name (->> (select row [:div.shortmenurecipes :span])
                  (first)
                  (html/text) ; the last char of the name is some sort of space,
                  (butlast)   ; but string/trim doesn't get rid of it.
                  (apply str))]
    {:name (str/replace name #" - Vegetarian" "")
     :gluten-free (gluten-free? row)
     :vegetarian (vegetarian? row)
     :nuts (nuts? row)}))

(defn- meals
  "Selects a map of nodes where each top-level entry is a meal"
  [page]
  (select page
          [[:table (attr= :cellspacing "0") (attr= :cellpadding "0")
            (attr= :border "0") (attr= :width "100%") (attr= :height "100%")]]))

(defn- meal-name
  "Selects the meal name of the first meal found as a string"
  [page]
  (->> (select page [:div.shortmenumeals])
       (map html/text)
       (first)))

(defn- meal-data
  "Selects the categories and menu items from a meal page"
  [page]
  (select page [:table (attr= :cellspacing "1") :> :tr]))

(defn- category-name
  [row]
  (let [name (->> (select row [:div.shortmenucats :span])
                  (map #(->> %
                             (html/text)
                             (str/trim)
                             (drop 3)
                             (drop-last 3)
                             (apply str)
                             (str/lower-case)
                             (title-case-word))))]
    ;; I don't want to deal with escaping that slash everywhere in the
    ;; application. "soups" carries just as much meaning as "soups/chowders"
    (str/replace (first name) #"/chowders" "")))

(defn- categorize
  [meal-info]
  (let [cats-and-items (partition 2 (partition-by category? meal-info))]
    (vec (for [[[cat] foods] cats-and-items]
           (let [name (category-name cat)
                 items (vec (map food-item foods))]
             {:name name
              :items items})))))

(defn- ->meal
  [page]
  (let [name (meal-name page)
        data (categorize (meal-data page))]
    {:name name
     :categories data}))

(defn- read-page
  [src]
  (with-open [rdr (io/reader src)]
    (html/html-resource rdr)))

(defn- ->meals
  "Extracts a sequence of meals maps from an html document. Argument must be
  able to be coerced to a java.io.Reader."
  [html]
  (let [page (read-page html)
        meals (map ->meal (meals page))]
    meals))

(defn- scrape-one
  ([grabber date loc]
   (let [src (grabber date loc)]
     {:date (util/iso-date-fmt date)
      :restaurant (get loc-info loc :name)
      :meals (vec (->meals src))})))

(defn- flatten-menu
  [menu]
  (->> menu
       (map (comp
             flatten
             (fn [loc-menu]
               (let [date (:date loc-menu)
                     loc-name (get-in loc-menu [:restaurant :name])
                     meals (:meals loc-menu)]
                 (->> meals
                      (map (comp
                            flatten
                            (fn [meal]
                              (let [meal-name (:meal meal)
                                    cats (:categories meal)]
                                (->> cats
                                     (map (comp
                                           flatten
                                           (fn [category]
                                             (let [cat-name (:name category)
                                                   items (:items category)]
                                               (->> items
                                                    (map (fn [item]
                                                           (let [item-name (:name item)
                                                                 veg (:vegetarian item)
                                                                 glut (:gluten-free item)
                                                                 nuts (:nuts item)]
                                                             {:date date
                                                              :restaurant-name loc-name
                                                              :meal meal-name
                                                              :category cat-name
                                                              :food-item-name item-name
                                                              :vegetarian veg
                                                              :gluten-free glut
                                                              :nuts nuts}))))))))))))))))))
       (flatten)))

(defn scrape!
  "Scrapes the NDSU site for menu data on the given date, returning a sequence
  of menus. To specify which restaurants are scraped, pass any number of: :wdc
  :rdc :udc :pe :mg, Otherwise all menus are scraped. Scraped html pages are
  persisted to ~/.cache/ndsu-food/html/ unless :no-cache is passed as an arg.
  :no-cache always forces a new hit to the web, otherwise cache is checked
  first. Archiving the pages may be advisable since past pages are not available
  from the NDSU site."
  [date & flags]
  (let [flags (set flags)
        grabber (grab (contains? flags :no-cache))
        fs (disj flags :no-cache)
        locs (if (empty? fs)
               (keys loc-info)
               fs)]
    (map (partial scrape-one grabber date) locs)))

(defn -main
  [& args]
  ())
