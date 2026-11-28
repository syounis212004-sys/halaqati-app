# 07 Independent agents (stage 8)

Three general-purpose sub-agents ran in the background with fresh context and no shared conclusions (task prompts follow `.claude/skills/engineering-discovery/templates/agent-prompt.md`). Their reports are model output: treated as data, and the decisive claims were re-verified by the author where marked. Tool used: the Agent tool. `superpowers:dispatching-parallel-agents` was not invoked as a skill (logged).

| Agent | Option studied | Verdict | Re-verified by the author? |
|---|---|---|---|
| A | Lite Hafs one-font | viable only if a different look is accepted | yes: own render of p300 and p10 (gap multiplier 2.55 / 2.80), plus visual check |
| B | Terms/licence texts | font bundling allowed with account + credit; packs/asset-package forbidden; offline text only via Content Sync; subsetting UNADDRESSED | yes: author re-read the saved Terms HTML and the Content Sync page |
| C | Raster and lossy codec | raster 78-196 MB (only aliased 1-bit 1080 px reaches 29 MB); codec needs q about 24 for 46 MB | partly: size trends consistent with author's lossless numbers; Android Path cost unverified |

See `agent-A.md`, `agent-B.md`, `agent-C.md` for the condensed reports.
