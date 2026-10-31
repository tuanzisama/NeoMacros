# partial

Fixture module exercising the generation warning paths: no @zh module body, no class-level @ApiSince, one fully documented member, and one member with neither a translation nor a version annotation. ES module only (global=false): reachable through the import channel but absent from the neo global aggregate.

Category: test · ES module only: `@neomacros/partial`

## Methods

### `tagged()`

Returns a fully documented value.

**Returns** `string` — the tagged value

Since: 0.1.0

### `untagged(input: string)`

Returns a value whose member lacks translation and version annotation.

**Parameters**

- `input` (`string`): the input value

**Returns** `string` — the untouched input

