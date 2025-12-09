;; Yuno Gasai 2 (Clojure Edition) - Configuration
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later
;;
;; "I'll protect this server forever... just for you~" <3

(ns yuno.config
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-config
  {:discord-token ""
   :default-prefix "."
   :database-path "yuno.db"
   :master-users []
   :spam-max-warnings 3
   :ban-default-image nil
   :dm-message "I'm just a bot :'(. I can't answer to you."
   :insufficient-permissions-message "${author} You don't have permission to do that~"})

(defn load-from-env
  "Load configuration from environment variables"
  []
  (merge default-config
         {:discord-token (or (System/getenv "DISCORD_TOKEN") "")
          :default-prefix (or (System/getenv "DEFAULT_PREFIX") ".")
          :database-path (or (System/getenv "DATABASE_PATH") "yuno.db")
          :master-users (if-let [master (System/getenv "MASTER_USER")]
                          [master]
                          [])
          :spam-max-warnings (if-let [warnings (System/getenv "SPAM_MAX_WARNINGS")]
                               (Integer/parseInt warnings)
                               3)
          :dm-message (or (System/getenv "DM_MESSAGE")
                          "I'm just a bot :'(. I can't answer to you.")}))

(defn load-from-file
  "Load configuration from a JSON file"
  [path]
  (when (.exists (io/file path))
    (let [content (slurp path)
          parsed (json/read-str content :key-fn keyword)]
      (merge default-config
             {:discord-token (or (:discord_token parsed) (:discord-token parsed) "")
              :default-prefix (or (:default_prefix parsed) (:default-prefix parsed) ".")
              :database-path (or (:database_path parsed) (:database-path parsed) "yuno.db")
              :master-users (or (:master_users parsed) (:master-users parsed) [])
              :spam-max-warnings (or (:spam_max_warnings parsed) (:spam-max-warnings parsed) 3)
              :ban-default-image (or (:ban_default_image parsed) (:ban-default-image parsed))
              :dm-message (or (:dm_message parsed) (:dm-message parsed)
                              "I'm just a bot :'(. I can't answer to you.")
              :insufficient-permissions-message
              (or (:insufficient_permissions_message parsed)
                  (:insufficient-permissions-message parsed)
                  "${author} You don't have permission to do that~")}))))

(defn load-config
  "Load configuration from file or environment"
  [& [path]]
  (let [config-path (or path "config.json")
        file-config (load-from-file config-path)
        env-config (load-from-env)]
    (if file-config
      ;; Override file config with env token if present
      (if (not (str/blank? (:discord-token env-config)))
        (assoc file-config :discord-token (:discord-token env-config))
        file-config)
      env-config)))

(defn master-user?
  "Check if a user ID is a master user"
  [config user-id]
  (some #(= % (str user-id)) (:master-users config)))

(defn valid-token?
  "Check if the token is valid (not empty or placeholder)"
  [config]
  (let [token (:discord-token config)]
    (and (not (str/blank? token))
         (not= token "YOUR_DISCORD_BOT_TOKEN_HERE"))))
