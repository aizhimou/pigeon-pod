# Transcript

`<podcast:transcript>`

This tag is used to link to a transcript or closed captions file. Multiple tags can be present for multiple transcript formats.

Detailed file format information and example files are [here](../examples/transcripts/transcripts.md).

### Parent

`<item>`

### Count

Multiple

### Attributes

- **url (required):** URL of the podcast transcript.
- **type (required):** Mime type of the file such as `text/plain`, `text/html`, `text/vtt`, `application/json`, `application/x-subrip`
- **language (optional):** The language of the linked transcript. If there is no language attribute given, the linked file is assumed to be the same language that is specified by the RSS `<language>` element.
- **rel (optional):** If the rel="captions" attribute is present, the linked file is considered to be a closed captions file, regardless of what the mime type is. In that scenario, time codes are assumed to be present in the file in some capacity.

### Examples

```xml
<podcast:transcript url="https://example.com/episode1/transcript.html" type="text/html" />
```

```xml
<podcast:transcript url="https://example.com/episode1/transcript.vtt" type="text/vtt" />
```

```xml
<podcast:transcript
        url="https://example.com/episode1/transcript.json"
        type="application/json"
        language="es"
        rel="captions"
/>
```

```xml
<podcast:transcript url="https://example.com/episode1/transcript.srt" type="application/x-subrip" rel="captions" />
```
