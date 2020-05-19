(ns clj-git.subprocess
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [go-loop <! >!] :as async])
  (:import [java.io OutputStreamWriter]))

(defn- parse-args
  [args]
  (let [default-opts {:out "UTF-8"}
        [cmd opts] (split-with string? args)]
    [cmd (merge default-opts (apply hash-map opts))]))

(defn sh
  [& args]
  (let [[cmd opts] (parse-args args)
        proc (.exec (Runtime/getRuntime) 
                    (into-array cmd) 
                    nil
                    (io/as-file (:dir opts)))]
    (if (:in opts)
      (with-open [osw (OutputStreamWriter. (.getOutputStream proc))]
        (.write osw (:in opts)))
      (.close (.getOutputStream proc)))
    proc))

(defn timebox
  "When a message is received on `source`, group it with messages that arrive
  within the subsequent `ms` milliseconds, then put the group as a vector of
  messages onto `sink`."
  [source sink ms]
  (go-loop []
    (if-let [next (<! source)] ;; exit when the source is closed
      (let [done (async/timeout ms)
            gather (async/chan)]
        (if (loop [batch [next]]
              (async/alt!
                done ([_] (>! sink batch))

                source ([v] (recur (conj batch v)))))
          ;; Loop returns false if the sink is closed
          (recur)

          :sink-closed))

      :source-closed))
  sink)

(defn std-out-stream
  ([proc onclose]
   (let [ch (async/chan)]
     (async/thread
       (with-open [stdout (io/reader (.getInputStream proc))]
         (loop []
           (when-let [l (.readLine stdout)]
             (println l)
             (if (>! ch l)
               (recur)

               (do
                 (println "gonna close")
                 (and onclose (onclose))))))))
     ch))
  ([proc]
   (std-out-stream proc #(.destroy proc))))

(defn watch-dir [dir]
  (let [proc (sh "fswatch" dir)
        stdout (std-out-stream proc)]
    (timebox stdout (async/chan) 500)))

#_(def ^:dynamic mych (watch-dir "/Users/brian/Desktop/gittest/.git"))
