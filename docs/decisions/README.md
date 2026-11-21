# Decision records

Short records of non-obvious choices, so agents and humans do not undo past
nuances. Check the records for an area before changing its behavior. Add one
when you make a choice a future reader might second-guess.

Raw transcripts are never committed. Summarize the decision in your own words,
and leave out personal or sensitive details.

## Index

_No records yet._

<!-- Add one bullet per record: link text "NNNN: Title", target the record file, then the platforms in parentheses. -->

## Template

Name files `NNNN-short-title.md` (e.g. `0001-dynamodb-prediction-cache.md`).

```markdown
# NNNN: Title

**Date:** YYYY-MM-DD

## Context
What problem or constraint forced a choice.

## Decision
What was chosen, stated so it can be checked against the code.

## Alternatives rejected
Each option considered, and why it lost.

## Platforms
backend / web / infra / firmware / apps: which ones this constrains.

## Source
Where this came from (PR, issue, design doc, or a summarized prior discussion).
```
