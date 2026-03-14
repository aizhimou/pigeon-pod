# YouTube API Key Setup

This page shows how to create a YouTube Data API v3 key and add it to PigeonPod.

## What This Page Is For

Use this page when:

- you want to subscribe to YouTube channels or playlists
- feed detection or sync fails because the API key is missing
- you are setting up PigeonPod for the first time

## Prerequisites

Before you start, make sure you have:

- a Google account
- access to [Google Cloud Console](https://console.cloud.google.com/)
- access to your PigeonPod **User Settings**

## Steps

### 1. Open Google Cloud Console

Go to:

- [Google Cloud Console](https://console.cloud.google.com/)

### 2. Create or select a project

In the top project selector:

- choose an existing project
- or create a new project for PigeonPod

If you create a new one, give it a clear name so you can recognize it later.

### 3. Enable YouTube Data API v3

Inside the selected project:

1. Open **APIs & Services**
2. Open **Library**
3. Search for `YouTube Data API v3`
4. Open it
5. Click **Enable**

### 4. Create the API key

After enabling the API:

1. Open **APIs & Services**
2. Open **Credentials**
3. Click **Create Credentials**
4. Choose **API key**

Google will generate a key for you.

### 5. Copy the key

Copy the generated API key and keep it available for the next step.

Treat it like a secret for your instance. Do not post it publicly.

### 6. Add the key to PigeonPod

In PigeonPod:

1. Log in
2. Open **User Settings**
3. Find the **YouTube Data API Key** field or edit action
4. Paste the key
5. Save

Optional:

- set a daily quota limit if you want PigeonPod to stop auto sync after a defined usage threshold

## Verify

After saving the key, verify these checks:

1. Open **User Settings** again and confirm the key is saved
2. Add or refresh a YouTube feed
3. Confirm PigeonPod can load the source preview
4. Confirm sync no longer fails because of a missing API key

If you manage many YouTube feeds, also check the quota panel in settings to make sure usage is being tracked as expected.

## Common Failures

### “I added a key, but YouTube feeds still fail”

Check:

- the key was copied correctly
- `YouTube Data API v3` is actually enabled in the same Google Cloud project
- you saved the key in the instance you are currently using

### “The app says quota is reached”

Check:

- whether the key is shared with other tools or services
- whether your configured daily quota limit is lower than expected

If the quota is already exhausted, auto sync may stop until the quota window resets.

### “Channel URL lookup is inaccurate or expensive”

Use a raw channel ID when possible. It is usually more accurate and uses less YouTube API quota than broad URL-based lookup.

Related page:

- [Find a YouTube Channel ID](Find-a-YouTube-Channel-ID)

## Related Pages

- [Quick Start](Quick-Start)
- [Configuration Overview](Configuration-Overview)
- [Find a YouTube Channel ID](Find-a-YouTube-Channel-ID)
- [Troubleshooting](Troubleshooting)
