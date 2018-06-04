(ns crux.rocksdb
  (:require [clojure.java.io :as io]
            [crux.kv-store :refer :all])
  (:import java.io.Closeable
           clojure.lang.MapEntry
           [org.rocksdb Checkpoint Options ReadOptions RocksDB RocksIterator WriteBatch WriteOptions]))

(defn- iterator->kv [^RocksIterator i]
  (when (.isValid i)
    (MapEntry. (.key i) (.value i))))

(defn- ^Closeable rocks-iterator [^RocksDB db ^ReadOptions read-options]
  (let [i (.newIterator db read-options)]
    (reify
      KvIterator
      (-seek [this k]
        (.seek i k)
        (iterator->kv i))
      (-next [this]
        (.next i)
        (iterator->kv i))
      Closeable
      (close [this]
        (.close i)))))

(defrecord RocksKv [db-dir]
  KvStore
  (open [this]
    (RocksDB/loadLibrary)
    (let [opts (doto (Options.)
                 (.setCreateIfMissing true))
          db (try
               (RocksDB/open opts (.getAbsolutePath (doto (io/file db-dir)
                                                      (.mkdirs))))
               (catch Throwable t
                 (.close opts)
                 (throw t)))]
      (assoc this :db db :options opts :write-options (doto (WriteOptions.)
                                                        (.setDisableWAL true)))))

  (new-snapshot [{:keys [^RocksDB db]} ]
    (let [snapshot (.getSnapshot db)
          read-options (doto (ReadOptions.)
                         (.setSnapshot snapshot))]
      (reify
        KvSnapshot
        (iterate-with [this f]
          (try
            (with-open [i (rocks-iterator db read-options)]
              (f i))
            ;; TODO: This will disappear once iterate-with becomes
            ;; new-iterator, done to ensure resources are closed for
            ;; now.
            (finally
              (.close this))))

        Closeable
        (close [_]
          (.releaseSnapshot db snapshot)
          (.close read-options)))))

  (store [{:keys [^RocksDB db ^WriteOptions write-options]} kvs]
    (with-open [wb (WriteBatch.)]
      (doseq [[k v] kvs]
        (.put wb k v))
      (.write db write-options wb)))

  (delete [{:keys [^RocksDB db ^WriteOptions write-options]} ks]
    (with-open [wb (WriteBatch.)]
      (doseq [k ks]
        (.delete wb k))
      (.write db write-options wb)))

  (backup [{:keys [^RocksDB db]} dir]
    (.createCheckpoint (Checkpoint/create db) (.getAbsolutePath (io/file dir))))

  Closeable
  (close [{:keys [^RocksDB db ^Options options ^WriteOptions write-options]}]
    (.close db)
    (.close options)
    (.close write-options)))
