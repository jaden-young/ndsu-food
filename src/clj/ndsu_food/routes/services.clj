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
                  "yyyy-MM-dd formatted date string"))

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "NDSU Food"
                           :description "Access NDSU Dining menu information"}}}}

  (context "/api" []
    :tags ["Menus"]
    ;; While automatic coercion/error throwing is nice, I haven't found a way
    ;; to customize those error messages. I don't want to return
    ;; "errors: (throws? (ndsu-food.ns.other-ns/schema-name?asdf0a9s8d))"
    :coercion (constantly nil)

    ;; TODO: list menus resource
    (GET "/menus" []
         :return [Menu]
         :summary "Get a list of menus with pagination"
         (not-implemented))

    (GET "/menus/:date" []
         :path-params [date :- Date-String]
         :return Menu
         :summary "Menu for a single date"
         (if (s/check Date-String date)
           (bad-request {:errors [{:message "Malformed parameter: date"
                                   :description (str "date must be a 'yyyy-MM-dd' formatted date string. Got: " date)}]})
           (if-let [menu (db/get-formatted-menu-on-date date)]
             (ok menu)
             (content-type
              (not-found (str "No menu data found for: " date))
              "text/plain"))))))
