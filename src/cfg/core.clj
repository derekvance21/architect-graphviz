(ns cfg.core
  (:require
   [clojure.tools.cli :as cli]
   [ubergraph.core :as uber]
   [ubergraph.alg :as alg]
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
      (update :line #(Integer/parseInt %))))


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
   (str/split-lines s)))


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
    (remove :comment))
   (str/split-lines s)))


(defn xml-escape
  [s]
  (when s
    (str/escape s {\& "&amp;"
                   \> "&gt;"
                   \< "&lt;"
                   \" "&quot;"
                   \' "&apos;"}))
  )


(def start-node 0)

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
                        (get ((get label-map label next) source) :line))
        detail-node (fn detail-node
                      ([detail]
                       (detail-node detail (:line detail)))
                      ([{:keys [label line type action]} node-id]
                       (let [node-label (str "<FONT>"
                                             (str/join "<br />"
                                                       [(str \[ line \] (when label \space) (xml-escape label))
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
                                     (contains? #{"Call"} type) (assoc :border 2 :peripheries 2)
                                     (contains? #{"Compare"} type) (assoc :style "dashed")
                                     (contains? #{"Return"} type) (assoc :style "rounded")
                                     (contains? #{"Call" "Calculate" "Database" "Compare"} type)
                                     (assoc :href
                                            (if (= type "Call")
                                              ;; TODO - this is so bad. need to fix this. Shouldn't be constrained to ".html", right?
                                              (str "../ProcessObject/" (xml-escape (name->href action)) ".html")
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
                fail-line (detail-target line fail)
                node (detail-node detail)
                edges (when (not= type "Return")
                        (cond-> []
                          pass-line (conj [line pass-line {:type :pass}])
                          fail-line (conj [line fail-line {:type :fail}])))]
            (into [node] edges)))]
    (into
     [[start-node {:label ""
                   :shape :doublecircle
                   :rank :min ;; the start node should always be at the top anyways, but this is how rank could be used
                   }]
      [start-node (:line (next 0))]]
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
   ["-h" "--help"]])


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


(defn color-edges
  [graph]
  (reduce
   (fn [g e]
     (if-let [color (get {:pass :green :fail :red} (uber/attr g e :type))]
       (uber/add-attr g e :color color)
       g))
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


(defn short-circuit-sinks
  [graph]
  (let [sink-nodes (filterv
                    (fn [n]
                      (and (zero? (uber/out-degree graph n))
                           #_(> (uber/in-degree graph n) 3)))
                    (uber/nodes graph))]
    (-> (reduce
         (fn [g [[src dest] edges]]
           (let [new-sink (str src \- dest)]
             (-> g
                 (uber/add-nodes-with-attrs [new-sink (uber/attrs graph dest)])
                 (uber/add-edges*
                  (map (fn [e] [src new-sink (uber/attrs g e)]) edges)))))
         graph
         (group-by (juxt uber/src uber/dest) (mapcat #(uber/in-edges graph %) sink-nodes)))
        (uber/remove-nodes* sink-nodes))))


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
         (uber/nodes graph))))


(defn merge-edges
  [graph]
  (reduce
   (fn [g n]
     (let [out-edges (uber/out-edges g n)]
       (if-let [[e & es] (seq out-edges)]
         (if (seq es) ;; more than one out-edge
           (reduce
            (fn [g' [dest [e & es]]]
              (if (seq es) ;; if there's more than one edge going to dest
                (-> g'
                    (uber/remove-attr e :type) ;; becomes a "type"-less edge
                    (uber/remove-edges* es))
                g'))
            g
            (group-by uber/dest out-edges))
           (uber/remove-attr g e :type) ;; only one out-edge, so "type"-less edge
           )
         g ;; no out-edges, so nothing to do
         )))
   graph
   (uber/nodes graph)))


(defn remove-unreachable
  [graph]
  (let [reachable (set (alg/post-traverse graph start-node))]
    (uber/remove-nodes*
     graph
     (into []
           (remove #(contains? reachable %))
           (uber/nodes graph)))))


(defn find-blocks
  [graph leader? follower?]
  (into []
        (comp
         (filter #(leader? graph %))
         (map (fn [l]
                (into []
                      (alg/bf-traverse
                       graph
                       l
                       :when (fn [neighbor predecessor depth]
                               (follower? graph neighbor predecessor depth)))))))
        (uber/nodes graph)))


(defn merge-nodes
  ([attrs]
   (fn [graph nodes]
     (merge-nodes graph nodes attrs)))
  ([graph nodes]
   (merge-nodes graph nodes {:shape :none}))
  ([graph nodes attrs]
   (if (next nodes)
     (let [new (str/join \, nodes)
           leader (first nodes)
           entries (into [] (map #(uber/edge-with-attrs graph %)) (uber/in-edges graph leader))
           exits (into []
                       (comp
                        (mapcat #(uber/out-edges graph %))
                        (remove #(contains? (set nodes) (uber/dest %))) ;; remove edges that are pointing to within the block
                        (map #(uber/edge-with-attrs graph %)))
                       nodes)]
       (-> graph
           (uber/add-nodes-with-attrs [new (-> (uber/attrs graph leader)
                                               (assoc ;; :label label
                                                :block (mapv #(uber/node-with-attrs graph %) nodes))
                                               (merge attrs))])
           (uber/add-edges*
            (mapv (fn [[src dest attrs]]
                    [src new (assoc attrs
                                    :headport dest)])
                  entries))
           (uber/add-edges*
            (mapv (fn [[src dest attrs]]
                    [new dest (assoc attrs
                                     :tailport src
                                   ;; :minlen 0
                                     )])
                  exits))
           (uber/remove-nodes* nodes)))
     graph)))


(defn compare-fail-blocks
  [graph]
  (let [leader? (fn [g n]
                  (and (= "Compare" (uber/attr g n :type))
                       (some (fn [e]
                               (or (not= "Compare" (uber/attr g (uber/src e) :type)) ;; entry is from a non-compare
                                   (= :pass (uber/attr g e :type))) ;; entry is a pass edge
                               )
                             (uber/in-edges g n))))
        follower? (fn [g n predecessor depth]
                    (and (= "Compare" (uber/attr g n :type))
                         (= (uber/in-degree g n) 1)
                         (= :fail (uber/attr g (uber/find-edge g predecessor n) :type))))]
    (find-blocks graph leader? follower?)))


(defn merge-compare-fail-blocks
  [graph]
  (reduce merge-nodes graph (compare-fail-blocks graph)))


;; have to exclude blocks and returns and Call Dialog / Universal Quantity
(defn basic-blocks
  [graph]
  (let [leader? (fn [g n]
                  (let [[e & es] (uber/in-edges g n)]
                    (or (nil? e) ;; no in-edges
                        (seq es) ;; more than one in-edge
                        (#{:pass :fail} (uber/attr g e :type)) ;; conditional in-edge
                        )))
        follower? (fn [g n predecessor depth]
                    (not (leader? g n)))]
    (find-blocks (uber/remove-nodes graph start-node) leader? follower?)))


(defn merge-basic-blocks
  [graph]
  (reduce
   (merge-nodes {:shape :none
                 :border 0
                 :peripheries 0})
   graph
   (basic-blocks graph)))


(defn color-fail-to-fail
  [graph]
  (transduce
   (comp (filter (fn [node]
                   (let [{:keys [type action]} (uber/attrs graph node)]
                     (and (= type "Return") (= action "FAIL")))))
         (mapcat #(uber/in-edges graph %))
         (filter #(= :fail (uber/attr graph % :type))))
   (completing
    (fn [g e]
      (-> g
          (uber/add-attr (uber/src e) :fillcolor "#ffc2c2")
          (uber/remove-edges e))))
   graph
   (uber/nodes graph)))


(defn label-blocks
  [graph]
  (transduce
   (keep (fn [node]
           (when-let [block (uber/attr graph node :block)]
             (let [border (uber/attr graph node :border)
                   label (str "<TABLE"
                              " BORDER=\"" (or border 0) "\""
                              " CELLBORDER=\"1\""
                                           ;; " CELLSPACING=\"4\"" ;; default is 2
                              ">"
                              (->> block
                                   (mapv
                                    (fn [[n {:keys [label href style border fillcolor target]}]]
                                      (str "<TR><TD"
                                           " PORT=\"" n "\""
                                           (when href
                                             (str " HREF=\"" href "\""))
                                           (when target
                                             (str " TARGET=\"" target "\""))
                                           (when style
                                             (str " STYLE=\"" style "\""))
                                           (when border
                                             (str " BORDER=\"" border "\""))
                                           (when fillcolor
                                             (str " BGCOLOR=\"" (name fillcolor) "\""))
                                           ">"
                                           label
                                           "</TD></TR>")))
                                   (str/join))
                              "</TABLE>")]
               [node {:label label}]))))
   (fn
     ([g] g)
     ([g [n attrs]]
      (uber/add-attrs g n attrs)))
   graph
   (uber/nodes graph)))


;; TODO - remove Goto nodes
(defn transform-graph
  [graph]
  (-> graph
      (remove-calculate-fail-edges)
      (color-fail-to-fail)
      (merge-edges)
      (remove-unreachable)
      (short-circuit-returns) ;; make this short-circuit-sink-paths
      (merge-basic-blocks)
      (merge-compare-fail-blocks)
      (label-blocks)
      (color-edges)))


(comment

  (def receipt-from-production "/home/dvance/HLF-3-14/WA/BusinessObject/Receipt from Production.arch")
  (def dialog "/home/dvance/HLF-3-14/WA/ProcessObject/Dialog.arch")
  (def dialog-confirm "/home/dvance/HLF-3-14/WA/ProcessObject/Dialog - Confirm.arch")
  (def loading "/home/dvance/HLF-3-14/WA/BusinessObject/Loading HLF.arch")
  (def bulk-pick-by-load "/home/dvance/HLF-3-14/WA/BusinessObject/Bulk Pick by Load.arch")
  (def bulk-pick-by-wo "/home/dvance/HLF-3-14/WA/BusinessObject/Bulk Pick by WO.arch")
  (def find-work-assign "/home/dvance/HLF-3-14/WA/ProcessObject/Find Work & Assign.arch")
  (def weird-one "/home/dvance/HLF-3-14/WA/ProcessObject/~Label Count~ label(s) sent to ~Printer ID~.arch")
  (def hanging "/home/dvance/HLF-3-14/WA/ProcessObject/CC - Location Skip.arch")

  
  (-> bulk-pick-by-wo
      (slurp)
      (source-details)
      (graph-inits)
      (multidigraph)
      ;;
      ;; (remove-calculate-fail-edges)
      ;; (color-fail-to-fail)
      ;; (merge-edges)
      ;; (remove-unreachable)
      ;; (short-circuit-returns)
      ;; (merge-basic-blocks)
      ;; (merge-compare-fail-blocks)
      ;; (label-blocks)
      ;; (color-edges)
      ;;
      
      (transform-graph)
      #_(as-> g
              (reduce
               #(uber/add-attr %1 %2 :constraint :false)
               g
               (uber/edges g)))
      (uber/viz-graph

      ;;  {:save {:format :dot :filename "find-work-assign.dot"}}
       ))

  )


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
