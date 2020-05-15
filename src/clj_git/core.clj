(ns clj-git.core
  (:import [java.util.zip InflaterInputStream])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(def ^:dynamic git-exec "git")

(defn rev-parse [rev]
  (-> (sh git-exec "rev-parse" "-n" "1" rev)
      :out
      (str/split-lines)
      last))

(defn sha-to-object-path [sha]
  (io/file (subs sha 0 2) (subs sha 2)))

(defn parse-object-header [str]
  (when-let [[_ obj-type obj-size] (re-find #"([a-z]+) (\d)" str)]
    {::type obj-type
     ::size (Integer/parseInt obj-size)}))

(defn build-metadata [lines]
  (reduce (fn [m s]
            (let [[k v] (str/split s #" " 2)]
              (update m (keyword k) #(conj (or % []) v))))
          {}
          lines))

(defn read-object-header [in]
  (apply str (map char (take-while pos-int? (repeatedly #(.read in))))))

(defn read-commit-body [in]
  (let [meta-lines (take-while not-empty (repeatedly #(.readLine in)))]
    (build-metadata meta-lines)))

(defn object-reader [path]
  (-> path
      (io/input-stream)
      (InflaterInputStream.)
      (io/reader)))

(defn trial [path]
  (with-open [in (object-reader path)]
    (prn (parse-object-header (read-object-header in)))
    (prn (read-commit-body in))))


(defn find-git-parent [path]
  (first (drop-while #(and % (not (.exists (io/file % ".git"))))
                     (iterate #(.getParentFile %) (io/file path)))))

(def own-repo (find-git-parent (.getParentFile (io/file *file*))))
(def commit-hash (rev-parse "HEAD"))
(def commit-path (str own-repo "/.git/objects/" (sha-to-object-path commit-hash)))

#_ (trial commit-path)
