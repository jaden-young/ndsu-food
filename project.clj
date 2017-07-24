(defproject ndsu-food "0.1.0-SNAPSHOT"
  :description "Collection of programs to improve the horrid experience of viewing NDSU restaurant and dining center menus."
  :url "https://www.gitlab.com/jaden-young/ndsu-food"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [enlive "1.1.6"]]
  :main ^:skip-aot ndsu-food.core
  :target-path "target/%s"
  :profiles {:scraper {:main scraper.core
                       :uberjar-name "ndsu-food-scraper.jar"}
             :grabber {:main grabber.core
                       :uberjar-name "ndsu-food-grabber.jar"}
             :uberjar {:aot :all}}
  :repl-options {:init (set! *print-length* 50)})
