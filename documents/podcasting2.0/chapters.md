# Chapters

`<podcast:chapters>`

Links to an external file (see example file) containing chapter data for the episode. See the [jsonChapters.md](../examples/chapters/jsonChapters.md) file for a description of the file syntax for chapters syntax. And, see the [example.json](../examples/chapters/example.json) example file for a real world example.

Benefits with this approach are that chapters do not require altering audio files, and the chapters can be edited after publishing, since they are a separate file that can be requested on playback (or cached with download). JSON chapter information also allows chapters to be displayed by a wider range of playback tools, including web browsers (which typically have no access to ID3 tags), thus greatly simplifying chapter support; and images can be retrieved on playback, rather than bloating the filesize of the audio. The data held is compatible with normal ID3 tags, thus requiring no additional work for the publisher.

### Parent

`<item>`

### Count

Single

### Attributes

- **url (required):** The URL where the chapters file is located.
- **type (required):** Mime type of file - JSON prefered, 'application/json+chapters'.

### Examples

```xml
<podcast:chapters url="https://example.com/episode1/chapters.json" type="application/json+chapters" />
```
