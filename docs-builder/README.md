English | [简体中文](README.zh-CN.md)

# docs-builder

Standalone Gradle build (not part of the main build). Generates the script API documentation from the [doc-api](../doc-api/) annotations and their Javadoc, including the `@zh` bilingual tags:

- `api/<module>.md` — English pages (canonical)
- `api/<module>.zh.md` — Chinese pages, translated from `@zh` tags with fallback to English per member
- `api/warnings.txt` — generation warnings (missing `@zh`, missing `@ApiSince`)
- `types/neomacros.d.ts` — `declare module '@neomacros/*'` definitions plus the `neo` global

Markdown and `.d.ts` render from the same `ApiModel` (the annotation-scan output). Generated files are not edited by hand; snapshot baselines under `src/test/snapshots/` are only updated after a reviewed diff.

## Usage

```bash
# Prerequisite: publish the annotation jar to the local repository
./gradlew :doc-api:publishToMavenLocal

# Generate from the real facade sources (core + ports) without snapshot
# assertions; output in build/gen/{md/api,ts/types}/
./gradlew -p docs-builder generateApiDocs

# Generate + snapshot regression + TypeScript compile check
./gradlew -p docs-builder check -PtscPath=<path to typescript/bin/tsc>

# Generate only (no snapshot assertions); output in build/manual-gen/
./gradlew -p docs-builder test
```

`-PtscPath` points at the `tsc` JS file from the typescript package (run via node); without it the compile check is skipped and the other tests still run.

`generateApiDocs` feeds the documentation site: `bash scripts/ci/sync-generated-docs.sh` copies the pages into `docs/vitepress/{en,zh}/api/` and the definitions into `docs/vitepress/public/` (see `.github/workflows/docs.yml` for the CI wiring).

## Layout

```
src/main/java/ink/tuanzi/neomacros/docs/
├── ApiModel.java        # shared render model (modules, members, handles, events, warnings)
├── ModelExtractor.java  # DocletEnvironment → ApiModel, with deterministic ordering
├── DocletSupport.java   # javadoc flattening, @zh extraction, Java→TS type mapping
├── MDDoclet.java        # bilingual Markdown rendering
└── TSDoclet.java        # TypeScript ambient definition rendering
```
