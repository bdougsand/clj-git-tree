(ns clj-git.tree
  (:import [java.nio.file FileSystems Path]
           [java.util.zip InflaterInputStream])
  (:require [clojure.core.async :refer [go-loop <!]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(def ^:dynamic git-exec "git")

(defn rev-parse [rev]
  (-> (shell/sh git-exec "rev-parse" "-n" "1" rev)
      :out
      (str/split-lines)
      last))

(defn ref-branch [symbolic-ref]
  (-> (shell/sh git-exec "symbolic-ref" "--short" symbolic-ref)
      :out
      (str/trim)))

(defn sha-to-object-path [sha]
  (io/file (subs sha 0 2) (subs sha 2)))

(defn path-to-sha [path]
  (let [[_ a b] (re-find #"([0-9a-f]{2})/([0-9a-f]{38})$" path)]
    (str a b)))

(defn find-git-parent [path]
  (first (drop-while #(and % (not (.exists (io/file % ".git"))))
                     (iterate #(.getParentFile %) (io/file path)))))

(defn branch-refs [git-root]
  (.listFiles (io/file git-root ".git/refs/heads")))

(defn tag-refs [git-root]
  (.listFiles (io/file git-root ".git/refs/tags")))

(defn parse-object-header [str]
  (when-let [[_ obj-type obj-size] (re-find #"([a-z]+) (\d+)" str)]
    {:type obj-type
     :size (Integer/parseInt obj-size)}))

(defn build-metadata [lines]
  (reduce (fn [m s]
            (let [[k v] (str/split s #" " 2)]
              (update m (keyword k) #(conj (or % []) v))))
          {}
          lines))

(defn read-object-header
  "Reads up to the first 0 byte"
  [in]
  (apply str (map char (take-while pos-int? (repeatedly #(.read in))))))

(defn read-commit-body [in]
  (let [meta-lines (take-while not-empty (repeatedly #(.readLine in)))]
    (build-metadata meta-lines)))

(defn object-reader [path]
  (-> path
      (io/input-stream)
      (InflaterInputStream.)
      (io/reader)))

(defn read-git-object-from-stream [in]
  (let [header (parse-object-header (read-object-header in))]
    (merge header
           (case (:type header)
             "commit" (read-commit-body in)
             nil))))

(defn read-git-object
  ([path]
   (with-open [in (object-reader path)]
     (let [path (str path)]
       (assoc (read-git-object-from-stream in)
              :id (path-to-sha path)
              :path path))))
  ([root sha]
   (read-git-object (str root "/.git/objects/" (sha-to-object-path sha)))))

(defn commit-parents [commit]
  (let [object-root (.. (io/file (:path commit)) getParentFile getParent)]
    (map #(read-git-object (io/file object-root (sha-to-object-path %)))
         (:parent commit))))

(defn commit-ancestors [commits]
  (take-while seq (iterate #(mapcat commit-parents %) commits)))

(defn make-commit-map [commit-groups]
  (persistent!
   (reduce (fn [m {:keys [id parent]}]
             (cond-> m
               id (assoc! id {:parents (or parent [])
                              :id id
                              :rootCommit (empty? parent) })
               (empty? parent) (assoc! :root-id id)))
           (transient {})
           (flatten commit-groups))))

(defn build-tree [root]
  (let [git-root (find-git-parent root)
        branches (branch-refs git-root)
        tags (tag-refs git-root)
        branch-names (map (memfn getName) branches)]
    (shell/with-sh-dir root
      (let [branch-heads (into {} (map (juxt identity rev-parse)) branch-names)
            commits (map #(read-git-object git-root %) (vals branch-heads))
            commit-map (make-commit-map (commit-ancestors commits))]
        {:commits (dissoc commit-map :root-id)
         :root-commit (:root-id commit-map)
         :branches (into {} (map (fn [[name target]]
                                   [name {:id name :target target :remoteTrackingBranchID nil}]))
                         branch-heads)
         :tags {}
         :HEAD {:id "HEAD"
                :target (ref-branch "HEAD")}}))))
