(ns ndsu-food.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clj-time.core :as t]
            [clj-time.format :as t-fmt]
            [ndsu-food.util :as util]
            [ndsu-food.db.core :as db]
            [cheshire.core :as c]))

(s/defschema Food-Item
  {:name s/Str
   :vegetarian s/Bool
   :gluten-free s/Bool
   :nuts s/Bool})

(s/defschema Category
  {:name s/Str
   :items [Food-Item]})

(s/defschema Restaurant
  {:name s/Str
   :categories [Category]})

(s/defschema Meal
  {:name s/Str
   :restaurants [Restaurant]})

(s/defschema Menu
  {:date s/Str
   :meals [Meal]})

(defn date-string?
  [date]
  (some? (t-fmt/parse (t-fmt/formatters :date) date)))

(def Date-String (describe
                  (s/constrained s/Str date-string?)
                  "ISO-8601 \"YYYY-MM-DD\" formatted date string"))

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "NDSU Food"
                           :description "Access NDSU Dining menu information"}}}}

  (context "/api" []
    :tags ["Menu"]

    (GET "/menu" []
      :return Menu
      :query-params [{date :- Date-String (util/today-str)}]
      :summary "menu for date, defaults to today"
      (let [d (util/parse-date-add-default-zone date)
            menu (-> {:date d}
                     (db/get-menus-on-date)
                     (db/hierarchize-menu-on-date)
                     (first))]
        (if menu
          (ok menu)
          (not-found {:errors (str "No menu data for " date)}))))))
