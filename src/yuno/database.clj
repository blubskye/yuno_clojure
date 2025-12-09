;; Yuno Gasai 2 (Clojure Edition) - Database
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns yuno.database
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defn create-datasource
  "Create a SQLite datasource"
  [path]
  (jdbc/get-datasource {:dbtype "sqlite" :dbname path}))

(defn initialize-db!
  "Initialize database tables"
  [ds]
  (log/info "Initializing database tables~")

  ;; Guild settings (extended)
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS guild_settings (
                        guild_id TEXT PRIMARY KEY,
                        prefix TEXT DEFAULT '.',
                        spam_filter_enabled INTEGER DEFAULT 0,
                        leveling_enabled INTEGER DEFAULT 1,
                        join_dm_title TEXT,
                        join_dm_message TEXT,
                        level_role_map TEXT)"])

  ;; User XP
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS user_xp (
                        user_id TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        xp INTEGER DEFAULT 0,
                        level INTEGER DEFAULT 0,
                        PRIMARY KEY (user_id, guild_id))"])

  ;; Mod actions
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS mod_actions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        moderator_id TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        action_type TEXT NOT NULL,
                        reason TEXT,
                        timestamp INTEGER NOT NULL)"])

  ;; Spam warnings
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS spam_warnings (
                        user_id TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        warnings INTEGER DEFAULT 0,
                        last_warning INTEGER,
                        PRIMARY KEY (user_id, guild_id))"])

  ;; Ban images
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS ban_images (
                        user_id TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        image_url TEXT NOT NULL,
                        PRIMARY KEY (user_id, guild_id))"])

  ;; Mention responses
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS mention_responses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        trigger_text TEXT NOT NULL,
                        response TEXT NOT NULL,
                        image_url TEXT)"])

  ;; Auto-clean settings
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS auto_clean (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        channel_name TEXT NOT NULL,
                        time_between_cleans INTEGER NOT NULL,
                        time_before_warning INTEGER NOT NULL,
                        remaining_time INTEGER,
                        UNIQUE(guild_id, channel_name))"])

  ;; Indexes
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_mod_actions_guild ON mod_actions(guild_id)"])
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_mod_actions_target ON mod_actions(guild_id, target_id)"])
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_user_xp_guild ON user_xp(guild_id)"])
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_mention_responses_guild ON mention_responses(guild_id)"])

  (log/info "Database initialized~ 💕"))

;; Guild Settings

(defn get-guild-settings
  "Get settings for a guild"
  [ds guild-id]
  (jdbc/execute-one! ds
                     ["SELECT * FROM guild_settings WHERE guild_id = ?" (str guild-id)]
                     {:builder-fn rs/as-unqualified-maps}))

(defn set-guild-settings!
  "Set settings for a guild"
  [ds guild-id settings]
  (jdbc/execute! ds
                 ["INSERT OR REPLACE INTO guild_settings
                   (guild_id, prefix, spam_filter_enabled, leveling_enabled, join_dm_title, join_dm_message, level_role_map)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"
                  (str guild-id)
                  (:prefix settings ".")
                  (if (:spam-filter-enabled settings) 1 0)
                  (if (:leveling-enabled settings true) 1 0)
                  (:join-dm-title settings)
                  (:join-dm-message settings)
                  (:level-role-map settings)]))

(defn get-prefix
  "Get prefix for a guild"
  [ds guild-id default-prefix]
  (or (:prefix (get-guild-settings ds guild-id))
      default-prefix))

(defn set-prefix!
  "Set prefix for a guild"
  [ds guild-id prefix]
  (let [settings (or (get-guild-settings ds guild-id)
                     {:spam-filter-enabled false :leveling-enabled true})]
    (set-guild-settings! ds guild-id (assoc settings :prefix prefix))))

(defn set-spam-filter!
  "Enable or disable spam filter for a guild"
  [ds guild-id enabled?]
  (let [settings (or (get-guild-settings ds guild-id)
                     {:prefix "." :leveling-enabled true})]
    (set-guild-settings! ds guild-id (assoc settings :spam-filter-enabled enabled?))))

(defn set-leveling!
  "Enable or disable leveling for a guild"
  [ds guild-id enabled?]
  (let [settings (or (get-guild-settings ds guild-id)
                     {:prefix "." :spam-filter-enabled false})]
    (set-guild-settings! ds guild-id (assoc settings :leveling-enabled enabled?))))

(defn set-join-message!
  "Set join DM message for a guild"
  [ds guild-id title message]
  (let [settings (or (get-guild-settings ds guild-id)
                     {:prefix "." :spam-filter-enabled false :leveling-enabled true})]
    (set-guild-settings! ds guild-id (assoc settings :join-dm-title title :join-dm-message message))))

(defn get-level-role-map
  "Get level role map for a guild"
  [ds guild-id]
  (when-let [json-str (:level_role_map (get-guild-settings ds guild-id))]
    (when (not (empty? json-str))
      (json/read-str json-str))))

