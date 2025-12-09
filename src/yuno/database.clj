;; Yuno Gasai 2 (Clojure Edition) - Database
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns yuno.database
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.tools.logging :as log]))

(defn create-datasource
  "Create a SQLite datasource"
  [path]
  (jdbc/get-datasource {:dbtype "sqlite" :dbname path}))

(defn initialize-db!
  "Initialize database tables"
  [ds]
  (log/info "Initializing database tables~")

  ;; Guild settings
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS guild_settings (
                        guild_id TEXT PRIMARY KEY,
                        prefix TEXT DEFAULT '.',
                        spam_filter_enabled INTEGER DEFAULT 0,
                        leveling_enabled INTEGER DEFAULT 1)"])

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

  ;; Indexes
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_mod_actions_guild ON mod_actions(guild_id)"])
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_user_xp_guild ON user_xp(guild_id)"])

  (log/info "Database initialized~ 💕"))

;; Guild Settings

(defn get-guild-settings
  "Get settings for a guild"
  [ds guild-id]
  (jdbc/execute-one! ds
                     ["SELECT prefix, spam_filter_enabled, leveling_enabled
                       FROM guild_settings WHERE guild_id = ?" (str guild-id)]
                     {:builder-fn rs/as-unqualified-maps}))

(defn set-guild-settings!
  "Set settings for a guild"
  [ds guild-id settings]
  (jdbc/execute! ds
                 ["INSERT OR REPLACE INTO guild_settings
                   (guild_id, prefix, spam_filter_enabled, leveling_enabled)
                   VALUES (?, ?, ?, ?)"
                  (str guild-id)
                  (:prefix settings ".")
                  (if (:spam-filter-enabled settings) 1 0)
                  (if (:leveling-enabled settings true) 1 0)]))

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
