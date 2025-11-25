# RSS Namespace Extension for Podcasting (Tag Specification)

A wholistic RSS namespace for podcasting that is meant to synthesize the fragmented world of podcast namespaces. As
elements are canonized, they will be added to this document so developers can begin implementation. The
specifications below are considered locked and the team will prioritize backward compatibility. We are operating
under the [Rules for Standards-Makers](http://scripting.com/2017/05/09/rulesForStandardsmakers.html).

The namespace for this extension is `https://podcastindex.org/namespace/1.0`. Clients which recognize this namespace
must also recognize `https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md` as identical. The
suggested tag prefix for use in XML is `podcast`, but clients should support alternate prefixes for this namespace.
If your application generates RSS feeds and you implement one or more elements below, you will need to link this
document in your XML:

```xml
<rss version="2.0" xmlns:podcast="https://podcastindex.org/namespace/1.0">
```

## Podcast Tags

Each tag below exists in the podcast namespace within the specified parent. All attributes are required unless
explicitly specified as optional. Anywhere the url of a hyper-text based resource is specified, it must be given as
`https:` and not `http:`.

## Table of Contents

- [Transcript](tags/transcript.md)<a id="transcript" />
- [Locked](tags/locked.md)<a id="locked" />
- [Funding](tags/funding.md)<a id="funding" />
- [Chapters](tags/chapters.md)<a id="chapters" />
- [Soundbite](tags/soundbite.md)<a id="soundbite" />
- [Person](tags/person.md)<a id="person" />
- [Location](tags/location.md)<a id="location" />
- [Season](tags/season.md)<a id="season" />
- [Episode](tags/episode.md)<a id="episode" />
- [Trailer](tags/trailer.md)<a id="trailer" />
- [License](tags/license.md)<a id="license" />
- [Alternate Enclosure](tags/alternate-enclosure.md)<a id="alternate-enclosure" />
  - [Source](tags/source.md)<a id="source" />
  - [Integrity](tags/integrity.md)<a id="integrity" />
- [Guid](tags/guid.md)<a id="guid" />
- [Value](tags/value.md)<a id="value" />
  - [Value Recipient](tags/value-recipient.md)<a id="value-recipient" />
- [Medium](tags/medium.md)<a id="medium" />
- [Live Item](tags/live-item.md)<a id="live-item" />
- [Content Link](tags/content-link.md)<a id="content-link" />
- [Social Interact](tags/social-interact.md)<a id="social-interact" />
- [Block](tags/block.md)<a id="block" />
- [Txt](tags/txt.md)<a id="txt" />
- [Remote Item](tags/remote-item.md)<a id="remote-item" />
- [Podroll](tags/podroll.md)<a id="podroll" />
- [Update Frequency](tags/update-frequency.md)<a id="update-frequency" />
- [Podping](tags/podping.md)<a id="podping" />
- [Value Time Split](tags/value-time-split.md)<a id="value-time-split" />
- [Chat](tags/chat.md)<a id="chat" />
- [Publisher](tags/publisher.md)<a id="publisher" />
- [Image](tags/image.md)<a id="image" />

## Deprecated
- [Images](tags/images-(deprecated).md)