# Failed Download Notifications Guide

PigeonPod can summarize downloads that have exhausted automatic retries and still need manual attention. The first version supports two notification channels:

- Email (SMTP)
- Generic Webhook Plus

This guide explains when notifications are sent, how to configure them, and how to customize webhook payloads.

## When PigeonPod Sends a Notification

PigeonPod sends a failed-download digest only when all of the following are true:

- The episode is still in the `FAILED` state
- It has already exhausted the automatic retry limit
- No further automatic retry is scheduled
- It has not already been successfully notified before

The notification scheduler runs every **8 hours**, with the first run happening about **5 minutes after startup**.

This is a digest workflow, not an instant per-failure alert. It is meant to reduce noise and help you periodically review episodes that truly need manual action.

## Available Channels

### Email (SMTP)

Use this if you want a human-readable HTML email delivered to a mailbox.

### Generic Webhook Plus

Use this if you want to send the digest to automation tools, self-hosted notification services, or custom integrations.

It supports:

- A required webhook URL
- Optional custom HTTP headers
- Optional custom JSON body
- Simple template placeholders

## How Delivery Works

If both Email and Webhook are enabled:

- PigeonPod tries both channels
- If at least one channel succeeds, the digest is considered delivered
- The same exhausted failure will not be sent again

If all enabled channels fail:

- The digest is not marked as delivered
- PigeonPod will try again in the next notification cycle

If you manually retry a failed episode and it later exhausts retries again, it can appear in a future digest again.

## How to Open the Settings

1. Sign in to PigeonPod
2. Open the `Settings` page
3. Open the `Notifications` modal

## Email Configuration

### Required fields

When Email notification is enabled, you should fill in:

- `SMTP host`
- `SMTP port`
- `From address`
- `Recipient address`

### Optional fields

- `Username`
- `Password`
- `Enable STARTTLS`
- `Enable SSL`

## Email Rules and Notes

- If you set a password, you should also set a username
- `STARTTLS` and `SSL` cannot both be enabled at the same time
- Some mail providers require an app-specific SMTP password instead of your normal login password
- The test button sends a test HTML email using the values currently in the form

## Recommended SMTP Checklist

Before saving Email notification settings, confirm:

- The SMTP host and port are correct
- The sender address is allowed by your mail provider
- The recipient address is valid
- Authentication is configured correctly if your provider requires it
- You chose either STARTTLS or SSL, not both

## Webhook Configuration

### Required field

- `Webhook URL`

The URL must use `http://` or `https://`.

### Optional fields

- `Custom HTTP headers`
- `Custom JSON body`

### Custom HTTP headers format

Enter one header per line:

```text
Authorization: Bearer your-token
X-Api-Key: your-api-key
Title: PigeonPod failed downloads
```

Header values can use the same template placeholders as the JSON body.

## Supported Template Placeholders

PigeonPod currently supports these placeholders:

- `{title}`
- `{content}`
- `{generatedAt}`
- `{baseUrl}`
- `{total}`

These placeholders can be used in:

- Custom HTTP header values
- Text values inside a custom JSON body

They are simple text substitutions. They do not support loops, conditions, or scripting.

## Default Webhook Behavior

If `Custom JSON body` is left empty, PigeonPod sends its built-in default payload.

For a real failed-download digest, the default payload is a JSON array of failed items, for example:

```json
[
  {
    "title": "Yann LeCun: AI, Open Source, and the Future",
    "feedName": "Lex Fridman Podcast",
    "feedURL": "https://pod.example.com/channel/UCexample",
    "retryNumber": 6,
    "publishedAt": "2026-03-10T08:00",
    "error": "ERROR: unable to download video data: HTTP Error 403: Forbidden"
  }
]
```

This default shape is intentionally simple and works well with generic automation tools.

## Custom JSON Body Examples

### Simple generic payload

```json
{
  "title": "{title}",
  "content": "{content}",
  "generatedAt": "{generatedAt}",
  "baseUrl": "{baseUrl}",
  "total": "{total}"
}
```

### Bark-style payload

```json
{
  "title": "{title}",
  "body": "{content}"
}
```

### ntfy-style JSON payload

```json
{
  "topic": "pigeonpod",
  "title": "{title}",
  "message": "{content}"
}
```

## Notes About Custom JSON Templates

- The custom body must be valid JSON
- It can be a JSON object or a JSON array
- Placeholder replacement is applied only to text values
- Numeric and boolean fields are left as-is unless you put a placeholder inside a string

For example, this:

```json
{
  "total": "{total}"
}
```

will produce a string value, not a native JSON number.

## What the Notification Contains

### Email digest

The Email digest includes:

- A clear subject line
- The generation time
- The base URL, if configured
- One section per failed episode
- A direct `Feed URL` so you can open the PigeonPod console and manually retry
- The latest recorded error summary

### Webhook digest

The default Webhook digest includes:

- Episode title
- Feed name
- Feed URL
- Retry count
- Published time
- Latest error summary

## Testing Your Configuration

### Test Email

Use `Send test email` to verify:

- SMTP connectivity
- Authentication
- Sender and recipient addresses
- HTML email rendering

### Test Webhook

Use `Send test webhook` to verify:

- The webhook URL is reachable
- Authentication headers are correct
- Your custom JSON body is valid
- Your receiver accepts the payload shape

## Troubleshooting

### Email test fails

Check:

- SMTP host and port
- Whether your provider requires authentication
- Whether your provider requires an app password
- Whether STARTTLS or SSL is configured correctly

### Webhook test fails

Check:

- The URL is valid and reachable
- Required auth headers are present
- The custom JSON body is valid JSON
- The receiver expects `application/json`

### I enabled both channels but only one arrived

That is still treated as a successful delivery. PigeonPod only needs one enabled channel to succeed to mark the digest as delivered.

### Why did I not get repeated reminders for the same episode?

Because PigeonPod suppresses duplicate notifications once a failed digest has been successfully delivered for that exhausted failure state.

## Summary

PigeonPod's failed-download notifications are designed to be lightweight and practical:

- Digest-based instead of noisy instant alerts
- Email for direct human review
- Generic Webhook Plus for integrations
- Simple placeholder-based customization
- Direct links back to the PigeonPod console for manual recovery
