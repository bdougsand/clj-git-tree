(defproject clj-git "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]

                 [aleph "0.4.6"]
                 [org.clojure/core.async "1.2.603"]
                 [compojure "1.6.1"]

                 [io.methvin/directory-watcher "0.9.10"]
                 [juxt/dirwatch "0.2.5"]]
  :repl-options {:init-ns clj-git.core}
  :main clj-git.core
  :aot :all)
