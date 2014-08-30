(ns cljx.dali.layout
  (:require [clojure.walk :as walk]
            [retrograde :as retro]
            [dali.syntax :as s]
            [dali.batik :as batik]
            [dali.geom :as geom :refer [v+ v- v-half]]))

(def anchors #{:top-left :top :top-right :left :right :bottom-left :bottom :bottom-right :center})

(defn bounds->anchor-point
  [anchor [_ [x y] [w h]]]
  (condp = anchor
    :top-left     [x y]
    :top          [(+ x (/ w 2)) y]
    :top-right    [(+ x w) y]
    :left         [x (+ y (/ h 2))]
    :right        [(+ x w) (+ y (/ h 2))]
    :bottom-left  [x (+ y h)]
    :bottom       [(+ x (/ w 2)) (+ y h)]
    :bottom-right [(+ x w) (+ y h)]
    :center       [(+ x (/ w 2)) (+ y (/ h 2))]))

(defn- replace-blanks [element replacement]
  (walk/postwalk (fn [f] (if (= f :_) replacement f)) element))

(defn place-top-left
  "Adds a translation transform to an element so that its top-left
  corner is at the passed position."
  [element top-left bounds]
  (let [type (first element)
        [_ current-pos [w h]] bounds]
    (s/add-transform element [:translate (v- top-left current-pos)])))

(defn place-by-anchor
  [element anchor position bounds]
  (let [[_ original-position] bounds
        anchor-point (bounds->anchor-point anchor bounds)]
    (place-top-left
     element
     (v- position (v- anchor-point original-position))
     bounds)))

(defn stack [ctx {:keys [position direction anchor gap] :as params} & elements]
  (let [gap (or gap 0)
        position (or position [0 0])
        direction (or direction :down)
        anchor (or anchor ({:down :top
                            :up :bottom
                            :right :left
                            :left :right}
                           direction))
        elements (if (seq? (first elements)) (first elements) elements)
        vertical? (or (= direction :down) (= direction :up))
        [x y] position
        elements (map #(replace-blanks % [0 0]) elements)
        advance-pos (if (or (= direction :down) (= direction :right)) + -)
        get-size (if vertical?
                   (fn get-size [[_ _ [_ h]]] h)
                   (fn get-size [[_ _ [w _]]] w))
        get-pos (if vertical?
                   (fn get-pos [[_ [_ y] _]] y)
                   (fn get-pos [[_ [x _] _]] x))
        place-point (if vertical?
                      (fn place-point [x y pos] [x pos])
                      (fn place-point [x y pos] [pos y]))
        initial-pos (if vertical? y x)]
    (into [:g]
     (retro/transform
      [this-gap 0 gap
       bounds nil (batik/rehearse-bounds ctx element)
       size 0 (get-size bounds)
       pos 0 (get-pos bounds)
       this-pos initial-pos (advance-pos this-pos' size' this-gap')
       element (place-by-anchor element anchor (place-point x y this-pos) bounds)]
      elements))))

(comment
  (distribute
   ctx
   {:position [10 10] :direction :qright}
   [:circle :_ 10]
   [:circle :_ 20]
   [:circle :_ 50]))

(comment
  (distribute
   ctx
   {:position [10 10] :direction :right}
   (take
    20
    (cycle
     [[:circle :_ 10]
      [:rect :_ [10 10]]]))))

(comment
  (distribute
   ctx
   {:position [10 10] :direction :left}
   (interleave
    [:circle :_ 10]
    (repeat [:rect :_ [10 10]]))))

(comment
  (distribute
   ctx
   {:position [10 10] :anchor :bottom-center}
   (map (fn [x] [:rect :_ [10 x]])
        [50 60 34 22 55 10 12 19])))

(comment
  (def ctx (batik/batik-context (batik/parse-svg-uri "file:///s:/temp/svg.svg") :dynamic? true))

  (defn marker [pos] [:circle pos 3])
  
  (defn anchor-box [ctx pos anchor]
    [:g
     (marker pos)
     (let [box [:rect [-100 -100] [25 25]]]
       (place-by-anchor box anchor pos
                        (batik/rehearse-bounds ctx box)))])

  (defn anchor-circle [ctx pos anchor]
    [:g
     (marker pos)
     (let [c [:circle [-100 -100] 12.5]]
       (place-by-anchor c anchor pos
                        (batik/rehearse-bounds ctx c)))])

  (defn make-stack [ctx pos anchor direction]
    [:g
     (marker pos)
     (stack
      ctx
      {:position pos :gap 5 :anchor anchor :direction direction}
      [:rect :_ [10 100]]
      [:circle :_ 15]
      [:rect :_ [20 20]]
      [:rect :_ [10 30]]
      [:rect :_ [10 5]]
      [:rect :_ [10 5]])])
  
  (s/spit-svg
   (s/dali->hiccup
    [:page
     {:height 750 :width 500, :stroke {:paint :black :width 1} :fill :none}

     ;;test that top-left works
     [:rect [300 50] [100 100]]
     (place-top-left
      [:circle [-100 40] 50]
      [300 50]
      (batik/rehearse-bounds ctx [:circle [-100 40] 50]))

     ;;test that top-left works
     (marker [250 200])
     (place-top-left
      [:rect [-100 -100] [25 25]]
      [250 200]
      (batik/rehearse-bounds ctx [:rect [-100 -100] [50 50]]))

     ;;test all cases of place-by-anchor
     (anchor-box ctx [300 200] :top-left)
     (anchor-box ctx [350 200] :top)
     (anchor-box ctx [400 200] :top-right)
     (anchor-box ctx [300 250] :left)
     (anchor-box ctx [350 250] :center)
     (anchor-box ctx [400 250] :right)
     (anchor-box ctx [300 300] :bottom-left)
     (anchor-box ctx [350 300] :bottom)
     (anchor-box ctx [400 300] :bottom-right)

     (anchor-circle ctx [300 325] :top-left)
     (anchor-circle ctx [350 325] :top)
     (anchor-circle ctx [400 325] :top-right)
     (anchor-circle ctx [300 375] :left)
     (anchor-circle ctx [350 375] :center)
     (anchor-circle ctx [400 375] :right)
     (anchor-circle ctx [300 425] :bottom-left)
     (anchor-circle ctx [350 425] :bottom)
     (anchor-circle ctx [400 425] :bottom-right)

     (make-stack ctx [50 50] :top-left :down)
     (make-stack ctx [120 50] :top :down)
     (make-stack ctx [190 50] :top-right :down)

     (make-stack ctx [50 500] :bottom-left :up)
     (make-stack ctx [120 500] :bottom :up)
     (make-stack ctx [190 500] :bottom-right :up)

     (make-stack ctx [50 575] :left :right)

     (make-stack ctx [165 650] :right :left)

     (stack
      ctx
      {:position [300 500], :direction :right, :anchor :bottom-left, :gap 0.5}
      (map (fn [h] [:rect {:stroke :none, :fill :gray} :_ [10 h]])
           (take 10 (repeatedly #(rand 50)))))

     (stack
      ctx
      {:position [300 525], :direction :right, :gap 3}
      (take
       10
       (repeat
        (stack ctx {:gap 3}
               [:rect :_ [5 5]]
               [:circle :_ 5]
               [:circle :_ 1.5]))))

     (stack
      ctx
      {:position [300 500], :direction :right, :anchor :bottom-left, :gap 0.5}
      (map (fn [h] [:rect {:stroke :none, :fill :gray} :_ [10 h]])
           (take 10 (repeatedly #(rand 50)))))

     (marker [300 550])
     (let [tt [:text {:x 30 :y 30 :stroke :none :fill :black :font-family "Georgia" :font-size 40} "This is it"]
           bb (batik/rehearse-bounds ctx tt)]
       (place-by-anchor tt :top-left [300 550] bb))     

     (stack
      ctx
      {:position [300 650], :direction :right, :anchor :bottom-left, :gap 1}
      (map (fn [h]
             (stack
              ctx
              {:direction :up, :gap 5}
              [:text {:x 30 :y 30 :stroke :none :fill :black :font-family "Verdana" :font-size 6} (format "%.1f" h)]
              [:rect {:stroke :none, :fill :gray} :_ [20 h]]))
           (take 5 (repeatedly #(rand 50)))))])
   
   "s:/temp/svg_stack1.svg")
  )