(ns cogito.core
  "
Cogito
======

*Cogito* is a Clojure implementation of **System-Z+**, a qualitative-probabilistic reasoner described in [*\"Qualitative probabilities for default reasoning, belief revision, and causal modeling\"*](ftp://ftp.cs.ucla.edu/pub/stat_ser/R161-L.pdf) by Moises Goldszmidt and Judea Pearl.

The basic idea is that you create a rule map, where keys are pairs of antecedents and consequents, each associated with an integer value, delta, that determines the strength of the connection between the pair

    {[:b :f] 1
     [:p :b] 1
     [:p [:not :f]] 1
     [:b :w] 1
     [:f :a] 1}

The system then automatically scores and orders the rules from most general (i.e. not inconsistent with any other rules) to most specific (i.e. inconsistent with one or more earlier rules). More specific rules will have higher scores than more general rules, where the score represents the surprise associated with a violation of the rule.

Below is an example of partitioning the above rules based on a consistency test (figure 2 Goldszmidt and Pearl). The rules in the first group are tolerated by all the rules in the rule set, whereas the rules in the second group are not tolerated by those in the first group.

    [#{[:b :f] [:b :w] [:f :a]}
     #{[:p :b] [:p [:not :f]]}]

Once partitioned scores are assigned to each rule. First the scores of the rules in the first group are set equal to the delta values associated with each of the rules. Next each rule in later groups are evaluated to determine which rules they violate in the first group. The z-plus-order function returns map showing which rules are violated.

    {[:p :b] ([:b :f]),
     [:p [:not :f]] ([:b :f])}

In the above case, both rules in the second group only violate one rule in the first group, [:b :f], which states that birds fly. The scores for these two rules will then be set equal to their respective delta values plus the score for the [:b :f] rule plus one, meaning violating more specific (i.e. later) rules will be associated with more surprise than violating more general ones.

The difference between the delta value and the score associated with each rule is that delta only represents the strength between the given antecedent and consequent, whereas the score takes all the other rules into consideration.

Queries are made by submitting competing hypotheses, the one that is the least surprising (i.e. has the lowest score associated with it) is selected.


Example
=======

The following example takes the above rules-map, compiles it, and runs several queries (each compare two competing hypotheses), which returns a map that associates a \"surprise\" score with each hypothesis, the lowest score wins.

A hypothesis is a model (map of truth values) formed from a logical statement. For instance, a statement

    penguins ^ birds -> fly

can be read as \"penguin birds can fly\" and can be represented in a truth-value map as:

    {:p true, :b true, :f true}

The following rules,

* birds fly
* penguins are birds
* penguins cannot fly
* birds have wings
* flying implies airborn

can be translated to the following rules-map.

    (def rules-map {[:b :f] 1
                    [:p :b] 1
                    [:p [:not :f]] 1
                    [:b :w] 1
                    [:f :a] 1})

All of the rules have a delta-value of one, these values can be adjusted if not all the rules have the same \"strength\".

Next compile the rules-map,

    (def compiled-rules (compile-rules rules-map))

and then run the following queries against it.

**Do penguin birds fly?**

This query is submitted by creating two hypotheses, one where penguin birds fly and one where they don't.

    (query compiled-rules
           {:p true, :b true, :f true}
           {:p true, :b true, :f false})

The results are that flying penguins that more surprising than non-flying ones.

    penguins ^ birds -> fly    (score)
    ----------------------------------
    true       true     true   (3)
    true       true     false  (1)


**Are all birds penguins?**

    (query compiled-rules
           {:b true, :p true}
           {:b true, :p false})

The results are that not all birds are penguins.

    birds -> not penguins    (score)
    --------------------------------
    true     not true        (0)
    true     not false       (1)


**Do red birds fly?**

    (query compiled-rules
           {:r true, :b true, :f true}
           {:r true, :b true, :f false})


The results are that red birds do fly.

    red ^ birds -> fly    (score)
    -----------------------------
    true  true     true   (0)
    true  true     false  (1)


**Are birds airborn?**

    (query compiled-rules
           {:b true, :a true}
           {:b true, :a false})

The results are that birds are airborn.

    birds -> airborn    (score)
    --------------------------
    true     true       (0)
    true     false      (1)


**Do penguins have wings?**

    (query compiled-rules
           {:p true, :w true}
           {:p true, :w false})


This is a known area of weakness for System-Z+, it cannot decide whether penguins have wings.

    penguins -> wings    (score)
    ----------------------------
    true        true     (1)
    true        false    (1)


"
  (:require [clojure.contrib.combinatorics :as comb]))

(defn negated?
  "
****
Determines if the variable has been negated.

**Examples**

    (negated? :x) ;=> false
    (negated? [:not :x]) ;=> true
"
  ([x]
     (and (vector? x) (= (first x) :not))))

(defn get-var
  "
****
Returns the logical variable's name.

**Examples**

    (get-var :x) ;=> :x
    (get-var [:not :x]) ;=> :x
"
  ([x]
     (if (negated? x) (second x) x)))

(defn extract-vars
  "
****
Extracts logical variable names from a rule set."
  ([rule-set]
     (set (mapcat (fn [r] (map get-var r)) rule-set))))

(defn generate-all-models-for-vars
  "
****
Generates all models possible for a given set of logical variables, even inconsistent models."
  ([vars]
     (let [truth-vals (comb/selections [true false] (count vars))]
       (map #(zipmap vars %) truth-vals))))

(defn generate-all-models-for-ruleset
  "
****
Generates all models possible for a given rule-set, even inconsistent models."
  ([rule-set]
     (let [vars (extract-vars rule-set)]
       (generate-all-models-for-vars vars))))

(defn assoc-truth-value
  "
****
"
  ([model logical-var truth-value]
     (cond
      (nil? (model logical-var)) (assoc model logical-var truth-value)
      (not= (model logical-var) truth-value) (assoc model logical-var :inconsistent)
      :else model)))

(defn merge-truth-values
  "
****
"
  ([& models]
     (reduce #(merge-with (fn [val1 val2]
			    (if (not= val1 val2) :inconsistent val1))
			  %1 %2)
	     {} models)))

(defn- to-model
  "
****
"
  ([term] (if (keyword? term) #{{term true}} term))
  ([term value] (if (keyword? term) #{{term value}} term)))

(defn filter-inconsistent-models
  "
****
"
  ([& models]
     (filter #(-> % vals set :inconsistent not) models)))


;; Logical Operation Functions
;; ===========================

(defn $not
  "
****
**Examples**

    ($not :a)
    ;; => #{{:a false}}

    ($not [{:a true, :b true}])
    ;; => #{{:b false, :a false} {:b false, :a true} {:b true, :a false}}
"
  ([term]
     (if (keyword? term)
       (to-model term false)
       (clojure.set/difference (set (generate-all-models-for-vars (keys (first term))))
			       (set term)))))

(defn $and
  "
****
**Examples**

    ($and ($not :a) ($not :b))
    ;; =>({:b false, :a false})

    ($and :a ($not [{:b false, :d true} {:b false, :d false}]))
    ;; => ({:b true, :d true, :a true} {:b true, :d false, :a true})

    ($and ($not [{:a true :c true} {:a true :c false}]) ($not [{:b false, :d true} {:b false, :d false}]))
    ;; => ({:b true, :d true, :a false, :c true} {:b true, :d false, :a false, :c true} {:b true, :d true, :a false, :c false} {:b true, :d false, :a false, :c false})

    ($and ($not [{:a true :c true} {:a true :c false}]) ($not [{:a false, :d true} {:a false, :d false}]))
    ;; => ({:d false, :a :inconsistent, :c true} {:d true, :a :inconsistent, :c true} {:d false, :a :inconsistent, :c false} {:d true, :a :inconsistent, :c false})

    ($and ($not [{:a true :c true} {:a true :c false}]) ($not [{:a true, :d true} {:a true, :d false}]))
    ;; => ({:d false, :a false, :c true} {:d true, :a false, :c true} {:d false, :a false, :c false} {:d true, :a false, :c false})


"
  ([term & terms]
     (reduce (fn [a b]
	       (let [a (to-model a)
		     b (to-model b)]
		 (apply filter-inconsistent-models
			(map #(apply merge-truth-values %)
			     (comb/cartesian-product a b)))))
	     term terms)))

(defn $or
  "
****
"
  ([term & terms]
     (reduce (fn [a b]
	       (let [a (to-model a)
		     b (to-model b)]
		 (apply filter-inconsistent-models
			(map #(apply merge-truth-values %)
			     (set (concat
				   (comb/cartesian-product a b)
				   (comb/cartesian-product a ($not b))
				   (comb/cartesian-product ($not a) b)))))))
	     term terms)))

(defn $=>
  "
****
"
  ([a b]
    (let [a (to-model a)
	  b (to-model b)]
      ($or ($and a b)
	   ($and ($not a)
		 ($or b ($not b)))))))


;; Z-System Internal Functions
;; =========

(defn stmt-to-models
  "
****

    (stmt-to-models [:not :a])
    (stmt-to-models [:and :a :b])
    (stmt-to-models [:or :a :b])
    (stmt-to-models [:=> :a :b])
    (stmt-to-models [:and [:not :a] :b])
    (stmt-to-models [:or [:not :a] :b])
    (stmt-to-models [:or [:and :a :b] [:and :c :d]])

"
  ([stmt]
     (if (keyword? stmt)
       (to-model stmt)
       (condp = (first stmt)
	   :not (apply $not (map #(stmt-to-models %) (rest stmt)))
	   :and (apply $and (map #(stmt-to-models %) (rest stmt)))
	   :or (apply $or (map #(stmt-to-models %) (rest stmt)))
	   :=> (apply $=> (map #(stmt-to-models %) (rest stmt)))
	   stmt))))

(defn antecedent
  "
****
Returns the antecedent of the given rule."
  ([rule] (first rule)))

(defn consequent
  "
****
Returns the consequent of the given rule."
  ([rule] (second rule)))

(defn state
  "
****
Returns the state of the logical variable in the model, where a model consists of a map of logical variables and their associated truth values.

**Examples**

    (state {:a true, :b false} :a) ;=> true
    (state {:a true, :b false} [:not b]) ;=> true
"
  ([model x]
     (if (negated? x)
       (not (model (second x)))
       (model x))))

(defmulti assoc-state
  "
****
Associates a truth-value(s) with a logical variable, or a pair of truth-values with a rule in the given model.

**Examples**

    (assoc-state {} :a true) ;=> {:a true}
    (assoc-state {} [:not :b] true) ;=> {:b false}

    (assoc-state {} [:a :b] [true false]) ;=> {:a true, :b false}
"
  (fn [model term p]
    (coll? p)))

(defmethod assoc-state false
  ([model term p]
     (assoc model (get-var term)
            (if (negated? term) (not p) p))))


(defmethod assoc-state true
  ([model rule [x y]]
     (-> {}
         (assoc-state (antecedent rule) x)
         (assoc-state (consequent rule) y))))

(defn update-bindings
  "
****
Updates model with new bindings based on the given rule. One or more bindings in the model will have a value of :inconsistent, if the new rule is inconsistent with the current model.

New values are only added to the model if the antecedent, 'a',  is already bound to true, then the value of 'b' is set to true if isn't bound yet, set to :inconsistent if it is already bound to false, otherwise it is left as is.

"
  ([model [a b :as rule]]
     (if (true? (state model a))
       (cond
        (false? (state model b)) (assoc model (get-var b) :inconsistent)
        (nil? (state model b)) (assoc-state model b true)
        :else model)
       model)))

(defn append-rule
  "
****
Returns the model created by adding rule to rule-set. A logical variable in the model will have a truth-value of :inconsistent if the new rule is not tolerated in the rule-set.

This method:

1. Sets the logical variables associated with the  antecedent and consequent of the rule to true in the model.
2. Walks through the rest of rules in a non-deterministic order and applying update-bindings to the model for each.
   * For each rule whose antecedent is already true in the model, its consequent is also set to true if it is currently nil, :inconsistent if its set to false, otherwise it's not changed.
   * Rules that could not be applied because their antecedents were false in the model are placed back at the end of the list of rules, possibly to be applied again once other bindings are in place.
   * Rules that don't change the state of the model because their antecendents and consequents were already true are not applied again.
   * When the model is updated after a new rule is applied all unapplied rules are attempted again.
"
  ([rule-set rule]
     (append-rule rule-set rule (assoc-state {} rule [true true])))
  ([rule-set rule model]
     (let [[a b] rule]
       (loop [m model
              rules rule-set
              unbound-rules []]
         (if-let [[r & rs] (seq rules)]
           (let [new-m (if (= rule r) m (update-bindings m r))]
             (cond
              (not= new-m m) 
                (recur new-m (concat unbound-rules rs) [])
              (true? (state new-m (antecedent r))) 
                (recur new-m rs unbound-rules)
              :else
                (recur new-m rs (conj unbound-rules r))))
           m)))))

(defn tolerate?
  "
****
Determines if a rule is tolerated by an existing rule-set, an optional model can be provided as well"
  ([rule-set rule]
     (-> (append-rule rule-set rule)
         vals
         set
         :inconsistent
         not))
  ([rule-set rule model]
     (-> (append-rule rule-set rule model)
         vals
         set
         :inconsistent
         not)))

(defn partition-by-consistency
  "
****
Partitions a set of rules into a set of groups orderd from general to specific, where the rules in each group are tolerated by all the rules in its group as well as all later groups.

If the rule-set cannot be partitioned such, then it is inconsistent and nil will be returned.

Each group forms a sub-theory, where earlier groups are more general and later groups are more specific.

**Notes**

See *Figure 2 Procedure for testing consistency* in Goldszmidt and Pearl.
"
  ([rule-set]
     (let [f (fn [rule-set] (set (filter #(tolerate? rule-set %) rule-set)))]
       (loop [parts [] rules rule-set]
         (if (seq rules)
           (let [new-part (f rules)]
             (if (seq new-part)
               (recur (conj parts new-part)
                      (apply clojure.set/difference rule-set new-part parts))
               nil))
           parts)))))

(defn entails?
  "
****
Determines if a set of truth-conditions are entailed by a given model."
  ([model truth-condition]
     (= truth-condition (select-keys model (keys truth-condition)))))

(defn filter-models
  "
****
Filters a set of models so that only those consistent with the given truth-condition are returned."
  ([models truth-condition]
     (filter #(= truth-condition (select-keys % (keys truth-condition))) models)))

(defn z-plus-order
  "
****
Z+-order algorithm
===================

1. Partition rules into ordered groups, where the rules in each successive group do not conflict with the rules in later groups.
2. Assign Z(r) scores to each rule in the first group equal to their individual delta values.
3. For each rule in the next group, find all the models where its antecedent is true and that are not in conflict with any other rule in the group.
  * From these models select models that conflict with any of rules in the first group and calculate their Z(r) score.
  * Choose the score from the model with the maximum value and add 1 to the value, and do the same for each other model.
  * Now choose the score from the model with the minimum score and add the delta value associated with the rule to determine the rule's Z(r) score.
4. Take the rule with the lowest Z(r) score and add it to the first group.
5. Repeat the procedure for each rule in the current group, and then move to the next group.

**Example**

    (def rules #{[:b :f]
                 [:p :b]
                 [:p [:not :f]]
                 [:b :w]
                 [:f :a]})

    (def parts (partition-by-consistency rules))

    ;;=> [#{[:b :f] [:b :w] [:f :a]} #{[:p :b] [:p [:not :f]]}]

    (def z-ordered-rules (z-plus-order (first parts)
                                       (second parts)))

    ;;=> {[:p :b] ([:b :f]), [:p [:not :f]] ([:b :f])}

"
  ([rz-plus delta-star]
     (letfn [(generate-models [r]
               {r (filter (fn [model] (tolerate? delta-star r model))
                          (filter-models (generate-all-models-for-ruleset delta-star)
                                         (assoc-state {} r [true true])))})
             (find-inconsistent-models [[r-star omega-r]]
               {r-star (mapcat (fn [model]
                                 (reduce (fn [out rz-rule]
                                           (if (entails? model (assoc-state {} rz-rule [true false]))
                                             (conj out rz-rule)
                                             out))
                                         [] rz-plus))
                               omega-r)})]
       (->> (map generate-models delta-star)
            (apply merge)
            (map find-inconsistent-models)
            (apply merge)))))

(defn apply-scores
  "
****
Updates the rules-map with scores from the output of the z-plus-order function.

**Examples**

    (def rules-map {[:b :f] 1
                    [:p :b] 1
                    [:p [:not :f]] 1
                    [:b :w] 1
                    [:f :a] 1})

    (def parts (partition-by-consistency (set (keys rules-map))))

    (def z-ordered-rules (z-plus-order (first parts)
                                       (second parts)))

    (apply-scores rules-map z-ordered-rules)
"
  ([rules-map z-ordered-rules]
     (apply merge rules-map
            (map (fn [[r m]]
                   {r (+ (apply max (map (fn [rz] (rules-map rz)) m))
                         (rules-map r)
                         1)})
                 z-ordered-rules))))

(defn process-query
  "
****
Returns a query result, that should be evaluated by score-query, given a z-ordered rule map and a query-map.

**Examples**

    (def rules-map {[:b :f] 1
                    [:p :b] 3
                    [:p [:not :f]] 3
                    [:b :w] 1
                    [:f :a] 1})

    (process-query rules-map {:p true, :b true, :f true}) ;;=> score = 3
    (process-query rules-map {:p true, :b true, :f false}) ;;=> score = 1
    ;; penguins ^ birds -> fly

    (process-query rules-map {:b true, :p true}) ;;=> score = 1
    (process-query rules-map {:b true, :p false}) ;;=> score = 0
    ;; birds -> not penguins

    (process-query rules-map {:r true, :b true, :f true}) ;;=> score = 0
    (process-query rules-map {:r true, :b true, :f false}) ;;=> score = 1
    ;; red ^ bird -> fly

    (process-query rules-map {:b true, :a true}) ;;=> score = 0
    (process-query rules-map {:b true, :a false}) ;;=> score = 1
    ;; birds -> airborn

    (process-query rules-map {:p true, :w true}) ;;=> score = 1
    (process-query rules-map {:p true, :w false}) ;;=> score = 1
    ;; undecided

          
"
  ([z-ordered-rules-map hypothesis-model]
     (map (fn [model]
            (reduce (fn [m rule]
                      (if (-> (update-bindings model rule)
                              vals
                              set
                              :inconsistent)
                        (assoc m rule (z-ordered-rules-map rule))
                        m))
                    {} (keys z-ordered-rules-map)))
          (-> (clojure.set/union (set (keys hypothesis-model))
                                 (extract-vars (keys z-ordered-rules-map)))
              generate-all-models-for-vars
              (filter-models hypothesis-model)))))

(defn score-query
  "
****
Returns a score associated with a query-result returned from the output of the query function.

Queries are performed by submitting queries for multiple hypotheses, and then selecting the hypothesis with the lowest (i.e. least surprising) score.

**Examples**

    (score-query (process-query rules-map {:p true, :b true, :f true})) ;;=> score = 3 ;
    (score-query (process-query rules-map {:p true, :b true, :f false})) ;;=> score = 1 ;
    ;; penguins ^ birds -> fly

    (score-query (process-query rules-map {:b true, :p true})) ;;=> score = 1 ;
    (score-query (process-query rules-map {:b true, :p false})) ;;=> score = 0 ;
    ;; birds -> not penguins

    (score-query (process-query rules-map {:r true, :b true, :f true})) ;;=> score = 0 ;
    (score-query (process-query rules-map {:r true, :b true, :f false})) ;;=> score = 1 ;
    ;; red ^ birds -> fly

    (score-query (process-query rules-map {:b true, :a true})) ;;=> score = 0 ;
    (score-query (process-query rules-map {:b true, :a false})) ;;=> score = 1 ;
    ;; birds -> airborn

    (score-query (process-query rules-map {:p true, :w true})) ;;=> score = 1 ;
    (score-query (process-query rules-map {:p true, :w false})) ;;=> score = 1 ;
    ;; undecided

"
  ([query-result]
     (apply min (or (seq (map #(apply max (or (vals %) [0])) query-result)) [0]))))


;; Query Functions
;; ===============

(defn compile-rules
  "
****
Given a map associating rules to delta-values, returns a new map containing two keys, :delta-values (which is associated with the original map) and :z+-scores (which is associated with a map containing a \"surprise\" score for each rule).

This compiled-rules map can be passed to the query function as an alternative to an uncompiled rules-map.

**Examples**

    (def rules-map {[:b :f] 1
                    [:p :b] 1
                    [:p [:not :f]] 1
                    [:b :w] 1
                    [:f :a] 1})

    (compile-rules rules-map)

"
  ([rules-map]
     {:delta-values rules-map
      :z+-scores (->> (partition-by-consistency (set (keys rules-map)))
                      (apply z-plus-order)
                      (apply-scores rules-map))}))

(defn query 
  "
****
Given either an uncompiled or compiled rules map and a series of hypothetical models, returns a map associating each hypothesis with a \"surprise\" score (i.e. the lowest score is the most likely).

**Examples**

    (def rules-map {[:b :f] 1
                    [:p :b] 1
                    [:p [:not :f]] 1
                    [:b :w] 1
                    [:f :a] 1})

    (def compiled-rules (compile-rules rules-map))

    ;; penguins ^ birds -> fly
    (query compiled-rules
           {:p true, :b true, :f true}
           {:p true, :b true, :f false})

    ;; birds -> not penguins
    (query compiled-rules
           {:b true, :p true}
           {:b true, :p false})

    ;; red ^ birds -> fly
    (query compiled-rules
           {:r true, :b true, :f true}
           {:r true, :b true, :f false})

    ;; birds -> airborn
    (query compiled-rules
           {:b true, :a true}
           {:b true, :a false})

    ;; undecided
    (query compiled-rules
           {:p true, :w true}
           {:p true, :w false})

"
  ([rules-map & hypotheses]
     (let [scored-rules (or (:z+-scores rules-map)
                            (:z+-scores (compile-rules rules-map)))]
       (reduce (fn [results hypothesis]
                 (assoc results
                   hypothesis
                   (score-query (process-query scored-rules hypothesis))))
               {} hypotheses))))

(defn entailed
  "
****
Returns a map of scores for the valid hypotheses associated with the given antecedent and consequent.

    (def rules {[:b :f] 1
                [:p :b] 1
                [:p [:not :f]] 1
                [:b :w] 1
                [:f :a] 1})

    (entailed rules ($and :p :b) :f)
    ;;=> {{:b true, :p true, :f true} 3, {:f false, :p true, :b true} 1}

    (entailed rules :b ($not :p))
    ;;=> {{:p true, :b true} 1, {:b true, :p false} 0}

    (entailed rules ($and :r :b) :f)
    ;;=> {{:b true, :r true, :f true} 0, {:f false, :r true, :b true} 1}

    (entailed rules :b :a)
    ;;=> {{:a false, :b true} 1, {:b true, :a true} 0}

    (entailed rules :p :w)
    ;;=> {{:w false, :p true} 1, {:p true, :w true} 1}

    (entailed rules ($or :b :p) :f)
    ;;=> {{:p true, :b true, :f true} 3,
          {:p false, :b true, :f true} 0,
          {:f false, :b true, :p true} 1,
          {:p true, :b false, :f true} 3,
          {:f false, :b true, :p false} 1,
          {:f false, :b false, :p true} 3}

"
  ([rules antecedent consequent]
    (let [a (to-model antecedent)
	  b (to-model consequent)]
      (apply query rules ($or ($and a b)
			      ($and a ($not b)))))))

(defn entailed?
  "
****
Returns a boolean indicating whether the given consequent is entailed from the antecendent and rules.

    (def rules {[:b :f] 1
                [:p :b] 1
                [:p [:not :f]] 1
                [:b :w] 1
                [:f :a] 1})

    (entailed? rules ($and :p :b) :f) ;;=> false

    (entailed? rules :b ($not :p)) ;;=> true

    (entailed? rules ($and :r :b) :f) ;;=> true

    (entailed? rules :b :a) ;;=> true

    (entailed? rules :p :w) ;;=> nil

    (entailed? rules ($or :b :p) :f) ;;=> true

"
  ([rules antecedent consequent]
    (let [a (to-model antecedent)
	  b (to-model consequent)
	  h0 (apply min (vals (apply query rules ($and a ($not b)))))
	  h1 (apply min (vals (apply query rules ($and a b))))]
      (cond
       (= h0 h1)
         nil
       (> h0 h1)
         true
       :else
         false))))


;; Experimental functions
;; ======================

(defn get-vars
  "
****

    (def rules #{[:=> :b :f]
                 [:=> :p :b]
                 [:=> :p [:not :f]]
                 [:=> :b :w]
                 [:=> :f :a]})

    (get-vars rules)


    (def rules2 #{[:=> [:or :b :c] :f]
                 [:=> :p [:and :b :d]]
                 [:=> :p [:not :f]]
                 [:=> :b :w]
                 [:=> :f :a]})

    (get-vars rules2)
"
  ([stmts]
     (letfn [(f [stmt]
		(if (coll? stmt)
		  (if (#{:not :and :or :=>} (first stmt))
		    (map #(f %) (rest stmt))
		    stmt)
		  stmt))]
	     (set (flatten (map f stmts))))))

(defn consistent-models
  "
****
    (def rules #{[:=> :b :f]
                 [:=> :p :b]
                 [:=> :p [:not :f]]
                 [:=> :b :w]
                 [:=> :f :a]})
    (consistent-models rules [:=> :b :f])
    (consistent-models rules [:=> :p :b]) ;; => rule not tolerated
    (consistent-models rules [:=> :p [:not :f]]) ;; => rule not tolerated
    (consistent-models rules [:=> :b :w])
    (consistent-models rules [:=> :f :a])
"
  ([rules [op a b :as rule]]
     (stmt-to-models (concat [:and a b] (clojure.set/difference rules rule)))))

(defn find-inconsistent-models
  "
****
Finds all the models possible from the model-vars that are consistent with the first rule and inconsistent with the second.

    (find-inconsistent-models rules-map [:=> [:and :a :b] :c] [:=> :e :f])
    (find-inconsistent-models rules-map [:=> :p [:not :f]] [:=> :b :f])
    (find-inconsistent-models rules-map [:=> :p :b] [:=> :b :f])

    (find-inconsistent-models rules [:and [:and :p :b] [:not :f]] [:=> :b :f])
"
  ([rule-map [_ a b :as consistent-rule] [_ e f :as inconsistent-rule]]
     (let [rules-set (set (keys rules-map))
	   vars (clojure.set/difference (get-vars rules-set)
					(get-vars consistent-rule)
					(get-vars inconsistent-rule))] ;; remove? not necessary? an optimization?
       (stmt-to-models [:and  a b e [:not f] (concat [:or] vars)]))))

(defn find-non-conflicting-models
  "
****
Finds all the models possible from the model-vars that are consistent with the first rule and inconsistent with the second.

    (find-non-conflicting-models rules-map [:=> [:and :p :b] [:not :f]] [:=> :b :f])

    (find-non-conflicting-models rules-map [:=> :p [:not :f]] [:=> :b :f])
    (find-non-conflicting-models rules-map [:=> :p :b] [:=> :b :f])

    (find-non-conflicting--models rules-map [:and [:and :p :b] [:not :f]] [:=> :b :f])
"
  ([rule-map stmt [_ a b :as rule]]
     (let [rules-set (set (keys rules-map))
	   rest-rules (clojure.set/difference rules-set #{rule})] ;; remove difference, not necessary
       (stmt-to-models (concat [:and a b stmt] rest-rules)))))


(defn find-conflicting-models
  "
****
Finds all the models possible from the model-vars that are consistent with the first rule and inconsistent with the second.


    (find-conflicting-models rules-map [:and :p :w] (first (keys scored-rules)))

    (find-conflicting-models rules-map [:and [:and :p :b] [:not :f]] [:=> :b :f])

    (find-conflicting-models rules-map [:and :p [:not :f]] [:=> :b :f])
    (find-conflicting-models rules-map [:and :p :b] [:=> :b :f])

    (find-conflicting-models rules-map [:and [:and :p :b] [:not :f]] [:=> :b :f])

"
  ([rule-map stmt [_ a b :as rule]]
     (let [rules-set (set (keys rules-map))
	   rest-rules (clojure.set/difference rules-set #{rule})] ;; remove the difference, not necessary
       (stmt-to-models (concat [:and a [:not b] stmt] rest-rules)))))

(defn partition-rules
  "
****

    (def rules-map {[:=> :b :f] 1
                    [:=> :p :b] 1
                    [:=> :p [:not :f]] 1
                    [:=> :b :w] 1
                    [:=> :f :a] 1})

    (partition-rules rules-map)


    (partition-rules {[:=> :s [:not :w]] 1
                      [:=> :s :a] 1
                      [:=> :a :w] 1})

"
  ([rules-map]
     (let [rule-set (set (keys rules-map))
	   f (fn [rs]
	       (set (filter #(seq (consistent-models rs %))
			    rs)))]
       (loop [parts [] rules rule-set]
         (if (seq rules)
           (let [new-part (f rules)]
             (if (seq new-part)
               (recur (conj parts new-part)
                      (apply clojure.set/difference rule-set new-part parts))
               nil))
           parts)))))

(defn score-rules
  "
    (def rules-map {[:=> :b :f] 1
                    [:=> :p :b] 1
                    [:=> :p [:not :f]] 1
                    [:=> :b :w] 1
                    [:=> :f :a] 1})

    (def scored-rules (score-rules rules-map))

 "
  ([rules-map]
     (let [[rz & parts] (partition-rules rules-map)
	   f (fn [rz-plus delta-star]
		(let [conflicts (apply merge-with concat
				  (for [ir (seq rz-plus) cr (seq delta-star)]
				    (when (seq (find-inconsistent-models rules-map cr ir)) {cr [ir]})))]
		  (apply merge rules-map
			 (map (fn [cr] {cr (+ 1 (rules-map cr)
					      (apply max (map (fn [ir] (or (rules-map ir) 0)) (conflicts cr))))})
			      (keys conflicts)))))]
       (when (seq rz)
	 (apply merge-with min (map #(f rz %) parts))))))


(defn query-hypothesis
  "
    (query-hypothesis scored-rules [:and [:and :p :b] [:not :f]]) ;; => 1
    (query-hypothesis scored-rules [:and [:and :p :b] :f]) ;; => 3

    (query-hypothesis scored-rules [:and :b [:not :p]]) ;; => 0
    (query-hypothesis scored-rules [:and :b [:not [:not :p]]]) ;; => 1

    (query-hypothesis scored-rules [:and [:and :r :b] :f]) ;; => 0
    (query-hypothesis scored-rules [:and [:and :r :b] [:not :f]]) ;; => 1

    (query-hypothesis scored-rules [:and :b :a]) ;; => 0
    (query-hypothesis scored-rules [:and :b [:not :a]]) ;; => 1

    (query-hypothesis scored-rules [:and :p :w]) ;; => 1
    (query-hypothesis scored-rules [:and :p [:not :w]]) ;; => 3

    (def rules2 (score-rules {[:=> :s [:not :w]] 1
                              [:=> :s :a] 1
                              [:=> :a :w] 1}))

    (query-hypothesis rules2 [:and :a :s]) ;; => 1
    (query-hypothesis rules2 [:and :a [:not :s]]) ;; => 0

    (query-hypothesis rules2 [:and [:and :s :a] :w]) ;; => 3
    (query-hypothesis rules2 [:and [:and :s :a] [:not :w]]) ;; => 1

    (query-hypothesis rules2 [{:a true}])
    (query-hypothesis rules2 [:not :a]) 



"
  ([scored-rules hypothesis]
     (let [rule-set (set (keys scored-rules))
	   f (fn [rule]
	       (merge-with max
			   (when-let [models (seq (find-conflicting-models rule-set hypothesis rule))]
			     (zipmap models (repeat (count models) (scored-rules rule))))
			   (when-let [consistent-models (seq (find-non-conflicting-models rule-set hypothesis rule))]
			     (zipmap consistent-models (repeat (count consistent-models) 0)))))]
       (->> (map f rule-set)
	    (apply merge-with max)))))

(defn score-hypothesis
  "
    (score-hypothesis scored-rules [:and [:and :p :b] [:not :f]]) ;; => 1
    (score-hypothesis scored-rules [:and [:and :p :b] :f]) ;; => 3

    (score-hypothesis scored-rules [:and :b [:not :p]]) ;; => 0
    (score-hypothesis scored-rules [:and :b [:not [:not :p]]]) ;; => 1

    (score-hypothesis scored-rules [:and [:and :r :b] :f]) ;; => 0
    (score-hypothesis scored-rules [:and [:and :r :b] [:not :f]]) ;; => 1

    (score-hypothesis scored-rules [:and :b :a]) ;; => 0
    (score-hypothesis scored-rules [:and :b [:not :a]]) ;; => 1

    (score-hypothesis scored-rules [:and :p :w]) ;; => 1
    (score-hypothesis scored-rules [:and :p [:not :w]]) ;; => 3


    (def rules2 (score-rules {[:=> :s [:not :w]] 1
                              [:=> :s :a] 1
                              [:=> :a :w] 1}))

    (score-hypothesis rules2 [:and :a :s]) ;; => 1
    (score-hypothesis rules2 [:and :a [:not :s]]) ;; => 0

    (score-hypothesis rules2 [:and [:and :s :a] :w]) ;; => 3
    (score-hypothesis rules2 [:and [:and :s :a] [:not :w]]) ;; => 1

"
  ([scored-rules hypothesis]
     (->> (query-hypothesis scored-rules hypothesis)
	  vals
	  (apply min))))