(defn set-level-role-map!
  "Set level role map for a guild"
  [ds guild-id role-map]
  (let [settings (or (get-guild-settings ds guild-id)
                     {:prefix "." :spam-filter-enabled false :leveling-enabled true})]
    (set-guild-settings! ds guild-id (assoc settings :level-role-map (json/write-str role-map)))))

;; User XP

(defn get-user-xp
  "Get XP data for a user in a guild"
  [ds user-id guild-id]
  (or (jdbc/execute-one! ds
                         ["SELECT xp, level FROM user_xp
                           WHERE user_id = ? AND guild_id = ?"
                          (str user-id) (str guild-id)]
                         {:builder-fn rs/as-unqualified-maps})
      {:xp 0 :level 0}))

(defn add-xp!
  "Add XP to a user in a guild"
  [ds user-id guild-id amount]
  (jdbc/execute! ds
                 ["INSERT INTO user_xp (user_id, guild_id, xp, level)
                   VALUES (?, ?, ?, 0)
                   ON CONFLICT(user_id, guild_id) DO UPDATE SET xp = xp + ?"
                  (str user-id) (str guild-id) amount amount]))

(defn set-xp!
  "Set XP and level for a user in a guild"
  [ds user-id guild-id xp level]
  (jdbc/execute! ds
                 ["INSERT INTO user_xp (user_id, guild_id, xp, level)
                   VALUES (?, ?, ?, ?)
                   ON CONFLICT(user_id, guild_id) DO UPDATE SET xp = ?, level = ?"
                  (str user-id) (str guild-id) xp level xp level]))

(defn set-level!
  "Set level for a user in a guild"
  [ds user-id guild-id level]
  (jdbc/execute! ds
                 ["UPDATE user_xp SET level = ?
                   WHERE user_id = ? AND guild_id = ?"
                  level (str user-id) (str guild-id)]))

(defn get-leaderboard
  "Get top users by XP in a guild"
  [ds guild-id limit]
  (jdbc/execute! ds
                 ["SELECT user_id, xp, level FROM user_xp
                   WHERE guild_id = ? ORDER BY xp DESC LIMIT ?"
                  (str guild-id) limit]
                 {:builder-fn rs/as-unqualified-maps}))

(defn get-users-at-level
  "Get all users at a specific level in a guild"
  [ds guild-id level]
  (jdbc/execute! ds
                 ["SELECT user_id, xp, level FROM user_xp
                   WHERE guild_id = ? AND level = ?"
                  (str guild-id) level]
                 {:builder-fn rs/as-unqualified-maps}))

(defn get-all-users-xp
  "Get all users with XP in a guild"
  [ds guild-id]
  (jdbc/execute! ds
                 ["SELECT user_id, xp, level FROM user_xp WHERE guild_id = ?"
                  (str guild-id)]
                 {:builder-fn rs/as-unqualified-maps}))

;; Mod Actions

(defn log-mod-action!
  "Log a moderation action"
  [ds action]
  (jdbc/execute! ds
                 ["INSERT INTO mod_actions
                   (guild_id, moderator_id, target_id, action_type, reason, timestamp)
                   VALUES (?, ?, ?, ?, ?, ?)"
                  (str (:guild-id action))
                  (str (:moderator-id action))
                  (str (:target-id action))
                  (:action-type action)
                  (or (:reason action) "No reason provided")
                  (or (:timestamp action) (System/currentTimeMillis))]))

(defn get-mod-actions
  "Get recent moderation actions for a guild"
  [ds guild-id limit]
  (jdbc/execute! ds
                 ["SELECT id, moderator_id, target_id, action_type, reason, timestamp
                   FROM mod_actions WHERE guild_id = ?
                   ORDER BY timestamp DESC LIMIT ?"
                  (str guild-id) limit]
                 {:builder-fn rs/as-unqualified-maps}))

(defn get-mod-stats
  "Get moderation stats for a user in a guild"
  [ds guild-id moderator-id]
  (let [results (jdbc/execute! ds
                               ["SELECT action_type, COUNT(*) as count
                                 FROM mod_actions
                                 WHERE guild_id = ? AND moderator_id = ?
                                 GROUP BY action_type"
                                (str guild-id) (str moderator-id)]
                               {:builder-fn rs/as-unqualified-maps})]
    (reduce (fn [acc row]
              (assoc acc (keyword (:action_type row)) (:count row)))
            {:ban 0 :kick 0 :timeout 0 :unban 0}
            results)))

(defn mod-action-exists?
  "Check if a mod action already exists"
  [ds guild-id target-id action-type timestamp]
  (some? (jdbc/execute-one! ds
                            ["SELECT id FROM mod_actions
                              WHERE guild_id = ? AND target_id = ? AND action_type = ? AND timestamp = ?
                              LIMIT 1"
                             (str guild-id) (str target-id) action-type timestamp]
                            {:builder-fn rs/as-unqualified-maps})))

(defn ban-exists?
  "Check if a ban record exists for target in guild"
  [ds guild-id target-id]
  (some? (jdbc/execute-one! ds
                            ["SELECT id FROM mod_actions
                              WHERE guild_id = ? AND target_id = ? AND action_type = 'ban'
                              LIMIT 1"
                             (str guild-id) (str target-id)]
                            {:builder-fn rs/as-unqualified-maps})))

