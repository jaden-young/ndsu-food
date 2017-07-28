(ns ndsu-food.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[ndsu-food started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[ndsu-food has shut down successfully]=-"))
   :middleware identity})
