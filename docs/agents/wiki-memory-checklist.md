# Wiki + Memory Checklist

Use this after non-trivial MiruPlay work. Keep it small: capture the durable part, skip the noise.

## When to record
- A user-visible behavior changed and the repo now has a new default, constraint, or workflow.
- A debugging session found the real root cause, not just a symptom.
- A project rule or cross-surface guardrail changed.
- A verification workflow produced a durable result worth reusing later.

## Record in wiki when
- The finding needs backlinks, synthesis, or cross-session retrieval.
- The work produced a reusable story: bug root cause, fix shape, verification result, or process rule.
- The result belongs to MiruPlay project knowledge rather than generic personal preference.

## Record in memory when
- The user shared a durable preference or corrected how work should be done.
- A stable project convention or tool quirk will likely matter again.
- The fact is short, durable, and useful even without wiki context.

## Do not record
- Temporary TODOs, half-finished guesses, or raw scratch notes.
- Every small edit or routine refactor.
- Duplicates of existing wiki pages or memory entries unless the old one is wrong.
- Test media titles or one-off repro content unless they matter to the fix itself.

## 30-second closeout
- Ask: "What would be annoying to rediscover next week?"
- If the answer is a project finding, save one `wiki_observe` or `wiki_retro`.
- If the answer is a preference/convention/tool quirk, save one `memory` entry.
- If nothing durable was learned, record nothing.

## Use a subagent when
- The change spans multiple surfaces and you need a bounded audit before closing, especially TV settings + WebAPI + WebUI parity.
- The work has one main implementation path plus a separate review/sweep path that can run in parallel.
- The wiki or docs need cleanup across several related pages and a focused pass is cheaper than hand-checking everything.
- After the main fix lands, a subagent may be used to audit parity, missing follow-through, or knowledge-capture gaps before finalizing.

## MiruPlay bias
- Prefer wiki for playback, Zidoo, WebControl, settings parity, scanner behavior, release verification, and other repo-specific knowledge.
- Prefer memory for user preferences, stable repo conventions, and non-obvious tool behavior.
- When both apply, keep memory short and let wiki hold the details.
