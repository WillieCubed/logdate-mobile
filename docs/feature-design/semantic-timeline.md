# Semantic Timeline

## Vision

The Semantic Timeline replaces the current modality-based timeline with a **narrative-first, moment-based layout**. When someone looks at the timeline, they should see a stream of consciousness and memories grouped by day, organized by narrative — not a categorized inventory of captures.

## Core Concept: Moments

A **Moment** is a semantically coherent experience — "coffee with Ava", "afternoon at the park", "that quiet evening at home". Moments are the structural unit of the timeline, replacing the current content-type sections ("Captured", "Noted", "Said out loud").

### Notes are not memories

A single text note written at 10pm might describe 5 different moments from throughout the day. Conversely, 3 photos taken at the same cafe within 10 minutes are a single moment. The timeline must parse content semantically, not just group by timestamp or input modality.

### Moment inference

Moments are inferred using two strategies:

1. **AI-inferred** (primary): An AI model analyzes all notes for a day and identifies semantic moment boundaries, producing contextual labels and time estimates. This handles the complex case of journal dumps that describe multiple events.
2. **Heuristic fallback** (offline/error): Notes are grouped by time-of-day buckets (Morning, Afternoon, Evening) crossed with place proximity. Labels are derived from place names or time of day.

## Layout

### Flowing moments (default)

Normal days use a **flowing layout** where moments are inline sections divided by contextual labels. The first or most significant moment gets hero treatment (larger media, more emphasis). Content flows naturally without hard card boundaries.

### Stacked moment cards (big days)

Birthdays, Rewinds, and high-activity days (5+ moments or 10+ notes) use **stacked card surfaces** where each moment is a distinct card with rounded borders, providing maximum visual impact and separation.

### Minimal days

A day with a single text note renders cleanly with just the contextual label and content — no unnecessary structure.

## Contextual Labels

Labels come from context, never from content type:

| Priority | Source | Example |
|----------|--------|---------|
| 1 | Place name | "At Blue Bottle Coffee" |
| 2 | Activity (AI-inferred) | "Morning run" |
| 3 | Time of day | "That evening", "Morning" |

Labels like "Captured", "Noted", or "Said out loud" are **never used** — they describe the input modality, not the experience.

## Embedded Metadata

No floating metadata chips or recap strips. Instead:

- **Place names** are part of the moment's contextual label
- **People** are referenced inline within the moment content ("with Ava")
- **Media counts** are implicit from the visible media grid
- Relevant stats for big days are embedded in card surfaces

## What Was Removed

| Removed | Why |
|---------|-----|
| "Captured" / "Noted" / "Said out loud" labels | Content-type labels have no semantic meaning |
| Recap strip ("3 captured", "2h span") | Vanity metrics that don't tell a story |
| Metadata chips ("2 places", "1 people") | Floating counts add noise, not meaning |
| `MEDIA_LED` / `VOICE_LED` / `PLACE_LED` / `STORY_LED` layout enum | Layout was driven by content-type counts, not by the moments themselves |

## Graceful Degradation

When AI is unavailable (offline, no credentials, error):

- No narrative summary displayed
- Moments fall back to time-of-day groupings ("Morning", "Afternoon", "Evening")
- Content still flows — media grids, text snippets, and audio notes render normally
- The experience is honest: no fake narrative, just organized content
