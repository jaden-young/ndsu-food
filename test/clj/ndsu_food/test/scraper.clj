(ns ndsu-food.test.scraper
  (:require [clojure.test :refer :all]
            [ndsu-food.scraper :refer :all]
            [ndsu-food.test.test-helpers :as h]))

(use-fixtures :once h/setup-db)
(use-fixtures :each h/rollback)

(def hierarchal-test-menu '({:date "2017-08-07",
                             :restaurant {:name "West Dining Center", :abbreviation "WDC", :num "02"},
                             :meals
                             [{:name "Breakfast",
                               :categories
                               [{:name "Entrees",
                                 :items
                                 [{:name "Baked Cheese Omelet",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}
                                  {:name "Breakfast Smokies",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}
                                  {:name "Chocolate Chip Pancakes",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Starches",
                                 :items
                                 [{:name "Spicy Roasted Potatoes w/ Cheese",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}]}]}
                              {:name "Lunch",
                               :categories
                               [{:name "Desserts",
                                 :items
                                 [{:name "Cream Cheese Chocolate Cake",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Soups",
                                 :items
                                 [{:name "Cheesy Ham Chowder",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}
                                  {:name "Vegetable Soup",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Entrees",
                                 :items
                                 [{:name "Hot Ham and Cheese on Bun",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}
                                  {:name "Lemon Linguine",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Starches",
                                 :items
                                 [{:name "Sour Cream & Chive Potato Wedges",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Vegetables",
                                 :items
                                 [{:name "Broccoli Normandy Blend",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}]}]}
                              {:name "Dinner",
                               :categories
                               [{:name "Desserts",
                                 :items
                                 [{:name "Wowbutter Smores Bar",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}]}
                                {:name "Soups",
                                 :items
                                 [{:name "Cheesy Ham Chowder",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}
                                  {:name "Vegetable Soup",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Entrees",
                                 :items
                                 [{:name "Tater Tot Casserole",
                                   :gluten-free false,
                                   :vegetarian false,
                                   :nuts false}
                                  {:name "Vegetable Fettuccine Alfredo",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Starches",
                                 :items
                                 [{:name "Tater Tots",
                                   :gluten-free false,
                                   :vegetarian true,
                                   :nuts false}]}
                                {:name "Vegetables",
                                 :items
                                 [{:name "Whole Baby Carrots",
                                   :gluten-free true,
                                   :vegetarian true,
                                   :nuts false}]}]}]}))
(def flat-test-menu '({:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Baked Cheese Omelet",
                       :gluten_free true,
                       :category "Entrees",
                       :meal "Breakfast"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Breakfast Smokies",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Breakfast"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Chocolate Chip Pancakes",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Breakfast"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Spicy Roasted Potatoes w/ Cheese",
                       :gluten_free true,
                       :category "Starches",
                       :meal "Breakfast"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Cream Cheese Chocolate Cake",
                       :gluten_free false,
                       :category "Desserts",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Cheesy Ham Chowder",
                       :gluten_free false,
                       :category "Soups",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Vegetable Soup",
                       :gluten_free true,
                       :category "Soups",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Hot Ham and Cheese on Bun",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Lemon Linguine",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Sour Cream & Chive Potato Wedges",
                       :gluten_free false,
                       :category "Starches",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Broccoli Normandy Blend",
                       :gluten_free true,
                       :category "Vegetables",
                       :meal "Lunch"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Wowbutter Smores Bar",
                       :gluten_free false,
                       :category "Desserts",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Cheesy Ham Chowder",
                       :gluten_free false,
                       :category "Soups",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Vegetable Soup",
                       :gluten_free true,
                       :category "Soups",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian false,
                       :nuts false,
                       :food_item_name "Tater Tot Casserole",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Vegetable Fettuccine Alfredo",
                       :gluten_free false,
                       :category "Entrees",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Tater Tots",
                       :gluten_free false,
                       :category "Starches",
                       :meal "Dinner"}
                      {:date "2017-08-07",
                       :restaurant_name "West Dining Center",
                       :vegetarian true,
                       :nuts false,
                       :food_item_name "Whole Baby Carrots",
                       :gluten_free true,
                       :category "Vegetables",
                       :meal "Dinner"}))

(deftest test-without-db
  (let [menu (scrape!
              "2017-08-07"
              :save-to-db false
              :restaurants [:wdc]
              :cache-dir "test/clj/ndsu_food/test_files/")]
    (is (= menu hierarchal-test-menu))
    (is (= (flatten-menus menu) flat-test-menu))))

(deftest test-scrape-one-to-db
  (is (= 18 (scrape! "2017-08-07"
                     :restaurants [:wdc]
                     :cache-dir "test/clj/ndsu-food/test_files/"))))

(deftest test-insert-all-menus-on-date
  (is (= 49 (scrape! "2017-08-07"
                     :cache-dir "test/clj/ndsu-food/test_files/"))))

(deftest test-insert-same-item-twice
  (is (= 0 (do
             (scrape! "2017-08-07"
                      :restaurants [:wdc]
                      :cache-dir "test/clj/ndsu-food/test_files/")
             (scrape! "2017-08-07"
                      :restaurants [:wdc]
                      :cache-dir "test/clj/ndsu-food/test_files/")))))
