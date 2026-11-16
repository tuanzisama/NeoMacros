let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };
  try {
    const { reflect } = neo;

    // forName / classOf / className
    const cls = reflect.forName('java.util.ArrayList');
    check('className', cls.className === 'java.util.ArrayList');
    const inst = reflect.newInstance('java.util.ArrayList');
    check('classOf', reflect.classOf(inst).className === 'java.util.ArrayList');

    // superclass / interfaces
    check('superclass', cls.superclass().className === 'java.util.AbstractList');
    check('interfaces', cls.interfaces().some((c) => c.className === 'java.util.List'));

    // Class enumerations
    check('declaredFields',
        Array.isArray(cls.declaredFields()) && cls.declaredFields().length > 0);
    check('methods', Array.isArray(cls.methods()) && cls.methods().length > 0);
    check('constructors',
        cls.constructors().length >= 1 && cls.declaredConstructors().length >= 1);

    // Constructor handle
    const ctor = cls.constructor();
    check('ctor-handle',
        ctor instanceof neo.ReflectConstructor && ctor.public === true);
    check('ctor-new', ctor.newInstance([]).className === 'java.util.ArrayList');

    // Field handle: a private field of a mod class (no JDK deep reflection)
    const apiCls = reflect.forName('ink.tuanzi.neomacros.core.api.reflect.ReflectApi');
    const apiInst = apiCls.constructor().newInstance();
    const resolverField = apiCls.field('resolver');
    check('field-shape',
        resolverField.name === 'resolver'
            && resolverField.private === true
            && resolverField.public === false
            && resolverField.static === false);
    const before = resolverField.get(apiInst);
    resolverField.set(apiInst, before);
    check('field-getset', before !== null && resolverField.get(apiInst) === before);
    check('field-type', resolverField.type().className
        === 'ink.tuanzi.neomacros.core.api.reflect.ReflectApi$ClassResolver');

    // Method handle
    const add = cls.method('add', ['java.lang.Object']);
    check('method-shape',
        add instanceof neo.ReflectMethod
            && add.name === 'add'
            && add.returnType().className === 'boolean'
            && add.parameterTypes().length === 1
            && typeof add.modifiers() === 'number'
            && add.public === true
            && add.static === false);
    check('invoke', add.invoke(inst, ['x']) === true && inst.size() === 1);

    // Annotations: runtime-retained positive on our own module class
    check('annotation-present',
        apiCls.isAnnotationPresent('ink.tuanzi.neomacros.doc.api.ApiModule'));
    const anno = apiCls.annotation('ink.tuanzi.neomacros.doc.api.ApiModule');
    check('annotation-attr', anno !== undefined && anno.name() === 'reflect');
    check('annotation-absent', cls.isAnnotationPresent('java.lang.Deprecated') === false);

    // Module compat paths
    const resolved = reflect.method(inst, 'add', ['java.lang.Object']);
    check('compat-invoke',
        reflect.invoke(resolved, inst, ['y']) === true && inst.size() === 2);
    check('compat-classname',
        reflect.className(inst) === 'java.util.ArrayList'
            && reflect.className(cls) === 'java.util.ArrayList');
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean) && Object.keys(results).length > 0;
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-reflect: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
