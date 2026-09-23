# Database changes

Every change this project makes to the MySQL schema lives in this directory and
is committed with the code that depends on it. There is no separate change
process and no out-of-band SQL: if a feature needs a procedure, the file that
creates it is in the same repository, in the same history.

```
db/
  migration/                 one .sql file per dated change  <- start here
  scheduler-stage-history/   supporting scripts for the CRQ stage-history work
  README.md                  this file
```

## Conventions

**One file per change, named `YYYY-MM-DD_<what-it-does>.sql`.** The date is the
day the change was authored, not the day it is applied; a file is never renamed
after the fact.

**Every file opens with a header comment** covering, at minimum: what already
existed and is left alone, what the file adds, why, and how to roll it back.
`2026-09-04_ui_actions_audit_log.sql` is the current reference for the shape.

**Files are additive and re-runnable.** Procedures are written
`DROP PROCEDURE IF EXISTS` then `CREATE PROCEDURE`; index creation is guarded
against `information_schema` because MySQL has no `CREATE INDEX IF NOT EXISTS`.
Running a migration twice must be a no-op, not an error.

**Stored procedures are the interface.** The Java layer calls `CALL <proc>(...)`
through `DatabaseUtils`; feature code does not write SELECT/INSERT/UPDATE text.
A new read path means a new procedure, not new SQL in a service.

**Column aliases are the underscore form of the DTO field.** Spring's
`BeanPropertyRowMapper` lowercases a column label and matches it against
`underscoreName(property)`. `underscoreName` breaks on a case change and never
before a digit, so `categorizationTier1` is `categorization_tier1`, not
`categorization_tier_1` — an alias that gets this wrong binds to nothing and
yields a silent null rather than an error.

**A read procedure must not return a column called `error_message`.**
`DatabaseUtils.executeProcedureGetDataWithError` treats that column as a thrown
`DatabaseOperationException`, which the API layer answers as a 500. "Nothing
matched" is a normal answer and must come back as zero rows.

**Paging is `COUNT(*) OVER ()` inside the same query**, so a page and its true
total arrive in one round trip and no separate `*_Count` procedure can drift out
of sync with the `WHERE` clause. Page size is clamped inside the procedure.

**Live and repo can drift — dump before you edit.** Procedures have been
hand-edited on the server in the past. Before changing one, dump what is
actually deployed (`SHOW CREATE PROCEDURE <name>` /
`information_schema.ROUTINES`) and diff it against the file here.

---

## Change log

Newest first. Each entry states what was added and what it deliberately did not
touch.

### 2026-09-04 — `2026-09-04_ui_actions_audit_log.sql`

UI action audit log, **read side only**.

*Existing objects reused, unchanged:*

- **Table `UI_ACTIONS_LOGGER`** — `log_id`, `module`, `sub_module`, `action`,
  `actor_user_id`, `affected_user_id`, `remark`, `created_at DATETIME(6) DEFAULT
  CURRENT_TIMESTAMP(6)`. No duplicate audit table was created.
- **Procedure `sp_insert_ui_actions_log`** — the only writer. Six IN
  parameters; `log_id` and `created_at` default. **Not modified, not wrapped,
  not replaced.** The audit timestamp therefore stays database-generated and can
  never be supplied by the application or the browser.

*Added:*

| Object | Kind | Purpose |
| --- | --- | --- |
| `idx_ui_actions_module_sub_created (module, sub_module, created_at)` | index | Narrow by module (often sub-module), order by time — the screen's commonest query. The four pre-existing indexes already cover actor / affected / module+action / pure-time. |
| `sp_get_ui_actions_log` | procedure | Paged, filtered, searched, sorted list. |
| `sp_get_ui_actions_log_filters` | procedure | Distinct values behind the filter dropdowns. |
| `sp_get_ui_actions_log_access` | procedure | "May this user read the audit trail?", from `USER_ROLE_MAP` / `ROLE_MASTER`. |

**`sp_get_ui_actions_log` parameters** — every one optional; NULL (or `''` / `0`)
means "do not narrow on this".

| Parameter | Type | Notes |
| --- | --- | --- |
| `p_Module` | VARCHAR(100) | exact match |
| `p_Sub_Module` | VARCHAR(100) | exact match |
| `p_Action` | VARCHAR(100) | exact match |
| `p_Actor_User_ID` | BIGINT UNSIGNED | `USER_MASTER.user_id` |
| `p_Affected_User_ID` | BIGINT UNSIGNED | `USER_MASTER.user_id` |
| `p_From_Date` | DATETIME | inclusive; the API widens a date to 00:00:00.000000 |
| `p_To_Date` | DATETIME | inclusive; the API widens a date to 23:59:59.999999 |
| `p_Search` | VARCHAR(200) | matches module, sub-module, action, remark, either user's name or OLM id, or an exact `log_id` |
| `p_Sort_By` | VARCHAR(50) | whitelist: `created_at` (default), `log_id`, `module`, `sub_module`, `action`, `actor`, `affected` |
| `p_Sort_Direction` | VARCHAR(20) | `ASC` / `DESC` (default `DESC`) |
| `p_Limit` | INT | `<= 0` → 25, capped at 200 |
| `p_Offset` | INT | `< 0` → 0 |

**Returned columns:** `Log_Id`, `Module`, `Sub_Module`, `Action`,
`Actor_User_Id`, `Actor_Olmid`, `Actor_Name`, `Actor_Email`, `Actor_Role`,
`Affected_User_Id`, `Affected_Olmid`, `Affected_Name`, `Affected_Email`,
`Remark`, `Created_At`, `Action_Date`, `Action_Time`, `Total_Count`.

**Date/time handling.** All three temporal columns derive from the one
database-generated `created_at`: `Created_At` is the raw `DATETIME(6)`,
`Action_Date` is `DATE_FORMAT(...,'%Y-%m-%d')` and `Action_Time` is
`DATE_FORMAT(...,'%H:%i:%s')`. The date and time parts are pre-split in SQL so
the browser renders what was recorded rather than re-deriving it in the viewer's
timezone.

**Sorting is injection-proof.** `p_Sort_By` / `p_Sort_Direction` are matched
against a fixed whitelist and applied through `CASE` expressions; no identifier
is ever concatenated into SQL text, and an unrecognised value degrades to the
default instead of erroring.

**Access control.** `sp_get_ui_actions_log_access` returns `Is_Allowed = 1` only
for a live, non-expired `USER_ROLE_MAP` grant of `SUPER_ADMIN` or
`VEGAYAN_SUPER_ADMIN`. Widening or narrowing that set is a one-line change in
the procedure and needs no application redeploy.

**Rollback** — the schema returns exactly to its prior state, and no data is
lost, because nothing here writes:

```sql
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log;
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_filters;
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_access;
ALTER TABLE UI_ACTIONS_LOGGER DROP INDEX idx_ui_actions_module_sub_created;
```

### Earlier changes

See the header comment of each file in `db/migration/`. Notable recent ones:

| Date | File | Summary |
| --- | --- | --- |
| 2026-09-03 | `2026-09-03_cancelled_crq_registry.sql` | `Get_Cancelled_CRQ_List` / `_Summary` — the cancelled-CRQ register |
| 2026-09-02 | `2026-09-02_sp_update_user_role_map_fix.sql` | fixes `sp_update_user` reading a `USER_MASTER.role_id` that does not exist |
| 2026-08-27 | `2026-08-27_crq_global_search.sql` | `Get_CRQ_Global_Search` |
| 2026-08-25 | `2026-08-25_auth_session_expiry.sql` | `AUTH_JWT_TOKENS.expires_at` and the session sweep |
