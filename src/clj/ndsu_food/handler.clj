(ns ndsu-food.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [ndsu-food.routes.services :refer [service-routes]]
            [compojure.route :as route]
            [ndsu-food.env :refer [defaults]]
            [mount.core :as mount]
            [ndsu-food.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    #'service-routes
    (route/not-found
      "page not found")))


(defn app [] (middleware/wrap-base #'app-routes))
