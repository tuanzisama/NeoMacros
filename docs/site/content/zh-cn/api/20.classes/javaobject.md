# JavaObject

Handle

宿主对象的代理：公共字段按值读写，方法按绑定到实例的可调用分发。

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

### `raw()`

The wrapped host object (interop argument adaptation unwraps it).

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

