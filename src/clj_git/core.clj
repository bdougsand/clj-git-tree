(ns clj-git.core
  (:require [clojure.data.json :as json]
            [clj-git.tree :as t])
  (:gen-class))


(defn start-server [repo]
  )

(def own-repo "/Users/brian/Desktop/gittest")

(defn -main
  [& args]
  (json/print-json (t/build-tree own-repo)))
