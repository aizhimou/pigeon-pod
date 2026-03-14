# YouTube Cookies Setup

This page explains when you actually need YouTube cookies in PigeonPod and how to export them safely.

## What This Page Is For

Use this page when:

- downloads fail with `Sign in to confirm you're not a bot`
- you need age-restricted, members-only, or other account-protected YouTube content
- public YouTube access is unreliable from your deployment environment

Do not use this page as a default setup step. Most users do not need YouTube cookies for normal public content.

## Prerequisites

Before you start, make sure you have:

- a working PigeonPod instance
- access to **User Settings**
- a browser you trust
- a YouTube account
- a browser extension that can export `cookies.txt`

Recommended extensions:

- Chrome: [Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc)
- Firefox: [cookies.txt](https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/)

Security note:

- be careful with browser extensions
- if you previously used the old Chrome extension named `Get cookies.txt` without `LOCALLY`, remove it

## When You Should Use Cookies

YouTube cookies are useful when:

- YouTube applies bot checks to your server IP
- the instance runs from a cloud or datacenter network
- the content requires an authenticated account

You usually do **not** need cookies for:

- normal public videos
- a fresh setup that is already working without YouTube challenges

## Steps

### 1. Open a private or incognito window

Start with a fresh private browsing session.

Then:

1. open one private/incognito window
2. log in to your YouTube account there
3. avoid opening extra private tabs for the same session if possible

The goal is to export a clean cookie session that will not be reused later.

### 2. Open the YouTube robots page

In the same private/incognito session, open:

```text
https://www.youtube.com/robots.txt
```

This is the page you should use before exporting cookies.

### 3. Export `youtube.com` cookies

Use your browser extension to export cookies for `youtube.com`.

Requirements:

- export a real `cookies.txt` file
- do not use other unsupported formats
- keep the file private

Important limitation:

- PigeonPod expects uploaded `cookies.txt` content
- this workflow is not the same as using `yt-dlp --cookies-from-browser`

### 4. Close the private window immediately

After exporting:

- close the private/incognito window
- do not keep browsing with the same session

This reduces the chance of the exported cookies being rotated or invalidated quickly.

### 5. Upload the file to PigeonPod

In PigeonPod:

1. log in
2. open **User Settings**
3. open the cookies management area
4. choose **Upload / Update YouTube Cookies**
5. upload the exported `cookies.txt`
6. save

### 6. Retry one failed download

After uploading the cookies:

1. go back to a failed episode or target feed
2. retry one download manually
3. confirm whether the bot-check failure is gone

## Verify

After uploading cookies, verify these checks:

1. the cookies upload succeeds in the UI
2. a previously failing YouTube download can now complete
3. the same feed no longer fails immediately with the bot-check error

If downloads still fail, do not assume cookies are working just because the upload succeeded. Always validate with a real retry.

## Common Failures

### “I uploaded cookies, but downloads still fail”

Check:

- the file is a real `cookies.txt` export
- the export was done from a private/incognito session
- the YouTube account can still access the target content in a browser
- the file was uploaded to the correct PigeonPod instance

### “The cookies stop working after a while”

Possible causes:

- the cookies expired
- the browser session was reused
- YouTube rotated or invalidated the session

Fix:

- export a new file and upload it again

### “Should I always enable cookies?”

No.

Using account cookies adds risk and maintenance overhead. Only use them when normal public access is not enough.

## Security and Risk Notes

- using account cookies can lead to temporary or permanent account restrictions
- use them only when necessary
- consider using a throwaway account
- never share your exported cookie file

## Related Pages

- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Troubleshooting](Troubleshooting)
