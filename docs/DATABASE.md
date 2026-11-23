# Database

PostgreSQL 17, migrations in `backend/src/main/resources/db/migration` (Flyway). Hibernate runs with
`ddl-auto=validate` — it never creates tables.

## ER overview (implemented)

```
users ─┬─< refresh_tokens          (family_id = one device session)
       ├─< email_tokens            (verify / reset, hashed)
       ├─< user_topics >── topics
       ├─< user_follows >─ users
       ├─< community_members >── communities ─┬─< community_rules
       │        (role)                         ├─< community_flairs
       │                                       └── topics
       ├─< posts >── communities   posts ─┬─< post_media >── media_assets
       │                                  ├── polls ─< poll_options ─< poll_votes
       │                                  ├─< post_votes / saved_posts / hidden_posts
       │                                  └─< comments (parent_id → comments) ─< comment_votes
       └─< notifications
```

## Key decisions
- **IDs**: `bigint GENERATED ALWAYS AS IDENTITY`. Monotonic, so `NEW` sorts and keyset cursors use `id`.
- **Soft delete** on users (anonymised), posts and comments (`deleted_at`): threads keep their shape.
- **Denormalised counters** (`score`, `upvotes`, `comment_count`, `member_count`, karma, follower counts) are
  updated with relative `SET x = x + :delta` in the same transaction as the source row. The vote tables are
  the source of truth; `VoteConcurrencyIT` asserts counters always equal `SUM(value)`.
- **Ranking columns** stored, not computed per query: `posts.hot_rank`, `comments.best_rank` (Wilson),
  `comments.controversy`. Updated on every vote/comment. `hot_rank` embeds time, so it needs no cron.
- **Comments**: adjacency list + `depth` (max 10) + `reply_count`. Indexed on `(post_id, best_rank)` for
  roots and `(parent_id, best_rank)` for children; fan-out loaded with `ROW_NUMBER() OVER (PARTITION BY parent_id)`.
- **Search**: generated `tsvector` columns (title A, tags B, body C) with GIN indexes; `pg_trgm` on usernames.
  `strata_tags_text()` is an IMMUTABLE wrapper because `array_to_string` is only STABLE.
- **Notifications** grouping: partial unique index `(user_id, group_key) WHERE read_at IS NULL` + upsert.
- **Partial indexes** on posts exclude deleted/removed rows so feed indexes stay small.

## Migrations
| Version | Content |
|---|---|
| V1 | core schema (identity, communities, posts, comments, votes, media, polls, notifications) |
| V2 | reference topics |
| V11 | moderator funding: `funding_policies`, `funding_pools`, `funding_subscriptions`, `funding_contributions`, `funding_allocations`, `funding_ledger` (append-only via triggers), `funding_webhook_events` (D-022) |

Future phases add tables (reports, moderation_actions, collections, pulses, digests, ai_*, podcasts, files,
funding_*) as new migrations — never by editing applied ones.
