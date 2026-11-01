import sys

p = sys.argv[1]
src = open(p, encoding='utf-8').read()
i = 0
line = 1
depth = 0
n = len(src)
while i < n:
    c = src[i]
    if c == '\n':
        line += 1
        i += 1
        continue
    if src.startswith('/*', i):
        j = src.find('*/', i + 2)
        if j == -1:
            print('UNTERMINATED block comment opening at line', line)
            sys.exit()
        line += src.count('\n', i, j)
        i = j + 2
        continue
    if src.startswith('//', i):
        j = src.find('\n', i)
        if j == -1:
            break
        line += src.count('\n', i, j)
        i = j
        continue
    if c == '"':
        j = i + 1
        while j < n:
            if src[j] == chr(92):
                j += 2
                continue
            if src[j] == '"':
                break
            j += 1
        line += src.count('\n', i, j)
        i = j + 1
        continue
    if c == "'":
        j = i + 1
        while j < n:
            if src[j] == chr(92):
                j += 2
                continue
            if src[j] == "'":
                break
            j += 1
        line += src.count('\n', i, j)
        i = j + 1
        continue
    if c == '{':
        depth += 1
    elif c == '}':
        depth -= 1
        if depth == 0:
            print('top-level close at line', line)
        if depth < 0:
            print('EXTRA } at line', line)
    i += 1
print('final depth', depth)
