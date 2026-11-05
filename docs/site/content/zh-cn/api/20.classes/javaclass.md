# JavaClass

Handle

宿主类的代理：静态字段按值读取，静态方法按可调用分发，new 成员构造实例。

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

