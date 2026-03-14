# Troubleshooting

This page covers the most common problems self-hosting users hit when running PigeonPod.

## What This Page Is For

Use this page when:

- the web UI does not open
- login or RSS access behaves unexpectedly
- YouTube subscriptions do not sync
- downloads fail
- cookies seem ineffective
- a feed looks empty even though the source has videos

## Before You Dive In

Check these basics first:

- your container is still running
- the mapped port is correct
- the database path is writable
- you are testing with a valid source URL or source ID

If the instance does not even start, begin with container logs or application logs before changing settings.

## Symptom: The Web UI Does Not Open

### Possible causes

- the service is not running
- the port mapping is wrong
- another process is already using the port
- the app failed during startup

### How to verify

- confirm the container is up with `docker ps`
- confirm your port mapping matches the URL you are opening
- check logs for startup exceptions
- verify the browser is opening the same host and port you exposed

### How to fix

- restart the container
- change the host port if it conflicts with another service
- fix any database path or filesystem permission errors
- re-run the service after the startup error is resolved

## Symptom: I Cannot Log In

### Possible causes

- you are using the wrong URL or wrong instance
- the initial credentials were changed
- built-in auth was disabled behind another access layer

### How to verify

- confirm you are connecting to the intended instance
- confirm whether `PIGEON_AUTH_ENABLED` is still enabled
- check whether a reverse proxy or auth proxy is intercepting the request

### How to fix

- use the correct instance URL
- if this is a fresh install, try the default credentials:
  - username: `root`
  - password: `Root@123`
- if auth is disabled, make sure your external auth layer is configured correctly

## Symptom: YouTube Feed Detection or Sync Fails

### Possible causes

- the YouTube API key is missing
- the API key is invalid
- the daily YouTube quota is exhausted
- the source URL is ambiguous or not the expected channel

### How to verify

- open **User Settings** and confirm the API key is saved
- check the quota status in the app
- try using a raw YouTube channel ID instead of a channel URL

### How to fix

- set or replace the API key
- wait until quota resets if the daily limit is already reached
- use a channel ID for more accurate matching and lower quota usage

Related pages:

- [YouTube API Key Setup](YouTube-API-Key-Setup)
- [Find a YouTube Channel ID](Find-a-YouTube-Channel-ID)

## Symptom: Auto Sync Stops for Today

### Possible causes

- the YouTube daily quota limit has been reached

### How to verify

- check the YouTube quota usage warning in the UI
- confirm the app reports that auto sync is stopped for today

### How to fix

- wait until the next quota window
- reduce unnecessary YouTube operations
- use channel IDs where possible instead of broader URL-based lookups

This is expected behavior when the quota guard blocks further sync work for the day.

## Symptom: Downloads Fail With `Sign in to confirm you're not a bot`

### Possible causes

- YouTube is treating your IP or request pattern as risky
- you are running from a cloud or datacenter IP
- you are trying to access restricted content without cookies

### How to verify

- check the download error in logs or in the episode failure details
- confirm whether the same content works in a browser account session

### How to fix

- upload YouTube cookies in **User Settings**
- use cookies only when necessary
- prefer a throwaway account for cookie export if possible
- reduce aggressive download behavior if your environment is being challenged repeatedly

Related page:

- [YouTube Cookies Setup](YouTube-Cookies-Setup)

## Symptom: Downloads Fail Even After Uploading Cookies

### Possible causes

- the exported cookies are stale
- the cookie file format is wrong
- the browser session was reused after export
- the account itself has problems

### How to verify

- re-export the cookies and compare the behavior
- make sure the file is a real `cookies.txt` export
- confirm the account can still access the target content in a normal browser session

### How to fix

- export a new `cookies.txt`
- follow the private/incognito export flow carefully
- replace the uploaded file in PigeonPod
- test again with one manual download

## Symptom: Bilibili Downloads Fail With Browser Verification or `412`

### Possible causes

- Bilibili is challenging the download request
- the current IP or session is being treated as untrusted

### How to verify

- inspect the failed episode logs
- confirm whether the same content is accessible in a logged-in browser session

### How to fix

- upload Bilibili cookies if needed
- retry later or from a different network environment

## Symptom: A Feed Exists but RSS Looks Empty

### Possible causes

- no episode has reached `COMPLETED` yet
- episodes were cleaned up by the retention limit
- all matching items are still pending, failed, or delayed

### How to verify

- open the feed detail page and inspect episode statuses
- confirm whether at least one episode is `COMPLETED`
- check whether `maximumEpisodes` is set too low

### How to fix

- manually download one episode and wait for it to complete
- increase `maximumEpisodes` if older completed items are being cleaned up too aggressively
- review the feed's auto-download and delay settings

## Symptom: New Videos Appear, but They Do Not Auto Download

### Possible causes

- auto download is disabled
- the auto-download limit is `0` or too low
- the delay setting has not elapsed yet
- keyword or duration filters are excluding the item

### How to verify

- open the feed settings
- confirm `Enable auto download` is on
- confirm the delay value is not larger than intended
- inspect whether the title, description, or duration matches your filters

### How to fix

- enable auto download
- raise the auto-download limit if needed
- set delay to `0` if you want immediate auto-downloads
- relax the keyword or duration filters

Related page:

- [Feed Settings Explained](Feed-Settings-Explained)

## Symptom: Old Downloaded Files Keep Disappearing

### Possible causes

- `maximumEpisodes` is set on the feed

### How to verify

- open the feed settings and check `maximumEpisodes`
- confirm whether the missing items changed from `COMPLETED` back to `READY`

### How to fix

- increase `maximumEpisodes`
- leave it empty if you do not want automatic retention cleanup

PigeonPod keeps the episode metadata, but the old downloaded file can be removed automatically when the completed count exceeds the configured limit.

## Symptom: Subtitles Are Missing

### Possible causes

- subtitle download is explicitly disabled
- the selected language does not exist for that video
- the feed is inheriting a global setting you did not expect

### How to verify

- inspect the feed subtitle settings
- confirm whether the feed uses explicit values or defaults
- check whether the target video actually has subtitles in the requested language

### How to fix

- choose subtitle languages explicitly
- try a broader set of languages
- switch between `VTT` and `SRT` only if the target workflow needs a different format

## Symptom: Playlist or Channel History Is Incomplete

### Possible causes

- only the latest items were loaded during initial subscription
- older items were not backfilled yet

### How to verify

- compare the source page with the episodes visible in the feed
- check whether the feed has a history/backfill action available

### How to fix

- run the history fetch flow for that feed
- wait for background initialization to complete before assuming the import is finished

## When to Stop Troubleshooting and Open an Issue

Open a GitHub issue when:

- you can reproduce the problem consistently
- logs show an application error that is not explained here
- the behavior changed after an upgrade
- a documented workflow no longer works as written

Include:

- your deployment method
- your PigeonPod version
- whether you use Docker Compose, Docker run, or another container workflow
- the relevant error message
- the exact source type involved: YouTube channel, YouTube playlist, or Bilibili

## Related Pages

- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Feed Settings Explained](Feed-Settings-Explained)
- [FAQ](FAQ)
