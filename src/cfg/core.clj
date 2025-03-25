(ns cfg.core
  (:require
   [clojure.tools.cli :as cli]
   [ubergraph.core :as uber]
   [clojure.string :as str]
   [clojure.java.io :as io])
  (:gen-class))


(defn diff-columns
  [line]
  (-> (zipmap
       [:blank :line :label :type :action :pass :fail]
       (map str/trim (str/split line #"\|")))
      (->> (remove (comp empty? val))
           (into {}))
      (update :line #(Integer/parseInt %)))
  )


(defn diff-details
  [s]
  (into
   []
   (comp
    (drop-while #(not (re-find #"^-+$" %)))
    (drop 1)
    (remove empty?)
    (map diff-columns)
    (remove #(when-let [label (:label %)]
               (re-find #"--" label))))
   (str/split-lines s))
  )


(defn source-columns
  [line]
  (into {}
        (remove (comp empty? val))
        (zipmap
         [:comment :label :pass :fail :type :action]
         (rest (mapv str/trim (str/split line #"\|"))))))


(defn source-details
  [s]
  (into
   []
   (comp
    (drop-while #(not (re-find #"\|----\+" %)))
    (drop 1)
    (drop-while #(not (re-find #"\|----\+" %)))
    (drop 1)
    (map source-columns)
    (map-indexed (fn [i m]
                   (assoc m :line (inc i))))
    (remove :comment)
    )
   (str/split-lines s)))


(defn xml-escape
  [s]
  (str/escape s {\& "&amp;"
                 \> "&gt;"
                 \< "&lt;"
                 \" "&quot;"
                 \' "&apos;"}))


(defn name->href
  [name]
  (let [char-map ;; from wikipedia: https://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
        {\/ "\u29f8"
         \\ "\u29f9"
         \? "\u0294"
         \* "\u2217"
         \: "\u2236"
         \< "\u02c2"
         \> "\u02C3"
         \" "\u201C"
         \| "\u2223"}
        ;; from stack exchange: https://lifehacks.stackexchange.com/questions/24681/hack-to-indicate-a-forward-slash-or-backslash-in-a-filename-when-those-are-reser
        #_{"/" (str \u2215) ;; this one's kind of better than big slash, but then i'd have to change architect-source-code code
           "\\" (str \u244a)}]
    (str/escape name char-map)))


(defn graph-inits
  [ds]
  (let [next (fn [line] (first (filter #(> (:line %) line) ds)))
        previous (fn [line] (last (filter #(< (:line %) line) ds)))
        label-map (into {"NEXT" next
                         "PREVIOUS" previous}
                        (comp (filter :label)
                              (map (juxt :label constantly)))
                        ds)
        detail-target (fn [source label]
                        (:line ((get label-map label next) source)))
        detail-node (fn detail-node
                      ([detail]
                       (detail-node detail (:line detail)))
                      ([{:keys [label line type action]} node-id]
                       (let [node-label (str "<FONT>"
                                             (str/join "<br />"
                                                       [(str \[ line \] (when label \space) label)
                                                        type
                                                        (xml-escape action)])
                                             "</FONT>")
                             attrs (cond-> {:id node-id
                                            :type type
                                            :action action
                                            :shape (get {"Call" (if (contains? #{"Dialog" "Universal Quantity"} action)
                                                                  :parallelogram
                                                                  :box #_:box3d)
                                                         "Calculate" :box
                                                         "Goto" :box
                                                         "Compare" :box #_:hexagon #_:diamond
                                                         "Database" :cylinder
                                                         "Return" :box}
                                                        type
                                                        :note)
                                            :label node-label
                                            :fontsize "10"}
                                     (contains? #{"Call"} type) (assoc :peripheries 2)
                                     (contains? #{"Compare"} type) (assoc :style "dashed")
                                     (contains? #{"Return"} type) (assoc :style "rounded")
                                     (contains? #{"Call" "Calculate" "Database" "Compare"} type)
                                     (assoc :href
                                            (if (= type "Call")
                                              (str "../ProcessObject/" (xml-escape (name->href action)) ".arch.svg")
                                              (str "../" type "/" (xml-escape (name->href action)) ".arch")))
                                     (= type "Return") (assoc
                                                        :style :filled
                                                        :fillcolor (case action
                                                                     "PASS" :green
                                                                     "FAIL" :red)))]
                         [node-id attrs])))
        detail-nodes-and-edges
        (fn [{:keys [line type pass fail] :as detail}]
          (let [pass-line (detail-target line pass)
                fail-line (detail-target line fail)]
            (into
             [(detail-node detail)]
             (when (not= type "Return") ;; Return nodes don't have out-edges
               [[line pass-line {:type :pass}]
                [line fail-line {:type :fail}]]))))]
    (into
     [[0 {:label ""
          :shape :doublecircle
          ;; :rank :min ;; the start node should always be at the top anyways, but this is how rank could be used
          ;; :fillcolor :black
          ;; :fontcolor :black
          }]
      [0 (:line (next 0))]]
     (mapcat detail-nodes-and-edges)
     ds)))


(defn diff-name
  [diff]
  (re-find #"(?<=Name: ).+" diff))


(defn source-name
 [s]
  (first (str/split-lines s)))


(defn multidigraph
  [inits]
  (apply uber/multidigraph inits))


(def graphviz-output-formats
  #{:dot
    :jpeg
    :jpg
    :json
    :pdf
    :plain
    :png
    :svg
    :x11})


(def cli-options
  [["-d" "--directory DIRECTORY" "Directory to output into"
    :validate [(fn [dir]
                 (.isDirectory (io/file dir)))]]
   ["-T" "--output-type TYPE" "Graphviz output format"
    :parse-fn keyword
    :validate [graphviz-output-formats]]
   ["-I" "--input-type TYPE" "Source input format"
    :default :source
    :parse-fn keyword
    :validate [#{:diff :source}]]
   ["-o" "--output FILE" "Output file"]
   ["-i" "--inplace" "In-place output relative to input FILE"]
   ["-X" "--extension EXT" "Output file extension type (requires -i)"]
   ["-h" "--help"]
   ])


(defn usage
  [options-summary]
  (str/join
   \newline
   ["Usage: clj -M -m cfg.core [options] [FILE] ..."
    ""
    "Options:"
    options-summary]))


(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary) :ok? true}
      errors {:exit-message (str/join \newline errors)}
      :else {:arguments arguments :options options})))


(defn exit
  [status msg]
  (println msg)
  (System/exit status))


(defn strip-extension
  [p]
  (let [components (str/split p #"\.")]
    (if-let [but-last (butlast components)]
      (str/join \. but-last)
      p)))


(comment
  
  (as-> (slurp "/home/dvance/HLF-3-14/WA/BusinessObject/Receipt from Production.arch") x
      (source-details x)
      (graph-inits x)
      (multidigraph x)
      (transform-graph x)
    
    (uber/viz-graph x)
      )
  
  
  )


(defn color-edges
  [graph]
  (reduce
   (fn [g e]
     (uber/add-attr g e :color (get {:pass :green :fail :red} (uber/attr g e :type))))
   graph
   (uber/edges graph)))


(defn short-circuit-returns
  [graph]
  (let [return-nodes (filterv
                      (fn [n]
                        (= "Return" (uber/attr graph n :type)))
                      (uber/nodes graph))]
    (-> (reduce
         (fn [g return-node]
           (let [return-attrs (uber/attrs g return-node)]
             (reduce (fn [g return-edge]
                       (let [[src dest edge-attrs] (uber/edge-with-attrs g return-edge)
                             return-id (str src \- dest)]
                         (uber/build-graph g
                                           [return-id return-attrs]
                                           [src return-id edge-attrs])))
                     g
                     (uber/in-edges g return-node))))
         graph
         return-nodes)
        (uber/remove-nodes* return-nodes))))


(defn remove-calculate-fail-edges
  [graph]
  (uber/remove-edges*
   graph
   (into []
         (comp
          (filter
           (fn [n]
             (= "Calculate" (uber/attr graph n :type))))
          (mapcat
           (fn [n]
             (uber/out-edges graph n)))
          (filter
           (fn [e]
             (= :fail (uber/attr graph e :type)))))
         (uber/nodes graph)))
  )


(defn merge-edges
  [graph]
  ;; TODO
graph

  )


(defn transform-graph
  [graph]
  (-> graph
      (short-circuit-returns)
      (remove-calculate-fail-edges)
      (merge-edges)
      (color-edges)
      
      ))


(defn -main
  [& args]
  (let [{:keys [exit-message ok? options arguments]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [{:keys [extension input-type output directory inplace output-type]} options]
        (doseq [file arguments]
          (let [text (slurp file)
                [title details] (if (= input-type :diff)
                                  [(diff-name text) (diff-details text)]
                                  [(source-name text) (source-details text)])
                graph (multidigraph (graph-inits details))
                save (when (or output directory inplace) ;; save to file
                       (let [format (or output-type :svg)
                             filename (or output
                                          (let [f (io/file file)
                                                parent (or directory (.getParent f))
                                                child (str (strip-extension (.getName f))
                                                           \.
                                                           (or extension (name format)))]
                                            (str (io/file parent child))))]
                         {:save {:filename filename
                                 :format format}}))]
            (uber/viz-graph
             (transform-graph graph)
             (merge
              {:layout :dot
               :label title
               :labelloc "t"}
              save))))))))
