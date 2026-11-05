# JavaClass

Handle

Proxy over one host class: static fields read as values, static methods dispatch as callables, and the new member constructs instances.

## Methods

### `className()`



**Returns** `string`

### `hasMember(name: string)`



**Parameters**

- `name` `string`: 

**Returns** `boolean`

### `instanceOf(className: string)`



**Parameters**

- `className` `string`: 

**Returns** `boolean`

### `invoke(name: string, args: unknown)`



**Parameters**

- `name` `string`: 
- `args` `unknown`: 

**Returns** `unknown`

### `rawValue()`



**Returns** `unknown`

### `readCallable(name: string)`



**Parameters**

- `name` `string`: 

**Returns** `Function`

### `readValue(name: string)`



**Parameters**

- `name` `string`: 

**Returns** `unknown`

### `write(name: string, value: unknown)`



**Parameters**

- `name` `string`: 
- `value` `unknown`: 

