(ns sexpbot.plugins.sed
  (:use [sexpbot registry info]
        clj-config.core
        [clojure.string :only [join]]))

(def message-map (atom {}))
(def sed-regex #"^s/([^/]+)/([^/]*)/?")

(defn- format-msg [{:keys [bot nick] :as com-m}]
  (send-message com-m (prefix bot nick "Format is sed [-<user name>] s/<regexp>/<replacement>/ Try <prefix>help sed")))

(defn sed* [string regexp replacement]
  (try
    (.replaceAll string (str "(?i)" regexp) replacement)))

(defn sed [com-m verbose?]
  (let [{:keys [com nick args channel]} com-m
        
        arg-str (.trim (join " " args))
        try-to-match (fn [regex]
                       (or (second (re-find regex arg-str))
                           ""))
        user-to (try-to-match #"^\s*-(\w+)")
        margs (try-to-match #"\s*(s/[^/]+/[^/]*/?)$")
        orig-msg (some #(get (get-in @message-map [com channel])
                             %)
                       [user-to :channel-last])
        [regexp replacement] (next (re-find sed-regex margs))]
    (cond
     (and verbose? (empty? orig-msg))
     (send-message com-m "No one said anything yet!")

     (and verbose? (not-any? seq [regexp replacement]))
     (format-msg com-m)

     :else
     (try
       (let [new-msg (sed* orig-msg regexp replacement)]
         (when-not (= orig-msg new-msg)
           (send-message com-m (str "<" user-to "> " new-msg))))
       (catch Exception _
         (when verbose? (format-msg com-m)))))))

(defplugin
  (:hook
   :on-message
   :irc
   (fn [{:keys [com bot nick message channel] :as com-m}]
     (when (seq (re-find sed-regex message))
       (sed (assoc com-m :args [(str "-" nick) message]) false))
     (when (and (not= nick (:name @com))
                (not= (take 4 message)
                      (-> @bot :config :prepends first (str "sed"))))
       (swap! message-map update-in [com channel]
              assoc nick message, :channel-last message))))

  (:cmd
   "Simple find and replace. Usage: sed [-<user name>] s/<regexp>/<replacement>/
If the specified user isn't found, it will default to the last thing said in the channel. 
Example Usage: sed -boredomist s/[aeiou]/#/
Shorthand : s/[aeiou]/#/"
   #{"sed"}
   (fn [com-m]
     (sed com-m true))))
