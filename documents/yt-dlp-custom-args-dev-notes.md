# yt-dlp Custom Args (User-Level) - Dev Notes

Goal: allow users to define safe, user-level yt-dlp CLI arguments (e.g. `--force-ipv6`)
from `frontend/src/pages/UserSetting/index.jsx`, stored on the User record, and injected
into the download command in `DownloadHandler`.

This document outlines DB changes, backend changes, frontend changes, and required safety controls.

---

## 1) Database Changes

### 1.1 User table
Add a nullable column for storing user-provided args. Prefer JSON array for safe parsing.

- Column name (suggested): `yt_dlp_args`
- Type: `TEXT` (or `JSON` if DB supports it)
- Example stored value: `["--force-ipv6", "--socket-timeout", "15"]`

If the codebase currently uses SQL migrations, add a new migration. If not, add a SQL
snippet to the deployment docs.

Example (SQLite/MySQL):
```sql
ALTER TABLE user ADD COLUMN yt_dlp_args TEXT;
```

If using JSON:
```sql
ALTER TABLE user ADD COLUMN yt_dlp_args JSON;
```

---

## 2) Backend Changes

### 2.1 Entity & DTO
Update the user entity and any response DTOs to include `ytDlpArgs`.

Suggested field (entity):
```java
private String ytDlpArgs;
```
If storing JSON array, keep as `String` and parse with Jackson in service layer.

### 2.2 AccountController endpoints
Add a new endpoint to update args:

- `POST /api/account/update-yt-dlp-args`
  - body: `{ "id": "userId", "ytDlpArgs": ["--force-ipv6"] }` (array form)
  - response: updated args

Also ensure `GET /api/account/info` (or equivalent) returns the stored args so the UI
can prefill the form.

### 2.3 FeedContext
Extend `FeedContext` to carry the user args:

```java
public record FeedContext(
  String title,
  DownloadType downloadType,
  String videoEncoding,
  String videoQuality,
  String audioQuality,
  String subtitleLanguages,
  String subtitleFormat,
  List<String> ytDlpArgs
) {}
```

Populate it in `DownloadHandler.resolveFeedContext()` by reading the default user:
`userMapper.selectById("0")`, then parsing `ytDlpArgs`.

### 2.4 DownloadHandler injection point
In `DownloadHandler.getProcess()`:

- Build the base command as today.
- Insert validated args **before** `videoUrl` is appended.

Pseudo:
```java
List<String> userArgs = feedContext.ytDlpArgs();
List<String> safeArgs = ytDlpArgsValidator.filter(userArgs);
command.addAll(safeArgs);
command.add(videoUrl);
```

### 2.5 Validation & parsing
Do not accept raw strings without validation. Implement a small validator that:

- Only accepts options from a **whitelist**.
- Rejects options that can:
  - execute commands (`--exec`)
  - write arbitrary paths (`-o`, `--paths`, `--config-locations`)
  - override cookies or ffmpeg path (`--cookies`, `--ffmpeg-location`)
  - change output templates (`-o`)
  - load arbitrary config files (`--config-locations`, `--config`)

**Recommended: allowlist only a minimal, safe subset**, e.g.:
- `--force-ipv6`
- `--force-ipv4`
- `--proxy`
- `--socket-timeout`
- `--retry-sleep`
- `--concurrent-fragments`
- `--retries`

For options that require a value, ensure the next argument exists and does not start
with `-` unless that option explicitly allows it.

If validation fails:
- Persist **nothing** or store as-is but ignore at runtime.
- Return a clear error to the UI so the user can fix input.

---

## 3) Frontend Changes

### 3.1 UI in `frontend/src/pages/UserSetting/index.jsx`
Add a small "yt-dlp Parameters" card or a modal (consistent with existing settings):

Proposed UI:
- Multi-line `TextInput` or `Textarea` for args.
- Format: one option per line (recommended) or JSON array.
- A helper hint: "Example: --force-ipv6"
- Save button that calls `POST /api/account/update-yt-dlp-args`.

Suggested input format (one per line):
```
--force-ipv6
--socket-timeout 15
```

On submit:
- Split by newline
- Trim
- Convert to array of tokens
- Send as array in API request

### 3.2 State handling
When user settings page loads:
- Read `state.user?.ytDlpArgs`
- Prepopulate the text area

---

## 4) Security Measures (Required)

Minimum required:
- **Allowlist** options (deny all others).
- **Block** options that can execute commands or change output paths.
- **Trim** and validate each arg.
- **Log** rejected options (warn-level) for audit.

Recommended:
- Store the validated list (not the raw input).
- Cap number of args (e.g. max 10 tokens).
- Cap each token length (e.g. 128 chars).

---

## 5) Compatibility Notes

- Ensure custom args are appended before `videoUrl`.
- Do not allow user args to override `-o` or output templates.
- If your UI uses a single string input, parse it deterministically; do not pass raw
  strings into the command list.

---

## 6) Test Plan

Backend:
- Accepts `--force-ipv6` and adds it to command.
- Rejects `--exec` and `-o` with a clear error.
- Accepts value options and validates next token exists.

Frontend:
- Save args and reload settings correctly.
- Trigger a download and verify yt-dlp invocation includes the args.

---

## 7) Implementation Checklist

- [ ] DB migration adds `yt_dlp_args` to user
- [ ] User entity + DTO updated
- [ ] API endpoint `/api/account/update-yt-dlp-args`
- [ ] FeedContext includes `ytDlpArgs`
- [ ] DownloadHandler appends validated args before URL
- [ ] Validator enforces allowlist and blocks dangerous flags
- [ ] User settings UI added with helper text and save flow

