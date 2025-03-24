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
                        (map (juxt :label constantly)
                             (filter :label ds)))
        detail-target (fn [detail label]
                        ((get label-map label next) (:line detail)))
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
                                            :fontsize "10"
                                            }
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
        src->dest-inits (fn src->dest
                          ([src-id dst]
                           (src->dest src-id dst nil))
                          ([src-id dst color]
                           (let [{dst-type :type dst-line :line} dst
                                 dst-return? (= dst-type "Return")
                                 dst-id (if dst-return?
                                          (str src-id \- dst-line)
                                          dst-line)]
                             (concat
                              (when dst-return?
                                [(detail-node dst dst-id)])
                              (if color
                                [[src-id dst-id {:color color
                                                 ;; :headport :e ;; this was just to test that these things work!
                                                 }]]
                                [[src-id dst-id]])))))
        detail-nodes-and-edges
        (fn [{:keys [line type pass fail] :as detail}]
          (let [{pass-line :line :as pass-target} (detail-target detail pass)
                {fail-line :line :as fail-target} (detail-target detail fail)]
            (when (not= type "Return")
              (concat
               [(detail-node detail)]
               (if (or (= fail-line pass-line)
                       (= type "Calculate"))
                 (src->dest-inits line pass-target)
                 (concat
                  (src->dest-inits line pass-target :green)
                  (src->dest-inits line fail-target :red)))))))]
    (concat
     [[0 {:label "Θ"
          :shape :doublecircle
          ;; :rank :min ;; the start node should always be at the top anyways, but this is how rank could be used
          ;; :fillcolor :black
          ;; :fontcolor :black
          }]
      [0 (:line (next 0))]]
     (mapcat detail-nodes-and-edges ds))))


(defn diff-name
  [diff]
  (re-find #"(?<=Name: ).+" diff))


(defn source-name
 [s]
  (first (str/split-lines s)))


(defn digraph
  [inits]
  (apply uber/digraph inits))


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
                graph (digraph (graph-inits details))
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
            (uber/viz-graph graph (merge
                                   {:layout :dot
                                    :label title
                                    :labelloc "t"}
                                   save))))))))
