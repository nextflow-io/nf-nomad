# Changelog

## Unreleased

### Fixed
- `nomad.jobs.constraints { ... }` and the per-process `constraints` directive
  now also accept the **Map** shape produced by Nextflow's config-file parser,
  not only the **Closure** shape produced by inline `=` assignment. Block-form
  `constraints { node { unique = [name: 'host'] } }` written in a `.config`
  file is parsed by Nextflow as a nested Map; the previous Closure-only check
  silently dropped these (jobs scope) or threw `must be a closure` (process
  scope), making the directive effectively unusable from config files.
  `JobConstraints.fromMap`, `JobConstraintsNode.fromMap`, and
  `JobConstraintsAttr.fromMap` are the new entry points; `parseConstraints`
  and `NomadTaskOptionsResolver.constraints` accept either shape.

### Added
- Map-shape constraint parsers now warn on unknown keys (typos like `nodes:`,
  `hostname:` placed under `node`, or `vendorId:` under `attr.cpu`), unknown
  sub-keys inside `unique`/`cpu`/`kernel` blocks, type mismatches (e.g.
  `node = "string"`), and blocks that produce zero raw entries — surfacing
  silently-dropped configuration so the user can fix the typo instead of
  wondering why their constraint had no effect.

