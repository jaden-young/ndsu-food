(ns ndsu-food.test.db.core
  (:require [ndsu-food.db.core :refer [*db*] :as db]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [ndsu-food.config :refer [env]]
            [mount.core :as mount]
            [ndsu-food.test.test-helpers :as h]))

(use-fixtures :once h/setup-db)
(use-fixtures :each h/rollback)


(deftest test-food-item

  (is (=
       '({:id 1})
       (db/new-food-item!
        {:name "test_item"
         :vegetarian true
         :gluten_free false
         :nuts true})))
  (is (= {:id 1
          :name "test_item"
          :vegetarian true
          :gluten-free false
          :nuts true}
         (db/get-food-item-by-id {:id 1}))))
