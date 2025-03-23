(ns cfg.dorothy
  (:require
   [clojure.string :as str]))



; id's that don't need quotes
(def ^:private safe-id-pattern #"^[_a-zA-Z\u0080-\u0255][_a-zA-Z0-9\u0080-\u0255]*$")
(def ^:private html-pattern    #"^\s*<([a-zA-Z1-9_-]+)(\s|>).*</\1>\s*$")

(defn ^:private safe-id? [s] (re-find safe-id-pattern s))
(defn ^:private html? [s] (re-find html-pattern s))
(defn html-label?
  [s]
  (and (str/starts-with? s "<")
       (str/ends-with? s ">")))
(defn ^:private escape-quotes [s] (str/replace s "\"" "\\\""))
(defn ^:private escape-id [id attr-name]
  (cond
    (string? id)  (cond
                    (and attr-name (safe-id? id)) id
                    (and (#{"label" "headlabel" "taillabel"} (name attr-name))
                         (html-label? id)) id
                    (html? id) (str \< id \>)
                    :else         (str \" (escape-quotes id) \"))
    (number? id)  (str id)
    (keyword? id) (escape-id (name id) attr-name)
    (fn? id)  (escape-id (#(-> % hash str) (id)) attr-name)
    :else         (throw (Error. (str "Invalid id: " (type id) " - " id)))))

(defn dot*-attrs
  [attrs]
  (str/join
   \,
   (for [[k v] attrs]
     (str (escape-id k true) \= (escape-id v k)))))

(comment
  
  (dorothy.core/dot*-attrs {:label "<TABLE>  </TABLE>"})
  (dot*-attrs {:label "<[8] SCR TEXT<br />Calculate<br />Quantity = UOM Qty>"})

  (html-label? "<[8] SCR TEXT<br />Calculate<br />Quantity = UOM Qty>")
  (name :label)
  )