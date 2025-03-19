(ns cfg.core
  (:require
   [ubergraph.core :as uber]
   [clojure.string :as str]
   [clojure.java.io :as io])
  (:gen-class))

(def dbdel-g
  "DBDel UEA w/Idnet, Attrib ID"
  (uber/digraph
   [2 {:label "DEL HUM"}]
   [5 {:label "DONE"}]
   [6 {:label "DATA ERROR"}]
   [1 6 {:label :pass}]
   [1 2 {:label :fail}]
   [2 5 {:label :pass}]
   [2 3 {:label :fail}]
   [3 2 {:label :pass}]
   [3 4 {:label :fail}]
   [4 2 ]
   [6 7 ]
   ))

(def po-diff-str
  "Name: DBUpd PKD Stage Loc w/Fork ID, HU ID or Stage Loc
Description: 
Sub Type: Normal
Comments: DESCRIPTION
Update PKD staging location with Load ID

REQUIRED FIELDS


MODIFICATIONS
Date          By       Name
06/22/03  ETH   Created
Commander Viewable: NO
Allow Dynamic Call: NO
Keywords: 

--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  | 0    | --To avoid exceeding the maximum number of calls allowed. Set EIL to a local variable, then turn EIL flag off
  | 1    | DB ACTION | Database      | Upd PKD Stage Loc w/Fork ID, HU ID| DONE      | NEXT      
  | 2    |           | Compare       | Deadlock Error?               | PREVIOUS  | NEXT      
  | 3    |           | Call          | Confirm Database Error Retry  | DB ACTION | DB ACTION 
  | 4    | DONE      | Return        | PASS                          | PASS      | PASS      
  | 5    | BAD DATA  | Calculate     | Log: Data Error               | NEXT      | NEXT      
  | 6    |           | Send          | Log Msg to APPLOG1 (Error)    | NEXT      | NEXT      
  | 7    |           | Return        | FAIL                          | FAIL      | FAIL      

")

(def packing-source-str
  "_Packing

|----+------------+------------+------------+--------------+-------------
|    | Label      | Pass       | Fail       | Type         | Action Name 
|----+------------+------------+------------+--------------+-------------
|    |            |            | LOG ERROR  | Calculate    | Transaction Code = \"315\"
|    |            |            | FAILURE    | Call         | Global Values - Manifesting
|    | RESTART    |            | FAILURE    | Call         | Reset Global Variables
|    |            |            | LOG ERROR  | Calculate    | Order Number = \"\"
|    |            |            | LOG ERROR  | Calculate    | Location = \"\"
|    |            |            | LOG ERROR  | Calculate    | Printer Name = \"\"
|    |            |            | LOG ERROR  | Calculate    | HU ID = \"\"
|    |            |            | LOG ERROR  | Calculate    | Source Location = \"\"
|    |            |            | LOG ERROR  | Calculate    | Source HU ID = \"\"
|    |            |            | LOG ERROR  | Calculate    | Destination HU ID = \"\"
|    |            |            | FAILURE    | Call         | Check Inventory On Fork
| -- |            |            |            |              | 
|    | PAKSTATION |            | FAILURE    | Call         | Pack - Pack Station
|    | SOURCE LP  |            | SWITCH1    | Call         | Pack - Source LP
|    |            |            | GET ITEM   | Compare      | FLAG Repack Set?
|    |            | GET ITEM   | SOURCE LP  | Call         | Confirm Repack
|    | SWITCH1    | CUBBY      | PAKSTATION | Compare      | <F2-Switch>?
|    | CUBBY      |            | SWITCH2    | Call         | Pack - Cubby
|    |            |            | GET ITEM   | Compare      | FLAG Repack Set?
|    |            | GET ITEM   | CUBBY      | Call         | Confirm Repack
|    | SWITCH2    | SOURCE LP  | PAKSTATION | Compare      | <F2-Switch>?
| -- Loop through by container
|    | GET ITEM   |            | LOG ERROR  | Call         | DBExec usp_pak_get_next_item
|    |            | SRC EMPTY  | ITEM       | Compare      | Item ID = \"\"?
|    | ITEM       | LOT        |            | Call         | Pack - Item
|    |            |            | EMPTY CHK  | Compare      | <F1-Cancel>?
|    |            | SOURCE LP  | CUBBY      | Compare      | FLAG LP Scanned set?
|    | EMPTY CHK  |            | GET ITEM   | Compare      | FLAG Source Empty set?
|    |            | LP ERR RES |            | Compare      | FLAG Error Resolution set?
|    |            | SOURCE LP  | CUBBY      | Compare      | FLAG LP Scanned set?
|    | LOT        |            | ITEM       | Call         | Pack - Lot
|    | EXP        | DISP INFO  | LOT SET?   | Call         | Pack - Expiration
| -- | ITM VERIFY | DISP INFO  |            | Call         | Pack - Verify Item Availability
|    | DISP INFO  | QTY        | LOG ERROR  | Call         | Pack - Display Info
|    |            |            | LOT SET?   | Compare      | Expiration Date Control = \"Y\"?
|    |            |            | EXP        | Compare      | FLAG Single Expiration set?
|    | LOT SET?   |            | ITEM       | Compare      | Lot Control = \"F\"?
|    |            | ITEM       | LOT        | Compare      | FLAG Single Lot set?
|    | QTY        | POST CHECK |            | Call         | Pack - Quantity TX
|    |            | DISP INFO  | LOG ERROR  | Compare      | <F1-Cancel>?
|    | POST CHECK | LP ERR RES |            | Compare      | FLAG Error Resolution set?
|    |            | DEST LP    |            | Compare      | FLAG Container Complete set?
|    |            | SRC EMPTY  | GET ITEM   | Compare      | FLAG Source Empty set?
| -- Post pack processing
| -- All stuff has been packed to container, define LP and print
|    | DEST LP    |            | LOG ERROR  | Call         | Pack - Destination LP
|    | PRINT      | COMPLETE   |            | Call         | Pack - Print
|    |            | RESOLUTION |            | Compare      | <F5-Issue>?
|    |            | DEST LP    | LOG ERROR  | Compare      | <F1-Cancel>?
| -- Finished!
|    | COMPLETE   |            |            | Call         | Pack - Complete
|    |            |            | GET ITEM   | Compare      | FLAG Source Empty set?
|    |            | SOURCE LP  | CUBBY      | Compare      | FLAG LP Scanned set?
| -- Empty LP notification
|    | SRC EMPTY  |            | SRC CHECK  | Call         | Pack - Source Empty
|    |            |            | GET ITEM   | Compare      | Source HU ID = \"\"?
|    |            |            | GET ITEM   | Compare      | Source Location = \"\"?
|    | SRC CHECK  | SOURCE LP  | CUBBY      | Compare      | FLAG LP Scanned set?
| -- LP Error Resolution Location
| -- 2024-07-31 BRIANH failure for Error LP should not go to log error
|    | LP ERR RES | RESOLUTION | ITEM       | Call         | Pack - Dest LP Error Resolution
| -- 2024-07-31 BRIANH failure for Error Location should not go to log error
|    | RESOLUTION |            | PRINT      | Call         | Pack - Error Resolution
|    |            |            | GET ITEM   | Compare      | FLAG Source Empty set?
|    |            | SOURCE LP  | CUBBY      | Compare      | FLAG LP Scanned set?
|    | SUCCESS    |            |            | Return       | PASS
|    | LOG ERROR  |            |            | Calculate    | Log: Error Occurred in SYS_PO
|    |            |            |            | Send         | Log Msg to APPLOG1 (Error)
|    | FAILURE    |            |            | Return       | FAIL")


(def valid-pallet-str
  "	1	  	          	Calculate   	Local Parent HU = Parent HU ID	          	          
	2	  	          	Compare     	Screen Override FLAG Set?     	          	SCREEN    
	3	  	          	Calculate   	Clear Screen Override FLAG    	DIALOG    	DIALOG    
	4	  	SCREEN    	Calculate   	Scr: Enter PT to mv to (Dst)PT	          	          
	5	  	          	Calculate   	Scr: Conc Option Switch F2    	          	          
	6	  	DIALOG    	Dialog      	C11AAA12-6861-4E4F-93EC-F90F41	VERIFY    	          
	7	  	          	Calculate   	Parent HU ID = Local Parent HU	          	          
	8	  	          	Compare     	<CLOSE_F2> key?               	FAILURE   	          
	9	  	          	Compare     	<CANCEL> key?                 	FAILURE   	          
	10	  	          	Calculate   	Err: Invalid Option           	DIALOG    	DIALOG    
	11	  	VERIFY    	Call        	DBExec SP Verify PLT-B Ship PT	          	DIALOG    
	12	  	          	Compare     	SYS_SHORTMSG = \"\"?            	          	DIALOG    
	13	  	SUCCESS   	Return      	PASS                          	          	          
	14	  	FAILURE   	Return      	FAIL                          	          	          ")

(defn columns
  [line]
  (-> (zipmap
       [:blank :line :comment :label :type :action :pass :fail]
       (map str/trim (str/split line #"\t")))
      (->> (remove (comp empty? val))
           (into {}))
      (update :line #(Integer/parseInt %)))
  )

(defn diff-columns
  [line]
  (-> (zipmap
       [:blank :line :label :type :action :pass :fail]
       (map str/trim (str/split line #"\|")))
      (->> (remove (comp empty? val))
           (into {}))
      (update :line #(Integer/parseInt %)))
  )

(defn details
  [s]
  (map columns (str/split-lines s)))

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

(comment
  (diff-details po-diff-str)
  (into
   []
   (comp
    (drop-while #(not (re-find #"^-+$" %)))
    (drop 1)
    (remove empty?)
    (map diff-columns)
    (remove #(when-let [label (:label %)]
               (re-find #"--" label))))
   (str/split-lines po-diff-str))
  )


(defn source-columns
  [line]
  (into {}
        (remove (comp empty? val))
        (zipmap
         [:comment :label :pass :fail :type :action]
         (rest (mapv str/trim (str/split line #"\|")))))
  #_(-> (zipmap
       [:blank :line :label :type :action :pass :fail]
       (map str/trim (str/split line #"\|")))
      (->> (remove (comp empty? val))
           (into {}))
      (update :line #(Integer/parseInt %))))


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


(comment
  (source-details packing-source-str) 
  )


(defn name->href
  [name]
  (let [char-map ;; from wikipedia: https://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
        {"/" "\u29f8"
         "\\" "\u29f9"
         "?" "\u0294"
         "*" "\u2217"
         ":" "\u2236"
         "<" "\u02c2"
         ">" "\u02C3"
         "\"" "\u201C"
         "|" "\u2223"
         "&" "&amp;" ;; &'s are required to be converted to &amp; in svg

         }
               ;; from stack exchange: https://lifehacks.stackexchange.com/questions/24681/hack-to-indicate-a-forward-slash-or-backslash-in-a-filename-when-those-are-reser
        #_{"/" (str \u2215)
           "\\" (str \u244a)}]
    (str/replace name
                 (re-pattern (str "[" (str/join (map str/re-quote-replacement (keys char-map))) "]"))
                 char-map)))

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
                       (let [node-label (str \[ line \] (when label \space) label \newline type \newline action)
                             attrs (cond-> {:id node-id
                                            :type type
                                            :action action
                                            :shape (get {"Call" (if (= action "Dialog") :parallelogram :box #_:box3d)
                                                         "Calculate" :box
                                                         "Goto" :circle
                                                         "Compare" :oval #_:hexagon #_:diamond
                                                         "Database" :cylinder
                                                         "Return" :oval
                                                         }
                                                        type
                                                        :note)
                                            :label node-label}
                                     (contains? #{"Call"} type)
                                     (assoc :peripheries 2)
                                     (contains? #{"Call" "Calculate" "Database" "Compare"} type)
                                     (assoc :href 
                                            (if (= type "Call")
                                              (str "../ProcessObject/" (name->href action) ".arch.svg")
                                              (str "../" type "/" (name->href action) ".arch")
                                              )
                                            
                                            )
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
                                [[src-id dst-id {:color color}]]
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
          ;; :fillcolor :black
          ;; :fontcolor :black
          }]
      [0 (:line (next 0))]]
     (mapcat detail-nodes-and-edges ds))))

(defn diff-graph
  [diff]
  (apply uber/digraph
         (graph-inits (diff-details diff))))

(defn source-graph
  [source]
  (apply uber/digraph
         (graph-inits (source-details source))))

(comment
  (details valid-pallet-str)
  (graph-inits (details valid-pallet-str))
  (graph-inits (source-details packing-source-str))
  
  )

(defn diff-name
  [diff]
  (re-find #"(?<=Name: ).+" diff))


(defn source-name
 [s]
  (first (str/split-lines s)))


(defn diff-graph-viz
  ([diff]
   (diff-graph-viz diff nil))
  ([diff file]
   (let [g (diff-graph diff)
         name (diff-name diff)]
     (uber/viz-graph
      g
      (merge
       {:layout :dot

        :label name
        :labelloc "t"}
       (when file
         {:save {:filename file
                 :format :jpg}}))))))

(defn source-graph-viz
  ([source]
   (source-graph-viz source nil))
  ([source file & {:keys [format]
                   :or {format :svg}}]
   (let [g (source-graph source)
         name (source-name source)]
     (uber/viz-graph
      g
      (merge {:layout :dot
              :label name
              :labelloc "t"}
             (when file
               {:save {:filename file
                       :format :dot #_format}}))))))


(let [source  (slurp "/home/dvance/HLF-3-14/WA/ProcessObject/Directed Pickup - Quantity.arch")
      g (source-graph source)]
  (sort-by (comp #(Integer/parseInt %) #(re-find #"\d+" %) str first) (map #(uber/node-with-attrs g %) (uber/nodes g)))
  )


(defn -main
  [& args]
  (if-let [[opt & rest-args] (seq args)]
    (case opt
      "-s" 
      (let [[folder & files] rest-args]
        (doseq [file files]
          (let [f (io/file folder (.getName (io/file file)))]
            (io/make-parents f)
            (source-graph-viz (slurp file) (str f ".dot" )))))
      "-i"
      (doseq [file rest-args]
        (let [f (io/file file)]
          (source-graph-viz (slurp f) (str f ".dot"))))
      (let [[jpg process] args]
        (if (or (nil? process) (= process "-"))
          (diff-graph-viz (slurp *in*) jpg)
          (diff-graph-viz (slurp process) jpg))))
    (println
      (str/join "\n"
                ["Usage:"
                 "clj -M -m cfg.core OUTPUT_JPG PROCESS_FILE"
                 "clj -M -m cfg.core OUTPUT_JPG [-]"
                 "clj -M -m cfg.core -i [PROCESS_FILE] ..."
                 "clj -M -m cfg.core -s OUT_FOLDER [SOURCE_FILE] ..."]))))
