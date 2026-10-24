# Lexicons the server validates records against

These are the schemas of the records other apps publish through a LogDate account. LogDate's own
schemas live in the repository's `lexicons/` folder and reach the server through `shared/lexicon`. The
files here belong to other authorities, and each is the exact `value` of the
`com.atproto.lexicon.schema` record its authority publishes, with the record's `$type` removed.

They were resolved as the [Lexicon specification](https://atproto.com/specs/lexicon#lexicon-publication-and-resolution)
describes: the `_lexicon` DNS TXT record of the NSID's authority names a DID, and that DID's repository
holds the schema as a `com.atproto.lexicon.schema` record whose key is the NSID.

| NSID | `_lexicon` record | Publishing DID | Record CID when copied (2026-10-24) |
|---|---|---|---|
| `site.standard.document` | `_lexicon.standard.site` | `did:plc:re3ebnp5v7ffagz6rb6xfei4` | `bafyreifylc5lfr4vqfzrwdtqm4pnfg7cbra7z4acbgikylqandzefwdeaq` |
| `site.standard.publication` | `_lexicon.standard.site` | `did:plc:re3ebnp5v7ffagz6rb6xfei4` | `bafyreiapvnarfn3nl2wvg27hsylli5zqvou7a74p7od5mu4detprtejapy` |
| `site.standard.theme.basic` | `_lexicon.theme.standard.site` | `did:plc:re3ebnp5v7ffagz6rb6xfei4` | `bafyreiabm6f64zosv5jcdmwd3qvekjhoi7enevg53rxj734ney4sd6lmku` |
| `site.standard.theme.color` | `_lexicon.theme.standard.site` | `did:plc:re3ebnp5v7ffagz6rb6xfei4` | `bafyreicps42z5maxdqwml7t3klsrrpk332jthyfgpeb4zjn3wlvp5vyjia` |
| `com.atproto.repo.strongRef` | `_lexicon.repo.atproto.com` | `did:plc:6msi3pj7krzih5qxqtryxlzw` | `bafyreifrkdbnkvfjujntdaeigolnrjj3srrs53tfixjhmacclps72qlov4` |
| `com.atproto.label.defs` | `_lexicon.label.atproto.com` | `did:plc:6msi3pj7krzih5qxqtryxlzw` | `bafyreidp2wpcbzl2qiob2uwoc7lhntojx3tjcr553mmahw2f5cumtm3wpm` |

To update one, resolve it again the same way (`dig +short TXT _lexicon.<authority domain>`, then
`com.atproto.repo.getRecord` with the DID, the collection `com.atproto.lexicon.schema` and the NSID as
the key), replace the file and the CID above, and run `PublicRecordValidatorTest`. Don't edit these
files by hand.