;; Spam Warnings

(defn add-spam-warning!
  "Add a spam warning for a user"
  [ds user-id guild-id]
  (jdbc/execute! ds
                 ["INSERT INTO spam_warnings (user_id, guild_id, warnings, last_warning)
                   VALUES (?, ?, 1, ?)
                   ON CONFLICT(user_id, guild_id)
                   DO UPDATE SET warnings = warnings + 1, last_warning = ?"
                  (str user-id) (str guild-id)
                  (System/currentTimeMillis) (System/currentTimeMillis)]))

(defn get-spam-warnings
  "Get spam warning count for a user"
  [ds user-id guild-id]
  (or (:warnings
       (jdbc/execute-one! ds
                          ["SELECT warnings FROM spam_warnings
                            WHERE user_id = ? AND guild_id = ?"
                           (str user-id) (str guild-id)]
                          {:builder-fn rs/as-unqualified-maps}))
      0))

(defn reset-spam-warnings!
  "Reset spam warnings for a user"
  [ds user-id guild-id]
  (jdbc/execute! ds
                 ["DELETE FROM spam_warnings WHERE user_id = ? AND guild_id = ?"
                  (str user-id) (str guild-id)]))

;; Ban Images

(defn set-ban-image!
  "Set ban image for a user in a guild"
  [ds user-id guild-id image-url]
  (jdbc/execute! ds
                 ["INSERT OR REPLACE INTO ban_images (user_id, guild_id, image_url)
                   VALUES (?, ?, ?)"
                  (str user-id) (str guild-id) image-url]))

(defn get-ban-image
  "Get ban image for a user in a guild"
  [ds user-id guild-id]
  (:image_url
   (jdbc/execute-one! ds
                      ["SELECT image_url FROM ban_images
                        WHERE user_id = ? AND guild_id = ?"
                       (str user-id) (str guild-id)]
                      {:builder-fn rs/as-unqualified-maps})))

(defn delete-ban-image!
  "Delete ban image for a user in a guild"
  [ds user-id guild-id]
  (jdbc/execute! ds
                 ["DELETE FROM ban_images WHERE user_id = ? AND guild_id = ?"
                  (str user-id) (str guild-id)]))

;; Mention Responses

(defn add-mention-response!
  "Add a mention response"
  [ds guild-id trigger response image-url]
  (jdbc/execute! ds
                 ["INSERT INTO mention_responses (guild_id, trigger_text, response, image_url)
                   VALUES (?, ?, ?, ?)"
                  (str guild-id) trigger response image-url]))

(defn get-mention-response
  "Get a mention response by trigger"
  [ds guild-id trigger]
  (jdbc/execute-one! ds
                     ["SELECT * FROM mention_responses
                       WHERE guild_id = ? AND trigger_text = ?"
                      (str guild-id) trigger]
                     {:builder-fn rs/as-unqualified-maps}))

(defn get-mention-responses
  "Get all mention responses for a guild"
  [ds guild-id]
  (jdbc/execute! ds
                 ["SELECT * FROM mention_responses WHERE guild_id = ?"
                  (str guild-id)]
                 {:builder-fn rs/as-unqualified-maps}))

(defn delete-mention-response!
  "Delete a mention response by id"
  [ds id]
  (jdbc/execute! ds
                 ["DELETE FROM mention_responses WHERE id = ?" id]))

;; Auto-Clean

(defn set-auto-clean!
  "Set auto-clean for a channel"
  [ds guild-id channel-name time-between time-before remaining]
  (jdbc/execute! ds
                 ["INSERT OR REPLACE INTO auto_clean
                   (guild_id, channel_name, time_between_cleans, time_before_warning, remaining_time)
                   VALUES (?, ?, ?, ?, ?)"
                  (str guild-id) channel-name time-between time-before
                  (or remaining (* time-between 60))]))

(defn get-auto-clean
  "Get auto-clean settings for a channel"
  [ds guild-id channel-name]
  (jdbc/execute-one! ds
                     ["SELECT * FROM auto_clean
                       WHERE guild_id = ? AND channel_name = ?"
                      (str guild-id) channel-name]
                     {:builder-fn rs/as-unqualified-maps}))

(defn get-auto-cleans
  "Get all auto-cleans for a guild"
  [ds guild-id]
  (jdbc/execute! ds
                 ["SELECT * FROM auto_clean WHERE guild_id = ?"
                  (str guild-id)]
                 {:builder-fn rs/as-unqualified-maps}))

(defn delete-auto-clean!
  "Delete auto-clean for a channel"
  [ds guild-id channel-name]
  (jdbc/execute! ds
                 ["DELETE FROM auto_clean WHERE guild_id = ? AND channel_name = ?"
                  (str guild-id) channel-name]))

(defn update-auto-clean-remaining!
  "Update remaining time for auto-clean"
  [ds guild-id channel-name remaining]
  (jdbc/execute! ds
                 ["UPDATE auto_clean SET remaining_time = ?
                   WHERE guild_id = ? AND channel_name = ?"
                  remaining (str guild-id) channel-name]))
