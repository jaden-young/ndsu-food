(ns ndsu-food.scraper
  (:require [clj-time.core :as t]
            [clojure.java.io :as io]
            [cemerick.url :as url]
            [net.cgrand.enlive-html :as html :refer [select attr= attr-contains]]
            [clj-time.format :as t-fmt]
            [clojure.string :as str]
            [ndsu-food.db.core :as db :refer [*db*]]
            [ndsu-food.util :as util]
            [clojure.java.jdbc :as jdbc])
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

(def default-cache-dir
  ;; One would assume that a directory name ends in '/', but I guess Java
  ;; has its reasons.
  (let [home (System/getProperty "user.home")
        folders [home ".cache" "ndsu-food" "html"]
        path (interpose (java.io.File/separatorChar) folders)
        s (apply str path)
        dir (str s (java.io.File/separatorChar))]
    dir))

(defn- read-page
  [src]
  (with-open [rdr (io/reader src)]
    (html/html-resource rdr)))

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
  ([date loc]
   (persist! date loc default-cache-dir))
  ([date loc cache-dir]
   (let [fname (build-filename date loc)
         file (io/file (str cache-dir fname))]
     (when-not (.exists file)
       (io/make-parents file)
       (with-open [r (io/input-stream (build-url date loc))]
         (io/copy r file)))
     file)))

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
  "Extracts a food item from a row"
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

;;; Apply selectors to parse data

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

(defn- ->meals
  "Extracts a sequence of meals maps from an html document. Argument must be
  able to be coerced to a java.io.Reader."
  [html]
  (let [page (read-page html)
        meals (map ->meal (meals page))]
    meals))

;;; Flatten parsed data into format consumable by the database

(defn- extract-food-item
  [item]
  (clojure.set/rename-keys item {:name :food_item_name
                                 :gluten-free :gluten_free}))

(defn- flatten-category
  [category]
  (let [cat-name (:name category)
        items (map extract-food-item (:items category))]
    (map #(assoc % :category cat-name) items)))

(defn- flatten-meal
  [meal]
  (let [meal-name (:name meal)
        cats (flatten (map flatten-category (:categories meal)))]
    (map #(assoc % :meal meal-name) cats)))

(defn- flatten-menu
  [menu]
  (let [date (:date menu)
        r-name (get-in menu [:restaurant :name])
        meals (flatten (map flatten-meal (:meals menu)))]
    (flatten (map #(merge {:date date
                           :restaurant_name r-name}
                          %)
                  meals))))

(defn flatten-menus
  "Flatten menus produced by scrape! into a format consumable by the database"
  [menus]
  (mapcat flatten-menu menus))

(defn- insert-scraped-menus!
  "Takes a sequence of flat menu data and inserts each inside of a transaction.
  Returns the number of items inserted."
  [menus]
  (jdbc/with-db-transaction [tx *db*]
    (->> menus
         (map db/new-food-item-served-at!)
         (apply +))))

(defn- scrape-one
  ([grabber date loc]
   (let [src (grabber date loc)]
     {:date (util/iso-date-fmt date)
      :restaurant (get loc-info loc :name)
      :meals (vec (->meals src))})))

(defn scrape!
  "Scrapes the NDSU site for menu data on the given date.
  Arguments:
      date: 'yyyy-MM-dd' formatted string

  Usage:
      Scrapes every menu on the date 1970-01-01, saves the
      html file in the default cache dir ~/home/$USER/.cache/ndsu-food/html/
      and saves the resulting data to the database

      (scrape! (clj-time.core/date-time 1970 01 01))

  Options:
      cache-dir: A string representing an absolute path to the directory to save
                 cached html pages. If set to nil or false, the pages are not
                 cached and the website is hit each time.
                 Defaults to /home/$USER/.cache/ndsu-food/html/
      save-to-db: If true, save the scraped data to the database and return the
                  number of items inserted. The inserts are performed inside of
                  a transaction. If any exceptions are raised (other than then
                  the data already existing) the exception is thrown and the
                  transaction rolled back. If false, returns a sequence of maps
                  where each map represents the menu for a single restaurant.
      restaurants: Vector of keywords specifying which establishments to scrape.
                   Defaults to all. Available options: :rdc :wdc :udc :pe :mg "
  [date & {:keys [cache-dir save-to-db restaurants]
           :or {cache-dir default-cache-dir
                save-to-db true
                restaurants [:rdc :wdc :udc :pe :mg]}}]
  (let [grab (if cache-dir persist! build-url)
        d (util/parse-date-add-default-zone date)
        menus (map (partial scrape-one grab d) restaurants)]
    (if save-to-db
      (->> menus
           (flatten-menus)
           (map #(update % :date util/parse-date-add-default-zone))
           (insert-scraped-menus!))
      menus)))

(defn -main
  [& args]
  ())
