(ns dali.batik
  (:require [clojure.java.io :as io])
  (:import [java.nio.charset StandardCharsets]
           [java.io ByteArrayInputStream]
           [java.awt.geom PathIterator]
           [org.apache.batik.transcoder.image PNGTranscoder]
           [org.apache.batik.transcoder
            TranscoderInput TranscoderOutput]
           [org.apache.batik.dom.svg SAXSVGDocumentFactory]
           [org.apache.batik.bridge UserAgentAdapter BridgeContext GVTBuilder]
           [org.apache.batik.bridge.svg12 SVG12BridgeContext]))

;;Batik - calculating bounds of cubic spline
;;http://stackoverflow.com/questions/10610355/batik-calculating-bounds-of-cubic-spline?rq=1

;;Wrong values of bounding box for text element using Batik
;;http://stackoverflow.com/questions/12166280/wrong-values-of-bounding-box-for-text-element-using-batik

(defprotocol BatikContext
  (gvt-node [this dom-node])
  (gvt-node-by-id [this id]))

(defrecord BatikContextRecord [bridge gvt dom]
  BatikContext
  (gvt-node [this dom-node]
    (.getGraphicsNode bridge dom-node))
  (gvt-node-by-id [this id]
    (gvt-node this (.getElementById dom id))))

(defn batik-context [dom & {:keys [dynamic?]}]
  (let [bridge (SVG12BridgeContext. (UserAgentAdapter.))]
    (.setDynamic bridge (or dynamic? true))
    (map->BatikContextRecord
     {:dom dom
      :bridge bridge
      :gvt (.build (GVTBuilder.) bridge dom)})))

(defn- parse-svg-uri [uri]
  (let [factory (SAXSVGDocumentFactory. "org.apache.xerces.parsers.SAXParser")]
    (.createDocument factory uri)))

(defn parse-svg-string [s]
  (let [factory (SAXSVGDocumentFactory. "org.apache.xerces.parsers.SAXParser")]
    (with-open [in (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8))]
      (.createDocument factory "file:///fake.svg" in))))

(defn render-document-to-png [svg-document png]
  (with-open [out-stream (io/output-stream (io/file png))]
    (let [in (TranscoderInput. svg-document)
          out (TranscoderOutput. out-stream)]
      (doto (PNGTranscoder.)
        (.transcode in out)))))

(defn render-uri-to-png [uri png-filename]
  (render-document-to-png (parse-svg-uri uri) png-filename))

(defn to-rect [rect]
  [:rect
   [(.x rect)
    (.y rect)]
   [(.width rect)
    (.height rect)]])

(defn bounds [node]
  (to-rect (.getBounds node)))

(defn sensitive-bounds [node]
  (to-rect (.getSensitiveBounds node)))

(defmacro maybe [call]
  `(try ~call (catch Exception ~'e nil)))

(defn all-bounds [node]
  {:normal (maybe (to-rect (.getBounds node)))
   :geometry (maybe (to-rect (.getGeometryBounds node)))
   :primitive (maybe (to-rect (.getPrimitiveBounds node)))
   :sensitive (maybe (to-rect (.getSensitiveBounds node)))
   :transformed (maybe (to-rect (.getTransformedBounds node)))
   :transformed-geometry (maybe (to-rect (.getTransformedGeometryBounds node)))
   :transformed-primitive (maybe (to-rect (.getTransformedPrimitiveBounds node)))
   :transformed-sensitive (maybe (to-rect (.getTransformedSensitiveBounds node)))})

;;see http://docs.oracle.com/javase/7/docs/api/java/awt/geom/PathIterator.html
(comment
  (let [ctx (batik-context (parse-svg-uri "file:///s:/temp/svg.svg"))
        node (gvt-node-by-id ctx "thick")]
    (-> node .getOutline (.getPathIterator nil))))

(def path-segment-types
  {PathIterator/SEG_MOVETO :move-to
   PathIterator/SEG_LINETO :line-to
   PathIterator/SEG_QUADTO :quad-to
   PathIterator/SEG_CUBICTO :cubic-to
   PathIterator/SEG_CLOSE :close})

(defn- path-seq-step [path-iterator arr]
  (let [type (path-segment-types (.currentSegment path-iterator arr))]
    (.next path-iterator)
    (cons
     [type (into [] arr)]
     (when-not (.isDone path-iterator)
       (lazy-seq (path-seq-step path-iterator arr))))))

(defn path-seq [path]
  (let [it (.getPathIterator path nil)
        arr (double-array 6)]
    (path-seq-step it arr)))

(comment
  (render-uri-to-png "file:///s:/temp/svg.svg" "s:/temp/out.png"))

(comment
  (do
    (require '[dali.svg-translate :as translate])
    (-> [:page {:width 250 :height 250}
         [:circle {:stroke {:paint :black :width 3}
                   :fill :green} [125 125] 75]]
        translate/to-hiccup
        translate/hiccup-to-svg-document-string
        parse-svg-string
        (render-document-to-png "s:/temp/out2.png"))
    ))
