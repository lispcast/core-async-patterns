(ns core-async-patterns.core
  (:require [clojure.core.async :as async])
  (:require [clojure.test :refer [deftest is]]))

;; Take with timeout
(defn take-with-timeout!! [c t]
  (first (async/alts!! [c (async/timeout t)] :priority true)))
(defn take-with-timeout! [c t]
  (async/go
    (first (async/alts! [c (async/timeout t)] :priority true))))

;; Put with timeout
(defn put-with-timeout!! [c v t]
  (first (async/alts!! [[c v] (async/timeout t)] :priority true)))
(defn put-with-timeout! [c v t]
  (async/go
    (first (async/alts! [[c v] (async/timeout t)] :priority true))))

;; Go Throw
(defmacro <?! [c]
  `(let [[op# v#] (async/<! c)]
     (case op#
       :return v#
       :throw (throw v#))))
(defn <?!! [c]
  (let [[op v] (async/<!! c)]
    (case op
      :return v
      :throw (throw v))))
(defmacro go-throw [& body]
  `(async/go
     (try
       (let [ret# (do ~@body)]
         [:return ret#])
       (catch Throwable t#
         [:throw t#]))))

;; Thread pool
(def work (async/chan))
(doseq [x 10]
  (async/thread
    (loop []
      (let [v (async/<!! work)]
        (try
          (handle v)
          (catch Throwable t
            (println t))))
      (recur))))

;; Backpressure
(def requests (async/chan))
(def backpressure (async/chan 1000))
(async/go
  (loop []
    (let [r (async/<! requests)
          [v] (async/alts! [[backpressure r]] :default ::not-ready)]
      (when (= v ::not-ready)
        (respond-not-ready r)))
    (recur)))
(async/go
  (doseq [x 10]
    (async/thread
      (loop []
        (let [r (async/<!! backpressure)]
          (handle r)
          (recur))))))

;; Kill switch
(def kill-switch (async/chan))
(async/thread
  (loop []
    (let [[v] (async/alts!! [kill-switch] :default :continue)]
      (when (= v :continue)
        (do-something)
        (recur)))))
(defn kill-thread []
  (async/close! kill-switch))

;; Poison pill
(def work (async/chan))
(async/go
  (loop []
    (let [v (async/<! work)]
      (when (not= :poison v)
        (do-work v)
        (recur)))))
(defn kill-go-block []
  (async/put! work :poison))

;; Start latch
(def latch (async/chan))
(dotimes [x 1000]
  (async/go
    (async/<! latch)
    (do-something)))
(async/close! latch)

;; Token bucket
(def bucket (async/chan (async/dropping-buffer 10)))
(async/go
  (loop []
    (async/put! bucket :token)
    (async/<! (async/timeout 1000))
    (recur)))
(defn api-request [url]
  (async/<!! bucket)
  (get-request url))














(def cache (atom :cached))

(defn request-value []
  (let [c (async/chan 10)]
    (async/go
      (loop []
        (let [v (get-request "http://lispcast.com/")]
          (reset! cache v)
          (async/put! c v))
        (async/<! (async/timeout 10000))
        (recur)))
    (async/put! c @cache)
    c))

(def current (atom :waiting))
(let [c (request-value)]
  (async/go
    (loop []
      (let [v (async/<! c)]
        (reset! current v))
      (recur))))













