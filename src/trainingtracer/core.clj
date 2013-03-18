(ns trainingtracer.core
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.pprint]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clj-time.format]
            [clj-time.coerce]))

;(def tcx-zipper (tcx-file->zipper "dev-resources/2013-03-15-131107-small.TCX"))

;;; Misc
;; (defn zip-str [s]
;;   (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

;;; Units and conversions

(def meters-per-mile 1609.334)

(defn m-per-sec->min-per-mi [meters-per-sec]
  (/ meters-per-mile (* 60 meters-per-sec)))

(defn m->mi [m]
  (/ m meters-per-mile))

;;; TCX parsing

(defn tp-zip->latlng [tp-zip]
  (map #(Double/parseDouble
         (zip-xml/xml1-> tp-zip :Position % zip-xml/text))
       [:LatitudeDegrees :LongitudeDegrees]))

(defn tp-zip->map [lap-zip tp-zip]
  (let [dist-m (Double/parseDouble
                (zip-xml/xml1-> tp-zip :DistanceMeters zip-xml/text))
        speed-m-per-sec (Double/parseDouble
                         (zip-xml/xml1->
                          tp-zip :Extensions :TPX :Speed zip-xml/text))
        pos-zip (zip-xml/xml1-> tp-zip :Position)
        latlng (tp-zip->latlng tp-zip)]
    {:lap-start-ms (clj-time.coerce/to-long
                    (clj-time.format/parse
                     (zip-xml/xml1-> lap-zip (zip-xml/attr :StartTime))))
     :time-ms (clj-time.coerce/to-long
               (clj-time.format/parse
                (zip-xml/xml1-> tp-zip :Time zip-xml/text)))
     :lat (first latlng)
     :lng (second latlng)
     :hr-bpm (Long/parseLong
              (zip-xml/xml1-> tp-zip :HeartRateBpm :Value zip-xml/text))
     :dist-m dist-m
     :dist-mi (m->mi dist-m)
     :speed-m-per-sec speed-m-per-sec
     :pace-min-per-mi (m-per-sec->min-per-mi speed-m-per-sec)}))

(defn tcx->trackpoints [tcx-zip]
  (map tp-zip->map
       (zip-xml/xml-> tcx-zip :Activities :Activity :Lap :Track :Trackpoint)))

(defn tcx->seq [tcx-zip]
  (for [lap-zip (zip-xml/xml-> tcx-zip :Activities :Activity :Lap)
        tp-zip (zip-xml/xml-> lap-zip :Track :Trackpoint)]
    (tp-zip->map lap-zip tp-zip)))

(defn tcx->nested-lap-seq [tcx-zip]
  (for [lap-zip (zip-xml/xml-> tcx-zip :Activities :Activity :Lap)]
    (for [tp-zip (zip-xml/xml-> lap-zip :Track :Trackpoint)]
      (tp-zip->map lap-zip tp-zip))))

;;; File handling

(defn write-pprint-clj-file [out-filename data]
  (with-open [w (io/writer out-filename)]
    (clojure.pprint/pprint data w)))

(defn file->zipper [in-filename]
  (-> in-filename
      io/file
      xml/parse
      zip/xml-zip))

(defn -main [tcx-in-filename clj-out-filename]
  (let [tcx-zipper (file->zipper tcx-in-filename)
        workout (tcx->nested-lap-seq tcx-zipper)]
    (write-pprint-clj-file clj-out-filename workout))
  0)

;; (-main "dev-resources/2013-03-15-131107-small.TCX" "dev-resources/2013-03-15-131107-small.clj")
