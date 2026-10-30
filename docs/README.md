# Documentation index

Documentation for the Tensura Quests ecosystem, maintained alongside the code
(Phase 0 deliverables were written 2026-10-30).

```text
docs/
  architecture/
    overview.md              components, data flow, core rules
  requirements/
    functional-requirements.md       FR/INT requirements with acceptance criteria
    non-functional-requirements.md   NFR/SEC/COMP/TEST requirements
  research/
    ftb-quests-api-investigation.md  registration, task/reward contracts, progress model
    existing-addons-analysis.md      addon survey and the non-duplication boundary
    job-system-integration-research.md   Sympathetic Jobs 1.0.13 bytecode findings
    capital-system-integration-research.md   what exists vs what the brief assumes
    infinite-dungeon-integration-research.md event contract and D2 scope
    economy-integration-research.md  Lightman's Currency findings and the version gate
    compatibility-matrix.md          every pinned version and coordinate
    technical-unknowns.md            open questions with resolution paths
  roadmap/
    roadmap.md                phases, gates, exit criteria, planning change log
  decisions/
    README.md                 ADR index and statuses
    ADR-0001-*.md             repository architecture (core + api split)
    ADR-0002-*.md             per-player counters gate team task completion
    ADR-0003-*.md             additive player attribution in infinite-dungeon-api
```

Rules of use:

- Research documents record what was verified, how, and against which
  version. Unverified claims are marked as such - never silently promoted to
  facts.
- Requirements change status only when acceptance criteria are verified.
- ADRs are immutable once accepted; supersession writes a new ADR.
