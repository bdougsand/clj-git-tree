(ns clj-git.server
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.core.async :as a]

            [compojure.core :as compojure :refer [GET]]
            [ring.middleware.params :as params]
            [compojure.route :as route]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]

            [clj-git.tree :as t])
  (:import [java.nio.file Path Paths]
           [io.methvin.watcher DirectoryWatcher DirectoryChangeListener]))

(defn to-path [p]
  (cond
    (string? p) (Paths/get p (make-array String 0))

    (instance? Path p) p))

(defn make-watcher [dirpath callback]
  (-> (DirectoryWatcher/builder)
      (.path (to-path dirpath))
      (.listener (reify DirectoryChangeListener
                   (onEvent [this event]
                     (callback event))))
      (.build)
      (.watchAsync)))

(def repo-root "/Users/brian/Desktop/gittest")
(def shared-mult
  (delay
    (let [change-ch (a/chan (a/sliding-buffer 5)
                            (map (fn [_]
                                   (json/json-str (t/build-tree repo-root)))))]
      (make-watcher repo-root #(a/put! change-ch %))
      (a/mult change-ch))))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn update-handler [req]
  (->
    (d/let-flow [socket (http/websocket-connection req)]
                (let [tap (a/tap @shared-mult (a/chan))]
                  (s/put! socket (json/json-str (t/build-tree repo-root)))
                  (s/connect tap socket)))
    (d/catch
      (fn [_]
        non-websocket-request))))

(def handler
  (params/wrap-params
    (compojure/routes
      (GET "/echo" [] update-handler)
      (route/not-found "No such page."))))

(def s (http/start-server handler {:port 10000}))
