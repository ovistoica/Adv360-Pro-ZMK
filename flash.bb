#!/usr/bin/env bb

(ns flash
  (:require [babashka.process :refer [shell]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def firmware-dir "firmware")
(def device "/dev/sda")
(def mount-point "/run/media/ovistoica/ADV360PRO")

(defn find-latest-firmware [side]
  (let [pattern (re-pattern (str "-" side "-clique\\.uf2$"))
        files (->> (fs/list-dir firmware-dir)
                   (map str)
                   (filter #(re-find pattern %))
                   (sort-by #(.toMillis (fs/last-modified-time %)) >))]
    (first files)))

(defn wait-for-device []
  (println "Waiting for keyboard to appear as USB device...")
  (loop [attempts 0]
    (if (fs/exists? device)
      (do
        (Thread/sleep 500) ; Give it a moment to settle
        true)
      (if (< attempts 60)
        (do
          (Thread/sleep 500)
          (recur (inc attempts)))
        (do
          (println "Timeout waiting for device")
          false)))))

(defn mount-device []
  (println "Mounting device...")
  (shell "udisksctl" "mount" "-b" device)
  (Thread/sleep 500))

(defn unmount-device []
  (println "Unmounting device...")
  (try
    (shell "udisksctl" "unmount" "-b" device)
    (catch Exception _
      (println "Device already unmounted or not mounted"))))

(defn copy-firmware [firmware-path]
  (println (str "Copying " firmware-path " to " mount-point))
  (fs/copy firmware-path mount-point {:replace-existing true})
  (println "Firmware copied successfully"))

(defn flash-side [side]
  (let [firmware (find-latest-firmware side)]
    (if firmware
      (do
        (println (str "\nFound " side " firmware: " firmware))
        (println (str "\n>>> Put the " (str/upper-case side) " keyboard in bootloader mode (press Mod + bootloader key)"))
        (println "Press Enter when ready...")
        (read-line)
        (when (wait-for-device)
          (mount-device)
          (copy-firmware firmware)
          (Thread/sleep 1000)
          (unmount-device)
          (println (str (str/upper-case side) " side flashed successfully!"))))
      (println (str "ERROR: No " side " firmware found in " firmware-dir)))))

(defn build-firmware []
  (println "Building firmware...")
  (println "Running 'make all'...")
  (shell "make" "all")
  (println "Build complete!\n"))

(defn main []
  (println "=== Adv360 Pro Firmware Flasher ===\n")

  ;; Build firmware
  (build-firmware)

  ;; Flash left side
  (flash-side "left")

  ;; Flash right side
  (flash-side "right")

  (println "\n=== Flashing complete! ==="))

(main)
